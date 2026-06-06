package com.elfmcys.yesstevemodel.geckolib4.cache;

import com.elfmcys.yesstevemodel.geckolib4.cache.object.BakedGeoModel;
import net.minecraft.util.ResourceLocation;

import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Minimal GeckoLib 4 style client cache for baked MCP models.
 */
public final class GeckoLibCache {
    private static final Map<ResourceLocation, BakedGeoModel> BAKED_MODELS = new ConcurrentHashMap<>();

    private GeckoLibCache() {
    }

    public static Map<ResourceLocation, BakedGeoModel> getBakedModels() {
        return BAKED_MODELS;
    }

    public static Map<ResourceLocation, BakedGeoModel> snapshotBakedModels() {
        return Collections.unmodifiableMap(BAKED_MODELS);
    }

    public static BakedGeoModel getBakedModel(ResourceLocation location) {
        return BAKED_MODELS.get(location);
    }

    public static BakedGeoModel registerBakedModel(ResourceLocation location, BakedGeoModel model) {
        if (location == null) {
            throw new IllegalArgumentException("location");
        }
        if (model == null) {
            throw new IllegalArgumentException("model");
        }
        BAKED_MODELS.put(location, model);
        return model;
    }

    public static void removeBakedModel(ResourceLocation location) {
        BAKED_MODELS.remove(location);
    }

    public static void clear() {
        BAKED_MODELS.clear();
    }
}
