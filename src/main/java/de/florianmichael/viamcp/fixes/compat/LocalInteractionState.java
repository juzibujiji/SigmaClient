package de.florianmichael.viamcp.fixes.compat;

import com.viaversion.viaversion.api.connection.StorableObject;
import com.viaversion.viaversion.api.connection.UserConnection;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Hand;

import java.util.concurrent.ArrayBlockingQueue;

public final class LocalInteractionState {
    private static final int MAX_PENDING_USES = 64;
    private static volatile ItemStack lastLocallyUsedItem = ItemStack.EMPTY;

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

    /**
     * Captures the item for one packet that will actually enter ViaVersion.
     * NetworkManager may enqueue the packet onto Netty after the render thread
     * returns, so a single global "last item" slot is not a safe hand-off.
     */
    public static boolean enqueueCurrentHand(UserConnection connection, Hand hand) {
        Minecraft mc = Minecraft.getInstance();
        if (connection == null || mc.player == null || hand == null) {
            return false;
        }

        ItemStack snapshot = mc.player.getHeldItem(hand).copy();
        PendingUsedItems pending = pendingItems(connection);
        if (!pending.items.offer(snapshot)) {
            // The oldest item belongs to the oldest packet already queued on
            // Netty. Reject the newest packet instead of breaking FIFO pairing.
            return false;
        }

        lastLocallyUsedItem = snapshot.copy();
        return true;
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

    public static com.viaversion.viaversion.api.minecraft.item.Item pollViaItem(UserConnection connection) {
        if (connection == null) {
            return null;
        }

        PendingUsedItems pending = connection.get(PendingUsedItems.class);
        if (pending == null) {
            return null;
        }

        ItemStack snapshot = pending.items.poll();
        return LocalItemTranslator.toViaItem(snapshot);
    }

    private static PendingUsedItems pendingItems(UserConnection connection) {
        PendingUsedItems pending = connection.get(PendingUsedItems.class);
        if (pending != null) {
            return pending;
        }

        synchronized (connection) {
            pending = connection.get(PendingUsedItems.class);
            if (pending == null) {
                pending = new PendingUsedItems();
                connection.put(pending);
            }
            return pending;
        }
    }

    private static final class PendingUsedItems implements StorableObject {
        private final ArrayBlockingQueue<ItemStack> items = new ArrayBlockingQueue<>(MAX_PENDING_USES);
    }
}
