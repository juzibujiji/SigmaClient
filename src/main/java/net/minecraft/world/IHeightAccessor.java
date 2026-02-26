package net.minecraft.world;

import net.minecraft.util.math.SectionPos;

/**
 * Height accessor interface modeled after 1.17's LevelHeightAccessor.
 * Provides methods for accessing the world's vertical build limits.
 * For 1.16.5 extended height: minBuildHeight = -64, height = 384, maxBuildHeight = 320
 */
public interface IHeightAccessor {

    /**
     * @return The minimum Y coordinate that blocks can exist at (inclusive). Default: -64
     */
    default int getMinBuildHeight() {
        return -64;
    }

    /**
     * @return The total height of the world in blocks. Default: 384
     */
    int getHeight();

    /**
     * @return The maximum Y coordinate that blocks can exist at (exclusive). Equal to minBuildHeight + height.
     */
    default int getMaxBuildHeight() {
        return this.getMinBuildHeight() + this.getHeight();
    }

    /**
     * @return The total number of chunk sections (each16 blocks tall). Default: 24
     */
    default int getSectionsCount() {
        return this.getMaxSection() - this.getMinSection();
    }

    /**
     * @return The minimum section Y index (inclusive). For minBuildHeight=-64, this is -4.
     */
    default int getMinSection() {
        return SectionPos.toChunk(this.getMinBuildHeight());
    }

    /**
     * @return The maximum section Y index (exclusive). For maxBuildHeight=320, this is 20.
     */
    default int getMaxSection() {
        return SectionPos.toChunk(this.getMaxBuildHeight() - 1) + 1;
    }

    /**
     * Converts an absolute block Y coordinate to a zero-based section array index.
     * For example, with minBuildHeight=-64: y=-64 -> index0, y=0 -> index 4, y=255 -> index 19
     *
     * @param y The absolute block Y coordinate
     * @return The zero-based section index
     */
    default int getSectionIndex(int y) {
        return this.getSectionIndexFromSectionY(SectionPos.toChunk(y));
    }

    /**
     * Converts a section Y coordinate to a zero-based array index.
     * For example, with minSection=-4: sectionY=-4 -> 0, sectionY=0 -> 4
     *
     * @param sectionY The section Y coordinate
     * @return The zero-based array index
     */
    default int getSectionIndexFromSectionY(int sectionY) {
        return sectionY - this.getMinSection();
    }

    /**
     * Converts a zero-based section array index back to a section Y coordinate.
     *
     * @param index The zero-based array index
     * @return The section Y coordinate
     */
    default int getSectionYFromSectionIndex(int index) {
        return index + this.getMinSection();
    }

    /**
     * Check if the given Y coordinate is outside the build height range.
     *
     * @param y The block Y coordinate
     * @return true if y is outside [minBuildHeight, maxBuildHeight)
     */
    default boolean isOutsideBuildHeight(int y) {
        return y < this.getMinBuildHeight() || y >= this.getMaxBuildHeight();
    }

    /**
     * Check if the given block Y coordinate is inside the build height range.
     *
     * @param y The block Y coordinate
     * @return true if y is within the build range
     */
    default boolean isInsideBuildHeight(int y) {
        return !isOutsideBuildHeight(y);
    }
}