package com.mentalfrostbyte.jello.module.impl.combat.aimbot;

import com.mentalfrostbyte.jello.event.impl.player.EventRunTicks;
import team.sdhq.eventBus.annotations.EventTarget;

public class SmoothAimbot extends NaturalAimbotMode {
    public SmoothAimbot() {
        super("Smooth", "Accelerated natural aim assistance", 11.0F, 7.0F, 0.32F, true);
    }

    @Override
    @EventTarget
    public void onRunTick(EventRunTicks event) {
        super.onRunTick(event);
    }
}
