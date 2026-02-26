package com.mentalfrostbyte.jello.managers.util;

import com.mentalfrostbyte.jello.util.client.network.youtube.YoutubeContentType;
import com.mentalfrostbyte.jello.util.client.network.youtube.YoutubeJPGThumbnail;
import com.mentalfrostbyte.jello.util.client.network.youtube.YoutubeVideoData;
import com.mentalfrostbyte.jello.util.client.network.youtube.ThumbnailUtil;

import com.mentalfrostbyte.Client;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class Thumbnails {
    public String name;
    public String videoId;
    public YoutubeContentType contentType;
    public List<YoutubeVideoData> videoList = new ArrayList<>();
    public boolean isUpdated = false;

    public Thumbnails(String name, String videoId, YoutubeContentType contentType) {
        this.name = name;
        this.videoId = videoId;
        this.contentType = contentType;
    }

    public void refreshVideoList() {
        this.videoList = new ArrayList<>();

        if (this.contentType == YoutubeContentType.LOCAL) {
            File musicDir = new File(Client.getInstance().file, "music");
            System.out.println("Scanning for local music in: " + musicDir.getAbsolutePath());
            if (!musicDir.exists()) {
                musicDir.mkdirs();
                System.out.println("Created music directory.");
            }

            if (musicDir.isDirectory()) {
                File[] files = musicDir.listFiles((dir, name) -> {
                    String lower = name.toLowerCase();
                    return lower.endsWith(".mp3") || lower.endsWith(".wav") || lower.endsWith(".ogg");
                });
                if (files != null) {
                    System.out.println("Found " + files.length + " music files.");
                    for (File file : files) {
                        String fileName = file.getName();
                        String baseName = fileName.substring(0, fileName.lastIndexOf('.'));
                        String audioUri = file.toURI().toString();
                        String imageUri = null;

                        File pngFile = new File(musicDir, baseName + ".png");
                        File jpgFile = new File(musicDir, baseName + ".jpg");

                        if (pngFile.exists()) {
                            imageUri = pngFile.toURI().toString();
                        } else if (jpgFile.exists()) {
                            imageUri = jpgFile.toURI().toString();
                        }

                        // Use audio URI as videoId, image URI as fullUrl (can be null)
                        System.out.println(
                                "Adding local track: " + fileName + ", Cover: " + (imageUri != null ? "Yes" : "No"));
                        this.videoList.add(new YoutubeVideoData(audioUri, baseName, imageUri));
                    }
                } else {
                    System.out.println("Music directory is empty or IO error.");
                }
            } else {
                System.out.println("Music path is not a directory!");
            }
            return;
        }

        YoutubeJPGThumbnail[] thumbnails = new YoutubeJPGThumbnail[0];
        if (this.contentType != YoutubeContentType.CHANNEL) {
            if (this.contentType == YoutubeContentType.PLAYLIST) {
                thumbnails = ThumbnailUtil.getFromPlaylist(this.videoId);
            }
        } else {
            thumbnails = ThumbnailUtil.getFromChannel(this.videoId);
        }

        for (YoutubeJPGThumbnail thumbnail : thumbnails) {
            this.videoList.add(new YoutubeVideoData(thumbnail.videoID, thumbnail.title, thumbnail.fullUrl));
        }
    }

    @Override
    public boolean equals(Object thumbnail) {
        if (thumbnail != this) {
            if (thumbnail instanceof Thumbnails thumbnails) {
                return thumbnails.videoId.equals(this.videoId);
            } else {
                return false;
            }
        } else {
            return true;
        }
    }
}