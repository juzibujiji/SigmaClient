/*
 * Ported from OpenSSNGScaffoldAndClutch (https://github.com/zyyzs/OpenSSNGScaffoldAndClutch)
 * Reconstruction glue: the upstream client applies the rotation chosen by
 * dev.southside.utils.rotation.RotationUtils to its outbound movement packets (and performs
 * the MoveFixMode movement correction) in code that is not part of the published repo. This
 * class recreates that plumbing on SigmaClient's event bus:
 *
 *   - EventMotion (PRE, outbound movement packet): inject SSRotationUtils.rotation as the
 *     silent server rotation, then record the final pitch for SSPlayerUtils.lastPitchDiff.
 *   - EventMoveInput / EventJump / EventMoveFlying: MoveFixMode.Silent movement correction —
 *     movement input, jump direction and moveRelative all follow the fake yaw. The strafe
 *     correction itself delegates to the host MovementUtil.silentStrafe (upstream's StrafeFix
 *     module is unpublished — "实在没有" fallback).
 *   - EventMoveInput also applies SSMovementUtils.cancelMove (zeroed movement input while the
 *     scaffold self-save rotation needs to land).
 *
 * Handlers are @LowestPriority so they run after the host RotationManager and win the
 * outbound yaw/pitch write while a SouthSide rotation is active; when no SS rotation is set
 * every handler is a no-op, so nothing else in the client is affected.
 */
package com.mentalfrostbyte.jello.southside.manager;

import com.mentalfrostbyte.jello.event.impl.player.movement.EventJump;
import com.mentalfrostbyte.jello.event.impl.player.movement.EventMotion;
import com.mentalfrostbyte.jello.event.impl.player.movement.EventMoveFlying;
import com.mentalfrostbyte.jello.event.impl.player.movement.EventMoveInput;
import com.mentalfrostbyte.jello.southside.utils.player.SSMovementUtils;
import com.mentalfrostbyte.jello.southside.utils.player.SSPlayerUtils;
import com.mentalfrostbyte.jello.southside.utils.rotation.SSMoveFixMode;
import com.mentalfrostbyte.jello.southside.utils.rotation.SSRotationUtils;
import com.mentalfrostbyte.jello.southside.utils.types.SSRotation;
import com.mentalfrostbyte.jello.util.game.player.MovementUtil;
import team.sdhq.eventBus.EventBus;
import team.sdhq.eventBus.annotations.EventTarget;
import team.sdhq.eventBus.annotations.priority.LowestPriority;

public final class SSRotationManager {
    private static final SSRotationManager INSTANCE = new SSRotationManager();
    private static boolean registered;

    private SSRotationManager() {
    }

    /** Lazily registers the singleton on the event bus (called from SSRotationUtils.choose()). */
    public static void ensureRegistered() {
        if (!registered) {
            EventBus.register(INSTANCE);
            registered = true;
        }
    }

    private static boolean silentFixActive() {
        return SSRotationUtils.rotation != null && SSRotationUtils.strafeFixMode == SSMoveFixMode.Silent;
    }

    @EventTarget
    @LowestPriority
    public void onMotion(EventMotion event) {
        if (!event.isPre()) {
            return;
        }

        SSRotation rot = SSRotationUtils.rotation;
        if (rot != null) {
            event.setYaw(rot.yaw);
            event.setPitch(rot.pitch);
        }

        SSPlayerUtils.onMotionSent(event.getPitch());
    }

    @EventTarget
    @LowestPriority
    public void onInput(EventMoveInput event) {
        if (silentFixActive()) {
            MovementUtil.silentStrafe(event, SSRotationUtils.rotation.yaw);
        }

        if (SSMovementUtils.cancelMove) {
            event.forward = 0.0F;
            event.strafe = 0.0F;
        }
    }

    @EventTarget
    @LowestPriority
    public void onJump(EventJump event) {
        if (silentFixActive()) {
            event.yaw = SSRotationUtils.rotation.yaw;
        }
    }

    @EventTarget
    @LowestPriority
    public void onStrafe(EventMoveFlying event) {
        if (silentFixActive()) {
            event.yaw = SSRotationUtils.rotation.yaw;
        }
    }
}
