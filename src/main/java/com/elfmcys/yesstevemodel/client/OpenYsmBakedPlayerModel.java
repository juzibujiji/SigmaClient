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
    private final List<OpenYsmBone> armRootBones;
    private final Map<String, OpenYsmBone> armBones;
    private final BakedGeoModel armGeoModel;
    private final ResourceLocation armGeoModelResource;
    private final OpenYsmAnimationSet animations;
    private final float widthScale;
    private final float heightScale;
    private final float footModelY;
    private final boolean renderLayersFirst;
    private final boolean allCutout;
    private final boolean disablePreviewRotation;
    private final boolean guiNoLighting;
    private final List<OpenYsmExtraEntityModel> extraEntityModels;
    private OpenYsmModelDebugInfo debugInfo;

    public OpenYsmBakedPlayerModel(String id, ResourceLocation texture, List<OpenYsmBone> rootBones,
                                   Map<String, OpenYsmBone> bones, OpenYsmAnimationSet animations,
                                   float widthScale, float heightScale) {
        this(id, texture, rootBones, bones, null, animations, widthScale, heightScale);
    }

    public OpenYsmBakedPlayerModel(String id, ResourceLocation texture, List<OpenYsmBone> rootBones,
                                   Map<String, OpenYsmBone> bones, BakedGeoModel geoModel,
                                   OpenYsmAnimationSet animations, float widthScale, float heightScale) {
        this(id, texture, rootBones, bones, geoModel, null, null, null, animations, widthScale, heightScale);
    }

    public OpenYsmBakedPlayerModel(String id, ResourceLocation texture, List<OpenYsmBone> rootBones,
                                   Map<String, OpenYsmBone> bones, BakedGeoModel geoModel,
                                   List<OpenYsmBone> armRootBones, Map<String, OpenYsmBone> armBones,
                                   BakedGeoModel armGeoModel, OpenYsmAnimationSet animations,
                                   float widthScale, float heightScale) {
        this(id, texture, rootBones, bones, geoModel, armRootBones, armBones, armGeoModel,
                animations, widthScale, heightScale, 24.0F);
    }

    public OpenYsmBakedPlayerModel(String id, ResourceLocation texture, List<OpenYsmBone> rootBones,
                                   Map<String, OpenYsmBone> bones, BakedGeoModel geoModel,
                                   List<OpenYsmBone> armRootBones, Map<String, OpenYsmBone> armBones,
                                   BakedGeoModel armGeoModel, OpenYsmAnimationSet animations,
                                   float widthScale, float heightScale, float footModelY) {
        this(id, texture, rootBones, bones, geoModel, armRootBones, armBones, armGeoModel, animations,
                widthScale, heightScale, footModelY, false, false, false, false, java.util.Collections.emptyList());
    }

    public OpenYsmBakedPlayerModel(String id, ResourceLocation texture, List<OpenYsmBone> rootBones,
                                   Map<String, OpenYsmBone> bones, BakedGeoModel geoModel,
                                   List<OpenYsmBone> armRootBones, Map<String, OpenYsmBone> armBones,
                                   BakedGeoModel armGeoModel, OpenYsmAnimationSet animations,
                                   float widthScale, float heightScale, float footModelY,
                                   boolean renderLayersFirst, boolean allCutout,
                                   boolean disablePreviewRotation, boolean guiNoLighting) {
        this(id, texture, rootBones, bones, geoModel, armRootBones, armBones, armGeoModel, animations,
                widthScale, heightScale, footModelY, renderLayersFirst, allCutout,
                disablePreviewRotation, guiNoLighting, java.util.Collections.emptyList());
    }

    public OpenYsmBakedPlayerModel(String id, ResourceLocation texture, List<OpenYsmBone> rootBones,
                                   Map<String, OpenYsmBone> bones, BakedGeoModel geoModel,
                                   List<OpenYsmBone> armRootBones, Map<String, OpenYsmBone> armBones,
                                   BakedGeoModel armGeoModel, OpenYsmAnimationSet animations,
                                   float widthScale, float heightScale, float footModelY,
                                   boolean renderLayersFirst, boolean allCutout,
                                   boolean disablePreviewRotation, boolean guiNoLighting,
                                   List<OpenYsmExtraEntityModel> extraEntityModels) {
        this.id = id;
        this.texture = texture;
        this.rootBones = rootBones;
        this.bones = bones;
        this.geoModel = geoModel;
        this.geoModelResource = geoModel == null ? null : geoModelResource(id, "main");
        if (this.geoModelResource != null) {
            GeckoLibCache.registerBakedModel(this.geoModelResource, geoModel);
        }
        this.armRootBones = armRootBones == null ? rootBones : armRootBones;
        this.armBones = armBones == null ? bones : armBones;
        this.armGeoModel = armGeoModel == null ? geoModel : armGeoModel;
        this.armGeoModelResource = armGeoModel == null ? this.geoModelResource : geoModelResource(id, "arm");
        if (armGeoModel != null && this.armGeoModelResource != null) {
            GeckoLibCache.registerBakedModel(this.armGeoModelResource, armGeoModel);
        }
        this.animations = animations;
        this.widthScale = widthScale;
        this.heightScale = heightScale;
        this.footModelY = footModelY <= 0.0F ? 24.0F : footModelY;
        this.renderLayersFirst = renderLayersFirst;
        this.allCutout = allCutout;
        this.disablePreviewRotation = disablePreviewRotation;
        this.guiNoLighting = guiNoLighting;
        this.extraEntityModels = extraEntityModels == null ? java.util.Collections.emptyList() : extraEntityModels;
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

    public boolean hasCustomArmModel() {
        return this.armBones != this.bones && this.armGeoModel != null;
    }

    public List<OpenYsmBone> getArmRootBones() {
        return this.armRootBones;
    }

    public Map<String, OpenYsmBone> getArmBones() {
        return this.armBones;
    }

    public BakedGeoModel getArmGeoModel() {
        return this.armGeoModel;
    }

    public ResourceLocation getArmGeoModelResource() {
        return this.armGeoModelResource;
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

    public float getGroundOffsetY() {
        return 1.501F - (this.heightScale * this.footModelY / 16.0F);
    }

    public boolean isRenderLayersFirst() {
        return this.renderLayersFirst;
    }

    public boolean isAllCutout() {
        return this.allCutout;
    }

    public boolean isDisablePreviewRotation() {
        return this.disablePreviewRotation;
    }

    public boolean isGuiNoLighting() {
        return this.guiNoLighting;
    }

    public List<OpenYsmExtraEntityModel> getExtraEntityModels() {
        return this.extraEntityModels;
    }

    public OpenYsmExtraEntityModel findExtraEntityModel(OpenYsmExtraEntityModel.Kind kind, String id) {
        for (OpenYsmExtraEntityModel model : this.extraEntityModels) {
            if (model.getKind() == kind && model.matches(id)) {
                return model;
            }
        }
        return null;
    }

    public OpenYsmModelDebugInfo getDebugInfo() {
        return this.debugInfo;
    }

    public void setDebugInfo(OpenYsmModelDebugInfo debugInfo) {
        this.debugInfo = debugInfo;
    }

    private static ResourceLocation geoModelResource(String id, String part) {
        String hash = Integer.toHexString(id == null ? 0 : id.hashCode());
        return new ResourceLocation(YesSteveModel.MOD_ID, "openysm/" + sanitizeResourcePath(id) + "_" + part + "_" + hash);
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
