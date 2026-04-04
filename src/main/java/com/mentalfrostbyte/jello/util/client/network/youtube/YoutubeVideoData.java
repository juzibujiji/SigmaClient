package com.mentalfrostbyte.jello.util.client.network.youtube;

public class YoutubeVideoData {
    public String videoId;
    public String title;
    public String fullUrl;
    /** Netease Cloud Music song ID (0 = not a Netease track) */
    public long neteaseSongId;
    /** Duration in milliseconds (from Netease API, 0 = unknown) */
    public long neteaseDurationMs;

    public YoutubeVideoData(String videoId, String title, String fullUrl) {
        this.videoId = videoId;
        this.title = title;
        this.fullUrl = fullUrl;
        this.neteaseSongId = 0;
        this.neteaseDurationMs = 0;
    }

    public YoutubeVideoData(String videoId, String title, String fullUrl, long neteaseSongId, long neteaseDurationMs) {
        this.videoId = videoId;
        this.title = title;
        this.fullUrl = fullUrl;
        this.neteaseSongId = neteaseSongId;
        this.neteaseDurationMs = neteaseDurationMs;
    }

    public boolean isNeteaseTrack() {
        return neteaseSongId > 0;
    }
}
