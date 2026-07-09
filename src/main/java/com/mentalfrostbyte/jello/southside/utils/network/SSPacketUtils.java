/*
 * Ported from OpenSSNGScaffoldAndClutch (https://github.com/zyyzs/OpenSSNGScaffoldAndClutch)
 * Original: dev.southside.utils.network.PacketUtils (referenced by upstream sources; not
 * published in the upstream repo — reconstructed from usage:
 * PacketUtils.sendPacket(p) [normal, event-visible send] and
 * PacketUtils.sendPacketNoEvent(p) [send bypassing the client's own EventSendPacket hook]).
 *
 * In this client, NetworkManager.sendPacket fires EventSendPacket (cancellable) and
 * NetworkManager.sendNoEventPacket is the raw path — an exact match for the two upstream
 * idioms.
 */
package com.mentalfrostbyte.jello.southside.utils.network;

import net.minecraft.client.Minecraft;
import net.minecraft.network.IPacket;

public final class SSPacketUtils {
    private SSPacketUtils() {
    }

    public static void sendPacket(IPacket<?> packet) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.getConnection() == null) {
            return;
        }
        mc.getConnection().sendPacket(packet);
    }

    public static void sendPacketNoEvent(IPacket<?> packet) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.getConnection() == null) {
            return;
        }
        mc.getConnection().getNetworkManager().sendNoEventPacket(packet);
    }
}
