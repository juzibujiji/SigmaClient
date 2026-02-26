package com.mentalfrostbyte.jello.module.impl.render.projectiles;
import com.google.common.base.Predicate;
import com.mentalfrostbyte.Client;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.vector.Vector3d;

public class ProjectileThingyPredicate implements Predicate<Entity> {
    private static String[] field537;
    public final float field538;
    public final Vector3d field539;
    public final Vector3d field540;
    public final ProjectileItems field541;

    public ProjectileThingyPredicate(ProjectileItems var1, float var2, Vector3d var3, Vector3d var4) {
        this.field541 = var1;
        this.field538 = var2;
        this.field539 = var3;
        this.field540 = var4;
    }

    public boolean apply(Entity var1) {
        AxisAlignedBB var4 = var1.getBoundingBox().expand(this.field538, this.field538,
				this.field538);
        boolean var5 = var4.intersects(this.field539, this.field540);
        return var1 != null && var1.canBeCollidedWith() && var5 && !Client.getInstance().botManager.isBot(var1);
    }
}