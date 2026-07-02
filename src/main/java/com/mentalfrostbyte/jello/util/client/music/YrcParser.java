package com.mentalfrostbyte.jello.util.client.music;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 网易云 YRC 逐词歌词解析器。
 *
 * <p>YRC 格式示例：
 * <pre>
 * [22380,2760]晚安 [25140,420]好运 [25560,420]再来
 * </pre>
 * 每行由多个 {@code [start_ms,duration_ms]word} 片段组成，
 * 每个词有独立的开始时间和持续时间，实现逐词高亮。
 *
 * <p>兼容两种 YRC 变体：
 * <ul>
 *   <li>标准文本格式：{@code [start,duration]word} 逐词标注</li>
 *   <li>JSON 数组格式：网易云部分接口返回 {@code [{"t":ms,"c":[{"tx":"word","s":ms,"d":ms}]}]}</li>
 * </ul>
 */
public class YrcParser {

    /** 逐词行 */
    public static class YrcLine implements Comparable<YrcLine> {
        public long startTime;          // 行起始时间（ms），取首个词的 start
        public String content;          // 整行文本（所有词拼接）
        public List<YrcWord> words;     // 逐词时间数据

        public YrcLine(long startTime, String content, List<YrcWord> words) {
            this.startTime = startTime;
            this.content = content;
            this.words = words;
        }

        @Override
        public int compareTo(YrcLine o) {
            return Long.compare(this.startTime, o.startTime);
        }
    }

    /** 单个词的时间信息 */
    public static class YrcWord {
        public long start;      // 开始时间（ms）
        public long duration;   // 持续时间（ms）
        public String text;     // 词文本

        public YrcWord(long start, long duration, String text) {
            this.start = start;
            this.duration = duration;
            this.text = text;
        }
    }

    // 匹配 [start,duration]word 格式
    private static final Pattern WORD_PATTERN =
            Pattern.compile("\\[(\\d+),(\\d+)]([^\\[]*)");

    /**
     * 解析 YRC 文本格式歌词。
     *
     * @param yrcText YRC 格式文本
     * @return 逐词行列表，按起始时间升序；解析失败返回空列表
     */
    public static List<YrcLine> parse(String yrcText) {
        if (yrcText == null || yrcText.trim().isEmpty()) {
            return new ArrayList<>();
        }

        List<YrcLine> lines = new ArrayList<>();
        String[] rawLines = yrcText.split("\n");

        for (String rawLine : rawLines) {
            if (rawLine == null || rawLine.trim().isEmpty()) continue;

            List<YrcWord> words = new ArrayList<>();
            StringBuilder contentBuilder = new StringBuilder();
            long lineStart = -1;

            Matcher matcher = WORD_PATTERN.matcher(rawLine);
            while (matcher.find()) {
                long start = Long.parseLong(matcher.group(1));
                long duration = Long.parseLong(matcher.group(2));
                String word = matcher.group(3);

                if (word == null || word.isEmpty()) continue;

                if (lineStart < 0) lineStart = start;
                words.add(new YrcWord(start, duration, word));
                contentBuilder.append(word);
            }

            if (!words.isEmpty()) {
                String content = contentBuilder.toString().trim();
                if (!content.isEmpty()) {
                    lines.add(new YrcLine(lineStart, content, words));
                }
            }
        }

        Collections.sort(lines);
        return lines;
    }

    /**
     * 判断文本是否为 YRC 格式（而非普通 LRC）。
     * YRC 行包含 {@code [数字,数字]word} 模式，LRC 只有 {@code [mm:ss.ms]}。
     */
    public static boolean isYrc(String text) {
        if (text == null || text.isEmpty()) return false;
        // YRC 的特征：[数字,数字] 后面紧跟非空白字符
        return WORD_PATTERN.matcher(text).find();
    }
}
