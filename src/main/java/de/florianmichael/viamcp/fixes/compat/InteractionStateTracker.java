package de.florianmichael.viamcp.fixes.compat;

import net.minecraft.item.ItemStack;

public final class InteractionStateTracker {
    private InteractionStateTracker() {
    }

    public static int nextSequence() {
        return InteractionSequence.next();
    }

    public static void setSequence(int sequenceIn) {
        InteractionSequence.set(sequenceIn);
    }

    public static void rememberLastUsedItem(ItemStack stack) {
        LocalInteractionState.rememberUsedItem(stack);
    }

    public static ItemStack lastUsedItem() {
        return LocalInteractionState.lastLocallyUsedItem();
    }
}
