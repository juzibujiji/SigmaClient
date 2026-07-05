package com.mentalfrostbyte.jello.module.impl.render.jello;

import com.mentalfrostbyte.Client;
import com.mentalfrostbyte.jello.event.impl.player.EventUpdate;
import com.mentalfrostbyte.jello.module.Module;
import com.mentalfrostbyte.jello.module.data.ModuleCategory;
import com.mentalfrostbyte.jello.module.settings.impl.BooleanSetting;
import com.mentalfrostbyte.jello.module.settings.impl.ColorSetting;
import com.mentalfrostbyte.jello.module.settings.impl.FontSwitch;
import com.mentalfrostbyte.jello.module.settings.impl.NumberSetting;
import com.mentalfrostbyte.jello.util.client.render.SkijaFontRenderer;
import com.mentalfrostbyte.jello.util.client.render.Resources;
import com.mentalfrostbyte.jello.util.client.render.theme.ClientColors;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.fonts.Font;
import net.minecraft.client.gui.fonts.FontResourceManager;
import net.minecraft.client.gui.fonts.providers.IGlyphProvider;
import net.minecraft.client.gui.fonts.providers.TrueTypeGlyphProvider;
import net.minecraft.client.renderer.texture.TextureUtil;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.Util;
import org.newdawn.slick.TrueTypeFont;
import org.lwjgl.stb.STBTTFontinfo;
import org.lwjgl.stb.STBTruetype;
import org.lwjgl.system.MemoryUtil;
import team.sdhq.eventBus.annotations.EventTarget;

import java.awt.FontFormatException;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Hot-swaps the vanilla Minecraft font (chat / F3 / GUI text) between the default
 * providers and your prefer font, giving the whole game proper CJK coverage.
 *
 * <p>Mechanism: the {@code minecraft:default} {@link Font} keeps an ordered
 * {@code List<IGlyphProvider>}; the first provider that has a glyph for a code point
 * wins. On enable we insert the selected {@link TrueTypeGlyphProvider} at the front
 * (vanilla providers stay as fallback for anything the selected font lacks). On disable we
 * put the original list back.</p>
 *
 * <p>{@link Font#setGlyphProviders(List)} closes the font's current providers, so to
 * preserve the originals we reflectively empty the list first (then it closes nothing)
 * and re-add the saved originals ourselves. Everything is guarded — on any failure the
 * vanilla font is left untouched and the game never crashes.</p>
 */
public class CustomFont extends Module {

    private static final String HARMONY_FONT_RESOURCE =
            "com/mentalfrostbyte/gui/resources/font/HarmonyOS_Sans_SC_Regular.ttf";
    private static final String BUNDLED_FONT_NAME = "内置鸿蒙";
    private static final String CUSTOM_FONT_FOLDER = "custom-fonts";

    // Cached reflection handles (mapped 1.16 names).
    private static Field minecraftFontManagerField;
    private static Field fontManagerMapField;
    private static Field fontProvidersField;

    private final NumberSetting<Float> sizeSetting;
    private final NumberSetting<Float> sharpnessSetting;
    private final BooleanSetting spectrumSetting;
    private final ColorSetting spectrumColorSetting;
    private final FontSwitch fontSwitch;
    private TrueTypeFont previewFont;
    private String spectrumTypefaceKey = "";

    /** The font we currently own (null when not applied). Used to detect resource reloads. */
    private Font appliedFont;
    /** The vanilla providers we displaced, kept alive so we can restore them. */
    private List<IGlyphProvider> savedOriginals;
    /** Our selected provider (we own its native memory; close exactly once). */
    private IGlyphProvider harmonyProvider;
    private boolean applied;

    public CustomFont() {
        super(ModuleCategory.RENDER, "CustomFont",
                "Replace the vanilla Minecraft font with your prefer font.");
        this.registerSetting(this.sizeSetting = new NumberSetting<>(
                "Size", "Rasterized glyph height; tweak until it matches the vanilla size.",
                9.0F, 6.0F, 20.0F, 0.5F));
        this.registerSetting(this.sharpnessSetting = new NumberSetting<>(
                "Sharpness", "Supersampling factor (higher = crisper, slower to build).",
                2.0F, 1.0F, 4.0F, 1.0F));
        this.registerSetting(this.spectrumSetting = new BooleanSetting(
                "Spectrum", "Apply your prefer font to the music spectrum text.", true));
        this.registerSetting(this.spectrumColorSetting = new ColorSetting(
                "Color", "The music spectrum text color.", ClientColors.LIGHT_GREYISH_BLUE.getColor()));
        this.registerSetting(this.fontSwitch = new FontSwitch(
                "Import",
                "Open the custom font folder, then refresh to load the first .ttf/.otf inside it.",
                BUNDLED_FONT_NAME,
                this::openCustomFontFolder,
                this::refreshCustomFont,
                this::listAvailableFontNames,
                this::selectFont,
                this::getPreviewFont));
        this.sizeSetting.addObserver(s -> this.invalidatePreviewAndReapply());
        this.sharpnessSetting.addObserver(s -> this.invalidatePreviewAndReapply());
        this.spectrumSetting.addObserver(s -> {
            if (!this.spectrumSetting.getCurrentValue()) {
                this.invalidateSpectrumTypeface();
            }
        });
        this.setAvailableOnClassic(false);
    }

    @Override
    public void onEnable() {
        super.onEnable();
        this.tryApply();
    }

    @Override
    public void onDisable() {
        super.onDisable();
        this.invalidateSpectrumTypeface();
        this.restore();
    }

    /**
     * Safety net: re-apply if the module is on but the live default font no longer carries our
     * provider (covers config-enable-before-font-init and resource/pack reloads that rebuild fonts).
     */
    @EventTarget
    public void onTick(EventUpdate event) {
        if (!this.isEnabled()) {
            return;
        }
        Font font = getDefaultFont();
        if (font == null || (this.applied && font == this.appliedFont)) {
            return;
        }
        if (this.applied && font != this.appliedFont) {
            // The font was rebuilt (reload) — MC already closed our old provider; drop the refs.
            this.harmonyProvider = null;
            this.savedOriginals = null;
            this.applied = false;
            this.appliedFont = null;
        }
        this.tryApply();
    }

    private void invalidatePreviewAndReapply() {
        this.previewFont = null;
        this.fontSwitch.notifyObservers();
        this.reapplyIfEnabled();
    }

    private void reapplyIfEnabled() {
        if (this.isEnabled()) {
            this.restore();
            this.tryApply();
        }
    }

    public boolean shouldApplyToSpectrum() {
        return this.isEnabled() && this.spectrumSetting.getCurrentValue();
    }

    public int getSpectrumTextColor() {
        return this.spectrumColorSetting.getCurrentValue();
    }

    public float getSpectrumFontScale() {
        return Math.max(0.65F, Math.min(2.25F, this.sizeSetting.getCurrentValue() / 9.0F));
    }

    public boolean ensureSpectrumTypeface() {
        if (!this.shouldApplyToSpectrum()) {
            return false;
        }

        String key = this.getSpectrumTypefaceKey();
        if (SkijaFontRenderer.hasCustomTypeface(key)) {
            return true;
        }

        try {
            byte[] fontBytes = this.readSelectedFontBytes();
            boolean loaded = SkijaFontRenderer.setCustomTypeface(fontBytes, key);
            if (loaded) {
                this.spectrumTypefaceKey = key;
            }
            return loaded;
        } catch (Throwable t) {
            System.err.println("[CustomFont] Failed to load spectrum font: " + t.getMessage());
            t.printStackTrace();
            return false;
        }
    }

    private void invalidateSpectrumTypeface() {
        this.spectrumTypefaceKey = "";
        SkijaFontRenderer.clearCustomTypeface();
    }

    private void tryApply() {
        if (this.applied) {
            return;
        }
        try {
            Font font = getDefaultFont();
            if (font == null) {
                return; // font system not ready yet; the tick handler retries.
            }
            IGlyphProvider provider = this.buildSelectedProvider();
            if (provider == null) {
                return;
            }

            List<IGlyphProvider> originals = new ArrayList<>(readProviders(font));
            // Empty the list first so setGlyphProviders' internal close() closes nothing.
            readProviders(font).clear();

            List<IGlyphProvider> combined = new ArrayList<>();
            combined.add(provider);
            combined.addAll(originals);
            font.setGlyphProviders(combined);

            this.appliedFont = font;
            this.savedOriginals = originals;
            this.harmonyProvider = provider;
            this.applied = true;
        } catch (Throwable t) {
            System.err.println("[CustomFont] Failed to apply selected font: " + t.getMessage());
            t.printStackTrace();
        }
    }

    private void restore() {
        try {
            Font current = getDefaultFont();
            boolean sameFont = this.applied && this.appliedFont != null
                    && current == this.appliedFont && this.savedOriginals != null;
            if (sameFont) {
                // No reload: empty the list (so the originals are NOT closed), restore them,
                // then close our provider exactly once.
                readProviders(this.appliedFont).clear();
                this.appliedFont.setGlyphProviders(new ArrayList<>(this.savedOriginals));
                if (this.harmonyProvider != null) {
                    this.harmonyProvider.close();
                }
            }
            // else: the font was reloaded; MC already closed our provider — just drop refs.
        } catch (Throwable t) {
            System.err.println("[CustomFont] Failed to restore vanilla font: " + t.getMessage());
            t.printStackTrace();
        } finally {
            this.harmonyProvider = null;
            this.savedOriginals = null;
            this.appliedFont = null;
            this.applied = false;
        }
    }

    private IGlyphProvider buildSelectedProvider() {
        File customFont = this.getSelectedCustomFont();
        if (customFont != null) {
            IGlyphProvider provider = this.buildProvider(customFont);
            if (provider != null) {
                return provider;
            }
        }
        return this.buildProvider(HARMONY_FONT_RESOURCE);
    }

    private IGlyphProvider buildProvider(String resourcePath) {
        try (InputStream is = Resources.readInputStream(resourcePath)) {
            return this.buildProvider(is, resourcePath);
        } catch (Throwable t) {
            System.err.println("[CustomFont] Failed to build bundled glyph provider: " + t.getMessage());
            t.printStackTrace();
            return null;
        }
    }

    private IGlyphProvider buildProvider(File fontFile) {
        try (InputStream is = new FileInputStream(fontFile)) {
            return this.buildProvider(is, fontFile.getAbsolutePath());
        } catch (Throwable t) {
            System.err.println("[CustomFont] Failed to build custom glyph provider: " + t.getMessage());
            t.printStackTrace();
            return null;
        }
    }

    /**
     * Minimum STB oversample so glyphs are baked at the same device density Skija uses.
     * MUST mirror SkijaFontRenderer.supersample() exactly (2 * ceil(guiScale), capped 2..4) —
     * otherwise the MC-font STB path and the Skija path bake at different densities and one looks
     * softer than the other at low Sharpness (the bug: STB floored at 2 while Skija baked at 4).
     */
    private static float deviceOversampleFloor() {
        try {
            double guiScale = net.minecraft.client.Minecraft.getInstance().getMainWindow().getGuiScaleFactor();
            int target = 2 * (int) Math.ceil(guiScale);
            return Math.max(2.0F, Math.min(4.0F, (float) target));
        } catch (Throwable t) {
            return 4.0F;
        }
    }

    private IGlyphProvider buildProvider(InputStream inputStream, String sourceName) throws IOException {
        ByteBuffer buffer = null;
        STBTTFontinfo info = null;
        try {
            buffer = TextureUtil.readToBuffer(inputStream);
            ((Buffer) buffer).flip();
            info = STBTTFontinfo.malloc();
            if (!STBTruetype.stbtt_InitFont(info, buffer)) {
                throw new IllegalStateException("stbtt_InitFont failed for " + sourceName);
            }
            float size = this.sizeSetting.getCurrentValue();
            // Sharpness is the user's relative crispness control, but it must never let the glyph
            // bake BELOW device-pixel density — Sharpness=1 would otherwise mean "oversample 1x",
            // i.e. a 1x bake that the GUI-scale matrix then magnifies and blurs (the same failure
            // we fixed on the Skija side). So floor the STB oversample at the GUI scale: the slider
            // still ranges soft->crisp, but even its lowest setting stays crisp on a magnified GUI.
            float oversample = Math.max(deviceOversampleFloor(), this.sharpnessSetting.getCurrentValue());
            // Provider takes ownership of buffer + info and frees them in close().
            return new TrueTypeGlyphProvider(buffer, info, size, oversample, 0.0F, 0.0F, "");
        } catch (Throwable t) {
            if (info != null) {
                try { info.free(); } catch (Throwable ignored) {
                }
            }
            if (buffer != null) {
                try { MemoryUtil.memFree(buffer); } catch (Throwable ignored) {
                }
            }
            if (t instanceof IOException) {
                throw (IOException) t;
            }
            throw new IllegalStateException(t);
        }
    }

    private void openCustomFontFolder() {
        File folder = this.ensureCustomFontDirectory();
        if (folder == null) {
            return;
        }
        try {
            Util.getOSType().openFile(folder);
        } catch (Throwable t) {
            System.err.println("[CustomFont] Failed to open custom font folder: " + t.getMessage());
            t.printStackTrace();
        }
    }

    private void refreshCustomFont() {
        this.invalidateSpectrumTypeface();
        if (!this.listAvailableFontNames().contains(this.fontSwitch.getCurrentValue())) {
            this.previewFont = null;
            this.fontSwitch.setCurrentValue(BUNDLED_FONT_NAME);
        } else {
            this.fontSwitch.notifyObservers();
        }
        this.reapplyIfEnabled();
    }

    private List<String> listAvailableFontNames() {
        List<String> fonts = new ArrayList<>();
        fonts.add(BUNDLED_FONT_NAME);
        for (File font : this.listCustomFonts()) {
            fonts.add(font.getName());
        }
        return fonts;
    }

    private void selectFont(String fontName) {
        if (!this.listAvailableFontNames().contains(fontName)) {
            fontName = BUNDLED_FONT_NAME;
        }
        this.invalidateSpectrumTypeface();
        this.previewFont = null;
        this.fontSwitch.updateCurrentValue(fontName, false);
        this.fontSwitch.notifyObservers();
        this.reapplyIfEnabled();
    }

    private File getSelectedCustomFont() {
        String selectedName = this.fontSwitch.getCurrentValue();
        if (selectedName == null || selectedName.equals(BUNDLED_FONT_NAME)) {
            return null;
        }
        for (File font : this.listCustomFonts()) {
            if (font.getName().equals(selectedName)) {
                return font;
            }
        }
        return null;
    }

    private String getSpectrumTypefaceKey() {
        File selected = this.getSelectedCustomFont();
        if (selected == null) {
            return HARMONY_FONT_RESOURCE;
        }
        return selected.getAbsolutePath() + ":" + selected.length() + ":" + selected.lastModified();
    }

    private byte[] readSelectedFontBytes() throws IOException {
        File selected = this.getSelectedCustomFont();
        if (selected == null) {
            try (InputStream inputStream = Resources.readInputStream(HARMONY_FONT_RESOURCE)) {
                return inputStream.readAllBytes();
            }
        }
        try (InputStream inputStream = new FileInputStream(selected)) {
            return inputStream.readAllBytes();
        }
    }

    private File[] listCustomFonts() {
        File folder = this.ensureCustomFontDirectory();
        if (folder == null) {
            return new File[0];
        }
        File[] fonts = folder.listFiles(file -> file.isFile() && isSupportedFontFile(file));
        if (fonts == null) {
            return new File[0];
        }
        Arrays.sort(fonts, Comparator.comparing(File::getName, String.CASE_INSENSITIVE_ORDER));
        return fonts;
    }

    private File ensureCustomFontDirectory() {
        try {
            File folder = new File(Client.getInstance().file, CUSTOM_FONT_FOLDER);
            if (!folder.exists() && !folder.mkdirs()) {
                throw new IOException("Unable to create " + folder.getAbsolutePath());
            }
            return folder;
        } catch (Throwable t) {
            System.err.println("[CustomFont] Failed to prepare custom font folder: " + t.getMessage());
            t.printStackTrace();
            return null;
        }
    }

    private static boolean isSupportedFontFile(File file) {
        String name = file.getName().toLowerCase();
        return name.endsWith(".ttf") || name.endsWith(".otf");
    }

    private TrueTypeFont getPreviewFont() {
        File selected = this.getSelectedCustomFont();
        if (this.previewFont == null) {
            this.previewFont = selected == null
                    ? this.buildPreviewFont(HARMONY_FONT_RESOURCE)
                    : this.buildPreviewFont(selected);
        }
        return this.previewFont;
    }

    private TrueTypeFont buildPreviewFont(String resourcePath) {
        try (InputStream inputStream = Resources.readInputStream(resourcePath)) {
            java.awt.Font font = java.awt.Font.createFont(java.awt.Font.TRUETYPE_FONT, inputStream)
                    .deriveFont(java.awt.Font.PLAIN, this.getPreviewPointSize());
            return new TrueTypeFont(font, true, previewCharacters(), this.getPreviewPadding());
        } catch (IOException | FontFormatException t) {
            System.err.println("[CustomFont] Failed to build preview font: " + t.getMessage());
            t.printStackTrace();
            return null;
        }
    }

    private TrueTypeFont buildPreviewFont(File fontFile) {
        try (InputStream inputStream = new FileInputStream(fontFile)) {
            java.awt.Font font = java.awt.Font.createFont(java.awt.Font.TRUETYPE_FONT, inputStream)
                    .deriveFont(java.awt.Font.PLAIN, this.getPreviewPointSize());
            return new TrueTypeFont(font, true, previewCharacters(), this.getPreviewPadding());
        } catch (IOException | FontFormatException t) {
            System.err.println("[CustomFont] Failed to build preview font: " + t.getMessage());
            t.printStackTrace();
            return null;
        }
    }

    private float getPreviewPointSize() {
        // Size ONLY controls the preview point size. Sharpness must never change the glyph
        // size — it only affects smoothing (see SkijaFontRenderer.configureSmoothing /
        // supersample, and the STB oversample path).
        float size = this.sizeSetting.getCurrentValue();
        return Math.max(1.0F, size * 2.0F);
    }

    private int getPreviewPadding() {
        return Math.max(1, Math.round(this.sharpnessSetting.getCurrentValue()));
    }

    private static char[] previewCharacters() {
        return ("\u4E2D\u56FD\u667A\u9020\uFF0C\u60E0\u53CA\u5168\u7403"
                + "The quick brown fox jumps over the lazy dog.").toCharArray();
    }

    private static Font getDefaultFont() {
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc == null) {
                return null;
            }
            if (minecraftFontManagerField == null) {
                minecraftFontManagerField = Minecraft.class.getDeclaredField("fontResourceMananger");
                minecraftFontManagerField.setAccessible(true);
            }
            FontResourceManager manager = (FontResourceManager) minecraftFontManagerField.get(mc);
            if (manager == null) {
                return null;
            }
            if (fontManagerMapField == null) {
                fontManagerMapField = FontResourceManager.class.getDeclaredField("field_238546_d_");
                fontManagerMapField.setAccessible(true);
            }
            @SuppressWarnings("unchecked")
            Map<ResourceLocation, Font> fonts = (Map<ResourceLocation, Font>) fontManagerMapField.get(manager);
            if (fonts == null) {
                return null;
            }
            return fonts.get(Minecraft.DEFAULT_FONT_RENDERER_NAME);
        } catch (Throwable t) {
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private static List<IGlyphProvider> readProviders(Font font) throws Exception {
        if (fontProvidersField == null) {
            fontProvidersField = Font.class.getDeclaredField("glyphProviders");
            fontProvidersField.setAccessible(true);
        }
        return (List<IGlyphProvider>) fontProvidersField.get(font);
    }
}
