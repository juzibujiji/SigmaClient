package com.mentalfrostbyte.jello.gui.impl.jello.ingame.buttons.keybind;

import com.mentalfrostbyte.Client;
import com.mentalfrostbyte.jello.managers.GuiManager;
import com.mentalfrostbyte.jello.module.Module;
import net.minecraft.client.gui.screen.Screen;

public class BindTarget {
    public Module module;
    public Class<? extends Screen> screen;

    public BindTarget(Module module) {
        this.module = module;
    }

    public BindTarget(Class<? extends Screen> screen) {
        this.screen = screen;
    }

    public String getDisplayName() {
        return this.module == null ? GuiManager.screenToScreenName.get(this.screen) : this.module.getName();
    }

    public String getCategoryName() {
        return this.module == null ? "Screen" : this.module.getCategoryBasedOnMode().name();
    }

    public void bind(int key) {
        if (this.module == null) {
            Client.getInstance().moduleManager.getKeyManager().bindScreen(key, this.screen);
        } else {
            Client.getInstance().moduleManager.getKeyManager().bindModule(key, this.module);
        }
    }

    public int getKeybind() {
        return this.module == null
                ? Client.getInstance().moduleManager.getKeyManager().getKeybindFor(this.screen)
                : Client.getInstance().moduleManager.getKeyManager().getKeybindFor(this.module);
    }
}
