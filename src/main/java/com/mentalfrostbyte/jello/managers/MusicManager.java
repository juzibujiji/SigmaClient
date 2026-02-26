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
import com.mentalfrostbyte.jello.util.client.network.youtube.YoutubeUtil;
import com.mentalfrostbyte.jello.util.client.network.youtube.YoutubeVideoData;
import com.mentalfrostbyte.jello.util.client.render.ResourceRegistry;
import com.mentalfrostbyte.jello.util.client.music.LrcParser;
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
import com.sapher.youtubedl.YoutubeDL;
import com.sapher.youtubedl.YoutubeDLException;
import com.sapher.youtubedl.YoutubeDLRequest;
import com.sapher.youtubedl.YoutubeDLResponse;
import com.sun.jna.platform.win32.Advapi32Util;
import com.sun.jna.platform.win32.WinReg;
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
import org.lwjgl.opengl.GL11;
import org.newdawn.slick.util.BufferedImageUtil;
import team.sdhq.eventBus.annotations.EventTarget;

import javax.imageio.ImageIO;
import javax.sound.sampled.*;
import javax.sound.sampled.FloatControl.Type;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.List;

public class MusicManager extends Manager implements MinecraftUtil {
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
    private boolean processing = false;
    private transient volatile Thread audioThread = null;
    private int currentVideoIndex2;
    private long totalDuration = 0L;
    private int currentVideoIndex;
    private YoutubeVideoData currentVideo;
    private boolean spectrum = true;
    private AudioRepeatMode repeat = AudioRepeatMode.REPEAT;
    private boolean finished = false;
    private double field32168;
    private boolean field32169 = false;
    private double field32170 = 0.0;
    private List<LrcParser.LyricLine> currentLyrics = new ArrayList<>();
    private long currentPositionMs = 0;

    @Override
    public void init() {
        super.init();

        try {
            this.loadSettings();
        } catch (JsonParseException e) {
            Client.logger.error(e);
        }

        if (!this.doesYTDLPExist()) {
            this.setupDownloadThread();
        }

        this.finished = false;

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
            this.renderSpectrum();
        }
    }

    private void renderSpectrum() {
        if (!this.visualizerData.isEmpty()) {
            if (this.notificationImage != null) {
                if (!this.amplitudes.isEmpty()) {
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
                                RenderUtil2.applyAlpha(ClientColors.MID_GREY.getColor(), 0.2F * alphaValue));
                    }

                    RenderUtil.initStencilBuffer();

                    for (int i = 0; (float) i < maxWidth; i++) {
                        float heightRatio = (float) mc.getMainWindow().getHeight() / 1080.0F;
                        float height = ((float) (Math.sqrt(this.amplitudes.get(i)) / 12.0) - 5.0F) * heightRatio;
                        RenderUtil.drawRoundedRect2((float) i * width, (float) mc.getMainWindow().getHeight() - height,
                                width, height, ClientColors.LIGHT_GREYISH_BLUE.getColor());
                    }

                    RenderUtil.configureStencilTest();
                    if (this.notificationImage != null && this.songThumbnail != null) {
                        RenderUtil.drawImage(0.0F, 0.0F, (float) mc.getMainWindow().getWidth(),
                                (float) mc.getMainWindow().getHeight(), this.songThumbnail, 0.4F);
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

                    // Draw lyrics with gradient progress using NanoVG (CJK support)
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

                        // Begin NanoVG frame
                        NanoVGFontRenderer.beginFrame(screenWidth, screenHeight);

                        // Draw dim base text
                        int dimColor = RenderUtil2.applyAlpha(ClientColors.LIGHT_GREYISH_BLUE.getColor(), 0.35F);
                        NanoVGFontRenderer.drawText(lyric, lyricX, lyricY, fontSize, dimColor);

                        // End NanoVG frame for base text
                        NanoVGFontRenderer.endFrame();

                        // Draw highlighted portion with scissor
                        RenderUtil.startScissor((int) lyricX, (int) lyricY, (int) (lyricX + progressWidth),
                                (int) (lyricY + fontSize), true);

                        NanoVGFontRenderer.beginFrame(screenWidth, screenHeight);
                        int brightColor = RenderUtil2.applyAlpha(ClientColors.LIGHT_GREYISH_BLUE.getColor(), 0.95F);
                        NanoVGFontRenderer.drawText(lyric, lyricX, lyricY, fontSize, brightColor);
                        NanoVGFontRenderer.endFrame();

                        RenderUtil.restoreScissor();
                    }
                }
            }
        }
    }

    @EventTarget
    public void onTick(EventUpdate event) {
        if (!this.playing) {
            this.visualizerData.clear();
            this.amplitudes.clear();
        }

        try {
            if (this.processing && this.thumbnailImage != null && this.scaledThumbnail != null
                    && this.currentVideo == null && !mc.isGamePaused()) {
                if (this.songThumbnail != null) {
                    this.songThumbnail.release();
                }

                if (this.notificationImage != null) {
                    this.notificationImage.release();
                }

                this.songThumbnail = BufferedImageUtil.getTexture("picture", this.thumbnailImage);
                this.notificationImage = BufferedImageUtil.getTexture("picture", this.scaledThumbnail);
                Client.getInstance().notificationManager
                        .send(new Notification("Now Playing", this.songTitle, 7000, this.notificationImage));
                this.processing = false;
            }
        } catch (IOException exc) {
            throw new RuntimeException(exc);
        }

        if (!this.processing) {
            this.startProcessingVideoThumbnail();
        }
    }

    private void startProcessingVideoThumbnail() {
        this.startProcessingVideoThumbnail(this.currentVideo);
    }

    private void startProcessingVideoThumbnail(YoutubeVideoData videoData) {
        if (videoData != null) {
            this.visualizerData.clear();
            new Thread(() -> this.processVideoThumbnail(videoData)).start();
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
                            if (videoData.videoId.startsWith("file:")) {
                                try {
                                    songUrl = new URL(videoData.videoId);
                                } catch (MalformedURLException e) {
                                    songUrl = YoutubeUtil.getVideoStreamURL(videoData.videoId);
                                }
                            } else {
                                songUrl = YoutubeUtil.getVideoStreamURL(videoData.videoId);
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
                                    // Check if this is a local file (MP3) - use JLayer with frame-by-frame decoding
                                    if (url.getProtocol().equals("file")) {
                                        try {
                                            InputStream fileStream = url.openStream();
                                            javazoom.jl.decoder.Bitstream bitstream = new javazoom.jl.decoder.Bitstream(
                                                    fileStream);
                                            javazoom.jl.decoder.Decoder mp3Decoder = new javazoom.jl.decoder.Decoder();

                                            this.songTitle = videoData.title;
                                            this.startProcessingVideoThumbnail(videoData);
                                            Client.getInstance().notificationManager
                                                    .send(new Notification("Now Playing", videoData.title));

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
                                            }

                                            // Estimate duration (rough estimate based on file size)
                                            java.io.File audioFile = new java.io.File(new java.net.URI(url.toString()));
                                            long fileSize = audioFile.length();
                                            // Estimate ~128kbps bitrate -> duration in seconds = fileSize * 8 / 128000
                                            this.duration = (fileSize * 8) / 128000;

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
                                            float msPerFrame = 0;
                                            byte[] pcmBytes = null; // Initialize pcmBytes here

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

                                                // Visualizer logic
                                                float[] pcmFloat = MathHelper.convertToPCMFloatArray(pcmBytes,
                                                        mp3Format);

                                                // Pad to next power of 2 for FFT
                                                int n = pcmFloat.length;
                                                int p = 1;
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
                                if (exc.getMessage() != null && exc.getMessage().contains("403")) {
                                    System.out.println("installing");
                                    this.download();
                                }
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
        try {
            this.processing = true;
            BufferedImage buffImage = null;
            try {
                buffImage = ImageIO.read(new URL(videoData.fullUrl));
            } catch (Exception e) {
                // If failed to read image (e.g. audio file), use default or null
            }

            if (buffImage == null) {
                // Create a default image or handle null
                buffImage = new BufferedImage(180, 180, BufferedImage.TYPE_INT_ARGB);
                // Fill with some color or make it transparent
            }

            this.thumbnailImage = ImageUtil.applyBlur(buffImage, 15);
            this.thumbnailImage = this.thumbnailImage
                    .getSubimage(0, (int) ((float) this.thumbnailImage.getHeight() * 0.75F),
                            this.thumbnailImage.getWidth(),
                            (int) ((float) this.thumbnailImage.getHeight() * 0.2F));
            this.songTitle = videoData.title;
            if (buffImage.getHeight() != buffImage.getWidth()) {
                if (this.songTitle.contains("[NCS Release]")) {
                    this.scaledThumbnail = buffImage.getSubimage(1, 3, 170, 170);
                } else {
                    this.scaledThumbnail = buffImage.getSubimage(0, 0, Math.min(buffImage.getWidth(), 180),
                            Math.min(buffImage.getHeight(), 180));
                }
            } else {
                this.scaledThumbnail = buffImage;
            }

            this.currentVideo = null;
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
        String songURLString = songURL.toString();
        String userHomeDir = System.getProperty("user.home");
        YoutubeDLRequest request = new YoutubeDLRequest(songURLString, userHomeDir);
        request.setOption("get-url");
        request.setOption("no-check-certificate", " ");
        request.setOption("rm-cache-dir", " ");
        request.setOption("retries", 10);
        request.setOption("format", 18);

        try {
            YoutubeDL.setExecutablePath(this.prepareYtDlpExecutable());
            YoutubeDLResponse response = YoutubeDL.execute(request);
            String responseString = response.getOut();
            return new URL(responseString);
        } catch (YoutubeDLException exception) {
            if (exception.getMessage() != null
                    && exception.getMessage().contains("ERROR: This video contains content from")
                    && exception.getMessage().contains("who has blocked it in your country on copyright grounds")) {
                Client.getInstance().notificationManager
                        .send(new Notification("Now Playing", "Not available in your region."));
            } else {
                exception.printStackTrace();
                this.download();
            }
        } catch (MalformedURLException exception) {
            exception.printStackTrace();
        }

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
        this.field32168 = duration;
        this.totalDuration = (long) this.field32168;
        this.field32169 = true;
    }

    public boolean doesYTDLPExist() {
        File targetFile = new File(Client.getInstance().file + "/music/yt-dlp");
        if (Util.getOSType() == Util.OS.WINDOWS) {
            targetFile = new File(Client.getInstance().file + "/music/yt-dlp.exe");
        }

        return targetFile.exists();
    }

    public void setupDownloadThread() {
        new Thread(this::download).start();
    }

    public void download() {
        if (!this.finished) {
            File musicDir = new File(Client.getInstance().file + "/music/");
            musicDir.mkdirs();
            if (Util.getOSType() == Util.OS.WINDOWS) {
                try {
                    File targetFile = new File(Client.getInstance().file + "/music/yt-dlp.exe");
                    CloseableHttpClient client = HttpClients.createDefault();
                    CloseableHttpResponse response = client.execute(
                            new HttpGet("https://github.com/yt-dlp/yt-dlp/releases/latest/download/yt-dlp.exe"));
                    Throwable throwable = null;

                    try {
                        HttpEntity entity = response.getEntity();
                        if (entity != null) {
                            try (FileOutputStream outputStream = new FileOutputStream(targetFile)) {
                                entity.writeTo(outputStream);
                            }
                        }
                    } catch (Throwable t) {
                        throwable = t;
                        throw t;
                    } finally {
                        if (response != null) {
                            if (throwable != null) {
                                try {
                                    response.close();
                                } catch (Throwable t) {
                                    throwable.addSuppressed(t);
                                }
                            } else {
                                response.close();
                            }
                        }
                    }
                } catch (IOException exc) {
                    exc.printStackTrace();
                }
            } else {
                try {
                    File targetFile = new File(Client.getInstance().file + "/music/yt-dlp");
                    CloseableHttpClient client = HttpClients.createDefault();
                    CloseableHttpResponse response = client
                            .execute(new HttpGet("https://github.com/yt-dlp/yt-dlp/releases/latest/download/yt-dlp"));
                    Throwable throwable = null;

                    try {
                        HttpEntity entity = response.getEntity();
                        if (entity != null) {
                            try (FileOutputStream outputStream = new FileOutputStream(targetFile)) {
                                entity.writeTo(outputStream);
                            }
                        }
                    } catch (Throwable t) {
                        throwable = t;
                        throw t;
                    } finally {
                        if (response != null) {
                            if (throwable != null) {
                                try {
                                    response.close();
                                } catch (Throwable t) {
                                    throwable.addSuppressed(t);
                                }
                            } else {
                                response.close();
                            }
                        }
                    }
                } catch (IOException exc) {
                    exc.printStackTrace();
                }
            }

            System.out.println("done");
            this.finished = true;
        }
    }

    public String prepareYtDlpExecutable() {
        String fileName = Client.getInstance().file.getAbsolutePath() + "/music/yt-dlp";
        if (Util.getOSType() != Util.OS.WINDOWS) {
            File targetFile = new File(fileName);
            targetFile.setExecutable(true);
        } else {
            fileName = fileName + ".exe";
        }

        return fileName;
    }

    public boolean hasPython() {
        if (Util.getOSType() == Util.OS.WINDOWS) {
            return true; // Windows yt-dlp doesn't require Python
        }

        String[][] commands = { { "python3", "-V" }, { "python", "-V" } };

        for (String[] cmd : commands) {
            try {
                Process process = new ProcessBuilder(cmd).start();

                // Read both stdout and stderr
                BufferedReader inputReader = new BufferedReader(new InputStreamReader(process.getInputStream()));
                BufferedReader errorReader = new BufferedReader(new InputStreamReader(process.getErrorStream()));

                String version;
                while ((version = inputReader.readLine()) != null || (version = errorReader.readLine()) != null) {
                    if (version.contains("Python")) {
                        return true;
                    }
                }
            } catch (IOException exc) {
                Client.logger.error("No Python version found!", exc);
            }
        }

        return false;
    }

    public boolean hasVCRedist() {
        if (Util.getOSType() != Util.OS.WINDOWS) {
            return true;
        }

        String[] redistKeys = {
                "SOFTWARE\\WOW6432Node\\Microsoft\\VisualStudio\\10.0\\VC\\VCRedist\\x86",
                "SOFTWARE\\Microsoft\\VisualStudio\\10.0\\VC\\VCRedist\\x86",
                "SOFTWARE\\WOW6432Node\\Microsoft\\VisualStudio\\14.0\\VC\\Runtimes\\x86",
                "SOFTWARE\\Microsoft\\VisualStudio\\14.0\\VC\\Runtimes\\x86",
                "SOFTWARE\\WOW6432Node\\Microsoft\\VisualStudio\\14.0\\VC\\Runtimes\\x64",
                "SOFTWARE\\Microsoft\\VisualStudio\\14.0\\VC\\Runtimes\\x64",
                "SOFTWARE\\Microsoft\\Windows\\CurrentVersion\\Uninstall\\{D992A37A-AF08-45C4-9E49-D50EA5F46A16}_is1",
                "SOFTWARE\\WOW6432Node\\Microsoft\\Windows\\CurrentVersion\\Uninstall\\{D992A37A-AF08-45C4-9E49-D50EA5F46A16}_is1"
        };

        for (String key : redistKeys) {
            try {
                if (Advapi32Util.registryKeyExists(WinReg.HKEY_LOCAL_MACHINE, key)) {
                    if (Advapi32Util.registryValueExists(WinReg.HKEY_LOCAL_MACHINE, key, "Installed")) {
                        int installed = Advapi32Util.registryGetIntValue(WinReg.HKEY_LOCAL_MACHINE, key, "Installed");
                        if (installed == 1)
                            return true;
                    } else {
                        // Some VC Redists don't use "Installed" but can be detected by presence
                        return true;
                    }
                }
            } catch (Exception exc) {
                Client.logger.warn("Failed to check key: " + key, exc);
            }
        }

        return false;
    }
}