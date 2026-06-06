package com.elfmcys.yesstevemodel.geckolib4.compat;

import com.mojang.blaze3d.matrix.MatrixStack;
import com.mojang.blaze3d.vertex.IVertexBuilder;
import net.minecraft.client.renderer.IRenderTypeBuffer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.vector.Matrix3f;
import net.minecraft.util.math.vector.Matrix4f;
import net.minecraft.util.math.vector.Vector3f;

/**
 * Centralized MCP 1.16.4 adapter for GeckoLib 4 render API concepts.
 */
public final class McpRenderAdapter {
    private McpRenderAdapter() {
    }

    public static RenderType entityCutoutNoCull(ResourceLocation texture) {
        return RenderType.getEntityCutoutNoCull(texture);
    }

    public static IVertexBuilder buffer(IRenderTypeBuffer buffers, RenderType renderType) {
        return buffers.getBuffer(renderType);
    }

    public static Matrix4f pose(MatrixStack stack) {
        return stack.getLast().getMatrix();
    }

    public static Matrix3f normal(MatrixStack stack) {
        return stack.getLast().getNormal();
    }

    public static void addVertex(IVertexBuilder buffer, Matrix4f pose, Vector3f position,
                                 float red, float green, float blue, float alpha,
                                 float textureU, float textureV, int packedOverlay, int packedLight,
                                 Vector3f normal) {
        float x = position.getX() / 16.0F;
        float y = position.getY() / 16.0F;
        float z = position.getZ() / 16.0F;
        buffer.addVertex(
                pose.getTransformX(x, y, z, 1.0F),
                pose.getTransformY(x, y, z, 1.0F),
                pose.getTransformZ(x, y, z, 1.0F),
                red, green, blue, alpha,
                textureU, textureV,
                packedOverlay, packedLight,
                normal.getX(), normal.getY(), normal.getZ());
    }
}
