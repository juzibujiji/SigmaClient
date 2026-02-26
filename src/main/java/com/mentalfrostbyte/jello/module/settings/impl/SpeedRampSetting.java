package com.mentalfrostbyte.jello.module.settings.impl;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.mentalfrostbyte.jello.module.settings.Setting;
import com.mentalfrostbyte.jello.module.settings.SettingType;
import com.mentalfrostbyte.jello.util.system.other.GsonUtil;
import org.jetbrains.annotations.NotNull;

public class SpeedRampSetting extends Setting<SpeedRampSetting.SpeedRamp> {
    public SpeedRampSetting(String name, String description, float start, float middle, float end, float max) {
        super(name, description, SettingType.SPEEDRAMP, new SpeedRamp(start, middle, end, max));
    }

    @Override
    public JsonObject loadCurrentValueFromJSONObject(JsonObject jsonObject) throws JsonParseException {
        this.currentValue = new SpeedRamp(GsonUtil.getJSONArrayOrNull(jsonObject, "value"));
        return jsonObject;
    }

    @Override
    public JsonObject buildUpSettingData(JsonObject jsonObject) {
        jsonObject.addProperty("name", this.getName());

        SpeedRamp currentRamp = this.getCurrentValue();

        JsonArray jsonArray = new JsonArray();
        jsonArray.add(currentRamp.startValue);
        jsonArray.add(currentRamp.middleValue);
        jsonArray.add(currentRamp.endValue);
        jsonArray.add(currentRamp.maxValue);

        jsonObject.add("value", jsonArray);
        return jsonObject;
    }

    @Override
    public @NotNull SpeedRampSetting.SpeedRamp getCurrentValue() {
        return this.currentValue;
    }

    public void updateValues(float start, float middle, float end, float max) {
        this.setValues(start, middle, end, max, true);
    }

    public float[] getValues() {
        SpeedRamp ramp = this.getCurrentValue();
        return new float[]{ramp.startValue, ramp.middleValue, ramp.endValue, ramp.maxValue};
    }

    public void setValues(float start, float middle, float end, float max, boolean notify) {
        SpeedRamp newRamp = new SpeedRamp(start, middle, end, max);
        if (!this.currentValue.equals(newRamp)) {
            this.currentValue = newRamp;
            if (notify) {
                this.notifyObservers();
            }
        }
    }

    public static class SpeedRamp {
        public float startValue;
        public float middleValue;
        public float endValue;
        public float maxValue;

        public SpeedRamp(float start, float middle, float end, float max) {
            this.startValue = start;
            this.middleValue = middle;
            this.endValue = end;
            this.maxValue = max;
        }

        public SpeedRamp(JsonArray jsonArray) throws JsonParseException {
            this.startValue = Float.parseFloat(jsonArray.get(0).getAsString());
            this.middleValue = Float.parseFloat(jsonArray.get(1).getAsString());
            this.endValue = Float.parseFloat(jsonArray.get(2).getAsString());
            this.maxValue = Float.parseFloat(jsonArray.get(3).getAsString());
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == this) {
                return true;
            } else if (obj instanceof SpeedRamp other) {
				return this.startValue == other.startValue
                        && this.middleValue == other.middleValue
                        && this.endValue == other.endValue
                        && this.maxValue == other.maxValue;
            } else {
                return false;
            }
        }
    }
}