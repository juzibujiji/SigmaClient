package com.elfmcys.yesstevemodel;

import net.minecraft.util.ResourceLocation;

import javax.annotation.Nullable;
import java.nio.file.Path;

public final class OpenYsmModelEntry {
    public enum SourceType {
        BUILTIN,
        CUSTOM,
        AUTH
    }

    private final SourceType sourceType;
    private final String id;
    private final String name;
    private final int spec;
    @Nullable
    private final ResourceLocation ysmJsonResource;
    @Nullable
    private final Path path;

    private OpenYsmModelEntry(SourceType sourceType, String id, String name, int spec,
                              @Nullable ResourceLocation ysmJsonResource, @Nullable Path path) {
        this.sourceType = sourceType;
        this.id = id;
        this.name = name;
        this.spec = spec;
        this.ysmJsonResource = ysmJsonResource;
        this.path = path;
    }

    public static OpenYsmModelEntry builtin(String id, String name, int spec, ResourceLocation ysmJsonResource) {
        return new OpenYsmModelEntry(SourceType.BUILTIN, id, name, spec, ysmJsonResource, null);
    }

    public static OpenYsmModelEntry external(SourceType sourceType, String id, String name, int spec, Path path) {
        return new OpenYsmModelEntry(sourceType, id, name, spec, null, path);
    }

    public SourceType getSourceType() {
        return this.sourceType;
    }

    public String getId() {
        return this.id;
    }

    public String getName() {
        return this.name;
    }

    public int getSpec() {
        return this.spec;
    }

    @Nullable
    public ResourceLocation getYsmJsonResource() {
        return this.ysmJsonResource;
    }

    @Nullable
    public Path getPath() {
        return this.path;
    }
}
