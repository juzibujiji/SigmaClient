package com.mentalfrostbyte.jello.util.game.world;

import com.mentalfrostbyte.jello.gui.base.JelloPortal;
import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;

/**
 * Helper class to dynamically determine world height parameters
 * based on the ViaVersion target protocol version.
 * 
 * IMPORTANT: isExtendedHeight() always returns false because ViaBackwards/ViaVersion
 * already translates 1.17+/1.18+ chunk data into 1.16.4 format (16 sections, Y=0-255).
 * The client must remain a standard 1.16.4 client and let ViaVersion handle all
 * protocol differences. Enabling extended height would cause the client to create
 * 24-section arrays and apply wrong section offsets to data that ViaBackwards has
 * already converted to 16-section format, resulting in broken chunk rendering,
 * wrong bitmask interpretation, and CH/SH metadata showing -1.
 */
public class WorldHeightHelper {

    /**
     * Whether the target server supports extended world height (1.17+)
     * Always returns false - ViaBackwards handles the translation.
     */
    public static boolean isExtendedHeight() {
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
        return 0;
    }

    /**
     * Maximum Y coordinate of the world (exclusive)
     */
    public static int getMaxY() {
        return 256;
    }

    /**
     * Total world height in blocks
     */
    public static int getTotalHeight() {
        return 256;
    }

    /**
     * Number of chunk sections in Y direction.
     * Always 16 for 1.16.4 client.
     */
    public static int getSectionCount() {
        return 16;
    }

    /**
     * Offset to apply when converting Y section coordinate to array index.
     * Always 0 for 1.16.4 client.
     */
    public static int getSectionOffset() {
        return 0;
    }

    /**
     * Check if a Y coordinate is outside the build height
     */
    public static boolean isYOutOfBounds(int y) {
        return y < 0 || y >= 256;
    }

    /**
     * Convert a Y section coordinate to an array index
     */
    public static int sectionToIndex(int sectionY) {
        return sectionY;
    }

    /**
     * Convert a block Y coordinate to a section array index
     */
    public static int blockYToSectionIndex(int blockY) {
        return blockY >> 4;
    }

    /**
     * Convert a section array index back to the block Y coordinate
     * of the bottom of that section.
     */
    public static int sectionToBlockY(int sectionIndex) {
        return sectionIndex << 4;
    }

    /**
     * Get the minimum section Y coordinate.
     * Always 0 for 1.16.4 client.
     */
    public static int getMinSection() {
        return 0;
    }

    /**
     * Get the maximum section Y coordinate (exclusive).
     * Always 16 for 1.16.4 client.
     */
    public static int getMaxSection() {
        return 16;
    }
}