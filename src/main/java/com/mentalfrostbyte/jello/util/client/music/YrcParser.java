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
 *
 * <p>另外支持 QQ 音乐 QRC 逐词格式（{@link #parseQrc(String)}），
 * 用于网易云缺失 yrc 时从 QQ 音乐补充逐词歌词：
 * <pre>
 * [行start,行duration]词1(词start,词duration)词2(词start,词duration)...
 * </pre>
 * QRC 每行以整行的 {@code [start,duration]} 开头，随后每个词的时间以
 * {@code (start,duration)} 紧跟在词文本 <b>之后</b>（与 YRC 的前缀式相反）。
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

    // ==================== QQ 音乐 QRC 解析 ====================

    // QRC 逐词时间戳：词文本后跟 (start,duration)，例如 "晚(22380,260)"
    private static final Pattern QRC_WORD_PATTERN =
            Pattern.compile("(.*?)\\((\\d+),(\\d+)\\)");
    // QRC 行首整行时间戳：[start,duration]
    private static final Pattern QRC_LINE_HEADER_PATTERN =
            Pattern.compile("^\\[(\\d+),(\\d+)\\]");
    // QRC 元数据行/属性标签，例如 [ti:xxx] [ar:xxx] [offset:0]，需要跳过
    private static final Pattern QRC_META_PATTERN =
            Pattern.compile("^\\[[a-zA-Z]+:");

    /**
     * 解析 QQ 音乐 QRC 逐词歌词。
     *
     * <p>QRC 行格式：{@code [行start,行dur]词1(start1,dur1)词2(start2,dur2)...}
     * 词的时间戳以 {@code (start,duration)} 紧跟在词文本 <b>之后</b>。
     * 输入可以是已解密的纯 QRC 文本，或包裹在 {@code <Lyric_1 ... LyricContent="..."/>}
     * XML 里的内容——本方法会自动剥离 XML 外壳。
     *
     * @param qrcText 解密后的 QRC 文本（可含 XML 外壳）
     * @return 逐词行列表，按起始时间升序；解析失败返回空列表
     */
    public static List<YrcLine> parseQrc(String qrcText) {
        if (qrcText == null || qrcText.trim().isEmpty()) {
            return new ArrayList<>();
        }

        String body = extractQrcLyricContent(qrcText);
        List<YrcLine> lines = new ArrayList<>();
        String[] rawLines = body.split("\n");

        for (String rawLine : rawLines) {
            if (rawLine == null || rawLine.trim().isEmpty()) continue;

            String line = rawLine.trim();
            // 跳过 [ti:] [ar:] [offset:] 等元数据行
            if (QRC_META_PATTERN.matcher(line).find()) continue;

            // 去掉行首 [行start,行dur] 头（若存在）
            long headerStart = -1;
            Matcher header = QRC_LINE_HEADER_PATTERN.matcher(line);
            if (header.find()) {
                headerStart = Long.parseLong(header.group(1));
                line = line.substring(header.end());
            }

            List<YrcWord> words = new ArrayList<>();
            StringBuilder contentBuilder = new StringBuilder();
            long lineStart = -1;

            Matcher matcher = QRC_WORD_PATTERN.matcher(line);
            while (matcher.find()) {
                String word = matcher.group(1);
                long start = Long.parseLong(matcher.group(2));
                long duration = Long.parseLong(matcher.group(3));

                if (word == null || word.isEmpty()) continue;

                if (lineStart < 0) lineStart = start;
                words.add(new YrcWord(start, duration, word));
                contentBuilder.append(word);
            }

            if (!words.isEmpty()) {
                String content = contentBuilder.toString().trim();
                if (!content.isEmpty()) {
                    if (lineStart < 0) lineStart = headerStart >= 0 ? headerStart : 0;
                    lines.add(new YrcLine(lineStart, content, words));
                }
            }
        }

        Collections.sort(lines);
        return lines;
    }

    /**
     * 判断文本是否为 QRC 逐词格式。
     * QRC 特征：词文本后紧跟 {@code (数字,数字)} 时间戳。
     */
    public static boolean isQrc(String text) {
        if (text == null || text.isEmpty()) return false;
        return QRC_WORD_PATTERN.matcher(extractQrcLyricContent(text)).find();
    }

    /**
     * 若 QRC 文本被 XML 外壳包裹（QQ 音乐部分接口返回
     * {@code <QrcInfos><LyricInfo><Lyric_1 LyricContent="..."/></LyricInfo></QrcInfos>}），
     * 提取出 {@code LyricContent} 属性里的实际歌词，否则原样返回。
     */
    private static String extractQrcLyricContent(String text) {
        int idx = text.indexOf("LyricContent=");
        if (idx < 0) {
            return text;
        }
        int quoteStart = text.indexOf('"', idx);
        if (quoteStart < 0) {
            return text;
        }
        int quoteEnd = text.indexOf('"', quoteStart + 1);
        if (quoteEnd < 0) {
            return text;
        }
        return text.substring(quoteStart + 1, quoteEnd);
    }
}
