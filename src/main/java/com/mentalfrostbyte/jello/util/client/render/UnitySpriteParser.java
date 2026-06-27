package com.mentalfrostbyte.jello.util.client.render;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.newdawn.slick.opengl.Texture;
import org.newdawn.slick.util.BufferedImageUtil;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Parses Unity sprite atlas JSON files and crops individual sprites from atlas
 * PNG images.
 * Ported from the Kotlin Manosaba Title Screen mod's UnitySpriteParser.
 */
public class UnitySpriteParser {

    private static final Gson gson = new Gson();

    public static class SpriteData {
        public final String name;
        public final float x, y, width, height;

        public SpriteData(String name, float x, float y, float width, float height) {
            this.name = name;
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
        }
    }

    public static class SpriteAtlas {
        public final String name;
        public final Map<String, SpriteData> sprites;

        public SpriteAtlas(String name, Map<String, SpriteData> sprites) {
            this.name = name;
            this.sprites = sprites;
        }
    }

    /**
     * Parse a Unity sprite atlas JSON string.
     */
    public static SpriteAtlas parseAtlas(String json) {
        JsonObject root = gson.fromJson(json, JsonObject.class);
        String atlasName = root.get("m_Name").getAsString();

        // Get sprite names
        JsonArray namesArray = root.getAsJsonArray("m_PackedSpriteNamesToIndex");
        List<String> names = new ArrayList<>();
        for (JsonElement e : namesArray) {
            names.add(e.getAsString());
        }

        // Get render data (texture rects)
        JsonArray renderDataArray = root.getAsJsonArray("m_RenderDataMap");
        Map<String, SpriteData> sprites = new HashMap<>();

        for (int i = 0; i < renderDataArray.size() && i < names.size(); i++) {
            String spriteName = names.get(i);
            JsonObject entry = renderDataArray.get(i).getAsJsonObject();
            JsonObject value = entry.getAsJsonObject("Value");
            JsonObject textureRect = value.getAsJsonObject("m_TextureRect");

            float x = textureRect.get("m_X").getAsFloat();
            float y = textureRect.get("m_Y").getAsFloat();
            float w = textureRect.get("m_Width").getAsFloat();
            float h = textureRect.get("m_Height").getAsFloat();

            sprites.put(spriteName, new SpriteData(spriteName, x, y, w, h));
        }

        return new SpriteAtlas(atlasName, sprites);
    }

    /**
     * Parse a Unity sprite atlas from a file path.
     */
    public static SpriteAtlas loadAtlasData(String jsonPath) {
        try (BufferedReader reader = new BufferedReader(new FileReader(jsonPath))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
            return parseAtlas(sb.toString());
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    /** Parse a Unity sprite atlas from an InputStream (e.g. a bundled classpath resource). */
    public static SpriteAtlas loadAtlasData(InputStream jsonStream) {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(jsonStream, java.nio.charset.StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
            return parseAtlas(sb.toString());
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Load an atlas image from a file path.
     */
    public static BufferedImage loadAtlasImage(String imagePath) {
        try {
            return ImageIO.read(new File(imagePath));
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    /** Load an atlas image from an InputStream (e.g. a bundled classpath resource). */
    public static BufferedImage loadAtlasImage(InputStream imageStream) {
        try {
            return ImageIO.read(imageStream);
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Crop a sprite from the atlas image.
     * Unity uses bottom-left origin, so we need to flip the Y coordinate.
     */
    public static BufferedImage cropSprite(BufferedImage atlas, SpriteData sprite) {
        int atlasHeight = atlas.getHeight();
        // Unity Y is from bottom, Java Y is from top, so flip
        int javaY = atlasHeight - (int) sprite.y - (int) sprite.height;
        int w = (int) sprite.width;
        int h = (int) sprite.height;

        // Clamp to prevent out-of-bounds
        int sx = Math.max(0, (int) sprite.x);
        int sy = Math.max(0, javaY);
        w = Math.min(w, atlas.getWidth() - sx);
        h = Math.min(h, atlasHeight - sy);

        if (w <= 0 || h <= 0)
            return null;

        return atlas.getSubimage(sx, sy, w, h);
    }

    /**
     * Convert a BufferedImage to a Slick2D Texture.
     */
    public static Texture toTexture(String name, BufferedImage image) {
        try {
            return BufferedImageUtil.getTexture(name, image);
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Load all named sprites from an atlas as Textures.
     *
     * @param jsonPath    Path to the atlas JSON file
     * @param imagePath   Path to the atlas PNG file
     * @param spriteNames Names of sprites to extract (null = extract all)
     * @return Map of sprite name to Texture
     */
    public static Map<String, Texture> loadSpritesAsTextures(String jsonPath, String imagePath,
            List<String> spriteNames) {
        return buildSprites(loadAtlasData(jsonPath), loadAtlasImage(imagePath), spriteNames);
    }

    /** Same as above, but reads the atlas JSON + image from InputStreams (e.g. classpath resources). */
    public static Map<String, Texture> loadSpritesAsTextures(InputStream jsonStream, InputStream imageStream,
            List<String> spriteNames) {
        return buildSprites(loadAtlasData(jsonStream), loadAtlasImage(imageStream), spriteNames);
    }

    private static Map<String, Texture> buildSprites(SpriteAtlas atlas, BufferedImage atlasImage,
            List<String> spriteNames) {
        if (atlas == null || atlasImage == null) {
            return new HashMap<>();
        }

        Map<String, Texture> result = new HashMap<>();
        Iterable<Map.Entry<String, SpriteData>> entries = (spriteNames != null)
                ? atlas.sprites.entrySet().stream()
                        .filter(e -> spriteNames.contains(e.getKey()))
                        .collect(java.util.stream.Collectors.toList())
                : atlas.sprites.entrySet();

        for (Map.Entry<String, SpriteData> entry : entries) {
            BufferedImage cropped = cropSprite(atlasImage, entry.getValue());
            if (cropped != null) {
                Texture tex = toTexture(entry.getKey(), cropped);
                if (tex != null) {
                    result.put(entry.getKey(), tex);
                }
            }
        }

        return result;
    }
}
