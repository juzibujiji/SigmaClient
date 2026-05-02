package com.mentalfrostbyte.jello.module.impl.movement.blockfly;

import com.mentalfrostbyte.Client;
import com.mentalfrostbyte.jello.event.impl.game.network.EventSendPacket;
import com.mentalfrostbyte.jello.event.impl.player.movement.EventJump;
import com.mentalfrostbyte.jello.event.impl.player.movement.EventMotion;
import com.mentalfrostbyte.jello.event.impl.player.movement.EventMove;
import com.mentalfrostbyte.jello.event.impl.player.movement.EventSafeWalk;
import com.mentalfrostbyte.jello.module.Module;
import com.mentalfrostbyte.jello.module.data.ModuleCategory;
import com.mentalfrostbyte.jello.module.impl.movement.BlockFly;
import com.mentalfrostbyte.jello.module.impl.movement.SafeWalk;
import com.mentalfrostbyte.jello.module.impl.movement.Speed;
import com.mentalfrostbyte.jello.module.settings.impl.BooleanSetting;
import com.mentalfrostbyte.jello.module.settings.impl.ModeSetting;
import com.mentalfrostbyte.jello.module.settings.impl.NumberSetting;
import com.mentalfrostbyte.jello.util.game.player.MovementUtil;
import com.mentalfrostbyte.jello.util.game.world.blocks.BlockUtil;
import net.minecraft.block.BlockState;
import net.minecraft.network.play.client.CAnimateHandPacket;
import net.minecraft.network.play.client.CHeldItemChangePacket;
import net.minecraft.util.ActionResultType;
import net.minecraft.util.Direction;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.BlockRayTraceResult;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RayTraceContext;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.vector.Vector3d;
import team.sdhq.eventBus.annotations.EventTarget;
import team.sdhq.eventBus.annotations.priority.HigherPriority;
import team.sdhq.eventBus.annotations.priority.LowerPriority;

import java.util.LinkedHashSet;
import java.util.Set;

public class BlockFlyGrimSemiMode extends Module {
    private static final float GRIM_REACH_SQ = 4.5F * 4.5F;
    private static final float[] NO_NUDGE = {0.0F};
    private static final float[] YAW_NUDGE = {0.0F, 0.03F, -0.03F, 0.06F, -0.06F, 0.12F, -0.12F, 0.25F, -0.25F};
    private static final float[] PITCH_NUDGE = {0.0F, 0.02F, -0.02F, 0.05F, -0.05F, 0.1F, -0.1F};

    private BlockFly blockFly;
    private PlaceCandidate stashedCandidate;
    private Hand hand = Hand.MAIN_HAND;
    private int previousSlot = -1;
    private int stashedSpoofedSlot = -1;
    private int stashedPrevSlot = -1;
    private String stashedItemSpoof = "None";
    private BlockPos lastPlacedPos;
    private int lastPlaceTick = -1;

    private final NumberSetting<Float> constantSpeed;
    private final BooleanSetting strictReach;
    private final BooleanSetting precisionSearch;
    private final NumberSetting<Float> downPitch;
    private final NumberSetting<Float> yawOffset;
    private boolean godBridgeRightSide;

    public BlockFlyGrimSemiMode() {
        super(ModuleCategory.MOVEMENT, "GrimSemi",
                "Semi-silent fixed-angle scaffold tuned for GrimAntiCheat.");
        this.registerSetting(new ModeSetting("Speed Mode", "Speed mode", 0,
                "None", "Jump", "Constant", "Slow", "Sneak"));
        this.registerSetting(new ModeSetting("Angle Mode", "Server-side scaffold angle.", 0,
                "UnfairPitch", "GodBridge", "Breezily", "Static"));
        this.registerSetting(this.constantSpeed = new NumberSetting<>(
                "Constant Speed", "Constant speed", 0.0F, 0.0F, 6.0F, 0.1F));
        this.registerSetting(this.strictReach = new BooleanSetting(
                "Strict Reach", "Abort the tick if eye->hitVec > 4.5 blocks.", true));
        this.registerSetting(this.precisionSearch = new BooleanSetting(
                "Precision Search", "Try tiny fixed-angle nudges before giving up.", true));
        this.registerSetting(this.downPitch = new NumberSetting<>(
                "Down Pitch", "Fixed scaffold pitch sent to the server.",
                75.7F, 60.0F, 90.0F, 0.1F));
        this.registerSetting(this.yawOffset = new NumberSetting<>(
                "Yaw Offset", "Extra offset added to the fixed server yaw.",
                0.0F, -45.0F, 45.0F, 0.1F));
    }

    @Override
    public void initialize() {
        this.blockFly = (BlockFly) this.access();
    }

    @Override
    public void onEnable() {
        this.previousSlot = mc.player.inventory.currentItem;
        this.stashedCandidate = null;
        this.stashedSpoofedSlot = -1;
        this.stashedPrevSlot = -1;
        this.stashedItemSpoof = "None";
        this.lastPlacedPos = null;
        this.lastPlaceTick = -1;
        this.godBridgeRightSide = false;
        ((BlockFly) this.access()).lastSpoofedSlot = -1;
    }

    @Override
    public void onDisable() {
        if (this.previousSlot != -1
                && this.access().getStringSettingValueByName("ItemSpoof").equals("Switch")) {
            mc.player.inventory.currentItem = this.previousSlot;
            mc.playerController.syncCurrentPlayItem();
        }
        this.previousSlot = -1;

        if (((BlockFly) this.access()).lastSpoofedSlot >= 0) {
            this.sendHeldItemChangeSilently(mc.player.inventory.currentItem);
            mc.playerController.syncCurrentPlayItem();
            ((BlockFly) this.access()).lastSpoofedSlot = -1;
        }

        this.stashedCandidate = null;
        this.stashedSpoofedSlot = -1;
        this.stashedPrevSlot = -1;
        this.stashedItemSpoof = "None";
        this.lastPlacedPos = null;
        this.lastPlaceTick = -1;
        mc.timer.timerSpeed = 1.0F;
    }

    @EventTarget
    public void onSafeWalk(EventSafeWalk event) {
        if (this.isEnabled()
                && mc.player.isOnGround()
                && Client.getInstance().moduleManager.getModuleByClass(SafeWalk.class).isEnabled()) {
            event.setSafe(true);
        }
    }

    @EventTarget
    @LowerPriority
    public void onPacket(EventSendPacket event) {
        if (this.isEnabled() && mc.player != null
                && event.packet instanceof CHeldItemChangePacket
                && ((BlockFly) this.access()).lastSpoofedSlot >= 0) {
            event.cancelled = true;
        }
    }

    @EventTarget
    @HigherPriority
    public void onMove(EventMove event) {
        if (!this.isEnabled() || this.blockFly.getValidItemCount() == 0) {
            return;
        }

        if (this.access().getBooleanValueFromSettingName("No Sprint")) {
            mc.player.setSprinting(false);
        }

        switch (this.getStringSettingValueByName("Speed Mode")) {
            case "Jump":
                if (mc.player.isOnGround() && MovementUtil.isMoving() && !mc.player.isSneaking()) {
                    mc.player.jump();
                    ((Speed) Client.getInstance().moduleManager.getModuleByClass(Speed.class))
                            .callHypixelSpeedMethod();
                    event.setY(mc.player.getMotion().y);
                    event.setX(mc.player.getMotion().x);
                    event.setZ(mc.player.getMotion().z);
                }
                break;
            case "Constant": {
                double speed = this.constantSpeed.currentValue;
                if (!MovementUtil.isMoving()) {
                    speed = 0.0;
                }
                MovementUtil.setMotion(event, speed);
                break;
            }
            case "Slow":
                event.setX(event.getX() * (mc.player.isOnGround() ? 0.75 : 0.93));
                event.setZ(event.getZ() * (mc.player.isOnGround() ? 0.75 : 0.93));
                break;
            case "Sneak":
                event.setX(event.getX() * (mc.player.isOnGround() ? 0.65 : 0.85));
                event.setZ(event.getZ() * (mc.player.isOnGround() ? 0.65 : 0.85));
                break;
            default:
                break;
        }

        this.blockFly.onMove(event);
    }

    @EventTarget
    public void onJump(EventJump event) {
        if (this.isEnabled()
                && this.access().getStringSettingValueByName("Tower Mode").equalsIgnoreCase("Vanilla")
                && (!MovementUtil.isMoving()
                || this.access().getBooleanValueFromSettingName("Tower while moving"))) {
            event.cancelled = true;
        }
    }

    @EventTarget
    @LowerPriority
    public void onUpdate(EventMotion event) {
        if (!this.isEnabled() || this.blockFly.getValidItemCount() == 0) {
            return;
        }

        if (event.isPre()) {
            this.preSelectTarget(event);
        } else {
            this.postSendPlace();
        }
    }

    private void preSelectTarget(EventMotion event) {
        this.hand = Hand.MAIN_HAND;
        this.stashedCandidate = null;

        if (!this.blockFly.canPlaceItem(this.hand)) {
            return;
        }

        this.blockFly.method16736();

        final double feetX = event.getX();
        final double feetY = event.getY();
        final double feetZ = event.getZ();
        final double motionX = mc.player.getMotion().x;
        final double motionZ = mc.player.getMotion().z;

        final Set<BlockPos> allowedTargets = this.selectAllowedTargets(feetX, feetY, feetZ, motionX, motionZ);
        if (!this.hasReplaceableTarget(allowedTargets)) {
            return;
        }

        final PlaceCandidate candidate = this.findPrecisionCandidate(event, allowedTargets);
        if (candidate == null || !this.prepareHeldItemSpoof()) {
            return;
        }

        this.applyServerRotation(event, candidate.yaw, candidate.pitch);
        this.guardSprintDesync(event, candidate.yaw, motionX, motionZ);
        this.stashedCandidate = candidate;
    }

    private void postSendPlace() {
        final PlaceCandidate c = this.stashedCandidate;
        if (c == null) {
            return;
        }

        if (this.lastPlaceTick == mc.player.ticksExisted
                || !BlockUtil.isValidBlockPosition(c.clickPos)
                || !isReplaceable(c.placedPos)) {
            this.restoreSpoofedSlot();
            this.stashedCandidate = null;
            return;
        }

        final BlockRayTraceResult rtr = new BlockRayTraceResult(c.hitVec, c.face, c.clickPos, false);
        final ActionResultType result = mc.playerController.func_217292_a(mc.player, mc.world, this.hand, rtr);

        if (result.isSuccessOrConsume()) {
            if (!this.access().getBooleanValueFromSettingName("NoSwing")) {
                mc.player.swingArm(this.hand);
            } else {
                mc.getConnection().sendPacket(new CAnimateHandPacket(this.hand));
            }

            this.lastPlacedPos = c.placedPos;
            this.lastPlaceTick = mc.player.ticksExisted;
        }

        this.restoreSpoofedSlot();
        this.stashedCandidate = null;
    }

    private boolean prepareHeldItemSpoof() {
        final int currentItem = mc.player.inventory.currentItem;
        this.stashedPrevSlot = currentItem;
        this.stashedItemSpoof = this.access().getStringSettingValueByName("ItemSpoof");
        this.stashedSpoofedSlot = -1;

        if ("None".equals(this.stashedItemSpoof)) {
            return true;
        }

        final int slot = this.blockFly.getValidHotbarItemSlot();
        if (slot == -1) {
            this.stashedPrevSlot = -1;
            this.stashedItemSpoof = "None";
            return false;
        }

        if (slot != currentItem) {
            final boolean restoreLater = "Spoof".equals(this.stashedItemSpoof)
                    || "LiteSpoof".equals(this.stashedItemSpoof);
            this.changeHeldSlotBeforeFlying(slot, restoreLater);
            if (restoreLater) {
                this.stashedSpoofedSlot = slot;
            }
        }

        return true;
    }

    private void restoreSpoofedSlot() {
        if (this.stashedSpoofedSlot != -1
                && this.stashedPrevSlot != -1
                && this.stashedSpoofedSlot != this.stashedPrevSlot
                && ("Spoof".equals(this.stashedItemSpoof)
                || "LiteSpoof".equals(this.stashedItemSpoof))) {
            this.sendHeldItemChangeSilently(this.stashedPrevSlot);
            mc.player.inventory.currentItem = this.stashedPrevSlot;
            mc.playerController.syncCurrentPlayItem();
            ((BlockFly) this.access()).lastSpoofedSlot = -1;
        }
        this.stashedSpoofedSlot = -1;
        this.stashedPrevSlot = -1;
        this.stashedItemSpoof = "None";
    }

    private void changeHeldSlotBeforeFlying(int slot, boolean restoreLater) {
        this.sendHeldItemChangeSilently(slot);
        mc.player.inventory.currentItem = slot;
        ((BlockFly) this.access()).lastSpoofedSlot = slot;
        mc.playerController.syncCurrentPlayItem();

        if (!restoreLater) {
            ((BlockFly) this.access()).lastSpoofedSlot = -1;
        }
    }

    private void sendHeldItemChangeSilently(int slot) {
        mc.getConnection().getNetworkManager().sendNoEventPacket(new CHeldItemChangePacket(slot));
    }

    private Set<BlockPos> selectAllowedTargets(double feetX, double feetY, double feetZ,
                                               double motionX, double motionZ) {
        final Set<BlockPos> targets = new LinkedHashSet<>();
        final BlockPos feet = new BlockPos(feetX, feetY, feetZ);
        this.addScaffoldTargetBand(targets, feet.down());

        final BlockPos predicted = new BlockPos(
                feetX + motionX * 1.15,
                feetY,
                feetZ + motionZ * 1.15
        ).down();
        this.addScaffoldTargetBand(targets, predicted);

        if (MovementUtil.isMoving()) {
            final double yaw = Math.toRadians(MovementUtil.getDirection());
            final BlockPos forward = new BlockPos(
                    feetX - Math.sin(yaw) * 0.7,
                    feetY,
                    feetZ + Math.cos(yaw) * 0.7
            ).down();
            this.addScaffoldTargetBand(targets, forward);
        }

        if (this.lastPlacedPos != null) {
            for (Direction direction : Direction.values()) {
                this.addScaffoldTargetBand(targets, this.lastPlacedPos.offset(direction));
            }
        }

        return targets;
    }

    private void addScaffoldTargetBand(Set<BlockPos> targets, BlockPos anchor) {
        if (anchor == null) {
            return;
        }

        for (int y = 0; y >= -1; y--) {
            for (int x = -1; x <= 1; x++) {
                for (int z = -1; z <= 1; z++) {
                    targets.add(anchor.add(x, y, z));
                }
            }
        }
    }

    private boolean hasReplaceableTarget(Set<BlockPos> targets) {
        for (BlockPos target : targets) {
            if (isReplaceable(target)) {
                return true;
            }
        }

        return false;
    }

    private PlaceCandidate findPrecisionCandidate(EventMotion event, Set<BlockPos> allowedTargets) {
        final float[] yawBases = this.getFixedYawCandidates();
        final float basePitch = this.getFixedPitch();
        final float[] yawNudges = this.precisionSearch.getCurrentValue() ? YAW_NUDGE : NO_NUDGE;
        final float[] pitchNudges = this.precisionSearch.getCurrentValue() ? PITCH_NUDGE : NO_NUDGE;

        for (float baseYaw : yawBases) {
            for (float yawNudge : yawNudges) {
                for (float pitchNudge : pitchNudges) {
                    final float yaw = MathHelper.wrapDegrees(baseYaw + yawNudge);
                    final float pitch = MathHelper.clamp(basePitch + pitchNudge, 60.0F, 89.9F);
                    final PlaceCandidate candidate = this.findRaycastCandidate(event, yaw, pitch, allowedTargets);
                    if (candidate != null) {
                        this.rememberGodBridgeSide(baseYaw);
                        return candidate;
                    }
                }
            }
        }

        return null;
    }

    private PlaceCandidate findRaycastCandidate(EventMotion event, float yaw, float pitch, Set<BlockPos> allowedTargets) {
        final BlockRayTraceResult rayTrace = this.rayTraceFromEvent(event, yaw, pitch,
                mc.playerController.getBlockReachDistance());
        if (rayTrace == null || rayTrace.getType() != RayTraceResult.Type.BLOCK) {
            return null;
        }

        final BlockPos clickPos = rayTrace.getPos();
        final Direction face = rayTrace.getFace();
        // UnfairPitch demands the new block extend the bridge along the SAME plane as
        // the player's feet (placedPos.y == clickPos.y), so the raycast must land on a
        // horizontal side face. A UP-face hit would place the new block on top of the
        // foot block (one block above the bridge), producing a "stacked tower" instead
        // of an extended bridge — which is geometrically wrong for this mode and would
        // also be flagged by Grim's blockplace.PositionPlace check.
        if ("UnfairPitch".equals(this.getStringSettingValueByName("Angle Mode"))
                && face == Direction.UP) {
            return null;
        }
        final BlockPos placedPos = clickPos.offset(face);
        if (!this.isAllowedTarget(placedPos, allowedTargets)
                || !BlockUtil.isValidBlockPosition(clickPos)
                || !isReplaceable(placedPos)) {
            return null;
        }

        if (this.strictReach.getCurrentValue()) {
            final double eyeX = event.getX();
            final double eyeY = event.getY() + mc.player.getEyeHeight();
            final double eyeZ = event.getZ();
            if (rayTrace.getHitVec().squareDistanceTo(eyeX, eyeY, eyeZ) > GRIM_REACH_SQ) {
                return null;
            }
        }

        return new PlaceCandidate(clickPos, face, rayTrace.getHitVec(), placedPos, yaw, pitch);
    }

    private BlockRayTraceResult rayTraceFromEvent(EventMotion event, float yaw, float pitch, double reach) {
        final Vector3d eyes = new Vector3d(
                event.getX(),
                event.getY() + mc.player.getEyeHeight(),
                event.getZ()
        );
        final float yawRad = (float) Math.toRadians(yaw);
        final float pitchRad = (float) Math.toRadians(pitch);
        final float x = -MathHelper.sin(yawRad) * MathHelper.cos(pitchRad);
        final float y = -MathHelper.sin(pitchRad);
        final float z = MathHelper.cos(yawRad) * MathHelper.cos(pitchRad);
        final Vector3d end = eyes.add(x * reach, y * reach, z * reach);

        return mc.world.rayTraceBlocks(new RayTraceContext(
                eyes,
                end,
                RayTraceContext.BlockMode.OUTLINE,
                RayTraceContext.FluidMode.NONE,
                mc.getRenderViewEntity()
        ));
    }

    private boolean isAllowedTarget(BlockPos placedPos, Set<BlockPos> allowedTargets) {
        return allowedTargets.contains(placedPos);
    }

    private float[] getFixedYawCandidates() {
        final float movingYaw = this.getRoundedPlacementYaw();
        final float offset = this.yawOffset.currentValue;
        switch (this.getStringSettingValueByName("Angle Mode")) {
            case "UnfairPitch": {
                // "UnfairPitch" / 神桥(截图所示方法): the body faces the direction of travel
                // (head pointed forward, NOT reversed) and the camera is tilted almost
                // straight down. Geometrically this means yaw == forward movement yaw,
                // and we deliberately do NOT round to 45° increments — the player walks
                // in a single cardinal direction along a straight bridge, not diagonally,
                // so snapping to a 45° grid would actually misalign the placement face.
                // The combination of (forward yaw + extreme pitch + raycast that rejects
                // the UP face, see findRaycastCandidate) makes the raycast graze the back
                // edge of the foot block and land on its side face, placing the new block
                // straight behind / below the player along the same bridge axis. This is
                // legal in vanilla (a player CAN reach that pitch) but the timing window
                // is humanly very tight — hence "unfair pitch".
                final float forwardYaw = MovementUtil.isMoving()
                        ? MovementUtil.getDirection()
                        : mc.player.rotationYaw;
                return new float[]{MathHelper.wrapDegrees(forwardYaw + offset)};
            }
            case "Breezily":
                return new float[]{MathHelper.wrapDegrees(movingYaw + offset)};
            case "Static":
                return new float[]{MathHelper.wrapDegrees(mc.player.rotationYaw + 180.0F + offset)};
            case "GodBridge":
            default:
                if (Math.abs(movingYaw % 90.0F) < 0.001F) {
                    final float first = movingYaw + (this.godBridgeRightSide ? 45.0F : -45.0F) + offset;
                    final float second = movingYaw + (this.godBridgeRightSide ? -45.0F : 45.0F) + offset;
                    return new float[]{MathHelper.wrapDegrees(first), MathHelper.wrapDegrees(second)};
                }

                return new float[]{MathHelper.wrapDegrees(movingYaw + offset)};
        }
    }

    private float getFixedPitch() {
        final String mode = this.getStringSettingValueByName("Angle Mode");
        if ("UnfairPitch".equals(mode)) {
            // UnfairPitch needs the camera tilted almost straight down so the raycast
            // line, originating at the eye (which is offset forward along yaw at this
            // pitch), grazes past the foot block's top edge and lands on its forward
            // side face rather than its top face. We force a ~89° floor (very near
            // vertical): below that the raycast tends to land on the foot block's UP
            // face only, which we reject in findRaycastCandidate. The Down Pitch slider
            // is still honoured if the user dials it higher (e.g. 89.5°) — it just
            // can't go below 89° in this mode. PrecisionSearch will then nudge ±0.1°
            // to find a hit that lands on a side face.
            return MathHelper.clamp(Math.max(this.downPitch.currentValue, 89.0F), 89.0F, 89.9F);
        }
        if ("Breezily".equals(mode) && Math.abs(this.getRoundedPlacementYaw() % 90.0F) < 0.001F) {
            return Math.max(this.downPitch.currentValue, 80.0F);
        }

        return this.downPitch.currentValue;
    }

    private float getRoundedPlacementYaw() {
        final float yaw = MovementUtil.isMoving()
                ? MovementUtil.getDirection() + 180.0F
                : mc.player.rotationYaw + 180.0F;
        return Math.round(yaw / 45.0F) * 45.0F;
    }

    private void rememberGodBridgeSide(float selectedBaseYaw) {
        if (!"GodBridge".equals(this.getStringSettingValueByName("Angle Mode"))) {
            return;
        }

        final float movingYaw = this.getRoundedPlacementYaw();
        final float diff = MathHelper.wrapDegrees(selectedBaseYaw - movingYaw - this.yawOffset.currentValue);
        if (Math.abs(Math.abs(diff) - 45.0F) < 1.0F) {
            this.godBridgeRightSide = diff > 0.0F;
        }
    }

    private void applyServerRotation(EventMotion event, float yaw, float pitch) {
        event.setYaw(yaw);
        event.setPitch(pitch);
    }

    private void guardSprintDesync(EventMotion event, float yaw, double motionX, double motionZ) {
        final double motionSq = motionX * motionX + motionZ * motionZ;
        if (motionSq <= 1.0E-4 || !mc.player.isSprinting()) {
            return;
        }

        final float motionYaw = (float) (Math.toDegrees(Math.atan2(motionZ, motionX)) - 90.0);
        final float diff = Math.abs(MathHelper.wrapDegrees(yaw - motionYaw));
        if (diff > 50.0F) {
            mc.player.setSprinting(false);
            event.attackPost(() -> mc.player.setSprinting(true));
        }
    }

    private static boolean isReplaceable(BlockPos pos) {
        if (pos == null) {
            return false;
        }
        final BlockState state = mc.world.getBlockState(pos);
        return state.getMaterial().isReplaceable();
    }

    private static final class PlaceCandidate {
        final BlockPos clickPos;
        final Direction face;
        final Vector3d hitVec;
        final BlockPos placedPos;
        final float yaw;
        final float pitch;

        PlaceCandidate(BlockPos clickPos, Direction face, Vector3d hitVec,
                       BlockPos placedPos, float yaw, float pitch) {
            this.clickPos = clickPos;
            this.face = face;
            this.hitVec = hitVec;
            this.placedPos = placedPos;
            this.yaw = yaw;
            this.pitch = pitch;
        }
    }
}
