package net.minecraft.block;

import net.minecraft.fluid.FluidState;
import net.minecraft.fluid.Fluids;
import net.minecraft.item.BlockItemUseContext;
import net.minecraft.state.BooleanProperty;
import net.minecraft.state.StateContainer;
import net.minecraft.state.properties.BlockStateProperties;

/**
 * 可含水的树叶（1.19 起原版树叶带 {@code waterlogged}）。
 *
 * <p>1.16.4 的 {@link LeavesBlock} 只有 {@code distance} 与 {@code persistent}。
 *
 * <p><b>为什么必须补上这个属性</b>（不是为了渲染）：树叶的 blockstate variant key 是空串
 * （所有状态共用一个模型），所以就算少个属性也不会渲染成紫黑块。真正的理由是<b>状态数</b> ——
 * 官方 1.21.11 的树叶有 28 个方块状态（7 distance × 2 persistent × 2 waterlogged），
 * 用原版 {@code LeavesBlock} 只有 14 个。跨版本映射要建立「目标版本状态 → 本地状态」的
 * 对应关系，状态数不一致就没法一一对上，服务器发来含水的树叶时客户端无法表达。
 *
 * <p>顺带让行为也跟高版本一致：树叶可以真的被水淹。1.16.4 本身有完整的含水机制
 * （{@link IWaterLoggable}，且项目已为它加了「连 1.12.2 及更老时禁用」的跨版本 gate）。
 */
public class ModernLeavesBlock extends LeavesBlock implements IWaterLoggable {
    public static final BooleanProperty WATERLOGGED = BlockStateProperties.WATERLOGGED;

    public ModernLeavesBlock(AbstractBlock.Properties properties) {
        super(properties);
        this.setDefaultState(this.getDefaultState().with(WATERLOGGED, Boolean.FALSE));
    }

    @Override
    protected void fillStateContainer(StateContainer.Builder<Block, BlockState> builder) {
        super.fillStateContainer(builder);
        builder.add(WATERLOGGED);
    }

    @Override
    public BlockState getStateForPlacement(BlockItemUseContext context) {
        FluidState fluidState = context.getWorld().getFluidState(context.getPos());
        BlockState placed = super.getStateForPlacement(context);
        if (placed == null) {
            placed = this.getDefaultState();
        }
        return placed.with(WATERLOGGED, Boolean.valueOf(fluidState.getFluid() == Fluids.WATER));
    }

    @Override
    public FluidState getFluidState(BlockState state) {
        return state.get(WATERLOGGED)
                ? Fluids.WATER.getStillFluidState(false)
                : super.getFluidState(state);
    }
}
