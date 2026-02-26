package com.mentalfrostbyte.jello.module.impl.player;

import com.mentalfrostbyte.jello.module.data.ModuleCategory;
import com.mentalfrostbyte.jello.module.data.ModuleWithModuleSettings;
import com.mentalfrostbyte.jello.module.impl.player.nofall.*;

public class NoFall extends ModuleWithModuleSettings {
    public NoFall() {
        super(
                ModuleCategory.PLAYER, "NoFall",
                "Avoid you from getting fall damages",
                "Mode", new VanillaNoFall(), new CancelNoFall(),
                new HypixelNoFall(), new Hypixel2NoFall(),
                new AACNoFall(), new NCPSpigotNoFall(),
                new OldHypixelNoFall(), new VanillaLegitNoFall(),
                new VerusNoFall(), new VulcanPhaseNoFall()
        );
    }
}