package com.elfmcys.yesstevemodel.client;

import com.elfmcys.yesstevemodel.YesSteveModel;
import com.mojang.blaze3d.matrix.MatrixStack;
import com.mojang.blaze3d.vertex.IVertexBuilder;
import net.minecraft.client.renderer.model.ModelRenderer;
import net.minecraft.util.math.vector.Matrix3f;
import net.minecraft.util.math.vector.Matrix4f;
import net.minecraft.util.math.vector.Vector3f;

import java.util.ArrayList;
import java.util.List;

/**
 * Extended ModelRenderer that supports face-explicit custom quads from binary YSM models.
 *
 * <p>In practice, a bone will have EITHER standard cubes (JSON-format models that use addBox)
 * OR custom quads (binary-format models that use addCustomQuad), never both.
 * The render method is designed around this invariant.</p>
 */
public final class OpenYsmModelRenderer extends ModelRenderer {
    private static final float MAX_ABS_VERTEX_PIXELS = 8192.0F;
    private static final float MAX_ABS_UV = 128.0F;
    private static final int MAX_CUSTOM_QUAD_WARNINGS = 8;
    private final List<CustomQuad> customQuads = new ArrayList<>();
    private int customQuadWarningCount;
    private float scaleX = 1.0F;
    private float scaleY = 1.0F;
    private float scaleZ = 1.0F;

    public OpenYsmModelRenderer(int textureWidthIn, int textureHeightIn) {
        super(textureWidthIn, textureHeightIn, 0, 0);
    }

    public void addCustomQuad(CustomQuad quad) {
        this.customQuads.add(quad);
    }

    public void setScale(float sx, float sy, float sz) {
        this.scaleX = sx;
        this.scaleY = sy;
        this.scaleZ = sz;
    }

    @Override
    public void render(MatrixStack matrixStackIn, IVertexBuilder bufferIn, int packedLightIn, int packedOverlayIn,
                       float red, float green, float blue, float alpha) {
        if (!this.showModel) {
            return;
        }

        // OpenYSM semantics: zero-scale means invisible. This is used by parallel animations
        // to hide feature bones (e.g., parallel3 sets BeiJing.scale=[0,0,0] to hide立绘框).
        // Must check BEFORE matrix operations to avoid singular normal matrices that produce NaN/Inf.
        if (this.scaleX == 0.0F && this.scaleY == 0.0F && this.scaleZ == 0.0F) {
            return;
        }

        boolean hasCubes = !this.cubeList.isEmpty() || !this.spriteList.isEmpty();
        boolean hasCustomQuads = !this.customQuads.isEmpty();
        boolean hasChildren = !this.childModels.isEmpty();

        if (!hasCubes && !hasCustomQuads && !hasChildren) {
            return;
        }

        boolean hasNonIdentityScale = this.scaleX != 1.0F || this.scaleY != 1.0F || this.scaleZ != 1.0F;

        // If this bone only has standard cubes/sprites (no custom quads), delegate entirely
        // to the vanilla ModelRenderer which handles cubes, sprites, children, OptiFine, etc.
        // Scale needs an extra matrix push/scale wrap because vanilla ModelRenderer has no per-bone scale.
        if (hasCubes && !hasCustomQuads) {
            if (!hasNonIdentityScale) {
                super.render(matrixStackIn, bufferIn, packedLightIn, packedOverlayIn, red, green, blue, alpha);
                return;
            }
            matrixStackIn.push();
            try {
                matrixStackIn.scale(this.scaleX, this.scaleY, this.scaleZ);
                super.render(matrixStackIn, bufferIn, packedLightIn, packedOverlayIn, red, green, blue, alpha);
            } finally {
                matrixStackIn.pop();
            }
            return;
        }

        // Custom quads path (binary-format models).
        // Push/pop with try/finally to guarantee PoseStack balance even if rendering throws.
        matrixStackIn.push();
        try {
            this.translateRotate(matrixStackIn);
            if (hasNonIdentityScale) {
                matrixStackIn.scale(this.scaleX, this.scaleY, this.scaleZ);
            }

            if (hasCustomQuads) {
                this.renderCustomQuads(matrixStackIn.getLast(), bufferIn, packedLightIn, packedOverlayIn,
                        red, green, blue, alpha);
            }

            for (ModelRenderer childModel : this.childModels) {
                childModel.render(matrixStackIn, bufferIn, packedLightIn, packedOverlayIn, red, green, blue, alpha);
            }
        } finally {
            matrixStackIn.pop();
        }
    }

    private void renderCustomQuads(MatrixStack.Entry matrixEntryIn, IVertexBuilder bufferIn,
                                   int packedLightIn, int packedOverlayIn,
                                   float red, float green, float blue, float alpha) {
        Matrix4f posMatrix = matrixEntryIn.getMatrix();
        Matrix3f normalMatrix = matrixEntryIn.getNormal();

        for (int quadIndex = 0; quadIndex < this.customQuads.size(); quadIndex++) {
            CustomQuad quad = this.customQuads.get(quadIndex);
            // Guard against degenerate data that would produce visual corruption.
            if (!hasCompleteQuadArrays(quad)) {
                this.warnCustomQuad("[YSM][quad] bone='{}' quad={} incomplete quad arrays - skipping quad",
                        this.getId(), quadIndex);
                continue;
            }
            if (!isFiniteVector(quad.normal) || isZeroVector(quad.normal)) {
                this.warnCustomQuad("[YSM][quad] bone='{}' quad={} invalid normal - skipping quad",
                        this.getId(), quadIndex);
                continue;
            }

            Vector3f normalVec = bufferIn.getTempVec3f(quad.normal);
            normalVec.transform(normalMatrix);
            float nx = normalVec.getX();
            float ny = normalVec.getY();
            float nz = normalVec.getZ();
            if (!isFinite(nx) || !isFinite(ny) || !isFinite(nz)) {
                this.warnCustomQuad("[YSM][quad] bone='{}' quad={} transformed normal is invalid - skipping quad",
                        this.getId(), quadIndex);
                continue;
            }

            boolean skip = false;
            for (int i = 0; i < 4; i++) {
                Vector3f pos = quad.positions[i];
                if (!isFiniteVector(pos) || exceedsAbs(pos, MAX_ABS_VERTEX_PIXELS)
                        || !isFinite(quad.u[i]) || !isFinite(quad.v[i])
                        || Math.abs(quad.u[i]) > MAX_ABS_UV || Math.abs(quad.v[i]) > MAX_ABS_UV) {
                    this.warnCustomQuad(
                            "[YSM][quad] bone='{}' quad={} vert={} invalid position/uv pos=({}, {}, {}) uv=({}, {}) - skipping quad",
                            this.getId(), quadIndex, i,
                            pos == null ? Float.NaN : pos.getX(),
                            pos == null ? Float.NaN : pos.getY(),
                            pos == null ? Float.NaN : pos.getZ(),
                            quad.u[i], quad.v[i]);
                    skip = true;
                    break;
                }
            }
            if (skip) {
                continue;
            }

            float[] vx = new float[4];
            float[] vy = new float[4];
            float[] vz = new float[4];
            for (int i = 0; i < 4; i++) {
                Vector3f pos = quad.positions[i];
                // Positions are stored in pixel units (same as ModelBox vertices).
                // Divide by 16 to convert to Minecraft block units for the vertex buffer.
                float px = pos.getX() / 16.0F;
                float py = pos.getY() / 16.0F;
                float pz = pos.getZ() / 16.0F;
                vx[i] = posMatrix.getTransformX(px, py, pz, 1.0F);
                vy[i] = posMatrix.getTransformY(px, py, pz, 1.0F);
                vz[i] = posMatrix.getTransformZ(px, py, pz, 1.0F);
                if (!isFinite(vx[i]) || !isFinite(vy[i]) || !isFinite(vz[i])) {
                    this.warnCustomQuad("[YSM][quad] bone='{}' quad={} transformed vertex is invalid - skipping quad",
                            this.getId(), quadIndex);
                    skip = true;
                    break;
                }
            }
            if (skip) {
                continue;
            }

            for (int i = 0; i < 4; i++) {
                bufferIn.addVertex(vx[i], vy[i], vz[i], red, green, blue, alpha,
                        quad.u[i], quad.v[i], packedOverlayIn, packedLightIn, nx, ny, nz);
            }
        }
    }

    private void warnCustomQuad(String message, Object... args) {
        if (this.customQuadWarningCount < MAX_CUSTOM_QUAD_WARNINGS) {
            YesSteveModel.LOGGER.warn(message, args);
        } else if (this.customQuadWarningCount == MAX_CUSTOM_QUAD_WARNINGS) {
            YesSteveModel.LOGGER.warn("[YSM][quad] bone='{}' reached {} invalid custom quad warnings; suppressing further warnings for this renderer",
                    this.getId(), MAX_CUSTOM_QUAD_WARNINGS);
        }
        this.customQuadWarningCount++;
    }

    private static boolean isFiniteVector(Vector3f v) {
        return v != null && isFinite(v.getX()) && isFinite(v.getY()) && isFinite(v.getZ());
    }

    private static boolean exceedsAbs(Vector3f v, float maxAbs) {
        return Math.abs(v.getX()) > maxAbs || Math.abs(v.getY()) > maxAbs || Math.abs(v.getZ()) > maxAbs;
    }

    private static boolean isZeroVector(Vector3f v) {
        return v.getX() == 0.0F && v.getY() == 0.0F && v.getZ() == 0.0F;
    }

    private static boolean isFinite(float value) {
        return !Float.isNaN(value) && !Float.isInfinite(value);
    }

    private static boolean hasCompleteQuadArrays(CustomQuad quad) {
        return quad != null && quad.positions != null && quad.positions.length >= 4
                && quad.u != null && quad.u.length >= 4
                && quad.v != null && quad.v.length >= 4;
    }

    public static class CustomQuad {
        public final Vector3f normal;
        public final Vector3f[] positions;
        public final float[] u;
        public final float[] v;

        public CustomQuad(Vector3f normal, Vector3f[] positions, float[] u, float[] v) {
            this.normal = normal;
            this.positions = positions;
            this.u = u;
            this.v = v;
        }
    }
}
