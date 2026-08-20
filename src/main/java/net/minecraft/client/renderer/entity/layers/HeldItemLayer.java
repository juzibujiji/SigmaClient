package net.minecraft.client.renderer.entity.layers;

import com.mojang.blaze3d.matrix.MatrixStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.IRenderTypeBuffer;
import net.minecraft.client.renderer.entity.IEntityRenderer;
import net.minecraft.client.renderer.entity.model.EntityModel;
import net.minecraft.client.renderer.entity.model.IHasArm;
import net.minecraft.client.renderer.model.ItemCameraTransforms;
import net.minecraft.entity.LivingEntity;
import net.minecraft.client.renderer.SpearAnimations;
import net.minecraft.item.ItemStack;
import net.minecraft.item.SpearItem;
import net.minecraft.util.Hand;
import net.minecraft.util.HandSide;
import net.minecraft.util.math.vector.Vector3f;

public class HeldItemLayer<T extends LivingEntity, M extends EntityModel<T> & IHasArm> extends LayerRenderer<T, M>
{
    public HeldItemLayer(IEntityRenderer<T, M> p_i50934_1_)
    {
        super(p_i50934_1_);
    }

    public void render(MatrixStack matrixStackIn, IRenderTypeBuffer bufferIn, int packedLightIn, T entitylivingbaseIn, float limbSwing, float limbSwingAmount, float partialTicks, float ageInTicks, float netHeadYaw, float headPitch)
    {
        boolean flag = entitylivingbaseIn.getPrimaryHand() == HandSide.RIGHT;
        ItemStack itemstack = flag ? entitylivingbaseIn.getHeldItemOffhand() : entitylivingbaseIn.getHeldItemMainhand();
        ItemStack itemstack1 = flag ? entitylivingbaseIn.getHeldItemMainhand() : entitylivingbaseIn.getHeldItemOffhand();

        if (!itemstack.isEmpty() || !itemstack1.isEmpty())
        {
            matrixStackIn.push();

            if (this.getEntityModel().isChild)
            {
                float f = 0.5F;
                matrixStackIn.translate(0.0D, 0.75D, 0.0D);
                matrixStackIn.scale(0.5F, 0.5F, 0.5F);
            }

            this.func_229135_a_(entitylivingbaseIn, itemstack1, ItemCameraTransforms.TransformType.THIRD_PERSON_RIGHT_HAND, HandSide.RIGHT, matrixStackIn, bufferIn, packedLightIn, partialTicks);
            this.func_229135_a_(entitylivingbaseIn, itemstack, ItemCameraTransforms.TransformType.THIRD_PERSON_LEFT_HAND, HandSide.LEFT, matrixStackIn, bufferIn, packedLightIn, partialTicks);
            matrixStackIn.pop();
        }
    }

    private void func_229135_a_(LivingEntity p_229135_1_, ItemStack p_229135_2_, ItemCameraTransforms.TransformType p_229135_3_, HandSide p_229135_4_, MatrixStack p_229135_5_, IRenderTypeBuffer p_229135_6_, int p_229135_7_, float partialTicks)
    {
        if (!p_229135_2_.isEmpty())
        {
            p_229135_5_.push();
            boolean locatorTransform = this.translateHand(p_229135_4_, p_229135_5_);
            boolean flag = p_229135_4_ == HandSide.LEFT;
            if (!locatorTransform) {
                p_229135_5_.rotate(Vector3f.XP.rotationDegrees(-90.0F));
                p_229135_5_.rotate(Vector3f.YP.rotationDegrees(180.0F));
                p_229135_5_.translate((double)((float)(flag ? -1 : 1) / 16.0F), 0.125D, -0.625D);
            }

            applySpearItemAnimations(p_229135_1_, p_229135_2_, p_229135_4_, p_229135_5_, partialTicks);

            Minecraft.getInstance().getFirstPersonRenderer().renderItemSide(p_229135_1_, p_229135_2_, p_229135_3_, flag, p_229135_5_, p_229135_6_, p_229135_7_);
            p_229135_5_.pop();
        }
    }

    /**
     * 长矛（1.21.11）在第三人称除了手臂姿态之外，还有两段施加在<b>矛身</b>上的变换。
     * 对应官方 {@code ItemInHandLayer.submitArmWithItem}（ItemInHandLayer.java:47-54），
     * 顺序和条件照抄：先判挥击（{@code attackTime > 0} 且是主手且挥击类型是 STAB），
     * 再判举矛（{@code ticksUsingItem != 0} 时走 {@code ArmPose.SPEAR.animateUseItem}）。
     *
     * <p>缺这一段的表现是「胳膊在动，矛不动」—— 手臂姿态由 {@code BipedModel} 管，
     * 矛身归这里管，两者互不替代。
     */
    private static void applySpearItemAnimations(LivingEntity entity, ItemStack stack, HandSide side,
                                                 MatrixStack matrixStack, float partialTicks)
    {
        if (!(stack.getItem() instanceof SpearItem))
        {
            return;
        }

        SpearItem spear = (SpearItem) stack.getItem();
        // 官方 state.attackTime，1.16.4 的等价物是带插值的挥击进度。
        float swingProgress = entity.getSwingProgress(partialTicks);

        // 官方还要判 swingAnimationType == STAB；本项目只有长矛用 STAB 动画，
        // 而这里已经确认手持长矛，所以条件自动成立。
        if (swingProgress > 0.0F && entity.getPrimaryHand() == side)
        {
            SpearAnimations.thirdPersonAttackItem(matrixStack, swingProgress);
        }

        // 官方 state.ticksUsingItem(arm)：只在「这只手正在使用物品」时非 0。
        Hand usingHand = side == entity.getPrimaryHand() ? Hand.MAIN_HAND : Hand.OFF_HAND;
        if (entity.isHandActive() && entity.getActiveHand() == usingHand)
        {
            float useTicks = stack.getUseDuration() - entity.getItemInUseCount() + partialTicks;
            // ticksSinceHit 传 0：1.16.4 没有 ticksSinceKineticHitFeedback 这个同步状态，
            // 与 FirstPersonRenderer 里那处调用保持一致。
            SpearAnimations.thirdPersonUseItem(matrixStack, useTicks, swingProgress, 0.0F, side, spear);
        }
    }

    private boolean translateHand(HandSide sideIn, MatrixStack matrixStackIn)
    {
        this.getEntityModel().translateHand(sideIn, matrixStackIn);
        return false;
    }
}
