package net.minecraft.client.renderer.entity.model;

import com.google.common.collect.ImmutableList;
import com.mentalfrostbyte.jello.managers.ViaManager;
import com.mojang.blaze3d.matrix.MatrixStack;
import java.util.function.Function;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.SpearAnimations;
import net.minecraft.client.renderer.model.ModelHelper;
import net.minecraft.client.renderer.model.ModelRenderer;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.Item;
import net.minecraft.item.SpearItem;
import net.minecraft.util.Hand;
import net.minecraft.util.HandSide;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.MathHelper;

public class BipedModel<T extends LivingEntity> extends AgeableModel<T> implements IHasArm, IHasHead
{
    public ModelRenderer bipedHead;

    /** The Biped's Headwear. Used for the outer layer of player skins. */
    public ModelRenderer bipedHeadwear;
    public ModelRenderer bipedBody;

    /** The Biped's Right Arm */
    public ModelRenderer bipedRightArm;

    /** The Biped's Left Arm */
    public ModelRenderer bipedLeftArm;

    /** The Biped's Right Leg */
    public ModelRenderer bipedRightLeg;

    /** The Biped's Left Leg */
    public ModelRenderer bipedLeftLeg;
    public BipedModel.ArmPose leftArmPose = BipedModel.ArmPose.EMPTY;
    public BipedModel.ArmPose rightArmPose = BipedModel.ArmPose.EMPTY;
    public boolean isSneak;
    public float swimAnimation;

    public BipedModel(float modelSize)
    {
        this(RenderType::getEntityCutoutNoCull, modelSize, 0.0F, 64, 32);
    }

    protected BipedModel(float modelSize, float yOffsetIn, int textureWidthIn, int textureHeightIn)
    {
        this(RenderType::getEntityCutoutNoCull, modelSize, yOffsetIn, textureWidthIn, textureHeightIn);
    }

    public BipedModel(Function<ResourceLocation, RenderType> renderTypeIn, float modelSizeIn, float yOffsetIn, int textureWidthIn, int textureHeightIn)
    {
        super(renderTypeIn, true, 16.0F, 0.0F, 2.0F, 2.0F, 24.0F);
        this.textureWidth = textureWidthIn;
        this.textureHeight = textureHeightIn;
        this.bipedHead = new ModelRenderer(this, 0, 0);
        this.bipedHead.addBox(-4.0F, -8.0F, -4.0F, 8.0F, 8.0F, 8.0F, modelSizeIn);
        this.bipedHead.setRotationPoint(0.0F, 0.0F + yOffsetIn, 0.0F);
        this.bipedHeadwear = new ModelRenderer(this, 32, 0);
        this.bipedHeadwear.addBox(-4.0F, -8.0F, -4.0F, 8.0F, 8.0F, 8.0F, modelSizeIn + 0.5F);
        this.bipedHeadwear.setRotationPoint(0.0F, 0.0F + yOffsetIn, 0.0F);
        this.bipedBody = new ModelRenderer(this, 16, 16);
        this.bipedBody.addBox(-4.0F, 0.0F, -2.0F, 8.0F, 12.0F, 4.0F, modelSizeIn);
        this.bipedBody.setRotationPoint(0.0F, 0.0F + yOffsetIn, 0.0F);
        this.bipedRightArm = new ModelRenderer(this, 40, 16);
        this.bipedRightArm.addBox(-3.0F, -2.0F, -2.0F, 4.0F, 12.0F, 4.0F, modelSizeIn);
        this.bipedRightArm.setRotationPoint(-5.0F, 2.0F + yOffsetIn, 0.0F);
        this.bipedLeftArm = new ModelRenderer(this, 40, 16);
        this.bipedLeftArm.mirror = true;
        this.bipedLeftArm.addBox(-1.0F, -2.0F, -2.0F, 4.0F, 12.0F, 4.0F, modelSizeIn);
        this.bipedLeftArm.setRotationPoint(5.0F, 2.0F + yOffsetIn, 0.0F);
        this.bipedRightLeg = new ModelRenderer(this, 0, 16);
        this.bipedRightLeg.addBox(-2.0F, 0.0F, -2.0F, 4.0F, 12.0F, 4.0F, modelSizeIn);
        this.bipedRightLeg.setRotationPoint(-1.9F, 12.0F + yOffsetIn, 0.0F);
        this.bipedLeftLeg = new ModelRenderer(this, 0, 16);
        this.bipedLeftLeg.mirror = true;
        this.bipedLeftLeg.addBox(-2.0F, 0.0F, -2.0F, 4.0F, 12.0F, 4.0F, modelSizeIn);
        this.bipedLeftLeg.setRotationPoint(1.9F, 12.0F + yOffsetIn, 0.0F);
    }

    protected Iterable<ModelRenderer> getHeadParts()
    {
        return ImmutableList.of(this.bipedHead);
    }

    protected Iterable<ModelRenderer> getBodyParts()
    {
        return ImmutableList.of(this.bipedBody, this.bipedRightArm, this.bipedLeftArm, this.bipedRightLeg, this.bipedLeftLeg, this.bipedHeadwear);
    }

    public void setLivingAnimations(T entityIn, float limbSwing, float limbSwingAmount, float partialTick)
    {
        this.swimAnimation = entityIn.getSwimAnimation(partialTick);
        super.setLivingAnimations(entityIn, limbSwing, limbSwingAmount, partialTick);
    }

    /**
     * Sets this entity's model rotation angles
     */
    public void setRotationAngles(T entityIn, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch)
    {
        boolean flag = entityIn.getTicksElytraFlying() > 4;
        boolean flag1 = entityIn.isActualySwimming();
        this.bipedHead.rotateAngleY = netHeadYaw * ((float)Math.PI / 180F);

        if (flag)
        {
            this.bipedHead.rotateAngleX = (-(float)Math.PI / 4F);
        }
        else if (this.swimAnimation > 0.0F)
        {
            if (flag1)
            {
                this.bipedHead.rotateAngleX = this.rotLerpRad(this.swimAnimation, this.bipedHead.rotateAngleX, (-(float)Math.PI / 4F));
            }
            else
            {
                this.bipedHead.rotateAngleX = this.rotLerpRad(this.swimAnimation, this.bipedHead.rotateAngleX, headPitch * ((float)Math.PI / 180F));
            }
        }
        else
        {
            this.bipedHead.rotateAngleX = headPitch * ((float)Math.PI / 180F);
        }

        this.bipedBody.rotateAngleY = 0.0F;
        this.bipedRightArm.rotationPointZ = 0.0F;
        this.bipedRightArm.rotationPointX = -5.0F;
        this.bipedLeftArm.rotationPointZ = 0.0F;
        this.bipedLeftArm.rotationPointX = 5.0F;
        float f = 1.0F;

        if (flag)
        {
            f = (float)entityIn.getMotion().lengthSquared();
            f = f / 0.2F;
            f = f * f * f;
        }

        if (f < 1.0F)
        {
            f = 1.0F;
        }

        this.bipedRightArm.rotateAngleX = MathHelper.cos(limbSwing * 0.6662F + (float)Math.PI) * 2.0F * limbSwingAmount * 0.5F / f;
        this.bipedLeftArm.rotateAngleX = MathHelper.cos(limbSwing * 0.6662F) * 2.0F * limbSwingAmount * 0.5F / f;
        this.bipedRightArm.rotateAngleZ = 0.0F;
        this.bipedLeftArm.rotateAngleZ = 0.0F;
        this.bipedRightLeg.rotateAngleX = MathHelper.cos(limbSwing * 0.6662F) * 1.4F * limbSwingAmount / f;
        this.bipedLeftLeg.rotateAngleX = MathHelper.cos(limbSwing * 0.6662F + (float)Math.PI) * 1.4F * limbSwingAmount / f;
        this.bipedRightLeg.rotateAngleY = 0.0F;
        this.bipedLeftLeg.rotateAngleY = 0.0F;
        this.bipedRightLeg.rotateAngleZ = 0.0F;
        this.bipedLeftLeg.rotateAngleZ = 0.0F;

        if (this.isSitting)
        {
            this.bipedRightArm.rotateAngleX += (-(float)Math.PI / 5F);
            this.bipedLeftArm.rotateAngleX += (-(float)Math.PI / 5F);
            this.bipedRightLeg.rotateAngleX = -1.4137167F;
            this.bipedRightLeg.rotateAngleY = ((float)Math.PI / 10F);
            this.bipedRightLeg.rotateAngleZ = 0.07853982F;
            this.bipedLeftLeg.rotateAngleX = -1.4137167F;
            this.bipedLeftLeg.rotateAngleY = (-(float)Math.PI / 10F);
            this.bipedLeftLeg.rotateAngleZ = -0.07853982F;
        }

        this.bipedRightArm.rotateAngleY = 0.0F;
        this.bipedLeftArm.rotateAngleY = 0.0F;
        boolean flag2 = entityIn.getPrimaryHand() == HandSide.RIGHT;
        boolean flag3 = flag2 ? this.leftArmPose.func_241657_a_() : this.rightArmPose.func_241657_a_();

        if (flag2 != flag3)
        {
            this.func_241655_c_(entityIn);
            this.func_241654_b_(entityIn);
        }
        else
        {
            this.func_241654_b_(entityIn);
            this.func_241655_c_(entityIn);
        }

        this.func_230486_a_(entityIn, ageInTicks);

        if (this.isSneak)
        {
            this.bipedBody.rotateAngleX = 0.5F;
            this.bipedRightArm.rotateAngleX += 0.4F;
            this.bipedLeftArm.rotateAngleX += 0.4F;
            this.bipedRightLeg.rotationPointZ = 4.0F;
            this.bipedLeftLeg.rotationPointZ = 4.0F;
            this.bipedRightLeg.rotationPointY = 12.2F;
            this.bipedLeftLeg.rotationPointY = 12.2F;
            this.bipedHead.rotationPointY = 4.2F;
            this.bipedBody.rotationPointY = 3.2F;
            this.bipedLeftArm.rotationPointY = 5.2F;
            this.bipedRightArm.rotationPointY = 5.2F;
        }
        else
        {
            this.bipedBody.rotateAngleX = 0.0F;
            this.bipedRightLeg.rotationPointZ = 0.1F;
            this.bipedLeftLeg.rotationPointZ = 0.1F;
            this.bipedRightLeg.rotationPointY = 12.0F;
            this.bipedLeftLeg.rotationPointY = 12.0F;
            this.bipedHead.rotationPointY = 0.0F;
            this.bipedBody.rotationPointY = 0.0F;
            this.bipedLeftArm.rotationPointY = 2.0F;
            this.bipedRightArm.rotationPointY = 2.0F;
        }

        // 官方 client/model/HumanoidModel#setupAnim 分别摆动两条手臂，好把举着望远镜的那条排除掉：
        //     if (rightArmPose != ArmPose.SPYGLASS) AnimationUtils.bobModelPart(this.rightArm, ageInTicks,  1.0F);
        //     if (leftArmPose  != ArmPose.SPYGLASS) AnimationUtils.bobModelPart(this.leftArm,  ageInTicks, -1.0F);
        // 1.16.4 的 ModelHelper.func_239101_a_ 是一次摆动两条、符号恰好就是这两个，
        // 所以在这里拆开写，而不去改那个被别处共用的 helper。
        if (this.rightArmPose != BipedModel.ArmPose.SPYGLASS)
        {
            this.bipedRightArm.rotateAngleZ += MathHelper.cos(ageInTicks * 0.09F) * 0.05F + 0.05F;
            this.bipedRightArm.rotateAngleX += MathHelper.sin(ageInTicks * 0.067F) * 0.05F;
        }

        if (this.leftArmPose != BipedModel.ArmPose.SPYGLASS)
        {
            this.bipedLeftArm.rotateAngleZ -= MathHelper.cos(ageInTicks * 0.09F) * 0.05F + 0.05F;
            this.bipedLeftArm.rotateAngleX -= MathHelper.sin(ageInTicks * 0.067F) * 0.05F;
        }

        if (this.swimAnimation > 0.0F)
        {
            float f1 = limbSwing % 26.0F;
            HandSide handside = this.getMainHand(entityIn);
            // 官方 HumanoidModel.java:227-228 在混合系数里多一项 `armPose != ArmPose.SPEAR`：
            // 举着长矛时游泳/爬行的手臂混合要完全关掉，否则举起来的矛会被插值拉回体侧。
            float f2 = this.rightArmPose != BipedModel.ArmPose.SPEAR
                    && !(handside == HandSide.RIGHT && this.swingProgress > 0.0F) ? this.swimAnimation : 0.0F;
            float f3 = this.leftArmPose != BipedModel.ArmPose.SPEAR
                    && !(handside == HandSide.LEFT && this.swingProgress > 0.0F) ? this.swimAnimation : 0.0F;

            if (f1 < 14.0F)
            {
                this.bipedLeftArm.rotateAngleX = this.rotLerpRad(f3, this.bipedLeftArm.rotateAngleX, 0.0F);
                this.bipedRightArm.rotateAngleX = MathHelper.lerp(f2, this.bipedRightArm.rotateAngleX, 0.0F);
                this.bipedLeftArm.rotateAngleY = this.rotLerpRad(f3, this.bipedLeftArm.rotateAngleY, (float)Math.PI);
                this.bipedRightArm.rotateAngleY = MathHelper.lerp(f2, this.bipedRightArm.rotateAngleY, (float)Math.PI);
                this.bipedLeftArm.rotateAngleZ = this.rotLerpRad(f3, this.bipedLeftArm.rotateAngleZ, (float)Math.PI + 1.8707964F * this.getArmAngleSq(f1) / this.getArmAngleSq(14.0F));
                this.bipedRightArm.rotateAngleZ = MathHelper.lerp(f2, this.bipedRightArm.rotateAngleZ, (float)Math.PI - 1.8707964F * this.getArmAngleSq(f1) / this.getArmAngleSq(14.0F));
            }
            else if (f1 >= 14.0F && f1 < 22.0F)
            {
                float f6 = (f1 - 14.0F) / 8.0F;
                this.bipedLeftArm.rotateAngleX = this.rotLerpRad(f3, this.bipedLeftArm.rotateAngleX, ((float)Math.PI / 2F) * f6);
                this.bipedRightArm.rotateAngleX = MathHelper.lerp(f2, this.bipedRightArm.rotateAngleX, ((float)Math.PI / 2F) * f6);
                this.bipedLeftArm.rotateAngleY = this.rotLerpRad(f3, this.bipedLeftArm.rotateAngleY, (float)Math.PI);
                this.bipedRightArm.rotateAngleY = MathHelper.lerp(f2, this.bipedRightArm.rotateAngleY, (float)Math.PI);
                this.bipedLeftArm.rotateAngleZ = this.rotLerpRad(f3, this.bipedLeftArm.rotateAngleZ, 5.012389F - 1.8707964F * f6);
                this.bipedRightArm.rotateAngleZ = MathHelper.lerp(f2, this.bipedRightArm.rotateAngleZ, 1.2707963F + 1.8707964F * f6);
            }
            else if (f1 >= 22.0F && f1 < 26.0F)
            {
                float f4 = (f1 - 22.0F) / 4.0F;
                this.bipedLeftArm.rotateAngleX = this.rotLerpRad(f3, this.bipedLeftArm.rotateAngleX, ((float)Math.PI / 2F) - ((float)Math.PI / 2F) * f4);
                this.bipedRightArm.rotateAngleX = MathHelper.lerp(f2, this.bipedRightArm.rotateAngleX, ((float)Math.PI / 2F) - ((float)Math.PI / 2F) * f4);
                this.bipedLeftArm.rotateAngleY = this.rotLerpRad(f3, this.bipedLeftArm.rotateAngleY, (float)Math.PI);
                this.bipedRightArm.rotateAngleY = MathHelper.lerp(f2, this.bipedRightArm.rotateAngleY, (float)Math.PI);
                this.bipedLeftArm.rotateAngleZ = this.rotLerpRad(f3, this.bipedLeftArm.rotateAngleZ, (float)Math.PI);
                this.bipedRightArm.rotateAngleZ = MathHelper.lerp(f2, this.bipedRightArm.rotateAngleZ, (float)Math.PI);
            }

            float f7 = 0.3F;
            float f5 = 0.33333334F;
            this.bipedLeftLeg.rotateAngleX = MathHelper.lerp(this.swimAnimation, this.bipedLeftLeg.rotateAngleX, 0.3F * MathHelper.cos(limbSwing * 0.33333334F + (float)Math.PI));
            this.bipedRightLeg.rotateAngleX = MathHelper.lerp(this.swimAnimation, this.bipedRightLeg.rotateAngleX, 0.3F * MathHelper.cos(limbSwing * 0.33333334F));
        }
        
        if (entityIn instanceof Entity && ViaManager.entities.contains(entityIn)) {
            this.bipedRightArm.rotateAngleX = this.bipedRightArm.rotateAngleX * 0.5F - (float) (Math.PI * 3.0 / 10.0);
            this.bipedRightArm.rotateAngleY = (float) (-Math.PI / 6);
        }

        this.bipedHeadwear.copyModelAngles(this.bipedHead);
    }

    private void func_241654_b_(T p_241654_1_)
    {
        switch (this.rightArmPose)
        {
            case EMPTY:
                this.bipedRightArm.rotateAngleY = 0.0F;
                break;

            case BLOCK:
                this.bipedRightArm.rotateAngleX = this.bipedRightArm.rotateAngleX * 0.5F - 0.9424779F;
                this.bipedRightArm.rotateAngleY = (-(float)Math.PI / 6F);
                break;

            case ITEM:
                this.bipedRightArm.rotateAngleX = this.bipedRightArm.rotateAngleX * 0.5F - ((float)Math.PI / 10F);
                this.bipedRightArm.rotateAngleY = 0.0F;
                break;

            case THROW_SPEAR:
                this.bipedRightArm.rotateAngleX = this.bipedRightArm.rotateAngleX * 0.5F - (float)Math.PI;
                this.bipedRightArm.rotateAngleY = 0.0F;
                break;

            case SPEAR:
                // 官方 HumanoidModel.poseRightArm 的 case SPEAR（HumanoidModel.java:309）
                SpearAnimations.thirdPersonHandUse(this.bipedRightArm, this.bipedHead, true,
                        getHeldSpear(p_241654_1_), p_241654_1_, this.swimAnimation);
                break;

            case BOW_AND_ARROW:
                this.bipedRightArm.rotateAngleY = -0.1F + this.bipedHead.rotateAngleY;
                this.bipedLeftArm.rotateAngleY = 0.1F + this.bipedHead.rotateAngleY + 0.4F;
                this.bipedRightArm.rotateAngleX = (-(float)Math.PI / 2F) + this.bipedHead.rotateAngleX;
                this.bipedLeftArm.rotateAngleX = (-(float)Math.PI / 2F) + this.bipedHead.rotateAngleX;
                break;

            case CROSSBOW_CHARGE:
                ModelHelper.func_239102_a_(this.bipedRightArm, this.bipedLeftArm, p_241654_1_, true);
                break;

            case CROSSBOW_HOLD:
                ModelHelper.func_239104_a_(this.bipedRightArm, this.bipedLeftArm, this.bipedHead, true);
                break;

            // ---- 1.17+ 物品移植，取自官方 client/model/HumanoidModel#poseRightArm ----
            case SPYGLASS:
                this.bipedRightArm.rotateAngleX = MathHelper.clamp(this.bipedHead.rotateAngleX - 1.9198622F - (p_241654_1_.isSneaking() ? ((float)Math.PI / 12F) : 0.0F), -2.4F, 3.3F);
                this.bipedRightArm.rotateAngleY = this.bipedHead.rotateAngleY - ((float)Math.PI / 12F);
                break;

            case TOOT_HORN:
                this.bipedRightArm.rotateAngleX = MathHelper.clamp(this.bipedHead.rotateAngleX, -1.2F, 1.2F) - 1.4835298F;
                this.bipedRightArm.rotateAngleY = this.bipedHead.rotateAngleY - ((float)Math.PI / 6F);
                break;

            case BRUSH:
                this.bipedRightArm.rotateAngleX = this.bipedRightArm.rotateAngleX * 0.5F - ((float)Math.PI / 5F);
                this.bipedRightArm.rotateAngleY = 0.0F;
        }
    }

    private void func_241655_c_(T p_241655_1_)
    {
        switch (this.leftArmPose)
        {
            case EMPTY:
                this.bipedLeftArm.rotateAngleY = 0.0F;
                break;

            case BLOCK:
                this.bipedLeftArm.rotateAngleX = this.bipedLeftArm.rotateAngleX * 0.5F - 0.9424779F;
                this.bipedLeftArm.rotateAngleY = ((float)Math.PI / 6F);
                break;

            case ITEM:
                this.bipedLeftArm.rotateAngleX = this.bipedLeftArm.rotateAngleX * 0.5F - ((float)Math.PI / 10F);
                this.bipedLeftArm.rotateAngleY = 0.0F;
                break;

            case THROW_SPEAR:
                this.bipedLeftArm.rotateAngleX = this.bipedLeftArm.rotateAngleX * 0.5F - (float)Math.PI;
                this.bipedLeftArm.rotateAngleY = 0.0F;
                break;

            case SPEAR:
                // 官方 HumanoidModel.poseLeftArm 的 case SPEAR（HumanoidModel.java:356）
                SpearAnimations.thirdPersonHandUse(this.bipedLeftArm, this.bipedHead, false,
                        getHeldSpear(p_241655_1_), p_241655_1_, this.swimAnimation);
                break;

            case BOW_AND_ARROW:
                this.bipedRightArm.rotateAngleY = -0.1F + this.bipedHead.rotateAngleY - 0.4F;
                this.bipedLeftArm.rotateAngleY = 0.1F + this.bipedHead.rotateAngleY;
                this.bipedRightArm.rotateAngleX = (-(float)Math.PI / 2F) + this.bipedHead.rotateAngleX;
                this.bipedLeftArm.rotateAngleX = (-(float)Math.PI / 2F) + this.bipedHead.rotateAngleX;
                break;

            case CROSSBOW_CHARGE:
                ModelHelper.func_239102_a_(this.bipedRightArm, this.bipedLeftArm, p_241655_1_, false);
                break;

            case CROSSBOW_HOLD:
                ModelHelper.func_239104_a_(this.bipedRightArm, this.bipedLeftArm, this.bipedHead, false);
                break;

            // ---- 1.17+ 物品移植，取自官方 client/model/HumanoidModel#poseLeftArm ----
            case SPYGLASS:
                this.bipedLeftArm.rotateAngleX = MathHelper.clamp(this.bipedHead.rotateAngleX - 1.9198622F - (p_241655_1_.isSneaking() ? ((float)Math.PI / 12F) : 0.0F), -2.4F, 3.3F);
                this.bipedLeftArm.rotateAngleY = this.bipedHead.rotateAngleY + ((float)Math.PI / 12F);
                break;

            case TOOT_HORN:
                this.bipedLeftArm.rotateAngleX = MathHelper.clamp(this.bipedHead.rotateAngleX, -1.2F, 1.2F) - 1.4835298F;
                this.bipedLeftArm.rotateAngleY = this.bipedHead.rotateAngleY + ((float)Math.PI / 6F);
                break;

            case BRUSH:
                this.bipedLeftArm.rotateAngleX = this.bipedLeftArm.rotateAngleX * 0.5F - ((float)Math.PI / 5F);
                this.bipedLeftArm.rotateAngleY = 0.0F;
        }
    }

    protected void func_230486_a_(T p_230486_1_, float p_230486_2_)
    {
        if (!(this.swingProgress <= 0.0F))
        {
            HandSide handside = this.getMainHand(p_230486_1_);
            ModelRenderer modelrenderer = this.getArmForSide(handside);
            float f = this.swingProgress;
            this.bipedBody.rotateAngleY = MathHelper.sin(MathHelper.sqrt(f) * ((float)Math.PI * 2F)) * 0.2F;

            if (handside == HandSide.LEFT)
            {
                this.bipedBody.rotateAngleY *= -1.0F;
            }

            this.bipedRightArm.rotationPointZ = MathHelper.sin(this.bipedBody.rotateAngleY) * 5.0F;
            this.bipedRightArm.rotationPointX = -MathHelper.cos(this.bipedBody.rotateAngleY) * 5.0F;
            this.bipedLeftArm.rotationPointZ = -MathHelper.sin(this.bipedBody.rotateAngleY) * 5.0F;
            this.bipedLeftArm.rotationPointX = MathHelper.cos(this.bipedBody.rotateAngleY) * 5.0F;
            this.bipedRightArm.rotateAngleY += this.bipedBody.rotateAngleY;
            this.bipedLeftArm.rotateAngleY += this.bipedBody.rotateAngleY;
            this.bipedLeftArm.rotateAngleX += this.bipedBody.rotateAngleY;

            // 长矛用 STAB 挥击曲线，不是原版的 WHACK —— 官方
            // HumanoidModel.setupAttackAnimation 里 switch (swingAnimationType) 的 case STAB
            // （client/model/HumanoidModel.java:395）。躯干旋转与手臂锚点的部分官方也是
            // 共用的，所以分支只从这里开始。
            if (getHeldSpear(p_230486_1_) != null)
            {
                SpearAnimations.thirdPersonAttackHand(this, this.swingProgress, handside);
                return;
            }

            f = 1.0F - this.swingProgress;
            f = f * f;
            f = f * f;
            f = 1.0F - f;
            float f1 = MathHelper.sin(f * (float)Math.PI);
            float f2 = MathHelper.sin(this.swingProgress * (float)Math.PI) * -(this.bipedHead.rotateAngleX - 0.7F) * 0.75F;
            modelrenderer.rotateAngleX = (float)((double)modelrenderer.rotateAngleX - ((double)f1 * 1.2D + (double)f2));
            modelrenderer.rotateAngleY += this.bipedBody.rotateAngleY * 2.0F;
            modelrenderer.rotateAngleZ += MathHelper.sin(this.swingProgress * (float)Math.PI) * -0.4F;
        }
    }

    protected float rotLerpRad(float angleIn, float maxAngleIn, float mulIn)
    {
        float f = (mulIn - maxAngleIn) % ((float)Math.PI * 2F);

        if (f < -(float)Math.PI)
        {
            f += ((float)Math.PI * 2F);
        }

        if (f >= (float)Math.PI)
        {
            f -= ((float)Math.PI * 2F);
        }

        return maxAngleIn + angleIn * f;
    }

    private float getArmAngleSq(float limbSwing)
    {
        return -65.0F * limbSwing + limbSwing * limbSwing;
    }

    public void setModelAttributes(BipedModel<T> modelIn)
    {
        super.copyModelAttributesTo(modelIn);
        modelIn.leftArmPose = this.leftArmPose;
        modelIn.rightArmPose = this.rightArmPose;
        modelIn.isSneak = this.isSneak;
        modelIn.bipedHead.copyModelAngles(this.bipedHead);
        modelIn.bipedHeadwear.copyModelAngles(this.bipedHeadwear);
        modelIn.bipedBody.copyModelAngles(this.bipedBody);
        modelIn.bipedRightArm.copyModelAngles(this.bipedRightArm);
        modelIn.bipedLeftArm.copyModelAngles(this.bipedLeftArm);
        modelIn.bipedRightLeg.copyModelAngles(this.bipedRightLeg);
        modelIn.bipedLeftLeg.copyModelAngles(this.bipedLeftLeg);
    }

    public void setVisible(boolean visible)
    {
        this.bipedHead.showModel = visible;
        this.bipedHeadwear.showModel = visible;
        this.bipedBody.showModel = visible;
        this.bipedRightArm.showModel = visible;
        this.bipedLeftArm.showModel = visible;
        this.bipedRightLeg.showModel = visible;
        this.bipedLeftLeg.showModel = visible;
    }

    public void translateHand(HandSide sideIn, MatrixStack matrixStackIn)
    {
        this.getArmForSide(sideIn).translateRotate(matrixStackIn);
    }

    protected ModelRenderer getArmForSide(HandSide side)
    {
        return side == HandSide.LEFT ? this.bipedLeftArm : this.bipedRightArm;
    }

    public ModelRenderer getModelHead()
    {
        return this.bipedHead;
    }

    /**
     * 拿着长矛就返回它，否则 null。官方靠 {@code SWING_ANIMATION} / {@code ATTACK_RANGE}
     * 数据组件判断，1.16.4 只能看物品类型。
     */
    private static SpearItem getHeldSpear(LivingEntity entityIn)
    {
        Item item = entityIn.getHeldItemMainhand().getItem();

        if (item instanceof SpearItem)
        {
            return (SpearItem)item;
        }

        item = entityIn.getHeldItemOffhand().getItem();
        return item instanceof SpearItem ? (SpearItem)item : null;
    }

    protected HandSide getMainHand(T entityIn)
    {
        HandSide handside = entityIn.getPrimaryHand();
        return entityIn.swingingHand == Hand.MAIN_HAND ? handside : handside.opposite();
    }

    public static enum ArmPose
    {
        EMPTY(false),
        ITEM(false),
        BLOCK(false),
        BOW_AND_ARROW(true),
        THROW_SPEAR(false),
        /**
         * 1.21.11 长矛。官方是 {@code HumanoidModel.ArmPose.SPEAR(false, true)}
         * ——不占双手，但有「使用中」动画。与上面的 {@code THROW_SPEAR}（三叉戟待投）无关。
         */
        SPEAR(false),
        // ---- 1.17+ item backports, official HumanoidModel.ArmPose:438-440 ----
        // 官方是 SPYGLASS(false, false) / TOOT_HORN(false, false) / BRUSH(false, false)，
        // 1.16.4 的 ArmPose 只有一个布尔（是否占双手），所以都取 false。
        // 注：PlayerRenderer#func_241741_a_ 已经在引用这三个常量了，这里补上定义。
        SPYGLASS(false),
        TOOT_HORN(false),
        BRUSH(false),
        CROSSBOW_CHARGE(true),
        CROSSBOW_HOLD(true);

        private final boolean field_241656_h_;

        private ArmPose(boolean p_i241257_3_)
        {
            this.field_241656_h_ = p_i241257_3_;
        }

        public boolean func_241657_a_()
        {
            return this.field_241656_h_;
        }
    }
}
