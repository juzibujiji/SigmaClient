package com.mentalfrostbyte.jello.module.impl.player;

import com.mentalfrostbyte.jello.event.impl.player.movement.EventMotion;
import com.mentalfrostbyte.jello.module.Module;
import com.mentalfrostbyte.jello.module.data.ModuleCategory;
import com.mentalfrostbyte.jello.util.game.player.PlayerUtil;
import team.sdhq.eventBus.annotations.EventTarget;

public class Parkour extends Module {
    public Parkour() {
        super(ModuleCategory.PLAYER, "Parkour", "Automatically jumps at the edge of blocks");
    }

    @EventTarget
    public void onMotion(EventMotion event) {
        if (this.isEnabled()) {
            if (mc.player.isOnGround()) {
                if (!PlayerUtil.isPlayerInCollision()) {
                    mc.player.jump();
                }
            }
        }
    }
}
