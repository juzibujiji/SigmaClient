package de.florianmichael.viamcp.fixes;

import com.viaversion.viabackwards.protocol.v1_21_2to1_21.Protocol1_21_2To1_21;
import com.viaversion.viaversion.api.connection.UserConnection;
import com.viaversion.viaversion.api.protocol.Protocol;
import com.viaversion.viaversion.api.protocol.packet.ServerboundPacketType;
import com.viaversion.viaversion.api.protocol.packet.PacketWrapper;
import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import com.viaversion.viaversion.protocols.v1_21_2to1_21_4.Protocol1_21_2To1_21_4;
import com.viaversion.viaversion.protocols.v1_21_2to1_21_4.packet.ServerboundPackets1_21_4;
import com.viaversion.viaversion.protocols.v1_21_4to1_21_5.Protocol1_21_4To1_21_5;
import com.viaversion.viaversion.protocols.v1_21_4to1_21_5.packet.ServerboundPackets1_21_5;
import com.viaversion.viaversion.protocols.v1_21_5to1_21_6.Protocol1_21_5To1_21_6;
import com.viaversion.viaversion.protocols.v1_21_5to1_21_6.packet.ServerboundPackets1_21_6;
import com.viaversion.viaversion.protocols.v1_21to1_21_2.packet.ServerboundPackets1_21_2;
import de.florianmichael.vialoadingbase.ViaLoadingBase;
import net.minecraft.client.Minecraft;
import net.minecraft.client.network.play.ClientPlayNetHandler;
import net.minecraft.network.NetworkManager;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class ClientTickFix {
    private static final Logger LOGGER = LogManager.getLogger("ClientTickFix");
    private static final String ENABLED_PROPERTY = "sigma.viamcp.clientTickFix";
    private static final String DEBUG_PROPERTY = "sigma.viamcp.debugServerbound";
    private static int debugTickCounter;
    private static int failureCounter;

    private ClientTickFix() {
    }

    /**
     * Sends the 1.21.2+ zero-payload end-of-tick packet once per client tick.
     */
    public static void tick() {
        if (!isEnabled()) {
            return;
        }

        ProtocolVersion targetVersion = ViaLoadingBase.getInstance().getTargetVersion();
        if (!targetVersion.newerThanOrEqualTo(ProtocolVersion.v1_21_2)) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return;
        }

        try {
            UserConnection connection = getActiveConnection(mc);
            if (connection == null) {
                return;
            }

            sendClientTickEnd(connection, targetVersion);
            failureCounter = 0;
        } catch (Exception e) {
            logFailure(e);
        }
    }

    public static boolean isEnabled() {
        return Boolean.parseBoolean(System.getProperty(ENABLED_PROPERTY, "true"));
    }

    private static UserConnection getActiveConnection(Minecraft mc) {
        ClientPlayNetHandler playHandler = mc.getConnection();
        if (playHandler != null) {
            NetworkManager networkManager = playHandler.getNetworkManager();
            if (networkManager != null) {
                UserConnection connection = networkManager.getViaUserConnection();
                if (connection != null) {
                    return connection;
                }
            }
        }

        return null;
    }

    private static void sendClientTickEnd(UserConnection connection, ProtocolVersion targetVersion) throws Exception {
        if (targetVersion.newerThanOrEqualTo(ProtocolVersion.v1_21_6)) {
            if (sendClientTickEnd(connection, targetVersion, ServerboundPackets1_21_6.CLIENT_TICK_END,
                    Protocol1_21_5To1_21_6.class, "1_21_6")) {
                return;
            }

            logSkippedClientTickEnd(targetVersion);
            return;
        }

        if (targetVersion.newerThanOrEqualTo(ProtocolVersion.v1_21_5)) {
            if (sendClientTickEnd(connection, targetVersion, ServerboundPackets1_21_5.CLIENT_TICK_END,
                    Protocol1_21_4To1_21_5.class, "1_21_5")) {
                return;
            }

            logSkippedClientTickEnd(targetVersion);
            return;
        }

        if (targetVersion.newerThanOrEqualTo(ProtocolVersion.v1_21_4)) {
            if (sendClientTickEnd(connection, targetVersion, ServerboundPackets1_21_4.CLIENT_TICK_END,
                    Protocol1_21_2To1_21_4.class, "1_21_4")) {
                return;
            }

            logSkippedClientTickEnd(targetVersion);
            return;
        }

        if (!sendClientTickEnd(connection, targetVersion, ServerboundPackets1_21_2.CLIENT_TICK_END,
                Protocol1_21_2To1_21.class, "1_21_2")) {
            logSkippedClientTickEnd(targetVersion);
        }
    }

    private static boolean sendClientTickEnd(UserConnection connection, ProtocolVersion targetVersion,
            ServerboundPacketType packetType, Class<? extends Protocol> protocolClass, String packetFamily)
            throws Exception {
        if (!PacketFixFor1_21Plus.hasProtocol(connection, protocolClass)) {
            return false;
        }

        PacketWrapper packet = PacketWrapper.create(packetType, null, connection);
        logClientTickEnd(targetVersion, packetFamily, protocolClass.getSimpleName());
        packet.scheduleSendToServer(protocolClass);
        return true;
    }

    private static void logClientTickEnd(ProtocolVersion targetVersion, String packetFamily, String protocolName) {
        if (Boolean.getBoolean(DEBUG_PROPERTY) && (++debugTickCounter % 20 == 1)) {
            LOGGER.info("[ClientTickFix] CLIENT_TICK_END target={} packetFamily={} viaProtocol={}",
                    targetVersion, packetFamily, protocolName);
        }
    }

    private static void logSkippedClientTickEnd(ProtocolVersion targetVersion) {
        if (Boolean.getBoolean(DEBUG_PROPERTY) && (++debugTickCounter % 20 == 1)) {
            LOGGER.info("[ClientTickFix] Skipping CLIENT_TICK_END for target={} because no matching clientside Via protocol is active",
                    targetVersion);
        }
    }

    private static void logFailure(Exception e) {
        ++failureCounter;
        if (failureCounter == 1 || failureCounter % 100 == 0 || Boolean.getBoolean(DEBUG_PROPERTY)) {
            LOGGER.warn("[ClientTickFix] Failed to send CLIENT_TICK_END (failureCount={})", failureCounter, e);
        } else if (failureCounter % 20 == 0) {
            LOGGER.warn("[ClientTickFix] Failed to send CLIENT_TICK_END (failureCount={}): {}",
                    failureCounter, e.toString());
        }
    }
}
