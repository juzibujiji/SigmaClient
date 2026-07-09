package com.elfmcys.yesstevemodel.client.animation.controller;

import com.elfmcys.yesstevemodel.YesSteveModel;
import com.elfmcys.yesstevemodel.capability.OpenYsmPlayerAnimationState;
import com.elfmcys.yesstevemodel.client.animation.LoopMode;
import com.elfmcys.yesstevemodel.client.animation.OpenYsmAnimationSet;
import com.elfmcys.yesstevemodel.client.animation.PlayerStateSnapshot;
import com.elfmcys.yesstevemodel.client.animation.molang.MolangBindings;
import com.elfmcys.yesstevemodel.client.animation.molang.MolangContext;
import com.elfmcys.yesstevemodel.client.animation.molang.MolangEvaluator;
import com.elfmcys.yesstevemodel.client.animation.molang.MolangExpression;
import com.elfmcys.yesstevemodel.client.animation.molang.MolangParser;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
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

    public static void clearAll() {
        INSTANCES.clear();
    }

    public static Result tick(String modelId, Collection<ControllerDefinition> definitions,
                              Map<String, OpenYsmAnimationSet.Clip> clips, PlayerStateSnapshot snapshot) {
        return tick(modelId, definitions, clips, snapshot, null, false);
    }

    public static Result tick(String modelId, Collection<ControllerDefinition> definitions,
                              Map<String, OpenYsmAnimationSet.Clip> clips, PlayerStateSnapshot snapshot,
                              Map<String, Double> externalVariables) {
        return tick(modelId, definitions, clips, snapshot, externalVariables, false);
    }

    public static Result tick(String modelId, Collection<ControllerDefinition> definitions,
                              Map<String, OpenYsmAnimationSet.Clip> clips, PlayerStateSnapshot snapshot,
                              Map<String, Double> externalVariables, boolean playingExtraAnimation) {
        if (snapshot == null || modelId == null || modelId.isEmpty() || definitions == null || definitions.isEmpty()) {
            return Result.EMPTY;
        }

        String playerKey = key(snapshot.uuid, modelId);
        Map<String, ControllerInstance> playerInstances = INSTANCES.computeIfAbsent(playerKey, ignored -> new ConcurrentHashMap<>());
        List<ActiveControllerAnimation> active = new ArrayList<>();
        List<ControllerEvent> events = new ArrayList<>();
        for (ControllerDefinition definition : definitions) {
            if (definition == null || !definition.isUsable()) {
                continue;
            }
            boolean created = !playerInstances.containsKey(definition.getName());
            ControllerInstance instance = playerInstances.computeIfAbsent(definition.getName(),
                    ignored -> new ControllerInstance(definition.getInitialState(), snapshot.ageInTicks));
            ControllerStateDefinition state = definition.getState(instance.getCurrentState());
            if (state == null) {
                instance.reset(definition.getInitialState(), snapshot.ageInTicks);
                state = definition.getState(instance.getCurrentState());
                appendEntryEvents(events, definition, state);
                created = false;
            }
            if (state == null) {
                continue;
            }
            if (created) {
                appendEntryEvents(events, definition, state);
            }

            String previousState = instance.getCurrentState();
            float stateElapsedSeconds = instance.elapsedSeconds(snapshot.ageInTicks);
            AnimationFinishSummary finishSummary = finishSummary(state, stateElapsedSeconds, clips);
            for (ControllerTransition transition : state.getTransitions()) {
                boolean result = evaluateCondition(transition.getExpression(), snapshot, modelId,
                        definition.getName(), transition.getTargetState(), finishSummary, externalVariables,
                        playingExtraAnimation, stateElapsedSeconds);
                debugTransition(snapshot, modelId, definition, previousState, transition, result);
                if (result && !transition.getTargetState().equals(instance.getCurrentState())
                        && definition.getStates().containsKey(transition.getTargetState())) {
                    ControllerStateDefinition targetState = definition.getState(transition.getTargetState());
                    appendExitEvents(events, definition, state);
                    instance.transitionTo(transition.getTargetState(), snapshot.ageInTicks, targetState);
                    state = definition.getState(instance.getCurrentState());
                    appendEntryEvents(events, definition, state);
                    break;
                }
            }

            if (state == null) {
                continue;
            }
            float elapsed = instance.elapsedSeconds(snapshot.ageInTicks);
            float currentBlendWeight = 1.0F;
            float previousBlendWeight = 0.0F;
            ControllerStateDefinition blendedPreviousState = null;
            if (!instance.getPreviousState().isEmpty()) {
                float blendProgress = instance.blendProgress(snapshot.ageInTicks);
                if (blendProgress < 1.0F) {
                    blendedPreviousState = definition.getState(instance.getPreviousState());
                    previousBlendWeight = 1.0F - blendProgress;
                    currentBlendWeight = blendProgress;
                } else {
                    instance.finishBlendIfComplete(snapshot.ageInTicks);
                }
            }
            List<String> activeNames = new ArrayList<>();
            AnimationFinishSummary outputFinishSummary = finishSummary(state, elapsed, clips);
            if (blendedPreviousState != null && previousBlendWeight > 0.0F) {
                appendStateAnimations(active, activeNames, clips, snapshot, modelId, definition, blendedPreviousState,
                        instance.getPreviousStateElapsedSeconds(), previousBlendWeight, finishSummary(blendedPreviousState,
                                instance.getPreviousStateElapsedSeconds(), clips), externalVariables,
                        playingExtraAnimation);
            }
            appendStateAnimations(active, activeNames, clips, snapshot, modelId, definition, state, elapsed,
                    currentBlendWeight, outputFinishSummary, externalVariables, playingExtraAnimation);
            debugActive(snapshot, modelId, definition, previousState, instance.getCurrentState(), activeNames);
        }
        return active.isEmpty() && events.isEmpty() ? Result.EMPTY : new Result(active, events);
    }

    private static void appendEntryEvents(List<ControllerEvent> events, ControllerDefinition definition,
                                          ControllerStateDefinition state) {
        if (state == null) {
            return;
        }
        for (String event : state.getOnEntryEvents()) {
            events.add(ControllerEvent.timeline(definition.getName(), state.getName(), event));
        }
        for (String soundEffect : state.getSoundEffects()) {
            events.add(ControllerEvent.sound(definition.getName(), state.getName(), soundEffect));
        }
    }

    private static void appendExitEvents(List<ControllerEvent> events, ControllerDefinition definition,
                                         ControllerStateDefinition state) {
        if (state == null) {
            return;
        }
        for (String event : state.getOnExitEvents()) {
            events.add(ControllerEvent.timeline(definition.getName(), state.getName(), event));
        }
    }

    private static void appendStateAnimations(List<ActiveControllerAnimation> active, List<String> activeNames,
                                              Map<String, OpenYsmAnimationSet.Clip> clips, PlayerStateSnapshot snapshot,
                                              String modelId, ControllerDefinition definition,
                                              ControllerStateDefinition state, float elapsed, float layerWeight,
                                              AnimationFinishSummary finishSummary,
                                              Map<String, Double> externalVariables,
                                              boolean playingExtraAnimation) {
        for (ControllerAnimationRef animationRef : state.getAnimations()) {
                float weight = evaluateWeight(animationRef.getWeightExpression(), snapshot, modelId,
                        definition.getName(), animationRef.getAnimationName(), finishSummary, externalVariables,
                        playingExtraAnimation, elapsed) * layerWeight;
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

    private static boolean evaluateCondition(String expression, PlayerStateSnapshot snapshot, String modelId,
                                             String controllerName, String targetState,
                                             AnimationFinishSummary finishSummary,
                                             Map<String, Double> externalVariables,
                                             boolean playingExtraAnimation, float controllerAnimTimeSeconds) {
        if (expression == null || expression.trim().isEmpty()) {
            return false;
        }
        EvaluationResult result = evaluateMolang(expression, snapshot, modelId, controllerName, "transition", targetState,
                finishSummary, externalVariables, playingExtraAnimation, controllerAnimTimeSeconds);
        if (!result.valid) {
            return false;
        }
        return result.value != 0.0F;
    }

    private static float evaluateWeight(String expression, PlayerStateSnapshot snapshot, String modelId,
                                        String controllerName, String animationName,
                                        AnimationFinishSummary finishSummary,
                                        Map<String, Double> externalVariables,
                                        boolean playingExtraAnimation, float controllerAnimTimeSeconds) {
        EvaluationResult result = evaluateMolang(expression == null || expression.trim().isEmpty() ? "1" : expression,
                snapshot, modelId, controllerName, "weight", animationName, finishSummary, externalVariables,
                playingExtraAnimation, controllerAnimTimeSeconds);
        if (!result.valid) {
            return 0.0F;
        }
        return Math.max(0.0F, result.value);
    }

    private static EvaluationResult evaluateMolang(String expression, PlayerStateSnapshot snapshot, String modelId,
                                                   String controllerName, String expressionKind, String owner,
                                                   AnimationFinishSummary finishSummary,
                                                   Map<String, Double> externalVariables,
                                                   boolean playingExtraAnimation, float controllerAnimTimeSeconds) {
        try {
            MolangExpression parsed = MolangParser.parse(expression);
            Map<String, Double> variables = externalVariables == null
                    ? OpenYsmPlayerAnimationState.getGuiVariables(snapshot.uuid, modelId)
                    : externalVariables;
            MolangBindings bindings = new MolangBindings(variables, Collections.emptyMap());
            MolangContext context = MolangContext.controller(snapshot, modelId, controllerName, bindings,
                    finishSummary.allFinished, finishSummary.anyFinished, playingExtraAnimation,
                    controllerAnimTimeSeconds)
                    .withRuntimeVariables(OpenYsmPlayerAnimationState.getRuntimeVariables(snapshot.uuid, modelId));
            return EvaluationResult.valid((float) MolangEvaluator.evaluate(parsed, context).asDouble());
        } catch (MolangParser.ParseException exception) {
            debugExpressionFailure(snapshot, controllerName, expression, expressionKind, owner, exception.getMessage());
            return EvaluationResult.invalid();
        }
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
                                               String expressionKind, String owner, String reason) {
        if (!isDebugEnabled()) {
            return;
        }
        YesSteveModel.LOGGER.info("[DEBUG-animation-state] player={} controller={} expression parse errors kind={} owner={} expression={} reason={}",
                snapshot.uuid, controllerName, expressionKind, owner, expression, reason);
    }

    private static boolean isDebugEnabled() {
        return Boolean.getBoolean("yes_steve_model.debugAnimationState");
    }

    private static AnimationFinishSummary finishSummary(ControllerStateDefinition state, float elapsed,
                                                        Map<String, OpenYsmAnimationSet.Clip> clips) {
        boolean anyValid = false;
        boolean anyFinished = false;
        boolean allFinished = true;
        for (ControllerAnimationRef animationRef : state.getAnimations()) {
            OpenYsmAnimationSet.Clip clip = findClip(clips, animationRef.getAnimationName());
            if (clip == null) {
                continue;
            }
            anyValid = true;
            boolean finished = isFinished(clip, elapsed);
            anyFinished = anyFinished || finished;
            allFinished = allFinished && finished;
        }
        return new AnimationFinishSummary(anyValid && allFinished, anyFinished);
    }

    private static boolean isFinished(OpenYsmAnimationSet.Clip clip, float elapsed) {
        if (clip.length <= 0.0F) {
            return false;
        }
        if (clip.loopMode == LoopMode.LOOP) {
            return false;
        }
        return elapsed >= clip.length;
    }

    private static final class AnimationFinishSummary {
        private final boolean allFinished;
        private final boolean anyFinished;

        private AnimationFinishSummary(boolean allFinished, boolean anyFinished) {
            this.allFinished = allFinished;
            this.anyFinished = anyFinished;
        }
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
        private static final Result EMPTY = new Result(Collections.emptyList(), Collections.emptyList());
        private final List<ActiveControllerAnimation> activeAnimations;
        private final List<ControllerEvent> controllerEvents;

        private Result(List<ActiveControllerAnimation> activeAnimations, List<ControllerEvent> controllerEvents) {
            this.activeAnimations = Collections.unmodifiableList(new ArrayList<>(activeAnimations));
            this.controllerEvents = Collections.unmodifiableList(new ArrayList<>(controllerEvents));
        }

        public List<ActiveControllerAnimation> getActiveAnimations() {
            return this.activeAnimations;
        }

        public List<ControllerEvent> getControllerEvents() {
            return this.controllerEvents;
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

    public static final class ControllerEvent {
        public enum Kind {
            TIMELINE,
            SOUND
        }

        private final Kind kind;
        private final String controllerName;
        private final String stateName;
        private final String value;

        private ControllerEvent(Kind kind, String controllerName, String stateName, String value) {
            this.kind = kind == null ? Kind.TIMELINE : kind;
            this.controllerName = controllerName == null ? "" : controllerName;
            this.stateName = stateName == null ? "" : stateName;
            this.value = value == null ? "" : value;
        }

        private static ControllerEvent timeline(String controllerName, String stateName, String value) {
            return new ControllerEvent(Kind.TIMELINE, controllerName, stateName, value);
        }

        private static ControllerEvent sound(String controllerName, String stateName, String value) {
            return new ControllerEvent(Kind.SOUND, controllerName, stateName, value);
        }

        public Kind getKind() {
            return this.kind;
        }

        public String getControllerName() {
            return this.controllerName;
        }

        public String getStateName() {
            return this.stateName;
        }

        public String getValue() {
            return this.value;
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
