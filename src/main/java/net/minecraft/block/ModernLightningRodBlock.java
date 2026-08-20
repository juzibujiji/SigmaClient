package net.minecraft.block;

import java.util.Random;

import net.minecraft.fluid.FluidState;
import net.minecraft.fluid.Fluids;
import net.minecraft.item.BlockItemUseContext;
import net.minecraft.particles.BasicParticleType;
import net.minecraft.particles.ParticleTypes;
import net.minecraft.pathfinding.PathType;
import net.minecraft.state.BooleanProperty;
import net.minecraft.state.StateContainer;
import net.minecraft.state.properties.BlockStateProperties;
import net.minecraft.util.Direction;
import net.minecraft.util.Mirror;
import net.minecraft.util.Rotation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.shapes.ISelectionContext;
import net.minecraft.util.math.shapes.VoxelShape;
import net.minecraft.util.math.vector.Vector3d;
import net.minecraft.world.IBlockReader;
import net.minecraft.world.IWorld;
import net.minecraft.world.World;
import net.minecraft.world.gen.Heightmap;
import net.minecraft.world.server.ServerWorld;

/**
 * 避雷针（1.17 加入，1.21.9 起有 8 个氧化/打蜡变种）。
 *
 * <p>官方对应 {@code net.minecraft.world.level.block.LightningRodBlock}
 * （非打蜡变种实际是子类 {@code WeatheringLightningRodBlock}，
 * 子类只多了 {@code randomTick} 里的氧化推进，见下面「做不到的部分」）。
 *
 * <p><b>状态属性与官方逐一对齐</b>：{@code facing}（6）× {@code powered}（2）
 * × {@code waterlogged}（2）= 24 个状态，与 1.21.11 blocks.json 报告完全一致。
 * 属性名直接复用 1.16.4 的 {@link BlockStateProperties#FACING} / {@link BlockStateProperties#POWERED}
 * / {@link BlockStateProperties#WATERLOGGED}，序列化名分别就是 {@code facing} / {@code powered}
 * / {@code waterlogged}，不需要像蜡烛的 {@code candles} 那样自建。
 *
 * <p>1.16.4 没有官方的 {@code RodBlock} 基类，这里直接继承 {@link DirectionalBlock}
 * 并把 {@code RodBlock} 的形状与寻路行为抄进来（做法与 1.16.4 自带的
 * {@link EndRodBlock} 一致）。
 *
 * <h3>做不到的部分（1.16.4 缺基础设施）</h3>
 * <ul>
 *   <li><b>吸引闪电</b>。官方是闪电生成时由 {@code ServerLevel.findLightningRod(pos)}
 *       在 {@code RANGE}=128 格内找最高的避雷针、把落点挪到针上，再调
 *       {@code LightningRodBlock.onLightningStrike}。1.16.4 的 {@code ServerWorld}
 *       和 {@code LightningBoltEntity} 都没有这套搜索。本类已按官方实现好
 *       {@link #onLightningStrike}，缺的只是调用方 —— 要补得改 ServerWorld 的天气逻辑，
 *       超出方块类范围。<b>联机时无影响</b>：真正的 1.21 服务器自己算落点，
 *       再把 powered 状态和世界事件 3002 发给客户端。</li>
 *   <li><b>氧化推进</b>（官方 {@code WeatheringLightningRodBlock.randomTick}
 *       → {@code ChangeOverTimeBlock.changeOverTime}）。需要一张
 *       「本方块 → 下一氧化阶段」的映射表（官方 {@code WeatheringCopper.NEXT_BY_BLOCK}），
 *       表里的值是注册好的方块常量，得写在 {@code ModernBlocks} 里 —— 那个文件不归本类管。
 *       打蜡 / 刮蜡还额外要蜜脾块与斧子的物品侧接线。</li>
 * </ul>
 */
public class ModernLightningRodBlock extends DirectionalBlock implements IWaterLoggable {
    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;
    public static final BooleanProperty WATERLOGGED = BlockStateProperties.WATERLOGGED;

    /** 官方 {@code LightningRodBlock.ACTIVATION_TICKS}：被雷击后保持通电 8 tick。 */
    private static final int ACTIVATION_TICKS = 8;

    /**
     * 官方 {@code LightningRodBlock.RANGE}：闪电搜索避雷针的半径。
     *
     * <p>1.16.4 的 {@code ServerWorld} 没有对应的搜索逻辑，这个常量目前无人调用，
     * 保留是为了将来接线时不用再去翻官方源码。
     */
    public static final int RANGE = 128;

    /** 官方 {@code LightningRodBlock.SPARK_CYCLE}：雷暴时冒火花的周期（tick）。 */
    private static final int SPARK_CYCLE = 200;

    /**
     * 官方 1.21 的形状是 {@code RodBlock.SHAPES = Shapes.rotateAllAxis(Block.cube(4.0, 4.0, 16.0))}。
     *
     * <p>{@code Block.cube(4, 4, 16)} 展开为 {@code column(4, 16, 6, 10)}，再展开为
     * {@code box(6, 6, 0, 10, 10, 16)}（即 Z 轴形态），{@code rotateAllAxis} 再绕轴转出 X / Y 形态。
     * 结果与 1.16.4 {@link EndRodBlock} 的三个常量逐字相同，这里照同样的坐标写死。
     */
    protected static final VoxelShape SHAPE_Y_AXIS = Block.makeCuboidShape(6.0D, 0.0D, 6.0D, 10.0D, 16.0D, 10.0D);
    protected static final VoxelShape SHAPE_Z_AXIS = Block.makeCuboidShape(6.0D, 6.0D, 0.0D, 10.0D, 10.0D, 16.0D);
    protected static final VoxelShape SHAPE_X_AXIS = Block.makeCuboidShape(0.0D, 6.0D, 6.0D, 16.0D, 10.0D, 10.0D);

    /**
     * 官方雷击表现走的世界事件 id，见 {@code LightningRodBlock.onLightningStrike} 的
     * {@code level.levelEvent(3002, pos, axis.ordinal())}，客户端在
     * {@code LevelEventHandler} 的 {@code case 3002} 里放电火花粒子。
     */
    public static final int LEVEL_EVENT_LIGHTNING_ROD_SPARKS = 3002;

    public ModernLightningRodBlock(AbstractBlock.Properties properties) {
        // 官方 LIGHTNING_ROD 的 Properties 带 noOcclusion()，1.16.4 的对应写法是 notSolid()。
        // 不声明的话渲染器会把它当完整立方体、剔除相邻面，站在避雷针旁边能从缝隙看穿地形。
        // 1.16.4 原版的 END_ROD 也是这么注册的（Blocks.END_ROD ... .notSolid()）。
        // 写在构造里而不是交给生成器，是为了保证 8 个变种不会漏配。
        super(properties.notSolid());
        // 官方默认状态：FACING=UP, WATERLOGGED=false, POWERED=false
        this.setDefaultState(this.stateContainer.getBaseState()
                .with(FACING, Direction.UP)
                .with(POWERED, Boolean.FALSE)
                .with(WATERLOGGED, Boolean.FALSE));
    }

    @Override
    protected void fillStateContainer(StateContainer.Builder<Block, BlockState> builder) {
        builder.add(FACING, POWERED, WATERLOGGED);
    }

    @Override
    public VoxelShape getShape(BlockState state, IBlockReader worldIn, BlockPos pos, ISelectionContext context) {
        switch (state.get(FACING).getAxis()) {
            case X:
                return SHAPE_X_AXIS;
            case Z:
                return SHAPE_Z_AXIS;
            case Y:
            default:
                return SHAPE_Y_AXIS;
        }
    }

    /**
     * 官方 {@code getStateForPlacement}：朝向就是点击面，泡在水里就带上 waterlogged。
     * 注意与末地烛不同 —— 避雷针不会因为对着另一根避雷针放置而反向。
     */
    @Override
    public BlockState getStateForPlacement(BlockItemUseContext context) {
        FluidState fluidState = context.getWorld().getFluidState(context.getPos());
        boolean inWater = fluidState.getFluid() == Fluids.WATER;
        return this.getDefaultState()
                .with(FACING, context.getFace())
                .with(WATERLOGGED, Boolean.valueOf(inWater));
    }

    /** 官方 {@code updateShape}：含水时补一次水流 tick，避免水面出现空洞。 */
    @Override
    public BlockState updatePostPlacement(BlockState stateIn, Direction facing, BlockState facingState,
            IWorld worldIn, BlockPos currentPos, BlockPos facingPos) {
        if (stateIn.get(WATERLOGGED)) {
            worldIn.getPendingFluidTicks().scheduleTick(currentPos, Fluids.WATER, Fluids.WATER.getTickRate(worldIn));
        }
        return super.updatePostPlacement(stateIn, facing, facingState, worldIn, currentPos, facingPos);
    }

    @Override
    public FluidState getFluidState(BlockState state) {
        return state.get(WATERLOGGED)
                ? Fluids.WATER.getStillFluidState(false)
                : super.getFluidState(state);
    }

    // --- 红石：官方 isSignalSource / getSignal / getDirectSignal ---

    @Override
    public boolean canProvidePower(BlockState state) {
        return true;
    }

    /** 官方 {@code getSignal}：通电时向各方向输出 15。 */
    @Override
    public int getWeakPower(BlockState blockState, IBlockReader blockAccess, BlockPos pos, Direction side) {
        return blockState.get(POWERED) ? 15 : 0;
    }

    /** 官方 {@code getDirectSignal}：只在朝向那一面给强充能。 */
    @Override
    public int getStrongPower(BlockState blockState, IBlockReader blockAccess, BlockPos pos, Direction side) {
        return blockState.get(POWERED) && blockState.get(FACING) == side ? 15 : 0;
    }

    /**
     * 被闪电击中时的表现，逐行对应官方 {@code LightningRodBlock.onLightningStrike}：
     * 置 powered、通知邻居、8 tick 后断电、并广播世界事件 3002 放电火花。
     *
     * <p><b>目前没有调用方。</b>官方是 {@code LightningBolt} 在
     * {@code ServerLevel.findLightningRod} 找到避雷针后调用它，1.16.4 完全没有这套机制
     * （见类注释里「做不到的部分」）。方法留成 public 是为了将来接线时直接可用，
     * 签名与官方一致。
     */
    public void onLightningStrike(BlockState state, World worldIn, BlockPos pos) {
        worldIn.setBlockState(pos, state.with(POWERED, Boolean.TRUE), 3);
        this.updateNeighbors(state, worldIn, pos);
        worldIn.getPendingBlockTicks().scheduleTick(pos, this, ACTIVATION_TICKS);
        worldIn.playEvent(null, LEVEL_EVENT_LIGHTNING_ROD_SPARKS, pos, state.get(FACING).getAxis().ordinal());
    }

    /**
     * 官方 {@code updateNeighbours}：只刷新「针尾贴着的那格」的邻居，
     * 也就是 {@code pos.relative(FACING.getOpposite())}。
     */
    private void updateNeighbors(BlockState state, World worldIn, BlockPos pos) {
        Direction behind = state.get(FACING).getOpposite();
        worldIn.notifyNeighborsOfStateChange(pos.offset(behind), this);
    }

    /** 官方 {@code tick}：计划刻到点，断电。 */
    @Override
    public void tick(BlockState state, ServerWorld worldIn, BlockPos pos, Random rand) {
        worldIn.setBlockState(pos, state.with(POWERED, Boolean.FALSE), 3);
        this.updateNeighbors(state, worldIn, pos);
    }

    /**
     * 官方 {@code onPlace}：如果放下来就是通电状态（例如活塞推过来、结构方块粘贴），
     * 补一个断电计划刻，否则会永久卡在通电。
     */
    @Override
    public void onBlockAdded(BlockState state, World worldIn, BlockPos pos, BlockState oldState, boolean isMoving) {
        if (!state.isIn(oldState.getBlock())
                && state.get(POWERED)
                && !worldIn.getPendingBlockTicks().isTickScheduled(pos, this)) {
            worldIn.getPendingBlockTicks().scheduleTick(pos, this, ACTIVATION_TICKS);
        }
    }

    /**
     * 官方 {@code affectNeighborsAfterRemoval}：通电的避雷针被移除时要把邻居的红石状态刷掉，
     * 否则相邻的红石线会记着 15 的信号。
     */
    @Override
    public void onReplaced(BlockState state, World worldIn, BlockPos pos, BlockState newState, boolean isMoving) {
        if (!state.isIn(newState.getBlock()) && state.get(POWERED)) {
            this.updateNeighbors(state, worldIn, pos);
        }
        super.onReplaced(state, worldIn, pos, newState, isMoving);
    }

    /**
     * 雷暴天在露天避雷针尖上冒放电火花，逐行对应官方 {@code animateTick}：
     * {@code isThundering() && random.nextInt(200) <= gameTime % 200 && y == 地表高度 - 1}。
     *
     * <p>这层表现<b>不在模型里</b>，只补 blockstate/模型的话雷暴天避雷针是死的。
     */
    @Override
    public void animateTick(BlockState stateIn, World worldIn, BlockPos pos, Random rand) {
        if (worldIn.isThundering()
                && worldIn.rand.nextInt(SPARK_CYCLE) <= worldIn.getGameTime() % (long) SPARK_CYCLE
                && pos.getY() == worldIn.getHeight(Heightmap.Type.WORLD_SURFACE, pos.getX(), pos.getZ()) - 1) {
            // 官方 UniformInt.of(1, 2)：1 或 2 颗
            spawnParticlesAlongAxis(stateIn.get(FACING).getAxis(), worldIn, pos, 0.125D,
                    ELECTRIC_SPARK_SUBSTITUTE, 1, 2);
        }
    }

    /**
     * 官方用的是 1.17 新增的 {@code ParticleTypes.ELECTRIC_SPARK}，1.16.4 没有这个粒子。
     * 视觉最接近的现成粒子是 {@code CRIT}（同为向外飞散的小亮点）。
     */
    public static final BasicParticleType ELECTRIC_SPARK_SUBSTITUTE = ParticleTypes.CRIT;

    /**
     * 官方 {@code ParticleUtils.spawnParticlesAlongAxis} 的逐行移植 ——
     * 1.16.4 没有 {@code ParticleUtils} 也没有 {@code UniformInt}，
     * 数量范围改成 [minCount, maxCount] 两个参数传入。
     *
     * <p>沿轴方向散布 ±0.5 格并带上速度，其余两轴只散布 ±offset 且速度为 0，
     * 所以火花是顺着杆子的方向喷出去的。
     */
    public static void spawnParticlesAlongAxis(Direction.Axis axis, World worldIn, BlockPos pos,
            double offset, BasicParticleType particle, int minCount, int maxCount) {
        Vector3d center = Vector3d.copyCentered(pos);
        boolean alongX = axis == Direction.Axis.X;
        boolean alongY = axis == Direction.Axis.Y;
        boolean alongZ = axis == Direction.Axis.Z;
        int count = minCount + worldIn.rand.nextInt(maxCount - minCount + 1);

        for (int i = 0; i < count; i++) {
            double x = center.x + MathHelper.nextDouble(worldIn.rand, -1.0D, 1.0D) * (alongX ? 0.5D : offset);
            double y = center.y + MathHelper.nextDouble(worldIn.rand, -1.0D, 1.0D) * (alongY ? 0.5D : offset);
            double z = center.z + MathHelper.nextDouble(worldIn.rand, -1.0D, 1.0D) * (alongZ ? 0.5D : offset);
            double vx = alongX ? MathHelper.nextDouble(worldIn.rand, -1.0D, 1.0D) : 0.0D;
            double vy = alongY ? MathHelper.nextDouble(worldIn.rand, -1.0D, 1.0D) : 0.0D;
            double vz = alongZ ? MathHelper.nextDouble(worldIn.rand, -1.0D, 1.0D) : 0.0D;
            worldIn.addParticle(particle, x, y, z, vx, vy, vz);
        }
    }

    // --- RodBlock 的旋转 / 镜像 / 寻路，与 1.16.4 EndRodBlock 一致 ---

    @Override
    public BlockState rotate(BlockState state, Rotation rot) {
        return state.with(FACING, rot.rotate(state.get(FACING)));
    }

    @Override
    public BlockState mirror(BlockState state, Mirror mirrorIn) {
        return state.with(FACING, mirrorIn.mirror(state.get(FACING)));
    }

    /** 官方 {@code RodBlock.isPathfindable} 返回 false：生物不会把避雷针当成可通行格。 */
    @Override
    public boolean allowsMovement(BlockState state, IBlockReader worldIn, BlockPos pos, PathType type) {
        return false;
    }
}
