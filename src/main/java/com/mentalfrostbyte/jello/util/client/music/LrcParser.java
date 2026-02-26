package com.mentalfrostbyte.jello.util.client.music;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
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

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {
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
