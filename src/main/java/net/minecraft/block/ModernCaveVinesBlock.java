package net.minecraft.block;

import java.util.Random;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.fluid.Fluids;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.state.StateContainer;
import net.minecraft.util.ActionResultType;
import net.minecraft.util.Direction;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.BlockRayTraceResult;
import net.minecraft.world.IBlockReader;
import net.minecraft.world.IWorld;
import net.minecraft.world.World;
import net.minecraft.world.server.ServerWorld;

/**
 * 1.21.11 backport of {@code net.minecraft.world.level.block.CaveVinesBlock} (the growing head of a
 * glow berry vine).
 *
 * <p>State properties (official blocks.json, 52 states): {@code age=[0..25]} (inherited
 * {@code BlockStateProperties.AGE_0_25}, same serialized name {@code "age"}),
 * {@code berries=[true,false]}.
 *
 * <p>Official constructor: {@code super(props, Direction.DOWN, SHAPE, false, 0.1)} - grows downwards,
 * does not schedule fluid ticks, 0.1 growth chance per random tick.
 * {@code CHANCE_OF_BERRIES_ON_GROWTH = 0.11F}.
 */
public class ModernCaveVinesBlock extends AbstractTopPlantBlock implements ModernCaveVines
{
    /** Official: {@code CaveVinesBlock.CHANCE_OF_BERRIES_ON_GROWTH}. */
    private static final float CHANCE_OF_BERRIES_ON_GROWTH = 0.11F;
    /**
     * The head/body pair reference each other. 1.16.4's {@code AbstractPlantBlock} demands a concrete
     * sibling block instance and this backport must not touch the shared registry classes, so the two
     * classes publish themselves here on construction. Both blocks must be registered.
     */
    static ModernCaveVinesBlock instance;

    public ModernCaveVinesBlock(AbstractBlock.Properties properties)
    {
        super(properties, Direction.DOWN, ModernCaveVines.SHAPE, false, 0.1D);
        this.setDefaultState(this.stateContainer.getBaseState().with(AGE, Integer.valueOf(0)).with(BERRIES, Boolean.valueOf(false)));
        instance = this;
    }

    protected int getGrowthAmount(Random rand)
    {
        // Official getBlocksToGrowWhenBonemealed -> 1
        return 1;
    }

    protected boolean canGrowIn(BlockState state)
    {
        return state.isAir();
    }

    protected Block getBodyPlantBlock()
    {
        return ModernCaveVinesPlantBlock.instance != null ? ModernCaveVinesPlantBlock.instance : this;
    }

    /** Official {@code getGrowIntoState}: {@code state.cycle(AGE).setValue(BERRIES, random.nextFloat() < 0.11F)}. */
    protected BlockState getGrowIntoState(BlockState state, Random rand)
    {
        return state.func_235896_a_(AGE).with(BERRIES, Boolean.valueOf(rand.nextFloat() < CHANCE_OF_BERRIES_ON_GROWTH));
    }

    /**
     * 1.16.4's {@code AbstractTopPlantBlock#randomTick} hardcodes {@code state.cycle(AGE)} with no
     * {@code getGrowIntoState} hook, so the loop is reproduced here to roll for berries.
     */
    public void randomTick(BlockState state, ServerWorld worldIn, BlockPos pos, Random random)
    {
        if (state.get(AGE) < 25 && random.nextDouble() < 0.1D)
        {
            BlockPos blockpos = pos.offset(this.growthDirection);

            if (this.canGrowIn(worldIn.getBlockState(blockpos)))
            {
                worldIn.setBlockState(blockpos, this.getGrowIntoState(state, random));
            }
        }
    }

    /** Official {@code updateBodyAfterConvertedFromHead}: carry the {@code berries} value over. */
    protected BlockState updateBodyAfterConvertedFromHead(BlockState head, BlockState body)
    {
        return body.with(BERRIES, head.get(BERRIES));
    }

    /**
     * Ported from 1.21.11 {@code GrowingPlantHeadBlock#updateShape}. 1.16.4's version lacks both the
     * "there is already vine below me, become a body block" branch and the
     * {@code updateBodyAfterConvertedFromHead} hook, so the whole method is reimplemented.
     */
    public BlockState updatePostPlacement(BlockState stateIn, Direction facing, BlockState facingState, IWorld worldIn, BlockPos currentPos, BlockPos facingPos)
    {
        if (facing == this.growthDirection.getOpposite())
        {
            if (!stateIn.isValidPosition(worldIn, currentPos))
            {
                worldIn.getPendingBlockTicks().scheduleTick(currentPos, this, 1);
            }
            else
            {
                BlockState blockstate = worldIn.getBlockState(currentPos.offset(this.growthDirection));

                if (blockstate.isIn(this) || blockstate.isIn(this.getBodyPlantBlock()))
                {
                    return this.updateBodyAfterConvertedFromHead(stateIn, this.getBodyPlantBlock().getDefaultState());
                }
            }
        }

        if (facing != this.growthDirection || !facingState.isIn(this) && !facingState.isIn(this.getBodyPlantBlock()))
        {
            if (this.breaksInWater)
            {
                worldIn.getPendingFluidTicks().scheduleTick(currentPos, Fluids.WATER, Fluids.WATER.getTickRate(worldIn));
            }

            // Official falls through to Block#updateShape here, which is a plain "return state"
            // (AbstractBlock.java:120-122). AbstractPlantBlock does not override it, and Java cannot
            // express super.super, so the identity result is returned directly.
            return stateIn;
        }
        else
        {
            return this.updateBodyAfterConvertedFromHead(stateIn, this.getBodyPlantBlock().getDefaultState());
        }
    }

    public ItemStack getItem(IBlockReader worldIn, BlockPos pos, BlockState state)
    {
        Item item = ModernCaveVines.getGlowBerriesItem();
        return new ItemStack(item);
    }

    public ActionResultType onBlockActivated(BlockState state, World worldIn, BlockPos pos, PlayerEntity player, Hand handIn, BlockRayTraceResult hit)
    {
        return ModernCaveVines.use(player, state, worldIn, pos);
    }

    protected void fillStateContainer(StateContainer.Builder<Block, BlockState> builder)
    {
        super.fillStateContainer(builder);
        builder.add(BERRIES);
    }

    /** Official {@code isValidBonemealTarget}: only usable while there are no berries. */
    public boolean canGrow(IBlockReader worldIn, BlockPos pos, BlockState state, boolean isClient)
    {
        return !state.get(BERRIES);
    }

    public boolean canUseBonemeal(World worldIn, Random rand, BlockPos pos, BlockState state)
    {
        return true;
    }

    public void grow(ServerWorld worldIn, Random rand, BlockPos pos, BlockState state)
    {
        worldIn.setBlockState(pos, state.with(BERRIES, Boolean.valueOf(true)), 2);
    }
}
