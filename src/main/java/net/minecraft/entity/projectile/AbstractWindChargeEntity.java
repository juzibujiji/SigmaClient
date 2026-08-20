package net.minecraft.entity.projectile;

import com.mentalfrostbyte.jello.util.game.world.WorldHeightHelper;
import javax.annotation.Nullable;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.particles.IParticleData;
import net.minecraft.util.DamageSource;
import net.minecraft.util.Direction;
import net.minecraft.util.math.BlockRayTraceResult;
import net.minecraft.util.math.EntityRayTraceResult;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.vector.Vector3d;
import net.minecraft.util.math.vector.Vector3i;
import net.minecraft.world.World;

/**
 * Backport of the 1.20.5 wind charge projectile base class.
 *
 * Official source: net/minecraft/world/entity/projectile/hurtingprojectile/windcharge/AbstractWindCharge.java
 * (1.21.11 / MCP-Reborn-release). Official superclass AbstractHurtingProjectile maps to 1.16.4's
 * {@link DamagingProjectileEntity}.
 *
 * Values taken verbatim from the official file:
 *   accelerationPower  = 0.0   -> accelerationX/Y/Z stay 0, the projectile never self-accelerates
 *   getInertia()       = 1.0F  -> {@link #getMotionFactor()} (no drag, flies dead straight)
 *   getLiquidInertia() = getInertia() -> water does not slow it down either
 *   shouldBurn()       = false -> {@link #isFireballFiery()}
 *   getTrailParticle() = null  -> no trail particle
 *   onHitEntity        -> 1.0F damage to the entity hit, then burst at this.position()
 *   onHitBlock         -> burst at hitLocation + faceNormal * 0.25, then discard
 *   tick()             -> burst + discard once more than 30 blocks above the build limit
 */
public abstract class AbstractWindChargeEntity extends DamagingProjectileEntity
{
    protected AbstractWindChargeEntity(EntityType <? extends AbstractWindChargeEntity > typeIn, World worldIn)
    {
        super(typeIn, worldIn);
        // Official: this.accelerationPower = 0.0;
        this.accelerationX = 0.0D;
        this.accelerationY = 0.0D;
        this.accelerationZ = 0.0D;
    }

    protected AbstractWindChargeEntity(EntityType <? extends AbstractWindChargeEntity > typeIn, World worldIn, Entity owner, double x, double y, double z)
    {
        this(typeIn, worldIn);
        this.setLocationAndAngles(x, y, z, this.rotationYaw, this.rotationPitch);
        this.recenterBoundingBox();
        this.setShooter(owner);
    }

    protected AbstractWindChargeEntity(EntityType <? extends AbstractWindChargeEntity > typeIn, World worldIn, double x, double y, double z, Vector3d motion)
    {
        this(typeIn, worldIn);
        this.setLocationAndAngles(x, y, z, this.rotationYaw, this.rotationPitch);
        this.recenterBoundingBox();
        this.setMotion(motion);
    }

    /**
     * Official AbstractWindCharge#getInertia() returns 1.0F - a wind charge keeps its full speed
     * (the fireball default is 0.95F).
     */
    protected float getMotionFactor()
    {
        return 1.0F;
    }

    /**
     * Official AbstractWindCharge#getLiquidInertia() returns this.getInertia(), i.e. also 1.0F.
     */
    protected float getLiquidInertia()
    {
        return this.getMotionFactor();
    }

    /**
     * Official AbstractWindCharge#shouldBurn() returns false.
     */
    protected boolean isFireballFiery()
    {
        return false;
    }

    /**
     * Official AbstractWindCharge#getTrailParticle() returns null - a wind charge leaves no trail.
     * {@link DamagingProjectileEntity#tick()} was taught to skip a null particle for this backport.
     */
    @Nullable
    protected IParticleData getParticle()
    {
        return null;
    }

    /**
     * Official AbstractWindCharge#tick():
     *     if (!level.isClientSide() && this.getBlockY() > this.level().getMaxY() + 30) { explode; discard; }
     * WorldHeightHelper.getHighestBuildY() is this project's equivalent of Level#getMaxY().
     */
    public void tick()
    {
        if (!this.world.isRemote && this.getPosition().getY() > WorldHeightHelper.getHighestBuildY() + 30)
        {
            this.explode(this.getPositionVec());
            this.remove();
        }
        else
        {
            super.tick();
        }
    }

    /**
     * Official AbstractWindCharge#canCollideWith / #canHitEntity: wind charges never hit each other, and
     * never hit end crystals. 1.16.4 folds both checks into this single predicate.
     */
    protected boolean func_230298_a_(Entity target)
    {
        if (target instanceof AbstractWindChargeEntity)
        {
            return false;
        }

        return target.getType() != EntityType.END_CRYSTAL && super.func_230298_a_(target);
    }

    /**
     * Official AbstractWindCharge#push(double, double, double) is an empty override, so a wind charge is
     * never pushed around by explosions (including other wind bursts).
     */
    public void addVelocity(double x, double y, double z)
    {
    }

    /**
     * Official AbstractWindCharge#onHitEntity: 1.0F damage to the entity hit, then burst in place.
     */
    protected void onEntityHit(EntityRayTraceResult result)
    {
        super.onEntityHit(result);

        if (!this.world.isRemote)
        {
            Entity entity = result.getEntity();
            Entity owner = this.func_234616_v_();

            if (owner instanceof LivingEntity)
            {
                ((LivingEntity)owner).setLastAttackedEntity(entity);
            }

            // Official: this.damageSources().windCharge(this, livingentity2). 1.16.4 has no wind-charge
            // damage source, so the generic thrown-projectile source (used by snowballs/eggs) stands in.
            entity.attackEntityFrom(DamageSource.causeThrownDamage(this, owner), 1.0F);
            this.explode(this.getPositionVec());
        }
    }

    /**
     * Official AbstractWindCharge#onHitBlock:
     *     Vec3i vec3i = hitResult.getDirection().getUnitVec3i();
     *     Vec3  vec3  = Vec3.atLowerCornerOf(vec3i).multiply(0.25, 0.25, 0.25);
     *     this.explode(hitResult.getLocation().add(vec3));
     *     this.discard();
     * The burst centre is nudged a quarter block out of the face that was hit.
     */
    protected void func_230299_a_(BlockRayTraceResult result)
    {
        super.func_230299_a_(result);

        if (!this.world.isRemote)
        {
            Direction direction = result.getFace();
            Vector3i vector3i = direction.getDirectionVec();
            Vector3d vector3d = Vector3d.copy(vector3i).mul(0.25D, 0.25D, 0.25D);
            this.explode(result.getHitVec().add(vector3d));
            this.remove();
        }
    }

    /**
     * Official AbstractWindCharge#onHit: discard on any hit.
     */
    protected void onImpact(RayTraceResult result)
    {
        super.onImpact(result);

        if (!this.world.isRemote)
        {
            this.remove();
        }
    }

    protected abstract void explode(Vector3d center);
}
