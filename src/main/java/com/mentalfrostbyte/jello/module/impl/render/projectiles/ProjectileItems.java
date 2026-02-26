package com.mentalfrostbyte.jello.module.impl.render.projectiles;

import com.mentalfrostbyte.jello.module.impl.render.Projectiles;
import net.minecraft.entity.Entity;
import net.minecraft.item.BowItem;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.util.EntityPredicates;
import net.minecraft.util.math.*;
import net.minecraft.util.math.vector.Vector3d;

import java.util.ArrayList;
import java.util.List;

public enum ProjectileItems {
    BOW(Items.BOW, 0.0F, 3.0F, 0.0F),
    SNOWBALL(Items.SNOWBALL, 0.0F, 1.875F, 0.0F),
    ENDER_PEARL(Items.ENDER_PEARL, 0.0F, 1.875F, 0.0F),
    EGG(Items.EGG, 0.0F, 1.875F, 0.0F),
    SPLASH_POTION(Items.SPLASH_POTION, 0.0F, 0.5F, 0.0F),
    EXPERIENCE_BOTTLE(Items.EXPERIENCE_BOTTLE, 0.0F, 0.6F, 0.0F),
    TRIDENT(Items.TRIDENT, 0.0F, 2.5F, 0.0F);

    private final Item item;
    private final float posX;
    private final float posY;
    private final float posZ;
    public double traceX;
    public double traceY;
    public double traceZ;
    public float traceXOffset;
    public float traceYOffset;
    public float traceZOffset;
    public RayTraceResult rayTraceResult;
    public Entity hit;

    ProjectileItems(Item item, float posX, float posY, float posZ) {
        this.item = item;
        this.posX = posX;
        this.posY = posY;
        this.posZ = posZ;
    }

    public float getPosY() {
        if (!this.item.equals(Items.BOW)) {
            return this.posY;
        } else {
            return !(this.posY * BowItem.getArrowVelocity(Projectiles.mc.player.getItemInUseCount()) > 0.0F)
                    ? BowItem.getArrowVelocity(20)
                    : BowItem.getArrowVelocity(Projectiles.mc.player.getItemInUseCount());
        }
    }

    public float getPosX() {
        return this.posX;
    }

    public float getPosZ() {
        return this.posZ;
    }

    public Item getItem() {
        return this.item;
    }

    public static ProjectileItems getProjectileThingyForItem(Item item) {
        for (ProjectileItems var6 : values()) {
            if (var6.getItem().equals(item)) {
                return var6;
            }
        }

        return null;
    }

    public List<TraceThing> getTraceThings() {
        ArrayList<TraceThing> list = new ArrayList<>();
		assert Projectiles.mc.player != null;
		float var4 = (float)Math.toRadians(Projectiles.mc.player.rotationYaw);
        float var5 = (float)Math.toRadians(Projectiles.mc.player.rotationPitch);
        double var6 = Projectiles.mc.player.lastTickPosX
                + (Projectiles.mc.player.getPosX() - Projectiles.mc.player.lastTickPosX)
                * (double) Projectiles.mc.timer.renderPartialTicks;
        double var8 = Projectiles.mc.player.lastTickPosY
                + (Projectiles.mc.player.getPosY() - Projectiles.mc.player.lastTickPosY)
                * (double) Projectiles.mc.timer.renderPartialTicks;
        double var10 = Projectiles.mc.player.lastTickPosZ
                + (Projectiles.mc.player.getPosZ() - Projectiles.mc.player.lastTickPosZ)
                * (double) Projectiles.mc.timer.renderPartialTicks;
        this.traceX = var6;
        this.traceY = var8 + (double) Projectiles.mc.player.getEyeHeight() - 0.1F;
        this.traceZ = var10;
        float var12 = Math.min(20.0F, (float)(72000 - Projectiles.mc.player.getItemInUseCount()) + Projectiles.mc.getRenderPartialTicks()) / 20.0F;
        this.traceXOffset = -MathHelper.sin(var4) * MathHelper.cos(var5) * this.posY * var12;
        this.traceYOffset = -MathHelper.sin(var5) * this.posY * var12;
        this.traceZOffset = MathHelper.cos(var4) * MathHelper.cos(var5) * this.posY * var12;
        this.rayTraceResult = null;
        this.hit = null;
        list.add(new TraceThing(this.traceX, this.traceY, this.traceZ));

        while (this.rayTraceResult == null && this.hit == null && this.traceY > 0.0) {
            Vector3d startVec = new Vector3d(this.traceX, this.traceY, this.traceZ);
            Vector3d endVec = new Vector3d(
                    this.traceX + (double)this.traceXOffset, this.traceY + (double)this.traceYOffset, this.traceZ + (double)this.traceZOffset
            );
            float size = (float)(!(this.item instanceof BowItem) ? 0.25 : 0.3);
            AxisAlignedBB boundingBox = new AxisAlignedBB(
                    this.traceX - (double)size,
                    this.traceY - (double)size,
                    this.traceZ - (double)size,
                    this.traceX + (double)size,
                    this.traceY + (double)size,
                    this.traceZ + (double)size
            );
			assert Projectiles.mc.world != null;
			List<Entity> entities = Projectiles.mc
                    .world
                    .getEntitiesInAABBexcluding(
                            Projectiles.mc.player,
                            boundingBox.offset(
                                    this.traceXOffset,
                                    this.traceYOffset,
                                    this.traceZOffset
                            ).grow(1.0, 1.0, 1.0),
                            EntityPredicates.NOT_SPECTATING
                                    .and(new ProjectileThingyPredicate(this, size, startVec, endVec))
                    );
            if (!entities.isEmpty()) {
                for (Entity entity : entities) {
                    this.hit = entity;
                }
                break;
            }

            BlockRayTraceResult trace = Projectiles.mc
                    .world
                    .rayTraceBlocks(
                            new RayTraceContext(
                                    startVec, endVec,
                                    RayTraceContext.BlockMode.COLLIDER, RayTraceContext.FluidMode.NONE,
                                    Projectiles.mc.player
                            )
                    );
            if (trace != null && trace.getType() != RayTraceResult.Type.MISS) {
                this.rayTraceResult = trace;
                this.traceX = this.rayTraceResult.getHitVec().x;
                this.traceY = this.rayTraceResult.getHitVec().y;
                this.traceZ = this.rayTraceResult.getHitVec().z;
                list.add(new TraceThing(this.traceX, this.traceY, this.traceZ));
                break;
            }

            float offsetMultiplier = 0.99F;
            float yOffsetDecrement = 0.05F;
            this.traceX = this.traceX + (double)this.traceXOffset;
            this.traceY = this.traceY + (double)this.traceYOffset;
            this.traceZ = this.traceZ + (double)this.traceZOffset;
            list.add(new TraceThing(this.traceX, this.traceY, this.traceZ));
            this.traceXOffset *= offsetMultiplier;
            this.traceYOffset *= offsetMultiplier;
            this.traceZOffset *= offsetMultiplier;
            this.traceYOffset -= yOffsetDecrement;
        }

        return list;
    }
}
