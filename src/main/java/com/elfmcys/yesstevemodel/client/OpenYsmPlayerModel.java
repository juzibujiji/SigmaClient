package com.elfmcys.yesstevemodel.client;

import com.elfmcys.yesstevemodel.capability.OpenYsmPlayerAnimationState;
import com.elfmcys.yesstevemodel.client.animation.ActiveAnimationSet;
import com.elfmcys.yesstevemodel.client.animation.AnimationRenderContext;
import com.elfmcys.yesstevemodel.client.animation.OpenYsmAnimationEventDispatcher;
import com.elfmcys.yesstevemodel.client.animation.PlayerStateSnapshot;
import com.mojang.blaze3d.matrix.MatrixStack;
import com.mojang.blaze3d.vertex.IVertexBuilder;
import net.minecraft.client.entity.player.AbstractClientPlayerEntity;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.model.PlayerModel;
import net.minecraft.client.renderer.model.ModelRenderer;
import net.minecraft.util.HandSide;
import net.minecraft.util.ResourceLocation;

import java.util.Map;

public final class OpenYsmPlayerModel extends PlayerModel<AbstractClientPlayerEntity> {
    private final OpenYsmBakedPlayerModel bakedModel;

    public OpenYsmPlayerModel(OpenYsmBakedPlayerModel bakedModel, boolean smallArms) {
        super(bakedModel.isAllCutout() ? RenderType::getEntityCutoutNoCull : RenderType::getEntityTranslucent,
                0.0F, smallArms);
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
        AnimationRenderContext context = OpenYsmPlayerModelState.isPreviewRender()
                ? AnimationRenderContext.PREVIEW : AnimationRenderContext.GAME;
        float animationAgeInTicks = OpenYsmPlayerModelState.previewAgeInTicks(ageInTicks);
        this.bakedModel.getBones().values().forEach(OpenYsmBone::resetPose);
        copyPosePreferControl("Head", "MHead", this.bipedHead);
        copyPosePreferControl("UpperBody", "MUpperBody", this.bipedBody);
        copyPosePreferControl("RightArm", "MRightArm", this.bipedRightArm);
        copyPosePreferControl("LeftArm", "MLeftArm", this.bipedLeftArm);
        copyPosePreferControl("RightLeg", "MRightLeg", this.bipedRightLeg);
        copyPosePreferControl("LeftLeg", "MLeftLeg", this.bipedLeftLeg);

        PlayerStateSnapshot snapshot = PlayerStateSnapshot.capture(entityIn, limbSwingAmount, animationAgeInTicks,
                netHeadYaw, headPitch);
        OpenYsmPlayerAnimationState.State extraState = OpenYsmPlayerAnimationState.get(entityIn);
        ActiveAnimationSet active = this.bakedModel.getAnimations()
                .resolveActive(snapshot, extraState, context);
        OpenYsmAnimationEventDispatcher.dispatch(this.bakedModel, entityIn, active, snapshot);
        this.bakedModel.getAnimations().apply(this.bakedModel.getBones(), active, 0.0F);
        OpenYsmDebugLogger.logActiveState(this.bakedModel, active);
    }

    @Override
    public void render(MatrixStack matrixStackIn, IVertexBuilder bufferIn, int packedLightIn, int packedOverlayIn,
                       float red, float green, float blue, float alpha) {
        matrixStackIn.push();
        try {
            matrixStackIn.translate(0.0D, this.bakedModel.getGroundOffsetY(), 0.0D);
            matrixStackIn.scale(this.bakedModel.getWidthScale(), this.bakedModel.getHeightScale(), this.bakedModel.getWidthScale());
            for (OpenYsmBone bone : this.bakedModel.getRootBones()) {
                bone.getRenderer().render(matrixStackIn, bufferIn, packedLightIn, packedOverlayIn, red, green, blue, alpha);
            }
        } finally {
            matrixStackIn.pop();
        }
    }

    public boolean renderFirstPersonArm(AbstractClientPlayerEntity entityIn, boolean rightArm, float ageInTicks,
                                        MatrixStack matrixStackIn, IVertexBuilder bufferIn, int packedLightIn,
                                        int packedOverlayIn, float red, float green, float blue, float alpha) {
        if (!this.bakedModel.hasCustomArmModel()) {
            return false;
        }

        Map<String, OpenYsmBone> armBones = this.bakedModel.getArmBones();
        armBones.values().forEach(OpenYsmBone::resetPose);
        PlayerStateSnapshot snapshot = PlayerStateSnapshot.capture(entityIn, 0.0F, ageInTicks);
        OpenYsmPlayerAnimationState.State extraState = OpenYsmPlayerAnimationState.get(entityIn);
        ActiveAnimationSet active = this.bakedModel.getAnimations()
                .resolveActive(snapshot, extraState, AnimationRenderContext.FIRST_PERSON_ARM);
        this.bakedModel.getAnimations().apply(armBones, active, 0.0F);

        OpenYsmBone armBone = getArmBone(armBones, rightArm);
        if (armBone == null) {
            return false;
        }

        matrixStackIn.push();
        try {
            matrixStackIn.scale(this.bakedModel.getWidthScale(), this.bakedModel.getHeightScale(), this.bakedModel.getWidthScale());
            armBone.getRenderer().render(matrixStackIn, bufferIn, packedLightIn, packedOverlayIn, red, green, blue, alpha);
            return true;
        } finally {
            matrixStackIn.pop();
        }
    }

    @Override
    public void translateHand(HandSide sideIn, MatrixStack matrixStackIn) {
        translateItemHand(sideIn, matrixStackIn);
    }

    public boolean translateItemHand(HandSide sideIn, MatrixStack matrixStackIn) {
        OpenYsmBone locatorBone = getHandLocatorBone(this.bakedModel.getBones(), sideIn == HandSide.RIGHT);
        if (locatorBone != null) {
            translateYsmBone(locatorBone, matrixStackIn);
            return true;
        }

        OpenYsmBone armBone = getArmBone(this.bakedModel.getBones(), sideIn == HandSide.RIGHT);
        if (armBone == null) {
            super.translateHand(sideIn, matrixStackIn);
            return false;
        }
        translateYsmBone(armBone, matrixStackIn);
        return false;
    }

    private void translateYsmBone(OpenYsmBone bone, MatrixStack matrixStackIn) {
        matrixStackIn.translate(0.0D, this.bakedModel.getGroundOffsetY(), 0.0D);
        matrixStackIn.scale(this.bakedModel.getWidthScale(), this.bakedModel.getHeightScale(), this.bakedModel.getWidthScale());
        bone.translateRotateChain(matrixStackIn);
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

    private static OpenYsmBone getArmBone(Map<String, OpenYsmBone> bones, boolean rightArm) {
        OpenYsmBone armBone = bones.get(rightArm ? "RightArm" : "LeftArm");
        if (armBone != null) {
            return armBone;
        }
        return bones.get(rightArm ? "MRightArm" : "MLeftArm");
    }

    private static OpenYsmBone getHandLocatorBone(Map<String, OpenYsmBone> bones, boolean rightArm) {
        String[] names = rightArm
                ? new String[]{"RightHand", "MRightHand", "rightHand", "right_hand", "RightHandItem", "rightHandItem", "right_hand_item"}
                : new String[]{"LeftHand", "MLeftHand", "leftHand", "left_hand", "LeftHandItem", "leftHandItem", "left_hand_item"};
        for (String name : names) {
            OpenYsmBone bone = bones.get(name);
            if (bone != null) {
                return bone;
            }
        }
        return null;
    }
}
