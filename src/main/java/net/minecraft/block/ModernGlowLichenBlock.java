package net.minecraft.block;

import java.util.Random;
import java.util.function.ToIntFunction;
import net.minecraft.util.Direction;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockReader;
import net.minecraft.world.World;
import net.minecraft.world.server.ServerWorld;

/**
 * 1.21.11 backport of {@code net.minecraft.world.level.block.GlowLichenBlock}.
 *
 * <p>State properties (official blocks.json, 128 states): inherited unchanged from
 * {@link ModernMultifaceBlock} - {@code down/east/north/south/up/west=[true,false]},
 * {@code waterlogged=[true,false]}.
 *
 * <p>Official registration uses {@code lightLevel(GlowLichenBlock.emission(7))}, i.e. light 7 as
 * soon as any face is set, 0 otherwise - see {@link #lightFromFaces()}.
 */
public class ModernGlowLichenBlock extends ModernMultifaceBlock implements IGrowable
{
    private final ModernMultifaceSpreader spreader = new ModernMultifaceSpreader(this);

    public ModernGlowLichenBlock(AbstractBlock.Properties properties)
    {
        super(properties);
    }

    /** Official {@code GlowLichenBlock.emission(int)}. */
    public static ToIntFunction<BlockState> emission(final int lightLevel)
    {
        return (state) ->
        {
            return ModernMultifaceBlock.hasAnyFace(state) ? lightLevel : 0;
        };
    }

    /** Convenience alias for the generator's DYNAMIC_LIGHT hook: {@code emission(7)}. */
    public static ToIntFunction<BlockState> lightFromFaces()
    {
        return emission(7);
    }

    public ModernMultifaceSpreader getSpreader()
    {
        return this.spreader;
    }

    /** Official {@code isValidBonemealTarget}. */
    public boolean canGrow(IBlockReader worldIn, BlockPos pos, BlockState state, boolean isClient)
    {
        for (Direction direction : Direction.values())
        {
            if (this.spreader.canSpreadInAnyDirection(state, worldIn, pos, direction.getOpposite()))
            {
                return true;
            }
        }

        return false;
    }

    public boolean canUseBonemeal(World worldIn, Random rand, BlockPos pos, BlockState state)
    {
        return true;
    }

    public void grow(ServerWorld worldIn, Random rand, BlockPos pos, BlockState state)
    {
        this.spreader.spreadFromRandomFaceTowardRandomDirection(state, worldIn, pos, rand);
    }

    public boolean propagatesSkylightDown(BlockState state, IBlockReader reader, BlockPos pos)
    {
        return state.getFluidState().isEmpty();
    }
}
