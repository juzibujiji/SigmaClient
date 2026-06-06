package com.elfmcys.yesstevemodel.geckolib4.renderer;

import com.elfmcys.yesstevemodel.geckolib4.cache.object.BakedGeoModel;
import com.elfmcys.yesstevemodel.geckolib4.cache.object.GeoBone;
import com.elfmcys.yesstevemodel.geckolib4.core.animatable.GeoAnimatable;
import com.elfmcys.yesstevemodel.geckolib4.core.animation.AnimationState;
import com.elfmcys.yesstevemodel.geckolib4.model.GeoModel;
import com.elfmcys.yesstevemodel.geckolib4.renderer.layer.GeoRenderLayer;
import com.elfmcys.yesstevemodel.geckolib4.renderer.layer.GeoRenderLayersContainer;
import com.mojang.blaze3d.matrix.MatrixStack;
import com.mojang.blaze3d.vertex.IVertexBuilder;
import net.minecraft.client.renderer.IRenderTypeBuffer;
import net.minecraft.client.renderer.ItemRenderer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.model.BipedModel;
import net.minecraft.client.renderer.model.ModelRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.inventory.EquipmentSlotType;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;

import java.util.List;

/**
 * GeckoLib 4 style armor model renderer backed by MCP BipedModel.
 */
public class GeoArmorRenderer<T extends Item & GeoAnimatable> extends BipedModel<LivingEntity> implements GeoRenderer {
    protected final GeoRenderLayersContainer renderLayers = new GeoRenderLayersContainer();
    protected final GeoModel<T> model;
    protected T animatable;
    protected Entity currentEntity;
    protected ItemStack currentStack = ItemStack.EMPTY;
    protected EquipmentSlotType currentSlot;
    protected BipedModel<?> baseModel;
    protected float scaleWidth = 1.0F;
    protected float scaleHeight = 1.0F;

    public GeoArmorRenderer(GeoModel<T> model) {
        super(1.0F);
        this.model = model;
    }

    public GeoModel<T> getGeoModel() {
        return this.model;
    }

    public T getAnimatable() {
        return this.animatable;
    }

    public Entity getCurrentEntity() {
        return this.currentEntity;
    }

    public ItemStack getCurrentStack() {
        return this.currentStack;
    }

    public EquipmentSlotType getCurrentSlot() {
        return this.currentSlot;
    }

    public long getInstanceId(T animatable) {
        int entityId = this.currentEntity == null ? 0 : this.currentEntity.getEntityId();
        int slotId = this.currentSlot == null ? 0 : this.currentSlot.ordinal() + 1;
        return ((long) entityId << 8) ^ slotId ^ System.identityHashCode(this.currentStack);
    }

    public List<GeoRenderLayer> getRenderLayers() {
        return this.renderLayers.getRenderLayers();
    }

    public GeoArmorRenderer<T> addRenderLayer(GeoRenderLayer renderLayer) {
        this.renderLayers.addLayer(renderLayer);
        return this;
    }

    public GeoArmorRenderer<T> withScale(float scale) {
        return withScale(scale, scale);
    }

    public GeoArmorRenderer<T> withScale(float scaleWidth, float scaleHeight) {
        this.scaleWidth = scaleWidth;
        this.scaleHeight = scaleHeight;
        return this;
    }

    @SuppressWarnings("unchecked")
    public GeoArmorRenderer<T> prepForRender(Entity entity, ItemStack stack, EquipmentSlotType slot,
                                             BipedModel<?> baseModel) {
        this.currentEntity = entity;
        this.currentStack = stack == null ? ItemStack.EMPTY : stack;
        this.currentSlot = slot;
        this.baseModel = baseModel;
        this.animatable = stack != null && stack.getItem() instanceof GeoAnimatable ? (T) stack.getItem() : null;
        return this;
    }

    public RenderType getRenderType(T animatable, ResourceLocation texture,
                                    IRenderTypeBuffer bufferSource, float partialTick) {
        return RenderType.getArmorCutoutNoCull(texture);
    }

    @Override
    public void render(MatrixStack matrixStackIn, IVertexBuilder bufferIn, int packedLightIn, int packedOverlayIn,
                       float red, float green, float blue, float alpha) {
        if (this.animatable == null) {
            return;
        }

        BakedGeoModel bakedModel = this.model.getBakedModel(this.model.getModelResource(this.animatable, this));
        this.model.handleAnimations(this.animatable, getInstanceId(this.animatable),
                new AnimationState<>(this.animatable, 0.0F, 0.0F, 0.0F, false));
        syncArmorBones(bakedModel);

        matrixStackIn.push();
        try {
            matrixStackIn.scale(this.scaleWidth, this.scaleHeight, this.scaleWidth);
            GeoRenderer.super.render(bakedModel, matrixStackIn, bufferIn, packedLightIn, packedOverlayIn,
                    red, green, blue, alpha);
        } finally {
            matrixStackIn.pop();
        }
    }

    public void renderArmor(MatrixStack poseStack, IRenderTypeBuffer bufferSource, int packedLight, int packedOverlay,
                            float partialTick, float red, float green, float blue, float alpha) {
        if (this.animatable == null) {
            return;
        }

        BakedGeoModel bakedModel = this.model.getBakedModel(this.model.getModelResource(this.animatable, this));
        ResourceLocation texture = this.model.getTextureResource(this.animatable, this);
        RenderType renderType = getRenderType(this.animatable, texture, bufferSource, partialTick);
        IVertexBuilder vertexBuilder = ItemRenderer.getArmorVertexBuilder(bufferSource, renderType, false,
                this.currentStack.hasEffect());

        AnimationState<T> animationState = new AnimationState<>(this.animatable, 0.0F, 0.0F, partialTick, false);
        long instanceId = getInstanceId(this.animatable);
        this.model.addAdditionalStateData(this.animatable, instanceId, animationState::setData);
        this.model.handleAnimations(this.animatable, instanceId, animationState);
        syncArmorBones(bakedModel);

        for (GeoRenderLayer layer : this.renderLayers.getRenderLayers()) {
            layer.preRender(poseStack, bakedModel, renderType, bufferSource, vertexBuilder, partialTick, packedLight, packedOverlay);
        }

        GeoRenderer.super.render(bakedModel, poseStack, vertexBuilder, packedLight, packedOverlay,
                red, green, blue, alpha);

        for (GeoRenderLayer layer : this.renderLayers.getRenderLayers()) {
            layer.render(poseStack, bakedModel, renderType, bufferSource, vertexBuilder, partialTick, packedLight, packedOverlay);
        }
    }

    public GeoBone getHeadBone() {
        return this.model.getBone("armorHead").orElse(null);
    }

    public GeoBone getBodyBone() {
        return this.model.getBone("armorBody").orElse(null);
    }

    public GeoBone getRightArmBone() {
        return this.model.getBone("armorRightArm").orElse(null);
    }

    public GeoBone getLeftArmBone() {
        return this.model.getBone("armorLeftArm").orElse(null);
    }

    public GeoBone getRightLegBone() {
        return this.model.getBone("armorRightLeg").orElse(null);
    }

    public GeoBone getLeftLegBone() {
        return this.model.getBone("armorLeftLeg").orElse(null);
    }

    public GeoBone getRightBootBone() {
        return this.model.getBone("armorRightBoot").orElse(null);
    }

    public GeoBone getLeftBootBone() {
        return this.model.getBone("armorLeftBoot").orElse(null);
    }

    protected void syncArmorBones(BakedGeoModel bakedModel) {
        bakedModel.resetPose();
        copyPose(getHeadBone(), this.bipedHead);
        copyPose(getBodyBone(), this.bipedBody);
        copyPose(getRightArmBone(), this.bipedRightArm);
        copyPose(getLeftArmBone(), this.bipedLeftArm);
        copyPose(getRightLegBone(), this.bipedRightLeg);
        copyPose(getLeftLegBone(), this.bipedLeftLeg);
        copyPose(getRightBootBone(), this.bipedRightLeg);
        copyPose(getLeftBootBone(), this.bipedLeftLeg);
    }

    protected void copyPose(GeoBone bone, ModelRenderer source) {
        if (bone == null || source == null) {
            return;
        }
        bone.setRotation(source.rotateAngleX, source.rotateAngleY, source.rotateAngleZ);
        bone.setPosition(source.rotationPointX, source.rotationPointY, source.rotationPointZ);
        bone.setHidden(!source.showModel);
    }

    public void clearCurrentContext() {
        this.animatable = null;
        this.currentEntity = null;
        this.currentStack = ItemStack.EMPTY;
        this.currentSlot = null;
        this.baseModel = null;
    }
}
