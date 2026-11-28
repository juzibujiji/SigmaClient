package com.mentalfrostbyte.jello.module.impl.movement.blockfly;

import com.mentalfrostbyte.Client;
import com.mentalfrostbyte.jello.event.impl.game.network.EventReceivePacket;
import com.mentalfrostbyte.jello.event.impl.game.network.EventSendPacket;
import com.mentalfrostbyte.jello.event.impl.player.EventGetFovModifier;
import com.mentalfrostbyte.jello.event.impl.player.EventUpdate;
import com.mentalfrostbyte.jello.event.impl.player.action.EventPlace;
import com.mentalfrostbyte.jello.event.impl.player.movement.*;
import com.mentalfrostbyte.jello.managers.RotationManager;
import com.mentalfrostbyte.jello.module.Module;
import com.mentalfrostbyte.jello.module.data.ModuleCategory;
import com.mentalfrostbyte.jello.module.impl.movement.BlockFly;
import com.mentalfrostbyte.jello.module.impl.movement.SafeWalk;
import com.mentalfrostbyte.jello.module.impl.movement.speed.AACSpeed;
import com.mentalfrostbyte.jello.module.settings.impl.BooleanSetting;
import com.mentalfrostbyte.jello.module.settings.impl.NumberSetting;
import com.mentalfrostbyte.jello.util.game.player.InvManagerUtil;
import com.mentalfrostbyte.jello.util.game.player.MovementPredictor;
import com.mentalfrostbyte.jello.util.game.player.MovementUtil;
import com.mentalfrostbyte.jello.util.game.player.constructor.Rotation;
import com.mentalfrostbyte.jello.util.game.player.rotation.RotationCore;
import com.mentalfrostbyte.jello.util.game.player.rotation.util.HumanRotation;
import com.mentalfrostbyte.jello.util.game.player.rotation.util.RotationUtils;
import com.mentalfrostbyte.jello.util.game.world.PositionFacing;
import com.mentalfrostbyte.jello.util.game.world.blocks.BlockUtil;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.network.play.client.CAnimateHandPacket;
import net.minecraft.network.play.client.CHeldItemChangePacket;
import net.minecraft.network.play.server.SPlayerPositionLookPacket;
import net.minecraft.util.ActionResultType;
import net.minecraft.util.Direction;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.BlockRayTraceResult;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.vector.Vector3d;
import org.lwjgl.glfw.GLFW;
import team.sdhq.eventBus.annotations.EventTarget;
import team.sdhq.eventBus.annotations.priority.LowerPriority;
import team.sdhq.eventBus.annotations.priority.LowestPriority;

import java.util.List;

public class BlockFlyAACMode extends Module {
    private float yaw;
    private float pitch;
    private int placeY = 0;
    private int previousSlot = 0;
    private int field23524;
    /** IDK, it's set to 0 when you get setback though. Speed value? **/
    private int speedValue;
    private double field23526;
    private final NumberSetting<Float> vrotationSpeed;
    private final NumberSetting<Float> hrotationSpeed;
    private final BooleanSetting useRotationSpeed;
    private final BooleanSetting recoverrot;
    private final BooleanSetting recoverrotsprint;
    private final BooleanSetting strictPredict;
    private final BooleanSetting simulateLegit;
    private final BooleanSetting smartPath;
    private final HumanRotation humanRotation = new HumanRotation();

    public BlockFlyAACMode() {
        super(ModuleCategory.MOVEMENT, "AAC", "Places block underneath if it is in hand");
        this.registerSetting(new BooleanSetting("Telly", "SpeedTelly", true));
        this.registerSetting(new BooleanSetting("Haphe (AACAP)", "Never let's you touch the ground.", false));
        this.registerSetting(this.useRotationSpeed = new BooleanSetting("Use Rotation Speed", "Max rotation change per tick.", true));
        this.registerSetting(this.hrotationSpeed = new NumberSetting<>("Rotation HSpeed", "Max rotation change per tick.", 6.0F, 6.0F, 360, 6F));
        this.registerSetting(this.vrotationSpeed = new NumberSetting<>("Rotation VSpeed", "Max rotation change per tick.", 6.0F, 6.0F, 360, 6F));
        this.registerSetting(this.recoverrot = new BooleanSetting("RecoverRotUseSpeed","use rotspeed too",false));
        this.registerSetting(this.recoverrotsprint = new BooleanSetting("RecoverRotSprint","",false));
        this.registerSetting(this.strictPredict = new BooleanSetting("Strict Predict",
                "Place with the last sent C03, aim from the predicted post-move position.", false));
        this.registerSetting(this.simulateLegit = new BooleanSetting("Simulate Legit",
                "Human-like flicks that finish in 1-3 ticks; Telly turns forward just before landing.", false));
        this.registerSetting(this.smartPath = new BooleanSetting("Smart Path",
                "Pre-aim at the next block from the edge, aim at the visible point closest to the current view.", true));
    }

    @Override
    public void onEnable() {
        this.previousSlot = mc.player.inventory.currentItem;
        this.yaw = mc.player.rotationYaw;
        this.pitch = mc.player.rotationPitch;
        this.placeY = (int) mc.player.getPosY();
        this.speedValue = -1;
        this.humanRotation.reset();
        ((BlockFly) this.access()).lastSpoofedSlot = -1;
        if (this.access().getStringSettingValueByName("ItemSpoof").equals("Switch")) {
            ((BlockFly) this.access()).switchToValidHotbarItem();
        }
    }

    @Override
    public void onDisable() {
        if (this.previousSlot != -1 && this.access().getStringSettingValueByName("ItemSpoof").equals("Switch")) {
            mc.player.inventory.currentItem = this.previousSlot;
        }
        if (this.getBooleanValueFromSettingName("Telly") && GLFW.glfwGetKey(mc.getMainWindow().getHandle(), mc.gameSettings.keyBindJump.keyCode.getKeyCode()) != 1) {
            mc.gameSettings.keyBindJump.setPressed(false);
        }
        this.previousSlot = -1;
        if (((BlockFly) this.access()).lastSpoofedSlot >= 0) {
            mc.getConnection().sendPacket(new CHeldItemChangePacket(mc.player.inventory.currentItem));
            ((BlockFly) this.access()).lastSpoofedSlot = -1;
        }

        mc.timer.timerSpeed = 1.0F;
    }

    @EventTarget
    @LowerPriority
    public void method16202(EventSendPacket var1) {
        if (this.isEnabled() && mc.player != null) {
            if (var1.packet instanceof CHeldItemChangePacket && ((BlockFly) this.access()).lastSpoofedSlot >= 0) {
                var1.cancelled = true;
            }
        }
    }

    @EventTarget
    public void method16203(EventReceivePacket event) {
        if (this.isEnabled()) {
            if (event.packet instanceof SPlayerPositionLookPacket) {
                this.speedValue = 0;
            }
        }
    }

    @EventTarget
    public void method16204(EventSafeWalk var1) {
        if (this.isEnabled()) {
            if (mc.player.isOnGround() && Client.getInstance().moduleManager.getModuleByClass(SafeWalk.class).isEnabled()) {
                var1.setSafe(true);
            }
        }
    }

    @EventTarget
    public void method16205(EventMove var1) {
        if (this.isEnabled()) {
            if (this.access().getBooleanValueFromSettingName("No Sprint")) {
                mc.player.setSprinting(false);
            } else {
                if (this.access().getBooleanValueFromSettingName("UesGameSprint")) {

                } else {
                    mc.player.setSprinting(true);
                }
            }

            /*if (!this.getBooleanValueFromSettingName("Haphe (AACAP)")) {
                mc.gameSettings.keyBindSprint.setPressed(false);
                mc.player.setSprinting(false);
            }*/

            ((BlockFly) this.access()).onMove(var1);
            if (this.getBooleanValueFromSettingName("Haphe (AACAP)")) {
                if (!mc.player.isOnGround() || mc.player.moveForward == 0.0F && mc.player.moveStrafing == 0.0F) {
                    if (this.field23524 >= 0) {
                        this.field23524++;
                    }
                } else {
                    this.field23524 = 0;
                    mc.player.jump();
                    var1.setY(0.419998 + (double) MovementUtil.getJumpBoost() * 0.1);
                    if (this.speedValue < 3) {
                        this.speedValue++;
                    }
                }

                if (mc.player.moveForward == 0.0F && mc.player.moveStrafing == 0.0F || mc.player.collidedHorizontally) {
                    this.speedValue = 0;
                }

                this.field23526 = AACSpeed.method16016(this.field23524, this.speedValue, () -> this.speedValue = 0);
                if (this.field23524 >= 0) {
                    MovementUtil.setMotion(var1, this.field23526);
                }
            }
        }
    }

    @EventTarget
    public void onFOV(EventGetFovModifier var1) {
        if (this.isEnabled() && mc.world != null && mc.player != null) {
            if (this.getBooleanValueFromSettingName("Haphe (AACAP)") && MovementUtil.isMoving() && !mc.player.isSprinting()) {
                var1.fovModifier *= 1.14F;
            }
        }
    }

    private boolean method16207() {
        BlockRayTraceResult var3 = (BlockRayTraceResult) BlockUtil.rayTraceWithOffset(RotationCore.currentYaw, RotationCore.currentPitch, BlockUtil.getBlockReachDistance(), 0.0F);
        return this.placeHit(var3);
    }

    private boolean placeHit(BlockRayTraceResult var3) {
        boolean var4 = false;
        if (var3 != null && var3.getType() == RayTraceResult.Type.BLOCK) {
            if (this.access().getStringSettingValueByName("ItemSpoof").equals("None")) {
                if (!InvManagerUtil.shouldPlaceItem(mc.player.getHeldItem(Hand.MAIN_HAND).getItem())) {
                    return false;
                }
            }

            if (this.getBooleanValueFromSettingName("Haphe (AACAP)") && !mc.player.isJumping && !mc.player.isOnGround()) {
                if (var3.getFace() == Direction.UP) {
                    return false;
                }

                if (var3.getPos().getY() != this.placeY - 1) {
                    return false;
                }
            }

            if (this.getBooleanValueFromSettingName("Telly") && GLFW.glfwGetKey(mc.getMainWindow().getHandle(), mc.gameSettings.keyBindJump.keyCode.getKeyCode()) != 1) {
                if (var3.getFace() == Direction.UP) {
                    return false;
                }

                /*if (var3.getPos().getY() != this.placeY - 1) {
                    return false;
                }*/
            }

            if (var3.getFace() == Direction.UP
                    && (double) (var3.getPos().getY() + 2) > mc.player.getPosY()
                    && BlockUtil.isValidBlockPosition(var3.getPos())) {
                return false;
            }

            if ((double) var3.getPos().getY() == mc.player.getPosY()) {
                return false;
            }

            ((BlockFly) this.access()).method16736();
            int var5 = mc.player.inventory.currentItem;
            if (!this.access().getStringSettingValueByName("ItemSpoof").equals("None")) {
                ((BlockFly) this.access()).switchToValidHotbarItem();
            }

            ActionResultType var6 = mc.playerController.func_217292_a(mc.player, mc.world, Hand.MAIN_HAND, var3);
            if (this.access().getStringSettingValueByName("ItemSpoof").equals("Spoof") || this.access().getStringSettingValueByName("ItemSpoof").equals("LiteSpoof")) {
                mc.player.inventory.currentItem = var5;
            }

            if (var6 == ActionResultType.SUCCESS) {
                if (!this.access().getBooleanValueFromSettingName("NoSwing")) {
                    mc.player.swingArm(Hand.MAIN_HAND);
                } else {
                    mc.getConnection().sendPacket(new CAnimateHandPacket(Hand.MAIN_HAND));
                }

                if (var3.getFace() == Direction.UP) {
                    this.placeY = var3.getPos().getY() + 2;
                }

                var4 = true;
            }
        }

        return var4;
    }

    public List<PositionFacing> method16208(Block var1, BlockPos var2) {
        return BlockFlyHelper.method35030(var1, var2, (int) mc.playerController.getBlockReachDistance());
    }

    /** Smart Path：现在瞄的角度还能放就不动，否则转到 BlockFlyHelper 找的那个点 */
    private void aimSmartPath(BlockPos target, boolean strict) {
        BlockFlyHelper.Placement plan = BlockFlyHelper.findPlacement(target, this.allowUpFace());
        if (plan == null || plan.hitBy(this.yaw, this.pitch, strict)) {
            return;
        }

        float[] rotation = plan.rotation(strict);
        this.yaw = rotation[0];
        // Simulate Legit 平地搭路贴侧面放时 pitch 不超过 84°（Intave BlockRotation 查侧面放置 pitch 超过 85°），多走一点就能打中。
        // Telly 不压：人在半空眼睛高，压了会晚一两 tick 才放得上，落地前来不及回正
        this.pitch = this.simulateLegit.getCurrentValue() && !this.getBooleanValueFromSettingName("Telly")
                && plan.face().getAxis() != Direction.Axis.Y
                ? Math.min(rotation[1], 84.0F)
                : rotation[1];
    }

    /** placeHit 会拒掉的顶面，找放置点时就不选 */
    private boolean allowUpFace() {
        boolean tellyKeepY = this.getBooleanValueFromSettingName("Telly")
                && GLFW.glfwGetKey(mc.getMainWindow().getHandle(), mc.gameSettings.keyBindJump.keyCode.getKeyCode()) != 1;
        boolean hapheInAir = this.getBooleanValueFromSettingName("Haphe (AACAP)") && !mc.player.isJumping && !mc.player.isOnGround();
        return !tellyKeepY && !hapheInAir;
    }

    /**
     * Simulate Legit 的 Telly 什么时候回正，返回最多还能用几 tick 转回来（含这一 tick），-1 表示接着瞄方块：
     * <ul>
     *     <li>空中再过 1~2 tick 就落到已经搭好的方块上：提前开始回正，落地那一 tick 正好朝前起跳。
     *     落点还没搭好就接着瞄方块，先保证不掉下去；</li>
     *     <li>在地上：这一 tick 要转到。还差 105° 以上（比正常甩 180° 时单 tick 最多转的还多）、多站一 tick 也不会掉下去时
     *     晚一 tick 跳，分两 tick 转回来，不在一 tick 里甩 180°。</li>
     * </ul>
     */
    private int tellyTurnTicks() {
        boolean jumpHeld = GLFW.glfwGetKey(mc.getMainWindow().getHandle(), mc.gameSettings.keyBindJump.keyCode.getKeyCode()) == 1;
        Vector3d motion = mc.player.getMotion();
        if (mc.player.isOnGround()) {
            if (!jumpHeld && Math.abs(MathHelper.wrapDegrees(mc.player.rotationYaw - RotationCore.lastYaw)) > 105.0F
                    && this.standsOn(mc.player.getPosX() + motion.x, mc.player.getPosZ() + motion.z)) {
                mc.gameSettings.keyBindJump.setPressed(false);
                return 2;
            }

            return 1;
        }

        if (jumpHeld) {
            return -1;
        }

        double y = mc.player.getPosY();
        double motionY = motion.y;
        for (int tick = 1; tick <= 2; tick++) {
            // 原版顺序：先按这一 tick 的速度移动，再减重力、乘空气阻力
            y += motionY;
            if (y <= this.placeY) {
                return this.standsOn(mc.player.getPosX() + motion.x * tick, mc.player.getPosZ() + motion.z * tick)
                        ? tick + 1
                        : -1;
            }

            motionY = (motionY - 0.08D) * 0.98D;
        }

        return -1;
    }

    /** 站在 (x, z) 时碰撞箱四个角有一个下面（placeY 那一层）是方块，就能站住 */
    private boolean standsOn(double x, double z) {
        for (double offsetX = -0.3D; offsetX <= 0.3D; offsetX += 0.6D) {
            for (double offsetZ = -0.3D; offsetZ <= 0.3D; offsetZ += 0.6D) {
                if (BlockUtil.isValidBlockPosition(new BlockPos(x + offsetX, this.placeY - 1, z + offsetZ))) {
                    return true;
                }
            }
        }

        return false;
    }

    /** Simulate Legit：用 HumanRotation 甩过去（限速沿用 Rotation HSpeed/VSpeed），最多 deadline tick 到位，再过 GCD */
    private void setLegitRotation(float yaw, float pitch, int deadline) {
        boolean limited = useRotationSpeed.getCurrentValue();
        Rotation next = this.humanRotation.step(new Rotation(RotationCore.lastYaw, RotationCore.lastPitch),
                new Rotation(yaw, pitch),
                limited ? hrotationSpeed.getCurrentValue() : 180.0F,
                limited ? vrotationSpeed.getCurrentValue() : 180.0F,
                deadline);
        float[] fixed = RotationUtils.gcdFix(new float[]{next.yaw, next.pitch},
                new float[]{mc.player.lastReportedYaw, mc.player.lastReportedPitch});
        RotationManager.setRotations(fixed[0], fixed[1]);
    }

    /*@EventTarget
    @LowestPriority
    public void onUpdate(EventMotion event) {
        if (this.isEnabled()) {
            event.setYaw(RotationCore.currentYaw);
            event.setPitch(RotationCore.currentPitch);
        }
    }*/

    //EventUpdate -> Motion -> LivingUpdate ->

    @EventTarget
    @LowestPriority
    public void onUpdate(EventUpdate event) {
        if (this.isEnabled()) {
            // Strict Predict：先放，放置射线用上一个 C03 的位置和角度；转头按这一 tick 移动后的眼睛位置瞄
            boolean strict = this.strictPredict.getCurrentValue();
            if (strict
                    && !this.placeHit(BlockUtil.rayTrace(mc.player.lastReportedYaw, mc.player.lastReportedPitch, 0.0F))
                    && MovementPredictor.mustPlaceNow()) {
                // 这一 tick 不放、移动后就会落进空格子：按原逻辑当场放
                strict = false;
            }

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

            // 跳跃键要在算转头之前定好，预测移动要用到
            if (this.getBooleanValueFromSettingName("Telly") && mc.player.isOnGround() && MovementUtil.isMoving()) {
                if (recoverrotsprint.getCurrentValue()) {
                    if (mc.player.isSprinting()) {
                        mc.gameSettings.keyBindJump.setPressed(true);
                    } else {
                        if (GLFW.glfwGetKey(mc.getMainWindow().getHandle(), mc.gameSettings.keyBindJump.keyCode.getKeyCode()) != 1) {
                            mc.gameSettings.keyBindJump.setPressed(false);
                        }
                    }
                } else {
                    mc.gameSettings.keyBindJump.setPressed(true);
                }
            }

            if (this.getBooleanValueFromSettingName("Telly") && !MovementUtil.isMoving() && GLFW.glfwGetKey(mc.getMainWindow().getHandle(), mc.gameSettings.keyBindJump.keyCode.getKeyCode()) != 1) {
                mc.gameSettings.keyBindJump.setPressed(false);
            }

            BlockPos var6 = new BlockPos(mc.player.getPosX(), (double) Math.round(placeY - 1.0), mc.player.getPosZ());
            if (this.smartPath.getCurrentValue()) {
                this.aimSmartPath(var6, strict);
            } else {
                List<PositionFacing> var7 = this.method16208(Blocks.STONE, var6);
                if (!var7.isEmpty()) {
                    PositionFacing var8 = var7.get(var7.size() - 1);
                    BlockRayTraceResult var9 = BlockUtil.rayTrace(this.yaw, this.pitch, 5.0F);
                    if (!var9.getPos().equals(var8.blockPos()) || !var9.getFace().equals(var8.direction())) {
                        float[] var10 = BlockUtil.rotationsToBlock(var8.blockPos(), var8.direction(), strict);
                        this.yaw = var10[0];
                        this.pitch = var10[1];
                    }
                }
            }

            boolean telly = this.getBooleanValueFromSettingName("Telly");
            if (this.simulateLegit.getCurrentValue()) {
                int turnTicks = telly ? this.tellyTurnTicks() : -1;
                if (turnTicks > 0) {
                    this.setLegitRotation(mc.player.rotationYaw,
                            recoverrot.getCurrentValue() ? this.pitch : mc.player.rotationPitch, turnTicks);
                } else {
                    this.setLegitRotation(this.yaw, this.pitch, Integer.MAX_VALUE);
                }
            } else if (telly && mc.player.isOnGround()) {
                if (recoverrot.getCurrentValue()) {
                    if (useRotationSpeed.getCurrentValue()) {
                        Rotation limitrot = RotationUtils.limitAngleChange(new Rotation(RotationCore.lastYaw, RotationCore.lastPitch), new Rotation(mc.player.rotationYaw, this.pitch), hrotationSpeed.getCurrentValue(), vrotationSpeed.getCurrentValue());
                        RotationManager.setRotations(limitrot.yaw, limitrot.pitch);
                    } else {
                        RotationManager.setRotations(mc.player.rotationYaw, this.pitch);
                    }
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

            if (strict) {
                return;
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
        }
    }

    @EventTarget
    public void onClick(EventPlace event) {

    }


    @EventTarget
    public void method16211(EventJump var1) {
        if (this.isEnabled()) {
            if (this.access().getStringSettingValueByName("Tower Mode").equalsIgnoreCase("Vanilla") && (!MovementUtil.isMoving() || this.access().getBooleanValueFromSettingName("Tower while moving"))) {
                var1.cancelled = true;
            }
        }
    }

}