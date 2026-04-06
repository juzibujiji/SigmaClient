package com.mentalfrostbyte.jello.util.client.render;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.shader.FramebufferConstants;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL;
import org.lwjgl.nanovg.NVGColor;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

import java.io.File;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.file.Files;

import static org.lwjgl.nanovg.NanoVG.*;
import static org.lwjgl.nanovg.NanoVGGL2.*;

/**
 * NanoVG-based font renderer with full CJK support for lyrics display.
 * Uses OpenGL 2 backend for better Minecraft compatibility.
 */
public class NanoVGFontRenderer {
    private static final int GL_FRAMEBUFFER_BINDING = 0x8CA6;
    private static long nvg = -1;
    private static int fontId = -1;
    private static boolean initialized = false;
    private static final String FONT_NAME = "lyrics_font";
    private static ByteBuffer fontBuffer = null; // Keep reference to prevent GC

    // GL state preservation across NanoVG frames
    private static boolean frameActive = false;
    private static int savedProgram = 0;
    private static int savedFramebuffer = 0;
    private static int savedActiveTexture = GL13.GL_TEXTURE0;
    private static int savedTextureBinding = 0;
    private static int savedArrayBuffer = 0;
    private static int savedElementArrayBuffer = 0;
    private static int savedVertexArray = 0;
    private static int savedMatrixMode = GL11.GL_MODELVIEW;
    private static final int[] savedViewport = new int[4];

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
     * Saves critical GL state that NanoVG may modify.
     */
    public static void beginFrame(int width, int height) {
        if (!initialized || frameActive)
            return;

        // NanoVG's GL2 backend can leave OptiFine's shader/FBO state dirty.
        savedProgram = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
        savedFramebuffer = GL11.glGetInteger(GL_FRAMEBUFFER_BINDING);
        savedActiveTexture = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        savedTextureBinding = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        savedArrayBuffer = GL11.glGetInteger(GL15.GL_ARRAY_BUFFER_BINDING);
        savedElementArrayBuffer = GL11.glGetInteger(GL15.GL_ELEMENT_ARRAY_BUFFER_BINDING);
        savedMatrixMode = GL11.glGetInteger(GL11.GL_MATRIX_MODE);

        IntBuffer viewport = BufferUtils.createIntBuffer(16);
        GL11.glGetIntegerv(GL11.GL_VIEWPORT, viewport);
        savedViewport[0] = viewport.get(0);
        savedViewport[1] = viewport.get(1);
        savedViewport[2] = viewport.get(2);
        savedViewport[3] = viewport.get(3);

        if (GL.getCapabilities().OpenGL30) {
            savedVertexArray = GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING);
        } else {
            savedVertexArray = 0;
        }

        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        GL11.glPushClientAttrib(GL11.GL_CLIENT_ALL_ATTRIB_BITS);
        GL11.glMatrixMode(GL11.GL_TEXTURE);
        GL11.glPushMatrix();
        GL11.glMatrixMode(GL11.GL_PROJECTION);
        GL11.glPushMatrix();
        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        GL11.glPushMatrix();

        nvgBeginFrame(nvg, width, height, 1.0f);
        frameActive = true;
    }

    /**
     * End a NanoVG frame. Must be called after all drawing operations.
     * Restores GL state that NanoVG may have modified.
     */
    public static void endFrame() {
        if (!initialized || !frameActive)
            return;
        try {
            nvgEndFrame(nvg);
        } finally {
            GL11.glMatrixMode(GL11.GL_MODELVIEW);
            GL11.glPopMatrix();
            GL11.glMatrixMode(GL11.GL_PROJECTION);
            GL11.glPopMatrix();
            GL11.glMatrixMode(GL11.GL_TEXTURE);
            GL11.glPopMatrix();
            GL11.glMatrixMode(savedMatrixMode);
            GL11.glPopClientAttrib();
            GL11.glPopAttrib();

            GlStateManager.useProgram(savedProgram);
            GlStateManager.bindFramebuffer(FramebufferConstants.GL_FRAMEBUFFER, savedFramebuffer);
            GlStateManager.viewport(savedViewport[0], savedViewport[1], savedViewport[2], savedViewport[3]);
            GL13.glActiveTexture(savedActiveTexture);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, savedTextureBinding);
            RenderSystem.activeTexture(savedActiveTexture);
            GlStateManager.bindTexture(savedTextureBinding);
            GlStateManager.bindBuffer(GL15.GL_ARRAY_BUFFER, savedArrayBuffer);
            GlStateManager.bindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, savedElementArrayBuffer);

            if (GL.getCapabilities().OpenGL30) {
                GL30.glBindVertexArray(savedVertexArray);
            }

            // Invalidate cached GL color so the next RenderSystem color call always re-applies.
            RenderSystem.clearCurrentColor();
            frameActive = false;
        }
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
     * Set NanoVG scissor clipping region. Must be called between beginFrame and endFrame.
     * @param x clip region x
     * @param y clip region y
     * @param w clip region width
     * @param h clip region height
     */
    public static void setScissor(float x, float y, float w, float h) {
        if (!initialized) return;
        nvgScissor(nvg, x, y, w, h);
    }

    /**
     * Reset NanoVG scissor clipping. Must be called between beginFrame and endFrame.
     */
    public static void resetScissor() {
        if (!initialized) return;
        nvgResetScissor(nvg);
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
