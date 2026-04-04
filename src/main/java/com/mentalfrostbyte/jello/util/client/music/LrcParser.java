package com.mentalfrostbyte.jello.util.client.music;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class LrcParser {
    public static class LyricLine implements Comparable<LyricLine> {
        public long timestamp;
        public String content;

        public LyricLine(long timestamp, String content) {
            this.timestamp = timestamp;
            this.content = content;
        }

        @Override
        public int compareTo(LyricLine o) {
            return Long.compare(this.timestamp, o.timestamp);
        }
    }

    public static List<LyricLine> parse(File file) {
        List<LyricLine> lyrics = new ArrayList<>();
        if (!file.exists())
            return lyrics;

        try (FileInputStream fis = new FileInputStream(file)) {
            return parse(fis);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return lyrics;
    }

    /**
     * 从 LRC 格式字符串解析歌词（适用于网易云 API 返回的歌词文本）。
     *
     * @param lrcText LRC 格式文本
     * @return 歌词行列表，按时间戳升序
     */
    public static List<LyricLine> parseString(String lrcText) {
        if (lrcText == null || lrcText.isEmpty()) {
            return new ArrayList<>();
        }
        return parse(new java.io.ByteArrayInputStream(lrcText.getBytes(StandardCharsets.UTF_8)));
    }

    public static List<LyricLine> parse(InputStream inputStream) {
        List<LyricLine> lyrics = new ArrayList<>();
        if (inputStream == null)
            return lyrics;

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            String line;
            Pattern pattern = Pattern.compile("\\[(\\d{2}):(\\d{2})\\.(\\d{2,3})\\](.*)");
            while ((line = reader.readLine()) != null) {
                Matcher matcher = pattern.matcher(line);
                if (matcher.find()) {
                    long minutes = Long.parseLong(matcher.group(1));
                    long seconds = Long.parseLong(matcher.group(2));
                    String millisStr = matcher.group(3);
                    long millis = Long.parseLong(millisStr);
                    if (millisStr.length() == 2)
                        millis *= 10;

                    long totalMillis = minutes * 60000 + seconds * 1000 + millis;
                    String content = matcher.group(4).trim();
                    if (!content.isEmpty()) {
                        lyrics.add(new LyricLine(totalMillis, content));
                    }
                }
            }
            Collections.sort(lyrics);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return lyrics;
    }
}
