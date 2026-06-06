package de.florianmichael.viamcp.fixes.compat;

import com.viaversion.viaversion.api.connection.UserConnection;
import com.viaversion.viaversion.protocols.v1_15_2to1_16.provider.PlayerAbilitiesProvider;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.player.ClientPlayerEntity;

public final class SigmaPlayerAbilitiesProvider extends PlayerAbilitiesProvider {
    @Override
    public float getFlyingSpeed(UserConnection connection) {
        ClientPlayerEntity player = Minecraft.getInstance().player;
        return player != null ? player.abilities.getFlySpeed() : super.getFlyingSpeed(connection);
    }

    @Override
    public float getWalkingSpeed(UserConnection connection) {
        ClientPlayerEntity player = Minecraft.getInstance().player;
        return player != null ? player.abilities.getWalkSpeed() : super.getWalkingSpeed(connection);
    }
}
