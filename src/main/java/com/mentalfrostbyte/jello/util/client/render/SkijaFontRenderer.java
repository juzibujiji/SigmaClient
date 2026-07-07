package com.mentalfrostbyte.jello.util.client.render;

import com.mentalfrostbyte.jello.util.game.render.RenderUtil;
import com.mentalfrostbyte.jello.util.game.render.SafeTextureUploader;
import io.github.humbleui.skija.Bitmap;
import io.github.humbleui.skija.Canvas;
import io.github.humbleui.skija.Color;
import io.github.humbleui.skija.ColorAlphaType;
import io.github.humbleui.skija.ColorType;
import io.github.humbleui.skija.Data;
import io.github.humbleui.skija.Font;
import io.github.humbleui.skija.FontEdging;
import io.github.humbleui.skija.FontHinting;
import io.github.humbleui.skija.FontMetrics;
import io.github.humbleui.skija.FontStyle;
import io.github.humbleui.skija.ImageInfo;
import io.github.humbleui.skija.Paint;
import io.github.humbleui.skija.Typeface;
import org.newdawn.slick.opengl.Texture;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Skija-backed text renderer for MusicPlayer.
 *
 * Text is rasterized by Skija into CPU memory, uploaded as regular GL textures,
 * and then drawn through the existing GUI renderer. This avoids Skija's OpenGL
 * backend taking over Minecraft's framebuffer state mid-GUI render.
 */
public final class SkijaFontRenderer {
    private static final int MAX_CACHE_SIZE = 256;
    private static final String ENGLISH_FONT_RESOURCE =
            "assets/minecraft/com/mentalfrostbyte/gui/resources/font/helvetica-neue-light.ttf";
    private static final String NON_ENGLISH_FONT_RESOURCE =
            "assets/minecraft/com/mentalfrostbyte/gui/resources/font/HarmonyOS_Sans_SC_Medium.ttf";
    private static final String NON_ENGLISH_LIGHT_FONT_RESOURCE =
            "assets/minecraft/com/mentalfrostbyte/gui/resources/font/HarmonyOS_Sans_SC_Light.ttf";
    private static final String NON_ENGLISH_REGULAR_FONT_RESOURCE =
            "assets/minecraft/com/mentalfrostbyte/gui/resources/font/HarmonyOS_Sans_SC_Regular.ttf";

    /** GUI text weight; only non-English text is routed to Skija, mapped to a HarmonyOS weight. */
    public enum FontWeight { LIGHT, MEDIUM, REGULAR }

    private static Typeface englishTypeface;
    private static Typeface nonEnglishLightTypeface;
    private static Typeface nonEnglishMediumTypeface;
    private static Typeface nonEnglishRegularTypeface;
    private static Typeface systemNonEnglishTypeface;
    private static Typeface customTypeface;
    private static String customTypefaceKey = "";
    private static boolean initialized = false;
    private static int currentWidth = 0;
    private static int currentHeight = 0;

    private static final Map<String, CachedText> textCache =
            new LinkedHashMap<String, CachedText>(MAX_CACHE_SIZE, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, CachedText> eldest) {
                    if (size() > MAX_CACHE_SIZE) {
                        eldest.getValue().dispose();
                        return true;
                    }
                    return false;
                }
            };

    private SkijaFontRenderer() {
    }

    public static void init(int width, int height) {
        currentWidth = width;
        currentHeight = height;
        loadFonts();
        initialized = englishTypeface != null || nonEnglishLightTypeface != null
                || nonEnglishMediumTypeface != null || systemNonEnglishTypeface != null;
    }

    public static boolean ensureInitialized(int width, int height) {
        if (!initialized || width != currentWidth || height != currentHeight) {
            init(width, height);
        }
        return initialized;
    }

    /** Lazily loads fonts (CPU-only, render-thread safe) without needing screen dimensions. */
    public static boolean ensureInitialized() {
        if (!initialized) {
            init(currentWidth, currentHeight);
        }
        return initialized;
    }

    public static void beginFrame(int width, int height) {
        ensureInitialized(width, height);
    }

    public static void endFrame() {
        // Raster-backed renderer: no Skija GL surface to flush.
    }

    public static boolean setCustomTypeface(byte[] fontBytes, String key) {
        if (fontBytes == null || fontBytes.length == 0 || key == null || key.isEmpty()) {
            return false;
        }
        if (customTypeface != null && key.equals(customTypefaceKey)) {
            return true;
        }

        Typeface newTypeface = Typeface.makeFromData(Data.makeFromBytes(fontBytes));
        if (newTypeface == null) {
            return false;
        }

        customTypeface = newTypeface;
        customTypefaceKey = key;
        clearTextCache();
        return true;
    }

    public static void clearCustomTypeface() {
        if (customTypeface == null && customTypefaceKey.isEmpty()) {
            return;
        }
        customTypeface = null;
        customTypefaceKey = "";
        clearTextCache();
    }

    public static boolean hasCustomTypeface(String key) {
        return customTypeface != null && key != null && key.equals(customTypefaceKey);
    }

    public static boolean hasCustomTypeface() {
        return customTypeface != null;
    }

    public static void drawText(String text, float x, float y, float fontSize, int color) {
        drawTextInternal(text, x, y, fontSize, color, englishTypeface, null, false);
    }

    public static void drawCustomText(String text, float x, float y, float fontSize, int color) {
        drawTextInternal(text, x, y, fontSize, color, customTypeface, customTypeface, false);
    }

    /** GUI text: weight-aware faces, top-aligned at {@code yTop} to match the legacy TrueTypeFont layout. */
    public static void drawGuiText(String text, float x, float yTop, float fontSize, int color, FontWeight weight) {
        // Skija only renders non-English (HarmonyOS); English stays on the original TrueTypeFont.
        Typeface face = cjkFaceFor(weight);
        drawTextInternal(text, x, yTop, fontSize, color, face, face, true);
    }

    private static void drawTextInternal(String text, float x, float y, float fontSize, int color,
                                         Typeface asciiFace, Typeface fixedCjkFace, boolean topAligned) {
        if (!initialized || text == null || text.isEmpty()) {
            return;
        }

        float currentX = x;
        StringBuilder asciiBuffer = new StringBuilder();
        StringBuilder nonEnglishBuffer = new StringBuilder();

        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (isAscii(c)) {
                if (nonEnglishBuffer.length() > 0) {
                    currentX = drawTextSegment(nonEnglishBuffer.toString(), currentX, y, fontSize, color,
                            resolveCjkFace(fixedCjkFace, nonEnglishBuffer.toString()), "cjk", topAligned);
                    nonEnglishBuffer.setLength(0);
                }
                asciiBuffer.append(c);
            } else {
                if (asciiBuffer.length() > 0) {
                    currentX = drawTextSegment(asciiBuffer.toString(), currentX, y, fontSize, color, asciiFace, "ascii", topAligned);
                    asciiBuffer.setLength(0);
                }
                nonEnglishBuffer.append(c);
            }
        }

        if (asciiBuffer.length() > 0) {
            drawTextSegment(asciiBuffer.toString(), currentX, y, fontSize, color, asciiFace, "ascii", topAligned);
        }
        if (nonEnglishBuffer.length() > 0) {
            drawTextSegment(nonEnglishBuffer.toString(), currentX, y, fontSize, color,
                    resolveCjkFace(fixedCjkFace, nonEnglishBuffer.toString()), "cjk", topAligned);
        }
    }

    public static float getTextWidth(String text, float fontSize) {
        return getTextWidthInternal(text, fontSize, englishTypeface, null);
    }

    public static float getTextWidth(String text, float fontSize, FontWeight weight) {
        Typeface face = cjkFaceFor(weight);
        return getTextWidthInternal(text, fontSize, face, face);
    }

    public static float getCustomTextWidth(String text, float fontSize) {
        return getTextWidthInternal(text, fontSize, customTypeface, customTypeface);
    }

    private static float getTextWidthInternal(String text, float fontSize, Typeface asciiFace, Typeface fixedCjkFace) {
        if (!initialized || text == null || text.isEmpty()) {
            return 0.0f;
        }

        float totalWidth = 0.0f;
        StringBuilder asciiBuffer = new StringBuilder();
        StringBuilder nonEnglishBuffer = new StringBuilder();

        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (isAscii(c)) {
                if (nonEnglishBuffer.length() > 0) {
                    totalWidth += measureTextSegment(nonEnglishBuffer.toString(), fontSize,
                            resolveCjkFace(fixedCjkFace, nonEnglishBuffer.toString()));
                    nonEnglishBuffer.setLength(0);
                }
                asciiBuffer.append(c);
            } else {
                if (asciiBuffer.length() > 0) {
                    totalWidth += measureTextSegment(asciiBuffer.toString(), fontSize, asciiFace);
                    asciiBuffer.setLength(0);
                }
                nonEnglishBuffer.append(c);
            }
        }

        if (asciiBuffer.length() > 0) {
            totalWidth += measureTextSegment(asciiBuffer.toString(), fontSize, asciiFace);
        }
        if (nonEnglishBuffer.length() > 0) {
            totalWidth += measureTextSegment(nonEnglishBuffer.toString(), fontSize,
                    resolveCjkFace(fixedCjkFace, nonEnglishBuffer.toString()));
        }

        return totalWidth;
    }

    public static boolean isInitialized() {
        return initialized;
    }

    public static void cleanup() {
        clearTextCache();
        englishTypeface = null;
        nonEnglishLightTypeface = null;
        nonEnglishMediumTypeface = null;
        nonEnglishRegularTypeface = null;
        systemNonEnglishTypeface = null;
        customTypeface = null;
        customTypefaceKey = "";
        initialized = false;
        currentWidth = 0;
        currentHeight = 0;
    }

    private static void clearTextCache() {
        synchronized (textCache) {
            for (CachedText cachedText : textCache.values()) {
                cachedText.dispose();
            }
            textCache.clear();
        }
    }

    /** Transparent padding above the caps baked into each glyph block (see rasterizeText). */
    private static final float GLYPH_TOP_PADDING = 2.0f;

    /**
     * Supersample (oversample) factor for glyph rasterization, decoupled from the user's GUI scale.
     *
     * <p>Baking at 1:1 with the GUI scale still looks soft: GL_LINEAR sampling, POT-padding edge
     * bleed and the integer position rounding in {@code RenderUtil.drawImage} each shave a little
     * crispness. Empirically the text only becomes crisp once it is baked at ~4x the logical size
     * (which a user only got "for free" by setting GUI scale to 4). So we bake at {@code ~2x the
     * GUI scale, capped at 4}, oversampling those losses away — text is crisp at the common GUI
     * scale of 2 with NO need for the user to change their GUI scale. Oversampling higher never
     * blurs (it just anti-aliases the minified result); only under-sampling blurs. The cap of 4
     * bounds the glyph texture size / upload cost.</p>
     */
    private static int supersample() {
        try {
            double guiScale = net.minecraft.client.Minecraft.getInstance().getMainWindow().getGuiScaleFactor();
            int target = 2 * (int) Math.ceil(guiScale);
            return Math.max(2, Math.min(4, target));
        } catch (Throwable t) {
            return 4;
        }
    }

    private static float drawTextSegment(String text, float x, float y, float fontSize, int color,
                                         Typeface typeface, String fontKey, boolean topAligned) {
        CachedText cachedText = getCachedText(text, fontSize, typeface, fontKey);
        if (cachedText == null) {
            return x;
        }

        // topAligned (GUI): put the glyph block's top at y so CJK sits on the same line as the
        // legacy TrueTypeFont ASCII run. Otherwise (music) keep the original baseline anchor.
        float drawY = topAligned
                ? y - GLYPH_TOP_PADDING
                : y + fontSize * 0.8f - cachedText.baseline;
        // Draw through the client's proven, GlStateManager-consistent texture path.
        // The glyph texture is white with per-pixel coverage alpha; drawImage's default
        // GL_MODULATE tints it by `color`, so text renders in the requested colour.
        //
        // The texture is rasterized at supersample x the display size (see rasterizeText) and
        // drawn here into a display-sized rectangle, so it is MINIFIED. We force GL_LINEAR
        // sampling for that minification — smooth edges instead of the jagged GL_NEAREST.
        // NOTE: RenderUtil.drawImage's boolean is inverted (true -> GL_NEAREST, false ->
        // GL_LINEAR), so we pass false to get linear filtering.
        RenderUtil.drawImage(x, drawY, cachedText.displayWidth, cachedText.displayHeight,
                cachedText.texture, color, false);
        return x + cachedText.advanceWidth;
    }

    private static CachedText getCachedText(String text, float fontSize, Typeface typeface, String fontKey) {
        if (typeface == null || text == null || text.isEmpty()) {
            return null;
        }

        int supersample = supersample();
        String cacheKey = fontKey + "_" + System.identityHashCode(typeface) + "|"
                + Math.round(fontSize * 100.0f) + "|x" + supersample + "|" + text;
        synchronized (textCache) {
            CachedText cached = textCache.get(cacheKey);
            if (cached != null) {
                return cached;
            }

            cached = rasterizeText(text, fontSize, typeface, cacheKey, supersample);
            if (cached != null) {
                textCache.put(cacheKey, cached);
            }
            return cached;
        }
    }

    private static CachedText rasterizeText(String text, float fontSize, Typeface typeface, String cacheKey, int supersample) {
        // Rasterize the glyphs at supersample x the requested size, then draw the resulting
        // texture minified (with GL_LINEAR) back down to the display size. This is what makes
        // Skija text smooth: the extra resolution gives the linear minification real coverage
        // gradients to interpolate, instead of hard 1:1 jagged edges. (The vanilla-font STB
        // path is different: its atlas is sampled GL_NEAREST, so it bakes at exactly the GUI
        // scale instead — see CustomFont.deviceOversample().)
        int ss = Math.max(1, supersample);
        float rasterSize = fontSize * ss;
        try (Font font = new Font(typeface, rasterSize); Paint paint = new Paint()) {
            paint.setAntiAlias(true);
            paint.setColor(Color.makeARGB(255, 255, 255, 255));
            configureSmoothing(font);

            FontMetrics metrics = font.getMetrics();
            // Padding is in supersampled pixels so it scales down to a constant display margin.
            int pad = 2 * ss;
            float advanceWidthRaster = Math.max(1.0f, font.measureTextWidth(text, paint));
            int imageWidth = Math.max(1, (int) Math.ceil(advanceWidthRaster) + pad * 2);
            int imageHeight = Math.max(1, (int) Math.ceil(metrics.getDescent() - metrics.getAscent()) + pad * 2);
            float baseline = -metrics.getAscent() + pad;

            // Display-space geometry: divide the supersampled measurements back down by ss.
            float advanceWidth = advanceWidthRaster / ss;
            float displayWidth = imageWidth / (float) ss;
            float displayHeight = imageHeight / (float) ss;

            // Rasterize white text on black, then repackage as a straight-alpha ARGB image:
            // RGB = white, A = glyph coverage. Uploading white+alpha lets RenderUtil.drawImage's
            // GL_MODULATE tint the text to any colour at draw time (one texture, any colour).
            BufferedImage image = new BufferedImage(imageWidth, imageHeight, BufferedImage.TYPE_INT_ARGB);
            Bitmap bitmap = new Bitmap();
            try {
                if (!bitmap.allocPixels(new ImageInfo(imageWidth, imageHeight, ColorType.RGBA_8888, ColorAlphaType.OPAQUE))) {
                    return null;
                }

                Canvas canvas = new Canvas(bitmap);
                try {
                    canvas.clear(Color.makeARGB(255, 0, 0, 0));
                    canvas.drawString(text, pad, baseline, font, paint);
                    byte[] rgba = bitmap.readPixels(
                            new ImageInfo(imageWidth, imageHeight, ColorType.RGBA_8888, ColorAlphaType.OPAQUE),
                            (long) imageWidth * 4L,
                            0,
                            0);
                    if (rgba == null) {
                        return null;
                    }

                    int src = 0;
                    for (int y = 0; y < imageHeight; y++) {
                        for (int x = 0; x < imageWidth; x++) {
                            int r = rgba[src++] & 0xFF;
                            int g = rgba[src++] & 0xFF;
                            int b = rgba[src++] & 0xFF;
                            src++; // alpha channel is opaque; coverage comes from luminance
                            int coverage = Math.max(r, Math.max(g, b));
                            image.setRGB(x, y, (coverage << 24) | 0x00FFFFFF);
                        }
                    }
                } finally {
                    canvas.close();
                }
            } finally {
                bitmap.close();
            }

            // Upload via the same crash-safe path used for album art (raw native pointer,
            // generic GL_RGBA, glFinish), returning a Slick Texture with correct UV ratios.
            Texture texture = SafeTextureUploader.upload("skija_text_" + cacheKey.hashCode(), image);
            if (texture == null) {
                return null;
            }

            return new CachedText(texture, advanceWidth, displayWidth, displayHeight, baseline / ss);
        } catch (Exception e) {
            System.err.println("[SkijaFontRenderer] Failed to rasterize text: " + text);
            e.printStackTrace();
            return null;
        }
    }

    private static float measureTextSegment(String text, float fontSize, Typeface typeface) {
        if (typeface == null || text == null || text.isEmpty()) {
            return 0.0f;
        }

        try (Font font = new Font(typeface, fontSize)) {
            configureSmoothing(font);
            return font.measureTextWidth(text);
        }
    }

    /**
     * Ported verbatim from the reference FontRenderer.setupFont (known-good smooth result):
     * subpixel positioning + FULL hinting + SUBPIXEL_ANTI_ALIAS edging. The RGB subpixel
     * coverage is collapsed to a single alpha channel (max(r,g,b)) and the colour is forced
     * white before tinting, so no colour fringing appears in this bake-to-texture pipeline.
     */
    private static void configureSmoothing(Font font) {
        font.setSubpixel(true);
        font.setHinting(FontHinting.FULL);
        font.setEdging(FontEdging.SUBPIXEL_ANTI_ALIAS);
    }

    private static void loadFonts() {
        if (englishTypeface == null) {
            englishTypeface = loadTypefaceFromResources(ENGLISH_FONT_RESOURCE);
            if (englishTypeface == null) {
                englishTypeface = Typeface.makeFromName("Arial", FontStyle.NORMAL);
            }
        }

        if (nonEnglishLightTypeface == null) {
            nonEnglishLightTypeface = loadTypefaceFromResources(NON_ENGLISH_LIGHT_FONT_RESOURCE);
        }

        if (nonEnglishMediumTypeface == null) {
            nonEnglishMediumTypeface = loadTypefaceFromResources(NON_ENGLISH_FONT_RESOURCE);
        }

        if (nonEnglishRegularTypeface == null) {
            nonEnglishRegularTypeface = loadTypefaceFromResources(NON_ENGLISH_REGULAR_FONT_RESOURCE);
            if (nonEnglishRegularTypeface == null) {
                nonEnglishRegularTypeface = nonEnglishMediumTypeface;
            }
        }

        if (systemNonEnglishTypeface == null) {
            systemNonEnglishTypeface = Typeface.makeFromName("Microsoft YaHei", FontStyle.NORMAL);
        }
    }

    private static Typeface loadTypefaceFromResources(String path) {
        try (java.io.InputStream is = SkijaFontRenderer.class.getClassLoader().getResourceAsStream(path)) {
            if (is == null) {
                return null;
            }
            return Typeface.makeFromData(Data.makeFromBytes(is.readAllBytes()));
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    private static boolean isAscii(char c) {
        return c >= 0x20 && c <= 0x7E;
    }

    private static Typeface selectNonEnglishTypeface(String text) {
        if (canRender(nonEnglishLightTypeface, text)) {
            return nonEnglishLightTypeface;
        }
        if (canRender(nonEnglishMediumTypeface, text)) {
            return nonEnglishMediumTypeface;
        }
        if (canRender(systemNonEnglishTypeface, text)) {
            return systemNonEnglishTypeface;
        }
        return nonEnglishLightTypeface != null ? nonEnglishLightTypeface
                : (nonEnglishMediumTypeface != null ? nonEnglishMediumTypeface : systemNonEnglishTypeface);
    }

    /** Maps a GUI weight to the matching HarmonyOS face, falling back through the loaded weights. */
    private static Typeface cjkFaceFor(FontWeight weight) {
        if (weight == FontWeight.LIGHT && nonEnglishLightTypeface != null) {
            return nonEnglishLightTypeface;
        }
        if (weight == FontWeight.REGULAR && nonEnglishRegularTypeface != null) {
            return nonEnglishRegularTypeface;
        }
        if (nonEnglishMediumTypeface != null) {
            return nonEnglishMediumTypeface;
        }
        if (nonEnglishLightTypeface != null) {
            return nonEnglishLightTypeface;
        }
        if (nonEnglishRegularTypeface != null) {
            return nonEnglishRegularTypeface;
        }
        return systemNonEnglishTypeface;
    }

    /** A fixed CJK face (GUI weight path) or the per-segment auto-selected face (music path). */
    private static Typeface resolveCjkFace(Typeface fixedCjkFace, String segment) {
        return fixedCjkFace != null ? fixedCjkFace : selectNonEnglishTypeface(segment);
    }

    private static boolean canRender(Typeface typeface, String text) {
        if (typeface == null || text == null || text.isEmpty()) {
            return false;
        }

        try (Font font = new Font(typeface, 12.0f)) {
            for (int i = 0; i < text.length(); ) {
                int codePoint = text.codePointAt(i);
                if (!Character.isWhitespace(codePoint) && font.getUTF32Glyph(codePoint) == 0) {
                    return false;
                }
                i += Character.charCount(codePoint);
            }
            return true;
        }
    }

    private static final class CachedText {
        private final Texture texture;
        private final float advanceWidth;
        private final float displayWidth;
        private final float displayHeight;
        private final float baseline;

        private CachedText(Texture texture, float advanceWidth, float displayWidth, float displayHeight, float baseline) {
            this.texture = texture;
            this.advanceWidth = advanceWidth;
            this.displayWidth = displayWidth;
            this.displayHeight = displayHeight;
            this.baseline = baseline;
        }

        private void dispose() {
            try {
                this.texture.release();
            } catch (Exception ignored) {
            }
        }
    }
}
