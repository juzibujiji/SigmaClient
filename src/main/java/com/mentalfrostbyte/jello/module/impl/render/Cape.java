package com.mentalfrostbyte.jello.module.impl.render;

import com.mentalfrostbyte.jello.event.impl.game.render.EventRenderCapeLayer;
import com.mentalfrostbyte.jello.event.impl.player.EventUpdate;
import com.mentalfrostbyte.jello.module.Module;
import com.mentalfrostbyte.jello.module.data.ModuleCategory;
import com.mentalfrostbyte.jello.module.settings.impl.ModeSetting;
import com.mentalfrostbyte.jello.module.settings.impl.NumberSetting;
import net.minecraft.util.ResourceLocation;
import team.sdhq.eventBus.annotations.EventTarget;

public class Cape extends Module {
    public Cape() {
        super(ModuleCategory.RENDER, "Cape", "Enable and customize a custom cape.");
        registerSetting(new ModeSetting("Cape", "Select a cape design.", 0, "Minecraft", "Monkey", "Spade"));
        registerSetting(new NumberSetting<>("Movement Factor", "Adjusts cape motion sensitivity.", 1, 1, 3.0F, 0.1f));
    }

    @EventTarget
    public void onUpdate(EventUpdate event) {
        mc.player.setLocationOfCape(new ResourceLocation("textures/entity/capes/" + getStringSettingValueByName("Cape") + ".png"));
    }

    @EventTarget
    public void onRenderCapeLayer(EventRenderCapeLayer event) {
        event.factor1 = getNumberValueBySettingName("Movement Factor") * 100.0F;
        event.factor2 = 20.0F * getNumberValueBySettingName("Movement Factor");
    }

    @Override
    public void onDisable() {
        super.onDisable();
        if (mc.player != null)
            mc.player.setLocationOfCape(null);
    }
}