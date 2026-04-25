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
import com.mentalfrostbyte.jello.util.client.ClientMode;
import com.mentalfrostbyte.jello.util.client.music.JavaFFT;
import com.mentalfrostbyte.jello.util.client.network.youtube.YoutubeContentType;
import com.mentalfrostbyte.jello.util.client.network.youtube.YoutubeVideoData;
import com.mentalfrostbyte.jello.util.client.render.ResourceRegistry;
import com.mentalfrostbyte.jello.util.client.music.LrcParser;
import com.mentalfrostbyte.jello.util.client.network.netease.NeteaseApiLogin;
import com.mentalfrostbyte.jello.util.client.network.netease.NeteaseApiSearch;
import com.mentalfrostbyte.jello.util.client.render.NanoVGFontRenderer;
import com.mentalfrostbyte.jello.util.client.render.theme.ClientColors;
import com.mentalfrostbyte.jello.util.game.MinecraftUtil;
import com.mentalfrostbyte.jello.util.game.render.RenderUtil;
import com.mentalfrostbyte.jello.util.game.render.RenderUtil2;
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
import net.minecraft.client.Minecraft;
import net.minecraft.util.Util;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import com.mentalfrostbyte.jello.util.game.render.SafeTextureUploader;
import team.sdhq.eventBus.annotations.EventTarget;

import javax.imageio.ImageIO;
import javax.sound.sampled.*;
import javax.sound.sampled.FloatControl.Type;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

public class MusicManager extends Manager implements MinecraftUtil {
    // Use a neutral pink fallback instead of green when artwork color extraction fails.
    private static final int DEFAULT_COVER_ACCENT_COLOR = 0xFFFF85C2;
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
    // Must be atomic: touched by Render thread (onTick) and MusicCoverProcessor worker thread.
    private final AtomicBoolean processing = new AtomicBoolean(false);
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

    // --- Texture cache to avoid re-creating every tick & prevent native crash ---
    // Three-key cover state machine, all written only with the render thread or under CAS:
    //   requestedCoverKey : the latest desired cover (refreshed on every song switch).
    //   pendingCoverKey   : a worker-produced result awaiting GL upload on the render thread.
    //   appliedCoverKey   : the cover key whose textures are currently live on-screen.
    // A new worker is spawned only when requestedCoverKey != appliedCoverKey, and worker
    // results are dropped when coverKey != requestedCoverKey (stale). This decouples the
    // "did cover change?" decision from song title equality and removes the old
    // `currentVideo==null` upload gate that caused lost updates on fast song switches.
    /** Pending BufferedImage from worker thread, to be uploaded on render thread */
    private volatile BufferedImage pendingThumbnailImage = null;
    private volatile BufferedImage pendingScaledThumbnail = null;
    private volatile Integer pendingCoverAccentColor = null;
    private volatile String pendingSongTitle = null;
    private volatile String pendingCoverKey = null;
    private volatile String requestedCoverKey = null;
    private volatile String appliedCoverKey = null;
    private volatile int coverAccentColor = DEFAULT_COVER_ACCENT_COLOR;

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

        // Initialize NanoVG font renderer for CJK lyrics
        NanoVGFontRenderer.init();
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
            // Snapshot the oldest amplitude frame under lock so audio-thread writes don't race
            // with this read (isEmpty()+get(0) was a TOCTOU bug that threw IOOBE on fast resets).
            double[] var4 = null;
            if (this.playing) {
                synchronized (this.visualizerData) {
                    if (!this.visualizerData.isEmpty()) {
                        var4 = this.visualizerData.get(0);
                    }
                }
            }
            if (var4 != null) {
                if (this.amplitudes.isEmpty()) {
                    for (double v : var4) {
                        if (this.amplitudes.size() < 1024) {
                            this.amplitudes.add(v);
                        }
                    }
                }

                float fps = 60.0F / (float) Minecraft.getFps();

                for (int i = 0; i < var4.length; i++) {
                    if (i >= this.amplitudes.size()) break;
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
        boolean hasVisualData;
        synchronized (this.visualizerData) {
            hasVisualData = !this.visualizerData.isEmpty();
        }
        if (this.playing && hasVisualData && this.spectrum) {
            // Save items not covered by the attrib stack
            int prevProgram   = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
            int prevFBO       = GL11.glGetInteger(GL30.GL_FRAMEBUFFER_BINDING);
            int prevTex       = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
            int prevActiveTex = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);

            GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
            GL11.glPushClientAttrib(GL11.GL_CLIENT_ALL_ATTRIB_BITS);
            try {
                this.renderSpectrum();
            } finally {
                GL11.glPopClientAttrib();
                GL11.glPopAttrib();

                // Restore items outside the attrib stack
                GL20.glUseProgram(prevProgram);
                GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, prevFBO);
                GL13.glActiveTexture(prevActiveTex);
                GL11.glBindTexture(GL11.GL_TEXTURE_2D, prevTex);

                // Sync GlStateManager cache
                RenderSystem.defaultBlendFunc();
                RenderSystem.color4f(1.0f, 1.0f, 1.0f, 1.0f);
                RenderSystem.clearCurrentColor();
            }
        }
    }

    private void renderSpectrum() {
        boolean hasData;
        synchronized (this.visualizerData) {
            hasData = !this.visualizerData.isEmpty();
        }
        if (hasData) {
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

                    String[] titleSplit = this.songTitle.split(" - ");
                    if (titleSplit.length <= 1) {
                        RenderUtil.drawString(
                                ResourceRegistry.JelloLightFont18_1,
                                130.0F,
                                (float) (mc.getMainWindow().getHeight() - 70),
                                titleSplit[0],
                                RenderUtil2.applyAlpha(ClientColors.DEEP_TEAL.getColor(), 0.5F));
                        RenderUtil.drawString(
                                ResourceRegistry.JelloLightFont18,
                                130.0F,
                                (float) (mc.getMainWindow().getHeight() - 70),
                                titleSplit[0],
                                RenderUtil2.applyAlpha(ClientColors.LIGHT_GREYISH_BLUE.getColor(), 0.7F));
                    } else {
                        RenderUtil.drawString(
                                ResourceRegistry.JelloMediumFont20_1,
                                130.0F,
                                (float) (mc.getMainWindow().getHeight() - 81),
                                titleSplit[0],
                                RenderUtil2.applyAlpha(ClientColors.DEEP_TEAL.getColor(), 0.4F));
                        RenderUtil.drawString(
                                ResourceRegistry.JelloLightFont18_1,
                                130.0F,
                                (float) (mc.getMainWindow().getHeight() - 56),
                                titleSplit[1],
                                RenderUtil2.applyAlpha(ClientColors.DEEP_TEAL.getColor(), 0.5F));
                        RenderUtil.drawString(
                                ResourceRegistry.JelloLightFont18,
                                130.0F,
                                (float) (mc.getMainWindow().getHeight() - 56),
                                titleSplit[1],
                                RenderUtil2.applyAlpha(ClientColors.LIGHT_GREYISH_BLUE.getColor(), 0.7F));
                        RenderUtil.drawString(
                                ResourceRegistry.JelloMediumFont20,
                                130.0F,
                                (float) (mc.getMainWindow().getHeight() - 81),
                                titleSplit[0],
                                RenderUtil2.applyAlpha(ClientColors.LIGHT_GREYISH_BLUE.getColor(), 0.6F));
                    }

                    // Lyrics (NanoVG manages its own colorMask via glPushAttrib/glPopAttrib internally)
                    String lyric = this.getCurrentLyric();
                    if (lyric != null && !lyric.isEmpty() && NanoVGFontRenderer.isInitialized()) {
                        int screenWidth = mc.getMainWindow().getWidth();
                        int screenHeight = mc.getMainWindow().getHeight();
                        float fontSize = 20.0f;
                        float lyricY = (float) (screenHeight - 38);
                        float lyricX = 130.0F;
                        float lyricWidth = NanoVGFontRenderer.getTextWidth(lyric, fontSize);
                        float progress = this.getLyricProgress();
                        float progressWidth = lyricWidth * progress;

                        NanoVGFontRenderer.beginFrame(screenWidth, screenHeight);
                        int dimColor = RenderUtil2.applyAlpha(ClientColors.LIGHT_GREYISH_BLUE.getColor(), 0.35F);
                        NanoVGFontRenderer.drawText(lyric, lyricX, lyricY, fontSize, dimColor);
                        NanoVGFontRenderer.endFrame();

                        RenderUtil.startScissor((int) lyricX, (int) lyricY, (int) (lyricX + progressWidth),
                                (int) (lyricY + fontSize), true);

                        NanoVGFontRenderer.beginFrame(screenWidth, screenHeight);
                        int brightColor = RenderUtil2.applyAlpha(ClientColors.LIGHT_GREYISH_BLUE.getColor(), 0.95F);
                        NanoVGFontRenderer.drawText(lyric, lyricX, lyricY, fontSize, brightColor);
                        NanoVGFontRenderer.endFrame();

                        RenderUtil.restoreScissor();
                    }

                    GL11.glColor4f(1.0f, 1.0f, 1.0f, 1.0f);
                    RenderSystem.color4f(1.0f, 1.0f, 1.0f, 1.0f);
                }
            }
        }
    }

    @EventTarget
    public void onTick(EventUpdate event) {
        if (!this.playing) {
            synchronized (this.visualizerData) {
                this.visualizerData.clear();
            }
            this.amplitudes.clear();
        }

        // --- Upload pending textures on the render thread (safe for OpenGL) ---
        // Upload gate intentionally does NOT check currentVideo. currentVideo is owned by the
        // audio thread and must never be used as a "cover processed" flag — doing so caused
        // lost updates on fast A->B->... switches.
        try {
            if (this.pendingThumbnailImage != null && this.pendingScaledThumbnail != null
                    && this.pendingCoverAccentColor != null && !mc.isGamePaused()) {
                // Grab pending data atomically
                BufferedImage thumbImg = this.pendingThumbnailImage;
                BufferedImage scaledImg = this.pendingScaledThumbnail;
                Integer pendingAccent = this.pendingCoverAccentColor;
                String title = this.pendingSongTitle;
                String pendingKey = this.pendingCoverKey;
                String expectedKey = this.requestedCoverKey;
                this.pendingThumbnailImage = null;
                this.pendingScaledThumbnail = null;
                this.pendingCoverAccentColor = null;
                this.pendingSongTitle = null;
                this.pendingCoverKey = null;

                // Drop stale worker results (old song cover) to avoid async race overwriting current song state.
                if (pendingKey != null && expectedKey != null && !pendingKey.equals(expectedKey)) {
                    this.processing.set(false);
                    return;
                }

                // Rebuild textures only when the pending key is a genuinely new cover.
                // (Title-based comparison was unreliable for same-title / different-resource tracks.)
                if (pendingKey != null && !pendingKey.equals(this.appliedCoverKey)) {
                    // Release old textures first
                    if (this.songThumbnail != null) {
                        try { this.songThumbnail.release(); } catch (Exception ignored) {}
                        this.songThumbnail = null;
                    }
                    if (this.notificationImage != null) {
                        try { this.notificationImage.release(); } catch (Exception ignored) {}
                        this.notificationImage = null;
                    }
                    // Force driver to finalize any pending texture-delete DMAs before reusing IDs.
                    try { GL11.glFinish(); } catch (Throwable ignored) {}

                    // Deep-copy to TYPE_INT_ARGB with power-of-two dims for safe GL upload
                    BufferedImage safeThumb = ensureSafeTexture(thumbImg);
                    BufferedImage safeScaled = ensureSafeTexture(scaledImg);

                    // --- GL state hardening around texture upload ---
                    // 1) Per-song unique keys to bypass Slick InternalTextureLoader's name cache
                    //    (fixed "picture" aliases two images onto one TextureImpl -> release-after-free).
                    // 2) SafeTextureUploader internally:
                    //      - sets UNPACK_ALIGNMENT=1 (defends against driver row-end overreads)
                    //      - calls glFinish() INSIDE the same stack frame that owns the
                    //        DirectByteBuffer, so the Cleaner cannot race the driver's DMA
                    //      - emits a Reference.reachabilityFence to block C2 scalar-replacing
                    //        the buffer local before the native call returns
                    //    This is the proper fix for the recurring AV @ nvoglv64+0xb77610.
                    //    The previous approach of calling glFinish() AFTER
                    //    BufferedImageUtil.getTexture() returned could not work because the
                    //    buffer reference had already left scope by the time glFinish ran.
                    final String keySuffix = pendingKey + "@" + System.nanoTime();
                    try {
                        this.songThumbnail = SafeTextureUploader.upload("music_thumb_" + keySuffix, safeThumb);
                        if (this.songThumbnail == null) {
                            System.err.println("[MusicManager] songThumbnail upload returned null (see prior stack trace)");
                        }
                    } catch (Throwable e) {
                        System.err.println("[MusicManager] Failed to create songThumbnail texture: " + e.getMessage());
                        e.printStackTrace();
                        this.songThumbnail = null;
                    }

                    try {
                        this.notificationImage = SafeTextureUploader.upload("music_notif_" + keySuffix, safeScaled);
                        if (this.notificationImage == null) {
                            System.err.println("[MusicManager] notificationImage upload returned null (see prior stack trace)");
                        }
                    } catch (Throwable e) {
                        System.err.println("[MusicManager] Failed to create notificationImage texture: " + e.getMessage());
                        e.printStackTrace();
                        this.notificationImage = null;
                    }

                    this.songTitle = title;
                    this.appliedCoverKey = pendingKey;
                    // Apply extracted accent only after artwork textures were uploaded successfully.
                    this.coverAccentColor = sanitizeAccentColor(pendingAccent);

                    if (this.notificationImage != null) {
                        Client.getInstance().notificationManager
                                .send(new Notification("Now Playing", this.songTitle, 7000, this.notificationImage));
                    } else {
                        Client.getInstance().notificationManager
                                .send(new Notification("Now Playing", this.songTitle));
                    }
                }
                this.processing.set(false);
            }
        } catch (Throwable exc) {
            // Catch Throwable (incl. OOM/LinkageError/UnsatisfiedLinkError) so the Render thread
            // can recover on the next tick instead of being taken down.
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
            this.appliedCoverKey = null; // allow retry on next tick (appliedCoverKey != requestedCoverKey)
            this.coverAccentColor = DEFAULT_COVER_ACCENT_COLOR;
            this.processing.set(false);
        }

        if (!this.processing.get()) {
            this.startProcessingVideoThumbnail();
        }
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
        // Do NOT pad to power-of-two here — Slick's BufferedImageUtil handles that
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

    private static String buildCoverKey(YoutubeVideoData videoData) {
        if (videoData == null) {
            return "";
        }
        String id = videoData.videoId == null ? "" : videoData.videoId;
        String title = videoData.title == null ? "" : videoData.title;
        return id + "|" + title;
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
        BufferedImage fallback = new BufferedImage(180, 180, BufferedImage.TYPE_INT_ARGB);
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

        int side = Math.min(width, height);
        int x = Math.max(0, (width - side) / 2);
        int y = Math.max(0, (height - side) / 2);
        return copyRegionToArgb(source, x, y, side, side);
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

    /**
     * Record the latest requested cover and start a worker if idle.
     * <p>
     * Invariants:
     * <ul>
     *   <li>{@code requestedCoverKey} is refreshed on <b>every</b> call (never skipped), so
     *       a concurrent in-flight worker will see the new key and drop its stale result.</li>
     *   <li>If {@code requestedCoverKey == appliedCoverKey} no worker is spawned — the cover
     *       is already on-screen.</li>
     *   <li>If CAS on {@code processing} fails (another worker is running), we return without
     *       spawning. The onTick pump will call us again after {@code processing} is released,
     *       at which point {@code requestedCoverKey} already reflects the latest request and
     *       a worker will be spawned for it. This is the "串行但不丢请求" guarantee.</li>
     * </ul>
     */
    private void startProcessingVideoThumbnail(YoutubeVideoData videoData) {
        if (videoData == null) return;
        String coverKey = buildCoverKey(videoData);
        // Always refresh the latest request before any early-return so in-flight workers
        // can detect staleness via `coverKey != requestedCoverKey`.
        this.requestedCoverKey = coverKey;
        // Nothing to do if the cover currently on-screen already matches the latest request.
        if (coverKey.equals(this.appliedCoverKey)) {
            return;
        }
        // A worker is already running; let it finish, then the onTick pump will re-trigger
        // and pick up the (now updated) requestedCoverKey. Do NOT spawn a second worker.
        if (!this.processing.compareAndSet(false, true)) {
            return;
        }
        synchronized (this.visualizerData) {
            this.visualizerData.clear();
        }
        Thread t = new Thread(() -> this.processVideoThumbnail(videoData, coverKey), "MusicCoverProcessor");
        t.setDaemon(true);
        t.start();
    }

    private void initializeAudioPlayback() {
        synchronized (this.visualizerData) {
            this.visualizerData.clear();
        }
        if (this.videoManager != null) {
            // Previously: `while (audioThread.isAlive()) audioThread.interrupt();`
            // That busy-loop pegged a CPU core and raced with the thread's native audio cleanup,
            // causing freezes and crashes when stopping / seeking / auto-advancing songs.
            Thread prev = this.audioThread;
            // Revoke the previous thread's progress-field ownership IMMEDIATELY,
            // before any of the slow cleanup (interrupt + close + 1.5s join) below.
            // The old thread's frame loop checks `Thread.currentThread() == this.audioThread`
            // before writing totalDuration / currentPositionMs / field32170; setting this
            // field to null here makes that check fail on the very next iteration, so the
            // old thread can no longer overwrite values being set by `setDuration` or by
            // the new thread we are about to spin up. This fixes the progress-bar
            // oscillation that appeared when an old thread was stuck on a slow Netease
            // HTTP read and outlived our 1.5s join window.
            this.audioThread = null;
            if (prev != null && prev.isAlive()) {
                prev.interrupt();
                // Help the old thread unblock from SourceDataLine.write()/drain() which are not
                // interruptible on their own.
                SourceDataLine prevLine = this.sourceDataLine;
                if (prevLine != null) {
                    try { prevLine.stop(); } catch (Exception ignored) {}
                    try { prevLine.flush(); } catch (Exception ignored) {}
                    try { prevLine.close(); } catch (Exception ignored) {}
                    this.sourceDataLine = null;
                }
                try {
                    prev.join(1500L);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                }
                // If the old thread is still alive after 1.5s, it's almost certainly stuck in a
                // native read on a dead HTTP stream; drop the reference and let it die on its own.
            }

            this.audioThread = new Thread(
                    () -> {
                        byte[] pcmBufferData;
                        // Reusable visualizer buffers — allocated once per audio thread, reused per frame.
                        // Prevents ~3.6 MB/sec of garbage (JavaFFT + padded PCM + fft output + frequencies.clone())
                        // from being produced in the MP3 frame loop, which was the primary cause of OOM/stutter.
                        float[] reusablePcmFloat = null;
                        float[] reusablePaddedPcm = null;
                        float[] reusableFftReal = null;
                        float[] reusableFftImag = null;
                        JavaFFT reusableFft = null;
                        int reusableFftSize = -1;
                        int reusablePcmLen = -1;
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
                            synchronized (this.visualizerData) {
                                this.visualizerData.clear();
                            }

                            while (!this.playing) {
                                try {
                                    Thread.sleep(300L);
                                } catch (final InterruptedException ignored) {
                                }

                                synchronized (this.visualizerData) {
                                    this.visualizerData.clear();
                                }
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
                                            Client.getInstance().notificationManager
                                                    .send(new Notification("Now Playing", videoData.title));

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
                                                    synchronized (this.visualizerData) {
                                                        this.visualizerData.clear();
                                                    }
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
                                                // Only the audio thread that currently owns playback may
                                                // update the progress fields. A previous-generation thread
                                                // stuck on a slow Netease HTTP read can outlive our 1.5s
                                                // join window; without this guard, both threads write
                                                // totalDuration/field32170 every frame and the GUI thread
                                                // sees them oscillate, manifesting as a progress bar that
                                                // visibly jumps back and forth between two positions.
                                                if (Thread.currentThread() == this.audioThread) {
                                                    this.totalDuration = (long) ((frameCount * msPerFrame) / 1000);
                                                    this.currentPositionMs = (long) (frameCount * msPerFrame);
                                                    this.field32170 = this.duration > 0
                                                            ? (double) this.totalDuration / (double) this.duration
                                                            : 0.0;
                                                }

                                                // Visualizer logic — reuse FFT + buffers across frames to avoid per-frame GC pressure.
                                                // Never let a visualizer failure kill audio playback.
                                                try {
                                                    int frameSize = mp3Format.getFrameSize();
                                                    int n = pcmBytes.length / frameSize; // matches MathHelper.convertToPCMFloatArray output length
                                                    if (n > 0) {
                                                        int p = 1;
                                                        while (p < n) p <<= 1;

                                                        // (Re)allocate FFT state only when the padded size changes.
                                                        if (reusableFftSize != p) {
                                                            reusableFftSize = p;
                                                            reusablePaddedPcm = new float[p];
                                                            reusableFftReal = new float[p];
                                                            reusableFftImag = new float[p];
                                                            reusableFft = new JavaFFT(p);
                                                        }
                                                        // (Re)allocate PCM buffer only when the live sample count changes.
                                                        if (reusablePcmLen != n || reusablePcmFloat == null) {
                                                            reusablePcmLen = n;
                                                            reusablePcmFloat = new float[n];
                                                            // Clear tail of paddedPcm beyond n so FFT sees zeros in the pad region.
                                                            java.util.Arrays.fill(reusablePaddedPcm, n, p, 0.0f);
                                                        }

                                                        // Inlined PCM conversion (little-endian, bit depth = 16, frameSize=2*channels).
                                                        // Mirrors MathHelper.convertToPCMFloatArray semantics but writes into reusable buffer.
                                                        boolean bigEndian = mp3Format.isBigEndian();
                                                        for (int i = 0; i < n; i++) {
                                                            int base = i * frameSize;
                                                            int sample = 0;
                                                            if (!bigEndian) {
                                                                for (int k = 0; k < frameSize; k++) {
                                                                    sample += (pcmBytes[base + k] & 0xFF) << (8 * k);
                                                                }
                                                            } else {
                                                                for (int k = 0; k < frameSize; k++) {
                                                                    sample += (pcmBytes[base + k] & 0xFF) << (8 * (frameSize - k - 1));
                                                                }
                                                            }
                                                            reusablePcmFloat[i] = sample / 32768.0f;
                                                        }
                                                        System.arraycopy(reusablePcmFloat, 0, reusablePaddedPcm, 0, n);

                                                        // Imag input is null => JavaFFT's 5-arg transform relies on imagOut starting at zero.
                                                        java.util.Arrays.fill(reusableFftImag, 0.0f);
                                                        reusableFft.transform(false, reusablePaddedPcm, null,
                                                                reusableFftReal, reusableFftImag);

                                                        double[] amps = MathHelper.calculateAmplitudes(reusableFftReal, reusableFftImag);
                                                        synchronized (this.visualizerData) {
                                                            this.visualizerData.add(amps);
                                                            while (this.visualizerData.size() > 18) {
                                                                this.visualizerData.remove(0);
                                                            }
                                                        }
                                                    }
                                                } catch (Throwable visualizerErr) {
                                                    // Swallow — visualizer must never kill audio.
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

                                            // Natural song end. Each cleanup step is guarded because another thread
                                            // (setPlaying/initializeAudioPlayback) may have concurrently closed the
                                            // line, and double-close on javax.sound.sampled implementations can
                                            // trigger native crashes.
                                            SourceDataLine endLine = this.sourceDataLine;
                                            if (endLine != null) {
                                                try { endLine.drain(); } catch (Exception ignored) {}
                                                try { endLine.close(); } catch (Exception ignored) {}
                                                this.sourceDataLine = null;
                                            }
                                            try { bitstream.close(); } catch (Exception ignored) {}
                                            try { fileStream.close(); } catch (Exception ignored) {}
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
                                            synchronized (this.visualizerData) {
                                                this.visualizerData.clear();
                                            }
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

                                        synchronized (this.visualizerData) {
                                            this.visualizerData.add(MathHelper.calculateAmplitudes(var21, var22));
                                            while (this.visualizerData.size() > 18) {
                                                this.visualizerData.remove(0);
                                            }
                                        }

                                        this.adjustAudioVolume(this.sourceDataLine, this.volume);
                                        if (!Thread.interrupted()) {
                                            // Same ownership guard as the MP3 path above: only the
                                            // current owner thread may update progress fields, so a
                                            // dangling old AAC thread cannot make the progress bar
                                            // oscillate.
                                            if (Thread.currentThread() == this.audioThread) {
                                                this.totalDuration = Math.round(var13.getNextTimeStamp());
                                                this.field32170 = var13.method23326();
                                                if (this.field32169) {
                                                    var13.seek(this.field32168);
                                                    this.totalDuration = (long) this.field32168;
                                                    this.field32169 = false;
                                                }
                                            }
                                        }

                                        if (!var13.hasMoreFrames()
                                                && (this.repeat == AudioRepeatMode.LOOP_CURRENT
                                                        || this.repeat == AudioRepeatMode.REPEAT
                                                                && this.videoManager.videoList.size() == 1)) {
                                            var13.seek(0.0);
                                            if (Thread.currentThread() == this.audioThread) {
                                                this.totalDuration = 0L;
                                            }
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
        this.processVideoThumbnail(videoData, buildCoverKey(videoData));
    }

    private void processVideoThumbnail(YoutubeVideoData videoData, String coverKey) {
        try {
            if (videoData == null) {
                this.processing.set(false);
                return;
            }

            BufferedImage buffImage = null;
            try {
                buffImage = ImageIO.read(new URL(videoData.fullUrl));
            } catch (Exception e) {
                // If failed to read image (e.g. audio file), use default or null
            }

            if (buffImage == null) {
                // Keep original behavior for texture pipeline stability.
                buffImage = new BufferedImage(180, 180, BufferedImage.TYPE_INT_ARGB);
            }

            String title = videoData.title;
            BufferedImage blurred = ImageUtil.applyBlur(buffImage, 15);
            if (blurred == null) {
                blurred = buffImage;
            }

            // Keep legacy sampling region for bar texture to avoid visual regressions.
            int blurH = blurred.getHeight();
            int blurW = blurred.getWidth();
            int subY = Math.min((int) (blurH * 0.75F), blurH - 1);
            int subH = Math.max(1, Math.min((int) (blurH * 0.2F), blurH - subY));
            BufferedImage thumbSub = blurred.getSubimage(0, subY, blurW, subH);

            int imgW = buffImage.getWidth();
            int imgH = buffImage.getHeight();
            BufferedImage scaledSub;
            if (imgH != imgW) {
                if (title != null && title.contains("[NCS Release]") && imgW >= 173 && imgH >= 173) {
                    scaledSub = buffImage.getSubimage(1, 3, 170, 170);
                } else {
                    int cropW = Math.min(imgW, 180);
                    int cropH = Math.min(imgH, 180);
                    scaledSub = buffImage.getSubimage(0, 0, cropW, cropH);
                }
            } else {
                scaledSub = buffImage;
            }

            // Analyze a detached ARGB copy to avoid getSubimage raster side effects.
            BufferedImage sampledCover = ensureSafeTexture(scaledSub);
            int extractedAccent = extractDominantCoverColor(sampledCover);

            // Async guard: do not let stale worker results override a newer song's artwork/color.
            // (Also checked again on the render thread in onTick as a belt-and-suspenders
            // safety net, since requestedCoverKey can be updated between here and upload.)
            if (!coverKey.equals(this.requestedCoverKey)) {
                this.processing.set(false);
                return;
            }

            // Store results for the render thread to pick up (no GL calls here!).
            // IMPORTANT: do NOT touch currentVideo here. currentVideo is owned by the audio
            // thread; writing it from the worker caused the "stuck on first cover" bug by
            // clobbering the audio thread's latest song and then confusing the pump.
            this.pendingThumbnailImage = ensureSafeTexture(thumbSub);
            this.pendingScaledThumbnail = sampledCover;
            this.pendingCoverAccentColor = extractedAccent;
            this.pendingSongTitle = title;
            this.pendingCoverKey = coverKey;
        } catch (Throwable var5) {
            var5.printStackTrace();
            this.processing.set(false);
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
            this.currentVideoIndex = this.currentVideoIndex2 - 1;
            this.totalDuration = 0L;
            this.field32170 = 0.0;
            this.initializeAudioPlayback();
        }
    }

    public void playNextSong() {
        if (this.videoManager != null) {
            this.currentVideoIndex = this.currentVideoIndex2 + 1;
            this.totalDuration = 0L;
            this.field32170 = 0.0;
            this.initializeAudioPlayback();
        }
    }

    public void playSong(Thumbnails vidManager, YoutubeVideoData videoData) {
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
        // Revoke the current audio thread's ownership of the progress fields BEFORE
        // we write the new seek target into totalDuration. Otherwise the still-running
        // old thread would overwrite our write on its next frame iteration, making the
        // progress bar visibly snap back to the old playback position for ~1 frame
        // before the new (post-seek) thread takes over. initializeAudioPlayback below
        // sets up a new thread that takes ownership.
        this.audioThread = null;

        this.field32168 = duration;
        this.totalDuration = (long) this.field32168;
        this.field32169 = true;

        // For MP3: seek by interrupting and restarting with a target frame
        this.mp3SeekTargetSec = (long) duration;
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
