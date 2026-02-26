package com.mentalfrostbyte.jello.util.game.world;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.network.PacketBuffer;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.ChunkSection;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.lang.reflect.Method;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Intercepts raw 1.17+ chunk packets BEFORE ViaVersion translates them.
 * Captures Y<0 section data that ViaBackwards strips during translation.
 * Uses reflection for ViaBackwards API to avoid compile-time dependency issues.
 */
public class ChunkDataInterceptor extends ChannelInboundHandlerAdapter {

    private static final Logger LOGGER = LogManager.getLogger();
    public static final String HANDLER_NAME = "chunk-data-interceptor";
    private static final int CHUNK_PACKET_ID = 0x22;
    private static final int NEG_SECTION_COUNT = 4;
    private static final int MAX_STORED = 2048;

    private static final ConcurrentHashMap<Long, byte[]> STORE = new ConcurrentHashMap<>();

    // Block state mapping: 1.17 ID -> 1.16.4 ID (lazily loaded via reflection)
    private static volatile int[] blockStateMap = null;
    private static volatile boolean mapLoaded = false;

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (msg instanceof ByteBuf && WorldHeightHelper.isExtendedHeight()) {
            ByteBuf buf = (ByteBuf) msg;
            int saved = buf.readerIndex();
            try {
                int packetId = readVarInt(buf);
                if (packetId == CHUNK_PACKET_ID) {
                    int cx = buf.readInt();
                    int cz = buf.readInt();
                    byte[] data = new byte[buf.readableBytes()];
                    buf.getBytes(buf.readerIndex(), data);
                    if (STORE.size() < MAX_STORED) {
                        STORE.put(ChunkPos.asLong(cx, cz), data);
                    }
                }
            } catch (Exception ignored) {
            } finally {
                buf.readerIndex(saved);
            }
        }
        super.channelRead(ctx, msg);
    }

    /**
     * Inject Y<0 sections into a chunk using stored raw 1.17+ data.
     */
    public static void injectNegativeYSections(Chunk chunk, int chunkX, int chunkZ) {
        byte[] raw = STORE.remove(ChunkPos.asLong(chunkX, chunkZ));
        if (raw == null)
            return;

        int offset = WorldHeightHelper.getSectionOffset();
        if (offset <= 0)
            return;

        try {
            PacketBuffer buf = new PacketBuffer(Unpooled.wrappedBuffer(raw));
            buf.readCompoundTag(); // skip heightmaps NBT
            buf.readVarInt(); // skip data size

            loadBlockStateMap(); // ensure mapping is loaded
            ChunkSection[] sections = chunk.getSections();
            int toRead = Math.min(NEG_SECTION_COUNT, offset);

            for (int i = 0; i < toRead; i++) {
                int sectionY = i - offset;
                buf.readShort(); // block count (unused, we count ourselves)
                ChunkSection section = readBlockStates(buf, sectionY);
                skipPaletted(buf, 3); // skip biomes
                if (section != null) {
                    sections[i] = section;
                }
            }
        } catch (Exception e) {
            LOGGER.warn("[ChunkInterceptor] Failed for ({},{}): {}", chunkX, chunkZ, e.getMessage());
        }
    }

    private static ChunkSection readBlockStates(PacketBuffer buf, int sectionY) {
        int bpe = buf.readUnsignedByte();

        if (bpe == 0) {
            int rawId = buf.readVarInt();
            int dataLen = buf.readVarInt();
            for (int d = 0; d < dataLen; d++)
                buf.readLong();

            BlockState state = stateFromId(mapId(rawId));
            ChunkSection sec = new ChunkSection(sectionY << 4);
            if (state != Blocks.AIR.getDefaultState()) {
                for (int x = 0; x < 16; x++)
                    for (int y = 0; y < 16; y++)
                        for (int z = 0; z < 16; z++)
                            sec.setBlockState(x, y, z, state);
            }
            return sec;
        }

        int[] palette = null;
        if (bpe <= 8) {
            int palLen = buf.readVarInt();
            palette = new int[palLen];
            for (int p = 0; p < palLen; p++) {
                palette[p] = mapId(buf.readVarInt());
            }
        }

        int dataLen = buf.readVarInt();
        long[] data = new long[dataLen];
        for (int d = 0; d < dataLen; d++)
            data[d] = buf.readLong();

        ChunkSection sec = new ChunkSection(sectionY << 4);
        long mask = (1L << bpe) - 1;
        int perLong = 64 / bpe;

        for (int idx = 0; idx < 4096; idx++) {
            int li = idx / perLong;
            int bo = (idx % perLong) * bpe;
            if (li >= data.length)
                break;

            int palIdx = (int) ((data[li] >> bo) & mask);
            int stateId;
            if (palette != null) {
                stateId = (palIdx >= 0 && palIdx < palette.length) ? palette[palIdx] : 0;
            } else {
                stateId = mapId(palIdx);
            }

            BlockState state = stateFromId(stateId);
            if (state != Blocks.AIR.getDefaultState()) {
                sec.setBlockState(idx & 0xF, (idx >> 8) & 0xF, (idx >> 4) & 0xF, state);
            }
        }
        return sec;
    }

    private static void skipPaletted(PacketBuffer buf, int indirectThreshold) {
        int bpe = buf.readUnsignedByte();
        if (bpe == 0) {
            buf.readVarInt();
        } else if (bpe <= indirectThreshold) {
            int palLen = buf.readVarInt();
            for (int i = 0; i < palLen; i++)
                buf.readVarInt();
        }
        int dataLen = buf.readVarInt();
        buf.skipBytes(dataLen * 8);
    }

    /**
     * Map a 1.17+ block state ID to 1.16.4 using ViaBackwards mapping (loaded via
     * reflection).
     */
    private static int mapId(int newId) {
        if (blockStateMap != null && newId >= 0 && newId < blockStateMap.length) {
            int mapped = blockStateMap[newId];
            return mapped != -1 ? mapped : 1; // stone fallback
        }
        return newId; // no mapping available, pass through
    }

    private static BlockState stateFromId(int id) {
        BlockState s = Block.BLOCK_STATE_IDS.getByValue(id);
        return s != null ? s : Blocks.STONE.getDefaultState();
    }

    /**
     * Load ViaBackwards block state mapping via reflection to avoid compile-time
     * API dependency.
     */
    @SuppressWarnings("all")
    private static void loadBlockStateMap() {
        if (mapLoaded)
            return;
        mapLoaded = true;
        try {
            // Via.getManager().getProtocolManager().getProtocol(Protocol1_17To1_16_4.class)
            Class<?> viaClass = Class.forName("com.viaversion.viaversion.api.Via");
            Object manager = viaClass.getMethod("getManager").invoke(null);
            Object protocolManager = manager.getClass().getMethod("getProtocolManager").invoke(manager);
            Class<?> protoClass = Class.forName(
                    "com.viaversion.viabackwards.protocol.v1_17to1_16_4.Protocol1_17To1_16_4");
            Method getProtocol = protocolManager.getClass().getMethod("getProtocol", Class.class);
            Object protocol = getProtocol.invoke(protocolManager, protoClass);
            if (protocol == null)
                return;

            // protocol.getMappingData().getBlockStateMappings()
            Object mappingData = protocol.getClass().getMethod("getMappingData").invoke(protocol);
            if (mappingData == null)
                return;

            Object blockMappings = mappingData.getClass().getMethod("getBlockStateMappings").invoke(mappingData);
            if (blockMappings == null)
                return;

            // mappings.size() and mappings.getNewId(id)
            Method sizeMethod = blockMappings.getClass().getMethod("size");
            Method getNewIdMethod = blockMappings.getClass().getMethod("getNewId", int.class);
            int size = (int) sizeMethod.invoke(blockMappings);

            int[] map = new int[size];
            for (int i = 0; i < size; i++) {
                map[i] = (int) getNewIdMethod.invoke(blockMappings, i);
            }
            blockStateMap = map;
            LOGGER.info("[ChunkInterceptor] Loaded {} block state mappings from ViaBackwards", size);
        } catch (Exception e) {
            LOGGER.warn("[ChunkInterceptor] Could not load block mappings (blocks may render incorrectly): {}",
                    e.getMessage());
        }
    }

    public static void clearAll() {
        STORE.clear();
    }

    private static int readVarInt(ByteBuf buf) {
        int val = 0, pos = 0;
        byte b;
        do {
            b = buf.readByte();
            val |= (b & 0x7F) << pos;
            pos += 7;
            if (pos >= 32)
                throw new RuntimeException("VarInt too big");
        } while ((b & 0x80) != 0);
        return val;
    }
}
