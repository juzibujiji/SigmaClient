package net.minecraft.block;

import java.util.Random;
import java.util.function.ToIntFunction;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.particles.ParticleTypes;
import net.minecraft.state.BooleanProperty;
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
import net.minecraft.util.math.shapes.VoxelShapes;
import net.minecraft.world.IBlockReader;
import net.minecraft.world.World;

/**
 * 插了蜡烛的蛋糕（1.17 加入）。
 *
 * <p>官方只有一个状态属性 {@code lit}，共 2 个状态。blockstate json 按 {@code lit} 分别指定
 * 模型，所以这个属性必须提供。
 *
 * <p>行为参照 1.16.4 的 {@link CakeBlock}：右键点燃、空手熄灭、吃掉后退化成缺一口的蛋糕
 * （官方是吃掉蜡烛掉落、蛋糕变 {@code bites=1}）。
 */
public class ModernCandleCakeBlock extends Block {
    public static final BooleanProperty LIT = BlockStateProperties.LIT;

    /**
     * 蜡烛 -> 对应的蜡烛蛋糕，与官方 {@code CandleCakeBlock.BY_CANDLE} 同构。
     *
     * <p>17 种颜色的蜡烛各对应一种蜡烛蛋糕。注册时建立关联，
     * {@link CakeBlock} 在玩家手持蜡烛右键蛋糕时用它查目标方块。
     */
    private static final java.util.Map<Block, ModernCandleCakeBlock> BY_CANDLE =
            new java.util.HashMap<>();

    private final Block candle;

    public ModernCandleCakeBlock(Block candle, AbstractBlock.Properties properties) {
        super(properties);
        this.candle = candle;
        this.setDefaultState(this.getDefaultState().with(LIT, Boolean.FALSE));
        BY_CANDLE.put(candle, this);
    }

    /** 手持 {@code candle} 右键蛋糕时应变成的方块状态，没有对应项时返回 null。 */
    public static BlockState byCandle(Block candle) {
        ModernCandleCakeBlock cake = BY_CANDLE.get(candle);
        return cake == null ? null : cake.getDefaultState();
    }

    /** 这块蜡烛蛋糕上插的是哪种蜡烛，吃掉时用来掉落。 */
    public Block getCandle() {
        return this.candle;
    }

    /** 供生成器使用：点燃时与单支蜡烛同亮度。 */
    public static ToIntFunction<BlockState> lightWhenLit() {
        return state -> state.get(LIT) ? 3 : 0;
    }

    @Override
    protected void fillStateContainer(StateContainer.Builder<Block, BlockState> builder) {
        builder.add(LIT);
    }

    @Override
    public VoxelShape getShape(BlockState state, IBlockReader worldIn, BlockPos pos, ISelectionContext context) {
        return SHAPE;
    }

    /**
     * 碰撞箱 = 蛋糕本体 + 上面那支蜡烛。
     *
     * <p>取自官方 {@code CandleCakeBlock.SHAPE}：蛋糕是 14×8×14（四周各留 1），
     * 蜡烛是 2×6×2 立在正中（y 从 8 到 14）。之前只写了蛋糕部分，
     * 蜡烛没有碰撞，鼠标指不到、也挡不住东西。
     */
    private static final VoxelShape SHAPE = VoxelShapes.or(
            makeCuboidShape(1.0D, 0.0D, 1.0D, 15.0D, 8.0D, 15.0D),
            makeCuboidShape(7.0D, 8.0D, 7.0D, 9.0D, 14.0D, 9.0D));

    /**
     * 点燃时在蜡烛顶端冒火焰与烟。
     *
     * <p>位置取自官方 {@code CandleCakeBlock.PARTICLE_OFFSETS}：格内 (0.5, 1.0, 0.5)，
     * 即蜡烛正上方。和 {@link ModernCandleBlock} 一样，官方的火焰是粒子而非模型的一部分。
     */
    @Override
    public void animateTick(BlockState state, World worldIn, BlockPos pos, Random rand) {
        if (!state.get(LIT)) {
            return;
        }

        double x = pos.getX() + 0.5D;
        double y = pos.getY() + 1.0D;
        double z = pos.getZ() + 0.5D;

        if (rand.nextFloat() < 0.3F) {
            worldIn.addParticle(ParticleTypes.SMOKE, x, y, z, 0.0D, 0.0D, 0.0D);
        }
        worldIn.addParticle(ParticleTypes.FLAME, x, y, z, 0.0D, 0.0D, 0.0D);
    }

    @Override
    public ActionResultType onBlockActivated(BlockState state, World worldIn, BlockPos pos,
            PlayerEntity player, Hand handIn, BlockRayTraceResult hit) {
        ItemStack held = player.getHeldItem(handIn);

        if (held.getItem() == Items.FLINT_AND_STEEL && !state.get(LIT)) {
            worldIn.setBlockState(pos, state.with(LIT, Boolean.TRUE), 11);
            worldIn.playSound(null, pos, SoundEvents.ITEM_FLINTANDSTEEL_USE,
                    SoundCategory.BLOCKS, 1.0F, 1.0F);
            held.damageItem(1, player, p -> p.sendBreakAnimation(handIn));
            return ActionResultType.func_233537_a_(worldIn.isRemote());
        }

        if (held.isEmpty() && state.get(LIT)) {
            worldIn.setBlockState(pos, state.with(LIT, Boolean.FALSE), 11);
            worldIn.playSound(null, pos, SoundEvents.BLOCK_FIRE_EXTINGUISH,
                    SoundCategory.BLOCKS, 0.5F, 1.5F);
            return ActionResultType.func_233537_a_(worldIn.isRemote());
        }

        // 吃掉：蜡烛掉落，蛋糕退化成缺一口的普通蛋糕。
        if (player.canEat(false)) {
            if (!worldIn.isRemote()) {
                player.getFoodStats().addStats(2, 0.1F);
                worldIn.setBlockState(pos,
                        Blocks.CAKE.getDefaultState().with(CakeBlock.BITES, Integer.valueOf(1)), 3);
                spawnAsEntity(worldIn, pos, new ItemStack(this.candle));
            }
            return ActionResultType.func_233537_a_(worldIn.isRemote());
        }

        return ActionResultType.PASS;
    }
}
