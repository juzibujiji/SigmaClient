package com.elfmcys.yesstevemodel.geckolib4.renderer;

import com.elfmcys.yesstevemodel.geckolib4.cache.object.BakedGeoModel;
import com.elfmcys.yesstevemodel.geckolib4.compat.McpRenderAdapter;
import com.elfmcys.yesstevemodel.geckolib4.core.animatable.GeoAnimatable;
import com.elfmcys.yesstevemodel.geckolib4.core.animation.AnimationState;
import com.elfmcys.yesstevemodel.geckolib4.model.GeoModel;
import com.elfmcys.yesstevemodel.geckolib4.renderer.layer.GeoRenderLayer;
import com.elfmcys.yesstevemodel.geckolib4.renderer.layer.GeoRenderLayersContainer;
import com.mojang.blaze3d.matrix.MatrixStack;
import com.mojang.blaze3d.vertex.IVertexBuilder;
import net.minecraft.client.renderer.IRenderTypeBuffer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererManager;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.entity.Entity;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.vector.Vector3f;

import java.util.List;

/**
 * GeckoLib 4 style entity renderer backed by MCP EntityRenderer.
 */
public class GeoEntityRenderer<T extends Entity & GeoAnimatable> extends EntityRenderer<T> implements GeoRenderer {
    protected final GeoRenderLayersContainer renderLayers = new GeoRenderLayersContainer();
    protected final GeoModel<T> model;
    protected T animatable;
    protected float scaleWidth = 1.0F;
    protected float scaleHeight = 1.0F;

    public GeoEntityRenderer(EntityRendererManager renderManager, GeoModel<T> model) {
        super(renderManager);
        this.model = model;
    }

    public GeoModel<T> getGeoModel() {
        return this.model;
    }

    public T getAnimatable() {
        return this.animatable;
    }

    public long getInstanceId(T animatable) {
        return animatable.getEntityId();
    }

    public List<GeoRenderLayer> getRenderLayers() {
        return this.renderLayers.getRenderLayers();
    }

    public GeoEntityRenderer<T> addRenderLayer(GeoRenderLayer renderLayer) {
        this.renderLayers.addLayer(renderLayer);
        return this;
    }

    public GeoEntityRenderer<T> withScale(float scale) {
        return withScale(scale, scale);
    }

    public GeoEntityRenderer<T> withScale(float scaleWidth, float scaleHeight) {
        this.scaleWidth = scaleWidth;
        this.scaleHeight = scaleHeight;
        return this;
    }

    @Override
    public ResourceLocation getEntityTexture(T entity) {
        return this.model.getTextureResource(entity, this);
    }

    @Override
    public void render(T entity, float entityYaw, float partialTicks, MatrixStack matrixStackIn,
                       IRenderTypeBuffer bufferIn, int packedLightIn) {
        this.animatable = entity;
        matrixStackIn.push();
        try {
            applyEntityRotations(entity, matrixStackIn, entityYaw, partialTicks);
            matrixStackIn.scale(this.scaleWidth, this.scaleHeight, this.scaleWidth);
            renderGeoModel(entity, matrixStackIn, bufferIn, partialTicks, packedLightIn, OverlayTexture.NO_OVERLAY,
                    1.0F, 1.0F, 1.0F, 1.0F);
        } finally {
            matrixStackIn.pop();
            this.animatable = null;
        }
        super.render(entity, entityYaw, partialTicks, matrixStackIn, bufferIn, packedLightIn);
    }

    protected void applyEntityRotations(T entity, MatrixStack matrixStack, float entityYaw, float partialTicks) {
        matrixStack.rotate(Vector3f.YP.rotationDegrees(180.0F - entityYaw));
    }

    protected void renderGeoModel(T animatable, MatrixStack poseStack, IRenderTypeBuffer bufferSource,
                                  float partialTick, int packedLight, int packedOverlay,
                                  float red, float green, float blue, float alpha) {
        AnimationState<T> animationState = new AnimationState<>(animatable, 0.0F, 0.0F, partialTick,
                animatable.getMotion().lengthSquared() > 1.0E-7D);
        long instanceId = getInstanceId(animatable);
        this.model.addAdditionalStateData(animatable, instanceId, animationState::setData);
        this.model.handleAnimations(animatable, instanceId, animationState);

        BakedGeoModel bakedModel = this.model.getBakedModel(this.model.getModelResource(animatable, this));
        ResourceLocation texture = this.model.getTextureResource(animatable, this);
        RenderType renderType = this.model.getRenderType(animatable, texture, bufferSource, partialTick);
        IVertexBuilder buffer = McpRenderAdapter.buffer(bufferSource, renderType);

        for (GeoRenderLayer layer : this.renderLayers.getRenderLayers()) {
            layer.preRender(poseStack, bakedModel, renderType, bufferSource, buffer, partialTick, packedLight, packedOverlay);
        }

        GeoRenderer.super.render(bakedModel, poseStack, buffer, packedLight, packedOverlay, red, green, blue, alpha);

        for (GeoRenderLayer layer : this.renderLayers.getRenderLayers()) {
            layer.render(poseStack, bakedModel, renderType, bufferSource, buffer, partialTick, packedLight, packedOverlay);
        }
    }
}
