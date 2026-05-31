package com.elfmcys.yesstevemodel.client;

import com.elfmcys.yesstevemodel.capability.OpenYsmPlayerAnimationState;
import com.elfmcys.yesstevemodel.client.animation.ActiveAnimationSet;
import com.elfmcys.yesstevemodel.client.animation.AnimationRenderContext;
import com.elfmcys.yesstevemodel.client.animation.PlayerStateSnapshot;
import com.mojang.blaze3d.matrix.MatrixStack;
import com.mojang.blaze3d.vertex.IVertexBuilder;
import net.minecraft.client.entity.player.AbstractClientPlayerEntity;
import net.minecraft.client.renderer.entity.model.PlayerModel;
import net.minecraft.client.renderer.model.ModelRenderer;
import net.minecraft.util.ResourceLocation;

public final class OpenYsmPlayerModel extends PlayerModel<AbstractClientPlayerEntity> {
    private final OpenYsmBakedPlayerModel bakedModel;

    public OpenYsmPlayerModel(OpenYsmBakedPlayerModel bakedModel, boolean smallArms) {
        super(0.0F, smallArms);
        this.bakedModel = bakedModel;
    }

    public ResourceLocation getTexture() {
        return this.bakedModel.getTexture();
    }

    public OpenYsmBakedPlayerModel getBakedModel() {
        return this.bakedModel;
    }

    @Override
    public void setRotationAngles(AbstractClientPlayerEntity entityIn, float limbSwing, float limbSwingAmount,
                                  float ageInTicks, float netHeadYaw, float headPitch) {
        super.setRotationAngles(entityIn, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch);
        this.bakedModel.getBones().values().forEach(OpenYsmBone::resetPose);
        copyPosePreferControl("Head", "MHead", this.bipedHead);
        copyPosePreferControl("UpperBody", "MUpperBody", this.bipedBody);
        copyPosePreferControl("RightArm", "MRightArm", this.bipedRightArm);
        copyPosePreferControl("LeftArm", "MLeftArm", this.bipedLeftArm);
        copyPosePreferControl("RightLeg", "MRightLeg", this.bipedRightLeg);
        copyPosePreferControl("LeftLeg", "MLeftLeg", this.bipedLeftLeg);

        PlayerStateSnapshot snapshot = PlayerStateSnapshot.capture(entityIn, limbSwingAmount, ageInTicks);
        OpenYsmPlayerAnimationState.State extraState = OpenYsmPlayerAnimationState.get(entityIn);
        ActiveAnimationSet active = this.bakedModel.getAnimations()
                .resolveActive(snapshot, extraState, AnimationRenderContext.GAME);
        this.bakedModel.getAnimations().apply(this.bakedModel.getBones(), active, 0.0F);
    }

    @Override
    public void render(MatrixStack matrixStackIn, IVertexBuilder bufferIn, int packedLightIn, int packedOverlayIn,
                       float red, float green, float blue, float alpha) {
        matrixStackIn.push();
        try {
            matrixStackIn.scale(this.bakedModel.getWidthScale(), this.bakedModel.getHeightScale(), this.bakedModel.getWidthScale());
            for (OpenYsmBone bone : this.bakedModel.getRootBones()) {
                bone.getRenderer().render(matrixStackIn, bufferIn, packedLightIn, packedOverlayIn, red, green, blue, alpha);
            }
        } finally {
            matrixStackIn.pop();
        }
    }

    private void copyPosePreferControl(String boneName, String controlBoneName, ModelRenderer source) {
        if (!copyPose(controlBoneName, source)) {
            copyPose(boneName, source);
        }
    }

    private boolean copyPose(String boneName, ModelRenderer source) {
        OpenYsmBone bone = this.bakedModel.getBones().get(boneName);
        if (bone != null) {
            bone.addRotation(source.rotateAngleX, source.rotateAngleY, source.rotateAngleZ);
            return true;
        }
        return false;
    }
}
