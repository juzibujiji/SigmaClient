package com.mentalfrostbyte.jello.util.game.world;

import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import java.util.List;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.crossversion.ModernBlockStateMap;
import net.minecraft.network.IPacket;
import net.minecraft.network.PacketBuffer;
import net.minecraft.network.PacketDirection;
import net.minecraft.network.ProtocolType;
import net.minecraft.network.play.server.SAnimateBlockBreakPacket;
import net.minecraft.network.play.server.SChangeBlockPacket;
import net.minecraft.network.play.server.SMultiBlockChangePacket;
import net.minecraft.util.math.BlockPos;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Sits between the Via decoder and the vanilla 1.16.4 packet decoder. Every
 * clientbound block change whose Y is outside 0..255 is cancelled by
 * ViaBackwards (1.17 -&gt; 1.16.4); {@link ChunkDataInterceptor} captured the raw
 * packet before the Via pipeline, and this handler re-injects an equivalent
 * 1.16.4 wire packet so the extended-height client world applies server-side
 * break/place updates at Y &lt; 0 and Y &gt; 255.
 *
 * <p>The buffer handed to {@code NettyPacketDecoder} is a bare
 * {@code [packet id VarInt][packet payload]} frame: the length prefix has
 * already been stripped by {@code splitter} and the payload has already been
 * decompressed by {@code decompress}, both of which sit upstream of this
 * handler.
 *
 * <p>Payloads are produced by the vanilla {@code writePacketData} methods
 * wherever the packet class has a usable constructor, so the layout can never
 * drift from the {@code readPacketData} the decoder runs. In particular a
 * 1.16.4 {@code BlockPos} is a single packed long, not three ints - writing
 * three ints left exactly four trailing bytes and tripped the decoder's
 * "larger than I expected" guard.
 *
 * <p>Modern -&gt; legacy payload differences that still have to be handled by
 * hand:
 * <ul>
 *   <li>BLOCK_UPDATE: identical layout (packed pos long + varint state), only
 *       the block-state id needs remapping.</li>
 *   <li>SECTION_BLOCKS_UPDATE: 1.18/1.19 carry a suppress-light boolean that
 *       1.20+ dropped; 1.16.4 always expects it. Record encoding
 *       ({@code state << 12 | packedPos}, var-long) is unchanged.</li>
 *   <li>BLOCK_DESTRUCTION: identical layout (varint entity id + packed pos long
 *       + byte stage).</li>
 * </ul>
 *
 * <h2>Second job: patching Via's own block changes</h2>
 *
 * <p>Re-injection only works because Via <b>cancelled</b> the original - there
 * is nothing to collide with. For a block change inside Y 0..255 that is no
 * longer true: Via delivers it, just with the state downgraded (deepslate
 * arrives as stone). Adding a second packet there would set the position twice
 * and leave the result to whichever the client thread applied last, so instead
 * this handler rewrites <b>Via's</b> packet on its way past, using the raw
 * state {@link ChunkDataInterceptor} parked under the same wire position.
 *
 * <p>Why that is safe to do here and nowhere else:
 * <ul>
 *   <li>The handler sits directly behind {@code via-decoder}
 *       ({@code MCPVLBPipeline#installExtendedHeightBlockUpdateHandler}), so
 *       the buffer seen here is Via's finished output and nothing downstream
 *       has looked at it yet.</li>
 *   <li>Dropping that buffer is not the same as cancelling inside Via: the
 *       translation already ran and Via's trackers are already updated. All
 *       that is discarded is the wire bytes on their way to the vanilla
 *       decoder.</li>
 *   <li>Exactly one packet comes out - patched or original, never both.</li>
 * </ul>
 *
 * <p>The patch is applied only where {@link ModernBlockStateMap} has a real
 * answer for the raw state. Where it does not (roughly a tenth of 1.21.11's
 * states), Via's downgrade is the best result available and is left completely
 * alone.
 */
public final class ExtendedHeightBlockUpdateHandler extends ChannelInboundHandlerAdapter {
    private static final Logger LOGGER = LogManager.getLogger("ExtendedHeightBlockUpdates");

    public static final String HANDLER_NAME = "extended-height-block-update";

    private static final String DEBUG_PROPERTY = "sigma.viamcp.debugBlockUpdateReinject";

    /**
     * Per-kind kill switches so a single kind can be bisected in game without a
     * rebuild ({@code -Dsigma.viamcp.reinject.multi=false}). All default to on.
     */
    private static final String SINGLE_PROPERTY = "sigma.viamcp.reinject.single";
    private static final String MULTI_PROPERTY = "sigma.viamcp.reinject.multi";
    private static final String DESTRUCTION_PROPERTY = "sigma.viamcp.reinject.destruction";

    /**
     * Kill switch for the in-bounds state patch only. Turning it off leaves the
     * extended-height re-injection above completely untouched, which is what
     * makes it useful for bisecting: if blocks misbehave with
     * {@code -Dsigma.viamcp.reinject.modernState=false} as well, the patch is
     * not the cause.
     */
    private static final String MODERN_STATE_PROPERTY = "sigma.viamcp.reinject.modernState";

    /** No local block state for this raw id; the caller must keep Via's value. */
    private static final int NO_NATIVE_STATE = -1;

    /**
     * 1.16.4 clientbound PLAY packet ids, resolved from the live
     * {@link ProtocolType#PLAY} registry instead of hardcoded ordinals.
     */
    private static volatile int[] packetIds;

    /** Guards against a re-injection triggering another drain of the same queue. */
    private boolean draining;

    @Override
    public void handlerAdded(ChannelHandlerContext ctx) throws Exception {
        ExtendedBlockUpdateReinjectSelfTest.runOnce();

        /*
         * Build the state table off the event loop while the connection is
         * still handshaking. A lookup on a table that has not been built yet
         * builds it inline, and the first place that would happen is the first
         * block change - i.e. on the netty thread, mid-game. Only started for
         * the version the table describes, so nothing is parsed on a 1.8 or
         * 1.12 connection.
         */
        if (isModernStateOverrideActive()) {
            ModernBlockStateMap.warmupAsync();
        }

        super.handlerAdded(ctx);
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        drain(ctx);
        super.channelRead(ctx, applyModernStateOverride(msg));
    }

    @Override
    public void channelReadComplete(ChannelHandlerContext ctx) throws Exception {
        drain(ctx);
        /*
         * A parked override is only meaningful while its translated packet is
         * still in flight, and the Via decoder emits synchronously, so anything
         * still parked at the end of the read burst was cancelled or reshaped
         * by Via. Dropping it here keeps it from being applied later to an
         * unrelated packet that happens to hit the same position.
         */
        ExtendedBlockUpdateStore.clearOverrides();
        super.channelReadComplete(ctx);
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        ExtendedBlockUpdateStore.clearAll();
        super.channelInactive(ctx);
    }

    private void drain(ChannelHandlerContext ctx) {
        if (!WorldHeightHelper.isExtendedHeight()) {
            ExtendedBlockUpdateStore.clearAll();
            return;
        }

        if (this.draining) {
            return;
        }

        this.draining = true;

        try {
            ExtendedBlockUpdateStore.CapturedUpdate update;
            while ((update = ExtendedBlockUpdateStore.poll()) != null) {
                ByteBuf synthetic = null;
                try {
                    synthetic = encode(update);
                    if (synthetic != null) {
                        logInjection(ctx, update, synthetic);
                        // Ownership moves to the vanilla decoder; do not release here.
                        ctx.fireChannelRead(synthetic);
                    }
                } catch (Throwable t) {
                    if (synthetic != null && synthetic.refCnt() > 0) {
                        synthetic.release();
                    }
                    if (isDebugEnabled() || ChunkDataInterceptor.isDebugEnabled()) {
                        LOGGER.warn("[ExtendedHeight] Could not re-inject captured block update kind={}: {}",
                                update.kind, t.toString());
                    } else {
                        LOGGER.debug("[ExtendedHeight] Could not re-inject captured block update: {}", t.getMessage());
                    }
                }
            }
        } finally {
            this.draining = false;
        }
    }

    /**
     * Replaces Via's block-change packet with one carrying the state the server
     * actually sent, when {@link ChunkDataInterceptor} parked a raw state for
     * the same wire position and {@link ModernBlockStateMap} can name a local
     * block for it. Returns {@code msg} untouched in every other case,
     * including every failure - a wrong block is bad, a dropped packet is
     * worse.
     *
     * <p>Called for every inbound packet, so the exits are ordered cheapest
     * first: an empty override map costs two field reads and is the permanent
     * state of every connection that is not to the one server version the
     * state table was built for.
     */
    private static Object applyModernStateOverride(Object msg) {
        if (!ExtendedBlockUpdateStore.hasOverrides() || !(msg instanceof ByteBuf)
                || !isModernStateOverrideActive()) {
            return msg;
        }

        ByteBuf buf = (ByteBuf) msg;
        int savedReaderIndex = buf.readerIndex();
        ByteBuf patched = null;

        try {
            int packetId = readVarInt(buf);

            if (packetId == packetId(PacketSlot.CHANGE_BLOCK)) {
                patched = patchSingle(buf);
            } else if (packetId == packetId(PacketSlot.MULTI_BLOCK_CHANGE)) {
                patched = patchMulti(buf);
            }
        } catch (Throwable t) {
            patched = null;

            if (isDebugEnabled() || ChunkDataInterceptor.isDebugEnabled()) {
                LOGGER.warn("[ModernBlockPassthrough] Could not patch a translated block change, keeping Via's: {}",
                        t.toString());
            } else {
                LOGGER.debug("[ModernBlockPassthrough] Could not patch a translated block change: {}", t.getMessage());
            }
        } finally {
            buf.readerIndex(savedReaderIndex);
        }

        if (patched == null) {
            return msg;
        }

        // Via's output is ours to dispose of; the replacement takes its place
        // one-for-one, so the position is still only written once.
        if (buf.refCnt() > 0) {
            buf.release();
        }

        return patched;
    }

    /**
     * 1.16.4 BLOCK_UPDATE is {@code [pos long][VarInt state]}. The position long
     * is copied straight out of Via's packet instead of being rebuilt from x/y/z
     * so the patched packet cannot possibly land somewhere else.
     *
     * <p>Expects {@code buf} positioned just after the packet id varint.
     * Package-private for {@link ExtendedBlockUpdateReinjectSelfTest}.
     *
     * @return the replacement packet, or {@code null} to keep Via's
     */
    static ByteBuf patchSingle(ByteBuf buf) {
        long packedPos = buf.readLong();
        int viaStateId = readVarInt(buf);

        if (buf.isReadable()) {
            // Not the layout we assumed - leave it alone rather than guess.
            return null;
        }

        int rawStateId = ExtendedBlockUpdateStore.takeSingleOverride(packedPos);
        if (rawStateId == ExtendedBlockUpdateStore.NO_OVERRIDE) {
            return null;
        }

        int nativeStateId = resolveModernStateId(rawStateId);
        if (nativeStateId == NO_NATIVE_STATE || nativeStateId == viaStateId) {
            return null;
        }

        logSingleOverride(packedPos, rawStateId, viaStateId, nativeStateId);
        return encodeSingleRaw(packedPos, nativeStateId);
    }

    /**
     * {@code [id][pos long][VarInt state]}, the same bytes
     * {@link #encodeSingle(int, int, int, int)} produces, but from an already
     * packed position - there is no {@link BlockPos} round trip that could move
     * the block.
     */
    static ByteBuf encodeSingleRaw(long packedPos, int nativeStateId) {
        ByteBuf buf = Unpooled.buffer();
        PacketBuffer out = new PacketBuffer(buf);
        out.writeVarInt(packetId(PacketSlot.CHANGE_BLOCK));
        out.writeLong(packedPos);
        out.writeVarInt(nativeStateId);
        return buf;
    }

    /**
     * 1.16.4 SECTION_BLOCKS_UPDATE is
     * {@code [section long][boolean][VarInt count][count * VarLong]}. Records
     * are matched to the raw ones by their 12-bit in-section position, so
     * reordered or partially dropped records patch what they can and leave the
     * rest as Via translated it. Via's own suppress-light flag and record order
     * are preserved.
     *
     * <p>Expects {@code buf} positioned just after the packet id varint.
     * Package-private for {@link ExtendedBlockUpdateReinjectSelfTest}.
     *
     * @return the replacement packet, or {@code null} to keep Via's
     */
    static ByteBuf patchMulti(ByteBuf buf) {
        long sectionPos = buf.readLong();
        boolean suppressLightUpdates = buf.readBoolean();
        int count = readVarInt(buf);

        if (count < 0 || count > buf.readableBytes()) {
            return null;
        }

        long[] records = new long[count];
        for (int i = 0; i < count; ++i) {
            records[i] = readVarLong(buf);
        }

        if (buf.isReadable()) {
            return null;
        }

        byte[] rawPayload = ExtendedBlockUpdateStore.takeMultiOverride(sectionPos);
        if (rawPayload == null) {
            return null;
        }

        long[] rawRecords = parseSectionBlocksPayload(rawPayload).records;
        int patchedRecords = 0;

        for (int i = 0; i < records.length; ++i) {
            long packedPos = records[i] & 0xFFFL;
            int rawStateId = findRawState(rawRecords, packedPos);
            if (rawStateId < 0) {
                continue;
            }

            int nativeStateId = resolveModernStateId(rawStateId);
            if (nativeStateId == NO_NATIVE_STATE || nativeStateId == (int) (records[i] >>> 12)) {
                continue;
            }

            records[i] = ((long) nativeStateId << 12) | packedPos;
            ++patchedRecords;
        }

        if (patchedRecords == 0) {
            return null;
        }

        logMultiOverride(sectionPos, count, patchedRecords);
        return encodeMulti(sectionPos, suppressLightUpdates, records);
    }

    /** Raw state for an in-section position, or -1 when that record was not captured. */
    private static int findRawState(long[] rawRecords, long packedPos) {
        for (long rawRecord : rawRecords) {
            if ((rawRecord & 0xFFFL) == packedPos) {
                return (int) (rawRecord >>> 12);
            }
        }

        return -1;
    }

    /**
     * The whole gate for the patch, in one place.
     *
     * <p>{@link ModernBlockStateMap} is indexed by <b>1.21.11</b> state ids and
     * {@link ChunkDataInterceptor} reads ids straight off the wire, i.e. in the
     * server's own version. On a 1.21.9 server the id spaces differ - that is
     * precisely why {@code Protocol1_21_11To1_21_9} ships a block-state mapping
     * at all - and looking up a foreign id would return an unrelated block:
     * silent, and far worse than the stone the downgrade produces. Supporting
     * another server version means generating another table, not widening this
     * check.
     */
    public static boolean isModernStateOverrideActive() {
        return ProtocolVersion.v1_21_11.equals(WorldHeightHelper.getTargetVersionSafe())
                && isEnabled(MODERN_STATE_PROPERTY);
    }

    /**
     * Raw (1.21.11) state id -> local state id, or {@link #NO_NATIVE_STATE} when
     * there is no direct match. Deliberately does <b>not</b> fall back to
     * {@link ExtendedBlockStateMapper}: that fallback is Via's downgrade chain,
     * which is what the untouched packet already carries.
     */
    private static int resolveModernStateId(int rawStateId) {
        int nativeStateId = ModernBlockStateMap.toNativeId(rawStateId);
        if (nativeStateId == ModernBlockStateMap.NO_MAPPING) {
            return NO_NATIVE_STATE;
        }

        return Block.BLOCK_STATE_IDS.getByValue(nativeStateId) != null ? nativeStateId : NO_NATIVE_STATE;
    }

    private static ByteBuf encode(ExtendedBlockUpdateStore.CapturedUpdate update) throws Exception {
        switch (update.kind) {
            case SINGLE:
                if (!isEnabled(SINGLE_PROPERTY)) {
                    return null;
                }

                return encodeSingle(update.x, update.y, update.z,
                        ExtendedBlockStateMapper.mapToNativeId(update.stateId));
            case MULTI: {
                if (!isEnabled(MULTI_PROPERTY)) {
                    return null;
                }

                SectionBlocksPayload payload = parseSectionBlocksPayload(update.payload);
                long[] records = new long[payload.records.length];

                for (int i = 0; i < records.length; ++i) {
                    long record = payload.records[i];
                    long packedPos = record & 0xFFFL;
                    int mappedId = ExtendedBlockStateMapper.mapToNativeId((int) (record >>> 12));
                    records[i] = ((long) mappedId << 12) | packedPos;
                }

                return encodeMulti(update.sectionPos, payload.suppressLightUpdates, records);
            }
            case DESTRUCTION:
                if (!isEnabled(DESTRUCTION_PROPERTY)) {
                    return null;
                }

                return encodeDestruction(update.entityId, update.x, update.y, update.z, update.stage);
            default:
                return null;
        }
    }

    /**
     * {@code [id][BlockPos long][VarInt state]} - byte-for-byte what
     * {@link SChangeBlockPacket#readPacketData(PacketBuffer)} consumes.
     */
    static ByteBuf encodeSingle(int x, int y, int z, int nativeStateId) throws Exception {
        SChangeBlockPacket packet = new SChangeBlockPacket(new BlockPos(x, y, z), stateById(nativeStateId));
        ByteBuf buf = Unpooled.buffer();
        PacketBuffer out = new PacketBuffer(buf);
        out.writeVarInt(packetId(PacketSlot.CHANGE_BLOCK));
        packet.writePacketData(out);
        return buf;
    }

    /**
     * {@code [id][VarInt breaker][BlockPos long][byte stage]} - byte-for-byte
     * what {@link SAnimateBlockBreakPacket#readPacketData(PacketBuffer)}
     * consumes.
     */
    static ByteBuf encodeDestruction(int entityId, int x, int y, int z, int stage) throws Exception {
        SAnimateBlockBreakPacket packet = new SAnimateBlockBreakPacket(entityId, new BlockPos(x, y, z), stage);
        ByteBuf buf = Unpooled.buffer();
        PacketBuffer out = new PacketBuffer(buf);
        out.writeVarInt(packetId(PacketSlot.ANIMATE_BLOCK_BREAK));
        packet.writePacketData(out);
        return buf;
    }

    /**
     * {@code [id][section long][boolean][VarInt count][count * VarLong]} -
     * the exact inverse of
     * {@link SMultiBlockChangePacket#readPacketData(PacketBuffer)}. The packet
     * class has no constructor that takes raw records, so this one stage is
     * hand-encoded and covered by
     * {@link ExtendedBlockUpdateReinjectSelfTest}.
     */
    static ByteBuf encodeMulti(long sectionPos, boolean suppressLightUpdates, long[] records) {
        ByteBuf buf = Unpooled.buffer();
        PacketBuffer out = new PacketBuffer(buf);
        out.writeVarInt(packetId(PacketSlot.MULTI_BLOCK_CHANGE));
        out.writeLong(sectionPos);
        out.writeBoolean(suppressLightUpdates);
        out.writeVarInt(records.length);

        for (long record : records) {
            out.writeVarLong(record);
        }

        return buf;
    }

    private static BlockState stateById(int nativeStateId) {
        BlockState state = Block.BLOCK_STATE_IDS.getByValue(nativeStateId);
        return state != null ? state : Blocks.STONE.getDefaultState();
    }

    /** Section-blocks payload from the suppress-light slot onward. */
    private static final class SectionBlocksPayload {
        private final boolean suppressLightUpdates;
        private final long[] records;

        private SectionBlocksPayload(boolean suppressLightUpdates, long[] records) {
            this.suppressLightUpdates = suppressLightUpdates;
            this.records = records;
        }
    }

    /**
     * 1.18/1.19 send {@code [boolean][VarInt count][records]}; 1.20+ dropped the
     * boolean and send {@code [VarInt count][records]}. The target version picks
     * the expected layout and the other one is tried as a fallback, so a wrong
     * version boundary degrades to a retry instead of a malformed packet. A
     * layout is only accepted when it consumes the payload exactly.
     */
    private static SectionBlocksPayload parseSectionBlocksPayload(byte[] payload) {
        boolean expectBoolean = WorldHeightHelper
                .hasSectionBlocksUpdateSuppressLight(WorldHeightHelper.getTargetVersionSafe());

        SectionBlocksPayload parsed = tryParseSectionBlocksPayload(payload, expectBoolean);
        if (parsed == null) {
            parsed = tryParseSectionBlocksPayload(payload, !expectBoolean);

            if (parsed != null && isDebugEnabled()) {
                LOGGER.info("[ExtendedHeightProbe] SECTION_BLOCKS_UPDATE layout fallback: suppressLightField={}",
                        !expectBoolean);
            }
        }

        if (parsed == null) {
            throw new IllegalStateException("Section blocks update payload (" + payload.length
                    + " bytes) matched neither the 1.18 nor the 1.20 layout");
        }

        return parsed;
    }

    private static SectionBlocksPayload tryParseSectionBlocksPayload(byte[] payload, boolean withBoolean) {
        ByteBuf buf = Unpooled.wrappedBuffer(payload);

        try {
            boolean suppressLightUpdates = false;

            if (withBoolean) {
                int raw = buf.readUnsignedByte();
                if (raw > 1) {
                    return null;
                }

                suppressLightUpdates = raw != 0;
            }

            int count = readVarInt(buf);
            if (count < 0 || count > buf.readableBytes()) {
                return null;
            }

            long[] records = new long[count];

            for (int i = 0; i < count; ++i) {
                records[i] = readVarLong(buf);
            }

            return buf.isReadable() ? null : new SectionBlocksPayload(suppressLightUpdates, records);
        } catch (RuntimeException e) {
            return null;
        }
    }

    enum PacketSlot {
        ANIMATE_BLOCK_BREAK,
        CHANGE_BLOCK,
        MULTI_BLOCK_CHANGE
    }

    static int packetId(PacketSlot slot) {
        int[] ids = packetIds;

        if (ids == null) {
            ids = new int[PacketSlot.values().length];
            ids[PacketSlot.ANIMATE_BLOCK_BREAK.ordinal()] = resolvePacketId(new SAnimateBlockBreakPacket());
            ids[PacketSlot.CHANGE_BLOCK.ordinal()] = resolvePacketId(new SChangeBlockPacket());
            ids[PacketSlot.MULTI_BLOCK_CHANGE.ordinal()] = resolvePacketId(new SMultiBlockChangePacket());
            packetIds = ids;
        }

        return ids[slot.ordinal()];
    }

    private static int resolvePacketId(IPacket<?> packet) {
        Integer id = ProtocolType.PLAY.getPacketId(PacketDirection.CLIENTBOUND, packet);

        if (id == null) {
            throw new IllegalStateException(
                    packet.getClass().getSimpleName() + " is not registered in ProtocolType.PLAY CLIENTBOUND");
        }

        return id;
    }

    public static boolean isDebugEnabled() {
        return Boolean.getBoolean(DEBUG_PROPERTY);
    }

    private static boolean isEnabled(String property) {
        return !"false".equalsIgnoreCase(System.getProperty(property));
    }

    private void logInjection(ChannelHandlerContext ctx, ExtendedBlockUpdateStore.CapturedUpdate update, ByteBuf buf) {
        if (!isDebugEnabled()) {
            return;
        }

        int wireId = buf.getUnsignedByte(buf.readerIndex());
        LOGGER.info(
                "[ExtendedHeightProbe] REINJECT kind={} wirePacketId={} pos=({},{},{}) packedPos={} sectionPos={} "
                        + "rawStateId={} nativeStateId={} entityId={} stage={} readerIndex={} writerIndex={} "
                        + "readableBytes={} hex={} before={} after={}",
                update.kind, wireId, update.x, update.y, update.z, BlockPos.pack(update.x, update.y, update.z),
                update.sectionPos, update.stateId,
                update.kind == ExtendedBlockUpdateStore.Kind.SINGLE
                        ? ExtendedBlockStateMapper.mapToNativeId(update.stateId)
                        : -1,
                update.entityId, update.stage, buf.readerIndex(), buf.writerIndex(), buf.readableBytes(),
                ByteBufUtil.hexDump(buf), neighbour(ctx, -1), neighbour(ctx, 1));
    }

    private static String neighbour(ChannelHandlerContext ctx, int offset) {
        List<String> names = ctx.pipeline().names();
        int index = names.indexOf(HANDLER_NAME) + offset;
        return index >= 0 && index < names.size() ? names.get(index) : "<none>";
    }

    /**
     * The line that answers "did the passthrough fire and what did it change":
     * {@code via=} is what the downgrade produced, {@code native=} is what the
     * client will actually place. Enabled by
     * {@code -Dsigma.viamcp.debugBlockUpdateReinject=true}.
     */
    private static void logSingleOverride(long packedPos, int rawStateId, int viaStateId, int nativeStateId) {
        if (!isDebugEnabled()) {
            return;
        }

        LOGGER.info(
                "[ModernBlockPassthrough] OVERRIDE_BLOCK_UPDATE pos=({},{},{}) rawStateId={} via={} native={}",
                WorldHeightHelper.blockPosX(packedPos), WorldHeightHelper.blockPosY(packedPos),
                WorldHeightHelper.blockPosZ(packedPos), rawStateId, describeState(viaStateId),
                describeState(nativeStateId));
    }

    private static void logMultiOverride(long sectionPos, int records, int patchedRecords) {
        if (!isDebugEnabled()) {
            return;
        }

        LOGGER.info("[ModernBlockPassthrough] OVERRIDE_SECTION_BLOCKS_UPDATE sectionPos={} sectionY={} records={} patched={}",
                sectionPos, WorldHeightHelper.sectionPosY(sectionPos), records, patchedRecords);
    }

    private static String describeState(int stateId) {
        BlockState state = Block.BLOCK_STATE_IDS.getByValue(stateId);
        return stateId + (state != null ? "/" + state.getBlock() : "/<unknown>");
    }

    private static int readVarInt(ByteBuf buf) {
        int value = 0;
        int position = 0;
        byte currentByte;

        do {
            currentByte = buf.readByte();
            value |= (currentByte & 0x7F) << position;
            position += 7;
            if (position >= 32) {
                throw new RuntimeException("VarInt too big");
            }
        } while ((currentByte & 0x80) != 0);

        return value;
    }

    private static long readVarLong(ByteBuf buf) {
        long value = 0L;
        int position = 0;
        byte currentByte;

        do {
            currentByte = buf.readByte();
            value |= (long) (currentByte & 0x7F) << position;
            position += 7;
            if (position >= 64) {
                throw new RuntimeException("VarLong too big");
            }
        } while ((currentByte & 0x80) != 0);

        return value;
    }
}
