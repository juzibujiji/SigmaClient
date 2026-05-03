package com.mentalfrostbyte.jello.gui.impl.jello.ingame.clickgui.musicplayer.elements;



import com.mentalfrostbyte.Client;

import com.mentalfrostbyte.jello.gui.base.elements.impl.button.types.ThumbnailButton;

import com.mentalfrostbyte.jello.gui.combined.CustomGuiScreen;

import com.mentalfrostbyte.jello.gui.combined.AnimatedIconPanel;

import com.mentalfrostbyte.jello.gui.impl.jello.buttons.ScrollableContentPanel;

import com.mentalfrostbyte.jello.gui.impl.jello.buttons.TextField;

import com.mentalfrostbyte.jello.managers.MusicManager;

import com.mentalfrostbyte.jello.util.client.network.netease.NeteaseApiSearch;

import com.mentalfrostbyte.jello.util.client.network.youtube.YoutubeVideoData;

import com.mentalfrostbyte.jello.util.client.render.theme.ColorHelper;



import java.util.ArrayList;

import java.util.HashMap;

import java.util.List;

import java.util.Map;



public class SearchBox extends AnimatedIconPanel {

    public ScrollableContentPanel field20840;

    public TextField searchBox;

    private ArrayList<YoutubeVideoData> field20842;

    private final MusicManager field20843 = Client.getInstance().musicManager;



    public SearchBox(CustomGuiScreen var1, String var2, int var3, int var4, int var5, int var6, String var7) {

        super(var1, var2, var3, var4, var5, var6, ColorHelper.field27961, var7, false);

        this.addToList(this.field20840 = new ScrollableContentPanel(this, "albumView", 0, 0, var5, var6, ColorHelper.field27961, "View"));

        this.addToList(this.searchBox = new TextField(this, "searchInput", 30, 14, var5 - 60, 70, TextField.field20742, "", "Search..."));

        this.searchBox.setReAddChildren(true);

    }



    public void setPageActive(boolean active) {

        this.setSelfVisible(active);

        this.setListening(active);

        this.searchBox.setSelfVisible(active);

        this.searchBox.setListening(active);

        this.field20840.setSelfVisible(active);

        this.field20840.setListening(active);

        if (!active) {

            this.searchBox.setFocused(false);

        }

    }



    private static String normalizeCoverUrl(String coverUrl) {

        if (coverUrl == null) {

            return "";

        }



        String normalized = coverUrl.trim();

        if (normalized.isEmpty()) {

            return "";

        }



        if (normalized.startsWith("//")) {

            normalized = "https:" + normalized;

        }



        if (normalized.startsWith("http://") || normalized.startsWith("https://")) {

            if (!normalized.contains("param=")) {

                normalized += (normalized.contains("?") ? "&" : "?") + "param=300y300";

            }

            return normalized;

        }



        return "";

    }



    @Override

    public void draw(float partialTicks) {

        super.draw(partialTicks);

    }



    @Override

    public void keyPressed(int keyCode) {

        if (keyCode == 257 && this.searchBox.isFocused()) {

            this.searchBox.setFocused(false);

            new Thread(

                    () -> {

                        this.field20842 = new ArrayList<>();



                        // 使用网易云音乐搜索替代 YouTube

                        List<NeteaseApiSearch.NeteaseTrack> tracks =

                                NeteaseApiSearch.search(this.searchBox.getText());

                        long[] trackIds = new long[tracks.size()];
                        for (int i = 0; i < tracks.size(); i++) {
                            trackIds[i] = tracks.get(i).id;
                        }

                        Map<Long, NeteaseApiSearch.NeteaseSongUrl> playableUrlMap =
                                NeteaseApiSearch.getSongUrlMap(trackIds);



                        List<Long> missingCoverIds = new ArrayList<>();

                        for (NeteaseApiSearch.NeteaseTrack track : tracks) {

                            if (normalizeCoverUrl(track.coverUrl).isEmpty() && track.id > 0) {

                                missingCoverIds.add(track.id);

                            }

                        }



                        Map<Long, String> detailCoverMap = new HashMap<>();

                        if (!missingCoverIds.isEmpty()) {

                            long[] ids = new long[missingCoverIds.size()];

                            for (int i = 0; i < missingCoverIds.size(); i++) {

                                ids[i] = missingCoverIds.get(i);

                            }

                            List<NeteaseApiSearch.NeteaseTrack> details = NeteaseApiSearch.getSongDetail(ids);

                            for (NeteaseApiSearch.NeteaseTrack detail : details) {

                                detailCoverMap.put(detail.id, normalizeCoverUrl(detail.coverUrl));

                            }

                        }



                        for (NeteaseApiSearch.NeteaseTrack track : tracks) {

                            NeteaseApiSearch.NeteaseSongUrl playableUrl = playableUrlMap.get(track.id);
                            if (!playableUrlMap.isEmpty()
                                    && (playableUrl == null || playableUrl.url == null || playableUrl.url.isEmpty())) {
                                continue;
                            }

                            String normalizedCover = normalizeCoverUrl(track.coverUrl);

                            if (normalizedCover.isEmpty()) {

                                normalizedCover = detailCoverMap.getOrDefault(track.id, "");

                            }



                            // 使用 netease:// 占位URL，播放时再延迟解析真实URL

                            // 避免搜索时对每首歌逐个调用 getSongUrl 造成阻塞

                            this.field20842.add(new YoutubeVideoData(

                                    playableUrl != null && playableUrl.url != null && !playableUrl.url.isEmpty()
                                            ? playableUrl.url
                                            : "netease://" + track.id,

                                    track.getDisplayTitle(),

                                    normalizedCover,

                                    track.id,

                                    track.duration

                            ));

                        }



                        this.runThisOnDimensionUpdate(

                                () -> {

                                    this.removeChildren(this.field20840);

                                    this.addToList(

                                            this.field20840 = new ScrollableContentPanel(this, "albumView", 0, 0, this.widthA, this.heightA, ColorHelper.field27961, "View")

                                    );

                                    if (this.field20842 != null) {

                                        for (int var3x = 0; var3x < this.field20842.size(); var3x++) {

                                            YoutubeVideoData var4 = this.field20842.get(var3x);

                                            ThumbnailButton var7x;

                                            this.field20840

                                                    .addToList(

                                                            var7x = new ThumbnailButton(

                                                                    this.field20840,

                                                                    10 + var3x % 3 * 183 - (var3x % 3 <= 0 ? 0 : 10) - (var3x % 3 <= 1 ? 0 : 10),

                                                                    80 + 10 + (var3x - var3x % 3) / 3 * 210,

                                                                    183,

                                                                    220,

                                                                    var4

                                                            )

                                                    );

                                            var7x.onClick((var2, var3xx) -> this.field20843.playSong(null, var4));

                                        }

                                    }

                                }

                        );

                    }

            )

                    .start();

        }



        super.keyPressed(keyCode);

    }

}

