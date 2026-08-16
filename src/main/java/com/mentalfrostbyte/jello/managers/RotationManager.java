package com.mentalfrostbyte.jello.managers;

/*
 * RETIRED — the whole class is commented out on purpose; see below before reviving it.
 *
 * This manager carried a packet/movement handler set that was a line-for-line duplicate of
 * the CorrectMovement modes in module/impl/movement/crtmov. Unlike those modes it was
 * registered by Manager#init at startup and never unregistered, and its handlers gated only
 * on CorrectMovement.isEnabled() — not on which mode was selected. So whenever a mode
 * declared the same handlers, every correction ran twice in the same tick.
 *
 * That is not harmless duplication. MovementUtil.silentStrafe derives the intended
 * world-space heading from mc.player.rotationYaw plus the current forward/strafe pair, then
 * picks the (forward, strafe) pair that best reproduces that heading from the corrected yaw.
 * Run it a second time and it reads the already-corrected pair as fresh user input, so the
 * error compounds instead of converging.
 *
 * Correction now lives entirely in the mode that is selected — Sigma, LiquidBounce or Zen,
 * all extending crtmov/CorrectorMode, which gates on identity against
 * CorrectMovement.getModWithTypeSetToName(). Each mode owns the outgoing packet, the
 * RotationCore.last* record, movement input, jump direction, moveRelative and FixLook.
 *
 * The static publish API moved to RotationCore.setRotations, which is where the fields it
 * writes already live. All former callers (KillAura, BowAimbot, NaturalAimbotMode, Nuker,
 * AutoMLG, AutoPotion, ChestStealer, NoteblockPlayer and the BlockFly modes) call it there.
 *
 * Reviving this class means re-introducing the double application, unless it is gated on the
 * active mode first.
 */

// import com.mentalfrostbyte.Client;
// import com.mentalfrostbyte.jello.event.impl.player.EventLook;
// import com.mentalfrostbyte.jello.event.impl.player.movement.EventJump;
// import com.mentalfrostbyte.jello.event.impl.player.movement.EventMotion;
// import com.mentalfrostbyte.jello.event.impl.player.movement.EventMoveFlying;
// import com.mentalfrostbyte.jello.event.impl.player.movement.EventMoveInput;
// import com.mentalfrostbyte.jello.managers.data.Manager;
// import com.mentalfrostbyte.jello.module.impl.movement.CorrectMovement;
// import com.mentalfrostbyte.jello.util.game.MinecraftUtil;
// import com.mentalfrostbyte.jello.util.game.player.MovementUtil;
// import com.mentalfrostbyte.jello.util.game.player.rotation.RotationCore;
// import team.sdhq.eventBus.annotations.EventTarget;
//
// public class RotationManager extends Manager implements MinecraftUtil {
//     public static void setRotations(final float rotationYaw, final float rotationPitch) {
//         RotationCore.currentYaw = rotationYaw;
//         RotationCore.currentPitch = rotationPitch;
//     }
//
//     @EventTarget
//     public void onPre(EventMotion event) {
//         if (event.isPre()) {
//             if (!Float.isNaN(RotationCore.currentYaw) && !Float.isNaN(RotationCore.currentPitch)) {
//                 event.setYaw(RotationCore.currentYaw);
//                 event.setPitch(RotationCore.currentPitch);
//             }
//
//             RotationCore.lastYaw = event.getYaw();
//             RotationCore.lastPitch = event.getPitch();
//         }
//     }
//
//     @EventTarget
//     public void onInput(EventMoveInput event) {
//         if (Client.getInstance().moduleManager.getModuleByClass(CorrectMovement.class).isEnabled()) {
//             MovementUtil.silentStrafe(event, RotationCore.currentYaw);
//         }
//     }
//
//     @EventTarget
//     public void onJump(EventJump event) {
//         if (Client.getInstance().moduleManager.getModuleByClass(CorrectMovement.class).isEnabled()) {
//             event.yaw = RotationCore.currentYaw;
//         }
//     }
//
//     @EventTarget
//     public void onLook(EventLook event) {
//         if (Client.getInstance().moduleManager.getModuleByClass(CorrectMovement.class).getBooleanValueFromSettingName("FixLook")
//                 && Client.getInstance().moduleManager.getModuleByClass(CorrectMovement.class).isEnabled()) {
//             event.yaw = RotationCore.currentYaw;
//             event.pitch = RotationCore.currentPitch;
//         }
//     }
//
//     @EventTarget
//     public void onStrafe(EventMoveFlying event) {
//         if (Client.getInstance().moduleManager.getModuleByClass(CorrectMovement.class).isEnabled()) {
//             event.yaw = RotationCore.currentYaw;
//         }
//     }
// }
