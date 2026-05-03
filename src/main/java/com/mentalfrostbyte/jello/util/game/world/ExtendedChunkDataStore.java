package com.mentalfrostbyte.jello.util.game.world;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import net.minecraft.util.math.ChunkPos;

public class ExtendedChunkDataStore {
    private static final int MAX_STORED = 2048;
    private static final ConcurrentHashMap<Long, ExtendedChunkData> STORE = new ConcurrentHashMap<>();
    private static final ConcurrentLinkedQueue<Long> ORDER = new ConcurrentLinkedQueue<>();

    private ExtendedChunkDataStore() {
    }

    public static void put(ExtendedChunkData data) {
        long key = ChunkPos.asLong(data.getChunkX(), data.getChunkZ());
        STORE.merge(key, data, (oldData, newData) -> {
            oldData.mergeFrom(newData);
            return oldData;
        });
        ORDER.add(key);
        trim();
    }

    public static ExtendedChunkData take(int chunkX, int chunkZ) {
        return STORE.remove(ChunkPos.asLong(chunkX, chunkZ));
    }

    public static ExtendedChunkData peek(int chunkX, int chunkZ) {
        return STORE.get(ChunkPos.asLong(chunkX, chunkZ));
    }

    public static void clearAll() {
        STORE.clear();
        ORDER.clear();
    }

    private static void trim() {
        while (STORE.size() > MAX_STORED) {
            Long key = ORDER.poll();
            if (key == null) {
                return;
            }

            STORE.remove(key);
        }
    }
}
