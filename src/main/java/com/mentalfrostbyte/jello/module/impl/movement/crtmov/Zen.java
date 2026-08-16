package com.mentalfrostbyte.jello.module.impl.movement.crtmov;

import com.mentalfrostbyte.jello.event.impl.player.EventLook;
import com.mentalfrostbyte.jello.event.impl.player.movement.EventJump;
import com.mentalfrostbyte.jello.event.impl.player.movement.EventMotion;
import com.mentalfrostbyte.jello.event.impl.player.movement.EventMoveFlying;
import com.mentalfrostbyte.jello.event.impl.player.movement.EventMoveInput;
import com.mentalfrostbyte.jello.module.settings.impl.BooleanSetting;
import com.mentalfrostbyte.jello.module.settings.impl.NumberSetting;
import com.mentalfrostbyte.jello.util.game.player.MovementUtil;
import com.mentalfrostbyte.jello.util.game.player.rotation.RotationCore;
import net.minecraft.util.math.MathHelper;
import org.apache.commons.lang3.RandomUtils;
import team.sdhq.eventBus.annotations.EventTarget;
import team.sdhq.eventBus.annotations.priority.HighestPriority;

import java.util.concurrent.ThreadLocalRandom;

/**
 * OpenZen {@code shit.zen.utils.rotation.Rotation} smoothing.
 *
 * <p>Ported from the {@code smoothYawArray} / {@code smoothPitchArray} pair rather than
 * {@code smoothYaw} / {@code smoothPitch}. The two pairs are the same algorithm and differ only
 * in where the sine noise term reads its angle from. The non-array variants read
 * {@code mc.player.getXRot()/getYRot()} because OpenZen writes rotations onto the player entity,
 * so the player angles <em>are</em> the smoothing state and change every tick. SigmaClient
 * rotates silently, so {@code mc.player.rotation*} stays pinned to the user's mouse and the sine
 * term would collapse into a constant per-axis bias — a fixed aim offset on every packet. The
 * array variants take the angles as parameters, which is the form that survives the difference.
 *
 * <p>Unlike the other modes this one filters before it applies: {@link #smooth()} rewrites
 * {@link RotationCore#currentYaw} / {@link RotationCore#currentPitch} in place, so the packet,
 * the movement correction, the crosshair ray trace in GameRenderer and KillAura's own ray trace
 * all see the angle that was actually sent. Publishing only to the packet is what made the
 * previous port miss: the hit checks kept reading the raw, unsmoothed target.
 */
public class Zen extends CorrectorMode {

    /** Smoothing state: the rotation emitted on the previous tick. */
    private float lastYaw;
    private float lastPitch;
    private boolean initialized;

    /**
     * Advances the smoothing exactly once per tick. Keyed on the player's tick counter rather
     * than an event, because every handler below has to be able to trigger the step and no
     * single event is guaranteed to fire first (or at all) on every code path.
     */
    private int lastSmoothTick = Integer.MIN_VALUE;

    public Zen() {
        super("Zen", "OpenZen rotation corrector");

        this.registerSetting(
                new NumberSetting(
                        "Speed",
                        "Per-tick rotation cap fed to OpenZen's smoothing",
                        10.0F,
                        1.0F,
                        90.0F,
                        1.0F
                )
        );

        /*
         * OpenZen patches the body-yaw logic in LivingEntity#tick so the body tracks its own
         * yaw rather than the travel direction. That is what makes head and body turn as one
         * and removes the vanilla diagonal lean while strafing — it is independent of whether
         * anything is aiming, which is why the effect is visible with KillAura off.
         */
        this.registerSetting(
                new BooleanSetting(
                        "BodySync",
                        "Pin the body to the view yaw (OpenZen head/body sync)",
                        true
                )
        );
    }

    @Override
    public void onDisable() {
        this.initialized = false;
        this.lastSmoothTick = Integer.MIN_VALUE;
    }

    /*
     * ============================================================
     * OpenZen Rotation.java
     *
     * Mth.wrapDegrees -> MathHelper.wrapDegrees
     * Mth.clamp       -> MathHelper.clamp
     *
     * ClientBase.mc.options.sensitivity().get().floatValue()
     * -> (float) mc.gameSettings.mouseSensitivity
     *
     * The arithmetic, the literal constants and the truncation points are unchanged,
     * including the deliberately mismatched 6.667 / 6.6666667 coefficient pair.
     * ============================================================
     */

    private static float moveTowards(float current, float target, float maxStep) {
        float diff = MathHelper.wrapDegrees(target - current);

        if (diff > maxStep) {
            diff = maxStep;
        }

        if (diff < -maxStep) {
            diff = -maxStep;
        }

        return current + diff;
    }

    /** OpenZen {@code smoothYawArray(float, float[], float)}; currentPair = {curYaw, curPitch}. */
    private float smoothYaw(float speed, float curYaw, float curPitch, float target) {
        float stepped = moveTowards(curYaw, target, speed + RandomUtils.nextFloat(0.0F, 15.0F));

        if (stepped != target) {
            stepped += (float) ((double) RandomUtils.nextFloat(1.0F, 2.0F)
                    * Math.sin((double) curPitch * Math.PI));
        }

        if (stepped == curYaw) {
            return curYaw;
        }

        float sensitivity = (float) mc.gameSettings.mouseSensitivity;

        stepped += (float) (ThreadLocalRandom.current().nextGaussian() * 0.2);

        if ((double) sensitivity == 0.5) {
            sensitivity = 0.47887325F;
        }

        float scaled = sensitivity * 0.6F + 0.2F;
        float gcd = scaled * scaled * scaled * 8.0F;

        int steps = (int) ((6.667 * (double) stepped - 6.6666667 * (double) curYaw) / (double) gcd);
        float snapped = (float) steps * gcd;

        return (float) ((double) curYaw + (double) snapped * 0.15);
    }

    /** OpenZen {@code smoothPitchArray(float, float[], float)}; currentPair = {curYaw, curPitch}. */
    private float smoothPitch(float speed, float curYaw, float curPitch, float target) {
        float stepped = moveTowards(curPitch, target, speed + RandomUtils.nextFloat(0.0F, 15.0F));

        if (stepped != target) {
            stepped += (float) ((double) RandomUtils.nextFloat(1.0F, 2.0F)
                    * Math.sin((double) curYaw * Math.PI));
        }

        float sensitivity = (float) mc.gameSettings.mouseSensitivity;

        if ((double) sensitivity == 0.5) {
            sensitivity = 0.47887325F;
        }

        float scaled = sensitivity * 0.6F + 0.2F;
        float gcd = scaled * scaled * scaled * 8.0F;

        int steps = (int) ((6.667 * (double) stepped - 6.666667 * (double) curPitch) / (double) gcd) * -1;
        float snapped = (float) steps * gcd;

        float result = (float) ((double) curPitch - (double) snapped * 0.15);

        return MathHelper.clamp(result, -90.0F, 90.0F);
    }

    private void smooth() {
        if (mc.player == null || !this.isActiveMode()) {
            this.initialized = false;
            return;
        }

        if (this.lastSmoothTick == mc.player.ticksExisted) {
            return;
        }

        this.lastSmoothTick = mc.player.ticksExisted;

        /*
         * Published before the aim-target checks below: the body sync is a property of the
         * mode, not of whether something is currently aiming, so it has to hold while idle too.
         * The lock is re-published every tick and expires by itself, so disabling the setting
         * or leaving the mode releases the body on the next tick.
         */
        if (this.getBooleanValueFromSettingName("BodySync")) {
            RotationCore.lockBodyYaw(
                    mc.player.rotationYaw,
                    mc.player.getEntityId(),
                    mc.player.ticksExisted
            );
        }

        float targetYaw = RotationCore.currentYaw;
        float targetPitch = RotationCore.currentPitch;

        if (Float.isNaN(targetYaw) || Float.isNaN(targetPitch)) {
            this.initialized = false;
            return;
        }

        float playerYaw = mc.player.rotationYaw;
        float playerPitch = mc.player.rotationPitch;

        /*
         * Minecraft#tick copies the player's view into RotationCore every tick before anything
         * else runs. If nothing overwrote it afterwards then no module is aiming, and running
         * the player's own view through the smoother would stamp OpenZen's sine and gaussian
         * noise onto every idle packet. Track the view and emit nothing.
         */
        if (Math.abs(MathHelper.wrapDegrees(targetYaw - playerYaw)) < 1.0E-4F
                && Math.abs(targetPitch - playerPitch) < 1.0E-4F) {

            this.lastYaw = playerYaw;
            this.lastPitch = playerPitch;
            this.initialized = true;
            return;
        }

        if (!this.initialized) {
            this.lastYaw = playerYaw;
            this.lastPitch = playerPitch;
            this.initialized = true;
        }

        float speed = this.getNumberValueBySettingName("Speed");

        // Both axes read the same pre-step pair, matching the array variants' contract.
        float yaw = this.smoothYaw(speed, this.lastYaw, this.lastPitch, targetYaw);
        float pitch = this.smoothPitch(speed, this.lastYaw, this.lastPitch, targetPitch);

        /*
         * The gcd step carries an intentional coefficient mismatch (6.667 vs 6.6666667) whose
         * residual scales with the magnitude of the angle. Keeping yaw wrapped holds that
         * residual below one mouse count; letting it accumulate unbounded turns it into a
         * steady drift of roughly a fifth of a degree per tick. moveTowards already takes the
         * wrapped shortest path, so wrapping the stored state changes the emitted
         * representation only, never the direction of travel.
         */
        this.lastYaw = MathHelper.wrapDegrees(yaw);
        this.lastPitch = pitch;

        RotationCore.currentYaw = this.lastYaw;
        RotationCore.currentPitch = this.lastPitch;
    }

    /*
     * Handlers run at HighestPriority so the smoothing lands before anything else reads
     * RotationCore this tick.
     */

    @EventTarget
    @HighestPriority
    public void onPre(EventMotion event) {
        if (!event.isPre()) {
            return;
        }

        this.smooth();

        if (!this.isActiveMode()) {
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
    @HighestPriority
    public void onInput(EventMoveInput event) {
        this.smooth();

        if (this.canCorrect()) {
            MovementUtil.silentStrafe(event, RotationCore.currentYaw);
        }
    }

    @EventTarget
    @HighestPriority
    public void onJump(EventJump event) {
        this.smooth();

        if (this.canCorrect()) {
            event.yaw = RotationCore.currentYaw;
        }
    }

    @EventTarget
    @HighestPriority
    public void onStrafe(EventMoveFlying event) {
        this.smooth();

        if (this.canCorrect()) {
            event.yaw = RotationCore.currentYaw;
        }
    }

    @EventTarget
    @HighestPriority
    public void onLook(EventLook event) {
        this.smooth();

        if (this.canCorrect() && this.fixLook()) {
            event.yaw = RotationCore.currentYaw;
            event.pitch = RotationCore.currentPitch;
        }
    }
}
