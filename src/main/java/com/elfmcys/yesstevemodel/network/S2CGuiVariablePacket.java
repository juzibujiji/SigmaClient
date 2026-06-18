package com.elfmcys.yesstevemodel.network;

import net.minecraft.network.PacketBuffer;

public final class S2CGuiVariablePacket {
    private final int entityId;
    private final String modelId;
    private final String variableName;
    private final double value;

    public S2CGuiVariablePacket(int entityId, String modelId, String variableName, double value) {
        this.entityId = entityId;
        this.modelId = modelId == null ? "" : modelId;
        this.variableName = variableName == null ? "" : variableName;
        this.value = value;
    }

    public int getEntityId() {
        return this.entityId;
    }

    public String getModelId() {
        return this.modelId;
    }

    public String getVariableName() {
        return this.variableName;
    }

    public double getValue() {
        return this.value;
    }

    public void encode(PacketBuffer buffer) {
        buffer.writeVarInt(this.entityId);
        buffer.writeString(this.modelId, 256);
        buffer.writeString(this.variableName, 96);
        buffer.writeDouble(this.value);
    }

    public static S2CGuiVariablePacket decode(PacketBuffer buffer) {
        return new S2CGuiVariablePacket(buffer.readVarInt(), buffer.readString(256), buffer.readString(96), buffer.readDouble());
    }
}
