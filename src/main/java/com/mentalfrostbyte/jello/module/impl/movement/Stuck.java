package com.mentalfrostbyte.jello.module.impl.movement;

import com.mentalfrostbyte.jello.event.impl.player.movement.EventMoveMath;
import com.mentalfrostbyte.jello.module.Module;
import com.mentalfrostbyte.jello.module.data.ModuleCategory;
import com.mentalfrostbyte.jello.module.settings.impl.ModeSetting;
import team.sdhq.eventBus.annotations.EventTarget;

public class Stuck extends Module {
    private final ModeSetting stuckmode = new ModeSetting("Mode","choose StuckMode","MoveMathLegit","MoveMathLegit");
    public Stuck() {
        super(ModuleCategory.MOVEMENT,"Stuck","Stuck you move");
        registerSetting(stuckmode);
    }

    @Override
    public void onEnable() {
    }

    @EventTarget
    public void onMoveMath(EventMoveMath event) {
        if (stuckmode.getCurrentValue().equals("MoveMathLegit") && mc.player != null && mc.player.getPositionUpdateTicks() < 18 && this.isEnabled()) {
            //别问我为什么这个mc.player.getPositionUpdateTicks() < 18的是18不是20 肯定是onUpdateWalkingPlayer被别人写炸了
            event.cancelled = true;
        }
    }

}
