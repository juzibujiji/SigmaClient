package com.mentalfrostbyte.jello.util.client.render;

import it.unimi.dsi.fastutil.ints.IntCollection;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.util.stream.IntStream;
import javax.annotation.Nullable;
import net.minecraft.client.gui.fonts.IGlyphInfo;
import net.minecraft.client.gui.fonts.providers.IGlyphProvider;
import net.minecraft.client.renderer.texture.NativeImage;
import org.lwjgl.stb.STBTTFontinfo;
import org.lwjgl.stb.STBTruetype;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

/**
 * TTF glyph provider for the vanilla font pipeline that renders pixel-perfect at the current
 * GUI scale and sits on the vanilla bitmap font's baseline.
 *
 * <p>The vanilla font atlas is sampled with GL_NEAREST ({@code RenderType.getText} builds its
 * {@code TextureState} with {@code blur=false}), so a glyph only looks clean when its atlas
 * texels map 1:1 onto device pixels. Callers must therefore bake with
 * {@code oversample == ceil(gui scale)}; this class additionally keeps every glyph origin on
 * that pixel grid (see {@code getAdvance()}).</p>
 *
 * <p>Differences from {@link net.minecraft.client.gui.fonts.providers.TrueTypeGlyphProvider},
 * each of which misplaces glyphs relative to the vanilla bitmap font:</p>
 * <ul>
 *   <li><b>bearingY</b>: vanilla derives it from the font's own hhea ascent, so the baseline
 *       lands at {@code size * ascent/(ascent-descent) - 3} — it drifts with the font file and
 *       the Size setting and never matches the bitmap font's fixed baseline at {@code y+7}.
 *       We anchor every glyph to that vanilla baseline instead, so vertical position matches
 *       vanilla text exactly, for any font and any Size.</li>
 *   <li><b>bearingX</b>: vanilla adds the left side bearing twice ({@code lsb*scale + x0},
 *       where the bitmap box {@code x0} already IS the scaled lsb rounded to pixels), pushing
 *       glyphs right of the pen. We use the bitmap box {@code x0} alone.</li>
 *   <li><b>advance</b>: vanilla keeps fractional advances, so glyphs land between device
 *       pixels and the NEAREST-sampled atlas alternately drops and doubles stroke columns.
 *       We round the advance to whole baked pixels; with {@code oversample == gui scale} that
 *       is exactly the device-pixel grid (and mirrors the bitmap font's integer advances).</li>
 * </ul>
 *
 * <p>Takes ownership of {@code fontData} and {@code fontInfo}; both are freed in
 * {@link #close()}, exactly once, mirroring the vanilla provider's contract.</p>
 */
public class VanillaAlignedGlyphProvider implements IGlyphProvider {

    /** Baseline of the vanilla bitmap font: "ascent": 7 in default.json's ascii provider. */
    private static final float VANILLA_BASELINE = 7.0F;
    /** TexturedGlyph.render subtracts 3 from bearingY (bitmap glyphs default it to 3). */
    private static final float TEXTURED_GLYPH_Y_OFFSET = 3.0F;

    private final ByteBuffer fontData;
    private final STBTTFontinfo fontInfo;
    private final float oversample;
    private final float scale;

    public VanillaAlignedGlyphProvider(ByteBuffer fontData, STBTTFontinfo fontInfo, float size, float oversample) {
        this.fontData = fontData;
        this.fontInfo = fontInfo;
        this.oversample = oversample;
        this.scale = STBTruetype.stbtt_ScaleForPixelHeight(fontInfo, size * oversample);
    }

    @Nullable
    @Override
    public IGlyphInfo getGlyphInfo(int codePoint) {
        int glyphIndex = STBTruetype.stbtt_FindGlyphIndex(this.fontInfo, codePoint);
        if (glyphIndex == 0) {
            return null;
        }

        try (MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer x0 = stack.mallocInt(1);
            IntBuffer y0 = stack.mallocInt(1);
            IntBuffer x1 = stack.mallocInt(1);
            IntBuffer y1 = stack.mallocInt(1);
            STBTruetype.stbtt_GetGlyphBitmapBox(this.fontInfo, glyphIndex, this.scale, this.scale, x0, y0, x1, y1);
            int width = x1.get(0) - x0.get(0);
            int height = y1.get(0) - y0.get(0);
            if (width == 0 || height == 0) {
                // Whitespace-like glyph: fall through to the vanilla providers, same as MC's own
                // TTF provider does (the space codepoint never reaches providers anyway — Font
                // pins it to a fixed 4px advance).
                return null;
            }

            IntBuffer advance = stack.mallocInt(1);
            IntBuffer leftSideBearing = stack.mallocInt(1);
            STBTruetype.stbtt_GetGlyphHMetrics(this.fontInfo, glyphIndex, advance, leftSideBearing);
            return new GlyphInfo(glyphIndex, x0.get(0), y0.get(0), width, height,
                    (float) advance.get(0) * this.scale);
        }
    }

    @Override
    public void close() {
        this.fontInfo.free();
        MemoryUtil.memFree(this.fontData);
    }

    @Override
    public IntSet func_230428_a_() {
        // Same supported-codepoint range the vanilla TTF provider reports; per-glyph lookups
        // outside it still work because Font queries getGlyphInfo directly when rendering.
        return IntStream.range(0, 65535).collect(IntOpenHashSet::new, IntCollection::add, IntCollection::addAll);
    }

    class GlyphInfo implements IGlyphInfo {
        private final int glyphIndex;
        /** Bitmap box left edge relative to the pen, in baked (oversampled) pixels. */
        private final int x0;
        /** Bitmap box top edge relative to the baseline, in baked pixels (negative above it). */
        private final int y0;
        private final int width;
        private final int height;
        /** Horizontal advance in baked pixels, unquantized. */
        private final float advancePx;

        private GlyphInfo(int glyphIndex, int x0, int y0, int width, int height, float advancePx) {
            this.glyphIndex = glyphIndex;
            this.x0 = x0;
            this.y0 = y0;
            this.width = width;
            this.height = height;
            this.advancePx = advancePx;
        }

        @Override
        public float getAdvance() {
            // Whole baked pixels == whole device pixels when oversample matches the GUI scale,
            // so successive glyph origins stay on the pixel grid like vanilla's integer advances.
            return (float) Math.round(this.advancePx) / VanillaAlignedGlyphProvider.this.oversample;
        }

        @Override
        public float getBearingX() {
            return (float) this.x0 / VanillaAlignedGlyphProvider.this.oversample;
        }

        @Override
        public float getBearingY() {
            // Anchor to the vanilla baseline: glyph top = y + 7 + y0/oversample (y0 negative
            // above the baseline); the +3 cancels TexturedGlyph.render's built-in -3.
            return VANILLA_BASELINE + TEXTURED_GLYPH_Y_OFFSET
                    + (float) this.y0 / VanillaAlignedGlyphProvider.this.oversample;
        }

        @Override
        public int getWidth() {
            return this.width;
        }

        @Override
        public int getHeight() {
            return this.height;
        }

        @Override
        public float getOversample() {
            return VanillaAlignedGlyphProvider.this.oversample;
        }

        @Override
        public void uploadGlyph(int xOffset, int yOffset) {
            NativeImage image = new NativeImage(NativeImage.PixelFormat.LUMINANCE, this.width, this.height, false);
            image.renderGlyph(VanillaAlignedGlyphProvider.this.fontInfo, this.glyphIndex, this.width, this.height,
                    VanillaAlignedGlyphProvider.this.scale, VanillaAlignedGlyphProvider.this.scale, 0.0F, 0.0F, 0, 0);
            image.uploadTextureSub(0, xOffset, yOffset, 0, 0, this.width, this.height, false, true);
        }

        @Override
        public boolean isColored() {
            return false;
        }
    }
}
