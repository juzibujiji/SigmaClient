package de.florianmichael.viamcp.fixes.compat;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Hand;

public final class LocalInteractionState {
    private static ItemStack lastLocallyUsedItem = ItemStack.EMPTY;

    private LocalInteractionState() {
    }

    public static void rememberUsedItem(PlayerEntity player, Hand hand) {
        if (player == null || hand == null) {
            lastLocallyUsedItem = ItemStack.EMPTY;
            return;
        }

        rememberUsedItem(player.getHeldItem(hand));
    }

    public static void rememberCurrentHand(Hand hand) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            rememberUsedItem(mc.player, hand);
        }
    }

    public static void rememberUsedItem(ItemStack stack) {
        lastLocallyUsedItem = stack == null ? ItemStack.EMPTY : stack.copy();
    }

    public static ItemStack lastLocallyUsedItem() {
        return lastLocallyUsedItem.copy();
    }

    public static com.viaversion.viaversion.api.minecraft.item.Item lastLocallyUsedViaItem() {
        return LocalItemTranslator.toViaItem(lastLocallyUsedItem);
    }
}
