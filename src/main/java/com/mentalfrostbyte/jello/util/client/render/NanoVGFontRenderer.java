package com.mentalfrostbyte.jello.util.client.render;

import org.lwjgl.nanovg.NVGColor;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

import java.io.File;
import java.nio.ByteBuffer;
import java.nio.file.Files;

import static org.lwjgl.nanovg.NanoVG.*;
import static org.lwjgl.nanovg.NanoVGGL2.*;

/**
 * NanoVG-based font renderer with full CJK support for lyrics display.
 * Uses OpenGL 2 backend for better Minecraft compatibility.
 */
public class NanoVGFontRenderer {
    private static long nvg = -1;
    private static int fontId = -1;
    private static boolean initialized = false;
    private static final String FONT_NAME = "lyrics_font";
    private static ByteBuffer fontBuffer = null; // Keep reference to prevent GC

    /**
     * Initialize NanoVG context and load Chinese font.
     * Should be called once during client initialization.
     */
    public static void init() {
        if (initialized)
            return;

        try {
            // Create NanoVG context with OpenGL 2 backend (more compatible with Minecraft)
            nvg = nvgCreate(NVG_ANTIALIAS | NVG_STENCIL_STROKES);
            if (nvg == -1) {
                System.err.println("[NanoVGFontRenderer] Failed to create NanoVG context");
                return;
            }

            // Font priority list - only .ttf files (avoid .ttc which may cause issues)
            String[] fontPaths = {
                    "C:\\Windows\\Fonts\\msyhbd.ttf", // Microsoft YaHei Bold
                    "C:\\Windows\\Fonts\\msyhl.ttf", // Microsoft YaHei Light
                    "C:\\Windows\\Fonts\\simhei.ttf", // SimHei
                    "C:\\Windows\\Fonts\\simkai.ttf", // SimKai
                    "C:\\Windows\\Fonts\\STKAITI.TTF", // STKaiti
                    "C:\\Windows\\Fonts\\STFANGSO.TTF", // STFangSong
                    "C:\\Windows\\Fonts\\SIMYOU.TTF", // YouYuan
            };

            for (String fontPath : fontPaths) {
                File fontFile = new File(fontPath);
                if (fontFile.exists()) {
                    try {
                        byte[] fontBytes = Files.readAllBytes(fontFile.toPath());
                        fontBuffer = MemoryUtil.memAlloc(fontBytes.length);
                        fontBuffer.put(fontBytes).flip();

                        // Create null-terminated ByteBuffer for font name (required by LWJGL NanoVG)
                        ByteBuffer nameBuffer = MemoryUtil.memUTF8(FONT_NAME);
                        // Use false for freeData parameter to prevent NanoVG from freeing our buffer
                        fontId = nvgCreateFontMem(nvg, nameBuffer, fontBuffer, false);
                        MemoryUtil.memFree(nameBuffer);
                        if (fontId != -1) {
                            System.out.println("[NanoVGFontRenderer] Successfully loaded font: " + fontPath);
                            initialized = true;
                            return;
                        } else {
                            System.err.println("[NanoVGFontRenderer] Failed to create font from: " + fontPath);
                            MemoryUtil.memFree(fontBuffer);
                            fontBuffer = null;
                        }
                    } catch (Exception e) {
                        System.err
                                .println("[NanoVGFontRenderer] Error loading font " + fontPath + ": " + e.getMessage());
                        if (fontBuffer != null) {
                            MemoryUtil.memFree(fontBuffer);
                            fontBuffer = null;
                        }
                    }
                }
            }

            System.err.println("[NanoVGFontRenderer] No suitable Chinese font found");
        } catch (Exception e) {
            System.err.println("[NanoVGFontRenderer] Initialization error: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Begin a NanoVG frame. Must be called before any drawing operations.
     */
    public static void beginFrame(int width, int height) {
        if (!initialized)
            return;
        nvgBeginFrame(nvg, width, height, 1.0f);
    }

    /**
     * End a NanoVG frame. Must be called after all drawing operations.
     */
    public static void endFrame() {
        if (!initialized)
            return;
        nvgEndFrame(nvg);
    }

    /**
     * Draw text at the specified position with given color.
     * 
     * @param text     The text to draw
     * @param x        X position
     * @param y        Y position
     * @param fontSize Font size in pixels
     * @param color    ARGB color value
     */
    public static void drawText(String text, float x, float y, float fontSize, int color) {
        if (!initialized || text == null || text.isEmpty())
            return;

        try (MemoryStack stack = MemoryStack.stackPush()) {
            NVGColor nvgColor = NVGColor.malloc(stack);

            // Extract ARGB components
            float a = ((color >> 24) & 0xFF) / 255.0f;
            float r = ((color >> 16) & 0xFF) / 255.0f;
            float g = ((color >> 8) & 0xFF) / 255.0f;
            float b = (color & 0xFF) / 255.0f;

            nvgRGBAf(r, g, b, a, nvgColor);

            nvgFontFaceId(nvg, fontId);
            nvgFontSize(nvg, fontSize);
            nvgTextAlign(nvg, NVG_ALIGN_LEFT | NVG_ALIGN_TOP);
            nvgFillColor(nvg, nvgColor);
            nvgText(nvg, x, y, text);
        }
    }

    /**
     * Get the width of the text in pixels.
     */
    public static float getTextWidth(String text, float fontSize) {
        if (!initialized || text == null || text.isEmpty())
            return 0;

        nvgFontFaceId(nvg, fontId);
        nvgFontSize(nvg, fontSize);
        float[] bounds = new float[4];
        nvgTextBounds(nvg, 0, 0, text, bounds);
        return bounds[2] - bounds[0];
    }

    /**
     * Get the height of the font in pixels.
     */
    public static float getFontHeight(float fontSize) {
        return fontSize;
    }

    /**
     * Check if the renderer is properly initialized.
     */
    public static boolean isInitialized() {
        return initialized;
    }

    /**
     * Clean up NanoVG resources.
     */
    public static void cleanup() {
        if (nvg != -1) {
            nvgDelete(nvg);
            nvg = -1;
        }
        if (fontBuffer != null) {
            MemoryUtil.memFree(fontBuffer);
            fontBuffer = null;
        }
        initialized = false;
    }
}
