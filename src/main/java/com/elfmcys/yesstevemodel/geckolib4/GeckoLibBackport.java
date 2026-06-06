package com.elfmcys.yesstevemodel.geckolib4;

import com.elfmcys.yesstevemodel.YesSteveModel;

/**
 * MCP-local GeckoLib 4 backport anchor.
 *
 * <p>The source baseline is GeckoLib Forge 1.20.1 / 4.8.3. Platform hooks that
 * are ForgeGradle, Fabric, datagen, or Mixin-specific are intentionally routed
 * through MCP shims in this package instead of a runtime jar dependency.</p>
 */
public final class GeckoLibBackport {
    public static final String SOURCE_VERSION = "geckolib-forge-1.20.1-4.8.3";
    public static final String SOURCE_GIT = "https://github.com/bernie-g/geckolib/tree/1.20.1";

    private static boolean initialized;

    private GeckoLibBackport() {
    }

    public static synchronized void initialize() {
        if (initialized) {
            return;
        }

        initialized = true;
        YesSteveModel.LOGGER.info("[YSM][GL4] Initialized MCP GeckoLib 4 backport baseline {}", SOURCE_VERSION);
    }
}
