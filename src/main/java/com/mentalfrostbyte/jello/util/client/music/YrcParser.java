package com.mentalfrostbyte.jello.util.client.music;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 网易云 YRC 逐词歌词解析器。
 *
 * <p>网易云 yrc（{@code /weapi/song/lyric} 带 {@code yv} 返回）格式示例：
 * <pre>
 * [24080,5080](24080,280,0)半(24360,210,0)夜(24570,560,0)睡
 * </pre>
 * 每行以 {@code [行start,行dur]} 开头，随后每个词的时间戳 {@code (词start,词dur,标志)}
 * 在其文本<b>之前</b>，实现逐词高亮。
 *
 * <p>兼容两种逐词格式：
 * <ul>
 *   <li>网易云 yrc：{@code [行start,行dur](词start,词dur,标志)词文本} 时间戳前置</li>
 *   <li>词前缀格式：{@code [start,duration]word} 逐词标注（旧版 YRC）</li>
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

    // 网易云 yrc 的逐词时间戳：(词start,词dur,标志)，位于词文本之前
    private static final Pattern NETEASE_WORD_TS =
            Pattern.compile("\\((\\d+),(\\d+)(?:,\\d+)?\\)");

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

            // 网易云 yrc 的时间戳前置于词文本；若整行没有这类时间戳，回退到词前缀格式
            List<YrcWord> words = NETEASE_WORD_TS.matcher(rawLine).find()
                    ? parseTimestampFirstWords(rawLine)
                    : parseWordPrefixWords(rawLine);

            if (!words.isEmpty()) {
                // 首词若带前导空白（如 " 作曲 : ..."），去掉后词拼接才与 trim 后的整行一致，
                // 保证 {行内容}.equals(拼接结果) 的逐字高亮不发生整体偏移
                YrcWord first = words.get(0);
                String trimmedLeading = first.text.replaceFirst("^\\s+", "");
                if (!trimmedLeading.isEmpty()) {
                    first.text = trimmedLeading;
                }

                StringBuilder contentBuilder = new StringBuilder();
                for (YrcWord word : words) {
                    contentBuilder.append(word.text);
                }
                String content = contentBuilder.toString().trim();
                if (!content.isEmpty()) {
                    lines.add(new YrcLine(words.get(0).start, content, words));
                }
            }
        }

        Collections.sort(lines);
        return lines;
    }

    /**
     * 解析网易云 yrc 的逐词行：{@code [行start,行dur](词start,词dur,标志)词文本...}。
     * 时间戳在词文本之前，词文本延伸到下一个时间戳（或行尾）。
     */
    private static List<YrcWord> parseTimestampFirstWords(String rawLine) {
        // 去掉行首 [行start,行dur]
        String body = rawLine;
        Matcher header = QRC_LINE_HEADER_PATTERN.matcher(body);
        if (header.find()) {
            body = body.substring(header.end());
        }

        List<YrcWord> words = new ArrayList<>();
        Matcher matcher = NETEASE_WORD_TS.matcher(body);
        long pendingStart = -1L;
        long pendingDuration = 0L;
        int textStart = 0;

        while (matcher.find()) {
            if (pendingStart >= 0L) {
                String word = body.substring(textStart, matcher.start());
                if (!word.isEmpty()) {
                    words.add(new YrcWord(pendingStart, pendingDuration, word));
                }
            }
            pendingStart = Long.parseLong(matcher.group(1));
            pendingDuration = Long.parseLong(matcher.group(2));
            textStart = matcher.end();
        }

        if (pendingStart >= 0L) {
            String word = body.substring(textStart);
            if (!word.isEmpty()) {
                words.add(new YrcWord(pendingStart, pendingDuration, word));
            }
        }
        return words;
    }

    /** 解析词前缀格式的逐词行：{@code [start,duration]word}。 */
    private static List<YrcWord> parseWordPrefixWords(String rawLine) {
        List<YrcWord> words = new ArrayList<>();
        Matcher matcher = WORD_PATTERN.matcher(rawLine);
        while (matcher.find()) {
            long start = Long.parseLong(matcher.group(1));
            long duration = Long.parseLong(matcher.group(2));
            String word = matcher.group(3);

            if (word == null || word.isEmpty()) continue;

            words.add(new YrcWord(start, duration, word));
        }
        return words;
    }

    /**
     * 判断文本是否为 YRC 格式（而非普通 LRC）。
     * YRC 行包含 {@code [数字,数字]word} 模式，LRC 只有 {@code [mm:ss.ms]}。
     */
    public static boolean isYrc(String text) {
        if (text == null || text.isEmpty()) return false;
        // YRC 的特征：[数字,数字] 后面紧跟非空白字符，或网易云的 (词start,词dur,标志) 时间戳
        return WORD_PATTERN.matcher(text).find()
                || NETEASE_WORD_TS.matcher(text).find();
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
