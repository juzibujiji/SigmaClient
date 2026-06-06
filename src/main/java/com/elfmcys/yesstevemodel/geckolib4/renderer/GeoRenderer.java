package com.elfmcys.yesstevemodel.geckolib4.renderer;

import com.elfmcys.yesstevemodel.geckolib4.cache.object.BakedGeoModel;
import com.elfmcys.yesstevemodel.geckolib4.cache.object.GeoBone;
import com.elfmcys.yesstevemodel.geckolib4.cache.object.GeoCube;
import com.elfmcys.yesstevemodel.geckolib4.cache.object.GeoQuad;
import com.elfmcys.yesstevemodel.geckolib4.cache.object.GeoVertex;
import com.elfmcys.yesstevemodel.geckolib4.compat.McpRenderAdapter;
import com.mojang.blaze3d.matrix.MatrixStack;
import com.mojang.blaze3d.vertex.IVertexBuilder;
import net.minecraft.util.math.vector.Matrix3f;
import net.minecraft.util.math.vector.Matrix4f;
import net.minecraft.util.math.vector.Vector3f;

/**
 * GeckoLib 4 style renderer core ported to MCP MatrixStack/IVertexBuilder.
 */
public interface GeoRenderer {
    default void render(BakedGeoModel model, MatrixStack poseStack, IVertexBuilder buffer,
                        int packedLight, int packedOverlay,
                        float red, float green, float blue, float alpha) {
        for (GeoBone bone : model.topLevelBones()) {
            renderRecursively(poseStack, bone, buffer, packedLight, packedOverlay, red, green, blue, alpha);
        }
    }

    default void renderRecursively(MatrixStack poseStack, GeoBone bone, IVertexBuilder buffer,
                                   int packedLight, int packedOverlay,
                                   float red, float green, float blue, float alpha) {
        // OpenYSM semantics: zero-scale bones are invisible and skip rendering entirely (including children).
        // This matches NativeModelRenderer.calculateBoneMatrix line 180-182.
        if (bone.getScaleX() == 0.0F && bone.getScaleY() == 0.0F && bone.getScaleZ() == 0.0F) {
            return;
        }

        poseStack.push();
        translateRotateScale(poseStack, bone);
        renderCubesOfBone(poseStack, bone, buffer, packedLight, packedOverlay, red, green, blue, alpha);

        if (!bone.isHidingChildren()) {
            for (GeoBone child : bone.getChildBones()) {
                renderRecursively(poseStack, child, buffer, packedLight, packedOverlay, red, green, blue, alpha);
            }
        }

        poseStack.pop();
    }

    default void renderCubesOfBone(MatrixStack poseStack, GeoBone bone, IVertexBuilder buffer,
                                   int packedLight, int packedOverlay,
                                   float red, float green, float blue, float alpha) {
        if (bone.isHidden()) {
            return;
        }

        for (GeoCube cube : bone.getCubes()) {
            renderCube(poseStack, cube, buffer, packedLight, packedOverlay, red, green, blue, alpha);
        }
    }

    default void renderCube(MatrixStack poseStack, GeoCube cube, IVertexBuilder buffer,
                            int packedLight, int packedOverlay,
                            float red, float green, float blue, float alpha) {
        Matrix4f pose = McpRenderAdapter.pose(poseStack);
        Matrix3f normalMatrix = McpRenderAdapter.normal(poseStack);

        for (GeoQuad quad : cube.quads()) {
            if (quad == null) {
                continue;
            }

            Vector3f normal = quad.normal();
            normal.transform(normalMatrix);
            fixInvertedFlatCube(cube, normal);
            createVerticesOfQuad(quad, pose, normal, buffer, packedLight, packedOverlay, red, green, blue, alpha);
        }
    }

    default void createVerticesOfQuad(GeoQuad quad, Matrix4f pose, Vector3f normal,
                                      IVertexBuilder buffer, int packedLight, int packedOverlay,
                                      float red, float green, float blue, float alpha) {
        for (GeoVertex vertex : quad.vertices()) {
            McpRenderAdapter.addVertex(buffer, pose, vertex.position(), red, green, blue, alpha,
                    vertex.textureU(), vertex.textureV(), packedOverlay, packedLight, normal);
        }
    }

    default void translateRotateScale(MatrixStack poseStack, GeoBone bone) {
        poseStack.translate(bone.getPosX() / 16.0F, bone.getPosY() / 16.0F, bone.getPosZ() / 16.0F);

        if (bone.getRotZ() != 0.0F) {
            poseStack.rotate(Vector3f.ZP.rotation(bone.getRotZ()));
        }
        if (bone.getRotY() != 0.0F) {
            poseStack.rotate(Vector3f.YP.rotation(bone.getRotY()));
        }
        if (bone.getRotX() != 0.0F) {
            poseStack.rotate(Vector3f.XP.rotation(bone.getRotX()));
        }

        if (bone.getScaleX() != 1.0F || bone.getScaleY() != 1.0F || bone.getScaleZ() != 1.0F) {
            poseStack.scale(bone.getScaleX(), bone.getScaleY(), bone.getScaleZ());
        }
    }

    default void fixInvertedFlatCube(GeoCube cube, Vector3f normal) {
        if (normal.getX() < 0.0F && (cube.sizeY() == 0.0F || cube.sizeZ() == 0.0F)) {
            normal.mul(-1.0F, 1.0F, 1.0F);
        }
        if (normal.getY() < 0.0F && (cube.sizeX() == 0.0F || cube.sizeZ() == 0.0F)) {
            normal.mul(1.0F, -1.0F, 1.0F);
        }
        if (normal.getZ() < 0.0F && (cube.sizeX() == 0.0F || cube.sizeY() == 0.0F)) {
            normal.mul(1.0F, 1.0F, -1.0F);
        }
    }
}
