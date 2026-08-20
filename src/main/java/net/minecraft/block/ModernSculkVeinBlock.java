package net.minecraft.block;

import java.util.Collection;
import javax.annotation.Nullable;
import net.minecraft.fluid.FluidState;
import net.minecraft.fluid.Fluids;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Direction;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.registry.Registry;
import net.minecraft.world.IBlockReader;
import net.minecraft.world.IWorld;

/**
 * 1.21.11 backport of {@code net.minecraft.world.level.block.SculkVeinBlock}.
 *
 * <p>State properties (official blocks.json, 128 states): inherited unchanged from
 * {@link ModernMultifaceBlock} - {@code down/east/north/south/up/west=[true,false]},
 * {@code waterlogged=[true,false]}. No light emission.
 *
 * <p>What is NOT ported: the sculk charge system. Official {@code SculkVeinBlock} implements
 * {@code SculkBehaviour} ({@code attemptUseCharge} / {@code onDischarged} / {@code attemptPlaceSculk}
 * / {@code hasSubstrateAccess}), which needs {@code SculkSpreader}, {@code SculkBlock},
 * {@code SculkCatalystBlockEntity} and the {@code minecraft:sculk_replaceable} block tag - none of
 * which exist in 1.16.4. Placement, attachment, shape, waterlogging, rotation/mirroring and the
 * vein spreader configuration are ported; charge-driven growth is not.
 */
public class ModernSculkVeinBlock extends ModernMultifaceBlock
{
    /** See {@link #regrow}: {@code Blocks.SCULK_VEIN} has no 1.16.4 counterpart to reference. */
    static ModernSculkVeinBlock instance;
    private static Block sculkBlock;
    private static Block sculkCatalystBlock;
    private final ModernMultifaceSpreader veinSpreader = new ModernMultifaceSpreader(new ModernSculkVeinBlock.SculkVeinSpreaderConfig(ModernMultifaceSpreader.DEFAULT_SPREAD_ORDER));
    private final ModernMultifaceSpreader sameSpaceSpreader = new ModernMultifaceSpreader(new ModernSculkVeinBlock.SculkVeinSpreaderConfig(ModernMultifaceSpreader.SpreadType.SAME_POSITION));

    public ModernSculkVeinBlock(AbstractBlock.Properties properties)
    {
        super(properties);
        instance = this;
    }

    public ModernMultifaceSpreader getSpreader()
    {
        return this.veinSpreader;
    }

    public ModernMultifaceSpreader getSameSpaceSpreader()
    {
        return this.sameSpaceSpreader;
    }

    /** Official {@code SculkVeinBlock.regrow}. */
    public static boolean regrow(IWorld world, BlockPos pos, BlockState oldState, Collection<Direction> faces)
    {
        if (instance == null)
        {
            return false;
        }

        boolean flag = false;
        BlockState blockstate = instance.getDefaultState();

        for (Direction direction : faces)
        {
            if (canAttachTo(world, pos, direction))
            {
                blockstate = blockstate.with(getFaceProperty(direction), Boolean.valueOf(true));
                flag = true;
            }
        }

        if (!flag)
        {
            return false;
        }
        else
        {
            if (!oldState.getFluidState().isEmpty())
            {
                blockstate = blockstate.with(WATERLOGGED, Boolean.valueOf(true));
            }

            world.setBlockState(pos, blockstate, 3);
            return true;
        }
    }

    /** {@code sculk} / {@code sculk_catalyst} are themselves backported blocks; resolve lazily. */
    private static Block sculk()
    {
        if (sculkBlock == null)
        {
            sculkBlock = Registry.BLOCK.getOrDefault(new ResourceLocation("sculk"));
        }

        return sculkBlock;
    }

    private static Block sculkCatalyst()
    {
        if (sculkCatalystBlock == null)
        {
            sculkCatalystBlock = Registry.BLOCK.getOrDefault(new ResourceLocation("sculk_catalyst"));
        }

        return sculkCatalystBlock;
    }

    /** Official {@code SculkVeinBlock.SculkVeinSpreaderConfig}. */
    class SculkVeinSpreaderConfig extends ModernMultifaceSpreader.DefaultSpreaderConfig
    {
        private final ModernMultifaceSpreader.SpreadType[] spreadTypes;

        public SculkVeinSpreaderConfig(ModernMultifaceSpreader.SpreadType... spreadTypes)
        {
            super(ModernSculkVeinBlock.this);
            this.spreadTypes = spreadTypes;
        }

        protected boolean stateCanBeReplaced(IBlockReader reader, BlockPos fromPos, BlockPos toPos, Direction face, BlockState state)
        {
            BlockState blockstate = reader.getBlockState(toPos.offset(face));
            Block block = sculk();
            Block block1 = sculkCatalyst();

            if ((block == Blocks.AIR || !blockstate.isIn(block)) && (block1 == Blocks.AIR || !blockstate.isIn(block1)) && !blockstate.isIn(Blocks.MOVING_PISTON))
            {
                if (fromPos.manhattanDistance(toPos) == 2)
                {
                    BlockPos blockpos = fromPos.offset(face.getOpposite());

                    if (reader.getBlockState(blockpos).isSolidSide(reader, blockpos, face))
                    {
                        return false;
                    }
                }

                FluidState fluidstate = state.getFluidState();

                if (!fluidstate.isEmpty() && fluidstate.getFluid() != Fluids.WATER && fluidstate.getFluid() != Fluids.FLOWING_WATER)
                {
                    return false;
                }
                else if (state.isIn(BlockTags.FIRE))
                {
                    return false;
                }
                else
                {
                    // Official: state.canBeReplaced()
                    return state.getMaterial().isReplaceable() || super.stateCanBeReplaced(reader, fromPos, toPos, face, state);
                }
            }
            else
            {
                return false;
            }
        }

        public ModernMultifaceSpreader.SpreadType[] getSpreadTypes()
        {
            return this.spreadTypes;
        }

        public boolean isOtherBlockValidAsSource(BlockState state)
        {
            return !state.isIn(ModernSculkVeinBlock.this);
        }
    }
}
