package com.mentalfrostbyte.jello.module.impl.misc;

import com.mentalfrostbyte.jello.module.Module;
import com.mentalfrostbyte.jello.module.data.ModuleCategory;
import com.mentalfrostbyte.jello.module.settings.impl.InputSetting;
import com.mentalfrostbyte.jello.module.settings.impl.ModeSetting;

public class ClientSpoof extends Module {
    public ClientSpoof() {
        super(ModuleCategory.MISC, "ClientSpoof", "Fakes client mod type on connection");
        this.registerSetting(
                new ModeSetting("Mode", "Client spoof mode", "Lunar", "Vanilla", "Geyser", "Lunar", "Cheatbreaker", "Custom"),
                new InputSetting("BrandName", "Custom client brand name", "")
        );
    }

    public String getSpoofedBrand(String originalBrand) {
        if (!this.isEnabled()) {
            return originalBrand;
        }

        String mode = this.getStringSettingValueByName("Mode");
        if (mode == null) {
            return originalBrand;
        }

        if (mode.equals("Vanilla")) {
            return "vanilla";
        }

        if (mode.equals("Geyser")) {
            return "geyser";
        }

        if (mode.equals("Lunar")) {
            return "lunarclient:v2.16.8-2433";
        }

        if (mode.equals("Cheatbreaker")) {
            return "CB";
        }

        if (mode.equals("Custom")) {
            String customBrand = this.getStringSettingValueByName("BrandName");
            return customBrand != null ? customBrand : "";
        }

        return originalBrand;
    }
}