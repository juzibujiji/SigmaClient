package com.mentalfrostbyte.jello.util.game.player.rotation.util;

import com.mentalfrostbyte.Client;
import com.mentalfrostbyte.jello.module.impl.movement.BlockFly;
import com.mentalfrostbyte.jello.module.impl.player.AutoSprint;
import com.mentalfrostbyte.jello.util.game.player.constructor.Rotation;
import net.minecraft.client.Minecraft;
import net.minecraft.client.util.MouseSmoother;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.*;
import net.minecraft.util.math.vector.Vector3d;

import java.util.concurrent.ThreadLocalRandom;

public class RotationUtils {
    private static final Minecraft mc = Minecraft.getInstance();

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
        float currentYaw = currentRotation[0];
        float currentPitch = currentRotation[1];
        float lastYaw = lastRotation[0];
        float lastPitch = lastRotation[1];
        boolean bypass = Client.getInstance().moduleManager.getModuleByClass(BlockFly.class).isEnabled()
                && (Client.getInstance().moduleManager.getModuleByClass(BlockFly.class).getStringSettingValueByName("Mode").equals("Grim")
                || Client.getInstance().moduleManager.getModuleByClass(BlockFly.class).getStringSettingValueByName("Mode").equals("Clutch"));

        // Get the mouse sensitivity
        float sensitivity = bypass ? 0.5f : (float) mc.gameSettings.mouseSensitivity;

        // Calculate GCD
        float f = sensitivity * 0.6F + 0.2F;
        float gcd = f * f * f * (bypass ? 1.2F : 8.0F);

        // Calculate rotation differences
        float deltaYaw = currentYaw - lastYaw;
        float deltaPitch = currentPitch - lastPitch;

        // Apply a human-like rotation pattern
        // This simulates how mouse movements would actually work
        float fixedYaw = lastYaw;
        float fixedPitch = lastPitch;

        // Calculate how many "mouse steps" this would take
        int yawSteps = Math.round(deltaYaw / gcd);
        int pitchSteps = Math.round(deltaPitch / gcd);

        // Apply the steps with slight variations
        if (yawSteps != 0) {
            // Add a tiny bit of randomness to each step
            float yawPerStep = gcd * (0.99f + ThreadLocalRandom.current().nextFloat() * 0.02f);
            fixedYaw = lastYaw + yawSteps * yawPerStep;
        }

        if (pitchSteps != 0) {
            // Add a tiny bit of randomness to each step
            float pitchPerStep = gcd * (0.99f + ThreadLocalRandom.current().nextFloat() * 0.02f);
            fixedPitch = lastPitch + pitchSteps * pitchPerStep;
        }

        // Apply smoothing if needed
        if (bypass) {
            MouseSmoother filterX = new MouseSmoother();
            MouseSmoother filterY = new MouseSmoother();
            fixedYaw = (float) filterX.smooth(fixedYaw - lastYaw, 3800F * gcd) + lastYaw;
            fixedPitch = (float) filterY.smooth(fixedPitch - lastPitch, 3800F * gcd) + lastPitch;
        }

        // Ensure pitch is within bounds
        fixedPitch = MathHelper.clamp(fixedPitch, -90.0F, 90.0F);

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

