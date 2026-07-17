package com.mentalfrostbyte.jello.module.impl.combat.backtrack;

import com.mentalfrostbyte.Client;
import com.mentalfrostbyte.jello.event.impl.game.network.EventGlobalReceivePacket;
import com.mentalfrostbyte.jello.event.impl.game.network.EventReceivePacket;
import com.mentalfrostbyte.jello.event.impl.game.network.EventSendPacket;
import com.mentalfrostbyte.jello.event.impl.game.render.EventRender3D;
import com.mentalfrostbyte.jello.event.impl.game.world.EventLoadWorld;
import com.mentalfrostbyte.jello.event.impl.player.movement.EventMovePacketAfter;
import com.mentalfrostbyte.jello.module.Module;
import com.mentalfrostbyte.jello.module.data.ModuleCategory;
import com.mentalfrostbyte.jello.module.impl.combat.Teams;
import com.mentalfrostbyte.jello.module.settings.impl.BooleanSetting;
import com.mentalfrostbyte.jello.module.settings.impl.ColorSetting;
import com.mentalfrostbyte.jello.module.settings.impl.NumberSetting;
import com.mentalfrostbyte.jello.util.game.MinecraftUtil;
import com.mentalfrostbyte.jello.util.game.player.combat.CombatUtil;
import com.mentalfrostbyte.jello.util.game.render.RenderUtil;
import com.mentalfrostbyte.jello.util.game.world.BoundingBox;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.IPacket;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.play.client.CUseEntityPacket;
import net.minecraft.network.play.server.*;
import net.minecraft.util.math.vector.Vector3d;
import team.sdhq.eventBus.EventBus;
import team.sdhq.eventBus.annotations.EventTarget;

import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * GPT BackTrack 实现：以“滚动延迟队列”缓存服务端下发的实体位置包，
 * 依据攻击时刻、预测服务端位置与追踪窗口决定何时回放，只在攻击窗口内对当前目标生效。
 */
public class GptBackTrack extends Module {
    private static final int GPT_PACKET_LIMIT = 2048;

    private final LinkedBlockingDeque<DelayedPacket> gptPackets = new LinkedBlockingDeque<>(GPT_PACKET_LIMIT);
    private final AtomicBoolean gptFlushScheduled = new AtomicBoolean();
    private final AtomicBoolean gptReplayScheduled = new AtomicBoolean();
    private final AtomicLong gptStateEpoch = new AtomicLong();
    private final Map<Integer, Vector3d> realEntityPositions = new ConcurrentHashMap<>();
    private volatile int gptTargetId = -1;
    private volatile long gptLastAttackNanos;
    private volatile Vector3d gptServerPosition;

    public GptBackTrack() {
        super(ModuleCategory.COMBAT, "GPT", "Rolling delay queue BackTrack");

        this.registerSetting(new NumberSetting<>("GPT Delay", "Rolling incoming packet delay in milliseconds", 150, 50, 5000, 10));
        this.registerSetting(new NumberSetting<>("GPT Attack Window", "Keep tracking this long after the latest attack", 1000, 100, 3000, 50));
        this.registerSetting(new NumberSetting<>("GPT Min Range", "Lower edge of the target BackTrack window", 1.0f, 0.0f, 6.0f, 0.1f));
        this.registerSetting(new NumberSetting<>("GPT Start Range", "Upper edge of the target BackTrack window", 3.2f, 1.0f, 6.0f, 0.1f));
        this.registerSetting(new NumberSetting<>("GPT Max Real Range", "Flush when the predicted server position exceeds this range", 6.0f, 3.0f, 8.0f, 0.1f));

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

    private int color(String name) {
        return (Integer) this.getSettingValueBySettingName(name);
    }

    private static final class DelayedPacket {
        private final IPacket<?> packet;
        private final long receivedAtNanos;
        private final Vector3d targetPosition;

        private DelayedPacket(IPacket<?> packet, long receivedAtNanos, Vector3d targetPosition) {
            this.packet = packet;
            this.receivedAtNanos = receivedAtNanos;
            this.targetPosition = targetPosition;
        }
    }

    @Override
    public void onEnable() {
        this.dropAllState();
    }

    @Override
    public void onDisable() {
        if (this.canReplayPackets()) {
            this.requestGptFlush(true);
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
        this.onGptReceivePacket(event);
    }

    @EventTarget
    public void onSendPacket(EventSendPacket event) {
        if (!this.isEnabled() || mc.world == null || mc.player == null
                || !(event.packet instanceof CUseEntityPacket attackPacket)
                || attackPacket.getAction() != CUseEntityPacket.Action.ATTACK) {
            return;
        }

        Entity attacked = attackPacket.getEntityFromWorld(mc.world);
        if (!(attacked instanceof LivingEntity) || attacked == mc.player || !attacked.isAlive()) {
            return;
        }

        float localDistance = mc.player.getDistanceToEntityBox(attacked);
        if (!this.isInsideGptTrackingRange(localDistance)) {
            return;
        }

        boolean targetChanged = this.gptTargetId != attacked.getEntityId();
        if (targetChanged) {
            this.requestGptFlush(true);
            this.gptTargetId = attacked.getEntityId();
            this.gptServerPosition = attacked.func_242274_V();
            this.realEntityPositions.clear();
            this.realEntityPositions.put(attacked.getEntityId(), this.gptServerPosition);
        }

        this.gptLastAttackNanos = System.nanoTime();
    }

    @EventTarget
    public void onUpdate(EventMovePacketAfter event) {
        if (!this.isEnabled()) return;
        this.updateGptMode();
    }

    private void onGptReceivePacket(EventGlobalReceivePacket event) {
        IPacket<?> packet = event.packet;

        // Chat is unrelated to combat state and should never appear delayed to the user.
        if (packet instanceof SChatPacket) {
            return;
        }

        if (this.mustFlushGptBefore(packet)) {
            this.requestGptFlush();
            return;
        }

        if (this.gptFlushScheduled.get()) {
            // A target can change while the previous target's queue is draining. Packets
            // pass normally during that boundary, so keep the new target's server base in sync.
            Entity target = this.getGptTarget();
            Vector3d passedPosition = this.predictGptTargetPosition(packet, target);
            if (passedPosition != null) {
                this.gptServerPosition = passedPosition;
                this.realEntityPositions.put(target.getEntityId(), passedPosition);
            }
            return;
        }

        if (!this.shouldQueueGptPackets()) {
            if (!this.gptPackets.isEmpty()) {
                this.requestGptFlush();
            }
            return;
        }

        Entity target = this.getGptTarget();
        Vector3d predictedPosition = this.predictGptTargetPosition(packet, target);
        if (predictedPosition != null) {
            this.gptServerPosition = predictedPosition;
            this.realEntityPositions.put(target.getEntityId(), predictedPosition);

            float realDistance = this.getDistanceToPosition(target, predictedPosition);
            float localDistance = mc.player.getDistanceToEntityBox(target);
            if (realDistance + 1.0E-3F < localDistance
                    || realDistance > num("GPT Max Real Range")) {
                // Never keep a stale position when catching up would make the target easier to hit.
                this.requestGptFlush();
                return;
            }
        }

        if (this.gptPackets.offerLast(new DelayedPacket(packet, System.nanoTime(), predictedPosition))) {
            event.cancelled = true;
        } else {
            // A hard bound prevents a stalled connection from growing this queue indefinitely.
            this.requestGptFlush();
        }
    }

    private void updateGptMode() {
        if (!this.canReplayPackets()) {
            this.dropAllState();
            return;
        }

        Entity target = this.getGptTarget();
        long now = System.nanoTime();
        long attackWindow = TimeUnit.MILLISECONDS.toNanos((long) num("GPT Attack Window"));
        if (target == null || !target.isAlive() || this.gptLastAttackNanos == 0L
                || now - this.gptLastAttackNanos > attackWindow) {
            this.requestGptFlush(true);
            return;
        }

        Vector3d serverPosition = this.gptServerPosition;
        if (serverPosition != null
                && this.getDistanceToPosition(target, serverPosition) > num("GPT Max Real Range")) {
            this.requestGptFlush(true);
            return;
        }

        this.requestExpiredGptReplay();

        if (bool("Debug") && serverPosition != null) {
            float localDistance = mc.player.getDistanceToEntityBox(target);
            float realDistance = this.getDistanceToPosition(target, serverPosition);
            MinecraftUtil.addChatMessage(Client.getInstance().commandManager.getPrefix()
                    + "GPT local=" + localDistance + " real=" + realDistance);
        }
    }

    private boolean shouldQueueGptPackets() {
        if (this.gptFlushScheduled.get() || !this.canReplayPackets()) {
            return false;
        }

        Entity target = this.getGptTarget();
        if (target == null || !target.isAlive() || this.gptLastAttackNanos == 0L) {
            return false;
        }

        long elapsed = System.nanoTime() - this.gptLastAttackNanos;
        if (elapsed > TimeUnit.MILLISECONDS.toNanos((long) num("GPT Attack Window"))) {
            return false;
        }

        if (this.gptPackets.isEmpty()
                && !this.isInsideGptTrackingRange(mc.player.getDistanceToEntityBox(target))) {
            return false;
        }

        Vector3d serverPosition = this.gptServerPosition;
        return serverPosition == null
                || this.getDistanceToPosition(target, serverPosition) <= num("GPT Max Real Range");
    }

    private boolean mustFlushGptBefore(IPacket<?> packet) {
        if (packet instanceof SPlayerPositionLookPacket || packet instanceof SRespawnPacket
                || packet instanceof SJoinGamePacket || packet instanceof SDisconnectPacket
                || packet instanceof SUpdateHealthPacket && ((SUpdateHealthPacket) packet).getHealth() <= 0.0F) {
            return true;
        }

        if (packet instanceof SDestroyEntitiesPacket) {
            for (int entityId : ((SDestroyEntitiesPacket) packet).getEntityIDs()) {
                if (entityId == this.gptTargetId) {
                    return true;
                }
            }
        }

        return false;
    }

    private Vector3d predictGptTargetPosition(IPacket<?> packet, Entity target) {
        if (target == null || this.gptServerPosition == null || mc.world == null) {
            return null;
        }

        if (packet instanceof SEntityPacket) {
            SEntityPacket entityPacket = (SEntityPacket) packet;
            if (entityPacket.func_229745_h_() && entityPacket.getEntity(mc.world) == target) {
                return entityPacket.func_244300_a(this.gptServerPosition);
            }
        } else if (packet instanceof SEntityTeleportPacket) {
            SEntityTeleportPacket teleportPacket = (SEntityTeleportPacket) packet;
            if (teleportPacket.getEntityId() == target.getEntityId()) {
                return new Vector3d(teleportPacket.getX(), teleportPacket.getY(), teleportPacket.getZ());
            }
        }

        return null;
    }

    private Entity getGptTarget() {
        return mc.world == null || this.gptTargetId < 0 ? null : mc.world.getEntityByID(this.gptTargetId);
    }

    private float getDistanceToPosition(Entity entity, Vector3d position) {
        if (mc.player == null || entity == null || position == null) {
            return Float.MAX_VALUE;
        }

        return mc.player.getDistanceToEntityBox(new BoundingBox(
                position.x - entity.getWidth() / 2.0D,
                position.y,
                position.z - entity.getWidth() / 2.0D,
                position.x + entity.getWidth() / 2.0D,
                position.y + entity.getHeight(),
                position.z + entity.getWidth() / 2.0D
        ));
    }

    private boolean isInsideGptTrackingRange(float distance) {
        float minRange = Math.min(num("GPT Min Range"), num("GPT Start Range"));
        float maxRange = Math.max(num("GPT Min Range"), num("GPT Start Range"));
        return distance >= minRange && distance <= maxRange;
    }

    private long findNewestGptTrackingTime(Entity target) {
        Iterator<DelayedPacket> iterator = this.gptPackets.descendingIterator();
        while (iterator.hasNext()) {
            DelayedPacket delayed = iterator.next();
            if (delayed.targetPosition != null
                    && this.isInsideGptTrackingRange(this.getDistanceToPosition(target, delayed.targetPosition))) {
                return delayed.receivedAtNanos;
            }
        }
        return -1L;
    }

    private void requestGptFlush() {
        this.requestGptFlush(true);
    }

    private void requestGptFlush(boolean clearTarget) {
        if (clearTarget) {
            this.clearGptTarget();
        }

        if (!this.canReplayPackets()) {
            this.gptPackets.clear();
            return;
        }

        if (!this.gptFlushScheduled.compareAndSet(false, true)) {
            return;
        }

        final long replayEpoch = this.gptStateEpoch.get();
        final Object replayWorld = mc.world;
        final Object replayConnection = mc.getConnection();
        // enqueue() is deliberately used instead of execute(): execute() runs inline on
        // the render thread, which may still be inside ClientWorld.tickEntities().
        mc.enqueue(() -> {
            try {
                if (replayEpoch == this.gptStateEpoch.get()
                        && mc.world == replayWorld
                        && mc.getConnection() == replayConnection
                        && this.canReplayPackets()) {
                    this.drainAllGptPackets();
                }
            } finally {
                if (replayEpoch == this.gptStateEpoch.get()) {
                    this.gptFlushScheduled.set(false);
                }
            }
        });
    }

    private void requestExpiredGptReplay() {
        if (this.gptFlushScheduled.get() || !this.canReplayPackets()
                || !this.gptReplayScheduled.compareAndSet(false, true)) {
            return;
        }

        final long replayEpoch = this.gptStateEpoch.get();
        final Object replayWorld = mc.world;
        final Object replayConnection = mc.getConnection();
        // The next executor drain happens before the following client tick, after the
        // current entity-map iterator has been released.
        mc.enqueue(() -> {
            try {
                if (replayEpoch == this.gptStateEpoch.get()
                        && mc.world == replayWorld
                        && mc.getConnection() == replayConnection
                        && !this.gptFlushScheduled.get()
                        && this.canReplayPackets()) {
                    this.drainExpiredGptPackets(System.nanoTime());
                }
            } finally {
                if (replayEpoch == this.gptStateEpoch.get()) {
                    this.gptReplayScheduled.set(false);
                }
            }
        });
    }

    private void drainExpiredGptPackets(long now) {
        long delayNanos = TimeUnit.MILLISECONDS.toNanos((long) num("GPT Delay"));
        long cutoffNanos = now - delayNanos;
        Entity target = this.getGptTarget();
        if (target != null && !this.isInsideGptTrackingRange(mc.player.getDistanceToEntityBox(target))) {
            long rangeCutoff = this.findNewestGptTrackingTime(target);
            if (rangeCutoff < 0L) {
                this.drainAllGptPackets();
                this.clearGptTarget();
                return;
            }
            cutoffNanos = Math.max(cutoffNanos, rangeCutoff);
        }

        DelayedPacket delayed;
        while ((delayed = this.gptPackets.peekFirst()) != null
                && delayed.receivedAtNanos <= cutoffNanos) {
            this.gptPackets.pollFirst();
            this.processIncomingPacket(delayed.packet);
        }
    }

    private void drainAllGptPackets() {
        DelayedPacket delayed;
        if (this.canReplayPackets()) {
            while ((delayed = this.gptPackets.pollFirst()) != null) {
                this.processIncomingPacket(delayed.packet);
            }
        } else {
            this.gptPackets.clear();
        }
    }

    private void clearGptTarget() {
        this.gptTargetId = -1;
        this.gptLastAttackNanos = 0L;
        this.gptServerPosition = null;
        this.realEntityPositions.clear();
    }

    @EventTarget
    public void onRender3D(EventRender3D event) {
        if (!this.isEnabled()) return;

        Entity trackedTarget = this.getGptTarget();
        if (!bool("ESP") || mc.world == null || this.gptPackets.isEmpty() || trackedTarget == null) {
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

    private void processIncomingPacket(IPacket<?> packet) {
        if (!this.canReplayPackets()) {
            return;
        }

        EventReceivePacket event = new EventReceivePacket(packet);
        EventBus.call(event);
        if (event.cancelled || mc.getConnection() == null) {
            return;
        }

        NetworkManager.processPacket(event.packet, mc.getConnection().getNetworkManager().packetListener);
    }

    private void dropAllState() {
        this.gptStateEpoch.incrementAndGet();
        this.gptPackets.clear();
        this.gptFlushScheduled.set(false);
        this.gptReplayScheduled.set(false);
        this.gptTargetId = -1;
        this.gptLastAttackNanos = 0L;
        this.gptServerPosition = null;
        this.realEntityPositions.clear();
    }
}
