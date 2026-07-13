package com.mentalfrostbyte.jello.module.impl.combat;

import com.mentalfrostbyte.Client;
import com.mentalfrostbyte.jello.event.impl.game.network.EventGlobalReceivePacket;
import com.mentalfrostbyte.jello.event.impl.game.network.EventSendPacket;
import com.mentalfrostbyte.jello.event.impl.game.world.EventLoadWorld;
import com.mentalfrostbyte.jello.event.impl.game.world.EventTick;
import com.mentalfrostbyte.jello.module.Module;
import com.mentalfrostbyte.jello.module.data.ModuleCategory;
import com.mentalfrostbyte.jello.module.settings.impl.BooleanSetting;
import com.mentalfrostbyte.jello.module.settings.impl.NumberSetting;
import com.mentalfrostbyte.jello.util.game.player.combat.CombatUtil;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.AxeItem;
import net.minecraft.item.Item;
import net.minecraft.item.SwordItem;
import net.minecraft.network.IPacket;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.play.client.CEntityActionPacket;
import net.minecraft.network.play.client.CInputPacket;
import net.minecraft.network.play.client.CMoveVehiclePacket;
import net.minecraft.network.play.client.CPlayerDiggingPacket;
import net.minecraft.network.play.client.CPlayerPacket;
import net.minecraft.network.play.client.CPlayerTryUseItemOnBlockPacket;
import net.minecraft.network.play.client.CPlayerTryUseItemPacket;
import net.minecraft.network.play.client.CUseEntityPacket;
import net.minecraft.network.play.server.SPlayerPositionLookPacket;
import net.minecraft.util.math.vector.Vector3d;
import team.sdhq.eventBus.annotations.EventTarget;
import team.sdhq.eventBus.annotations.priority.LowestPriority;

import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.TimeUnit;

/**
 * Delays the local player's outbound movement while approaching a nearby target.
 * BackTrack has the opposite packet direction and deliberately shares no state with this module.
 */
public class LagRange extends Module {
    private static final int PACKET_LIMIT = 512;
    private static final double MOVEMENT_EPSILON = 1.0E-4D;

    private final NumberSetting<Integer> delay;
    private final NumberSetting<Float> minRange;
    private final NumberSetting<Float> maxRange;
    private final BooleanSetting weaponsOnly;
    private final BooleanSetting flushOnSprintReset;
    private final BooleanSetting flushOnUse;

    private final ConcurrentLinkedDeque<DelayedPacket> packets = new ConcurrentLinkedDeque<>();
    private volatile boolean lagging;
    private volatile Vector3d serverPosition;

    public LagRange() {
        super(ModuleCategory.COMBAT, "LagRange", "Delay your outbound movement while approaching a target");
        this.delay = new NumberSetting<>("Delay", "Maximum local movement delay in milliseconds", 150, 50, 500, 10);
        this.minRange = new NumberSetting<>("Min Range", "Stop lagging below this target distance", 2.5F, 0.0F, 6.0F, 0.1F);
        this.maxRange = new NumberSetting<>("Max Range", "Start lagging inside this target distance", 6.0F, 3.0F, 10.0F, 0.1F);
        this.weaponsOnly = new BooleanSetting("Weapons Only", "Only lag while holding a sword or axe", true);
        this.flushOnSprintReset = new BooleanSetting("Flush On Sprint Reset", "Release movement before changing sprint state", true);
        this.flushOnUse = new BooleanSetting("Flush On Use", "Release movement before attacks and other interactions", true);
        this.registerSetting(this.delay, this.minRange, this.maxRange, this.weaponsOnly,
                this.flushOnSprintReset, this.flushOnUse);
    }

    @Override
    public void onEnable() {
        this.clearState();
        this.rememberCurrentPosition();
    }

    @Override
    public void onDisable() {
        this.lagging = false;
        this.flushPackets();
        this.serverPosition = null;
    }

    @EventTarget
    public void onWorldLoad(EventLoadWorld event) {
        this.clearState();
    }

    @EventTarget
    public void onGlobalReceivePacket(EventGlobalReceivePacket event) {
        if (event.packet instanceof SPlayerPositionLookPacket) {
            // Positions captured before a server correction must never be replayed afterwards.
            this.clearState();
        }
    }

    @EventTarget
    public void onTick(EventTick event) {
        if (mc.player == null || mc.world == null || mc.getConnection() == null) {
            this.clearState();
            return;
        }

        if (this.serverPosition == null) {
            this.rememberCurrentPosition();
        }

        this.releaseExpiredPackets();
        boolean shouldLag = this.shouldLagMovement();
        if (!shouldLag && (this.lagging || !this.packets.isEmpty())) {
            this.lagging = false;
            this.flushPackets();
        } else {
            this.lagging = shouldLag;
        }
    }

    @EventTarget
    @LowestPriority
    public void onSendPacket(EventSendPacket event) {
        if (event.cancelled || mc.player == null || mc.world == null || mc.getConnection() == null) {
            return;
        }

        this.releaseExpiredPackets();
        IPacket<?> packet = event.packet;

        if (this.shouldFlushBefore(packet)) {
            this.lagging = false;
            this.flushPackets();
            this.rememberSentPosition(packet);
            return;
        }

        if (!this.lagging || !this.isMovementPacket(packet)) {
            this.rememberSentPosition(packet);
            return;
        }

        if (this.packets.size() >= PACKET_LIMIT) {
            this.lagging = false;
            this.flushPackets();
            this.rememberSentPosition(packet);
            return;
        }

        this.packets.addLast(new DelayedPacket(packet, System.nanoTime()));
        event.cancelled = true;
    }

    private boolean shouldLagMovement() {
        if (mc.player.isPassenger() || !this.isHoldingAllowedWeapon()) {
            return false;
        }

        double moveX = mc.player.getPosX() - mc.player.lastTickPosX;
        double moveZ = mc.player.getPosZ() - mc.player.lastTickPosZ;
        if (moveX * moveX + moveZ * moveZ <= MOVEMENT_EPSILON * MOVEMENT_EPSILON) {
            return false;
        }

        float lowerRange = Math.min(this.minRange.getCurrentValue(), this.maxRange.getCurrentValue());
        float upperRange = Math.max(this.minRange.getCurrentValue(), this.maxRange.getCurrentValue());
        for (PlayerEntity target : mc.world.getPlayers()) {
            if (!this.isValidTarget(target)) {
                continue;
            }

            float distance = mc.player.getDistanceToEntityBox(target);
            if (distance < lowerRange || distance > upperRange) {
                continue;
            }

            double targetX = target.getPosX() - mc.player.getPosX();
            double targetZ = target.getPosZ() - mc.player.getPosZ();
            if (moveX * targetX + moveZ * targetZ > MOVEMENT_EPSILON) {
                return true;
            }
        }
        return false;
    }

    private boolean isValidTarget(PlayerEntity target) {
        if (target == mc.player || !target.isAlive() || target.isSpectator()) {
            return false;
        }
        if (Client.getInstance().botManager != null && Client.getInstance().botManager.isBot(target)) {
            return false;
        }

        Module teams = Client.getInstance().moduleManager.getModuleByClass(Teams.class);
        return teams == null || !teams.isEnabled() || mc.player.getTeam() == null || target.getTeam() == null
                || !CombatUtil.arePlayersOnSameTeam(target);
    }

    private boolean isHoldingAllowedWeapon() {
        if (!this.weaponsOnly.getCurrentValue()) {
            return true;
        }
        Item item = mc.player.getHeldItemMainhand().getItem();
        return item instanceof SwordItem || item instanceof AxeItem;
    }

    private boolean shouldFlushBefore(IPacket<?> packet) {
        if (packet instanceof CUseEntityPacket) {
            CUseEntityPacket useEntityPacket = (CUseEntityPacket) packet;
            return useEntityPacket.getAction() == CUseEntityPacket.Action.ATTACK || this.flushOnUse.getCurrentValue();
        }
        if (this.flushOnUse.getCurrentValue()
                && (packet instanceof CPlayerDiggingPacket
                || packet instanceof CPlayerTryUseItemPacket
                || packet instanceof CPlayerTryUseItemOnBlockPacket)) {
            return true;
        }
        return this.flushOnSprintReset.getCurrentValue() && this.isSprintAction(packet);
    }

    private boolean isMovementPacket(IPacket<?> packet) {
        return packet instanceof CPlayerPacket
                || packet instanceof CInputPacket
                || packet instanceof CMoveVehiclePacket
                || (!this.flushOnSprintReset.getCurrentValue() && this.isSprintAction(packet));
    }

    private boolean isSprintAction(IPacket<?> packet) {
        if (!(packet instanceof CEntityActionPacket)) {
            return false;
        }
        CEntityActionPacket.Action action = ((CEntityActionPacket) packet).getAction();
        return action == CEntityActionPacket.Action.START_SPRINTING
                || action == CEntityActionPacket.Action.STOP_SPRINTING;
    }

    private void releaseExpiredPackets() {
        long cutoff = System.nanoTime()
                - TimeUnit.MILLISECONDS.toNanos(this.delay.getCurrentValue().longValue());
        DelayedPacket delayed;
        while ((delayed = this.packets.peekFirst()) != null && delayed.sentAtNanos <= cutoff) {
            this.packets.pollFirst();
            this.sendWithoutEvent(delayed.packet);
        }
    }

    private void flushPackets() {
        DelayedPacket delayed;
        while ((delayed = this.packets.pollFirst()) != null) {
            this.sendWithoutEvent(delayed.packet);
        }
    }

    private void sendWithoutEvent(IPacket<?> packet) {
        if (mc.getConnection() == null) {
            return;
        }
        NetworkManager networkManager = mc.getConnection().getNetworkManager();
        this.rememberSentPosition(packet);
        networkManager.sendNoEventPacket(packet);
    }

    private void rememberSentPosition(IPacket<?> packet) {
        if (!(packet instanceof CPlayerPacket) || mc.player == null) {
            return;
        }
        CPlayerPacket movement = (CPlayerPacket) packet;
        Vector3d fallback = this.serverPosition != null
                ? this.serverPosition
                : new Vector3d(mc.player.getPosX(), mc.player.getPosY(), mc.player.getPosZ());
        if (movement.moving) {
            this.serverPosition = new Vector3d(
                    movement.getX(fallback.x),
                    movement.getY(fallback.y),
                    movement.getZ(fallback.z));
        }
    }

    private void rememberCurrentPosition() {
        if (mc.player != null) {
            this.serverPosition = new Vector3d(mc.player.getPosX(), mc.player.getPosY(), mc.player.getPosZ());
        }
    }

    private void clearState() {
        this.packets.clear();
        this.lagging = false;
        this.serverPosition = null;
    }

    private static final class DelayedPacket {
        private final IPacket<?> packet;
        private final long sentAtNanos;

        private DelayedPacket(IPacket<?> packet, long sentAtNanos) {
            this.packet = packet;
            this.sentAtNanos = sentAtNanos;
        }
    }
}
