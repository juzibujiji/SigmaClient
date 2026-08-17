package com.mentalfrostbyte.jello.module.impl.movement.crtmov;

import com.mentalfrostbyte.Client;
import com.mentalfrostbyte.jello.event.impl.player.EventLook;
import com.mentalfrostbyte.jello.event.impl.player.movement.EventJump;
import com.mentalfrostbyte.jello.event.impl.player.movement.EventMotion;
import com.mentalfrostbyte.jello.event.impl.player.movement.EventMoveButton;
import com.mentalfrostbyte.jello.event.impl.player.movement.EventMoveFlying;
import com.mentalfrostbyte.jello.module.Module;
import com.mentalfrostbyte.jello.module.data.ModuleCategory;
import com.mentalfrostbyte.jello.module.impl.movement.CorrectMovement;
import com.mentalfrostbyte.jello.util.game.player.rotation.RotationCore;
import net.minecraft.util.math.MathHelper;
import team.sdhq.eventBus.annotations.EventTarget;
import team.sdhq.eventBus.annotations.priority.HighestPriority;
import team.sdhq.eventBus.annotations.priority.LowestPriority;

/**
 * OpenVape-style movement correction.
 *
 * Ported from:
 * gg.vape.rotation.RotationManager
 *
 * This is NOT MovementUtil.silentStrafe().
 *
 * OpenVape works by remapping W/A/S/D according to the difference between
 * the player's intended movement direction and the silent/managed rotation.
 *
 * Sigma's EventMoveButton is fired after KeyBinding states are sampled but
 * before they are converted into moveForward/moveStrafe, so it is the
 * appropriate equivalent hook.
 */
public class Vape extends Module {

    /**
     * OpenVape:
     *
     * private static final float DEFAULT_MOVEMENT_THRESHOLD = 0.4f;
     *
     * The 0.075 branch in OpenVape belongs to PlayerMovementTaskManager,
     * not SilentAura's normal player movement, so normal correction uses 0.4.
     */
    private static final float MOVEMENT_THRESHOLD = 0.4F;

    public Vape() {
        super(
                ModuleCategory.MOVEMENT,
                "Vape",
                "OpenVape movement correction"
        );
    }

    private CorrectMovement getCorrectMovement() {
        return (CorrectMovement) Client.getInstance()
                .moduleManager
                .getModuleByClass(CorrectMovement.class);
    }

    private boolean isActiveMode() {
        CorrectMovement correctMovement = this.getCorrectMovement();

        return correctMovement != null
                && correctMovement.isEnabled()
                && correctMovement.getModWithTypeSetToName() instanceof Vape;
    }

    private boolean hasRotation() {
        return !Float.isNaN(RotationCore.currentYaw)
                && !Float.isNaN(RotationCore.currentPitch);
    }

    /*
     * ============================================================
     * OpenVape RotationManager.adjustMovementYaw
     * ============================================================
     *
     * IMPORTANT:
     *
     * OpenVape's decompiled RotationManager has the left/right wrapper
     * bindings named backwards:
     *
     *     "left"  -> actual right key
     *     "right" -> actual left key
     *
     * Therefore this method is deliberately kept in OpenVape's original
     * orientation, and EventMoveButton swaps left/right when calling it.
     *
     * Do NOT "correct" the signs here without also removing those swaps.
     */

    private static float adjustMovementYaw(
            float yaw,
            boolean forward,
            boolean vapeLeft,
            boolean vapeRight,
            boolean back
    ) {
        float adjustedYaw = yaw;

        if (forward && vapeLeft) {
            adjustedYaw += 45.0F;
        } else if (back && vapeLeft) {
            adjustedYaw += 135.0F;
        } else if (vapeLeft) {
            adjustedYaw += 90.0F;
        } else if (forward && vapeRight) {
            adjustedYaw -= 45.0F;
        } else if (back && vapeRight) {
            adjustedYaw -= 135.0F;
        } else if (vapeRight) {
            adjustedYaw -= 90.0F;
        } else if (back) {
            adjustedYaw += 180.0F;
        }

        return adjustedYaw;
    }

    /*
     * ============================================================
     * Aim correction
     * ============================================================
     *
     * OpenVape temporarily applies managedYaw/managedPitch while its
     * rotation controller is active.
     *
     * In Sigma the equivalent managed rotation is:
     *
     *     RotationCore.currentYaw
     *     RotationCore.currentPitch
     *
     * Since your KillAura now delegates the final correction to the
     * CorrectMovement mode, Vape writes it here.
     */

    @EventTarget
    @LowestPriority
    public void onPre(EventMotion event) {
        if (!event.isPre()
                || mc.player == null
                || !this.isActiveMode()
                || !this.hasRotation()) {
            return;
        }

        event.setYaw(RotationCore.currentYaw);
        event.setPitch(RotationCore.currentPitch);

        /*
         * Keep the actually applied rotation available to raytrace /
         * combat code using RotationCore.last*.
         */
        RotationCore.lastYaw = event.getYaw();
        RotationCore.lastPitch = event.getPitch();
    }

    /*
     * ============================================================
     * OpenVape W/A/S/D movement correction
     * ============================================================
     *
     * Sigma flow:
     *
     * KeyBinding.isKeyDown()
     *         ↓
     * EventMoveButton       <-- we remap HERE
     *         ↓
     * moveForward/moveStrafe
     *         ↓
     * player movement
     *
     * This means there is no EventMoveInput vector correction involved.
     */

    @EventTarget
    @HighestPriority
    public void onMoveButton(EventMoveButton event) {
        if (mc.player == null
                || !this.isActiveMode()
                || !this.hasRotation()) {
            return;
        }

        /*
         * OpenVape only remaps movement when directional input exists.
         *
         * Jump and sneak are intentionally left untouched.
         */
        if (!event.forward
                && !event.back
                && !event.left
                && !event.right) {
            return;
        }

        /*
         * OpenVape AdaptiveRotationController.getReferenceYaw()
         * defaults to the visible/reference player's yaw.
         */
        float referenceYaw = mc.player.rotationYaw;

        /*
         * OpenVape RotationManager's decompiled binding names are reversed:
         *
         *   vapeLeft  = actual RIGHT
         *   vapeRight = actual LEFT
         *
         * Therefore:
         *
         *   event.right -> vapeLeft
         *   event.left  -> vapeRight
         */
        float movementYaw = adjustMovementYaw(
                referenceYaw,
                event.forward,
                event.right,
                event.left,
                event.back
        );

        /*
         * OpenVape normal movement correction:
         *
         * appliedPlayerYaw = managedYaw;
         *
         * managedYaw maps to Sigma's final silent yaw.
         */
        float appliedPlayerYaw = RotationCore.currentYaw;

        /*
         * OpenVape:
         *
         * relativeMovementYaw =
         *     wrapAngleTo180(
         *         wrapAngleTo180(appliedPlayerYaw) - movementYaw
         *     );
         */
        float relativeMovementYaw = MathHelper.wrapDegrees(
                MathHelper.wrapDegrees(appliedPlayerYaw) - movementYaw
        );

        float relativeMovementRadians =
                relativeMovementYaw * ((float) Math.PI / 180.0F);

        /*
         * OpenVape original projection.
         */
        float forwardProjection =
                (float) Math.cos(relativeMovementRadians);

        float leftProjection =
                (float) (-Math.sin(relativeMovementRadians));

        /*
         * Keep OpenVape's original logical names first.
         *
         * Remember:
         * vapePressLeft  is written to OpenVape's wrapper that is
         *                 actually the RIGHT key.
         *
         * vapePressRight is written to the wrapper that is
         *                 actually the LEFT key.
         */
        boolean vapePressForward =
                (double) forwardProjection >= MOVEMENT_THRESHOLD;

        boolean vapePressLeft =
                (double) leftProjection >= MOVEMENT_THRESHOLD;

        boolean vapePressRight =
                (double) leftProjection <= -MOVEMENT_THRESHOLD;

        boolean vapePressBack =
                (double) forwardProjection <= -MOVEMENT_THRESHOLD;

        /*
         * OpenVape effectively does:
         *
         * forwardKey.setPressed(vapePressForward);
         * "leftKey".setPressed(vapePressLeft);   // actually RIGHT
         * "rightKey".setPressed(vapePressRight); // actually LEFT
         * backKey.setPressed(vapePressBack);
         *
         * Sigma has correctly named EventMoveButton fields,
         * so swap them back here.
         */
        event.forward = vapePressForward;
        event.right = vapePressLeft;
        event.left = vapePressRight;
        event.back = vapePressBack;
    }

    /*
     * ============================================================
     * Jump / moveRelative correction
     * ============================================================
     *
     * OpenVape temporarily changes the player's actual yaw to managedYaw
     * during the local player tick, so jump direction and moveRelative
     * naturally see managedYaw.
     *
     * Sigma exposes those operations as events instead, so these two
     * assignments are the mechanical equivalent.
     */

    @EventTarget
    @LowestPriority
    public void onJump(EventJump event) {
        if (mc.player == null
                || !this.isActiveMode()
                || !this.hasRotation()) {
            return;
        }

        event.yaw = RotationCore.currentYaw;
    }

    @EventTarget
    @LowestPriority
    public void onStrafe(EventMoveFlying event) {
        if (mc.player == null
                || !this.isActiveMode()
                || !this.hasRotation()) {
            return;
        }

        event.yaw = RotationCore.currentYaw;
    }

    /*
     * ============================================================
     * Mouse-over / looking correction
     * ============================================================
     *
     * OpenVape applies managed rotation during mouse-over/raytrace.
     *
     * Your Sigma CorrectMovement exposes that behavior through FixLook.
     */

    @EventTarget
    @LowestPriority
    public void onLook(EventLook event) {
        if (mc.player == null
                || !this.isActiveMode()
                || !this.hasRotation()) {
            return;
        }

        CorrectMovement correctMovement = this.getCorrectMovement();

        if (correctMovement != null
                && correctMovement.getBooleanValueFromSettingName("FixLook")) {
            event.yaw = RotationCore.currentYaw;
            event.pitch = RotationCore.currentPitch;
        }
    }
}
