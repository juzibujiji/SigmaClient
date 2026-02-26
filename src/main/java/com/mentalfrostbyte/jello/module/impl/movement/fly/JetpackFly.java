package com.mentalfrostbyte.jello.module.impl.movement.fly;

import team.sdhq.eventBus.annotations.EventTarget;
import com.mentalfrostbyte.jello.event.impl.player.EventUpdate;
import com.mentalfrostbyte.jello.module.Module;
import com.mentalfrostbyte.jello.module.data.ModuleCategory;

public class JetpackFly extends Module {
    public JetpackFly() {
        super(ModuleCategory.MOVEMENT, "Jetpack", "A jetpack type fly");
    }

    @EventTarget
    public void onTick(EventUpdate event) {
        if (this.isEnabled()) {
            if (mc.player.isJumping) {
                mc.player.jump();
            }
        }
    }
}
