package verify;

import com.google.gson.*;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/**
 * 留一验证：用 1.16.4 原版 959 个已知分组的物品当标准答案，量出各种分类算法的准确率。
 *
 * <p>为什么要做这一步：扩展物品的「正确分类」没有标准答案，只能靠人判断，而人判断
 * 一次错一次（方案文档 §9.20 记了两轮误伤）。但原版物品的分组是<b>确定的</b> ——
 * 把某个原版物品当成未知，只用<b>其他</b>原版物品作依据去预测它，预测对不对可以直接比。
 * 算法在原版上准确率高，用到扩展物品上才有底气。
 *
 * <p>注意留一时必须把物品自己排除干净：既不能当自己的邻居，也不能参与自己那一类的类型投票。
 *
 * <p>用法：{@code java verify.EvalGroups <仓库根目录>}
 */
public class EvalGroups {

    public static void main(String[] args) throws Exception {
        Path repo = Paths.get(args.length > 0 ? args[0] : ".");
        JsonObject blocksJson = JsonParser.parseString(Files.readString(
                repo.resolve("1.21.11/reports/blocks.json"), StandardCharsets.UTF_8)).getAsJsonObject();

        Map<String, List<String>> tabs = CreativeGroups.readTabs(repo);
        Map<String, String> truth = CreativeGroups.readVanillaGroups(repo);
        Map<String, List<String>> families = CreativeGroups.readFamilies(repo);

        // MATERIALS 在 1.16.4 就是 MISC 的别名（ItemGroup.MATERIALS = MISC），归一化后再比
        Map<String, String> norm = new LinkedHashMap<>();
        truth.forEach((k, v) -> norm.put(k, "MATERIALS".equals(v) ? "MISC" : v));

        // 只评在官方 tab 里出现过的原版物品 —— 不在任何 tab 里的预测不了，也不该算进分母
        List<String> subjects = new ArrayList<>();
        for (String id : norm.keySet()) {
            if (tabs.values().stream().anyMatch(l -> l.contains(id))) subjects.add(id);
        }
        System.out.printf("原版有分组且在官方 tab 里的物品：%d 个（可评测集）%n%n", subjects.size());

        System.out.printf("%-46s %8s %8s%n", "算法", "准确率", "错误数");
        System.out.println("-".repeat(66));

        for (int w : new int[]{1, 2, 3, 5, 8, 12}) {
            eval("邻居窗口 W=" + w + "（1/名次 加权）", subjects, norm, tabs, blocksJson, families,
                    (id, ctx) -> ctx.neighbour(id, w, false, false));
        }
        for (int w : new int[]{3, 5, 8}) {
            eval("邻居窗口 W=" + w + " + tab 纯度加权", subjects, norm, tabs, blocksJson, families,
                    (id, ctx) -> ctx.neighbour(id, w, true, false));
        }
        for (int w : new int[]{3, 5, 8}) {
            eval("邻居窗口 W=" + w + " 均匀权重", subjects, norm, tabs, blocksJson, families,
                    (id, ctx) -> ctx.neighbour(id, w, false, true));
        }
        eval("仅 tab 众数（不看邻居）", subjects, norm, tabs, blocksJson, families,
                (id, ctx) -> ctx.tabMode(id));
        for (int w : new int[]{3, 5, 8}) {
            for (double exp : new double[]{0.5, 1.0, 2.0}) {
                eval(String.format(Locale.ROOT, "邻居窗口 W=%d，按距离 1/d^%.1f", w, exp),
                        subjects, norm, tabs, blocksJson, families,
                        (id, ctx) -> ctx.neighbour(id, w, false, false, exp));
            }
        }
        for (int w : new int[]{3, 5, 8}) {
            eval("类型投票优先，回退 邻居窗口 W=" + w, subjects, norm, tabs, blocksJson, families,
                    (id, ctx) -> {
                        String t = ctx.typeVote(id);
                        return t != null ? t : ctx.neighbour(id, w, false, false);
                    });
        }
        for (int w : new int[]{3, 5, 8}) {
            eval("类型投票优先，回退 邻居W=" + w + "+纯度", subjects, norm, tabs, blocksJson, families,
                    (id, ctx) -> {
                        String t = ctx.typeVote(id);
                        return t != null ? t : ctx.neighbour(id, w, true, false);
                    });
        }
        for (int w : new int[]{3, 5, 8, 12}) {
            for (double exp : new double[]{0.5, 1.0, 2.0}) {
                eval(String.format(Locale.ROOT, "类型投票优先，回退 邻居W=%d 距离 1/d^%.1f", w, exp),
                        subjects, norm, tabs, blocksJson, families,
                        (id, ctx) -> {
                            String t = ctx.typeVote(id);
                            return t != null ? t : ctx.neighbour(id, w, false, false, exp);
                        });
            }
        }
        // 泛型 block 类型要不要参与投票？排除它，新木种的 _planks 会被上一个木种的
        // _button / _pressure_plate（1.16.4 属 REDSTONE）带跑；不排除，所有素方块
        // 一律 BUILDING_BLOCKS。用数据定，别猜。
        for (int w : new int[]{5, 8}) {
            eval("类型投票（含泛型 block）优先，回退 邻居W=" + w + " 1/d",
                    subjects, norm, tabs, blocksJson, families,
                    (id, ctx) -> {
                        String t = ctx.typeVote(id, true);
                        return t != null ? t : ctx.neighbour(id, w, false, false, 1.0);
                    });
        }
        // 邻居得分很低说明四周全是新内容（sculk 前后连着萤石蛙灯和整套幽匿方块），
        // 这时候局部信号不可靠，退回「该 tab 里原版成员的分组众数」是不是更好？
        for (double t : new double[]{0.5, 1.0, 1.5, 2.0, 3.0}) {
            eval(String.format(Locale.ROOT, "类型投票 -> 邻居W=8 1/d（总分<%.1f 退 tab 众数）", t),
                    subjects, norm, tabs, blocksJson, families,
                    (id, ctx) -> {
                        String ty = ctx.typeVote(id, true);
                        if (ty != null) return ty;
                        return ctx.neighbourOrTabMode(id, 8, 1.0, t);
                    });
        }

        // 逐类错误剖析，用最优配置
        System.out.println("\n=== 最优配置的错误明细（类型投票优先 + 邻居窗口 W=8 + 权重 1/距离）===");
        Ctx ctx = new Ctx(tabs, norm, blocksJson, families);
        Map<String, Integer> confusion = new TreeMap<>();
        List<String> wrong = new ArrayList<>();
        for (String id : subjects) {
            ctx.hold(id);
            String t = ctx.typeVote(id);
            String got = t != null ? t : ctx.neighbour(id, 8, false, false, 1.0);
            if (!Objects.equals(got, norm.get(id))) {
                confusion.merge(norm.get(id) + " -> " + got, 1, Integer::sum);
                wrong.add(String.format("  %-34s 真值 %-16s 预测 %-16s %s",
                        id, norm.get(id), got, t != null ? "(类型投票)" : "(邻居)"));
            }
        }
        System.out.println("混淆分布（真值 -> 预测）：");
        confusion.entrySet().stream().sorted((a, b) -> b.getValue() - a.getValue())
                .forEach(e -> System.out.printf("  %-40s %d%n", e.getKey(), e.getValue()));
        System.out.println("\n明细：");
        wrong.forEach(System.out::println);
    }

    interface Algo {
        String apply(String id, Ctx ctx);
    }

    private static void eval(String name, List<String> subjects, Map<String, String> truth,
                             Map<String, List<String>> tabs, JsonObject blocksJson,
                             Map<String, List<String>> families, Algo algo) {
        Ctx ctx = new Ctx(tabs, truth, blocksJson, families);
        int ok = 0, miss = 0;
        for (String id : subjects) {
            ctx.hold(id);
            String got = algo.apply(id, ctx);
            if (Objects.equals(got, truth.get(id))) ok++;
            else miss++;
        }
        System.out.printf("%-46s %7.2f%% %8d%n", name, 100.0 * ok / subjects.size(), miss);
    }

    // ------------------------------------------------------------------

    /** 一次留一预测的上下文：{@link #hold} 指定当前被隐藏的物品。 */
    static final class Ctx {
        private final Map<String, List<String>> tabs;
        private final Map<String, String> truth;
        private final JsonObject blocksJson;
        private final Map<String, List<String>> families;
        /** tab -> (1.16.4 分组 -> 该 tab 内原版成员数)，用于纯度加权。 */
        private final Map<String, Map<String, Integer>> tabDist = new HashMap<>();
        private String held;

        Ctx(Map<String, List<String>> tabs, Map<String, String> truth,
            JsonObject blocksJson, Map<String, List<String>> families) {
            this.tabs = tabs;
            this.truth = truth;
            this.blocksJson = blocksJson;
            this.families = families;
            tabs.forEach((tab, list) -> {
                Map<String, Integer> d = new TreeMap<>();
                list.forEach(id -> {
                    String g = truth.get(id);
                    if (g != null) d.merge(g, 1, Integer::sum);
                });
                tabDist.put(tab, d);
            });
        }

        void hold(String id) {
            this.held = id;
        }

        /** 被隐藏的物品，以及与它同族的物品，都不能当依据。 */
        private boolean visible(String id) {
            if (id.equals(held)) return false;
            List<String> kin = families.get(held);
            return kin == null || !kin.contains(id);
        }

        String typeVote(String id) {
            return typeVote(id, false);
        }

        String typeVote(String id, boolean includeGeneric) {
            String type = typeOf(id);
            if (type == null) return null;
            if (!includeGeneric && "block".equals(type)) return null;
            Map<String, Integer> votes = new TreeMap<>();
            for (Map.Entry<String, String> e : truth.entrySet()) {
                if (!visible(e.getKey())) continue;
                if (type.equals(typeOf(e.getKey()))) votes.merge(e.getValue(), 1, Integer::sum);
            }
            return votes.entrySet().stream().max(Map.Entry.comparingByValue()).map(Map.Entry::getKey).orElse(null);
        }

        String tabMode(String id) {
            Map<String, Double> score = new TreeMap<>();
            for (Map.Entry<String, List<String>> e : tabs.entrySet()) {
                if (!e.getValue().contains(id)) continue;
                tabDist.get(e.getKey()).forEach((g, n) -> score.merge(g, (double) n, Double::sum));
            }
            return top(score);
        }

        /** 邻居总分低于 {@code minScore} 时改用 tab 众数：局部全是新内容时局部信号不可信。 */
        String neighbourOrTabMode(String id, int window, double exp, double minScore) {
            Map<String, Double> score = neighbourScore(id, window, exp);
            double total = score.values().stream().mapToDouble(Double::doubleValue).sum();
            if (total < minScore) {
                String byTab = tabMode(id);
                if (byTab != null) return byTab;
            }
            return top(score);
        }

        String neighbour(String id, int window, boolean purityWeighted, boolean uniform) {
            return neighbour(id, window, purityWeighted, uniform, 0.0);
        }

        /**
         * 邻居窗口投票。
         *
         * @param window       每个 tab 的每个方向最多取几个「已知分组」的物品
         * @param distanceExp  按<b>实际距离</b>衰减的指数：0 表示只按名次（1/名次），
         *                     1 表示 1/距离，2 表示 1/距离²。
         *                     只按名次会出问题：{@code copper_bulb} 前面连着一串新方块，
         *                     11 格外的 {@code smooth_quartz_slab} 也算「第 1 个原版邻居」拿满权重，
         *                     把紧挨着的 {@code redstone_lamp} 压下去。
         */
        String neighbour(String id, int window, boolean purityWeighted, boolean uniform, double distanceExp) {
            return top(neighbourScore(id, window, purityWeighted, uniform, distanceExp));
        }

        Map<String, Double> neighbourScore(String id, int window, double distanceExp) {
            return neighbourScore(id, window, false, false, distanceExp);
        }

        Map<String, Double> neighbourScore(String id, int window, boolean purityWeighted,
                                           boolean uniform, double distanceExp) {
            Map<String, Double> score = new TreeMap<>();
            for (Map.Entry<String, List<String>> e : tabs.entrySet()) {
                List<String> list = e.getValue();
                int at = list.indexOf(id);
                if (at < 0) continue;
                double tabWeight = purityWeighted ? purity(e.getKey()) : 1.0;

                for (int dir : new int[]{-1, 1}) {
                    int rank = 0;
                    for (int j = at + dir; j >= 0 && j < list.size() && rank < window; j += dir) {
                        if (!visible(list.get(j))) continue;
                        String g = truth.get(list.get(j));
                        if (g == null) continue;
                        rank++;
                        double w;
                        if (uniform) {
                            w = 1.0;
                        } else if (distanceExp > 0) {
                            w = 1.0 / Math.pow(Math.abs(j - at), distanceExp);
                        } else {
                            w = 1.0 / rank;
                        }
                        score.merge(g, tabWeight * w, Double::sum);
                    }
                }
            }
            return score;
        }

        /** 该 tab 内原版成员集中在单一 1.16.4 分组的程度。 */
        private double purity(String tab) {
            Map<String, Integer> d = tabDist.get(tab);
            int total = d.values().stream().mapToInt(Integer::intValue).sum();
            int max = d.values().stream().mapToInt(Integer::intValue).max().orElse(0);
            return total == 0 ? 0 : (double) max / total;
        }

        private String typeOf(String id) {
            JsonElement e = blocksJson.get("minecraft:" + id);
            if (e == null) return null;
            return e.getAsJsonObject().getAsJsonObject("definition").get("type")
                    .getAsString().replace("minecraft:", "");
        }

        private static String top(Map<String, Double> score) {
            return score.entrySet().stream()
                    .max(Comparator.<Map.Entry<String, Double>>comparingDouble(Map.Entry::getValue)
                            .thenComparing(e -> e.getKey(), Comparator.reverseOrder()))
                    .map(Map.Entry::getKey).orElse(null);
        }
    }
}
