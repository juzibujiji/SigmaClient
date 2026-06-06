package com.elfmcys.yesstevemodel.client;

import com.elfmcys.yesstevemodel.YesSteveModel;
import com.elfmcys.yesstevemodel.geckolib4.GeckoLibBackport;

public final class OpenYsmRendererSelector {
    public static final String PROPERTY = "yes_steve_model.renderer";

    private static String lastRawValue;
    private static OpenYsmRendererMode lastMode = OpenYsmRendererMode.LEGACY_SELF;

    private OpenYsmRendererSelector() {
    }

    public static OpenYsmRendererMode getMode() {
        String raw = System.getProperty(PROPERTY, OpenYsmRendererMode.LEGACY_SELF.propertyValue());
        if (!raw.equals(lastRawValue)) {
            lastRawValue = raw;
            lastMode = OpenYsmRendererMode.fromProperty(raw);
            if (lastMode.usesGl4Renderer()) {
                GeckoLibBackport.initialize();
            }
            if (!lastMode.propertyValue().equals(raw.trim())) {
                YesSteveModel.LOGGER.warn("[YSM] Unknown renderer mode '{}'; using {}", raw, lastMode.propertyValue());
            } else {
                YesSteveModel.LOGGER.info("[YSM] Renderer mode: {}", lastMode.propertyValue());
            }
        }
        return lastMode;
    }
}
