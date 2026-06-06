package com.elfmcys.yesstevemodel.geckolib4.renderer;

import com.elfmcys.yesstevemodel.geckolib4.cache.object.BakedGeoModel;
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
import net.minecraft.client.renderer.model.ItemCameraTransforms;
import net.minecraft.client.renderer.tileentity.ItemStackTileEntityRenderer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;

import java.util.List;

/**
 * GeckoLib 4 style item renderer backed by MCP ItemStackTileEntityRenderer.
 */
public class GeoItemRenderer<T extends Item & GeoAnimatable> extends ItemStackTileEntityRenderer implements GeoRenderer {
    protected final GeoRenderLayersContainer renderLayers = new GeoRenderLayersContainer();
    protected final GeoModel<T> model;
    protected ItemStack currentItemStack = ItemStack.EMPTY;
    protected ItemCameraTransforms.TransformType renderPerspective = ItemCameraTransforms.TransformType.NONE;
    protected T animatable;
    protected float scaleWidth = 1.0F;
    protected float scaleHeight = 1.0F;

    public GeoItemRenderer(GeoModel<T> model) {
        this.model = model;
    }

    public GeoModel<T> getGeoModel() {
        return this.model;
    }

    public T getAnimatable() {
        return this.animatable;
    }

    public ItemStack getCurrentItemStack() {
        return this.currentItemStack;
    }

    public ItemCameraTransforms.TransformType getRenderPerspective() {
        return this.renderPerspective;
    }

    public long getInstanceId(T animatable) {
        return System.identityHashCode(this.currentItemStack);
    }

    public List<GeoRenderLayer> getRenderLayers() {
        return this.renderLayers.getRenderLayers();
    }

    public GeoItemRenderer<T> addRenderLayer(GeoRenderLayer renderLayer) {
        this.renderLayers.addLayer(renderLayer);
        return this;
    }

    public GeoItemRenderer<T> withScale(float scale) {
        return withScale(scale, scale);
    }

    public GeoItemRenderer<T> withScale(float scaleWidth, float scaleHeight) {
        this.scaleWidth = scaleWidth;
        this.scaleHeight = scaleHeight;
        return this;
    }

    @Override
    @SuppressWarnings("unchecked")
    public void func_239207_a_(ItemStack stack, ItemCameraTransforms.TransformType transformType,
                               MatrixStack matrixStack, IRenderTypeBuffer buffer, int combinedLight,
                               int combinedOverlay) {
        if (!(stack.getItem() instanceof GeoAnimatable)) {
            return;
        }

        this.currentItemStack = stack;
        this.renderPerspective = transformType;
        this.animatable = (T) stack.getItem();

        matrixStack.push();
        try {
            matrixStack.scale(this.scaleWidth, this.scaleHeight, this.scaleWidth);
            renderGeoModel(this.animatable, matrixStack, buffer, 0.0F, combinedLight, combinedOverlay,
                    1.0F, 1.0F, 1.0F, 1.0F);
        } finally {
            matrixStack.pop();
            this.animatable = null;
            this.currentItemStack = ItemStack.EMPTY;
            this.renderPerspective = ItemCameraTransforms.TransformType.NONE;
        }
    }

    protected void renderGeoModel(T animatable, MatrixStack poseStack, IRenderTypeBuffer bufferSource,
                                  float partialTick, int packedLight, int packedOverlay,
                                  float red, float green, float blue, float alpha) {
        AnimationState<T> animationState = new AnimationState<>(animatable, 0.0F, 0.0F, partialTick, false);
        long instanceId = getInstanceId(animatable);
        this.model.addAdditionalStateData(animatable, instanceId, animationState::setData);
        this.model.handleAnimations(animatable, instanceId, animationState);

        BakedGeoModel bakedModel = this.model.getBakedModel(this.model.getModelResource(animatable, this));
        ResourceLocation texture = this.model.getTextureResource(animatable, this);
        RenderType renderType = this.model.getRenderType(animatable, texture, bufferSource, partialTick);
        IVertexBuilder vertexBuilder = ItemRenderer.getEntityGlintVertexBuilder(bufferSource, renderType, false,
                this.currentItemStack.hasEffect());

        for (GeoRenderLayer layer : this.renderLayers.getRenderLayers()) {
            layer.preRender(poseStack, bakedModel, renderType, bufferSource, vertexBuilder, partialTick, packedLight, packedOverlay);
        }

        GeoRenderer.super.render(bakedModel, poseStack, vertexBuilder, packedLight, packedOverlay, red, green, blue, alpha);

        for (GeoRenderLayer layer : this.renderLayers.getRenderLayers()) {
            layer.render(poseStack, bakedModel, renderType, bufferSource, vertexBuilder, partialTick, packedLight, packedOverlay);
        }
    }
}
