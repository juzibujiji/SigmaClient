package com.elfmcys.yesstevemodel.network;

import net.minecraft.network.PacketBuffer;

public final class C2SGuiVariablePacket {
    private final String modelId;
    private final String variableName;
    private final double value;

    public C2SGuiVariablePacket(String modelId, String variableName, double value) {
        this.modelId = modelId == null ? "" : modelId;
        this.variableName = variableName == null ? "" : variableName;
        this.value = value;
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
        buffer.writeString(this.modelId, 256);
        buffer.writeString(this.variableName, 96);
        buffer.writeDouble(this.value);
    }

    public static C2SGuiVariablePacket decode(PacketBuffer buffer) {
        return new C2SGuiVariablePacket(buffer.readString(256), buffer.readString(96), buffer.readDouble());
    }
}
