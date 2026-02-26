package com.mentalfrostbyte.jello.module.impl.movement.speed;

import com.mentalfrostbyte.jello.event.impl.player.movement.EventMotion;
import com.mentalfrostbyte.jello.util.game.player.MovementUtil;
import team.sdhq.eventBus.annotations.EventTarget;
import com.mentalfrostbyte.jello.module.Module;
import com.mentalfrostbyte.jello.module.data.ModuleCategory;
import com.mentalfrostbyte.jello.module.settings.impl.BooleanSetting;

public class LegitSpeed extends Module {
    public LegitSpeed() {
        super(ModuleCategory.MOVEMENT, "Legit", "Legit Sprint jumping.");
        this.registerSetting(new BooleanSetting("Sprint", "Sprints when walking", true));
        this.registerSetting(new BooleanSetting("AutoJump", "Automatically jumps for you.", true));
    }

    @Override
    public void onDisable() {
        mc.gameSettings.keyBindSprint.setPressed(false);
        mc.gameSettings.keyBindJump.setPressed(false);
    }

    @EventTarget
    public void onMotion(EventMotion event) {
        if (MovementUtil.isMoving()) {
            if (getBooleanValueFromSettingName("Sprint")) {
                mc.gameSettings.keyBindSprint.setPressed(true);
            }

            if (getBooleanValueFromSettingName("AutoJump")) {
                mc.gameSettings.keyBindJump.setPressed(true);
            }
        }
    }
}
