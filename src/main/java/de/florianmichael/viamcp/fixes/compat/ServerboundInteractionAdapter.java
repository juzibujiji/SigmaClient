package de.florianmichael.viamcp.fixes.compat;

import com.viaversion.viabackwards.protocol.v1_19to1_18_2.Protocol1_19To1_18_2;
import com.viaversion.viaversion.api.connection.UserConnection;
import com.viaversion.viaversion.api.minecraft.BlockPosition;
import com.viaversion.viaversion.api.protocol.packet.PacketWrapper;
import com.viaversion.viaversion.api.type.Types;
import com.viaversion.viaversion.protocols.v1_18_2to1_19.packet.ServerboundPackets1_19;
import net.minecraft.inventory.container.ClickType;
import net.minecraft.network.IPacket;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.play.client.CAnimateHandPacket;
import net.minecraft.network.play.client.CClickWindowPacket;
import net.minecraft.network.play.client.CHeldItemChangePacket;
import net.minecraft.network.play.client.CPickItemPacket;
import net.minecraft.network.play.client.CPlayerDiggingPacket;
import net.minecraft.network.play.client.CPlayerTryUseItemOnBlockPacket;
import net.minecraft.network.play.client.CPlayerTryUseItemPacket;
import net.minecraft.util.math.BlockRayTraceResult;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public final class ServerboundInteractionAdapter {
    private static final Logger LOGGER = LogManager.getLogger("ViaMCP-Interactions");

    private ServerboundInteractionAdapter() {
    }

    public static boolean trySend(NetworkManager networkManager, IPacket<?> packet) {
        if (shouldDropUnsupported(packet)) {
            return true;
        }

        if (!rememberLocalUse(networkManager, packet)) {
            return true;
        }

        if (!InteractionProtocol.between1_19And1_21_1()) {
            return false;
        }

        UserConnection viaConnection = networkManager.getViaUserConnection();
        if (viaConnection == null) {
            return false;
        }

        try {
            if (packet instanceof CHeldItemChangePacket heldItemChangePacket) {
                sendHeldItemChange(viaConnection, heldItemChangePacket);
                return true;
            }

            if (packet instanceof CPlayerTryUseItemPacket useItemPacket) {
                sendUseItem(viaConnection, useItemPacket);
                return true;
            }

            if (packet instanceof CPlayerTryUseItemOnBlockPacket useItemOnBlockPacket) {
                sendUseItemOn(viaConnection, useItemOnBlockPacket);
                return true;
            }

            if (packet instanceof CPlayerDiggingPacket diggingPacket) {
                sendPlayerAction(viaConnection, diggingPacket);
                return true;
            }
        } catch (Exception e) {
            LOGGER.warn("Failed to send direct interaction packet {}, falling back to normal Via path",
                    packet.getClass().getSimpleName(), e);
        }

        return false;
    }

    private static boolean rememberLocalUse(NetworkManager networkManager, IPacket<?> packet) {
        if (!InteractionProtocol.atOrOlderThan1_8()) {
            return true;
        }

        UserConnection connection = networkManager.getViaUserConnection();
        if (packet instanceof CPlayerTryUseItemPacket useItemPacket) {
            return LocalInteractionState.enqueueCurrentHand(connection, useItemPacket.getHand());
        } else if (packet instanceof CPlayerTryUseItemOnBlockPacket useItemOnBlockPacket) {
            return LocalInteractionState.enqueueCurrentHand(connection, useItemOnBlockPacket.getHand());
        }
        return true;
    }

    private static boolean shouldDropUnsupported(IPacket<?> packet) {
        if (packet instanceof CPlayerTryUseItemPacket useItemPacket) {
            return !InteractionSemantics.isSupportedHand(useItemPacket.getHand());
        }

        if (packet instanceof CPlayerTryUseItemOnBlockPacket useItemOnBlockPacket) {
            return !InteractionSemantics.isSupportedHand(useItemOnBlockPacket.getHand());
        }

        if (packet instanceof CAnimateHandPacket animateHandPacket) {
            return !InteractionSemantics.isSupportedHand(animateHandPacket.getHand());
        }

        if (packet instanceof CPlayerDiggingPacket diggingPacket) {
            return InteractionProtocol.atOrOlderThan1_8()
                    && diggingPacket.getAction() == CPlayerDiggingPacket.Action.SWAP_ITEM_WITH_OFFHAND;
        }

        if (packet instanceof CPickItemPacket) {
            return !InteractionProtocol.supportsPickItemPacket();
        }

        if (packet instanceof CClickWindowPacket clickWindowPacket) {
            ClickType clickType = clickWindowPacket.getClickType();
            return !InteractionSemantics.isInventoryActionSupported(
                    clickWindowPacket.getSlotId(),
                    clickWindowPacket.getUsedButton(),
                    clickType);
        }

        return false;
    }

    private static void sendHeldItemChange(UserConnection connection, CHeldItemChangePacket packet) throws Exception {
        PacketWrapper wrapper = PacketWrapper.create(ServerboundPackets1_19.SET_CARRIED_ITEM, connection);
        wrapper.write(Types.SHORT, (short) packet.getSlotId());
        wrapper.sendToServer(Protocol1_19To1_18_2.class);
    }

    private static void sendUseItem(UserConnection connection, CPlayerTryUseItemPacket packet) throws Exception {
        PacketWrapper wrapper = PacketWrapper.create(ServerboundPackets1_19.USE_ITEM, connection);
        wrapper.write(Types.VAR_INT, packet.getHand().ordinal());
        wrapper.write(Types.VAR_INT, InteractionSequence.next());
        wrapper.sendToServer(Protocol1_19To1_18_2.class);
    }

    private static void sendUseItemOn(UserConnection connection, CPlayerTryUseItemOnBlockPacket packet) throws Exception {
        BlockRayTraceResult hit = packet.func_218794_c();
        PacketWrapper wrapper = PacketWrapper.create(ServerboundPackets1_19.USE_ITEM_ON, connection);
        wrapper.write(Types.VAR_INT, packet.getHand().ordinal());
        wrapper.write(Types.BLOCK_POSITION1_14,
                new BlockPosition(hit.getPos().getX(), hit.getPos().getY(), hit.getPos().getZ()));
        wrapper.write(Types.VAR_INT, hit.getFace().getIndex());
        wrapper.write(Types.FLOAT, (float) (hit.getHitVec().x - hit.getPos().getX()));
        wrapper.write(Types.FLOAT, (float) (hit.getHitVec().y - hit.getPos().getY()));
        wrapper.write(Types.FLOAT, (float) (hit.getHitVec().z - hit.getPos().getZ()));
        wrapper.write(Types.BOOLEAN, hit.isInside());
        wrapper.write(Types.VAR_INT, InteractionSequence.next());
        wrapper.sendToServer(Protocol1_19To1_18_2.class);
    }

    private static void sendPlayerAction(UserConnection connection, CPlayerDiggingPacket packet) throws Exception {
        PacketWrapper wrapper = PacketWrapper.create(ServerboundPackets1_19.PLAYER_ACTION, connection);
        wrapper.write(Types.VAR_INT, packet.getAction().ordinal());
        wrapper.write(Types.BLOCK_POSITION1_14,
                new BlockPosition(packet.getPosition().getX(), packet.getPosition().getY(), packet.getPosition().getZ()));
        wrapper.write(Types.BYTE, (byte) packet.getFacing().getIndex());
        wrapper.write(Types.VAR_INT, InteractionSequence.next());
        wrapper.sendToServer(Protocol1_19To1_18_2.class);
    }
}
