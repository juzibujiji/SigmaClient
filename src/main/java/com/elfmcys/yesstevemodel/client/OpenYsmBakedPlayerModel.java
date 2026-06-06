package com.elfmcys.yesstevemodel.client;

import com.elfmcys.yesstevemodel.YesSteveModel;
import com.elfmcys.yesstevemodel.client.animation.OpenYsmAnimationSet;
import com.elfmcys.yesstevemodel.geckolib4.cache.GeckoLibCache;
import com.elfmcys.yesstevemodel.geckolib4.cache.object.BakedGeoModel;
import net.minecraft.util.ResourceLocation;

import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class OpenYsmBakedPlayerModel {
    private final String id;
    private final ResourceLocation texture;
    private final List<OpenYsmBone> rootBones;
    private final Map<String, OpenYsmBone> bones;
    private final BakedGeoModel geoModel;
    private final ResourceLocation geoModelResource;
    private final OpenYsmAnimationSet animations;
    private final float widthScale;
    private final float heightScale;
    private OpenYsmModelDebugInfo debugInfo;

    public OpenYsmBakedPlayerModel(String id, ResourceLocation texture, List<OpenYsmBone> rootBones,
                                   Map<String, OpenYsmBone> bones, OpenYsmAnimationSet animations,
                                   float widthScale, float heightScale) {
        this(id, texture, rootBones, bones, null, animations, widthScale, heightScale);
    }

    public OpenYsmBakedPlayerModel(String id, ResourceLocation texture, List<OpenYsmBone> rootBones,
                                   Map<String, OpenYsmBone> bones, BakedGeoModel geoModel,
                                   OpenYsmAnimationSet animations, float widthScale, float heightScale) {
        this.id = id;
        this.texture = texture;
        this.rootBones = rootBones;
        this.bones = bones;
        this.geoModel = geoModel;
        this.geoModelResource = geoModel == null ? null : geoModelResource(id);
        if (this.geoModelResource != null) {
            GeckoLibCache.registerBakedModel(this.geoModelResource, geoModel);
        }
        this.animations = animations;
        this.widthScale = widthScale;
        this.heightScale = heightScale;
    }

    public String getId() {
        return this.id;
    }

    public ResourceLocation getTexture() {
        return this.texture;
    }

    public List<OpenYsmBone> getRootBones() {
        return this.rootBones;
    }

    public Map<String, OpenYsmBone> getBones() {
        return this.bones;
    }

    public BakedGeoModel getGeoModel() {
        return this.geoModel;
    }

    public ResourceLocation getGeoModelResource() {
        return this.geoModelResource;
    }

    public OpenYsmAnimationSet getAnimations() {
        return this.animations;
    }

    public float getWidthScale() {
        return this.widthScale;
    }

    public float getHeightScale() {
        return this.heightScale;
    }

    public OpenYsmModelDebugInfo getDebugInfo() {
        return this.debugInfo;
    }

    public void setDebugInfo(OpenYsmModelDebugInfo debugInfo) {
        this.debugInfo = debugInfo;
    }

    private static ResourceLocation geoModelResource(String id) {
        String hash = Integer.toHexString(id == null ? 0 : id.hashCode());
        return new ResourceLocation(YesSteveModel.MOD_ID, "openysm/" + sanitizeResourcePath(id) + "_" + hash);
    }

    private static String sanitizeResourcePath(String value) {
        if (value == null || value.trim().isEmpty()) {
            return "model";
        }

        String normalized = value.trim().toLowerCase(Locale.ROOT).replace('\\', '/');
        StringBuilder builder = new StringBuilder(normalized.length());
        for (int i = 0; i < normalized.length(); i++) {
            char c = normalized.charAt(i);
            if ((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '/' || c == '_' || c == '-' || c == '.') {
                builder.append(c);
            } else {
                builder.append('_');
            }
        }

        while (builder.length() > 0 && builder.charAt(0) == '/') {
            builder.deleteCharAt(0);
        }
        while (builder.length() > 0 && builder.charAt(builder.length() - 1) == '/') {
            builder.deleteCharAt(builder.length() - 1);
        }
        return builder.length() == 0 ? "model" : builder.toString();
    }
}
