package com.mentalfrostbyte.jello.module.impl.render.search;

import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;

import java.util.List;

public class ChunkRegion {
    public int chunkX;
    public int chunkZ;
    public List<BlockPos> blockPositions;

    public ChunkRegion(int var1, int var2, List<BlockPos> var3) {
        this.chunkX = var1;
        this.chunkZ = var2;
        this.blockPositions = var3;
    }

    public boolean isSameChunk(ChunkPos var1) {
        return var1.x == this.chunkX && var1.z == this.chunkZ;
    }

    public int getDistanceFromChunk(ChunkPos var1) {
        return (int)Math.sqrt(
				(var1.x - this.chunkX) * (var1.x - this.chunkX)
						+ (var1.z - this.chunkZ) * (var1.z - this.chunkZ)
        );
    }

    public ChunkPos getChunkPosition() {
        return new ChunkPos(this.chunkX, this.chunkZ);
    }
}
