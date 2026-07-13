package com.mentalfrostbyte.jello.module.impl.combat.aimbot;

import com.mentalfrostbyte.jello.event.impl.player.EventRunTicks;
import com.mentalfrostbyte.jello.util.game.player.rotation.JelloAI;
import team.sdhq.eventBus.annotations.EventTarget;

public class JelloAIAimbot extends NaturalAimbotMode {
    public JelloAIAimbot() {
        super("JelloAI", "AI-guided natural aim assistance", 9.5F, 6.0F, 0.24F, true);
    }

    @Override
    public void onEnable() {
        super.onEnable();
        JelloAI.init();
    }

    @Override
    @EventTarget
    public void onRunTick(EventRunTicks event) {
        super.onRunTick(event);
    }

    @Override
    protected float[] getDesiredRotation(double x, double y, double z) {
        return JelloAI.getRotationsToPosition(x, y, z);
    }
}
