package com.mentalfrostbyte.jello.managers;



import com.google.gson.JsonObject;

import com.google.gson.JsonParseException;

import com.mentalfrostbyte.Client;

import com.mentalfrostbyte.jello.event.impl.game.render.EventRender2DCustom;

import com.mentalfrostbyte.jello.event.impl.game.render.EventRender2DOffset;

import com.mentalfrostbyte.jello.event.impl.game.render.EventRenderChat;

import com.mentalfrostbyte.jello.event.impl.player.EventUpdate;

import com.mentalfrostbyte.jello.managers.data.Manager;

import com.mentalfrostbyte.jello.managers.util.Thumbnails;

import com.mentalfrostbyte.jello.managers.util.notifs.Notification;

import com.mentalfrostbyte.jello.module.impl.render.jello.CustomFont;

import com.mentalfrostbyte.jello.util.client.ClientMode;

import com.mentalfrostbyte.jello.util.client.music.JavaFFT;

import com.mentalfrostbyte.jello.util.client.network.youtube.YoutubeContentType;

import com.mentalfrostbyte.jello.util.client.network.youtube.YoutubeVideoData;

import com.mentalfrostbyte.jello.util.client.render.ResourceRegistry;

import com.mentalfrostbyte.jello.util.client.music.LrcParser;

import com.mentalfrostbyte.jello.util.client.network.netease.NeteaseApiLogin;

import com.mentalfrostbyte.jello.util.client.network.netease.NeteaseApiSearch;

import com.mentalfrostbyte.jello.util.client.render.theme.ClientColors;

import com.mentalfrostbyte.jello.util.client.render.SkijaFontRenderer;

import com.mentalfrostbyte.jello.util.game.MinecraftUtil;

import com.mentalfrostbyte.jello.util.game.render.RenderUtil;

import com.mentalfrostbyte.jello.util.game.render.RenderUtil2;
import com.mentalfrostbyte.jello.util.game.render.SafeTextureUploader;

import com.mentalfrostbyte.jello.util.system.math.MathHelper;

import com.mentalfrostbyte.jello.util.system.network.ImageUtil;

import com.mentalfrostbyte.jello.util.system.sound.AudioRepeatMode;

import com.mentalfrostbyte.jello.util.system.sound.BasicAudioProcessor;

import com.mentalfrostbyte.jello.util.system.sound.MusicStream;

import net.sourceforge.jaad.aac.Decoder;

import net.sourceforge.jaad.aac.SampleBuffer;

import net.sourceforge.jaad.mp4.MP4Container;

import net.sourceforge.jaad.mp4.api.*;

import org.apache.http.HttpEntity;

import org.apache.http.client.methods.CloseableHttpResponse;

import org.apache.http.client.methods.HttpGet;

import org.apache.http.impl.client.CloseableHttpClient;

import org.apache.http.impl.client.HttpClients;

import org.newdawn.slick.opengl.Texture;

import org.newdawn.slick.TrueTypeFont;

import net.minecraft.client.Minecraft;

import net.minecraft.util.Util;

import com.mojang.blaze3d.platform.GlStateManager;

import com.mojang.blaze3d.systems.RenderSystem;

import org.lwjgl.opengl.GL11;

import team.sdhq.eventBus.annotations.EventTarget;



import javax.imageio.ImageIO;

import javax.sound.sampled.*;

import javax.sound.sampled.FloatControl.Type;

import java.awt.Color;

import java.awt.Graphics2D;

import java.awt.image.BufferedImage;

import java.io.*;

import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;


public class MusicManager extends Manager implements MinecraftUtil {
    // Use a neutral pink fallback instead of green when artwork color extraction fails.
    private static final int DEFAULT_COVER_ACCENT_COLOR = 0xFFFF85C2;
    private static final int COVER_CONNECT_TIMEOUT_MS = 3000;
    private static final int COVER_READ_TIMEOUT_MS = 5000;
    private static final int COVER_PROCESS_SIZE = 256;
    private static final int COVER_NOTIFICATION_SIZE = 114;
    private static final int PROCESSED_COVER_CACHE_MAX = 24;

    private static final LinkedHashMap<String, ProcessedCoverImages> PROCESSED_COVER_CACHE =
            new LinkedHashMap<String, ProcessedCoverImages>(32, 0.75F, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, ProcessedCoverImages> eldest) {
                    return size() > PROCESSED_COVER_CACHE_MAX;
                }
            };

    private static final class ProcessedCoverImages {
        final BufferedImage backgroundStrip;
        final BufferedImage notificationCover;
        final int accentColor;

        private ProcessedCoverImages(BufferedImage backgroundStrip,
                                     BufferedImage notificationCover,
                                     int accentColor) {
            this.backgroundStrip = backgroundStrip;
            this.notificationCover = notificationCover;
            this.accentColor = accentColor;
        }
    }

    private static final class ProcessedCoverResult {
        final String title;
        final String coverKey;
        final int generation;
        final ProcessedCoverImages images;

        private ProcessedCoverResult(String title, String coverKey, int generation,
                                     ProcessedCoverImages images) {
            this.title = title;
            this.coverKey = coverKey;
            this.generation = generation;
            this.images = images;
        }
    }

    private static final class PendingCoverUpload {
        final ProcessedCoverResult result;
        Texture songThumbnail;
        Texture notificationImage;
        int step;

        private PendingCoverUpload(ProcessedCoverResult result) {
            this.result = result;
        }
    }

    public BufferedImage scaledThumbnail;
    public String songTitle = "";
    public List<double[]> visualizerData = new ArrayList<double[]>();

    public ArrayList<Double> amplitudes = new ArrayList<Double>();

    public SourceDataLine sourceDataLine;

    private boolean playing = false;

    private Thumbnails videoManager;

    private int volume = 50;

    private long duration = -1L;

    private Texture notificationImage;

    private BufferedImage thumbnailImage;

    private Texture songThumbnail;

    private volatile boolean processing = false;

    private transient volatile Thread audioThread = null;

    private int currentVideoIndex2;

    private long totalDuration = 0L;

    private int currentVideoIndex;

    private volatile YoutubeVideoData currentVideo;

    private boolean spectrum = true;

    private AudioRepeatMode repeat = AudioRepeatMode.REPEAT;

    private boolean finished = false;

    private double field32168;

    private boolean field32169 = false;

    private double field32170 = 0.0;

    private List<LrcParser.LyricLine> currentLyrics = new ArrayList<>();

    private long currentPositionMs = 0;

    private volatile long mp3SeekTargetSec = -1;

    // Suppresses the Now Playing toast caused by restarting the same track for a seek.
    private volatile String seekNotificationSuppressionKey = null;



    // --- Texture cache to avoid re-creating every tick & prevent native crash ---

    /** The song title for which the cached textures were created */

    private String cachedTextureSongTitle = null;
    private String cachedTextureCoverKey = null;

    /** Pending CPU-side cover result from worker thread, uploaded on render thread. */
    private volatile ProcessedCoverResult pendingProcessedCover = null;
    private volatile String requestedCoverKey = null;
    private volatile int requestedCoverGeneration = 0;
    private volatile int coverAccentColor = DEFAULT_COVER_ACCENT_COLOR;
    private final AtomicInteger coverGeneration = new AtomicInteger();
    private PendingCoverUpload activeCoverUpload = null;


    @Override

    public void init() {

        super.init();



        try {

            this.loadSettings();

        } catch (JsonParseException e) {

            Client.logger.error(e);

        }



        this.finished = false;



        // 尝试恢复网易云登录状态

        NeteaseApiLogin.loadPersistentCookie();

    }



    public void saveMusicSettings() {

        JsonObject jsonObject = new JsonObject();

        jsonObject.addProperty("volume", this.volume);

        jsonObject.addProperty("spectrum", this.spectrum);

        jsonObject.addProperty("repeat", this.repeat.type);

        Client.getInstance().config.add("music", jsonObject);

    }



    private void loadSettings() throws JsonParseException {

        if (Client.getInstance().config.has("music")) {

            JsonObject jsonObject = Client.getInstance().config.getAsJsonObject("music");

            if (jsonObject != null) {

                if (jsonObject.has("volume")) {

                    this.volume = Math.max(0, Math.min(100, jsonObject.get("volume").getAsInt()));

                }



                if (jsonObject.has("spectrum")) {

                    this.spectrum = jsonObject.get("spectrum").getAsBoolean();

                }



                if (jsonObject.has("repeat")) {

                    this.repeat = AudioRepeatMode.parseRepeat(jsonObject.get("repeat").getAsInt());

                }

            }

        }

    }



    @EventTarget

    public void onRender2D(EventRender2DOffset event) {

        if (Client.getInstance().clientMode == ClientMode.JELLO) {

            if (this.playing && !this.visualizerData.isEmpty()) {

                double[] var4 = this.visualizerData.get(0);

                if (this.amplitudes.isEmpty()) {

                    for (double v : var4) {

                        if (this.amplitudes.size() < 1024) {

                            this.amplitudes.add(v);

                        }

                    }

                }



                float fps = 60.0F / (float) Minecraft.getFps();



                for (int i = 0; i < var4.length; i++) {

                    double var7 = this.amplitudes.get(i) - var4[i];

                    boolean var9 = !(this.amplitudes.get(i) < Double.MAX_VALUE);

                    this.amplitudes.set(i, Math.min(2.256E7,

                            Math.max(0.0, this.amplitudes.get(i) - var7 * (double) Math.min(0.335F * fps, 1.0F))));

                    if (var9) {

                        this.amplitudes.set(i, 0.0);

                    }

                }

            }

        }

    }



    @EventTarget

    public void onRenderChat(EventRenderChat eventRenderChat) {

        if (isPlayingSong())

            eventRenderChat.addOffset(-45);

    }



    @EventTarget

    public void onRender2D(EventRender2DCustom event) {

        if (this.playing && !this.visualizerData.isEmpty() && this.spectrum) {

            // ── TEMP DIAGNOSTIC (Stage 1): confirm the new path runs + observe leaked GL state ──
            if (!spectrumPathLogged) {
                spectrumPathLogged = true;
                System.out.println("[SkijaFix] NEW spectrum render path ACTIVE (no glPushAttrib). "
                        + "Seeing this confirms your running build includes the fix.");
            }
            boolean diag = (spectrumDiagFrame++ % 60) == 0;
            if (diag) {
                logSpectrumGlState("before");
            }

            // The spectrum HUD now draws exclusively through GlStateManager-consistent
            // paths (RenderUtil + SkijaFontRenderer via SafeTextureUploader/drawImage).
            try {

                this.renderSpectrum();

            } finally {

                // Leave a known-good, shadow-consistent state for the rest of the GUI,
                // mirroring IngameGui.renderIngameGui's own post-event reset.
                RenderSystem.defaultBlendFunc();

                GL11.glColor4f(1.0f, 1.0f, 1.0f, 1.0f);

                RenderSystem.color4f(1.0f, 1.0f, 1.0f, 1.0f);

                RenderSystem.clearCurrentColor();

                if (diag) {
                    logSpectrumGlState("after ");
                }

            }

        }

    }

    // ── TEMP DIAGNOSTIC (Stage 1) — remove once the render issue is resolved ──
    private static boolean spectrumPathLogged = false;
    private static int spectrumDiagFrame = 0;

    private void logSpectrumGlState(String when) {
        int err = GL11.glGetError();
        System.out.println("[SkijaFix][" + when + "]"
                + " err=0x" + Integer.toHexString(err)
                + " stencil=" + GL11.glIsEnabled(2960)
                + " scissor=" + GL11.glIsEnabled(3089)
                + " blend=" + GL11.glIsEnabled(3042)
                + " depthTest=" + GL11.glIsEnabled(2929)
                + " tex2d=" + GL11.glIsEnabled(3553)
                + " lighting=" + GL11.glIsEnabled(2896)
                + " alphaTest=" + GL11.glIsEnabled(3008)
                + " activeTex=0x" + Integer.toHexString(GL11.glGetInteger(0x84E0))
                + " boundTex=" + GL11.glGetInteger(0x8069)
                + " program=" + GL11.glGetInteger(0x8B8D));
    }



    private void renderSpectrum() {
        if (!this.visualizerData.isEmpty()) {
            // Guard: only render artwork-driven HUD effects when both textures are fully ready.
            if (this.hasReadySongArtwork()) {
                if (!this.amplitudes.isEmpty()) {
                    int accentColor = this.getDynamicCoverColor();
                    float maxWidth = 114.0F;
                    float width = (float) Math.ceil((float) mc.getMainWindow().getWidth() / maxWidth);

                    for (int i = 0; (float) i < maxWidth; i++) {
                        float alphaValue = 1.0F - (float) (i + 1) / maxWidth;
                        float heightRatio = (float) mc.getMainWindow().getHeight() / 1080.0F;

                        float height = ((float) (Math.sqrt(this.amplitudes.get(i)) / 12.0) - 5.0F) * heightRatio;

                        RenderUtil.drawRoundedRect2(
                                (float) i * width,
                                (float) mc.getMainWindow().getHeight() - height,
                                width,
                                height,
                                RenderUtil2.applyAlpha(accentColor, 0.2F * alphaValue));
                    }

                    RenderUtil.initStencilBuffer();


                    for (int i = 0; (float) i < maxWidth; i++) {
                        float heightRatio = (float) mc.getMainWindow().getHeight() / 1080.0F;
                        float height = ((float) (Math.sqrt(this.amplitudes.get(i)) / 12.0) - 5.0F) * heightRatio;
                        RenderUtil.drawRoundedRect2((float) i * width, (float) mc.getMainWindow().getHeight() - height,
                                width, height, RenderUtil2.applyAlpha(accentColor, 0.95F));
                    }

                    RenderUtil.configureStencilTest();
                    if (this.hasReadySongArtwork()) {
                        RenderUtil.drawImage(0.0F, 0.0F, (float) mc.getMainWindow().getWidth(),
                                (float) mc.getMainWindow().getHeight(), this.songThumbnail, 0.4F);
                        GL11.glDisable(GL11.GL_TEXTURE_2D);

                        GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);

                        GL11.glColor4f(1.0f, 1.0f, 1.0f, 1.0f);

                        RenderSystem.color4f(1.0f, 1.0f, 1.0f, 1.0f);

                    }



                    RenderUtil.restorePreviousStencilBuffer();



                    double var9 = 0.0;

                    float var16 = 4750;



                    for (int i = 0; i < 3; i++) {

                        var9 = Math.max(var9, Math.sqrt(this.amplitudes.get(i)) - 1000.0);

                    }



                    float scale = 1.0F

                            + (float) Math.round((float) (var9 / (double) (var16 - 1000)) * 0.14F * 75.0F) / 75.0F;

                    GL11.glPushMatrix();

                    GL11.glTranslated(60.0, mc.getMainWindow().getHeight() - 55, 0.0);

                    GL11.glScalef(scale, scale, 0.0F);

                    GL11.glTranslated(-60.0, -(mc.getMainWindow().getHeight() - 55), 0.0);

                    RenderUtil.drawImage(10.0F, (float) (mc.getMainWindow().getHeight() - 110), 100.0F, 100.0F,

                            this.notificationImage);

                    RenderUtil.drawRoundedRect(10.0F, (float) (mc.getMainWindow().getHeight() - 110), 100.0F, 100.0F,

                            14.0F, 0.3F);

                    GL11.glPopMatrix();



                    GL11.glDisable(GL11.GL_TEXTURE_2D);

                    GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);

                    GL11.glColor4f(1.0f, 1.0f, 1.0f, 1.0f);

                    RenderSystem.color4f(1.0f, 1.0f, 1.0f, 1.0f);



                    int screenWidth = mc.getMainWindow().getWidth();
                    int screenHeight = mc.getMainWindow().getHeight();

                    TrackTitleParts titleParts = parseTrackTitle(this.songTitle);
                    String lyric = this.getCurrentLyric();
                    CustomFont customFont = this.getSpectrumCustomFont();
                    boolean customSpectrumFont = customFont != null && customFont.ensureSpectrumTypeface();
                    int customSpectrumColor = customSpectrumFont
                            ? customFont.getSpectrumTextColor()
                            : ClientColors.LIGHT_GREYISH_BLUE.getColor();
                    SpectrumTextLayout spectrumLayout = this.buildSpectrumTextLayout(
                            screenWidth, screenHeight, titleParts, lyric, customSpectrumFont, customFont);

                    if (titleParts.artist.isEmpty()) {

                        drawSpectrumText(
                                screenWidth, screenHeight,
                                titleParts.title,
                                130.0F,
                                spectrumLayout.titleY,
                                spectrumLayout.titleSize,
                                customSpectrumFont
                                        ? RenderUtil2.applyAlpha(customSpectrumColor, 0.35F)
                                        : RenderUtil2.applyAlpha(ClientColors.DEEP_TEAL.getColor(), 0.5F),
                                customSpectrumFont
                                        ? RenderUtil2.applyAlpha(customSpectrumColor, 0.82F)
                                        : RenderUtil2.applyAlpha(ClientColors.LIGHT_GREYISH_BLUE.getColor(), 0.7F),
                                ResourceRegistry.JelloLightFont18_1,
                                ResourceRegistry.JelloLightFont18,
                                customSpectrumFont);

                    } else {

                        drawSpectrumText(
                                screenWidth, screenHeight,
                                titleParts.artist,
                                130.0F,
                                spectrumLayout.artistY,
                                spectrumLayout.artistSize,
                                customSpectrumFont
                                        ? RenderUtil2.applyAlpha(customSpectrumColor, 0.3F)
                                        : RenderUtil2.applyAlpha(ClientColors.DEEP_TEAL.getColor(), 0.4F),
                                customSpectrumFont
                                        ? RenderUtil2.applyAlpha(customSpectrumColor, 0.72F)
                                        : RenderUtil2.applyAlpha(ClientColors.LIGHT_GREYISH_BLUE.getColor(), 0.6F),
                                ResourceRegistry.JelloMediumFont20_1,
                                ResourceRegistry.JelloMediumFont20,
                                customSpectrumFont);

                        drawSpectrumText(
                                screenWidth, screenHeight,
                                titleParts.title,
                                130.0F,
                                spectrumLayout.titleY,
                                spectrumLayout.titleSize,
                                customSpectrumFont
                                        ? RenderUtil2.applyAlpha(customSpectrumColor, 0.35F)
                                        : RenderUtil2.applyAlpha(ClientColors.DEEP_TEAL.getColor(), 0.5F),
                                customSpectrumFont
                                        ? RenderUtil2.applyAlpha(customSpectrumColor, 0.86F)
                                        : RenderUtil2.applyAlpha(ClientColors.LIGHT_GREYISH_BLUE.getColor(), 0.7F),
                                ResourceRegistry.JelloLightFont18_1,
                                ResourceRegistry.JelloLightFont18,
                                customSpectrumFont);

                    }



                    // Lyrics

                    if (lyric != null && !lyric.isEmpty()) {

                        float fontSize = spectrumLayout.lyricSize;

                        float lyricY = spectrumLayout.lyricY;

                        float lyricX = 130.0F;

                        float lyricWidth = this.measureSpectrumText(
                                screenWidth, screenHeight, lyric, fontSize, customSpectrumFont, ResourceRegistry.JelloLightFont18);

                        float progress = this.getLyricProgress();

                        float progressWidth = lyricWidth * progress;


                        int dimColor = RenderUtil2.applyAlpha(customSpectrumColor, 0.35F);

                        this.drawSpectrumPlainText(
                                screenWidth, screenHeight, lyric, lyricX, lyricY, fontSize, dimColor,
                                ResourceRegistry.JelloLightFont18, customSpectrumFont);



                        RenderUtil.startScissor((int) lyricX, (int) lyricY, (int) (lyricX + progressWidth),

                                (int) (lyricY + fontSize), true);


                        int brightColor = RenderUtil2.applyAlpha(customSpectrumColor, 0.95F);

                        this.drawSpectrumPlainText(
                                screenWidth, screenHeight, lyric, lyricX, lyricY, fontSize, brightColor,
                                ResourceRegistry.JelloLightFont18, customSpectrumFont);



                        RenderUtil.restoreScissor();

                    }



                    GL11.glColor4f(1.0f, 1.0f, 1.0f, 1.0f);

                    RenderSystem.color4f(1.0f, 1.0f, 1.0f, 1.0f);

                }

            }

        }

    }



    private void drawSpectrumText(int screenWidth, int screenHeight, String text, float x, float y, float fontSize,
                                  int shadowColor, int mainColor, TrueTypeFont shadowFallback,
                                  TrueTypeFont mainFallback, boolean useCustomTypeface) {
        if (text == null || text.isEmpty()) {
            return;
        }

        this.drawSpectrumPlainText(
                screenWidth, screenHeight, text, x, y, fontSize, shadowColor, shadowFallback, useCustomTypeface);
        this.drawSpectrumPlainText(
                screenWidth, screenHeight, text, x, y, fontSize, mainColor, mainFallback, useCustomTypeface);
    }

    private void drawSpectrumPlainText(int screenWidth, int screenHeight, String text, float x, float y,
                                       float fontSize, int color, TrueTypeFont fallback,
                                       boolean useCustomTypeface) {
        if (text == null || text.isEmpty()) {
            return;
        }

        if (SkijaFontRenderer.ensureInitialized(screenWidth, screenHeight)) {
            SkijaFontRenderer.beginFrame(screenWidth, screenHeight);
            if (useCustomTypeface && SkijaFontRenderer.hasCustomTypeface()) {
                SkijaFontRenderer.drawCustomText(text, x, y, fontSize, color);
            } else {
                SkijaFontRenderer.drawText(text, x, y, fontSize, color);
            }
            SkijaFontRenderer.endFrame();
            return;
        }

        RenderUtil.drawString(fallback, x, y, text, color);
    }

    private SpectrumTextLayout buildSpectrumTextLayout(int screenWidth, int screenHeight, TrackTitleParts titleParts,
                                                       String lyric, boolean useCustomTypeface, CustomFont customFont) {
        SpectrumTextLayout layout = new SpectrumTextLayout();
        boolean hasArtist = titleParts != null && !titleParts.artist.isEmpty();
        boolean hasLyric = lyric != null && !lyric.isEmpty();

        if (!useCustomTypeface || customFont == null) {
            layout.artistSize = 20.0F;
            layout.titleSize = 18.0F;
            layout.lyricSize = 20.0F;
            layout.artistY = (float) (screenHeight - 81);
            layout.titleY = (float) (screenHeight - (hasArtist ? 56 : 70));
            layout.lyricY = (float) (screenHeight - 38);
            return layout;
        }

        float scale = customFont.getSpectrumFontScale();
        layout.artistSize = this.clampSpectrumFontSize(20.0F * scale);
        layout.titleSize = this.clampSpectrumFontSize(18.0F * scale);
        layout.lyricSize = this.clampSpectrumFontSize(20.0F * scale);

        float maxTextWidth = Math.max(80.0F, (float) screenWidth - 150.0F);
        if (hasArtist) {
            layout.artistSize = this.fitSpectrumFontSize(
                    screenWidth, screenHeight, titleParts.artist, layout.artistSize, maxTextWidth,
                    true, ResourceRegistry.JelloMediumFont20);
        }
        layout.titleSize = this.fitSpectrumFontSize(
                screenWidth, screenHeight, titleParts.title, layout.titleSize, maxTextWidth,
                true, ResourceRegistry.JelloLightFont18);
        if (hasLyric) {
            layout.lyricSize = this.fitSpectrumFontSize(
                    screenWidth, screenHeight, lyric, layout.lyricSize, maxTextWidth,
                    true, ResourceRegistry.JelloLightFont18);
        }

        float gap = Math.max(4.0F, Math.min(9.0F, 5.0F * scale));
        float topLimit = (float) screenHeight - 106.0F;
        float bottomLimit = (float) screenHeight - 14.0F;
        float totalHeight = layout.titleSize;
        if (hasArtist) {
            totalHeight += gap + layout.artistSize;
        }
        if (hasLyric) {
            totalHeight += gap + layout.lyricSize;
        }

        float verticalBudget = Math.max(42.0F, bottomLimit - topLimit);
        if (totalHeight > verticalBudget) {
            float fit = verticalBudget / totalHeight;
            layout.artistSize = Math.max(10.0F, layout.artistSize * fit);
            layout.titleSize = Math.max(10.0F, layout.titleSize * fit);
            layout.lyricSize = Math.max(10.0F, layout.lyricSize * fit);
            gap = Math.max(3.0F, gap * fit);
        }

        if (hasLyric) {
            layout.lyricY = bottomLimit - layout.lyricSize;
            layout.titleY = layout.lyricY - gap - layout.titleSize;
            layout.artistY = hasArtist ? layout.titleY - gap - layout.artistSize : layout.titleY;
        } else if (hasArtist) {
            layout.titleY = bottomLimit - layout.titleSize;
            layout.artistY = layout.titleY - gap - layout.artistSize;
            layout.lyricY = bottomLimit - layout.lyricSize;
        } else {
            layout.titleY = topLimit + (verticalBudget - layout.titleSize) * 0.5F;
            layout.artistY = layout.titleY;
            layout.lyricY = bottomLimit - layout.lyricSize;
        }

        float top = hasArtist ? layout.artistY : layout.titleY;
        if (top < topLimit) {
            float offset = topLimit - top;
            layout.artistY += offset;
            layout.titleY += offset;
            layout.lyricY += offset;
        }

        layout.titleSize = this.fitSpectrumFontSize(
                screenWidth, screenHeight, titleParts.title, layout.titleSize, maxTextWidth,
                true, ResourceRegistry.JelloLightFont18);
        if (hasArtist) {
            layout.artistSize = this.fitSpectrumFontSize(
                    screenWidth, screenHeight, titleParts.artist, layout.artistSize, maxTextWidth,
                    true, ResourceRegistry.JelloMediumFont20);
        }
        if (hasLyric) {
            layout.lyricSize = this.fitSpectrumFontSize(
                    screenWidth, screenHeight, lyric, layout.lyricSize, maxTextWidth,
                    true, ResourceRegistry.JelloLightFont18);
        }

        return layout;
    }

    private float fitSpectrumFontSize(int screenWidth, int screenHeight, String text, float fontSize, float maxWidth,
                                      boolean useCustomTypeface, TrueTypeFont fallback) {
        if (text == null || text.isEmpty()) {
            return fontSize;
        }

        float width = this.measureSpectrumText(screenWidth, screenHeight, text, fontSize, useCustomTypeface, fallback);
        if (width <= 0.0F || width <= maxWidth) {
            return fontSize;
        }
        return Math.max(10.0F, fontSize * maxWidth / width);
    }

    private float measureSpectrumText(int screenWidth, int screenHeight, String text, float fontSize,
                                      boolean useCustomTypeface, TrueTypeFont fallback) {
        if (text == null || text.isEmpty()) {
            return 0.0F;
        }

        if (SkijaFontRenderer.ensureInitialized(screenWidth, screenHeight)) {
            if (useCustomTypeface && SkijaFontRenderer.hasCustomTypeface()) {
                return SkijaFontRenderer.getCustomTextWidth(text, fontSize);
            }
            return SkijaFontRenderer.getTextWidth(text, fontSize);
        }

        if (fallback != null) {
            return fallback.getWidth(text);
        }
        return text.length() * fontSize * 0.55F;
    }

    private float clampSpectrumFontSize(float fontSize) {
        return Math.max(10.0F, Math.min(34.0F, fontSize));
    }

    private CustomFont getSpectrumCustomFont() {
        try {
            if (Client.getInstance() == null || Client.getInstance().moduleManager == null) {
                return null;
            }
            return (CustomFont) Client.getInstance().moduleManager.getModuleByClass(CustomFont.class);
        } catch (Throwable ignored) {
            return null;
        }
    }



    private static TrackTitleParts parseTrackTitle(String rawTitle) {
        String safeTitle = rawTitle == null ? "" : rawTitle.trim();
        if (safeTitle.isEmpty()) {
            return new TrackTitleParts("", "Jello Music");
        }

        String[] delimiters = {" - ", " – ", " — ", "-", "–", "—"};
        for (String delimiter : delimiters) {
            int splitAt = safeTitle.indexOf(delimiter);
            if (splitAt > 0 && splitAt + delimiter.length() < safeTitle.length()) {
                String artist = safeTitle.substring(0, splitAt).trim();
                String title = safeTitle.substring(splitAt + delimiter.length()).trim();
                if (!artist.isEmpty() && !title.isEmpty()) {
                    return new TrackTitleParts(artist, title);
                }
            }
        }

        return new TrackTitleParts("", safeTitle);
    }

    private static class SpectrumTextLayout {
        private float artistY;
        private float titleY;
        private float lyricY;
        private float artistSize;
        private float titleSize;
        private float lyricSize;
    }

    private static class TrackTitleParts {
        private final String artist;
        private final String title;

        private TrackTitleParts(String artist, String title) {
            this.artist = artist;
            this.title = title;
        }
    }



    @EventTarget

    public void onTick(EventUpdate event) {

        if (!this.playing) {

            this.visualizerData.clear();

            this.amplitudes.clear();

        }



        // --- Upload pending textures on the render thread (safe for OpenGL) ---
        try {
            if (!mc.isGamePaused()) {
                this.preparePendingCoverUpload();
                this.uploadNextCoverTexture();
            }
        } catch (Throwable exc) {
            // Catch all exceptions (including native wrapper errors) to prevent render thread crash
            System.err.println("[MusicManager] Texture upload failed: " + exc.getMessage());
            exc.printStackTrace();
            // Release any textures that were successfully created before the error

            if (this.songThumbnail != null) {

                try { this.songThumbnail.release(); } catch (Exception ignored) {}

            }

            if (this.notificationImage != null) {

                try { this.notificationImage.release(); } catch (Exception ignored) {}

            }

            this.songThumbnail = null;
            this.notificationImage = null;
            this.cachedTextureSongTitle = null; // allow retry on next tick
            this.cachedTextureCoverKey = null;
            this.pendingProcessedCover = null;
            this.releaseActiveCoverUpload();
            this.coverAccentColor = DEFAULT_COVER_ACCENT_COLOR;
            this.processing = false;
        }


        if (!this.processing) {

            this.startProcessingVideoThumbnail();

        }

    }

    private void preparePendingCoverUpload() {
        if (this.activeCoverUpload != null) {
            return;
        }

        ProcessedCoverResult result = this.pendingProcessedCover;
        if (result == null) {
            return;
        }

        this.pendingProcessedCover = null;
        if (!this.isCurrentCoverResult(result)) {
            this.processing = false;
            return;
        }

        if (result.coverKey != null
                && result.coverKey.equals(this.cachedTextureCoverKey)
                && this.hasReadySongArtwork()) {
            this.songTitle = result.title;
            this.cachedTextureSongTitle = result.title;
            this.coverAccentColor = sanitizeAccentColor(result.images.accentColor);
            this.currentVideo = null;
            this.processing = false;
            if (this.notificationImage != null) {
                Client.getInstance().notificationManager
                        .send(new Notification("Now Playing", this.songTitle, 7000, this.notificationImage));
            }
            return;
        }

        this.activeCoverUpload = new PendingCoverUpload(result);
    }

    private void uploadNextCoverTexture() {
        PendingCoverUpload upload = this.activeCoverUpload;
        if (upload == null) {
            return;
        }

        if (!this.isCurrentCoverResult(upload.result)) {
            this.releaseActiveCoverUpload();
            this.processing = false;
            return;
        }

        if (upload.step == 0) {
            upload.step = 1;
            try {
                upload.songThumbnail = SafeTextureUploader.upload(
                        uniqueTextureName("music_bg"), upload.result.images.backgroundStrip);
            } catch (Exception e) {
                System.err.println("[MusicManager] Failed to create songThumbnail texture: " + e.getMessage());
                e.printStackTrace();
                upload.songThumbnail = null;
            }
            return;
        }

        if (upload.step == 1) {
            upload.step = 2;
            try {
                upload.notificationImage = SafeTextureUploader.upload(
                        uniqueTextureName("music_cover"), upload.result.images.notificationCover);
            } catch (Exception e) {
                System.err.println("[MusicManager] Failed to create notificationImage texture: " + e.getMessage());
                e.printStackTrace();
                upload.notificationImage = null;
            }
            this.finishCoverUpload(upload);
        }
    }

    private void finishCoverUpload(PendingCoverUpload upload) {
        Texture oldSongThumbnail = this.songThumbnail;
        Texture oldNotificationImage = this.notificationImage;

        this.songThumbnail = upload.songThumbnail;
        this.notificationImage = upload.notificationImage;

        if (oldSongThumbnail != null && oldSongThumbnail != this.songThumbnail) {
            try { oldSongThumbnail.release(); } catch (Exception ignored) {}
        }

        if (oldNotificationImage != null && oldNotificationImage != this.notificationImage) {
            try { oldNotificationImage.release(); } catch (Exception ignored) {}
        }

        this.songTitle = upload.result.title;
        this.cachedTextureSongTitle = upload.result.title;
        this.cachedTextureCoverKey = upload.result.coverKey;
        this.coverAccentColor = sanitizeAccentColor(upload.result.images.accentColor);
        this.currentVideo = null;
        this.activeCoverUpload = null;
        this.processing = false;

        if (this.notificationImage != null) {
            Client.getInstance().notificationManager
                    .send(new Notification("Now Playing", this.songTitle, 7000, this.notificationImage));
        } else {
            Client.getInstance().notificationManager
                    .send(new Notification("Now Playing", this.songTitle));
        }
    }

    private void releaseActiveCoverUpload() {
        PendingCoverUpload upload = this.activeCoverUpload;
        this.activeCoverUpload = null;
        if (upload == null) {
            return;
        }

        if (upload.songThumbnail != null) {
            try { upload.songThumbnail.release(); } catch (Exception ignored) {}
        }

        if (upload.notificationImage != null) {
            try { upload.notificationImage.release(); } catch (Exception ignored) {}
        }
    }

    private boolean isCurrentCoverResult(ProcessedCoverResult result) {
        if (result == null) {
            return false;
        }

        String expectedKey = this.requestedCoverKey;
        return expectedKey != null
                && expectedKey.equals(result.coverKey)
                && result.generation == this.requestedCoverGeneration;
    }



    /**

     * Create a deep copy of the image as TYPE_INT_ARGB with power-of-two dimensions.

     * This guarantees:

     * 1. No shared raster from getSubimage() views (prevents GC-related native crashes)

     * 2. Predictable TYPE_INT_ARGB pixel layout for OpenGL

     * 3. Power-of-two dimensions required by OpenGL 1.x / some drivers

     */

    private static BufferedImage ensureSafeTexture(BufferedImage src) {

        if (src == null) return null;

        // Deep-copy to TYPE_INT_ARGB to avoid shared raster issues from getSubimage().

        // Do NOT pad to power-of-two here; SafeTextureUploader handles that

        // internally and stores the original image dimensions for correct UV mapping.

        BufferedImage safe = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_INT_ARGB);

        Graphics2D g = safe.createGraphics();

        g.drawImage(src, 0, 0, null);

        g.dispose();

        return safe;

    }



    private static int nextPowerOfTwo(int n) {
        if (n <= 1) return 1;
        if ((n & (n - 1)) == 0) return n; // already a power of two
        return Integer.highestOneBit(n - 1) << 1;
    }

    private static String uniqueTextureName(String prefix) {
        return prefix + "_" + System.nanoTime();
    }

    private static String buildCoverKey(YoutubeVideoData videoData) {
        if (videoData == null) {
            return "missing-cover";
        }

        String fullUrl = normalizeSongKeyPart(videoData.fullUrl);
        if (fullUrl.isEmpty()) {
            return "missing-cover";
        }

        try {
            URL url = new URL(fullUrl);
            if ("file".equalsIgnoreCase(url.getProtocol())) {
                File file = new File(url.toURI());
                return "file:" + file.getAbsolutePath()
                        + "|" + file.lastModified()
                        + "|" + file.length();
            }
        } catch (Exception ignored) {
        }

        return "url:" + fullUrl;
    }

    private static ProcessedCoverImages processedCoverCacheGet(String coverKey) {
        synchronized (PROCESSED_COVER_CACHE) {
            return PROCESSED_COVER_CACHE.get(coverKey);
        }
    }

    private static void processedCoverCachePut(String coverKey, ProcessedCoverImages images) {
        if (coverKey == null || images == null) {
            return;
        }

        synchronized (PROCESSED_COVER_CACHE) {
            PROCESSED_COVER_CACHE.put(coverKey, images);
        }
    }

    private static BufferedImage readCoverImage(String fullUrl) throws IOException {
        String normalized = normalizeSongKeyPart(fullUrl);
        if (normalized.isEmpty()) {
            return null;
        }

        URL url = new URL(normalized);
        URLConnection connection = url.openConnection();
        connection.setConnectTimeout(COVER_CONNECT_TIMEOUT_MS);
        connection.setReadTimeout(COVER_READ_TIMEOUT_MS);
        connection.setUseCaches(true);

        if (connection instanceof HttpURLConnection httpConnection) {
            httpConnection.setRequestMethod("GET");
            httpConnection.setRequestProperty("User-Agent", "Mozilla/5.0");
            int responseCode = httpConnection.getResponseCode();
            if (responseCode < 200 || responseCode >= 300) {
                httpConnection.disconnect();
                return null;
            }
        }

        try (InputStream inputStream = new BufferedInputStream(connection.getInputStream())) {
            return ImageIO.read(inputStream);
        } finally {
            if (connection instanceof HttpURLConnection httpConnection) {
                httpConnection.disconnect();
            }
        }
    }

    private static String buildSongNotificationKey(YoutubeVideoData videoData) {
        if (videoData == null) {
            return "";
        }
        if (videoData.isNeteaseTrack()) {
            return "netease:" + videoData.neteaseSongId;
        }

        String id = normalizeSongKeyPart(videoData.videoId);
        String fullUrl = normalizeSongKeyPart(videoData.fullUrl);
        String title = normalizeSongKeyPart(videoData.title);
        return id + "|" + fullUrl + "|" + title;
    }

    private static String normalizeSongKeyPart(String value) {
        return value == null ? "" : value.trim();
    }

    private YoutubeVideoData getCurrentPlaylistVideo() {
        if (this.videoManager == null
                || this.currentVideoIndex2 < 0
                || this.currentVideoIndex2 >= this.videoManager.videoList.size()) {
            return null;
        }

        return this.videoManager.videoList.get(this.currentVideoIndex2);
    }

    private void suppressNextNowPlayingForCurrentSong() {
        String songKey = buildSongNotificationKey(this.getCurrentPlaylistVideo());
        this.seekNotificationSuppressionKey = songKey.isEmpty() ? null : songKey;
    }

    private boolean shouldSuppressNowPlayingForSeek(YoutubeVideoData videoData) {
        String songKey = buildSongNotificationKey(videoData);
        boolean suppress = songKey != null
                && !songKey.isEmpty()
                && songKey.equals(this.seekNotificationSuppressionKey);
        this.seekNotificationSuppressionKey = null;
        return suppress;
    }

    private void sendNowPlayingNotification(YoutubeVideoData videoData) {
        if (videoData == null || this.shouldSuppressNowPlayingForSeek(videoData)) {
            return;
        }

        Client.getInstance().notificationManager
                .send(new Notification("Now Playing", videoData.title));
    }

    private static boolean isTextureReady(Texture texture) {
        if (texture == null) {
            return false;
        }
        try {
            return texture.getTextureID() > 0 && texture.getImageWidth() > 1 && texture.getImageHeight() > 1;
        } catch (Exception ignored) {
            return false;
        }
    }

    public boolean hasReadySongArtwork() {
        return isTextureReady(this.songThumbnail) && isTextureReady(this.notificationImage);
    }

    public int getDynamicCoverColor() {
        // Guard fallback for early frames where async artwork upload is not finished yet.
        return this.hasReadySongArtwork() ? this.coverAccentColor : DEFAULT_COVER_ACCENT_COLOR;
    }

    private static int sanitizeAccentColor(Integer color) {
        if (color == null) {
            return DEFAULT_COVER_ACCENT_COLOR;
        }

        int argb = color | 0xFF000000;
        Color awt = new Color(argb, true);
        float[] hsb = Color.RGBtoHSB(awt.getRed(), awt.getGreen(), awt.getBlue(), null);
        // If the picked color is too gray/too dark/too bright, use the neutral pink fallback.
        if (hsb[1] < 0.15F || hsb[2] < 0.1F || hsb[2] > 0.95F) {
            return DEFAULT_COVER_ACCENT_COLOR;
        }
        return argb;
    }

    private static BufferedImage createDefaultCoverImage() {
        BufferedImage fallback = new BufferedImage(COVER_PROCESS_SIZE, COVER_PROCESS_SIZE, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = fallback.createGraphics();
        g.setColor(new Color(DEFAULT_COVER_ACCENT_COLOR, true));
        g.fillRect(0, 0, fallback.getWidth(), fallback.getHeight());
        g.dispose();
        return fallback;
    }

    private static BufferedImage copyRegionToArgb(BufferedImage source, int x, int y, int width, int height) {
        BufferedImage copy = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = copy.createGraphics();
        g.drawImage(source, 0, 0, width, height, x, y, x + width, y + height, null);
        g.dispose();
        return copy;
    }

    private static BufferedImage normalizeCoverForSampling(BufferedImage source, String title) {
        if (source == null || source.getWidth() <= 1 || source.getHeight() <= 1) {
            return createDefaultCoverImage();
        }

        int width = source.getWidth();
        int height = source.getHeight();
        if (title != null && title.contains("[NCS Release]") && width >= 173 && height >= 173) {
            // Keep legacy NCS crop behavior, but copy into a detached ARGB buffer.
            return copyRegionToArgb(source, 1, 3, 170, 170);
        }

        return source;
    }

    private static BufferedImage createBottomStripSample(BufferedImage blurredSquare) {
        int blurH = blurredSquare.getHeight();
        int blurW = blurredSquare.getWidth();
        int subY = Math.min((int) (blurH * 0.75F), blurH - 1);
        int subH = Math.max(1, Math.min((int) (blurH * 0.2F), blurH - subY));
        return copyRegionToArgb(blurredSquare, 0, subY, blurW, subH);
    }

    private static boolean isGreenHue(float hueDegrees) {
        return hueDegrees >= 90.0F && hueDegrees <= 150.0F;
    }

    private static int extractDominantCoverColor(BufferedImage coverImage) {
        if (coverImage == null || coverImage.getWidth() <= 1 || coverImage.getHeight() <= 1) {
            return DEFAULT_COVER_ACCENT_COLOR;
        }

        Map<Integer, ColorBucket> buckets = new HashMap<>();
        int visiblePixelCount = 0;
        int validCandidateCount = 0;
        int greenCandidateCount = 0;
        int width = coverImage.getWidth();
        int height = coverImage.getHeight();
        int step = Math.max(1, Math.min(width, height) / 64);

        for (int y = 0; y < height; y += step) {
            for (int x = 0; x < width; x += step) {
                int argb = coverImage.getRGB(x, y);
                int alpha = (argb >> 24) & 0xFF;
                // Skip mostly-transparent pixels so blank fallback images do not pollute the palette.
                if (alpha < 128) {
                    continue;
                }
                visiblePixelCount++;

                int r = (argb >> 16) & 0xFF;
                int g = (argb >> 8) & 0xFF;
                int b = argb & 0xFF;
                float[] hsb = Color.RGBtoHSB(r, g, b, null);
                float sat = hsb[1];
                float bri = hsb[2];
                float hue = hsb[0] * 360.0F;

                // Filter gray/black noise before scoring. This keeps UI background and empty pixels out.
                if (sat < 0.15F || bri < 0.08F) {
                    continue;
                }

                validCandidateCount++;
                if (isGreenHue(hue)) {
                    greenCandidateCount++;
                }

                int quantizedKey = ((r >> 4) << 8) | ((g >> 4) << 4) | (b >> 4);
                ColorBucket bucket = buckets.computeIfAbsent(quantizedKey, ignored -> new ColorBucket());
                bucket.add(r, g, b, sat, bri, hue);
            }
        }

        // A fully transparent image means there was no usable artwork yet, so return the pink fallback directly.
        if (visiblePixelCount <= 0) {
            return DEFAULT_COVER_ACCENT_COLOR;
        }

        if (validCandidateCount <= 0 || buckets.isEmpty()) {
            return DEFAULT_COVER_ACCENT_COLOR;
        }

        // Only allow green-dominant outputs when the cover itself is genuinely green-heavy.
        boolean coverLooksGreen = greenCandidateCount >= Math.max(4, (int) (validCandidateCount * 0.35F));
        ColorBucket best = null;
        ColorBucket bestGreen = null;
        for (ColorBucket bucket : buckets.values()) {
            if (!coverLooksGreen && bucket.greenSamples > bucket.count * 0.6F) {
                if (bestGreen == null
                        || bucket.getAverageSaturation() > bestGreen.getAverageSaturation()
                        || (Math.abs(bucket.getAverageSaturation() - bestGreen.getAverageSaturation()) < 1.0E-4F
                        && bucket.count > bestGreen.count)) {
                    bestGreen = bucket;
                }
                continue;
            }

            // Pick the most saturated remaining bucket first; frequency only breaks ties.
            if (best == null
                    || bucket.getAverageSaturation() > best.getAverageSaturation()
                    || (Math.abs(bucket.getAverageSaturation() - best.getAverageSaturation()) < 1.0E-4F
                    && bucket.count > best.count)
                    || (Math.abs(bucket.getAverageSaturation() - best.getAverageSaturation()) < 1.0E-4F
                    && bucket.count == best.count
                    && bucket.getAverageBrightness() > best.getAverageBrightness())) {
                best = bucket;
            }
        }

        if (best == null) {
            best = bestGreen;
        }

        if (best == null) {
            return DEFAULT_COVER_ACCENT_COLOR;
        }

        return sanitizeAccentColor(best.toArgb());
    }

    private static final class ColorBucket {
        private int count;
        private int greenSamples;
        private long rSum;
        private long gSum;
        private long bSum;
        private float saturationSum;
        private float brightnessSum;

        private void add(int r, int g, int b, float sat, float bri, float hue) {
            this.count++;
            this.rSum += r;
            this.gSum += g;
            this.bSum += b;
            this.saturationSum += sat;
            this.brightnessSum += bri;
            if (isGreenHue(hue)) {
                this.greenSamples++;
            }
        }

        private float getAverageSaturation() {
            return this.count <= 0 ? 0.0F : this.saturationSum / (float) this.count;
        }

        private float getAverageBrightness() {
            return this.count <= 0 ? 0.0F : this.brightnessSum / (float) this.count;
        }

        private int toArgb() {
            if (this.count <= 0) {
                return DEFAULT_COVER_ACCENT_COLOR;
            }
            int r = (int) Math.max(0, Math.min(255, this.rSum / this.count));
            int g = (int) Math.max(0, Math.min(255, this.gSum / this.count));
            int b = (int) Math.max(0, Math.min(255, this.bSum / this.count));
            return 0xFF000000 | (r << 16) | (g << 8) | b;
        }
    }

    private void startProcessingVideoThumbnail() {
        this.startProcessingVideoThumbnail(this.currentVideo);
    }

    private void startProcessingVideoThumbnail(YoutubeVideoData videoData) {
        if (videoData != null) {
            String coverKey = buildCoverKey(videoData);
            int generation = this.coverGeneration.incrementAndGet();
            this.requestedCoverKey = coverKey;
            this.requestedCoverGeneration = generation;
            this.visualizerData.clear();

            if (this.processing) {
                return;
            }

            // Mark processing before starting thread to prevent duplicate workers racing each other.
            this.processing = true;
            Thread worker = new Thread(
                    () -> this.processVideoThumbnail(videoData, coverKey, generation),
                    "MusicCoverProcessor");
            worker.setDaemon(true);
            worker.start();
        }
    }


    private void initializeAudioPlayback() {

        this.visualizerData.clear();

        if (this.videoManager != null) {

            while (this.audioThread != null && this.audioThread.isAlive()) {

                this.audioThread.interrupt();

            }



            this.audioThread = new Thread(

                    () -> {

                        byte[] pcmBufferData;

                        if (this.currentVideoIndex < 0

                                || this.currentVideoIndex >= this.videoManager.videoList.size()) {

                            this.currentVideoIndex = 0;

                        }



                        for (int index = this.currentVideoIndex; index < this.videoManager.videoList.size(); index++) {

                            URL songUrl;

                            YoutubeVideoData videoData = this.videoManager.videoList.get(index);



                            // 延迟解析 netease:// 占位URL为真实播放URL

                            String videoUrl = videoData.videoId;

                            if (videoUrl != null && videoUrl.startsWith("netease://")) {

                                try {

                                    long neteaseId = Long.parseLong(videoUrl.substring("netease://".length()));

                                    System.out.println("[MusicManager] Resolving Netease song URL for ID: " + neteaseId);

                                    String realUrl = NeteaseApiSearch.getSongUrl(neteaseId);

                                    if (realUrl == null || realUrl.isEmpty()) {

                                        System.err.println("[MusicManager] Failed to get song URL for Netease ID: " + neteaseId);

                                        continue;

                                    }

                                    videoUrl = realUrl;

                                    // 缓存已解析的URL避免重复请求

                                    videoData.videoId = realUrl;

                                } catch (NumberFormatException e) {

                                    System.err.println("[MusicManager] Invalid Netease song ID: " + videoUrl);

                                    continue;

                                }

                            }



                            // 支持 file:, http:, https: URL

                            try {

                                songUrl = new URL(videoUrl);

                            } catch (MalformedURLException e) {

                                System.err.println("[MusicManager] Invalid URL: " + videoUrl);

                                continue;

                            }



                            this.currentVideoIndex2 = index;

                            this.currentVideo = this.videoManager.videoList.get(index);

                            this.visualizerData.clear();



                            while (!this.playing) {

                                try {

                                    Thread.sleep(300L);

                                } catch (final InterruptedException ignored) {

                                }



                                double[] var6 = new double[0];

                                this.visualizerData.clear();

                                if (Thread.interrupted()) {

                                    if (this.sourceDataLine != null) {

                                        this.sourceDataLine.close();

                                    }



                                    return;

                                }

                            }



                            try {

                                URL url = this.resolveAudioStream(songUrl);

                                if (url != null) {

                                    // Use JLayer MP3 decoding for file: and http(s): streams

                                    boolean isMp3Stream = url.getProtocol().equals("file")

                                            || url.getProtocol().equals("http")

                                            || url.getProtocol().equals("https");

                                    if (isMp3Stream) {

                                        try {

                                            InputStream fileStream = url.openStream();

                                            javazoom.jl.decoder.Bitstream bitstream = new javazoom.jl.decoder.Bitstream(

                                                    fileStream);

                                            javazoom.jl.decoder.Decoder mp3Decoder = new javazoom.jl.decoder.Decoder();



                                            this.songTitle = videoData.title;

                                            this.startProcessingVideoThumbnail(videoData);

                                            this.sendNowPlayingNotification(videoData);



                                            // Load lyrics for local files

                                            if (url.getProtocol().equals("file")) {

                                                try {

                                                    File audioFile = new File(url.toURI());

                                                    String name = audioFile.getName();

                                                    String lrcName = name.substring(0, name.lastIndexOf('.')) + ".lrc";

                                                    File lrcFile = new File(audioFile.getParent(), lrcName);

                                                    if (lrcFile.exists()) {

                                                        this.currentLyrics = LrcParser.parse(lrcFile);

                                                    } else {

                                                        this.currentLyrics = null;

                                                    }

                                                } catch (Exception e) {

                                                    e.printStackTrace();

                                                    this.currentLyrics = null;

                                                }

                                            } else {

                                                // For Netease streams: load lyrics via API using song ID

                                                if (videoData.isNeteaseTrack()) {

                                                    try {

                                                        String lrcText = NeteaseApiSearch.getLyrics(videoData.neteaseSongId);

                                                        if (lrcText != null && !lrcText.isEmpty()) {

                                                            this.currentLyrics = LrcParser.parseString(lrcText);

                                                            System.out.println("[MusicManager] Loaded Netease lyrics for song ID: " + videoData.neteaseSongId

                                                                    + " (" + (this.currentLyrics != null ? this.currentLyrics.size() : 0) + " lines)");

                                                        } else {

                                                            this.currentLyrics = null;

                                                        }

                                                    } catch (Exception e) {

                                                        System.err.println("[MusicManager] Failed to load Netease lyrics: " + e.getMessage());

                                                        this.currentLyrics = null;

                                                    }

                                                } else {

                                                    this.currentLyrics = null;

                                                }

                                            }



                                            // Estimate duration

                                            if (url.getProtocol().equals("file")) {

                                                java.io.File audioFile = new java.io.File(new java.net.URI(url.toString()));

                                                long fileSize = audioFile.length();

                                                // Estimate ~128kbps bitrate -> duration in seconds = fileSize * 8 / 128000

                                                this.duration = (fileSize * 8) / 128000;

                                            } else {

                                                // For HTTP streams: use Netease duration if available

                                                if (videoData.isNeteaseTrack() && videoData.neteaseDurationMs > 0) {

                                                    this.duration = videoData.neteaseDurationMs / 1000;

                                                } else {

                                                    this.duration = 300; // 5 min default

                                                }

                                            }



                                            // Read first frame to get actual sample rate

                                            javazoom.jl.decoder.Header firstHeader = bitstream.readFrame();

                                            if (firstHeader == null) {

                                                Client.getInstance().notificationManager

                                                        .send(new Notification("Error", "Invalid MP3 file"));

                                                continue;

                                            }



                                            int sampleRate = firstHeader.frequency();

                                            int channels = (firstHeader

                                                    .mode() == javazoom.jl.decoder.Header.SINGLE_CHANNEL) ? 1 : 2;



                                            // Set up audio output with correct sample rate

                                            AudioFormat mp3Format = new AudioFormat(sampleRate, 16, channels, true,

                                                    false);

                                            this.sourceDataLine = AudioSystem.getSourceDataLine(mp3Format);

                                            this.sourceDataLine.open(mp3Format);

                                            this.sourceDataLine.start();



                                            // Start playback state

                                            this.playing = true;



                                            javazoom.jl.decoder.Header frameHeader = firstHeader;

                                            int frameCount = 0;

                                            float msPerFrame = firstHeader.ms_per_frame();

                                            byte[] pcmBytes = null; // Initialize pcmBytes here



                                            // Handle seek: skip frames to target position

                                            if (this.mp3SeekTargetSec >= 0 && msPerFrame > 0) {

                                                int targetFrame = (int) (this.mp3SeekTargetSec * 1000 / msPerFrame);

                                                bitstream.closeFrame(); // close the first frame

                                                for (int skipI = 1; skipI < targetFrame; skipI++) {

                                                    javazoom.jl.decoder.Header skipHeader = bitstream.readFrame();

                                                    if (skipHeader == null)

                                                        break;

                                                    bitstream.closeFrame();

                                                }

                                                // Read the frame we'll start playing from

                                                frameHeader = bitstream.readFrame();

                                                if (frameHeader == null) {

                                                    this.mp3SeekTargetSec = -1;

                                                    this.sourceDataLine.close();

                                                    bitstream.close();

                                                    fileStream.close();

                                                    continue;

                                                }

                                                frameCount = targetFrame;

                                                this.totalDuration = this.mp3SeekTargetSec;

                                                this.currentPositionMs = this.mp3SeekTargetSec * 1000;

                                                this.mp3SeekTargetSec = -1;

                                            }



                                            do {

                                                if (this.currentVideoIndex2 != this.currentVideoIndex) {

                                                    this.sourceDataLine.close();

                                                    bitstream.close();

                                                    return;

                                                }



                                                if (this.sourceDataLine == null) {

                                                    AudioFormat audioFormat = new AudioFormat(

                                                            (float) frameHeader.frequency(),

                                                            16,

                                                            frameHeader.mode() == 3 ? 1 : 2,

                                                            true,

                                                            false);

                                                    this.sourceDataLine = AudioSystem.getSourceDataLine(audioFormat);

                                                    this.sourceDataLine.open(audioFormat);

                                                    this.sourceDataLine.start();

                                                }



                                                // Check for pause

                                                while (!this.playing) {

                                                    Thread.sleep(300L);

                                                    this.visualizerData.clear();

                                                    if (Thread.interrupted()) {

                                                        this.sourceDataLine.close();

                                                        bitstream.close();

                                                        this.playing = false; // Reset playing state on interrupt

                                                        return;

                                                    }

                                                }



                                                // Decode frame

                                                javazoom.jl.decoder.SampleBuffer output = (javazoom.jl.decoder.SampleBuffer) mp3Decoder

                                                        .decodeFrame(frameHeader, bitstream);

                                                short[] samples = output.getBuffer();

                                                int len = output.getBufferLength();



                                                if (pcmBytes == null || pcmBytes.length != len * 2) {

                                                    pcmBytes = new byte[len * 2];

                                                    mp3Format = new AudioFormat( // Re-initialize mp3Format if needed

                                                            (float) frameHeader.frequency(),

                                                            16,

                                                            frameHeader.mode() == 3 ? 1 : 2,

                                                            true,

                                                            false);

                                                }



                                                for (int i = 0; i < len; i++) {

                                                    pcmBytes[i * 2] = (byte) (samples[i] & 0xFF);

                                                    pcmBytes[i * 2 + 1] = (byte) ((samples[i] >> 8) & 0xFF);

                                                }



                                                this.sourceDataLine.write(pcmBytes, 0, pcmBytes.length);



                                                // Update progress

                                                if (msPerFrame == 0) {

                                                    msPerFrame = frameHeader.ms_per_frame();

                                                }

                                                frameCount++;

                                                this.totalDuration = (long) ((frameCount * msPerFrame) / 1000);

                                                this.currentPositionMs = (long) (frameCount * msPerFrame);

                                                this.field32170 = this.totalDuration;



                                                // Visualizer logic

                                                float[] pcmFloat = MathHelper.convertToPCMFloatArray(pcmBytes,

                                                        mp3Format);



                                                // Pad to next power of 2 for FFT

                                                int n = pcmFloat.length;

                                                if (n >= 2) {

                                                    int p = 2;

                                                    while (p < n)

                                                        p <<= 1;



                                                    float[] paddedPcm = new float[p];

                                                    System.arraycopy(pcmFloat, 0, paddedPcm, 0, n);



                                                    JavaFFT fft = new JavaFFT(paddedPcm.length);

                                                    float[][] transformed = fft.transform(paddedPcm);

                                                    float[] fftLeft = transformed[0];

                                                    float[] fftRight = transformed[1];



                                                    this.visualizerData

                                                            .add(MathHelper.calculateAmplitudes(fftLeft, fftRight));

                                                    if (this.visualizerData.size() > 18) {

                                                        this.visualizerData.remove(0);

                                                    }

                                                }



                                                // Volume control

                                                this.adjustAudioVolume(this.sourceDataLine, this.volume);



                                                // Check for stop

                                                if (Thread.interrupted()) {

                                                    this.sourceDataLine.close();

                                                    bitstream.close();

                                                    this.playing = false; // Reset playing state on interrupt

                                                    return;

                                                }



                                                bitstream.closeFrame();

                                            } while ((frameHeader = bitstream.readFrame()) != null);



                                            this.sourceDataLine.drain();

                                            this.sourceDataLine.close();

                                            bitstream.close();

                                            fileStream.close();

                                            this.playing = false; // Reset playing state after loop finishes

                                        } catch (Exception e) {

                                            this.playing = false; // Reset playing state on exception

                                            e.printStackTrace();

                                            Client.getInstance().notificationManager

                                                    .send(new Notification("Error",

                                                            "Failed to play MP3: " + e.getMessage()));

                                        }

                                        continue; // Move to next track

                                    }



                                    // Otherwise use MP4Container for YouTube streams (AAC)

                                    URLConnection connection = url.openConnection();

                                    connection.setConnectTimeout(14000);

                                    connection.setReadTimeout(14000);

                                    connection.setUseCaches(true);

                                    connection.setDoOutput(true);

                                    connection.setRequestProperty("Connection", "Keep-Alive");



                                    InputStream iS = connection.getInputStream();

                                    MusicStream mS = new MusicStream(iS, new BasicAudioProcessor());



                                    MP4Container container = new MP4Container(mS);

                                    Movie movie = container.getMovie();

                                    List<Track> tracks = movie.getTracks();



                                    AudioTrack var13 = (AudioTrack) movie.getTracks().get(1);

                                    AudioFormat var14 = new AudioFormat((float) var13.getSampleRate(),

                                            var13.getSampleSize(), var13.getChannelCount(), true, true);

                                    this.sourceDataLine = AudioSystem.getSourceDataLine(var14);

                                    this.sourceDataLine.open();

                                    this.sourceDataLine.start();

                                    this.duration = (long) movie.getDuration();



                                    if (this.duration > 1300L) {

                                        mS.close();

                                        Client.getInstance().notificationManager

                                                .send(new Notification("Now Playing", "Music is too long."));

                                    }



                                    Decoder var15 = new Decoder(var13.getDecoderSpecificInfo());

                                    SampleBuffer var16 = new SampleBuffer();



                                    while (var13.hasMoreFrames()) {

                                        while (!this.playing) {

                                            Thread.sleep(300L);

                                            this.visualizerData.clear();

                                            if (Thread.interrupted()) {

                                                this.sourceDataLine.close();

                                                return;

                                            }

                                        }



                                        Frame var18 = var13.readNextFrame();

                                        var15.decodeFrame(var18.getData(), var16);

                                        pcmBufferData = var16.getData();



                                        this.sourceDataLine.write(pcmBufferData, 0, pcmBufferData.length);

                                        float[] var29 = MathHelper.convertToPCMFloatArray(var16.getData(), var14);



                                        JavaFFT var19 = new JavaFFT(var29.length);



                                        float[][] var20 = var19.transform(var29);

                                        float[] var21 = var20[0];

                                        float[] var22 = var20[1];



                                        this.visualizerData.add(MathHelper.calculateAmplitudes(var21, var22));

                                        if (this.visualizerData.size() > 18) {

                                            this.visualizerData.remove(0);

                                        }



                                        this.adjustAudioVolume(this.sourceDataLine, this.volume);

                                        if (!Thread.interrupted()) {

                                            this.totalDuration = Math.round(var13.getNextTimeStamp());

                                            this.field32170 = var13.method23326();

                                            if (this.field32169) {

                                                var13.seek(this.field32168);

                                                this.totalDuration = (long) this.field32168;

                                                this.field32169 = false;

                                            }

                                        }



                                        if (!var13.hasMoreFrames()

                                                && (this.repeat == AudioRepeatMode.LOOP_CURRENT

                                                        || this.repeat == AudioRepeatMode.REPEAT

                                                                && this.videoManager.videoList.size() == 1)) {

                                            var13.seek(0.0);

                                            this.totalDuration = 0L;

                                        }



                                        if (Thread.interrupted()) {

                                            this.sourceDataLine.close();

                                            return;

                                        }

                                    }



                                    this.sourceDataLine.close();

                                    mS.close();

                                } else {

                                    Thread.sleep(1000L);

                                }

                            } catch (IOException exc) {

                                System.err.println("[MusicManager] IO error: " + exc.getMessage());

                                exc.printStackTrace();

                            } catch (LineUnavailableException | InterruptedException exc) {

                                throw new RuntimeException(exc);

                            }



                            if (this.repeat == AudioRepeatMode.LOOP_CURRENT) {

                                index--;

                            } else if (this.repeat == AudioRepeatMode.REPEAT

                                    && index == this.videoManager.videoList.size() - 1) {

                                index = -1;

                            } else if (this.repeat == AudioRepeatMode.NO_REPEAT) {

                                return;

                            }



                            if (index >= this.videoManager.videoList.size()) {

                                index = 0;

                            }

                        }

                    });

            this.audioThread.start();

        }

    }



    public void setRepeat(AudioRepeatMode repeatMode) {

        this.repeat = repeatMode;

        this.saveMusicSettings();

    }



    public AudioRepeatMode getRepeat() {

        return this.repeat;

    }



    public void processVideoThumbnail(YoutubeVideoData videoData) {
        String coverKey = buildCoverKey(videoData);
        int generation = this.coverGeneration.incrementAndGet();
        this.requestedCoverKey = coverKey;
        this.requestedCoverGeneration = generation;
        this.processVideoThumbnail(videoData, coverKey, generation);
    }

    private void processVideoThumbnail(YoutubeVideoData videoData, String coverKey, int generation) {
        try {
            if (videoData == null) {
                this.processing = false;
                return;
            }

            String title = videoData.title;
            ProcessedCoverImages images = processedCoverCacheGet(coverKey);
            if (images == null) {
                BufferedImage buffImage = readCoverImage(videoData.fullUrl);
                if (buffImage == null) {
                    buffImage = createDefaultCoverImage();
                }

                BufferedImage normalizedCover = normalizeCoverForSampling(buffImage, title);
                BufferedImage workingCover = ImageUtil.resizeCover(normalizedCover, COVER_PROCESS_SIZE);
                if (workingCover == null) {
                    workingCover = createDefaultCoverImage();
                }

                BufferedImage blurred = ImageUtil.applyBlur(workingCover, 15);
                if (blurred == null) {
                    blurred = workingCover;
                }

                BufferedImage thumbSub = createBottomStripSample(blurred);
                BufferedImage sampledCover = ImageUtil.resizeCover(
                        workingCover, COVER_NOTIFICATION_SIZE, COVER_NOTIFICATION_SIZE);
                if (sampledCover == null) {
                    sampledCover = ImageUtil.resizeCover(
                            createDefaultCoverImage(), COVER_NOTIFICATION_SIZE, COVER_NOTIFICATION_SIZE);
                }

                int extractedAccent = extractDominantCoverColor(workingCover);
                images = new ProcessedCoverImages(
                        ensureSafeTexture(thumbSub),
                        ensureSafeTexture(sampledCover),
                        extractedAccent);
                processedCoverCachePut(coverKey, images);
            }

            // Async guard: do not let stale worker results override a newer song's artwork/color.
            if (!coverKey.equals(this.requestedCoverKey) || generation != this.requestedCoverGeneration) {
                this.processing = false;
                return;
            }

            // Store results for the render thread to pick up (no GL calls here!)
            this.pendingProcessedCover = new ProcessedCoverResult(title, coverKey, generation, images);
        } catch (Exception var5) {
            var5.printStackTrace();
            this.processing = false;
        }
    }



    public void setPlaying(boolean playing) {

        if (!playing && this.sourceDataLine != null) {

            this.sourceDataLine.flush();

        }



        this.playing = playing;

    }



    public void setVolume(int var1) {

        this.volume = var1;

        this.saveMusicSettings();

    }



    public void setSpectrum(boolean var1) {

        this.spectrum = var1;

        this.saveMusicSettings();

    }



    public boolean isSpectrum() {

        return this.spectrum;

    }



    public int getVolume() {

        return this.volume;

    }



    public void playPreviousSong() {

        if (this.videoManager != null) {

            this.seekNotificationSuppressionKey = null;

            this.currentVideoIndex = this.currentVideoIndex2 - 1;

            this.totalDuration = 0L;

            this.field32170 = 0.0;

            this.initializeAudioPlayback();

        }

    }



    public void playNextSong() {

        if (this.videoManager != null) {

            this.seekNotificationSuppressionKey = null;

            this.currentVideoIndex = this.currentVideoIndex2 + 1;

            this.totalDuration = 0L;

            this.field32170 = 0.0;

            this.initializeAudioPlayback();

        }

    }



    public void playSong(Thumbnails vidManager, YoutubeVideoData videoData) {

        this.seekNotificationSuppressionKey = null;

        if (vidManager == null) {

            vidManager = new Thumbnails("temp", "temp", YoutubeContentType.PLAYLIST);

            vidManager.videoList.add(videoData);

        }



        this.videoManager = vidManager;

        this.playing = true;

        this.totalDuration = 0L;

        this.field32170 = 0.0;



        for (int i = 0; i < vidManager.videoList.size(); i++) {

            if (vidManager.videoList.get(i) == videoData) {

                this.currentVideoIndex = i;

            }

        }



        this.initializeAudioPlayback();

    }



    public boolean isPlayingSong() {

        return this.playing;

    }



    public long getDuration() {

        return this.totalDuration;

    }



    public double method24322() {

        return this.field32170;

    }



    public URL resolveAudioStream(URL songURL) {

        if (songURL.getProtocol().equals("file")) {

            return songURL;

        }

        // 对于网易云音乐等 HTTP/HTTPS URL 直接返回

        if (songURL.getProtocol().equals("http") || songURL.getProtocol().equals("https")) {

            return songURL;

        }

        System.out.println("[MusicManager] Unsupported URL protocol: " + songURL.getProtocol());

        return null;

    }



    public String getSongTitle() {

        return this.songTitle;

    }



    public Texture getSongThumbnail() {

        return this.songThumbnail;

    }



    public String getCurrentLyric() {

        if (currentLyrics == null || currentLyrics.isEmpty())

            return "";

        long current = this.currentPositionMs;

        for (int i = 0; i < currentLyrics.size(); i++) {

            if (currentLyrics.get(i).timestamp > current) {

                return i > 0 ? currentLyrics.get(i - 1).content : "";

            }

        }

        return currentLyrics.get(currentLyrics.size() - 1).content;

    }



    public float getLyricProgress() {

        if (currentLyrics == null || currentLyrics.isEmpty())

            return 0.0f;

        long current = this.currentPositionMs;

        for (int i = 0; i < currentLyrics.size(); i++) {

            if (currentLyrics.get(i).timestamp > current) {

                if (i == 0)

                    return 0.0f;

                long start = currentLyrics.get(i - 1).timestamp;

                long end = currentLyrics.get(i).timestamp;

                float progress = (float) (current - start) / (float) (end - start);

                return Math.max(0.0f, Math.min(1.0f, progress));

            }

        }

        return 1.0f;

    }



    public Texture getNotificationImage() {

        return this.notificationImage;

    }



    public int getDurationInt() {

        return (int) this.duration;

    }



    private void adjustAudioVolume(SourceDataLine var1, int var2) {

        try {

            FloatControl var5 = (FloatControl) var1.getControl(Type.MASTER_GAIN);

            BooleanControl var6 = (BooleanControl) var1.getControl(javax.sound.sampled.BooleanControl.Type.MUTE);

            if (var2 == 0) {

                var6.setValue(true);

            } else {

                var6.setValue(false);

                var5.setValue((float) (Math.log((double) var2 / 100.0) / Math.log(10.0) * 20.0));

            }

        } catch (Exception ignored) {

        }

    }



    public void setDuration(double duration) {

        double targetDuration = this.duration > 0L

                ? Math.min(Math.max(duration, 0.0), (double) this.duration)

                : Math.max(duration, 0.0);

        this.field32168 = targetDuration;

        this.totalDuration = (long) this.field32168;

        this.currentPositionMs = this.totalDuration * 1000;

        this.field32170 = this.totalDuration;

        this.field32169 = true;

        if (this.videoManager != null

                && this.currentVideoIndex2 >= 0

                && this.currentVideoIndex2 < this.videoManager.videoList.size()) {

            this.currentVideoIndex = this.currentVideoIndex2;

        }

        this.suppressNextNowPlayingForCurrentSong();



        // For MP3: seek by interrupting and restarting with a target frame

        this.mp3SeekTargetSec = this.totalDuration;

        // Keep playing state so seek doesn't pause

        boolean wasPlaying = this.playing;

        this.initializeAudioPlayback();

        if (wasPlaying) {

            this.playing = true;

        }

    }



    /** @deprecated YouTube support removed; kept as stub for compatibility. */

    public boolean doesYTDLPExist() {

        return false;

    }



    /** @deprecated YouTube support removed. */

    public void setupDownloadThread() {

        // No-op: yt-dlp downloads removed

    }



    /** @deprecated YouTube support removed. */

    public void download() {

        // No-op: yt-dlp downloads removed

    }



    /** @deprecated YouTube support removed. */

    public String prepareYtDlpExecutable() {

        return "";

    }



    /** @deprecated YouTube support removed. */

    public boolean hasPython() {

        return false;

    }



    /** @deprecated YouTube support removed. */

    public boolean hasVCRedist() {

        return true;

    }

}
