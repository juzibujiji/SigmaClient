package com.mentalfrostbyte.jello.module.settings.impl;

import com.google.gson.JsonObject;
import com.mentalfrostbyte.jello.module.settings.Setting;
import com.mentalfrostbyte.jello.module.settings.SettingType;
import com.mentalfrostbyte.jello.util.system.other.GsonUtil;
import org.jetbrains.annotations.NotNull;

import java.awt.*;

public class ColorSetting extends Setting<Integer> {
    public boolean rainbow = false;

    public ColorSetting(String name, String description, int defaultColor, boolean rainbow) {
        super(name, description, SettingType.COLOR, defaultColor);
        this.rainbow = rainbow;
    }

    public ColorSetting(String name, String description, int defaultColor) {
        super(name, description, SettingType.COLOR, defaultColor);
    }

    @Override
    public JsonObject loadCurrentValueFromJSONObject(JsonObject jsonObject) {
        this.currentValue = GsonUtil.getIntOrDefault(jsonObject, "value", this.getDefaultColor());
        this.rainbow = GsonUtil.getBooleanOrDefault(jsonObject, "rainbow", false);
        return jsonObject;
    }

    @Override
    public JsonObject buildUpSettingData(JsonObject jsonObject) {
        jsonObject.addProperty("name", this.getName());
        jsonObject.addProperty("value", this.getCurrentValue());
        jsonObject.addProperty("rainbow", this.rainbow);
        return jsonObject;
    }

    public @NotNull Integer getCurrentValue() {
        if (!this.rainbow) {
            return this.currentValue;
        } else {
            Color color = new Color(this.currentValue);
            float[] hsb = Color.RGBtoHSB(color.getRed(), color.getGreen(), color.getBlue(), null);
            return Color.getHSBColor((float) (System.currentTimeMillis() % 4000L) / 4000.0F, hsb[1], hsb[2]).getRGB();
        }
    }

    public Integer getDefaultColor() {
        return this.currentValue;
    }
}