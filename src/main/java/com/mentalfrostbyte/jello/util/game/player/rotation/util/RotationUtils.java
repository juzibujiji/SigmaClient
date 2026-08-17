package com.mentalfrostbyte.jello.util.game.player.rotation.util;

import com.mentalfrostbyte.Client;
import com.mentalfrostbyte.jello.module.impl.movement.BlockFly;
import com.mentalfrostbyte.jello.util.game.player.constructor.Rotation;
import com.mentalfrostbyte.jello.util.game.world.blocks.BlockUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.util.MouseSmoother;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.*;
import net.minecraft.util.math.vector.Vector3d;

import java.util.concurrent.ThreadLocalRandom;

public class RotationUtils {
    private static final Minecraft mc = Minecraft.getInstance();

    // =====================================================================================
    // Rise 6.9.5 RotationComponent port
    //
    // Field names mirror Rise's decompiled component:
    //   fk -> riseYaw / risePitch   (current smoothed server rotation)
    //   fl -> riseLastYaw / riseLastPitch (last rotation used as smoothing origin)
    //   fm -> riseTargetYaw / riseTargetPitch (raw module target)
    //   ft / fu -> riseWanderAngle / riseWanderYaw / riseWanderPitch (flick wander state)
    // =====================================================================================
    private static float riseTargetYaw;
    private static float riseTargetPitch;
    private static float riseYaw;
    private static float risePitch;
    private static float riseLastYaw;
    private static float riseLastPitch;
    private static float riseWanderAngle;
    private static float riseWanderYaw;
    private static float riseWanderPitch;
    private static double riseRotationSpeed;
    private static Entity riseTargetEntity;
    private static double riseRange;
    private static boolean riseThroughWalls;
    private static boolean riseLook;
    private static boolean riseActive;

    public static void riseReset(float yaw, float pitch) {
        riseTargetYaw = riseYaw = riseLastYaw = yaw;
        riseTargetPitch = risePitch = riseLastPitch = pitch;
        riseWanderAngle = 0.0F;
        riseWanderYaw = 0.0F;
        riseWanderPitch = 0.0F;
        riseRotationSpeed = 0.0D;
        riseTargetEntity = null;
        riseRange = 0.0D;
        riseThroughWalls = false;
        riseLook = true;
        riseActive = false;
    }

    public static boolean riseIsActive() {
        return riseActive;
    }

    public static float riseGetYaw() {
        return riseYaw;
    }

    public static float riseGetPitch() {
        return risePitch;
    }

    /**
     * Equivalent of {@code RotationComponent.a(vec2, speed, movementFix, function, silent, look)}.
     * {@code speed} is Rise's raw rotation-speed setting; like Rise it is multiplied by 36 before
     * being used as the per-tick max delta.
     */
    public static void riseSetRotations(float targetYaw, float targetPitch, double speed, Entity target,
                                        double range, boolean throughWalls, boolean silent, boolean look) {
        if (mc.player == null) {
            return;
        }

        riseTargetYaw = targetYaw;
        riseTargetPitch = MathHelper.clamp(targetPitch, -90.0F, 90.0F);
        riseRotationSpeed = speed * 36.0D;
        riseTargetEntity = target;
        riseRange = range;
        riseThroughWalls = throughWalls;
        riseLook = look;
        riseActive = true;
        riseUpdateCurrentRotation();
    }

    /**
     * Rise's {@code bJ()}: one smoothed step from {@code fl} towards {@code fm}, including the
     * FPS sub-iteration loop, micro-jitter, sensitivity-patch quantization, and the random
     * flick-guard path when the raw target is more than five degrees away from the current rotation.
     */
    public static void riseUpdateCurrentRotation() {
        if (!riseActive || mc.player == null) {
            return;
        }

        float targetYaw = riseTargetYaw;
        float targetPitch = riseTargetPitch;

        if (riseTargetEntity != null && riseRange > 0.0D
                && (Math.abs(targetYaw - riseYaw) > 5.0F || Math.abs(targetPitch - risePitch) > 5.0F)) {
            float[] flicked = riseApplyFlickGuard(targetYaw, targetPitch);
            targetYaw = flicked[0];
            targetPitch = flicked[1];
        }

        float[] smoothed = riseSmoothRotation(riseLastYaw, riseLastPitch, targetYaw, targetPitch,
                riseRotationSpeed + Math.random());
        riseYaw = smoothed[0];
        risePitch = smoothed[1];
        riseLastYaw = riseYaw;
        riseLastPitch = risePitch;

        if (riseLook && mc.gameRenderer != null) {
            mc.gameRenderer.getMouseOver(1.0F);
        }
    }

    /**
     * Rise applies the head/body part of the rotation in PreMotion, independently of the
     * movement-correction mode. In 1.16.5 the equivalent fields are rotationYawHead for the
     * head and renderYawOffset for the body. When silent is false Rise also writes the actual
     * camera rotation in the same event.
     */
    public static void riseSyncVisuals(float yaw, float pitch, boolean silent) {
        if (mc.player == null) {
            return;
        }

        mc.player.rotationYawHead = yaw;
        mc.player.renderYawOffset = yaw;

        if (!silent) {
            mc.player.rotationYaw = yaw;
            mc.player.rotationPitch = pitch;
        }
    }

    /**
     * Rise's flick guard: instead of turning straight onto a freshly acquired target, walk a
     * small random offset path. Each offset is raytrace-validated against the target; when the
     * first offset is invalid the walk is redirected towards the original target, and when that
     * is still invalid the walk restarts with Rise's two-degree fallback offset.
     */
    private static float[] riseApplyFlickGuard(float targetYaw, float targetPitch) {
        double distance = Math.random() * Math.random() * Math.random() * 20.0D;
        float directionSign = mc.player.ticksExisted / 10 % 2 == 0 ? -1.0F : 1.0F;

        riseWanderAngle += (float) ((20.0D
                + (Math.random() - 0.5D) * (Math.random() * Math.random() * Math.random() * 360.0D))
                * directionSign);
        riseWanderYaw += (float) (-MathHelper.sin((float) Math.toRadians(riseWanderAngle)) * distance);
        riseWanderPitch += (float) (MathHelper.cos((float) Math.toRadians(riseWanderAngle)) * distance);

        float yaw = targetYaw + riseWanderYaw;
        float pitch = targetPitch + riseWanderPitch;

        if (!riseValidateRotation(yaw, pitch)) {
            riseWanderAngle = (float) Math.toDegrees(Math.atan2(targetYaw - yaw, pitch - targetPitch)) - 180.0F;
            riseWanderYaw += (float) (-MathHelper.sin((float) Math.toRadians(riseWanderAngle)) * distance);
            riseWanderPitch += (float) (MathHelper.cos((float) Math.toRadians(riseWanderAngle)) * distance);
            yaw = targetYaw + riseWanderYaw;
            pitch = targetPitch + riseWanderPitch;
        }

        if (!riseValidateRotation(yaw, pitch)) {
            riseWanderYaw = 0.0F;
            riseWanderPitch = 0.0F;
            yaw = targetYaw + (float) (Math.random() * 2.0D);
            pitch = targetPitch + (float) (Math.random() * 2.0D);
        }

        return new float[] { yaw, pitch };
    }

    private static boolean riseValidateRotation(float yaw, float pitch) {
        if (mc.player == null || mc.world == null || riseTargetEntity == null || riseRange <= 0.0D) {
            return true;
        }

        return BlockUtil.rayTraceEntitiesnolastpos(yaw, pitch, (float) riseRange, riseThroughWalls)
                .contains(riseTargetEntity);
    }

    /**
     * Rise {@code RotationUtil.e/b}: one Euclidean rotation step followed by
     * {@code (debugFPS / 20 + random * 10)} sensitivity-patch quantization sub-iterations.
     * Every active sub-iteration also applies Rise's tiny random yaw/pitch jitter.
     */
    private static float[] riseSmoothRotation(float lastYaw, float lastPitch, float targetYaw,
                                              float targetPitch, double speed) {
        float wrappedYaw = MathHelper.wrapDegrees(targetYaw - lastYaw);
        float pitchDelta = targetPitch - lastPitch;
        double length = Math.sqrt((double) wrappedYaw * wrappedYaw + (double) pitchDelta * pitchDelta);

        float stepYaw = 0.0F;
        float stepPitch = 0.0F;

        if (length >= 1.0E-4D) {
            double yawRatio = Math.abs(wrappedYaw / length);
            double pitchRatio = Math.abs(pitchDelta / length);
            double maxStepYaw = speed * yawRatio;
            double maxStepPitch = speed * pitchRatio;
            stepYaw = (float) Math.max(Math.min(wrappedYaw, maxStepYaw), -maxStepYaw);
            stepPitch = (float) Math.max(Math.min(pitchDelta, maxStepPitch), -maxStepPitch);
        }

        float yaw = lastYaw + stepYaw;
        float pitch = lastPitch + stepPitch;

        int iterations = (int) (Minecraft.getFps() / 20.0D + Math.random() * 10.0D);
        for (int i = 1; i <= iterations; i++) {
            if (Math.abs(stepYaw) + Math.abs(stepPitch) > 1.0E-4F) {
                yaw += (float) ((Math.random() - 0.5D) / 1000.0D);
                pitch -= (float) (Math.random() / 200.0D);
            }

            float[] patched = riseApplySensitivityPatch(yaw, pitch);
            yaw = patched[0];
            pitch = Math.max(-90.0F, Math.min(90.0F, patched[1]));
        }

        return new float[] { yaw, pitch };
    }

    /**
     * Rise {@code RotationUtil.m}: quantize to the client's real mouse-sensitivity grid against
     * lastReportedYaw / lastReportedPitch (Rise's getPreviousRotation uses those same fields).
     * The tiny random sensitivity term is part of Rise's anti-flick quantization.
     */
    private static float[] riseApplySensitivityPatch(float yaw, float pitch) {
        if (mc.player == null) {
            return new float[] { yaw, pitch };
        }

        float sensitivity = (float) (mc.gameSettings.mouseSensitivity * (1.0D + Math.random() / 1000000.0D)
                * 0.6D + 0.2D);
        double gcd = sensitivity * sensitivity * sensitivity * 8.0D * 0.15D;

        float baseYaw = mc.player.lastReportedYaw;
        float basePitch = mc.player.lastReportedPitch;
        float patchedYaw = baseYaw + (float) (Math.round((yaw - baseYaw) / gcd) * gcd);
        float patchedPitch = basePitch + (float) (Math.round((pitch - basePitch) / gcd) * gcd);

        return new float[] { patchedYaw, MathHelper.clamp(patchedPitch, -90.0F, 90.0F) };
    }


    public static Rotation limitAngleChange(Rotation currentRotation, Rotation targetRotation, float horizontalSpeed, float verticalSpeed) {
        float yawDifference = getAngleDifference(targetRotation.yaw, currentRotation.yaw);
        float pitchDifference = getAngleDifference(targetRotation.pitch, currentRotation.pitch);
        return new Rotation(currentRotation.yaw + (yawDifference > horizontalSpeed ? horizontalSpeed : Math.max(yawDifference, -horizontalSpeed)), currentRotation.pitch + (pitchDifference > verticalSpeed ? verticalSpeed : Math.max(pitchDifference, -verticalSpeed)));
    }

    public static float updateRotation(float current, float calc, float maxDelta) {
        float f = MathHelper.wrapAngleTo180_float(calc - current);
        if (f > maxDelta) {
            f = maxDelta;
        }
        if (f < -maxDelta) {
            f = -maxDelta;
        }
        return current + f;
    }

    public static float[] gcdFix(float[] currentRotation, float[] lastRotation) {
        final float f = (float) (mc.gameSettings.mouseSensitivity * 0.6F + 0.2F);
        final float gcd = f * f * f * 1.2F;

        final float deltaYaw = currentRotation[0] - lastRotation[0];
        final float deltaPitch = currentRotation[1] - lastRotation[1];

        final float fixedDeltaYaw = deltaYaw - (deltaYaw % gcd);
        final float fixedDeltaPitch = deltaPitch - (deltaPitch % gcd);

        final float fixedYaw = lastRotation[0] + fixedDeltaYaw;
        final float fixedPitch = lastRotation[1] + fixedDeltaPitch;
        return new float[]{fixedYaw, fixedPitch};
    }

    public static float getAngleDifference(float a, float b) {
        return ((a - b) % 360.0f + 540.0f) % 360.0f - 180.0f;
    }

    public static float[] scaffoldRots(double bx, double by, double bz, float lastYaw, float lastPitch, float yawSpeed, float pitchSpeed, boolean random) {
        double x = bx - Minecraft.getInstance().player.getPosX();
        double y = by - (Minecraft.getInstance().player.getPosY() + (double) Minecraft.getInstance().player.getEyeHeight());
        double z = bz - Minecraft.getInstance().player.getPosZ();
        float calcYaw = (float) (Math.toDegrees(MathHelper.atan2(z, x)) - 90.0);
        float calcPitch = (float) (-(MathHelper.atan2(y, MathHelper.sqrt(x * x + z * z)) * 180.0 / Math.PI));
        float pitch = RotationUtils.updateRotation(lastPitch, calcPitch, pitchSpeed + RandomUtil.nextFloat(0.0f, 15.0f));
        float yaw = RotationUtils.updateRotation(lastYaw, calcYaw, yawSpeed + RandomUtil.nextFloat(0.0f, 15.0f));
        if (random) {
            yaw = (float) ((double) yaw + ThreadLocalRandom.current().nextDouble(-2.0, 2.0));
            pitch = (float) ((double) pitch + ThreadLocalRandom.current().nextDouble(-0.2, 0.2));
        }
        return new float[]{yaw, pitch};
    }

    public static Rotation getRotationsToPosition(Vector3d var0) {
        float[] var3 = getRotationsToVector(Minecraft.getInstance().player.getPositionVec().add(0.0, Minecraft.getInstance().player.getEyeHeight(), 0.0), var0);
        return new Rotation(var3[0], var3[1]);
    }

    public static Vector3d getEntityPosition(Entity var0) {
        return calculateBoundingBoxPosition(var0.boundingBox);
    }

    public static float wrapAngleDifference(float var0, float var1) {
        return MathHelper.wrapAngleTo180_float(-(var0 - var1));
    }

    public static Vector3d calculateBoundingBoxPosition(AxisAlignedBB var0) {
        double var3 = var0.getCenter().x;
        double var5 = var0.minY;
        double var7 = var0.getCenter().z;
        double var9 = (var0.maxY - var5) * 0.95;
        double var11 = (var0.maxX - var0.minX) * 0.95;
        double var13 = (var0.maxZ - var0.minZ) * 0.95;
        double var15 = Math.max(var5, Math.min(var5 + var9, mc.player.getPosY() + (double) mc.player.getEyeHeight()));
        double var17 = Math.max(var3 - var11 / 2.0, Math.min(var3 + var11 / 2.0, mc.player.getPosX()));
        double var19 = Math.max(var7 - var13 / 2.0, Math.min(var7 + var13 / 2.0, mc.player.getPosZ()));
        return new Vector3d(var17, var15, var19);
    }

    public static float[] getRotationsToVector(Vector3d var0, Vector3d var1) {
        double var4 = var1.x - var0.x;
        double var6 = var1.z - var0.z;
        double var8 = var1.y - var0.y;
        double var10 = MathHelper.sqrt(var4 * var4 + var6 * var6);
        float var12 = smoothAngle(0.0F, (float) (Math.atan2(var6, var4) * 180.0 / Math.PI) - 90.0F, 360.0F);
        float var13 = smoothAngle(Minecraft.getInstance().player.rotationPitch, (float) (-(Math.atan2(var8, var10) * 180.0 / Math.PI)), 360.0F);
        return new float[]{var12, var13};
    }

    public static float smoothAngle(float var0, float var1, float var2) {
        float var5 = MathHelper.wrapAngleTo180_float(var1 - var0);
        if (var5 > var2) {
            var5 = var2;
        }

        if (var5 < -var2) {
            var5 = -var2;
        }

        return var0 + var5;
    }

    public static float getAngleDifference2(float target, float current) {
        target %= 360.0F;
        current %= 360.0F;
        if (target < 0.0F) {
            target += 360.0F;
        }

        if (current < 0.0F) {
            current += 360.0F;
        }

        float var4 = current - target;
        return !(var4 > 180.0F) ? (!(var4 < -180.0F) ? var4 : var4 + 360.0F) : var4 - 360.0F;
    }

    public static Rotation getAdvancedRotation(Entity target, boolean raycast) {
        Vector3d entityPosition = getEntityPosition(target);
        if (raycast && !isHovering(entityPosition)) {
            for (int heightLevel = -1; heightLevel < 2; heightLevel++) {
                double heightAdjustment = heightLevel;
                if (heightLevel != -1) {
                    heightAdjustment *= target.boundingBox.getYSize();
                } else {
                    heightAdjustment = target.getEyeHeight() - 0.02F;
                }

                double entityPosX = target.getPosX();
                double entityPosZ = target.getPosZ();
                double entityPosY = target.getPosY() + heightAdjustment + 0.05;
                double deltaX = entityPosX - mc.player.getPosX();
                double deltaY = entityPosY - (double) mc.player.getEyeHeight() - 0.02F - mc.player.getPosY();
                double deltaZ = entityPosZ - mc.player.getPosZ();
                double horizontalDistance = MathHelper.sqrt(deltaX * deltaX + deltaZ * deltaZ);
                float adjustedYaw = smoothAngle(mc.player.rotationYaw, (float) (Math.atan2(deltaZ, deltaX) * 180.0 / Math.PI) - 90.0F, 360.0F);
                float adjustedPitch = smoothAngle(mc.player.rotationPitch, (float) (-(Math.atan2(deltaY, horizontalDistance) * 180.0 / Math.PI)), 360.0F);
                boolean isHoveringOverEntity = isHovering(new Vector3d(entityPosX, entityPosY, entityPosZ));
                if (isHoveringOverEntity) {
                    return new Rotation(adjustedYaw, adjustedPitch);
                }

                for (int sideAdjustment = -1; sideAdjustment < 2; sideAdjustment += 2) {
                    entityPosX = target.getPosX() + (target.getPosX() - target.lastTickPosX) * (double) mc.getRenderPartialTicks();
                    entityPosZ = target.getPosZ() + (target.getPosZ() - target.lastTickPosZ) * (double) mc.getRenderPartialTicks();
                    entityPosY = target.getPosY() + 0.05 + (target.getPosY() - target.lastTickPosY) * (double) mc.getRenderPartialTicks() + heightAdjustment;
                    double adjustmentX = target.boundingBox.getXSize() / 2.5 * (double) sideAdjustment;
                    double adjustmentZ = target.boundingBox.getZSize() / 2.5 * (double) sideAdjustment;
                    if (!(mc.player.getPosX() < entityPosX + adjustmentX)) {
                        if (mc.player.getPosX() > entityPosX + adjustmentX) {
                            if (!(mc.player.getPosZ() < entityPosZ - adjustmentZ)) {
                                entityPosX += adjustmentX;
                            } else {
                                entityPosX -= adjustmentX;
                            }

                            if (!(mc.player.getPosX() > entityPosX + adjustmentX)) {
                                entityPosZ += adjustmentZ;
                            } else {
                                entityPosZ -= adjustmentZ;
                            }
                        }
                    } else {
                        if (!(mc.player.getPosZ() > entityPosZ + adjustmentZ)) {
                            entityPosX -= adjustmentX;
                        } else {
                            entityPosX += adjustmentX;
                        }

                        if (!(mc.player.getPosX() < entityPosX - adjustmentX)) {
                            entityPosZ -= adjustmentZ;
                        } else {
                            entityPosZ += adjustmentZ;
                        }
                    }

                    deltaX = entityPosX - mc.player.getPosX();
                    deltaY = entityPosY - (double) mc.player.getEyeHeight() - 0.02 - mc.player.getPosY();
                    deltaZ = entityPosZ - mc.player.getPosZ();
                    horizontalDistance = MathHelper.sqrt(deltaX * deltaX + deltaZ * deltaZ);
                    adjustedYaw = smoothAngle(mc.player.rotationYaw, (float) (Math.atan2(deltaZ, deltaX) * 180.0 / Math.PI) - 90.0F, 360.0F);
                    adjustedPitch = smoothAngle(mc.player.rotationPitch, (float) (-(Math.atan2(deltaY, horizontalDistance) * 180.0 / Math.PI)), 360.0F);
                    isHoveringOverEntity = isHovering(new Vector3d(entityPosX, entityPosY, entityPosZ));
                    if (isHoveringOverEntity) {
                        return new Rotation(adjustedYaw, adjustedPitch);
                    }
                }
            }

            return null;
        } else {
            return getRotationsToPosition(entityPosition);
        }
    }

    public static boolean isHovering(Vector3d end) {
        Vector3d start = new Vector3d(mc.player.getPosX(), mc.player.getPosY() + (double) mc.player.getEyeHeight(), mc.player.getPosZ());
        RayTraceContext ctx = new RayTraceContext(start, end, RayTraceContext.BlockMode.OUTLINE, RayTraceContext.FluidMode.NONE, mc.player);
        BlockRayTraceResult ray = mc.world.rayTraceBlocks(ctx);
        return ray.getType() == RayTraceResult.Type.MISS || ray.getType() == RayTraceResult.Type.ENTITY;
    }
}

