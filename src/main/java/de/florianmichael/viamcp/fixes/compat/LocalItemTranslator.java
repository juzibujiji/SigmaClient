package de.florianmichael.viamcp.fixes.compat;

import com.viaversion.viaversion.api.Via;
import com.viaversion.viaversion.api.connection.ProtocolInfo;
import com.viaversion.viaversion.api.connection.UserConnection;
import com.viaversion.viaversion.api.minecraft.item.Item;
import com.viaversion.viaversion.api.protocol.ProtocolPathEntry;
import com.viaversion.viaversion.api.protocol.ProtocolPipeline;
import com.viaversion.viaversion.api.protocol.packet.Direction;
import com.viaversion.viaversion.api.protocol.packet.PacketWrapper;
import com.viaversion.viaversion.api.protocol.packet.State;
import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import com.viaversion.viaversion.api.type.Types;
import com.viaversion.viaversion.connection.UserConnectionImpl;
import com.viaversion.viaversion.protocol.ProtocolPipelineImpl;
import com.viaversion.viaversion.protocols.v1_16_1to1_16_2.packet.ServerboundPackets1_16_2;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.item.ItemStack;
import net.minecraft.item.SwordItem;
import net.minecraft.network.PacketBuffer;
import net.minecraft.network.play.client.CCreativeInventoryActionPacket;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

public final class LocalItemTranslator {
    private static final Logger LOGGER = LogManager.getLogger("ViaMCP-ItemTranslator");
    private static final long ERROR_LOG_INTERVAL_NANOS = TimeUnit.SECONDS.toNanos(30L);
    private static final AtomicLong NEXT_ERROR_LOG_NANOS = new AtomicLong();

    private LocalItemTranslator() {
    }

    /**
     * Converts a native 1.16.4 stack to the item representation expected by a
     * 1.8 server.  Numeric Minecraft registry ids are version-specific, so they
     * must never be written directly as {@link Types#ITEM1_8}.
     *
     * <p>This mirrors ViaFabricPlus' item translator: serialize a harmless
     * creative-slot packet, run it through a fresh dummy Via protocol pipeline,
     * then read the target-version item back out.</p>
     */
    public static Item toViaItem(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return null;
        }

        // ViaBackwards replaces unknown 1.16 items with generic legacy
        // placeholders (for example a netherite sword becomes item id 1).
        // Such a stack cannot originate from a real 1.8 server, so do not
        // misrepresent it as another item in the legacy use packet.
        if (stack.getItem() instanceof SwordItem && !SwordItem.isLegacyBlockingSword(stack)) {
            return null;
        }

        EmbeddedChannel channel = new EmbeddedChannel();
        ByteBuf rawPacket = Unpooled.buffer();
        try {
            UserConnection connection = createDummyConnection(channel, ProtocolVersion.v1_8);
            PacketBuffer packetBuffer = new PacketBuffer(rawPacket);
            new CCreativeInventoryActionPacket(0, stack).writePacketData(packetBuffer);

            PacketWrapper wrapper = PacketWrapper.create(
                    ServerboundPackets1_16_2.SET_CREATIVE_MODE_SLOT,
                    rawPacket,
                    connection);
            connection.getProtocolInfo().getPipeline().transform(Direction.SERVERBOUND, State.PLAY, wrapper);

            wrapper.read(Types.SHORT);
            Item translated = wrapper.read(Types.ITEM1_8);
            // ITEM1_8 serializes null as absent (-1), while a non-null id 0 is
            // present AIR and is explicitly rejected by Grim BadPacketsU.
            return translated == null || translated.identifier() <= 0 ? null : translated.copy();
        } catch (Throwable throwable) {
            // Null is the safe failure mode: emitting a native registry id as a
            // legacy item becomes AIR on the server and triggers Grim BadPacketsU.
            logTranslationFailure(throwable);
            return null;
        } finally {
            if (rawPacket.refCnt() > 0) {
                rawPacket.release();
            }
            channel.finishAndReleaseAll();
        }
    }

    private static void logTranslationFailure(Throwable throwable) {
        long now = System.nanoTime();
        long next = NEXT_ERROR_LOG_NANOS.get();
        if (now >= next && NEXT_ERROR_LOG_NANOS.compareAndSet(next, now + ERROR_LOG_INTERVAL_NANOS)) {
            LOGGER.error("Failed to translate native item stack to a 1.8 ViaVersion item", throwable);
        }
    }

    private static UserConnection createDummyConnection(EmbeddedChannel channel, ProtocolVersion targetVersion) {
        UserConnection user = new UserConnectionImpl(channel, true);
        ProtocolPipeline pipeline = new ProtocolPipelineImpl(user);
        List<ProtocolPathEntry> path = Via.getManager()
                .getProtocolManager()
                .getProtocolPath(ProtocolVersion.v1_16_4, targetVersion);

        if (path == null) {
            throw new IllegalStateException("No ViaVersion protocol path from 1.16.4 to " + targetVersion);
        }

        for (ProtocolPathEntry entry : path) {
            pipeline.add(entry.protocol());
            entry.protocol().init(user);
        }

        ProtocolInfo info = user.getProtocolInfo();
        info.setState(State.PLAY);
        info.setProtocolVersion(ProtocolVersion.v1_16_4);
        info.setServerProtocolVersion(targetVersion);
        return user;
    }
}
