package com.elfmcys.yesstevemodel.network;

import net.minecraft.network.PacketBuffer;

public final class C2SPlayExtraAnimationPacket {
    private final String modelId;
    private final String animationName;

    public C2SPlayExtraAnimationPacket(String modelId, String animationName) {
        this.modelId = modelId == null ? "" : modelId;
        this.animationName = animationName == null ? "" : animationName;
    }

    public String getModelId() {
        return this.modelId;
    }

    public String getAnimationName() {
        return this.animationName;
    }

    public void encode(PacketBuffer buffer) {
        buffer.writeString(this.modelId, 256);
        buffer.writeString(this.animationName, 256);
    }

    public static C2SPlayExtraAnimationPacket decode(PacketBuffer buffer) {
        return new C2SPlayExtraAnimationPacket(buffer.readString(256), buffer.readString(256));
    }
}
