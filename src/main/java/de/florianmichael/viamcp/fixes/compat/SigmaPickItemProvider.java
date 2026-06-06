package de.florianmichael.viamcp.fixes.compat;

import com.viaversion.viaversion.api.connection.UserConnection;
import com.viaversion.viaversion.api.minecraft.BlockPosition;
import com.viaversion.viaversion.protocols.v1_21_2to1_21_4.provider.PickItemProvider;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public final class SigmaPickItemProvider extends PickItemProvider {
    private static final Logger LOGGER = LogManager.getLogger("ViaMCP-InteractionCompat");

    @Override
    public void pickItemFromBlock(UserConnection connection, BlockPosition blockPosition, boolean includeData) {
        LOGGER.warn("Target server requested PICK_ITEM_FROM_BLOCK, but this 1.16 client has no equivalent local pick-block context.");
    }

    @Override
    public void pickItemFromEntity(UserConnection connection, int entityId, boolean includeData) {
        LOGGER.warn("Target server requested PICK_ITEM_FROM_ENTITY for entity {}, but this 1.16 client has no equivalent local pick-block context.", entityId);
    }
}
