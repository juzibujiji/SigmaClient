package com.mentalfrostbyte.jello.module.impl.movement.crtmov;

import com.mentalfrostbyte.jello.event.impl.player.EventLook;
import com.mentalfrostbyte.jello.event.impl.player.movement.EventJump;
import com.mentalfrostbyte.jello.event.impl.player.movement.EventMotion;
import com.mentalfrostbyte.jello.event.impl.player.movement.EventMoveFlying;
import com.mentalfrostbyte.jello.event.impl.player.movement.EventMoveInput;
import com.mentalfrostbyte.jello.util.game.player.MovementUtil;
import com.mentalfrostbyte.jello.util.game.player.rotation.RotationCore;
import team.sdhq.eventBus.annotations.EventTarget;

/**
 * Pass-through corrector: the published rotation goes onto the packet unchanged and movement
 * follows it directly.
 *
 * <p>The handlers are the original ones, unchanged apart from gating. They used to run
 * alongside an identical copy on {@code managers.RotationManager}, so every correction was
 * applied twice per tick; {@code silentStrafe} in particular derives the intended heading from
 * {@code mc.player.rotationYaw} plus the current forward/strafe pair, so the second pass read
 * the already-corrected pair as fresh input and compounded the error. That copy is retired and
 * this is now the only application.
 */
public class Sigma extends CorrectorMode {

    public Sigma() {
        super("Sigma", "Corrector");
    }

    @EventTarget
    public void onPre(EventMotion event) {
        if (!event.isPre() || !this.isActiveMode()) {
            return;
        }

        if (this.hasRotation()) {
            event.setYaw(RotationCore.currentYaw);
            event.setPitch(RotationCore.currentPitch);
        }

        RotationCore.lastYaw = event.getYaw();
        RotationCore.lastPitch = event.getPitch();
    }

    @EventTarget
    public void onInput(EventMoveInput event) {
        if (this.canCorrect()) {
            MovementUtil.silentStrafe(event, RotationCore.currentYaw);
        }
    }

    @EventTarget
    public void onJump(EventJump event) {
        if (this.canCorrect()) {
            event.yaw = RotationCore.currentYaw;
        }
    }

    @EventTarget
    public void onStrafe(EventMoveFlying event) {
        if (this.canCorrect()) {
            event.yaw = RotationCore.currentYaw;
        }
    }

    @EventTarget
    public void onLook(EventLook event) {
        if (this.canCorrect() && this.fixLook()) {
            event.yaw = RotationCore.currentYaw;
            event.pitch = RotationCore.currentPitch;
        }
    }
}
