package de.florianmichael.viamcp.fixes.compat;

import com.viaversion.viaversion.api.minecraft.item.DataItem;
import com.viaversion.viaversion.api.minecraft.item.Item;
import net.minecraft.item.ItemStack;

public final class ViaItemStackTranslator {
    private ViaItemStackTranslator() {
    }

    public static Item toViaItem(ItemStack stack) {
        return LocalItemTranslator.toViaItem(stack);
    }
}
