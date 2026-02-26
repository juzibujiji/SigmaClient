package com.mentalfrostbyte.jello.module.impl.render;

import com.mentalfrostbyte.jello.event.impl.game.network.EventReceivePacket;
import com.mentalfrostbyte.jello.module.Module;
import com.mentalfrostbyte.jello.module.data.ModuleCategory;
import net.minecraft.network.play.server.SUpdateBossInfoPacket;
import net.minecraft.network.play.server.SScoreboardObjectivePacket;
import team.sdhq.eventBus.annotations.EventTarget;

public class NoServerInfo extends Module {
    public NoServerInfo() {
        super(ModuleCategory.RENDER, "NoServerInfo", "Hides the server scoreboard and boss bar at top");
    }

    @EventTarget
    public void onReceivePacket(EventReceivePacket event) {
        if (this.isEnabled()) {
            if (event.packet instanceof SUpdateBossInfoPacket sUpdateBossInfoPacket) {
				event.cancelled = true;
            }

            if (event.packet instanceof SScoreboardObjectivePacket sScoreboardObjectivePacket) {
				event.cancelled = true;
            }
        }
    }
}