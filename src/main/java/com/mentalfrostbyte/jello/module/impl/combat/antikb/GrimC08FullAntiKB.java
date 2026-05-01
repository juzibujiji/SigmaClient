package com.mentalfrostbyte.jello.module.impl.combat.antikb;

import com.mentalfrostbyte.jello.event.impl.game.network.EventReceivePacket;
import com.mentalfrostbyte.jello.event.impl.game.network.EventSendPacket;
import com.mentalfrostbyte.jello.event.impl.player.EventUpdate;
import com.mentalfrostbyte.jello.module.Module;
import com.mentalfrostbyte.jello.module.data.ModuleCategory;
import net.minecraft.client.network.play.IClientPlayNetHandler;
import net.minecraft.network.IPacket;
import net.minecraft.network.play.client.*;
import net.minecraft.network.play.server.*;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.BlockRayTraceResult;
import net.minecraft.util.math.RayTraceContext;
import net.minecraft.util.math.vector.Vector3d;
import team.sdhq.eventBus.annotations.EventTarget;

import java.util.ArrayList;
import java.util.List;

public class GrimC08FullAntiKB extends Module {

    private boolean cancelNextVelocity = false;
    private boolean delay = false;
    private boolean needClick = false;
    private boolean waitForPing = false;
    private boolean waitForUpdate = false;
    private BlockRayTraceResult hitResult = null;
    private boolean shouldSkip = false;
    private int freezeTicks = 0;
    private final int MAX_FREEZE_TICKS = 20; // 防止无限卡住

    // 用于代替 BlinkManager，存储被拦截的 incoming 包
    private final List<IPacket<?>> incomingPackets = new ArrayList<>();

    public GrimC08FullAntiKB() {
        super(ModuleCategory.COMBAT, "GrimC08Full", "Click Block wait for ServerBlockUpdatePacket");
    }

    @Override
    public void onEnable() {
        cancelNextVelocity = false;
        delay = false;
        needClick = false;
        waitForUpdate = false;
        hitResult = null;
        shouldSkip = false;
        freezeTicks = 0;
        incomingPackets.clear();
    }

    @Override
    public void onDisable() {
        flushPackets();
    }

    @EventTarget
    public void onReceivePacket(EventReceivePacket event) {
        IPacket<?> packet = event.packet;

        // 处理 ServerBlockUpdatePacket (MCP中为 SBlockChangePacket)
        if (packet instanceof SChangeBlockPacket && waitForUpdate) {
            SChangeBlockPacket sBlockChangePacket = (SChangeBlockPacket) packet;
            if (sBlockChangePacket.getPos().equals(mc.player.getPosition())) {
                // LiquidBounce 中 waitTicks(1)，这里通过标识符让下一 Tick 处理
                waitForPing = true;
                needClick = false;
                return;
            }
        }

        if (waitForUpdate || delay) {
            // Blink INCOMING 逻辑：拦截服务端发来的包
            if (delay && !waitForUpdate) {
                incomingPackets.add(packet);
                event.cancelled = true;
            }
            return;
        }

        // 判断是否为玩家受伤 (LocalPlayerDamage)
        if (packet instanceof SEntityStatusPacket) {
            SEntityStatusPacket statusPacket = (SEntityStatusPacket) packet;
            if (statusPacket.getEntity(mc.world) == mc.player && statusPacket.getOpCode() == 2) {
                cancelNextVelocity = true;
            }
        }

        // 判断是否为玩家击退包 (LocalPlayerVelocity)
        if (cancelNextVelocity && (packet instanceof SEntityVelocityPacket || packet instanceof SExplosionPacket)) {
            if (packet instanceof SEntityVelocityPacket && ((SEntityVelocityPacket) packet).getEntityID() != mc.player.getEntityId()) {
                return;
            }
            event.cancelled = true;
            delay = true;
            cancelNextVelocity = false;
            needClick = true;
        }
    }

    @EventTarget
    public void onSendPacket(EventSendPacket event) {
        IPacket<?> packet = event.packet;

        // 对应原代码中的 ServerboundInteractPacket, ServerboundAttackPacket 等
        if (packet instanceof CUseEntityPacket ||
                packet instanceof CSpectatePacket ||
                packet instanceof CPlayerTryUseItemOnBlockPacket) {
            shouldSkip = true;
        }

        // 对应 hasPosition() && waitForUpdate
        if (packet instanceof CPlayerPacket && waitForUpdate) {
            if (packet instanceof CPlayerPacket.PositionPacket || packet instanceof CPlayerPacket.PositionRotationPacket) {
                event.cancelled = true;
            }
        }

        // 对应 1.16.4 的 Ping (ConfirmTransaction 或 KeepAlive)
        if ((packet instanceof CConfirmTransactionPacket || packet instanceof CKeepAlivePacket) && waitForPing) {
            waitForUpdate = false;
            waitForPing = false;
        }
    }

    @EventTarget
    public void onUpdate(EventUpdate event) {
        if (needClick && !shouldSkip && !mc.player.isHandActive()) {
            // TraceFromPlayer: 模拟 Pitch = 90 (向下看) 射线追踪
            Vector3d start = mc.player.getEyePosition(1.0F);
            Vector3d end = start.add(0, -4.5, 0); // 向下延展
            BlockRayTraceResult result = mc.world.rayTraceBlocks(new RayTraceContext(start, end, RayTraceContext.BlockMode.OUTLINE, RayTraceContext.FluidMode.NONE, mc.player));

            if (result.getType() == BlockRayTraceResult.Type.BLOCK) {
                BlockPos offsetPos = result.getPos().offset(result.getFace());
                // 判断相对位置是否为玩家所站的方块
                if (offsetPos.equals(mc.player.getPosition())) {
                    hitResult = result;
                }
            }
        }

        if (hitResult != null) {
            delay = false;

            // 对应 BlinkManager.flush(TransferOrigin.INCOMING)
            flushPackets();

            // 对应 interaction.useItemOn(...)
            if (mc.playerController.processRightClickBlock(mc.player, mc.world, hitResult.getPos(), hitResult.getFace(), hitResult.getHitVec(), Hand.MAIN_HAND).isSuccessOrConsume()) {
                mc.player.swingArm(Hand.MAIN_HAND);
            }

            // 发送修正视角的 MovePlayerPacket
            if (mc.player.rotationPitch != 90f) {
                mc.getConnection().sendPacket(new CPlayerPacket.RotationPacket(mc.player.rotationYaw, 90f, mc.player.onGround));
            } else {
                mc.getConnection().sendPacket(new CPlayerPacket(mc.player.onGround));
            }

            freezeTicks = 0;
            waitForUpdate = true;
            this.hitResult = null;
            needClick = false;
        }

        if (waitForUpdate) {
            event.cancelled = true;
            freezeTicks++;
            if (freezeTicks > MAX_FREEZE_TICKS) {
                waitForUpdate = false;
                waitForPing = false;
                needClick = false;
            }
        }

        shouldSkip = false;
    }

    /**
     * 释放被拦截的服务端数据包并让客户端处理
     */
    @SuppressWarnings("unchecked")
    private void flushPackets() {
        if (incomingPackets.isEmpty() || mc.getConnection() == null) return;

        for (IPacket<?> packet : incomingPackets) {
            try {
                ((IPacket<IClientPlayNetHandler>) packet).processPacket(mc.getConnection());
            } catch (Exception ignored) {
            }
        }
        incomingPackets.clear();
    }
}