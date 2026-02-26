package com.mentalfrostbyte.jello.module.settings.impl;

import com.google.gson.JsonObject;
import com.mentalfrostbyte.jello.module.settings.Setting;
import com.mentalfrostbyte.jello.module.settings.SettingType;
import com.mentalfrostbyte.jello.util.system.other.GsonUtil;
import org.jetbrains.annotations.NotNull;

public class BooleanSetting extends Setting<Boolean> {
    public BooleanSetting(String name, String description, boolean value) {
        super(name, description, SettingType.BOOLEAN, value);
    }

    public void updateCurrentValue(Boolean value, boolean notify) {
        super.updateCurrentValue(value, notify);
    }

    public @NotNull Boolean getCurrentValue() {
        return this.currentValue;
    }

    @Override
    public JsonObject loadCurrentValueFromJSONObject(JsonObject jsonObject) {
        this.currentValue = GsonUtil.getBooleanOrDefault(jsonObject, "value", this.getDefaultValue());
        return jsonObject;
    }
}