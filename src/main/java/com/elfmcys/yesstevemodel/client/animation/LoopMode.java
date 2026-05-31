package com.elfmcys.yesstevemodel.client.animation;

import com.google.gson.JsonElement;

import java.util.Locale;

public enum LoopMode {
    PLAY_ONCE,
    LOOP,
    HOLD_ON_LAST_FRAME;

    public static LoopMode fromJson(JsonElement element) {
        if (element == null || element.isJsonNull()) {
            return PLAY_ONCE;
        }
        try {
            if (element.isJsonPrimitive()) {
                if (element.getAsJsonPrimitive().isBoolean()) {
                    return element.getAsBoolean() ? LOOP : PLAY_ONCE;
                }
                if (element.getAsJsonPrimitive().isNumber()) {
                    return fromRaw(element.getAsInt());
                }
                String value = element.getAsString().toLowerCase(Locale.ROOT);
                if ("true".equals(value) || "loop".equals(value)) {
                    return LOOP;
                }
                if ("hold_on_last_frame".equals(value) || "hold".equals(value)) {
                    return HOLD_ON_LAST_FRAME;
                }
            }
        } catch (RuntimeException ignored) {
            return PLAY_ONCE;
        }
        return PLAY_ONCE;
    }

    public static LoopMode fromRaw(int raw) {
        return switch (raw) {
            case 1 -> LOOP;
            case 2 -> HOLD_ON_LAST_FRAME;
            default -> PLAY_ONCE;
        };
    }

    public float time(float elapsedSeconds, float lengthSeconds) {
        if (lengthSeconds <= 0.0F) {
            return 0.0F;
        }
        return switch (this) {
            case LOOP -> elapsedSeconds % lengthSeconds;
            case HOLD_ON_LAST_FRAME -> Math.min(elapsedSeconds, lengthSeconds);
            case PLAY_ONCE -> Math.min(elapsedSeconds, lengthSeconds);
        };
    }
}
