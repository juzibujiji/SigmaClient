package com.mentalfrostbyte.jello.util.client.render;

import com.mentalfrostbyte.jello.util.game.render.SafeTextureUploader;
import io.github.humbleui.skija.Bitmap;
import io.github.humbleui.skija.Canvas;
import io.github.humbleui.skija.ColorAlphaType;
import io.github.humbleui.skija.ColorType;
import io.github.humbleui.skija.Data;
import io.github.humbleui.skija.Font;
import io.github.humbleui.skija.FontEdging;
import io.github.humbleui.skija.FontHinting;
import io.github.humbleui.skija.FontMetrics;
import io.github.humbleui.skija.FontStyle;
import io.github.humbleui.skija.Image;
import io.github.humbleui.skija.ImageInfo;
import io.github.humbleui.skija.Paint;
import io.github.humbleui.skija.PaintMode;
import io.github.humbleui.types.Rect;
import io.github.humbleui.types.RRect;
import io.github.humbleui.skija.Typeface;
import org.newdawn.slick.opengl.Texture;

import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

/**
 * 使用 Skija（Chrome 的 Skia 2D 引擎的 Java 封装）渲染 HUD 元素到位图，
 * 再上传为 GL 纹理。提供抗锯齿圆角、高质量文字、平滑形状——渲染质量等同 Chrome。
 *
 * <p>支持中英文混排：英文用 Helvetica Neue，中文用 HarmonyOS Sans SC。
 */
public final class SkijaHudRenderer {

    private final int logicalWidth;
    private final int logicalHeight;
    private final int supersample;
    private final int pixelWidth;
    private final int pixelHeight;
    private final Bitmap bitmap;
    private final Canvas canvas;

    // 英文字体
    private static Typeface lightTypeface;
    private static Typeface mediumTypeface;
    // 中文字体
    private static Typeface cjkLightTypeface;
    private static Typeface cjkMediumTypeface;
    private static Typeface systemCjkTypeface;
    private static boolean fontsLoaded = false;

    // Font 缓存：避免每次 drawText/measureText 都创建新 Font 对象（减少 GC 压力）
    private static final Map<String, Font> fontCache = new HashMap<>();

    private static Font getCachedFont(Typeface face, float size) {
        String key = System.identityHashCode(face) + "_" + Math.round(size * 10);
        Font cached = fontCache.get(key);
        if (cached != null) return cached;
        Font f = new Font(face, size);
        f.setSubpixel(true);
        f.setHinting(FontHinting.FULL);
        f.setEdging(FontEdging.SUBPIXEL_ANTI_ALIAS);
        fontCache.put(key, f);
        return f;
    }

    public SkijaHudRenderer(int logicalWidth, int logicalHeight, int supersample) {
        this.logicalWidth = logicalWidth;
        this.logicalHeight = logicalHeight;
        this.supersample = Math.max(1, supersample);
        this.pixelWidth = logicalWidth * this.supersample;
        this.pixelHeight = logicalHeight * this.supersample;

        ensureFonts();

        bitmap = new Bitmap();
        bitmap.allocPixels(new ImageInfo(pixelWidth, pixelHeight, ColorType.RGBA_8888, ColorAlphaType.PREMUL));
        canvas = new Canvas(bitmap);
        canvas.clear(0);
        canvas.scale(this.supersample, this.supersample);
    }

    private static void ensureFonts() {
        if (fontsLoaded) return;
        fontsLoaded = true;
        lightTypeface = loadTypeface(
                "assets/minecraft/com/mentalfrostbyte/gui/resources/font/helvetica-neue-light.ttf");
        mediumTypeface = loadTypeface(
                "assets/minecraft/com/mentalfrostbyte/gui/resources/font/helvetica-neue medium.ttf");
        cjkLightTypeface = loadTypeface(
                "assets/minecraft/com/mentalfrostbyte/gui/resources/font/HarmonyOS_Sans_SC_Light.ttf");
        cjkMediumTypeface = loadTypeface(
                "assets/minecraft/com/mentalfrostbyte/gui/resources/font/HarmonyOS_Sans_SC_Medium.ttf");
        systemCjkTypeface = Typeface.makeFromName("Microsoft YaHei", FontStyle.NORMAL);
    }

    private static Typeface loadTypeface(String path) {
        try (InputStream is = SkijaHudRenderer.class.getClassLoader().getResourceAsStream(path)) {
            if (is != null) {
                byte[] bytes = is.readAllBytes();
                return Typeface.makeFromData(Data.makeFromBytes(bytes));
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    private static boolean hasCJK(String text) {
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) > 0x7E) return true;
        }
        return false;
    }

    private Typeface selectFace(String text, boolean medium) {
        if (hasCJK(text)) {
            Typeface cjk = medium ? cjkMediumTypeface : cjkLightTypeface;
            if (cjk != null) return cjk;
            if (systemCjkTypeface != null) return systemCjkTypeface;
        }
        Typeface face = medium ? mediumTypeface : lightTypeface;
        if (face == null) face = lightTypeface;
        if (face == null) face = Typeface.makeFromName("Arial", FontStyle.NORMAL);
        return face;
    }

    // ===== 绘制方法 =====

    public void drawRoundedRectStroke(float x, float y, float w, float h, float r, float strokeWidth, int argb) {
        try (Paint paint = new Paint()) {
            paint.setAntiAlias(true);
            paint.setColor(argb);
            paint.setMode(PaintMode.STROKE);
            paint.setStrokeWidth(strokeWidth);
            canvas.drawRRect(RRect.makeLTRB(x, y, x + w, y + h, r), paint);
        }
    }

    public void drawRoundedRect(float x, float y, float w, float h, float r, int argb) {
        try (Paint paint = new Paint()) {
            paint.setAntiAlias(true);
            paint.setColor(argb);
            canvas.drawRRect(RRect.makeLTRB(x, y, x + w, y + h, r), paint);
        }
    }

    public void drawRect(float x, float y, float w, float h, int argb) {
        try (Paint paint = new Paint()) {
            paint.setAntiAlias(true);
            paint.setColor(argb);
            canvas.drawRect(Rect.makeLTRB(x, y, x + w, y + h), paint);
        }
    }

    public void drawCircle(float cx, float cy, float radius, int argb) {
        try (Paint paint = new Paint()) {
            paint.setAntiAlias(true);
            paint.setColor(argb);
            canvas.drawCircle(cx, cy, radius, paint);
        }
    }

    public void drawTopHalfOval(float cx, float cy, float rx, float ry, int argb) {
        try (Paint paint = new Paint()) {
            paint.setAntiAlias(true);
            paint.setColor(argb);
            canvas.save();
            canvas.clipRect(Rect.makeLTRB(cx - rx, cy - ry, cx + rx, cy));
            canvas.drawOval(Rect.makeLTRB(cx - rx, cy - ry, cx + rx, cy + ry), paint);
            canvas.restore();
        }
    }

    public void drawImage(Image img, float x, float y, float w, float h, int argb) {
        if (img == null) return;
        try (Paint paint = new Paint()) {
            paint.setAntiAlias(true);
            paint.setColor(argb);
            canvas.drawImageRect(img, Rect.makeXYWH(x, y, w, h), paint);
        }
    }

    public void drawText(String text, float x, float yTop, float fontSize, int argb, boolean medium) {
        if (text == null || text.isEmpty()) return;
        Typeface face = selectFace(text, medium);
        if (face == null) return;

        Font font = getCachedFont(face, fontSize);
        try (Paint paint = new Paint()) {
            paint.setAntiAlias(true);
            paint.setColor(argb);
            FontMetrics metrics = font.getMetrics();
            float baseline = -metrics.getAscent();
            canvas.drawString(text, x, yTop + baseline, font, paint);
        }
    }

    public float measureText(String text, float fontSize, boolean medium) {
        if (text == null || text.isEmpty()) return 0;
        Typeface face = selectFace(text, medium);
        if (face == null) return 0;

        Font font = getCachedFont(face, fontSize);
        try (Paint paint = new Paint()) {
            return font.measureTextWidth(text, paint);
        }
    }

    public float getFontHeight(float fontSize, boolean medium) {
        Typeface face = medium ? mediumTypeface : lightTypeface;
        if (face == null) return fontSize;
        Font font = getCachedFont(face, fontSize);
        FontMetrics metrics = font.getMetrics();
        return metrics.getDescent() - metrics.getAscent();
    }

    // ===== 纹理输出 =====

    public Texture toTexture() {
        byte[] rgba = bitmap.readPixels(
                new ImageInfo(pixelWidth, pixelHeight, ColorType.RGBA_8888, ColorAlphaType.PREMUL),
                (long) pixelWidth * 4L, 0, 0);

        canvas.close();
        bitmap.close();

        if (rgba == null) return null;

        // 使用 DataBufferInt 直接写入像素数组，避免逐像素 setRGB() 的方法调用开销
        BufferedImage image = new BufferedImage(pixelWidth, pixelHeight, BufferedImage.TYPE_INT_ARGB);
        int[] pixelArray = ((DataBufferInt) image.getRaster().getDataBuffer()).getData();

        int src = 0;
        for (int i = 0; i < pixelArray.length; i++) {
            int r = rgba[src++] & 0xFF;
            int g = rgba[src++] & 0xFF;
            int b = rgba[src++] & 0xFF;
            int a = rgba[src++] & 0xFF;
            if (a > 0 && a < 255) {
                int halfA = a >> 1;
                r = Math.min(255, (r * 255 + halfA) / a);
                g = Math.min(255, (g * 255 + halfA) / a);
                b = Math.min(255, (b * 255 + halfA) / a);
            }
            pixelArray[i] = (a << 24) | (r << 16) | (g << 8) | b;
        }

        // forceFinish=false 使用 glFlush 替代 glFinish，避免管线阻塞
        return SafeTextureUploader.upload("hud_" + System.nanoTime(), image, false);
    }
}
