package com.elfmcys.yesstevemodel.network;

import net.minecraft.network.PacketBuffer;

public final class S2CModelSelectionPacket {
    private final int entityId;
    private final String modelId;
    private final String textureId;

    public S2CModelSelectionPacket(int entityId, String modelId, String textureId) {
        this.entityId = entityId;
        this.modelId = modelId == null ? "" : modelId;
        this.textureId = textureId == null ? "" : textureId;
    }

    public int getEntityId() {
        return this.entityId;
    }

    public String getModelId() {
        return this.modelId;
    }

    public String getTextureId() {
        return this.textureId;
    }

    public void encode(PacketBuffer buffer) {
        buffer.writeVarInt(this.entityId);
        buffer.writeString(this.modelId, 256);
        buffer.writeString(this.textureId, 256);
    }

    public static S2CModelSelectionPacket decode(PacketBuffer buffer) {
        return new S2CModelSelectionPacket(buffer.readVarInt(), buffer.readString(256), buffer.readString(256));
    }
}
