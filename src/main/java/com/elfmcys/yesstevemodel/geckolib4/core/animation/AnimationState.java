package com.elfmcys.yesstevemodel.geckolib4.core.animation;

import com.elfmcys.yesstevemodel.geckolib4.core.animatable.GeoAnimatable;

import java.util.HashMap;
import java.util.Map;

/**
 * GeckoLib 4 style animation state data container.
 */
public final class AnimationState<T extends GeoAnimatable> {
    private final T animatable;
    private final float limbSwing;
    private final float limbSwingAmount;
    private final float partialTick;
    private final boolean moving;
    private final Map<String, Object> data = new HashMap<>();

    public AnimationState(T animatable, float limbSwing, float limbSwingAmount,
                          float partialTick, boolean moving) {
        this.animatable = animatable;
        this.limbSwing = limbSwing;
        this.limbSwingAmount = limbSwingAmount;
        this.partialTick = partialTick;
        this.moving = moving;
    }

    public T getAnimatable() {
        return this.animatable;
    }

    public float getLimbSwing() {
        return this.limbSwing;
    }

    public float getLimbSwingAmount() {
        return this.limbSwingAmount;
    }

    public float getPartialTick() {
        return this.partialTick;
    }

    public boolean isMoving() {
        return this.moving;
    }

    public void setData(String key, Object value) {
        this.data.put(key, value);
    }

    @SuppressWarnings("unchecked")
    public <D> D getData(String key) {
        return (D)this.data.get(key);
    }
}
