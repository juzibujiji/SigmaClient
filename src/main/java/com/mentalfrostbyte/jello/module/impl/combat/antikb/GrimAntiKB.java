package com.mentalfrostbyte.jello.module.impl.combat.antikb;

import com.mentalfrostbyte.jello.event.impl.game.network.EventReceivePacket;
import com.mentalfrostbyte.jello.module.Module;
import com.mentalfrostbyte.jello.module.data.ModuleCategory;
import net.minecraft.network.play.server.SConfirmTransactionPacket;
import net.minecraft.network.play.server.SEntityVelocityPacket;
import team.sdhq.eventBus.annotations.EventTarget;

public class GrimAntiKB extends Module {
    public GrimAntiKB() {
        super(ModuleCategory.COMBAT, "Grim", "Epic prediction bypass 2025 dont let stormingmoon code again");
    }

    @EventTarget
    public void dontletmecodeagain(EventReceivePacket event) {
        if (mc.player != null) {
            if (event.packet instanceof SEntityVelocityPacket packet) {
                if (packet.getEntityID() == mc.player.getEntityId()) {
                    event.cancelled = true;
                }
            }

            if (event.packet instanceof SConfirmTransactionPacket) {
                if (mc.player.ticksExisted > 180) {
                    event.cancelled = true;
                }
            }
        }
    }
}