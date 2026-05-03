package com.mentalfrostbyte.jello.util.game.network;

import com.google.common.collect.ImmutableSet;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.nbt.NBTDynamicOps;
import net.minecraft.network.PacketBuffer;
import net.minecraft.util.registry.DynamicRegistries;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public final class RegistryDataCompatibility {
    private static final Logger LOGGER = LogManager.getLogger("RegistryCompat");
    private static final Path REGISTRY_ERROR_LOG = Paths.get("logs", "via-registry-decode-errors.log");
    private static final Set<String> SUPPORTED_PACKET_REGISTRIES = ImmutableSet.of(
            "dimension_type",
            "minecraft:dimension_type",
            "worldgen/biome",
            "minecraft:worldgen/biome"
    );

    private RegistryDataCompatibility() {
    }

    public static DynamicRegistries.Impl readDynamicRegistries(PacketBuffer buffer) throws IOException {
        CompoundNBT registryData = buffer.func_244273_m();

        if (registryData == null) {
            throw new IOException("Missing dynamic registry data");
        }

        CompoundNBT compatibleData = filterUnsupportedRegistries(registryData);
        return decode(DynamicRegistries.Impl.registryCodec, compatibleData);
    }

    private static CompoundNBT filterUnsupportedRegistries(CompoundNBT registryData) {
        CompoundNBT compatibleData = registryData.copy();
        List<String> removedKeys = new ArrayList<>();

        for (String key : new ArrayList<>(compatibleData.keySet())) {
            if (!SUPPORTED_PACKET_REGISTRIES.contains(key)) {
                compatibleData.remove(key);
                removedKeys.add(key);
            }
        }

        if (!removedKeys.isEmpty()) {
            LOGGER.warn("[RegistryCompat] Dropped unsupported dynamic registry keys before 1.16 decode: {}", removedKeys);
        }

        return compatibleData;
    }

    private static <T> T decode(Codec<T> codec, CompoundNBT nbt) throws IOException {
        DataResult<T> result = codec.parse(NBTDynamicOps.INSTANCE, nbt);

        if (result.error().isPresent()) {
            String message = result.error().get().message();
            dumpDecodeFailure(message, nbt);
            throw new IOException("Failed to decode compatible dynamic registries: " + message
                    + " (full sanitized registry NBT written to " + REGISTRY_ERROR_LOG + ")");
        }

        return result.result().get();
    }

    private static void dumpDecodeFailure(String message, CompoundNBT nbt) {
        try {
            Files.createDirectories(REGISTRY_ERROR_LOG.getParent());
            String text = "==== " + Instant.now() + " ====\r\n"
                    + message + "\r\n"
                    + nbt + "\r\n\r\n";
            Files.write(REGISTRY_ERROR_LOG, text.getBytes(StandardCharsets.UTF_8),
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            LOGGER.error("[RegistryCompat] Dynamic registry decode failed; full sanitized NBT written to {}",
                    REGISTRY_ERROR_LOG);
        } catch (IOException exception) {
            LOGGER.error("[RegistryCompat] Dynamic registry decode failed and could not write {}", REGISTRY_ERROR_LOG,
                    exception);
        }
    }
}
