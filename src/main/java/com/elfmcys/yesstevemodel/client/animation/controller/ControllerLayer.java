package com.elfmcys.yesstevemodel.client.animation.controller;

public enum ControllerLayer {
    PRE_MAIN,
    MAIN,
    POST_MAIN,
    PARALLEL,
    HOLD_MAINHAND,
    HOLD_OFFHAND,
    USE,
    SWING,
    UNKNOWN;

    public static ControllerLayer fromControllerName(String name) {
        String lower = name == null ? "" : name.toLowerCase(java.util.Locale.ROOT);
        if (lower.contains("player.pre_main") || lower.contains("pre_main")) {
            return PRE_MAIN;
        }
        if (lower.contains("player.post_main") || lower.contains("post_main")) {
            return POST_MAIN;
        }
        if (lower.contains("player.pre_parallel") || lower.contains("pre_parallel")) {
            return PRE_MAIN;
        }
        if (lower.contains("player.parallel_") || lower.contains("parallel_")) {
            return PARALLEL;
        }
        if (lower.contains("player.hold_mainhand") || lower.contains("hold_mainhand")) {
            return HOLD_MAINHAND;
        }
        if (lower.contains("player.hold_offhand") || lower.contains("hold_offhand")) {
            return HOLD_OFFHAND;
        }
        if (lower.contains("player.use") || lower.endsWith(".use") || lower.endsWith("_use")) {
            return USE;
        }
        if (lower.contains("player.swing") || lower.endsWith(".swing") || lower.endsWith("_swing")) {
            return SWING;
        }
        if (lower.contains("player.main") || lower.endsWith(".main") || lower.endsWith("_main")) {
            return MAIN;
        }
        return UNKNOWN;
    }

    public boolean replacesHardcodedMain() {
        return this == MAIN;
    }
}
