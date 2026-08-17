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
 * Rise-style movement correction (MovementFix.NORMAL).
 *
 * <p>Ported from Rise 6.9.5 RotationComponent / MoveUtil#fixMovement:
 * the published rotation goes onto the packet unchanged, movement input is remapped to
 * the silent yaw, and jump / moveRelative / look are corrected to the same rotation.
 */
public class Rise extends CorrectorMode {

    public Rise() {
        super("Rise", "Rise movement correction");
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
