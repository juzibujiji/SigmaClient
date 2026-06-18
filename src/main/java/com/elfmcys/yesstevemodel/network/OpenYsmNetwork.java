package com.elfmcys.yesstevemodel.network;

import com.elfmcys.yesstevemodel.YesSteveModel;
import com.elfmcys.yesstevemodel.capability.OpenYsmPlayerAnimationState;
import com.elfmcys.yesstevemodel.client.OpenYsmPlayerModelState;
import com.elfmcys.yesstevemodel.client.animation.ActionSource;
import com.elfmcys.yesstevemodel.client.animation.OpenYsmAnimationRegistry;
import io.netty.buffer.Unpooled;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.network.PacketBuffer;
import net.minecraft.network.play.client.CCustomPayloadPacket;
import net.minecraft.network.play.server.SCustomPayloadPlayPacket;
import net.minecraft.util.ResourceLocation;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class OpenYsmNetwork {
    public static final ResourceLocation C2S_MODEL_SELECTION = new ResourceLocation(YesSteveModel.MOD_ID, "model_selection");
    public static final ResourceLocation C2S_PLAY_EXTRA = new ResourceLocation(YesSteveModel.MOD_ID, "play_extra_animation");
    public static final ResourceLocation C2S_STOP_EXTRA = new ResourceLocation(YesSteveModel.MOD_ID, "stop_extra_animation");
    public static final ResourceLocation C2S_GUI_VARIABLE = new ResourceLocation(YesSteveModel.MOD_ID, "gui_variable");
    public static final ResourceLocation S2C_MODEL_SELECTION = new ResourceLocation(YesSteveModel.MOD_ID, "sync_model_selection");
    public static final ResourceLocation S2C_EXTRA_ENTITY_MODEL = new ResourceLocation(YesSteveModel.MOD_ID, "sync_extra_entity_model");
    public static final ResourceLocation S2C_PLAY_EXTRA = new ResourceLocation(YesSteveModel.MOD_ID, "sync_play_extra_animation");
    public static final ResourceLocation S2C_STOP_EXTRA = new ResourceLocation(YesSteveModel.MOD_ID, "sync_stop_extra_animation");
    public static final ResourceLocation S2C_GUI_VARIABLE = new ResourceLocation(YesSteveModel.MOD_ID, "sync_gui_variable");
    private static final Map<UUID, ModelSelection> SERVER_MODEL_SELECTIONS = new ConcurrentHashMap<>();
    private static final Map<Integer, ExtraEntitySelection> SERVER_EXTRA_ENTITY_SELECTIONS = new ConcurrentHashMap<>();
    private static final int MAX_SERVER_EXTRA_ENTITY_SELECTIONS = 4096;

    private OpenYsmNetwork() {
    }

    public static void sendCurrentModelSelection() {
        if (!YesSteveModel.isEnabled() || !YesSteveModel.getClientConfig().isRenderPlayers()) {
            sendModelSelection("", "");
            return;
        }
        sendModelSelection(YesSteveModel.getClientConfig().getSelectedModelId(),
                YesSteveModel.getClientConfig().getSelectedTextureId());
    }

    public static void sendModelSelection(String modelId, String textureId) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.getConnection() == null
                || !isSafeOptionalIdentifier(modelId) || !isSafeOptionalIdentifier(textureId)) {
            return;
        }
        PacketBuffer buffer = new PacketBuffer(Unpooled.buffer());
        new C2SModelSelectionPacket(modelId, textureId).encode(buffer);
        minecraft.getConnection().sendPacket(new CCustomPayloadPacket(C2S_MODEL_SELECTION, buffer));
    }

    public static void sendPlayExtraAnimation(String modelId, String animationName) {
        Minecraft minecraft = Minecraft.getInstance();
        if (!YesSteveModel.isEnabled()
                || minecraft == null || minecraft.getConnection() == null
                || !isSafeIdentifier(modelId) || !isSafeIdentifier(animationName)) {
            return;
        }
        PacketBuffer buffer = new PacketBuffer(Unpooled.buffer());
        new C2SPlayExtraAnimationPacket(modelId, animationName).encode(buffer);
        minecraft.getConnection().sendPacket(new CCustomPayloadPacket(C2S_PLAY_EXTRA, buffer));
    }

    public static void sendStopExtraAnimation(String modelId) {
        Minecraft minecraft = Minecraft.getInstance();
        if (!YesSteveModel.isEnabled()
                || minecraft == null || minecraft.getConnection() == null || !isSafeIdentifier(modelId)) {
            return;
        }
        PacketBuffer buffer = new PacketBuffer(Unpooled.buffer());
        new C2SStopExtraAnimationPacket(modelId).encode(buffer);
        minecraft.getConnection().sendPacket(new CCustomPayloadPacket(C2S_STOP_EXTRA, buffer));
    }

    public static void sendGuiVariable(String modelId, String variableName, double value) {
        Minecraft minecraft = Minecraft.getInstance();
        if (!YesSteveModel.isEnabled()
                || minecraft == null || minecraft.getConnection() == null
                || !isSafeIdentifier(modelId) || !isSafeVariableName(variableName) || !Double.isFinite(value)) {
            return;
        }
        PacketBuffer buffer = new PacketBuffer(Unpooled.buffer());
        new C2SGuiVariablePacket(modelId, variableName, value).encode(buffer);
        minecraft.getConnection().sendPacket(new CCustomPayloadPacket(C2S_GUI_VARIABLE, buffer));
    }

    public static boolean handleClientPayload(SCustomPayloadPlayPacket packet) {
        ResourceLocation channel = packet.getChannelName();
        if (!S2C_MODEL_SELECTION.equals(channel) && !S2C_EXTRA_ENTITY_MODEL.equals(channel)
                && !S2C_PLAY_EXTRA.equals(channel)
                && !S2C_STOP_EXTRA.equals(channel) && !S2C_GUI_VARIABLE.equals(channel)) {
            return false;
        }

        PacketBuffer buffer = packet.getBufferData();
        try {
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft == null || minecraft.world == null) {
                return true;
            }
            if (!YesSteveModel.isEnabled()) {
                return true;
            }
            if (S2C_MODEL_SELECTION.equals(channel)) {
                S2CModelSelectionPacket selection = S2CModelSelectionPacket.decode(buffer);
                if (!isSafeOptionalIdentifier(selection.getModelId())
                        || !isSafeOptionalIdentifier(selection.getTextureId())) {
                    return true;
                }
                Entity entity = minecraft.world.getEntityByID(selection.getEntityId());
                if (entity instanceof PlayerEntity) {
                    OpenYsmPlayerModelState.setSynced(entity, selection.getModelId(), selection.getTextureId());
                }
                return true;
            }
            if (S2C_EXTRA_ENTITY_MODEL.equals(channel)) {
                S2CExtraEntityModelPacket selection = S2CExtraEntityModelPacket.decode(buffer);
                if (!isSafeOptionalIdentifier(selection.getModelId())
                        || !isSafeOptionalIdentifier(selection.getTextureId())
                        || !areSafeVariables(selection.getVariables())) {
                    return true;
                }
                Entity entity = minecraft.world.getEntityByID(selection.getEntityId());
                if (entity != null) {
                    OpenYsmPlayerModelState.setSyncedExtraEntity(entity, selection.getModelId(),
                            selection.getTextureId(), selection.getVariables());
                }
                return true;
            }
            if (S2C_PLAY_EXTRA.equals(channel)) {
                S2CPlayExtraAnimationPacket play = S2CPlayExtraAnimationPacket.decode(buffer);
                if (!isSafeIdentifier(play.getModelId()) || !isSafeIdentifier(play.getAnimationName())) {
                    return true;
                }
                Entity entity = minecraft.world.getEntityByID(play.getEntityId());
                if (entity instanceof PlayerEntity) {
                    OpenYsmPlayerAnimationState.play(entity.getUniqueID(), play.getModelId(), play.getAnimationName(), entity.ticksExisted, ActionSource.NETWORK_SYNC);
                }
                return true;
            }
            if (S2C_GUI_VARIABLE.equals(channel)) {
                S2CGuiVariablePacket variable = S2CGuiVariablePacket.decode(buffer);
                if (!isSafeIdentifier(variable.getModelId()) || !isSafeVariableName(variable.getVariableName())
                        || !Double.isFinite(variable.getValue())) {
                    return true;
                }
                Entity entity = minecraft.world.getEntityByID(variable.getEntityId());
                if (entity instanceof PlayerEntity) {
                    OpenYsmPlayerAnimationState.setGuiVariable(entity.getUniqueID(), variable.getModelId(),
                            variable.getVariableName(), variable.getValue());
                }
                return true;
            }
            S2CStopExtraAnimationPacket stop = S2CStopExtraAnimationPacket.decode(buffer);
            Entity entity = minecraft.world.getEntityByID(stop.getEntityId());
            if (entity instanceof PlayerEntity) {
                OpenYsmPlayerAnimationState.stop(entity.getUniqueID());
            }
            return true;
        } catch (RuntimeException exception) {
            YesSteveModel.LOGGER.warn("[YSM] Ignoring malformed OpenYSM animation payload", exception);
            return true;
        } finally {
            buffer.release();
        }
    }

    public static boolean handleServerPayload(CCustomPayloadPacket packet, ServerPlayerEntity player) {
        ResourceLocation channel = packet.getChannelName();
        if (!C2S_MODEL_SELECTION.equals(channel) && !C2S_PLAY_EXTRA.equals(channel)
                && !C2S_STOP_EXTRA.equals(channel) && !C2S_GUI_VARIABLE.equals(channel)) {
            return false;
        }

        PacketBuffer buffer = packet.getBufferData();
        try {
            if (C2S_MODEL_SELECTION.equals(channel)) {
                C2SModelSelectionPacket selection = C2SModelSelectionPacket.decode(buffer);
                if (!isSafeOptionalIdentifier(selection.getModelId())
                        || !isSafeOptionalIdentifier(selection.getTextureId())) {
                    return true;
                }
                if (selection.getModelId().isEmpty()) {
                    SERVER_MODEL_SELECTIONS.remove(player.getUniqueID());
                } else {
                    SERVER_MODEL_SELECTIONS.put(player.getUniqueID(),
                            new ModelSelection(selection.getModelId(), selection.getTextureId()));
                }
                sendKnownModelSelections(player);
                broadcast(player, new SCustomPayloadPlayPacket(S2C_MODEL_SELECTION,
                        encodeModelSelection(player.getEntityId(), selection.getModelId(), selection.getTextureId())));
                return true;
            }

            if (C2S_PLAY_EXTRA.equals(channel)) {
                C2SPlayExtraAnimationPacket play = C2SPlayExtraAnimationPacket.decode(buffer);
                if (!isSafeIdentifier(play.getModelId()) || !isSafeIdentifier(play.getAnimationName())) {
                    return true;
                }
                if (!OpenYsmAnimationRegistry.isWheelActionAllowed(play.getModelId(), play.getAnimationName())) {
                    YesSteveModel.LOGGER.warn("[YSM] Rejected extra animation '{}' for model '{}' from {}: action is not registered for that model",
                            play.getAnimationName(), play.getModelId(), player.getGameProfile().getName());
                    return true;
                }
                broadcast(player, new SCustomPayloadPlayPacket(S2C_PLAY_EXTRA, encodePlay(player.getEntityId(), play.getModelId(), play.getAnimationName())));
                return true;
            }

            if (C2S_GUI_VARIABLE.equals(channel)) {
                C2SGuiVariablePacket variable = C2SGuiVariablePacket.decode(buffer);
                if (!isSafeIdentifier(variable.getModelId()) || !isSafeVariableName(variable.getVariableName())
                        || !Double.isFinite(variable.getValue())) {
                    return true;
                }
                OpenYsmPlayerAnimationState.setGuiVariable(player.getUniqueID(), variable.getModelId(),
                        variable.getVariableName(), variable.getValue());
                broadcast(player, new SCustomPayloadPlayPacket(S2C_GUI_VARIABLE, encodeVariable(player.getEntityId(),
                        variable.getModelId(), variable.getVariableName(), variable.getValue())));
                return true;
            }

            C2SStopExtraAnimationPacket stop = C2SStopExtraAnimationPacket.decode(buffer);
            if (isSafeIdentifier(stop.getModelId())) {
                broadcast(player, new SCustomPayloadPlayPacket(S2C_STOP_EXTRA, encodeStop(player.getEntityId(), stop.getModelId())));
            }
            return true;
        } catch (RuntimeException exception) {
            YesSteveModel.LOGGER.warn("[YSM] Ignoring malformed OpenYSM client animation request", exception);
            return true;
        } finally {
            buffer.release();
        }
    }

    public static void forgetServerPlayer(ServerPlayerEntity player) {
        if (player != null) {
            SERVER_MODEL_SELECTIONS.remove(player.getUniqueID());
        }
    }

    public static void syncExtraEntityModel(Entity extraEntity, Entity sourceEntity) {
        if (extraEntity == null || sourceEntity == null || extraEntity.world.isRemote
                || !(sourceEntity instanceof ServerPlayerEntity)) {
            return;
        }

        ServerPlayerEntity sourcePlayer = (ServerPlayerEntity) sourceEntity;
        ModelSelection selection = SERVER_MODEL_SELECTIONS.get(sourcePlayer.getUniqueID());
        ExtraEntitySelection previous = SERVER_EXTRA_ENTITY_SELECTIONS.get(extraEntity.getEntityId());
        if (selection == null) {
            if (previous != null) {
                SERVER_EXTRA_ENTITY_SELECTIONS.remove(extraEntity.getEntityId());
                sendExtraEntityModel(extraEntity, sourcePlayer, "", "", Collections.emptyMap());
            }
            return;
        }

        Map<String, Double> variables = OpenYsmPlayerAnimationState.getGuiVariables(sourcePlayer.getUniqueID(),
                selection.modelId);
        ExtraEntitySelection current = new ExtraEntitySelection(selection, variables);
        if (current.sameAs(previous)) {
            return;
        }

        if (SERVER_EXTRA_ENTITY_SELECTIONS.size() >= MAX_SERVER_EXTRA_ENTITY_SELECTIONS) {
            SERVER_EXTRA_ENTITY_SELECTIONS.clear();
        }
        SERVER_EXTRA_ENTITY_SELECTIONS.put(extraEntity.getEntityId(), current);
        sendExtraEntityModel(extraEntity, sourcePlayer, selection.modelId, selection.textureId, variables);
    }

    public static void sendKnownExtraEntityModel(Entity extraEntity, ServerPlayerEntity viewer) {
        if (extraEntity == null || viewer == null) {
            return;
        }
        ExtraEntitySelection selection = SERVER_EXTRA_ENTITY_SELECTIONS.get(extraEntity.getEntityId());
        if (selection == null || selection.model == null || selection.model.modelId.isEmpty()) {
            return;
        }
        viewer.connection.sendPacket(new SCustomPayloadPlayPacket(S2C_EXTRA_ENTITY_MODEL,
                encodeExtraEntityModel(extraEntity.getEntityId(), selection.model.modelId,
                        selection.model.textureId, selection.variables)));
    }

    private static PacketBuffer encodeModelSelection(int entityId, String modelId, String textureId) {
        PacketBuffer buffer = new PacketBuffer(Unpooled.buffer());
        new S2CModelSelectionPacket(entityId, modelId, textureId).encode(buffer);
        return buffer;
    }

    private static PacketBuffer encodeExtraEntityModel(int entityId, String modelId, String textureId,
                                                       Map<String, Double> variables) {
        PacketBuffer buffer = new PacketBuffer(Unpooled.buffer());
        new S2CExtraEntityModelPacket(entityId, modelId, textureId, variables).encode(buffer);
        return buffer;
    }

    private static PacketBuffer encodePlay(int entityId, String modelId, String animationName) {
        PacketBuffer buffer = new PacketBuffer(Unpooled.buffer());
        new S2CPlayExtraAnimationPacket(entityId, modelId, animationName).encode(buffer);
        return buffer;
    }

    private static PacketBuffer encodeStop(int entityId, String modelId) {
        PacketBuffer buffer = new PacketBuffer(Unpooled.buffer());
        new S2CStopExtraAnimationPacket(entityId, modelId).encode(buffer);
        return buffer;
    }

    private static PacketBuffer encodeVariable(int entityId, String modelId, String variableName, double value) {
        PacketBuffer buffer = new PacketBuffer(Unpooled.buffer());
        new S2CGuiVariablePacket(entityId, modelId, variableName, value).encode(buffer);
        return buffer;
    }

    private static void broadcast(ServerPlayerEntity player, SCustomPayloadPlayPacket packet) {
        player.connection.sendPacket(packet);
        player.getServerWorld().getChunkProvider().sendToAllTracking(player, packet);
    }

    private static void sendExtraEntityModel(Entity extraEntity, ServerPlayerEntity sourcePlayer,
                                             String modelId, String textureId, Map<String, Double> variables) {
        SCustomPayloadPlayPacket packet = new SCustomPayloadPlayPacket(S2C_EXTRA_ENTITY_MODEL,
                encodeExtraEntityModel(extraEntity.getEntityId(), modelId, textureId, variables));
        sourcePlayer.connection.sendPacket(packet);
        sourcePlayer.getServerWorld().getChunkProvider().sendToAllTracking(extraEntity, packet);
    }

    private static void sendKnownModelSelections(ServerPlayerEntity player) {
        if (player.getServer() == null) {
            return;
        }
        for (ServerPlayerEntity other : player.getServer().getPlayerList().getPlayers()) {
            ModelSelection selection = SERVER_MODEL_SELECTIONS.get(other.getUniqueID());
            if (selection != null) {
                player.connection.sendPacket(new SCustomPayloadPlayPacket(S2C_MODEL_SELECTION,
                        encodeModelSelection(other.getEntityId(), selection.modelId, selection.textureId)));
            }
        }
    }

    private static boolean isSafeIdentifier(String value) {
        return value != null
                && !value.isEmpty()
                && value.length() <= 256
                && value.indexOf('\0') < 0
                && value.indexOf('\n') < 0
                && value.indexOf('\r') < 0;
    }

    private static boolean isSafeOptionalIdentifier(String value) {
        return value != null
                && value.length() <= 256
                && value.indexOf('\0') < 0
                && value.indexOf('\n') < 0
                && value.indexOf('\r') < 0;
    }

    private static boolean isSafeVariableName(String value) {
        if (value == null || value.isEmpty() || value.length() > 64) {
            return false;
        }
        String lower = value.toLowerCase(java.util.Locale.ROOT);
        for (int i = 0; i < lower.length(); i++) {
            char c = lower.charAt(i);
            if (!((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '_' || c == '.')) {
                return false;
            }
        }
        return true;
    }

    private static boolean areSafeVariables(Map<String, Double> variables) {
        if (variables == null || variables.size() > 64) {
            return false;
        }
        for (Map.Entry<String, Double> entry : variables.entrySet()) {
            if (!isSafeVariableName(entry.getKey()) || entry.getValue() == null || !Double.isFinite(entry.getValue())) {
                return false;
            }
        }
        return true;
    }

    private static final class ModelSelection {
        private final String modelId;
        private final String textureId;

        private ModelSelection(String modelId, String textureId) {
            this.modelId = modelId == null ? "" : modelId;
            this.textureId = textureId == null ? "" : textureId;
        }

        private boolean sameAs(ModelSelection other) {
            return other != null && this.modelId.equals(other.modelId) && this.textureId.equals(other.textureId);
        }
    }

    private static final class ExtraEntitySelection {
        private final ModelSelection model;
        private final Map<String, Double> variables;

        private ExtraEntitySelection(ModelSelection model, Map<String, Double> variables) {
            this.model = model;
            this.variables = Collections.unmodifiableMap(new HashMap<>(variables == null
                    ? Collections.emptyMap()
                    : variables));
        }

        private boolean sameAs(ExtraEntitySelection other) {
            return other != null && this.model.sameAs(other.model) && this.variables.equals(other.variables);
        }
    }
}
