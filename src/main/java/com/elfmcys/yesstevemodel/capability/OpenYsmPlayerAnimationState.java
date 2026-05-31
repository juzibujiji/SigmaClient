package com.elfmcys.yesstevemodel.capability;

import com.elfmcys.yesstevemodel.client.animation.ActionSource;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class OpenYsmPlayerAnimationState {
    private static final Map<UUID, State> STATES = new ConcurrentHashMap<>();

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
