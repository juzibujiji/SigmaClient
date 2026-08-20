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
 * 1.21.11 backport of {@code net.minecraft.world.level.block.CaveVinesPlantBlock} (the body segment
 * of a glow berry vine).
 *
 * <p>State properties (official blocks.json, 2 states): {@code berries=[true,false]} only - the
 * official {@code createBlockStateDefinition} deliberately does NOT call super, so there is no
 * {@code age} property on the body block.
 *
 * <p>Official constructor: {@code super(props, Direction.DOWN, SHAPE, false)}.
 */
public class ModernCaveVinesPlantBlock extends AbstractBodyPlantBlock implements ModernCaveVines
{
    /** See {@link ModernCaveVinesBlock#instance}. Both blocks must be registered. */
    static ModernCaveVinesPlantBlock instance;

    public ModernCaveVinesPlantBlock(AbstractBlock.Properties properties)
    {
        super(properties, Direction.DOWN, ModernCaveVines.SHAPE, false);
        this.setDefaultState(this.stateContainer.getBaseState().with(BERRIES, Boolean.valueOf(false)));
        instance = this;
    }

    protected AbstractTopPlantBlock getTopPlantBlock()
    {
        return ModernCaveVinesBlock.instance;
    }

    /** Official {@code updateHeadAfterConvertedFromBody}: carry the {@code berries} value over. */
    protected BlockState updateHeadAfterConvertedFromBody(BlockState body, BlockState head)
    {
        return head.with(BERRIES, body.get(BERRIES));
    }

    /**
     * Ported from 1.21.11 {@code GrowingPlantBodyBlock#updateShape}; 1.16.4's
     * {@code AbstractBodyPlantBlock} has no {@code updateHeadAfterConvertedFromBody} hook.
     */
    public BlockState updatePostPlacement(BlockState stateIn, Direction facing, BlockState facingState, IWorld worldIn, BlockPos currentPos, BlockPos facingPos)
    {
        if (facing == this.growthDirection.getOpposite() && !stateIn.isValidPosition(worldIn, currentPos))
        {
            worldIn.getPendingBlockTicks().scheduleTick(currentPos, this, 1);
        }

        AbstractTopPlantBlock abstracttopplantblock = this.getTopPlantBlock();

        if (facing == this.growthDirection && !facingState.isIn(this) && !facingState.isIn(abstracttopplantblock))
        {
            return this.updateHeadAfterConvertedFromBody(stateIn, abstracttopplantblock.grow(worldIn));
        }
        else
        {
            if (this.breaksInWater)
            {
                worldIn.getPendingFluidTicks().scheduleTick(currentPos, Fluids.WATER, Fluids.WATER.getTickRate(worldIn));
            }

            // Official falls through to Block#updateShape, a plain "return state"
            // (AbstractBlock.java:120-122).
            return stateIn;
        }
    }

    /** Official {@code getCloneItemStack}: {@code new ItemStack(Items.GLOW_BERRIES)}. */
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
