package com.mentalfrostbyte.jello.module.impl.player;

import com.mentalfrostbyte.Client;
import com.mentalfrostbyte.jello.event.impl.player.EventKeepSprint;
import com.mentalfrostbyte.jello.event.impl.player.EventUpdate;
import com.mentalfrostbyte.jello.module.Module;
import com.mentalfrostbyte.jello.module.data.ModuleCategory;
import com.mentalfrostbyte.jello.module.impl.movement.BlockFly;
import com.mentalfrostbyte.jello.module.settings.impl.BooleanSetting;
import team.sdhq.eventBus.annotations.EventTarget;

public class AutoSprint extends Module {

    public AutoSprint() {
        super(ModuleCategory.PLAYER, "AutoSprint", "Sprints for you");
        this.registerSetting(new BooleanSetting("NoJumpDelay", "Removes delay onJump.", false));
        this.registerSetting(new BooleanSetting("KeepSprint", "Attack Entity Sprint.", true) {
            @Override
            public boolean isHidden() {
                return getBooleanValueFromSettingName("KeepSprintAndMotion");
            }
        });
        this.registerSetting(new BooleanSetting("KeepSprintAndMotion", "Attack Entity KeepSprintAndMotion.", false));
    }

    @EventTarget
    public void TickEvent(EventUpdate event) {
        mc.gameSettings.keyBindSprint.setPressed(true);
        if (Client.getInstance().moduleManager.getModuleByClass(BlockFly.class).isEnabled() && Client.getInstance().moduleManager.getModuleByClass(BlockFly.class).getBooleanValueFromSettingName("Sprint")) {
            mc.gameSettings.keyBindSprint.setPressed(this.getBooleanValueFromSettingName("Sprint"));

        } else if (Client.getInstance().moduleManager.getModuleByClass(BlockFly.class).isEnabled() && Client.getInstance().moduleManager.getModuleByClass(BlockFly.class).getBooleanValueFromSettingName("Sprint") && Client.getInstance().moduleManager.getModuleByClass(BlockFly.class).getBooleanValueFromSettingName("UesGameSprint")) {
            mc.gameSettings.keyBindSprint.setPressed(false);
        }
    }

    @EventTarget
    public void KeepSprintEvent(EventKeepSprint event) {
        if (this.getBooleanValueFromSettingName("KeepSprintAndMotion")) {
            event.greater = false;
        }
    }

    @Override
    public void onDisable() {
        mc.gameSettings.keyBindSprint.setPressed(mc.gameSettings.keyBindSprint.isKeyDown());
    }
}