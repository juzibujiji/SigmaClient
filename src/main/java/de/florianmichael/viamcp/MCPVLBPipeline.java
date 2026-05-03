package de.florianmichael.viamcp;

import com.mentalfrostbyte.jello.util.game.world.ChunkDataInterceptor;
import com.mentalfrostbyte.jello.util.game.network.ServerboundPacketDebugHandler;
import com.viaversion.viaversion.api.connection.UserConnection;
import de.florianmichael.vialoadingbase.netty.event.CompressionReorderEvent;
import de.florianmichael.vialoadingbase.netty.VLBPipeline;
import io.netty.channel.ChannelHandlerContext;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class MCPVLBPipeline extends VLBPipeline {
    private static final Logger LOGGER = LogManager.getLogger();

    public MCPVLBPipeline(UserConnection user) {
        super(user);
    }

    @Override
    public void handlerAdded(ChannelHandlerContext ctx) throws Exception {
        super.handlerAdded(ctx);
        installChunkInterceptor(ctx);
        installServerboundDebugHandler(ctx);
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        super.userEventTriggered(ctx, evt);

        if (evt instanceof CompressionReorderEvent) {
            moveChunkInterceptorAfterDecompression(ctx);
            moveServerboundDebugHandler(ctx);
        }
    }

    @Override
    public String getDecoderHandlerName() {
        return "decoder";
    }

    @Override
    public String getEncoderHandlerName() {
        return "encoder";
    }

    @Override
    public String getDecompressionHandlerName() {
        return "decompress";
    }

    @Override
    public String getCompressionHandlerName() {
        return "compress";
    }

    private void installChunkInterceptor(ChannelHandlerContext ctx) {
        if (ctx.pipeline().get(ChunkDataInterceptor.HANDLER_NAME) != null) {
            return;
        }

        if (ctx.pipeline().get(getDecompressionHandlerName()) != null) {
            ctx.pipeline().addAfter(getDecompressionHandlerName(), ChunkDataInterceptor.HANDLER_NAME,
                    new ChunkDataInterceptor());
            logPipeline(ctx, "installed after decompression");
        } else if (ctx.pipeline().get(VIA_DECODER_HANDLER_NAME) != null) {
            ctx.pipeline().addBefore(VIA_DECODER_HANDLER_NAME, ChunkDataInterceptor.HANDLER_NAME,
                    new ChunkDataInterceptor());
            logPipeline(ctx, "installed before Via decoder");
        }
    }

    private void moveChunkInterceptorAfterDecompression(ChannelHandlerContext ctx) {
        if (ctx.pipeline().get(getDecompressionHandlerName()) == null) {
            installChunkInterceptor(ctx);
            return;
        }

        if (ctx.pipeline().get(ChunkDataInterceptor.HANDLER_NAME) != null) {
            ctx.pipeline().remove(ChunkDataInterceptor.HANDLER_NAME);
        }

        ctx.pipeline().addAfter(getDecompressionHandlerName(), ChunkDataInterceptor.HANDLER_NAME,
                new ChunkDataInterceptor());
        logPipeline(ctx, "moved after decompression");
    }

    private void installServerboundDebugHandler(ChannelHandlerContext ctx) {
        if (!ServerboundPacketDebugHandler.isDebugEnabled()
                || ctx.pipeline().get(ServerboundPacketDebugHandler.HANDLER_NAME) != null) {
            return;
        }

        String anchor = ctx.pipeline().get(getCompressionHandlerName()) != null
                ? getCompressionHandlerName()
                : "prepender";
        if (ctx.pipeline().get(anchor) != null) {
            ctx.pipeline().addAfter(anchor, ServerboundPacketDebugHandler.HANDLER_NAME,
                    new ServerboundPacketDebugHandler());
            logPipeline(ctx, "serverbound debug installed after " + anchor);
        }
    }

    private void moveServerboundDebugHandler(ChannelHandlerContext ctx) {
        if (!ServerboundPacketDebugHandler.isDebugEnabled()) {
            return;
        }

        if (ctx.pipeline().get(ServerboundPacketDebugHandler.HANDLER_NAME) != null) {
            ctx.pipeline().remove(ServerboundPacketDebugHandler.HANDLER_NAME);
        }

        installServerboundDebugHandler(ctx);
    }

    private void logPipeline(ChannelHandlerContext ctx, String reason) {
        if (ChunkDataInterceptor.isDebugEnabled() || ServerboundPacketDebugHandler.isDebugEnabled()) {
            LOGGER.info("[ExtendedHeight] Chunk interceptor {}. Pipeline={}", reason, ctx.pipeline().names());
        }
    }
}
