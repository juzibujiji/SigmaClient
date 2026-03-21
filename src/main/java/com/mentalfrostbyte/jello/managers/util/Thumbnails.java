package com.mentalfrostbyte.jello.managers.util;

import com.mentalfrostbyte.jello.util.client.network.youtube.YoutubeContentType;
import com.mentalfrostbyte.jello.util.client.network.youtube.YoutubeJPGThumbnail;
import com.mentalfrostbyte.jello.util.client.network.youtube.YoutubeVideoData;
import com.mentalfrostbyte.jello.util.client.network.youtube.ThumbnailUtil;

import com.mentalfrostbyte.Client;
import com.mentalfrostbyte.jello.util.client.render.Resources;

import java.io.File;
import java.io.InputStream;
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
                        String imageUri = Resources.artworkPNG.toString();

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

        if (this.contentType == YoutubeContentType.BUNDLED) {
            System.out.println("Scanning for bundled music in classpath resources...");
            try {
                // Read manifest.txt from classpath
                InputStream manifestStream = Thumbnails.class.getClassLoader()
                        .getResourceAsStream("bundled_music/manifest.txt");
                if (manifestStream == null) {
                    System.out.println("No bundled_music/manifest.txt found in classpath.");
                    return;
                }

                // Parse manifest: lines like "xilian.mp3=昔涟-张韶涵_HOYO-MiX[iq].mp3"
                java.util.Properties manifest = new java.util.Properties();
                java.io.BufferedReader reader = new java.io.BufferedReader(
                        new java.io.InputStreamReader(manifestStream, java.nio.charset.StandardCharsets.UTF_8));
                String line;
                while ((line = reader.readLine()) != null) {
                    line = line.trim();
                    if (line.isEmpty() || line.startsWith("#"))
                        continue;
                    int eq = line.indexOf('=');
                    if (eq > 0) {
                        manifest.setProperty(line.substring(0, eq).trim(), line.substring(eq + 1).trim());
                    }
                }
                reader.close();
                manifestStream.close();

                // Collect unique base names from MP3 entries
                java.util.Set<String> baseNames = new java.util.LinkedHashSet<>();
                for (String key : manifest.stringPropertyNames()) {
                    if (key.endsWith(".mp3")) {
                        baseNames.add(key.substring(0, key.lastIndexOf('.')));
                    }
                }

                // Create temp directory for extracted bundled music
                File tempDir = new File(System.getProperty("java.io.tmpdir"), "sigma_bundled_music");
                if (!tempDir.exists()) {
                    tempDir.mkdirs();
                }

                for (String baseName : baseNames) {
                    String mp3Key = baseName + ".mp3";
                    String displayName = manifest.getProperty(mp3Key, mp3Key);
                    // Remove extension from display name for title
                    String title = displayName;
                    if (title.lastIndexOf('.') > 0) {
                        title = title.substring(0, title.lastIndexOf('.'));
                    }

                    // Extract MP3 to temp file
                    File tempMp3 = new File(tempDir, baseName + ".mp3");
                    if (!tempMp3.exists()) {
                        InputStream mp3Stream = Thumbnails.class.getClassLoader()
                                .getResourceAsStream("bundled_music/" + mp3Key);
                        if (mp3Stream == null) {
                            System.out.println("Bundled MP3 not found: " + mp3Key);
                            continue;
                        }
                        java.nio.file.Files.copy(mp3Stream, tempMp3.toPath(),
                                java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                        mp3Stream.close();
                    }

                    String audioUri = tempMp3.toURI().toString();

                    // Extract cover image (PNG)
                    String imageUri = Resources.artworkPNG.toString();
                    String pngKey = baseName + ".png";
                    InputStream pngStream = Thumbnails.class.getClassLoader()
                            .getResourceAsStream("bundled_music/" + pngKey);
                    if (pngStream != null) {
                        File tempPng = new File(tempDir, pngKey);
                        if (!tempPng.exists()) {
                            java.nio.file.Files.copy(pngStream, tempPng.toPath(),
                                    java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                        }
                        imageUri = tempPng.toURI().toString();
                        pngStream.close();
                    }

                    // Extract LRC lyrics
                    String lrcKey = baseName + ".lrc";
                    InputStream lrcStream = Thumbnails.class.getClassLoader()
                            .getResourceAsStream("bundled_music/" + lrcKey);
                    if (lrcStream != null) {
                        File tempLrc = new File(tempDir, lrcKey);
                        if (!tempLrc.exists()) {
                            java.nio.file.Files.copy(lrcStream, tempLrc.toPath(),
                                    java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                        }
                        lrcStream.close();
                    }

                    System.out.println("Adding bundled track: " + title + ", Cover: " + imageUri);
                    this.videoList.add(new YoutubeVideoData(audioUri, title, imageUri));
                }
            } catch (Exception e) {
                System.out.println("Error loading bundled music: " + e.getMessage());
                e.printStackTrace();
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
