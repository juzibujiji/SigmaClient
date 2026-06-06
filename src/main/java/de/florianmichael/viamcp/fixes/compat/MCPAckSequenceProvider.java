package de.florianmichael.viamcp.fixes.compat;

import com.viaversion.viaversion.api.connection.UserConnection;
import com.viaversion.viaversion.protocols.v1_18_2to1_19.provider.AckSequenceProvider;

public final class MCPAckSequenceProvider extends AckSequenceProvider {
    @Override
    public void handleSequence(UserConnection connection, int sequence) {
        InteractionSequence.set(Math.max(InteractionSequence.get(), sequence));
    }
}
