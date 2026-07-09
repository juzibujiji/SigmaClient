//from liquidbounce.net by CCBlueX
package com.mentalfrostbyte.jello.module.impl.world.disabler;

import com.mentalfrostbyte.jello.event.impl.game.network.EventReceivePacket;
import com.mentalfrostbyte.jello.event.impl.game.world.EventLoadWorld;
import com.mentalfrostbyte.jello.event.impl.player.EventUpdate;
import com.mentalfrostbyte.jello.module.Module;
import com.mentalfrostbyte.jello.module.data.ModuleCategory;
import com.mentalfrostbyte.jello.util.game.MinecraftUtil;
import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import de.florianmichael.vialoadingbase.ViaLoadingBase;
import net.minecraft.network.IPacket;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.play.server.SConfirmTransactionPacket;
import net.minecraft.network.play.server.SKeepAlivePacket;
import net.minecraft.network.play.server.SPlayerPositionLookPacket;
import team.sdhq.eventBus.annotations.EventTarget;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class CubeCraftDisabler extends Module {
    private final List<QueuedIncomingPacket> incomingQueue = new ArrayList<>();

    public CubeCraftDisabler() {
        super(ModuleCategory.EXPLOIT, "CubeCraft", "Full ping spoof disabler for CubeCraft.");
    }

    @Override
    public void onEnable() {
        this.incomingQueue.clear();
        if (mc.player != null) {
            MinecraftUtil.addChatMessage("[CubeCraft Disabler] Full ping spoof enabled.");
        }
    }

    @Override
    public void onDisable() {
        this.flushAll();
    }

    @EventTarget
    public void onWorldLoad(EventLoadWorld event) {
        if (this.isEnabled()) {
            this.incomingQueue.clear();
        }
    }

    @EventTarget
    public void onReceivePacket(EventReceivePacket event) {
        if (!this.isEnabled() || mc.getCurrentServerData() == null) {
            return;
        }

        IPacket<?> packet = event.packet;
        if (packet instanceof SKeepAlivePacket || this.isViaPingPacket(packet)) {
            this.incomingQueue.add(new QueuedIncomingPacket(packet));
            event.cancelled = true;
        } else if (packet instanceof SPlayerPositionLookPacket) {
            this.flushAll();
        }
    }

    @EventTarget
    public void onUpdate(EventUpdate event) {
        if (!this.isEnabled()) {
            return;
        }

        if (mc.getIntegratedServer() != null) {
            MinecraftUtil.addChatMessage("[CubeCraft Disabler] Cannot enable in singleplayer.");
            this.setEnabled(false);
            return;
        }

        if (mc.player == null || mc.getConnection() == null || mc.getCurrentServerData() == null) {
            return;
        }

        this.flushExpired(this.getFlushDelay());
    }

    private long getFlushDelay() {
        int ticksExisted = mc.player.ticksExisted;
        if (ticksExisted < 150) {
            return 5000L;
        }

        if (ticksExisted < 300) {
            return 10000L;
        }

        return 20000L;
    }

    private void flushExpired(long delay) {
        long currentTime = System.currentTimeMillis();
        Iterator<QueuedIncomingPacket> iterator = this.incomingQueue.iterator();

        while (iterator.hasNext()) {
            QueuedIncomingPacket queuedPacket = iterator.next();
            if (currentTime - queuedPacket.timestamp >= delay) {
                this.processQueuedPacket(queuedPacket.packet);
                iterator.remove();
            }
        }
    }

    private void flushAll() {
        for (QueuedIncomingPacket queuedPacket : this.incomingQueue) {
            this.processQueuedPacket(queuedPacket.packet);
        }

        this.incomingQueue.clear();
    }

    private void processQueuedPacket(IPacket<?> packet) {
        if (mc.getConnection() == null) {
            return;
        }

        NetworkManager networkManager = mc.getConnection().getNetworkManager();
        if (networkManager != null && networkManager.packetListener != null) {
            NetworkManager.processPacket(packet, networkManager.packetListener);
        }
    }

    private boolean isViaPingPacket(IPacket<?> packet) {
        ViaLoadingBase viaLoadingBase = ViaLoadingBase.getInstance();
        return packet instanceof SConfirmTransactionPacket
                && viaLoadingBase != null
                && viaLoadingBase.getTargetVersion().newerThanOrEqualTo(ProtocolVersion.v1_17);
    }

    private static final class QueuedIncomingPacket {
        private final IPacket<?> packet;
        private final long timestamp;

        private QueuedIncomingPacket(IPacket<?> packet) {
            this.packet = packet;
            this.timestamp = System.currentTimeMillis();
        }
    }
}
