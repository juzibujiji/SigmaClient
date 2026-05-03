package com.mentalfrostbyte.jello.util.game.world;

import net.minecraft.world.LightType;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.ChunkSection;
import net.minecraft.world.chunk.NibbleArray;
import net.minecraft.world.lighting.WorldLightManager;
import net.minecraft.util.math.SectionPos;

public class ExtendedChunkData {
    private final int chunkX;
    private final int chunkZ;
    private final ChunkSection[] sections;
    private final byte[][] skyLight;
    private final byte[][] blockLight;
    private boolean sectionPayload;

    public ExtendedChunkData(int chunkX, int chunkZ) {
        this.chunkX = chunkX;
        this.chunkZ = chunkZ;
        this.sections = new ChunkSection[WorldHeightHelper.getSectionCount()];
        this.skyLight = new byte[WorldHeightHelper.getLightSectionCount()][];
        this.blockLight = new byte[WorldHeightHelper.getLightSectionCount()][];
    }

    public int getChunkX() {
        return this.chunkX;
    }

    public int getChunkZ() {
        return this.chunkZ;
    }

    public void setSection(int sectionIndex, ChunkSection section) {
        if (WorldHeightHelper.isSectionIndexInBounds(sectionIndex)) {
            this.sections[sectionIndex] = section;
            this.sectionPayload = true;
        }
    }

    public void setSkyLight(int lightIndex, byte[] data) {
        if (lightIndex >= 0 && lightIndex < this.skyLight.length) {
            this.skyLight[lightIndex] = data;
        }
    }

    public void setBlockLight(int lightIndex, byte[] data) {
        if (lightIndex >= 0 && lightIndex < this.blockLight.length) {
            this.blockLight[lightIndex] = data;
        }
    }

    public boolean hasSections() {
        return this.sectionPayload;
    }

    public int getSectionPayloadCount() {
        return this.sectionPayload ? this.sections.length : 0;
    }

    public int getNegativeSectionPayloadCount() {
        return this.sectionPayload ? Math.min(WorldHeightHelper.getSectionOffset(), this.sections.length) : 0;
    }

    public int getNonEmptySectionCount() {
        int count = 0;

        for (ChunkSection section : this.sections) {
            if (!ChunkSection.isEmpty(section)) {
                ++count;
            }
        }

        return count;
    }

    public int getNonEmptyNegativeSectionCount() {
        int count = 0;

        for (int i = 0; i < this.sections.length; ++i) {
            if (WorldHeightHelper.getSectionYForIndex(i) < 0 && !ChunkSection.isEmpty(this.sections[i])) {
                ++count;
            }
        }

        return count;
    }

    public String describeNonEmptyNegativeSections(int limit) {
        StringBuilder builder = new StringBuilder();
        int written = 0;
        int total = 0;

        for (int i = 0; i < this.sections.length; ++i) {
            int sectionY = WorldHeightHelper.getSectionYForIndex(i);
            if (sectionY >= 0 || ChunkSection.isEmpty(this.sections[i])) {
                continue;
            }

            if (written < limit) {
                if (builder.length() > 0) {
                    builder.append(',');
                }

                builder.append(sectionY);
                ++written;
            }

            ++total;
        }

        if (total > written) {
            builder.append(",...");
        }

        return builder.length() == 0 ? "none" : builder.toString();
    }

    public boolean hasLight() {
        return hasLight(this.skyLight) || hasLight(this.blockLight);
    }

    public int getLightPayloadCount() {
        return countLight(this.skyLight) + countLight(this.blockLight);
    }

    private static boolean hasLight(byte[][] light) {
        for (byte[] section : light) {
            if (section != null) {
                return true;
            }
        }

        return false;
    }

    private static int countLight(byte[][] light) {
        int count = 0;

        for (byte[] section : light) {
            if (section != null) {
                ++count;
            }
        }

        return count;
    }

    public void mergeFrom(ExtendedChunkData other) {
        for (int i = 0; i < this.sections.length && i < other.sections.length; ++i) {
            if (other.hasSections()) {
                this.sections[i] = other.sections[i];
            }
        }

        this.sectionPayload |= other.sectionPayload;

        mergeLight(this.skyLight, other.skyLight);
        mergeLight(this.blockLight, other.blockLight);
    }

    private static void mergeLight(byte[][] target, byte[][] source) {
        for (int i = 0; i < target.length && i < source.length; ++i) {
            if (source[i] != null) {
                target[i] = source[i];
            }
        }
    }

    public int applySections(Chunk chunk) {
        ChunkSection[] target = chunk.getSections();
        int changed = 0;

        for (int i = 0; i < target.length && i < this.sections.length; ++i) {
            target[i] = this.sections[i];
            ++changed;
        }

        return changed;
    }

    public int applyLight(WorldLightManager lightManager) {
        int changed = 0;
        changed += applyLightArray(lightManager, LightType.SKY, this.skyLight);
        changed += applyLightArray(lightManager, LightType.BLOCK, this.blockLight);
        return changed;
    }

    private int applyLightArray(WorldLightManager lightManager, LightType type, byte[][] light) {
        int changed = 0;

        for (int i = 0; i < light.length; ++i) {
            byte[] sectionLight = light[i];
            if (sectionLight == null) {
                continue;
            }

            int sectionY = WorldHeightHelper.getRawLightBitToSectionY(i);
            NibbleArray nibbleArray = WorldHeightHelper.isLightResetMarker(sectionLight)
                    ? new NibbleArray()
                    : new NibbleArray((byte[]) sectionLight.clone());
            lightManager.setData(type, SectionPos.of(this.chunkX, sectionY, this.chunkZ), nibbleArray, true);
            ++changed;
        }

        return changed;
    }
}
