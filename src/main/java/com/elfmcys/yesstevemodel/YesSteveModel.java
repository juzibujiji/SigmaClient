package com.elfmcys.yesstevemodel;

import com.elfmcys.yesstevemodel.client.OpenYsmBakedPlayerModel;
import com.elfmcys.yesstevemodel.client.OpenYsmModelLoader;
import com.elfmcys.yesstevemodel.capability.OpenYsmPlayerAnimationState;
import com.elfmcys.yesstevemodel.client.animation.controller.OpenYsmControllerRuntime;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.IResourceManager;
import net.minecraft.resources.IResourceManagerReloadListener;
import net.minecraft.util.text.StringTextComponent;
import net.minecraft.util.text.TextFormatting;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

public final class YesSteveModel {
    public static final String MOD_ID = "yes_steve_model";
    public static final Logger LOGGER = LogManager.getLogger(MOD_ID);
    public static final Gson GSON = new GsonBuilder().disableHtmlEscaping().setPrettyPrinting().create();

    private static final OpenYsmModelIndex MODEL_INDEX = new OpenYsmModelIndex();
    private static final OpenYsmResourceReloadListener RELOAD_LISTENER = new OpenYsmResourceReloadListener();
    private static OpenYsmClientConfig clientConfig = new OpenYsmClientConfig();

    private static volatile boolean initialized;
    private static Path configDirectory;
    private static OpenYsmBakedPlayerModel cachedPlayerModel;
    private static String cachedPlayerModelId;
    private static String failedPlayerModelId;
    private static String failedPlayerModelError;
    private static boolean failedPlayerModelWarningSent;

    private YesSteveModel() {
    }

    public static synchronized void bootstrap(Path gameDirectory) {
        configDirectory = gameDirectory.resolve("config").resolve(MOD_ID).toAbsolutePath().normalize();
        try {
            Files.createDirectories(configDirectory.resolve("custom"));
            Files.createDirectories(configDirectory.resolve("auth"));
            Files.createDirectories(configDirectory.resolve("cache"));
            loadClientConfig();
            initialized = true;
            LOGGER.info("[YSM] OpenYSM 1.16.4 bridge initialized at {}", configDirectory);
        } catch (IOException exception) {
            initialized = false;
            LOGGER.error("[YSM] Failed to initialize OpenYSM directories", exception);
        }
    }

    public static void reload(IResourceManager resourceManager) {
        if (!initialized || configDirectory == null) {
            LOGGER.warn("[YSM] Reload requested before OpenYSM bridge initialization");
            return;
        }

        MODEL_INDEX.reload(resourceManager, configDirectory);
        clearPlayerModelCache();
        LOGGER.info("[YSM] Indexed {} model entries", MODEL_INDEX.getEntries().size());
    }

    public static boolean isAvailable() {
        return initialized;
    }

    public static Path getConfigDirectory() {
        return configDirectory;
    }

    public static OpenYsmClientConfig getClientConfig() {
        return clientConfig;
    }

    public static OpenYsmModelIndex getModelIndex() {
        return MODEL_INDEX;
    }

    public static Optional<OpenYsmModelEntry> getSelectedModelEntry() {
        return MODEL_INDEX.findById(clientConfig.getSelectedModelId());
    }

    public static void setRenderPlayers(boolean renderPlayers) {
        clientConfig.setRenderPlayers(renderPlayers);
        clearPlayerModelCache();
        saveClientConfig();
    }

    public static boolean selectModel(String modelId) {
        return selectModel(modelId, "");
    }

    public static boolean selectModel(String modelId, String textureId) {
        Optional<OpenYsmModelEntry> entry = MODEL_INDEX.findById(modelId);
        if (!entry.isPresent()) {
            return false;
        }

        String previousModelId = clientConfig.getSelectedModelId();
        clientConfig.setSelectedModelId(entry.get().getId());
        clientConfig.setSelectedTextureId(textureId);
        if (!entry.get().getId().equals(previousModelId)) {
            OpenYsmPlayerAnimationState.clearModel(previousModelId);
            OpenYsmControllerRuntime.clearModel(previousModelId);
        }
        clearPlayerModelCache();
        saveClientConfig();
        return true;
    }

    public static OpenYsmBakedPlayerModel getSelectedPlayerModel(IResourceManager resourceManager) {
        if (!clientConfig.isRenderPlayers()) {
            return null;
        }

        Optional<OpenYsmModelEntry> entry = getSelectedModelEntry();
        if (!entry.isPresent()) {
            return null;
        }

        String id = entry.get().getId() + "|" + clientConfig.getSelectedTextureId();
        if (cachedPlayerModel != null && id.equals(cachedPlayerModelId)) {
            return cachedPlayerModel;
        }

        if (id.equals(failedPlayerModelId)) {
            warnClientOnce(id, failedPlayerModelError);
            return null;
        }

        try {
            cachedPlayerModel = OpenYsmModelLoader.load(resourceManager, entry.get(), clientConfig.getSelectedTextureId());
            cachedPlayerModelId = id;
            clearFailedPlayerModelCache();
            return cachedPlayerModel;
        } catch (IOException | RuntimeException exception) {
            cachedPlayerModel = null;
            cachedPlayerModelId = null;
            failedPlayerModelId = id;
            failedPlayerModelError = shortError(exception);
            failedPlayerModelWarningSent = false;
            LOGGER.warn("[YSM] Failed to bake selected model {}", id, exception);
            warnClientOnce(id, failedPlayerModelError);
            return null;
        }
    }

    public static IResourceManagerReloadListener getReloadListener() {
        return RELOAD_LISTENER;
    }

    private static void loadClientConfig() throws IOException {
        Path path = clientConfigPath();
        if (!Files.isRegularFile(path)) {
            clientConfig = new OpenYsmClientConfig();
            saveClientConfig();
            return;
        }

        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            OpenYsmClientConfig loaded = GSON.fromJson(reader, OpenYsmClientConfig.class);
            clientConfig = loaded != null ? loaded : new OpenYsmClientConfig();
        } catch (JsonParseException exception) {
            LOGGER.warn("[YSM] Invalid client config {}; using defaults", path, exception);
            clientConfig = new OpenYsmClientConfig();
            saveClientConfig();
        }
    }

    private static void saveClientConfig() {
        if (configDirectory == null) {
            return;
        }

        try {
            Files.createDirectories(configDirectory);
            try (Writer writer = Files.newBufferedWriter(clientConfigPath(), StandardCharsets.UTF_8)) {
                GSON.toJson(clientConfig, writer);
            }
        } catch (IOException exception) {
            LOGGER.warn("[YSM] Failed to save client config", exception);
        }
    }

    private static Path clientConfigPath() {
        return configDirectory.resolve("client.json").toAbsolutePath().normalize();
    }

    private static void clearPlayerModelCache() {
        cachedPlayerModel = null;
        cachedPlayerModelId = null;
        clearFailedPlayerModelCache();
    }

    private static void clearFailedPlayerModelCache() {
        failedPlayerModelId = null;
        failedPlayerModelError = null;
        failedPlayerModelWarningSent = false;
    }

    private static void warnClientOnce(String id, String error) {
        if (failedPlayerModelWarningSent) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.ingameGUI == null) {
            return;
        }

        minecraft.ingameGUI.getChatGUI().printChatMessage(new StringTextComponent(
                "[Sigma] OpenYSM model failed to bake; using vanilla player rendering: "
                        + chatSafe(id) + " (" + chatSafe(error) + ")").mergeStyle(TextFormatting.RED));
        failedPlayerModelWarningSent = true;
    }

    private static String shortError(Throwable exception) {
        String message = exception.getMessage();
        return message == null || message.isEmpty() ? exception.getClass().getSimpleName() : message;
    }

    private static String chatSafe(String value) {
        if (value == null) {
            return "unknown";
        }

        String sanitized = value.replace('\r', ' ').replace('\n', ' ');
        return sanitized.length() <= 180 ? sanitized : sanitized.substring(0, 177) + "...";
    }
}
