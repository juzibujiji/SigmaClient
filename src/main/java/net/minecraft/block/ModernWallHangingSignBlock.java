package net.minecraft.block;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.fluid.FluidState;
import net.minecraft.fluid.Fluids;
import net.minecraft.item.BlockItemUseContext;
import net.minecraft.pathfinding.PathType;
import net.minecraft.state.DirectionProperty;
import net.minecraft.state.StateContainer;
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
 * 卡在两面墙之间的悬挂告示牌（1.19 加入），官方类型 {@code wall_hanging_sign}。
 *
 * <p>状态：{@code facing}(north/south/west/east) + {@code waterlogged} = 8 个，
 * 与官方 {@code WallHangingSignBlock.createBlockStateDefinition} 逐一对齐。
 *
 * <p>与普通壁挂告示牌的区别：它不是贴在<b>背后</b>那面墙上，而是靠<b>左右两侧</b>
 * 至少一侧有支撑（官方 {@code canPlace} 查 {@code facing} 的顺时针/逆时针邻居），
 * 所以能跨在门框之间。
 */
public class ModernWallHangingSignBlock extends ModernAbstractHangingSignBlock
{
    /** 官方 WallHangingSignBlock.FACING = HorizontalDirectionalBlock.FACING。 */
    public static final DirectionProperty FACING = HorizontalBlock.HORIZONTAL_FACING;

    /**
     * 顶横梁，官方 {@code Block.column(16.0, 4.0, 14.0, 16.0)}：
     * 4 参版 {@code column(wx, wz, minY, maxY)} =
     * {@code box(8-wx/2, minY, 8-wz/2, 8+wx/2, maxY, 8+wz/2)}
     * = {@code box(0, 14, 6, 16, 16, 10)}。这是 Z 轴（面朝南北）的形态。
     */
    private static final VoxelShape PLANK_Z = Block.makeCuboidShape(0.0D, 14.0D, 6.0D, 16.0D, 16.0D, 10.0D);
    /** 上面那个绕 Y 轴转 90 度，即官方 {@code Shapes.rotateHorizontalAxis} 的 X 轴项。 */
    private static final VoxelShape PLANK_X = Block.makeCuboidShape(6.0D, 14.0D, 0.0D, 10.0D, 16.0D, 16.0D);

    /** 牌面，官方 {@code Block.column(14.0, 2.0, 0.0, 10.0)} = {@code box(1, 0, 7, 15, 10, 9)}。 */
    private static final VoxelShape BOARD_Z = Block.makeCuboidShape(1.0D, 0.0D, 7.0D, 15.0D, 10.0D, 9.0D);
    /** 牌面绕 Y 轴转 90 度。 */
    private static final VoxelShape BOARD_X = Block.makeCuboidShape(7.0D, 0.0D, 1.0D, 9.0D, 10.0D, 15.0D);

    /**
     * 官方 {@code SHAPES_PLANK = Shapes.rotateHorizontalAxis(Block.column(16, 4, 14, 16))}。
     * 只有横梁，用作<b>碰撞箱</b> —— 牌面本身不挡人，可以走过去。
     */
    private static final Map<Direction.Axis, VoxelShape> SHAPES_PLANK = Maps.newEnumMap(ImmutableMap.of(
                Direction.Axis.Z, PLANK_Z,
                Direction.Axis.X, PLANK_X));

    /**
     * 官方 {@code SHAPES = Shapes.rotateHorizontalAxis(Shapes.or(SHAPES_PLANK.get(Z), Block.column(14, 2, 0, 10)))}：
     * 横梁 + 牌面的并集，用作<b>选择框</b>。
     */
    private static final Map<Direction.Axis, VoxelShape> SHAPES = Maps.newEnumMap(ImmutableMap.of(
                Direction.Axis.Z, VoxelShapes.or(PLANK_Z, BOARD_Z),
                Direction.Axis.X, VoxelShapes.or(PLANK_X, BOARD_X)));

    public ModernWallHangingSignBlock(AbstractBlock.Properties properties, WoodType type)
    {
        super(properties, type);
        this.setDefaultState(this.stateContainer.getBaseState().with(FACING, Direction.NORTH).with(WATERLOGGED, Boolean.FALSE));
    }

    /** 木种由注册名反推的构造，供跨版本注册生成器使用。 */
    public ModernWallHangingSignBlock(AbstractBlock.Properties properties)
    {
        super(properties);
        this.setDefaultState(this.stateContainer.getBaseState().with(FACING, Direction.NORTH).with(WATERLOGGED, Boolean.FALSE));
    }

    @Override
    protected void fillStateContainer(StateContainer.Builder<Block, BlockState> builder)
    {
        builder.add(FACING, WATERLOGGED);
    }

    /**
     * 壁挂告示牌的物品名与方块名不同（物品只有 {@code <木>_hanging_sign} 一个），
     * 与 1.16.4 {@link WallSignBlock#getTranslationKey} 同样的处理。
     */
    @Override
    public String getTranslationKey()
    {
        return this.asItem().getTranslationKey();
    }

    @Override
    public VoxelShape getShape(BlockState state, IBlockReader worldIn, BlockPos pos, ISelectionContext context)
    {
        return SHAPES.get(state.get(FACING).getAxis());
    }

    /** 官方 {@code getCollisionShape} 只返回横梁。 */
    @Override
    public VoxelShape getCollisionShape(BlockState state, IBlockReader worldIn, BlockPos pos, ISelectionContext context)
    {
        return SHAPES_PLANK.get(state.get(FACING).getAxis());
    }

    /**
     * 官方 {@code canPlace}：{@code facing} 的顺时针或逆时针邻居能挂住就行。
     *
     * <p>注意官方把它与 {@code canSurvive} 分开：{@code canSurvive} 走
     * {@code SignBlock} 的默认实现（恒 true），真正的支撑判定在这里，
     * 由 {@code getStateForPlacement} 与 {@code updateShape} 显式调用。
     */
    public boolean canPlace(BlockState state, IWorldReader worldIn, BlockPos pos)
    {
        Direction clockwise = state.get(FACING).rotateY();
        Direction counterClockwise = state.get(FACING).rotateYCCW();
        return this.canAttachTo(worldIn, state, pos.offset(clockwise), counterClockwise)
                || this.canAttachTo(worldIn, state, pos.offset(counterClockwise), clockwise);
    }

    /**
     * 官方 {@code canAttachTo}：邻居也是壁挂悬挂告示牌且朝向同轴时可以串联，
     * 否则要求邻居那一面是完整实心面（{@code SupportType.FULL}）。
     *
     * <p>{@code SupportType.FULL} 在 1.16.4 就是 {@code BlockState.isSolidSide}
     * （内部走 {@code BlockVoxelShape.FULL}）。
     * {@code #wall_hanging_signs} 标签用 instanceof 代替，见
     * {@link ModernAbstractHangingSignBlock}。
     */
    public boolean canAttachTo(IWorldReader worldIn, BlockState state, BlockPos pos, Direction direction)
    {
        BlockState neighbour = worldIn.getBlockState(pos);

        if (neighbour.getBlock() instanceof ModernWallHangingSignBlock)
        {
            return neighbour.get(FACING).getAxis().test(state.get(FACING));
        }

        return neighbour.isSolidSide(worldIn, pos, direction);
    }

    /** 官方 {@code getStateForPlacement} 的逐句移植。 */
    @Override
    @Nullable
    public BlockState getStateForPlacement(BlockItemUseContext context)
    {
        BlockState blockstate = this.getDefaultState();
        FluidState fluidstate = context.getWorld().getFluidState(context.getPos());
        IWorldReader iworldreader = context.getWorld();
        BlockPos blockpos = context.getPos();

        for (Direction direction : context.getNearestLookingDirections())
        {
            // 官方：只考虑水平方向，且不能与点击到的面同轴
            // （否则会卡在自己刚点的那面墙里）。
            if (direction.getAxis().isHorizontal() && !direction.getAxis().test(context.getFace()))
            {
                blockstate = blockstate.with(FACING, direction.getOpposite());

                if (blockstate.isValidPosition(iworldreader, blockpos) && this.canPlace(blockstate, iworldreader, blockpos))
                {
                    return blockstate.with(WATERLOGGED, Boolean.valueOf(fluidstate.getFluid() == Fluids.WATER));
                }
            }
        }

        return null;
    }

    /** 官方：左右两侧的方块变化导致失去支撑就掉落。 */
    @Override
    public BlockState updatePostPlacement(BlockState stateIn, Direction facing, BlockState facingState, IWorld worldIn, BlockPos currentPos, BlockPos facingPos)
    {
        return facing.getAxis() == stateIn.get(FACING).rotateY().getAxis() && !this.canPlace(stateIn, worldIn, currentPos)
                ? Blocks.AIR.getDefaultState()
                : super.updatePostPlacement(stateIn, facing, facingState, worldIn, currentPos, facingPos);
    }

    @Override
    public BlockState rotate(BlockState state, Rotation rot)
    {
        return state.with(FACING, rot.rotate(state.get(FACING)));
    }

    @Override
    public BlockState mirror(BlockState state, Mirror mirrorIn)
    {
        return state.rotate(mirrorIn.toRotation(state.get(FACING)));
    }

    /** 官方 {@code isPathfindable} 恒 false —— 生物寻路不把它当可通行。 */
    @Override
    public boolean allowsMovement(BlockState state, IBlockReader worldIn, BlockPos pos, PathType type)
    {
        return false;
    }
}
