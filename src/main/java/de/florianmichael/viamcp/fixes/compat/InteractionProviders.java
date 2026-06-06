package de.florianmichael.viamcp.fixes.compat;

import com.viaversion.viaversion.api.platform.providers.ViaProviders;
import com.viaversion.viaversion.protocols.v1_15_2to1_16.provider.PlayerAbilitiesProvider;
import com.viaversion.viaversion.protocols.v1_18_2to1_19.provider.AckSequenceProvider;
import com.viaversion.viaversion.protocols.v1_21_2to1_21_4.provider.PickItemProvider;
import com.viaversion.viaversion.protocols.v1_8to1_9.provider.HandItemProvider;

public final class InteractionProviders {
    private InteractionProviders() {
    }

    public static void register(ViaProviders providers) {
        providers.use(AckSequenceProvider.class, new MCPAckSequenceProvider());
        providers.use(HandItemProvider.class, new MCPLocalHandItemProvider());
        providers.use(PlayerAbilitiesProvider.class, new MCPLocalPlayerAbilitiesProvider());
        providers.use(PickItemProvider.class, new MCPPickItemProvider());
    }
}
