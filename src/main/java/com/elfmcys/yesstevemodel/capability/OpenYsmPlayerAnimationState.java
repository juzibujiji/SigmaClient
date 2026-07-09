package com.elfmcys.yesstevemodel.capability;

import com.elfmcys.yesstevemodel.client.animation.ActionSource;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;

import java.util.Map;
import java.util.HashMap;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class OpenYsmPlayerAnimationState {
    private static final Map<UUID, State> STATES = new ConcurrentHashMap<>();
    private static final Map<UUID, Map<String, Map<String, Double>>> GUI_VARIABLES = new ConcurrentHashMap<>();
    private static final Map<UUID, Map<String, Map<String, Double>>> RUNTIME_VARIABLES = new ConcurrentHashMap<>();

    private OpenYsmPlayerAnimationState() {
    }

    public static State get(UUID playerId) {
        return playerId == null ? null : STATES.get(playerId);
    }

    public static State get(Entity entity) {
        return entity == null ? null : get(entity.getUniqueID());
    }

    public static void play(PlayerEntity player, String modelId, String animationName, ActionSource source) {
        if (player == null) {
            return;
        }
        play(player.getUniqueID(), modelId, animationName, player.ticksExisted, source);
    }

    public static void play(UUID playerId, String modelId, String animationName, float startAgeTicks, ActionSource source) {
        if (playerId == null || modelId == null || modelId.isEmpty() || animationName == null || animationName.isEmpty()) {
            return;
        }
        STATES.put(playerId, new State(modelId, animationName, startAgeTicks, source));
    }

    public static void stop(PlayerEntity player) {
        if (player != null) {
            STATES.remove(player.getUniqueID());
        }
    }

    public static void stop(UUID playerId) {
        if (playerId != null) {
            STATES.remove(playerId);
        }
    }

    public static void stop(UUID playerId, String modelId, String animationName) {
        if (playerId == null) {
            return;
        }
        State state = STATES.get(playerId);
        if (state != null
                && state.modelId.equals(modelId)
                && (animationName == null || animationName.equals(state.animationName))) {
            STATES.remove(playerId);
        }
    }

    public static void clearModel(String modelId) {
        if (modelId == null || modelId.isEmpty()) {
            return;
        }
        STATES.entrySet().removeIf(entry -> modelId.equals(entry.getValue().modelId));
        for (Map<String, Map<String, Double>> byModel : GUI_VARIABLES.values()) {
            byModel.remove(modelId);
        }
        for (Map<String, Map<String, Double>> byModel : RUNTIME_VARIABLES.values()) {
            byModel.remove(modelId);
        }
    }

    public static void clearAll() {
        STATES.clear();
        GUI_VARIABLES.clear();
        RUNTIME_VARIABLES.clear();
    }

    public static void setGuiVariable(UUID playerId, String modelId, String variableName, double value) {
        if (playerId == null || modelId == null || modelId.isEmpty() || variableName == null || variableName.isEmpty()) {
            return;
        }
        GUI_VARIABLES
                .computeIfAbsent(playerId, ignored -> new ConcurrentHashMap<>())
                .computeIfAbsent(modelId, ignored -> new ConcurrentHashMap<>())
                .put(variableName.toLowerCase(java.util.Locale.ROOT), value);
    }

    public static Map<String, Double> getGuiVariables(UUID playerId, String modelId) {
        if (playerId == null || modelId == null || modelId.isEmpty()) {
            return java.util.Collections.emptyMap();
        }
        Map<String, Map<String, Double>> byModel = GUI_VARIABLES.get(playerId);
        if (byModel == null) {
            return java.util.Collections.emptyMap();
        }
        Map<String, Double> variables = byModel.get(modelId);
        return variables == null ? java.util.Collections.emptyMap() : new HashMap<>(variables);
    }

    /**
     * Live, mutable per-player/per-model molang variable store used by animation expression
     * assignments (e.g. a "molang" driver bone running {@code v.bv=...} each frame). Values
     * persist across frames and are shared by every expression evaluated for the same player
     * and model, mirroring real YSM's single {@code v.*} namespace.
     */
    public static Map<String, Double> getRuntimeVariables(UUID playerId, String modelId) {
        if (playerId == null || modelId == null || modelId.isEmpty()) {
            return null;
        }
        return RUNTIME_VARIABLES
                .computeIfAbsent(playerId, ignored -> new ConcurrentHashMap<>())
                .computeIfAbsent(modelId, ignored -> new ConcurrentHashMap<>());
    }

    public static final class State {
        private final String modelId;
        private final String animationName;
        private final float startAgeTicks;
        private final ActionSource source;

        private State(String modelId, String animationName, float startAgeTicks, ActionSource source) {
            this.modelId = modelId;
            this.animationName = animationName;
            this.startAgeTicks = startAgeTicks;
            this.source = source == null ? ActionSource.UNKNOWN : source;
        }

        public String getModelId() {
            return this.modelId;
        }

        public String getAnimationName() {
            return this.animationName;
        }

        public float getStartAgeTicks() {
            return this.startAgeTicks;
        }

        public ActionSource getSource() {
            return this.source;
        }

        public float elapsedSeconds(float ageInTicks) {
            return Math.max(0.0F, (ageInTicks - this.startAgeTicks) / 20.0F);
        }
    }
}
