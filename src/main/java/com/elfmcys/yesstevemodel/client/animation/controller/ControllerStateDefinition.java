package com.elfmcys.yesstevemodel.client.animation.controller;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class ControllerStateDefinition {
    private final String name;
    private final List<ControllerAnimationRef> animations;
    private final List<ControllerTransition> transitions;
    private final float blendTransitionSeconds;

    public ControllerStateDefinition(String name, List<ControllerAnimationRef> animations,
                                     List<ControllerTransition> transitions, float blendTransitionSeconds) {
        this.name = name == null ? "" : name.trim();
        this.animations = Collections.unmodifiableList(new ArrayList<>(animations == null ? Collections.emptyList() : animations));
        this.transitions = Collections.unmodifiableList(new ArrayList<>(transitions == null ? Collections.emptyList() : transitions));
        this.blendTransitionSeconds = Math.max(0.0F, blendTransitionSeconds);
    }

    public String getName() {
        return this.name;
    }

    public List<ControllerAnimationRef> getAnimations() {
        return this.animations;
    }

    public List<ControllerTransition> getTransitions() {
        return this.transitions;
    }

    public float getBlendTransitionSeconds() {
        return this.blendTransitionSeconds;
    }
}
