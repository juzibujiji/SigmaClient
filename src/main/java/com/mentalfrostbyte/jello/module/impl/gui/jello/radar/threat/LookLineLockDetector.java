package com.mentalfrostbyte.jello.module.impl.gui.jello.radar.threat;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.vector.Vector3d;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * 敌锁定默认实现 —— 末影人式视线判定 + 迟滞。
 *
 * <p>判定核心与 {@code EndermanEntity.shouldAttackPlayer} 相同：
 * 观察者视线方向与"观察者眼睛 → 我们眼睛"连线的点积超过
 * {@code 1 - tolerance / distance} 即视为瞄准 —— 阈值随距离收紧，
 * 等效于一个以我们头部为尺度的角窗口（近处宽、远处窄）。
 * 相比固定角度阈值，远距离不会误报"大致朝向我们"的玩家。
 *
 * <p>两处改进：
 * <ul>
 *   <li>使用 {@code rotationYawHead}（头部朝向）而非身体朝向合成视线，
 *       对玩家更准确（身体可能与视线偏离最多 ±50°）</li>
 *   <li>时间迟滞：连续 {@code engageTicks} 个 tick 满足瞄准条件才判定锁定，
 *       中断超过 {@code releaseTicks} 才解除 —— 消除路过扫视造成的告警抖动</li>
 * </ul>
 *
 * <p>可选视线遮挡检查（{@code requireLineOfSight}）：隔墙瞄准不告警。
 */
public class LookLineLockDetector implements EnemyLockDetector {

    /** 状态过期时间（tick）：超过该时长未评估的观察者状态被清除 */
    private static final long STALE_TICKS = 100L;
    /** 清理扫描间隔（tick） */
    private static final long PRUNE_INTERVAL_TICKS = 100L;

    /** 末影人公式的基准容差（0.025 = 原版注视判定），乘 toleranceScale 调节灵敏度 */
    private final double toleranceScale;
    /** 连续满足瞄准条件多少 tick 判定锁定 */
    private final int engageTicks;
    /** 瞄准条件中断多少 tick 解除锁定（迟滞宽限） */
    private final int releaseTicks;
    /** 是否要求视线无方块遮挡 */
    private final boolean requireLineOfSight;

    private final Map<Integer, ObserverState> states = new HashMap<>();
    private long lastPruneTime = Long.MIN_VALUE;

    private static class ObserverState {
        long lastGameTime = Long.MIN_VALUE;
        int aimTicks;   // 连续瞄准 tick 数（宽限期内不清零）
        int missTicks;  // 连续未瞄准 tick 数
    }

    public LookLineLockDetector() {
        this(1.0D, 4, 6, true);
    }

    public LookLineLockDetector(double toleranceScale, int engageTicks, int releaseTicks, boolean requireLineOfSight) {
        this.toleranceScale = toleranceScale;
        this.engageTicks = Math.max(1, engageTicks);
        this.releaseTicks = Math.max(0, releaseTicks);
        this.requireLineOfSight = requireLineOfSight;
    }

    @Override
    public LockAssessment assess(LivingEntity observer, PlayerEntity self) {
        if (observer == null || self == null || observer == self) return LockAssessment.NONE;

        // 观察者头部视线（玩家的 rotationYawHead 由服务器同步，比身体朝向准确）
        Vector3d look = Vector3d.fromPitchYaw(observer.rotationPitch, observer.rotationYawHead).normalize();
        // 观察者眼睛 → 我们眼睛
        Vector3d toSelf = new Vector3d(
                self.getPosX() - observer.getPosX(),
                self.getPosYEye() - observer.getPosYEye(),
                self.getPosZ() - observer.getPosZ());
        double dist = toSelf.length();
        if (dist < 0.5D) return LockAssessment.NONE;
        toSelf = toSelf.scale(1.0D / dist);

        double dot = look.dotProduct(toSelf);
        float aimAngle = (float) Math.toDegrees(Math.acos(MathHelper.clamp(dot, -1.0D, 1.0D)));

        // 末影人公式：阈值随距离收紧（0.025 / dist），toleranceScale 调节窗口宽度
        boolean aimNow = dot > 1.0D - 0.025D * toleranceScale / Math.max(1.0D, dist);
        if (aimNow && requireLineOfSight) {
            aimNow = observer.canEntityBeSeen(self);
        }

        long gameTime = self.world.getGameTime();
        pruneStale(gameTime);

        ObserverState state = states.computeIfAbsent(observer.getEntityId(), id -> new ObserverState());
        // 每帧调用多次，只在游戏 tick 前进时推进计数
        if (gameTime != state.lastGameTime) {
            long elapsed = state.lastGameTime == Long.MIN_VALUE
                    ? 1L
                    : Math.max(1L, Math.min(gameTime - state.lastGameTime, STALE_TICKS));
            state.lastGameTime = gameTime;
            if (aimNow) {
                state.aimTicks += (int) elapsed;
                state.missTicks = 0;
            } else {
                state.missTicks += (int) elapsed;
                if (state.missTicks > releaseTicks) state.aimTicks = 0;
            }
        }

        boolean locking = state.aimTicks >= engageTicks;
        return new LockAssessment(locking, aimAngle, state.aimTicks);
    }

    @Override
    public void reset() {
        states.clear();
        lastPruneTime = Long.MIN_VALUE;
    }

    private void pruneStale(long gameTime) {
        if (lastPruneTime != Long.MIN_VALUE && gameTime - lastPruneTime < PRUNE_INTERVAL_TICKS) return;
        lastPruneTime = gameTime;
        Iterator<ObserverState> it = states.values().iterator();
        while (it.hasNext()) {
            ObserverState s = it.next();
            if (s.lastGameTime != Long.MIN_VALUE && gameTime - s.lastGameTime > STALE_TICKS) it.remove();
        }
    }
}
