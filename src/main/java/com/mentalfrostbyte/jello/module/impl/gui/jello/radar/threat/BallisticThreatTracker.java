package com.mentalfrostbyte.jello.module.impl.gui.jello.radar.threat;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.AbstractArrowEntity;
import net.minecraft.entity.projectile.DamagingProjectileEntity;
import net.minecraft.entity.projectile.LlamaSpitEntity;
import net.minecraft.entity.projectile.PotionEntity;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.entity.projectile.ShulkerBulletEntity;
import net.minecraft.entity.projectile.ThrowableEntity;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockRayTraceResult;
import net.minecraft.util.math.RayTraceContext;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.vector.Vector3d;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;

/**
 * 敌跟踪默认实现 —— 原版弹道逐 tick 推演。
 *
 * <p>推演循环与原版投掷物 tick 顺序一致（先按当前速度位移，再乘阻力、减重力）：
 * <pre>  pos += motion;  motion = motion * drag - (0, gravity, 0)</pre>
 * 物理参数按投掷物类型分派（数值取自对应实体的 tick 实现）：
 * <ul>
 *   <li>喷溅药水 0.05 / 0.99（PotionEntity#getGravityVelocity）</li>
 *   <li>雪球/鸡蛋/珍珠等 ThrowableEntity 0.03 / 0.99</li>
 *   <li>箭/三叉戟 0.05 / 0.99（AbstractArrowEntity#tick）</li>
 *   <li>羊驼唾沫 0.06 / 0.99</li>
 *   <li>火球/潜影贝弹（自驱动）0 / 1.0 —— 按直线近似</li>
 * </ul>
 *
 * <p>命中判定：每步用线段与本地玩家碰撞箱（扩展 {@code hitPadding}，
 * 并按玩家当前速度逐 tick 线性外推位置）做射线求交；同时可选做方块遮挡
 * 射线检测，弹道打到方块即终止（墙后的投掷物不告警）。
 *
 * <p>性能：结果按投掷物 tick 缓存 —— 同一 tick 内的多次渲染帧调用直接命中缓存，
 * 每个投掷物每 tick 至多推演一次（≤ maxTicks 段，每段一次方块射线）。
 */
public class BallisticThreatTracker implements ProjectileThreatTracker {

    /** 缓存过期时间（tick） */
    private static final long STALE_TICKS = 40L;
    private static final long PRUNE_INTERVAL_TICKS = 100L;

    /** 最大推演 tick 数 */
    private final int maxTicks;
    /** 玩家碰撞箱扩展半径（格），补偿目标外推误差 */
    private final double hitPadding;
    /** 是否做方块遮挡检测（关闭可省每段一次的射线开销） */
    private final boolean respectBlocks;

    private final Map<Integer, CachedResult> cache = new HashMap<>();
    private long lastPruneTime = Long.MIN_VALUE;

    private static class CachedResult {
        int atTick;
        long lastGameTime;
        ProjectileThreat threat;
    }

    public BallisticThreatTracker() {
        this(80, 0.4D, true);
    }

    public BallisticThreatTracker(int maxTicks, double hitPadding, boolean respectBlocks) {
        this.maxTicks = Math.max(1, maxTicks);
        this.hitPadding = hitPadding;
        this.respectBlocks = respectBlocks;
    }

    @Override
    public ProjectileThreat assess(ProjectileEntity projectile, PlayerEntity self) {
        if (projectile == null || self == null || !projectile.isAlive()) return ProjectileThreat.SAFE;

        long gameTime = self.world.getGameTime();
        pruneStale(gameTime);

        CachedResult cached = cache.computeIfAbsent(projectile.getEntityId(), id -> new CachedResult());
        // 同一投掷物 tick 内复用推演结果（渲染每帧调用，逻辑每 tick 才前进）
        if (cached.threat != null && cached.atTick == projectile.ticksExisted) {
            cached.lastGameTime = gameTime;
            return cached.threat;
        }

        ProjectileThreat threat = simulate(projectile, self);
        cached.atTick = projectile.ticksExisted;
        cached.lastGameTime = gameTime;
        cached.threat = threat;
        return threat;
    }

    @Override
    public void reset() {
        cache.clear();
        lastPruneTime = Long.MIN_VALUE;
    }

    private ProjectileThreat simulate(ProjectileEntity projectile, PlayerEntity self) {
        Vector3d pos = projectile.getPositionVec();
        Vector3d motion = projectile.getMotion();
        double gravity = gravityOf(projectile);
        double drag = dragOf(projectile);

        AxisAlignedBB baseBox = self.getBoundingBox().grow(hitPadding);
        Vector3d selfMotion = self.getMotion();

        double minMiss = Double.MAX_VALUE;
        Vector3d closestPos = null;

        for (int t = 1; t <= maxTicks; t++) {
            Vector3d next = pos.add(motion);

            // 目标外推：假设我们保持当前速度直线移动 t tick
            AxisAlignedBB box = baseBox.offset(selfMotion.scale(t));
            Optional<Vector3d> hit = box.rayTrace(pos, next);
            if (hit.isPresent()) {
                return new ProjectileThreat(true, t, hit.get(), 0.0D);
            }

            Vector3d center = new Vector3d(
                    (box.minX + box.maxX) * 0.5D,
                    (box.minY + box.maxY) * 0.5D,
                    (box.minZ + box.maxZ) * 0.5D);
            Vector3d nearest = closestPointOnSegment(pos, next, center);
            double miss = nearest.distanceTo(center);
            if (miss < minMiss) {
                minMiss = miss;
                closestPos = nearest;
            }

            if (respectBlocks) {
                BlockRayTraceResult blockHit = projectile.world.rayTraceBlocks(new RayTraceContext(
                        pos, next, RayTraceContext.BlockMode.COLLIDER, RayTraceContext.FluidMode.NONE, projectile));
                if (blockHit.getType() != RayTraceResult.Type.MISS) break;
            }

            pos = next;
            motion = motion.scale(drag);
            motion = new Vector3d(motion.x, motion.y - gravity, motion.z);

            // 无重力且几乎停止的弹体不再前进，提前结束
            if (gravity <= 0.0D && motion.lengthSquared() < 1.0E-6D) break;
        }
        return new ProjectileThreat(false, -1, closestPos, minMiss);
    }

    /** 每 tick 重力加速度（格/tick²），数值来自各实体 tick 实现 */
    protected double gravityOf(ProjectileEntity projectile) {
        if (projectile instanceof PotionEntity) return 0.05D;
        if (projectile instanceof ThrowableEntity) return 0.03D;
        if (projectile instanceof AbstractArrowEntity) return 0.05D;
        if (projectile instanceof LlamaSpitEntity) return 0.06D;
        if (projectile instanceof DamagingProjectileEntity) return 0.0D;   // 火球：自驱动
        if (projectile instanceof ShulkerBulletEntity) return 0.0D;       // 追踪弹：直线近似
        return 0.03D;
    }

    /** 每 tick 空气阻力系数 */
    protected double dragOf(ProjectileEntity projectile) {
        if (projectile instanceof DamagingProjectileEntity) return 1.0D;  // 火球实际带加速度，近似匀速
        if (projectile instanceof ShulkerBulletEntity) return 1.0D;
        return 0.99D;
    }

    private static Vector3d closestPointOnSegment(Vector3d a, Vector3d b, Vector3d p) {
        Vector3d ab = b.subtract(a);
        double lenSq = ab.lengthSquared();
        if (lenSq < 1.0E-9D) return a;
        double t = p.subtract(a).dotProduct(ab) / lenSq;
        t = Math.max(0.0D, Math.min(1.0D, t));
        return a.add(ab.scale(t));
    }

    private void pruneStale(long gameTime) {
        if (lastPruneTime != Long.MIN_VALUE && gameTime - lastPruneTime < PRUNE_INTERVAL_TICKS) return;
        lastPruneTime = gameTime;
        Iterator<CachedResult> it = cache.values().iterator();
        while (it.hasNext()) {
            CachedResult c = it.next();
            if (gameTime - c.lastGameTime > STALE_TICKS) it.remove();
        }
    }
}
