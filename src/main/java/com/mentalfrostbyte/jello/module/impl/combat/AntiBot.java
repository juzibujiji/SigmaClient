package com.mentalfrostbyte.jello.module.impl.combat;

import com.mentalfrostbyte.Client;
import com.mentalfrostbyte.jello.managers.BotManager;
import com.mentalfrostbyte.jello.managers.util.combat.AntiBotBase;
import com.mentalfrostbyte.jello.module.Module;
import com.mentalfrostbyte.jello.module.data.ModuleCategory;
import com.mentalfrostbyte.jello.module.impl.combat.antibot.HypixelAntiBot;
import com.mentalfrostbyte.jello.module.impl.combat.antibot.MovementAntiBot;
import com.mentalfrostbyte.jello.module.settings.impl.ModeSetting;
import team.sdhq.eventBus.EventBus;

public class AntiBot extends Module {
    public AntiBot() {
        super(ModuleCategory.COMBAT, "AntiBot", "Avoid the client to focus bots.");
        this.registerSetting(new ModeSetting("Mode", "Mode", 0, "Advanced", "Hypixel").addObserver(var1 -> this.setup()));
    }

    @Override
    public void initialize() {
        if (this.isEnabled()) {
            this.setup();
        }
    }

    @Override
    public void onEnable() {
        this.setup();
    }

    @Override
    public void onDisable() {
        this.clearDetector();
    }

    public void setup() {
        this.clearDetector();
        if (!this.isEnabled()) {
            return;
        }

        BotManager botManager = Client.getInstance().botManager;
        String mode = this.getStringSettingValueByName("Mode");
        switch (mode) {
            case "Advanced":
                botManager.antiBot = new MovementAntiBot();
                break;
            case "Hypixel":
                botManager.antiBot = new HypixelAntiBot();
        }
    }

    private void clearDetector() {
        BotManager botManager = Client.getInstance().botManager;
        AntiBotBase detector = botManager.antiBot;
        if (detector != null) {
            detector.method22763(false);
            EventBus.unregister(detector);
            botManager.antiBot = null;
        }
        botManager.bots.clear();
    }
}
