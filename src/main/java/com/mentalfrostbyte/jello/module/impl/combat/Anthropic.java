package com.mentalfrostbyte.jello.module.impl.combat;

import com.mentalfrostbyte.Client;
import com.mentalfrostbyte.jello.event.impl.player.movement.EventMotion;
import com.mentalfrostbyte.jello.module.Module;
import com.mentalfrostbyte.jello.module.data.ModuleCategory;
import com.mentalfrostbyte.jello.module.settings.impl.BooleanSetting;
import com.mentalfrostbyte.jello.module.settings.impl.ModeSetting;
import com.mentalfrostbyte.jello.module.settings.impl.NumberSetting;
import net.minecraft.client.entity.player.AbstractClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.ProjectileHelper;
import net.minecraft.scoreboard.ScorePlayerTeam;
import net.minecraft.util.Hand;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.EntityRayTraceResult;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RayTraceContext;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.vector.Vector3d;
import team.sdhq.eventBus.annotations.EventTarget;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Independent KillAura experiment.
 *
 * Combat helpers from the client are intentionally not used here. Targeting,
 * aim-point selection, rotation smoothing, ray casting and attack timing are
 * implemented locally so the module can be evaluated in isolation.
 */
public class Anthropic extends Module {
    private final List<PlayerEntity> selectedTargets = new ArrayList<>();

    private PlayerEntity primaryTarget;
    private Vector3d attackOrigin;
    private Vector3d aimPoint;

    private float appliedYaw;
    private float appliedPitch;
    private float yawVelocity;
    private float pitchVelocity;

    private int lastTargetId = Integer.MIN_VALUE;
    private int switchTicks;
    private long nextAttackTime;

    public Anthropic() {
        super(ModuleCategory.COMBAT, "Anthropic", "Independent KillAura experiment.");

        this.registerSetting(new ModeSetting("Mode", "Attack one target or multiple targets.", 0,
                "Single", "Multi", "Switch"));
        this.registerSetting(new NumberSetting<>("Range", "Maximum attack range.",
                3.4F, 2.0F, 6.0F, 0.05F));
        this.registerSetting(new NumberSetting<>("Max Targets", "Maximum targets attacked in Multi mode.",
                4.0F, 1.0F, 8.0F, 1.0F));
        this.registerSetting(new NumberSetting<>("Switch Delay", "Ticks spent on one target in Switch mode.",
                5.0F, 2.0F, 20.0F, 1.0F));
        this.registerSetting(new NumberSetting<>("CPS", "Attack cycles per second.",
                10.0F, 1.0F, 20.0F, 1.0F));
        this.registerSetting(new NumberSetting<>("Rotation Speed", "Maximum adaptive rotation step per tick.",
                90.0F, 5.0F, 180.0F, 5.0F));
        this.registerSetting(new NumberSetting<>("Prediction", "Ticks of target motion to aim ahead of.",
                1.0F, 0.0F, 2.5F, 0.05F));
        this.registerSetting(new NumberSetting<>("FOV", "Horizontal field of view targets are engaged in.",
                180.0F, 30.0F, 360.0F, 5.0F));
        this.registerSetting(new NumberSetting<>("Max Hurt Time", "Only attack targets with a lower hurt time.",
                10.0F, 0.0F, 10.0F, 1.0F));
        this.registerSetting(new BooleanSetting("Raycast", "Require the server rotation to resolve to a valid target.",
                true));
        this.registerSetting(new BooleanSetting("Through Walls", "Allow targets behind blocks.",
                false));
    }

    @Override
    public void onEnable() {
        this.clearState();
        if (mc.player != null) {
            this.appliedYaw = mc.player.lastReportedYaw;
            this.appliedPitch = mc.player.lastReportedPitch;
        }
        super.onEnable();
    }

    @Override
    public void onDisable() {
        this.clearState();
        super.onDisable();
    }

    @EventTarget
    public void onMotion(EventMotion event) {
        if (!event.isPre()) {
            return;
        }

        if (mc.player == null || mc.world == null || mc.playerController == null) {
            this.clearTargets();
            return;
        }

        // A human cannot aim while a screen is open, while using an item or while
        // clicking manually; the aura stands down instead of fighting the player.
        if (mc.currentScreen != null || mc.player.isHandActive()
                || mc.gameSettings.keyBindAttack.isKeyDown()) {
            this.clearTargets();
            this.yawVelocity *= 0.5F;
            this.pitchVelocity *= 0.5F;
            return;
        }

        this.attackOrigin = new Vector3d(
                event.getX(),
                event.getY() + (double) mc.player.getEyeHeight(),
                event.getZ());

        float baseYaw = mc.player.lastReportedYaw;
        float basePitch = mc.player.lastReportedPitch;

        if (!this.updateTargets(baseYaw, basePitch)) {
            this.yawVelocity *= 0.35F;
            this.pitchVelocity *= 0.35F;
            return;
        }

        // Aim costs are measured from the last applied rotation rather than the raw
        // server rotation, so the chosen point does not wander across the hitbox.
        this.aimPoint = this.findBestAimPoint(this.primaryTarget, this.attackOrigin, this.appliedYaw, this.appliedPitch);
        if (this.aimPoint == null) {
            this.clearTargets();
            return;
        }

        float[] desiredRotation = this.rotationTo(this.attackOrigin, this.aimPoint);
        this.updateSmoothRotation(baseYaw, basePitch, desiredRotation[0], desiredRotation[1]);

        event.setYaw(this.appliedYaw);
        event.setPitch(this.appliedPitch);

        float remainingYaw = Math.abs(wrapDegrees(desiredRotation[0] - this.appliedYaw));
        float remainingPitch = Math.abs(desiredRotation[1] - this.appliedPitch);
        boolean rotationReady = remainingYaw <= 6.0F && remainingPitch <= 6.0F;

        if (rotationReady) {
            event.attackPost(this::attackSelectedTargets);
        }
    }

    private boolean updateTargets(float baseYaw, float basePitch) {
        List<ScoredTarget> candidates = new ArrayList<>();
        double engageRange = this.getNumberValueBySettingName("Range") + 1.0D;
        float halfFov = this.getNumberValueBySettingName("FOV") * 0.5F;

        for (AbstractClientPlayerEntity player : mc.world.getPlayers()) {
            if (!this.isBasicTargetValid(player)) {
                continue;
            }

            if (this.distanceToBox(this.attackOrigin, player.getBoundingBox()) <= engageRange
                    && !this.isOutsideFov(player, baseYaw, halfFov)) {
                candidates.add(new ScoredTarget(player, this.targetScore(player, baseYaw, basePitch)));
            }
        }

        if (candidates.isEmpty()) {
            this.clearTargets();
            return false;
        }

        candidates.sort(Comparator.comparingDouble(scored -> scored.score));

        String mode = this.getStringSettingValueByName("Mode");
        PlayerEntity best;
        if ("Switch".equals(mode)) {
            best = this.pickSwitchTarget(candidates);
        } else {
            best = candidates.get(0).player;

            if (this.primaryTarget != null) {
                double bestScore = candidates.get(0).score;
                double ownScore = Double.NaN;
                for (ScoredTarget scored : candidates) {
                    if (scored.player == this.primaryTarget) {
                        ownScore = scored.score;
                        break;
                    }
                }

                // Small hysteresis keeps the aura from changing target every frame when
                // two players have nearly identical scores.
                if (!Double.isNaN(ownScore) && ownScore <= bestScore * 1.15D + 2.0D) {
                    best = this.primaryTarget;
                }
            }
        }

        if (best.getEntityId() != this.lastTargetId) {
            this.lastTargetId = best.getEntityId();
            this.yawVelocity *= 0.2F;
            this.pitchVelocity *= 0.2F;
        }

        this.primaryTarget = best;
        this.selectedTargets.clear();
        this.selectedTargets.add(best);

        if ("Multi".equals(mode)) {
            int maxTargets = Math.max(1, (int) this.getNumberValueBySettingName("Max Targets"));
            for (ScoredTarget scored : candidates) {
                if (this.selectedTargets.size() >= maxTargets) {
                    break;
                }
                if (scored.player != best) {
                    this.selectedTargets.add(scored.player);
                }
            }
        }

        return true;
    }

    private PlayerEntity pickSwitchTarget(List<ScoredTarget> candidates) {
        int delay = Math.max(2, (int) this.getNumberValueBySettingName("Switch Delay"));

        if (this.primaryTarget != null) {
            for (ScoredTarget scored : candidates) {
                if (scored.player == this.primaryTarget) {
                    if (this.switchTicks < delay) {
                        this.switchTicks++;
                        return this.primaryTarget;
                    }
                    break;
                }
            }
        }

        // Dwell time expired or the target vanished: take the next candidate after
        // the current one so every switch is a single step down the sorted list.
        int index = -1;
        for (int i = 0; i < candidates.size(); i++) {
            if (candidates.get(i).player == this.primaryTarget) {
                index = i;
                break;
            }
        }

        this.switchTicks = 0;
        return candidates.get((index + 1) % candidates.size()).player;
    }

    private boolean isBasicTargetValid(PlayerEntity player) {
        return player != null
                && player != mc.player
                && player.isAlive()
                && !player.isSpectator()
                && !player.isCreative()
                && !player.isInvulnerable()
                && player.getHealth() > 0.0F
                && !Client.getInstance().botManager.isBot(player)
                && !Client.getInstance().friendManager.isFriendPure(player)
                && !this.isTeammate(player);
    }

    private boolean isTeammate(PlayerEntity player) {
        Module teams = Client.getInstance().moduleManager.getModuleByClass(Teams.class);
        if (teams == null || !teams.isEnabled() || mc.player == null) {
            return false;
        }

        // Both players must actually be on a scoreboard team; otherwise the check
        // would treat every player on a team-less server as an ally.
        if (!(mc.player.getTeam() instanceof ScorePlayerTeam) || !(player.getTeam() instanceof ScorePlayerTeam)) {
            return false;
        }

        Integer ownColor = ((ScorePlayerTeam) mc.player.getTeam()).getColor().getColor();
        Integer otherColor = ((ScorePlayerTeam) player.getTeam()).getColor().getColor();
        return ownColor != null && ownColor.equals(otherColor);
    }

    private boolean isOutsideFov(PlayerEntity target, float baseYaw, float halfFov) {
        if (halfFov >= 179.0F) {
            return false;
        }

        Vector3d closest = this.closestPoint(this.attackOrigin, target.getBoundingBox());
        float yawTo = this.rotationTo(this.attackOrigin, closest)[0];
        return Math.abs(wrapDegrees(yawTo - baseYaw)) > halfFov;
    }

    private double targetScore(PlayerEntity target, float currentYaw, float currentPitch) {
        Vector3d closest = this.closestPoint(this.attackOrigin, target.getBoundingBox());
        float[] rotation = this.rotationTo(this.attackOrigin, closest);
        double yawCost = Math.abs(wrapDegrees(rotation[0] - currentYaw));
        double pitchCost = Math.abs(rotation[1] - currentPitch);
        double angularCost = Math.sqrt(yawCost * yawCost + pitchCost * pitchCost * 0.55D);
        double distance = this.distanceToBox(this.attackOrigin, target.getBoundingBox());

        // Distance matters, but not enough to constantly pull the target away from
        // whatever the current server rotation can reach cleanly.
        return angularCost * 0.72D + distance * 8.0D;
    }

    /**
     * Extrapolates the hitbox along the target's motion. Attacks and range checks
     * always use the real box; only the aim leads ahead.
     */
    private AxisAlignedBB predictedBox(PlayerEntity target) {
        float lead = this.getNumberValueBySettingName("Prediction");
        if (lead <= 0.0F) {
            return target.getBoundingBox();
        }

        double dx = clampMotionAxis(target.getPosX() - target.prevPosX);
        double dy = clampMotionAxis(target.getPosY() - target.prevPosY);
        double dz = clampMotionAxis(target.getPosZ() - target.prevPosZ);
        return target.getBoundingBox().offset(dx * lead, dy * lead, dz * lead);
    }

    private static double clampMotionAxis(double motion) {
        // Teleports and lag spikes produce huge prev/pos deltas; leading by those
        // would throw the aim far off the real trajectory.
        return MathHelper.clamp(motion, -1.5D, 1.5D);
    }

    private Vector3d findBestAimPoint(PlayerEntity target, Vector3d origin, float currentYaw, float currentPitch) {
        AxisAlignedBB box = this.predictedBox(target);

        // Eight candidates cover a player hitbox well: the closest point, the box
        // center and the six face centers. They are ranked by cost first, so only
        // the few best points ever need a block raytrace.
        List<ScoredPoint> points = new ArrayList<>(8);
        this.addAimPoint(points, origin, this.closestPoint(origin, box), currentYaw, currentPitch);
        this.addAimPoint(points, origin, box.getCenter(), currentYaw, currentPitch);

        double cx = (box.minX + box.maxX) * 0.5D;
        double cy = (box.minY + box.maxY) * 0.5D;
        double cz = (box.minZ + box.maxZ) * 0.5D;
        this.addAimPoint(points, origin, new Vector3d(box.minX, cy, cz), currentYaw, currentPitch);
        this.addAimPoint(points, origin, new Vector3d(box.maxX, cy, cz), currentYaw, currentPitch);
        this.addAimPoint(points, origin, new Vector3d(cx, box.minY, cz), currentYaw, currentPitch);
        this.addAimPoint(points, origin, new Vector3d(cx, box.maxY, cz), currentYaw, currentPitch);
        this.addAimPoint(points, origin, new Vector3d(cx, cy, box.minZ), currentYaw, currentPitch);
        this.addAimPoint(points, origin, new Vector3d(cx, cy, box.maxZ), currentYaw, currentPitch);

        points.sort(Comparator.comparingDouble(point -> point.cost));

        if (this.getBooleanValueFromSettingName("Through Walls")) {
            return points.get(0).point;
        }

        // Visibility is the expensive part, so only the best handful of points are
        // tested, in cost order: the first one with a clear line of sight wins.
        int raycastBudget = Math.min(6, points.size());
        for (int i = 0; i < raycastBudget; i++) {
            if (this.canUseAimPoint(origin, points.get(i).point)) {
                return points.get(i).point;
            }
        }

        return null;
    }

    private void addAimPoint(List<ScoredPoint> points, Vector3d origin, Vector3d point,
                             float currentYaw, float currentPitch) {
        points.add(new ScoredPoint(point, this.aimPointCost(origin, point, currentYaw, currentPitch)));
    }

    private double aimPointCost(Vector3d origin, Vector3d point, float currentYaw, float currentPitch) {
        float[] rotation = this.rotationTo(origin, point);
        double yawCost = Math.abs(wrapDegrees(rotation[0] - currentYaw));
        double pitchCost = Math.abs(rotation[1] - currentPitch);
        double distanceCost = Math.sqrt(origin.squareDistanceTo(point)) * 0.12D;
        return yawCost + pitchCost * 0.75D + distanceCost;
    }

    private boolean canUseAimPoint(Vector3d origin, Vector3d point) {
        if (this.getBooleanValueFromSettingName("Through Walls")) {
            return true;
        }

        RayTraceResult blockHit = mc.world.rayTraceBlocks(new RayTraceContext(
                origin,
                point,
                RayTraceContext.BlockMode.COLLIDER,
                RayTraceContext.FluidMode.NONE,
                mc.player));

        if (blockHit == null || blockHit.getType() == RayTraceResult.Type.MISS) {
            return true;
        }

        double pointDistance = origin.squareDistanceTo(point);
        double blockDistance = origin.squareDistanceTo(blockHit.getHitVec());
        return blockDistance + 1.0E-4D >= pointDistance;
    }

    private void updateSmoothRotation(float baseYaw, float basePitch, float targetYaw, float targetPitch) {
        float maxStep = this.getNumberValueBySettingName("Rotation Speed");
        float yawError = wrapDegrees(targetYaw - baseYaw);
        float pitchError = targetPitch - basePitch;

        if (Math.hypot(yawError, pitchError) < 0.4F) {
            // Deadzone: settle on the exact aim instead of emitting endless
            // sub-degree rotations every tick.
            this.yawVelocity = 0.0F;
            this.pitchVelocity = 0.0F;
            this.appliedYaw = wrapDegrees(targetYaw);
            this.appliedPitch = MathHelper.clamp(targetPitch, -90.0F, 90.0F);
            return;
        }

        // Proportional gain grows with the remaining error: precise near the aim,
        // decisive on large flicks.
        float gain = 0.20F + 0.35F * Math.min(1.0F, (float) Math.hypot(yawError, pitchError) / 60.0F);
        float desiredYawStep = MathHelper.clamp(yawError * gain, -maxStep, maxStep);
        float desiredPitchStep = MathHelper.clamp(pitchError * gain, -maxStep, maxStep);

        // The smoothed step gives ease-in and ease-out; clamping it to the remaining
        // error guarantees the rotation can never overshoot the aim point.
        this.yawVelocity = this.yawVelocity * 0.45F + desiredYawStep * 0.55F;
        this.pitchVelocity = this.pitchVelocity * 0.45F + desiredPitchStep * 0.55F;

        float yawStep = clampMagnitude(this.yawVelocity, Math.abs(yawError));
        float pitchStep = clampMagnitude(this.pitchVelocity, Math.abs(pitchError));

        float yaw = baseYaw + yawStep;
        float pitch = MathHelper.clamp(basePitch + pitchStep, -90.0F, 90.0F);

        float gcd = this.mouseGcd();
        if (gcd > 0.0F) {
            yawStep = yaw - baseYaw;
            pitchStep = pitch - basePitch;
            yaw = baseYaw + yawStep - yawStep % gcd;
            pitch = basePitch + pitchStep - pitchStep % gcd;
        }

        this.appliedYaw = wrapDegrees(yaw);
        this.appliedPitch = MathHelper.clamp(pitch, -90.0F, 90.0F);
    }

    private static float clampMagnitude(float value, float cap) {
        if (cap < 0.0F) {
            throw new IllegalArgumentException("cap must be non-negative");
        }
        return MathHelper.clamp(value, -cap, cap);
    }

    private float mouseGcd() {
        float sensitivity = (float) (mc.gameSettings.mouseSensitivity * 0.6F + 0.2F);
        return sensitivity * sensitivity * sensitivity * 1.2F;
    }

    private void attackSelectedTargets() {
        if (mc.player == null || mc.world == null || mc.playerController == null
                || this.primaryTarget == null || this.attackOrigin == null) {
            return;
        }

        long now = System.currentTimeMillis();
        if (now < this.nextAttackTime) {
            return;
        }

        float range = this.getNumberValueBySettingName("Range");
        String mode = this.getStringSettingValueByName("Mode");
        boolean raycast = this.getBooleanValueFromSettingName("Raycast");
        boolean attacked = false;

        if ("Multi".equals(mode)) {
            // Multi intentionally means "attack all selected valid targets". A single
            // server rotation cannot geometrically point at several separated players,
            // so exact crosshair raycast is a Single/Switch-mode condition. Multi
            // still uses per-target block visibility unless Through Walls is enabled.
            for (PlayerEntity target : this.selectedTargets) {
                if (this.isAttackableNow(target, range)) {
                    this.attackEntity(target);
                    attacked = true;
                }
            }
        } else {
            PlayerEntity target = this.primaryTarget;

            if (raycast) {
                Entity hit = this.raycastEntity(this.appliedYaw, this.appliedPitch, range);
                if (hit instanceof PlayerEntity && this.isAttackableNow((PlayerEntity) hit, range)) {
                    target = (PlayerEntity) hit;
                } else {
                    return;
                }
            }

            if (this.isAttackableNow(target, range)) {
                this.attackEntity(target);
                attacked = true;
            }
        }

        if (attacked) {
            this.scheduleNextAttack(now);
        }
    }

    private boolean isAttackableNow(PlayerEntity target, float range) {
        if (!this.isBasicTargetValid(target)) {
            return false;
        }

        // Hurt time gate: a 1.8 target is invulnerable to equal damage for most of
        // its hurt animation, so clicking into that window wastes swings.
        if (target.hurtTime > (int) this.getNumberValueBySettingName("Max Hurt Time")) {
            return false;
        }

        // Range is measured on the real hitbox, never the predicted one; the server
        // resolves the attack against the current position.
        if (this.distanceToBox(this.attackOrigin, target.getBoundingBox()) > range) {
            return false;
        }

        if (!this.getBooleanValueFromSettingName("Through Walls")) {
            return this.findBestAimPoint(target, this.attackOrigin, this.appliedYaw, this.appliedPitch) != null;
        }

        return true;
    }

    private Entity raycastEntity(float yaw, float pitch, double reach) {
        Vector3d origin = this.attackOrigin;
        Vector3d look = mc.player.getLookCustom(1.0F, yaw, pitch);
        Vector3d end = origin.add(look.x * reach, look.y * reach, look.z * reach);

        double maxDistanceSq = reach * reach;
        if (!this.getBooleanValueFromSettingName("Through Walls")) {
            RayTraceResult blockHit = mc.world.rayTraceBlocks(new RayTraceContext(
                    origin,
                    end,
                    RayTraceContext.BlockMode.COLLIDER,
                    RayTraceContext.FluidMode.NONE,
                    mc.player));
            if (blockHit != null && blockHit.getType() != RayTraceResult.Type.MISS) {
                maxDistanceSq = origin.squareDistanceTo(blockHit.getHitVec());
            }
        }

        AxisAlignedBB searchBox = mc.player.getBoundingBox()
                .expand(look.scale(reach))
                .grow(1.0D, 1.0D, 1.0D);

        EntityRayTraceResult result = ProjectileHelper.rayTraceEntities(
                mc.player,
                origin,
                end,
                searchBox,
                this::canRaycastEntity,
                maxDistanceSq);

        return result == null ? null : result.getEntity();
    }

    private boolean canRaycastEntity(Entity entity) {
        if (entity instanceof PlayerEntity) {
            // Keeps the crosshair from "locking onto" friends or bots that merely
            // stand between the player and the intended target.
            return this.isBasicTargetValid((PlayerEntity) entity) && entity.canBeCollidedWith();
        }

        return entity != mc.player
                && entity instanceof LivingEntity
                && entity.isAlive()
                && !entity.isSpectator()
                && entity.canBeCollidedWith();
    }

    private void attackEntity(PlayerEntity target) {
        mc.playerController.attackEntity(mc.player, target);
        mc.player.swingArm(Hand.MAIN_HAND);
    }

    private void scheduleNextAttack(long now) {
        double cps = Math.max(1.0D, this.getNumberValueBySettingName("CPS"));
        double delay = 1000.0D / cps * ThreadLocalRandom.current().nextDouble(0.85D, 1.15D);

        // Click rhythm is not a metronome: most intervals hover around the target
        // CPS, with occasional double clicks and short hesitations.
        double roll = ThreadLocalRandom.current().nextDouble();
        if (roll < 0.08D) {
            delay *= 0.55D;
        } else if (roll > 0.95D) {
            delay += ThreadLocalRandom.current().nextDouble(40.0D, 110.0D);
        }

        this.nextAttackTime = now + Math.max(30L, Math.round(delay));
    }

    private float[] rotationTo(Vector3d from, Vector3d to) {
        double dx = to.x - from.x;
        double dy = to.y - from.y;
        double dz = to.z - from.z;
        double horizontal = Math.sqrt(dx * dx + dz * dz);

        float yaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0D);
        float pitch = (float) (-Math.toDegrees(Math.atan2(dy, horizontal)));
        return new float[] { yaw, MathHelper.clamp(pitch, -90.0F, 90.0F) };
    }

    private Vector3d closestPoint(Vector3d point, AxisAlignedBB box) {
        double x = Math.max(box.minX, Math.min(box.maxX, point.x));
        double y = Math.max(box.minY, Math.min(box.maxY, point.y));
        double z = Math.max(box.minZ, Math.min(box.maxZ, point.z));
        return new Vector3d(x, y, z);
    }

    private double distanceToBox(Vector3d point, AxisAlignedBB box) {
        Vector3d closest = this.closestPoint(point, box);
        return Math.sqrt(point.squareDistanceTo(closest));
    }

    private static float wrapDegrees(float value) {
        return MathHelper.wrapDegrees(value);
    }

    private void clearTargets() {
        this.primaryTarget = null;
        this.aimPoint = null;
        this.attackOrigin = null;
        this.selectedTargets.clear();
        this.lastTargetId = Integer.MIN_VALUE;
        this.switchTicks = 0;
    }

    private void clearState() {
        this.clearTargets();
        this.yawVelocity = 0.0F;
        this.pitchVelocity = 0.0F;
        this.appliedYaw = 0.0F;
        this.appliedPitch = 0.0F;
        this.nextAttackTime = 0L;
    }

    private static final class ScoredTarget {
        private final PlayerEntity player;
        private final double score;

        private ScoredTarget(PlayerEntity player, double score) {
            this.player = player;
            this.score = score;
        }
    }

    private static final class ScoredPoint {
        private final Vector3d point;
        private final double cost;

        private ScoredPoint(Vector3d point, double cost) {
            this.point = point;
            this.cost = cost;
        }
    }
}
