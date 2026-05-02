package com.mentalfrostbyte.jello.module.impl.movement.blockfly;

import com.mentalfrostbyte.Client;
import com.mentalfrostbyte.jello.event.impl.game.network.EventReceivePacket;
import com.mentalfrostbyte.jello.event.impl.game.network.EventSendPacket;
import com.mentalfrostbyte.jello.event.impl.player.EventGetFovModifier;
import com.mentalfrostbyte.jello.event.impl.player.EventUpdate;
import com.mentalfrostbyte.jello.event.impl.player.action.EventPlace;
import com.mentalfrostbyte.jello.event.impl.player.movement.EventJump;
import com.mentalfrostbyte.jello.event.impl.player.movement.EventMove;
import com.mentalfrostbyte.jello.event.impl.player.movement.EventSafeWalk;
import com.mentalfrostbyte.jello.managers.RotationManager;
import com.mentalfrostbyte.jello.module.Module;
import com.mentalfrostbyte.jello.module.data.ModuleCategory;
import com.mentalfrostbyte.jello.module.impl.movement.BlockFly;
import com.mentalfrostbyte.jello.module.impl.movement.SafeWalk;
import com.mentalfrostbyte.jello.module.impl.movement.speed.AACSpeed;
import com.mentalfrostbyte.jello.module.settings.impl.BooleanSetting;
import com.mentalfrostbyte.jello.module.settings.impl.ModeSetting;
import com.mentalfrostbyte.jello.module.settings.impl.NumberSetting;
import com.mentalfrostbyte.jello.util.game.player.InvManagerUtil;
import com.mentalfrostbyte.jello.util.game.player.MovementUtil;
import com.mentalfrostbyte.jello.util.game.player.constructor.Rotation;
import com.mentalfrostbyte.jello.util.game.player.rotation.util.RotationUtils;
import com.mentalfrostbyte.jello.util.game.world.PositionFacing;
import com.mentalfrostbyte.jello.util.game.world.blocks.BlockUtil;
import net.minecraft.network.play.client.CAnimateHandPacket;
import net.minecraft.network.play.client.CHeldItemChangePacket;
import net.minecraft.network.play.server.SPlayerPositionLookPacket;
import net.minecraft.util.ActionResultType;
import net.minecraft.util.Direction;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.BlockRayTraceResult;
import net.minecraft.util.math.vector.Vector3d;
import org.lwjgl.glfw.GLFW;
import team.sdhq.eventBus.annotations.EventTarget;
import team.sdhq.eventBus.annotations.priority.LowerPriority;
import team.sdhq.eventBus.annotations.priority.LowestPriority;

/**
 * Scaffold-architecture rewrite of BlockFlyAACMode.
 * <p>
 * Pipeline (per tick, in {@link #onUpdate(EventUpdate)}):
 * <ol>
 *   <li>Maintain {@link #baseY} — the Y level the player should be bridging on.
 *       Locked while airborne+moving in non-Normal modes; tracked from the
 *       player position otherwise.</li>
 *   <li>Search for a placement target via {@link #computeBlockPos()} — motion
 *       prediction + expanding shells around the predicted next-tick foot
 *       position, mirroring the reference Scaffold algorithm.</li>
 *   <li>Compute {@link #correctRotation} (face-aware, via
 *       {@link BlockUtil#rotationsToBlock}) and either snap (Normal+Snap) or
 *       smoothly interpolate {@link #rots} toward it, gated by
 *       HSpeed/VSpeed.</li>
 *   <li>Mode-specific behaviour: eagle on edges (Normal), jump-press driving
 *       (Telly Bridge / Keep Y), sneak boost (all).</li>
 *   <li>Submit {@link #rots} to {@link RotationManager#setRotations} so
 *       silent strafe, outgoing motion packets, <em>and</em> visual camera
 *       rendering (via {@code FixLook} in CorrectMovement) all pick up
 *       the scaffold rotation. Update {@link #lastRots}.</li>
 *   <li>Place via a constructed {@code BlockHitResult} aimed at the face
 *       centre — deterministic, no raycast/origin mismatch, no random
 *       miss.</li>
 * </ol>
 */
public class BlockFlyAACMode extends Module {

    /* === Rotation pipeline state (Vector2f rots in the reference) === */
    public Rotation rots = new Rotation(0F, 0F);
    public Rotation lastRots = new Rotation(0F, 0F);
    public Rotation correctRotation = new Rotation(0F, 0F);

    /* === Bridging state === */
    private PositionFacing pos;
    public int baseY = -1;
    private int offGroundTicks;
    private int sneakTicks;

    /* === Legacy fields kept for parent-module / Keep-Y movement === */
    private int previousSlot = 0;
    private int hapheAirborneTicks;
    private int hapheSpeedStage;
    private double hapheMotion;

    /* === Settings === */
    private final ModeSetting modeSetting;
    private final BooleanSetting eagle;
    private final BooleanSetting sneakBoost;
    private final BooleanSetting snap;
    private final BooleanSetting useRotationSpeed;
    private final NumberSetting<Float> hRotationSpeed;
    private final NumberSetting<Float> vRotationSpeed;

    public BlockFlyAACMode() {
        super(ModuleCategory.MOVEMENT, "AAC", "Scaffold-style bridge with smooth rotations.");
        this.registerSetting(this.modeSetting = new ModeSetting("Mode", "Bridging mode.", 1,
                "Normal", "Telly Bridge", "Keep Y"));
        this.registerSetting(this.eagle = new BooleanSetting("Eagle",
                "Auto-sneak when on a block edge (Normal mode only).", true) {
            @Override
            public boolean isHidden() {
                return !"Normal".equals(BlockFlyAACMode.this.modeSetting.getCurrentValue());
            }
        });
        this.registerSetting(this.snap = new BooleanSetting("Snap",
                "Snap yaw on the place tick (Normal mode only).", true) {
            @Override
            public boolean isHidden() {
                return !"Normal".equals(BlockFlyAACMode.this.modeSetting.getCurrentValue());
            }
        });
        this.registerSetting(this.sneakBoost = new BooleanSetting("Sneak",
                "Periodic sneak pulse to keep sprint capped.", true));
        this.registerSetting(this.useRotationSpeed = new BooleanSetting("Use Rotation Speed",
                "Cap rotation change per tick.", true));
        this.registerSetting(this.hRotationSpeed = new NumberSetting<>("Rotation HSpeed",
                "Max yaw delta per tick.", 180F, 6F, 360F, 6F));
        this.registerSetting(this.vRotationSpeed = new NumberSetting<>("Rotation VSpeed",
                "Max pitch delta per tick.", 180F, 6F, 360F, 6F));
    }

    /* ============ Mode helpers ============ */
    private String currentMode() {
        return this.modeSetting.getCurrentValue();
    }
    private boolean isNormalMode() { return "Normal".equals(currentMode()); }
    private boolean isTellyBridgeMode() { return "Telly Bridge".equals(currentMode()); }
    private boolean isKeepYMode() { return "Keep Y".equals(currentMode()); }

    private boolean userHoldingJump() {
        return GLFW.glfwGetKey(mc.getMainWindow().getHandle(),
                mc.gameSettings.keyBindJump.keyCode.getKeyCode()) == 1;
    }
    private boolean userHoldingSneak() {
        return GLFW.glfwGetKey(mc.getMainWindow().getHandle(),
                mc.gameSettings.keyBindSneak.keyCode.getKeyCode()) == 1;
    }
    private boolean hasMovementInput() {
        return mc.player.movementInput.moveForward != 0F
                || mc.player.movementInput.moveStrafe != 0F;
    }

    /* ============ Lifecycle ============ */
    @Override
    public void onEnable() {
        if (mc.player == null) return;
        this.previousSlot = mc.player.inventory.currentItem;
        this.rots = new Rotation(mc.player.rotationYaw, mc.player.rotationPitch);
        this.lastRots = new Rotation(mc.player.prevRotationYaw, mc.player.prevRotationPitch);
        this.correctRotation = new Rotation(this.rots.yaw, this.rots.pitch);
        this.pos = null;
        this.baseY = (int) Math.floor(mc.player.getPosY()) - 1;
        this.offGroundTicks = 0;
        this.sneakTicks = 0;
        this.hapheAirborneTicks = 0;
        this.hapheSpeedStage = -1;
        ((BlockFly) this.access()).lastSpoofedSlot = -1;
        if (this.access().getStringSettingValueByName("ItemSpoof").equals("Switch")) {
            ((BlockFly) this.access()).switchToValidHotbarItem();
        }
    }

    @Override
    public void onDisable() {
        if (this.previousSlot != -1
                && this.access().getStringSettingValueByName("ItemSpoof").equals("Switch")) {
            mc.player.inventory.currentItem = this.previousSlot;
        }
        // Restore key states to user's actual hold so we don't leak presses.
        mc.gameSettings.keyBindJump.setPressed(this.userHoldingJump());
        mc.gameSettings.keyBindSneak.setPressed(this.userHoldingSneak());
        this.previousSlot = -1;
        if (((BlockFly) this.access()).lastSpoofedSlot >= 0) {
            mc.getConnection().sendPacket(new CHeldItemChangePacket(mc.player.inventory.currentItem));
            ((BlockFly) this.access()).lastSpoofedSlot = -1;
        }
        mc.timer.timerSpeed = 1.0F;
    }

    /* ============ Parent-integration handlers (preserved) ============ */
    @EventTarget
    @LowerPriority
    public void onSendPacket(EventSendPacket var1) {
        if (this.isEnabled() && mc.player != null
                && var1.packet instanceof CHeldItemChangePacket
                && ((BlockFly) this.access()).lastSpoofedSlot >= 0) {
            var1.cancelled = true;
        }
    }

    @EventTarget
    public void onReceivePacket(EventReceivePacket event) {
        if (this.isEnabled() && event.packet instanceof SPlayerPositionLookPacket) {
            this.hapheSpeedStage = 0;
        }
    }

    @EventTarget
    public void onSafeWalk(EventSafeWalk var1) {
        if (this.isEnabled() && mc.player.isOnGround()
                && Client.getInstance().moduleManager.getModuleByClass(SafeWalk.class).isEnabled()) {
            var1.setSafe(true);
        }
    }

    @EventTarget
    public void onMove(EventMove var1) {
        if (!this.isEnabled()) return;

        if (this.access().getBooleanValueFromSettingName("No Sprint")) {
            mc.player.setSprinting(false);
        } else if (!this.access().getBooleanValueFromSettingName("UesGameSprint")) {
            mc.player.setSprinting(true);
        }

        ((BlockFly) this.access()).onMove(var1);

        // Keep-Y mode: AACAP-style airborne motion (preserves original behaviour).
        if (this.isKeepYMode()) {
            if (!mc.player.isOnGround() || !this.hasMovementInput()) {
                if (this.hapheAirborneTicks >= 0) this.hapheAirborneTicks++;
            } else {
                this.hapheAirborneTicks = 0;
                mc.player.jump();
                var1.setY(0.419998 + (double) MovementUtil.getJumpBoost() * 0.1);
                if (this.hapheSpeedStage < 3) this.hapheSpeedStage++;
            }
            if (!this.hasMovementInput() || mc.player.collidedHorizontally) {
                this.hapheSpeedStage = 0;
            }
            this.hapheMotion = AACSpeed.method16016(this.hapheAirborneTicks, this.hapheSpeedStage,
                    () -> this.hapheSpeedStage = 0);
            if (this.hapheAirborneTicks >= 0) {
                MovementUtil.setMotion(var1, this.hapheMotion);
            }
        }
    }

    @EventTarget
    public void onFOV(EventGetFovModifier var1) {
        if (this.isEnabled() && mc.world != null && mc.player != null) {
            if (this.isKeepYMode() && MovementUtil.isMoving() && !mc.player.isSprinting()) {
                var1.fovModifier *= 1.14F;
            }
        }
    }

    @EventTarget
<<<<<<< HEAD
    public void onJump(EventJump var1) {
        if (this.isEnabled()
                && this.access().getStringSettingValueByName("Tower Mode").equalsIgnoreCase("Vanilla")
                && (!MovementUtil.isMoving()
                || this.access().getBooleanValueFromSettingName("Tower while moving"))) {
            var1.cancelled = true;
=======
    @LowestPriority
    public void onUpdate(EventUpdate event) {
        if (this.isEnabled() && mc.player != null) {
            double placeY = mc.player.getPosY();
            if (this.getBooleanValueFromSettingName("Telly") && GLFW.glfwGetKey(mc.getMainWindow().getHandle(), mc.gameSettings.keyBindJump.keyCode.getKeyCode()) != 1) {
                placeY = this.placeY;
            }
            if (MovementUtil.isMoving() && mc.player.isOnGround() && this.getBooleanValueFromSettingName("Haphe (AACAP)") && !mc.player.isJumping) {
                mc.player.jump();
            }
            if (!mc.player.isJumping && this.getBooleanValueFromSettingName("Haphe (AACAP)")) {
                placeY = this.placeY;
            }
            BlockPos var6 = new BlockPos(mc.player.getPosX(), (double) Math.round(placeY - 1.0), mc.player.getPosZ());
            List var7 = this.method16208(Blocks.STONE, var6);

            if (!var7.isEmpty()) {
                PositionFacing var8 = (PositionFacing) var7.get(var7.size() - 1);
                BlockRayTraceResult var9 = BlockUtil.rayTrace(this.yaw, this.pitch, 5.0F);
                if (!var9.getPos().equals(var8.blockPos()) || !var9.getFace().equals(var8.direction())) {
                    float[] var10 = BlockUtil.rotationsToBlock(var8.blockPos(), var8.direction());
                    this.yaw = var10[0];
                    this.pitch = var10[1];
                }
            }
            if (this.getBooleanValueFromSettingName("Telly") && mc.player.isOnGround()) {
                if (useRotationSpeed.getCurrentValue()) {
                    //Rotation limitrot = RotationUtils.limitAngleChange(new Rotation(RotationCore.lastYaw, RotationCore.lastPitch), new Rotation(mc.player.rotationYaw, this.pitch), rotationSpeed.getCurrentValue(), rotationSpeed.getCurrentValue());
                    //RotationManager.setRotations(limitrot.yaw, limitrot.pitch);
                } else {
                    //RotationManager.setRotations(mc.player.rotationYaw, this.pitch);
                }
            } else {
                if (useRotationSpeed.getCurrentValue()) {
                    float[] limitrot = RotationUtils.gcdFix(new float[]{
                            RotationUtils.limitAngleChange(new Rotation(RotationCore.lastYaw, RotationCore.lastPitch), new Rotation(this.yaw, this.pitch), hrotationSpeed.getCurrentValue(), vrotationSpeed.getCurrentValue()).yaw,
                            RotationUtils.limitAngleChange(new Rotation(RotationCore.lastYaw, RotationCore.lastPitch), new Rotation(this.yaw, this.pitch), hrotationSpeed.getCurrentValue(), vrotationSpeed.getCurrentValue()).pitch},
                            new float[]{mc.player.lastReportedYaw, mc.player.lastReportedPitch}
                    );
                    RotationManager.setRotations(limitrot[0], limitrot[1]);
                } else {
                    RotationManager.setRotations(this.yaw, this.pitch);
                }
            }

            if (this.getBooleanValueFromSettingName("Telly") && mc.player.isOnGround() && MovementUtil.isMoving()) {
                mc.gameSettings.keyBindJump.setPressed(true);
            }

            if (this.getBooleanValueFromSettingName("Telly") && !MovementUtil.isMoving() && GLFW.glfwGetKey(mc.getMainWindow().getHandle(), mc.gameSettings.keyBindJump.keyCode.getKeyCode()) != 1) {
                mc.gameSettings.keyBindJump.setPressed(false);
            }
            if (!this.getBooleanValueFromSettingName("Haphe (AACAP)")) {
                if (!this.method16207()) {
                    float var11 = 0.0F;

                    while (var11 < 0.7F && !this.method16207()) {
                        var11 += 0.1F;
                    }
                }
            } else {
                this.method16207();
            }
>>>>>>> a2cb78427defbe5aaccbf808001b554a3107eb07
        }
    }

    @EventTarget
    public void onClick(EventPlace event) {
        // Marker event; placement is driven from onUpdate.
    }

    /* ============ Main pipeline ============ */
    @EventTarget
    @LowestPriority
    public void onUpdate(EventUpdate event) {
        if (!this.isEnabled() || mc.player == null || mc.world == null) return;

        // Off-ground tick counter (Telly bridge timing).
        if (mc.player.isOnGround()) this.offGroundTicks = 0;
        else this.offGroundTicks++;

        // baseY: in airborne non-Normal modes, lock the level we bridge on.
        // Otherwise (on ground, no movement, jumping intentionally, or Normal),
        // resync to current foot level. This prevents the Y drift that caused
        // the head/ceiling-aim bug in the legacy implementation.
        int currentFloor = (int) Math.floor(mc.player.getPosY()) - 1;
        boolean shouldResetBaseY = mc.player.isOnGround()
                || !this.hasMovementInput()
                || this.userHoldingJump()
                || this.isNormalMode()
                || this.baseY == -1
                || this.baseY > currentFloor;
        if (shouldResetBaseY) this.baseY = currentFloor;

        // Find a placement target.
        this.pos = null;
        this.computeBlockPos();

        // Compute & interpolate rotation toward target.
        if (this.pos != null) {
            float[] raw = BlockUtil.rotationsToBlock(this.pos.blockPos(), this.pos.direction());
            this.correctRotation = new Rotation(raw[0], raw[1]);

            float maxYaw = this.useRotationSpeed.getCurrentValue() ? this.hRotationSpeed.getCurrentValue() : 180F;
            float maxPitch = this.useRotationSpeed.getCurrentValue() ? this.vRotationSpeed.getCurrentValue() : 180F;

            if (this.isNormalMode() && this.snap.getCurrentValue()) {
                // Snap yaw on the place tick; pitch always follows directly.
                this.rots.yaw = this.correctRotation.yaw;
            } else {
                this.rots.yaw = RotationUtils.updateRotation(this.rots.yaw, this.correctRotation.yaw, maxYaw);
            }
            this.rots.pitch = RotationUtils.updateRotation(this.rots.pitch, this.correctRotation.pitch, maxPitch);
        }

        // Sneak-boost pulse (Scaffold-style: every ~21 ticks, briefly hold sneak).
        this.handleSneakBoost();

        // Mode-specific behaviour BEFORE we publish the rotation.
        boolean placed;
        if (this.isTellyBridgeMode()) {
            // Telly: drive jump from movement input, only place after airborne >=1 tick.
            mc.gameSettings.keyBindJump.setPressed(this.hasMovementInput() || this.userHoldingJump());
            placed = (this.offGroundTicks >= 1) && this.tryPlaceBlock();
        } else if (this.isKeepYMode()) {
            // Keep Y: jump on demand, place freely (motion is owned by EventMove handler).
            mc.gameSettings.keyBindJump.setPressed(this.hasMovementInput() || this.userHoldingJump());
            placed = this.tryPlaceBlock();
        } else {
            // Normal: eagle on edges, place freely. Don't fight user's jump.
            if (this.eagle.getCurrentValue()) {
                boolean onEdge = mc.player.isOnGround() && this.isOnBlockEdge(0.3F);
                // Compose with sneak-boost without overwriting it.
                if (onEdge) mc.gameSettings.keyBindSneak.setPressed(true);
            }
            mc.gameSettings.keyBindJump.setPressed(this.userHoldingJump());
            placed = this.tryPlaceBlock();
        }

        // Publish rotation to the standard RotationManager pipeline. This
        // drives:
        //   • outgoing motion packet yaw/pitch via RotationManager.onPre,
        //   • visual camera (when CorrectMovement.FixLook=true, the default)
        //     via RotationManager.onLook — head visibly turns toward the
        //     placement face,
        //   • silentStrafe input correction via RotationManager.onInput.
        RotationManager.setRotations(this.rots.yaw, this.rots.pitch);
        this.lastRots = new Rotation(this.rots.yaw, this.rots.pitch);

        // Tower-while-moving for Vanilla tower (kept from original).
        if (placed && this.access().getStringSettingValueByName("Tower Mode").equalsIgnoreCase("AAC")) {
            // Reserved for future tower-state hooks if needed.
        }
    }

    private void handleSneakBoost() {
        if (!this.sneakBoost.getCurrentValue()) {
            // Hand sneak control back to the user (eagle may re-press in Normal mode below).
            if (!this.userHoldingSneak()) mc.gameSettings.keyBindSneak.setPressed(false);
            return;
        }
        this.sneakTicks++;
        if (this.sneakTicks == 18) {
            if (mc.player.isSprinting()) mc.player.setSprinting(false);
            mc.gameSettings.keyBindSneak.setPressed(true);
        } else if (this.sneakTicks >= 21) {
            if (!this.userHoldingSneak()) mc.gameSettings.keyBindSneak.setPressed(false);
            this.sneakTicks = 0;
        }
    }

    /* ============ Block-finding (Scaffold algorithm) ============ */
    private void computeBlockPos() {
        Vector3d motion = mc.player.getMotion();
        Vector3d eye = mc.player.getEyePosition(1.0F);
        // Predict next tick's eye position; bridging anchors should hit there.
        Vector3d baseVec = eye.add(motion.x * 2.0, motion.y * 2.0, motion.z * 2.0);

        BlockPos baseFoot = new BlockPos(baseVec.x, (double) ((float) this.baseY + 0.1F), baseVec.z);
        int baseX = baseFoot.getX();
        int baseZ = baseFoot.getZ();

        // If the slot directly under the predicted foot is already solid, nothing to do.
        if (mc.world.getBlockState(baseFoot).isSolid()) return;

        if (this.checkBlock(baseVec, baseFoot)) return;

        // Expanding shells: down-only first, then radial up to distance 6.
        for (int d = 1; d <= 6; d++) {
            if (this.checkBlock(baseVec, new BlockPos(baseX, this.baseY - d, baseZ))) return;
            for (int x = 1; x <= d; x++) {
                for (int z = 0; z <= d - x; z++) {
                    int y = d - x - z;
                    for (int rx = 0; rx <= 1; rx++) {
                        for (int rz = 0; rz <= 1; rz++) {
                            BlockPos cand = new BlockPos(
                                    baseX + (rx == 0 ? x : -x),
                                    this.baseY - y,
                                    baseZ + (rz == 0 ? z : -z));
                            if (this.checkBlock(baseVec, cand)) return;
                        }
                    }
                }
            }
        }
    }

    /**
     * Returns {@code true} (and assigns {@link #pos}) if {@code bp} is
     * a placeable air slot that has at least one solid neighbour we can
     * click on, the click point is within reach, and the angle between
     * the click vector and the face normal is non-negative (i.e. the
     * face is actually visible from the player).
     */
    private boolean checkBlock(Vector3d baseVec, BlockPos bp) {
        if (mc.world.getBlockState(bp).getMaterial().isSolid()) return false;
        double cx = bp.getX() + 0.5;
        double cy = bp.getY() + 0.5;
        double cz = bp.getZ() + 0.5;
        for (Direction face : Direction.values()) {
            BlockPos anchor = bp.offset(face);
            if (!mc.world.getBlockState(anchor).getMaterial().isSolid()) continue;

            double hx = cx + face.getXOffset() * 0.5;
            double hy = cy + face.getYOffset() * 0.5;
            double hz = cz + face.getZOffset() * 0.5;
            double rx = hx - baseVec.x;
            double ry = hy - baseVec.y;
            double rz = hz - baseVec.z;
            double lenSqr = rx * rx + ry * ry + rz * rz;
            if (lenSqr > 20.25) continue; // > 4.5 blocks: out of reach
            // Don't aim at faces pointing away from us (back-face culling).
            double dot = rx * face.getXOffset() + ry * face.getYOffset() + rz * face.getZOffset();
            if (dot < 0.0) continue;

            this.pos = new PositionFacing(anchor, face.getOpposite());
            return true;
        }
        return false;
    }

    /* ============ Placement ============ */
    private boolean tryPlaceBlock() {
        if (this.pos == null) return false;

        // shouldBuild: position the player is standing in must be empty
        // (avoid placing on top of self, which causes the chin-aim bug).
        BlockPos under = new BlockPos(mc.player.getPosX(), mc.player.getPosY() - 0.5, mc.player.getPosZ());
        if (!mc.world.isAirBlock(under)) return false;

        if (this.access().getStringSettingValueByName("ItemSpoof").equals("None")
                && !InvManagerUtil.shouldPlaceItem(mc.player.getHeldItem(Hand.MAIN_HAND).getItem())) {
            return false;
        }

        // Never aim at the top face of a block we are sitting on (this is
        // the "chin-aim" bug — clicking UP when the anchor is at our feet
        // places a tower block beneath ourselves rather than ahead).
        if (this.pos.direction() == Direction.UP
                && (double) (this.pos.blockPos().getY() + 2) > mc.player.getPosY()) {
            return false;
        }

        // Construct the BlockHitResult deterministically from face geometry
        // instead of running a raycast for verification. The raycast in
        // BlockUtil.rayTrace originates at lastReportedPos (one tick behind)
        // while our rotation is computed from getEyePosition (current tick),
        // so the ray would clip the face edge or miss outright on roughly
        // half of placements — which is why scaffold appeared broken.
        Direction face = this.pos.direction();
        BlockPos anchor = this.pos.blockPos();
        Vector3d hit = new Vector3d(
                anchor.getX() + 0.5 + face.getXOffset() * 0.5,
                anchor.getY() + 0.5 + face.getYOffset() * 0.5,
                anchor.getZ() + 0.5 + face.getZOffset() * 0.5
        );
        Vector3d eye = mc.player.getEyePosition(1.0F);
        double rdx = hit.x - eye.x, rdy = hit.y - eye.y, rdz = hit.z - eye.z;
        if (rdx * rdx + rdy * rdy + rdz * rdz > 20.25) return false; // reach
        BlockRayTraceResult ray = new BlockRayTraceResult(hit, face, anchor, false);

        ((BlockFly) this.access()).method16736();
        int prev = mc.player.inventory.currentItem;
        if (!this.access().getStringSettingValueByName("ItemSpoof").equals("None")) {
            ((BlockFly) this.access()).switchToValidHotbarItem();
        }
        ActionResultType result = mc.playerController.func_217292_a(mc.player, mc.world, Hand.MAIN_HAND, ray);
        String spoof = this.access().getStringSettingValueByName("ItemSpoof");
        if (spoof.equals("Spoof") || spoof.equals("LiteSpoof")) {
            mc.player.inventory.currentItem = prev;
        }
        if (result == ActionResultType.SUCCESS) {
            if (!this.access().getBooleanValueFromSettingName("NoSwing")) {
                mc.player.swingArm(Hand.MAIN_HAND);
            } else {
                mc.getConnection().sendPacket(new CAnimateHandPacket(Hand.MAIN_HAND));
            }
            this.pos = null;
            return true;
        }
        return false;
    }

    /* ============ Eagle helper ============ */
    private boolean isOnBlockEdge(float sensitivity) {
        return !mc.world.getCollisionShapes(mc.player,
                        mc.player.getBoundingBox().offset(0.0, -0.5, 0.0)
                                .grow(-sensitivity, 0.0, -sensitivity))
                .findAny().isPresent();
    }
}
