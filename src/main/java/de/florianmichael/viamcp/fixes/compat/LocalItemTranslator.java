package de.florianmichael.viamcp.fixes.compat;

import com.viaversion.viaversion.api.minecraft.item.DataItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

public final class LocalItemTranslator {
    private LocalItemTranslator() {
    }

    public static com.viaversion.viaversion.api.minecraft.item.Item toViaItem(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return null;
        }

        int identifier = Item.getIdFromItem(stack.getItem());
        byte amount = (byte) Math.max(1, Math.min(127, stack.getCount()));
        short data = (short) Math.max(0, Math.min(Short.MAX_VALUE, stack.getDamage()));
        return new DataItem(identifier, amount, data, null);
    }
}
