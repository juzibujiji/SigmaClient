package net.minecraft.block;

import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.vector.Vector3d;
import net.minecraft.world.IBlockReader;
import net.minecraft.world.World;

public class SlimeBlock extends BreakableBlock
{
    public SlimeBlock(AbstractBlock.Properties properties)
    {
        super(properties);
    }

    /**
     * Block's chance to react to a living entity falling on it.
     */
    public void onFallenUpon(World worldIn, BlockPos pos, Entity entityIn, float fallDistance)
    {
        if (entityIn.isSuppressingBounce())
        {
            super.onFallenUpon(worldIn, pos, entityIn, fallDistance);
        }
        else
        {
            entityIn.onLivingFall(fallDistance, 0.0F);
        }
    }

    /**
     * Called when an Entity lands on this Block. This method *must* update motionY because the entity will not do that
     * on its own
     */
    public void onLanded(IBlockReader worldIn, Entity entityIn)
    {
        if (entityIn.isSuppressingBounce())
        {
            super.onLanded(worldIn, entityIn);
        }
        else
        {
            this.bounceEntity(entityIn);
        }
    }

    private void bounceEntity(Entity entity)
    {
        Vector3d vector3d = entity.getMotion();

        if (vector3d.y < 0.0D)
        {
            double d0 = entity instanceof LivingEntity ? 1.0D : 0.8D;
            entity.setMotion(vector3d.x, -vector3d.y * d0, vector3d.z);
        }
    }

    @Override
    public void onEntityWalk(World worldIn, BlockPos pos, Entity entityIn) {
        double motionY = Math.abs(entityIn.getMotion().y);
        if (motionY < 0.1 && !entityIn.isSteppingCarefully()) {
            double motionAdjust = 0.4 + motionY * 0.2;
            entityIn.setMotion(entityIn.getMotion().mul(motionAdjust, 1.0, motionAdjust));
        }

        super.onEntityWalk(worldIn, pos, entityIn);
    }
}
