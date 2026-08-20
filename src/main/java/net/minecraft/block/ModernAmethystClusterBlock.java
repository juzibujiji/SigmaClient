package net.minecraft.block;

import java.util.Map;
import javax.annotation.Nullable;
import com.google.common.collect.Maps;
import net.minecraft.fluid.FluidState;
import net.minecraft.fluid.Fluids;
import net.minecraft.item.BlockItemUseContext;
import net.minecraft.state.DirectionProperty;
import net.minecraft.state.BooleanProperty;
import net.minecraft.state.StateContainer;
import net.minecraft.state.properties.BlockStateProperties;
import net.minecraft.util.Direction;
import net.minecraft.util.Mirror;
import net.minecraft.util.Rotation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.shapes.ISelectionContext;
import net.minecraft.util.math.shapes.VoxelShape;
import net.minecraft.world.IBlockReader;
import net.minecraft.world.IWorld;
import net.minecraft.world.IWorldReader;

/**
 * 1.21.11 backport of {@code net.minecraft.world.level.block.AmethystClusterBlock}.
 *
 * <p>Backs {@code amethyst_cluster}, {@code large_amethyst_bud}, {@code medium_amethyst_bud} and
 * {@code small_amethyst_bud}.
 *
 * <p>State properties (official blocks.json, 12 states each):
 * {@code facing=[north,east,south,west,up,down]}, {@code waterlogged=[true,false]}.
 *
 * <p>Official constructor args (1.21.11 Blocks.java):
 * <pre>
 * amethyst_cluster     new AmethystClusterBlock(7.0F, 10.0F, ...)  lightLevel 5
 * large_amethyst_bud   new AmethystClusterBlock(5.0F, 10.0F, ...)  lightLevel 4
 * medium_amethyst_bud  new AmethystClusterBlock(4.0F, 10.0F, ...)  lightLevel 2
 * small_amethyst_bud   new AmethystClusterBlock(3.0F,  8.0F, ...)  lightLevel 1
 * </pre>
 *
 * <p>Shapes: official is {@code Shapes.rotateAll(Block.boxZ(width, 16.0F - height, 16.0))}.
 * {@code Block.boxZ(w, minZ, maxZ)} expands (Block.java:190-202) to
 * {@code box(8-w/2, 8-w/2, minZ, 8+w/2, 8+w/2, maxZ)}, so with {@code o = 8 - width/2} the six
 * rotations are the explicit boxes built below (identical to the pre-1.21 explicit form where
 * {@code o} was the "aabbOffset" constructor argument).
 */
public class ModernAmethystClusterBlock extends ModernAmethystBlock implements IWaterLoggable
{
    public static final BooleanProperty WATERLOGGED = BlockStateProperties.WATERLOGGED;
    public static final DirectionProperty FACING = BlockStateProperties.FACING;
    private final Map<Direction, VoxelShape> shapes;

    public ModernAmethystClusterBlock(float height, float width, AbstractBlock.Properties properties)
    {
        super(properties);
        this.setDefaultState(this.stateContainer.getBaseState().with(WATERLOGGED, Boolean.valueOf(false)).with(FACING, Direction.UP));
        double o = 8.0D - (double)width / 2.0D;
        double h = (double)height;
        Map<Direction, VoxelShape> map = Maps.newEnumMap(Direction.class);
        map.put(Direction.UP, Block.makeCuboidShape(o, 0.0D, o, 16.0D - o, h, 16.0D - o));
        map.put(Direction.DOWN, Block.makeCuboidShape(o, 16.0D - h, o, 16.0D - o, 16.0D, 16.0D - o));
        map.put(Direction.NORTH, Block.makeCuboidShape(o, o, 16.0D - h, 16.0D - o, 16.0D - o, 16.0D));
        map.put(Direction.SOUTH, Block.makeCuboidShape(o, o, 0.0D, 16.0D - o, 16.0D - o, h));
        map.put(Direction.EAST, Block.makeCuboidShape(0.0D, o, o, h, 16.0D - o, 16.0D - o));
        map.put(Direction.WEST, Block.makeCuboidShape(16.0D - h, o, o, 16.0D, 16.0D - o, 16.0D - o));
        this.shapes = map;
    }

    /**
     * Convenience factory so registration code can build any of the four amethyst growths from its
     * registry id without repeating the official size table.
     */
    public static ModernAmethystClusterBlock forId(String id, AbstractBlock.Properties properties)
    {
        if ("large_amethyst_bud".equals(id))
        {
            return new ModernAmethystClusterBlock(5.0F, 10.0F, properties);
        }
        else if ("medium_amethyst_bud".equals(id))
        {
            return new ModernAmethystClusterBlock(4.0F, 10.0F, properties);
        }
        else if ("small_amethyst_bud".equals(id))
        {
            return new ModernAmethystClusterBlock(3.0F, 8.0F, properties);
        }
        else
        {
            return new ModernAmethystClusterBlock(7.0F, 10.0F, properties);
        }
    }

    public VoxelShape getShape(BlockState state, IBlockReader worldIn, BlockPos pos, ISelectionContext context)
    {
        return this.shapes.get(state.get(FACING));
    }

    public boolean isValidPosition(BlockState state, IWorldReader worldIn, BlockPos pos)
    {
        Direction direction = state.get(FACING);
        BlockPos blockpos = pos.offset(direction.getOpposite());
        return worldIn.getBlockState(blockpos).isSolidSide(worldIn, blockpos, direction);
    }

    public BlockState updatePostPlacement(BlockState stateIn, Direction facing, BlockState facingState, IWorld worldIn, BlockPos currentPos, BlockPos facingPos)
    {
        if (stateIn.get(WATERLOGGED))
        {
            worldIn.getPendingFluidTicks().scheduleTick(currentPos, Fluids.WATER, Fluids.WATER.getTickRate(worldIn));
        }

        return facing == stateIn.get(FACING).getOpposite() && !stateIn.isValidPosition(worldIn, currentPos) ? Blocks.AIR.getDefaultState() : super.updatePostPlacement(stateIn, facing, facingState, worldIn, currentPos, facingPos);
    }

    @Nullable
    public BlockState getStateForPlacement(BlockItemUseContext context)
    {
        return this.getDefaultState().with(WATERLOGGED, Boolean.valueOf(context.getWorld().getFluidState(context.getPos()).getFluid() == Fluids.WATER)).with(FACING, context.getFace());
    }

    public BlockState rotate(BlockState state, Rotation rot)
    {
        return state.with(FACING, rot.rotate(state.get(FACING)));
    }

    public BlockState mirror(BlockState state, Mirror mirrorIn)
    {
        return state.rotate(mirrorIn.toRotation(state.get(FACING)));
    }

    public FluidState getFluidState(BlockState state)
    {
        return state.get(WATERLOGGED) ? Fluids.WATER.getStillFluidState(false) : super.getFluidState(state);
    }

    protected void fillStateContainer(StateContainer.Builder<Block, BlockState> builder)
    {
        builder.add(WATERLOGGED, FACING);
    }
}
