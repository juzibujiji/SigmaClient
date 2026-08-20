package net.minecraft.client.renderer;

import net.minecraft.client.renderer.entity.model.BipedModel;
import net.minecraft.client.renderer.model.ModelRenderer;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.SpearItem;
import net.minecraft.util.Hand;
import net.minecraft.util.HandSide;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.vector.Quaternion;
import net.minecraft.util.math.vector.Vector3f;
import com.mojang.blaze3d.matrix.MatrixStack;

/**
 * 1.21.11 长矛突刺动画，移植自官方 {@code client/model/effects/SpearAnimations.java}。
 *
 * <p>官方那个类从 {@code KineticWeapon} 数据组件里读 {@code delayTicks} 和三个
 * {@code Condition.maxDurationTicks} 来切分动画阶段；1.16.4 没有组件，这里直接从
 * {@link SpearItem} 上读同名的字段，数值来源完全一致。
 *
 * <p>官方用 {@code net.minecraft.util.Ease}，1.16.4 没有这个类，所以把用到的几条缓动曲线
 * 原样抄在下面（{@link #inQuad} 等），常数与官方逐位一致。
 *
 * <p>官方还有 {@code @OnlyIn(Dist.CLIENT)}，本项目不是 Forge，去掉。
 */
public class SpearAnimations
{
    /* ------------------------------------------------------------------
     * 官方 net.minecraft.util.Ease 里用到的缓动曲线
     * ------------------------------------------------------------------ */

    /** 官方 {@code Ease.inQuad}。 */
    public static float inQuad(float x)
    {
        return x * x;
    }

    /** 官方 {@code Ease.outQuart}。 */
    public static float outQuart(float x)
    {
        float f = 1.0F - x;
        return 1.0F - f * f * f * f;
    }

    /** 官方 {@code Ease.outCubic}。 */
    public static float outCubic(float x)
    {
        float f = 1.0F - x;
        return 1.0F - f * f * f;
    }

    /** 官方 {@code Ease.inOutSine}。 */
    public static float inOutSine(float x)
    {
        return -(MathHelper.cos((float)Math.PI * x) - 1.0F) / 2.0F;
    }

    /** 官方 {@code Ease.inOutExpo}。 */
    public static float inOutExpo(float x)
    {
        if (x < 0.5F)
        {
            return x == 0.0F ? 0.0F : (float)(Math.pow(2.0D, 20.0D * x - 10.0D) / 2.0D);
        }
        return x == 1.0F ? 1.0F : (float)((2.0D - Math.pow(2.0D, -20.0D * x + 10.0D)) / 2.0D);
    }

    /** 官方 {@code Ease.outCirc}。 */
    public static float outCirc(float x)
    {
        float f = x - 1.0F;
        return (float)Math.sqrt(1.0F - f * f);
    }

    /** 官方 {@code Ease.inCirc}。 */
    public static float inCirc(float x)
    {
        return (float)(-Math.sqrt(1.0F - x * x)) + 1.0F;
    }

    /** 官方 {@code Ease.outBack}（常数 1.70158 / 2.70158 照抄）。 */
    public static float outBack(float x)
    {
        float f = x - 1.0F;
        return 1.0F + 2.70158F * f * f * f + 1.70158F * f * f;
    }

    /** 官方 {@code Ease.inOutBack}。 */
    public static float inOutBack(float x)
    {
        if (x < 0.5F)
        {
            return 4.0F * x * x * (7.189819F * x - 2.5949094F) / 2.0F;
        }
        float f = 2.0F * x - 2.0F;
        return (f * f * (3.5949094F * f + 2.5949094F) + 2.0F) / 2.0F;
    }

    /** 官方 {@code Ease.inOutElastic}。 */
    public static float inOutElastic(float x)
    {
        if (x == 0.0F)
        {
            return 0.0F;
        }
        if (x == 1.0F)
        {
            return 1.0F;
        }
        double d0 = Math.sin((20.0D * x - 11.125D) * (float)Math.PI * 4.0F / 9.0F);
        return x < 0.5F
                ? (float)(-(Math.pow(2.0D, 20.0D * x - 10.0D) * d0) / 2.0D)
                : (float)(Math.pow(2.0D, -20.0D * x + 10.0D) * d0 / 2.0D + 1.0D);
    }

    /** 官方 {@code Mth.inverseLerp}，1.16.4 的 MathHelper 没有。 */
    private static float inverseLerp(float value, float from, float to)
    {
        return (value - from) / (to - from);
    }

    /** 官方 {@code SpearAnimations.progress}。 */
    static float progress(float value, float from, float to)
    {
        return MathHelper.clamp(inverseLerp(value, from, to), 0.0F, 1.0F);
    }

    /**
     * 官方 {@code PoseStack.rotateAround(Quaternion, x, y, z)}，1.16.4 的 MatrixStack 没有，
     * 用「平移到支点 → 旋转 → 平移回去」等价展开。
     */
    private static void rotateAround(MatrixStack stack, Quaternion rotation, float x, float y, float z)
    {
        stack.translate(x, y, z);
        stack.rotate(rotation);
        stack.translate(-x, -y, -z);
    }

    /** 官方 {@code SpearAnimations.hitFeedbackAmount}。 */
    private static float hitFeedbackAmount(float ticksSinceHit)
    {
        return 0.4F * (outQuart(progress(ticksSinceHit, 1.0F, 3.0F)) - inOutSine(progress(ticksSinceHit, 3.0F, 10.0F)));
    }

    /**
     * 官方 {@code SpearAnimations.firstPersonUse}：第一人称「举矛蓄势」。
     *
     * @param ticksSinceHit 官方 {@code getTicksSinceLastKineticHitFeedback}。1.16.4 没有这个
     *                      状态（见 {@link SpearItem} 类注释里关于实体状态 2 的说明），调用方传 0，
     *                      于是 {@link #hitFeedbackAmount} 恒为 0，回弹不生效。
     * @param useTicks      已经举了多少 tick（官方 {@code getUseDuration - remaining}）
     */
    public static void firstPersonUse(float ticksSinceHit, MatrixStack stack, float useTicks, HandSide side, SpearItem spear)
    {
        UseParams p = UseParams.from(spear, useTicks);
        int i = side == HandSide.RIGHT ? 1 : -1;

        stack.translate(
                i * (p.raiseProgress * 0.15F + p.raiseProgressEnd * -0.05F + p.swayProgress * -0.1F + p.swayScaleSlow * 0.005F),
                p.raiseProgress * -0.075F + p.raiseProgressMiddle * 0.075F + p.swayScaleFast * 0.01F,
                p.raiseProgressStart * 0.05D + p.raiseProgressEnd * -0.05D + p.swayScaleSlow * 0.005F);

        rotateAround(stack, Vector3f.XP.rotationDegrees(
                -65.0F * inOutBack(p.raiseProgress)
                        - 35.0F * p.lowerProgress
                        + 100.0F * p.raiseBackProgress
                        + -0.5F * p.swayScaleFast),
                0.0F, 0.1F, 0.0F);

        rotateAround(stack, Vector3f.YN.rotationDegrees(
                i * (-90.0F * progress(p.raiseProgress, 0.5F, 0.55F)
                        + 90.0F * p.swayProgress
                        + 2.0F * p.swayScaleSlow)),
                i * 0.15F, 0.0F, 0.0F);

        stack.translate(0.0F, -hitFeedbackAmount(ticksSinceHit), 0.0F);
    }

    /**
     * 官方 {@code SpearAnimations.firstPersonAttack}：第一人称左键穿刺（{@code STAB} 挥击）。
     *
     * @param swingProgress 官方 {@code p_450980_}，也就是 0~1 的挥击进度
     * @param handSign      右手 1 / 左手 -1（官方 {@code p_454745_}）
     */
    /**
     * 突刺前伸量。官方 {@code SpearAnimations.firstPersonAttack} 里是 {@code 0.65F}。
     *
     * <p><b>为什么不是官方值</b>：实测把它取成负数（{@code -0.65F}）时可见的矛身<b>更少</b>，
     * 说明在 1.16.4 的手部坐标空间里，这个值越大矛身露出得越多，趋势与官方相反 ——
     * 官方 {@code GameRenderer} 在渲染手部前对姿态栈做的变换和 1.16.4 不同
     * （官方是 {@code posestack.mulPose(相机旋转矩阵的逆)}，1.16.4 是
     * {@code resetProjectionMatrix}），同一个数值在两边的观感并不等价。
     *
     * <p>所以这里按实测方向放大。配套的回收量 {@link #STAB_RETRACT} 保持官方的
     * 0.65 : 0.25 比例，免得只放大前伸、动作收不回来。
     */
    private static final float STAB_REACH = 1.2F;

    /** 突刺的回收量，按官方 {@code 0.25F / 0.65F} 的比例跟随 {@link #STAB_REACH}。 */
    private static final float STAB_RETRACT = STAB_REACH * (0.25F / 0.65F);

    public static void firstPersonAttack(float swingProgress, MatrixStack stack, int handSign, HandSide side)
    {
        float f = inOutSine(progress(swingProgress, 0.0F, 0.05F));
        float f1 = outBack(progress(swingProgress, 0.05F, 0.2F));
        float f2 = inOutExpo(progress(swingProgress, 0.4F, 1.0F));

        stack.translate(handSign * 0.1F * (f - f1), -0.075F * (f - f2), STAB_REACH * (f - f1));
        stack.rotate(Vector3f.XP.rotationDegrees(-70.0F * (f - f2)));
        stack.translate(0.0D, 0.0D, -(double)STAB_RETRACT * (f2 - f1));
    }

    /**
     * 官方 {@code SpearAnimations.thirdPersonUseItem}：第三人称「举矛蓄势」时施加在
     * <b>矛身</b>上的变换。
     *
     * <p>这个和 {@link #thirdPersonHandUse} 是两回事，缺一不可：后者摆的是<b>手臂骨骼</b>，
     * 前者摆的是<b>手里那把矛</b>。之前只实现了手臂那半，表现就是「胳膊动了，矛没跟着动」。
     * 官方调用点在 {@code ItemInHandLayer.submitArmWithItem}，走
     * {@code ArmPose.SPEAR.animateUseItem}。
     *
     * @param swingProgress 官方 {@code state.attackTime}
     * @param useTicks      已经举了多少 tick，官方 {@code ticksUsingItem(arm)}，为 0 时不生效
     * @param ticksSinceHit 官方 {@code ticksSinceKineticHitFeedback}，1.16.4 没有这个状态，
     *                      调用方传 0（于是 {@link #hitFeedbackAmount} 恒为 0）
     */
    public static void thirdPersonUseItem(MatrixStack stack, float useTicks, float swingProgress,
                                          float ticksSinceHit, HandSide side, SpearItem spear)
    {
        if (useTicks == 0.0F)
        {
            return;
        }

        float f = inQuad(progress(swingProgress, 0.05F, 0.2F));
        float f1 = inOutExpo(progress(swingProgress, 0.4F, 1.0F));
        UseParams p = UseParams.from(spear, useTicks);
        int i = side == HandSide.RIGHT ? 1 : -1;
        float f2 = 1.0F - outBack(1.0F - p.raiseProgress);
        float f4 = hitFeedbackAmount(ticksSinceHit);

        stack.translate(0.0D, -f4 * 0.4D,
                -SpearItem.FORWARD_MOVEMENT * (f2 - p.raiseBackProgress) + f4);

        rotateAround(stack, Vector3f.XN.rotationDegrees(
                70.0F * (p.raiseProgress - p.raiseBackProgress) - 40.0F * (f - f1)),
                0.0F, -0.03125F, 0.125F);

        rotateAround(stack, Vector3f.YP.rotationDegrees(
                i * 90 * (p.raiseProgress - p.swayProgress + 3.0F * f1 + f)),
                0.0F, 0.0F, 0.125F);
    }

    /**
     * 官方 {@code SpearAnimations.thirdPersonAttackItem}：第三人称左键穿刺时施加在
     * <b>矛身</b>上的前刺变换。与 {@link #thirdPersonAttackHand}（手臂）配对。
     *
     * <p>官方 {@code forwardMovement} 取自物品的 {@code KINETIC_WEAPON} 组件，
     * 拿不到时按 0 算；这里直接用 {@link SpearItem#FORWARD_MOVEMENT}。
     *
     * @param swingProgress 官方 {@code state.attackTime}，&lt;= 0 时不生效
     */
    public static void thirdPersonAttackItem(MatrixStack stack, float swingProgress)
    {
        if (swingProgress <= 0.0F)
        {
            return;
        }

        float f = SpearItem.FORWARD_MOVEMENT;
        float f3 = inQuad(progress(swingProgress, 0.05F, 0.2F));
        float f4 = inOutExpo(progress(swingProgress, 0.4F, 1.0F));

        rotateAround(stack, Vector3f.XN.rotationDegrees(70.0F * (f3 - f4)), 0.0F, -0.125F, 0.125F);
        stack.translate(0.0F, f * (f3 - f4), 0.0F);
    }

    /**
     * 官方 {@code SpearAnimations.thirdPersonAttackHand}：第三人称挥击时的手臂姿态。
     *
     * <p>官方从 {@code HumanoidRenderState} 取 {@code attackTime} 和 {@code attackArm}；
     * 1.16.4 的 {@code BipedModel} 直接拿 {@code swingProgress} 与主手侧。
     */
    public static void thirdPersonAttackHand(BipedModel<?> model, float swingProgress, HandSide attackSide)
    {        model.bipedRightArm.rotateAngleY = model.bipedRightArm.rotateAngleY - model.bipedBody.rotateAngleY;
        model.bipedLeftArm.rotateAngleY = model.bipedLeftArm.rotateAngleY - model.bipedBody.rotateAngleY;
        model.bipedLeftArm.rotateAngleX = model.bipedLeftArm.rotateAngleX - model.bipedBody.rotateAngleY;

        float f1 = inOutSine(progress(swingProgress, 0.0F, 0.05F));
        float f2 = inQuad(progress(swingProgress, 0.05F, 0.2F));
        float f3 = inOutExpo(progress(swingProgress, 0.4F, 1.0F));

        ModelRenderer arm = attackSide == HandSide.RIGHT ? model.bipedRightArm : model.bipedLeftArm;
        arm.rotateAngleX += (90.0F * f1 - 120.0F * f2 + 30.0F * f3) * ((float)Math.PI / 180.0F);
    }

    /**
     * 官方 {@code SpearAnimations.thirdPersonHandUse}：第三人称「举矛蓄势」的手臂姿态。
     *
     * @param isRight 官方 {@code p_454141_}：这只手是不是右手
     */
    public static void thirdPersonHandUse(ModelRenderer arm, ModelRenderer head, boolean isRight,
                                          SpearItem spear, LivingEntity entity, float swimAmount)
    {
        int i = isRight ? 1 : -1;
        arm.rotateAngleY = -0.1F * i + head.rotateAngleY;
        arm.rotateAngleX = (float)(-Math.PI / 2) + head.rotateAngleX + 0.8F;

        if (entity.isElytraFlying() || swimAmount > 0.0F)
        {
            arm.rotateAngleX -= 0.9599311F;
        }

        arm.rotateAngleY = ((float)Math.PI / 180.0F) * MathHelper.clamp((180.0F / (float)Math.PI) * arm.rotateAngleY, -60.0F, 60.0F);
        arm.rotateAngleX = ((float)Math.PI / 180.0F) * MathHelper.clamp((180.0F / (float)Math.PI) * arm.rotateAngleX, -120.0F, 30.0F);

        if (spear == null || !entity.isHandActive())
        {
            // 没在举矛：只留上面的静态持矛姿势（官方 ticksUsingItem <= 0 的分支）。
            return;
        }

        // 官方还要求「正在使用的那只手就是这只手」：
        // !isUsingItem || useItemHand == (isRight ? MAIN_HAND : OFF_HAND)
        if (entity.getActiveHand() != (isRight ? Hand.MAIN_HAND : Hand.OFF_HAND))
        {
            return;
        }

        float useTicks = spear.getUseDuration(entity.getActiveItemStack()) - entity.getItemInUseCount();
        if (useTicks <= 0.0F)
        {
            return;
        }

        UseParams p = UseParams.from(spear, useTicks);
        arm.rotateAngleY = arm.rotateAngleY + -i * p.swayScaleFast * ((float)Math.PI / 180.0F) * p.swayIntensity * 1.0F;
        arm.rotateAngleZ = arm.rotateAngleZ + -i * p.swayScaleSlow * ((float)Math.PI / 180.0F) * p.swayIntensity * 0.5F;
        arm.rotateAngleX = arm.rotateAngleX + ((float)Math.PI / 180.0F)
                * (-40.0F * p.raiseProgressStart
                    + 30.0F * p.raiseProgressMiddle
                    + -20.0F * p.raiseProgressEnd
                    + 20.0F * p.lowerProgress
                    + 10.0F * p.raiseBackProgress
                    + 0.6F * p.swayScaleSlow * p.swayIntensity);
    }

    /**
     * 官方 {@code SpearAnimations.UseParams}：按「已经举了多少 tick」把突刺动画切成
     * 抬起 / 晃动 / 放下 / 收回几个阶段。阶段边界全部来自 {@link SpearItem} 上那几个
     * 官方数值（{@code delayTicks} 与三个 {@code Condition.maxDurationTicks}）。
     */
    static final class UseParams
    {
        final float raiseProgress;
        final float raiseProgressStart;
        final float raiseProgressMiddle;
        final float raiseProgressEnd;
        final float swayProgress;
        final float lowerProgress;
        final float raiseBackProgress;
        final float swayIntensity;
        final float swayScaleSlow;
        final float swayScaleFast;

        /** 官方 {@code UseParams.fromKineticWeapon(KineticWeapon, float)}。 */
        static UseParams from(SpearItem spear, float useTicks)
        {
            int delay = spear.getDelayTicks();
            int dismountEnd = spear.getDismountMaxDurationTicks() + delay;
            int swayStart = dismountEnd - 20;
            int knockbackEnd = spear.getKnockbackMaxDurationTicks() + delay;
            int lowerStart = knockbackEnd - 40;
            int damageEnd = spear.getDamageMaxDurationTicks() + delay;

            float raise = progress(useTicks, 0.0F, delay);
            float raiseStart = progress(raise, 0.0F, 0.5F);
            float raiseMiddle = progress(raise, 0.5F, 0.8F);
            float raiseEnd = progress(raise, 0.8F, 1.0F);
            float sway = progress(useTicks, swayStart, lowerStart);
            float lower = outCubic(inOutElastic(progress(useTicks - 20.0F, lowerStart, knockbackEnd)));
            float raiseBack = progress(useTicks, damageEnd - 5, damageEnd);
            float intensity = 2.0F * outCirc(sway) - 2.0F * inCirc(raiseBack);
            float slow = MathHelper.sin(useTicks * 19.0F * ((float)Math.PI / 180.0F)) * intensity;
            float fast = MathHelper.sin(useTicks * 30.0F * ((float)Math.PI / 180.0F)) * intensity;

            return new UseParams(raise, raiseStart, raiseMiddle, raiseEnd, sway, lower, raiseBack, intensity, slow, fast);
        }

        private UseParams(float raiseProgress, float raiseProgressStart, float raiseProgressMiddle,
                          float raiseProgressEnd, float swayProgress, float lowerProgress,
                          float raiseBackProgress, float swayIntensity, float swayScaleSlow, float swayScaleFast)
        {
            this.raiseProgress = raiseProgress;
            this.raiseProgressStart = raiseProgressStart;
            this.raiseProgressMiddle = raiseProgressMiddle;
            this.raiseProgressEnd = raiseProgressEnd;
            this.swayProgress = swayProgress;
            this.lowerProgress = lowerProgress;
            this.raiseBackProgress = raiseBackProgress;
            this.swayIntensity = swayIntensity;
            this.swayScaleSlow = swayScaleSlow;
            this.swayScaleFast = swayScaleFast;
        }
    }
}
