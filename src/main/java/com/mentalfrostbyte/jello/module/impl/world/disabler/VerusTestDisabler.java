package com.mentalfrostbyte.jello.module.impl.world.disabler;

import com.mentalfrostbyte.Client;
import com.mentalfrostbyte.jello.event.impl.game.network.EventSendPacket;
import com.mentalfrostbyte.jello.event.impl.game.world.EventLoadWorld;
import com.mentalfrostbyte.jello.module.Module;
import com.mentalfrostbyte.jello.module.data.ModuleCategory;
import net.minecraft.network.play.client.CConfirmTransactionPacket;
import net.minecraft.network.play.client.CInputPacket;
import net.minecraft.network.play.client.CKeepAlivePacket;
import team.sdhq.eventBus.annotations.EventTarget;

import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.ConcurrentModificationException;
import java.util.Objects;

public class VerusTestDisabler extends Module {
    public ArrayList<CConfirmTransactionPacket> transactionPackets = new ArrayList<>();

    public VerusTestDisabler() {
        super(ModuleCategory.EXPLOIT, "VerusTest", "Testing Verus disabler.");
    }

//    @EventTarget
//    public void onReceivePacket(EventReceivePacket event) {
//        if (event.packet instanceof SConfirmTransactionPacket packet) {
//            if (mc.player.ticksExisted > 180 && mc.player.ticksExisted % 4 <= 2) {
//                transactionPackets.add(packet);
//                return;
//            }
//            for (var oldPacket : transactionPackets) {
//                Objects.requireNonNull(mc.getConnection()).sendPacket(new CConfirmTransactionPacket(
//                        oldPacket.getWindowId(),
//                        oldPacket.getActionNumber(),
//                        true
//                ));
//                transactionPackets.remove(oldPacket);
//            }
//        }
//    }
    @EventTarget
    public void onLoadWorld(EventLoadWorld __) {
        transactionPackets.clear();
    }

    @Override
    public void onDisable() {
        try {
            Client.logger.info("Sending {} old transaction packets", transactionPackets.size());
            for (var packet : transactionPackets) {
                transactionPackets.remove(packet);
                mc.getConnection().getNetworkManager().sendNoEventPacket(packet);
            }
            transactionPackets.clear();
        } catch (ConcurrentModificationException e) {
            System.out.println("Ignored ConcurrentModificationException because those are gay");
        }
    }

    @EventTarget
    public void onSendPacket(EventSendPacket event) throws NoSuchAlgorithmException {
        var connection = Objects.requireNonNull(mc.getConnection());
        var player = mc.player;
        assert player != null;
        if (event.packet instanceof CInputPacket packet) {
            event.cancelled = true;
            connection.getNetworkManager().sendNoEventPacket(new CInputPacket(
                    Float.MAX_VALUE,
                    Float.MAX_VALUE,
                    packet.isJumping() || player.getMotion().y >= -0.08,
                    packet.isSneaking()
            ));
            return;
        }
        if (event.packet instanceof CKeepAlivePacket packet) {
            event.cancelled = true;
            connection.getNetworkManager().sendNoEventPacket(new CKeepAlivePacket(
                    mc.player.ticksExisted % 4 == 0 ? packet.getKey() : packet.getKey() - (mc.player.ticksExisted % 2)
            ));
            return;
        }

        if (event.packet instanceof CConfirmTransactionPacket packet) {
            if (mc.player.ticksExisted > 180 && mc.player.ticksExisted % 4 <= 2) {
                event.cancelled = true;
                transactionPackets.add(packet);
                return;
            }
            var oldPacket = transactionPackets.get(transactionPackets.size() - 1);
            Objects.requireNonNull(mc.getConnection()).getNetworkManager().sendNoEventPacket(oldPacket);
            transactionPackets.clear();
        }

//        VerusTimerDisabler.onSendPacket(event);
    }
}
