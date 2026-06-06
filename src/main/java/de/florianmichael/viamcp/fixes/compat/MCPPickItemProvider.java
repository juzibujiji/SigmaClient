package de.florianmichael.viamcp.fixes.compat;

import com.viaversion.viaversion.api.connection.UserConnection;
import com.viaversion.viaversion.api.minecraft.BlockPosition;
import com.viaversion.viaversion.protocols.v1_21_2to1_21_4.provider.PickItemProvider;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public final class MCPPickItemProvider extends PickItemProvider {
    private static final Logger LOGGER = LogManager.getLogger("ViaMCP-PickItem");
    private static int warnedBlockPick;
    private static int warnedEntityPick;

    @Override
    public void pickItemFromBlock(UserConnection connection, BlockPosition position, boolean includeData) {
        if (++warnedBlockPick == 1) {
            LOGGER.warn("Ignoring unsupported 1.21.2+ pick-item-from-block fallback at {}", position);
        }
    }

    @Override
    public void pickItemFromEntity(UserConnection connection, int entityId, boolean includeData) {
        if (++warnedEntityPick == 1) {
            LOGGER.warn("Ignoring unsupported 1.21.2+ pick-item-from-entity fallback for entity {}", entityId);
        }
    }
}
