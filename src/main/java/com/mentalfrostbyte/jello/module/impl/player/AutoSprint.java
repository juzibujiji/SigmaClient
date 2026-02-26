package com.mentalfrostbyte.jello.module.impl.player;

import com.mentalfrostbyte.Client;
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
    }

    @EventTarget
    public void TickEvent(EventUpdate event) {
        mc.gameSettings.keyBindSprint.setPressed(true);
        Client.getInstance().moduleManager.getModuleByClass(BlockFly.class);
        if (Client.getInstance().moduleManager.getModuleByClass(BlockFly.class).isEnabled()) {
			mc.gameSettings.keyBindSprint.setPressed(this.getBooleanValueFromSettingName("Sprint"));

        }

    }

    @Override
    public void onDisable() {
        mc.gameSettings.keyBindSprint.setPressed(mc.gameSettings.keyBindSprint.isKeyDown());
    }
}