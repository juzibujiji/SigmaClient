package com.mentalfrostbyte.jello.module.impl.player;

import com.mentalfrostbyte.jello.event.impl.player.EventUpdate;
import com.mentalfrostbyte.jello.module.Module;
import com.mentalfrostbyte.jello.module.data.ModuleCategory;
import team.sdhq.eventBus.annotations.EventTarget;

public class AutoWalk extends Module {
    public AutoWalk() {
        super(ModuleCategory.PLAYER, "AutoWalk", "Automatically walks forward");
    }

    @EventTarget
    public void TickEvent(EventUpdate event) {
        if (this.isEnabled()) {
            mc.gameSettings.keyBindForward.setPressed(true);
        }
    }

    @Override
    public void onDisable() {
        mc.gameSettings.keyBindForward.setPressed(false);

    }
}
