package com.mentalfrostbyte.jello.module.impl.player;

import com.mentalfrostbyte.jello.event.impl.game.network.EventSendPacket;
import com.mentalfrostbyte.jello.event.impl.game.render.EventRendererLivingEntity;
import com.mentalfrostbyte.jello.event.impl.player.movement.EventMotion;
import com.mentalfrostbyte.jello.module.Module;
import com.mentalfrostbyte.jello.module.data.ModuleCategory;
import com.mentalfrostbyte.jello.module.settings.impl.BooleanSetting;
import com.mentalfrostbyte.jello.module.settings.impl.NumberSetting;
import net.minecraft.client.entity.player.RemoteClientPlayerEntity;
import net.minecraft.network.IPacket;
import net.minecraft.network.handshake.client.CHandshakePacket;
import net.minecraft.network.login.client.CEncryptionResponsePacket;
import net.minecraft.network.login.client.CLoginStartPacket;
import net.minecraft.network.play.client.*;
import net.minecraft.network.status.client.CPingPacket;
import net.minecraft.network.status.client.CServerQueryPacket;
import net.minecraft.util.math.vector.Vector3d;
import team.sdhq.eventBus.annotations.EventTarget;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;

public class Blink extends Module {
    private static final BooleanSetting slowRelease = new BooleanSetting("SlowRelease","SlowRelease Mode On ReleasePlayerPacket", true);
    private static final NumberSetting<Integer> slowReleaseMaxTick = new NumberSetting<>("SlowReleaseMaxTick", "SlowRelease Max Tick", 50, 1, 500, 10);
    public static RemoteClientPlayerEntity clientPlayerEntity;

    private final LinkedBlockingQueue<IPacket<?>> packets = new LinkedBlockingQueue<>();

    private double posX, posY, posZ;

    public Blink() {
        super(ModuleCategory.PLAYER, "Blink", "Stops your packets to blink");
        this.registerSetting(slowRelease,slowReleaseMaxTick);
    }

    @Override
    public void onEnable() {
        clientPlayerEntity = new RemoteClientPlayerEntity(mc.world, mc.player.getGameProfile());
        clientPlayerEntity.copyLocationAndAnglesFrom(mc.player);
        clientPlayerEntity.rotationYawHead = mc.player.rotationYawHead;
        clientPlayerEntity.setSprinting(mc.player.serverSprintState);
        mc.world.addEntity(-1, clientPlayerEntity);
    }

    @EventTarget
    public void onRenderEntity(EventRendererLivingEntity e) {
        if (e.getEntity() == clientPlayerEntity) {
            e.setAlpha(0.4f);
        }
    }

    @Override
    public void onDisable() {
        releasePackets();
        mc.world.removeEntityFromWorld(-1);
    }

    @EventTarget
    public void onMotion(EventMotion event) {
        if (event.isPre()) {
            if (slowRelease.getCurrentValue()) {
                float blinkTicks = slowReleaseMaxTick.getCurrentValue();
                long movementCount = packets.stream().filter(p -> p instanceof CPlayerPacket).count();
                if (movementCount >= blinkTicks) {
                    releaseoneC03();
                }
            }
        }
    }

    @EventTarget
    public void onSendPacket(EventSendPacket event) {
        if (mc.player != null) {
            if (event.packet instanceof CHandshakePacket || event.packet instanceof CLoginStartPacket || event.packet instanceof CServerQueryPacket || event.packet instanceof  CChatMessagePacket || event.packet instanceof CEncryptionResponsePacket || event.packet instanceof CPingPacket || event.packet instanceof CKeepAlivePacket) {
                return;
            }
            this.packets.add(event.packet);
            event.cancelled = true;
        }
    }

    private void releasePackets() {
        while (!packets.isEmpty()) {
            IPacket<?> packet = packets.poll();
            handleFakePlayerPacket(packet);
            mc.getConnection().getNetworkManager().sendNoEventPacket(packet);
        }
    }
    private void releaseoneC03() {
        while (!packets.isEmpty()) {
            IPacket<?> packet = packets.poll();
            handleFakePlayerPacket(packet);
            mc.getConnection().getNetworkManager().sendNoEventPacket(packet);
            if (packet instanceof CPlayerPacket) {
                break;
            }
        }
    }

    private void handleFakePlayerPacket(IPacket<?> packet) {
        if (packet instanceof CPlayerPacket.PositionPacket) {
            CPlayerPacket.PositionPacket position = (CPlayerPacket.PositionPacket) packet;
            clientPlayerEntity.setPositionAndRotationDirect(position.getX(0.0d), position.getY(0.0d), position.getZ(0.0d), clientPlayerEntity.rotationYaw, clientPlayerEntity.rotationPitch, 3, true);
            clientPlayerEntity.onGround = position.isOnGround();

            posX = position.getX(0.0d);
            posY = position.getY(0.0d);
            posZ = position.getZ(0.0d);
        } else if (packet instanceof CPlayerPacket.RotationPacket) {
            CPlayerPacket.RotationPacket look = (CPlayerPacket.RotationPacket) packet;
            clientPlayerEntity.setPositionAndRotationDirect(clientPlayerEntity.positionVec.x, clientPlayerEntity.positionVec.y, clientPlayerEntity.positionVec.z, look.getYaw(0.0f), look.getPitch(0.0f), 3, true);
            clientPlayerEntity.onGround = look.isOnGround();
            clientPlayerEntity.rotationYawHead = look.getYaw(0.0f);
            clientPlayerEntity.rotationYaw = look.getYaw(0.0f);
            clientPlayerEntity.rotationPitch = look.getPitch(0.0f);
        } else if (packet instanceof CPlayerPacket.PositionRotationPacket) {
            CPlayerPacket.PositionRotationPacket posLook = (CPlayerPacket.PositionRotationPacket) packet;
            clientPlayerEntity.setPositionAndRotationDirect(posLook.getX(0.0d), posLook.getY(0.0d), posLook.getZ(0.0d), posLook.getYaw(0.0f), posLook.getPitch(0.0f), 3, true);
            clientPlayerEntity.onGround = posLook.isOnGround();

            posX = posLook.getX(0.0d);
            posY = posLook.getY(0.0d);
            posZ = posLook.getZ(0.0d);
            clientPlayerEntity.rotationYawHead = posLook.getYaw(0.0f);
            clientPlayerEntity.rotationYaw = posLook.getYaw(0.0f);
            clientPlayerEntity.rotationPitch = posLook.getPitch(0.0f);
        } else if (packet instanceof CEntityActionPacket) {
            CEntityActionPacket action = (CEntityActionPacket) packet;
            if (action.getAction() == CEntityActionPacket.Action.START_SPRINTING) {
                clientPlayerEntity.setSprinting(true);
            } else if (action.getAction() == CEntityActionPacket.Action.STOP_SPRINTING) {
                clientPlayerEntity.setSprinting(false);
            } else if (action.getAction() == CEntityActionPacket.Action.PRESS_SHIFT_KEY) {
                clientPlayerEntity.setSneaking(true);
            } else if (action.getAction() == CEntityActionPacket.Action.RELEASE_SHIFT_KEY) {
                clientPlayerEntity.setSneaking(false);
            }
        } else if (packet instanceof CAnimateHandPacket) {
            clientPlayerEntity.swingArm(((CAnimateHandPacket) packet).getHand());
        }
    }
}
