package com.mentalfrostbyte.jello.module.settings;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;

public abstract class Setting<T> {
    public final SettingType settingType;
    public final String name;
    public final String description;
    private final List<SettingObserver> observers = new ArrayList<>();
    private BooleanSupplier hidden = () -> false;
    @NotNull
    public T currentValue;
    @NotNull
    public T defaultValue;

    public Setting(String name, String description, SettingType settingType, @NotNull T defaultValue) {
        this.settingType = settingType;
        this.currentValue = this.defaultValue = defaultValue;
        this.name = name;
        this.description = description;
    }

    public abstract JsonObject loadCurrentValueFromJSONObject(JsonObject jsonObject) throws JsonParseException;

    public JsonObject buildUpSettingData(JsonObject jsonObject) {
        Gson gson = new Gson();

        jsonObject.addProperty("name", this.getName());

        JsonElement valueElement = gson.toJsonTree(this.currentValue);
        jsonObject.add("value", valueElement);

        return jsonObject;
    }

    public boolean isHidden() {
        return hidden.getAsBoolean();
    }

    public void resetToDefault() {
        this.currentValue = this.defaultValue;
    }

    public final Setting<T> addObserver(SettingObserver observer) {
        this.observers.add(observer);
        return this;
    }

    public final void notifyObservers() {
        for (SettingObserver observer : this.observers) {
            observer.observe(this);
        }
    }

    public SettingType getSettingType() {
        return this.settingType;
    }

    public @NotNull T getCurrentValue() {
        return this.currentValue;
    }

    public void setCurrentValue(T value) {
        this.updateCurrentValue(value, true);
    }

    public void updateCurrentValue(T value, boolean notify) {
        if (this.currentValue != value) {
            this.currentValue = value;
            if (notify) {
                this.notifyObservers();
            }
        }
    }

    public @NotNull T getDefaultValue() {
        return this.defaultValue;
    }

    public String getName() {
        return this.name;
    }

    public String getDescription() {
        return this.description;
    }

    @Override
    public String toString() {
        return this.getCurrentValue().toString();
    }
}
