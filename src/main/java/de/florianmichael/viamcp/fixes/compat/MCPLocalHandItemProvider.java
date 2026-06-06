package de.florianmichael.viamcp.fixes.compat;

import com.viaversion.viaversion.api.connection.UserConnection;
import com.viaversion.viaversion.api.minecraft.item.Item;
import com.viaversion.viaversion.protocols.v1_8to1_9.provider.HandItemProvider;

public final class MCPLocalHandItemProvider extends HandItemProvider {
    @Override
    public Item getHandItem(UserConnection connection) {
        return LocalInteractionState.lastLocallyUsedViaItem();
    }
}
