package net.minecraft.item;

import javax.annotation.Nullable;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.ModernWallHangingSignBlock;
import net.minecraft.util.Direction;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.shapes.ISelectionContext;
import net.minecraft.world.IWorldReader;

/**
 * 悬挂告示牌物品（1.19 加入），官方对应 {@code world/item/HangingSignItem}。
 *
 * <p>一个物品对应两个方块：吊顶的 {@code <木>_hanging_sign} 与
 * 卡墙的 {@code <木>_wall_hanging_sign}，靠视线方向选。
 *
 * <p><b>与普通告示牌的关键差别。</b>官方 1.21 的 {@code StandingAndWallBlockItem}
 * 有一个 {@code attachmentDirection} 参数：普通告示牌传 {@code DOWN}（立在地上），
 * 悬挂告示牌传 {@code UP}（吊在天花板下）。它的选块逻辑是
 * 「跳过 {@code attachmentDirection.getOpposite()}；方向等于 {@code attachmentDirection}
 * 时用主方块，否则用墙上方块」。
 *
 * <p>1.16.4 的 {@link WallOrFloorItem} 把这个方向<b>写死成 DOWN</b>
 * （代码里是 {@code if (direction != Direction.UP)} 加
 * {@code direction == Direction.DOWN ? floor : wall}），
 * 所以悬挂告示牌必须覆写 {@code getStateForPlacement} 把方向翻过来，
 * 否则朝天花板放会得到壁挂形态、根本吊不上去。
 *
 * <p>继承 {@link SignItem} 是为了拿到它的 {@code onBlockPlaced}
 * —— 放置成功后自动弹出编辑界面。
 */
public class ModernHangingSignItem extends SignItem
{
    public ModernHangingSignItem(Item.Properties propertiesIn, Block ceilingBlockIn, Block wallBlockIn)
    {
        super(propertiesIn, ceilingBlockIn, wallBlockIn);
    }

    /**
     * {@link WallOrFloorItem#getStateForPlacement} 的镜像版本：
     * attachmentDirection 由 DOWN 换成 UP。
     */
    @Override
    @Nullable
    protected BlockState getStateForPlacement(BlockItemUseContext context)
    {
        BlockState wallState = this.wallBlock.getStateForPlacement(context);
        BlockState chosen = null;
        IWorldReader iworldreader = context.getWorld();
        BlockPos blockpos = context.getPos();

        for (Direction direction : context.getNearestLookingDirections())
        {
            // 官方：跳过 attachmentDirection.getOpposite()，即 DOWN。
            if (direction != Direction.DOWN)
            {
                BlockState candidate = direction == Direction.UP ? this.getBlock().getStateForPlacement(context) : wallState;

                if (candidate != null && candidate.isValidPosition(iworldreader, blockpos))
                {
                    chosen = candidate;
                    break;
                }
            }
        }

        return chosen != null && iworldreader.placedBlockCollides(chosen, blockpos, ISelectionContext.dummy()) ? chosen : null;
    }

    /**
     * 官方 {@code HangingSignItem.canPlace}：壁挂形态还要额外过一遍
     * {@code WallHangingSignBlock.canPlace}（左右两侧至少一侧有支撑）。
     *
     * <p>1.16.4 的 {@code BlockItem.canPlace} 签名是
     * {@code (BlockItemUseContext, BlockState)}，没有官方那个
     * {@code (LevelReader, BlockState, BlockPos)} 形式，所以在这里对齐。
     */
    @Override
    protected boolean canPlace(BlockItemUseContext context, BlockState state)
    {
        if (state.getBlock() instanceof ModernWallHangingSignBlock
                && !((ModernWallHangingSignBlock)state.getBlock()).canPlace(state, context.getWorld(), context.getPos()))
        {
            return false;
        }

        return super.canPlace(context, state);
    }
}
