package com.mentalfrostbyte.jello.util.game.world;

import com.mentalfrostbyte.jello.gui.base.JelloPortal;
import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;

/**
 * Centralizes the vertical build limits used by the 1.16 client shell.
 */
public class WorldHeightHelper {
    private static final int LEGACY_MIN_Y = 0;
    private static final int LEGACY_MAX_Y = 256;
    private static final int LEGACY_SECTION_COUNT = 16;

    private static final int EXTENDED_MIN_Y = -64;
    private static final int EXTENDED_MAX_Y = 320;
    private static final int EXTENDED_SECTION_COUNT = 24;
    private static final int EXTENDED_SECTION_OFFSET = 4;

    /**
     * Whether the selected Via target uses the 1.18+ Caves & Cliffs world height
     * (-64..320, 24 chunk sections). Extended up through 1.21.11 now that
     * ViaBackwards 5.9.1 provides the full downgrade chain and the raw-chunk
     * fast path has been taught about the newer packet IDs / chunk types.
     */
    public static boolean isExtendedHeight() {
        try {
            return JelloPortal.getVersion().newerThanOrEqualTo(ProtocolVersion.v1_18);
        } catch (Exception e) {
            return false;
        }
    }

    public static ProtocolVersion getTargetVersionSafe() {
        try {
            return JelloPortal.getVersion();
        } catch (Exception e) {
            return ProtocolVersion.v1_16_4;
        }
    }

    public static boolean isSupportedExtendedTarget(ProtocolVersion version) {
        return version.newerThanOrEqualTo(ProtocolVersion.v1_18);
    }

    public static boolean isRawChunkPacket(int packetId) {
        return isExtendedHeight() && packetId == getRawChunkPacketId(getTargetVersionSafe());
    }

    public static boolean isRawLightPacket(int packetId) {
        return isExtendedHeight() && packetId == getRawLightPacketId(getTargetVersionSafe());
    }

    /**
     * Raw LEVEL_CHUNK_WITH_LIGHT packet ordinal per target version. Values come
     * from ViaVersion 5.9.1's ClientboundPackets1_*.LEVEL_CHUNK_WITH_LIGHT enum
     * ordinals and were verified by reflection at implementation time.
     *
     * Ranges that share an enum share an ordinal:
     *   1.21.9  / 1.21.11                -> ClientboundPackets1_21_9  / _11
     *   1.21.5  / 1.21.6  / 1.21.7       -> ClientboundPackets1_21_5  / _6
     *   1.21.2  / 1.21.4                 -> ClientboundPackets1_21_2
     *   1.20.5  / 1.21                   -> ClientboundPackets1_21
     *   1.20.2  / 1.20.3                 -> ClientboundPackets1_20_2 / _3
     */
    public static int getRawChunkPacketId(ProtocolVersion version) {
        if (version.newerThanOrEqualTo(ProtocolVersion.v1_21_9)) {
            return 44;
        }

        if (version.newerThanOrEqualTo(ProtocolVersion.v1_21_5)) {
            return 39;
        }

        if (version.newerThanOrEqualTo(ProtocolVersion.v1_21_2)) {
            return 40;
        }

        if (version.newerThanOrEqualTo(ProtocolVersion.v1_20_5)) {
            return 39;
        }

        if (version.newerThanOrEqualTo(ProtocolVersion.v1_20_2)) {
            return 37;
        }

        if (version.newerThanOrEqualTo(ProtocolVersion.v1_19_4)) {
            return 36;
        }

        if (version.newerThanOrEqualTo(ProtocolVersion.v1_19_3)) {
            return 32;
        }

        if (version.newerThanOrEqualTo(ProtocolVersion.v1_19_1)) {
            return 33;
        }

        if (version.newerThanOrEqualTo(ProtocolVersion.v1_19)) {
            return 31;
        }

        return 34;
    }

    /**
     * Raw LIGHT_UPDATE packet ordinal per target version. Verified against
     * ClientboundPackets1_*.LIGHT_UPDATE ordinals in ViaVersion 5.9.1.
     */
    public static int getRawLightPacketId(ProtocolVersion version) {
        if (version.newerThanOrEqualTo(ProtocolVersion.v1_21_9)) {
            return 47;
        }

        if (version.newerThanOrEqualTo(ProtocolVersion.v1_21_5)) {
            return 42;
        }

        if (version.newerThanOrEqualTo(ProtocolVersion.v1_21_2)) {
            return 43;
        }

        if (version.newerThanOrEqualTo(ProtocolVersion.v1_20_5)) {
            return 42;
        }

        if (version.newerThanOrEqualTo(ProtocolVersion.v1_20_2)) {
            return 40;
        }

        if (version.newerThanOrEqualTo(ProtocolVersion.v1_19_4)) {
            return 39;
        }

        if (version.newerThanOrEqualTo(ProtocolVersion.v1_19_3)) {
            return 35;
        }

        if (version.newerThanOrEqualTo(ProtocolVersion.v1_19_1)) {
            return 36;
        }

        if (version.newerThanOrEqualTo(ProtocolVersion.v1_19)) {
            return 34;
        }

        return 37;
    }

    /**
     * Normal 1.16 chunk packets produced by ViaBackwards still use sectionY 0..15.
     */
    public static int translatedSectionToIndex(int translatedSectionY) {
        return translatedSectionY + getSectionOffset();
    }

    public static int translatedLightSectionY(int packetBitIndex) {
        return -1 + packetBitIndex;
    }

    public static int getTranslatedSectionCount() {
        return LEGACY_SECTION_COUNT;
    }

    public static int getLightSectionCount() {
        return getSectionCount() + 2;
    }

    public static int getLightMinSection() {
        return getMinSection() - 1;
    }

    public static int getTranslatedLightSectionCount() {
        return LEGACY_SECTION_COUNT + 2;
    }

    public static int getTranslatedLightMinSection() {
        return -1;
    }

    public static byte[] fullSkyLightArray() {
        byte[] data = new byte[2048];
        java.util.Arrays.fill(data, (byte) 0xFF);
        return data;
    }

    public static byte[] emptyLightArray() {
        return new byte[2048];
    }

    public static boolean isLightResetMarker(byte[] lightData) {
        return lightData != null && lightData.length == 0;
    }

    public static byte[] lightResetMarker() {
        return new byte[0];
    }

    public static int clampToBuildHeight(int y) {
        if (y < getMinY()) {
            return getMinY();
        }

        if (y >= getMaxY()) {
            return getMaxY() - 1;
        }

        return y;
    }

    public static int heightmapStorageValue(int absoluteHeight) {
        return absoluteHeight - getMinY();
    }

    public static int heightmapWorldValue(int storedHeight) {
        return storedHeight + getMinY();
    }

    public static int getHeightmapBits() {
        return 9;
    }

    public static int getHeightmapSize() {
        return 256;
    }

    public static int getHighestBuildY() {
        return getMaxY() - 1;
    }

    public static int getLowestBuildY() {
        return getMinY();
    }

    public static boolean isLegacyHeight() {
        return !isExtendedHeight();
    }

    public static boolean canUseRawExtendedChunks() {
        return isExtendedHeight();
    }

    public static boolean shouldInjectRawChunkPacket(int packetId) {
        return isRawChunkPacket(packetId) || isRawLightPacket(packetId);
    }

    public static int getRawLightBitToSectionY(int bitIndex) {
        return getLightMinSection() + bitIndex;
    }

    public static int getRawLightSectionYToBit(int sectionY) {
        return sectionY - getLightMinSection();
    }

    public static int getIndexForSectionY(int sectionY) {
        return sectionY + getSectionOffset();
    }

    public static int getSectionYForIndex(int sectionIndex) {
        return sectionIndex - getSectionOffset();
    }

    public static int getBlockYForSectionY(int sectionY) {
        return sectionY << 4;
    }

    public static int getSectionYForBlockY(int blockY) {
        return blockY >> 4;
    }

    public static boolean isSectionIndexInBounds(int sectionIndex) {
        return sectionIndex >= 0 && sectionIndex < getSectionCount();
    }

    public static boolean isSectionYInBounds(int sectionY) {
        return sectionY >= getMinSection() && sectionY < getMaxSection();
    }

    public static boolean isTranslatedSectionInBounds(int sectionY) {
        return sectionY >= 0 && sectionY < LEGACY_SECTION_COUNT;
    }

    public static int getRawChunkSectionCount() {
        return getSectionCount();
    }

    public static int getRawChunkMinSection() {
        return getMinSection();
    }

    public static int getRawChunkMaxSection() {
        return getMaxSection();
    }

    /**
     * True when the raw chunk payload should be parsed with ChunkType1_20_2
     * (1.20.2 through 1.21.4) rather than the older ChunkType1_18.
     */
    public static boolean shouldUseChunkType1202(ProtocolVersion version) {
        return version.newerThanOrEqualTo(ProtocolVersion.v1_20_2)
                && version.olderThan(ProtocolVersion.v1_21_5);
    }

    /**
     * True when the raw chunk payload uses the 1.21.5+ biome-as-registry-id
     * format and therefore requires ChunkType1_21_5 for correct decoding.
     */
    public static boolean shouldUseChunkType1_21_5(ProtocolVersion version) {
        return version.newerThanOrEqualTo(ProtocolVersion.v1_21_5);
    }

    public static int getDefaultBlockGlobalPaletteBits() {
        return 15;
    }

    public static int getDefaultBiomeGlobalPaletteBits() {
        return 6;
    }

    public static boolean hasExtendedPacketLayout(ProtocolVersion version) {
        return isSupportedExtendedTarget(version);
    }

    public static boolean is1_20_2OrNewer(ProtocolVersion version) {
        return version.newerThanOrEqualTo(ProtocolVersion.v1_20_2);
    }

    public static boolean is1_20_4OrOlder(ProtocolVersion version) {
        return version.olderThan(ProtocolVersion.v1_20_5);
    }

    public static boolean isWithinSupportedRawRange(ProtocolVersion version) {
        return isSupportedExtendedTarget(version);
    }

    public static boolean isRawPacketSupported() {
        return isWithinSupportedRawRange(getTargetVersionSafe());
    }

    public static boolean isTranslatedNativePacket() {
        return false;
    }

    /**
     * Check if connected to a 1.17+ server (for informational/logging purposes only).
     * Do NOT use this to change chunk/render behavior.
     */
    public static boolean isConnectedTo117Plus() {
        try {
            ProtocolVersion version = JelloPortal.getVersion();
            return version.newerThanOrEqualTo(ProtocolVersion.v1_17);
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Minimum Y coordinate of the world
     */
    public static int getMinY() {
        return isExtendedHeight() ? EXTENDED_MIN_Y : LEGACY_MIN_Y;
    }

    /**
     * Maximum Y coordinate of the world (exclusive)
     */
    public static int getMaxY() {
        return isExtendedHeight() ? EXTENDED_MAX_Y : LEGACY_MAX_Y;
    }

    /**
     * Total world height in blocks
     */
    public static int getTotalHeight() {
        return getMaxY() - getMinY();
    }

    /**
     * Number of chunk sections in Y direction.
     */
    public static int getSectionCount() {
        return isExtendedHeight() ? EXTENDED_SECTION_COUNT : LEGACY_SECTION_COUNT;
    }

    /**
     * Offset to apply when converting Y section coordinate to array index.
     */
    public static int getSectionOffset() {
        return isExtendedHeight() ? EXTENDED_SECTION_OFFSET : 0;
    }

    /**
     * Check if a Y coordinate is outside the build height
     */
    public static boolean isYOutOfBounds(int y) {
        return y < getMinY() || y >= getMaxY();
    }

    /**
     * Convert a Y section coordinate to an array index
     */
    public static int sectionToIndex(int sectionY) {
        return sectionY + getSectionOffset();
    }

    /**
     * Convert a block Y coordinate to a section array index
     */
    public static int blockYToSectionIndex(int blockY) {
        return (blockY >> 4) + getSectionOffset();
    }

    /**
     * Convert a section array index back to the block Y coordinate
     * of the bottom of that section.
     */
    public static int sectionToBlockY(int sectionIndex) {
        return (sectionIndex - getSectionOffset()) << 4;
    }

    /**
     * Get the minimum section Y coordinate.
     */
    public static int getMinSection() {
        return getMinY() >> 4;
    }

    /**
     * Get the maximum section Y coordinate (exclusive).
     */
    public static int getMaxSection() {
        return getMaxY() >> 4;
    }
}
