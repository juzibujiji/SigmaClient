package com.mentalfrostbyte.jello.module.impl.render;

import com.mentalfrostbyte.jello.module.Module;
import com.mentalfrostbyte.jello.module.data.ModuleCategory;

import com.mentalfrostbyte.jello.module.settings.impl.BooleanSetting;
import com.mentalfrostbyte.jello.module.settings.impl.NumberSetting;

public class ItemPhysics extends Module {
    public ItemPhysics() {
        super(ModuleCategory.RENDER, "ItemPhysics", "Better physics for items");
        this.registerSetting(new BooleanSetting("Disable Floating", "Disable the item floating on drop.", false));
        this.registerSetting(new BooleanSetting("Enable Gravity", "The item rotates on the air.", false));
        this.registerSetting(new NumberSetting<Float>("Gravity Value", "How fast the items rotates on fall.", 1, 1, 3, 0.05f));
        this.registerSetting(new BooleanSetting("Loaf Always", "Always render the items sided.", false));
    }
}
