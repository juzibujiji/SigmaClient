package com.elfmcys.yesstevemodel.geckolib4.core.animation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * GeckoLib 4 style controller registry.
 */
public final class AnimatableManager {
    public static final class ControllerRegistrar {
        private final List<AnimationController<?>> controllers = new ArrayList<>();

        public void add(AnimationController<?> controller) {
            this.controllers.add(controller);
        }

        public List<AnimationController<?>> controllers() {
            return Collections.unmodifiableList(this.controllers);
        }
    }
}
