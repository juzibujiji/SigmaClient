package com.mentalfrostbyte.jello.module.impl.gui.jello.radar.threat;

/**
 * 敌锁定评估结果（不可变）。
 *
 * <p>由 {@link EnemyLockDetector} 产出，描述某个观察者（其他玩家/生物）
 * 当前是否正在"瞄准"本地玩家，以及瞄准的精确程度与持续时间。
 * 后续功能（如自动闪避、反制提示、锁定来源列表）直接消费该结果即可，
 * 无需关心具体判定算法。
 */
public final class LockAssessment {

    /** 空结果：未瞄准，夹角 180°，持续 0 tick */
    public static final LockAssessment NONE = new LockAssessment(false, 180.0F, 0);

    /** 是否判定为锁定（瞄准条件持续满足了引入阈值时长） */
    public final boolean locking;
    /** 观察者视线与"观察者眼睛 → 本地玩家眼睛"连线的夹角（度），越小越正对 */
    public final float aimAngle;
    /** 瞄准条件连续保持的 tick 数（含迟滞宽限，见实现） */
    public final int holdTicks;

    public LockAssessment(boolean locking, float aimAngle, int holdTicks) {
        this.locking = locking;
        this.aimAngle = aimAngle;
        this.holdTicks = holdTicks;
    }
}
