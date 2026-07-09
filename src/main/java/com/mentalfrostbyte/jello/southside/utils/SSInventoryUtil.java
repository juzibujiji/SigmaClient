/*
 * Ported from OpenSSNGScaffoldAndClutch (https://github.com/zyyzs/OpenSSNGScaffoldAndClutch)
 * Original: dev.southside.utils.InventoryUtil (referenced by upstream sources; not published
 * in the upstream repo — reconstructed from usage: InventoryUtil.isFullBlock(ItemStack) and
 * InventoryUtil.swap(containerSlot, hotbarIndex)).
 *
 */
package com.mentalfrostbyte.jello.southside.utils;

import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.inventory.container.ClickType;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;

public final class SSInventoryUtil {
    private SSInventoryUtil() {
    }

    /**
     * True when the stack is a block item whose default state is a solid (full-cube-ish)
     * block — the upstream predicate for "can bridge with this".
     */
    public static boolean isFullBlock(ItemStack stack) {
        if (stack == null || stack.isEmpty() || !(stack.getItem() instanceof BlockItem)) {
            return false;
        }
        Block block = ((BlockItem) stack.getItem()).getBlock();
        return block.getDefaultState().isSolid();
    }

    /**
     * SWAP window click: swaps container slot {@code slot} with hotbar index
     * {@code hotbarIndex} (0-8) — the 1.16.5 equivalent of the upstream inventory swap.
     */
    public static void swap(int slot, int hotbarIndex) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.playerController == null) {
            return;
        }
        mc.playerController.windowClick(mc.player.container.windowId, slot, hotbarIndex, ClickType.SWAP, mc.player);
    }
}
