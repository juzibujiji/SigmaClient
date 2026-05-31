package com.elfmcys.yesstevemodel.network;

import net.minecraft.network.PacketBuffer;

public final class S2CStopExtraAnimationPacket {
    private final int entityId;
    private final String modelId;

    public S2CStopExtraAnimationPacket(int entityId, String modelId) {
        this.entityId = entityId;
        this.modelId = modelId == null ? "" : modelId;
    }

    public int getEntityId() {
        return this.entityId;
    }

    public String getModelId() {
        return this.modelId;
    }

    public void encode(PacketBuffer buffer) {
        buffer.writeVarInt(this.entityId);
        buffer.writeString(this.modelId, 256);
    }

    public static S2CStopExtraAnimationPacket decode(PacketBuffer buffer) {
        return new S2CStopExtraAnimationPacket(buffer.readVarInt(), buffer.readString(256));
    }
}
