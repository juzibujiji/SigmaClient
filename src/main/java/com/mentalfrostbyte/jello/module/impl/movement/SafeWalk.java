package com.mentalfrostbyte.jello.module.impl.movement;

import com.mentalfrostbyte.Client;
import com.mentalfrostbyte.jello.event.impl.player.movement.EventSafeWalk;
import com.mentalfrostbyte.jello.module.Module;
import com.mentalfrostbyte.jello.module.data.ModuleCategory;
import team.sdhq.eventBus.annotations.EventTarget;

public class SafeWalk extends Module {
    public SafeWalk() {
        super(ModuleCategory.MOVEMENT, "SafeWalk", "Doesn't let you run off edges");
    }

    @EventTarget
    public void onSafeWalk(EventSafeWalk event) {
        if (this.isEnabled() && mc != null && mc.player != null) {
            if (mc.player.isOnGround()) {
                Module BlockFly = Client.getInstance().moduleManager.getModuleByClass(BlockFly.class);
                if (!BlockFly.isEnabled()) {
                    event.setSafe(true);
                }
            }
        }
    }
}