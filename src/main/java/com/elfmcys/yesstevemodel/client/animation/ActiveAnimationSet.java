package com.elfmcys.yesstevemodel.client.animation;

import com.elfmcys.yesstevemodel.client.animation.controller.ControllerLayer;
import com.elfmcys.yesstevemodel.client.animation.controller.OpenYsmControllerRuntime;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class ActiveAnimationSet {
    public OpenYsmAnimationSet.Clip mainStateClip;
    public final List<OpenYsmAnimationSet.Clip> handClips = new ArrayList<>();
    public Optional<OpenYsmAnimationSet.Clip> extraActionClip = Optional.empty();
    public final List<OpenYsmAnimationSet.Clip> controllerClips = new ArrayList<>();
    public final List<ActiveClip> controllerLayerClips = new ArrayList<>();
    public final List<OpenYsmControllerRuntime.ControllerEvent> controllerEvents = new ArrayList<>();
    public Optional<OpenYsmAnimationSet.Clip> previewClip = Optional.empty();
    public ActionSource actionSource = ActionSource.UNKNOWN;

    private final Map<OpenYsmAnimationSet.Clip, Float> clipTimes = new IdentityHashMap<>();
    private final Map<OpenYsmAnimationSet.Clip, Float> clipWeights = new IdentityHashMap<>();

    public void setTime(OpenYsmAnimationSet.Clip clip, float seconds) {
        if (clip != null) {
            this.clipTimes.put(clip, seconds);
        }
    }

    public float timeFor(OpenYsmAnimationSet.Clip clip) {
        Float value = this.clipTimes.get(clip);
        return value == null ? 0.0F : value;
    }

    public void setWeight(OpenYsmAnimationSet.Clip clip, float weight) {
        if (clip != null) {
            this.clipWeights.put(clip, Math.max(0.0F, weight));
        }
    }

    public float weightFor(OpenYsmAnimationSet.Clip clip) {
        Float value = this.clipWeights.get(clip);
        return value == null ? 1.0F : value;
    }

    public void addControllerClip(OpenYsmAnimationSet.Clip clip, ControllerLayer layer, float seconds,
                                  float weight, String controllerName, String stateName) {
        if (clip == null || weight <= 0.0F) {
            return;
        }
        this.controllerClips.add(clip);
        this.controllerLayerClips.add(new ActiveClip(clip, seconds, weight, layer, controllerName, stateName));
        setTime(clip, seconds);
        setWeight(clip, weight);
    }

    public List<OpenYsmAnimationSet.Clip> allClips() {
        List<OpenYsmAnimationSet.Clip> clips = new ArrayList<>();
        for (ActiveClip activeClip : activeClipsInOrder()) {
            clips.add(activeClip.getClip());
        }
        return Collections.unmodifiableList(clips);
    }

    public List<ActiveClip> activeClipsInOrder() {
        List<ActiveClip> activeClips = new ArrayList<>();
        addControllerLayer(activeClips, ControllerLayer.PRE_MAIN);
        addControllerLayer(activeClips, ControllerLayer.MAIN);
        if (this.mainStateClip != null) {
            activeClips.add(new ActiveClip(this.mainStateClip, timeFor(this.mainStateClip), weightFor(this.mainStateClip),
                    ControllerLayer.UNKNOWN, "", ""));
        }
        for (OpenYsmAnimationSet.Clip clip : this.handClips) {
            activeClips.add(new ActiveClip(clip, timeFor(clip), weightFor(clip), ControllerLayer.UNKNOWN, "", ""));
        }
        addControllerLayer(activeClips, ControllerLayer.HOLD_MAINHAND);
        addControllerLayer(activeClips, ControllerLayer.HOLD_OFFHAND);
        addControllerLayer(activeClips, ControllerLayer.USE);
        addControllerLayer(activeClips, ControllerLayer.SWING);
        this.extraActionClip.ifPresent(clip -> activeClips.add(new ActiveClip(clip, timeFor(clip), weightFor(clip),
                ControllerLayer.UNKNOWN, "", "")));
        addControllerLayer(activeClips, ControllerLayer.PARALLEL);
        addControllerLayer(activeClips, ControllerLayer.UNKNOWN);
        addControllerLayer(activeClips, ControllerLayer.POST_MAIN);
        this.previewClip.ifPresent(clip -> activeClips.add(new ActiveClip(clip, timeFor(clip), weightFor(clip),
                ControllerLayer.UNKNOWN, "", "")));
        return Collections.unmodifiableList(activeClips);
    }

    private void addControllerLayer(List<ActiveClip> activeClips, ControllerLayer layer) {
        for (ActiveClip controllerClip : this.controllerLayerClips) {
            if (controllerClip.getLayer() == layer) {
                activeClips.add(controllerClip);
            }
        }
    }

    public static final class ActiveClip {
        private final OpenYsmAnimationSet.Clip clip;
        private final float timeSeconds;
        private final float weight;
        private final ControllerLayer layer;
        private final String controllerName;
        private final String stateName;

        private ActiveClip(OpenYsmAnimationSet.Clip clip, float timeSeconds, float weight, ControllerLayer layer,
                           String controllerName, String stateName) {
            this.clip = clip;
            this.timeSeconds = timeSeconds;
            this.weight = weight;
            this.layer = layer == null ? ControllerLayer.UNKNOWN : layer;
            this.controllerName = controllerName == null ? "" : controllerName;
            this.stateName = stateName == null ? "" : stateName;
        }

        public OpenYsmAnimationSet.Clip getClip() {
            return this.clip;
        }

        public float getTimeSeconds() {
            return this.timeSeconds;
        }

        public float getWeight() {
            return this.weight;
        }

        public ControllerLayer getLayer() {
            return this.layer;
        }

        public String getControllerName() {
            return this.controllerName;
        }

        public String getStateName() {
            return this.stateName;
        }
    }
}
