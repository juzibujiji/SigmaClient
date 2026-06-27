package com.mentalfrostbyte.jello.module.impl.render.jello;

import com.mentalfrostbyte.jello.event.impl.player.EventUpdate;
import com.mentalfrostbyte.jello.module.Module;
import com.mentalfrostbyte.jello.module.data.ModuleCategory;
import com.mentalfrostbyte.jello.module.settings.impl.NumberSetting;
import com.mentalfrostbyte.jello.util.client.render.Resources;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.fonts.Font;
import net.minecraft.client.gui.fonts.FontResourceManager;
import net.minecraft.client.gui.fonts.providers.IGlyphProvider;
import net.minecraft.client.gui.fonts.providers.TrueTypeGlyphProvider;
import net.minecraft.client.renderer.texture.TextureUtil;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.stb.STBTTFontinfo;
import org.lwjgl.stb.STBTruetype;
import org.lwjgl.system.MemoryUtil;
import team.sdhq.eventBus.annotations.EventTarget;

import java.io.InputStream;
import java.lang.reflect.Field;
import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Hot-swaps the vanilla Minecraft font (chat / F3 / GUI text) between the default
 * providers and a bundled HarmonyOS TTF, giving the whole game proper CJK coverage.
 *
 * <p>Mechanism: the {@code minecraft:default} {@link Font} keeps an ordered
 * {@code List<IGlyphProvider>}; the first provider that has a glyph for a code point
 * wins. On enable we insert a HarmonyOS {@link TrueTypeGlyphProvider} at the front
 * (vanilla providers stay as fallback for anything HarmonyOS lacks). On disable we
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

    // Cached reflection handles (mapped 1.16 names).
    private static Field minecraftFontManagerField;
    private static Field fontManagerMapField;
    private static Field fontProvidersField;

    private final NumberSetting<Float> sizeSetting;
    private final NumberSetting<Float> sharpnessSetting;

    /** The font we currently own (null when not applied). Used to detect resource reloads. */
    private Font appliedFont;
    /** The vanilla providers we displaced, kept alive so we can restore them. */
    private List<IGlyphProvider> savedOriginals;
    /** Our HarmonyOS provider (we own its native memory; close exactly once). */
    private IGlyphProvider harmonyProvider;
    private boolean applied;

    public CustomFont() {
        super(ModuleCategory.RENDER, "CustomFont",
                "Replace the vanilla Minecraft font with HarmonyOS (full CJK support).");
        this.registerSetting(this.sizeSetting = new NumberSetting<>(
                "Size", "Rasterized glyph height; tweak until it matches the vanilla size.",
                9.0F, 6.0F, 20.0F, 0.5F));
        this.registerSetting(this.sharpnessSetting = new NumberSetting<>(
                "Sharpness", "Supersampling factor (higher = crisper, slower to build).",
                2.0F, 1.0F, 4.0F, 1.0F));
        this.sizeSetting.addObserver(s -> this.reapplyIfEnabled());
        this.sharpnessSetting.addObserver(s -> this.reapplyIfEnabled());
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

    private void reapplyIfEnabled() {
        if (this.isEnabled()) {
            this.restore();
            this.tryApply();
        }
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
            IGlyphProvider provider = this.buildHarmonyProvider();
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
            System.err.println("[CustomFont] Failed to apply HarmonyOS font: " + t.getMessage());
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

    private IGlyphProvider buildHarmonyProvider() {
        ByteBuffer buffer = null;
        STBTTFontinfo info = null;
        try (InputStream is = Resources.readInputStream(HARMONY_FONT_RESOURCE)) {
            buffer = TextureUtil.readToBuffer(is);
            ((Buffer) buffer).flip();
            info = STBTTFontinfo.malloc();
            if (!STBTruetype.stbtt_InitFont(info, buffer)) {
                throw new IllegalStateException("stbtt_InitFont failed for HarmonyOS font");
            }
            float size = this.sizeSetting.getCurrentValue();
            float oversample = this.sharpnessSetting.getCurrentValue();
            // Provider takes ownership of buffer + info and frees them in close().
            return new TrueTypeGlyphProvider(buffer, info, size, oversample, 0.0F, 0.0F, "");
        } catch (Throwable t) {
            System.err.println("[CustomFont] Failed to build HarmonyOS glyph provider: " + t.getMessage());
            t.printStackTrace();
            if (info != null) {
                try { info.free(); } catch (Throwable ignored) {
                }
            }
            if (buffer != null) {
                try { MemoryUtil.memFree(buffer); } catch (Throwable ignored) {
                }
            }
            return null;
        }
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
