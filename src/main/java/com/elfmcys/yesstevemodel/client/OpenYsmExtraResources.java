package com.elfmcys.yesstevemodel.client;

import net.minecraft.util.ResourceLocation;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public final class OpenYsmExtraResources {
    public static final OpenYsmExtraResources EMPTY = new OpenYsmExtraResources(
            Collections.emptyMap(), Collections.emptyMap(), Collections.emptyMap(), Collections.emptyMap());

    private final Map<String, byte[]> sounds;
    private final Map<String, String> functions;
    private final Map<String, Map<String, String>> translations;
    private final Map<String, ImageResource> images;
    private final Map<String, ResourceLocation> imageLocations;

    public OpenYsmExtraResources(Map<String, byte[]> sounds, Map<String, String> functions,
                                 Map<String, Map<String, String>> translations,
                                 Map<String, ImageResource> images) {
        this.sounds = immutableByteMap(sounds);
        this.functions = immutableStringMap(functions);
        this.translations = immutableNestedStringMap(translations);
        this.images = images == null || images.isEmpty()
                ? Collections.emptyMap()
                : Collections.unmodifiableMap(new LinkedHashMap<>(images));
        Map<String, ResourceLocation> locations = new LinkedHashMap<>();
        for (Map.Entry<String, ImageResource> entry : this.images.entrySet()) {
            locations.put(entry.getKey(), entry.getValue().getLocation());
        }
        this.imageLocations = Collections.unmodifiableMap(locations);
    }

    public Map<String, byte[]> getSounds() {
        return this.sounds;
    }

    public Map<String, String> getFunctions() {
        return this.functions;
    }

    public Map<String, Map<String, String>> getTranslations() {
        return this.translations;
    }

    public Map<String, ResourceLocation> getImages() {
        return this.imageLocations;
    }

    public Map<String, ImageResource> getImageResources() {
        return this.images;
    }

    public ResourceLocation getImage(String name) {
        ImageResource image = this.images.get(name);
        return image == null ? null : image.getLocation();
    }

    public ImageResource getImageResource(String name) {
        return this.images.get(name);
    }

    public boolean hasImage(String name) {
        return this.images.containsKey(name);
    }

    private static Map<String, byte[]> immutableByteMap(Map<String, byte[]> source) {
        if (source == null || source.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<String, byte[]> copy = new LinkedHashMap<>();
        for (Map.Entry<String, byte[]> entry : source.entrySet()) {
            if (entry.getKey() != null && !entry.getKey().isEmpty() && entry.getValue() != null) {
                copy.put(entry.getKey(), Arrays.copyOf(entry.getValue(), entry.getValue().length));
            }
        }
        return Collections.unmodifiableMap(copy);
    }

    private static Map<String, String> immutableStringMap(Map<String, String> source) {
        if (source == null || source.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<String, String> copy = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : source.entrySet()) {
            if (entry.getKey() != null && !entry.getKey().isEmpty() && entry.getValue() != null) {
                copy.put(entry.getKey(), entry.getValue());
            }
        }
        return Collections.unmodifiableMap(copy);
    }

    private static Map<String, Map<String, String>> immutableNestedStringMap(Map<String, Map<String, String>> source) {
        if (source == null || source.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<String, Map<String, String>> copy = new LinkedHashMap<>();
        for (Map.Entry<String, Map<String, String>> entry : source.entrySet()) {
            if (entry.getKey() != null && !entry.getKey().isEmpty() && entry.getValue() != null) {
                copy.put(entry.getKey(), immutableStringMap(entry.getValue()));
            }
        }
        return Collections.unmodifiableMap(copy);
    }

    public static final class ImageResource {
        private final ResourceLocation location;
        private final int width;
        private final int height;

        public ImageResource(ResourceLocation location, int width, int height) {
            this.location = location;
            this.width = Math.max(1, width);
            this.height = Math.max(1, height);
        }

        public ResourceLocation getLocation() {
            return this.location;
        }

        public int getWidth() {
            return this.width;
        }

        public int getHeight() {
            return this.height;
        }
    }
}
