package com.mentalfrostbyte.jello.gui.impl.jello.ingame.clickgui.musicplayer;

import com.mentalfrostbyte.Client;
import com.mentalfrostbyte.jello.gui.base.animations.Animation;
import com.mentalfrostbyte.jello.gui.base.elements.impl.button.Button;
import com.mentalfrostbyte.jello.gui.base.elements.impl.button.types.ChangingButton;
import com.mentalfrostbyte.jello.gui.base.elements.impl.button.types.SpectrumButton;
import com.mentalfrostbyte.jello.gui.base.elements.impl.button.types.ThumbnailButton;
import com.mentalfrostbyte.jello.gui.base.elements.impl.image.types.SmallImage;
import com.mentalfrostbyte.jello.gui.combined.AnimatedIconPanel;
import com.mentalfrostbyte.jello.gui.combined.CustomGuiScreen;
import com.mentalfrostbyte.jello.gui.impl.jello.ingame.clickgui.musicplayer.elements.*;
import com.mentalfrostbyte.jello.util.client.render.Resources;
import com.mentalfrostbyte.jello.util.game.render.RenderUtil;
import com.mentalfrostbyte.jello.util.game.render.RenderUtil2;
import com.mentalfrostbyte.jello.util.system.math.smoothing.QuadraticEasing;
import com.mentalfrostbyte.jello.gui.impl.jello.buttons.ScrollableContentPanel;
import com.mentalfrostbyte.jello.gui.impl.jello.ingame.clickgui.ClickGuiScreen;
import com.mentalfrostbyte.jello.managers.MusicManager;
import com.mentalfrostbyte.jello.managers.util.Thumbnails;
import com.mentalfrostbyte.jello.managers.util.notifs.Notification;
import com.mentalfrostbyte.jello.util.client.network.netease.NeteaseApiLogin;
import com.mentalfrostbyte.jello.util.client.network.netease.NeteaseRequestApi;
import com.mentalfrostbyte.jello.util.client.network.youtube.YoutubeContentType;
import com.mentalfrostbyte.jello.util.client.network.youtube.YoutubeVideoData;
import com.mentalfrostbyte.jello.util.client.render.theme.ClientColors;
import com.mentalfrostbyte.jello.util.client.render.theme.ColorHelper;
import com.mentalfrostbyte.jello.util.client.render.ResourceRegistry;
import com.mentalfrostbyte.jello.util.client.render.SkijaFontRenderer;
import com.mentalfrostbyte.jello.util.client.network.youtube.YoutubeUtil;
import com.mentalfrostbyte.jello.util.client.render.FontSizeAdjust;
import com.mentalfrostbyte.jello.util.system.network.ImageUtil;
import com.mojang.blaze3d.systems.RenderSystem;
import org.lwjgl.opengl.GL11;
import org.newdawn.slick.opengl.Texture;
import org.newdawn.slick.util.BufferedImageUtil;

import java.io.IOException;
import java.util.*;

public class MusicPlayer extends AnimatedIconPanel {
    private static final String NETEASE_PLAYLIST_PREFIX = "netease_playlist:";
    private final int width = 250;
    private final int height = 40;
    private final int field20847 = 64;
    private final int field20848 = 94;
    private String field20849 = "Music Player";
    private final ScrollableContentPanel musicTabs;
    private ScrollableContentPanel field20852;
    private final CustomGuiScreen musicControls;
    private final MusicManager musicManager = Client.getInstance().musicManager;
    public static Map<String, Thumbnails> videoMap = new LinkedHashMap<>();
    private final Button play;
    private final Button pause;
    private final Button forwards;
    private final Button backwards;
    private final VolumeSlider volumeSlider;
    private int field20863;
    private Texture texture;
    private final CustomGuiScreen field20865;
    public SearchBox searchBox;
    public ProgressBar field20867;
    private Button neteaseLoginBtn;
    public static List<Thumbnails> videos = new ArrayList<>();
    public static long time = 0L;
    public float field20871 = 0.0F;
    public float field20872 = 0.0F;
    private final Animation field20873 = new Animation(80, 150, Animation.Direction.BACKWARDS);
    public boolean field20874 = false;

    // Netease QR login overlay
    public volatile boolean showNeteaseQr = false;
    public volatile java.awt.image.BufferedImage neteaseQrImage = null;
    public volatile String neteaseQrUniKey = null;
    public volatile Texture neteaseQrTexture = null;

    public ClickGuiScreen parent;

    private static synchronized void ensureBaseVideoSources() {
        addVideoSourceIfMissing(new Thumbnails("Bundled Music", "bundled_music", YoutubeContentType.BUNDLED));
        addVideoSourceIfMissing(new Thumbnails("Local Music", "local_music", YoutubeContentType.LOCAL));
        addVideoSourceIfMissing(new Thumbnails("\u7f51\u6613\u4e91\u70ed\u6b4c", "netease_hot", YoutubeContentType.NETEASE));
        addVideoSourceIfMissing(new Thumbnails("\u7f51\u6613\u4e91\u65b0\u6b4c", "netease_new", YoutubeContentType.NETEASE));
        addVideoSourceIfMissing(new Thumbnails("Wuthering Waves", "netease_artist:61908633", YoutubeContentType.NETEASE));
    }

    private static void addVideoSourceIfMissing(Thumbnails thumbnails) {
        if (!hasVideo(thumbnails.videoId)) {
            videos.add(thumbnails);
        }
    }

    public MusicPlayer(ClickGuiScreen parent, String var2) {
        super(parent, var2, 875, 55, 800, 600, false);
        this.parent = parent;

        // Only initialize default sources once; preserve loaded Thumbnails on re-open.
        ensureBaseVideoSources();

        time = System.nanoTime();
        this.setWidthA(800);
        this.setHeightA(600);
        this.setXA(Math.abs(this.getXA()));
        this.setYA(Math.abs(this.getYA()));
        this.addToList(this.musicTabs = new ScrollableContentPanel(this, "musictabs", 0, this.field20847 + 14,
                this.width, this.getHeightA() - 170 - (this.field20847 + 14)));
        this.addToList(
                this.musicControls = new ScrollableContentPanel(
                        this, "musiccontrols", this.width, this.getHeightA() - this.field20848,
                        this.getWidthA() - this.width, this.field20848));

        ColorHelper color = new ColorHelper(0xFF131313, -15329770).setTextColor(ClientColors.LIGHT_GREYISH_BLUE.getColor())
            .method19414(FontSizeAdjust.NEGATE_AND_DIVIDE_BY_2);

        // Add Open Folder Button
        Button openFolderBtn = new MusicTabButton(this, "openFolder", this.width - 110, 10, 100, 30,
                new ColorHelper(ClientColors.DEEP_TEAL.getColor(), ClientColors.DEEP_TEAL.getColor(),
                        ClientColors.DEEP_TEAL.getColor(), ClientColors.LIGHT_GREYISH_BLUE.getColor()),
                "Open Folder", ResourceRegistry.JelloLightFont14);
        openFolderBtn.onClick((a, b) -> {
            try {
                java.awt.Desktop.getDesktop().open(new java.io.File(Client.getInstance().file, "music"));
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
        this.addToList(openFolderBtn);

        // Add Netease Cloud Music Login Button
        this.neteaseLoginBtn = new MusicTabButton(this, "neteaseLogin", this.width - 110, 42, 100, 30,
                new ColorHelper(ClientColors.DEEP_TEAL.getColor(), ClientColors.DEEP_TEAL.getColor(),
                        ClientColors.DEEP_TEAL.getColor(), ClientColors.LIGHT_GREYISH_BLUE.getColor()),
            NeteaseApiLogin.isLoggedIn()
                        ? "\u5df2\u767b\u5f55" : "NE Login",
                ResourceRegistry.JelloLightFont14);
        this.neteaseLoginBtn.onClick((a, b) -> {
            if (NeteaseApiLogin.isLoggedIn()) {
            this.loadUserNeteasePlaylists(color, this);
                Client.getInstance().notificationManager.send(
                        new com.mentalfrostbyte.jello.managers.util.notifs.Notification(
                                "\u7f51\u6613\u4e91",
                    "\u5df2\u767b\u5f55: " + NeteaseApiLogin.getNickname()));
                return;
            }
            new Thread(() -> {
                try {
                String uniKey = NeteaseApiLogin.getUniKey();
                    if (uniKey == null) {
                        Client.getInstance().notificationManager.send(
                                new com.mentalfrostbyte.jello.managers.util.notifs.Notification(
                                        "\u7f51\u6613\u4e91", "\u83b7\u53d6\u4e8c\u7ef4\u7801\u5931\u8d25"));
                        return;
                    }
                java.awt.image.BufferedImage qrImage = NeteaseApiLogin.generateQRCode(uniKey);
                    if (qrImage == null) {
                        Client.getInstance().notificationManager.send(
                                new com.mentalfrostbyte.jello.managers.util.notifs.Notification(
                                        "\u7f51\u6613\u4e91", "\u751f\u6210\u4e8c\u7ef4\u7801\u5931\u8d25"));
                        return;
                    }
                this.showNeteaseQrOverlay(qrImage, uniKey);
                    Client.getInstance().notificationManager.send(
                            new com.mentalfrostbyte.jello.managers.util.notifs.Notification(
                                    "\u7f51\u6613\u4e91", "\u8bf7\u7528\u7f51\u6613\u4e91\u97f3\u4e50 App \u626b\u7801\u767b\u5f55"));
                    // Poll for login status
                    System.out.println("[MusicPlayer] Starting QR login poll...");
                    while (this.showNeteaseQr) {
                        Thread.sleep(2000);
                        int status = NeteaseApiLogin.checkQrLogin(uniKey);
                        System.out.println("[MusicPlayer] QR poll status: " + status);
                        if (status == 803) {
                            this.closeNeteaseQrOverlay(false);
                            System.out.println("[MusicPlayer] Login success, fetching user info...");
                            NeteaseApiLogin.getLoginStatus(null);
                            // Save persistent cookie NOW (after getLoginStatus set nickname/userId)
                            NeteaseApiLogin.savePersistentCookie();
                            this.neteaseLoginBtn.setText("\u5df2\u767b\u5f55");
                            System.out.println("[MusicPlayer] Loading user playlists...");
                            this.loadUserNeteasePlaylists(color, this);
                            Client.getInstance().notificationManager.send(
                                    new com.mentalfrostbyte.jello.managers.util.notifs.Notification(
                                            "\u7f51\u6613\u4e91",
                                            "\u767b\u5f55\u6210\u529f: " + NeteaseApiLogin.getNickname()));
                            break;
                        } else if (status == 800) {
                            this.closeNeteaseQrOverlay(false);
                            Client.getInstance().notificationManager.send(
                                    new com.mentalfrostbyte.jello.managers.util.notifs.Notification(
                                            "\u7f51\u6613\u4e91", "\u4e8c\u7ef4\u7801\u5df2\u8fc7\u671f\uff0c\u8bf7\u91cd\u8bd5"));
                            break;
                        } else if (status == 802) {
                            Client.getInstance().notificationManager.send(
                                    new com.mentalfrostbyte.jello.managers.util.notifs.Notification(
                                            "\u7f51\u6613\u4e91", "\u5df2\u626b\u7801\uff0c\u8bf7\u5728\u624b\u673a\u4e0a\u786e\u8ba4"));
                        } else if (status == -1) {
                            System.err.println("[MusicPlayer] QR poll returned -1 (network error), retrying...");
                        } else {
                            System.out.println("[MusicPlayer] Unknown QR status: " + status);
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                    this.closeNeteaseQrOverlay(false);
                }
            }).start();
        });
        this.neteaseLoginBtn.setSelfVisible(false);
        this.addToList(this.neteaseLoginBtn);

        this.addToList(this.field20865 = new CustomGuiScreen(this, "reShowView", 0, 0, 1, this.getHeightA()));
        SpectrumButton var5;
        this.addToList(var5 = new SpectrumButton(this, "spectrumButton", 15, this.heightA - 140, 40, 40,
                this.musicManager.isSpectrum()));
        var5.setReAddChildren(true);
        var5.onClick((var1x, var2x) -> {
            this.musicManager.setSpectrum(!this.musicManager.isSpectrum());
            ((SpectrumButton) var1x).method13099(this.musicManager.isSpectrum());
        });
        this.musicTabs.setListening(false);
        var5.setListening(false);
        this.musicControls.setListening(false);
        this.field20865.setListening(false);
        List<Thread> threads = new ArrayList<>();
        MusicPlayer player = this;

        for (Thumbnails video : videos) {
            threads.add(new Thread(() -> {
                if (!videoMap.containsKey(video.videoId) && !video.isUpdated) {
                    video.isUpdated = true;
                    video.refreshVideoList();
                    videoMap.put(video.videoId, video);
                }

                this.runThisOnDimensionUpdate(new MusicInitializer(this, video, color, player));
            }));
            threads.get(threads.size() - 1).start();
        }

        // If already logged in (e.g., cookie restored), load user playlists asynchronously
        if (NeteaseApiLogin.isLoggedIn()) {
            this.loadUserNeteasePlaylists(color, player);
        }

        int var15 = (this.getWidthA() - this.width - 38) / 2;
        this.musicControls
                .addToList(
                        this.play = new SmallImage(
                                this.musicControls, "play", var15, 27, 38, 38, Resources.playPNG,
                                new ColorHelper(ClientColors.LIGHT_GREYISH_BLUE.getColor()), null));
        this.musicControls
                .addToList(
                        this.pause = new SmallImage(
                                this.musicControls, "pause", var15, 27, 38, 38, Resources.pausePNG,
                                new ColorHelper(ClientColors.LIGHT_GREYISH_BLUE.getColor()), null));
        this.musicControls
                .addToList(
                        this.forwards = new SmallImage(
                                this.musicControls, "forwards", var15 + 114, 23, 46, 46, Resources.forwardsPNG,
                                new ColorHelper(ClientColors.LIGHT_GREYISH_BLUE.getColor()), null));
        this.musicControls
                .addToList(
                        this.backwards = new SmallImage(
                                this.musicControls, "backwards", var15 - 114, 23, 46, 46, Resources.backwardsPNG,
                                new ColorHelper(ClientColors.LIGHT_GREYISH_BLUE.getColor()), null));
        this.musicControls.addToList(this.volumeSlider = new VolumeSlider(this.musicControls, "volume",
                this.getWidthA() - this.width - 19, 14, 4, 40));
        ChangingButton repeat;
        this.musicControls.addToList(repeat = new ChangingButton(this.musicControls, "repeat", 14, 34, 27, 20,
                this.musicManager.getRepeat()));
        repeat.onPress(var2x -> this.musicManager.setRepeat(repeat.getRepeatMode()));
        this.addToList(this.field20867 = new ProgressBar(this, "progress", this.width, this.getHeightA() - 5,
                this.getWidthA() - this.width, 5));
        this.field20867.setReAddChildren(true);
        this.field20867.setListening(false);
        this.field20865.setReAddChildren(true);
        this.field20865.method13247((var1x, var2x) -> {
            this.field20874 = true;
            this.field20871 = (float) this.getXA();
            this.field20872 = (float) this.getYA();
        });
        this.pause.setSelfVisible(false);
        this.play.setSelfVisible(false);
        this.play.onClick((var1x, var2x) -> this.musicManager.setPlaying(true));
        this.pause.onClick((var1x, var2x) -> this.musicManager.setPlaying(false));
        this.forwards.onClick((var1x, var2x) -> this.musicManager.playNextSong());
        this.backwards.onClick((var1x, var2x) -> this.musicManager.playPreviousSong());
        this.volumeSlider.method13709(
                var1x -> this.musicManager.setVolume((int) ((1.0F - this.volumeSlider.getVolume()) * 100.0F)));
        this.volumeSlider.setVolume(1.0F - (float) this.musicManager.getVolume() / 100.0F);
        this.addToList(
                this.searchBox = new SearchBox(
                        this, "search", this.width, 0, this.getWidthA() - this.width,
                        this.getHeightA() - this.field20848, "Search..."));
        this.searchBox.setPageActive(true);

        // 添加 "搜索" 选项卡按钮，使搜索框始终可访问
        Button searchTab = new MusicTabButton(
                this.musicTabs,
                "search_tab",
                0,
                0,  // Y位置将在tab列表头部
                this.width,
                this.height,
                color,
                "Search",
                ResourceRegistry.JelloLightFont14
        );
        this.musicTabs.addToList(searchTab);
        searchTab.onClick((var1x, var2x) -> this.showSearchBox());
    }

    /**
     * 显示搜索框，隐藏当前选中的内容面板
     */
    private void showSearchBox() {
        if (this.field20852 != null) {
            this.field20852.setSelfVisible(false);
        }
        this.searchBox.setPageActive(true);
        this.field20849 = "Search";
        this.field20852 = null;
        if (this.neteaseLoginBtn != null) {
            this.neteaseLoginBtn.setSelfVisible(false);
        }
    }

    private void method13189(ScrollableContentPanel var1) {
        if (this.field20852 != null) {
            this.field20852.setSelfVisible(false);
        }

        var1.setSelfVisible(true);
        this.field20849 = var1.getText();
        this.field20852 = var1;
        this.searchBox.setPageActive(false);
        this.field20852.field21207 = 65;

        // Show NE Login button only for Netease-related tabs
        if (this.neteaseLoginBtn != null) {
            String tabId = var1.getText();
            boolean isNetease = tabId != null && (tabId.contains("\u7f51\u6613") || tabId.contains("netease"));
            this.neteaseLoginBtn.setSelfVisible(isNetease);
        }
    }

    private void playSong(Thumbnails manager, YoutubeVideoData video) {
        this.musicManager.playSong(manager, video);
    }

    public static ThumbnailButton addTrackButton(
            MusicPlayer player,
            ScrollableContentPanel queue,
            Thumbnails manager,
            YoutubeVideoData song,
            int index) {
        if (player == null || queue == null || song == null) {
            return null;
        }
        if (song.videoId != null && queue.isntQueue(song.videoId)) {
            return null;
        }

        int x = 65;
        int y = 10;
        ThumbnailButton thumbnail = new ThumbnailButton(
                queue,
                y + index % 3 * 183 - (index % 3 <= 0 ? 0 : y) - (index % 3 <= 1 ? 0 : y),
                x + y + (index - index % 3) / 3 * 210,
                183,
                220,
                song);
        queue.addToList(thumbnail);
        thumbnail.onClick((parent, mouseButton) -> {
            MusicPlayer.playSong(player, manager, song);
        });
        return thumbnail;
    }

    @Override
    public void updatePanelDimensions(int newHeight, int newWidth) {
        long var5 = System.nanoTime() - time;
        float var7 = Math.min(10.0F, Math.max(0.0F, (float) var5 / 1.810361E7F));
        time = System.nanoTime();
        super.updatePanelDimensions(newHeight, newWidth);
        if (this.parent != null) {
            if (!this.method13216()) {
                if ((this.field20909 || this.field20874) && !this.method13214() && !this.method13216()) {
                    this.field20874 = true;
                    int var11 = this.parent.getWidthA() - 20 - this.getWidthA();
                    int var13 = (this.parent.getHeightA() - this.getHeightA()) / 2;
                    this.field20871 = Math.max(this.field20871 - (this.field20871 - (float) var11) * 0.25F * var7,
                            (float) var11);
                    if (!(this.field20872 - (float) var13 > 0.0F)) {
                        Math.min(this.field20872 = this.field20872 - (this.field20872 - (float) var13) * 0.2F * var7,
                                (float) var13);
                    } else {
                        Math.max(this.field20872 = this.field20872 - (this.field20872 - (float) var13) * 0.2F * var7,
                                (float) var13);
                    }

                    if (!(this.field20871 - (float) var11 < 0.0F)) {
                        if (this.field20871 - (float) var11 - (float) this.getWidthA() > 0.0F) {
                            this.field20871 = (float) var11;
                        }
                    } else {
                        this.field20871 = (float) var11;
                    }

                    this.setXA((int) this.field20871);
                    this.setYA((int) this.field20872);
                    if (Math.abs(this.field20871 - (float) var11) < 2.0F
                            && Math.abs(this.field20872 - (float) var13) < 2.0F) {
                        this.method13215(true);
                        this.field20874 = false;
                    }
                } else if (this.getXA() + this.getWidthA() > this.parent.getWidthA() || this.getXA() < 0
                        || this.getYA() < 0) {
                    if (this.field20871 == 0.0F || this.field20872 == 0.0F) {
                        this.field20871 = (float) this.getXA();
                        this.field20872 = (float) this.getYA();
                    }

                    int var8 = this.parent.getWidthA() - 40;
                    int var9 = (this.parent.getHeightA() - this.getHeightA()) / 2;
                    this.field20871 = Math.min(this.field20871 - (this.field20871 - (float) var8) * 0.25F * var7,
                            (float) var8);
                    if (!(this.field20872 - (float) var9 > 0.0F)) {
                        Math.min(this.field20872 = this.field20872 - (this.field20872 - (float) var9) * 0.2F * var7,
                                (float) var9);
                    } else {
                        Math.max(this.field20872 = this.field20872 - (this.field20872 - (float) var9) * 0.2F * var7,
                                (float) var9);
                    }

                    if (!(this.field20871 - (float) var8 > 0.0F)) {
                        if (this.field20871 - (float) var8 + (float) this.getWidthA() < 0.0F) {
                            this.field20871 = (float) var8;
                        }
                    } else {
                        this.field20871 = (float) var8;
                    }

                    if (Math.abs(this.field20871 - (float) var8) < 2.0F
                            && Math.abs(this.field20872 - (float) var9) < 2.0F) {
                        this.field20871 = (float) this.getXA();
                        this.field20872 = (float) this.getYA();
                    }

                    this.setXA((int) this.field20871);
                    this.setYA((int) this.field20872);
                    this.method13215(false);
                    this.method13217(false);
                }
            } else {
                int var12 = newHeight - this.sizeWidthThingy - (this.parent == null ? 0 : this.parent.method13271());
                int var14 = 200;
                if (var12 + this.getWidthA() > this.parent.getWidthA() + var14 && newHeight - this.mouseX > 70) {
                    int var15 = var12 - this.getXA() - var14;
                    this.setXA((int) ((float) this.getXA() + (float) var15 * 0.5F));
                    this.field20871 = (float) this.getXA();
                    this.field20872 = (float) this.getYA();
                }
            }
        }
    }

    @Override
    public void draw(float partialTicks) {
        super.method13224();
        super.method13225();
        this.field20865.setWidthA(this.getXA() + this.getWidthA() <= this.parent.getWidthA() ? 0 : 41);
        this.field20873
                .changeDirection(this.getXA() + this.getWidthA() > this.parent.getWidthA() && !this.field20874
                        ? Animation.Direction.FORWARDS
                        : Animation.Direction.BACKWARDS);
        partialTicks *= 0.5F + (1.0F - this.field20873.calcPercent()) * 0.5F;
        if (this.musicManager.isPlayingSong()) {
            this.play.setSelfVisible(false);
            this.pause.setSelfVisible(true);
        } else {
            this.play.setSelfVisible(true);
            this.pause.setSelfVisible(false);
        }

        RenderUtil.drawRoundedRect(
                (float) (this.getXA() + this.width),
                (float) this.getYA(),
                (float) (this.getXA() + this.getWidthA()),
                (float) (this.getYA() + this.getHeightA() - this.field20848),
                RenderUtil2.applyAlpha(-14277082, partialTicks * 0.8F));
        RenderUtil.drawRoundedRect(
                (float) this.getXA(),
                (float) this.getYA(),
                (float) (this.getXA() + this.width),
                (float) (this.getYA() + this.getHeightA() - this.field20848),
                RenderUtil2.applyAlpha(-16777216, partialTicks * 0.95F));
        this.method13193(partialTicks);
        this.method13194(partialTicks);
        this.method13192(partialTicks);
        float var4 = 55;
        RenderUtil.drawString(
                ResourceRegistry.JelloLightFont40,
                var4 + this.getXA(),
                (float) (this.getYA() + 20),
                "Jello",
                RenderUtil2.applyAlpha(ClientColors.LIGHT_GREYISH_BLUE.getColor(), partialTicks));
        RenderUtil.drawString(
                ResourceRegistry.JelloLightFont20,
                var4 + this.getXA() + 80,
                (float) (this.getYA() + 40),
                "music",
                RenderUtil2.applyAlpha(ClientColors.LIGHT_GREYISH_BLUE.getColor(), partialTicks));
        RenderUtil.drawRoundedRect((float) this.getXA(), (float) this.getYA(), (float) this.getWidthA(),
                (float) this.getHeightA(), 14.0F, partialTicks);
        super.draw(partialTicks);
        if (this.field20852 != null) {
            this.method13196(partialTicks);
        }

        // Render Netease QR code overlay
        // After super.draw(), GL matrix has drawChildren's glTranslatef(getXA(), getYA())
        // so GL rendering uses panel-local coords (0-based).
        // Skija ignores the GL matrix and needs absolute screen coords.
        if (this.showNeteaseQr && this.neteaseQrImage != null) {
            // Panel-local coords for GL
            float overlayWidth = 240.0F;
            float overlayHeight = 300.0F;
            float qrSize = 200.0F;
            float padding = 20.0F;
            float overlayX = (this.getWidthA() - overlayWidth) / 2.0F;
            float overlayY = (this.getHeightA() - overlayHeight) / 2.0F;
            float localX = overlayX + padding;
            float localY = overlayY + padding;
            // Absolute coords for Skija
            float absX = this.getXA() + localX;
            float absY = this.getYA() + localY;

            float overlayAlpha = partialTicks;
            RenderUtil.drawRoundedRect(
                    overlayX, overlayY,
                    overlayWidth, overlayHeight,
                    14.0F, overlayAlpha);

            int backgroundColor = RenderUtil2.applyAlpha(-16777216, overlayAlpha * 0.88F);
            RenderUtil.drawRoundedRect(
                    overlayX, overlayY,
                    overlayX + overlayWidth, overlayY + overlayHeight,
                    backgroundColor);

            int borderColor = RenderUtil2.applyAlpha(-16777216, overlayAlpha * 0.18F);
            RenderUtil.drawRoundedRect(overlayX, overlayY, overlayX + overlayWidth, overlayY + 1.0F, borderColor);
            RenderUtil.drawRoundedRect(overlayX, overlayY + overlayHeight - 1.0F, overlayX + overlayWidth, overlayY + overlayHeight, borderColor);
            RenderUtil.drawRoundedRect(overlayX, overlayY + 1.0F, overlayX + 1.0F, overlayY + overlayHeight - 1.0F, borderColor);
            RenderUtil.drawRoundedRect(overlayX + overlayWidth - 1.0F, overlayY + 1.0F, overlayX + overlayWidth, overlayY + overlayHeight - 1.0F, borderColor);

            RenderUtil.drawBlurredBackground(
                    (int) (this.getXA() + overlayX),
                    (int) (this.getYA() + overlayY),
                    (int) (this.getXA() + overlayX + overlayWidth),
                    (int) (this.getYA() + overlayY + overlayHeight));

            // GL: upload QR texture if needed
            if (this.neteaseQrTexture == null && this.neteaseQrImage != null) {
                try {
                    java.awt.image.BufferedImage argbImage = new java.awt.image.BufferedImage(
                            this.neteaseQrImage.getWidth(), this.neteaseQrImage.getHeight(),
                            java.awt.image.BufferedImage.TYPE_INT_ARGB);
                    java.awt.Graphics2D g2d = argbImage.createGraphics();
                    g2d.drawImage(this.neteaseQrImage, 0, 0, null);
                    g2d.dispose();
                    this.neteaseQrTexture = BufferedImageUtil.getTexture("neteaseQr", argbImage);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }

            // GL: draw QR image
            if (this.neteaseQrTexture != null) {
                RenderUtil.drawImage(localX, localY, qrSize, qrSize,
                        this.neteaseQrTexture,
                        RenderUtil2.applyAlpha(-1, partialTicks));
            }
            RenderUtil.restoreScissor();

            // Skija: draw text hints using absolute screen coords
            if (this.ensureSkijaReady()) {
                int sw = net.minecraft.client.Minecraft.getInstance().getMainWindow().getFramebufferWidth();
                int sh = net.minecraft.client.Minecraft.getInstance().getMainWindow().getFramebufferHeight();
                SkijaFontRenderer.beginFrame(sw, sh);
                String qrHint = "\u8bf7\u7528\u7f51\u6613\u4e91\u97f3\u4e50 App \u626b\u7801";
                float tw = SkijaFontRenderer.getTextWidth(qrHint, 16f);
                SkijaFontRenderer.drawText(qrHint,
                        absX + (qrSize - tw) / 2, absY + 210, 16f,
                        RenderUtil2.applyAlpha(ClientColors.LIGHT_GREYISH_BLUE.getColor(), partialTicks));
                String closeHint = "\u70b9\u51fb\u4efb\u610f\u4f4d\u7f6e\u5173\u95ed";
                float tw2 = SkijaFontRenderer.getTextWidth(closeHint, 14f);
                SkijaFontRenderer.drawText(closeHint,
                        absX + (qrSize - tw2) / 2, absY + 235, 14f,
                        RenderUtil2.applyAlpha(ClientColors.LIGHT_GREYISH_BLUE.getColor(), partialTicks * 0.7f));
                SkijaFontRenderer.endFrame();
            }
        }
    }

    private void method13192(float var1) {
        int duration1 = (int) this.musicManager.getDuration();
        int duration = this.musicManager.getDurationInt();
        String timeElapsed = YoutubeUtil.parseSongTime(duration1);
        String timeTotal = YoutubeUtil.parseSongTime(duration);
        RenderUtil.drawString(
                ResourceRegistry.JelloLightFont14,
                (float) (this.getXA() + this.width + 14),
                (float) (this.getYA() + this.getHeightA() - 10) - 22.0F * var1,
                timeElapsed,
                RenderUtil2.applyAlpha(ClientColors.LIGHT_GREYISH_BLUE.getColor(), var1 * var1));
        RenderUtil.drawString(
                ResourceRegistry.JelloLightFont14,
                (float) (this.getXA() + this.getWidthA() - 14
                        - ResourceRegistry.JelloLightFont14.getWidth(timeTotal)),
                (float) (this.getYA() + this.getHeightA() - 10) - 22.0F * var1,
                timeTotal,
                RenderUtil2.applyAlpha(ClientColors.LIGHT_GREYISH_BLUE.getColor(), var1 * var1));
    }

    private void method13193(float var1) {
        Texture var4 = this.musicManager.getNotificationImage();
        Texture var5 = this.musicManager.getSongThumbnail();
        // Guard: wait until both artwork textures are fully uploaded before using dynamic artwork color.
        if (this.musicManager.hasReadySongArtwork() && var4 != null && var5 != null) {
            int accentColor = this.musicManager.getDynamicCoverColor();
            int overlayColor = RenderUtil2.shiftTowardsOther(accentColor, ClientColors.DEEP_TEAL.getColor(), 0.45F);
            RenderUtil.drawImage(
                    (float) this.getXA(),
                    (float) (this.getYA() + this.getHeightA() - this.field20848),
                    (float) this.getWidthA(),
                    (float) this.field20848,
                    var5,
                    RenderUtil2.applyAlpha(ClientColors.LIGHT_GREYISH_BLUE.getColor(), var1 * var1)
            );
            RenderUtil.drawRoundedRect(
                    (float) this.getXA(),
                    (float) (this.getYA() + this.getHeightA() - this.field20848),
                    (float) (this.getXA() + this.getWidthA()),
                    (float) (this.getYA() + this.getHeightA() - 5),
                    RenderUtil2.applyAlpha(/*overlayColor*/ClientColors.DEEP_TEAL.getColor(), 0.43F * var1)
            );
            RenderUtil.drawRoundedRect(
                    (float) this.getXA(),
                    (float) (this.getYA() + this.getHeightA() - 5),
                    (float) (this.getXA() + this.width),
                    (float) (this.getYA() + this.getHeightA()),
                    RenderUtil2.applyAlpha(/*overlayColor*/ClientColors.DEEP_TEAL.getColor(), 0.43F * var1)
            );
            RenderUtil.drawImage(
                    (float) (this.getXA() + (this.width - 114) / 2),
                    (float) (this.getYA() + this.getHeightA() - 170),
                    114.0F,
                    114.0F,
                    var4,
                    RenderUtil2.applyAlpha(ClientColors.LIGHT_GREYISH_BLUE.getColor(), var1)
            );
            RenderUtil.drawRoundedRect(
                    (float) (this.getXA() + (this.width - 114) / 2), (float) (this.getYA() + this.getHeightA() - 170), 114.0F, 114.0F, 14.0F, var1
            );
        } else {
            RenderUtil.drawImage(
                    (float) this.getXA(),
                    (float) (this.getYA() + this.getHeightA() - this.field20848),
                    (float) this.getWidthA(),
                    (float) this.field20848,
                    Resources.bgPNG,
                    RenderUtil2.applyAlpha(ClientColors.LIGHT_GREYISH_BLUE.getColor(), var1 * var1)
            );
            RenderUtil.drawRoundedRect(
                    (float) this.getXA(),
                    (float) (this.getYA() + this.getHeightA() - this.field20848),
                    (float) (this.getXA() + this.getWidthA()),
                    (float) (this.getYA() + this.getHeightA() - 5),
                    RenderUtil2.applyAlpha(ClientColors.DEEP_TEAL.getColor(), 0.43F * var1)
            );
            RenderUtil.drawRoundedRect(
                    (float) this.getXA(),
                    (float) (this.getYA() + this.getHeightA() - 5),
                    (float) (this.getXA() + this.width),
                    (float) (this.getYA() + this.getHeightA()),
                    RenderUtil2.applyAlpha(ClientColors.DEEP_TEAL.getColor(), 0.43F * var1)
            );
            RenderUtil.drawImage(
                    (float) (this.getXA() + (this.width - 114) / 2),
                    (float) (this.getYA() + this.getHeightA() - 170),
                    114.0F,
                    114.0F,
                    Resources.artworkPNG,
                    RenderUtil2.applyAlpha(ClientColors.LIGHT_GREYISH_BLUE.getColor(), var1)
            );
            RenderUtil.drawRoundedRect(
                    (float) (this.getXA() + (this.width - 114) / 2), (float) (this.getYA() + this.getHeightA() - 170), 114.0F, 114.0F, 14.0F, var1
            );
        }

        // Restore GL state after cover art texture rendering to prevent state leak
        // that would cause sidebar buttons to render as white rectangles
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);
        GL11.glColor4f(1.0f, 1.0f, 1.0f, 1.0f);
        RenderSystem.disableTexture();
        RenderSystem.color4f(1.0f, 1.0f, 1.0f, 1.0f);
        RenderSystem.enableTexture();
    }

    private void method13194(float var1) {
        String songTitle = this.musicManager.getSongTitle();
        if (songTitle != null && !songTitle.trim().isEmpty()) {
            TrackTitleParts titleParts = this.parseTrackTitle(songTitle);
            int var5 = 30;

            String lyric = this.musicManager.getCurrentLyric();
            boolean hasLyric = lyric != null && !lyric.isEmpty();
            String primaryText = hasLyric ? lyric : (!titleParts.title.isEmpty() ? titleParts.title : "Jello Music");
            String secondaryText = titleParts.artist;

            this.drawSkijaString(var1, primaryText, this.width - var5 * 2, 0, 0);

            if (secondaryText != null && !secondaryText.isEmpty()) {
                this.drawSkijaString(var1, secondaryText, this.width - var5 * 2, 20, -1000);
            }
        }
    }

    private TrackTitleParts parseTrackTitle(String rawTitle) {
        String safeTitle = rawTitle == null ? "" : rawTitle.trim();
        if (safeTitle.isEmpty()) {
            return new TrackTitleParts("", "", "");
        }

        String[] delimiters = {" - ", " – ", " — ", "-", "–", "—"};
        for (String delimiter : delimiters) {
            int splitAt = safeTitle.indexOf(delimiter);
            if (splitAt > 0 && splitAt + delimiter.length() < safeTitle.length()) {
                String artist = safeTitle.substring(0, splitAt).trim();
                String title = safeTitle.substring(splitAt + delimiter.length()).trim();
                if (!artist.isEmpty() && !title.isEmpty()) {
                    return new TrackTitleParts(artist, title, safeTitle);
                }
            }
        }

        return new TrackTitleParts("", safeTitle, "");
    }

    private static class TrackTitleParts {
        private final String artist;
        private final String title;
        private final String fallback;

        private TrackTitleParts(String artist, String title, String fallback) {
            this.artist = artist;
            this.title = title;
            this.fallback = fallback;
        }
    }

    private void drawSkijaString(float var1, String text, int var3, int var4, int var5) {
        boolean skijaReady = this.ensureSkijaReady();
        Date var8 = new Date();
        float var9 = (float) ((var8.getTime() + (long) var5) % 8500L) / 8500.0F;
        if (!(var9 < 0.4F)) {
            var9 -= 0.4F;
            var9 = (float) ((double) var9 * 1.6666666666666667);
        } else {
            var9 = 0.0F;
        }

        var9 = QuadraticEasing.easeInOutQuad(var9, 0.0F, 1.0F, 1.0F);

        int var10 = Math.round(skijaReady ? SkijaFontRenderer.getTextWidth(text, 14f)
                : ResourceRegistry.JelloLightFont14.getWidth(text));
        int var11 = Math.min(var3, var10);
        int var12 = 14;
        int var13 = this.getXA() + (this.width - var11) / 2;
        int var14 = this.getYA() + this.getHeightA() - 50 + var4;

        if (var10 <= var3) {
            var9 = 0.0F;
        }

        RenderUtil.startScissor(var13, var14, var13 + var11, var14 + var12, true);
        int dimColor = RenderUtil2.applyAlpha(
                ClientColors.LIGHT_GREYISH_BLUE.getColor(),
                var1 * var1 * Math.min(1.0F, Math.max(0.0F, 1.0F - var9 * 0.75F))
        );
        float textX = (float) var13 - (float) var10 * var9 - 50.0F * var9;

        if (skijaReady) {
            int sw = net.minecraft.client.Minecraft.getInstance().getMainWindow().getFramebufferWidth();
            int sh = net.minecraft.client.Minecraft.getInstance().getMainWindow().getFramebufferHeight();
            SkijaFontRenderer.beginFrame(sw, sh);
            SkijaFontRenderer.drawText(text, textX, (float) var14, 14f, dimColor);
            SkijaFontRenderer.endFrame();
        } else {
            RenderUtil.drawString(ResourceRegistry.JelloLightFont14, textX, (float) var14, text, dimColor);
        }

        if (var9 > 0.0F) {
            int color = RenderUtil2.applyAlpha(ClientColors.LIGHT_GREYISH_BLUE.getColor(), var1 * var1);
            float loopTextX = (float) var13 - (float) var10 * var9 + (float) var10;

            if (skijaReady) {
                int sw = net.minecraft.client.Minecraft.getInstance().getMainWindow().getFramebufferWidth();
                int sh = net.minecraft.client.Minecraft.getInstance().getMainWindow().getFramebufferHeight();
                SkijaFontRenderer.beginFrame(sw, sh);
                SkijaFontRenderer.drawText(text, loopTextX, (float) var14, 14f, color);
                SkijaFontRenderer.endFrame();
            } else {
                RenderUtil.drawString(ResourceRegistry.JelloLightFont14, loopTextX, (float) var14, text, color);
            }
        }
        RenderUtil.restoreScissor();
    }

    private void drawLyricString(float var1, String text, int var3, int var4, int var5) {
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
        boolean skijaReady = this.ensureSkijaReady();

        // Use Skija for lyric rendering
        if (skijaReady) {
            float fontSize = 14.0f;
            float textWidth = SkijaFontRenderer.getTextWidth(text, fontSize);
            int var11 = Math.min(var3, (int) textWidth);
            int var12 = (int) fontSize;
            int var13 = this.getXA() + (this.width - var11) / 2;
            int var14 = this.getYA() + this.getHeightA() - 50 + var4;

            float progress = this.musicManager.getLyricProgress();
            int progressX = var13 + (int) (var11 * progress) + 3;

            int screenWidth = mc.getMainWindow().getFramebufferWidth();
            int screenHeight = mc.getMainWindow().getFramebufferHeight();

            // Draw dim base text
            int dimColor = RenderUtil2.applyAlpha(ClientColors.LIGHT_GREYISH_BLUE.getColor(), var1 * 0.4f);
            SkijaFontRenderer.beginFrame(screenWidth, screenHeight);
            SkijaFontRenderer.drawText(text, var13, var14, fontSize, dimColor);
            SkijaFontRenderer.endFrame();

            // Draw highlighted portion with scissor
            RenderUtil.startScissor(var13, var14, progressX, var14 + var12, true);
            int brightColor = RenderUtil2.applyAlpha(ClientColors.LIGHT_GREYISH_BLUE.getColor(), var1);
            SkijaFontRenderer.beginFrame(screenWidth, screenHeight);
            SkijaFontRenderer.drawText(text, var13, var14, fontSize, brightColor);
            SkijaFontRenderer.endFrame();
            RenderUtil.restoreScissor();
        } else {
            // Fallback to Minecraft font renderer
            int var10 = mc.fontRenderer.getStringWidth(text);
            int var11 = Math.min(var3, var10);
            int var12 = mc.fontRenderer.FONT_HEIGHT;
            int var13 = this.getXA() + (this.width - var11) / 2;
            int var14 = this.getYA() + this.getHeightA() - 50 + var4;

            float progress = this.musicManager.getLyricProgress();
            int progressX = var13 + (int) (var11 * progress) + 3;

            com.mojang.blaze3d.matrix.MatrixStack matrixStack = new com.mojang.blaze3d.matrix.MatrixStack();

            RenderUtil.startScissor(var13, var14, var13 + var11, var14 + var12, true);
            int dimColor = RenderUtil2.applyAlpha(ClientColors.LIGHT_GREYISH_BLUE.getColor(), var1 * 0.4f);
            mc.fontRenderer.drawString(matrixStack, text, (float) var13, (float) var14, dimColor);
            RenderUtil.restoreScissor();

            RenderUtil.startScissor(var13, var14, progressX, var14 + var12, true);
            int brightColor = RenderUtil2.applyAlpha(ClientColors.LIGHT_GREYISH_BLUE.getColor(), var1);
            mc.fontRenderer.drawString(matrixStack, text, (float) var13, (float) var14, brightColor);
            RenderUtil.restoreScissor();
        }
    }

    private void method13196(float var1) {
        this.field20852.setReAddChildren(false);
        if (this.field20863 != this.field20852.method13513()) {
            try {
                if (this.texture != null) {
                    this.texture.release();
                }

                this.texture = BufferedImageUtil.getTexture(
                        "blur",
                        ImageUtil.method35037(this.getXA() + this.width, this.getYA(), this.getWidthA() - this.width,
                                this.field20847, 10, 10));
            } catch (IOException var5) {
                var5.printStackTrace();
            }
        }

        float var4 = this.field20863 < 50 ? (float) this.field20863 / 50.0F : 1.0F;
        if (this.texture != null) {
            RenderUtil.drawImage(
                    (float) this.width,
                    0.0F,
                    (float) (this.getWidthA() - this.width),
                    (float) this.field20847,
                    this.texture,
                    RenderUtil2.applyAlpha(ClientColors.LIGHT_GREYISH_BLUE.getColor(), var4 * var1));
        }

        // 使用深色半透明overlay代替白色overlay，避免产生白色条带
        RenderUtil.drawRoundedRect(
                (float) this.width,
                0.0F,
                (float) this.getWidthA(),
                (float) this.field20847,
                RenderUtil2.applyAlpha(ClientColors.DEEP_TEAL.getColor(), var4 * var1 * 0.6F));

        // Use Skija for header title (CJK safe)
        if (this.ensureSkijaReady()) {
            int sw = net.minecraft.client.Minecraft.getInstance().getMainWindow().getFramebufferWidth();
            int sh = net.minecraft.client.Minecraft.getInstance().getMainWindow().getFramebufferHeight();
            float fontSize = 25.0f;
            float titleWidth = SkijaFontRenderer.getTextWidth(this.field20849, fontSize);
            float titleX = (float) ((this.getWidthA() - (int) titleWidth + this.width) / 2);
            float titleY = 16.0F + (1.0F - var4) * 14.0F;
            SkijaFontRenderer.beginFrame(sw, sh);
            SkijaFontRenderer.drawText(this.field20849, titleX, titleY, fontSize,
                    RenderUtil2.applyAlpha(ClientColors.LIGHT_GREYISH_BLUE.getColor(), var4));
            SkijaFontRenderer.endFrame();
        } else {
            // Fallback to hybrid font rendering
            RenderUtil.drawHybridString(
                    ResourceRegistry.JelloLightFont25,
                    (float) ((this.getWidthA() - RenderUtil.getHybridStringWidth(ResourceRegistry.JelloLightFont25, this.field20849) + this.width)
                            / 2),
                    16.0F + (1.0F - var4) * 14.0F,
                    this.field20849,
                    RenderUtil2.applyAlpha(ClientColors.LIGHT_GREYISH_BLUE.getColor(), var4));
            RenderUtil.drawHybridString(
                    ResourceRegistry.JelloMediumFont25,
                    (float) ((this.getWidthA() - RenderUtil.getHybridStringWidth(ResourceRegistry.JelloMediumFont25, this.field20849) + this.width)
                            / 2),
                    16.0F + (1.0F - var4) * 14.0F,
                    this.field20849,
                    RenderUtil2.applyAlpha(ClientColors.LIGHT_GREYISH_BLUE.getColor(), 1.0F - var4));
        }
        RenderUtil.drawImage(
                (float) this.width,
                (float) this.field20847,
                (float) (this.getWidthA() - this.width),
                20.0F,
                Resources.shadowBottomPNG,
                RenderUtil2.applyAlpha(ClientColors.LIGHT_GREYISH_BLUE.getColor(), var4 * var1 * 0.5F));
        this.field20863 = this.field20852.method13513();
    }

    private boolean ensureSkijaReady() {
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
        return SkijaFontRenderer.ensureInitialized(
                mc.getMainWindow().getFramebufferWidth(),
                mc.getMainWindow().getFramebufferHeight());
    }

    @Override
    public boolean onClick(int mouseX, int mouseY, int mouseButton) {
        // Dismiss Netease QR overlay on click
        if (this.showNeteaseQr) {
            this.closeNeteaseQrOverlay(true);
            return true;
        }
        return super.onClick(mouseX, mouseY, mouseButton);
    }

    private static boolean hasVideo(String videoId) {
        for (Thumbnails existing : videos) {
            if (existing.videoId.equals(videoId)) {
                return true;
            }
        }
        return false;
    }

    private void showNeteaseQrOverlay(java.awt.image.BufferedImage qrImage, String uniKey) {
        this.neteaseQrImage = qrImage;
        this.neteaseQrUniKey = uniKey;
        this.neteaseQrTexture = null;
        this.showNeteaseQr = true;
    }

    private void closeNeteaseQrOverlay(boolean releaseTexture) {
        this.showNeteaseQr = false;
        this.neteaseQrImage = null;
        this.neteaseQrUniKey = null;
        if (releaseTexture && this.neteaseQrTexture != null) {
            this.neteaseQrTexture.release();
        }
        this.neteaseQrTexture = null;
    }

    private void loadUserNeteasePlaylists(ColorHelper color, MusicPlayer player) {
        new Thread(() -> {
            try {
                System.out.println("[MusicPlayer] loadUserNeteasePlaylists: isLoggedIn=" + NeteaseApiLogin.isLoggedIn()
                        + ", userId=" + NeteaseApiLogin.getUserId());
                if (!NeteaseApiLogin.isLoggedIn()) {
                    System.out.println("[MusicPlayer] Not logged in, skipping playlist load");
                    return;
                }

                if (NeteaseApiLogin.getUserId() == 0) {
                    System.out.println("[MusicPlayer] userId is 0, fetching login status...");
                    NeteaseApiLogin.getLoginStatus(null);
                    System.out.println("[MusicPlayer] After getLoginStatus: userId=" + NeteaseApiLogin.getUserId());
                    if (NeteaseApiLogin.getUserId() == 0) {
                        System.err.println("[MusicPlayer] Still no userId after getLoginStatus, aborting");
                        return;
                    }
                }

                List<NeteaseRequestApi.ToplistInfo> playlists = NeteaseRequestApi.getUserPlaylists(0);
                System.out.println("[MusicPlayer] Got " + playlists.size() + " user playlists");
                List<Thumbnails> added = new ArrayList<>();
                for (NeteaseRequestApi.ToplistInfo playlist : playlists) {
                    String videoId = NETEASE_PLAYLIST_PREFIX + playlist.id;
                    if (!hasVideo(videoId)) {
                        Thumbnails thumbnail = new Thumbnails(playlist.name, videoId, YoutubeContentType.NETEASE);
                        videos.add(thumbnail);
                        added.add(thumbnail);
                        System.out.println("[MusicPlayer] Added playlist: " + playlist.name + " (id=" + playlist.id + ")");
                    }
                }

                System.out.println("[MusicPlayer] Loading songs for " + added.size() + " new playlists...");
                for (Thumbnails thumbnail : added) {
                    if (!videoMap.containsKey(thumbnail.videoId) && !thumbnail.isUpdated) {
                        thumbnail.isUpdated = true;
                        thumbnail.refreshVideoList();
                        videoMap.put(thumbnail.videoId, thumbnail);
                    }
                    System.out.println("[MusicPlayer] Scheduling MusicInitializer for: " + thumbnail.name
                            + " (" + thumbnail.videoList.size() + " songs)");
                    this.runThisOnDimensionUpdate(new MusicInitializer(this, thumbnail, color, player));
                }
                System.out.println("[MusicPlayer] loadUserNeteasePlaylists complete");
            } catch (Exception e) {
                System.err.println("[MusicPlayer] loadUserNeteasePlaylists FAILED");
                e.printStackTrace();
            }
        }, "NeteaseUserPlaylistsLoader").start();
    }

    public static ScrollableContentPanel getTabs(MusicPlayer player) {
        return player.musicTabs;
    }

    public static int getHeight(MusicPlayer player) {
        return player.height;
    }

    public static int getWidth(MusicPlayer player) {
        return player.width;
    }

    public static int method13209(MusicPlayer player) {
        return player.field20848;
    }

    public static void method13210(MusicPlayer player, ScrollableContentPanel tabs) {
        player.method13189(tabs);
    }

    public static void playSong(MusicPlayer player, Thumbnails videoManager, YoutubeVideoData video) {
        player.playSong(videoManager, video);
    }
}
