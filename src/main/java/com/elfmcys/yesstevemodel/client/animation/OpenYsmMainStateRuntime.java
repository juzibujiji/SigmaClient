package com.elfmcys.yesstevemodel.client.animation;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-player runtime for the hardcoded main-state selection (idle/walk/run/...): tracks when
 * each state was entered so clips play from their own start (PLAY_ONCE states like jump sample
 * correctly), and cross-fades between the outgoing and incoming state over a short window so
 * flickering inputs (onGround on stairs, water surface bobbing) no longer snap the pose.
 */
public final class OpenYsmMainStateRuntime {
    private static final float BLEND_SECONDS = 0.15F;
    private static final Map<String, Instance> INSTANCES = new ConcurrentHashMap<>();

    private OpenYsmMainStateRuntime() {
    }

    public static void clearAll() {
        INSTANCES.clear();
    }

    public static void clearModel(String modelId) {
        if (modelId == null || modelId.isEmpty()) {
            return;
        }
        String suffix = "|" + modelId;
        INSTANCES.keySet().removeIf(key -> key.endsWith(suffix));
    }

    public static Result advance(UUID playerId, String modelId, String stateName, float ageInTicks) {
        String key = playerId + "|" + modelId;
        Instance instance = INSTANCES.computeIfAbsent(key, ignored -> new Instance(stateName, ageInTicks));
        synchronized (instance) {
            if (ageInTicks < instance.currentStartAge - 0.001F) {
                // Time went backwards (respawn, dimension change): restart cleanly.
                instance.reset(stateName, ageInTicks);
            } else if (!instance.currentName.equals(stateName)) {
                instance.previousName = instance.currentName;
                instance.previousStartAge = instance.currentStartAge;
                instance.currentName = stateName;
                instance.currentStartAge = ageInTicks;
            }

            float currentElapsedSeconds = Math.max(0.0F, (ageInTicks - instance.currentStartAge) / 20.0F);
            float blendProgress = instance.previousName == null
                    ? 1.0F
                    : Math.min(1.0F, currentElapsedSeconds / BLEND_SECONDS);
            String previousName = instance.previousName;
            float previousElapsedSeconds = previousName == null
                    ? 0.0F
                    : Math.max(0.0F, (ageInTicks - instance.previousStartAge) / 20.0F);
            if (blendProgress >= 1.0F) {
                instance.previousName = null;
            }
            return new Result(currentElapsedSeconds, blendProgress,
                    blendProgress >= 1.0F ? null : previousName, previousElapsedSeconds, 1.0F - blendProgress);
        }
    }

    public static final class Result {
        public final float currentElapsedSeconds;
        public final float currentWeight;
        public final String previousName;
        public final float previousElapsedSeconds;
        public final float previousWeight;

        private Result(float currentElapsedSeconds, float currentWeight, String previousName,
                       float previousElapsedSeconds, float previousWeight) {
            this.currentElapsedSeconds = currentElapsedSeconds;
            this.currentWeight = currentWeight;
            this.previousName = previousName;
            this.previousElapsedSeconds = previousElapsedSeconds;
            this.previousWeight = previousWeight;
        }
    }

    private static final class Instance {
        private String currentName;
        private float currentStartAge;
        private String previousName;
        private float previousStartAge;

        private Instance(String currentName, float startAge) {
            this.currentName = currentName;
            this.currentStartAge = startAge;
        }

        private void reset(String stateName, float ageInTicks) {
            this.currentName = stateName;
            this.currentStartAge = ageInTicks;
            this.previousName = null;
            this.previousStartAge = ageInTicks;
        }
    }
}
