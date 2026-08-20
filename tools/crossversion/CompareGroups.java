package verify;

import com.google.gson.*;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/**
 * 一次性对照报告：把「类型投票」「邻居窗口」「现有名字规则」三种分类结果并排列出，
 * 只在有分歧时打印。用来在换掉名字规则之前逐条复核，而不是改完直接信。
 *
 * <p>用法：{@code java verify.CompareGroups <仓库根目录>}
 */
public class CompareGroups {

    public static void main(String[] args) throws Exception {
        Path repo = Paths.get(args.length > 0 ? args[0] : ".");
        Path diff = repo.resolve("docs/registry-diff");

        JsonObject blocksJson = JsonParser.parseString(Files.readString(
                repo.resolve("1.21.11/reports/blocks.json"), StandardCharsets.UTF_8)).getAsJsonObject();
        CreativeGroups groups = CreativeGroups.load(repo, blocksJson);

        Set<String> allBlocks = new LinkedHashSet<>(
                Files.readAllLines(diff.resolve("official-blocks-1.21.11.txt")));
        List<String> extended = Files.readAllLines(diff.resolve("added-items-1.16.4-to-1.21.11.txt"));

        int agree = 0;
        List<String> disagree = new ArrayList<>();
        List<String> fallbacks = new ArrayList<>();
        Map<String, Integer> basisCount = new TreeMap<>();
        Map<String, Integer> finalCount = new TreeMap<>();
        StringBuilder all = new StringBuilder();

        for (String id : extended) {
            boolean isBlock = allBlocks.contains(id);
            String legacy = isBlock ? legacyBlockGroup(id, groups) : legacyPlainGroup(id);
            CreativeGroups.Decision d = groups.decide(id, isBlock ? "BUILDING_BLOCKS" : "MISC");

            basisCount.merge(d.basis, 1, Integer::sum);
            finalCount.merge(d.group, 1, Integer::sum);
            if ("兜底".equals(d.basis)) fallbacks.add(id);
            all.append(String.format("%-44s %-16s %-14s %s%n", id, d.group, d.basis, d.detail));

            if (d.group.equals(legacy) || aliasSame(d.group, legacy)) {
                agree++;
            } else {
                CreativeGroups.Decision t = groups.byType(id);
                CreativeGroups.Decision n = groups.byNeighbour(id);
                disagree.add(String.format("  %-40s 旧 %-16s 新 %-16s  类型:%-16s 邻居:%s",
                        id, legacy, d.group,
                        t == null ? "-" : t.group + "(" + t.detail + ")",
                        n == null ? "-" : n.group + " " + n.detail));
            }
        }

        Path dump = repo.resolve("target/crossversion-check/group-decisions.txt");
        Files.createDirectories(dump.getParent());
        Files.writeString(dump, all.toString(), StandardCharsets.UTF_8);
        System.out.println("全量判定已写入 " + dump);

        System.out.printf("扩展物品 %d 个：与旧规则一致 %d，分歧 %d%n", extended.size(), agree, disagree.size());
        System.out.println("判定依据分布：" + basisCount);
        System.out.println("最终分类分布：" + finalCount);
        System.out.println("\n=== 需要兜底的（" + fallbacks.size() + " 个）===");
        fallbacks.forEach(id -> System.out.println("  " + id));
        System.out.println("\n=== 与旧规则分歧的（" + disagree.size() + " 项）===");
        disagree.forEach(System.out::println);
    }

    /** MATERIALS 在 1.16.4 就是 MISC 的别名（{@code ItemGroup.MATERIALS = MISC}），不算分歧。 */
    private static boolean aliasSame(String a, String b) {
        return norm(a).equals(norm(b));
    }

    private static String norm(String g) {
        return "MATERIALS".equals(g) ? "MISC" : g;
    }

    // --- 下面两个是被替换掉的旧规则，原样保留只为对照 ---

    private static String legacyBlockGroup(String id, CreativeGroups groups) {
        String type = groups.typeOf(id);
        if (type != null) {
            CreativeGroups.Decision t = groups.byType(id);
            if (t != null) return t.group;
        }
        if (id.contains("sapling") || id.contains("leaves") || id.contains("flower")
                || id.contains("azalea") || id.endsWith("_carpet") || id.contains("candle")
                || id.contains("lantern") || id.contains("torch") || id.contains("sign")
                || id.contains("pot") || id.contains("head") || id.contains("statue")
                || id.contains("_bars") || id.contains("chain")) {
            return "DECORATIONS";
        }
        if (id.contains("_bulb") || id.contains("lightning_rod")
                || id.equals("sculk_sensor") || id.equals("calibrated_sculk_sensor")
                || id.equals("sculk_shrieker")) {
            return "REDSTONE";
        }
        return "BUILDING_BLOCKS";
    }

    private static String legacyPlainGroup(String id) {
        if (id.endsWith("_ingot") || id.endsWith("_nugget") || id.startsWith("raw_")
                || id.endsWith("_shard") || id.endsWith("_rod") || id.endsWith("_brick")
                || id.endsWith("_scute") || id.endsWith("_key") || id.contains("resin")) {
            return "MATERIALS";
        }
        if (id.contains("charge") || id.contains("bottle")) {
            return "COMBAT";
        }
        return "MISC";
    }
}
