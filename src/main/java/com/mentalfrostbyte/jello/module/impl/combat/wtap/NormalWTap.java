package com.mentalfrostbyte.jello.module.impl.combat.wtap;

import com.mentalfrostbyte.jello.event.impl.game.network.EventSendPacket;
import com.mentalfrostbyte.jello.event.impl.game.world.EventLoadWorld;
import com.mentalfrostbyte.jello.module.Module;
import com.mentalfrostbyte.jello.module.data.ModuleCategory;
import net.minecraft.network.play.client.CEntityActionPacket;
import net.minecraft.network.play.client.CPlayerPacket;
import net.minecraft.network.play.client.CUseEntityPacket;
import team.sdhq.eventBus.annotations.EventTarget;
import team.sdhq.eventBus.annotations.priority.LowestPriority;

public class NormalWTap extends Module {
    private boolean waitingForSprintStop;

    public NormalWTap() {
        super(ModuleCategory.COMBAT, "Normal", "Uses a conservative one-tick sprint reset after valid attacks");
    }

    @EventTarget
    @LowestPriority
    public void onPacketSend(EventSendPacket event) {
        if (!this.isEnabled() || mc.player == null || mc.getConnection() == null) {
            return;
        }

        if (event.packet instanceof CUseEntityPacket attackPacket) {
            if (!event.cancelled && WTapSupport.isValidSprintAttack(mc, attackPacket)) {
                this.waitingForSprintStop = true;
                mc.player.setSprinting(false);
            }
            return;
        }

        if (!this.waitingForSprintStop) {
            return;
        }

        if (event.packet instanceof CEntityActionPacket actionPacket
                && actionPacket.getAction() == CEntityActionPacket.Action.STOP_SPRINTING) {
            this.finishSprintTap();
            return;
        }

        if (event.packet instanceof CPlayerPacket) {
            this.waitingForSprintStop = false;
            if (mc.player.serverSprintState) {
                mc.getConnection().sendPacket(
                        new CEntityActionPacket(mc.player, CEntityActionPacket.Action.STOP_SPRINTING));
                mc.player.serverSprintState = false;
            }
            this.resumeSprintIfValid();
        }
    }

    @EventTarget
    public void onWorldLoad(EventLoadWorld event) {
        this.waitingForSprintStop = false;
    }

    @Override
    public void onDisable() {
        if (this.waitingForSprintStop) {
            this.resumeSprintIfValid();
        }
        this.waitingForSprintStop = false;
    }

    private void finishSprintTap() {
        this.waitingForSprintStop = false;
        this.resumeSprintIfValid();
    }

    private void resumeSprintIfValid() {
        if (WTapSupport.canResumeSprint(mc)) {
            mc.player.setSprinting(true);
        }
    }
}
