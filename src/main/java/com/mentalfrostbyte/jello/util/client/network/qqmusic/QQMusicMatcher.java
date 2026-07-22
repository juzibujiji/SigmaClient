package com.mentalfrostbyte.jello.util.client.network.qqmusic;

import java.util.List;
import java.util.Locale;

/**
 * QQ 音乐搜索结果综合打分匹配器。
 *
 * <p>网易云在播歌曲与 QQ 音乐搜索候选往往不是逐字相同（翻唱、版本、featuring、
 * 括号备注等），直接取第一条容易匹配错。此匹配器按 <b>歌名 + 歌手 + 时长</b>
 * 三个维度综合打分，选出最可信的候选；低于阈值则判定"没匹配上"，让调用方
 * 回退到网易云自身的 lrc，避免贴错逐词歌词。
 */
public class QQMusicMatcher {

    // 各维度权重
    private static final double NAME_WEIGHT = 0.5;
    private static final double ARTIST_WEIGHT = 0.25;
    private static final double DURATION_WEIGHT = 0.25;

    // 综合得分低于此阈值视为未匹配
    private static final double MIN_ACCEPT_SCORE = 0.55;

    /** 匹配结果：命中的候选 + 其综合得分。 */
    public static class MatchResult {
        public final QQMusicApi.QQTrack track;
        public final double score;

        public MatchResult(QQMusicApi.QQTrack track, double score) {
            this.track = track;
            this.score = score;
        }
    }

    /**
     * 从候选列表中选出与目标歌曲最匹配的一条。
     *
     * @param candidates    QQ 音乐搜索候选
     * @param targetName    目标歌名（网易云在播歌名）
     * @param targetArtist  目标歌手（可为空）
     * @param targetDurMs   目标时长（毫秒，≤0 表示未知，不参与时长打分）
     * @return 最佳匹配（得分达阈值），无合格候选返回 null
     */
    public static MatchResult match(List<QQMusicApi.QQTrack> candidates,
                                    String targetName, String targetArtist, long targetDurMs) {
        if (candidates == null || candidates.isEmpty()) {
            return null;
        }

        QQMusicApi.QQTrack best = null;
        double bestScore = -1.0;

        for (QQMusicApi.QQTrack c : candidates) {
            double score = score(c, targetName, targetArtist, targetDurMs);
            if (score > bestScore) {
                bestScore = score;
                best = c;
            }
        }

        if (best == null || bestScore < MIN_ACCEPT_SCORE) {
            return null;
        }
        return new MatchResult(best, bestScore);
    }

    private static double score(QQMusicApi.QQTrack c,
                                String targetName, String targetArtist, long targetDurMs) {
        double nameScore = similarity(normalize(targetName), normalize(c.name));

        double artistScore;
        String ta = normalize(targetArtist);
        String ca = normalize(c.artist);
        if (ta.isEmpty() || ca.isEmpty()) {
            artistScore = 0.5; // 缺歌手信息时给中性分，不偏袒也不惩罚
        } else if (ca.contains(ta) || ta.contains(ca)) {
            artistScore = 1.0; // 一方包含另一方（"周杰伦" vs "周杰伦/费玉清"）
        } else {
            artistScore = similarity(ta, ca);
        }

        double durationScore;
        if (targetDurMs <= 0 || c.durationMs <= 0) {
            durationScore = 0.5; // 时长未知时中性
        } else {
            long diff = Math.abs(targetDurMs - c.durationMs);
            // ±3s 内几乎满分，之后线性衰减，>15s 视为不同版本给 0
            if (diff <= 3000L) {
                durationScore = 1.0;
            } else if (diff >= 15000L) {
                durationScore = 0.0;
            } else {
                durationScore = 1.0 - (double) (diff - 3000L) / 12000.0;
            }
        }

        return NAME_WEIGHT * nameScore
                + ARTIST_WEIGHT * artistScore
                + DURATION_WEIGHT * durationScore;
    }

    /**
     * 归一化：转小写、去除括号备注（(Live)/（伴奏）等）、去除空白与常见标点，
     * 以便"歌名 (Live版)" 与 "歌名" 能高相似度匹配。
     */
    private static String normalize(String s) {
        if (s == null) return "";
        String t = s.toLowerCase(Locale.ROOT);
        // 去掉圆/方括号及其内容（中英文括号）
        t = t.replaceAll("[\\(（\\[【].*?[\\)）\\]】]", "");
        // 去掉空白与常见分隔标点
        t = t.replaceAll("[\\s\\-_·,，.。!！?？'\"]", "");
        return t.trim();
    }

    /**
     * 基于 Levenshtein 编辑距离的相似度，归一化到 [0,1]。
     */
    private static double similarity(String a, String b) {
        if (a.isEmpty() && b.isEmpty()) return 1.0;
        if (a.isEmpty() || b.isEmpty()) return 0.0;
        if (a.equals(b)) return 1.0;

        int dist = levenshtein(a, b);
        int maxLen = Math.max(a.length(), b.length());
        return 1.0 - (double) dist / (double) maxLen;
    }

    private static int levenshtein(String a, String b) {
        int n = a.length();
        int m = b.length();
        int[] prev = new int[m + 1];
        int[] curr = new int[m + 1];

        for (int j = 0; j <= m; j++) {
            prev[j] = j;
        }
        for (int i = 1; i <= n; i++) {
            curr[0] = i;
            for (int j = 1; j <= m; j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                curr[j] = Math.min(Math.min(prev[j] + 1, curr[j - 1] + 1), prev[j - 1] + cost);
            }
            int[] tmp = prev;
            prev = curr;
            curr = tmp;
        }
        return prev[m];
    }
}
