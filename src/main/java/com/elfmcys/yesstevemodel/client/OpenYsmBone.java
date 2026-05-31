package com.elfmcys.yesstevemodel.client;

import net.minecraft.client.renderer.model.ModelRenderer;

public final class OpenYsmBone {
    private final String name;
    private final ModelRenderer renderer;
    private final float baseRotationX;
    private final float baseRotationY;
    private final float baseRotationZ;
    private final float basePositionX;
    private final float basePositionY;
    private final float basePositionZ;
    private final boolean baseVisible;

    private float scaleX = 1.0F;
    private float scaleY = 1.0F;
    private float scaleZ = 1.0F;

    private boolean defaultHidden;

    public OpenYsmBone(String name, ModelRenderer renderer, float baseRotationX, float baseRotationY, float baseRotationZ) {
        this.name = name;
        this.renderer = renderer;
        this.baseRotationX = baseRotationX;
        this.baseRotationY = baseRotationY;
        this.baseRotationZ = baseRotationZ;
        this.basePositionX = renderer.rotationPointX;
        this.basePositionY = renderer.rotationPointY;
        this.basePositionZ = renderer.rotationPointZ;
        this.baseVisible = renderer.showModel;
    }

    public String getName() {
        return this.name;
    }

    public ModelRenderer getRenderer() {
        return this.renderer;
    }

    public float getScaleX() {
        return this.scaleX;
    }

    public float getScaleY() {
        return this.scaleY;
    }

    public float getScaleZ() {
        return this.scaleZ;
    }

    public boolean hasNonIdentityScale() {
        return this.scaleX != 1.0F || this.scaleY != 1.0F || this.scaleZ != 1.0F;
    }

    public void setScale(float sx, float sy, float sz) {
        this.scaleX = sx;
        this.scaleY = sy;
        this.scaleZ = sz;
        if (this.renderer instanceof OpenYsmModelRenderer) {
            ((OpenYsmModelRenderer) this.renderer).setScale(sx, sy, sz);
        }
    }

    public void setVisible(boolean visible) {
        this.renderer.showModel = visible;
    }

    public boolean isVisible() {
        return this.renderer.showModel;
    }

    /**
     * Marks this bone as hidden by default. Used for condition-driven bones whose
     * geometry should not be displayed unless an animation explicitly sets visibility=true.
     */
    public void setDefaultHidden(boolean defaultHidden) {
        this.defaultHidden = defaultHidden;
        if (defaultHidden) {
            this.renderer.showModel = false;
        }
    }

    public boolean isDefaultHidden() {
        return this.defaultHidden;
    }

    /**
     * Reset this bone to its bind/default pose. Called once per frame before
     * vanilla biped pose copy and animation runtime evaluation.
     */
    public void resetPose() {
        this.renderer.rotateAngleX = this.baseRotationX;
        this.renderer.rotateAngleY = this.baseRotationY;
        this.renderer.rotateAngleZ = this.baseRotationZ;
        this.renderer.rotationPointX = this.basePositionX;
        this.renderer.rotationPointY = this.basePositionY;
        this.renderer.rotationPointZ = this.basePositionZ;
        this.scaleX = 1.0F;
        this.scaleY = 1.0F;
        this.scaleZ = 1.0F;
        if (this.renderer instanceof OpenYsmModelRenderer) {
            ((OpenYsmModelRenderer) this.renderer).setScale(1.0F, 1.0F, 1.0F);
        }
        this.renderer.showModel = this.defaultHidden ? false : this.baseVisible;
    }

    public void addRotation(float x, float y, float z) {
        this.renderer.rotateAngleX += x;
        this.renderer.rotateAngleY += y;
        this.renderer.rotateAngleZ += z;
    }

    public void setRotation(float x, float y, float z) {
        this.renderer.rotateAngleX = x;
        this.renderer.rotateAngleY = y;
        this.renderer.rotateAngleZ = z;
    }

    public void addPosition(float x, float y, float z) {
        this.renderer.rotationPointX += x;
        this.renderer.rotationPointY += y;
        this.renderer.rotationPointZ += z;
    }
}
