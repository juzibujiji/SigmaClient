package com.mentalfrostbyte.jello.module.impl.world.disabler;

import com.mentalfrostbyte.jello.event.impl.game.network.EventSendPacket;
import com.mentalfrostbyte.jello.event.impl.player.EventUpdate;
import com.mentalfrostbyte.jello.gui.impl.others.ChatUtil;
import com.mentalfrostbyte.jello.module.Module;
import com.mentalfrostbyte.jello.module.data.ModuleCategory;
import net.minecraft.network.IPacket;
import net.minecraft.network.play.client.CConfirmTransactionPacket;
import net.minecraft.network.play.client.CKeepAlivePacket;
import net.minecraft.network.play.client.CPlayerPacket;
import team.sdhq.eventBus.annotations.EventTarget;

import java.util.HashSet;
import java.util.Set;

public class VerusTimerDisabler extends Module {
    private long lastPlayerPacketTime = 0;
    private long lastBurst = 0;
    private final Set<IPacket<?>> packets = new HashSet<>(20);

    public VerusTimerDisabler() {
        super(ModuleCategory.EXPLOIT, "VerusTimer", "Disable Verus timer checks.");
    }

    @EventTarget
    public void onSendPacket(EventSendPacket event) {
        if (mc.player == null) return;
		// Cancel KeepAlivePacket to bypass timer checks
		if (event.packet instanceof CKeepAlivePacket) {
			event.cancelled = true;
		}
		if (event.packet instanceof CConfirmTransactionPacket packet && packet.getWindowId() == 0) {
			if (packet.getUid() % 30 != 0) event.cancelled = true;
		}

		// Cancel CPlayerPacket but allow some interval between cancellations
		if (event.packet instanceof CPlayerPacket packet) {
			long currentTime = System.currentTimeMillis();
			long MOVEMENT_THRESHOLD_MS = 38;
			if (currentTime - lastPlayerPacketTime < MOVEMENT_THRESHOLD_MS) {
				event.cancelled = true; // Cancel the packet if we're sending it too fast
				packets.add(packet);
			}
			lastPlayerPacketTime = currentTime;
		}
	}

    @EventTarget
    public void onTick(EventUpdate e) {
		if (packets.size() < 5) return;
        if (System.currentTimeMillis() - lastBurst < 15 + packets.size()) return;
        if (mc.player == null) return;
        for (IPacket<?> packet : packets)
			mc.player.connection.getNetworkManager().sendNoEventPacket(packet);
//        ChatUtil.printMessage("Burst!");
        packets.clear();
    }
}
