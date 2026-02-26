package com.mentalfrostbyte.jello.module.impl.render;

import com.mentalfrostbyte.jello.event.impl.game.render.EventRender2D;
import com.mentalfrostbyte.jello.module.Module;
import com.mentalfrostbyte.jello.module.data.ModuleCategory;
import net.minecraft.potion.Effects;
import team.sdhq.eventBus.annotations.EventTarget;

public class AntiBlind extends Module {
    public AntiBlind() {
        super(ModuleCategory.RENDER, "AntiBlind", "Disables bad visual potion effects");
    }

    @EventTarget
    public void onRender2D(EventRender2D event) {
        if (mc.player != null) {
            if (mc.player.isPotionActive(Effects.BLINDNESS)) {
                mc.player.removePotionEffect(Effects.BLINDNESS);
            }
            if (mc.player.isPotionActive(Effects.NAUSEA)) {
                mc.player.removePotionEffect(Effects.NAUSEA);
            }
        }
    }
}
