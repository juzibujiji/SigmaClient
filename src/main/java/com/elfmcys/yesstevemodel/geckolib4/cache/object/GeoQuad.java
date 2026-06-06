package com.elfmcys.yesstevemodel.geckolib4.cache.object;

import net.minecraft.util.math.vector.Vector3f;

/**
 * GeckoLib 4 style baked quad.
 */
public final class GeoQuad {
    private final GeoVertex[] vertices;
    private final Vector3f normal;

    public GeoQuad(GeoVertex[] vertices, Vector3f normal) {
        this.vertices = vertices;
        this.normal = new Vector3f(normal.getX(), normal.getY(), normal.getZ());
    }

    public GeoVertex[] vertices() {
        return this.vertices;
    }

    public Vector3f normal() {
        return new Vector3f(this.normal.getX(), this.normal.getY(), this.normal.getZ());
    }

    public static GeoQuad build(GeoVertex[] vertices, float u1, float v1, float u2, float v2,
                                float textureWidth, float textureHeight, boolean mirror,
                                float normalX, float normalY, float normalZ) {
        vertices[0] = vertices[0].withUVs(u2 / textureWidth, v1 / textureHeight);
        vertices[1] = vertices[1].withUVs(u1 / textureWidth, v1 / textureHeight);
        vertices[2] = vertices[2].withUVs(u1 / textureWidth, v2 / textureHeight);
        vertices[3] = vertices[3].withUVs(u2 / textureWidth, v2 / textureHeight);

        if (mirror) {
            for (int i = 0; i < vertices.length / 2; i++) {
                GeoVertex swap = vertices[i];
                vertices[i] = vertices[vertices.length - 1 - i];
                vertices[vertices.length - 1 - i] = swap;
            }
            normalX *= -1.0F;
        }

        return new GeoQuad(vertices, new Vector3f(normalX, normalY, normalZ));
    }
}
