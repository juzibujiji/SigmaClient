package de.florianmichael.viamcp.fixes.compat;

import com.viaversion.viaversion.api.platform.providers.ViaProviders;
public final class ViaMCPProviders {
    private ViaMCPProviders() {
    }

    public static void register(ViaProviders providers) {
        InteractionProviders.register(providers);
    }
}
