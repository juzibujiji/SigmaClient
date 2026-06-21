package com.elfmcys.yesstevemodel.client.animation.controller;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public final class ControllerStateDefinition {
    private final String name;
    private final List<ControllerAnimationRef> animations;
    private final List<ControllerTransition> transitions;
    private final List<String> onEntryEvents;
    private final List<String> onExitEvents;
    private final List<String> soundEffects;
    private final float blendTransitionSeconds;
    private final float[] blendTransitionTimes;
    private final float[] blendTransitionValues;

    public ControllerStateDefinition(String name, List<ControllerAnimationRef> animations,
                                     List<ControllerTransition> transitions, float blendTransitionSeconds) {
        this(name, animations, transitions, blendTransitionSeconds, Collections.emptyList(),
                Collections.emptyList(), Collections.emptyList());
    }

    public ControllerStateDefinition(String name, List<ControllerAnimationRef> animations,
                                     List<ControllerTransition> transitions, float blendTransitionSeconds,
                                     List<String> onEntryEvents, List<String> onExitEvents,
                                     List<String> soundEffects) {
        this(name, animations, transitions, blendTransitionSeconds, Collections.emptyMap(),
                onEntryEvents, onExitEvents, soundEffects);
    }

    public ControllerStateDefinition(String name, List<ControllerAnimationRef> animations,
                                     List<ControllerTransition> transitions, float blendTransitionSeconds,
                                     Map<Float, Float> blendTransitions,
                                     List<String> onEntryEvents, List<String> onExitEvents,
                                     List<String> soundEffects) {
        this.name = name == null ? "" : name.trim();
        this.animations = Collections.unmodifiableList(new ArrayList<>(animations == null ? Collections.emptyList() : animations));
        this.transitions = Collections.unmodifiableList(new ArrayList<>(transitions == null ? Collections.emptyList() : transitions));
        this.onEntryEvents = Collections.unmodifiableList(new ArrayList<>(onEntryEvents == null ? Collections.emptyList() : onEntryEvents));
        this.onExitEvents = Collections.unmodifiableList(new ArrayList<>(onExitEvents == null ? Collections.emptyList() : onExitEvents));
        this.soundEffects = Collections.unmodifiableList(new ArrayList<>(soundEffects == null ? Collections.emptyList() : soundEffects));
        this.blendTransitionSeconds = Math.max(0.0F, blendTransitionSeconds);
        Map<Float, Float> safeBlendTransitions = blendTransitions == null ? Collections.emptyMap() : blendTransitions;
        List<Map.Entry<Float, Float>> sortedBlendTransitions = safeBlendTransitions.entrySet().stream()
                .filter(entry -> entry.getKey() != null && entry.getValue() != null)
                .sorted(Map.Entry.comparingByKey())
                .toList();
        this.blendTransitionTimes = new float[sortedBlendTransitions.size()];
        this.blendTransitionValues = new float[sortedBlendTransitions.size()];
        for (int i = 0; i < sortedBlendTransitions.size(); i++) {
            Map.Entry<Float, Float> entry = sortedBlendTransitions.get(i);
            this.blendTransitionTimes[i] = Math.max(0.0F, entry.getKey());
            this.blendTransitionValues[i] = entry.getValue();
        }
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

    public List<String> getOnEntryEvents() {
        return this.onEntryEvents;
    }

    public List<String> getOnExitEvents() {
        return this.onExitEvents;
    }

    public List<String> getSoundEffects() {
        return this.soundEffects;
    }

    public float getBlendTransitionSeconds() {
        return this.blendTransitionSeconds;
    }

    public boolean hasBlendTransitionCurve() {
        return this.blendTransitionTimes.length > 1;
    }

    public float[] getBlendTransitionTimes() {
        return this.blendTransitionTimes.clone();
    }

    public float[] getBlendTransitionValues() {
        return this.blendTransitionValues.clone();
    }

    public float getBlendTransitionDurationSeconds() {
        if (this.blendTransitionTimes.length == 0) {
            return this.blendTransitionSeconds;
        }
        return this.blendTransitionTimes[this.blendTransitionTimes.length - 1];
    }
}
