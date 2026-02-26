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

    public void method13725(int var1, Module var2) {
        this.method13727(var2);
        Bound var5 = new Bound(var1, var2);
        this.boundables.add(var5);
    }

    public void method13726(int var1, Class<? extends Screen> var2) {
        this.method13727(var2);
        Bound var5 = new Bound(var1, var2);
        this.boundables.add(var5);
    }

    public void method13727(Object var1) {
        this.boundables.removeIf(o -> o.getTarget().equals(var1));
    }

    public int getKeybindFor(Class<? extends Screen> screen) {
        for (Bound var5 : this.boundables) {
            if (var5.getKeybindTypes() == KeybindTypes.SCREEN && var5.getScreenTarget() == screen) {
                return var5.getKeybind();
            }
        }

        return -1;
    }

    public int method13729(Module var1) {
        for (Bound var5 : this.boundables) {
            if (var5.getKeybindTypes() == KeybindTypes.MODULE && var5.getModuleTarget() == var1) {
                return var5.getKeybind();
            }
        }

        return -1;
    }

    public void getKeybindsJSONObject(JsonObject obj) throws JsonParseException {
        JsonArray keybinds = new JsonArray();

        for (Bound var6 : this.boundables) {
            if (var6.getKeybind() != -1 && var6.getKeybind() != 0) {
                keybinds.add(var6.getKeybindData());
            }
        }

        obj.add("keybinds", keybinds);
    }

    public void method13732(JsonObject pKeybinds) throws JsonParseException {
        if (pKeybinds.has("keybinds")) {
            JsonArray keybindsArr = pKeybinds.getAsJsonArray("keybinds");

            for (int i = 0; i < keybindsArr.size(); i++) {
                JsonObject boundJson = keybindsArr.get(i).getAsJsonObject();
                Bound var7 = new Bound(boundJson);
                if (var7.hasTarget()) {
                    this.boundables.add(var7);
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
