package com.mentalfrostbyte.jello.module.impl.render;

import com.mentalfrostbyte.jello.event.impl.game.network.EventGetLocationSkin;
import com.mentalfrostbyte.jello.event.impl.game.EventReplaceText;
import com.mentalfrostbyte.jello.module.Module;
import com.mentalfrostbyte.jello.module.data.ModuleCategory;
import com.mentalfrostbyte.jello.module.settings.impl.BooleanSetting;
import com.mentalfrostbyte.jello.module.settings.impl.InputSetting;
import team.sdhq.eventBus.annotations.EventTarget;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.regex.Pattern;

public class Streaming extends Module {
    public Streaming() {
        super(ModuleCategory.RENDER, "Streaming", "Useful module when recording or streaming");
        this.registerSetting(new BooleanSetting("Hide skins", "Spoof all players skin", true));
        this.registerSetting(new BooleanSetting("Hide server name", "Spoof server name", false));
        this.registerSetting(new InputSetting("Server name", "The server name that you need to hide", "servernamehere"));
        this.registerSetting(new BooleanSetting("Hide date", "Hide date on scoreboard", false));
    }

    @EventTarget
    public void onTextReplace(EventReplaceText event) {
        if (getBooleanValueFromSettingName("Hide date")) {
            String todayFull = LocalDate.now().format(DateTimeFormatter.ofPattern("MM/dd/yyyy"));
            String todayShort = LocalDate.now().format(DateTimeFormatter.ofPattern("MM/dd/yy"));

            event.getText(event.getText().replaceAll(
                    Pattern.quote(todayFull) + "|" + Pattern.quote(todayShort),
                    "HIDDEN"
            ));
        }

        if (this.getBooleanValueFromSettingName("Hide server name") && this.getStringSettingValueByName("Server name").length() > 1) {
            event.getText(event.getText().replaceAll(this.getStringSettingValueByName("Server name"), "sigmaclient"));
            event.getText(event.getText().replaceAll(this.getStringSettingValueByName("Server name").toLowerCase(), "sigmaclient"));
            event.getText(event.getText().replaceAll(this.getStringSettingValueByName("Server name").toUpperCase(), "sigmaclient"));
        }
    }

    @EventTarget
    public void onGetLocationSkin(EventGetLocationSkin event) {
        if (this.getBooleanValueFromSettingName("Hide skins")) {
            event.cancelled = true;
        }
    }
}