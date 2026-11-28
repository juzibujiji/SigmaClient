package com.mentalfrostbyte.jello.util.game.player.rotation.util;

import com.mentalfrostbyte.jello.util.game.player.constructor.Rotation;
import net.minecraft.util.math.MathHelper;

import java.util.concurrent.ThreadLocalRandom;

/**
 * 模拟熟练玩家甩鼠标：每次甩头按最小加加速度曲线走（先加速后减速，速度是钟形），走到点就停，不会越来越慢地拖尾。
 * 时长按 Fitts 定律随角度增长：180° 约 3 tick（约 37°、105°、37°），90° 约 2 tick，30° 以内 1 tick；轨迹中途往一侧微微弯。
 * 目标在甩的过程中移动（人在走）时跟着走，跳到别处（换了方块、telly 回正）就从当前角度重新甩。
 * 和 Intave PlacementAnalysis 看转头形状的几项的关系：
 * <ul>
 *     <li>SharpRotation（单 tick 转 170°~190°）：单 tick 最多约 105°；</li>
 *     <li>AngleSnap（大幅转动后停在离 45° 整数倍 0.08° 以内）：停下时 yaw 离 45° 整数倍至少 0.75°；</li>
 *     <li>Snap（前后静止、中间一两 tick 跳 70° 以上）：180° 分 3 tick 能缓和，但峰值那 tick 仍有约 105°，不保证避开。</li>
 * </ul>
 */
public final class HumanRotation {
    /** 离 45° 整数倍至少留这么远（度），比最大的 GCD 步长（约 0.61°）再多一点，过完 GCD 也不会落回去 */
    private static final float SNAP_MARGIN = 0.75F;
    /** Fitts 定律里的目标宽度（度），大约是一个方块面在眼里的大小 */
    private static final float TARGET_WIDTH = 20.0F;

    /** 这次甩头的起点、规划时的目标、一共几 tick、已经走了几 tick、往侧面弯多少（占总角度的比例） */
    private Rotation start;
    private Rotation planned;
    private int duration;
    private int elapsed;
    private float curve;
    /** 上一 tick 给出的角度，用来发现角度被别处改过 */
    private Rotation last;

    public void reset() {
        this.start = null;
    }

    /**
     * 从 current 往 target 转一 tick。
     *
     * @param maxYawStep   每 tick 最多转多少 yaw，不限速传 180
     * @param maxPitchStep 每 tick 最多转多少 pitch，不限速传 180
     * @param deadline     最多再用几 tick 转到（含这一 tick），没有要求传 Integer.MAX_VALUE；限速优先
     */
    public Rotation step(Rotation current, Rotation target, float maxYawStep, float maxPitchStep, int deadline) {
        if (this.start == null || this.elapsed >= this.duration || this.duration - this.elapsed > deadline
                || angle(current, this.last) > 2.0F
                || angle(target, this.planned) > Math.max(10.0F, angle(this.start, this.planned) * 0.25F)) {
            float distance = angle(current, target);
            if (distance < 0.05F) {
                this.start = null;
                return current;
            }

            this.start = current;
            this.elapsed = 0;
            this.duration = Math.min(duration(current, target, maxYawStep, maxPitchStep), Math.max(deadline, 1));
            this.curve = distance > 20.0F ? random(-0.05F, 0.05F) : 0.0F;
        }

        this.planned = target;
        this.elapsed++;
        float progress = minimumJerk((float) this.elapsed / this.duration);
        float dYaw = MathHelper.wrapDegrees(target.yaw - this.start.yaw);
        float dPitch = target.pitch - this.start.pitch;
        // 中途往垂直于方向的一侧偏，两头为 0
        float bend = this.curve * 4.0F * progress * (1.0F - progress);
        float yaw = this.start.yaw + dYaw * progress - dPitch * bend;
        float pitch = this.start.pitch + dPitch * progress + dYaw * bend;

        // 目标中途移动可能让这一 tick 超出规划的速度，按限速截一下
        yaw = current.yaw + MathHelper.clamp(MathHelper.wrapDegrees(yaw - current.yaw), -maxYawStep, maxYawStep);
        pitch = current.pitch + MathHelper.clamp(pitch - current.pitch, -maxPitchStep, maxPitchStep);
        this.last = new Rotation(awayFromSnapAngle(yaw, current.yaw), MathHelper.clamp(pitch, -90.0F, 90.0F));
        return this.last;
    }

    /** 甩这么远要几 tick：Fitts 定律给一个，再按限速加长到最快那一 tick 不超速 */
    private static int duration(Rotation from, Rotation to, float maxYawStep, float maxPitchStep) {
        float dYaw = Math.abs(MathHelper.wrapDegrees(to.yaw - from.yaw));
        float dPitch = Math.abs(to.pitch - from.pitch);
        float distance = MathHelper.sqrt(dYaw * dYaw + dPitch * dPitch);
        int ticks = Math.max(1, MathHelper.ceil(log2(1.0F + distance / TARGET_WIDTH) / random(1.2F, 1.6F)));
        float overSpeed = Math.max(dYaw / maxYawStep, dPitch / maxPitchStep);
        while (ticks < 20 && peakStep(ticks) * overSpeed > 1.0F) {
            ticks++;
        }

        return ticks;
    }

    /** 最小加加速度曲线分成 ticks 段，最大那一段占总角度的比例 */
    private static float peakStep(int ticks) {
        float peak = 0.0F;
        float previous = 0.0F;
        for (int i = 1; i <= ticks; i++) {
            float progress = minimumJerk((float) i / ticks);
            peak = Math.max(peak, progress - previous);
            previous = progress;
        }

        return peak;
    }

    /** 最小加加速度曲线：t 从 0 到 1，位置从 0 到 1，起止速度和加速度都是 0 */
    private static float minimumJerk(float t) {
        return t * t * t * (10.0F - 15.0F * t + 6.0F * t * t);
    }

    private static float angle(Rotation a, Rotation b) {
        if (a == null || b == null) {
            return Float.MAX_VALUE;
        }

        float dYaw = MathHelper.wrapDegrees(a.yaw - b.yaw);
        float dPitch = a.pitch - b.pitch;
        return MathHelper.sqrt(dYaw * dYaw + dPitch * dPitch);
    }

    /**
     * yaw 太靠近 45° 的整数倍时推到 SNAP_MARGIN 之外。推向 from 所在的那一侧：
     * 沿轴向搭路时目标 yaw 常常正好是 90° 的倍数，按 yaw 自己在哪一侧推会每 tick 左右来回跳 2°。
     */
    private static float awayFromSnapAngle(float yaw, float from) {
        float snap = Math.round(yaw / 45.0F) * 45.0F;
        float offset = yaw - snap;
        if (Math.abs(offset) >= SNAP_MARGIN) {
            return yaw;
        }

        float side = Math.signum(MathHelper.wrapDegrees(from - snap));
        if (side == 0.0F) {
            side = offset < 0.0F ? -1.0F : 1.0F;
        }

        return snap + side * (SNAP_MARGIN + random(0.0F, 0.5F));
    }

    private static float log2(float value) {
        return (float) (Math.log(value) / Math.log(2.0D));
    }

    private static float random(float min, float max) {
        return min + ThreadLocalRandom.current().nextFloat() * (max - min);
    }
}
