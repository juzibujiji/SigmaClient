package de.florianmichael.viamcp.fixes.compat;

import com.viaversion.viaversion.api.connection.UserConnection;
import com.viaversion.viaversion.protocols.v1_18_2to1_19.provider.AckSequenceProvider;

public final class SigmaAckSequenceProvider extends AckSequenceProvider {
    @Override
    public void handleSequence(UserConnection connection, int sequence) {
        InteractionStateTracker.setSequence(Math.max(InteractionSequence.get(), sequence));
    }
}
