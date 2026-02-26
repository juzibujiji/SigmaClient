package com.mentalfrostbyte.jello.module.impl.world.disabler;

import com.mentalfrostbyte.jello.event.impl.player.EventUpdate;
import com.mentalfrostbyte.jello.module.Module;
import com.mentalfrostbyte.jello.module.data.ModuleCategory;
import net.minecraft.network.play.client.CInputPacket;
import team.sdhq.eventBus.annotations.EventTarget;

import java.util.Objects;

public class GhostlyDisabler extends Module {
    public GhostlyDisabler() {
        super(ModuleCategory.EXPLOIT, "Ghostly", "Disable ghostly's anticheat.");
    }

    @EventTarget
    public void TickEvent(EventUpdate event) {
        if (this.isEnabled() && mc.getCurrentServerData() != null) {
            Objects.requireNonNull(mc.getConnection()).sendPacket(
                    new CInputPacket(1.0F, 1.0F, false, false)
            );
        }
    }
}
