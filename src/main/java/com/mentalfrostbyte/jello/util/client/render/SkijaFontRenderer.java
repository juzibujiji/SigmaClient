package com.mentalfrostbyte.jello.util.client.render;

import io.github.humbleui.skija.*;
import io.github.humbleui.types.Rect;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;

/**
 * Skija-based font renderer for MusicPlayer
 * Replaces NanoVG with Skija for superior font rendering quality
 */
public class SkijaFontRenderer {
    private static Surface surface;
    private static Canvas canvas;
    private static DirectContext context;
    private static BackendRenderTarget renderTarget;

    private static Typeface englishTypeface;
    private static Typeface chineseTypeface;
    private static Font englishFont;
    private static Font chineseFont;

    private static final Map<String, Integer> textureCache = new HashMap<>();
    private static boolean initialized = false;

    private static int currentWidth = 0;
    private static int currentHeight = 0;

    /**
     * Initialize Skija rendering context
     */
    public static void init(int width, int height) {
        if (initialized && width == currentWidth && height == currentHeight) {
            return;
        }

        cleanup();

        try {
            currentWidth = width;
            currentHeight = height;

            // Create DirectContext for OpenGL backend
            context = DirectContext.makeGL();

            // Create backend render target
            int fbId = GL11.glGetInteger(GL11.GL_FRAMEBUFFER_BINDING);
            renderTarget = BackendRenderTarget.makeGL(
                width, height,
                0, 8,
                fbId,
                FramebufferFormat.GR_GL_RGBA8
            );

            // Create surface
            surface = Surface.makeFromBackendRenderTarget(
                context,
                renderTarget,
                SurfaceOrigin.BOTTOM_LEFT,
                SurfaceColorFormat.RGBA_8888,
                ColorSpace.getSRGB()
            );

            if (surface == null) {
                throw new RuntimeException("Failed to create Skija surface");
            }

            canvas = surface.getCanvas();

            // Load fonts
            loadFonts();

            initialized = true;
        } catch (Exception e) {
            System.err.println("[SkijaFontRenderer] Initialization failed: " + e.getMessage());
            e.printStackTrace();
            cleanup();
        }
    }

    private static void loadFonts() {
        try {
            // Load English font (Helvetica Neue Light from resources)
            englishTypeface = loadTypefaceFromResources("assets/minecraft/com/mentalfrostbyte/gui/resources/font/helvetica-neue-light.ttf");

            // Load Chinese font (HarmonyOS Sans from resources)
            chineseTypeface = loadTypefaceFromResources("assets/minecraft/com/mentalfrostbyte/gui/resources/font/HarmonyOS_Sans_SC_Medium.ttf");

            // Fallbacks
            if (englishTypeface == null) {
                englishTypeface = Typeface.makeFromName("Arial", FontStyle.NORMAL);
            }
            if (chineseTypeface == null) {
                // Try system font as fallback
                chineseTypeface = Typeface.makeFromName("Microsoft YaHei", FontStyle.NORMAL);
            }

        } catch (Exception e) {
            System.err.println("[SkijaFontRenderer] Font loading failed: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static Typeface loadTypefaceFromResources(String path) {
        try {
            java.io.InputStream is = SkijaFontRenderer.class.getClassLoader().getResourceAsStream(path);
            if (is != null) {
                byte[] fontData = is.readAllBytes();
                is.close();
                return Typeface.makeFromData(Data.makeFromBytes(fontData));
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    /**
     * Begin a new frame
     */
    public static void beginFrame(int width, int height) {
        if (!initialized || width != currentWidth || height != currentHeight) {
            init(width, height);
        }

        if (canvas != null) {
            canvas.save();
            canvas.clear(0x00000000); // Transparent
        }
    }

    /**
     * End the current frame and flush
     */
    public static void endFrame() {
        if (canvas != null) {
            canvas.restore();
        }
        if (surface != null) {
            surface.flushAndSubmit();
        }
        if (context != null) {
            context.flush();
        }
    }

    /**
     * Draw text with hybrid font support (ASCII uses English font, CJK uses Chinese font)
     */
    public static void drawText(String text, float x, float y, float fontSize, int color) {
        if (!initialized || canvas == null || text == null || text.isEmpty()) {
            return;
        }

        // Extract color components
        float a = ((color >> 24) & 0xFF) / 255.0f;
        float r = ((color >> 16) & 0xFF) / 255.0f;
        float g = ((color >> 8) & 0xFF) / 255.0f;
        float b = (color & 0xFF) / 255.0f;

        try (Paint paint = new Paint()) {
            paint.setColor(Color.makeARGB((int)(a * 255), (int)(r * 255), (int)(g * 255), (int)(b * 255)));
            paint.setAntiAlias(true);

            float currentX = x;
            StringBuilder asciiBuffer = new StringBuilder();
            StringBuilder cjkBuffer = new StringBuilder();

            for (int i = 0; i < text.length(); i++) {
                char c = text.charAt(i);
                boolean isAscii = c >= 0x20 && c <= 0x7E;

                if (isAscii) {
                    // Flush CJK buffer
                    if (cjkBuffer.length() > 0) {
                        currentX = drawTextSegment(cjkBuffer.toString(), currentX, y, fontSize, paint, chineseTypeface);
                        cjkBuffer.setLength(0);
                    }
                    asciiBuffer.append(c);
                } else {
                    // Flush ASCII buffer
                    if (asciiBuffer.length() > 0) {
                        currentX = drawTextSegment(asciiBuffer.toString(), currentX, y, fontSize, paint, englishTypeface);
                        asciiBuffer.setLength(0);
                    }
                    cjkBuffer.append(c);
                }
            }

            // Flush remaining buffers
            if (asciiBuffer.length() > 0) {
                drawTextSegment(asciiBuffer.toString(), currentX, y, fontSize, paint, englishTypeface);
            }
            if (cjkBuffer.length() > 0) {
                drawTextSegment(cjkBuffer.toString(), currentX, y, fontSize, paint, chineseTypeface);
            }
        }
    }

    private static float drawTextSegment(String text, float x, float y, float fontSize, Paint paint, Typeface typeface) {
        if (typeface == null) return x;

        try (Font font = new Font(typeface, fontSize)) {
            canvas.drawString(text, x, y + fontSize * 0.8f, font, paint);
            return x + font.measureTextWidth(text);
        }
    }

    /**
     * Get text width with hybrid font support
     */
    public static float getTextWidth(String text, float fontSize) {
        if (!initialized || text == null || text.isEmpty()) {
            return 0;
        }

        float totalWidth = 0;
        StringBuilder asciiBuffer = new StringBuilder();
        StringBuilder cjkBuffer = new StringBuilder();

        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            boolean isAscii = c >= 0x20 && c <= 0x7E;

            if (isAscii) {
                if (cjkBuffer.length() > 0) {
                    totalWidth += measureTextSegment(cjkBuffer.toString(), fontSize, chineseTypeface);
                    cjkBuffer.setLength(0);
                }
                asciiBuffer.append(c);
            } else {
                if (asciiBuffer.length() > 0) {
                    totalWidth += measureTextSegment(asciiBuffer.toString(), fontSize, englishTypeface);
                    asciiBuffer.setLength(0);
                }
                cjkBuffer.append(c);
            }
        }

        if (asciiBuffer.length() > 0) {
            totalWidth += measureTextSegment(asciiBuffer.toString(), fontSize, englishTypeface);
        }
        if (cjkBuffer.length() > 0) {
            totalWidth += measureTextSegment(cjkBuffer.toString(), fontSize, chineseTypeface);
        }

        return totalWidth;
    }

    private static float measureTextSegment(String text, float fontSize, Typeface typeface) {
        if (typeface == null) return 0;

        try (Font font = new Font(typeface, fontSize)) {
            return font.measureTextWidth(text);
        }
    }

    /**
     * Check if Skija is initialized
     */
    public static boolean isInitialized() {
        return initialized;
    }

    /**
     * Cleanup resources
     */
    public static void cleanup() {
        textureCache.clear();

        if (englishFont != null) {
            englishFont.close();
            englishFont = null;
        }
        if (chineseFont != null) {
            chineseFont.close();
            chineseFont = null;
        }
        if (surface != null) {
            surface.close();
            surface = null;
        }
        if (renderTarget != null) {
            renderTarget.close();
            renderTarget = null;
        }
        if (context != null) {
            context.close();
            context = null;
        }

        canvas = null;
        initialized = false;
        currentWidth = 0;
        currentHeight = 0;
    }
}
