package com.elfmcys.yesstevemodel;

public final class OpenYsmTextureOption {
    private final String id;
    private final String path;
    private final String normalPath;
    private final String specularPath;

    public OpenYsmTextureOption(String id, String path) {
        this(id, path, "", "");
    }

    public OpenYsmTextureOption(String id, String path, String normalPath, String specularPath) {
        this.id = id;
        this.path = path;
        this.normalPath = normalPath == null ? "" : normalPath;
        this.specularPath = specularPath == null ? "" : specularPath;
    }

    public String getId() {
        return this.id;
    }

    public String getPath() {
        return this.path;
    }

    public String getNormalPath() {
        return this.normalPath;
    }

    public String getSpecularPath() {
        return this.specularPath;
    }

    public boolean hasShaderLayers() {
        return !this.normalPath.isEmpty() || !this.specularPath.isEmpty();
    }
}
