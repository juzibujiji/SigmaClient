package com.elfmcys.yesstevemodel.network;

import net.minecraft.network.PacketBuffer;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public final class S2CExtraEntityModelPacket {
    private static final int MAX_VARIABLES = 64;

    private final int entityId;
    private final String modelId;
    private final String textureId;
    private final Map<String, Double> variables;

    public S2CExtraEntityModelPacket(int entityId, String modelId, String textureId) {
        this(entityId, modelId, textureId, Collections.emptyMap());
    }

    public S2CExtraEntityModelPacket(int entityId, String modelId, String textureId, Map<String, Double> variables) {
        this.entityId = entityId;
        this.modelId = modelId == null ? "" : modelId;
        this.textureId = textureId == null ? "" : textureId;
        this.variables = copyVariables(variables);
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

    public Map<String, Double> getVariables() {
        return this.variables;
    }

    public void encode(PacketBuffer buffer) {
        buffer.writeVarInt(this.entityId);
        buffer.writeString(this.modelId, 256);
        buffer.writeString(this.textureId, 256);
        buffer.writeVarInt(this.variables.size());
        for (Map.Entry<String, Double> entry : this.variables.entrySet()) {
            buffer.writeString(entry.getKey(), 64);
            buffer.writeDouble(entry.getValue());
        }
    }

    public static S2CExtraEntityModelPacket decode(PacketBuffer buffer) {
        int entityId = buffer.readVarInt();
        String modelId = buffer.readString(256);
        String textureId = buffer.readString(256);
        int count = buffer.readVarInt();
        if (count < 0 || count > MAX_VARIABLES) {
            throw new IllegalArgumentException("Invalid extra entity variable count: " + count);
        }
        Map<String, Double> variables = new LinkedHashMap<>();
        for (int i = 0; i < count; i++) {
            variables.put(buffer.readString(64), buffer.readDouble());
        }
        return new S2CExtraEntityModelPacket(entityId, modelId, textureId, variables);
    }

    private static Map<String, Double> copyVariables(Map<String, Double> variables) {
        if (variables == null || variables.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<String, Double> copy = new LinkedHashMap<>();
        for (Map.Entry<String, Double> entry : variables.entrySet()) {
            if (copy.size() >= MAX_VARIABLES) {
                break;
            }
            if (entry.getKey() != null && entry.getValue() != null && Double.isFinite(entry.getValue())) {
                copy.put(entry.getKey(), entry.getValue());
            }
        }
        return Collections.unmodifiableMap(copy);
    }
}
