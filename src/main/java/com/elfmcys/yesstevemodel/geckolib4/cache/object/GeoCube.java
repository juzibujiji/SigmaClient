package com.elfmcys.yesstevemodel.geckolib4.cache.object;

import net.minecraft.util.math.vector.Vector3f;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * GeckoLib 4 style baked cuboid/quad holder.
 */
public final class GeoCube {
    private final List<GeoQuad> quads;
    private final float sizeX;
    private final float sizeY;
    private final float sizeZ;

    public GeoCube(List<GeoQuad> quads, float sizeX, float sizeY, float sizeZ) {
        this.quads = Collections.unmodifiableList(quads);
        this.sizeX = sizeX;
        this.sizeY = sizeY;
        this.sizeZ = sizeZ;
    }

    public List<GeoQuad> quads() {
        return this.quads;
    }

    public float sizeX() {
        return this.sizeX;
    }

    public float sizeY() {
        return this.sizeY;
    }

    public float sizeZ() {
        return this.sizeZ;
    }

    public static GeoCube customQuad(Vector3f normal, Vector3f[] positions, float[] u, float[] v) {
        GeoVertex[] vertices = new GeoVertex[4];
        for (int i = 0; i < 4; i++) {
            vertices[i] = new GeoVertex(positions[i], u[i], v[i]);
        }
        return new GeoCube(Collections.singletonList(new GeoQuad(vertices, normal)), 0.0F, 0.0F, 0.0F);
    }

    public static GeoCube boxFromLegacyUv(int texOffX, int texOffY, float x, float y, float z,
                                          float width, float height, float depth, float inflate,
                                          boolean mirror, float textureWidth, float textureHeight) {
        BoxVertices box = BoxVertices.create(x, y, z, width, height, depth, inflate, mirror);
        float u0 = texOffX;
        float u1 = texOffX + depth;
        float u2 = texOffX + depth + width;
        float u3 = texOffX + depth + width + width;
        float u4 = texOffX + depth + width + depth;
        float u5 = texOffX + depth + width + depth + width;
        float v0 = texOffY;
        float v1 = texOffY + depth;
        float v2 = texOffY + depth + height;

        return new GeoCube(Arrays.asList(
                GeoQuad.build(new GeoVertex[]{box.p4, box.p0, box.p1, box.p5}, u2, v1, u4, v2, textureWidth, textureHeight, mirror, 1.0F, 0.0F, 0.0F),
                GeoQuad.build(new GeoVertex[]{box.p7, box.p3, box.p6, box.p2}, u0, v1, u1, v2, textureWidth, textureHeight, mirror, -1.0F, 0.0F, 0.0F),
                GeoQuad.build(new GeoVertex[]{box.p4, box.p3, box.p7, box.p0}, u1, v0, u2, v1, textureWidth, textureHeight, mirror, 0.0F, -1.0F, 0.0F),
                GeoQuad.build(new GeoVertex[]{box.p1, box.p2, box.p6, box.p5}, u2, v1, u3, v0, textureWidth, textureHeight, mirror, 0.0F, 1.0F, 0.0F),
                GeoQuad.build(new GeoVertex[]{box.p0, box.p7, box.p2, box.p1}, u1, v1, u2, v2, textureWidth, textureHeight, mirror, 0.0F, 0.0F, -1.0F),
                GeoQuad.build(new GeoVertex[]{box.p3, box.p4, box.p5, box.p6}, u4, v1, u5, v2, textureWidth, textureHeight, mirror, 0.0F, 0.0F, 1.0F)
        ), width + inflate * 2.0F, height + inflate * 2.0F, depth + inflate * 2.0F);
    }

    public static GeoCube boxFromFaceUv(int[][] faceUv, float x, float y, float z,
                                        float width, float height, float depth, float inflate,
                                        boolean mirror, float textureWidth, float textureHeight) {
        BoxVertices box = BoxVertices.create(x, y, z, width, height, depth, inflate, mirror);
        GeoQuad[] quads = new GeoQuad[6];
        quads[0] = buildFace(new GeoVertex[]{box.p4, box.p0, box.p1, box.p5}, faceUv[4], false, textureWidth, textureHeight, mirror, 1.0F, 0.0F, 0.0F);
        quads[1] = buildFace(new GeoVertex[]{box.p7, box.p3, box.p6, box.p2}, faceUv[5], false, textureWidth, textureHeight, mirror, -1.0F, 0.0F, 0.0F);
        quads[2] = buildFace(new GeoVertex[]{box.p4, box.p3, box.p7, box.p0}, faceUv[1], true, textureWidth, textureHeight, mirror, 0.0F, -1.0F, 0.0F);
        quads[3] = buildFace(new GeoVertex[]{box.p1, box.p2, box.p6, box.p5}, faceUv[0], true, textureWidth, textureHeight, mirror, 0.0F, 1.0F, 0.0F);
        quads[4] = buildFace(new GeoVertex[]{box.p0, box.p7, box.p2, box.p1}, faceUv[2], false, textureWidth, textureHeight, mirror, 0.0F, 0.0F, -1.0F);
        quads[5] = buildFace(new GeoVertex[]{box.p3, box.p4, box.p5, box.p6}, faceUv[3], false, textureWidth, textureHeight, mirror, 0.0F, 0.0F, 1.0F);
        return new GeoCube(Arrays.asList(quads), width + inflate * 2.0F, height + inflate * 2.0F, depth + inflate * 2.0F);
    }

    private static GeoQuad buildFace(GeoVertex[] vertices, int[] uv, boolean invertUv,
                                     float textureWidth, float textureHeight, boolean mirror,
                                     float normalX, float normalY, float normalZ) {
        if (uv == null) {
            return null;
        }
        if (invertUv) {
            return GeoQuad.build(vertices, uv[2], uv[3], uv[0], uv[1], textureWidth, textureHeight, mirror,
                    normalX, normalY, normalZ);
        }
        return GeoQuad.build(vertices, uv[0], uv[1], uv[2], uv[3], textureWidth, textureHeight, mirror,
                normalX, normalY, normalZ);
    }

    private static final class BoxVertices {
        private final GeoVertex p0;
        private final GeoVertex p1;
        private final GeoVertex p2;
        private final GeoVertex p3;
        private final GeoVertex p4;
        private final GeoVertex p5;
        private final GeoVertex p6;
        private final GeoVertex p7;

        private BoxVertices(GeoVertex p0, GeoVertex p1, GeoVertex p2, GeoVertex p3,
                            GeoVertex p4, GeoVertex p5, GeoVertex p6, GeoVertex p7) {
            this.p0 = p0;
            this.p1 = p1;
            this.p2 = p2;
            this.p3 = p3;
            this.p4 = p4;
            this.p5 = p5;
            this.p6 = p6;
            this.p7 = p7;
        }

        private static BoxVertices create(float x, float y, float z, float width, float height,
                                          float depth, float inflate, boolean mirror) {
            float x2 = x + width;
            float y2 = y + height;
            float z2 = z + depth;
            x -= inflate;
            y -= inflate;
            z -= inflate;
            x2 += inflate;
            y2 += inflate;
            z2 += inflate;

            if (mirror) {
                float swap = x2;
                x2 = x;
                x = swap;
            }

            GeoVertex p7 = new GeoVertex(x, y, z);
            GeoVertex p0 = new GeoVertex(x2, y, z);
            GeoVertex p1 = new GeoVertex(x2, y2, z);
            GeoVertex p2 = new GeoVertex(x, y2, z);
            GeoVertex p3 = new GeoVertex(x, y, z2);
            GeoVertex p4 = new GeoVertex(x2, y, z2);
            GeoVertex p5 = new GeoVertex(x2, y2, z2);
            GeoVertex p6 = new GeoVertex(x, y2, z2);
            return new BoxVertices(p0, p1, p2, p3, p4, p5, p6, p7);
        }
    }
}
