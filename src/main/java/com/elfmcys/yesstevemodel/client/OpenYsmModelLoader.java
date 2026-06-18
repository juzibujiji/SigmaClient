package com.elfmcys.yesstevemodel.client;

import com.elfmcys.yesstevemodel.OpenYsmArchiveUtil;
import com.elfmcys.yesstevemodel.OpenYsmModelEntry;
import com.elfmcys.yesstevemodel.client.animation.OpenYsmAnimationSet;
import com.elfmcys.yesstevemodel.geckolib4.cache.object.BakedGeoModel;
import com.elfmcys.yesstevemodel.geckolib4.cache.object.GeoBone;
import com.elfmcys.yesstevemodel.geckolib4.cache.object.GeoCube;
import com.elfmcys.yesstevemodel.resource.pojo.RawYsmModel;
import com.elfmcys.yesstevemodel.OpenYsmTextureOption;
import com.elfmcys.yesstevemodel.YesSteveModel;
import com.google.common.collect.ImmutableList;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.model.ModelRenderer;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.NativeImage;
import net.minecraft.resources.IResource;
import net.minecraft.resources.IResourceManager;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.vector.Vector3f;
import org.apache.commons.lang3.StringUtils;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public final class OpenYsmModelLoader {
    private static final String[] FACE_ORDER = new String[]{"up", "down", "north", "south", "east", "west"};
    private static final float TWO_PI = (float) (Math.PI * 2.0D);
    private static final float MAX_ABS_ROTATION_DEGREES = 36000.0F;
    private static final float MAX_ABS_PIVOT = 4096.0F;
    private static final float MAX_ABS_LOCAL_COORD = 8192.0F;
    private static final float MAX_CUBE_EDGE = 1024.0F;
    private static final float MAX_ABS_INFLATE = 128.0F;
    private static final float MAX_ABS_UV = 128.0F;
    private static final int DEFAULT_TEXTURE_SIZE = 64;
    private static final int MAX_TEXTURE_SIZE = 8192;
    private static final int MAX_TEXTURE_PIXELS = 8192 * 8192;
    private static boolean imageIoPluginsScanned;

    private OpenYsmModelLoader() {
    }

    public static OpenYsmBakedPlayerModel load(IResourceManager resourceManager, OpenYsmModelEntry entry) throws IOException {
        return load(resourceManager, entry, "");
    }

    public static OpenYsmBakedPlayerModel load(IResourceManager resourceManager, OpenYsmModelEntry entry, String textureId) throws IOException {
        Path path = entry.getPath();
        if (path != null && path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".ysm")) {
            return loadFromYsmFile(entry, textureId);
        }

        try (ModelSource source = ModelSource.open(resourceManager, entry)) {
            JsonObject ysm = source.readJson("ysm.json");
            JsonObject player = ysm.getAsJsonObject("files").getAsJsonObject("player");
            JsonObject modelFiles = objectOrEmpty(player, "model");
            String modelPath = getString(modelFiles, "main", "");
            if (modelPath.isEmpty()) {
                throw new IOException("YSM player main model is missing");
            }
            String texturePath = selectTexturePath(ysm, player, textureId);
            ResourceLocation textureLocation = source.loadTexture(entry, texturePath);
            BakedPart mainPart = bakeJsonPart(source, modelPath, entry.getId());
            BakedPart armPart = null;
            String armModelPath = getString(modelFiles, "arm", "");
            if (!armModelPath.isEmpty()) {
                try {
                    armPart = bakeJsonPart(source, armModelPath, entry.getId());
                } catch (IOException | RuntimeException exception) {
                    YesSteveModel.LOGGER.warn("[YSM] Failed to bake arm model model='{}' path='{}'; falling back to main arms",
                            entry.getId(), armModelPath, exception);
                }
            }

            JsonObject properties = ysm.has("properties") && ysm.get("properties").isJsonObject() ? ysm.getAsJsonObject("properties") : new JsonObject();
            OpenYsmAnimationSet animations = buildJsonAnimationSet(ysm, player, source, entry.getId(),
                    combinedBoneMap(mainPart.bones, armPart == null ? null : armPart.bones));
            OpenYsmBakedPlayerModel bakedModel = new OpenYsmBakedPlayerModel(entry.getId(), textureLocation,
                    ImmutableList.copyOf(mainPart.roots), mainPart.bones, mainPart.geoModel,
                    armPart == null ? null : ImmutableList.copyOf(armPart.roots),
                    armPart == null ? null : armPart.bones,
                    armPart == null ? null : armPart.geoModel,
                    animations,
                    sanitizeScale(getFloat(properties, "width_scale", 1.0F), entry.getId(), "width_scale"),
                    sanitizeScale(getFloat(properties, "height_scale", 1.0F), entry.getId(), "height_scale"),
                    mainPart.footModelY,
                    getBoolean(properties, "render_layers_first", false),
                    getBoolean(properties, "all_cutout", false),
                    getBoolean(properties, "disable_preview_rotation", false),
                    getBoolean(properties, "gui_no_lighting", false));
            OpenYsmDebugLogger.logModelLoad(bakedModel);
            return bakedModel;
        }
    }

    private static BakedPart bakeJsonPart(ModelSource source, String modelPath, String modelId) throws IOException {
        JsonObject geometry = firstGeometry(source.readJson(modelPath));
        JsonObject description = geometry.getAsJsonObject("description");
        int textureWidth = sanitizeTextureSize(getInt(description, "texture_width", DEFAULT_TEXTURE_SIZE),
                modelId, "texture_width");
        int textureHeight = sanitizeTextureSize(getInt(description, "texture_height", DEFAULT_TEXTURE_SIZE),
                modelId, "texture_height");

        Map<String, BoneDef> definitions = new LinkedHashMap<>();
        JsonArray bones = geometry.getAsJsonArray("bones");
        for (JsonElement element : bones) {
            if (element.isJsonObject()) {
                BoneDef def = parseBoneDef(element.getAsJsonObject(), textureWidth, textureHeight, modelId);
                definitions.put(def.name, def);
            }
        }

        Map<String, OpenYsmBone> bakedBones = new LinkedHashMap<>();
        List<OpenYsmBone> roots = new ArrayList<>();
        List<GeoBone> geoRoots = new ArrayList<>();
        for (BoneDef def : definitions.values()) {
            bakeBone(def, definitions, bakedBones, roots, geoRoots, modelId);
        }
        return new BakedPart(roots, bakedBones, new BakedGeoModel(geoRoots), jsonFootModelY(definitions));
    }

    public static List<OpenYsmTextureOption> listTextures(IResourceManager resourceManager, OpenYsmModelEntry entry) throws IOException {
        Path path = entry.getPath();
        if (path != null && path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".ysm")) {
            return listYsmTextures(entry);
        }

        try (ModelSource source = ModelSource.open(resourceManager, entry)) {
            JsonObject ysm = source.readJson("ysm.json");
            JsonObject player = ysm.getAsJsonObject("files").getAsJsonObject("player");
            return textureOptions(player);
        }
    }

    private static OpenYsmAnimationSet buildJsonAnimationSet(JsonObject ysm, JsonObject player, ModelSource source,
                                                             String modelId, Map<String, OpenYsmBone> bakedBones) {
        OpenYsmAnimationSet animations = new OpenYsmAnimationSet(modelId);
        JsonObject properties = objectOrEmpty(ysm, "properties");
        animations.setPreviewAnimationName(getString(properties, "preview_animation", ""));
        registerJsonActionMetadata(animations, properties);

        JsonObject animationFiles = objectOrEmpty(player, "animation");
        for (Map.Entry<String, JsonElement> entry : animationFiles.entrySet()) {
            if (!entry.getValue().isJsonPrimitive()) {
                continue;
            }
            String group = entry.getKey();
            String animationPath = entry.getValue().getAsString();
            try {
                animations.addJsonAnimations(group, animationPath, source.readJson(animationPath));
            } catch (IOException | RuntimeException exception) {
                YesSteveModel.LOGGER.warn("[YSM] Failed to parse animation file model='{}' group='{}' path='{}'",
                        modelId, group, animationPath, exception);
            }
        }

        JsonElement controllers = player.get("animation_controllers");
        for (String controllerPath : stringList(controllers)) {
            try {
                animations.addJsonControllerReferences(controllerPath, source.readJson(controllerPath));
            } catch (IOException | RuntimeException exception) {
                YesSteveModel.LOGGER.warn("[YSM] Failed to parse animation controller model='{}' path='{}'",
                        modelId, controllerPath, exception);
            }
        }

        animations.configureDefaultHidden(bakedBones);
        return animations;
    }

    private static OpenYsmAnimationSet buildRawAnimationSet(RawYsmModel rawModel, String modelId,
                                                            Map<String, OpenYsmBone> bakedBones) {
        OpenYsmAnimationSet animations = new OpenYsmAnimationSet(modelId);
        animations.setPreviewAnimationName(rawModel.properties.previewAnimation);
        registerRawActionMetadata(animations, rawModel);
        for (Map.Entry<String, RawYsmModel.RawAnimationFile> entry : rawModel.mainEntity.animationFiles.entrySet()) {
            RawYsmModel.RawAnimationFile file = entry.getValue();
            animations.addRawAnimations(entry.getKey(), file == null ? 0 : file.animType, file);
        }
        animations.addRawControllerReferences(rawModel.mainEntity.animationControllers);
        animations.configureDefaultHidden(bakedBones);
        return animations;
    }

    private static void registerJsonActionMetadata(OpenYsmAnimationSet animations, JsonObject properties) {
        JsonObject extraAnimation = objectOrEmpty(properties, "extra_animation");
        for (Map.Entry<String, JsonElement> entry : extraAnimation.entrySet()) {
            registerJsonAction(animations, entry.getKey(), entry.getValue(), false);
            animations.registerRootWheelEntry(entry.getKey(), safeString(entry.getValue()));
        }
        registerJsonActionButtons(animations, properties.get("extra_animation_buttons"));
        registerJsonActionContainer(animations, properties.get("roulette"));
        registerJsonActionContainer(animations, properties.get("wheel"));
        registerJsonActionContainer(animations, properties.get("extra_animation_classify"));
    }

    private static void registerJsonActionContainer(OpenYsmAnimationSet animations, JsonElement element) {
        if (element == null || element.isJsonNull()) {
            return;
        }
        if (element.isJsonArray()) {
            for (JsonElement child : element.getAsJsonArray()) {
                registerJsonActionContainer(animations, child);
            }
            return;
        }
        if (!element.isJsonObject()) {
            return;
        }
        JsonObject object = element.getAsJsonObject();
        if (object.has("extras") && object.get("extras").isJsonObject()) {
            String id = firstString(object, "id", "name", "title");
            Map<String, String> extras = new LinkedHashMap<>();
            for (Map.Entry<String, JsonElement> entry : object.getAsJsonObject("extras").entrySet()) {
                extras.put(entry.getKey(), safeString(entry.getValue()));
                registerJsonAction(animations, entry.getKey(), entry.getValue(), false);
            }
            if (!id.isEmpty()) {
                animations.registerWheelGroup(id, extras);
            }
            return;
        }
        String animationName = firstString(object, "animation", "animationName", "id");
        if (!animationName.isEmpty()) {
            animations.registerExplicitAction(animationName, firstString(object, "displayName", "display_name", "name", "title"),
                    firstString(object, "icon"), firstString(object, "description"), getBoolean(object, "global", false));
        }
    }

    private static void registerJsonAction(OpenYsmAnimationSet animations, String fallbackName, JsonElement value, boolean global) {
        String animationName = fallbackName;
        String displayName = fallbackName;
        String icon = "";
        String description = "";
        if (value != null && value.isJsonObject()) {
            JsonObject object = value.getAsJsonObject();
            animationName = firstNonEmpty(firstString(object, "animation", "animationName", "id"), fallbackName);
            displayName = firstNonEmpty(firstString(object, "displayName", "display_name", "name", "title"), animationName);
            icon = firstString(object, "icon");
            description = firstString(object, "description", "desc");
            global = getBoolean(object, "global", global);
        } else if (value != null && value.isJsonPrimitive()) {
            String text = value.getAsString();
            if (text != null && !text.startsWith("#")) {
                description = text;
            }
        }
        animations.registerExplicitAction(animationName, displayName, icon, description, global);
    }

    private static void registerRawActionMetadata(OpenYsmAnimationSet animations, RawYsmModel rawModel) {
        for (Map.Entry<String, String> entry : rawModel.properties.extraAnimations.entrySet()) {
            String description = entry.getValue() == null || entry.getValue().startsWith("#") ? "" : entry.getValue();
            animations.registerExplicitAction(entry.getKey(), entry.getKey(), "", description, false);
            animations.registerRootWheelEntry(entry.getKey(), entry.getValue());
        }
        for (RawYsmModel.ExtraAnimationClassify classify : rawModel.properties.extraAnimationClassifies) {
            if (classify == null || classify.extras == null) {
                continue;
            }
            for (Map.Entry<String, String> entry : classify.extras.entrySet()) {
                animations.registerExplicitAction(entry.getKey(), entry.getKey(), "", entry.getValue(), false);
            }
            animations.registerWheelGroup(classify.id, classify.extras);
        }
        for (RawYsmModel.ExtraAnimationButton button : rawModel.properties.extraAnimationButtons) {
            if (button == null) {
                continue;
            }
            animations.registerActionButton(new OpenYsmAnimationSet.ExtraActionButton(
                    button.id,
                    firstNonEmpty(button.name, button.id),
                    button.description,
                    rawForms(button.forms)));
        }
    }

    private static void registerJsonActionButtons(OpenYsmAnimationSet animations, JsonElement element) {
        if (element == null || element.isJsonNull()) {
            return;
        }
        if (element.isJsonObject()) {
            for (Map.Entry<String, JsonElement> entry : element.getAsJsonObject().entrySet()) {
                registerJsonActionButton(animations, entry.getKey(), entry.getValue());
            }
            return;
        }
        if (!element.isJsonArray()) {
            return;
        }
        for (JsonElement child : element.getAsJsonArray()) {
            registerJsonActionButton(animations, "", child);
        }
    }

    private static void registerJsonActionButton(OpenYsmAnimationSet animations, String fallbackId, JsonElement element) {
        if (element == null || !element.isJsonObject()) {
            return;
        }
        JsonObject object = element.getAsJsonObject();
        String id = firstNonEmpty(firstString(object, "id", "key"), fallbackId);
        if (id.isEmpty()) {
            id = firstString(object, "name", "title");
        }
        List<OpenYsmAnimationSet.ActionForm> forms = new ArrayList<>();
        JsonElement formsElement = object.has("config_forms") ? object.get("config_forms") : object.get("forms");
        if (formsElement != null && formsElement.isJsonArray()) {
            for (JsonElement formElement : formsElement.getAsJsonArray()) {
                if (!formElement.isJsonObject()) {
                    continue;
                }
                JsonObject form = formElement.getAsJsonObject();
                forms.add(new OpenYsmAnimationSet.ActionForm(
                        firstString(form, "type"),
                        firstString(form, "title", "name"),
                        firstString(form, "description", "desc"),
                        firstString(form, "value", "default", "default_value"),
                        getFloat(form, "step", 0.0F),
                        getFloat(form, "min", 0.0F),
                        getFloat(form, "max", 0.0F),
                        stringMap(objectOrEmpty(form, "labels"))));
            }
        }
        animations.registerActionButton(new OpenYsmAnimationSet.ExtraActionButton(
                id,
                firstNonEmpty(firstString(object, "name", "title"), id),
                firstString(object, "description", "desc"),
                forms));
    }

    private static List<OpenYsmAnimationSet.ActionForm> rawForms(List<RawYsmModel.ConfigForm> rawForms) {
        List<OpenYsmAnimationSet.ActionForm> forms = new ArrayList<>();
        if (rawForms == null) {
            return forms;
        }
        for (RawYsmModel.ConfigForm form : rawForms) {
            if (form == null) {
                continue;
            }
            forms.add(new OpenYsmAnimationSet.ActionForm(
                    form.type,
                    form.title,
                    form.description,
                    form.defaultValue,
                    form.step,
                    form.min,
                    form.max,
                    form.labels));
        }
        return forms;
    }

    private static Map<String, String> stringMap(JsonObject object) {
        Map<String, String> map = new LinkedHashMap<>();
        if (object == null) {
            return map;
        }
        for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
            map.put(entry.getKey(), safeString(entry.getValue()));
        }
        return map;
    }

    private static String safeString(JsonElement element) {
        if (element == null || element.isJsonNull()) {
            return "";
        }
        try {
            return element.getAsString();
        } catch (RuntimeException exception) {
            return "";
        }
    }

    private static OpenYsmBakedPlayerModel loadFromYsmFile(OpenYsmModelEntry entry, String textureId) throws IOException {
        byte[] fileBytes = Files.readAllBytes(entry.getPath());
        if (fileBytes.length > 50 * 1024 * 1024) throw new IOException("File too large");

        RawYsmModel rawModel;
        try {
            byte[] decompressed = rip.ysm.security.YsmCrypt.decryptYsmFile(fileBytes);
            try (com.elfmcys.yesstevemodel.resource.YSMBinaryDeserializer deserializer = new com.elfmcys.yesstevemodel.resource.YSMBinaryDeserializer(decompressed)) {
                rawModel = deserializer.deserialize();
            }
        } catch (Exception e) {
            throw new IOException("Failed to deserialize YSM binary: " + e.getMessage(), e);
        }

        RawYsmModel.RawTexture selectedTexture = null;
        if (!StringUtils.isBlank(textureId)) {
            selectedTexture = rawModel.mainEntity.textures.get(textureId);
        }
        if (selectedTexture == null) {
            String defaultTex = rawModel.properties.defaultTexture;
            if (!StringUtils.isBlank(defaultTex)) {
                selectedTexture = rawModel.mainEntity.textures.get(defaultTex);
            }
        }
        if (selectedTexture == null && !rawModel.mainEntity.textures.isEmpty()) {
            selectedTexture = rawModel.mainEntity.textures.values().iterator().next();
        }
        if (selectedTexture == null) {
            throw new IOException("No textures found in YSM model: " + entry.getId());
        }
        if (rawModel.mainEntity.mainModel == null) {
            throw new IOException("No player main model (type=1) found in YSM package: " + entry.getId());
        }

        ResourceLocation textureLocation = registerTexture(entry, selectedTexture);

        BakedPart mainPart = bakeRawPart(rawModel.mainEntity.mainModel, entry.getId());
        BakedPart armPart = rawModel.mainEntity.armModel == null ? null : bakeRawPart(rawModel.mainEntity.armModel, entry.getId());

        float widthScale = sanitizeScale(rawModel.properties.widthScale, entry.getId(), "binary_width_scale");
        float heightScale = sanitizeScale(rawModel.properties.heightScale, entry.getId(), "binary_height_scale");
        OpenYsmAnimationSet animations = buildRawAnimationSet(rawModel, entry.getId(),
                combinedBoneMap(mainPart.bones, armPart == null ? null : armPart.bones));
        List<OpenYsmExtraEntityModel> extraEntityModels = buildRawExtraEntityModels(entry, rawModel);
        OpenYsmBakedPlayerModel bakedModel = new OpenYsmBakedPlayerModel(entry.getId(), textureLocation,
                ImmutableList.copyOf(mainPart.roots), mainPart.bones, mainPart.geoModel,
                armPart == null ? null : ImmutableList.copyOf(armPart.roots),
                armPart == null ? null : armPart.bones,
                armPart == null ? null : armPart.geoModel,
                animations, widthScale, heightScale, mainPart.footModelY,
                rawModel.properties.renderLayersFirst,
                rawModel.properties.allCutout,
                rawModel.properties.disablePreviewRotation,
                rawModel.properties.guiNoLighting,
                extraEntityModels);
        bakedModel.setDebugInfo(buildRawDebugInfo(entry, rawModel, selectedTexture, animations));
        OpenYsmDebugLogger.logModelLoad(bakedModel);
        return bakedModel;
    }

    private static List<OpenYsmTextureOption> listYsmTextures(OpenYsmModelEntry entry) throws IOException {
        byte[] fileBytes = Files.readAllBytes(entry.getPath());
        if (fileBytes.length > 50 * 1024 * 1024) throw new IOException("File too large");

        RawYsmModel rawModel;
        try {
            byte[] decompressed = rip.ysm.security.YsmCrypt.decryptYsmFile(fileBytes);
            try (com.elfmcys.yesstevemodel.resource.YSMBinaryDeserializer deserializer = new com.elfmcys.yesstevemodel.resource.YSMBinaryDeserializer(decompressed)) {
                rawModel = deserializer.deserialize();
            }
        } catch (Exception e) {
            throw new IOException("Failed to deserialize YSM binary: " + e.getMessage(), e);
        }

        List<OpenYsmTextureOption> options = new ArrayList<>();
        for (String texName : rawModel.mainEntity.textures.keySet()) {
            options.add(new OpenYsmTextureOption(texName, texName));
        }
        return options;
    }

    private static List<OpenYsmExtraEntityModel> buildRawExtraEntityModels(OpenYsmModelEntry entry,
                                                                           RawYsmModel rawModel) throws IOException {
        List<OpenYsmExtraEntityModel> models = new ArrayList<>();
        for (Map.Entry<String, RawYsmModel.RawSubEntity> subEntry : rawModel.projectiles.entrySet()) {
            OpenYsmExtraEntityModel model = buildRawExtraEntityModel(entry, subEntry.getKey(), subEntry.getValue(),
                    OpenYsmExtraEntityModel.Kind.PROJECTILE, rawModel.properties.allCutout);
            if (model != null) {
                models.add(model);
            }
        }
        for (Map.Entry<String, RawYsmModel.RawSubEntity> subEntry : rawModel.vehicles.entrySet()) {
            OpenYsmExtraEntityModel model = buildRawExtraEntityModel(entry, subEntry.getKey(), subEntry.getValue(),
                    OpenYsmExtraEntityModel.Kind.VEHICLE, rawModel.properties.allCutout);
            if (model != null) {
                models.add(model);
            }
        }
        return Collections.unmodifiableList(models);
    }

    private static OpenYsmExtraEntityModel buildRawExtraEntityModel(OpenYsmModelEntry entry, String fallbackId,
                                                                    RawYsmModel.RawSubEntity subEntity,
                                                                    OpenYsmExtraEntityModel.Kind kind,
                                                                    boolean allCutout) throws IOException {
        if (subEntity == null || subEntity.model == null) {
            return null;
        }

        RawYsmModel.RawTexture texture = firstTexture(subEntity.textures);
        if (texture == null) {
            YesSteveModel.LOGGER.warn("[YSM] Extra {} model '{}' has no texture; skipping",
                    kind.name().toLowerCase(Locale.ROOT), fallbackId);
            return null;
        }

        BakedPart bakedPart = bakeRawPart(subEntity.model, entry.getId() + "/" + fallbackId);
        OpenYsmAnimationSet animations = buildRawExtraAnimationSet(entry.getId(), fallbackId, subEntity, kind,
                bakedPart.bones);
        ResourceLocation textureLocation = registerTexture(entry, "extra/" + kind.name().toLowerCase(Locale.ROOT)
                + "/" + safeExtraModelId(fallbackId) + "/" + texture.name, texture);
        String identifier = StringUtils.defaultIfBlank(subEntity.identifier, fallbackId);
        List<String> matchIds = subEntity.matchIds == null || subEntity.matchIds.length == 0
                ? Collections.singletonList(identifier)
                : ImmutableList.copyOf(subEntity.matchIds);
        return new OpenYsmExtraEntityModel(kind, identifier, matchIds, textureLocation,
                ImmutableList.copyOf(bakedPart.roots), bakedPart.bones, bakedPart.geoModel, animations, allCutout);
    }

    private static OpenYsmAnimationSet buildRawExtraAnimationSet(String modelId, String fallbackId,
                                                                 RawYsmModel.RawSubEntity subEntity,
                                                                 OpenYsmExtraEntityModel.Kind kind,
                                                                 Map<String, OpenYsmBone> bakedBones) {
        String groupPrefix = kind == OpenYsmExtraEntityModel.Kind.PROJECTILE ? "projectile" : "vehicle";
        OpenYsmAnimationSet animations = new OpenYsmAnimationSet(modelId + "/" + groupPrefix + "/"
                + safeExtraModelId(fallbackId));
        if (subEntity == null) {
            return animations;
        }
        for (Map.Entry<String, RawYsmModel.RawAnimationFile> entry : subEntity.animationFiles.entrySet()) {
            RawYsmModel.RawAnimationFile file = entry.getValue();
            animations.addRawAnimations(extraAnimationGroup(groupPrefix, entry.getKey()),
                    file == null ? 0 : file.animType, file);
        }
        animations.addRawControllerReferences(subEntity.animationControllers);
        animations.configureDefaultHidden(bakedBones);
        return animations;
    }

    private static String extraAnimationGroup(String groupPrefix, String sourceKey) {
        String suffix = sourceKey == null || sourceKey.trim().isEmpty() ? "animation" : sourceKey.trim();
        return groupPrefix + "/" + suffix;
    }

    private static RawYsmModel.RawTexture firstTexture(Map<String, RawYsmModel.RawTexture> textures) {
        if (textures == null || textures.isEmpty()) {
            return null;
        }
        return textures.values().iterator().next();
    }

    private static String safeExtraModelId(String id) {
        if (id == null || id.trim().isEmpty()) {
            return "model";
        }
        String normalized = id.trim().toLowerCase(Locale.ROOT);
        StringBuilder builder = new StringBuilder(normalized.length());
        for (int i = 0; i < normalized.length(); i++) {
            char c = normalized.charAt(i);
            if ((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '_' || c == '-' || c == '.') {
                builder.append(c);
            } else {
                builder.append('_');
            }
        }
        return builder.length() == 0 ? "model" : builder.toString();
    }

    private static BakedPart bakeRawPart(RawYsmModel.RawGeometry geometry, String modelId) {
        Map<String, OpenYsmBone> bakedBones = new LinkedHashMap<>();
        List<OpenYsmBone> roots = new ArrayList<>();
        List<GeoBone> geoRoots = new ArrayList<>();
        if (geometry != null) {
            for (RawYsmModel.RawBone bone : geometry.bones) {
                bakeYsmBone(bone, geometry.bones, bakedBones, roots, geoRoots, geometry, modelId);
            }
        }
        return new BakedPart(roots, bakedBones, new BakedGeoModel(geoRoots), rawFootModelY(geometry));
    }

    private static OpenYsmBone bakeYsmBone(RawYsmModel.RawBone bone, List<RawYsmModel.RawBone> allBones,
                                           Map<String, OpenYsmBone> bakedBones, List<OpenYsmBone> roots,
                                           List<GeoBone> geoRoots, RawYsmModel.RawGeometry geometry, String modelId) {
        OpenYsmBone existing = bakedBones.get(bone.name);
        if (existing != null) {
            return existing;
        }

        float rx = toModelRotationRadians(arrayValue(bone.rotation, 0, 0.0F), modelId, bone.name, "rotX");
        float ry = toModelRotationRadians(arrayValue(bone.rotation, 1, 0.0F), modelId, bone.name, "rotY");
        float rz = toModelRotationRadians(arrayValue(bone.rotation, 2, 0.0F), modelId, bone.name, "rotZ");

        OpenYsmModelRenderer renderer = new OpenYsmModelRenderer(
                sanitizeTextureSize(Math.round(geometry.textureWidth), modelId, "binary_texture_width"),
                sanitizeTextureSize(Math.round(geometry.textureHeight), modelId, "binary_texture_height"));
        renderer.setId(bone.name);

        float[] bedrockPivot = sanitizePivot(bone.pivot, modelId, bone.name);
        float[] pivot = toMinecraftPivot(bedrockPivot);
        float[] parentPivot = new float[]{0.0F, 24.0F, 0.0F};
        OpenYsmBone parent = null;
        if (!StringUtils.isBlank(bone.parentName)) {
            RawYsmModel.RawBone parentBone = findBoneByName(allBones, bone.parentName);
            if (parentBone != null) {
                parent = bakeYsmBone(parentBone, allBones, bakedBones, roots, geoRoots, geometry, modelId);
                parentPivot = toMinecraftPivot(sanitizePivot(parentBone.pivot, modelId, parentBone.name));
            }
        }

        renderer.setRotationPoint(pivot[0] - parentPivot[0], pivot[1] - parentPivot[1], pivot[2] - parentPivot[2]);
        renderer.rotateAngleX = rx;
        renderer.rotateAngleY = ry;
        renderer.rotateAngleZ = rz;

        GeoBone geoBone = new GeoBone(bone.name);
        geoBone.setInitialPose(renderer.rotationPointX, renderer.rotationPointY, renderer.rotationPointZ,
                rx, ry, rz, !renderer.showModel);

        int cubeIndex = 0;
        List<RawYsmModel.RawCube> cubes = bone.cubes == null ? java.util.Collections.emptyList() : bone.cubes;
        for (RawYsmModel.RawCube rawCube : cubes) {
            if (rawCube == null || rawCube.faces == null) {
                YesSteveModel.LOGGER.warn("[YSM][binary-face] model='{}' bone='{}' cube={} has no face list - skipping cube",
                        modelId, bone.name, cubeIndex);
                cubeIndex++;
                continue;
            }
            for (RawYsmModel.RawFace face : rawCube.faces) {
                if (!hasCompleteFaceArrays(face)) {
                    YesSteveModel.LOGGER.warn("[YSM][binary-face] model='{}' bone='{}' cube={} incomplete face arrays - skipping face",
                            modelId, bone.name, cubeIndex);
                    continue;
                }
                // Binary face normals: Bedrock uses left-hand Y-up; negate Y to convert to MC right-hand Y-up.
                float nx = face.normal[0];
                float ny = -face.normal[1];
                float nz = face.normal[2];
                if (!isFinite(nx, ny, nz) || (nx == 0.0F && ny == 0.0F && nz == 0.0F)) {
                    YesSteveModel.LOGGER.warn(
                            "[YSM][binary-face] model='{}' bone='{}' cube={} invalid normal ({}, {}, {}) - skipping face",
                            modelId, bone.name, cubeIndex, nx, ny, nz);
                    continue;
                }
                if (Float.isNaN(nx) || Float.isNaN(ny) || Float.isNaN(nz)) {
                    YesSteveModel.LOGGER.warn("[YSM] bone='{}' cube={} has NaN normal – skipping face", bone.name, cubeIndex);
                    continue;
                }
                Vector3f normal = new Vector3f(nx, ny, nz);

                // Binary face.positions are in BLOCK units (Bedrock world space).
                // bone.pivot is in PIXEL units (Bedrock model space, i.e. the same as JSON geometry).
                // We need local pixel coordinates relative to the bone pivot:
                //   localX_px =  pos_blocks * 16 - pivot_px
                //   localY_px =  pivot_px_Y - pos_blocks_Y * 16   (flip Y axis Bedrock→MC)
                //   localZ_px =  pos_blocks * 16 - pivot_px
                // These are then divided by 16 in renderCustomQuads() to get block units for the vertex buffer.
                boolean skip = false;
                Vector3f[] positions = new Vector3f[4];
                for (int i = 0; i < 4; i++) {
                    float px = face.positions[i][0];
                    float py = face.positions[i][1];
                    float pz = face.positions[i][2];
                    if (!isFinite(px, py, pz)) {
                        YesSteveModel.LOGGER.warn(
                                "[YSM][binary-face] model='{}' bone='{}' cube={} vert={} invalid position ({}, {}, {}) - skipping face",
                                modelId, bone.name, cubeIndex, i, px, py, pz);
                        skip = true;
                        break;
                    }
                    if (Float.isNaN(px) || Float.isNaN(py) || Float.isNaN(pz)) {
                        YesSteveModel.LOGGER.warn("[YSM] bone='{}' cube={} vert={} has NaN position – skipping face",
                                bone.name, cubeIndex, i);
                        skip = true;
                        break;
                    }
                    float localX = px * 16.0F - bedrockPivot[0];
                    float localY = bedrockPivot[1] - py * 16.0F;
                    float localZ = pz * 16.0F - bedrockPivot[2];

                    // Sanity-check: a typical player model fits within ±256 px.
                    if (!isFinite(localX, localY, localZ)
                            || Math.abs(localX) > MAX_ABS_LOCAL_COORD
                            || Math.abs(localY) > MAX_ABS_LOCAL_COORD
                            || Math.abs(localZ) > MAX_ABS_LOCAL_COORD) {
                        YesSteveModel.LOGGER.warn(
                                "[YSM][binary-face] model='{}' bone='{}' cube={} vert={} local=({}, {}, {}) pivot=({}, {}, {}) world=({}, {}, {}) texture={}x{} - skipping face",
                                modelId, bone.name, cubeIndex, i, localX, localY, localZ,
                                bedrockPivot[0], bedrockPivot[1], bedrockPivot[2], px, py, pz,
                                geometry.textureWidth, geometry.textureHeight);
                        YesSteveModel.LOGGER.warn(
                                "[YSM] bone='{}' cube={} vert={} suspiciously large local coord ({},{},{}) – skipping face",
                                bone.name, cubeIndex, i, localX, localY, localZ);
                        skip = true;
                        break;
                    }
                    positions[i] = new Vector3f(localX, localY, localZ);
                }
                if (skip) continue;

                float[] u = new float[]{face.u[0], face.u[1], face.u[2], face.u[3]};
                float[] v = new float[]{face.v[0], face.v[1], face.v[2], face.v[3]};
                if (!isUvSane(u) || !isUvSane(v)) {
                    YesSteveModel.LOGGER.warn(
                            "[YSM][binary-face] model='{}' bone='{}' cube={} invalid uv u=[{}, {}, {}, {}] v=[{}, {}, {}, {}] texture={}x{} - skipping face",
                            modelId, bone.name, cubeIndex, u[0], u[1], u[2], u[3], v[0], v[1], v[2], v[3],
                            geometry.textureWidth, geometry.textureHeight);
                    continue;
                }
                renderer.addCustomQuad(new OpenYsmModelRenderer.CustomQuad(normal, positions, u, v));
                geoBone.addCube(GeoCube.customQuad(normal, positions, u, v));
            }
            cubeIndex++;
        }

        OpenYsmBone wrapper = new OpenYsmBone(bone.name, renderer, rx, ry, rz);
        wrapper.setGeoBone(geoBone);
        wrapper.setParent(parent);
        bakedBones.put(bone.name, wrapper);

        if (parent != null) {
            parent.getRenderer().addChild(renderer);
            if (parent.getGeoBone() != null) {
                parent.getGeoBone().addChild(geoBone);
            } else {
                geoRoots.add(geoBone);
            }
        } else {
            roots.add(wrapper);
            geoRoots.add(geoBone);
        }

        return wrapper;
    }

    private static OpenYsmModelDebugInfo buildRawDebugInfo(OpenYsmModelEntry entry, RawYsmModel rawModel,
                                                           RawYsmModel.RawTexture selectedTexture,
                                                           OpenYsmAnimationSet animations) {
        Map<String, List<OpenYsmModelDebugInfo.ClipTouch>> touchedBy = new LinkedHashMap<>();
        for (OpenYsmAnimationSet.Clip clip : animations.getClips().values()) {
            OpenYsmModelDebugInfo.ClipTouch touch = new OpenYsmModelDebugInfo.ClipTouch(
                    clip.name, clip.group, clip.sourceType, clip.originFile,
                    clip.isMainState, clip.isHandCondition, clip.isExtraAction, clip.isControllerReferenced);
            for (String boneName : clip.touchedBones) {
                touchedBy.computeIfAbsent(boneName, ignored -> new ArrayList<>()).add(touch);
            }
        }

        Map<String, OpenYsmModelDebugInfo.BoneInfo> bones = new LinkedHashMap<>();
        List<RawYsmModel.RawBone> rawBones = rawModel.mainEntity.mainModel == null
                ? Collections.emptyList()
                : rawModel.mainEntity.mainModel.bones;
        for (RawYsmModel.RawBone rawBone : rawBones) {
            List<OpenYsmModelDebugInfo.CubeInfo> cubes = new ArrayList<>();
            for (int i = 0; i < rawBone.cubes.size(); i++) {
                RawYsmModel.RawCube cube = rawBone.cubes.get(i);
                cubes.add(new OpenYsmModelDebugInfo.CubeInfo(i, cube == null || cube.faces == null ? 0 : cube.faces.size()));
            }
            List<OpenYsmModelDebugInfo.ClipTouch> touches = touchedBy.getOrDefault(rawBone.name, Collections.emptyList());
            bones.put(rawBone.name, new OpenYsmModelDebugInfo.BoneInfo(
                    rawBone.name,
                    rawBone.parentName,
                    buildParentChain(rawBone, rawBones),
                    isFeatureCandidate(rawBone.name, touches),
                    cubes,
                    touches));
        }

        List<String> controllerNames = new ArrayList<>(rawModel.mainEntity.animationControllers.keySet());
        List<String> functionNames = new ArrayList<>(rawModel.functionFiles.keySet());
        List<String> extraButtonIds = new ArrayList<>();
        for (RawYsmModel.ExtraAnimationButton button : rawModel.properties.extraAnimationButtons) {
            if (button != null && button.id != null && !button.id.isEmpty()) {
                extraButtonIds.add(button.id);
            }
        }
        List<String> extraClassifyIds = new ArrayList<>();
        for (RawYsmModel.ExtraAnimationClassify classify : rawModel.properties.extraAnimationClassifies) {
            if (classify != null && classify.id != null && !classify.id.isEmpty()) {
                extraClassifyIds.add(classify.id);
            }
        }

        String sourcePath = entry.getPath() == null ? "" : entry.getPath().toAbsolutePath().normalize().toString();
        String sourceFileName = entry.getPath() == null || entry.getPath().getFileName() == null
                ? ""
                : entry.getPath().getFileName().toString();
        String rawMetadataSummary = "name=" + rawModel.metadata.name
                + ", tips=" + rawModel.metadata.tips
                + ", previewAnimation=" + rawModel.properties.previewAnimation
                + ", backgroundImages=" + rawModel.properties.backgroundImages.size()
                + ", extraAnimations=" + rawModel.properties.extraAnimations.keySet();

        return new OpenYsmModelDebugInfo(
                entry.getId(),
                sourceFileName,
                sourcePath,
                String.valueOf(entry.getSourceType()),
                selectedTexture == null ? "" : selectedTexture.name,
                rawModel.metadata.name,
                rawModel.mainEntity.mainModel == null ? "" : rawModel.mainEntity.mainModel.identifier,
                rawModel.mainEntity.armModel == null ? "" : rawModel.mainEntity.armModel.identifier,
                rawModel.properties.previewAnimation,
                rawMetadataSummary,
                controllerNames,
                functionNames,
                extraButtonIds,
                extraClassifyIds,
                bones);
    }

    private static String buildParentChain(RawYsmModel.RawBone rawBone, List<RawYsmModel.RawBone> rawBones) {
        List<String> chain = new ArrayList<>();
        RawYsmModel.RawBone current = rawBone;
        int guard = 0;
        while (current != null && guard++ < rawBones.size() + 1) {
            chain.add(0, current.name);
            if (StringUtils.isBlank(current.parentName)) {
                break;
            }
            current = findBoneByName(rawBones, current.parentName);
        }
        return String.join(" -> ", chain);
    }

    private static boolean isFeatureCandidate(String boneName, List<OpenYsmModelDebugInfo.ClipTouch> touches) {
        String lowerName = boneName == null ? "" : boneName.toLowerCase(Locale.ROOT);
        if (lowerName.contains("background") || lowerName.contains("beijing")
                || lowerName.contains("kuang") || lowerName.contains("glow_ban")) {
            return true;
        }
        for (OpenYsmModelDebugInfo.ClipTouch touch : touches) {
            if (touch.getSourceType() != com.elfmcys.yesstevemodel.client.animation.AnimationSourceType.MAIN) {
                return true;
            }
            if (!touch.isMainState() && !touch.isHandCondition()) {
                return true;
            }
        }
        return false;
    }

    /** Clamp a raw rotation degree to [-360,360] and warn on NaN/Infinity. */
    private static float safeAngle(float deg, String boneName, String channel) {
        if (Float.isNaN(deg) || Float.isInfinite(deg)) {
            YesSteveModel.LOGGER.warn("[YSM] bone='{}' channel='{}' has invalid rotation {} – clamped to 0", boneName, channel, deg);
            return 0.0F;
        }
        return deg;
    }

    private static RawYsmModel.RawBone findBoneByName(List<RawYsmModel.RawBone> bones, String name) {
        for (RawYsmModel.RawBone bone : bones) {
            if (bone.name.equals(name)) {
                return bone;
            }
        }
        return null;
    }

    private static JsonObject readJson(IResourceManager resourceManager, ResourceLocation location) throws IOException {
        try (IResource resource = resourceManager.getResource(location);
             Reader reader = new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        }
    }

    private static JsonObject readJson(InputStream inputStream) throws IOException {
        try (Reader reader = new InputStreamReader(inputStream, StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        }
    }

    private static String selectTexturePath(JsonObject ysm, JsonObject player, String textureId) throws IOException {
        List<OpenYsmTextureOption> options = textureOptions(player);
        if (options.isEmpty()) {
            throw new IOException("YSM player texture list is empty");
        }

        OpenYsmTextureOption selected = findTexture(options, textureId);
        if (selected != null) {
            return selected.getPath();
        }

        selected = findTexture(options, defaultTextureId(ysm));
        return selected != null ? selected.getPath() : options.get(0).getPath();
    }

    public static OpenYsmTextureOption findTexture(List<OpenYsmTextureOption> options, String textureId) {
        if (StringUtils.isBlank(textureId)) {
            return null;
        }

        for (OpenYsmTextureOption option : options) {
            if (textureId.equalsIgnoreCase(option.getId()) || textureId.equalsIgnoreCase(option.getPath())) {
                return option;
            }
        }
        return null;
    }

    private static List<OpenYsmTextureOption> textureOptions(JsonObject player) throws IOException {
        JsonElement texture = player.get("texture");
        if (texture == null) {
            throw new IOException("YSM player texture is missing");
        }

        List<OpenYsmTextureOption> options = new ArrayList<>();
        if (texture.isJsonArray()) {
            for (JsonElement element : texture.getAsJsonArray()) {
                addTextureOption(options, element);
            }
        } else {
            addTextureOption(options, texture);
        }

        return options;
    }

    private static void addTextureOption(List<OpenYsmTextureOption> options, JsonElement texture) {
        String path = null;
        if (texture.isJsonPrimitive()) {
            path = texture.getAsString();
        } else if (texture.isJsonObject() && texture.getAsJsonObject().has("uv")) {
            path = texture.getAsJsonObject().get("uv").getAsString();
        }

        if (!StringUtils.isBlank(path)) {
            options.add(new OpenYsmTextureOption(textureIdFromPath(path), path));
        }
    }

    private static String defaultTextureId(JsonObject ysm) {
        if (ysm.has("properties") && ysm.get("properties").isJsonObject()) {
            JsonObject properties = ysm.getAsJsonObject("properties");
            if (properties.has("default_texture") && properties.get("default_texture").isJsonPrimitive()) {
                return properties.get("default_texture").getAsString();
            }
        }
        return "";
    }

    private static String textureIdFromPath(String path) {
        String normalized = path.replace('\\', '/');
        int slash = normalized.lastIndexOf('/');
        if (slash >= 0) {
            normalized = normalized.substring(slash + 1);
        }

        int dot = normalized.lastIndexOf('.');
        if (dot > 0) {
            normalized = normalized.substring(0, dot);
        }

        return normalized;
    }

    private static JsonObject firstGeometry(JsonObject modelJson) throws IOException {
        JsonArray geometries = modelJson.getAsJsonArray("minecraft:geometry");
        if (geometries == null || geometries.size() == 0 || !geometries.get(0).isJsonObject()) {
            throw new IOException("Missing minecraft:geometry");
        }
        return geometries.get(0).getAsJsonObject();
    }

    private static BoneDef parseBoneDef(JsonObject json, int textureWidth, int textureHeight, String modelId) {
        String name = json.get("name").getAsString();
        String parent = json.has("parent") ? json.get("parent").getAsString() : null;
        float[] pivot = sanitizePivot(getFloatArray(json.getAsJsonArray("pivot"), 3), modelId, name);
        float[] rotation = sanitizeVector(getFloatArray(json.getAsJsonArray("rotation"), 3), modelId, name, "rotation", 0.0F);
        boolean mirror = getBoolean(json, "mirror", false);
        float inflate = sanitizeMagnitude(getFloat(json, "inflate", 0.0F), MAX_ABS_INFLATE, 0.0F, modelId, name, "inflate");
        BoneDef def = new BoneDef(name, parent, pivot, rotation, textureWidth, textureHeight, mirror, inflate);
        if (json.has("cubes") && json.get("cubes").isJsonArray()) {
            int cubeIndex = 0;
            for (JsonElement cube : json.getAsJsonArray("cubes")) {
                if (cube.isJsonObject()) {
                    def.cubes.add(parseCube(cube.getAsJsonObject(), inflate, mirror, modelId, name, cubeIndex));
                }
                cubeIndex++;
            }
        }
        return def;
    }

    private static CubeDef parseCube(JsonObject json, float inheritedInflate, boolean inheritedMirror,
                                     String modelId, String boneName, int cubeIndex) {
        CubeDef cube = new CubeDef();
        cube.origin = sanitizeVector(getFloatArray(json.getAsJsonArray("origin"), 3), modelId, boneName, "cube[" + cubeIndex + "].origin", 0.0F);
        cube.size = sanitizeVector(getFloatArray(json.getAsJsonArray("size"), 3), modelId, boneName, "cube[" + cubeIndex + "].size", 0.0F);
        cube.inflate = sanitizeMagnitude(getFloat(json, "inflate", inheritedInflate), MAX_ABS_INFLATE, inheritedInflate,
                modelId, boneName, "cube[" + cubeIndex + "].inflate");
        cube.mirror = getBoolean(json, "mirror", inheritedMirror);
        if (json.has("uv")) {
            JsonElement uv = json.get("uv");
            if (uv.isJsonObject()) {
                JsonObject faces = uv.getAsJsonObject();
                for (int i = 0; i < FACE_ORDER.length; i++) {
                    JsonElement face = faces.get(FACE_ORDER[i]);
                    if (face != null && face.isJsonObject()) {
                        cube.uv[i] = parseFaceUv(face.getAsJsonObject());
                    }
                }
            } else if (uv.isJsonArray()) {
                JsonArray uvArray = uv.getAsJsonArray();
                if (uvArray.size() < 2) {
                    YesSteveModel.LOGGER.warn(
                            "[YSM][geometry] model='{}' bone='{}' cube={} legacy uv has fewer than two values - using 0,0",
                            modelId, boneName, cubeIndex);
                }
                int u = Math.round(uvArray.size() > 0 ? sanitizeFinite(uvArray.get(0).getAsFloat(), 0.0F,
                        modelId, boneName, "cube[" + cubeIndex + "].uv.u") : 0.0F);
                int v = Math.round(uvArray.size() > 1 ? sanitizeFinite(uvArray.get(1).getAsFloat(), 0.0F,
                        modelId, boneName, "cube[" + cubeIndex + "].uv.v") : 0.0F);
                cube.legacyUv = new int[]{u, v};
            }
        }
        return cube;
    }

    private static int[] parseFaceUv(JsonObject json) {
        float[] uv = getFloatArray(json.getAsJsonArray("uv"), 2);
        float[] size = getFloatArray(json.getAsJsonArray("uv_size"), 2);
        float u1 = uv[0];
        float v1 = uv[1];
        float u2 = uv[0] + size[0];
        float v2 = uv[1] + size[1];
        return new int[]{Math.round(Math.min(u1, u2)), Math.round(Math.min(v1, v2)),
                Math.round(Math.max(u1, u2)), Math.round(Math.max(v1, v2))};
    }

    private static OpenYsmBone bakeBone(BoneDef def, Map<String, BoneDef> definitions,
                                        Map<String, OpenYsmBone> bakedBones, List<OpenYsmBone> roots,
                                        List<GeoBone> geoRoots, String modelId) {
        OpenYsmBone existing = bakedBones.get(def.name);
        if (existing != null) {
            return existing;
        }

        ModelRenderer renderer = new OpenYsmModelRenderer(def.textureWidth, def.textureHeight);
        renderer.setId(def.name);
        float[] pivot = toMinecraftPivot(def.pivot);
        float[] parentPivot = new float[]{0.0F, 24.0F, 0.0F};
        OpenYsmBone parent = null;
        if (!StringUtils.isBlank(def.parentName)) {
            BoneDef parentDef = definitions.get(def.parentName);
            if (parentDef != null) {
                parent = bakeBone(parentDef, definitions, bakedBones, roots, geoRoots, modelId);
                parentPivot = toMinecraftPivot(parentDef.pivot);
            }
        }

        renderer.setRotationPoint(pivot[0] - parentPivot[0], pivot[1] - parentPivot[1], pivot[2] - parentPivot[2]);
        float rx = toBedrockRotationRadians(def.rotation[0], -1.0F, modelId, def.name, "rotX");
        float ry = toBedrockRotationRadians(def.rotation[1], -1.0F, modelId, def.name, "rotY");
        float rz = toBedrockRotationRadians(def.rotation[2], 1.0F, modelId, def.name, "rotZ");
        renderer.rotateAngleX = rx;
        renderer.rotateAngleY = ry;
        renderer.rotateAngleZ = rz;

        GeoBone geoBone = new GeoBone(def.name);
        geoBone.setInitialPose(renderer.rotationPointX, renderer.rotationPointY, renderer.rotationPointZ,
                rx, ry, rz, !renderer.showModel);

        for (int i = 0; i < def.cubes.size(); i++) {
            CubeDef cube = def.cubes.get(i);
            if (!isCubeSane(cube, pivot, modelId, def.name, i, def.textureWidth, def.textureHeight)) {
                continue;
            }
            addCube(renderer, cube, pivot);
            addGeoCube(geoBone, cube, pivot, def.textureWidth, def.textureHeight);
        }

        OpenYsmBone bone = new OpenYsmBone(def.name, renderer, rx, ry, rz);
        bone.setGeoBone(geoBone);
        bone.setParent(parent);
        bakedBones.put(def.name, bone);
        if (parent != null) {
            parent.getRenderer().addChild(renderer);
            if (parent.getGeoBone() != null) {
                parent.getGeoBone().addChild(geoBone);
            } else {
                geoRoots.add(geoBone);
            }
        } else {
            roots.add(bone);
            geoRoots.add(geoBone);
        }
        return bone;
    }

    private static void addCube(ModelRenderer renderer, CubeDef cube, float[] pivot) {
        float x = cube.origin[0] - pivot[0];
        float y = 24.0F - (cube.origin[1] + cube.size[1]) - pivot[1];
        float z = cube.origin[2] - pivot[2];
        boolean previousMirror = renderer.mirror;
        renderer.mirror = cube.mirror;
        try {
            if (cube.legacyUv != null) {
                renderer.setTextureOffset(cube.legacyUv[0], cube.legacyUv[1]);
                renderer.addBox(x, y, z, cube.size[0], cube.size[1], cube.size[2], cube.inflate, cube.mirror);
            } else {
                renderer.addBox(cube.uv, x, y, z, cube.size[0], cube.size[1], cube.size[2], cube.inflate);
            }
        } finally {
            renderer.mirror = previousMirror;
        }
    }

    private static void addGeoCube(GeoBone geoBone, CubeDef cube, float[] pivot,
                                   int textureWidth, int textureHeight) {
        float x = cube.origin[0] - pivot[0];
        float y = 24.0F - (cube.origin[1] + cube.size[1]) - pivot[1];
        float z = cube.origin[2] - pivot[2];
        if (cube.legacyUv != null) {
            geoBone.addCube(GeoCube.boxFromLegacyUv(cube.legacyUv[0], cube.legacyUv[1], x, y, z,
                    cube.size[0], cube.size[1], cube.size[2], cube.inflate, cube.mirror, textureWidth, textureHeight));
        } else {
            geoBone.addCube(GeoCube.boxFromFaceUv(cube.uv, x, y, z,
                    cube.size[0], cube.size[1], cube.size[2], cube.inflate, cube.mirror, textureWidth, textureHeight));
        }
    }

    private static float[] toMinecraftPivot(float[] bedrockPivot) {
        return new float[]{bedrockPivot[0], 24.0F - bedrockPivot[1], bedrockPivot[2]};
    }

    private static float jsonFootModelY(Map<String, BoneDef> definitions) {
        float footModelY = 24.0F;
        if (definitions == null) {
            return footModelY;
        }

        for (BoneDef def : definitions.values()) {
            for (CubeDef cube : def.cubes) {
                footModelY = Math.max(footModelY, 24.0F - cube.origin[1] + cube.inflate);
            }
        }
        return footModelY;
    }

    private static float rawFootModelY(RawYsmModel.RawGeometry geometry) {
        if (geometry == null || geometry.bones == null) {
            return 24.0F;
        }

        float minWorldY = Float.POSITIVE_INFINITY;
        for (RawYsmModel.RawBone bone : geometry.bones) {
            if (bone == null || bone.cubes == null) {
                continue;
            }
            for (RawYsmModel.RawCube cube : bone.cubes) {
                if (cube == null || cube.faces == null) {
                    continue;
                }
                for (RawYsmModel.RawFace face : cube.faces) {
                    if (face == null || face.positions == null) {
                        continue;
                    }
                    for (int i = 0; i < face.positions.length; i++) {
                        float[] position = face.positions[i];
                        if (position != null && position.length > 1 && isFinite(position[1])) {
                            minWorldY = Math.min(minWorldY, position[1]);
                        }
                    }
                }
            }
        }
        return minWorldY == Float.POSITIVE_INFINITY ? 24.0F : -minWorldY * 16.0F;
    }

    private static float degreesToRadians(float degrees) {
        return degrees * ((float) Math.PI / 180.0F);
    }

    private static int sanitizeTextureSize(int value, String modelId, String field) {
        if (value <= 0 || value > MAX_TEXTURE_SIZE) {
            YesSteveModel.LOGGER.warn("[YSM][geometry] model='{}' field='{}' invalid texture size {} - using {}",
                    modelId, field, value, DEFAULT_TEXTURE_SIZE);
            return DEFAULT_TEXTURE_SIZE;
        }
        return value;
    }

    private static float sanitizeScale(float value, String modelId, String field) {
        if (!isFinite(value) || value <= 0.0F || value > 16.0F) {
            YesSteveModel.LOGGER.warn("[YSM][geometry] model='{}' field='{}' invalid scale {} - using 1.0",
                    modelId, field, value);
            return 1.0F;
        }
        return value;
    }

    private static float[] sanitizeVector(float[] values, String modelId, String boneName, String field, float fallback) {
        float[] sanitized = new float[3];
        for (int i = 0; i < sanitized.length; i++) {
            sanitized[i] = sanitizeFinite(arrayValue(values, i, fallback), fallback, modelId, boneName, field + "[" + i + "]");
        }
        return sanitized;
    }

    private static float[] sanitizePivot(float[] values, String modelId, String boneName) {
        float[] pivot = sanitizeVector(values, modelId, boneName, "pivot", 0.0F);
        for (int i = 0; i < pivot.length; i++) {
            if (Math.abs(pivot[i]) > MAX_ABS_PIVOT) {
                YesSteveModel.LOGGER.warn("[YSM][geometry] model='{}' bone='{}' pivot[{}]={} exceeds abs limit {} - using 0",
                        modelId, boneName, i, pivot[i], MAX_ABS_PIVOT);
                pivot[i] = 0.0F;
            }
        }
        return pivot;
    }

    private static float sanitizeFinite(float value, float fallback, String modelId, String boneName, String field) {
        if (!isFinite(value)) {
            YesSteveModel.LOGGER.warn("[YSM][geometry] model='{}' bone='{}' field='{}' invalid value {} - using {}",
                    modelId, boneName, field, value, fallback);
            return fallback;
        }
        return value;
    }

    private static float sanitizeMagnitude(float value, float maxAbs, float fallback,
                                           String modelId, String boneName, String field) {
        float finite = sanitizeFinite(value, fallback, modelId, boneName, field);
        if (Math.abs(finite) > maxAbs) {
            YesSteveModel.LOGGER.warn("[YSM][geometry] model='{}' bone='{}' field='{}' value {} exceeds abs limit {} - using {}",
                    modelId, boneName, field, finite, maxAbs, fallback);
            return fallback;
        }
        return finite;
    }

    private static float toModelRotationRadians(float value, String modelId, String boneName, String channel) {
        float finite = sanitizeFinite(value, 0.0F, modelId, boneName, channel);
        if (Math.abs(finite) <= TWO_PI + 0.001F) {
            return finite;
        }

        if (Math.abs(finite) > MAX_ABS_ROTATION_DEGREES) {
            YesSteveModel.LOGGER.warn("[YSM][geometry] model='{}' bone='{}' channel='{}' rotation {} exceeds degree limit - using 0",
                    modelId, boneName, channel, finite);
            return 0.0F;
        }
        return degreesToRadians(finite);
    }

    private static float toBedrockRotationRadians(float degrees, float sign, String modelId, String boneName, String channel) {
        float finite = sanitizeFinite(degrees, 0.0F, modelId, boneName, channel);
        if (Math.abs(finite) > MAX_ABS_ROTATION_DEGREES) {
            YesSteveModel.LOGGER.warn("[YSM][geometry] model='{}' bone='{}' channel='{}' rotation {} exceeds degree limit - using 0",
                    modelId, boneName, channel, finite);
            return 0.0F;
        }
        return degreesToRadians(finite * sign);
    }

    private static boolean isCubeSane(CubeDef cube, float[] pivot, String modelId, String boneName,
                                      int cubeIndex, int textureWidth, int textureHeight) {
        if (!isFinite(cube.origin[0], cube.origin[1], cube.origin[2])
                || !isFinite(cube.size[0], cube.size[1], cube.size[2])
                || !isFinite(cube.inflate)) {
            logInvalidCube(modelId, boneName, cubeIndex, cube, pivot, textureWidth, textureHeight,
                    "non-finite origin/size/inflate");
            return false;
        }

        if (cube.size[0] < 0.0F || cube.size[1] < 0.0F || cube.size[2] < 0.0F
                || cube.size[0] > MAX_CUBE_EDGE || cube.size[1] > MAX_CUBE_EDGE || cube.size[2] > MAX_CUBE_EDGE
                || Math.abs(cube.inflate) > MAX_ABS_INFLATE) {
            logInvalidCube(modelId, boneName, cubeIndex, cube, pivot, textureWidth, textureHeight,
                    "invalid size/inflate range");
            return false;
        }

        float minX = cube.origin[0] - cube.inflate - pivot[0];
        float minY = 24.0F - (cube.origin[1] + cube.size[1]) - pivot[1] - cube.inflate;
        float minZ = cube.origin[2] - cube.inflate - pivot[2];
        float maxX = minX + cube.size[0] + cube.inflate * 2.0F;
        float maxY = minY + cube.size[1] + cube.inflate * 2.0F;
        float maxZ = minZ + cube.size[2] + cube.inflate * 2.0F;
        if (absMax(minX, minY, minZ, maxX, maxY, maxZ) > MAX_ABS_LOCAL_COORD) {
            logInvalidCube(modelId, boneName, cubeIndex, cube, pivot, textureWidth, textureHeight,
                    "local bounds exceed limit");
            return false;
        }

        if (cube.legacyUv != null) {
            if (!isUvPixelSane(cube.legacyUv[0], textureWidth) || !isUvPixelSane(cube.legacyUv[1], textureHeight)) {
                logInvalidCube(modelId, boneName, cubeIndex, cube, pivot, textureWidth, textureHeight,
                        "legacy uv outside texture bounds");
                return false;
            }
        } else {
            boolean hasAnyFace = false;
            for (int[] uv : cube.uv) {
                if (uv != null) {
                    hasAnyFace = true;
                    if (uv.length < 4
                            || !isUvPixelSane(uv[0], textureWidth)
                            || !isUvPixelSane(uv[2], textureWidth)
                            || !isUvPixelSane(uv[1], textureHeight)
                            || !isUvPixelSane(uv[3], textureHeight)) {
                        logInvalidCube(modelId, boneName, cubeIndex, cube, pivot, textureWidth, textureHeight,
                                "per-face uv outside texture bounds");
                        return false;
                    }
                }
            }
            if (!hasAnyFace) {
                logInvalidCube(modelId, boneName, cubeIndex, cube, pivot, textureWidth, textureHeight,
                        "missing uv");
                return false;
            }
        }

        return true;
    }

    private static void logInvalidCube(String modelId, String boneName, int cubeIndex, CubeDef cube,
                                       float[] pivot, int textureWidth, int textureHeight, String reason) {
        String uv = cube.legacyUv != null
                ? "[" + cube.legacyUv[0] + "," + cube.legacyUv[1] + "]"
                : java.util.Arrays.deepToString(cube.uv);
        YesSteveModel.LOGGER.warn(
                "[YSM][geometry] model='{}' bone='{}' cube={} reason='{}' origin=[{}, {}, {}] size=[{}, {}, {}] pivot=[{}, {}, {}] inflate={} mirror={} uv={} texture={}x{} - skipping cube",
                modelId, boneName, cubeIndex, reason,
                cube.origin[0], cube.origin[1], cube.origin[2],
                cube.size[0], cube.size[1], cube.size[2],
                pivot[0], pivot[1], pivot[2], cube.inflate, cube.mirror, uv, textureWidth, textureHeight);
    }

    private static boolean isUvSane(float[] uv) {
        return uv != null
                && uv.length >= 4
                && isFinite(uv[0]) && isFinite(uv[1]) && isFinite(uv[2]) && isFinite(uv[3])
                && Math.abs(uv[0]) <= MAX_ABS_UV
                && Math.abs(uv[1]) <= MAX_ABS_UV
                && Math.abs(uv[2]) <= MAX_ABS_UV
                && Math.abs(uv[3]) <= MAX_ABS_UV;
    }

    private static boolean isUvPixelSane(int value, int textureSize) {
        return textureSize > 0 && value >= -textureSize && value <= textureSize * 2;
    }

    private static float arrayValue(float[] values, int index, float fallback) {
        return values != null && index >= 0 && index < values.length ? values[index] : fallback;
    }

    private static boolean hasCompleteFaceArrays(RawYsmModel.RawFace face) {
        if (face == null || face.normal == null || face.normal.length < 3
                || face.positions == null || face.positions.length < 4
                || face.u == null || face.u.length < 4
                || face.v == null || face.v.length < 4) {
            return false;
        }

        for (int i = 0; i < 4; i++) {
            if (face.positions[i] == null || face.positions[i].length < 3) {
                return false;
            }
        }
        return true;
    }

    private static float absMax(float... values) {
        float max = 0.0F;
        for (float value : values) {
            max = Math.max(max, Math.abs(value));
        }
        return max;
    }

    private static boolean isFinite(float value) {
        return !Float.isNaN(value) && !Float.isInfinite(value);
    }

    private static boolean isFinite(float x, float y, float z) {
        return isFinite(x) && isFinite(y) && isFinite(z);
    }

    private static String basePath(String ysmPath) {
        int index = ysmPath.lastIndexOf('/');
        return index < 0 ? "" : ysmPath.substring(0, index);
    }

    private static String appendPath(String basePath, String relativePath) throws IOException {
        String normalizedRelative = normalizeRelativePath(relativePath);
        return basePath == null || basePath.isEmpty() ? normalizedRelative : basePath + "/" + normalizedRelative;
    }

    private static String normalizeRelativePath(String relativePath) throws IOException {
        if (relativePath == null) {
            throw new IOException("Relative path is missing");
        }

        String normalized = relativePath.replace('\\', '/');
        while (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }

        if (normalized.isEmpty() || normalized.indexOf('\0') >= 0) {
            throw new IOException("Invalid relative path: " + relativePath);
        }

        for (String part : normalized.split("/")) {
            if (part.isEmpty() || ".".equals(part) || "..".equals(part)) {
                throw new IOException("Unsafe relative path: " + relativePath);
            }
        }

        return normalized;
    }

    private static Path resolveInside(Path root, String relativePath) throws IOException {
        Path normalizedRoot = root.toAbsolutePath().normalize();
        Path target = normalizedRoot.resolve(normalizeRelativePath(relativePath)).toAbsolutePath().normalize();
        if (!target.startsWith(normalizedRoot)) {
            throw new IOException("Path escapes model root: " + relativePath);
        }
        if (!Files.isRegularFile(target)) {
            throw new IOException("Model resource is missing: " + relativePath);
        }
        return target;
    }

    private static ResourceLocation registerTexture(OpenYsmModelEntry entry, String texturePath, InputStream inputStream) throws IOException {
        NativeImage nativeImage = readTexture(inputStream);
        ResourceLocation location = new ResourceLocation(YesSteveModel.MOD_ID,
                "dynamic/" + sha256(entry.getSourceType().name() + ":" + entry.getId() + ":" + texturePath).substring(0, 24));
        Minecraft.getInstance().getTextureManager().loadTexture(location, new DynamicTexture(nativeImage));
        return location;
    }

    private static ResourceLocation registerTexture(OpenYsmModelEntry entry, RawYsmModel.RawTexture texture) throws IOException {
        return registerTexture(entry, texture == null ? "" : texture.name, texture);
    }

    private static ResourceLocation registerTexture(OpenYsmModelEntry entry, String textureKey,
                                                    RawYsmModel.RawTexture texture) throws IOException {
        if (texture == null || texture.data == null) {
            throw new IOException("Texture data is missing");
        }

        NativeImage nativeImage = readTexture(texture);
        ResourceLocation location = new ResourceLocation(YesSteveModel.MOD_ID,
                "dynamic/" + sha256(entry.getSourceType().name() + ":" + entry.getId() + ":" + textureKey).substring(0, 24));
        Minecraft.getInstance().getTextureManager().loadTexture(location, new DynamicTexture(nativeImage));
        return location;
    }

    private static NativeImage readTexture(RawYsmModel.RawTexture texture) throws IOException {
        byte[] data = texture.data;
        if (data.length == 0) {
            throw new IOException("Texture data is empty");
        }

        if (!isWebP(data) && !isPng(data) && isRawRgbaTexture(texture.width, texture.height, data.length)) {
            return readRawRgbaTexture(texture.width, texture.height, data);
        }

        try (InputStream inputStream = new ByteArrayInputStream(data)) {
            return readTexture(inputStream);
        } catch (IOException exception) {
            if (isRawRgbaTexture(texture.width, texture.height, data.length)) {
                return readRawRgbaTexture(texture.width, texture.height, data);
            }
            throw exception;
        }
    }

    private static NativeImage readTexture(InputStream inputStream) throws IOException {
        byte[] data = inputStream.readAllBytes();
        if (isWebP(data)) {
            ensureImageIoPluginsScanned();
            BufferedImage bufferedImage = ImageIO.read(new ByteArrayInputStream(data));
            if (bufferedImage == null) {
                throw new IOException("Could not decode WebP texture");
            }

            return toNativeImage(bufferedImage);
        }

        return NativeImage.read(new ByteArrayInputStream(data));
    }

    private static boolean isWebP(byte[] data) {
        return data.length >= 12
                && data[0] == 'R' && data[1] == 'I' && data[2] == 'F' && data[3] == 'F'
                && data[8] == 'W' && data[9] == 'E' && data[10] == 'B' && data[11] == 'P';
    }

    private static boolean isPng(byte[] data) {
        return data.length >= 8
                && (data[0] & 0xFF) == 0x89
                && data[1] == 'P'
                && data[2] == 'N'
                && data[3] == 'G'
                && (data[4] & 0xFF) == 0x0D
                && (data[5] & 0xFF) == 0x0A
                && (data[6] & 0xFF) == 0x1A
                && (data[7] & 0xFF) == 0x0A;
    }

    private static boolean isRawRgbaTexture(int width, int height, int dataLength) {
        return width > 0
                && height > 0
                && (long) width * (long) height <= MAX_TEXTURE_PIXELS
                && (long) width * (long) height * 4L == (long) dataLength;
    }

    private static NativeImage readRawRgbaTexture(int width, int height, byte[] data) throws IOException {
        if (!isRawRgbaTexture(width, height, data.length)) {
            throw new IOException("Invalid raw RGBA texture data");
        }

        NativeImage nativeImage = new NativeImage(width, height, false);
        int index = 0;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int r = data[index++] & 0xFF;
                int g = data[index++] & 0xFF;
                int b = data[index++] & 0xFF;
                int a = data[index++] & 0xFF;
                nativeImage.setPixelRGBA(x, y, NativeImage.getCombined(a, b, g, r));
            }
        }
        return nativeImage;
    }

    private static synchronized void ensureImageIoPluginsScanned() {
        if (!imageIoPluginsScanned) {
            ImageIO.scanForPlugins();
            imageIoPluginsScanned = true;
        }
    }

    private static NativeImage toNativeImage(BufferedImage bufferedImage) throws IOException {
        int width = bufferedImage.getWidth();
        int height = bufferedImage.getHeight();
        if (width <= 0 || height <= 0 || (long) width * (long) height > MAX_TEXTURE_PIXELS) {
            throw new IOException("Invalid texture dimensions: " + width + "x" + height);
        }

        NativeImage nativeImage = new NativeImage(width, height, false);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int argb = bufferedImage.getRGB(x, y);
                nativeImage.setPixelRGBA(x, y, NativeImage.getCombined(
                        argb >>> 24,
                        argb & 0xFF,
                        argb >>> 8,
                        argb >>> 16));
            }
        }
        return nativeImage;
    }

    private static String sha256(String value) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder(bytes.length * 2);
            for (byte b : bytes) {
                builder.append(String.format(Locale.ROOT, "%02x", b & 0xFF));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IOException("SHA-256 digest is unavailable", exception);
        }
    }

    private static float[] getFloatArray(JsonArray array, int size) {
        float[] values = new float[size];
        if (array == null) {
            return values;
        }
        for (int i = 0; i < size && i < array.size(); i++) {
            values[i] = array.get(i).getAsFloat();
        }
        return values;
    }

    private static JsonObject objectOrEmpty(JsonObject json, String key) {
        return json != null && json.has(key) && json.get(key).isJsonObject() ? json.getAsJsonObject(key) : new JsonObject();
    }

    private static List<String> stringList(JsonElement element) {
        List<String> values = new ArrayList<>();
        if (element == null || element.isJsonNull()) {
            return values;
        }
        if (element.isJsonArray()) {
            for (JsonElement child : element.getAsJsonArray()) {
                if (child.isJsonPrimitive()) {
                    values.add(child.getAsString());
                }
            }
        } else if (element.isJsonPrimitive()) {
            values.add(element.getAsString());
        } else if (element.isJsonObject()) {
            for (Map.Entry<String, JsonElement> entry : element.getAsJsonObject().entrySet()) {
                if (entry.getValue().isJsonPrimitive()) {
                    values.add(entry.getValue().getAsString());
                }
            }
        }
        return values;
    }

    private static String firstString(JsonObject json, String... keys) {
        if (json == null) {
            return "";
        }
        for (String key : keys) {
            if (json.has(key) && json.get(key).isJsonPrimitive()) {
                String value = json.get(key).getAsString();
                if (value != null && !value.isEmpty()) {
                    return value;
                }
            }
        }
        return "";
    }

    private static String firstNonEmpty(String value, String fallback) {
        return value == null || value.isEmpty() ? fallback : value;
    }

    private static String getString(JsonObject json, String key, String fallback) {
        return json.has(key) && json.get(key).isJsonPrimitive() ? json.get(key).getAsString() : fallback;
    }

    private static int getInt(JsonObject json, String key, int fallback) {
        return json.has(key) ? json.get(key).getAsInt() : fallback;
    }

    private static float getFloat(JsonObject json, String key, float fallback) {
        return json.has(key) ? json.get(key).getAsFloat() : fallback;
    }

    private static boolean getBoolean(JsonObject json, String key, boolean fallback) {
        return json.has(key) ? json.get(key).getAsBoolean() : fallback;
    }

    private static Map<String, OpenYsmBone> combinedBoneMap(Map<String, OpenYsmBone> mainBones,
                                                            Map<String, OpenYsmBone> armBones) {
        if (armBones == null || armBones.isEmpty()) {
            return mainBones;
        }

        Map<String, OpenYsmBone> combined = new LinkedHashMap<>();
        if (mainBones != null) {
            combined.putAll(mainBones);
        }
        for (Map.Entry<String, OpenYsmBone> entry : armBones.entrySet()) {
            combined.put("arm:" + entry.getKey(), entry.getValue());
        }
        return combined;
    }

    private static final class BakedPart {
        final List<OpenYsmBone> roots;
        final Map<String, OpenYsmBone> bones;
        final BakedGeoModel geoModel;
        final float footModelY;

        BakedPart(List<OpenYsmBone> roots, Map<String, OpenYsmBone> bones, BakedGeoModel geoModel, float footModelY) {
            this.roots = roots;
            this.bones = bones;
            this.geoModel = geoModel;
            this.footModelY = footModelY;
        }
    }

    private static final class BoneDef {
        final String name;
        final String parentName;
        final float[] pivot;
        final float[] rotation;
        final int textureWidth;
        final int textureHeight;
        final boolean mirror;
        final float inflate;
        final List<CubeDef> cubes = new ArrayList<>();

        BoneDef(String name, String parentName, float[] pivot, float[] rotation,
                int textureWidth, int textureHeight, boolean mirror, float inflate) {
            this.name = name;
            this.parentName = parentName;
            this.pivot = pivot;
            this.rotation = rotation;
            this.textureWidth = textureWidth;
            this.textureHeight = textureHeight;
            this.mirror = mirror;
            this.inflate = inflate;
        }
    }

    private static final class CubeDef {
        float[] origin = new float[3];
        float[] size = new float[3];
        float inflate;
        boolean mirror;
        int[][] uv = new int[6][];
        int[] legacyUv;
    }

    private abstract static class ModelSource implements AutoCloseable {
        static ModelSource open(IResourceManager resourceManager, OpenYsmModelEntry entry) throws IOException {
            if (entry.getYsmJsonResource() != null) {
                return new ResourceModelSource(resourceManager, basePath(entry.getYsmJsonResource().getPath()));
            }

            Path path = entry.getPath();
            if (path == null) {
                throw new IOException("Model entry has no readable source: " + entry.getId());
            }

            String fileName = path.getFileName().toString().toLowerCase(Locale.ROOT);
            if (fileName.equals("ysm.json")) {
                return new FolderModelSource(path.getParent());
            }
            if (fileName.endsWith(".zip")) {
                return new ZipModelSource(path);
            }
            if (fileName.endsWith(".ysm")) {
                throw new IOException("Unexpected .ysm source dispatch path for " + entry.getId());
            }

            throw new IOException("Unsupported OpenYSM source: " + path);
        }

        abstract JsonObject readJson(String relativePath) throws IOException;

        abstract ResourceLocation loadTexture(OpenYsmModelEntry entry, String texturePath) throws IOException;

        @Override
        public void close() throws IOException {
        }
    }

    private static final class ResourceModelSource extends ModelSource {
        private final IResourceManager resourceManager;
        private final String basePath;

        private ResourceModelSource(IResourceManager resourceManager, String basePath) {
            this.resourceManager = resourceManager;
            this.basePath = basePath;
        }

        @Override
        JsonObject readJson(String relativePath) throws IOException {
            return OpenYsmModelLoader.readJson(this.resourceManager,
                    new ResourceLocation(YesSteveModel.MOD_ID, appendPath(this.basePath, relativePath)));
        }

        @Override
        ResourceLocation loadTexture(OpenYsmModelEntry entry, String texturePath) throws IOException {
            return new ResourceLocation(YesSteveModel.MOD_ID, appendPath(this.basePath, texturePath));
        }
    }

    private static final class FolderModelSource extends ModelSource {
        private final Path root;

        private FolderModelSource(Path root) {
            this.root = root;
        }

        @Override
        JsonObject readJson(String relativePath) throws IOException {
            try (InputStream inputStream = Files.newInputStream(resolveInside(this.root, relativePath))) {
                return OpenYsmModelLoader.readJson(inputStream);
            }
        }

        @Override
        ResourceLocation loadTexture(OpenYsmModelEntry entry, String texturePath) throws IOException {
            try (InputStream inputStream = Files.newInputStream(resolveInside(this.root, texturePath))) {
                return registerTexture(entry, texturePath, inputStream);
            }
        }
    }

    private static final class ZipModelSource extends ModelSource {
        private final ZipFile zipFile;
        private final String rootPrefix;

        private ZipModelSource(Path archive) throws IOException {
            this.zipFile = new ZipFile(archive.toFile());
            ZipEntry ysmJson = OpenYsmArchiveUtil.findYsmJson(this.zipFile);
            if (ysmJson == null) {
                close();
                throw new IOException("Missing ysm.json in " + archive);
            }
            this.rootPrefix = OpenYsmArchiveUtil.rootPrefix(ysmJson);
        }

        @Override
        JsonObject readJson(String relativePath) throws IOException {
            try (InputStream inputStream = this.openEntry(relativePath)) {
                return OpenYsmModelLoader.readJson(inputStream);
            }
        }

        @Override
        ResourceLocation loadTexture(OpenYsmModelEntry entry, String texturePath) throws IOException {
            try (InputStream inputStream = this.openEntry(texturePath)) {
                return registerTexture(entry, texturePath, inputStream);
            }
        }

        @Override
        public void close() throws IOException {
            this.zipFile.close();
        }

        private InputStream openEntry(String relativePath) throws IOException {
            String entryName = this.rootPrefix + OpenYsmArchiveUtil.normalizeArchivePath(relativePath);
            ZipEntry zipEntry = OpenYsmArchiveUtil.findEntry(this.zipFile, entryName);
            if (zipEntry == null) {
                throw new IOException("Model archive resource is missing: " + relativePath);
            }
            return this.zipFile.getInputStream(zipEntry);
        }
    }
}
