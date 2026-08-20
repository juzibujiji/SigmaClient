package net.minecraft.block;

import java.util.Optional;
import net.minecraft.util.Direction;
import net.minecraft.util.math.MathHelper;

/**
 * 16 等分水平角度的换算，移植自官方
 * {@code world/level/block/state/properties/RotationSegment} 与它依赖的
 * {@code util/SegmentedAnglePrecision}（精度 4 位，即 16 段）。
 *
 * <p>1.16.4 没有这两个类：普通告示牌把同样的算式内联在
 * {@link StandingSignBlock#getStateForPlacement} 和 {@code SignTileEntityRenderer} 里。
 * 悬挂告示牌要在放置、形状选择、渲染三处都用到，且需要
 * {@code convertToDirection} 这个反向换算（判断能不能与上方的悬挂告示牌串联），
 * 所以单独抽出来。
 *
 * <p>数值等价性：官方 {@code SegmentedAnglePrecision(4)} 的
 * {@code fromDegrees(d) = Math.round(d * 16/360) & 15}，而 Java 的
 * {@code Math.round(float)} 定义就是 {@code floor(x + 0.5)}，
 * 与 1.16.4 {@code StandingSignBlock} 里那句
 * {@code MathHelper.floor(deg * 16.0F / 360.0F + 0.5D) & 15} 完全一致。
 */
public final class ModernRotationSegment
{
    /** 官方 SegmentedAnglePrecision(4) 的 mask = (1 << 4) - 1。 */
    private static final int MASK = 15;
    /** 官方 SegmentedAnglePrecision 的 precision = 4。 */
    private static final int PRECISION = 4;
    /** 官方 SegmentedAnglePrecision.angleToDegree = 360 / 16。 */
    private static final float ANGLE_TO_DEGREE = 360.0F / 16.0F;
    /** 官方 SegmentedAnglePrecision.degreeToAngle = 16 / 360。 */
    private static final float DEGREE_TO_ANGLE = 16.0F / 360.0F;

    private ModernRotationSegment()
    {
    }

    /** 官方 RotationSegment.getMaxSegmentIndex()。 */
    public static int getMaxSegmentIndex()
    {
        return MASK;
    }

    /**
     * 官方 SegmentedAnglePrecision.fromDirection：垂直方向返回 0，
     * 水平方向返回 {@code get2DDataValue() << (precision - 2)}。
     *
     * <p>1.16.4 的 {@code get2DDataValue} 叫 {@code getHorizontalIndex}，
     * 取值同为 SOUTH=0 / WEST=1 / NORTH=2 / EAST=3。
     */
    public static int convertToSegment(Direction directionIn)
    {
        if (directionIn.getAxis().isVertical())
        {
            return 0;
        }

        return directionIn.getHorizontalIndex() << (PRECISION - 2);
    }

    /** 官方 SegmentedAnglePrecision.fromDegrees = normalize(round(deg * degreeToAngle))。 */
    public static int convertToSegment(float degreesIn)
    {
        return MathHelper.floor((double)(degreesIn * DEGREE_TO_ANGLE) + 0.5D) & MASK;
    }

    /**
     * 官方 RotationSegment.convertToDirection：只有 4 个正朝向的段位能反推出方向，
     * 斜向的 12 个段位返回空。
     */
    public static Optional<Direction> convertToDirection(int segmentIn)
    {
        switch (segmentIn)
        {
            case 0:
                return Optional.of(Direction.NORTH);

            case 4:
                return Optional.of(Direction.EAST);

            case 8:
                return Optional.of(Direction.SOUTH);

            case 12:
                return Optional.of(Direction.WEST);

            default:
                return Optional.empty();
        }
    }

    /**
     * 官方 SegmentedAnglePrecision.toDegrees：先归一化，再乘 22.5，
     * 大于等于 180 的减 360（结果落在 [-180, 180)）。
     */
    public static float convertToDegrees(int segmentIn)
    {
        float f = (segmentIn & MASK) * ANGLE_TO_DEGREE;
        return f >= 180.0F ? f - 360.0F : f;
    }
}
