package com.mentalfrostbyte.jello.module.impl.player;

import com.mentalfrostbyte.jello.event.impl.player.EventUpdate;
import com.mentalfrostbyte.jello.module.Module;
import com.mentalfrostbyte.jello.module.data.ModuleCategory;
import team.sdhq.eventBus.annotations.EventTarget;

public class AutoRespawn extends Module {

    public AutoRespawn() {
        super(ModuleCategory.PLAYER, "AutoRespawn", "Respawns for you");
    }

    @EventTarget
    public void TickEvent(EventUpdate event) {
        if (this.isEnabled()) {
            if (!mc.player.isAlive()) {
                mc.player.respawnPlayer();
            }
        }
    }
}
