package net.minecraft.block;

import net.minecraft.fluid.FluidState;
import net.minecraft.fluid.Fluids;
import net.minecraft.item.BlockItemUseContext;
import net.minecraft.state.BooleanProperty;
import net.minecraft.state.StateContainer;
import net.minecraft.state.properties.BlockStateProperties;

/**
 * 可含水的普通方块。
 *
 * <p>1.21 有一批方块是「整块 + 可含水」的形态，官方类型名叫
 * {@code waterlogged_transparent}，典型是铜格栅。1.16.4 的普通 {@link Block} 没有
 * {@code waterlogged} 属性。
 *
 * <p><b>为什么必须补上</b>（不是为了渲染）：这些方块的 blockstate variant key 是空串，
 * 少个属性也不会紫黑。真正的理由是<b>状态数</b> —— 官方铜格栅有 2 个方块状态
 * （含水与否），用普通 {@code Block} 只有 1 个，跨版本映射就对不上，
 * 服务器发来含水状态时客户端无法表达。
 *
 * <p>1.16.4 自带完整的含水机制（{@link IWaterLoggable}），补上属性顺带让行为也一致。
 */
public class ModernWaterloggedBlock extends Block implements IWaterLoggable {
    public static final BooleanProperty WATERLOGGED = BlockStateProperties.WATERLOGGED;

    public ModernWaterloggedBlock(AbstractBlock.Properties properties) {
        super(properties);
        this.setDefaultState(this.getDefaultState().with(WATERLOGGED, Boolean.FALSE));
    }

    @Override
    protected void fillStateContainer(StateContainer.Builder<Block, BlockState> builder) {
        builder.add(WATERLOGGED);
    }

    @Override
    public BlockState getStateForPlacement(BlockItemUseContext context) {
        FluidState fluidState = context.getWorld().getFluidState(context.getPos());
        return this.getDefaultState()
                .with(WATERLOGGED, Boolean.valueOf(fluidState.getFluid() == Fluids.WATER));
    }

    @Override
    public FluidState getFluidState(BlockState state) {
        return state.get(WATERLOGGED)
                ? Fluids.WATER.getStillFluidState(false)
                : super.getFluidState(state);
    }
}
