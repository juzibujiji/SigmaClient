package net.minecraft.tileentity;

/**
 * 悬挂告示牌方块实体（1.19 加入）。
 *
 * <p>官方对应类 {@code world/level/block/entity/HangingSignBlockEntity}，
 * 它只做三件事：换一个方块实体类型、把文本行高改成 9、把单行最大宽度改成 60
 * （普通告示牌是行高 10 / 宽 90）。文本存储、NBT 读写、命令点击全部继承
 * {@link SignTileEntity}。这里照搬同一套结构。
 *
 * <p>行高与宽度数值取自官方 {@code HangingSignBlockEntity} 的
 * {@code MAX_TEXT_LINE_WIDTH = 60} 与 {@code TEXT_LINE_HEIGHT = 9}。
 * 1.16.4 的 {@code SignTileEntity} 没有这两个可覆写的方法（渲染器里写死成 90 / 10），
 * 所以在这里以常量形式提供，由
 * {@code net.minecraft.client.renderer.tileentity.ModernHangingSignTileEntityRenderer} 读取。
 */
public class ModernHangingSignTileEntity extends SignTileEntity
{
    /** 官方 HangingSignBlockEntity.MAX_TEXT_LINE_WIDTH。 */
    public static final int MAX_TEXT_LINE_WIDTH = 60;
    /** 官方 HangingSignBlockEntity.TEXT_LINE_HEIGHT。 */
    public static final int TEXT_LINE_HEIGHT = 9;

    public ModernHangingSignTileEntity()
    {
        super(TileEntityType.HANGING_SIGN);
    }
}
