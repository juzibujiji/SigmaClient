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
import net.minecraft.block.DirectionalBlock;
import net.minecraft.block.HorizontalBlock;
import net.minecraft.block.HorizontalFaceBlock;
import net.minecraft.block.SixWayBlock;
import net.minecraft.block.WallTorchBlock;
import net.minecraft.block.BlockState;
import net.minecraft.client.renderer.IRenderTypeBuffer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.tileentity.TileEntityRenderer;
import net.minecraft.client.renderer.tileentity.TileEntityRendererDispatcher;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.Direction;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.vector.Vector3f;

import java.util.List;

/**
 * GeckoLib 4 style block-entity renderer backed by MCP TileEntityRenderer.
 */
public class GeoBlockRenderer<T extends TileEntity & GeoAnimatable> extends TileEntityRenderer<T> implements GeoRenderer {
    protected final GeoRenderLayersContainer renderLayers = new GeoRenderLayersContainer();
    protected final GeoModel<T> model;
    protected T animatable;
    protected float scaleWidth = 1.0F;
    protected float scaleHeight = 1.0F;

    public GeoBlockRenderer(GeoModel<T> model) {
        this(TileEntityRendererDispatcher.instance, model);
    }

    public GeoBlockRenderer(TileEntityRendererDispatcher rendererDispatcher, GeoModel<T> model) {
        super(rendererDispatcher);
        this.model = model;
    }

    public GeoModel<T> getGeoModel() {
        return this.model;
    }

    public T getAnimatable() {
        return this.animatable;
    }

    public long getInstanceId(T animatable) {
        return animatable.getPos().toLong();
    }

    public List<GeoRenderLayer> getRenderLayers() {
        return this.renderLayers.getRenderLayers();
    }

    public GeoBlockRenderer<T> addRenderLayer(GeoRenderLayer renderLayer) {
        this.renderLayers.addLayer(renderLayer);
        return this;
    }

    public GeoBlockRenderer<T> withScale(float scale) {
        return withScale(scale, scale);
    }

    public GeoBlockRenderer<T> withScale(float scaleWidth, float scaleHeight) {
        this.scaleWidth = scaleWidth;
        this.scaleHeight = scaleHeight;
        return this;
    }

    @Override
    public void render(T tileEntityIn, float partialTicks, MatrixStack matrixStackIn,
                       IRenderTypeBuffer bufferIn, int combinedLightIn, int combinedOverlayIn) {
        this.animatable = tileEntityIn;
        matrixStackIn.push();
        try {
            matrixStackIn.translate(0.5D, 0.0D, 0.5D);
            rotateBlock(getFacing(tileEntityIn), matrixStackIn);
            matrixStackIn.scale(this.scaleWidth, this.scaleHeight, this.scaleWidth);
            renderGeoModel(tileEntityIn, matrixStackIn, bufferIn, partialTicks, combinedLightIn, combinedOverlayIn,
                    1.0F, 1.0F, 1.0F, 1.0F);
        } finally {
            matrixStackIn.pop();
            this.animatable = null;
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
        IVertexBuilder buffer = McpRenderAdapter.buffer(bufferSource, renderType);

        for (GeoRenderLayer layer : this.renderLayers.getRenderLayers()) {
            layer.preRender(poseStack, bakedModel, renderType, bufferSource, buffer, partialTick, packedLight, packedOverlay);
        }

        GeoRenderer.super.render(bakedModel, poseStack, buffer, packedLight, packedOverlay, red, green, blue, alpha);

        for (GeoRenderLayer layer : this.renderLayers.getRenderLayers()) {
            layer.render(poseStack, bakedModel, renderType, bufferSource, buffer, partialTick, packedLight, packedOverlay);
        }
    }

    protected Direction getFacing(T blockEntity) {
        BlockState state = blockEntity.getBlockState();
        if (state.hasProperty(HorizontalBlock.HORIZONTAL_FACING)) {
            return state.get(HorizontalBlock.HORIZONTAL_FACING);
        }
        if (state.hasProperty(DirectionalBlock.FACING)) {
            return state.get(DirectionalBlock.FACING);
        }
        return Direction.NORTH;
    }

    protected void rotateBlock(Direction facing, MatrixStack poseStack) {
        switch (facing) {
            case SOUTH:
                poseStack.rotate(Vector3f.YP.rotationDegrees(180.0F));
                break;
            case WEST:
                poseStack.rotate(Vector3f.YP.rotationDegrees(90.0F));
                break;
            case EAST:
                poseStack.rotate(Vector3f.YP.rotationDegrees(270.0F));
                break;
            case UP:
                poseStack.rotate(Vector3f.XP.rotationDegrees(90.0F));
                break;
            case DOWN:
                poseStack.rotate(Vector3f.XP.rotationDegrees(-90.0F));
                break;
            case NORTH:
            default:
                break;
        }
    }
}
