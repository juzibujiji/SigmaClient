package com.mentalfrostbyte.jello.util.game.network;

import de.florianmichael.vialoadingbase.ViaLoadingBase;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPromise;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Logs Via-translated serverbound packet ids before compression/framing.
 */
public class ServerboundPacketDebugHandler extends ChannelOutboundHandlerAdapter {
    private static final Logger LOGGER = LogManager.getLogger();
    private static final String DEBUG_PROPERTY = "sigma.viamcp.debugServerbound";
    public static final String HANDLER_NAME = "serverbound-packet-debug";

    public static boolean isDebugEnabled() {
        return Boolean.getBoolean(DEBUG_PROPERTY);
    }

    @Override
    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
        if (isDebugEnabled() && msg instanceof ByteBuf) {
            ByteBuf buf = (ByteBuf) msg;
            try {
                int packetId = readVarInt(buf, buf.readerIndex());
                LOGGER.info("[ViaServerboundProbe] POST_VIA packetId={} length={} target={}",
                        packetId, buf.readableBytes(), ViaLoadingBase.getInstance().getTargetVersion());
            } catch (Exception e) {
                LOGGER.warn("[ViaServerboundProbe] Could not inspect post-Via serverbound packet length={} target={}: {}",
                        buf.readableBytes(), ViaLoadingBase.getInstance().getTargetVersion(), e.getMessage());
            }
        }

        super.write(ctx, msg, promise);
    }

    private static int readVarInt(ByteBuf buf, int index) {
        int value = 0;
        int position = 0;
        int offset = 0;

        while (true) {
            if (index + offset >= buf.writerIndex()) {
                throw new IllegalArgumentException("VarInt exceeds readable bytes");
            }

            byte currentByte = buf.getByte(index + offset);
            value |= (currentByte & 0x7F) << position;

            if ((currentByte & 0x80) == 0) {
                return value;
            }

            position += 7;
            ++offset;

            if (position >= 32) {
                throw new IllegalArgumentException("VarInt too big");
            }
        }
    }
}
