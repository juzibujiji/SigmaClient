package de.florianmichael.viamcp.fixes.compat;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class ContainerStateTracker {
    private static final Map<Integer, Integer> STATE_IDS = new ConcurrentHashMap<>();
    private static volatile int lastWindowId;
    private static volatile int lastStateId;

    private ContainerStateTracker() {
    }

    public static void track(int windowId, int stateId) {
        lastWindowId = windowId;
        lastStateId = stateId;
        STATE_IDS.put(windowId, stateId);
    }

    public static void reset(int windowId) {
        STATE_IDS.remove(windowId);
        if (lastWindowId == windowId) {
            lastStateId = 0;
        }
    }

    public static int stateId(int windowId) {
        return STATE_IDS.getOrDefault(windowId, windowId == lastWindowId ? lastStateId : 0);
    }

    public static int lastStateId() {
        return lastStateId;
    }
}
