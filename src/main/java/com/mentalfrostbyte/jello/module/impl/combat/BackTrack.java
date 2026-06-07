package com.mentalfrostbyte.jello.module.impl.combat;

import com.mentalfrostbyte.Client;
import com.mentalfrostbyte.jello.event.impl.game.network.EventGlobalReceivePacket;
import com.mentalfrostbyte.jello.event.impl.game.network.EventReceivePacket;
import com.mentalfrostbyte.jello.event.impl.game.render.EventRender3D;
import com.mentalfrostbyte.jello.event.impl.player.movement.EventMovePacketAfter;
import com.mentalfrostbyte.jello.module.Module;
import com.mentalfrostbyte.jello.module.data.ModuleCategory;
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

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.LinkedBlockingDeque;

//问题有点多有时间我会修复的 完成度85%
//这个只适合在GrimAC使用 其他AC(AntiCheat)不敢保证
public class BackTrack extends Module {
    //reach
    private final static BooleanSetting maxreach = new BooleanSetting("UseMaxReach", "Use BackTrack End Max Reach", true);
    private final static NumberSetting<Float> maxreachvalue = new NumberSetting<>("MaxReach", "BackTrack End Max Reach", 6.0f, 3.0f, 8.0f, 0.1f);
    private final static BooleanSetting maxreachslowrelease = new BooleanSetting("MaxReachSlowRelease", "BackTrack Max Reach SlowRelease", true);

    //以下两个都应该满足 才能开始循环发包
    //真实位置超过设定位置
    //Tracking位置不超过设定位置
    //当开启这个后 可能和其他控制reach发包的不适用 比如maxreachslowrelease
    private final static BooleanSetting followtarget = new BooleanSetting("FollowTarget SlowRelease", "ReleasePacket If ", false);
    //目标超过你多少格就开始释放
    //香草为6格 比如 我们设置6格 Real位置超过这个Reach就开始循环发包
    private final static NumberSetting<Float> followTargetStartReach = new NumberSetting<>("FollowStartRealReach", "To Target Real Reach Start ReleasePacket", 5.0f, 1.0f, 8.0f, 0.1f);
    //让你能打人的距离 非真实位置 而是 Tracking
    //这个作用在于发包后你仍然能攻击到实体的距离 即必须让攻击的实体在范围内
    //其实我们可以直接用killaura reach
    private final static NumberSetting<Float> followTargetKeepAttackReach = new NumberSetting<>("FollowKeepAttackReach", "To Target BackTracking Reach.", 2.8f, 1.0f, 6.0f, 0.1f);


    //targetTotargetreach:这里可以写玩家自己到目标的真实位置 大于某个值就循环发包 大概就是玩家到目标真实位置的值在定值而且在释放包时杀戮应该一直能打到backtrack实体 我们跟着backtrack目标实体打人
    //但是太难写了先随便凑合着用
    /*private final static BooleanSetting targetTotargetreach = new BooleanSetting("TargetToTargetReach", "FastSlowRelease TargetToTargetRealReach To VanillaReach", true);
    private final static NumberSetting<Float> targetTotargetreachvalue = new NumberSetting<>("TargetToTargetReachValue", "TargetToTargetReachValue", 6.0f, 3.0f, 8.0f, 0.1f);*/

    private final static BooleanSetting minreach = new BooleanSetting("UseMinReach", "Use BackTrack Start Min Reach", false);
    private final static NumberSetting<Float> minreachvalue = new NumberSetting<>("MinReachValue", "BackTrack Start Min Reach", 0.5f, 0.0f, 3.0f, 0.1f);
    //delay setting
    private final static NumberSetting<Integer> pingtick = new NumberSetting<>("Max PingTick", "BackTrack Max Tick", 50, 10, 500, 10);
    private final static BooleanSetting maxtickslowrelease = new BooleanSetting("Slow Release", "Max Tick SlowRelease", false);
    private final static BooleanSetting entitymetadatapacket = new BooleanSetting("Delay EntityMetadataPacket", "Delay EntityMetadataPacket And EntityPropertiesPacket,This packet can ed PlayerMove", false);
    //render
    private final static BooleanSetting esprender = new BooleanSetting("ESP", "Render Player Real Pos", true);
    private final static NumberSetting<Float> espboxexpand = new NumberSetting<>("BoxExpand","ESP Box Expand",0.05f,0.00f,1.00f,0.05f);
    private final static NumberSetting<Float> espwidth = new NumberSetting<>("ESPWidth","ESP Width",2.0f,1.0f,3.0f,0.5f);
    private final static BooleanSetting rendertargetesp = new BooleanSetting("RenderTargetESP", "Render Target ESP", true);
    private final static BooleanSetting renderfriendesp = new BooleanSetting("RenderFriendESP", "Render Friend ESP", true);
    private final static BooleanSetting renderotheresp = new BooleanSetting("RenderOtherESP", "Render Other ESP", true);
    //private final static ColorSetting targetfillColor = new ColorSetting("TargetFillColor","Target ESP FillColor",0x33FF0000);
    private final static ColorSetting targetwireColor = new ColorSetting("Target"+/*Wire*/"Color", "Target ESP "+/*Wire*/"Color",0xFFFF0000);
    //private final static ColorSetting fillColor = new ColorSetting("OtherFillColor","Other ESP FillColor",0x33ffff00);
    private final static ColorSetting wireColor = new ColorSetting("Other"+/*Wire*/"Color", "Other ESP "+/*Wire*/"Color",0xffffff00);
    //debug
    private final static BooleanSetting reachdebug = new BooleanSetting("Debug", "Debug TargetReach", false);
    public final static LinkedBlockingDeque<IPacket<?>> packets = new LinkedBlockingDeque<>();
    //Inteager:玩家实体ID Vector3d:玩家实体位置
    private final Map<Integer, Vector3d> realEntityPositions = new HashMap<>();
    private int ticks = 0;

    public BackTrack() {
        super(ModuleCategory.COMBAT, "BackTrack", "Track and render entity real positions");
        registerSetting(maxreach,maxreachvalue,maxreachslowrelease,followtarget,followTargetStartReach,followTargetKeepAttackReach,/*targetTotargetreach,targetTotargetreachvalue,*/minreach,minreachvalue,pingtick, maxtickslowrelease, entitymetadatapacket, esprender, espboxexpand, espwidth,rendertargetesp,renderfriendesp,renderotheresp, /*targetfillColor,*/ targetwireColor,  /*fillColor,*/ wireColor,reachdebug);
    }

    @Override
    public void onDisable() {
        releaseAllSPacket();
        realEntityPositions.clear();
    }

    @EventTarget
    public void onGlobalReceivePacket(EventGlobalReceivePacket event) {
        IPacket<?> packet = event.packet;
        if (KillAura.targetEntity != null || !packets.isEmpty()) {
            //小于min距离不开始
            if (KillAura.targetEntity != null && getbacktrackDistance(KillAura.targetEntity) < minreachvalue.getCurrentValue() && minreach.getCurrentValue() && packets.isEmpty()) {
                return;
            }
            //超过max距离不开始
            if (KillAura.targetEntity != null && getbacktrackDistance(KillAura.targetEntity) > maxreachvalue.getCurrentValue() && maxreach.getCurrentValue() && packets.isEmpty()) {
                return;
            }
            if (NeedCancelSPacket(packet)) {
                //延迟了玩家的元数据包 这里包括了生命值 搞个fake
                //我也不知道这个SEntityMetadataPacket和SEntityPropertiesPacket具体是啥
                if (packet instanceof SEntityMetadataPacket && entitymetadatapacket.getCurrentValue()) {
                    RemoteClientPlayerEntity entity = new RemoteClientPlayerEntity(mc.world, mc.player.getGameProfile());
                    entity.setHealth(mc.player.getHealth());
                    entity.getDataManager().setEntryValues(((SEntityMetadataPacket) packet).getDataManagerEntries());
                    mc.player.setHealth(entity.getHealth());
                }

                event.cancelled = true;
                packets.add(packet);

                if (esprender.getCurrentValue()) {
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

                if (packet instanceof SPlayerPositionLookPacket) {
                    releaseAllSPacket();
                }
            }
        }
    }

    @EventTarget
    public void onUpdate(EventMovePacketAfter event) {
        ticks++;

        if (maxtickslowrelease.getCurrentValue()) {
            packets.add(new CChatMessagePacket("BackTrack Slow Release Tick"));
        }

        if (!Client.getInstance().moduleManager.getModuleByClass(KillAura.class).isEnabled() || KillAura.targetEntity == null) {
            releaseAllSPacket();
            return;
        }

        /*if (targetTotargetreach.getCurrentValue()) {
            if (getTEtoTEbacktrackDistance(KillAura.targetEntity) > targetTotargetreachvalue.getCurrentValue() && !packets.isEmpty()) {
                releaseTickSPacket();
            }
        }*/

        //启用最大距离
        if (maxreach.getCurrentValue()) {
            //启用最大距离且没启用最小距离
            if (!minreach.getCurrentValue()) {
                //目标超过最大距离释放包
                if (getbacktrackDistance(KillAura.targetEntity) > maxreachvalue.getCurrentValue()) {
                    if (maxreachslowrelease.getCurrentValue()) {
                        releaseTickSPacket();
                    } else {
                        releaseAllSPacket();
                    }
                }
            } else {
                //启用了最大距离和最小距离
                //目标超过最大距离释放包
                if (getbacktrackDistance(KillAura.targetEntity) > maxreachvalue.getCurrentValue()) {
                    if (maxreachslowrelease.getCurrentValue()) {
                        releaseTickSPacket();
                    } else {
                        releaseAllSPacket();
                    }
                }
                //目标小于最小距离释放包
                if (getbacktrackDistance(KillAura.targetEntity) < minreachvalue.getCurrentValue()) {
                    releaseAllSPacket();
                }
            }
        }

        //开启最小距离没开启最大距离情况
        if (!maxreach.getCurrentValue() && minreach.getCurrentValue()) {
            if (getbacktrackDistance(KillAura.targetEntity) < minreachvalue.getCurrentValue()) {
                releaseAllSPacket();
            }
        }

        if (followtarget.getCurrentValue()) {
            releasefollowSPacket();
        }

        //我这是不是应该用 判断packetlist里的c01更好?
        if (ticks >= pingtick.getCurrentValue()) {
            if (maxtickslowrelease.getCurrentValue()) {
                releaseTickSPacket();
                //test debug
                //MinecraftUtil.addChatMessage(Client.getInstance().commandManager.getPrefix() + getbacktrackDistance(KillAura.targetEntity) + "Reach");
            } else {
                releaseAllSPacket();
            }
        }

        if (reachdebug.getCurrentValue()) {
            MinecraftUtil.addChatMessage(Client.getInstance().commandManager.getPrefix() + getbacktrackDistance(KillAura.targetEntity) + "Reach");
        }
    }

    @EventTarget
    public void onRender3D(EventRender3D event) {
        if (!esprender.getCurrentValue() || mc.world == null || packets.isEmpty() || KillAura.targetEntity == null) {
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
                ).expand(espboxexpand.getCurrentValue());
            }

            // 渲染样式（红色线框+半透明红色填充）
            /*int wireColor = 0xFFFF0000; // 红色线框
            int fillColor = 0x33FF0000; // 半透明红色填充*/

            // 绘制填充框和线框
            if (entity == KillAura.targetEntity) {
                if (rendertargetesp.getCurrentValue()) {
                    int targetFillColor = (targetwireColor.getCurrentValue() & 0x00FFFFFF) | 0x33000000;
                    RenderUtil.render3DColoredBox(realBox, targetFillColor);
                    RenderUtil.renderWireframeBox(realBox, espwidth.getCurrentValue(), targetwireColor.getCurrentValue());
                }
            } else {
                if (entity instanceof PlayerEntity && Client.getInstance().moduleManager.getModuleByClass(Teams.class).isEnabled() && CombatUtil.arePlayersOnSameTeam((PlayerEntity) entity) || entity instanceof PlayerEntity && Client.getInstance().friendManager.isFriend(Objects.requireNonNull(entity))) {
                    if (renderfriendesp.getCurrentValue()) {
                        RenderUtil.render3DColoredBox(realBox, 0x3300ff00);
                        RenderUtil.renderWireframeBox(realBox, espwidth.getCurrentValue(), 0xff00ff00);
                    }
                } else {
                    if (renderotheresp.getCurrentValue()) {
                        int autoFillColor = (wireColor.getCurrentValue() & 0x00FFFFFF) | 0x33000000;
                        RenderUtil.render3DColoredBox(realBox, autoFillColor);
                        RenderUtil.renderWireframeBox(realBox, espwidth.getCurrentValue(), wireColor.getCurrentValue());
                    }
                }
            }
        }
    }

    private void releaseAllSPacket() {
        while (!packets.isEmpty()) {
            IPacket<?> packet;
            try {
                packet = packets.take();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            if (packet instanceof CChatMessagePacket && ((CChatMessagePacket) packet).message.equals("BackTrack Slow Release Tick")) {
                continue;//跳过这个包
            }
            EventReceivePacket event = new EventReceivePacket(packet);
            EventBus.call(event);
            if (!event.cancelled) {
                //已经设置血量过了不设置了
                if (event.packet instanceof SEntityMetadataPacket && entitymetadatapacket.getCurrentValue()) {
                    RemoteClientPlayerEntity entity = new RemoteClientPlayerEntity(mc.world, mc.player.getGameProfile());
                    entity.setHealth(mc.player.getHealth());
                    NetworkManager.processPacket(event.packet, Objects.requireNonNull(mc.getConnection()).getNetworkManager().packetListener);
                    mc.player.setHealth(entity.getHealth());
                } else {
                    NetworkManager.processPacket(event.packet, Objects.requireNonNull(mc.getConnection()).getNetworkManager().packetListener);
                }
            }
        }
        ticks = 0;
        realEntityPositions.clear();
    }

    Vector3d targetpos = null;
    private void releasefollowSPacket() {
        while (!packets.isEmpty()
                // 条件1：真实位置超过设定距离
                && getbacktrackDistance(KillAura.targetEntity) > followTargetStartReach.getCurrentValue()
                // 条件2：当前延迟位置仍在攻击范围内（作为持续条件）
                && mc.player.getDistance(KillAura.targetEntity) <= followTargetKeepAttackReach.getCurrentValue()) {

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
            )) > followTargetKeepAttackReach.getCurrentValue()) {
                targetpos = null;
                break;
            }

            // 取出并处理包
            IPacket<?> packet;
            try {
                packet = packets.take();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }

            EventReceivePacket event = new EventReceivePacket(packet);
            EventBus.call(event);
            if (!event.cancelled) {
                if (event.packet instanceof SEntityMetadataPacket && entitymetadatapacket.getCurrentValue()) {
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
            IPacket<?> packet;
            try {
                packet = packets.take();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            if (packet instanceof CChatMessagePacket && ((CChatMessagePacket) packet).message.equals("BackTrack Slow Release Tick")) {
                break;//停止
            }
            EventReceivePacket event = new EventReceivePacket(packet);
            EventBus.call(event);
            if (!event.cancelled) {
                //已经设置血量过了不设置了
                if (event.packet instanceof SEntityMetadataPacket && entitymetadatapacket.getCurrentValue()) {
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

    /*private float getTEtoTEbacktrackDistance(Entity getentity) {
        Vector3d realPos = realEntityPositions.get(getentity.getEntityId());
        if (realPos != null) {
            Entity entity = mc.world.getEntityByID(getentity.getEntityId());
            double renderX = realPos.x;
            double renderY = realPos.y;
            double renderZ = realPos.z;

            return getentity.getDistanceToEntityBox(new BoundingBox(
                    renderX - entity.getWidth() / 2,
                    renderY,
                    renderZ - entity.getWidth() / 2,
                    renderX + entity.getWidth() / 2,
                    renderY + entity.getHeight(),
                    renderZ + entity.getWidth() / 2
            ));
        }
        return getentity.getDistanceToEntityBox(getentity);
    }*/

    private boolean NeedCancelSPacket(IPacket<?> packet) {
        return packet instanceof SExplosionPacket //爆炸与击退
                || packet instanceof SEntityVelocityPacket //击退s12
                || packet instanceof SConfirmTransactionPacket //通信包c0f
                || packet instanceof SPlayerPositionLookPacket //回弹包s08
                || packet instanceof SEntityPacket //实体位置包s14
                || packet instanceof SEntityTeleportPacket //实体tp包
                || packet instanceof SMultiBlockChangePacket //方块
                || packet instanceof SChangeBlockPacket //方块
                || packet instanceof SCooldownPacket //冷却条?
                || packet instanceof SPlayEntityEffectPacket //效果
                || packet instanceof SEntityStatusPacket && ((SEntityStatusPacket) packet).getOpCode() != 2//实体状态 2为受伤
                || entitymetadatapacket.getCurrentValue() && packet instanceof SEntityMetadataPacket && ((SEntityMetadataPacket) packet).getEntityId() == Objects.requireNonNull(mc.player).getEntityId() //玩家数据包 不延迟可能报模拟 原因:这个包会设置玩家的移动相关似乎是疾跑
                || entitymetadatapacket.getCurrentValue() && packet instanceof SEntityPropertiesPacket && ((SEntityPropertiesPacket) packet).getEntityId() == Objects.requireNonNull(mc.player).getEntityId() //玩家属性包 不延迟可能报模拟 原因:这个包会设置玩家的移动相关似乎是疾跑
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