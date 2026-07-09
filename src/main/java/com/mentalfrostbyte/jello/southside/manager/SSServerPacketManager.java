/*
 * Ported from OpenSSNGScaffoldAndClutch (https://github.com/zyyzs/OpenSSNGScaffoldAndClutch)
 * Original: dev.southside.manager.ServerPacketManager (referenced by upstream sources; not
 * published in the upstream repo — reconstructed from its observed API surface and usage in
 * Scaffold's Block Fly option:
 *   onEnable (blockFly on):            ServerPacketManager.reset(true);
 *   place()  (blockFly on):            if (!ServerPacketManager.deSyncing) ServerPacketManager.setup();
 *   TickEvent POST (blockFly on):      if (ServerPacketManager.deSyncTick > 16) ServerPacketManager.releaseTick(true);
 * i.e. a blink-style outbound packet hold: while deSyncing, all outgoing packets are queued
 * (desyncing the server view of the player), and every ~17 ticks the queue is flushed in
 * order).
 *
 * Implementation follows this client's canonical Blink capture/release idiom: capture in an
 * EventSendPacket handler (cancel + queue, skipping connection-critical packets), release via
 * NetworkManager.sendNoEventPacket so flushed packets are not re-captured. A watchdog
 * (release + stop after 100 held ticks, hard-drop on disconnect) is reconstruction-side
 * safety: upstream's own lifecycle handling lives in its unpublished manager.
 */
package com.mentalfrostbyte.jello.southside.manager;

import com.mentalfrostbyte.jello.event.impl.game.network.EventSendPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.network.IPacket;
import net.minecraft.network.handshake.client.CHandshakePacket;
import net.minecraft.network.login.client.CEncryptionResponsePacket;
import net.minecraft.network.login.client.CLoginStartPacket;
import net.minecraft.network.play.client.CChatMessagePacket;
import net.minecraft.network.status.client.CServerQueryPacket;
import team.sdhq.eventBus.EventBus;
import team.sdhq.eventBus.annotations.EventTarget;
import team.sdhq.eventBus.annotations.priority.HighestPriority;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

public final class SSServerPacketManager {
    public static boolean deSyncing;
    public static int deSyncTick;

    /** Safety valve: no upstream constant is published; 100 ticks (~5 s) is far above the
     *  scaffold's own 16-tick release cadence and only triggers if the owner stops ticking. */
    private static final int WATCHDOG_TICKS = 100;

    private static final Queue<IPacket<?>> queue = new ConcurrentLinkedQueue<>();
    private static final SSServerPacketManager INSTANCE = new SSServerPacketManager();
    private static boolean registered;
    private static final Minecraft mc = Minecraft.getInstance();

    private SSServerPacketManager() {
    }

    private static void ensureRegistered() {
        if (!registered) {
            EventBus.register(INSTANCE);
            registered = true;
        }
    }

    /** Begin desyncing: hold every outgoing packet until released. */
    public static void setup() {
        ensureRegistered();
        if (!deSyncing) {
            deSyncing = true;
            deSyncTick = 0;
        }
    }

    /** Stop desyncing entirely; flush (or drop) whatever is held. */
    public static void reset(boolean flush) {
        if (flush) {
            flushQueue();
        } else {
            queue.clear();
        }
        deSyncing = false;
        deSyncTick = 0;
    }

    /** Release the held packets but keep desyncing (the periodic ~17-tick flush). */
    public static void releaseTick(boolean flush) {
        if (flush) {
            flushQueue();
        } else {
            queue.clear();
        }
        deSyncTick = 0;
    }

    /** Called once per client tick from SSRotationUtils.choose(). */
    public static void onTick() {
        if (mc.player == null || mc.getConnection() == null) {
            // Connection gone — held packets are unsendable; drop and stop.
            if (deSyncing || !queue.isEmpty()) {
                queue.clear();
                deSyncing = false;
                deSyncTick = 0;
            }
            return;
        }

        if (deSyncing) {
            deSyncTick++;
            if (deSyncTick > WATCHDOG_TICKS) {
                reset(true);
            }
        }
    }

    private static void flushQueue() {
        if (mc.getConnection() == null) {
            queue.clear();
            return;
        }
        IPacket<?> packet;
        while ((packet = queue.poll()) != null) {
            mc.getConnection().getNetworkManager().sendNoEventPacket(packet);
        }
    }

    @EventTarget
    @HighestPriority
    public void onSendPacket(EventSendPacket event) {
        if (!deSyncing || event.cancelled || mc.getConnection() == null) {
            return;
        }

        IPacket<?> packet = event.packet;
        // Connection-critical packets must pass through (same skip list as the host Blink).
        if (packet instanceof CHandshakePacket
                || packet instanceof CLoginStartPacket
                || packet instanceof CServerQueryPacket
                || packet instanceof CChatMessagePacket
                || packet instanceof CEncryptionResponsePacket) {
            return;
        }

        queue.add(packet);
        event.cancelled = true;
    }
}
