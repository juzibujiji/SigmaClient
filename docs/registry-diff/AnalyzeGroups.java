package extract;

import com.google.gson.*;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.regex.*;

/**
 * 一次性分析：为「创造栏分类改用官方数据」这一步提供决策依据。
 *
 * <p>打印两件事：
 * <ol>
 *   <li>各方块类型在原版 1.16.4 的 ItemGroup 投票分布 —— 用来判断哪些类型的
 *       投票是可信的（比如 door 全票 REDSTONE），哪些其实是噪声
 *       （比如泛型 block 只是「多数原版方块是建筑方块」）</li>
 *   <li>邻居法在官方 tab 顺序里给出的结果，逐项列出距离与依据物品</li>
 * </ol>
 */
public class AnalyzeGroups {

    public static void main(String[] args) throws Exception {
        Path repo = Paths.get(args.length > 0 ? args[0] : ".");
        Path diff = repo.resolve("docs/registry-diff");

        Map<String, String> blockIds = vanillaBlockIds(repo);
        Map<String, String> vanillaGroup = vanillaItemGroups(repo, blockIds);
        JsonObject blocksJson = JsonParser.parseString(Files.readString(
                repo.resolve("1.21.11/reports/blocks.json"), StandardCharsets.UTF_8)).getAsJsonObject();

        // --- 1) 类型投票分布 ---
        Map<String, Map<String, Integer>> votes = new TreeMap<>();
        for (Map.Entry<String, String> e : vanillaGroup.entrySet()) {
            String type = typeOf(blocksJson, e.getKey());
            if (type == null) continue;
            votes.computeIfAbsent(type, k -> new TreeMap<>()).merge(e.getValue(), 1, Integer::sum);
        }

        System.out.println("=== 方块类型 -> 原版 ItemGroup 投票分布（按纯度升序，只列有分歧或票数少的）===");
        votes.entrySet().stream()
                .map(e -> new Object[]{e.getKey(), e.getValue(), purity(e.getValue())})
                .sorted(Comparator.comparingDouble(a -> (double) a[2]))
                .forEach(a -> {
                    @SuppressWarnings("unchecked")
                    Map<String, Integer> v = (Map<String, Integer>) a[1];
                    int total = v.values().stream().mapToInt(Integer::intValue).sum();
                    if ((double) a[2] < 1.0 || total < 3) {
                        System.out.printf("  纯度 %.2f  票数 %2d  %-28s %s%n", (double) a[2], total, a[0], v);
                    }
                });
        long pure = votes.values().stream().filter(v -> purity(v) == 1.0).count();
        System.out.printf("共 %d 种类型，其中 %d 种全票一致%n%n", votes.size(), pure);

        // --- 2) 邻居法 ---
        Map<String, List<String>> tabs = readTabs(diff);
        Set<String> extended = new LinkedHashSet<>(Files.readAllLines(
                diff.resolve("added-items-1.16.4-to-1.21.11.txt")));

        System.out.println("=== 邻居法结果（扩展物品 -> 分类，含依据）===");
        Map<String, Integer> groupCount = new TreeMap<>();
        List<String> noNeighbour = new ArrayList<>();
        for (String id : extended) {
            Neighbour n = neighbour(tabs, vanillaGroup, id);
            if (n == null) {
                noNeighbour.add(id);
                continue;
            }
            groupCount.merge(n.group, 1, Integer::sum);
            System.out.printf("  %-42s %-16s  (%s #%d，距离 %d 的 %s)%n",
                    id, n.group, n.tab, n.index, n.distance, n.via);
        }
        System.out.println("\n分类分布：" + groupCount);
        System.out.println("找不到邻居的（" + noNeighbour.size() + " 个）：" + noNeighbour);
    }

    private static double purity(Map<String, Integer> v) {
        int total = v.values().stream().mapToInt(Integer::intValue).sum();
        int max = v.values().stream().mapToInt(Integer::intValue).max().orElse(0);
        return total == 0 ? 0 : (double) max / total;
    }

    private static String typeOf(JsonObject blocksJson, String id) {
        JsonElement e = blocksJson.get("minecraft:" + id);
        if (e == null) return null;
        return e.getAsJsonObject().getAsJsonObject("definition").get("type")
                .getAsString().replace("minecraft:", "");
    }

    // ------------------------------------------------------------------

    static final class Neighbour {
        String group, tab, via;
        int index, distance;
    }

    /**
     * 在官方 tab 顺序里向前后找最近的、1.16.4 已存在的物品，取它的 ItemGroup。
     * 一个物品可能出现在多个 tab，取距离最小的那个；同距离时前后都算一票投票决定。
     */
    static Neighbour neighbour(Map<String, List<String>> tabs, Map<String, String> vanillaGroup, String id) {
        Neighbour best = null;
        for (Map.Entry<String, List<String>> e : tabs.entrySet()) {
            List<String> list = e.getValue();
            int at = list.indexOf(id);
            if (at < 0) continue;
            for (int d = 1; d < list.size(); d++) {
                String pick = null;
                String group = null;
                for (int dir : new int[]{-1, 1}) {
                    int j = at + dir * d;
                    if (j < 0 || j >= list.size()) continue;
                    String cand = list.get(j);
                    String g = vanillaGroup.get(cand);
                    if (g != null) {
                        pick = cand;
                        group = g;
                        break;
                    }
                }
                if (group == null) continue;
                if (best == null || d < best.distance) {
                    best = new Neighbour();
                    best.group = group;
                    best.tab = e.getKey();
                    best.index = at + 1;
                    best.distance = d;
                    best.via = pick;
                }
                break;
            }
        }
        return best;
    }

    static Map<String, List<String>> readTabs(Path diff) throws Exception {
        Map<String, List<String>> tabs = new LinkedHashMap<>();
        for (String line : Files.readAllLines(diff.resolve("official-creative-tabs-1.21.11.csv"))) {
            String[] p = line.trim().split(",");
            if (p.length != 3) continue;
            tabs.computeIfAbsent(p[0], k -> new ArrayList<>()).add(p[2]);
        }
        return tabs;
    }

    /** 本项目 1.16.4 的 {@code Blocks.FIELD -> 标识符}。全部是 {@code register("id", ...)}。 */
    static Map<String, String> vanillaBlockIds(Path repo) throws Exception {
        Map<String, String> out = new HashMap<>();
        Matcher m = Pattern.compile("public static final Block ([A-Z0-9_]+) = register\\(\"([a-z0-9_]+)\"")
                .matcher(Files.readString(repo.resolve("src/main/java/net/minecraft/block/Blocks.java"),
                        StandardCharsets.UTF_8));
        while (m.find()) out.put(m.group(1), m.group(2));
        return out;
    }

    /**
     * 本项目 1.16.4 的 {@code 标识符 -> ItemGroup 名}。
     * 两种写法：{@code register(Blocks.X, ItemGroup.Y)} / {@code register(new XItem(Blocks.X, ...group(ItemGroup.Y)))}
     * 取方块标识符；{@code register("id", ...group(ItemGroup.Y))} 直接取字面量。
     * 没写 ItemGroup 的（命令方块、结构空位等）不进表 —— 它们在原版也不出现在创造栏。
     */
    static Map<String, String> vanillaItemGroups(Path repo, Map<String, String> blockIds) throws Exception {
        Map<String, String> out = new LinkedHashMap<>();
        Pattern group = Pattern.compile("ItemGroup\\.([A-Z_]+)");
        Pattern viaBlock = Pattern.compile("Blocks\\.([A-Z0-9_]+)");
        Pattern viaName = Pattern.compile("register\\(\"([a-z0-9_]+)\"");

        for (String line : Files.readAllLines(repo.resolve("src/main/java/net/minecraft/item/Items.java"),
                StandardCharsets.UTF_8)) {
            if (!line.contains("public static final Item ")) continue;
            Matcher gm = group.matcher(line);
            if (!gm.find()) continue;

            Matcher nm = viaName.matcher(line);
            Matcher bm = viaBlock.matcher(line);
            String id = null;
            if (nm.find()) {
                id = nm.group(1);
            } else if (bm.find()) {
                id = blockIds.get(bm.group(1));
            }
            if (id != null) out.put(id, gm.group(1));
        }
        System.out.printf("原版 1.16.4 有分组的物品 %d 个%n", out.size());
        return out;
    }
}
