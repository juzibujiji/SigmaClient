package com.mentalfrostbyte.jello.util.game.world;

import com.viaversion.viaversion.api.minecraft.chunks.Chunk;
import com.viaversion.viaversion.api.minecraft.chunks.DataPalette;
import com.viaversion.viaversion.api.minecraft.chunks.PaletteType;
import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import com.viaversion.viaversion.api.type.Type;
import com.viaversion.viaversion.api.type.types.chunk.ChunkType1_18;
import com.viaversion.viaversion.api.type.types.chunk.ChunkType1_20_2;
import com.viaversion.viaversion.api.type.types.chunk.ChunkType1_21_5;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import net.minecraft.block.Blocks;
import net.minecraft.world.chunk.ChunkSection;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Captures raw 1.18+ chunk data before ViaBackwards narrows it to 1.16.
 */
public class ChunkDataInterceptor extends ChannelInboundHandlerAdapter {
    private static final Logger LOGGER = LogManager.getLogger();
    private static final String DEBUG_PROPERTY = "sigma.viamcp.debugChunkCapture";
    public static final String HANDLER_NAME = "chunk-data-interceptor";

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (msg instanceof ByteBuf && WorldHeightHelper.canUseRawExtendedChunks()) {
            ByteBuf buf = (ByteBuf) msg;
            int savedReaderIndex = buf.readerIndex();
            int packetId = -1;

            try {
                packetId = readVarInt(buf);
                if (WorldHeightHelper.isRawChunkPacket(packetId)) {
                    ExtendedChunkData data = readChunkWithLight(buf);
                    if (data != null) {
                        ExtendedChunkDataStore.put(data);
                        logCapturedChunk(packetId, data);
                    }
                } else if (WorldHeightHelper.isRawLightPacket(packetId)) {
                    ExtendedChunkData data = readLightUpdate(buf);
                    if (data != null) {
                        ExtendedChunkDataStore.put(data);
                        logCapturedLight(packetId, data);
                    }
                }
            } catch (Exception e) {
                if (isDebugEnabled()) {
                    LOGGER.warn("[ExtendedHeight] Could not capture raw packet id={} target={}: {}", packetId,
                            WorldHeightHelper.getTargetVersionSafe(), e.toString());
                } else {
                    LOGGER.debug("[ExtendedHeight] Could not capture raw packet: {}", e.getMessage());
                }
            } finally {
                buf.readerIndex(savedReaderIndex);
            }
        }

        super.channelRead(ctx, msg);
    }

    public static void clearAll() {
        ExtendedChunkDataStore.clearAll();
    }

    public static boolean isDebugEnabled() {
        return Boolean.getBoolean(DEBUG_PROPERTY);
    }

    private static void logCapturedChunk(int packetId, ExtendedChunkData data) {
        if (isDebugEnabled()) {
            LOGGER.info(
                    "[ExtendedHeightProbe] RAW_CAPTURED packetId={} target={} chunk=({}, {}) sections={}/{} negativeSections={}/{} negativeY={} lightSections={}",
                    packetId, WorldHeightHelper.getTargetVersionSafe(), data.getChunkX(), data.getChunkZ(),
                    data.getNonEmptySectionCount(), data.getSectionPayloadCount(),
                    data.getNonEmptyNegativeSectionCount(), data.getNegativeSectionPayloadCount(),
                    data.describeNonEmptyNegativeSections(6), data.getLightPayloadCount());
        }
    }

    private static void logCapturedLight(int packetId, ExtendedChunkData data) {
        if (isDebugEnabled()) {
            LOGGER.info("[ExtendedHeightProbe] RAW_LIGHT_CAPTURED packetId={} target={} chunk=({}, {}) lightSections={}",
                    packetId, WorldHeightHelper.getTargetVersionSafe(), data.getChunkX(), data.getChunkZ(),
                    data.getLightPayloadCount());
        }
    }

    private static ExtendedChunkData readChunkWithLight(ByteBuf buf) throws Exception {
        ProtocolVersion version = WorldHeightHelper.getTargetVersionSafe();
        Type<Chunk> chunkType = createChunkType(version);

        Chunk viaChunk = chunkType.read(buf);
        ExtendedChunkData data = new ExtendedChunkData(viaChunk.getX(), viaChunk.getZ());
        com.viaversion.viaversion.api.minecraft.chunks.ChunkSection[] sections = viaChunk.getSections();

        for (int i = 0; i < sections.length && i < WorldHeightHelper.getSectionCount(); ++i) {
            int sectionY = WorldHeightHelper.getRawChunkMinSection() + i;
            data.setSection(WorldHeightHelper.sectionToIndex(sectionY), convertSection(sections[i], sectionY));
        }

        if (buf.isReadable()) {
            readLightPayload(buf, data);
        }

        return data;
    }

    private static Type<Chunk> createChunkType(ProtocolVersion version) {
        if (WorldHeightHelper.shouldUseChunkType1_21_5(version)) {
            return new ChunkType1_21_5(WorldHeightHelper.getRawChunkSectionCount(),
                    WorldHeightHelper.getDefaultBlockGlobalPaletteBits(),
                    WorldHeightHelper.getDefaultBiomeGlobalPaletteBits());
        }

        if (WorldHeightHelper.shouldUseChunkType1202(version)) {
            return new ChunkType1_20_2(WorldHeightHelper.getRawChunkSectionCount(),
                    WorldHeightHelper.getDefaultBlockGlobalPaletteBits(),
                    WorldHeightHelper.getDefaultBiomeGlobalPaletteBits());
        }

        return new ChunkType1_18(WorldHeightHelper.getRawChunkSectionCount(),
                WorldHeightHelper.getDefaultBlockGlobalPaletteBits(),
                WorldHeightHelper.getDefaultBiomeGlobalPaletteBits());
    }

    private static ExtendedChunkData readLightUpdate(ByteBuf buf) {
        int chunkX = readVarInt(buf);
        int chunkZ = readVarInt(buf);
        ExtendedChunkData data = new ExtendedChunkData(chunkX, chunkZ);
        readLightPayload(buf, data);
        return data;
    }

    private static ChunkSection convertSection(com.viaversion.viaversion.api.minecraft.chunks.ChunkSection source,
            int sectionY) {
        if (source == null) {
            return null;
        }

        DataPalette palette = source.palette(PaletteType.BLOCKS);
        if (palette == null) {
            return null;
        }

        ChunkSection target = new ChunkSection(sectionY << 4);
        boolean nonAir = false;

        for (int y = 0; y < 16; ++y) {
            for (int z = 0; z < 16; ++z) {
                for (int x = 0; x < 16; ++x) {
                    int rawId = palette.idAt(x, y, z);
                    net.minecraft.block.BlockState state = ExtendedBlockStateMapper.mapToBlockState(rawId);
                    if (state != Blocks.AIR.getDefaultState()) {
                        target.setBlockState(x, y, z, state, false);
                        nonAir = true;
                    }
                }
            }
        }

        return nonAir ? target : null;
    }

    private static void readLightPayload(ByteBuf buf, ExtendedChunkData data) {
        if (!buf.isReadable()) {
            return;
        }

        buf.readBoolean(); // trust edges
        long[] skyMask = readLongArray(buf);
        long[] blockMask = readLongArray(buf);
        long[] emptySkyMask = readLongArray(buf);
        long[] emptyBlockMask = readLongArray(buf);
        applyResetMask(data, true, emptySkyMask);
        applyResetMask(data, false, emptyBlockMask);
        readLightArrays(buf, data, true, skyMask);
        readLightArrays(buf, data, false, blockMask);
    }

    private static void applyResetMask(ExtendedChunkData data, boolean sky, long[] mask) {
        int max = WorldHeightHelper.getLightSectionCount();

        for (int i = 0; i < max; ++i) {
            if (isBitSet(mask, i)) {
                if (sky) {
                    data.setSkyLight(i, WorldHeightHelper.lightResetMarker());
                } else {
                    data.setBlockLight(i, WorldHeightHelper.lightResetMarker());
                }
            }
        }
    }

    private static void readLightArrays(ByteBuf buf, ExtendedChunkData data, boolean sky, long[] mask) {
        int count = readVarInt(buf);
        int read = 0;

        for (int i = 0; i < WorldHeightHelper.getLightSectionCount() && read < count; ++i) {
            if (!isBitSet(mask, i)) {
                continue;
            }

            byte[] light = readByteArray(buf);
            if (light.length == 2048) {
                if (sky) {
                    data.setSkyLight(i, light);
                } else {
                    data.setBlockLight(i, light);
                }
            }

            ++read;
        }

        while (read < count) {
            readByteArray(buf);
            ++read;
        }
    }

    private static boolean isBitSet(long[] mask, int bitIndex) {
        int longIndex = bitIndex >> 6;
        if (longIndex < 0 || longIndex >= mask.length) {
            return false;
        }

        return (mask[longIndex] & (1L << (bitIndex & 63))) != 0L;
    }

    private static long[] readLongArray(ByteBuf buf) {
        int length = readVarInt(buf);
        long[] data = new long[length];

        for (int i = 0; i < length; ++i) {
            data[i] = buf.readLong();
        }

        return data;
    }

    private static byte[] readByteArray(ByteBuf buf) {
        int length = readVarInt(buf);
        byte[] data = new byte[length];
        buf.readBytes(data);
        return data;
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
}
