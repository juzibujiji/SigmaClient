package com.mentalfrostbyte.jello.module.settings.impl;

import com.google.gson.JsonObject;
import com.mentalfrostbyte.jello.module.settings.Setting;
import com.mentalfrostbyte.jello.module.settings.SettingType;
import com.mentalfrostbyte.jello.util.system.other.GsonUtil;

public class InputSetting extends Setting<String> {
    public InputSetting(String name, String description, String defaultValue) {
        super(name, description, SettingType.INPUT, defaultValue);
    }

    @Override
    public JsonObject loadCurrentValueFromJSONObject(JsonObject jsonObject) {
        this.currentValue = GsonUtil.getStringOrDefault(jsonObject, "value", this.getDefaultValue());
        return jsonObject;
    }
}
