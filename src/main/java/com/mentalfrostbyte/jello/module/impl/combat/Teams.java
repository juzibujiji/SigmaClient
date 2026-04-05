package com.mentalfrostbyte.jello.module.impl.combat;

import com.mentalfrostbyte.jello.module.Module;
import com.mentalfrostbyte.jello.module.data.ModuleCategory;
import com.mentalfrostbyte.jello.module.settings.impl.BooleanSetting;

public class Teams extends Module {
    public Teams() {
        super(ModuleCategory.COMBAT, "Teams", "Avoid combat module to target your team mates");
        this.registerSetting(new BooleanSetting("RemoveHitbox","Remove team Hitbox", true));
    }
}