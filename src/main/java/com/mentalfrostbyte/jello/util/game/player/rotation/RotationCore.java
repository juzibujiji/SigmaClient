package com.mentalfrostbyte.jello.util.game.player.rotation;

public class RotationCore {
    public static float lastYaw, lastPitch, currentYaw, currentPitch, sYaw, sPitch;

    /**
     * Publishes the rotation for this tick. Every consumer reads it from here: the outgoing
     * motion packet and the movement correction applied by the active CorrectMovement mode,
     * the crosshair ray trace in GameRenderer, and item use in Item.
     *
     * <p>Previously lived on {@code managers.RotationManager}, which also carried a duplicate
     * set of packet/movement handlers. Those handlers are retired; the publish API belongs
     * with the fields it writes.
     */
    public static void setRotations(final float rotationYaw, final float rotationPitch) {
        currentYaw = rotationYaw;
        currentPitch = rotationPitch;
    }

    /**
     * Body-yaw lock for the OpenZen head/body sync, read by {@code LivingEntity#tick}.
     *
     * <p>Vanilla aims the body at the direction the entity is actually travelling (derived from
     * the position delta), which is what produces the diagonal lean while strafing. OpenZen
     * feeds its own yaw into that logic instead, so the body stays on the view direction and
     * head and body turn as one. This is the 1.16.5 equivalent of that hook.
     *
     * <p>The lock carries the entity it applies to and the tick it was published on, so it
     * targets exactly one entity and expires by itself after a single tick. A mode that stops
     * running therefore cannot leave a body pinned.
     */
    public static float bodyLockYaw = Float.NaN;
    public static int bodyLockEntityId = -1;
    public static int bodyLockTick = Integer.MIN_VALUE;

    public static void lockBodyYaw(final float yaw, final int entityId, final int tick) {
        bodyLockYaw = yaw;
        bodyLockEntityId = entityId;
        bodyLockTick = tick;
    }

    /** True when {@code entity} should have its body pinned to {@link #bodyLockYaw} this tick. */
    public static boolean isBodyLocked(final int entityId, final int ticksExisted) {
        /*
         * One tick of slack: the lock is normally published from the move-input handler, which
         * runs inside livingTick and so lands before the body logic in the same LivingEntity#tick.
         * If it ends up published from the motion handler instead, that fires after the body
         * logic and would otherwise be a tick too late to ever be seen.
         */
        return !Float.isNaN(bodyLockYaw)
                && bodyLockEntityId == entityId
                && (bodyLockTick == ticksExisted || bodyLockTick == ticksExisted - 1);
    }
}
