package net.minecraft.block;

import java.util.Random;
import java.util.function.ToIntFunction;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.fluid.FluidState;
import net.minecraft.fluid.Fluids;
import net.minecraft.item.BlockItemUseContext;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.particles.ParticleTypes;
import net.minecraft.state.BooleanProperty;
import net.minecraft.state.IntegerProperty;
import net.minecraft.state.StateContainer;
import net.minecraft.state.properties.BlockStateProperties;
import net.minecraft.util.ActionResultType;
import net.minecraft.util.Hand;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.BlockRayTraceResult;
import net.minecraft.util.math.shapes.ISelectionContext;
import net.minecraft.util.math.shapes.VoxelShape;
import net.minecraft.world.IBlockReader;
import net.minecraft.world.World;

/**
 * 蜡烛（1.17 加入）。
 *
 * <p>状态属性 {@code candles} / {@code lit} / {@code waterlogged} 与官方一致，共 16 个状态 ——
 * blockstate json 会按 {@code candles=N,lit=B} 分别指定模型，属性缺一个就有变体匹配不到、
 * 渲染成紫黑块，所以三个属性都必须提供。
 *
 * <p>1.16.4 里最接近的现成范例是 {@link SeaPickleBlock}（数量 1-4 + 含水），
 * 但属性名必须是 {@code candles} 才能对上官方 blockstate，不能复用
 * {@code BlockStateProperties.PICKLES_1_4}（那个属性名是 {@code pickles}）。
 *
 * <p>发光等级 {@code 3 * candles} 取自官方 {@code CandleBlock.LIGHT_EMISSION}。
 */
public class ModernCandleBlock extends Block implements IWaterLoggable {
    public static final int MIN_CANDLES = 1;
    public static final int MAX_CANDLES = 4;

    /** 官方属性名是 candles，1.16.4 的 BlockStateProperties 里没有，这里自建。 */
    public static final IntegerProperty CANDLES = IntegerProperty.create("candles", MIN_CANDLES, MAX_CANDLES);
    public static final BooleanProperty LIT = BlockStateProperties.LIT;
    public static final BooleanProperty WATERLOGGED = BlockStateProperties.WATERLOGGED;

    public ModernCandleBlock(AbstractBlock.Properties properties) {
        super(properties);
        this.setDefaultState(this.getDefaultState()
                .with(CANDLES, Integer.valueOf(MIN_CANDLES))
                .with(LIT, Boolean.FALSE)
                .with(WATERLOGGED, Boolean.FALSE));
    }

    /** 供生成器使用：与官方 CandleBlock.LIGHT_EMISSION 一致。 */
    public static ToIntFunction<BlockState> lightFromCandles() {
        return state -> state.get(LIT) ? 3 * state.get(CANDLES) : 0;
    }

    @Override
    protected void fillStateContainer(StateContainer.Builder<Block, BlockState> builder) {
        builder.add(CANDLES, LIT, WATERLOGGED);
    }

    /** 对着同种蜡烛放置时叠加数量，直到 4 支。 */
    @Override
    public boolean isReplaceable(BlockState state, BlockItemUseContext useContext) {
        // 1.16.4 的 BlockItemUseContext 没有 isPlacerSneaking()，从 player 直接取。
        PlayerEntity placer = useContext.getPlayer();
        boolean sneaking = placer != null && placer.isSneaking();
        return !sneaking
                && useContext.getItem().getItem() == this.asItem()
                && state.get(CANDLES) < MAX_CANDLES;
    }

    @Override
    public BlockState getStateForPlacement(BlockItemUseContext context) {
        BlockState existing = context.getWorld().getBlockState(context.getPos());
        if (existing.isIn(this)) {
            return existing.with(CANDLES, Integer.valueOf(Math.min(MAX_CANDLES, existing.get(CANDLES) + 1)));
        }
        FluidState fluidState = context.getWorld().getFluidState(context.getPos());
        boolean inWater = fluidState.getFluid() == Fluids.WATER;
        return this.getDefaultState()
                .with(WATERLOGGED, Boolean.valueOf(inWater))
                .with(LIT, Boolean.FALSE);
    }

    /** 空手右键熄灭；打火石点燃由 FlintAndSteelItem 的通用路径处理不了，这里一并接管。 */
    @Override
    public ActionResultType onBlockActivated(BlockState state, World worldIn, BlockPos pos,
            PlayerEntity player, Hand handIn, BlockRayTraceResult hit) {
        ItemStack held = player.getHeldItem(handIn);
        if (held.isEmpty() && state.get(LIT)) {
            extinguish(worldIn, state, pos);
            return ActionResultType.func_233537_a_(worldIn.isRemote());
        }
        if (held.getItem() == Items.FLINT_AND_STEEL && !state.get(LIT) && !state.get(WATERLOGGED)) {
            worldIn.setBlockState(pos, state.with(LIT, Boolean.TRUE), 11);
            worldIn.playSound(null, pos, SoundEvents.ITEM_FLINTANDSTEEL_USE,
                    SoundCategory.BLOCKS, 1.0F, 1.0F);
            held.damageItem(1, player, p -> p.sendBreakAnimation(handIn));
            return ActionResultType.func_233537_a_(worldIn.isRemote());
        }
        return ActionResultType.PASS;
    }

    private static void extinguish(World worldIn, BlockState state, BlockPos pos) {
        worldIn.setBlockState(pos, state.with(LIT, Boolean.FALSE), 11);
        // 1.16.4 没有 1.17 的 BLOCK_CANDLE_EXTINGUISH，用通用的灭火音效。
        worldIn.playSound(null, pos, SoundEvents.BLOCK_FIRE_EXTINGUISH,
                SoundCategory.BLOCKS, 0.5F, 1.5F);
    }

    @Override
    public FluidState getFluidState(BlockState state) {
        return state.get(WATERLOGGED)
                ? Fluids.WATER.getStillFluidState(false)
                : super.getFluidState(state);
    }

    /** 被水淹时熄灭，与官方一致。 */
    @Override
    public boolean receiveFluid(net.minecraft.world.IWorld worldIn, BlockPos pos, BlockState state,
            FluidState fluidStateIn) {
        if (!state.get(WATERLOGGED) && fluidStateIn.getFluid() == Fluids.WATER) {
            BlockState wet = state.with(WATERLOGGED, Boolean.TRUE);
            if (state.get(LIT)) {
                wet = wet.with(LIT, Boolean.FALSE);
            }
            worldIn.setBlockState(pos, wet, 3);
            worldIn.getPendingFluidTicks().scheduleTick(pos, fluidStateIn.getFluid(),
                    fluidStateIn.getFluid().getTickRate(worldIn));
            return true;
        }
        return false;
    }

    @Override
    public VoxelShape getShape(BlockState state, IBlockReader worldIn, BlockPos pos, ISelectionContext context) {
        // 官方按数量给了四套碰撞箱，这里用一个居中的小方柱近似，视觉由模型决定。
        return makeCuboidShape(5.0D, 0.0D, 5.0D, 11.0D, 6.0D, 11.0D);
    }

    /**
     * 每支蜡烛烛芯的位置（格内偏移），取自官方 {@code CandleBlock.PARTICLE_OFFSETS}。
     * 官方原值是 1/16 格为单位的整数再乘 0.0625，这里直接写成成品坐标。
     */
    private static final double[][][] FLAME_OFFSETS = {
        // 1 支
        {{0.5D, 0.5D, 0.5D}},
        // 2 支
        {{0.375D, 0.4375D, 0.5D}, {0.625D, 0.5D, 0.4375D}},
        // 3 支
        {{0.5D, 0.3125D, 0.625D}, {0.375D, 0.4375D, 0.5D}, {0.5625D, 0.5D, 0.4375D}},
        // 4 支
        {{0.3125D, 0.3125D, 0.625D}, {0.375D, 0.4375D, 0.375D},
         {0.625D, 0.5D, 0.5D}, {0.5625D, 0.3125D, 0.5625D}},
    };

    /**
     * 点燃时在每根烛芯上冒火焰与烟。
     *
     * <p>官方的火焰<b>不在模型里</b>，是 {@code AbstractCandleBlock.animateTick} 产生的粒子 ——
     * 只补 blockstate 和模型的话蜡烛点亮后顶上是空的，看不出在烧。
     */
    @Override
    public void animateTick(BlockState state, World worldIn, BlockPos pos, Random rand) {
        if (!state.get(LIT)) {
            return;
        }

        int candles = state.get(CANDLES);
        double[][] offsets = FLAME_OFFSETS[Math.min(candles, FLAME_OFFSETS.length) - 1];

        for (double[] offset : offsets) {
            double x = pos.getX() + offset[0];
            double y = pos.getY() + offset[1];
            double z = pos.getZ() + offset[2];

            // 官方：30% 的 tick 冒烟，火焰常驻。
            // 1.16.4 没有 1.17 的 SMALL_FLAME，用普通 FLAME 代替（视觉上略大一点）。
            if (rand.nextFloat() < 0.3F) {
                worldIn.addParticle(ParticleTypes.SMOKE, x, y, z, 0.0D, 0.0D, 0.0D);
            }
            worldIn.addParticle(ParticleTypes.FLAME, x, y, z, 0.0D, 0.0D, 0.0D);
        }
    }
}
