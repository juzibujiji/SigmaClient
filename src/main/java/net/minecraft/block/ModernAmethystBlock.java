package net.minecraft.block;

import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.BlockRayTraceResult;
import net.minecraft.world.World;

/**
 * 1.21.11 backport of {@code net.minecraft.world.level.block.AmethystBlock}.
 *
 * <p>Only behaviour: projectiles hitting the block play the amethyst chime at a random pitch.
 * Official source (1.21.11 AmethystBlock#onProjectileHit):
 * {@code playSound(null, pos, SoundEvents.AMETHYST_BLOCK_CHIME, BLOCKS, 1.0F, 0.5F + random.nextFloat() * 1.2F)}
 *
 * <p>1.16.4 has no {@code block.amethyst_block.chime} SoundEvent, so
 * {@link SoundEvents#BLOCK_NOTE_BLOCK_CHIME} ("block.note_block.chime") is used as the closest
 * vanilla stand-in. Volume/pitch numbers are the official ones.
 */
public class ModernAmethystBlock extends Block
{
    public ModernAmethystBlock(AbstractBlock.Properties properties)
    {
        super(properties);
    }

    public void onProjectileCollision(World worldIn, BlockState state, BlockRayTraceResult hit, ProjectileEntity projectile)
    {
        if (!worldIn.isRemote())
        {
            BlockPos blockpos = hit.getPos();
            // Official: SoundEvents.AMETHYST_BLOCK_CHIME (does not exist in 1.16.4).
            worldIn.playSound((net.minecraft.entity.player.PlayerEntity)null, blockpos, SoundEvents.BLOCK_NOTE_BLOCK_CHIME, SoundCategory.BLOCKS, 1.0F, 0.5F + worldIn.rand.nextFloat() * 1.2F);
        }
    }
}
