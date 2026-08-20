package net.minecraft.block;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import java.util.Map;
import java.util.Optional;
import net.minecraft.fluid.FluidState;
import net.minecraft.fluid.Fluids;
import net.minecraft.item.BlockItemUseContext;
import net.minecraft.state.BooleanProperty;
import net.minecraft.state.IntegerProperty;
import net.minecraft.state.StateContainer;
import net.minecraft.state.properties.BlockStateProperties;
import net.minecraft.util.BlockVoxelShape;
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
 * 吊在天花板下的悬挂告示牌（1.19 加入），官方类型 {@code ceiling_hanging_sign}。
 *
 * <p>状态：{@code rotation}(0-15) + {@code attached} + {@code waterlogged} = 64 个，
 * 与官方 {@code CeilingHangingSignBlock.createBlockStateDefinition} 逐一对齐。
 *
 * <p>{@code attached} 的含义（官方 {@code getStateForPlacement} 里的 {@code flag1}）：
 * true 表示挂在「不是完整下表面」的方块下（或玩家蹲下放置），渲染成一条 V 形短链
 * （{@code CEILING_MIDDLE}）；false 表示挂在实心方块下，渲染成两侧各一对斜链
 * （{@code CEILING}）。
 */
public class ModernCeilingHangingSignBlock extends ModernAbstractHangingSignBlock
{
    /** 官方 CeilingHangingSignBlock.ROTATION = BlockStateProperties.ROTATION_16。 */
    public static final IntegerProperty ROTATION = BlockStateProperties.ROTATION_0_15;
    /** 官方 CeilingHangingSignBlock.ATTACHED = BlockStateProperties.ATTACHED。 */
    public static final BooleanProperty ATTACHED = BlockStateProperties.ATTACHED;

    /**
     * 官方 {@code SHAPE_DEFAULT = Block.column(10.0, 0.0, 16.0)}。
     *
     * <p>官方 {@code column(w, minY, maxY)} 展开为
     * {@code box(8-w/2, minY, 8-w/2, 8+w/2, maxY, 8+w/2)}，
     * 即 {@code box(3, 0, 3, 13, 16, 13)}。斜向（非正朝向）的 12 个 rotation 用这个。
     */
    private static final VoxelShape SHAPE_DEFAULT = Block.makeCuboidShape(3.0D, 0.0D, 3.0D, 13.0D, 16.0D, 13.0D);

    /**
     * 官方 {@code Block.column(14.0, 2.0, 0.0, 10.0)} 的展开：
     * 4 参版 {@code column(wx, wz, minY, maxY)} =
     * {@code box(8-wx/2, minY, 8-wz/2, 8+wx/2, maxY, 8+wz/2)}
     * = {@code box(1, 0, 7, 15, 10, 9)}。这是牌面沿 X 轴铺开（面朝南北）的形状。
     */
    private static final VoxelShape SHAPE_X_WIDE = Block.makeCuboidShape(1.0D, 0.0D, 7.0D, 15.0D, 10.0D, 9.0D);

    /** 上面那个绕 Y 轴转 90 度（官方 {@code Shapes.rotateHorizontal} 的 EAST/WEST 项）。 */
    private static final VoxelShape SHAPE_Z_WIDE = Block.makeCuboidShape(7.0D, 0.0D, 1.0D, 9.0D, 10.0D, 15.0D);

    /**
     * 官方：
     * <pre>SHAPES = Shapes.rotateHorizontal(Block.column(14, 2, 0, 10))
     *     .entrySet().stream()
     *     .collect(toMap(e -&gt; RotationSegment.convertToSegment(e.getKey()), Entry::getValue));</pre>
     *
     * <p>{@code convertToSegment(Direction)} = {@code getHorizontalIndex() << 2}，
     * 于是 SOUTH-&gt;0、WEST-&gt;4、NORTH-&gt;8、EAST-&gt;12。
     * 南北两项都是 {@link #SHAPE_X_WIDE}、东西两项都是 {@link #SHAPE_Z_WIDE}
     * （形状左右对称，转 180 度不变），所以最终只有 4 个键：
     * 0 和 8 用 X 向，4 和 12 用 Z 向；<b>其余 12 个斜向 rotation 落到
     * {@link #SHAPE_DEFAULT}</b>（这是官方 {@code getOrDefault} 的行为，不是简化）。
     */
    private static final Map<Integer, VoxelShape> SHAPES = Maps.newHashMap(ImmutableMap.of(
                Integer.valueOf(0), SHAPE_X_WIDE,
                Integer.valueOf(8), SHAPE_X_WIDE,
                Integer.valueOf(4), SHAPE_Z_WIDE,
                Integer.valueOf(12), SHAPE_Z_WIDE));

    public ModernCeilingHangingSignBlock(AbstractBlock.Properties properties, WoodType type)
    {
        super(properties, type);
        this.setDefaultState(this.stateContainer.getBaseState().with(ROTATION, Integer.valueOf(0)).with(ATTACHED, Boolean.FALSE).with(WATERLOGGED, Boolean.FALSE));
    }

    /** 木种由注册名反推的构造，供跨版本注册生成器使用。 */
    public ModernCeilingHangingSignBlock(AbstractBlock.Properties properties)
    {
        super(properties);
        this.setDefaultState(this.stateContainer.getBaseState().with(ROTATION, Integer.valueOf(0)).with(ATTACHED, Boolean.FALSE).with(WATERLOGGED, Boolean.FALSE));
    }

    @Override
    protected void fillStateContainer(StateContainer.Builder<Block, BlockState> builder)
    {
        builder.add(ROTATION, ATTACHED, WATERLOGGED);
    }

    /**
     * 官方 {@code canSurvive}：上方方块的下表面必须能支撑中心。
     *
     * <p>官方是 {@code isFaceSturdy(reader, pos, Direction.DOWN, SupportType.CENTER)}；
     * 1.16.4 里 {@code SupportType} 叫 {@code BlockVoxelShape}，这个查询没有公开的
     * 具名方法（{@code Block.hasEnoughSolidSide} 会额外查 UNSTABLE_BOTTOM_CENTER 标签，
     * 官方这里没查），所以直接用底层的 {@code func_242698_a}。
     */
    @Override
    public boolean isValidPosition(BlockState state, IWorldReader worldIn, BlockPos pos)
    {
        BlockPos above = pos.up();
        return worldIn.getBlockState(above).func_242698_a(worldIn, above, Direction.DOWN, BlockVoxelShape.CENTER);
    }

    /**
     * 官方 {@code CeilingHangingSignBlock.getStateForPlacement} 的逐句移植。
     *
     * <p>要点：挂在另一块悬挂告示牌下面且朝向同轴时，{@code attached} 置 false 并
     * 与上面那块对齐朝向 —— 这就是「串联」效果。
     */
    @Override
    public BlockState getStateForPlacement(BlockItemUseContext context)
    {
        net.minecraft.world.World world = context.getWorld();
        FluidState fluidstate = world.getFluidState(context.getPos());
        BlockPos above = context.getPos().up();
        BlockState aboveState = world.getBlockState(above);
        boolean isHangingSignAbove = isHangingSign(aboveState);
        Direction placeDir = Direction.fromAngle((double)context.getPlacementYaw());
        // 官方 flag1：上方不是完整下表面，或玩家蹲下放置 -> 用「短 V 链」形态。
        // 1.16.4 的 BlockItemUseContext 没有 isSecondaryUseActive()，从 player 取蹲伏状态。
        boolean secondaryUse = isSecondaryUseActive(context);
        boolean attached = !Block.doesSideFillSquare(aboveState.getCollisionShape(world, above), Direction.DOWN) || secondaryUse;

        if (isHangingSignAbove && !secondaryUse)
        {
            if (aboveState.hasProperty(ModernWallHangingSignBlock.FACING))
            {
                Direction aboveFacing = aboveState.get(ModernWallHangingSignBlock.FACING);

                if (aboveFacing.getAxis().test(placeDir))
                {
                    attached = false;
                }
            }
            else if (aboveState.hasProperty(ROTATION))
            {
                Optional<Direction> aboveDir = ModernRotationSegment.convertToDirection(aboveState.get(ROTATION));

                if (aboveDir.isPresent() && aboveDir.get().getAxis().test(placeDir))
                {
                    attached = false;
                }
            }
        }

        int rotation = !attached
                ? ModernRotationSegment.convertToSegment(placeDir.getOpposite())
                : ModernRotationSegment.convertToSegment(context.getPlacementYaw() + 180.0F);
        return this.getDefaultState()
                .with(ATTACHED, Boolean.valueOf(attached))
                .with(ROTATION, Integer.valueOf(rotation))
                .with(WATERLOGGED, Boolean.valueOf(fluidstate.getFluid() == Fluids.WATER));
    }

    /** 官方 {@code BlockPlaceContext.isSecondaryUseActive()} 在 1.16.4 没有对应方法。 */
    private static boolean isSecondaryUseActive(BlockItemUseContext context)
    {
        return context.getPlayer() != null && context.getPlayer().isSneaking();
    }

    @Override
    public VoxelShape getShape(BlockState state, IBlockReader worldIn, BlockPos pos, ISelectionContext context)
    {
        return SHAPES.getOrDefault(state.get(ROTATION), SHAPE_DEFAULT);
    }

    // 官方还覆写了 getBlockSupportShape 让它返回 getShape()。
    // 1.16.4 不需要写：支撑面判定走 BlockVoxelShape，而它查的是
    // getRenderShape(state, reader, pos)，其默认实现就是 state.getShape(...)。
    // 行为已经一致，覆写反而多余。

    /** 官方：上方方块变化导致失去支撑就掉落（变成空气）。 */
    @Override
    public BlockState updatePostPlacement(BlockState stateIn, Direction facing, BlockState facingState, IWorld worldIn, BlockPos currentPos, BlockPos facingPos)
    {
        return facing == Direction.UP && !this.isValidPosition(stateIn, worldIn, currentPos)
                ? Blocks.AIR.getDefaultState()
                : super.updatePostPlacement(stateIn, facing, facingState, worldIn, currentPos, facingPos);
    }

    /** 官方 {@code rotate}：rotation 按 16 段步进。 */
    @Override
    public BlockState rotate(BlockState state, Rotation rot)
    {
        return state.with(ROTATION, Integer.valueOf(rot.rotate(state.get(ROTATION), 16)));
    }

    /** 官方 {@code mirror}：rotation 按 16 段镜像。 */
    @Override
    public BlockState mirror(BlockState state, Mirror mirrorIn)
    {
        return state.with(ROTATION, Integer.valueOf(mirrorIn.mirrorRotation(state.get(ROTATION), 16)));
    }
}
