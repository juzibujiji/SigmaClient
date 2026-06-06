package com.elfmcys.yesstevemodel.geckolib4.model;

import com.elfmcys.yesstevemodel.geckolib4.cache.GeckoLibCache;
import com.elfmcys.yesstevemodel.geckolib4.cache.object.BakedGeoModel;
import com.elfmcys.yesstevemodel.geckolib4.cache.object.GeoBone;
import com.elfmcys.yesstevemodel.geckolib4.compat.McpRenderAdapter;
import com.elfmcys.yesstevemodel.geckolib4.core.animatable.GeoAnimatable;
import com.elfmcys.yesstevemodel.geckolib4.core.animation.AnimationState;
import com.elfmcys.yesstevemodel.geckolib4.renderer.GeoRenderer;
import net.minecraft.client.renderer.IRenderTypeBuffer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.util.ResourceLocation;

import java.util.Optional;
import java.util.function.BiConsumer;

/**
 * GeckoLib 4 style model base adapted to the MCP 1.16.4 cache/render objects.
 */
public abstract class GeoModel<T extends GeoAnimatable> {
    private BakedGeoModel currentModel;

    public ResourceLocation getModelResource(T animatable, GeoRenderer renderer) {
        return getModelResource(animatable);
    }

    public abstract ResourceLocation getModelResource(T animatable);

    public ResourceLocation getTextureResource(T animatable, GeoRenderer renderer) {
        return getTextureResource(animatable);
    }

    public abstract ResourceLocation getTextureResource(T animatable);

    public abstract ResourceLocation getAnimationResource(T animatable);

    public ResourceLocation[] getAnimationResourceFallbacks(T animatable) {
        return new ResourceLocation[0];
    }

    public boolean crashIfBoneMissing() {
        return false;
    }

    public RenderType getRenderType(T animatable, ResourceLocation texture) {
        return McpRenderAdapter.entityCutoutNoCull(texture);
    }

    public RenderType getRenderType(T animatable, ResourceLocation texture,
                                    IRenderTypeBuffer bufferSource, float partialTick) {
        return getRenderType(animatable, texture);
    }

    public BakedGeoModel getBakedModel(ResourceLocation location) {
        BakedGeoModel model = GeckoLibCache.getBakedModel(location);
        if (model == null) {
            throw new IllegalStateException("Unable to find baked geo model: " + location);
        }
        this.currentModel = model;
        return model;
    }

    public BakedGeoModel getCurrentModel() {
        return this.currentModel;
    }

    public Optional<GeoBone> getBone(String name) {
        return this.currentModel == null ? Optional.empty() : this.currentModel.getBone(name);
    }

    public Optional<GeoBone> getBone(ResourceLocation modelLocation, String name) {
        return getBakedModel(modelLocation).getBone(name);
    }

    public void addAdditionalStateData(T animatable, long instanceId, BiConsumer<String, Object> dataConsumer) {
    }

    public void handleAnimations(T animatable, long instanceId, AnimationState<T> animationState) {
        setCustomAnimations(animatable, instanceId, animationState);
    }

    public void setCustomAnimations(T animatable, long instanceId, AnimationState<T> animationState) {
    }
}
