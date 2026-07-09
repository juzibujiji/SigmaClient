/*
 * Ported from OpenSSNGScaffoldAndClutch (https://github.com/zyyzs/OpenSSNGScaffoldAndClutch)
 * Original: dev.southside.utils.player.SlotUtils (referenced by upstream sources; not
 * published in the upstream repo — reconstructed from usage: SlotUtils.OFFHAND slot id and
 * SlotUtils.isGoodForBridging(Item)).
 *
 */
package com.mentalfrostbyte.jello.southside.utils.player;

import com.mentalfrostbyte.jello.southside.utils.SSInventoryUtil;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

public final class SSSlotUtils {
    /** Offhand slot id in the player container (1.16.5 survival inventory). */
    public static final int OFFHAND = 45;

    private SSSlotUtils() {
    }

    /**
     * Basic sanity filter for bridging items; the block-specific blacklist lives in the
     * scaffold's own isValid()/invalidBlocks (mirroring upstream's split between the two).
     */
    public static boolean isGoodForBridging(Item item) {
        if (!(item instanceof BlockItem)) {
            return false;
        }
        return SSInventoryUtil.isFullBlock(new ItemStack(item));
    }
}
