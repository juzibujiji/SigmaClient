package com.elfmcys.yesstevemodel.geckolib4.renderer.layer;

import com.elfmcys.yesstevemodel.geckolib4.cache.object.BakedGeoModel;
import com.elfmcys.yesstevemodel.geckolib4.cache.object.GeoBone;
import com.elfmcys.yesstevemodel.geckolib4.renderer.GeoRenderer;
import com.mojang.blaze3d.matrix.MatrixStack;
import com.mojang.blaze3d.vertex.IVertexBuilder;
import net.minecraft.client.renderer.IRenderTypeBuffer;
import net.minecraft.client.renderer.RenderType;

/**
 * GeckoLib 4 style render layer base.
 */
public abstract class GeoRenderLayer {
    protected final GeoRenderer renderer;

    protected GeoRenderLayer(GeoRenderer renderer) {
        this.renderer = renderer;
    }

    public void preRender(MatrixStack poseStack, BakedGeoModel model, RenderType renderType,
                          IRenderTypeBuffer bufferSource, IVertexBuilder buffer,
                          float partialTick, int packedLight, int packedOverlay) {
    }

    public void render(MatrixStack poseStack, BakedGeoModel model, RenderType renderType,
                       IRenderTypeBuffer bufferSource, IVertexBuilder buffer,
                       float partialTick, int packedLight, int packedOverlay) {
    }

    public void renderForBone(MatrixStack poseStack, GeoBone bone, RenderType renderType,
                              IRenderTypeBuffer bufferSource, IVertexBuilder buffer,
                              float partialTick, int packedLight, int packedOverlay) {
    }
}
