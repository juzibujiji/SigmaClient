package de.florianmichael.viamcp.fixes;

import com.viaversion.viaversion.api.protocol.packet.ServerboundPacketType;
import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import com.viaversion.viaversion.protocols.v1_21_2to1_21_4.packet.ServerboundPackets1_21_4;
import com.viaversion.viaversion.protocols.v1_21_4to1_21_5.packet.ServerboundPackets1_21_5;
import com.viaversion.viaversion.protocols.v1_21_5to1_21_6.packet.ServerboundPackets1_21_6;
import com.viaversion.viaversion.protocols.v1_21to1_21_2.packet.ServerboundPackets1_21_2;
import de.florianmichael.vialoadingbase.ViaLoadingBase;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.player.ClientPlayerEntity;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

public final class PacketFixFor1_21Plus {
    public static final String HANDLER_NAME = "sigma-1_21-movement-flag-fix";
    private static final String ENABLED_PROPERTY = "sigma.viamcp.packetFix1_21";
    private static final int FLAG_ON_GROUND = 1;
    private static final int FLAG_HORIZONTAL_COLLISION = 2;
    private static final int DOUBLE_BYTES = 8;
    private static final int FLOAT_BYTES = 4;
    private static final Queue<Boolean> MOVEMENT_HORIZONTAL_COLLISIONS = new ConcurrentLinkedQueue<>();

    private PacketFixFor1_21Plus() {
    }

    public static boolean isEnabled() {
        return Boolean.parseBoolean(System.getProperty(ENABLED_PROPERTY, "true"));
    }

    public static boolean shouldUseMovementFlags() {
        return isEnabled()
                && ViaLoadingBase.getInstance().getTargetVersion().newerThanOrEqualTo(ProtocolVersion.v1_21_2);
    }

    public static boolean shouldUseVanilla1_21MovementCadence() {
        return shouldUseVanilla1_21MovementPhysics();
    }

    public static boolean shouldUseVanilla1_21MovementPhysics() {
        return isEnabled()
                && ViaLoadingBase.getInstance().getTargetVersion().newerThanOrEqualTo(ProtocolVersion.v1_21);
    }

    public static int getPositionPacketInterval(boolean legacy) {
        if (legacy) {
            return 21;
        }

        return shouldUseVanilla1_21MovementCadence() ? 20 : 19;
    }

    public static boolean unpackOnGround(int flags) {
        return shouldUseMovementFlags() ? (flags & FLAG_ON_GROUND) != 0 : flags != 0;
    }

    public static boolean unpackHorizontalCollision(int flags) {
        return shouldUseMovementFlags() && (flags & FLAG_HORIZONTAL_COLLISION) != 0;
    }

    public static boolean horizontalCollision() {
        Minecraft mc = Minecraft.getInstance();
        ClientPlayerEntity player = mc.player;
        return player != null && player.collidedHorizontally;
    }

    public static void rememberMovementPacket(boolean horizontalCollision) {
        if (!shouldUseMovementFlags()) {
            return;
        }

        MOVEMENT_HORIZONTAL_COLLISIONS.offer(horizontalCollision);
    }

    public static ChannelOutboundHandlerAdapter createServerboundMovementFlagHandler() {
        return new ServerboundMovementFlagHandler();
    }

    private static void rewriteServerboundMovementFlags(ByteBuf buf) {
        if (!shouldUseMovementFlags() || !buf.isReadable()) {
            return;
        }

        int packetStart = buf.readerIndex();
        VarInt packetId = readVarInt(buf, packetStart);
        if (packetId.bytes <= 0) {
            return;
        }

        int payloadStart = packetStart + packetId.bytes;
        int flagsIndex = flagsIndex(buf, packetId.value, payloadStart);
        if (flagsIndex < 0 || flagsIndex >= buf.writerIndex()) {
            return;
        }

        int flags = buf.getUnsignedByte(flagsIndex);
        Boolean queuedHorizontalCollision = MOVEMENT_HORIZONTAL_COLLISIONS.poll();
        boolean horizontalCollision = queuedHorizontalCollision != null
                ? queuedHorizontalCollision
                : horizontalCollision();

        if (horizontalCollision) {
            flags |= FLAG_HORIZONTAL_COLLISION;
        } else {
            flags &= ~FLAG_HORIZONTAL_COLLISION;
        }

        buf.setByte(flagsIndex, flags);
    }

    private static int flagsIndex(ByteBuf buf, int packetId, int payloadStart) {
        MovementPacketIds ids = movementPacketIds();
        if (packetId == ids.position) {
            return readable(buf, payloadStart, DOUBLE_BYTES * 3 + 1)
                    ? payloadStart + DOUBLE_BYTES * 3
                    : -1;
        }

        if (packetId == ids.positionRotation) {
            return readable(buf, payloadStart, DOUBLE_BYTES * 3 + FLOAT_BYTES * 2 + 1)
                    ? payloadStart + DOUBLE_BYTES * 3 + FLOAT_BYTES * 2
                    : -1;
        }

        if (packetId == ids.rotation) {
            return readable(buf, payloadStart, FLOAT_BYTES * 2 + 1)
                    ? payloadStart + FLOAT_BYTES * 2
                    : -1;
        }

        if (packetId == ids.statusOnly) {
            return readable(buf, payloadStart, 1) ? payloadStart : -1;
        }

        return -1;
    }

    private static boolean readable(ByteBuf buf, int start, int length) {
        return start >= buf.readerIndex() && start + length <= buf.writerIndex();
    }

    private static MovementPacketIds movementPacketIds() {
        ProtocolVersion targetVersion = ViaLoadingBase.getInstance().getTargetVersion();
        if (targetVersion.newerThanOrEqualTo(ProtocolVersion.v1_21_6)) {
            return movementPacketIds(
                    ServerboundPackets1_21_6.MOVE_PLAYER_POS,
                    ServerboundPackets1_21_6.MOVE_PLAYER_POS_ROT,
                    ServerboundPackets1_21_6.MOVE_PLAYER_ROT,
                    ServerboundPackets1_21_6.MOVE_PLAYER_STATUS_ONLY);
        }

        if (targetVersion.newerThanOrEqualTo(ProtocolVersion.v1_21_5)) {
            return movementPacketIds(
                    ServerboundPackets1_21_5.MOVE_PLAYER_POS,
                    ServerboundPackets1_21_5.MOVE_PLAYER_POS_ROT,
                    ServerboundPackets1_21_5.MOVE_PLAYER_ROT,
                    ServerboundPackets1_21_5.MOVE_PLAYER_STATUS_ONLY);
        }

        if (targetVersion.newerThanOrEqualTo(ProtocolVersion.v1_21_4)) {
            return movementPacketIds(
                    ServerboundPackets1_21_4.MOVE_PLAYER_POS,
                    ServerboundPackets1_21_4.MOVE_PLAYER_POS_ROT,
                    ServerboundPackets1_21_4.MOVE_PLAYER_ROT,
                    ServerboundPackets1_21_4.MOVE_PLAYER_STATUS_ONLY);
        }

        return movementPacketIds(
                ServerboundPackets1_21_2.MOVE_PLAYER_POS,
                ServerboundPackets1_21_2.MOVE_PLAYER_POS_ROT,
                ServerboundPackets1_21_2.MOVE_PLAYER_ROT,
                ServerboundPackets1_21_2.MOVE_PLAYER_STATUS_ONLY);
    }

    private static MovementPacketIds movementPacketIds(ServerboundPacketType position,
                                                       ServerboundPacketType positionRotation,
                                                       ServerboundPacketType rotation,
                                                       ServerboundPacketType statusOnly) {
        return new MovementPacketIds(position.getId(), positionRotation.getId(), rotation.getId(), statusOnly.getId());
    }

    private static VarInt readVarInt(ByteBuf buf, int index) {
        int value = 0;
        int position = 0;

        for (int offset = 0; offset < 5; ++offset) {
            if (index + offset >= buf.writerIndex()) {
                return new VarInt(0, -1);
            }

            byte currentByte = buf.getByte(index + offset);
            value |= (currentByte & 0x7F) << position;
            if ((currentByte & 0x80) == 0) {
                return new VarInt(value, offset + 1);
            }

            position += 7;
        }

        return new VarInt(0, -1);
    }

    private record VarInt(int value, int bytes) {
    }

    private record MovementPacketIds(int position, int positionRotation, int rotation, int statusOnly) {
    }

    private static final class ServerboundMovementFlagHandler extends ChannelOutboundHandlerAdapter {
        @Override
        public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
            if (msg instanceof ByteBuf) {
                rewriteServerboundMovementFlags((ByteBuf) msg);
            }

            super.write(ctx, msg, promise);
        }
    }
}
