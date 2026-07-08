package com.mentalfrostbyte.jello.module.impl.gui.jello.radar.threat;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;

/**
 * 敌锁定检测接口：判断某个观察者（其他玩家/生物）是否正在瞄准本地玩家。
 *
 * <p>纯客户端数据即可实现（视角、位置均由服务器同步），不依赖任何服务端插件。
 * 默认实现 {@link LookLineLockDetector} 采用末影人注视判定的距离缩放点积公式，
 * 外加视线遮挡检查与时间迟滞。
 *
 * <p>扩展点：实现本接口即可替换/叠加判定策略，例如
 * <ul>
 *   <li>基于射线-包围盒求交的精确判定（射线打中我们的碰撞箱才算）</li>
 *   <li>结合观察者手持物品（拉弓/持三叉戟时收紧阈值）</li>
 *   <li>多观察者威胁合成（被多人同时瞄准时提升告警等级）</li>
 * </ul>
 * 消费方（雷达等）只依赖本接口与 {@link LockAssessment}。
 */
public interface EnemyLockDetector {

    /**
     * 评估 observer 是否正在瞄准本地玩家。
     * 每帧可重复调用；实现内部按游戏 tick 推进状态。
     *
     * @param observer 被评估的观察者（其他玩家或生物），非空且存活
     * @param self     本地玩家
     * @return 评估结果，永不为 null（无威胁返回 {@link LockAssessment#NONE}）
     */
    LockAssessment assess(LivingEntity observer, PlayerEntity self);

    /** 清空全部内部状态（模块关闭 / 切换世界时调用） */
    void reset();
}
