package com.mentalfrostbyte.jello.module.impl.gui.jello.radar.threat;

import net.minecraft.util.math.vector.Vector3d;

/**
 * 敌跟踪（投掷物弹道预测）评估结果（不可变）。
 *
 * <p>由 {@link ProjectileThreatTracker} 产出。{@code incoming} 为 true 表示
 * 按当前弹道推演该投掷物将命中本地玩家（的扩展碰撞箱）。
 * 后续功能（自动格挡、闪避方向计算、命中点世界渲染）直接消费该结果。
 */
public final class ProjectileThreat {

    /** 空结果：不构成威胁 */
    public static final ProjectileThreat SAFE = new ProjectileThreat(false, -1, null, Double.MAX_VALUE);

    /** 预测将命中本地玩家 */
    public final boolean incoming;
    /** 预测命中剩余 tick 数（-1 = 不命中） */
    public final int ticksToImpact;
    /** 预测命中点（incoming 时为碰撞箱上的交点；否则为弹道最接近点，可能为 null） */
    public final Vector3d impactPos;
    /** 弹道与本地玩家（预测位置）的最近逼近距离（格），命中时为 0 */
    public final double missDistance;

    public ProjectileThreat(boolean incoming, int ticksToImpact, Vector3d impactPos, double missDistance) {
        this.incoming = incoming;
        this.ticksToImpact = ticksToImpact;
        this.impactPos = impactPos;
        this.missDistance = missDistance;
    }

    /** 预测命中剩余秒数（不命中返回 -1） */
    public float secondsToImpact() {
        return ticksToImpact < 0 ? -1.0F : ticksToImpact / 20.0F;
    }
}
