package com.elfmcys.yesstevemodel.gui;

import com.elfmcys.yesstevemodel.client.animation.ActionEntry;
import com.elfmcys.yesstevemodel.client.animation.OpenYsmAnimationSet;

public final class OpenYsmWheelNode {
    public enum Type {
        ACTION,
        CATEGORY,
        CONFIG_GROUP,
        RETURN,
        STOP
    }

    private final Type type;
    private final String key;
    private final String label;
    private final String description;
    private final ActionEntry action;
    private final OpenYsmAnimationSet.ExtraActionButton button;

    private OpenYsmWheelNode(Type type, String key, String label, String description,
                             ActionEntry action, OpenYsmAnimationSet.ExtraActionButton button) {
        this.type = type;
        this.key = key == null ? "" : key;
        this.label = label == null || label.isEmpty() ? this.key : label;
        this.description = description == null ? "" : description;
        this.action = action;
        this.button = button;
    }

    public static OpenYsmWheelNode action(ActionEntry action, String description) {
        return new OpenYsmWheelNode(Type.ACTION, action == null ? "" : action.getAnimationName(),
                action == null ? "" : action.getDisplayName(), description, action, null);
    }

    public static OpenYsmWheelNode category(String key, String label) {
        return new OpenYsmWheelNode(Type.CATEGORY, key, label, "", null, null);
    }

    public static OpenYsmWheelNode config(OpenYsmAnimationSet.ExtraActionButton button) {
        return new OpenYsmWheelNode(Type.CONFIG_GROUP, button == null ? "" : button.getId(),
                button == null ? "" : button.getName(), button == null ? "" : button.getDescription(), null, button);
    }

    public static OpenYsmWheelNode back() {
        return new OpenYsmWheelNode(Type.RETURN, "#return", "Back", "", null, null);
    }

    public static OpenYsmWheelNode stop() {
        return new OpenYsmWheelNode(Type.STOP, "#stop", "Stop", "", null, null);
    }

    public Type getType() {
        return this.type;
    }

    public String getKey() {
        return this.key;
    }

    public String getLabel() {
        return this.label;
    }

    public String getDescription() {
        return this.description;
    }

    public ActionEntry getAction() {
        return this.action;
    }

    public OpenYsmAnimationSet.ExtraActionButton getButton() {
        return this.button;
    }
}
