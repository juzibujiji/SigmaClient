package com.mentalfrostbyte.jello.util.game.player.combat;


import com.mentalfrostbyte.jello.util.game.player.constructor.Rotation;
import com.mentalfrostbyte.jello.util.game.world.EntityUtil;
import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.BlockRayTraceResult;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RayTraceContext;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.vector.Vector3d;

import java.util.Date;

public class RotationUtil {
    private static final Minecraft mc = Minecraft.getInstance();
    public static float field42013;
    public static float field42014;
    public static long field42015;

    public static float calculate(float current, float var1, float max) {
        float wrapped = MathHelper.wrapDegrees(var1 - current);

        return current + wrapped;
    }

//    public static float[] advancedRotation(LivingEntity var0, double var1, double var3, double var5) {
//        double[] var9 = Class9047.method33642(var0);
//        double var10 = var9[0] - var1;
//        double var12 = var0.getPosY() - (double) mc.player.getEyeHeight() - 0.02F + (double)var0.getEyeHeight() - var3;
//        double var14 = var9[2] - var5;
//        double var16 = (double) MathHelper.sqrt(var10 * var10 + var14 * var14);
//        float var18 = calculate(mc.player.rotationYaw, (float)(Math.atan2(var14, var10) * 180.0 / Math.PI) - 90.0F, 360.0F);
//        float var19 = calculate(mc.player.rotationPitch, (float)(-(Math.atan2(var12, var16) * 180.0 / Math.PI)), 360.0F);
//        return new float[]{var18, var19};
//    }

    public static float[] doBasicRotation(Entity target) {
        double var3 = mc.player.getPosX() + (mc.player.getPosX() - mc.player.lastTickPosX) * (double) mc.getRenderPartialTicks();
        double var5 = mc.player.getPosZ() + (mc.player.getPosZ() - mc.player.lastTickPosZ) * (double) mc.getRenderPartialTicks();
        double var7 = mc.player.getPosY() + (mc.player.getPosY() - mc.player.lastTickPosY) * (double) mc.getRenderPartialTicks();
        return calculateEntityRotations(target, var3, var7, var5);
    }
    public static float[] method34144(double var0, double var2, double var4) {
        double var8 = var0 - mc.player.getPosX();
        double var10 = var2 - mc.player.getPosZ();
        double var12 = var4 - mc.player.getPosY() - 1.2;
        double var14 = MathHelper.sqrt(var8 * var8 + var10 * var10);
        float var16 = (float)(Math.atan2(var10, var8) * 180.0 / Math.PI) - 90.0F;
        float var17 = (float)(-(Math.atan2(var12, var14) * 180.0 / Math.PI));
        return new float[]{var16, var17};
    }

    public static float[] calculateEntityRotations(Entity entity, double playerX, double playerZ, double playerY) {
        // Calculate the interpolated position of the entity based on render ticks
        double interpolatedPosX = entity.getPosX() + (entity.getPosX() - entity.lastTickPosX) * mc.getRenderPartialTicks();
        double interpolatedPosZ = entity.getPosZ() + (entity.getPosZ() - entity.lastTickPosZ) * mc.getRenderPartialTicks();
        double interpolatedPosY = entity.getPosY() + (entity.getPosY() - entity.lastTickPosY) * mc.getRenderPartialTicks();

        // Calculate the differences in position
        double deltaX = interpolatedPosX - playerX;
        double deltaY = interpolatedPosY - mc.player.getEyeHeight() - 0.02F + entity.getEyeHeight() - playerY;
        double deltaZ = interpolatedPosZ - playerZ;

        // Calculate horizontal distance and yaw/pitch angles
        double horizontalDistance = MathHelper.sqrt(deltaX * deltaX + deltaZ * deltaZ);
        float yaw = calculate(mc.player.rotationYaw, (float)(Math.atan2(deltaZ, deltaX) * 180.0 / Math.PI) - 90.0F, 360.0F);
        float pitch = calculate(mc.player.rotationPitch, (float)(-(Math.atan2(deltaY, horizontalDistance) * 180.0 / Math.PI)), 360.0F);

        // Return the calculated yaw and pitch
        return new float[]{yaw, pitch};
    }

    public static float[] method34139(LivingEntity var0, double var1, double var3, double var5) {
        double var9 = var0.getPosX() - var1 + 0.25 - Math.random() * 0.5;
        double var11 = var0.getPosY() - (double) mc.player.getEyeHeight() - 0.02F + (double)var0.getEyeHeight() - var3 - Math.random();
        double var13 = var0.getPosZ() - var5 + 0.25 - Math.random() * 0.5;
        double var15 = MathHelper.sqrt(var9 * var9 + var13 * var13);
        float var17 = calculate(mc.player.rotationYaw, (float)(Math.atan2(var13, var9) * 180.0 / Math.PI) - 90.0F, 360.0F);
        float var18 = calculate(mc.player.rotationPitch, (float)(-(Math.atan2(var11, var15) * 180.0 / Math.PI)), 360.0F);
        return new float[]{var17, var18};
    }

    public static float[] method34140(LivingEntity var0, double var1, double var3, double var5) {
        double var9 = Math.sin((double)(System.currentTimeMillis() - 500L) / 521.0) * 0.2;
        double var11 = Math.sin((double)System.currentTimeMillis() / 300.0) * 0.65;
        double var13 = Math.cos(((double)System.currentTimeMillis() - 150.0) / 705.0) * 0.2;
        double var15 = var0.getPosX() - var1 + var9;
        double var17 = var0.getPosY() - var3 - (double) mc.player.getEyeHeight() + (double)var0.getEyeHeight() - 0.02F - 0.6F - var11;
        double var19 = var0.getPosZ() - var5 + var13;
        double var21 = MathHelper.sqrt(var15 * var15 + var19 * var19);
        float var23 = calculate(mc.player.rotationYaw, (float)(Math.atan2(var19, var15) * 180.0 / Math.PI) - 90.0F, 360.0F);
        float var24 = calculate(mc.player.rotationPitch, (float)(-(Math.atan2(var17, var21) * 180.0 / Math.PI)), 360.0F);
        return new float[]{var23, var24};
    }

    public static Vector3d getLookVector(float var0, float var1) {
        float var4 = var0 * (float) (Math.PI / 180.0);
        float var5 = -var1 * (float) (Math.PI / 180.0);
        float var6 = MathHelper.cos(var5);
        float var7 = MathHelper.sin(var5);
        float var8 = MathHelper.cos(var4);
        float var9 = MathHelper.sin(var4);
        return new Vector3d(var7 * var8, -var9, var6 * var8);
    }

    public static float[] method34141(LivingEntity var0, double var1, double var3, double var5, float var7) {
        Entity var10 = EntityUtil.getEntityFromRayTrace(field42013, field42014, var7, 0.0);
        if (var10 == null) {
            if (field42015 <= new Date().getTime() - 500L) {
                double var11 = Math.sin((double)(System.currentTimeMillis() - 500L) / 521.0) * 0.2;
                double var13 = Math.sin((double)System.currentTimeMillis() / 421.0) * 0.65;
                double var15 = Math.cos(((double)System.currentTimeMillis() - 150.0) / 7051.0) * 0.2;
                double var17 = var0.getPosX() - var1 + var11;
                double var19 = var0.getPosY() - var3 - (double) mc.player.getEyeHeight() + (double)var0.getEyeHeight() - 0.02F - 0.6F - var13;
                double var21 = var0.getPosZ() - var5 + var15;
                double var23 = MathHelper.sqrt(var17 * var17 + var21 * var21);
                field42013 = calculate(mc.player.rotationYaw, (float)(Math.atan2(var21, var17) * 180.0 / Math.PI) - 90.0F, 360.0F);
                field42014 = calculate(mc.player.rotationPitch, (float)(-(Math.atan2(var19, var23) * 180.0 / Math.PI)), 360.0F);
                return new float[]{field42013, field42014};
            } else {
                return new float[]{field42013, field42014};
            }
        } else {
            field42015 = new Date().getTime();
            return new float[]{field42013, field42014};
        }
    }

    public static float method34142(float var0, float var1) {
        float var4 = Math.abs(var0 - var1) % 360.0F;
        float var5 = !(var4 > 180.0F) ? var4 : 360.0F - var4;
        int var6 = var0 - var1 >= 0.0F && var0 - var1 <= 180.0F || var0 - var1 <= -180.0F && var0 - var1 >= -360.0F ? -1 : 1;
        return var5 * (float)var6;
    }

    public static float angleDiff(float var0, float var1) {
        float var4 = Math.abs(var0 - var1) % 360.0F;
        if (var4 > 180.0F) {
            var4 = 360.0F - var4;
        }

        return var4;
    }

    public static float[] rotationToPos(double x, double y, double z) {
        double dX = x - mc.player.getPosX();
        double dY = y - mc.player.getPosZ();
        double adjustedDZ = z - mc.player.getPosY() - 1.2;
        double var14 = MathHelper.sqrt(dX * dX + dY * dY);
        float yaw = (float)(Math.atan2(dY, dX) * 180.0 / Math.PI) - 90.0F;
        float pitch = (float)(-(Math.atan2(adjustedDZ, var14) * 180.0 / Math.PI));
        return new float[]{yaw, pitch};
    }

    public static float[] method34145(Vector3d var0, Vector3d var1) {
        double var4 = var1.x - var0.x;
        double var6 = var1.z - var0.z;
        double var8 = var1.y - var0.y;
        double var10 = MathHelper.sqrt(var4 * var4 + var6 * var6);
        float var12 = calculate(0.0F, (float)(Math.atan2(var6, var4) * 180.0 / Math.PI) - 90.0F, 360.0F);
        float var13 = calculate(mc.player.rotationPitch, (float)(-(Math.atan2(var8, var10) * 180.0 / Math.PI)), 360.0F);
        return new float[]{var12, var13};
    }

    public static float[] method34146(LivingEntity var0) {
        double var3 = mc.player.getPosX() + (mc.player.getPosX() - mc.player.lastTickPosX) * (double) mc.getRenderPartialTicks();
        double var5 = mc.player.getPosZ() + (mc.player.getPosZ() - mc.player.lastTickPosZ) * (double) mc.getRenderPartialTicks();
        double var7 = mc.player.getPosY() + (mc.player.getPosY() - mc.player.lastTickPosY) * (double) mc.getRenderPartialTicks();
        double var9 = var0.getPosX() + (var0.getPosX() - var0.lastTickPosX) * (double) mc.getRenderPartialTicks();
        double var11 = var0.getPosZ() + (var0.getPosZ() - var0.lastTickPosZ) * (double) mc.getRenderPartialTicks();
        double var13 = var0.getPosY() + (var0.getPosY() - var0.lastTickPosY) * (double) mc.getRenderPartialTicks();
        double var15 = (var9 - var0.lastTickPosX) * 0.4;
        double var17 = (var11 - var0.lastTickPosZ) * 0.4;
        double var19 = mc.player.getDistance(var0);
        var19 -= var19 % 0.8;
        double var21 = 1.0;
        double var23 = 1.0;
        boolean var25 = var0.isSprinting();
        var21 = var19 / 0.8 * var15 * (!var25 ? 1.0 : 1.25);
        var23 = var19 / 0.8 * var17 * (!var25 ? 1.0 : 1.25);
        double var26 = var9 + var21 - var3;
        double var28 = var11 + var23 - var5;
        double var30 = var7 + (double) mc.player.getEyeHeight() - (var13 + (double)var0.getHeight());
        double var32 = mc.player.getDistance(var0);
        float var34 = (float)Math.toDegrees(Math.atan2(var28, var26)) - 90.0F;
        double var35 = MathHelper.sqrt(var26 * var26 + var28 * var28);
        float var37 = (float)(-(Math.atan2(var30, var35) * 180.0 / Math.PI)) + (float)var32 * 0.14F;
        return new float[]{var34, -var37};
    }

    public static Rotation method34147(Entity var0) {
        float[] var3 = calculateEntityRotations(var0, mc.player.getPosX(), mc.player.getPosY(), mc.player.getPosZ());
        return new Rotation(var3[0], var3[1]);
    }

    public static Rotation getRotationsToVector(Vector3d var0) {
        float[] var3 = method34145(mc.player.getPositionVec().add(0.0, mc.player.getEyeHeight(), 0.0), var0);
        return new Rotation(var3[0], var3[1]);
    }

    public static boolean raytraceVector(Vector3d vec) {
        Vector3d var3 = new Vector3d(
                mc.player.getPosX(), mc.player.getPosY() + (double) mc.player.getEyeHeight(), mc.player.getPosZ()
        );
        RayTraceContext var4 = new RayTraceContext(var3, vec, RayTraceContext.BlockMode.OUTLINE, RayTraceContext.FluidMode.NONE, mc.player);
        BlockRayTraceResult var5 = mc.world.rayTraceBlocks(var4);
        boolean var6 = var5.getType() == RayTraceResult.Type.MISS || var5.getType() == RayTraceResult.Type.ENTITY;
        Block var7 = mc.world.getBlockState(var5.getPos()).getBlock();
        return var6;
    }

    public static float method34151(float var0, double var1, double var3) {
        double var7 = var1 - mc.player.getPosX();
        double var9 = var3 - mc.player.getPosZ();
        double var11 = 0.0;
        double degrees = Math.toDegrees(Math.atan(var9 / var7));
        if (var9 < 0.0 && var7 < 0.0) {
            if (var7 != 0.0) {
                var11 = 90.0 + degrees;
            }
        } else if (var9 < 0.0 && var7 > 0.0) {
            if (var7 != 0.0) {
                var11 = -90.0 + degrees;
            }
        } else if (var9 != 0.0) {
            var11 = Math.toDegrees(-Math.atan(var7 / var9));
        }

        return MathHelper.wrapDegrees(-(var0 - (float)var11));
    }

    public static float getShortestAngleDifference(float currentAngle, float targetAngle) {
        return MathHelper.wrapDegrees(-(currentAngle - targetAngle));
    }

    public static float getShortestYawDifference(float yaw1, float yaw2) {
        yaw1 %= 360.0F;
        yaw2 %= 360.0F;

        if (yaw1 < 0.0F) {
            yaw1 += 360.0F;
        }

        if (yaw2 < 0.0F) {
            yaw2 += 360.0F;
        }

        float difference = yaw2 - yaw1;
        return (difference > 180.0F) ? difference - 360.0F : (difference < -180.0F ? difference + 360.0F : difference);
    }

    private double method34153(double var1, double var3) {
        return var1 + Math.random() * (var3 - var1);
    }

    public static float method34154(float var0, Entity var1, double var2) {
        double var6 = var1.getPosX() - mc.player.getPosX();
        double var8 = var1.getPosZ() - mc.player.getPosZ();
        double var10 = var2 - 2.2 + (double)var1.getEyeHeight() - mc.player.getPosY();
        double var12 = MathHelper.sqrt(var6 * var6 + var8 * var8);
        double var14 = -Math.toDegrees(Math.atan(var10 / var12));
        return -MathHelper.wrapDegrees(var0 - (float)var14) - 2.5F;
    }
}