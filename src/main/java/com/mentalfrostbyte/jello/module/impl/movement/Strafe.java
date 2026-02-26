package com.mentalfrostbyte.jello.module.impl.movement;

import com.mentalfrostbyte.jello.event.impl.player.movement.EventMove;
import com.mentalfrostbyte.jello.module.Module;
import com.mentalfrostbyte.jello.module.data.ModuleCategory;
import com.mentalfrostbyte.jello.util.game.player.MovementUtil;
import team.sdhq.eventBus.annotations.EventTarget;

public class Strafe extends Module {
    private double currentSpeed;

    public Strafe() {
        super(ModuleCategory.MOVEMENT, "Strafe", "Strafe in mid air");
    }

    @EventTarget
    public void onMove(EventMove event) {
        if (!this.isEnabled()) return;
        double movementMagnitude = Math.sqrt(event.getX() * event.getX() + event.getZ() * event.getZ());
        if (!MovementUtil.isMoving()) {
            movementMagnitude = 0.0;
        }
        if (!(movementMagnitude > currentSpeed + 0.1F)) {
            MovementUtil.strafe(movementMagnitude);
        }
        currentSpeed = movementMagnitude;
    }
}
