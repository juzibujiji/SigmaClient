package com.elfmcys.yesstevemodel.geckolib4.core.animation;

import com.elfmcys.yesstevemodel.geckolib4.core.animatable.GeoAnimatable;
import com.elfmcys.yesstevemodel.geckolib4.core.object.PlayState;

import java.util.function.Function;

/**
 * GeckoLib 4 style animation controller shell for MCP integration.
 */
public final class AnimationController<T extends GeoAnimatable> {
    private final T animatable;
    private final String name;
    private final int transitionLength;
    private final Function<AnimationState<T>, PlayState> handler;
    private RawAnimation currentAnimation;

    public AnimationController(T animatable, String name, int transitionLength,
                               Function<AnimationState<T>, PlayState> handler) {
        this.animatable = animatable;
        this.name = name;
        this.transitionLength = transitionLength;
        this.handler = handler;
    }

    public T getAnimatable() {
        return this.animatable;
    }

    public String getName() {
        return this.name;
    }

    public int getTransitionLength() {
        return this.transitionLength;
    }

    public RawAnimation getCurrentAnimation() {
        return this.currentAnimation;
    }

    public void setAnimation(RawAnimation animation) {
        this.currentAnimation = animation;
    }

    public PlayState tick(AnimationState<T> state) {
        return this.handler == null ? PlayState.CONTINUE : this.handler.apply(state);
    }
}
