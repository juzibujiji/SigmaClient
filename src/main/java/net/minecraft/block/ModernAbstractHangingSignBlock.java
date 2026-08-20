package net.minecraft.block;

import net.minecraft.tileentity.ModernHangingSignTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.IBlockReader;

/**
 * 悬挂告示牌（1.19 加入）的公共基类。
 *
 * <p>官方没有这一层 —— {@code CeilingHangingSignBlock} 与 {@code WallHangingSignBlock}
 * 直接继承 {@code SignBlock}，「是不是悬挂告示牌」靠方块标签
 * {@code #minecraft:all_hanging_signs} 判断。1.16.4 没有这两个标签
 * （标签是数据包内容，本项目的 data 目录里没有 1.19 的标签文件），
 * 所以这里抽一层基类，用 {@code instanceof} 代替标签查询：
 *
 * <ul>
 *   <li>{@code #all_hanging_signs} -> {@code instanceof ModernAbstractHangingSignBlock}</li>
 *   <li>{@code #wall_hanging_signs} -> {@code instanceof ModernWallHangingSignBlock}</li>
 * </ul>
 *
 * <p>这两个标签在官方源码里只被悬挂告示牌自己用到（串联判定与贴墙判定），
 * 官方标签内容恰好就是这 24 / 12 个方块，所以 instanceof 与标签查询等价。
 */
public abstract class ModernAbstractHangingSignBlock extends AbstractSignBlock
{
    protected ModernAbstractHangingSignBlock(AbstractBlock.Properties propertiesIn, WoodType woodTypeIn)
    {
        super(propertiesIn, woodTypeIn);
    }

    /** 木种由注册名反推，见 {@link WoodType#fromSignBlockName}。 */
    protected ModernAbstractHangingSignBlock(AbstractBlock.Properties propertiesIn)
    {
        super(propertiesIn);
    }

    /**
     * 官方 {@code CeilingHangingSignBlock.newBlockEntity} /
     * {@code WallHangingSignBlock.newBlockEntity} 都建
     * {@code HangingSignBlockEntity}，不是普通的 {@code SignBlockEntity}。
     */
    @Override
    public TileEntity createNewTileEntity(IBlockReader worldIn)
    {
        return new ModernHangingSignTileEntity();
    }

    /** 代替官方的 {@code state.is(BlockTags.ALL_HANGING_SIGNS)}。 */
    public static boolean isHangingSign(BlockState stateIn)
    {
        return stateIn.getBlock() instanceof ModernAbstractHangingSignBlock;
    }
}
