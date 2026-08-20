package net.minecraft.block;

import java.util.function.ToIntFunction;

import net.minecraft.state.BooleanProperty;
import net.minecraft.state.StateContainer;
import net.minecraft.state.properties.BlockStateProperties;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.SoundEvent;
import net.minecraft.util.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/**
 * 铜灯（1.20.3 加入，共 8 个氧化/打蜡变种）。
 *
 * <p>官方对应 {@code net.minecraft.world.level.block.CopperBulbBlock}
 * （非打蜡变种是子类 {@code WeatheringCopperBulbBlock}，子类只多了 {@code randomTick}
 * 里的氧化推进，见「做不到的部分」）。
 *
 * <p><b>状态属性与官方逐一对齐</b>：{@code lit}（2）× {@code powered}（2）= 4 个状态，
 * 与 1.21.11 blocks.json 报告完全一致。两个属性名在 1.16.4 的
 * {@link BlockStateProperties} 里现成（{@code lit} / {@code powered}），不必自建。
 *
 * <p><b>红石语义是「翻转」不是「跟随」</b>：铜灯像一个 T 触发器 ——
 * 只在红石信号的<b>上升沿</b>翻转 {@code lit}，信号消失时保持原样。
 * {@code powered} 只是用来记住上一次的信号电平，好识别上升沿。
 * 见官方 {@code CopperBulbBlock.checkAndFlip}：只有 {@code !state.getValue(POWERED)}
 * 的分支才 {@code cycle(LIT)}。
 *
 * <h3>做不到的部分（1.16.4 缺基础设施）</h3>
 * <ul>
 *   <li><b>氧化推进</b>（官方 {@code WeatheringCopperBulbBlock.randomTick}
 *       → {@code ChangeOverTimeBlock.changeOverTime}）。需要一张
 *       「本方块 → 下一氧化阶段」的映射表（官方 {@code WeatheringCopper.NEXT_BY_BLOCK}），
 *       表里的值是注册好的方块常量，得写在 {@code ModernBlocks} 里 —— 那个文件不归本类管。
 *       打蜡 / 刮蜡还额外要蜜脾块与斧子的物品侧接线。四个未打蜡变种目前是「永不氧化」，
 *       行为上等同于对应的打蜡变种。</li>
 * </ul>
 */
public class ModernCopperBulbBlock extends Block {
    public static final BooleanProperty LIT = BlockStateProperties.LIT;
    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;

    /**
     * 官方 1.16.4 没有 {@code block.copper_bulb.turn_on} / {@code turn_off}
     * （官方常量是 {@code SoundEvents.COPPER_BULB_TURN_ON} / {@code COPPER_BULB_TURN_OFF}）。
     * 用最接近的金属开关音 —— 金属压力板的按下 / 松开音代替。
     */
    private static final SoundEvent TURN_ON_SOUND = SoundEvents.BLOCK_METAL_PRESSURE_PLATE_CLICK_ON;
    private static final SoundEvent TURN_OFF_SOUND = SoundEvents.BLOCK_METAL_PRESSURE_PLATE_CLICK_OFF;

    public ModernCopperBulbBlock(AbstractBlock.Properties properties) {
        // 官方 COPPER_BULB 的 Properties 带 isRedstoneConductor(Blocks::never)：
        // 铜灯是实心不透明方块，但不导红石（不能靠它把强充能传过去，
        // 这是铜灯能贴着红石线做灯而不干扰电路的原因）。
        //
        // 1.16.4 的等价开关是 Properties.setOpaque(...) —— 它唯一的用途就是
        // AbstractBlockState.isNormalCube()（AbstractBlock 第 461 行
        // this.isNormalCube = properties.isOpaque），而 isNormalCube 正是
        // World.getRedstonePowerFromNeighbors / RedstoneWireBlock 判断「能否导红石」的依据。
        //
        // 注意不能用 notSolid() 代替：那会连渲染遮挡一起关掉，而官方铜灯没有
        // noOcclusion()，是正常剔除相邻面的完整立方体。
        super(properties.setOpaque((state, reader, pos) -> false));
        // 官方默认状态：LIT=false, POWERED=false
        this.setDefaultState(this.getDefaultState()
                .with(LIT, Boolean.FALSE)
                .with(POWERED, Boolean.FALSE));
    }

    /**
     * 供生成器 {@code DYNAMIC_LIGHT} 使用的动态亮度。
     *
     * <p><b>必须动态。</b>官方提取出来的静态亮度是「默认状态」的值，也就是
     * {@code lit=false} 的 0 —— 照抄会让铜灯永远不发光；反过来若取 lit 的值，
     * 熄灭的铜灯也会发光（深层红石矿踩过的坑）。
     *
     * <p>与官方 {@code Blocks.litBlockEmission(int)} 等价：
     * {@code state -> state.getValue(LIT) ? level : 0}。
     *
     * <p><b>各氧化阶段亮度不同</b>，取自官方 {@code Blocks} 的注册处
     * （{@code lightLevel(litBlockEmission(N))}）：
     * <ul>
     *   <li>{@code copper_bulb} / {@code waxed_copper_bulb} → 15</li>
     *   <li>{@code exposed_copper_bulb} / {@code waxed_exposed_copper_bulb} → 12</li>
     *   <li>{@code weathered_copper_bulb} / {@code waxed_weathered_copper_bulb} → 8</li>
     *   <li>{@code oxidized_copper_bulb} / {@code waxed_oxidized_copper_bulb} → 4</li>
     * </ul>
     * 打蜡变种在官方是 {@code Properties.ofFullCopy(对应未打蜡方块)}，所以亮度相同。
     *
     * @param litLightLevel 点亮时的亮度，必须按上表逐个方块传
     */
    public static ToIntFunction<BlockState> lightWhenLit(int litLightLevel) {
        return state -> state.get(LIT) ? litLightLevel : 0;
    }

    @Override
    protected void fillStateContainer(StateContainer.Builder<Block, BlockState> builder) {
        builder.add(LIT, POWERED);
    }

    /**
     * 官方 {@code onPlace}：放在已通电的位置时立刻按上升沿处理一次，
     * 所以「先拉红石、后放铜灯」放下去就是亮的。
     */
    @Override
    public void onBlockAdded(BlockState state, World worldIn, BlockPos pos, BlockState oldState, boolean isMoving) {
        if (oldState.getBlock() != state.getBlock() && !worldIn.isRemote()) {
            this.checkAndFlip(state, worldIn, pos);
        }
    }

    @Override
    public void neighborChanged(BlockState state, World worldIn, BlockPos pos, Block blockIn, BlockPos fromPos,
            boolean isMoving) {
        if (!worldIn.isRemote()) {
            this.checkAndFlip(state, worldIn, pos);
        }
    }

    /**
     * 官方 {@code CopperBulbBlock.checkAndFlip} 的逐行移植。
     *
     * <p>只有电平<b>变化</b>时才动；而且只在「原本没通电」（上升沿）时翻转 {@code lit} 并放音，
     * 下降沿仅仅把 {@code powered} 记回 false。这就是铜灯能被一个按钮反复开关的原因。
     *
     * <p>官方用的是 4 参数的 {@code playSound(null, pos, sound, SoundSource.BLOCKS)}，
     * 该重载在 {@code Level} 里默认音量 1.0F、音调 1.0F（见 {@code Level.playSound} 的
     * {@code playSound(..., 1.0F, 1.0F)} 转发），这里显式写出这两个值。
     */
    public void checkAndFlip(BlockState state, World worldIn, BlockPos pos) {
        boolean hasSignal = worldIn.isBlockPowered(pos);
        if (hasSignal != state.get(POWERED)) {
            BlockState newState = state;
            if (!state.get(POWERED)) {
                // func_235896_a_ 就是官方的 cycle(Property)，1.16.4 未反混淆
                newState = state.func_235896_a_(LIT);
                worldIn.playSound(null, pos,
                        newState.get(LIT) ? TURN_ON_SOUND : TURN_OFF_SOUND,
                        SoundCategory.BLOCKS, 1.0F, 1.0F);
            }
            worldIn.setBlockState(pos, newState.with(POWERED, Boolean.valueOf(hasSignal)), 3);
        }
    }

    // --- 比较器读数：官方 hasAnalogOutputSignal / getAnalogOutputSignal ---

    @Override
    public boolean hasComparatorInputOverride(BlockState state) {
        return true;
    }

    /** 官方：亮着读 15，灭着读 0（用来把铜灯当作可读的一位存储）。 */
    @Override
    public int getComparatorInputOverride(BlockState blockState, World worldIn, BlockPos pos) {
        return worldIn.getBlockState(pos).get(LIT) ? 15 : 0;
    }
}
