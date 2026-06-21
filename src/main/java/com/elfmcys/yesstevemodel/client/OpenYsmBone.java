package com.elfmcys.yesstevemodel.client;

import com.elfmcys.yesstevemodel.geckolib4.cache.object.GeoBone;
import com.mojang.blaze3d.matrix.MatrixStack;
import net.minecraft.client.renderer.model.ModelRenderer;

public final class OpenYsmBone {
    private final String name;
    private final ModelRenderer renderer;
    private OpenYsmBone parent;
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
    private GeoBone geoBone;

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

    public float getRotationX() {
        return this.renderer.rotateAngleX;
    }

    public float getRotationY() {
        return this.renderer.rotateAngleY;
    }

    public float getRotationZ() {
        return this.renderer.rotateAngleZ;
    }

    public float getPositionX() {
        return this.renderer.rotationPointX;
    }

    public float getPositionY() {
        return this.renderer.rotationPointY;
    }

    public float getPositionZ() {
        return this.renderer.rotationPointZ;
    }

    public OpenYsmBone getParent() {
        return this.parent;
    }

    public void setParent(OpenYsmBone parent) {
        this.parent = parent;
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

    public GeoBone getGeoBone() {
        return this.geoBone;
    }

    public void setGeoBone(GeoBone geoBone) {
        this.geoBone = geoBone;
        this.syncGeoBonePose();
    }

    public void setScale(float sx, float sy, float sz) {
        this.scaleX = sx;
        this.scaleY = sy;
        this.scaleZ = sz;
        if (this.renderer instanceof OpenYsmModelRenderer) {
            ((OpenYsmModelRenderer) this.renderer).setScale(sx, sy, sz);
        }
        if (this.geoBone != null) {
            this.geoBone.setScale(sx, sy, sz);
        }
    }

    public void setVisible(boolean visible) {
        this.renderer.showModel = visible;
        if (this.geoBone != null) {
            this.geoBone.setHidden(!visible);
        }
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
        if (this.geoBone != null) {
            this.geoBone.setHidden(!this.renderer.showModel);
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
        this.syncGeoBonePose();
    }

    public void addRotation(float x, float y, float z) {
        this.renderer.rotateAngleX += x;
        this.renderer.rotateAngleY += y;
        this.renderer.rotateAngleZ += z;
        if (this.geoBone != null) {
            this.geoBone.addRotation(x, y, z);
        }
    }

    public void setRotation(float x, float y, float z) {
        this.renderer.rotateAngleX = x;
        this.renderer.rotateAngleY = y;
        this.renderer.rotateAngleZ = z;
        if (this.geoBone != null) {
            this.geoBone.setRotation(x, y, z);
        }
    }

    public void addPosition(float x, float y, float z) {
        this.renderer.rotationPointX += x;
        this.renderer.rotationPointY += y;
        this.renderer.rotationPointZ += z;
        if (this.geoBone != null) {
            this.geoBone.addPosition(x, y, z);
        }
    }

    public void translateRotateChain(MatrixStack matrixStackIn) {
        if (this.parent != null) {
            this.parent.translateRotateChain(matrixStackIn);
        }
        this.renderer.translateRotate(matrixStackIn);
    }

    private void syncGeoBonePose() {
        if (this.geoBone == null) {
            return;
        }

        this.geoBone.setPosition(this.renderer.rotationPointX, this.renderer.rotationPointY, this.renderer.rotationPointZ);
        this.geoBone.setRotation(this.renderer.rotateAngleX, this.renderer.rotateAngleY, this.renderer.rotateAngleZ);
        this.geoBone.setScale(this.scaleX, this.scaleY, this.scaleZ);
        this.geoBone.setHidden(!this.renderer.showModel);
    }
}
