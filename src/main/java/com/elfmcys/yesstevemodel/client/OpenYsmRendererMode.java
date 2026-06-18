package com.elfmcys.yesstevemodel.client;

import java.util.Locale;

public enum OpenYsmRendererMode {
    VANILLA("vanilla"),
    LEGACY_SELF("legacy_self"),
    GL4("gl4"),
    OPENYSM("openysm");

    private final String propertyValue;

    OpenYsmRendererMode(String propertyValue) {
        this.propertyValue = propertyValue;
    }

    public boolean usesYsm() {
        return this != VANILLA;
    }

    public boolean usesGl4Renderer() {
        return this == GL4 || this == OPENYSM;
    }

    public String propertyValue() {
        return this.propertyValue;
    }

    public static OpenYsmRendererMode fromProperty(String value) {
        String normalized = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        for (OpenYsmRendererMode mode : values()) {
            if (mode.propertyValue.equals(normalized)) {
                return mode;
            }
        }
        return OPENYSM;
    }
}
