package com.mentalfrostbyte.jello.module.impl.movement.speed;

import com.mentalfrostbyte.jello.event.impl.game.network.EventReceivePacket;
import com.mentalfrostbyte.jello.event.impl.player.movement.EventMotion;
import com.mentalfrostbyte.jello.module.Module;
import com.mentalfrostbyte.jello.module.data.ModuleCategory;
import com.mentalfrostbyte.jello.module.settings.impl.ModeSetting;
import net.minecraft.network.IPacket;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.play.client.CPlayerPacket;
import net.minecraft.network.play.server.SConfirmTransactionPacket;
import net.minecraft.network.play.server.SEntityTeleportPacket;
import net.minecraft.network.play.server.SEntityVelocityPacket;
import team.sdhq.eventBus.annotations.EventTarget;

import java.util.Objects;
import java.util.concurrent.LinkedBlockingDeque;

public class GrimSpeed extends Module {
    private final ModeSetting grimode = new ModeSetting("GrimMode","choose speed mode","EntityCollide","EntityCollide");

    public GrimSpeed() {
        super(ModuleCategory.MOVEMENT,"Grim","Speed for Grim-AntiCheat");
        registerSetting(grimode);
    }

    @EventTarget
    public void onMotion(EventMotion event) {

    }

    @EventTarget
    public void onPacket(EventReceivePacket event) {

    }

}
