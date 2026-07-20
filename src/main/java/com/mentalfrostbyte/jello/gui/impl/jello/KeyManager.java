package com.mentalfrostbyte.jello.gui.impl.jello;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.mentalfrostbyte.jello.gui.impl.jello.ingame.buttons.keybind.Bound;
import com.mentalfrostbyte.jello.gui.impl.jello.ingame.buttons.keybind.KeybindTypes;
import com.mentalfrostbyte.jello.gui.impl.jello.ingame.holders.ClickGuiHolder;
import com.mentalfrostbyte.jello.module.Module;
import com.mentalfrostbyte.jello.util.system.FileUtil;
import net.minecraft.client.gui.screen.Screen;
import team.sdhq.eventBus.EventBus;

import java.util.*;

public class KeyManager {
    private final LinkedHashSet<Bound> boundables = new LinkedHashSet<>();

    public KeyManager() {
        EventBus.register(this);
        if (FileUtil.freshConfig) {
            this.boundables.add(new Bound(344, ClickGuiHolder.class));
        }
    }

    public void bindModule(int key, Module module) {
        this.removeBind(module);
        Bound bound = new Bound(key, module);
        this.boundables.add(bound);
    }

    public void bindScreen(int key, Class<? extends Screen> screen) {
        this.removeBind(screen);
        Bound bound = new Bound(key, screen);
        this.boundables.add(bound);
    }

    public void removeBind(Object target) {
        this.boundables.removeIf(o -> o.getTarget().equals(target));
    }

    public int getKeybindFor(Class<? extends Screen> screen) {
        for (Bound bound : this.boundables) {
            if (bound.getKeybindTypes() == KeybindTypes.SCREEN && bound.getScreenTarget() == screen) {
                return bound.getKeybind();
            }
        }

        return -1;
    }

    public int getKeybindFor(Module module) {
        for (Bound bound : this.boundables) {
            if (bound.getKeybindTypes() == KeybindTypes.MODULE && bound.getModuleTarget() == module) {
                return bound.getKeybind();
            }
        }

        return -1;
    }

    public void getKeybindsJSONObject(JsonObject obj) throws JsonParseException {
        JsonArray keybinds = new JsonArray();

        for (Bound bound : this.boundables) {
            if (bound.getKeybind() != -1 && bound.getKeybind() != 0) {
                keybinds.add(bound.getKeybindData());
            }
        }

        obj.add("keybinds", keybinds);
    }

    public void loadKeybinds(JsonObject pKeybinds) throws JsonParseException {
        if (pKeybinds.has("keybinds")) {
            JsonArray keybindsArr = pKeybinds.getAsJsonArray("keybinds");

            for (int i = 0; i < keybindsArr.size(); i++) {
                JsonObject boundJson = keybindsArr.get(i).getAsJsonObject();
                Bound bound = new Bound(boundJson);
                if (bound.hasTarget()) {
                    this.boundables.add(bound);
                }
            }
        }
    }

    public List<Bound> getBindedObjects(int key) {
        List<Bound> boundObjects = new ArrayList<>();
        if (key != -1) {
            for (Bound boundable : this.boundables) {
                if (boundable.getKeybind() == key) {
                    boundObjects.add(boundable);
                }
            }

            return boundObjects;
        } else {
            return null;
        }
    }
}
