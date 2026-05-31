package com.elfmcys.yesstevemodel.network;

import net.minecraft.network.PacketBuffer;

public final class C2SStopExtraAnimationPacket {
    private final String modelId;

    public C2SStopExtraAnimationPacket(String modelId) {
        this.modelId = modelId == null ? "" : modelId;
    }

    public String getModelId() {
        return this.modelId;
    }

    public void encode(PacketBuffer buffer) {
        buffer.writeString(this.modelId, 256);
    }

    public static C2SStopExtraAnimationPacket decode(PacketBuffer buffer) {
        return new C2SStopExtraAnimationPacket(buffer.readString(256));
    }
}
