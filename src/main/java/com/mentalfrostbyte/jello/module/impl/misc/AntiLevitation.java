package com.mentalfrostbyte.jello.module.impl.misc;

import com.mentalfrostbyte.jello.event.impl.player.EventUpdate;
import com.mentalfrostbyte.jello.module.Module;
import com.mentalfrostbyte.jello.module.data.ModuleCategory;
import net.minecraft.potion.Effects;
import team.sdhq.eventBus.annotations.EventTarget;

public class AntiLevitation extends Module {
    public AntiLevitation() {
        super(ModuleCategory.MISC, "AntiLevitation", "Removes levitation effects");
    }

    @EventTarget
    public void onTick(EventUpdate event) {
        if (this.isEnabled()) {
            mc.player.removeActivePotionEffect(Effects.LEVITATION);
        }
    }
}
