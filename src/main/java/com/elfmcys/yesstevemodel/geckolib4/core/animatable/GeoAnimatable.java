package com.elfmcys.yesstevemodel.geckolib4.core.animatable;

import com.elfmcys.yesstevemodel.geckolib4.core.animation.AnimatableManager;

/**
 * GeckoLib 4 style animatable contract for the MCP backport.
 */
public interface GeoAnimatable {
    default void registerControllers(AnimatableManager.ControllerRegistrar registrar) {
    }
}
