package com.mentalfrostbyte.jello.module.impl.gui.jello.radar.threat;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.ProjectileEntity;

/**
 * 敌跟踪接口：预测投掷物（其他玩家/生物丢出）的飞行轨迹，判断是否会命中本地玩家。
 *
 * <p>敌我识别说明：客户端拿不到服务器的 owner 数据（生成包里的 ownerId 在部分
 * 服务端实现上不可靠），因此本接口不做敌我判断 —— 调用方先用启发式过滤掉
 * 自己丢出的投掷物（见 WarThunderRadar.isOwnProjectile 的出生点/方向启发式），
 * 再把剩余投掷物交给本接口评估。将来若有更可靠的归属判定
 * （如观战数据、队伍插件协议），替换调用方的过滤器即可，本接口不变。
 *
 * <p>默认实现 {@link BallisticThreatTracker} 逐 tick 推演原版弹道
 * （重力 + 空气阻力 + 方块遮挡），并对本地玩家位置做线性外推。
 *
 * <p>扩展点：实现本接口即可替换预测策略，例如
 * <ul>
 *   <li>考虑玩家操作意图的非线性目标外推</li>
 *   <li>对火球类的加速度场建模</li>
 *   <li>群体投掷物的合成威胁（弹幕覆盖范围）</li>
 * </ul>
 */
public interface ProjectileThreatTracker {

    /**
     * 评估投掷物是否会命中本地玩家。
     * 每帧可重复调用；实现内部按投掷物 tick 缓存推演结果。
     *
     * @param projectile 待评估的投掷物，非空且存活
     * @param self       本地玩家
     * @return 评估结果，永不为 null（无威胁返回 {@link ProjectileThreat#SAFE}）
     */
    ProjectileThreat assess(ProjectileEntity projectile, PlayerEntity self);

    /** 清空全部内部状态（模块关闭 / 切换世界时调用） */
    void reset();
}
