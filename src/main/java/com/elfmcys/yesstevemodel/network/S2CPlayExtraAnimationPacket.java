package com.elfmcys.yesstevemodel.network;

import net.minecraft.network.PacketBuffer;

public final class S2CPlayExtraAnimationPacket {
    private final int entityId;
    private final String modelId;
    private final String animationName;

    public S2CPlayExtraAnimationPacket(int entityId, String modelId, String animationName) {
        this.entityId = entityId;
        this.modelId = modelId == null ? "" : modelId;
        this.animationName = animationName == null ? "" : animationName;
    }

    public int getEntityId() {
        return this.entityId;
    }

    public String getModelId() {
        return this.modelId;
    }

    public String getAnimationName() {
        return this.animationName;
    }

    public void encode(PacketBuffer buffer) {
        buffer.writeVarInt(this.entityId);
        buffer.writeString(this.modelId, 256);
        buffer.writeString(this.animationName, 256);
    }

    public static S2CPlayExtraAnimationPacket decode(PacketBuffer buffer) {
        return new S2CPlayExtraAnimationPacket(buffer.readVarInt(), buffer.readString(256), buffer.readString(256));
    }
}
