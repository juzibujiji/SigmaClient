package com.mentalfrostbyte.jello.module.impl.combat.backtrack;

import com.mentalfrostbyte.Client;
import com.mentalfrostbyte.jello.event.impl.game.network.EventGlobalReceivePacket;
import com.mentalfrostbyte.jello.event.impl.game.network.EventReceivePacket;
import com.mentalfrostbyte.jello.event.impl.game.render.EventRender3D;
import com.mentalfrostbyte.jello.event.impl.game.world.EventLoadWorld;
import com.mentalfrostbyte.jello.event.impl.player.movement.EventMovePacketAfter;
import com.mentalfrostbyte.jello.module.Module;
import com.mentalfrostbyte.jello.module.data.ModuleCategory;
import com.mentalfrostbyte.jello.module.impl.combat.KillAura;
import com.mentalfrostbyte.jello.module.impl.combat.Teams;
import com.mentalfrostbyte.jello.module.settings.impl.BooleanSetting;
import com.mentalfrostbyte.jello.module.settings.impl.ColorSetting;
import com.mentalfrostbyte.jello.module.settings.impl.NumberSetting;
import com.mentalfrostbyte.jello.util.game.MinecraftUtil;
import com.mentalfrostbyte.jello.util.game.player.combat.CombatUtil;
import com.mentalfrostbyte.jello.util.game.render.RenderUtil;
import com.mentalfrostbyte.jello.util.game.world.BoundingBox;
import net.minecraft.client.entity.player.RemoteClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.IPacket;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.play.client.CChatMessagePacket;
import net.minecraft.network.play.server.*;
import net.minecraft.util.math.vector.Vector3d;
import team.sdhq.eventBus.EventBus;
import team.sdhq.eventBus.annotations.EventTarget;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingDeque;

/**
 * 经典（原始）BackTrack 实现：取消并缓存服务端下发的位置/击退等包，
 * 在满足距离/时间条件时再回放，从而让被追踪实体停留在真实位置上以扩大打击窗口。
 * 该模式只适合在 GrimAC 使用，其它 AC 不保证。
 */
public class LegacyBackTrack extends Module {
    public final LinkedBlockingDeque<IPacket<?>> packets = new LinkedBlockingDeque<>();
    private final Map<Integer, Vector3d> realEntityPositions = new ConcurrentHashMap<>();

    private Vector3d targetpos = null;

    public LegacyBackTrack() {
        super(ModuleCategory.COMBAT, "Legacy", "Cancel and replay server packets to keep the target on its real position");

        //reach
        this.registerSetting(new BooleanSetting("UseMaxReach", "Use BackTrack End Max Reach", true));
        this.registerSetting(new NumberSetting<>("MaxReach", "BackTrack End Max Reach", 6.0f, 3.0f, 8.0f, 0.1f));
        this.registerSetting(new BooleanSetting("MaxReachSlowRelease", "BackTrack Max Reach SlowRelease", true));

        this.registerSetting(new BooleanSetting("FollowTarget SlowRelease", "ReleasePacket If ", false));
        this.registerSetting(new NumberSetting<>("FollowStartRealReach", "To Target Real Reach Start ReleasePacket", 5.0f, 1.0f, 8.0f, 0.1f));
        this.registerSetting(new NumberSetting<>("FollowKeepAttackReach", "To Target BackTracking Reach.", 2.8f, 1.0f, 6.0f, 0.1f));

        this.registerSetting(new BooleanSetting("UseMinReach", "Use BackTrack Start Min Reach", false));
        this.registerSetting(new NumberSetting<>("MinReachValue", "BackTrack Start Min Reach", 0.5f, 0.0f, 3.0f, 0.1f));

        //delay setting
        this.registerSetting(new NumberSetting<>("Max PingTick", "BackTrack Max Tick", 50, 10, 500, 10));
        this.registerSetting(new BooleanSetting("Slow Release", "Max Tick SlowRelease", false));
        this.registerSetting(new BooleanSetting("Delay EntityMetadataPacket", "Delay EntityMetadataPacket And EntityPropertiesPacket,This packet can ed PlayerMove", false));

        //render
        this.registerSetting(new BooleanSetting("ESP", "Render Player Real Pos", true));
        this.registerSetting(new NumberSetting<>("BoxExpand", "ESP Box Expand", 0.05f, 0.00f, 1.00f, 0.05f));
        this.registerSetting(new NumberSetting<>("ESPWidth", "ESP Width", 2.0f, 1.0f, 3.0f, 0.5f));
        this.registerSetting(new BooleanSetting("RenderTargetESP", "Render Target ESP", true));
        this.registerSetting(new BooleanSetting("RenderFriendESP", "Render Friend ESP", true));
        this.registerSetting(new BooleanSetting("RenderOtherESP", "Render Other ESP", true));
        this.registerSetting(new ColorSetting("TargetColor", "Target ESP Color", 0xFFFF0000));
        this.registerSetting(new ColorSetting("OtherColor", "Other ESP Color", 0xffffff00));

        //debug
        this.registerSetting(new BooleanSetting("Debug", "Debug TargetReach", false));
    }

    // ---- setting shortcuts ----
    private boolean bool(String name) {
        return this.getBooleanValueFromSettingName(name);
    }

    private float num(String name) {
        return this.getNumberValueBySettingName(name);
    }

    private int numInt(String name) {
        return Math.round(this.getNumberValueBySettingName(name));
    }

    private int color(String name) {
        return (Integer) this.getSettingValueBySettingName(name);
    }

    @Override
    public void onEnable() {
        this.dropAllState();
    }

    @Override
    public void onDisable() {
        if (this.canReplayPackets()) {
            this.releaseAllSPacket();
        } else {
            this.dropAllState();
        }
    }

    @EventTarget
    public void onWorldLoad(EventLoadWorld event) {
        if (!this.isEnabled()) return;
        // Delayed packets refer to the previous ClientWorld and must never be replayed into the new one.
        this.dropAllState();
    }

    @EventTarget
    public void onGlobalReceivePacket(EventGlobalReceivePacket event) {
        if (!this.isEnabled()) return;

        IPacket<?> packet = event.packet;
        if (KillAura.targetEntity != null || !packets.isEmpty()) {
            //小于min距离不开始
            if (KillAura.targetEntity != null && getbacktrackDistance(KillAura.targetEntity) < num("MinReachValue") && bool("UseMinReach") && packets.isEmpty()) {
                return;
            }
            //超过max距离不开始
            if (KillAura.targetEntity != null && getbacktrackDistance(KillAura.targetEntity) > num("MaxReach") && bool("UseMaxReach") && packets.isEmpty()) {
                return;
            }
            if (packet instanceof SPlayerPositionLookPacket) {
                if (!packets.isEmpty()) {
                    event.cancelled = true;
                    packets.add(packet);
                    // 先排空既有队列（主线程串行，release* 的 this 锁保证与入队互斥）
                    releaseAllSPacket();
                }
                // 不 cancel、不缓存：让原版在本次 channelRead0 后正常处理 PosLook，
                // 其处理会被 PacketThreadUtil 入队到主线程，排在上面的排空任务之后。
                return;
            }
            if (NeedCancelSPacket(packet)) {
                //延迟了玩家的元数据包 这里包括了生命值 搞个fake
                if (packet instanceof SEntityMetadataPacket && bool("Delay EntityMetadataPacket")) {
                    RemoteClientPlayerEntity entity = new RemoteClientPlayerEntity(mc.world, mc.player.getGameProfile());
                    entity.setHealth(mc.player.getHealth());
                    entity.getDataManager().setEntryValues(((SEntityMetadataPacket) packet).getDataManagerEntries());
                    mc.player.setHealth(entity.getHealth());
                }

                event.cancelled = true;
                packets.add(packet);

                if (bool("ESP")) {
                    if (realEntityPositions.isEmpty()) {
                        if (mc.world != null) {
                            for (Entity entity : mc.world.getAllEntities()) {
                                if (entity == mc.player) {
                                    continue;
                                }
                                realEntityPositions.put(entity.getEntityId(), entity.getPositionVec());
                            }
                        }
                    }
                    recordRealPosition(packet);
                }
            }
        }
    }

    @EventTarget
    public void onUpdate(EventMovePacketAfter event) {
        if (!this.isEnabled()) return;

        if (bool("Slow Release")) {
            packets.add(new CChatMessagePacket("BackTrack Slow Release Tick"));
        }

        if (!Client.getInstance().moduleManager.getModuleByClass(KillAura.class).isEnabled() || KillAura.targetEntity == null) {
            releaseAllSPacket();
            return;
        }

        //启用最大距离
        if (bool("UseMaxReach")) {
            //启用最大距离且没启用最小距离
            if (!bool("UseMinReach")) {
                //目标超过最大距离释放包
                if (getbacktrackDistance(KillAura.targetEntity) > num("MaxReach")) {
                    if (bool("MaxReachSlowRelease")) {
                        releaseTickSPacket();
                    } else {
                        releaseAllSPacket();
                    }
                }
            } else {
                //启用了最大距离和最小距离
                //目标超过最大距离释放包
                if (getbacktrackDistance(KillAura.targetEntity) > num("MaxReach")) {
                    if (bool("MaxReachSlowRelease")) {
                        releaseTickSPacket();
                    } else {
                        releaseAllSPacket();
                    }
                }
                //目标小于最小距离释放包
                if (getbacktrackDistance(KillAura.targetEntity) < num("MinReachValue")) {
                    releaseAllSPacket();
                }
            }
        }

        //开启最小距离没开启最大距离情况
        if (!bool("UseMaxReach") && bool("UseMinReach")) {
            if (getbacktrackDistance(KillAura.targetEntity) < num("MinReachValue")) {
                releaseAllSPacket();
            }
        }

        if (bool("FollowTarget SlowRelease")) {
            releasefollowSPacket();
        }

        if (packets.stream().filter(p -> p instanceof CChatMessagePacket).count() >= numInt("Max PingTick")) {
            if (bool("Slow Release")) {
                releaseTickSPacket();
            } else {
                releaseAllSPacket();
            }
        }

        if (bool("Debug")) {
            MinecraftUtil.addChatMessage(Client.getInstance().commandManager.getPrefix() + getbacktrackDistance(KillAura.targetEntity) + "Reach");
        }
    }

    @EventTarget
    public void onRender3D(EventRender3D event) {
        if (!this.isEnabled()) return;

        Entity trackedTarget = KillAura.targetEntity;
        if (!bool("ESP") || mc.world == null || packets.isEmpty() || trackedTarget == null) {
            return;
        }

        for (Map.Entry<Integer, Vector3d> entry : realEntityPositions.entrySet()) {
            int entityId = entry.getKey();
            Vector3d realPos = entry.getValue();
            Entity entity = mc.world.getEntityByID(entityId);

            //真实位置 - 视角投影位置
            double renderX = realPos.x - mc.getRenderManager().info.getProjectedView().x;
            double renderY = realPos.y - mc.getRenderManager().info.getProjectedView().y;
            double renderZ = realPos.z - mc.getRenderManager().info.getProjectedView().z;

            BoundingBox realBox = null;
            if (entity != null) {
                realBox = new BoundingBox(
                        renderX - entity.getWidth() / 2,
                        renderY,
                        renderZ - entity.getWidth() / 2,
                        renderX + entity.getWidth() / 2,
                        renderY + entity.getHeight(),
                        renderZ + entity.getWidth() / 2
                ).expand(num("BoxExpand"));
            }

            if (realBox == null) {
                continue;
            }

            if (entity == trackedTarget) {
                if (bool("RenderTargetESP")) {
                    int targetFillColor = (color("TargetColor") & 0x00FFFFFF) | 0x33000000;
                    RenderUtil.render3DColoredBox(realBox, targetFillColor);
                    RenderUtil.renderWireframeBox(realBox, num("ESPWidth"), color("TargetColor"));
                }
            } else {
                if (entity instanceof PlayerEntity && Client.getInstance().moduleManager.getModuleByClass(Teams.class).isEnabled() && CombatUtil.arePlayersOnSameTeam((PlayerEntity) entity) || entity instanceof PlayerEntity && Client.getInstance().friendManager.isFriend(Objects.requireNonNull(entity))) {
                    if (bool("RenderFriendESP")) {
                        RenderUtil.render3DColoredBox(realBox, 0x3300ff00);
                        RenderUtil.renderWireframeBox(realBox, num("ESPWidth"), 0xff00ff00);
                    }
                } else {
                    if (bool("RenderOtherESP")) {
                        int autoFillColor = (color("OtherColor") & 0x00FFFFFF) | 0x33000000;
                        RenderUtil.render3DColoredBox(realBox, autoFillColor);
                        RenderUtil.renderWireframeBox(realBox, num("ESPWidth"), color("OtherColor"));
                    }
                }
            }
        }
    }

    private boolean canReplayPackets() {
        return mc.world != null && mc.player != null && mc.getConnection() != null;
    }

    private void dropAllState() {
        packets.clear();
        this.realEntityPositions.clear();
        this.targetpos = null;
    }

    private void releaseAllSPacket() {
        if (!this.canReplayPackets()) {
            packets.clear();
            this.realEntityPositions.clear();
            return;
        }

        IPacket<?> packet;
        while ((packet = packets.pollFirst()) != null) {
            if (packet instanceof CChatMessagePacket && ((CChatMessagePacket) packet).message.equals("BackTrack Slow Release Tick")) {
                continue;//跳过这个包
            }
            EventReceivePacket event = new EventReceivePacket(packet);
            EventBus.call(event);
            if (!event.cancelled) {
                //已经设置血量过了不设置了
                if (event.packet instanceof SEntityMetadataPacket && bool("Delay EntityMetadataPacket")) {
                    RemoteClientPlayerEntity entity = new RemoteClientPlayerEntity(mc.world, mc.player.getGameProfile());
                    entity.setHealth(mc.player.getHealth());
                    NetworkManager.processPacket(event.packet, Objects.requireNonNull(mc.getConnection()).getNetworkManager().packetListener);
                    mc.player.setHealth(entity.getHealth());
                } else {
                    NetworkManager.processPacket(event.packet, Objects.requireNonNull(mc.getConnection()).getNetworkManager().packetListener);
                }
            }
        }
        realEntityPositions.clear();
    }

    private void releasefollowSPacket() {
        while (!packets.isEmpty()
                // 条件1：真实位置超过设定距离
                && getbacktrackDistance(KillAura.targetEntity) > num("FollowStartRealReach")
                // 条件2：当前延迟位置仍在攻击范围内（作为持续条件）
                && mc.player.getDistance(KillAura.targetEntity) <= num("FollowKeepAttackReach")) {

            // 先peek：如果是tick标记，直接跳过，不打断循环
            if (packets.peek() instanceof CChatMessagePacket chat
                    && chat.message.equals("BackTrack Slow Release Tick")) {
                packets.poll(); // 移除标记
                continue;
            }

            if (targetpos == null) targetpos = KillAura.targetEntity.getPositionVector();

            if (packets.peek() instanceof SEntityPacket entityPacket) {
                if (entityPacket.getEntity(mc.world) == KillAura.targetEntity) {
                    targetpos = new Vector3d(
                            targetpos.x + entityPacket.posX / 4096.0D,
                            targetpos.y + entityPacket.posY / 4096.0D,
                            targetpos.z + entityPacket.posZ / 4096.0D
                    );
                }
            } else if (packets.peek() instanceof SEntityTeleportPacket teleportPacket) {
                if (teleportPacket.getEntityId() == KillAura.targetEntity.getEntityId()) {
                    targetpos = new Vector3d(teleportPacket.getX(), teleportPacket.getY(), teleportPacket.getZ());
                }
            }

            // 释放这个包后，目标会超出攻击范围 → 停止
            if (mc.player.getDistanceToEntityBox(new BoundingBox(
                    targetpos.x - KillAura.targetEntity.getWidth() / 2,
                    targetpos.y,
                    targetpos.z - KillAura.targetEntity.getWidth() / 2,
                    targetpos.x + KillAura.targetEntity.getWidth() / 2,
                    targetpos.y + KillAura.targetEntity.getHeight(),
                    targetpos.z + KillAura.targetEntity.getWidth() / 2
            )) > num("FollowKeepAttackReach")) {
                targetpos = null;
                break;
            }

            // 取出并处理包
            IPacket<?> packet = packets.pollFirst();
            if (packet == null) {
                break;
            }

            EventReceivePacket event = new EventReceivePacket(packet);
            EventBus.call(event);
            if (!event.cancelled) {
                if (event.packet instanceof SEntityMetadataPacket && bool("Delay EntityMetadataPacket")) {
                    RemoteClientPlayerEntity entity = new RemoteClientPlayerEntity(mc.world, mc.player.getGameProfile());
                    entity.setHealth(mc.player.getHealth());
                    NetworkManager.processPacket(event.packet, Objects.requireNonNull(mc.getConnection()).getNetworkManager().packetListener);
                    mc.player.setHealth(entity.getHealth());
                } else {
                    NetworkManager.processPacket(event.packet, Objects.requireNonNull(mc.getConnection()).getNetworkManager().packetListener);
                }
            }
        }
    }

    private void releaseTickSPacket() {
        while (!packets.isEmpty()) {
            IPacket<?> packet = packets.pollFirst();
            if (packet == null) {
                break;
            }
            if (packet instanceof CChatMessagePacket && ((CChatMessagePacket) packet).message.equals("BackTrack Slow Release Tick")) {
                break;//停止
            }
            EventReceivePacket event = new EventReceivePacket(packet);
            EventBus.call(event);
            if (!event.cancelled) {
                //已经设置血量过了不设置了
                if (event.packet instanceof SEntityMetadataPacket && bool("Delay EntityMetadataPacket")) {
                    RemoteClientPlayerEntity entity = new RemoteClientPlayerEntity(mc.world, mc.player.getGameProfile());
                    entity.setHealth(mc.player.getHealth());
                    NetworkManager.processPacket(event.packet, Objects.requireNonNull(mc.getConnection()).getNetworkManager().packetListener);
                    mc.player.setHealth(entity.getHealth());
                } else {
                    NetworkManager.processPacket(event.packet, Objects.requireNonNull(mc.getConnection()).getNetworkManager().packetListener);
                }
            }
        }
    }

    private float getbacktrackDistance(Entity getentity) {
        Vector3d realPos = realEntityPositions.get(getentity.getEntityId());
        if (realPos != null) {
            Entity entity = mc.world.getEntityByID(getentity.getEntityId());
            double renderX = realPos.x;
            double renderY = realPos.y;
            double renderZ = realPos.z;

            return mc.player.getDistanceToEntityBox(new BoundingBox(
                    renderX - entity.getWidth() / 2,
                    renderY,
                    renderZ - entity.getWidth() / 2,
                    renderX + entity.getWidth() / 2,
                    renderY + entity.getHeight(),
                    renderZ + entity.getWidth() / 2
            ));
        }
        return mc.player.getDistanceToEntityBox(getentity);
    }

    private boolean NeedCancelSPacket(IPacket<?> packet) {
        return packet instanceof SExplosionPacket //爆炸与击退
                || packet instanceof SEntityVelocityPacket //击退s12
                || packet instanceof SConfirmTransactionPacket //通信包c0f
                || packet instanceof SKeepAlivePacket
                // SPlayerPositionLookPacket 不再进入缓存：它已在 onGlobalReceivePacket 里作为
                // “硬同步点”单独处理（先排空既有队列，再让原版正常处理位置包并回 CConfirmTeleport）。
                || packet instanceof SEntityPacket //实体位置包s14
                || packet instanceof SEntityTeleportPacket //实体tp包
                || packet instanceof SMultiBlockChangePacket //方块
                || packet instanceof SChangeBlockPacket //方块
                || packet instanceof SCooldownPacket //冷却条?
                || packet instanceof SPlayEntityEffectPacket //效果
                || packet instanceof SEntityStatusPacket && ((SEntityStatusPacket) packet).getOpCode() != 2//实体状态 2为受伤
                || bool("Delay EntityMetadataPacket") && packet instanceof SEntityMetadataPacket && ((SEntityMetadataPacket) packet).getEntityId() == Objects.requireNonNull(mc.player).getEntityId() //玩家数据包 不延迟可能报模拟
                || bool("Delay EntityMetadataPacket") && packet instanceof SEntityPropertiesPacket && ((SEntityPropertiesPacket) packet).getEntityId() == Objects.requireNonNull(mc.player).getEntityId() //玩家属性包 不延迟可能报模拟
                ;
    }

    private void recordRealPosition(IPacket<?> packet) {
        if (mc.world == null) return;

        if (packet instanceof SEntityPacket entityPacket) {
            Entity entity = entityPacket.getEntity(mc.world);
            if (entity != null) {
                double newX = realEntityPositions.getOrDefault(entity.getEntityId(), entity.getPositionVec()).x + entityPacket.posX / 4096.0D;
                double newY = realEntityPositions.getOrDefault(entity.getEntityId(), entity.getPositionVec()).y + entityPacket.posY / 4096.0D;
                double newZ = realEntityPositions.getOrDefault(entity.getEntityId(), entity.getPositionVec()).z + entityPacket.posZ / 4096.0D;
                realEntityPositions.put(entity.getEntityId(), new Vector3d(newX, newY, newZ));
            }
        } else if (packet instanceof SEntityTeleportPacket teleportPacket) {
            Entity entity = mc.world.getEntityByID(teleportPacket.getEntityId());
            Vector3d newPos = new Vector3d(teleportPacket.getX(), teleportPacket.getY(), teleportPacket.getZ());
            realEntityPositions.put(Objects.requireNonNull(entity).getEntityId(), newPos);
        }
    }
}