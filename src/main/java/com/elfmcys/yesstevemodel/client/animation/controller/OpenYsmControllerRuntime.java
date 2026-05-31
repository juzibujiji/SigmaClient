package com.elfmcys.yesstevemodel.client.animation.controller;

import com.elfmcys.yesstevemodel.YesSteveModel;
import com.elfmcys.yesstevemodel.client.animation.OpenYsmAnimationSet;
import com.elfmcys.yesstevemodel.client.animation.PlayerStateSnapshot;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class OpenYsmControllerRuntime {
    private static final Map<String, Map<String, ControllerInstance>> INSTANCES = new ConcurrentHashMap<>();

    private OpenYsmControllerRuntime() {
    }

    public static void clearModel(String modelId) {
        if (modelId == null || modelId.isEmpty()) {
            return;
        }
        String suffix = "|" + modelId;
        INSTANCES.keySet().removeIf(key -> key.endsWith(suffix));
    }

    public static Result tick(String modelId, Collection<ControllerDefinition> definitions,
                              Map<String, OpenYsmAnimationSet.Clip> clips, PlayerStateSnapshot snapshot) {
        if (snapshot == null || modelId == null || modelId.isEmpty() || definitions == null || definitions.isEmpty()) {
            return Result.EMPTY;
        }

        String playerKey = key(snapshot.uuid, modelId);
        Map<String, ControllerInstance> playerInstances = INSTANCES.computeIfAbsent(playerKey, ignored -> new ConcurrentHashMap<>());
        List<ActiveControllerAnimation> active = new ArrayList<>();
        for (ControllerDefinition definition : definitions) {
            if (definition == null || !definition.isUsable()) {
                continue;
            }
            ControllerInstance instance = playerInstances.computeIfAbsent(definition.getName(),
                    ignored -> new ControllerInstance(definition.getInitialState(), snapshot.ageInTicks));
            ControllerStateDefinition state = definition.getState(instance.getCurrentState());
            if (state == null) {
                instance.reset(definition.getInitialState(), snapshot.ageInTicks);
                state = definition.getState(instance.getCurrentState());
            }
            if (state == null) {
                continue;
            }

            String previousState = instance.getCurrentState();
            for (ControllerTransition transition : state.getTransitions()) {
                boolean result = evaluateCondition(transition.getExpression(), snapshot, definition.getName(), transition.getTargetState());
                debugTransition(snapshot, modelId, definition, previousState, transition, result);
                if (result && definition.getStates().containsKey(transition.getTargetState())) {
                    instance.transitionTo(transition.getTargetState(), snapshot.ageInTicks, state.getBlendTransitionSeconds());
                    state = definition.getState(instance.getCurrentState());
                    break;
                }
            }

            if (state == null) {
                continue;
            }
            float elapsed = instance.elapsedSeconds(snapshot.ageInTicks);
            List<String> activeNames = new ArrayList<>();
            for (ControllerAnimationRef animationRef : state.getAnimations()) {
                float weight = evaluateWeight(animationRef.getWeightExpression(), snapshot, definition.getName(), animationRef.getAnimationName());
                OpenYsmAnimationSet.Clip clip = findClip(clips, animationRef.getAnimationName());
                if (clip == null) {
                    debugSkip(snapshot, modelId, definition, state, animationRef, "clip not found");
                    continue;
                }
                if (weight <= 0.0F) {
                    debugSkip(snapshot, modelId, definition, state, animationRef, "weight <= 0");
                    continue;
                }
                active.add(new ActiveControllerAnimation(definition.getName(), state.getName(), definition.getLayer(), clip, elapsed, weight));
                activeNames.add(clip.name + "@" + weight);
            }
            debugActive(snapshot, modelId, definition, previousState, instance.getCurrentState(), activeNames);
        }
        return active.isEmpty() ? Result.EMPTY : new Result(active);
    }

    private static OpenYsmAnimationSet.Clip findClip(Map<String, OpenYsmAnimationSet.Clip> clips, String animationName) {
        if (clips == null || animationName == null || animationName.isEmpty()) {
            return null;
        }
        OpenYsmAnimationSet.Clip clip = clips.get(animationName);
        if (clip != null) {
            return clip;
        }
        if (animationName.startsWith("animation.")) {
            return clips.get(animationName.substring("animation.".length()));
        }
        return null;
    }

    private static String key(UUID playerId, String modelId) {
        return String.valueOf(playerId) + "|" + modelId;
    }

    private static boolean evaluateCondition(String expression, PlayerStateSnapshot snapshot, String controllerName, String targetState) {
        EvaluationResult result = evaluateSimple(expression, snapshot);
        if (!result.valid) {
            debugExpressionFailure(snapshot, controllerName, expression, "transition", targetState);
            return false;
        }
        return result.value != 0.0F;
    }

    private static float evaluateWeight(String expression, PlayerStateSnapshot snapshot, String controllerName, String animationName) {
        EvaluationResult result = evaluateSimple(expression, snapshot);
        if (!result.valid) {
            debugExpressionFailure(snapshot, controllerName, expression, "weight", animationName);
            return 0.0F;
        }
        return Math.max(0.0F, result.value);
    }

    private static EvaluationResult evaluateSimple(String expression, PlayerStateSnapshot snapshot) {
        if (expression == null || expression.trim().isEmpty()) {
            return EvaluationResult.valid(1.0F);
        }
        String value = expression.trim().toLowerCase(Locale.ROOT);
        if ("true".equals(value)) {
            return EvaluationResult.valid(1.0F);
        }
        if ("false".equals(value)) {
            return EvaluationResult.valid(0.0F);
        }
        if (value.startsWith("ctrl.")) {
            return EvaluationResult.valid(ctrlValue(value.substring("ctrl.".length()), snapshot));
        }
        try {
            return EvaluationResult.valid(Float.parseFloat(value));
        } catch (NumberFormatException exception) {
            return EvaluationResult.invalid();
        }
    }

    private static float ctrlValue(String key, PlayerStateSnapshot snapshot) {
        return switch (key) {
            case "idle" -> snapshot.isMoving() ? 0.0F : 1.0F;
            case "walk", "moving" -> snapshot.isMoving() && !snapshot.sprinting && !snapshot.sneaking ? 1.0F : 0.0F;
            case "run", "sprint", "sprinting" -> snapshot.sprinting && snapshot.isMoving() ? 1.0F : 0.0F;
            case "sneak", "sneaking" -> snapshot.sneaking ? 1.0F : 0.0F;
            case "use", "using_item" -> snapshot.usingItem ? 1.0F : 0.0F;
            case "swing", "swinging" -> snapshot.swingInProgress ? 1.0F : 0.0F;
            case "hold_mainhand" -> snapshot.mainhandEmpty ? 0.0F : 1.0F;
            case "hold_offhand" -> snapshot.offhandEmpty ? 0.0F : 1.0F;
            default -> 0.0F;
        };
    }

    private static void debugTransition(PlayerStateSnapshot snapshot, String modelId, ControllerDefinition definition,
                                        String previousState, ControllerTransition transition, boolean result) {
        if (!isDebugEnabled()) {
            return;
        }
        YesSteveModel.LOGGER.info("[DEBUG-animation-state] player={} model={} controller={} previous state={} current state={} transition expression={} transition target={} transition result={}",
                snapshot.uuid, modelId, definition.getName(), previousState, previousState,
                transition.getExpression(), transition.getTargetState(), result);
    }

    private static void debugActive(PlayerStateSnapshot snapshot, String modelId, ControllerDefinition definition,
                                    String previousState, String currentState, List<String> activeNames) {
        if (!isDebugEnabled()) {
            return;
        }
        YesSteveModel.LOGGER.info("[DEBUG-animation-state] player={} model={} controller={} previous state={} current state={} active controller animations={}",
                snapshot.uuid, modelId, definition.getName(), previousState, currentState, activeNames);
    }

    private static void debugSkip(PlayerStateSnapshot snapshot, String modelId, ControllerDefinition definition,
                                  ControllerStateDefinition state, ControllerAnimationRef animationRef, String reason) {
        if (!isDebugEnabled()) {
            return;
        }
        YesSteveModel.LOGGER.info("[DEBUG-animation-state] player={} model={} controller={} current state={} skipped animation={} weight={} reason={}",
                snapshot.uuid, modelId, definition.getName(), state.getName(), animationRef.getAnimationName(),
                animationRef.getWeightExpression(), reason);
    }

    private static void debugExpressionFailure(PlayerStateSnapshot snapshot, String controllerName, String expression,
                                               String expressionKind, String owner) {
        if (!isDebugEnabled()) {
            return;
        }
        YesSteveModel.LOGGER.info("[DEBUG-animation-state] player={} controller={} expression parse errors kind={} owner={} expression={}",
                snapshot.uuid, controllerName, expressionKind, owner, expression);
    }

    private static boolean isDebugEnabled() {
        return Boolean.getBoolean("yes_steve_model.debugAnimationState");
    }

    private static final class EvaluationResult {
        private final boolean valid;
        private final float value;

        private EvaluationResult(boolean valid, float value) {
            this.valid = valid;
            this.value = value;
        }

        private static EvaluationResult valid(float value) {
            return new EvaluationResult(true, value);
        }

        private static EvaluationResult invalid() {
            return new EvaluationResult(false, 0.0F);
        }
    }

    public static final class Result {
        private static final Result EMPTY = new Result(Collections.emptyList());
        private final List<ActiveControllerAnimation> activeAnimations;

        private Result(List<ActiveControllerAnimation> activeAnimations) {
            this.activeAnimations = Collections.unmodifiableList(new ArrayList<>(activeAnimations));
        }

        public List<ActiveControllerAnimation> getActiveAnimations() {
            return this.activeAnimations;
        }

        public boolean hasMainLayerAnimation() {
            for (ActiveControllerAnimation animation : this.activeAnimations) {
                if (animation.getLayer().replacesHardcodedMain()) {
                    return true;
                }
            }
            return false;
        }
    }

    public static final class ActiveControllerAnimation {
        private final String controllerName;
        private final String stateName;
        private final ControllerLayer layer;
        private final OpenYsmAnimationSet.Clip clip;
        private final float timeSeconds;
        private final float weight;

        private ActiveControllerAnimation(String controllerName, String stateName, ControllerLayer layer,
                                          OpenYsmAnimationSet.Clip clip, float timeSeconds, float weight) {
            this.controllerName = controllerName;
            this.stateName = stateName;
            this.layer = layer == null ? ControllerLayer.UNKNOWN : layer;
            this.clip = clip;
            this.timeSeconds = timeSeconds;
            this.weight = weight;
        }

        public String getControllerName() {
            return this.controllerName;
        }

        public String getStateName() {
            return this.stateName;
        }

        public ControllerLayer getLayer() {
            return this.layer;
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
    }
}
