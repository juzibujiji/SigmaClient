package com.mentalfrostbyte.jello.module.impl.render.projectiles;

import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.vector.Vector3i;

public class ProjectileDirection {
    public double x;
    public double y;
    public double z;

    public ProjectileDirection(double x, double y, double z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public ProjectileDirection(BlockPos blockPos) {
        this.x = blockPos.getX();
        this.y = blockPos.getY();
        this.z = blockPos.getZ();
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

    public ProjectileDirection add(double x, double y, double z) {
        return new ProjectileDirection(this.x + x, this.y + y, this.z + z);
    }

    public ProjectileDirection floored() {
        return new ProjectileDirection(Math.floor(this.x), Math.floor(this.y), Math.floor(this.z));
    }

    public BlockPos toBlockPos() {
        return new BlockPos(Math.floor(this.x), Math.floor(this.y), Math.floor(this.z));
    }

    public double method36626(ProjectileDirection var1) {
        return Math.pow(var1.x - this.x, 2.0)
                + Math.pow(var1.y - this.y, 2.0)
                + Math.pow(var1.z - this.z, 2.0);
    }

    public ProjectileDirection method36627(ProjectileDirection var1) {
        return this.add(var1.getX(), var1.getY(), var1.getZ());
    }

    public Vector3i method36628() {
        return new Vector3i(this.x, this.y, this.z);
    }

    @Override
    public String toString() {
        return "[" + this.x + ";" + this.y + ";" + this.z + "]";
    }

    public ProjectileDirection add(ProjectileDirection dir) {
        return this.add(-dir.getX(), -dir.getY(), -dir.getZ());
    }

    public ProjectileDirection method36630(ProjectileDirection dir) {
        return new ProjectileDirection(
                this.y * dir.z - this.z * dir.y,
                this.z * dir.x - this.x * dir.z,
                this.x * dir.y - this.y * dir.x
        );
    }

    public ProjectileDirection method36631() {
        double mag = Math.sqrt(this.x * this.x + this.y * this.y + this.z * this.z);
        return !(mag < 1.0E-4) ? new ProjectileDirection(this.x / mag, this.y / mag, this.z / mag) : new ProjectileDirection(0.0, 0.0, 0.0);
    }
}