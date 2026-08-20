package net.minecraft.block;

import com.google.common.collect.ImmutableMap;
import java.util.Collection;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nullable;
import net.minecraft.fluid.FluidState;
import net.minecraft.fluid.Fluids;
import net.minecraft.item.BlockItemUseContext;
import net.minecraft.state.BooleanProperty;
import net.minecraft.state.StateContainer;
import net.minecraft.state.properties.BlockStateProperties;
import net.minecraft.util.Direction;
import net.minecraft.util.Mirror;
import net.minecraft.util.Rotation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.shapes.ISelectionContext;
import net.minecraft.util.math.shapes.VoxelShape;
import net.minecraft.util.math.shapes.VoxelShapes;
import net.minecraft.world.IBlockReader;
import net.minecraft.world.IWorld;
import net.minecraft.world.IWorldReader;

/**
 * 1.21.11 backport of {@code net.minecraft.world.level.block.MultifaceBlock} - the shared base for
 * blocks that stick to any of a block's six faces ({@code glow_lichen}, {@code sculk_vein},
 * {@code resin_clump}).
 *
 * <p>1.16.4 has no {@code MultifaceBlock}. Its {@link VineBlock} is NOT a usable base: it exposes
 * only {@code up/north/east/south/west} (no {@code down}), has no {@code waterlogged}, and its
 * attachment/growth rules are the pre-1.17 vine rules (vines may hang off other vines, spread by
 * random tick, etc.). Reusing it would give 32 states with the wrong property set instead of the
 * official 128. This class is therefore a direct port of the official {@code MultifaceBlock},
 * borrowing only the six {@code BooleanProperty} constants from {@link SixWayBlock} (whose
 * {@code FACING_TO_PROPERTY_MAP} does contain all six directions, unlike VineBlock's filtered copy).
 *
 * <p>State properties (official blocks.json, 128 states):
 * {@code down/east/north/south/up/west=[true,false]}, {@code waterlogged=[true,false]}.
 *
 * <p>Shapes: official is {@code Shapes.rotateAll(Block.boxZ(16.0, 0.0, 1.0))} unioned over the
 * enabled faces, falling back to a full cube when no face is set. {@code Block.boxZ(16, 0, 1)}
 * expands (Block.java:190-202) to {@code box(0, 0, 0, 16, 16, 1)}, i.e. a 1px slab on the north
 * face; the other five are its rotations. (These are the same five slabs 1.16.4's VineBlock uses,
 * plus the missing {@code down} one.)
 */
public class ModernMultifaceBlock extends Block implements IWaterLoggable
{
    public static final BooleanProperty WATERLOGGED = BlockStateProperties.WATERLOGGED;
    private static final Map<Direction, BooleanProperty> PROPERTY_BY_DIRECTION = SixWayBlock.FACING_TO_PROPERTY_MAP;
    protected static final Direction[] DIRECTIONS = Direction.values();
    private static final VoxelShape DOWN_AABB = Block.makeCuboidShape(0.0D, 0.0D, 0.0D, 16.0D, 1.0D, 16.0D);
    private static final VoxelShape UP_AABB = Block.makeCuboidShape(0.0D, 15.0D, 0.0D, 16.0D, 16.0D, 16.0D);
    private static final VoxelShape NORTH_AABB = Block.makeCuboidShape(0.0D, 0.0D, 0.0D, 16.0D, 16.0D, 1.0D);
    private static final VoxelShape SOUTH_AABB = Block.makeCuboidShape(0.0D, 0.0D, 15.0D, 16.0D, 16.0D, 16.0D);
    private static final VoxelShape WEST_AABB = Block.makeCuboidShape(0.0D, 0.0D, 0.0D, 1.0D, 16.0D, 16.0D);
    private static final VoxelShape EAST_AABB = Block.makeCuboidShape(15.0D, 0.0D, 0.0D, 16.0D, 16.0D, 16.0D);
    private final Map<BlockState, VoxelShape> shapesCache;
    private final boolean canRotate;
    private final boolean canMirrorX;
    private final boolean canMirrorZ;

    public ModernMultifaceBlock(AbstractBlock.Properties properties)
    {
        super(properties);
        BlockState blockstate = this.stateContainer.getBaseState().with(WATERLOGGED, Boolean.valueOf(false));

        for (BooleanProperty booleanproperty : PROPERTY_BY_DIRECTION.values())
        {
            if (blockstate.hasProperty(booleanproperty))
            {
                blockstate = blockstate.with(booleanproperty, Boolean.valueOf(false));
            }
        }

        this.setDefaultState(blockstate);
        ImmutableMap.Builder<BlockState, VoxelShape> builder = ImmutableMap.builder();

        for (BlockState blockstate1 : this.stateContainer.getValidStates())
        {
            builder.put(blockstate1, makeShapeForState(blockstate1));
        }

        this.shapesCache = builder.build();
        int i = 0;
        int j = 0;
        int k = 0;

        for (Direction direction : Direction.Plane.HORIZONTAL)
        {
            if (this.isFaceSupported(direction))
            {
                ++i;

                if (direction.getAxis() == Direction.Axis.X)
                {
                    ++j;
                }
                else
                {
                    ++k;
                }
            }
        }

        this.canRotate = i == 4;
        this.canMirrorX = j % 2 == 0;
        this.canMirrorZ = k % 2 == 0;
    }

    private static VoxelShape shapeForFace(Direction direction)
    {
        switch (direction)
        {
            case DOWN:
                return DOWN_AABB;

            case UP:
                return UP_AABB;

            case NORTH:
                return NORTH_AABB;

            case SOUTH:
                return SOUTH_AABB;

            case WEST:
                return WEST_AABB;

            case EAST:
            default:
                return EAST_AABB;
        }
    }

    private static VoxelShape makeShapeForState(BlockState state)
    {
        VoxelShape voxelshape = VoxelShapes.empty();

        for (Direction direction : DIRECTIONS)
        {
            if (hasFace(state, direction))
            {
                voxelshape = VoxelShapes.or(voxelshape, shapeForFace(direction));
            }
        }

        return voxelshape.isEmpty() ? VoxelShapes.fullCube() : voxelshape;
    }

    public static Set<Direction> availableFaces(BlockState state)
    {
        if (!(state.getBlock() instanceof ModernMultifaceBlock))
        {
            return EnumSet.noneOf(Direction.class);
        }
        else
        {
            Set<Direction> set = EnumSet.noneOf(Direction.class);

            for (Direction direction : DIRECTIONS)
            {
                if (hasFace(state, direction))
                {
                    set.add(direction);
                }
            }

            return set;
        }
    }

    public static Set<Direction> unpack(byte packed)
    {
        Set<Direction> set = EnumSet.noneOf(Direction.class);

        for (Direction direction : DIRECTIONS)
        {
            if ((packed & (byte)(1 << direction.ordinal())) > 0)
            {
                set.add(direction);
            }
        }

        return set;
    }

    public static byte pack(Collection<Direction> faces)
    {
        byte b0 = 0;

        for (Direction direction : faces)
        {
            b0 = (byte)(b0 | 1 << direction.ordinal());
        }

        return b0;
    }

    protected boolean isFaceSupported(Direction direction)
    {
        return true;
    }

    protected void fillStateContainer(StateContainer.Builder<Block, BlockState> builder)
    {
        for (Direction direction : DIRECTIONS)
        {
            if (this.isFaceSupported(direction))
            {
                builder.add(getFaceProperty(direction));
            }
        }

        builder.add(WATERLOGGED);
    }

    public BlockState updatePostPlacement(BlockState stateIn, Direction facing, BlockState facingState, IWorld worldIn, BlockPos currentPos, BlockPos facingPos)
    {
        if (stateIn.get(WATERLOGGED))
        {
            worldIn.getPendingFluidTicks().scheduleTick(currentPos, Fluids.WATER, Fluids.WATER.getTickRate(worldIn));
        }

        if (!hasAnyFace(stateIn))
        {
            return Blocks.AIR.getDefaultState();
        }
        else
        {
            return hasFace(stateIn, facing) && !canAttachTo(worldIn, facing, facingPos, facingState) ? removeFace(stateIn, getFaceProperty(facing)) : stateIn;
        }
    }

    public FluidState getFluidState(BlockState state)
    {
        return state.get(WATERLOGGED) ? Fluids.WATER.getStillFluidState(false) : super.getFluidState(state);
    }

    public VoxelShape getShape(BlockState state, IBlockReader worldIn, BlockPos pos, ISelectionContext context)
    {
        return this.shapesCache.get(state);
    }

    public boolean isValidPosition(BlockState state, IWorldReader worldIn, BlockPos pos)
    {
        boolean flag = false;

        for (Direction direction : DIRECTIONS)
        {
            if (hasFace(state, direction))
            {
                if (!canAttachTo(worldIn, pos, direction))
                {
                    return false;
                }

                flag = true;
            }
        }

        return flag;
    }

    public boolean isReplaceable(BlockState state, BlockItemUseContext useContext)
    {
        return useContext.getItem().getItem() != this.asItem() || hasAnyVacantFace(state);
    }

    @Nullable
    public BlockState getStateForPlacement(BlockItemUseContext context)
    {
        IWorldReader iworldreader = context.getWorld();
        BlockPos blockpos = context.getPos();
        BlockState blockstate = context.getWorld().getBlockState(blockpos);

        for (Direction direction : context.getNearestLookingDirections())
        {
            BlockState blockstate1 = this.getStateForPlacement(blockstate, iworldreader, blockpos, direction);

            if (blockstate1 != null)
            {
                return blockstate1;
            }
        }

        return null;
    }

    public boolean isValidStateForPlacement(IBlockReader reader, BlockState state, BlockPos pos, Direction direction)
    {
        if (this.isFaceSupported(direction) && (!state.isIn(this) || !hasFace(state, direction)))
        {
            BlockPos blockpos = pos.offset(direction);
            return canAttachTo(reader, direction, blockpos, reader.getBlockState(blockpos));
        }
        else
        {
            return false;
        }
    }

    @Nullable
    public BlockState getStateForPlacement(BlockState state, IBlockReader reader, BlockPos pos, Direction direction)
    {
        if (!this.isValidStateForPlacement(reader, state, pos, direction))
        {
            return null;
        }
        else
        {
            BlockState blockstate;

            if (state.isIn(this))
            {
                blockstate = state;
            }
            else if (state.getFluidState().isSource() && state.getFluidState().getFluid() == Fluids.WATER)
            {
                blockstate = this.getDefaultState().with(WATERLOGGED, Boolean.valueOf(true));
            }
            else
            {
                blockstate = this.getDefaultState();
            }

            return blockstate.with(getFaceProperty(direction), Boolean.valueOf(true));
        }
    }

    public BlockState rotate(BlockState state, Rotation rot)
    {
        if (!this.canRotate)
        {
            return state;
        }
        else
        {
            BlockState blockstate = state;

            for (Direction direction : DIRECTIONS)
            {
                if (this.isFaceSupported(direction))
                {
                    blockstate = blockstate.with(getFaceProperty(rot.rotate(direction)), state.get(getFaceProperty(direction)));
                }
            }

            return blockstate;
        }
    }

    public BlockState mirror(BlockState state, Mirror mirrorIn)
    {
        if (mirrorIn == Mirror.FRONT_BACK && !this.canMirrorX)
        {
            return state;
        }
        else if (mirrorIn == Mirror.LEFT_RIGHT && !this.canMirrorZ)
        {
            return state;
        }
        else
        {
            BlockState blockstate = state;

            for (Direction direction : DIRECTIONS)
            {
                if (this.isFaceSupported(direction))
                {
                    blockstate = blockstate.with(getFaceProperty(mirrorIn.mirror(direction)), state.get(getFaceProperty(direction)));
                }
            }

            return blockstate;
        }
    }

    public static boolean hasFace(BlockState state, Direction direction)
    {
        BooleanProperty booleanproperty = getFaceProperty(direction);
        return state.hasProperty(booleanproperty) && state.get(booleanproperty);
    }

    public static boolean canAttachTo(IBlockReader reader, BlockPos pos, Direction direction)
    {
        BlockPos blockpos = pos.offset(direction);
        return canAttachTo(reader, direction, blockpos, reader.getBlockState(blockpos));
    }

    /**
     * Official: {@code Block.isFaceFull(state.getBlockSupportShape(..), dir.getOpposite())
     * || Block.isFaceFull(state.getCollisionShape(..), dir.getOpposite())}.
     * 1.16.4's name for {@code getBlockSupportShape} is {@code getRenderShapeTrue}
     * (AbstractBlock.java:498, defaulting to {@code state.getShape}) and {@code isFaceFull} is
     * {@code Block.doesSideFillSquare}.
     */
    public static boolean canAttachTo(IBlockReader reader, Direction direction, BlockPos pos, BlockState state)
    {
        return Block.doesSideFillSquare(state.getRenderShapeTrue(reader, pos), direction.getOpposite()) || Block.doesSideFillSquare(state.getCollisionShape(reader, pos), direction.getOpposite());
    }

    private static BlockState removeFace(BlockState state, BooleanProperty property)
    {
        BlockState blockstate = state.with(property, Boolean.valueOf(false));
        return hasAnyFace(blockstate) ? blockstate : Blocks.AIR.getDefaultState();
    }

    public static BooleanProperty getFaceProperty(Direction direction)
    {
        return PROPERTY_BY_DIRECTION.get(direction);
    }

    protected static boolean hasAnyFace(BlockState state)
    {
        for (Direction direction : DIRECTIONS)
        {
            if (hasFace(state, direction))
            {
                return true;
            }
        }

        return false;
    }

    private static boolean hasAnyVacantFace(BlockState state)
    {
        for (Direction direction : DIRECTIONS)
        {
            if (!hasFace(state, direction))
            {
                return true;
            }
        }

        return false;
    }
}
