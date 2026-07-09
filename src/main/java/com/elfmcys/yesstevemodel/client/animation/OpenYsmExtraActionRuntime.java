package com.elfmcys.yesstevemodel.client.animation;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-player blend-out for extra/wheel actions. When an action ends (natural PLAY_ONCE end or
 * explicit stop) the outgoing clip keeps being sampled at its stop frame with a weight decaying
 * to 0 over {@link #BLEND_OUT_SECONDS}, while the main state (applied underneath at full weight)
 * ramps in. Without this the extra clip's weight drops full->0 in one frame and the pose snaps to
 * idle. Mirrors {@link OpenYsmMainStateRuntime}.
 */
public final class OpenYsmExtraActionRuntime {
    private static final float BLEND_OUT_SECONDS = 0.2F;
    private static final Map<String, Instance> INSTANCES = new ConcurrentHashMap<>();

    private OpenYsmExtraActionRuntime() {
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

    /**
     * @param activeName the extra action playing this frame, or null when none is active.
     * @return a fade-out descriptor while an ended action is still decaying, else null.
     */
    public static FadeOut advance(UUID playerId, String modelId, String activeName,
                                  float activeElapsedSeconds, float ageInTicks) {
        String key = playerId + "|" + modelId;
        Instance instance = INSTANCES.computeIfAbsent(key, ignored -> new Instance());
        synchronized (instance) {
            if (activeName != null) {
                instance.pendingName = activeName;
                instance.pendingElapsed = activeElapsedSeconds;
                instance.fadingName = null; // a live action supersedes any in-flight fade
                return null;
            }
            if (instance.pendingName != null) {
                // Action ended this frame: freeze at its stop frame and start the blend-out.
                instance.fadingName = instance.pendingName;
                instance.fadeFreezeElapsed = instance.pendingElapsed;
                instance.fadeStartAge = ageInTicks;
                instance.pendingName = null;
            }
            if (instance.fadingName == null || ageInTicks < instance.fadeStartAge - 0.001F) {
                instance.fadingName = null;
                return null;
            }
            float fadeElapsed = Math.max(0.0F, (ageInTicks - instance.fadeStartAge) / 20.0F);
            float weight = 1.0F - Math.min(1.0F, fadeElapsed / BLEND_OUT_SECONDS);
            if (weight <= 0.0F) {
                instance.fadingName = null;
                return null;
            }
            return new FadeOut(instance.fadingName, instance.fadeFreezeElapsed, weight);
        }
    }

    public static final class FadeOut {
        public final String name;
        public final float freezeElapsedSeconds;
        public final float weight;

        private FadeOut(String name, float freezeElapsedSeconds, float weight) {
            this.name = name;
            this.freezeElapsedSeconds = freezeElapsedSeconds;
            this.weight = weight;
        }
    }

    private static final class Instance {
        private String pendingName;
        private float pendingElapsed;
        private String fadingName;
        private float fadeFreezeElapsed;
        private float fadeStartAge;
    }
}
