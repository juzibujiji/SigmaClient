package com.mentalfrostbyte.jello.module.impl.combat.aimbot;

import com.mentalfrostbyte.jello.event.impl.player.EventRunTicks;
import team.sdhq.eventBus.annotations.EventTarget;

public class BasicAimbot extends NaturalAimbotMode {
    public BasicAimbot() {
        super("Basic", "Direct but controllable aim assistance", 18.0F, 12.0F, 0.75F, false);
    }

    @Override
    @EventTarget
    public void onRunTick(EventRunTicks event) {
        super.onRunTick(event);
    }
}
