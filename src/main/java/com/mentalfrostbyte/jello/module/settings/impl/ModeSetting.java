package com.mentalfrostbyte.jello.module.settings.impl;

import com.google.gson.JsonObject;
import com.mentalfrostbyte.jello.module.settings.Setting;
import com.mentalfrostbyte.jello.module.settings.SettingType;
import com.mentalfrostbyte.jello.util.system.other.GsonUtil;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.List;

public class ModeSetting extends Setting<String> {

    public ModeSetting(String name, String description, int index, String... modes) {
        super(name, description, SettingType.MODE, modes[index]);
        this.modes = Arrays.asList(modes);
    }

    public ModeSetting(String name, String description, String defaultValue, String... modes) {
        super(name, description, SettingType.MODE, defaultValue);
        this.modes = Arrays.asList(modes);
    }

    private final List<String> modes;

    public @NotNull String getCurrentValue() {
        return this.currentValue;
    }

    public int getModeIndex() {
        int index = 0;

        for (String mode : this.modes) {
            if (mode.equals(this.currentValue)) {
                return index;
            }

            index++;
        }

        return 0;
    }

    public void setModeByIndex(int index) {
        if (index < this.modes.size()) {
            String mode = this.modes.get(index);
            this.setCurrentValue(mode);
        }
    }

    @Override
    public JsonObject loadCurrentValueFromJSONObject(JsonObject jsonObject) {
        this.currentValue = GsonUtil.getStringOrDefault(jsonObject, "value", this.getDefaultValue());
        boolean isValid = this.modes.contains(this.currentValue);

        if (!isValid) {
            this.currentValue = this.getDefaultValue();
        }

        return jsonObject;
    }

    public List<String> getAvailableModes() {
        return this.modes;
    }
}
