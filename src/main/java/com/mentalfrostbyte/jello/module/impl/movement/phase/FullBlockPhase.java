package com.mentalfrostbyte.jello.module.impl.movement.phase;


import com.mentalfrostbyte.jello.event.impl.game.world.EventPushBlock;
import com.mentalfrostbyte.jello.event.impl.player.movement.EventMove;
import com.mentalfrostbyte.jello.event.impl.player.movement.EventMotion;
import com.mentalfrostbyte.jello.module.Module;
import com.mentalfrostbyte.jello.module.data.ModuleCategory;
import com.mentalfrostbyte.jello.util.game.player.PlayerUtil;
import com.mentalfrostbyte.jello.util.game.player.MovementUtil;
import team.sdhq.eventBus.annotations.EventTarget;

public class FullBlockPhase extends Module {
    public FullBlockPhase() {
        super(ModuleCategory.MOVEMENT, "FullBlock", "Basic phase");
    }

    @EventTarget
    public void EventUpdate(EventMotion event) {
        if (this.isEnabled() && PlayerUtil.isCollidingWithSurroundingBlocks()) {
            event.setMoving(true);
        }
    }

    @EventTarget
    public void EventMove(EventMove event) {
        if (this.isEnabled()) {
            if (!PlayerUtil.isCollidingWithSurroundingBlocks()) {
                if (mc.player.collidedHorizontally) {
                    MovementUtil.setMotion(event, 0.0);
                    MovementUtil.movePlayerInDirection(1.1920931E-8);
                }
            } else {
                MovementUtil.movePlayerInDirection(0.617);
            }
        }
    }

    @EventTarget
    public void EventPushBlock(EventPushBlock event) {
        if (this.isEnabled()) {
            event.cancelled = true;
        }
    }
}
