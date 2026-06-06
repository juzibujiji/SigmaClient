package com.elfmcys.yesstevemodel.geckolib4.cache.object;

import net.minecraft.util.math.vector.Vector3f;

/**
 * GeckoLib 4 style vertex data holder.
 */
public final class GeoVertex {
    private final Vector3f position;
    private final float textureU;
    private final float textureV;

    public GeoVertex(float x, float y, float z) {
        this(new Vector3f(x, y, z), 0.0F, 0.0F);
    }

    public GeoVertex(Vector3f position, float textureU, float textureV) {
        this.position = new Vector3f(position.getX(), position.getY(), position.getZ());
        this.textureU = textureU;
        this.textureV = textureV;
    }

    public Vector3f position() {
        return new Vector3f(this.position.getX(), this.position.getY(), this.position.getZ());
    }

    public float textureU() {
        return this.textureU;
    }

    public float textureV() {
        return this.textureV;
    }

    public GeoVertex withUVs(float textureU, float textureV) {
        return new GeoVertex(this.position, textureU, textureV);
    }
}
