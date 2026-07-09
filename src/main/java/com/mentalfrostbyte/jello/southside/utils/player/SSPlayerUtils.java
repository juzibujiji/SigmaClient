/*
 * Ported from OpenSSNGScaffoldAndClutch (https://github.com/zyyzs/OpenSSNGScaffoldAndClutch)
 * Original: dev.southside.utils.player.PlayerUtils (referenced by upstream sources; not
 * published in the upstream repo — reconstructed from its observed API surface:
 * onGroundTicks / offGroundTicks tick counters, isMoving(), getMoveSpeedEffectAmplifier(),
 * blockRelativeToPlayer(x,y,z), lastPitchDiff / lastPlacePitchDiff).
 *
 * update() is driven once per client tick from SSRotationUtils.choose() (upstream updates
 * these counters inside its own client's tick pipeline). onMotionSent(...) is driven from
 * SSRotationManager's outbound EventMotion handler and tracks the absolute pitch delta
 * between consecutive rotations sent to the server — Scaffold's DuplicateRotPlace logic
 * compares lastPitchDiff against lastPlacePitchDiff to avoid byte-identical rotation deltas
 * on consecutive placements.
 */
package com.mentalfrostbyte.jello.southside.utils.player;

import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.potion.Effects;
import net.minecraft.util.math.BlockPos;

public final class SSPlayerUtils {
    public static int onGroundTicks;
    public static int offGroundTicks;
    public static double lastPitchDiff;
    public static double lastPlacePitchDiff;

    private static float prevSentPitch = Float.NaN;
    private static final Minecraft mc = Minecraft.getInstance();

    private SSPlayerUtils() {
    }

    /** Called once per client tick from SSRotationUtils.choose(). */
    public static void update() {
        if (mc.player == null) {
            onGroundTicks = 0;
            offGroundTicks = 0;
            prevSentPitch = Float.NaN;
            return;
        }

        if (mc.player.isOnGround()) {
            onGroundTicks++;
            offGroundTicks = 0;
        } else {
            offGroundTicks++;
            onGroundTicks = 0;
        }
    }

    /** Called from SSRotationManager after the outbound rotation for this tick is final. */
    public static void onMotionSent(float pitch) {
        if (!Float.isNaN(prevSentPitch)) {
            lastPitchDiff = Math.abs(pitch - prevSentPitch);
        }
        prevSentPitch = pitch;
    }

    public static boolean isMoving() {
        if (mc.player == null || mc.player.movementInput == null) {
            return false;
        }
        return mc.player.movementInput.moveForward != 0.0F || mc.player.movementInput.moveStrafe != 0.0F;
    }

    /** Speed potion amplifier used for the Keep FoV formula (0 when no speed effect). */
    public static int getMoveSpeedEffectAmplifier() {
        if (mc.player == null || !mc.player.isPotionActive(Effects.SPEED)) {
            return 0;
        }
        return mc.player.getActivePotionEffect(Effects.SPEED).getAmplifier() + 1;
    }

    public static Block blockRelativeToPlayer(int x, int y, int z) {
        BlockPos pos = new BlockPos(mc.player.getPosX(), mc.player.getPosY(), mc.player.getPosZ()).add(x, y, z);
        return mc.world.getBlockState(pos).getBlock();
    }
}
