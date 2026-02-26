package com.mentalfrostbyte.jello.module.impl.render.projectiles;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.FallingBlock;
import net.minecraft.block.FenceBlock;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.shapes.VoxelShape;
import net.minecraft.util.math.vector.Vector3d;

public class TraceThing {
    private double x;
    private double y;
    private double z;
    public static Minecraft mc = Minecraft.getInstance();
    public Class9510 field41869;
    private static final ProjectileDirection[] directions = new ProjectileDirection[]{
            new ProjectileDirection(1.0, 0.0, 0.0),
            new ProjectileDirection(-1.0, 0.0, 0.0),
            new ProjectileDirection(0.0, 0.0, 1.0),
            new ProjectileDirection(0.0, 0.0, -1.0),
            new ProjectileDirection(0.0, -1.0, 0.0),
            new ProjectileDirection(0.0, 1.0, 0.0)
    };

    public TraceThing(double var1, double var3, double var5, Class9510 var7) {
        this.fromXYZ(var1, var3, var5);
        this.field41869 = var7;
    }

    public TraceThing(double var1, double var3, double var5) {
        this.fromXYZ(var1, var3, var5);
    }

    public TraceThing(BlockPos var1) {
        this.fromXYZ(var1.getX(), var1.getY(), var1.getZ());
    }

    public void fromXYZ(double x, double y, double z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public TraceThing method33965(double var1, double var3, double var5) {
        return new TraceThing(this.x + var1, this.y + var3, this.z + var5);
    }

    public void setX(float x) {
        this.x = x;
    }

    public void setY(float y) {
        this.y = y;
    }

    public void setZ(float z) {
        this.z = z;
    }

    public double getX() {
        return this.x;
    }

    public double getY() {
        return this.y;
    }

    public double getZ() {
        return this.z;
    }

    public Vector3d getMiddleXZ() {
        return new Vector3d(this.getX(), this.getY(), this.getZ()).add(0.5, 0.0, 0.5);
    }

    public double getXZDistance(Vector3d vec) {
        double xDist = vec.x - this.getX() - 0.5;
        double zDist = vec.z - this.getZ() - 0.5;
        return MathHelper.sqrt(xDist * xDist + zDist * zDist);
    }

    public float getDistance(Entity e) {
        double xDist = e.getPosX() - this.getX();
        double yDist = e.getPosY() - this.getY();
        double zDist = e.getPosZ() - this.getZ();
        return MathHelper.sqrt(xDist * xDist + yDist * yDist + zDist * zDist);
    }

    public float method33975(TraceThing var1) {
        double var4 = var1.getX() - this.getX();
        double var6 = var1.getY() - this.getY();
        double var8 = var1.getZ() - this.getZ();
        return MathHelper.sqrt(var4 * var4 + var6 * var6 + var8 * var8);
    }

    public double method33976(TraceThing var1) {
        double var4 = var1.getX() - this.getX();
        double var6 = var1.getY() - this.getY();
        double var8 = var1.getZ() - this.getZ();
        return var4 * var4 + var6 * var6 + var8 * var8;
    }

    public TraceThing method33977(float var1, float var2, float var3) {
        return new TraceThing(this.getX() + (double)var1, this.getY() + (double)var2, this.getZ() + (double)var3, this.field41869);
    }

    public double method33978(TraceThing var1) {
        double var4 = var1.getX() - this.getX();
        double var6 = var1.getY() - this.getY();
        double var8 = var1.getZ() - this.getZ();
        return Math.abs(var4) + Math.abs(var8) + Math.abs(var6);
    }

    public BlockPos method33979() {
        return new BlockPos(this.getX(), this.getY(), this.getZ());
    }

    public boolean method33980() {
        if (mc.world.getBlockState(this.method33979()).getBlock() == Blocks.LAVA) {
            return false;
        } else if (mc.world.getBlockState(this.method33979()).getFluidState().isEmpty()) {
            return !(mc.world.getBlockState(this.method33979().down()).getBlock() instanceof FenceBlock) && this.method33988(this.method33979()) && this.method33988(this.method33979().up());
        } else {
            return false;
        }
    }

    public boolean method33981() {
        return this.method33980() && this.method33988(this.method33979().up(2));
    }

    public boolean method33982() {
        return this.method33981() && this.method33988(this.method33979().up(3));
    }

    public boolean method33983() {
        return this.method33980() && this.method33990(this.method33979().down());
    }

    public static boolean method33984(BlockPos var0) {
        for (ProjectileDirection var6 : directions) {
            BlockState var7 = mc.world.getBlockState(var0.add(var6.x, var6.y, var6.z));
            if (var7.getBlock() == Blocks.WATER || var7.getBlock() == Blocks.LAVA) {
                return true;
            }
        }

        return false;
    }

    public static boolean method33985(BlockPos var0) {
        for (ProjectileDirection var6 : directions) {
            BlockState var7 = mc.world.getBlockState(var0.add(var6.x, var6.y, var6.z));
            if (var7.isAir()) {
                return false;
            }
        }

        return true;
    }

    public boolean method33986() {
        for (ProjectileDirection var6 : directions) {
            BlockState var7 = mc.world.getBlockState(this.method33979().add(var6.x, var6.y, var6.z));
            if (var7.getBlock() == Blocks.WATER || var7.getBlock() == Blocks.LAVA) {
                return true;
            }
        }

        for (ProjectileDirection var11 : directions) {
            BlockState var12 = mc.world.getBlockState(this.method33979().add(var11.x, var11.y + 1.0, var11.z));
            if (var12.getBlock() == Blocks.WATER || var12.getBlock() == Blocks.LAVA) {
                return true;
            }
        }

        return mc.world.getBlockState(this.method33979().up(2)).getBlock() instanceof FallingBlock
                && !this.field41869.field44283.contains(this.method33979().up(2).toLong());
    }

    public boolean method33987() {
        return this.method33986()
                || mc.world.getBlockState(this.method33979()).getBlock() == Blocks.BEDROCK
                || mc.world.getBlockState(this.method33979().up()).getBlock() == Blocks.BEDROCK;
    }

    public boolean method33988(BlockPos var1) {
        return this.field41869.field44283.contains(var1.toLong()) || Minecraft.getInstance().world.getBlockState(var1).getCollisionShape(mc.world, var1).isEmpty();
    }

    public boolean method33989() {
        return this.method33990(this.method33979());
    }

    public boolean method33990(BlockPos var1) {
        if (!this.field41869.field44283.contains(this.method33979().toLong())) {
            VoxelShape var4 = Minecraft.getInstance().world.getBlockState(var1).getCollisionShape(mc.world, var1);
            if (var4.isEmpty()) {
                return false;
            } else {
                AxisAlignedBB var5 = var4.getBoundingBox();
                return var5.getYSize() >= 0.9 && var5.getYSize() <= 1.0;
            }
        } else {
            return false;
        }
    }
}
