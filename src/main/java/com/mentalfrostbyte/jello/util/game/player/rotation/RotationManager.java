package com.mentalfrostbyte.jello.util.game.player.rotation;

import net.minecraft.client.Minecraft;
import net.minecraft.util.math.MathHelper;

import java.util.Random;

/**
 * Manages rotation values and smoothing
 */
public class RotationManager {
    private static final Minecraft mc = Minecraft.getInstance();
    private static final Random random = new Random();

    // Current rotation values
    private float currentYaw;
    private float currentPitch;

    // Target rotation values
    private float targetYaw;
    private float targetPitch;

    // Rotation state
    private boolean rotating = false;

    // Timing for smooth rotations
    private long rotationTime = System.currentTimeMillis();

    // Tick-based timing instead of system time
    private static final float TICK_TIME = 0.05f; // 1/20 seconds per tick

    // Feed-forward tracking
    private float lastYawDiff = 0;
    private float lastPitchDiff = 0;

    // Control parameters - adjusted to prevent server kicks
    private static final float YAW_PROPORTIONAL_GAIN = 12.0f;
    private static final float PITCH_PROPORTIONAL_GAIN = 10.0f;
    private static final float YAW_FEEDFORWARD_GAIN = 0.8f;      // Increased from 0.5f
    private static final float PITCH_FEEDFORWARD_GAIN = 0.6f;    // Increased from 0.4f
    // Ensure MAX_SPEED * TICK_TIME stays under ~45 degrees
    private static final float MAX_YAW_SPEED = 720.0f;   // Increased to 720 * 0.05 = 36 degrees per tick
    private static final float MAX_PITCH_SPEED = 360.0f; // Increased to 360 * 0.05 = 18 degrees per tick

    /**
     * Initialize rotation values
     */
    public void initialize() {
        currentYaw = mc.player != null ? mc.player.rotationYaw : 0;
        currentPitch = mc.player != null ? mc.player.rotationPitch : 0;
        targetYaw = currentYaw;
        targetPitch = currentPitch;
        rotating = false;
        lastYawDiff = 0;
        lastPitchDiff = 0;
    }

    /**
     * Set target rotation and start rotating
     */
    public void setTargetRotation(float yaw, float pitch) {
        // Ensure yaw is properly wrapped to [-180, 180]
        targetYaw = MathHelper.wrapDegrees(yaw);

        // Clamp pitch to valid range
        targetPitch = MathHelper.clamp(pitch, -90.0f, 90.0f);

        // Remove instant snaps that cause server kicks
        // No more teleporting currentYaw/currentPitch to target

        rotating = true;
        // No need to reset time if using fixed tick
    }

    /**
     * Update rotations with smoothing
     */
    public void updateRotations() {
        // Remove the rotating check to always update rotations
        // if (!rotating) return;

        // Use fixed tick time instead of wall clock time
        float timeDelta = TICK_TIME;  // assume one tick per update

        // Adjust smoothing based on distance to target
        float yawDiff = MathHelper.wrapDegrees(targetYaw - currentYaw);
        float pitchDiff = targetPitch - currentPitch;

        // Calculate angular velocities (for feed-forward)
        float yawAngularVelocity = (yawDiff - lastYawDiff) / timeDelta;
        float pitchAngularVelocity = (pitchDiff - lastPitchDiff) / timeDelta;
        lastYawDiff = yawDiff;
        lastPitchDiff = pitchDiff;

        // Calculate base speeds using proportional control
        float yawBaseSpeed = Math.abs(yawDiff) * YAW_PROPORTIONAL_GAIN;
        float pitchBaseSpeed = Math.abs(pitchDiff) * PITCH_PROPORTIONAL_GAIN;

        // Add feed-forward component to anticipate movement - keep sign for direction
        float yawFeedForward = yawAngularVelocity * YAW_FEEDFORWARD_GAIN;
        float pitchFeedForward = pitchAngularVelocity * PITCH_FEEDFORWARD_GAIN;

        // Calculate total speeds with feed-forward, capped at maximum
        float totalYawSpeed = Math.min(Math.abs(yawBaseSpeed + yawFeedForward), MAX_YAW_SPEED);
        float totalPitchSpeed = Math.min(Math.abs(pitchBaseSpeed + pitchFeedForward), MAX_PITCH_SPEED);

        // Calculate movement this tick - preserve sign from yawDiff
        float yawMove = Math.signum(yawDiff) * totalYawSpeed * timeDelta;
        float pitchMove = Math.signum(pitchDiff) * totalPitchSpeed * timeDelta;

        // Additional safety check to prevent server kicks - limit per-tick rotation
        yawMove = MathHelper.clamp(yawMove, -40.0f, 180.0f);
        pitchMove = MathHelper.clamp(pitchMove, -40.0f, 180.0f);

        // Apply movement (capped to prevent overshooting)
        if (Math.abs(yawMove) > Math.abs(yawDiff)) {
            currentYaw = targetYaw;
        } else {
            currentYaw = MathHelper.wrapDegrees(currentYaw + yawMove);
        }

        if (Math.abs(pitchMove) > Math.abs(pitchDiff)) {
            currentPitch = targetPitch;
        } else {
            currentPitch = currentPitch + pitchMove;
        }

        // Clamp pitch to valid range
        currentPitch = MathHelper.clamp(currentPitch, -90.0f, 90.0f);

        // Only set rotating to false when we're very close to target
        if (Math.abs(yawDiff) < 0.1f && Math.abs(pitchDiff) < 0.1f) {
            rotating = false;
        }
    }

    /**
     * Smoothly reset rotations to player's view
     */
    public void smoothResetRotations(float playerYaw, float playerPitch) {
        setTargetRotation(playerYaw, playerPitch);
    }

    /**
     * Get current yaw
     */
    public float getCurrentYaw() {
        return currentYaw;
    }

    /**
     * Get current pitch
     */
    public float getCurrentPitch() {
        return currentPitch;
    }

    /**
     * Check if currently rotating
     */
    public boolean isRotating() {
        return rotating;
    }
}