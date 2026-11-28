package com.mentalfrostbyte.jello.util.game.player;

import com.mentalfrostbyte.Client;
import com.mentalfrostbyte.jello.event.impl.player.movement.EventMoveInput;
import com.mentalfrostbyte.jello.gui.base.JelloPortal;
import com.mentalfrostbyte.jello.module.impl.movement.CorrectMovement;
import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.player.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ModernMovementPhysics;
import net.minecraft.potion.Effects;
import net.minecraft.util.ReuseableStream;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.shapes.ISelectionContext;
import net.minecraft.util.math.vector.Vector3d;

import java.util.stream.Stream;

/**
 * 预测这一 tick 移动之后的眼睛位置。C03 是在 EventUpdate 之后、移动完才发的，
 * EventUpdate 里瞄准要按移动后的位置算，C03 里的位置和角度才对得上。
 */
public final class MovementPredictor {
    private static final Minecraft mc = Minecraft.getInstance();

    private MovementPredictor() {
    }

    /**
     * 这一 tick 移动之后的眼睛位置。按键用调用时的状态，所以要在模块处理完跳跃/潜行键之后调用。
     *
     * @param serverYaw 这一 tick 要发的 yaw。开着 CorrectMovement 时 movefix 按它映射按键、决定起跳加速方向，
     *                  所以不同的 yaw 会得到不同的位置
     */
    public static Vector3d predictEyes(float serverYaw) {
        ClientPlayerEntity player = mc.player;
        Vector3d move = predictMove(serverYaw);
        return new Vector3d(player.getPosX() + move.x,
                player.getPosY() + move.y + (double) player.getEyeHeight(),
                player.getPosZ() + move.z);
    }

    /**
     * 按预测，这一 tick 移动后脚会从上面落进一个空格子。等下一 tick 再放时碰撞箱已经占住那一格，放置会被拒绝，
     * Strict Predict 这一 tick 没放成时就该按原逻辑当场放。
     */
    public static boolean mustPlaceNow() {
        ClientPlayerEntity player = mc.player;
        Vector3d move = predictMove(player.lastReportedYaw);
        double feet = player.getPosY();
        double level = Math.floor(feet + 1.0E-7D);
        if (!(feet + move.y < level - 1.0E-7D)) {
            return false;
        }

        BlockPos cell = new BlockPos(player.getPosX() + move.x, level - 1.0D, player.getPosZ() + move.z);
        return mc.world.getBlockState(cell).getMaterial().isReplaceable();
    }

    /**
     * 按原版 livingTick/travel 的顺序预测位移：速度阈值清零 → 起跳 → 按键经 movefix 映射 → 潜行减速、×0.98
     * → moveRelative → 一次碰撞查询。只读，不改实体。
     * 不模拟台阶、潜行/SafeWalk 边缘回退和其它模块在 EventMove 里改的位移。
     */
    private static Vector3d predictMove(float serverYaw) {
        ClientPlayerEntity player = mc.player;
        ProtocolVersion version = JelloPortal.getVersion();
        Vector3d motion = ModernMovementPhysics.applyMovementThreshold(player.getMotion(), true);
        double mx = motion.x;
        double my = motion.y;
        double mz = motion.z;

        // RotationManager 的 onInput/onJump/onStrafe 都换成 RotationCore.currentYaw
        boolean moveFix = Client.getInstance().moduleManager.getModuleByClass(CorrectMovement.class).isEnabled();
        float moveYaw = moveFix ? serverYaw : player.rotationYaw;
        float forward = (mc.gameSettings.keyBindForward.isKeyDown() ? 1.0F : 0.0F)
                - (mc.gameSettings.keyBindBack.isKeyDown() ? 1.0F : 0.0F);
        float strafe = (mc.gameSettings.keyBindLeft.isKeyDown() ? 1.0F : 0.0F)
                - (mc.gameSettings.keyBindRight.isKeyDown() ? 1.0F : 0.0F);
        if (moveFix && (forward != 0.0F || strafe != 0.0F)) {
            EventMoveInput input = new EventMoveInput(forward, strafe, false, false, 0.3F);
            MovementUtil.silentStrafe(input, serverYaw);
            forward = input.forward;
            strafe = input.strafe;
        }

        boolean sneaking = isSneakSlowed(player, version);
        if (sneaking) {
            forward *= 0.3F;
            strafe *= 0.3F;
        }

        // livingTick 先 --jumpTicks 再判定起跳；jump() 不改 onGround，这一 tick travel 仍按地面算
        boolean onGround = player.isOnGround();
        if (mc.gameSettings.keyBindJump.isKeyDown() && onGround && player.jumpTicks <= 1 && !player.abilities.isFlying) {
            float jump = 0.42F * player.getJumpFactor();
            if (player.isPotionActive(Effects.JUMP_BOOST)) {
                jump += 0.1F * (float) (player.getActivePotionEffect(Effects.JUMP_BOOST).getAmplifier() + 1);
            }

            my = version.newerThanOrEqualTo(ProtocolVersion.v1_21_2) ? Math.max(jump, my) : jump;
            if (player.isSprinting() && keepsSprint(version, forward, sneaking)) {
                float rad = moveYaw * ((float) Math.PI / 180F);
                mx += (double) (-MathHelper.sin(rad) * 0.2F);
                mz += (double) (MathHelper.cos(rad) * 0.2F);
            }
        }

        if (ModernMovementPhysics.shouldApplyTravelInputFactors()) {
            strafe *= 0.98F;
            forward *= 0.98F;
            if (ModernMovementPhysics.shouldApplySquareMovementCompensation(player)) {
                float[] squared = ModernMovementPhysics.modifyInputSpeedForSquareMovement(strafe, forward);
                strafe = squared[0];
                forward = squared[1];
            }
        }

        // getAIMoveSpeed / jumpMovementFactor 在上一 tick 末尾刷新，正好是这一 tick travel 要用的值
        AxisAlignedBB box = player.getBoundingBox();
        BlockPos below = new BlockPos(player.getPosX(),
                box.minY - (version.olderThanOrEqualTo(ProtocolVersion.v1_14_4) ? 1.0D : 0.5000001D),
                player.getPosZ());
        float slipperiness = mc.world.getBlockState(below).getBlock().getSlipperiness();
        float factor = onGround
                ? player.getAIMoveSpeed() * (0.21600002F / (slipperiness * slipperiness * slipperiness))
                : offGroundSpeed(player, version);

        Vector3d relative = new Vector3d(strafe, 0.0D, forward);
        double lengthSq = relative.lengthSquared();
        if (lengthSq >= (version.olderThanOrEqualTo(ProtocolVersion.v1_13_2) ? 1.0E-4D : 1.0E-7D)) {
            Vector3d scaled = (lengthSq > 1.0D ? relative.normalize() : relative).scale((double) factor);
            float sin = MathHelper.sin(moveYaw * ((float) Math.PI / 180F));
            float cos = MathHelper.cos(moveYaw * ((float) Math.PI / 180F));
            mx += scaled.x * (double) cos - scaled.z * (double) sin;
            mz += scaled.z * (double) cos + scaled.x * (double) sin;
        }

        Vector3d wanted = new Vector3d(mx, my, mz);
        if (wanted.lengthSquared() == 0.0D) {
            return Vector3d.ZERO;
        }

        // 不走 getAllowedMovement：它会触发 EventStep
        return Entity.collideBoundingBoxHeuristically(player, wanted, box, mc.world,
                ISelectionContext.forEntity(player), new ReuseableStream<>(Stream.empty()));
    }

    /** 和 MovementInputFromOptions.shouldApplySneakSlowdown 一致：<=1.13.2 看实时潜行键，1.15+ 看上一 tick 的蹲姿 */
    private static boolean isSneakSlowed(ClientPlayerEntity player, ProtocolVersion version) {
        boolean liveSneak = mc.gameSettings.keyBindSneak.isKeyDown();
        if (version.olderThanOrEqualTo(ProtocolVersion.v1_13_2)) {
            return liveSneak;
        }

        return version.olderThanOrEqualTo(ProtocolVersion.v1_14_4)
                ? liveSneak || player.isSneaking()
                : player.isSneaking();
    }

    /** 起跳前 livingTick 的疾跑取消：<=1.14.1 要求 forward >= 0.8 且没在潜行，之后只要求有前进输入 */
    private static boolean keepsSprint(ProtocolVersion version, float forward, boolean sneaking) {
        return version.olderThanOrEqualTo(ProtocolVersion.v1_14_1)
                ? forward >= 0.8F && !sneaking
                : forward > 1.0E-5F;
    }

    /** 和 PlayerEntity.getOffGroundSpeed 一致：1.19.4+ 按当前疾跑状态，之前用上一 tick 末尾的 jumpMovementFactor */
    private static float offGroundSpeed(ClientPlayerEntity player, ProtocolVersion version) {
        if (ModernMovementPhysics.shouldUseVanilla1_21MovementPhysics()
                || version.newerThanOrEqualTo(ProtocolVersion.v1_19_4)) {
            return player.isSprinting() ? 0.025999999F : 0.02F;
        }

        return player.jumpMovementFactor;
    }
}
