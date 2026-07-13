package com.mentalfrostbyte.jello.module.impl.movement.blockfly;

import com.mentalfrostbyte.jello.event.impl.game.action.EventClick;
import com.mentalfrostbyte.jello.event.impl.game.network.EventSendPacket;
import com.mentalfrostbyte.jello.event.impl.game.world.EventLoadWorld;
import com.mentalfrostbyte.jello.event.impl.player.EventRunTicks;
import com.mentalfrostbyte.jello.event.impl.player.action.EventPlace;
import com.mentalfrostbyte.jello.event.impl.player.action.EventUpdatePlayerActionState;
import com.mentalfrostbyte.jello.event.impl.player.movement.EventMove;
import com.mentalfrostbyte.jello.managers.RotationManager;
import com.mentalfrostbyte.jello.module.Module;
import com.mentalfrostbyte.jello.module.data.ModuleCategory;
import com.mentalfrostbyte.jello.module.impl.movement.BlockFly;
import com.mentalfrostbyte.jello.module.settings.impl.BooleanSetting;
import com.mentalfrostbyte.jello.module.settings.impl.NumberSetting;
import com.mentalfrostbyte.jello.util.game.player.InvManagerUtil;
import com.mentalfrostbyte.jello.util.game.player.rotation.util.RotationUtils;
import net.minecraft.block.BlockState;
import net.minecraft.block.ContainerBlock;
import net.minecraft.block.CraftingTableBlock;
import net.minecraft.network.play.client.CAnimateHandPacket;
import net.minecraft.network.play.client.CHeldItemChangePacket;
import net.minecraft.util.ActionResultType;
import net.minecraft.util.Direction;
import net.minecraft.util.Hand;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.BlockRayTraceResult;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RayTraceContext;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.shapes.VoxelShape;
import net.minecraft.util.math.vector.Vector3d;
import team.sdhq.eventBus.annotations.EventTarget;
import team.sdhq.eventBus.annotations.priority.LowerPriority;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Silent-rotation scaffold intended to behave like manual edge bridging.
 *
 * <p>The mode predicts an edge with downward block raycasts, but uses a swept
 * foot collision probe as the final safety deadline.  A configured Eagle
 * delay is sampled once per edge episode; it may be negative (early) or very
 * large, while the collision deadline still guarantees that sneak is applied
 * before the projected support loss.  Sneak ownership is deliberately limited
 * to {@link net.minecraft.util.MovementInput#sneaking} for the current tick, so
 * toggle-sneak and the physical Shift key are never latched by this module.</p>
 */
public final class BlockFlyLegitMode extends Module {
    private static final long NANOS_PER_MILLI = 1_000_000L;
    private static final double EDGE_SCAN_STEP = 0.04D;
    private static final double SAFETY_MARGIN_MS = 55.0D;
    private static final long NEVER = Long.MAX_VALUE;
    private final BooleanSetting eagle;
    private final NumberSetting<Float> eagleMinDelay;
    private final NumberSetting<Float> eagleMaxDelay;
    private final NumberSetting<Float> eagleLookahead;
    private final NumberSetting<Float> horizontalSpeed;
    private final NumberSetting<Float> verticalSpeed;
    private final NumberSetting<Float> edgeAim;
    private final NumberSetting<Float> aimNoise;
    private final BooleanSetting zitter;
    private final NumberSetting<Float> zitterAmount;
    private final NumberSetting<Float> zitterMinDelay;
    private final NumberSetting<Float> zitterMaxDelay;
    private final NumberSetting<Float> placeMinDelay;
    private final NumberSetting<Float> placeMaxDelay;

    private Object sessionPlayer;
    private Object sessionWorld;
    private int previousSlot = -1;
    private boolean switchSlotOwned;
    private boolean liteSpoofOwned;
    private boolean internalLiteSpoofSlotChange;

    /*
     * The mode sends a silent placement yaw, so physical WASD must not be
     * reinterpreted against that server-side rotation.  We lock the world-space
     * bridge direction once an edge/target has been acquired and only unlock
     * it for an explicit opposite physical input or a lifecycle reset.
     */
    private boolean bridgeDirectionLocked;
    private double bridgeDirectionX;
    private double bridgeDirectionZ;
    private float inputReferenceYaw;

    private boolean forceSneak;
    private boolean edgeEpisodeActive;
    private double approachX;
    private double approachZ;
    private long eagleTriggerNanos = NEVER;
    private long eagleReleaseNanos = NEVER;

    private PlacementTarget target;
    private boolean serverAimReady;
    private boolean placedThisTick;
    private long nextPlaceNanos;

    private PlacementKey noiseKey;
    private double noiseFromLateral;
    private double noiseFromVertical;
    private double noiseToLateral;
    private double noiseToVertical;
    private long noiseStartNanos;
    private long noiseEndNanos;

    private PlacementKey zitterKey;
    private double zitterFromYaw;
    private double zitterFromPitch;
    private double zitterToYaw;
    private double zitterToPitch;
    private int zitterSign = 1;
    private long zitterStartNanos;
    private long zitterEndNanos;

    public BlockFlyLegitMode() {
        super(ModuleCategory.MOVEMENT, "Legit",
                "Silent raycast bridging with predictive Eagle and humanized aim.");
        this.registerSetting(this.eagle = new BooleanSetting("Eagle",
                "Sneak before walking beyond the current support block.", true));
        this.registerSetting(this.eagleMinDelay = new NumberSetting<>("Eagle Min Delay",
                "Minimum reaction delay in ms; negative values sneak early.", 25.0F,
                -1000.0F, 5000.0F, 5.0F));
        this.registerSetting(this.eagleMaxDelay = new NumberSetting<>("Eagle Max Delay",
                "Maximum reaction delay in ms; safety always wins before a fall.", 90.0F,
                -1000.0F, 5000.0F, 5.0F));
        this.registerSetting(this.eagleLookahead = new NumberSetting<>("Eagle Lookahead",
                "Forward edge-raycast distance in blocks.", 1.7F, 0.5F, 4.0F, 0.1F));
        this.registerSetting(this.horizontalSpeed = new NumberSetting<>("Rotation HSpeed",
                "Maximum silent yaw movement per tick.", 18.0F, 1.0F, 90.0F, 1.0F));
        this.registerSetting(this.verticalSpeed = new NumberSetting<>("Rotation VSpeed",
                "Maximum silent pitch movement per tick.", 14.0F, 1.0F, 90.0F, 1.0F));
        this.registerSetting(this.edgeAim = new NumberSetting<>("Edge Aim",
                "Vertical point on a horizontal block face.", 0.82F, 0.55F, 0.94F, 0.01F));
        this.registerSetting(this.aimNoise = new NumberSetting<>("Aim Noise",
                "Slow face-tangent aim drift in blocks.", 0.04F, 0.0F, 0.18F, 0.005F));
        this.registerSetting(this.zitter = new BooleanSetting("Zitter",
                "Add bounded low-frequency aim tremor without leaving the clicked face.", true));
        this.registerSetting(this.zitterAmount = new NumberSetting<>("Zitter Amount",
                "Maximum yaw tremor in degrees.", 0.35F, 0.0F, 2.5F, 0.05F));
        this.registerSetting(this.zitterMinDelay = new NumberSetting<>("Zitter Min Delay",
                "Minimum time between tremor direction changes in ms.", 65.0F,
                20.0F, 1000.0F, 5.0F));
        this.registerSetting(this.zitterMaxDelay = new NumberSetting<>("Zitter Max Delay",
                "Maximum time between tremor direction changes in ms.", 145.0F,
                20.0F, 1000.0F, 5.0F));
        this.registerSetting(this.placeMinDelay = new NumberSetting<>("Place Min Delay",
                "Minimum delay between legitimate placement attempts in ms.", 65.0F,
                0.0F, 1000.0F, 5.0F));
        this.registerSetting(this.placeMaxDelay = new NumberSetting<>("Place Max Delay",
                "Maximum delay between legitimate placement attempts in ms.", 125.0F,
                0.0F, 1000.0F, 5.0F));
    }

    @Override
    public void onEnable() {
        this.clearRuntimeState();
        if (mc.player != null && mc.world != null) {
            this.beginSession();
        }
    }

    @Override
    public void onDisable() {
        BlockFly parent = this.parent();
        boolean stillSelected = parent.getModWithTypeSetToName() == this;
        if (stillSelected) {
            if (mc.player != null && this.switchSlotOwned && this.previousSlot >= 0) {
                mc.player.inventory.currentItem = MathHelper.clamp(this.previousSlot, 0, 8);
            }
            this.releaseOwnedLiteSpoof();
        }
        this.clearRuntimeState();
        this.sessionPlayer = null;
        this.sessionWorld = null;
        this.previousSlot = -1;
        this.switchSlotOwned = false;
        this.liteSpoofOwned = false;
        this.internalLiteSpoofSlotChange = false;
    }

    @EventTarget
    public void onLoadWorld(EventLoadWorld event) {
        if (!this.isEnabled()) {
            return;
        }
        if (this.liteSpoofOwned) {
            this.parent().lastSpoofedSlot = -1;
        }
        this.clearRuntimeState();
        this.sessionPlayer = null;
        this.sessionWorld = null;
        this.previousSlot = -1;
        this.switchSlotOwned = false;
        this.liteSpoofOwned = false;
        this.internalLiteSpoofSlotChange = false;
    }

    @EventTarget
    @LowerPriority
    public void onSendPacket(EventSendPacket event) {
        if (this.isEnabled()
                && event.packet instanceof CHeldItemChangePacket
                && this.liteSpoofOwned
                && "LiteSpoof".equals(this.itemSpoof())
                && !this.internalLiteSpoofSlotChange
                && this.parent().lastSpoofedSlot >= 0) {
            event.cancelled = true;
        }
    }

    @EventTarget
    public void onRunTicks(EventRunTicks event) {
        if (!this.isEnabled() || !event.isPre()) {
            return;
        }

        this.placedThisTick = false;
        if (mc.player != this.sessionPlayer || mc.world != this.sessionWorld) {
            this.beginSession();
        }
        this.releaseStaleSwitchSlot();
        this.releaseStaleLiteSpoof();
        if (!this.canOperate()) {
            this.clearTransientState();
            return;
        }

        long now = System.nanoTime();
        MotionIntent intent = this.getMotionIntent();
        this.updateEagle(intent, now);

        PlacementTarget oldTarget = this.target;
        PlacementTarget newTarget = this.hasPlaceableBlocks()
                ? this.findPlacementTarget(intent)
                : null;
        if (newTarget == null) {
            this.clearAimState();
            return;
        }
        this.lockBridgeDirection(intent);

        PlacementKey newKey = newTarget.key();
        boolean sameTarget = oldTarget != null && oldTarget.key().equals(newKey);
        BlockRayTraceResult previousRotationRay = this.serverRayCast();
        this.serverAimReady = sameTarget && this.isExactHit(previousRotationRay, newTarget);
        if (!sameTarget) {
            this.nextPlaceNanos = safeAddNanos(now,
                    this.randomDelayNanos(this.placeMinDelay, this.placeMaxDelay, 0, 30_000));
        }

        AimOffset noise = this.updateAimNoise(newKey, now);
        Vector3d noisyPoint = this.getSafeNoisyAimPoint(newTarget, noise);
        newTarget = newTarget.withAim(noisyPoint);
        this.target = newTarget;
        this.applySilentRotation(newTarget, now);
    }

    @EventTarget
    public void onUpdatePlayerActionState(EventUpdatePlayerActionState event) {
        if (this.isEnabled()
                && this.forceSneak
                && this.canForceSneak()
                && mc.player != null
                && mc.player == this.sessionPlayer
                && mc.world == this.sessionWorld
                && mc.player.movementInput != null) {
            mc.player.movementInput.sneaking = true;
        }
    }

    @EventTarget
    public void onMove(EventMove event) {
        if (!this.isEnabled() || mc.player == null) {
            return;
        }
        if (this.access().getBooleanValueFromSettingName("No Sprint")) {
            mc.player.setSprinting(false);
        } else if (!this.access().getBooleanValueFromSettingName("UesGameSprint")) {
            mc.player.setSprinting(true);
        }
        this.parent().onMove(event);
    }

    @EventTarget
    public void onPlace(EventPlace event) {
        if (this.isEnabled()) {
            this.tryPlace();
        }
    }

    @EventTarget
    public void onClick(EventClick event) {
        if (!this.isEnabled() || event.getButton() != EventClick.Button.RIGHT) {
            return;
        }
        if (this.placedThisTick) {
            event.cancelled = true;
            return;
        }
        if (this.target == null || !this.canOperate()) {
            return;
        }
        BlockRayTraceResult ray = this.serverRayCast();
        if (!this.isExactHit(ray, this.target)) {
            return;
        }
        if (this.tryPlace()) {
            event.cancelled = true;
        }
    }

    private void beginSession() {
        this.clearRuntimeState();
        this.switchSlotOwned = false;
        this.liteSpoofOwned = false;
        this.internalLiteSpoofSlotChange = false;
        this.sessionPlayer = mc.player;
        this.sessionWorld = mc.world;
        this.previousSlot = mc.player == null ? -1 : mc.player.inventory.currentItem;
        BlockFly parent = this.parent();
        parent.lastSpoofedSlot = -1;
        if (mc.player != null && "Switch".equals(this.itemSpoof())) {
            this.switchSlotOwned = true;
            this.switchToValidHotbarItem(parent);
        }
    }

    private boolean canOperate() {
        return mc.player != null
                && mc.world != null
                && mc.playerController != null
                && mc.getConnection() != null
                && mc.currentScreen == null
                && mc.isGameFocused()
                && mc.mouseHelper != null
                && mc.mouseHelper.isMouseGrabbed()
                && mc.player.isAlive()
                && !mc.player.isSpectator()
                && !mc.player.isPassenger()
                && !mc.player.abilities.isFlying
                && !mc.player.isElytraFlying()
                && !mc.player.isInWater()
                && !mc.player.isInLava()
                && mc.player.isOnGround()
                && mc.player.fallDistance <= 0.0F
                && !mc.gameSettings.keyBindJump.isKeyDown();
    }

    private boolean canForceSneak() {
        return this.eagle.getCurrentValue() && this.canOperate();
    }

    private void switchToValidHotbarItem(BlockFly parent) {
        boolean wantsLiteSpoof = "LiteSpoof".equals(this.itemSpoof());
        this.internalLiteSpoofSlotChange = wantsLiteSpoof;
        try {
            parent.switchToValidHotbarItem();
        } finally {
            this.internalLiteSpoofSlotChange = false;
        }
        if (wantsLiteSpoof && parent.lastSpoofedSlot >= 0) {
            this.liteSpoofOwned = true;
        }
    }

    private void releaseStaleLiteSpoof() {
        if (this.liteSpoofOwned && !"LiteSpoof".equals(this.itemSpoof())) {
            this.releaseOwnedLiteSpoof();
        }
    }

    private void releaseStaleSwitchSlot() {
        if (!this.switchSlotOwned || "Switch".equals(this.itemSpoof())) {
            return;
        }
        if (mc.player != null && this.previousSlot >= 0) {
            mc.player.inventory.currentItem = MathHelper.clamp(this.previousSlot, 0, 8);
        }
        this.switchSlotOwned = false;
    }

    private void releaseOwnedLiteSpoof() {
        if (!this.liteSpoofOwned) {
            return;
        }
        BlockFly parent = this.parent();
        parent.lastSpoofedSlot = -1;
        if (mc.player != null && mc.getConnection() != null) {
            mc.getConnection().sendPacket(new CHeldItemChangePacket(mc.player.inventory.currentItem));
        }
        this.liteSpoofOwned = false;
    }

    private void clearRuntimeState() {
        this.clearTransientState();
        this.nextPlaceNanos = 0L;
        this.noiseKey = null;
        this.noiseStartNanos = 0L;
        this.noiseEndNanos = 0L;
        this.zitterKey = null;
        this.zitterStartNanos = 0L;
        this.zitterEndNanos = 0L;
        this.resetBridgeDirection();
    }

    private void clearTransientState() {
        this.resetEagle();
        this.clearAimState();
        this.resetBridgeDirection();
        this.placedThisTick = false;
    }

    private void clearAimState() {
        this.target = null;
        this.serverAimReady = false;
        this.noiseKey = null;
        this.zitterKey = null;
    }

    private void resetEagle() {
        this.forceSneak = false;
        this.edgeEpisodeActive = false;
        this.approachX = 0.0D;
        this.approachZ = 0.0D;
        this.eagleTriggerNanos = NEVER;
        this.eagleReleaseNanos = NEVER;
    }

    private void resetBridgeDirection() {
        this.bridgeDirectionLocked = false;
        this.bridgeDirectionX = 0.0D;
        this.bridgeDirectionZ = 0.0D;
        this.inputReferenceYaw = 0.0F;
    }

    private MotionIntent getMotionIntent() {
        double forward = 0.0D;
        double strafe = 0.0D;
        if (mc.gameSettings.keyBindForward.isKeyDown()) forward += 1.0D;
        if (mc.gameSettings.keyBindBack.isKeyDown()) forward -= 1.0D;
        if (mc.gameSettings.keyBindLeft.isKeyDown()) strafe += 1.0D;
        if (mc.gameSettings.keyBindRight.isKeyDown()) strafe -= 1.0D;

        if (forward == 0.0D && strafe == 0.0D && mc.player.movementInput != null) {
            forward = mc.player.movementInput.moveForward;
            strafe = mc.player.movementInput.moveStrafe;
        }

        if (!this.bridgeDirectionLocked) {
            this.inputReferenceYaw = mc.player.rotationYaw;
        }
        double yaw = Math.toRadians(this.bridgeDirectionLocked
                ? this.inputReferenceYaw
                : mc.player.rotationYaw);
        double inputX = forward * -Math.sin(yaw) + strafe * Math.cos(yaw);
        double inputZ = forward * Math.cos(yaw) + strafe * Math.sin(yaw);
        double inputLength = Math.hypot(inputX, inputZ);

        Vector3d motion = mc.player.getMotion();
        double motionSpeed = Math.hypot(motion.x, motion.z);
        if (inputLength > 1.0E-4D) {
            inputX /= inputLength;
            inputZ /= inputLength;
            if (this.bridgeDirectionLocked) {
                double explicitDirection = inputX * this.bridgeDirectionX
                        + inputZ * this.bridgeDirectionZ;
                if (explicitDirection < -0.6D) {
                    this.resetBridgeDirection();
                    return new MotionIntent(inputX, inputZ, Math.max(motionSpeed, 0.055D));
                }
                return new MotionIntent(this.bridgeDirectionX, this.bridgeDirectionZ,
                        Math.max(motionSpeed, 0.055D));
            }
            if (motionSpeed > 0.02D) {
                double motionX = motion.x / motionSpeed;
                double motionZ = motion.z / motionSpeed;
                if (inputX * motionX + inputZ * motionZ > 0.25D) {
                    inputX = inputX * 0.7D + motionX * 0.3D;
                    inputZ = inputZ * 0.7D + motionZ * 0.3D;
                    double blendedLength = Math.hypot(inputX, inputZ);
                    inputX /= blendedLength;
                    inputZ /= blendedLength;
                }
            }
            return new MotionIntent(inputX, inputZ, Math.max(motionSpeed, 0.055D));
        }

        if (motionSpeed > 0.015D) {
            if (this.bridgeDirectionLocked) {
                return new MotionIntent(this.bridgeDirectionX, this.bridgeDirectionZ, motionSpeed);
            }
            return new MotionIntent(motion.x / motionSpeed, motion.z / motionSpeed, motionSpeed);
        }
        if (this.bridgeDirectionLocked) {
            return new MotionIntent(this.bridgeDirectionX, this.bridgeDirectionZ, 0.035D);
        }
        return null;
    }

    private void lockBridgeDirection(MotionIntent intent) {
        if (intent == null || this.bridgeDirectionLocked) {
            return;
        }
        double length = Math.hypot(intent.x, intent.z);
        if (length <= 1.0E-4D) {
            return;
        }
        this.bridgeDirectionLocked = true;
        this.bridgeDirectionX = intent.x / length;
        this.bridgeDirectionZ = intent.z / length;
    }

    private void updateEagle(MotionIntent intent, long now) {
        if (!this.eagle.getCurrentValue()) {
            this.resetEagle();
            return;
        }
        if (intent == null) {
            if (!this.forceSneak) {
                this.resetEagle();
            }
            return;
        }

        if (this.edgeEpisodeActive) {
            double dot = intent.x * this.approachX + intent.z * this.approachZ;
            if (dot < 0.6D) {
                this.resetEagle();
            }
        }
        if (!this.edgeEpisodeActive) {
            this.approachX = intent.x;
            this.approachZ = intent.z;
        }

        double effectiveMinimumDelay = this.effectiveMinimumDelay(
                this.eagleMinDelay, this.eagleMaxDelay, -30_000.0F, 30_000.0F);
        double earlyLeadMs = Math.max(0.0D, -effectiveMinimumDelay);
        double lookahead = this.safeNumber(this.eagleLookahead, 1.7F, 0.5F, 4.0F);
        lookahead = MathHelper.clamp(Math.max(lookahead,
                intent.speed * ((earlyLeadMs + 250.0D) / 50.0D)), 0.5D, 4.0D);

        double rayGap = this.firstRayGapDistance(intent.x, intent.z, lookahead);
        double unsafeDistance = this.firstUnsupportedFootDistance(
                intent.x, intent.z, Math.min(4.5D, lookahead + 0.55D));
        boolean warning = Double.isFinite(rayGap) || Double.isFinite(unsafeDistance);

        if (!warning) {
            this.handleSafeEagleRelease(intent, now, Double.POSITIVE_INFINITY);
            return;
        }

        if (!Double.isFinite(unsafeDistance)) {
            unsafeDistance = Math.min(4.5D, rayGap + mc.player.getWidth() * 0.65D);
        }
        double speed = Math.max(0.02D, intent.speed);
        double unsafeMs = Math.max(0.0D, unsafeDistance / speed * 50.0D);
        long hardDeadline = safeAddNanos(now,
                millisToNanos(Math.max(0.0D, unsafeMs - SAFETY_MARGIN_MS)));

        if (!this.edgeEpisodeActive) {
            this.edgeEpisodeActive = true;
            this.approachX = intent.x;
            this.approachZ = intent.z;
            this.lockBridgeDirection(intent);
            double sampledDelay = this.randomDelayMillis(
                    this.eagleMinDelay, this.eagleMaxDelay, -30_000, 30_000);
            double scheduledMs = sampledDelay < 0.0D
                    ? unsafeMs + sampledDelay
                    : sampledDelay;
            long sampledDeadline = safeAddNanos(now,
                    millisToNanos(Math.max(0.0D, scheduledMs)));
            this.eagleTriggerNanos = Math.min(sampledDeadline, hardDeadline);
        } else {
            this.eagleTriggerNanos = Math.min(this.eagleTriggerNanos, hardDeadline);
        }

        double immediateDistance = Math.max(0.08D, speed * 1.5D + 0.02D);
        if (unsafeDistance <= immediateDistance || now >= this.eagleTriggerNanos) {
            this.forceSneak = true;
            this.eagleReleaseNanos = NEVER;
        }

        if (this.forceSneak) {
            boolean stable = unsafeDistance > Math.max(0.48D, speed * 4.0D)
                    && this.hasFootSupportAt(0.0D, 0.0D)
                    && this.hasFootSupportAt(intent.x * Math.max(0.12D, speed * 1.5D),
                    intent.z * Math.max(0.12D, speed * 1.5D));
            if (stable) {
                if (this.eagleReleaseNanos == NEVER) {
                    this.eagleReleaseNanos = safeAddNanos(now,
                            millisToNanos(ThreadLocalRandom.current().nextLong(60L, 121L)));
                } else if (now >= this.eagleReleaseNanos) {
                    this.resetEagle();
                }
            } else {
                this.eagleReleaseNanos = NEVER;
            }
        }
    }

    private void handleSafeEagleRelease(MotionIntent intent, long now, double unsafeDistance) {
        if (!this.forceSneak) {
            this.resetEagle();
            return;
        }
        boolean stable = unsafeDistance > 0.48D
                && this.hasFootSupportAt(0.0D, 0.0D)
                && this.hasFootSupportAt(intent.x * 0.12D, intent.z * 0.12D);
        if (!stable) {
            this.eagleReleaseNanos = NEVER;
            return;
        }
        if (this.eagleReleaseNanos == NEVER) {
            this.eagleReleaseNanos = safeAddNanos(now,
                    millisToNanos(ThreadLocalRandom.current().nextLong(60L, 121L)));
        } else if (now >= this.eagleReleaseNanos) {
            this.resetEagle();
        }
    }

    private double firstRayGapDistance(double directionX, double directionZ, double distance) {
        AxisAlignedBB box = mc.player.getBoundingBox();
        double halfWidth = Math.max(0.2D, mc.player.getWidth() * 0.5D - 0.015D);
        double perpendicularX = -directionZ;
        double perpendicularZ = directionX;
        for (double step = 0.0D; step <= distance + 1.0E-6D; step += EDGE_SCAN_STEP) {
            double leadingX = (box.minX + box.maxX) * 0.5D + directionX * (halfWidth + step);
            double leadingZ = (box.minZ + box.maxZ) * 0.5D + directionZ * (halfWidth + step);
            double lateral = halfWidth * 0.72D;
            if (!this.hasDownwardRaySupport(leadingX, leadingZ)
                    || !this.hasDownwardRaySupport(
                    leadingX + perpendicularX * lateral,
                    leadingZ + perpendicularZ * lateral)
                    || !this.hasDownwardRaySupport(
                    leadingX - perpendicularX * lateral,
                    leadingZ - perpendicularZ * lateral)) {
                return step;
            }
        }
        return Double.POSITIVE_INFINITY;
    }

    private boolean hasDownwardRaySupport(double x, double z) {
        return this.downwardSupportRay(x, z) != null;
    }

    private BlockRayTraceResult downwardSupportRay(double x, double z) {
        double feetY = mc.player.getBoundingBox().minY;
        Vector3d start = new Vector3d(x, feetY + 0.08D, z);
        Vector3d end = new Vector3d(x, feetY - 0.82D, z);
        BlockRayTraceResult ray = mc.world.rayTraceBlocks(new RayTraceContext(
                start, end, RayTraceContext.BlockMode.COLLIDER,
                RayTraceContext.FluidMode.NONE, mc.player));
        if (ray.getType() == RayTraceResult.Type.BLOCK
                && ray.getHitVec().y <= feetY + 0.08D
                && feetY - ray.getHitVec().y <= 0.72D) {
            return ray;
        }
        return null;
    }

    private double firstUnsupportedFootDistance(double directionX, double directionZ, double distance) {
        for (double step = 0.0D; step <= distance + 1.0E-6D; step += EDGE_SCAN_STEP) {
            if (!this.hasFootSupportAt(directionX * step, directionZ * step)) {
                return step;
            }
        }
        return Double.POSITIVE_INFINITY;
    }

    private boolean hasFootSupportAt(double offsetX, double offsetZ) {
        AxisAlignedBB box = mc.player.getBoundingBox();
        double inset = Math.min(0.03D, Math.max(0.005D, mc.player.getWidth() * 0.08D));
        AxisAlignedBB probe = new AxisAlignedBB(
                box.minX + inset + offsetX,
                box.minY - 0.10D,
                box.minZ + inset + offsetZ,
                box.maxX - inset + offsetX,
                box.minY + 0.01D,
                box.maxZ - inset + offsetZ);
        return mc.world.getCollisionShapes(mc.player, probe).findAny().isPresent();
    }

    private PlacementTarget findPlacementTarget(MotionIntent intent) {
        if (intent == null) {
            return null;
        }
        int supportY = this.getSupportBlockY();
        double scanDistance = this.safeNumber(this.eagleLookahead, 1.7F, 0.5F, 4.0F) + 0.9D;
        double perpendicularX = -intent.z;
        double perpendicularZ = intent.x;
        double[] lateralOffsets = {0.0D, 0.24D, -0.24D};
        Set<BlockPos> candidates = new LinkedHashSet<>();
        for (double distance = 0.28D; distance <= scanDistance; distance += 0.10D) {
            for (double lateral : lateralOffsets) {
                double x = mc.player.getPosX() + intent.x * distance + perpendicularX * lateral;
                double z = mc.player.getPosZ() + intent.z * distance + perpendicularZ * lateral;
                candidates.add(new BlockPos(MathHelper.floor(x), supportY, MathHelper.floor(z)));
            }
        }

        PlacementTarget closest = null;
        for (BlockPos placePos : candidates) {
            if (!this.isReplaceable(placePos)) {
                continue;
            }
            PlacementTarget candidate = this.findAnchor(placePos, intent.x, intent.z);
            if (candidate != null && this.isCloserPlacementTarget(candidate, closest)) {
                closest = candidate;
            }
        }
        return closest;
    }

    private PlacementTarget findAnchor(BlockPos placePos, double directionX, double directionZ) {
        Direction primary = this.primaryAnchorDirection(directionX, directionZ);
        Direction[] ordered = this.orderedAnchorDirections(primary);
        PlacementTarget closest = null;
        for (Direction anchorDirection : ordered) {
            BlockPos anchor = placePos.offset(anchorDirection);
            Direction face = anchorDirection.getOpposite();
            BlockState state = mc.world.getBlockState(anchor);
            if (!this.isUsableAnchor(state, anchor)) {
                continue;
            }
            Vector3d aim = this.buildAimPoint(anchor, face, 0.0D, 0.0D);
            if (aim == null) {
                continue;
            }
            double reach = mc.playerController.getBlockReachDistance();
            if (mc.player.getEyePosition(1.0F).squareDistanceTo(aim) > reach * reach) {
                continue;
            }
            PlacementTarget candidate = new PlacementTarget(placePos, anchor, face, aim);
            float[] rotation = this.rotationsTo(aim);
            if (this.isExactHit(this.rayCast(rotation[0], rotation[1]), candidate)) {
                closest = this.closerAimTarget(candidate, closest);
            }
        }

        BlockPos anchor = placePos.down();
        Direction face = Direction.UP;
        BlockState state = mc.world.getBlockState(anchor);
        if (this.isUsableAnchor(state, anchor)) {
            Vector3d aim = this.buildAimPoint(anchor, face, 0.0D, 0.0D);
            if (aim != null) {
                double reach = mc.playerController.getBlockReachDistance();
                PlacementTarget candidate = new PlacementTarget(placePos, anchor, face, aim);
                float[] rotation = this.rotationsTo(aim);
                if (mc.player.getEyePosition(1.0F).squareDistanceTo(aim) <= reach * reach
                        && this.isExactHit(this.rayCast(rotation[0], rotation[1]), candidate)) {
                    closest = this.closerAimTarget(candidate, closest);
                }
            }
        }
        return closest;
    }

    private boolean isCloserPlacementTarget(PlacementTarget candidate, PlacementTarget current) {
        if (current == null) {
            return true;
        }
        double candidateDistance = this.placeDistanceSquared(candidate);
        double currentDistance = this.placeDistanceSquared(current);
        if (Math.abs(candidateDistance - currentDistance) > 1.0E-9D) {
            return candidateDistance < currentDistance;
        }
        return this.isCloserAimTarget(candidate, current);
    }

    private double placeDistanceSquared(PlacementTarget target) {
        double x = target.placePos.getX() + 0.5D - mc.player.getPosX();
        double z = target.placePos.getZ() + 0.5D - mc.player.getPosZ();
        return x * x + z * z;
    }

    private PlacementTarget closerAimTarget(PlacementTarget candidate, PlacementTarget current) {
        return current == null || this.isCloserAimTarget(candidate, current) ? candidate : current;
    }

    private boolean isCloserAimTarget(PlacementTarget candidate, PlacementTarget current) {
        Vector3d eyes = mc.player.getEyePosition(1.0F);
        return eyes.squareDistanceTo(candidate.aimPoint)
                + 1.0E-9D < eyes.squareDistanceTo(current.aimPoint);
    }

    private Direction primaryAnchorDirection(double directionX, double directionZ) {
        if (Math.abs(directionX) > Math.abs(directionZ)) {
            return directionX > 0.0D ? Direction.WEST : Direction.EAST;
        }
        return directionZ > 0.0D ? Direction.NORTH : Direction.SOUTH;
    }

    private Direction[] orderedAnchorDirections(Direction primary) {
        Direction firstSide;
        Direction secondSide;
        if (primary == Direction.NORTH || primary == Direction.SOUTH) {
            firstSide = Direction.WEST;
            secondSide = Direction.EAST;
        } else {
            firstSide = Direction.NORTH;
            secondSide = Direction.SOUTH;
        }
        return new Direction[]{primary, firstSide, secondSide, primary.getOpposite()};
    }

    private boolean isReplaceable(BlockPos pos) {
        BlockState state = mc.world.getBlockState(pos);
        return state.getFluidState().isEmpty()
                && (state.isAir() || state.getMaterial().isReplaceable());
    }

    private boolean isUsableAnchor(BlockState state, BlockPos pos) {
        if (state.isAir() || state.getMaterial().isReplaceable()
                || !state.getFluidState().isEmpty()
                || state.getBlock() instanceof ContainerBlock
                || state.getBlock() instanceof CraftingTableBlock) {
            return false;
        }
        return !state.getShape(mc.world, pos).isEmpty();
    }

    private int getSupportBlockY() {
        AxisAlignedBB box = mc.player.getBoundingBox();
        double centerX = (box.minX + box.maxX) * 0.5D;
        double centerZ = (box.minZ + box.maxZ) * 0.5D;
        double offset = Math.max(0.08D, mc.player.getWidth() * 0.25D);
        double[][] points = {
                {centerX, centerZ},
                {centerX + offset, centerZ}, {centerX - offset, centerZ},
                {centerX, centerZ + offset}, {centerX, centerZ - offset}
        };
        for (double[] point : points) {
            BlockRayTraceResult support = this.downwardSupportRay(point[0], point[1]);
            if (support != null) {
                return support.getPos().getY();
            }
        }
        return MathHelper.floor(box.minY - 0.01D);
    }

    private AimOffset updateAimNoise(PlacementKey key, long now) {
        double amount = this.safeNumber(this.aimNoise, 0.04F, 0.0F, 0.18F);
        if (!key.equals(this.noiseKey)) {
            this.noiseKey = key;
            this.noiseFromLateral = this.randomSymmetric(amount);
            this.noiseFromVertical = this.randomSymmetric(amount * 0.75D);
            this.noiseToLateral = this.randomSymmetric(amount);
            this.noiseToVertical = this.randomSymmetric(amount * 0.75D);
            this.noiseStartNanos = now;
            this.noiseEndNanos = safeAddNanos(now,
                    millisToNanos(ThreadLocalRandom.current().nextLong(140L, 321L)));
        } else if (now >= this.noiseEndNanos) {
            this.noiseFromLateral = this.noiseToLateral;
            this.noiseFromVertical = this.noiseToVertical;
            this.noiseToLateral = this.randomSymmetric(amount);
            this.noiseToVertical = this.randomSymmetric(amount * 0.75D);
            this.noiseStartNanos = now;
            this.noiseEndNanos = safeAddNanos(now,
                    millisToNanos(ThreadLocalRandom.current().nextLong(140L, 321L)));
        }
        double progress = smoothProgress(now, this.noiseStartNanos, this.noiseEndNanos);
        return new AimOffset(
                lerp(this.noiseFromLateral, this.noiseToLateral, progress),
                lerp(this.noiseFromVertical, this.noiseToVertical, progress));
    }

    private Vector3d getSafeNoisyAimPoint(PlacementTarget target, AimOffset noise) {
        double[] scales = {1.0D, 0.5D, 0.0D};
        for (double scale : scales) {
            Vector3d point = this.buildAimPoint(target.anchor, target.face,
                    noise.lateral * scale, noise.vertical * scale);
            if (point == null) {
                continue;
            }
            PlacementTarget adjusted = target.withAim(point);
            float[] rotations = this.rotationsTo(point);
            if (this.isExactHit(this.rayCast(rotations[0], rotations[1]), adjusted)) {
                return point;
            }
        }
        return target.aimPoint;
    }

    private Vector3d buildAimPoint(BlockPos anchor, Direction face,
                                    double lateralNoise, double verticalNoise) {
        AxisAlignedBB bounds = this.getFaceBounds(anchor, face);
        if (bounds == null) {
            return null;
        }
        double x = anchor.getX() + 0.5D;
        double y = anchor.getY() + 0.5D;
        double z = anchor.getZ() + 0.5D;
        if (face == Direction.UP || face == Direction.DOWN) {
            x = anchor.getX() + this.insideShapeCoordinate(
                    bounds.minX, bounds.maxX, 0.5D, lateralNoise);
            z = anchor.getZ() + this.insideShapeCoordinate(
                    bounds.minZ, bounds.maxZ, 0.5D, verticalNoise);
            y = anchor.getY() + (face == Direction.UP ? bounds.maxY : bounds.minY);
        } else {
            y = anchor.getY() + this.insideShapeCoordinate(bounds.minY, bounds.maxY,
                    this.safeNumber(this.edgeAim, 0.82F, 0.55F, 0.94F), verticalNoise);
            if (face == Direction.EAST || face == Direction.WEST) {
                x = anchor.getX() + (face == Direction.EAST ? bounds.maxX : bounds.minX);
                z = anchor.getZ() + this.insideShapeCoordinate(
                        bounds.minZ, bounds.maxZ, 0.5D, lateralNoise);
            } else {
                z = anchor.getZ() + (face == Direction.SOUTH ? bounds.maxZ : bounds.minZ);
                x = anchor.getX() + this.insideShapeCoordinate(
                        bounds.minX, bounds.maxX, 0.5D, lateralNoise);
            }
        }
        return new Vector3d(x, y, z);
    }

    private AxisAlignedBB getFaceBounds(BlockPos anchor, Direction face) {
        VoxelShape shape = mc.world.getBlockState(anchor).getShape(mc.world, anchor);
        if (shape.isEmpty()) {
            return null;
        }
        AxisAlignedBB selected = null;
        for (AxisAlignedBB candidate : shape.toBoundingBoxList()) {
            if (selected == null || this.isBetterFaceBounds(candidate, selected, face)) {
                selected = candidate;
            }
        }
        return selected;
    }

    private boolean isBetterFaceBounds(AxisAlignedBB candidate, AxisAlignedBB current, Direction face) {
        double candidatePlane = this.facePlane(candidate, face);
        double currentPlane = this.facePlane(current, face);
        boolean positive = face == Direction.EAST || face == Direction.SOUTH || face == Direction.UP;
        if (Math.abs(candidatePlane - currentPlane) > 1.0E-6D) {
            return positive ? candidatePlane > currentPlane : candidatePlane < currentPlane;
        }
        double candidateHeight = candidate.maxY - candidate.minY;
        double currentHeight = current.maxY - current.minY;
        if (Math.abs(candidateHeight - currentHeight) > 1.0E-6D) {
            return candidateHeight > currentHeight;
        }
        return candidate.maxY > current.maxY;
    }

    private double facePlane(AxisAlignedBB bounds, Direction face) {
        switch (face) {
            case EAST:
                return bounds.maxX;
            case WEST:
                return bounds.minX;
            case SOUTH:
                return bounds.maxZ;
            case NORTH:
                return bounds.minZ;
            case UP:
                return bounds.maxY;
            case DOWN:
            default:
                return bounds.minY;
        }
    }

    private double insideShapeCoordinate(double minimum, double maximum,
                                         double fraction, double noise) {
        double span = Math.max(0.0D, maximum - minimum);
        if (span <= 1.0E-6D) {
            return (minimum + maximum) * 0.5D;
        }
        double margin = Math.min(0.04D, span * 0.18D);
        double desired = minimum + span * MathHelper.clamp(fraction, 0.08D, 0.94D) + noise;
        return MathHelper.clamp(desired, minimum + margin, maximum - margin);
    }

    private void applySilentRotation(PlacementTarget target, long now) {
        float[] desired = this.rotationsTo(target.aimPoint);
        AimOffset tremor = this.updateZitter(target.key(), now);
        double[] scales = {1.0D, 0.5D, 0.0D};
        for (double scale : scales) {
            float candidateYaw = desired[0] + (float) (tremor.lateral * scale);
            float candidatePitch = MathHelper.clamp(
                    desired[1] + (float) (tremor.vertical * scale), -90.0F, 90.0F);
            if (this.isExactHit(this.rayCast(candidateYaw, candidatePitch), target)) {
                desired[0] = candidateYaw;
                desired[1] = candidatePitch;
                break;
            }
        }

        float oldYaw = mc.player.lastReportedYaw;
        float oldPitch = mc.player.lastReportedPitch;
        if (!Float.isFinite(oldYaw)) {
            oldYaw = mc.player.rotationYaw;
        }
        if (!Float.isFinite(oldPitch)) {
            oldPitch = mc.player.rotationPitch;
        }
        float yaw = RotationUtils.updateRotation(oldYaw, desired[0],
                this.safeNumber(this.horizontalSpeed, 18.0F, 1.0F, 90.0F));
        float pitch = RotationUtils.updateRotation(oldPitch, desired[1],
                this.safeNumber(this.verticalSpeed, 14.0F, 1.0F, 90.0F));
        float[] fixed = RotationUtils.gcdFix(new float[]{yaw, pitch},
                new float[]{oldYaw, oldPitch});
        if (Float.isFinite(fixed[0]) && Float.isFinite(fixed[1])) {
            yaw = fixed[0];
            pitch = MathHelper.clamp(fixed[1], -90.0F, 90.0F);
        }
        if (!Float.isFinite(yaw) || !Float.isFinite(pitch)) {
            return;
        }
        RotationManager.setRotations(yaw, pitch);
    }

    private AimOffset updateZitter(PlacementKey key, long now) {
        if (!this.zitter.getCurrentValue()) {
            this.zitterKey = null;
            return AimOffset.ZERO;
        }
        double amount = this.safeNumber(this.zitterAmount, 0.35F, 0.0F, 2.5F);
        if (amount <= 0.0D) {
            return AimOffset.ZERO;
        }
        if (!key.equals(this.zitterKey)) {
            this.zitterKey = key;
            this.zitterSign = ThreadLocalRandom.current().nextBoolean() ? 1 : -1;
            this.zitterFromYaw = 0.0D;
            this.zitterFromPitch = 0.0D;
            this.zitterToYaw = this.zitterSign * amount
                    * ThreadLocalRandom.current().nextDouble(0.65D, 1.0D);
            this.zitterToPitch = this.randomSymmetric(amount * 0.25D);
            this.zitterStartNanos = now;
            this.zitterEndNanos = safeAddNanos(now,
                    this.randomDelayNanos(this.zitterMinDelay, this.zitterMaxDelay, 20, 5000));
        } else if (now >= this.zitterEndNanos) {
            this.zitterFromYaw = this.zitterToYaw;
            this.zitterFromPitch = this.zitterToPitch;
            this.zitterSign *= -1;
            this.zitterToYaw = this.zitterSign * amount
                    * ThreadLocalRandom.current().nextDouble(0.65D, 1.0D);
            this.zitterToPitch = this.randomSymmetric(amount * 0.25D);
            this.zitterStartNanos = now;
            this.zitterEndNanos = safeAddNanos(now,
                    this.randomDelayNanos(this.zitterMinDelay, this.zitterMaxDelay, 20, 5000));
        }
        double progress = smoothProgress(now, this.zitterStartNanos, this.zitterEndNanos);
        return new AimOffset(
                lerp(this.zitterFromYaw, this.zitterToYaw, progress),
                lerp(this.zitterFromPitch, this.zitterToPitch, progress));
    }

    private boolean tryPlace() {
        if (this.placedThisTick || this.target == null || !this.serverAimReady
                || !this.canOperate() || System.nanoTime() < this.nextPlaceNanos
                || !this.isReplaceable(this.target.placePos)) {
            return false;
        }
        BlockRayTraceResult ray = this.serverRayCast();
        if (!this.isExactHit(ray, this.target)) {
            return false;
        }

        String spoof = this.itemSpoof();
        if ("None".equals(spoof)
                && !InvManagerUtil.shouldPlaceItem(mc.player.getHeldItem(Hand.MAIN_HAND).getItem())) {
            return false;
        }

        BlockFly parent = this.parent();
        parent.method16736();
        int currentSlot = mc.player.inventory.currentItem;
        if (!"None".equals(spoof)) {
            this.switchToValidHotbarItem(parent);
        }
        if (!InvManagerUtil.shouldPlaceItem(mc.player.getHeldItem(Hand.MAIN_HAND).getItem())) {
            mc.player.inventory.currentItem = currentSlot;
            return false;
        }

        ActionResultType result = mc.playerController.func_217292_a(
                mc.player, mc.world, Hand.MAIN_HAND, ray);
        if ("Spoof".equals(spoof) || "LiteSpoof".equals(spoof)) {
            mc.player.inventory.currentItem = currentSlot;
        }

        long now = System.nanoTime();
        this.nextPlaceNanos = safeAddNanos(now,
                this.randomDelayNanos(this.placeMinDelay, this.placeMaxDelay, 0, 30_000));
        if (result != ActionResultType.SUCCESS) {
            return false;
        }
        if (this.access().getBooleanValueFromSettingName("NoSwing")) {
            mc.getConnection().sendPacket(new CAnimateHandPacket(Hand.MAIN_HAND));
        } else {
            mc.player.swingArm(Hand.MAIN_HAND);
        }
        this.placedThisTick = true;
        this.serverAimReady = false;
        return true;
    }

    private boolean hasPlaceableBlocks() {
        if ("None".equals(this.itemSpoof())) {
            return InvManagerUtil.shouldPlaceItem(
                    mc.player.getHeldItem(Hand.MAIN_HAND).getItem());
        }
        return this.parent().getValidItemCount() > 0;
    }

    private String itemSpoof() {
        String value = this.access().getStringSettingValueByName("ItemSpoof");
        return value == null ? "None" : value;
    }

    private BlockFly parent() {
        return (BlockFly) this.access();
    }

    private float[] rotationsTo(Vector3d point) {
        Vector3d eye = mc.player.getEyePosition(1.0F);
        double deltaX = point.x - eye.x;
        double deltaY = point.y - eye.y;
        double deltaZ = point.z - eye.z;
        double horizontal = Math.sqrt(deltaX * deltaX + deltaZ * deltaZ);
        float yaw = (float) (Math.toDegrees(Math.atan2(deltaZ, deltaX)) - 90.0D);
        float pitch = (float) -Math.toDegrees(Math.atan2(deltaY, horizontal));
        return new float[]{yaw, MathHelper.clamp(pitch, -90.0F, 90.0F)};
    }

    private BlockRayTraceResult rayCast(float yaw, float pitch) {
        return this.rayCast(mc.player.getEyePosition(1.0F), yaw, pitch);
    }

    private BlockRayTraceResult serverRayCast() {
        Vector3d serverEyes = new Vector3d(
                mc.player.lastReportedPosX,
                mc.player.lastReportedPosY + mc.player.getEyeHeight(),
                mc.player.lastReportedPosZ);
        return this.rayCast(serverEyes, mc.player.lastReportedYaw, mc.player.lastReportedPitch);
    }

    private BlockRayTraceResult rayCast(Vector3d eyes, float yaw, float pitch) {
        float yawRadians = (float) Math.toRadians(yaw);
        float pitchRadians = (float) Math.toRadians(pitch);
        double x = -MathHelper.sin(yawRadians) * MathHelper.cos(pitchRadians);
        double y = -MathHelper.sin(pitchRadians);
        double z = MathHelper.cos(yawRadians) * MathHelper.cos(pitchRadians);
        double reach = mc.playerController.getBlockReachDistance();
        return mc.world.rayTraceBlocks(new RayTraceContext(
                eyes, eyes.add(x * reach, y * reach, z * reach),
                RayTraceContext.BlockMode.OUTLINE,
                RayTraceContext.FluidMode.NONE, mc.player));
    }

    private boolean isExactHit(BlockRayTraceResult ray, PlacementTarget expected) {
        return ray != null
                && ray.getType() == RayTraceResult.Type.BLOCK
                && expected.anchor.equals(ray.getPos())
                && expected.face == ray.getFace()
                && expected.placePos.equals(expected.anchor.offset(expected.face));
    }

    private float safeNumber(NumberSetting<Float> setting, float fallback,
                             float minimum, float maximum) {
        Float value = setting.getCurrentValue();
        float number = value == null ? fallback : value;
        if (!Float.isFinite(number)) {
            number = fallback;
        }
        return MathHelper.clamp(number, minimum, maximum);
    }

    private long randomDelayNanos(NumberSetting<Float> minimum,
                                  NumberSetting<Float> maximum,
                                  int hardMinimum, int hardMaximum) {
        return millisToNanos(this.randomDelayMillis(
                minimum, maximum, hardMinimum, hardMaximum));
    }

    private long randomDelayMillis(NumberSetting<Float> minimum,
                                   NumberSetting<Float> maximum,
                                   int hardMinimum, int hardMaximum) {
        long first = Math.round(this.safeNumber(minimum, minimum.getDefaultValue(),
                hardMinimum, hardMaximum));
        long second = Math.round(this.safeNumber(maximum, maximum.getDefaultValue(),
                hardMinimum, hardMaximum));
        long low = Math.min(first, second);
        long high = Math.max(first, second);
        if (low == high) {
            return low;
        }
        return ThreadLocalRandom.current().nextLong(low, high + 1L);
    }

    private double effectiveMinimumDelay(NumberSetting<Float> first,
                                         NumberSetting<Float> second,
                                         float hardMinimum, float hardMaximum) {
        float firstValue = this.safeNumber(first, first.getDefaultValue(), hardMinimum, hardMaximum);
        float secondValue = this.safeNumber(second, second.getDefaultValue(), hardMinimum, hardMaximum);
        return Math.min(firstValue, secondValue);
    }

    private double randomSymmetric(double amount) {
        if (!(amount > 0.0D) || !Double.isFinite(amount)) {
            return 0.0D;
        }
        return ThreadLocalRandom.current().nextDouble(-amount, amount);
    }

    private static long millisToNanos(double milliseconds) {
        if (!Double.isFinite(milliseconds)) {
            return 0L;
        }
        double clamped = MathHelper.clamp(milliseconds, 0.0D, 60_000.0D);
        return (long) (clamped * NANOS_PER_MILLI);
    }

    private static long safeAddNanos(long base, long delta) {
        if (delta <= 0L) {
            return base;
        }
        return base > Long.MAX_VALUE - delta ? Long.MAX_VALUE : base + delta;
    }

    private static double smoothProgress(long now, long start, long end) {
        if (end <= start) {
            return 1.0D;
        }
        double linear = MathHelper.clamp((double) (now - start) / (double) (end - start),
                0.0D, 1.0D);
        return linear * linear * (3.0D - 2.0D * linear);
    }

    private static double lerp(double start, double end, double progress) {
        return start + (end - start) * progress;
    }

    private static final class MotionIntent {
        private final double x;
        private final double z;
        private final double speed;

        private MotionIntent(double x, double z, double speed) {
            this.x = x;
            this.z = z;
            this.speed = speed;
        }
    }

    private static final class PlacementTarget {
        private final BlockPos placePos;
        private final BlockPos anchor;
        private final Direction face;
        private final Vector3d aimPoint;

        private PlacementTarget(BlockPos placePos, BlockPos anchor,
                                Direction face, Vector3d aimPoint) {
            this.placePos = placePos;
            this.anchor = anchor;
            this.face = face;
            this.aimPoint = aimPoint;
        }

        private PlacementKey key() {
            return new PlacementKey(this.placePos, this.anchor, this.face);
        }

        private PlacementTarget withAim(Vector3d point) {
            return new PlacementTarget(this.placePos, this.anchor, this.face, point);
        }
    }

    private static final class PlacementKey {
        private final BlockPos placePos;
        private final BlockPos anchor;
        private final Direction face;

        private PlacementKey(BlockPos placePos, BlockPos anchor, Direction face) {
            this.placePos = placePos;
            this.anchor = anchor;
            this.face = face;
        }

        @Override
        public boolean equals(Object object) {
            if (this == object) return true;
            if (!(object instanceof PlacementKey)) return false;
            PlacementKey other = (PlacementKey) object;
            return this.placePos.equals(other.placePos)
                    && this.anchor.equals(other.anchor)
                    && this.face == other.face;
        }

        @Override
        public int hashCode() {
            int result = this.placePos.hashCode();
            result = 31 * result + this.anchor.hashCode();
            result = 31 * result + this.face.hashCode();
            return result;
        }
    }

    private static final class AimOffset {
        private static final AimOffset ZERO = new AimOffset(0.0D, 0.0D);
        private final double lateral;
        private final double vertical;

        private AimOffset(double lateral, double vertical) {
            this.lateral = lateral;
            this.vertical = vertical;
        }
    }
}
