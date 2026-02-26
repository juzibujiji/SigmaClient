package com.mentalfrostbyte.jello.util.game.world;

import net.minecraft.util.math.AxisAlignedBB;

public class BoundingBox {
    public final double minX;
    public final double minY;
    public final double minZ;
    public final double maxX;
    public final double maxY;
    public final double maxZ;

    public BoundingBox(double x1, double y1, double z1, double x2, double y2, double z2) {
        this.minX = x1;
        this.minY = y1;
        this.minZ = z1;
        this.maxX = x2;
        this.maxY = y2;
        this.maxZ = z2;
    }

    public BoundingBox(AxisAlignedBB axisAlignedBB) {
        this.minX = axisAlignedBB.minX;
        this.minY = axisAlignedBB.minY;
        this.minZ = axisAlignedBB.minZ;
        this.maxX = axisAlignedBB.maxX;
        this.maxY = axisAlignedBB.maxY;
        this.maxZ = axisAlignedBB.maxZ;
    }

    public BoundingBox expand(double amount) {
        return new BoundingBox(
                this.minX - amount,
                this.minY - amount,
                this.minZ - amount,
                this.maxX + amount * 2.0,
                this.maxY + amount * 2.0,
                this.maxZ + amount * 2.0
        );
    }
}
