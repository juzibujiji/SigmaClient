package com.elfmcys.yesstevemodel.network;

import com.elfmcys.yesstevemodel.YesSteveModel;
import com.elfmcys.yesstevemodel.capability.OpenYsmPlayerAnimationState;
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

public final class OpenYsmNetwork {
    public static final ResourceLocation C2S_PLAY_EXTRA = new ResourceLocation(YesSteveModel.MOD_ID, "play_extra_animation");
    public static final ResourceLocation C2S_STOP_EXTRA = new ResourceLocation(YesSteveModel.MOD_ID, "stop_extra_animation");
    public static final ResourceLocation S2C_PLAY_EXTRA = new ResourceLocation(YesSteveModel.MOD_ID, "sync_play_extra_animation");
    public static final ResourceLocation S2C_STOP_EXTRA = new ResourceLocation(YesSteveModel.MOD_ID, "sync_stop_extra_animation");

    private OpenYsmNetwork() {
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

    public static boolean handleClientPayload(SCustomPayloadPlayPacket packet) {
        ResourceLocation channel = packet.getChannelName();
        if (!S2C_PLAY_EXTRA.equals(channel) && !S2C_STOP_EXTRA.equals(channel)) {
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
        if (!C2S_PLAY_EXTRA.equals(channel) && !C2S_STOP_EXTRA.equals(channel)) {
            return false;
        }

        PacketBuffer buffer = packet.getBufferData();
        try {
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

    private static void broadcast(ServerPlayerEntity player, SCustomPayloadPlayPacket packet) {
        player.connection.sendPacket(packet);
        player.getServerWorld().getChunkProvider().sendToAllTracking(player, packet);
    }

    private static boolean isSafeIdentifier(String value) {
        return value != null
                && !value.isEmpty()
                && value.length() <= 256
                && value.indexOf('\0') < 0
                && value.indexOf('\n') < 0
                && value.indexOf('\r') < 0;
    }
}
