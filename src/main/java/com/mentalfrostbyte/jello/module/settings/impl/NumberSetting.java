package com.mentalfrostbyte.jello.module.settings.impl;

import com.google.gson.JsonObject;
import com.mentalfrostbyte.jello.module.settings.Setting;
import com.mentalfrostbyte.jello.module.settings.SettingType;
import com.mentalfrostbyte.jello.util.system.other.GsonUtil;

public class NumberSetting<T extends Number> extends Setting<Float> {
    private float minValue;
    private float maxValue;
    private float step;

    public NumberSetting(String name, String description, float defaultValue, float minimum, float maximum, float increment) {
        super(name, description, SettingType.NUMBER, defaultValue);
        this.minValue = minimum;
        this.maxValue = maximum;
        this.step = increment;
    }

    public int getDecimalPlaces() {
        if (this.step != 1.0F) {
            String stepString = Float.toString(Math.abs(this.step));
            int decimalPointIndex = stepString.indexOf('.');
            return stepString.length() - decimalPointIndex - 1;
        } else {
            return 0;
        }
    }

    @Override
    public JsonObject loadCurrentValueFromJSONObject(JsonObject jsonObject) {
        this.currentValue = GsonUtil.getFloatOrDefault(jsonObject, "value", this.getDefaultValue());
        return jsonObject;
    }

    public float getMin() {
        return this.minValue;
    }

    public void setMin(float var1) {
        this.minValue = var1;
    }

    public float getMax() {
        return this.maxValue;
    }

    public void setMax(float var1) {
        this.maxValue = var1;
    }

    public float getStep() {
        return this.step;
    }

    public void setStep(float var1) {
        this.step = var1;
    }
}
