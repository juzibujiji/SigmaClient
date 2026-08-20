package de.florianmichael.viamcp;

import com.mentalfrostbyte.jello.util.game.world.ChunkDataInterceptor;
import com.mentalfrostbyte.jello.util.game.world.ExtendedHeightBlockUpdateHandler;
import com.mentalfrostbyte.jello.util.game.network.ServerboundPacketDebugHandler;
import com.mentalfrostbyte.jello.util.game.network.UseItemRotationDebug;
import com.viaversion.viaversion.api.connection.UserConnection;
import de.florianmichael.vialoadingbase.netty.event.CompressionReorderEvent;
import de.florianmichael.vialoadingbase.netty.VLBPipeline;
import io.netty.channel.ChannelHandlerContext;
import net.minecraft.entity.ModernMovementPhysics;
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
        installExtendedHeightBlockUpdateHandler(ctx);
        installMovementFlagFixHandler(ctx);
        installServerboundDebugHandler(ctx);
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        super.userEventTriggered(ctx, evt);

        if (evt instanceof CompressionReorderEvent) {
            moveChunkInterceptorAfterDecompression(ctx);
            moveExtendedHeightBlockUpdateHandler(ctx);
            moveMovementFlagFixHandler(ctx);
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
        if ((!ServerboundPacketDebugHandler.isDebugEnabled() && !UseItemRotationDebug.isEnabled())
                || ctx.pipeline().get(ServerboundPacketDebugHandler.HANDLER_NAME) != null) {
            return;
        }

        String anchor = ctx.pipeline().get(VIA_ENCODER_HANDLER_NAME) != null
                ? VIA_ENCODER_HANDLER_NAME
                : "prepender";
        if (ctx.pipeline().get(anchor) != null) {
            ctx.pipeline().addAfter(anchor, ServerboundPacketDebugHandler.HANDLER_NAME,
                    new ServerboundPacketDebugHandler());
            logPipeline(ctx, "serverbound debug installed after " + anchor);
        }
    }

    private void installMovementFlagFixHandler(ChannelHandlerContext ctx) {
        if (ctx.pipeline().get(ModernMovementPhysics.HANDLER_NAME) != null) {
            return;
        }

        /*
         * The flag rewriter parses the post-Via packet layout (packet id first).
         * It must therefore sit between via-encoder and compress; after compress
         * the buffer starts with a length varint (0 for under-threshold packets)
         * and every movement packet is silently skipped, leaving the horizontal
         * collision queue to grow forever.
         */
        String anchor = ctx.pipeline().get(VIA_ENCODER_HANDLER_NAME) != null
                ? VIA_ENCODER_HANDLER_NAME
                : "prepender";
        if (ctx.pipeline().get(anchor) != null) {
            ctx.pipeline().addAfter(anchor, ModernMovementPhysics.HANDLER_NAME,
                    ModernMovementPhysics.createServerboundMovementFlagHandler(getUser()));
            logPipeline(ctx, "movement flag handler installed after " + anchor);
        }
    }

    /**
     * Re-injects captured 1.18+ block changes (Y &lt; 0 / Y &gt; 255) as 1.16.4
     * packets after the Via decoder translated (and ViaBackwards cancelled) the
     * originals, and patches the downgraded block state out of the changes Via
     * <i>did</i> forward. Must stay between via-decoder and the vanilla packet
     * decoder.
     *
     * <p>{@code addAfter(via-decoder, ...)} is what makes the patch possible at
     * all, and the resulting order is the whole design:
     *
     * <pre>
     *   splitter -&gt; decompress -&gt; chunk-data-interceptor -&gt; via-decoder
     *            -&gt; extended-height-block-update -&gt; decoder
     * </pre>
     *
     * <p>{@code chunk-data-interceptor} therefore sees the raw server-version
     * packet and this handler sees Via's finished translation of that same
     * packet - and it sees it before anything downstream does, so it can hand
     * the vanilla decoder a corrected packet instead of racing a second one
     * against it. {@code VLBViaDecodeHandler} is a
     * {@code MessageToMessageDecoder}, whose output is fired after
     * {@code decode()} returns, so the pair always arrives in that order within
     * the same read.
     */
    private void installExtendedHeightBlockUpdateHandler(ChannelHandlerContext ctx) {
        if (ctx.pipeline().get(ExtendedHeightBlockUpdateHandler.HANDLER_NAME) != null) {
            return;
        }

        if (ctx.pipeline().get(VIA_DECODER_HANDLER_NAME) != null) {
            ctx.pipeline().addAfter(VIA_DECODER_HANDLER_NAME, ExtendedHeightBlockUpdateHandler.HANDLER_NAME,
                    new ExtendedHeightBlockUpdateHandler());
            logPipeline(ctx, "block-update reinjection installed after Via decoder");
        }
    }

    private void moveExtendedHeightBlockUpdateHandler(ChannelHandlerContext ctx) {
        if (ctx.pipeline().get(ExtendedHeightBlockUpdateHandler.HANDLER_NAME) != null) {
            ctx.pipeline().remove(ExtendedHeightBlockUpdateHandler.HANDLER_NAME);
        }

        installExtendedHeightBlockUpdateHandler(ctx);
    }

    private void moveMovementFlagFixHandler(ChannelHandlerContext ctx) {
        if (ctx.pipeline().get(ModernMovementPhysics.HANDLER_NAME) != null) {
            ctx.pipeline().remove(ModernMovementPhysics.HANDLER_NAME);
        }

        installMovementFlagFixHandler(ctx);
    }

    private void moveServerboundDebugHandler(ChannelHandlerContext ctx) {
        if (!ServerboundPacketDebugHandler.isDebugEnabled() && !UseItemRotationDebug.isEnabled()) {
            return;
        }

        if (ctx.pipeline().get(ServerboundPacketDebugHandler.HANDLER_NAME) != null) {
            ctx.pipeline().remove(ServerboundPacketDebugHandler.HANDLER_NAME);
        }

        installServerboundDebugHandler(ctx);
    }

    private void logPipeline(ChannelHandlerContext ctx, String reason) {
        if (ChunkDataInterceptor.isDebugEnabled() || ServerboundPacketDebugHandler.isDebugEnabled()
                || UseItemRotationDebug.isEnabled()) {
            LOGGER.info("[ExtendedHeight] Chunk interceptor {}. Pipeline={}", reason, ctx.pipeline().names());
        }
    }
}
