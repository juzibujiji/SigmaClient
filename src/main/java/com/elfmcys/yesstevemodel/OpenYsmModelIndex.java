package com.elfmcys.yesstevemodel;

import com.elfmcys.yesstevemodel.resource.YSMBinaryDeserializer;
import com.elfmcys.yesstevemodel.resource.pojo.RawYsmModel;
import rip.ysm.security.YsmCrypt;
import com.google.common.collect.ImmutableList;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.resources.IResource;
import net.minecraft.resources.IResourceManager;
import net.minecraft.util.ResourceLocation;
import org.apache.commons.io.FilenameUtils;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public final class OpenYsmModelIndex {
    private static final int MAX_EXTERNAL_DEPTH = 16;

    private volatile List<OpenYsmModelEntry> entries = ImmutableList.of();

    public List<OpenYsmModelEntry> getEntries() {
        return this.entries;
    }

    public Optional<OpenYsmModelEntry> findById(String id) {
        if (id == null) {
            return Optional.empty();
        }

        return this.entries.stream().filter(entry -> entry.getId().equals(id)).findFirst();
    }

    public void reload(IResourceManager resourceManager, Path configDirectory) {
        List<OpenYsmModelEntry> discovered = new ArrayList<>();
        discovered.addAll(loadBuiltinModels(resourceManager));
        discovered.addAll(loadExternalModels(configDirectory.resolve("custom"), OpenYsmModelEntry.SourceType.CUSTOM));
        discovered.addAll(loadExternalModels(configDirectory.resolve("auth"), OpenYsmModelEntry.SourceType.AUTH));
        discovered.sort(Comparator.comparing(OpenYsmModelEntry::getSourceType).thenComparing(OpenYsmModelEntry::getId));
        this.entries = ImmutableList.copyOf(discovered);
    }

    private List<OpenYsmModelEntry> loadBuiltinModels(IResourceManager resourceManager) {
        List<OpenYsmModelEntry> models = new ArrayList<>();
        Collection<ResourceLocation> locations = resourceManager.getAllResourceLocations("builtin", path -> path.equals("ysm.json"));

        for (ResourceLocation location : locations) {
            if (!YesSteveModel.MOD_ID.equals(location.getNamespace())) {
                continue;
            }

            try (IResource resource = resourceManager.getResource(location);
                 Reader reader = new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8)) {
                JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
                String id = toBuiltinId(location);
                models.add(OpenYsmModelEntry.builtin(id, readName(json, id), readSpec(json), location));
            } catch (RuntimeException | IOException exception) {
                YesSteveModel.LOGGER.warn("[YSM] Skipping invalid builtin model {}", location, exception);
            }
        }

        return models;
    }

    private List<OpenYsmModelEntry> loadExternalModels(Path root, OpenYsmModelEntry.SourceType sourceType) {
        List<OpenYsmModelEntry> models = new ArrayList<>();
        Path normalizedRoot = root.toAbsolutePath().normalize();
        if (!Files.isDirectory(normalizedRoot)) {
            return models;
        }

        try (Stream<Path> stream = Files.walk(normalizedRoot, MAX_EXTERNAL_DEPTH)) {
            stream.filter(Files::isRegularFile)
                    .map(Path::toAbsolutePath)
                    .map(Path::normalize)
                    .filter(path -> path.startsWith(normalizedRoot))
                    .filter(this::isExternalModelMarker)
                    .forEach(path -> addExternalModel(models, normalizedRoot, sourceType, path));
        } catch (IOException exception) {
            YesSteveModel.LOGGER.warn("[YSM] Failed to scan {}", normalizedRoot, exception);
        }

        return models;
    }

    private boolean isExternalModelMarker(Path path) {
        String fileName = path.getFileName().toString().toLowerCase(Locale.ROOT);
        return fileName.equals("ysm.json") || fileName.endsWith(".ysm") || fileName.endsWith(".zip");
    }

    private void addExternalModel(List<OpenYsmModelEntry> models, Path root, OpenYsmModelEntry.SourceType sourceType, Path path) {
        String id = FilenameUtils.separatorsToUnix(root.relativize(path).toString());
        String name = FilenameUtils.getBaseName(path.getFileName().toString());
        int spec = -1;

        String fileName = path.getFileName().toString();
        if (fileName.equalsIgnoreCase("ysm.json")) {
            Path modelRoot = path.getParent();
            id = FilenameUtils.separatorsToUnix(root.relativize(modelRoot).toString());
            try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
                JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
                name = readName(json, id);
                spec = readSpec(json);
            } catch (RuntimeException | IOException exception) {
                YesSteveModel.LOGGER.warn("[YSM] Skipping invalid model descriptor {}", path, exception);
                return;
            }
        } else if (fileName.toLowerCase(Locale.ROOT).endsWith(".zip")) {
            try (ZipFile zipFile = new ZipFile(path.toFile())) {
                ZipEntry ysmJson = OpenYsmArchiveUtil.findYsmJson(zipFile);
                if (ysmJson == null) {
                    throw new IOException("Missing ysm.json");
                }

                try (Reader reader = new InputStreamReader(zipFile.getInputStream(ysmJson), StandardCharsets.UTF_8)) {
                    JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
                    name = readName(json, id);
                    spec = readSpec(json);
                }
            } catch (RuntimeException | IOException exception) {
                YesSteveModel.LOGGER.warn("[YSM] Skipping invalid model archive {}", path, exception);
                return;
            }
        } else if (fileName.toLowerCase(Locale.ROOT).endsWith(".ysm")) {
            try {
                byte[] fileBytes = Files.readAllBytes(path);
                if (fileBytes.length > 50 * 1024 * 1024) throw new IOException("File too large");
                byte[] decompressed = YsmCrypt.decryptYsmFile(fileBytes);
                try (YSMBinaryDeserializer deserializer = new YSMBinaryDeserializer(decompressed)) {
                    RawYsmModel rawModel = deserializer.deserialize();
                    if (rawModel.metadata != null && !org.apache.commons.lang3.StringUtils.isBlank(rawModel.metadata.name)) {
                        name = rawModel.metadata.name;
                    }
                    spec = rawModel.formatVersion;
                }
            } catch (Exception exception) {
                YesSteveModel.LOGGER.warn("[YSM] Skipping invalid model package {}", path, exception);
                return;
            }
        }

        models.add(OpenYsmModelEntry.external(sourceType, id, name, spec, path));
    }

    private static String toBuiltinId(ResourceLocation location) {
        String path = location.getPath();
        if (path.startsWith("builtin/")) {
            path = path.substring("builtin/".length());
        }
        if (path.endsWith("/ysm.json")) {
            path = path.substring(0, path.length() - "/ysm.json".length());
        }
        return path;
    }

    private static int readSpec(JsonObject json) {
        return json.has("spec") && json.get("spec").isJsonPrimitive() ? json.get("spec").getAsInt() : -1;
    }

    private static String readName(JsonObject json, String fallback) {
        if (json.has("metadata") && json.get("metadata").isJsonObject()) {
            JsonObject metadata = json.getAsJsonObject("metadata");
            if (metadata.has("name") && metadata.get("name").isJsonPrimitive()) {
                return metadata.get("name").getAsString();
            }
        }
        return fallback;
    }
}
