package com.elfmcys.yesstevemodel.client;

import com.elfmcys.yesstevemodel.YesSteveModel;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.resources.IResourceManager;

import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class OpenYsmPlayerModelState {
    private static final Map<UUID, Choice> CLIENT_CHOICES = new ConcurrentHashMap<>();
    private static final Map<Integer, Choice> CLIENT_EXTRA_ENTITY_CHOICES = new ConcurrentHashMap<>();

    private OpenYsmPlayerModelState() {
    }

    public static void setSynced(Entity entity, String modelId, String textureId) {
        if (!(entity instanceof PlayerEntity)) {
            return;
        }
        UUID playerId = entity.getUniqueID();
        if (modelId == null || modelId.isEmpty()) {
            CLIENT_CHOICES.remove(playerId);
            return;
        }
        CLIENT_CHOICES.put(playerId, new Choice(modelId, textureId));
    }

    public static void clearAll() {
        CLIENT_CHOICES.clear();
        CLIENT_EXTRA_ENTITY_CHOICES.clear();
    }

    public static void setSyncedExtraEntity(Entity entity, String modelId, String textureId) {
        setSyncedExtraEntity(entity, modelId, textureId, Collections.emptyMap());
    }

    public static void setSyncedExtraEntity(Entity entity, String modelId, String textureId,
                                            Map<String, Double> variables) {
        if (entity == null) {
            return;
        }
        if (modelId == null || modelId.isEmpty()) {
            CLIENT_EXTRA_ENTITY_CHOICES.remove(entity.getEntityId());
            return;
        }
        CLIENT_EXTRA_ENTITY_CHOICES.put(entity.getEntityId(), new Choice(modelId, textureId, variables));
    }

    public static Choice choiceFor(PlayerEntity player) {
        if (player == null || !YesSteveModel.isEnabled() || !YesSteveModel.getClientConfig().isRenderPlayers()) {
            return null;
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft != null && minecraft.player != null && minecraft.player.getUniqueID().equals(player.getUniqueID())) {
            String modelId = YesSteveModel.getClientConfig().getSelectedModelId();
            if (modelId == null || modelId.isEmpty()) {
                return null;
            }
            return new Choice(modelId, YesSteveModel.getClientConfig().getSelectedTextureId());
        }

        return CLIENT_CHOICES.get(player.getUniqueID());
    }

    public static OpenYsmBakedPlayerModel getBakedModelForPlayer(PlayerEntity player, IResourceManager resourceManager) {
        Choice choice = choiceFor(player);
        return choice == null ? null : YesSteveModel.getPlayerModel(resourceManager, choice.getModelId(), choice.getTextureId());
    }

    public static OpenYsmBakedPlayerModel getBakedModelForExtraEntity(Entity entity, IResourceManager resourceManager) {
        if (entity == null) {
            return null;
        }
        Choice choice = CLIENT_EXTRA_ENTITY_CHOICES.get(entity.getEntityId());
        return choice == null ? null : YesSteveModel.getPlayerModel(resourceManager, choice.getModelId(), choice.getTextureId());
    }

    public static Map<String, Double> getExtraEntityVariables(Entity entity) {
        if (entity == null) {
            return Collections.emptyMap();
        }
        Choice choice = CLIENT_EXTRA_ENTITY_CHOICES.get(entity.getEntityId());
        return choice == null ? Collections.emptyMap() : choice.getVariables();
    }

    public static final class Choice {
        private final String modelId;
        private final String textureId;
        private final Map<String, Double> variables;

        public Choice(String modelId, String textureId) {
            this(modelId, textureId, Collections.emptyMap());
        }

        public Choice(String modelId, String textureId, Map<String, Double> variables) {
            this.modelId = modelId == null ? "" : modelId;
            this.textureId = textureId == null ? "" : textureId;
            Map<String, Double> copy = new HashMap<>();
            if (variables != null) {
                for (Map.Entry<String, Double> entry : variables.entrySet()) {
                    if (entry.getKey() != null && entry.getValue() != null && Double.isFinite(entry.getValue())) {
                        copy.put(entry.getKey().toLowerCase(Locale.ROOT), entry.getValue());
                    }
                }
            }
            this.variables = Collections.unmodifiableMap(copy);
        }

        public String getModelId() {
            return this.modelId;
        }

        public String getTextureId() {
            return this.textureId;
        }

        public Map<String, Double> getVariables() {
            return this.variables;
        }
    }
}
