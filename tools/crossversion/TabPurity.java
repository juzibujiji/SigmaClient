package verify;

import com.google.gson.*;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/**
 * 量每个官方 tab 的「纯度」：这个 tab 里的 1.16.4 原版物品，有多大比例落在同一个 ItemGroup。
 *
 * <p>为什么要量：方案文档记着「只看 tab 众数准确率 68.45%」，于是 tab 一直被当成不可用的信号。
 * 但 68.45% 是<b>把 12 个 tab 一视同仁</b>算出来的平均数。1.21 的 {@code building} 栏确实
 * 横跨 1.16.4 的 BUILDING_BLOCKS 与 DECORATIONS，可 {@code food} 栏大概全是 FOOD ——
 * 平均数把这种差别抹平了。分 tab 量出来，纯的那几个就能放心直接用。
 *
 * <p>用法：{@code java verify.TabPurity <仓库根目录>}
 */
public class TabPurity {

    public static void main(String[] args) throws Exception {
        Path repo = Paths.get(args.length > 0 ? args[0] : ".");
        JsonObject blocksJson = JsonParser.parseString(Files.readString(
                repo.resolve("1.21.11/reports/blocks.json"), StandardCharsets.UTF_8)).getAsJsonObject();

        Map<String, List<String>> tabs = CreativeGroups.readTabs(repo);
        Map<String, String> vanilla = CreativeGroups.readVanillaGroups(repo);

        // MATERIALS 是 MISC 的别名，不归一化会把票劈开
        Map<String, String> norm = new LinkedHashMap<>();
        vanilla.forEach((k, v) -> norm.put(k, "MATERIALS".equals(v) ? "MISC" : v));

        System.out.printf("%-14s %6s %8s  %-18s %s%n", "tab", "原版数", "纯度", "众数", "分布");
        System.out.println("-".repeat(100));

        List<Map.Entry<String, Double>> ranked = new ArrayList<>();
        Map<String, String> mode = new LinkedHashMap<>();
        Map<String, String> spread = new LinkedHashMap<>();
        Map<String, Integer> sizes = new LinkedHashMap<>();

        for (Map.Entry<String, List<String>> tab : tabs.entrySet()) {
            Map<String, Integer> votes = new TreeMap<>();
            for (String id : tab.getValue()) {
                String g = norm.get(id);
                if (g != null) votes.merge(g, 1, Integer::sum);
            }
            int total = votes.values().stream().mapToInt(Integer::intValue).sum();
            if (total == 0) continue;

            Map.Entry<String, Integer> best = votes.entrySet().stream()
                    .max(Map.Entry.comparingByValue()).orElseThrow();
            double purity = (double) best.getValue() / total;

            ranked.add(new AbstractMap.SimpleEntry<>(tab.getKey(), purity));
            mode.put(tab.getKey(), best.getKey());
            sizes.put(tab.getKey(), total);
            spread.put(tab.getKey(), votes.entrySet().stream()
                    .sorted((a, b) -> b.getValue() - a.getValue())
                    .map(e -> e.getKey() + " " + e.getValue())
                    .reduce((a, b) -> a + " / " + b).orElse(""));
        }

        ranked.sort((a, b) -> Double.compare(b.getValue(), a.getValue()));
        for (Map.Entry<String, Double> e : ranked) {
            String t = e.getKey();
            System.out.printf("%-14s %6d %7.1f%%  %-18s %s%n",
                    t, sizes.get(t), e.getValue() * 100, mode.get(t), spread.get(t));
        }
    }
}
