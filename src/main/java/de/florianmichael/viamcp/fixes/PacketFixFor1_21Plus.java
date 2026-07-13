package de.florianmichael.viamcp.fixes;

import com.viaversion.viaversion.api.connection.UserConnection;
import com.viaversion.viaversion.api.connection.StorableObject;
import com.viaversion.viaversion.api.protocol.Protocol;
import com.viaversion.viaversion.api.protocol.packet.PacketWrapper;
import com.viaversion.viaversion.api.protocol.packet.ServerboundPacketType;
import com.viaversion.viaversion.api.protocol.packet.State;
import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import com.viaversion.viaversion.protocols.v1_21_2to1_21_4.packet.ServerboundPackets1_21_4;
import com.viaversion.viaversion.protocols.v1_21_4to1_21_5.Protocol1_21_4To1_21_5;
import com.viaversion.viaversion.protocols.v1_21_4to1_21_5.packet.ServerboundPackets1_21_5;
import com.viaversion.viaversion.protocols.v1_21_5to1_21_6.Protocol1_21_5To1_21_6;
import com.viaversion.viaversion.protocols.v1_21_5to1_21_6.packet.ServerboundPackets1_21_6;
import com.viaversion.viaversion.protocols.v1_21to1_21_2.packet.ServerboundPackets1_21_2;
import com.viaversion.viaversion.api.type.Types;
import de.florianmichael.vialoadingbase.ViaLoadingBase;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.player.ClientPlayerEntity;
import net.minecraft.client.network.play.ClientPlayNetHandler;
import net.minecraft.network.NetworkManager;
import net.minecraft.util.MovementInput;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

public final class PacketFixFor1_21Plus {
    private static final Logger LOGGER = LogManager.getLogger("ViaMCP-1.21-Movement");
    public static final String HANDLER_NAME = "sigma-1_21-movement-flag-fix";
    private static final String ENABLED_PROPERTY = "sigma.viamcp.packetFix1_21";
    private static final String GRIM_VANILLA_COMPAT_PROPERTY = "sigma.viamcp.grimVanillaCompat";
    /*
     * ViaVersion exposes protocol 768 as v1_21_2 for the 1.21.2-1.21.3 range.
     * This is the lowest protocol node that can represent a 1.21.3+ target.
     */
    private static final ProtocolVersion FIRST_1_21_3_PROTOCOL = ProtocolVersion.v1_21_2;
    private static final int FLAG_ON_GROUND = 1;
    private static final int FLAG_HORIZONTAL_COLLISION = 2;
    private static final int INPUT_FORWARD = 1;
    private static final int INPUT_BACKWARD = 2;
    private static final int INPUT_LEFT = 4;
    private static final int INPUT_RIGHT = 8;
    private static final int INPUT_JUMP = 16;
    private static final int INPUT_SHIFT = 32;
    private static final int INPUT_SPRINT = 64;
    private static final int DOUBLE_BYTES = 8;
    private static final int FLOAT_BYTES = 4;
    private static final String[] MOVEMENT_1_21_5_INPUT_PROTOCOLS = {
            "Protocol1_21_4To1_21_5",
            "Protocol1_21_5To1_21_4",
            "Protocol1_21_5To1_21_6",
            "Protocol1_21_6To1_21_5"
    };
    private static final String[] MOVEMENT_1_21_3_PROTOCOLS = {
            "Protocol1_21To1_21_2",
            "Protocol1_21_2To1_21",
            "Protocol1_21_2To1_21_4",
            "Protocol1_21_4To1_21_2",
            "Protocol1_21_4To1_21_5",
            "Protocol1_21_5To1_21_4",
            "Protocol1_21_5To1_21_6",
            "Protocol1_21_6To1_21_5"
    };

    private PacketFixFor1_21Plus() {
    }

    public static boolean isEnabled() {
        return Boolean.parseBoolean(System.getProperty(ENABLED_PROPERTY, "true"));
    }

    public static boolean isTargetAtLeast1_21_3Protocol() {
        return isAtLeast1_21_3Protocol(targetVersion());
    }

    public static boolean isAtLeast1_21_3Protocol(ProtocolVersion targetVersion) {
        return targetVersion != null && targetVersion.newerThanOrEqualTo(FIRST_1_21_3_PROTOCOL);
    }

    public static boolean shouldUseMovementFlags() {
        return shouldUseMovementFlags(activeUserConnection());
    }

    private static boolean shouldUseMovementFlags(UserConnection connection) {
        return isEnabled()
                && isTargetAtLeast1_21_3Protocol()
                && isPlayState(connection)
                && hasProtocolNamed(connection, MOVEMENT_1_21_3_PROTOCOLS);
    }

    public static boolean shouldUseVanilla1_21MovementCadence() {
        return shouldUseVanilla1_21MovementPhysics();
    }

    public static boolean shouldUseVanilla1_21MovementPhysics() {
        UserConnection connection = activeUserConnection();
        return isEnabled()
                && isTargetAtLeast1_21_3Protocol()
                && isPlayState(connection)
                && hasProtocolNamed(connection, MOVEMENT_1_21_3_PROTOCOLS);
    }

    public static boolean shouldUseGrimVanillaMovement() {
        return shouldUseVanilla1_21MovementPhysics()
                && Boolean.parseBoolean(System.getProperty(GRIM_VANILLA_COMPAT_PROPERTY, "false"));
    }

    public static boolean shouldUseVanilla1_21_5InputPhysics() {
        UserConnection connection = activeUserConnection();
        return isEnabled()
                && targetVersion().newerThanOrEqualTo(ProtocolVersion.v1_21_5)
                && isPlayState(connection)
                && hasProtocolNamed(connection, MOVEMENT_1_21_5_INPUT_PROTOCOLS);
    }

    public static boolean shouldSendPlayerInput() {
        return shouldSendPlayerInput(activeUserConnection());
    }

    private static boolean shouldSendPlayerInput(UserConnection connection) {
        ProtocolVersion targetVersion = targetVersion();
        return isEnabled()
                && isAtLeast1_21_3Protocol(targetVersion)
                && targetVersion.newerThanOrEqualTo(ProtocolVersion.v1_21_5)
                && isPlayState(connection)
                && hasProtocol(connection, playerInputProtocol(targetVersion));
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
        UserConnection connection = activeUserConnection();
        if (!shouldUseMovementFlags(connection)) {
            clearMovementFlags(connection);
            return;
        }

        movementFlagState(connection).horizontalCollisions.offer(horizontalCollision);
    }

    public static boolean sendPlayerInputPacket(ClientPlayerEntity player) {
        if (player == null || player.connection == null) {
            return false;
        }

        UserConnection connection = player.connection.getNetworkManager().getViaUserConnection();
        if (!shouldSendPlayerInput(connection)) {
            return false;
        }

        try {
            byte flags = playerInputFlagsFromMovementInput(player);
            ProtocolVersion targetVersion = targetVersion();
            Class<? extends Protocol> protocolClass = playerInputProtocol(targetVersion);
            if (!hasProtocol(connection, protocolClass)) {
                return false;
            }

            if (targetVersion.newerThanOrEqualTo(ProtocolVersion.v1_21_6)) {
                PacketWrapper wrapper = PacketWrapper.create(ServerboundPackets1_21_6.PLAYER_INPUT, connection);
                wrapper.write(Types.BYTE, flags);
                wrapper.scheduleSendToServer(protocolClass);
            } else {
                PacketWrapper wrapper = PacketWrapper.create(ServerboundPackets1_21_5.PLAYER_INPUT, connection);
                wrapper.write(Types.BYTE, flags);
                wrapper.scheduleSendToServer(protocolClass);
            }

            return true;
        } catch (Exception e) {
            LOGGER.warn("Failed to send 1.21+ player input packet", e);
            return false;
        }
    }

    public static boolean reportedSneaking(ClientPlayerEntity player) {
        MovementInput input = player == null ? null : player.movementInput;
        if (input == null || !input.sneaking) {
            return false;
        }

        if (!shouldSendPlayerInput()) {
            return true;
        }

        return canReportSneakInput(player);
    }

    public static void normalizeRaw1_21_5MovementInput(MovementInput input) {
        if (!shouldUseVanilla1_21_5InputPhysics() || input == null) {
            return;
        }

        float lengthSquared = input.moveStrafe * input.moveStrafe + input.moveForward * input.moveForward;
        if (lengthSquared > 1.0F) {
            float inverseLength = (float) (1.0D / Math.sqrt(lengthSquared));
            input.moveStrafe *= inverseLength;
            input.moveForward *= inverseLength;
        }
    }

    private static boolean canReportSneakInput(ClientPlayerEntity player) {
        return player.onGround
                || player.abilities.isFlying
                || player.isPassenger()
                || player.isInWater()
                || player.isSwimming()
                || player.isOnLadder()
                || player.isElytraFlying();
    }

    private static byte playerInputFlagsFromMovementInput(ClientPlayerEntity player) {
        MovementInput input = player.movementInput;
        int flags = 0;

        if (input != null) {
            flags |= input.forwardKeyDown ? INPUT_FORWARD : 0;
            flags |= input.backKeyDown ? INPUT_BACKWARD : 0;
            flags |= input.leftKeyDown ? INPUT_LEFT : 0;
            flags |= input.rightKeyDown ? INPUT_RIGHT : 0;
            flags |= input.jump ? INPUT_JUMP : 0;
            flags |= reportedSneaking(player) ? INPUT_SHIFT : 0;
        }

        flags |= player.isSprinting() ? INPUT_SPRINT : 0;
        return (byte) flags;
    }

    private static Class<? extends Protocol> playerInputProtocol(ProtocolVersion targetVersion) {
        if (targetVersion.newerThanOrEqualTo(ProtocolVersion.v1_21_6)) {
            return Protocol1_21_5To1_21_6.class;
        }

        if (targetVersion.newerThanOrEqualTo(ProtocolVersion.v1_21_5)) {
            return Protocol1_21_4To1_21_5.class;
        }

        return null;
    }

    public static ChannelOutboundHandlerAdapter createServerboundMovementFlagHandler(UserConnection connection) {
        return new ServerboundMovementFlagHandler(connection);
    }

    /**
     * ViaVersion changes the server-side state to CONFIGURATION while a modern
     * proxy transfers the client to another backend. Custom PLAY packets and raw
     * PLAY packet rewrites must stop until that state returns to PLAY.
     */
    public static boolean isPlayState(UserConnection connection) {
        return connection != null
                && connection.isActive()
                && !connection.isPendingDisconnect()
                && connection.getProtocolInfo() != null
                && connection.getProtocolInfo().getClientState() == State.PLAY
                && connection.getProtocolInfo().getServerState() == State.PLAY;
    }

    public static boolean hasProtocol(UserConnection connection, Class<? extends Protocol> protocolClass) {
        return connection != null
                && protocolClass != null
                && connection.getProtocolInfo() != null
                && connection.getProtocolInfo().getPipeline().contains(protocolClass);
    }

    private static boolean hasProtocolNamed(UserConnection connection, String... protocolNames) {
        if (connection == null || connection.getProtocolInfo() == null) {
            return false;
        }

        for (Protocol protocol : connection.getProtocolInfo().getPipeline().pipes()) {
            String simpleName = protocol.getClass().getSimpleName();
            for (String protocolName : protocolNames) {
                if (protocolName.equals(simpleName)) {
                    return true;
                }
            }
        }

        return false;
    }

    private static UserConnection activeUserConnection() {
        Minecraft mc = Minecraft.getInstance();
        ClientPlayNetHandler playHandler = mc.getConnection();
        if (playHandler == null) {
            return null;
        }

        NetworkManager networkManager = playHandler.getNetworkManager();
        return networkManager == null ? null : networkManager.getViaUserConnection();
    }

    private static ProtocolVersion targetVersion() {
        ViaLoadingBase loadingBase = ViaLoadingBase.getInstance();
        return loadingBase == null ? ProtocolVersion.v1_16_4 : loadingBase.getTargetVersion();
    }

    private static void rewriteServerboundMovementFlags(ByteBuf buf, UserConnection connection) {
        if (!shouldUseMovementFlags(connection) || !buf.isReadable()) {
            clearMovementFlags(connection);
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
        Boolean queuedHorizontalCollision = movementFlagState(connection).horizontalCollisions.poll();
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
        ProtocolVersion targetVersion = targetVersion();
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
        private final UserConnection connection;

        private ServerboundMovementFlagHandler(UserConnection connection) {
            this.connection = connection;
        }

        @Override
        public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
            if (msg instanceof ByteBuf) {
                rewriteServerboundMovementFlags((ByteBuf) msg, this.connection);
            }

            super.write(ctx, msg, promise);
        }
    }

    private static MovementFlagState movementFlagState(UserConnection connection) {
        return (MovementFlagState) connection.getStoredObjects().computeIfAbsent(
                MovementFlagState.class, ignored -> new MovementFlagState());
    }

    private static void clearMovementFlags(UserConnection connection) {
        if (connection == null) {
            return;
        }

        MovementFlagState state = connection.get(MovementFlagState.class);
        if (state != null) {
            state.horizontalCollisions.clear();
        }
    }

    private static final class MovementFlagState implements StorableObject {
        private final Queue<Boolean> horizontalCollisions = new ConcurrentLinkedQueue<>();

        @Override
        public void onRemove() {
            this.horizontalCollisions.clear();
        }
    }
}
