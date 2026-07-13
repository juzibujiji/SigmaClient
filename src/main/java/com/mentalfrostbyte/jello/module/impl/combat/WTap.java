package com.mentalfrostbyte.jello.module.impl.combat;

import com.mentalfrostbyte.jello.module.data.ModuleCategory;
import com.mentalfrostbyte.jello.module.data.ModuleWithModuleSettings;
import com.mentalfrostbyte.jello.module.impl.combat.wtap.*;

public class WTap extends ModuleWithModuleSettings {
    public WTap() {
        super(ModuleCategory.COMBAT, "WTap", "Resets sprint with human-like timing to increase dealt knockback",
                new NormalWTap(), new LegitWTap());
    }
}
