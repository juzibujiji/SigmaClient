package com.mentalfrostbyte.jello.module.impl.combat.antikb;

import com.mentalfrostbyte.jello.event.impl.game.network.EventReceivePacket;
import com.mentalfrostbyte.jello.event.impl.game.world.EventLoadWorld;
import com.mentalfrostbyte.jello.event.impl.player.EventLivingUpdate;
import com.mentalfrostbyte.jello.event.impl.player.EventRunTicks;
import com.mentalfrostbyte.jello.event.impl.player.EventUpdate;
import com.mentalfrostbyte.jello.event.impl.player.action.EventPlace;
import com.mentalfrostbyte.jello.event.impl.player.movement.EventMoveFlying;
import com.mentalfrostbyte.jello.event.impl.player.movement.EventMoveInput;
import com.mentalfrostbyte.jello.event.impl.player.movement.EventMovePacketAfter;
import com.mentalfrostbyte.jello.module.Module;
import com.mentalfrostbyte.jello.module.data.ModuleCategory;
import com.mentalfrostbyte.jello.module.impl.combat.KillAura;
import com.mentalfrostbyte.jello.module.settings.impl.BooleanSetting;
import com.mentalfrostbyte.jello.module.settings.impl.NumberSetting;
import com.mentalfrostbyte.jello.util.game.MinecraftUtil;
import com.mentalfrostbyte.jello.util.game.player.rotation.RotationCore;
import com.mentalfrostbyte.jello.util.game.world.blocks.BlockUtil;
import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import de.florianmichael.vialoadingbase.ViaLoadingBase;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.IPacket;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.play.client.CAnimateHandPacket;
import net.minecraft.network.play.client.CEntityActionPacket;
import net.minecraft.network.play.client.CUseEntityPacket;
import net.minecraft.network.play.server.*;
import net.minecraft.util.Hand;
import net.minecraft.util.math.EntityRayTraceResult;
import net.minecraft.util.math.RayTraceResult;
import team.sdhq.eventBus.annotations.EventTarget;
import team.sdhq.eventBus.annotations.priority.LowerPriority;
import team.sdhq.eventBus.annotations.priority.LowestPriority;

import java.util.Objects;
import java.util.concurrent.LinkedBlockingQueue;

public class AttackReduceAntiKB extends Module {
    private final BooleanSetting usec0b = new BooleanSetting("UseC0B","UserC0BPacket Sprint", false);
    private final NumberSetting<Integer> keepattacktick = new NumberSetting<>("KeepAttackTick", "KeepAttackReduceTick", 5, 1, 10, 1);
    private final NumberSetting<Integer> oncecount = new NumberSetting<>("Once Count", "Attack once AttackCount", 1, 0, 5, 1);
    //攻击的时候alink
    private final BooleanSetting alink = new BooleanSetting("Attacking Alink","Alink in Attack",false);
    //空中被攻击先alink后释放击退
    private final BooleanSetting alinkinair = new BooleanSetting("Alink If hurt Air","Alink If hurt in air,can use OnGround JumpReset together", false);
    private final NumberSetting<Integer> airalinkdelay = new NumberSetting<>("AirAlinkDelay(MS)","Max Delay timer",1000,50,3000,50);
    private final BooleanSetting autosprint = new BooleanSetting("AutoSprint" ,"LegitSprint",false);
    private final BooleanSetting onlysprint = new BooleanSetting("Only Sprint","Only Sprint", true);
    //地面上时跳跃重置
    private final BooleanSetting ongroundjump = new BooleanSetting("Ground JumpReset","In Ground JumpReset",false);
    private final BooleanSetting raytrace = new BooleanSetting("RayTrace","Need RayTrace Attack", true);
    private final BooleanSetting debug = new BooleanSetting("Debug", "Debug", false);

    //private static final BooleanSetting attackingkillurastopattack = new BooleanSetting("Attacking Killaura Stop Attack","Attacking Killaura Stop Attack", false);
    private static final BooleanSetting disonuse = new BooleanSetting("Disable on use","Disable on use",true);
    public AttackReduceAntiKB() {
        super(ModuleCategory.COMBAT, "AttackReduce", "AttackReduce");
        this.registerSetting(usec0b,keepattacktick,oncecount,alink,alinkinair,airalinkdelay,autosprint,onlysprint,ongroundjump,raytrace,disonuse,debug);
    }
    private final LinkedBlockingQueue<IPacket<?>> alinkpackets = new LinkedBlockingQueue<>();
    public int attackTick = 0;
    private boolean canattack = false;
    private boolean velpacket = false;
    private boolean playerinairalink = false;
    private long airalinkStartTime = 0L;

    private void debugLog(String message) {
        if (debug.getCurrentValue()) {
            MinecraftUtil.addChatMessage("§7[AttackReduce] §f" + message);
        }
    }

    @Override
    public void onDisable() {
        debugLog("Disabled");
        attackTick = 0;
        canattack = false;
        playerinairalink = false;
        releaseAlinkPacket();
    }

    @EventTarget
    public void onUpdate(EventMovePacketAfter event) {
        if (mc.world != null && mc.player != null && this.isEnabled()) {
            if (playerinairalink && (mc.player.onGround || System.currentTimeMillis() - airalinkStartTime >= airalinkdelay.getCurrentValue())) {
                boolean landed = mc.player.onGround;
                playerinairalink = false;
                releaseAlinkPacket();
                canattack = true;
                if (!velpacket) {
                    velpacket = true;
                }
                attackTick = (int) keepattacktick.getCurrentValue().intValue();
                debugLog(landed ? "Landed, released air-alink packets" : "Air-alink max delay " + airalinkdelay.getCurrentValue() + "ms reached, released packets");
            }
            if (attackTick == 0 && !alinkinair.getCurrentValue()){
                releaseAlinkPacket();
            }
        }
    }

    @EventTarget
    public void onReceivePacket(EventReceivePacket event) {
        IPacket<?> packet = event.packet;
        if (mc.world != null && mc.player != null && this.isEnabled()) {
            if ((alink.getCurrentValue() && attackTick > 0) || (playerinairalink && alinkinair.getCurrentValue()) || !alinkpackets.isEmpty()) {
                if (NeedCancelSPacket(packet)) {
                    alinkpackets.add(event.packet);
                    event.cancelled = true;
                    if (packet instanceof SPlayerPositionLookPacket) {
                        releaseAlinkPacket();
                    }
                    return;
                }
            }
            if (packet instanceof SEntityVelocityPacket && ((SEntityVelocityPacket) packet).getEntityID() == mc.player.getEntityId() && (((SEntityVelocityPacket) packet).motionX != 0 || ((SEntityVelocityPacket) packet).motionZ != 0)) {
                if (alinkinair.getCurrentValue()) {
                    if (!mc.player.onGround) {
                        alinkpackets.add(event.packet);
                        event.cancelled = true;
                        playerinairalink = true;
                        airalinkStartTime = System.currentTimeMillis();
                        return;
                    }
                }
                if (disonuse.getCurrentValue() && mc.player.isHandActive()) {
                    canattack = false;
                    attackTick = 0;
                    return;
                }
                canattack = true;
                if (!velpacket) {
                    velpacket = true;
                }
                attackTick = (int) keepattacktick.getCurrentValue().intValue();
                debugLog("Velocity packet received (motionX=" + ((SEntityVelocityPacket) packet).motionX + ", motionZ=" + ((SEntityVelocityPacket) packet).motionZ + "), armed attackTick=" + attackTick);
            }
        }
    }

    //jumpreset
    @EventTarget
    public void onMoveFlyingEvent(EventMoveFlying event) {
        if (this.isEnabled() && mc.player != null && mc.world != null) {
            if (velpacket && ongroundjump.getCurrentValue()) {
                if (mc.player.isSprinting() && mc.player.onGround) {
                    //必须有mc.gameSettings.keyBindJump.isKeyDown()否则和手动按会报antikb
                    if (!mc.gameSettings.keyBindJump.isKeyDown()) {
                        mc.player.jump();
                    }
                }
                velpacket = false;
            }
        }
    }

    //sprint
    @EventTarget
    public void onMoveInputEvent(EventMoveInput event) {
        if (this.isEnabled() && mc.player != null && mc.world != null) {
            //autosprint spirnt
            if (canattack && autosprint.getCurrentValue()) {
                if (!mc.player.isSprinting()) {
                    event.forward = 1.0f;
                    mc.player.setSprinting(true);
                }
            }
            //jumpreset sprint
            if (velpacket && ongroundjump.getCurrentValue()) {
                if (!mc.player.isSprinting() && mc.player.onGround) {
                    event.forward = 1.0f;
                    mc.player.setSprinting(true);
                }
            }
        }
    }

    @EventTarget
    @LowestPriority
    public void onUpdateEvent(EventUpdate event) {
        if ((canattack || attackTick > 0) && mc.world != null && mc.player != null && this.isEnabled()) {
            debugLog("Update: canattack=" + canattack + ", attackTick=" + attackTick);
            Entity entity = null;
            if (mc.objectMouseOver != null && mc.objectMouseOver.getType() == RayTraceResult.Type.ENTITY) {
                entity = ((EntityRayTraceResult)mc.objectMouseOver).getEntity();
            } else if (!BlockUtil.rayTraceEntitiesnolastpos(RotationCore.currentYaw,RotationCore.currentPitch,3.0f,false).isEmpty()) {
                entity = BlockUtil.rayTraceEntitiesnolastpos(RotationCore.currentYaw,RotationCore.currentPitch,3.0f,false).get(0);
            } else if (KillAura.targetEntity != null && !raytrace.getCurrentValue()) {
                entity = KillAura.targetEntity;
            }
            if (entity == null) {
                debugLog("No target entity found, cancelled");
                canattack = false;
                attackTick = 0;
                return;
            }
            if (disonuse.getCurrentValue() && mc.player.isHandActive()) {
                debugLog("Cancelled: using item (DisableOnUse)");
                canattack = false;
                attackTick = 0;
                return;
            }
            boolean state = mc.player.isSprinting();
            if (!state && usec0b.getCurrentValue()) {
                debugLog("Not sprinting, sending C0B START_SPRINTING");
                mc.getConnection().sendPacket(new CEntityActionPacket(mc.player, CEntityActionPacket.Action.START_SPRINTING));
            }
            if (onlysprint.getCurrentValue() && state || !onlysprint.getCurrentValue() || usec0b.getCurrentValue()) {
                debugLog("Attacking " + entity.getName().getUnformattedComponentText() + " x" + oncecount.getCurrentValue() + ", remaining tick=" + (attackTick - 1));
                for (int i = 0; i < oncecount.getCurrentValue(); i++) {
                    if (ViaLoadingBase.getInstance().getTargetVersion().olderThanOrEqualTo(ProtocolVersion.v1_8)) {
                        mc.getConnection().sendPacket(new CAnimateHandPacket(Hand.MAIN_HAND));
                        mc.getConnection().sendPacket(new CUseEntityPacket(entity, mc.player.isSneaking()));
                    } else {
                        mc.getConnection().sendPacket(new CUseEntityPacket(entity, mc.player.isSneaking()));
                        mc.getConnection().sendPacket(new CAnimateHandPacket(Hand.MAIN_HAND));
                    }

                    mc.player.getMotion().x *= 0.6;
                    mc.player.getMotion().z *= 0.6;
                }
                attackTick--;
                debugLog("Executed: " + oncecount.getCurrentValue() + " attack(s) sent, knockback reduced (motionX=" + String.format("%.3f", mc.player.getMotion().x) + ", motionZ=" + String.format("%.3f", mc.player.getMotion().z) + "), remaining tick=" + attackTick + " - effective");
            } else {
                debugLog("Cancelled: not sprinting (OnlySprint)");
                attackTick = 0;
            }

            if (!state && usec0b.getCurrentValue()) {
                mc.getConnection().sendPacket(new CEntityActionPacket(mc.player, CEntityActionPacket.Action.STOP_SPRINTING));
            } else {
                mc.player.setSprinting(false);
            }
            canattack = false;
        }
    }

    private void releaseAlinkPacket() {
        while (!alinkpackets.isEmpty()) {
            IPacket<?> packet = alinkpackets.poll();
            NetworkManager.processPacket(packet, Objects.requireNonNull(mc.getConnection()).getNetworkManager().packetListener);
        }
    }

    @EventTarget
    public void onLoadWorld(EventLoadWorld event) {
        if (this.isEnabled()) {
            attackTick = 0;
            canattack = false;
            playerinairalink = false;
            alinkpackets.clear();
        }
    }

    private boolean NeedCancelSPacket(IPacket<?> packet) {
        return packet instanceof SExplosionPacket //爆炸与击退
                || packet instanceof SEntityVelocityPacket //击退s12
                || packet instanceof SConfirmTransactionPacket //通信包c0f
                || packet instanceof SKeepAlivePacket
                || packet instanceof SPlayerPositionLookPacket
                || packet instanceof SEntityPacket //实体位置包s14
                || packet instanceof SEntityTeleportPacket //实体tp包
                || packet instanceof SMultiBlockChangePacket //方块
                || packet instanceof SChangeBlockPacket //方块
                || packet instanceof SCooldownPacket //冷却条?
                || packet instanceof SPlayEntityEffectPacket //效果
                || packet instanceof SEntityStatusPacket && ((SEntityStatusPacket) packet).getOpCode() != 2//实体状态 2为受伤
                || packet instanceof SEntityMetadataPacket && ((SEntityMetadataPacket) packet).getEntityId() == Objects.requireNonNull(mc.player).getEntityId() //玩家数据包 不延迟可能报模拟
                || packet instanceof SEntityPropertiesPacket && ((SEntityPropertiesPacket) packet).getEntityId() == Objects.requireNonNull(mc.player).getEntityId() //玩家属性包 不延迟可能报模拟
                ;
    }
}
