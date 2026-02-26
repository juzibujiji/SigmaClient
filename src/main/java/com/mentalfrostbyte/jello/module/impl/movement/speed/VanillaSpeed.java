package com.mentalfrostbyte.jello.module.impl.movement.speed;

import com.mentalfrostbyte.jello.event.impl.player.movement.EventMove;
import com.mentalfrostbyte.jello.module.Module;
import com.mentalfrostbyte.jello.module.data.ModuleCategory;
import com.mentalfrostbyte.jello.module.settings.impl.BooleanSetting;
import com.mentalfrostbyte.jello.module.settings.impl.NumberSetting;
import com.mentalfrostbyte.jello.util.game.player.MovementUtil;
import team.sdhq.eventBus.annotations.EventTarget;

public class VanillaSpeed extends Module {
    public VanillaSpeed() {
        super(ModuleCategory.MOVEMENT, "Vanilla", "Vanilla speed");
        this.registerSetting(new BooleanSetting("AutoJump","AutoJumps",false));
        this.registerSetting(new NumberSetting("Speed", "Speed value", 4.0F, 1.0F, 10.0F, 0.1F));

    }

    @EventTarget
    public void EventMove(EventMove event) {
        if (this.isEnabled()) {
            double speedInput = MovementUtil.getSmartSpeed() * (double) this.getNumberValueBySettingName("Speed");
            MovementUtil.setMotion(event, speedInput);
            if(this.getBooleanValueFromSettingName("AutoJump"))
                mc.gameSettings.keyBindJump.setPressed(true);
        }
    }
}
