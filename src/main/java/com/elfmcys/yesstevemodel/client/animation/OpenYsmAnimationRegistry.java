package com.elfmcys.yesstevemodel.client.animation;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class OpenYsmAnimationRegistry {
    private static final Map<String, Set<String>> WHEEL_ACTIONS_BY_MODEL = new ConcurrentHashMap<>();

    private OpenYsmAnimationRegistry() {
    }

    public static void registerWheelActions(String modelId, Set<String> actionNames) {
        if (modelId == null || modelId.isEmpty()) {
            return;
        }
        WHEEL_ACTIONS_BY_MODEL.put(modelId, Collections.unmodifiableSet(new LinkedHashSet<>(actionNames)));
    }

    public static boolean isWheelActionAllowed(String modelId, String animationName) {
        Set<String> actions = WHEEL_ACTIONS_BY_MODEL.get(modelId);
        return actions != null && actions.contains(animationName);
    }

    public static Set<String> listWheelActions(String modelId) {
        Set<String> actions = WHEEL_ACTIONS_BY_MODEL.get(modelId);
        return actions == null ? Collections.emptySet() : actions;
    }

    public static void clearModel(String modelId) {
        if (modelId != null) {
            WHEEL_ACTIONS_BY_MODEL.remove(modelId);
        }
    }
}
