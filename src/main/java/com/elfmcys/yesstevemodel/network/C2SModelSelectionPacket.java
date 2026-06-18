package com.elfmcys.yesstevemodel.network;

import net.minecraft.network.PacketBuffer;

public final class C2SModelSelectionPacket {
    private final String modelId;
    private final String textureId;

    public C2SModelSelectionPacket(String modelId, String textureId) {
        this.modelId = modelId == null ? "" : modelId;
        this.textureId = textureId == null ? "" : textureId;
    }

    public String getModelId() {
        return this.modelId;
    }

    public String getTextureId() {
        return this.textureId;
    }

    public void encode(PacketBuffer buffer) {
        buffer.writeString(this.modelId, 256);
        buffer.writeString(this.textureId, 256);
    }

    public static C2SModelSelectionPacket decode(PacketBuffer buffer) {
        return new C2SModelSelectionPacket(buffer.readString(256), buffer.readString(256));
    }
}
