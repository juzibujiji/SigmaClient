package com.mentalfrostbyte.jello.module.settings.impl;

import com.google.gson.JsonObject;
import com.mentalfrostbyte.jello.module.settings.Setting;
import com.mentalfrostbyte.jello.module.settings.SettingType;
import com.mentalfrostbyte.jello.util.client.render.ResourceRegistry;
import com.mentalfrostbyte.jello.util.system.other.GsonUtil;
import org.newdawn.slick.TrueTypeFont;

import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class FontSwitch extends Setting<String> {
    private final Runnable openFolderAction;
    private final Runnable refreshAction;
    private final Supplier<List<String>> fontsSupplier;
    private final Consumer<String> fontSelectionAction;
    private final Supplier<TrueTypeFont> previewFontSupplier;

    public FontSwitch(String name, String description, String defaultValue,
                      Runnable openFolderAction, Runnable refreshAction,
                      Supplier<List<String>> fontsSupplier, Consumer<String> fontSelectionAction,
                      Supplier<TrueTypeFont> previewFontSupplier) {
        super(name, description, SettingType.FONT_SWITCH, defaultValue);
        this.openFolderAction = openFolderAction;
        this.refreshAction = refreshAction;
        this.fontsSupplier = fontsSupplier;
        this.fontSelectionAction = fontSelectionAction;
        this.previewFontSupplier = previewFontSupplier;
    }

    @Override
    public JsonObject loadCurrentValueFromJSONObject(JsonObject jsonObject) {
        this.currentValue = GsonUtil.getStringOrDefault(jsonObject, "value", this.getDefaultValue());
        return jsonObject;
    }

    public void openFolder() {
        if (this.openFolderAction != null) {
            this.openFolderAction.run();
        }
    }

    public void refresh() {
        if (this.refreshAction != null) {
            this.refreshAction.run();
        }
    }

    public List<String> getFonts() {
        List<String> fonts = this.fontsSupplier == null ? null : this.fontsSupplier.get();
        return fonts == null ? Collections.emptyList() : fonts;
    }

    public void selectFont(String fontName) {
        if (this.fontSelectionAction != null) {
            this.fontSelectionAction.accept(fontName);
        } else {
            this.setCurrentValue(fontName);
        }
    }

    public TrueTypeFont getPreviewFont() {
        TrueTypeFont font = this.previewFontSupplier == null ? null : this.previewFontSupplier.get();
        return font == null ? ResourceRegistry.JelloLightFont18 : font;
    }
}
