package com.mentalfrostbyte.jello.module.impl.combat.antikb;

import com.mentalfrostbyte.jello.event.impl.game.network.EventReceivePacket;
import com.mentalfrostbyte.jello.event.impl.player.EventUpdate;
import com.mentalfrostbyte.jello.module.Module;
import com.mentalfrostbyte.jello.module.data.ModuleCategory;
import com.mentalfrostbyte.jello.module.settings.impl.NumberSetting;
import com.mentalfrostbyte.jello.util.game.player.MovementUtil;
import net.minecraft.network.play.server.SEntityVelocityPacket;
import team.sdhq.eventBus.annotations.EventTarget;

public class SpartanAntiKB extends Module {
    private int ticksSinceKb;

    public SpartanAntiKB() {
        super(ModuleCategory.COMBAT, "Spartan", "AntiKB for spartan antichet");
        this.registerSetting(new NumberSetting<Float>("Ticks", "Ticks delay", 1.0F, 1.0F, 6.0F, 1.0F));
    }

    @EventTarget
    public void onTick(EventUpdate event) {
        if (this.isEnabled()) {
            if (this.ticksSinceKb < 10) {
                this.ticksSinceKb++;
                if (this.ticksSinceKb == (int) this.getNumberValueBySettingName("Ticks")) {
                    MovementUtil.moveInDirection(0.0);
                }
            }
        }
    }

    @EventTarget
    public void onReceivePacket(EventReceivePacket event) {
        if (this.isEnabled()) {
            if (mc.player != null && event.packet instanceof SEntityVelocityPacket var4) {
				if (var4.getEntityID() == mc.player.getEntityId()) {
                    this.ticksSinceKb = 0;
                }
            }
        }
    }
}
