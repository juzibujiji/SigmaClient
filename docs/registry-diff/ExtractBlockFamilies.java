package extract;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.regex.*;

/**
 * 一次性导出「氧化铜家族」：同一种方块的 8 个氧化/打蜡状态属于同一个家族。
 *
 * <p>为什么需要这张表：创造栏分类如果逐个方块判定，同一种方块会被劈成两半 ——
 * {@code waxed_copper_bars} 的类型就是普通 {@code iron_bars}（原版在 DECORATIONS），
 * 而未打蜡的 {@code copper_bars} 是 {@code WeatheringCopperBarsBlock}，没有原版同类，
 * 只能靠邻居猜，结果落到 BUILDING_BLOCKS。玩家看到的就是「铜栏杆在建筑方块里，
 * 打过蜡的铜栏杆在装饰里」。按家族整体投票就不会出现这种劈裂。
 *
 * <p>家族定义取自官方两张权威映射表，不按名字前缀推：
 * <ul>
 *   <li>{@code WeatheringCopper.NEXT_BY_BLOCK} —— 氧化链（未氧化→斑驳→锈蚀→氧化）</li>
 *   <li>{@code HoneycombItem.WAXABLES} —— 打蜡（未打蜡→打蜡）</li>
 * </ul>
 * 两张表求并集后取连通分量，每个连通分量就是一个家族。
 *
 * <p>其中 {@code .putAll(Blocks.X.weatheringMapping())} 与
 * {@code .putAll(Blocks.X.waxedMapping())} 要按官方 {@code WeatheringCopperBlocks}
 * 的实现展开（见该类的两个 mapping 方法）。
 *
 * <p>用法：{@code java extract.ExtractBlockFamilies <MCP-Reborn 根目录> <SigmaClient 根目录>}
 */
public class ExtractBlockFamilies {

    public static void main(String[] args) throws Exception {
        Path srcRoot = Paths.get(args[0]).resolve("src/main/java/net/minecraft");
        Path repo = Paths.get(args[1]);

        Map<String, String> blockIds = blockFieldIds(srcRoot);
        Map<String, String[]> weatheringGroups = weatheringGroups(srcRoot);

        // 官方 WeatheringCopperBlocks 的两个 mapping 方法给出 putAll 的展开方式
        Map<String, List<String[]>> mappingPairs = mappingPairs(srcRoot);

        List<String[]> edges = new ArrayList<>();
        edges.addAll(parseMap(srcRoot.resolve("world/level/block/WeatheringCopper.java"),
                "NEXT_BY_BLOCK", "weatheringMapping", blockIds, weatheringGroups, mappingPairs));
        edges.addAll(parseMap(srcRoot.resolve("world/item/HoneycombItem.java"),
                "WAXABLES", "waxedMapping", blockIds, weatheringGroups, mappingPairs));

        // 连通分量：并查集
        Map<String, String> parent = new TreeMap<>();
        for (String[] e : edges) {
            parent.putIfAbsent(e[0], e[0]);
            parent.putIfAbsent(e[1], e[1]);
            union(parent, e[0], e[1]);
        }
        Map<String, List<String>> families = new TreeMap<>();
        for (String id : parent.keySet()) {
            families.computeIfAbsent(find(parent, id), k -> new ArrayList<>()).add(id);
        }

        // 家族名用「未氧化未打蜡」那个成员，也就是在氧化链和打蜡表里都只作为源出现的那个
        Set<String> hasPredecessor = new HashSet<>();
        for (String[] e : edges) hasPredecessor.add(e[1]);

        StringBuilder csv = new StringBuilder();
        int rows = 0;
        for (List<String> members : families.values()) {
            List<String> roots = new ArrayList<>(members);
            roots.removeIf(hasPredecessor::contains);
            if (roots.size() != 1) {
                throw new IllegalStateException("家族根不唯一：" + members + " 候选 " + roots);
            }
            Collections.sort(members);
            for (String m : members) {
                csv.append(roots.get(0)).append(',').append(m).append('\n');
                rows++;
            }
        }

        Path out = repo.resolve("docs/registry-diff/weathering-families-1.21.11.csv");
        Files.writeString(out, csv.toString(), StandardCharsets.UTF_8);
        System.out.printf("%d 个家族、%d 个成员，已写入 %s%n", families.size(), rows, out);
        families.values().forEach(m -> System.out.printf("  %d 成员：%s%n", m.size(), m));
    }

    /**
     * 解析一张 {@code ImmutableBiMap.builder()...build()} 映射。
     * 只取 {@code 字段名} 那一段到 {@code .build()} 为止，避免把同文件里别的表也吃进来。
     */
    private static List<String[]> parseMap(Path file, String fieldName, String mappingMethod,
                                          Map<String, String> blockIds,
                                          Map<String, String[]> weatheringGroups,
                                          Map<String, List<String[]>> mappingPairs) throws Exception {
        String src = Files.readString(file, StandardCharsets.UTF_8);
        int from = src.indexOf(fieldName);
        if (from < 0) throw new IllegalStateException(file + " 里找不到 " + fieldName);
        int to = src.indexOf(".build()", from);
        if (to < 0) throw new IllegalStateException(fieldName + " 没有 .build()");
        String body = src.substring(from, to);

        List<String[]> edges = new ArrayList<>();
        Matcher put = Pattern.compile("\\.put\\(\\s*Blocks\\.([A-Z0-9_]+)\\s*,\\s*Blocks\\.([A-Z0-9_]+)\\s*\\)")
                .matcher(body);
        while (put.find()) {
            String a = blockIds.get(put.group(1));
            String b = blockIds.get(put.group(2));
            if (a == null || b == null) {
                throw new IllegalStateException("解析不到方块标识符：" + put.group(0));
            }
            edges.add(new String[]{a, b});
        }
        int direct = edges.size();

        int expanded = 0;
        Matcher putAll = Pattern.compile(
                "\\.putAll\\(\\s*Blocks\\.([A-Z0-9_]+)\\." + Pattern.quote(mappingMethod) + "\\(\\)\\s*\\)")
                .matcher(body);
        while (putAll.find()) {
            String[] members = weatheringGroups.get(putAll.group(1));
            if (members == null) {
                throw new IllegalStateException("未知的 WeatheringCopperBlocks 组：" + putAll.group(1));
            }
            for (String[] pair : mappingPairs.get(mappingMethod)) {
                edges.add(new String[]{members[accessorIndex(pair[0])], members[accessorIndex(pair[1])]});
            }
            expanded++;
        }
        if (expanded == 0) {
            throw new IllegalStateException(fieldName + " 里一处 putAll 都没解析到，正则可能失配");
        }
        System.out.printf("%s：%d 条 put + %d 处 putAll 展开 %d 条 -> 共 %d 条边%n",
                fieldName, direct, expanded, edges.size() - direct, edges.size());
        return edges;
    }

    /** {@code WeatheringCopperBlocks} 记录组件的声明顺序，用来把访问器名换成下标。 */
    private static final List<String> ACCESSORS = List.of(
            "unaffected", "exposed", "weathered", "oxidized",
            "waxed", "waxedExposed", "waxedWeathered", "waxedOxidized");

    private static int accessorIndex(String accessor) {
        int i = ACCESSORS.indexOf(accessor);
        if (i < 0) throw new IllegalStateException("未知访问器 " + accessor);
        return i;
    }

    /**
     * 从官方 {@code WeatheringCopperBlocks} 读出 {@code weatheringMapping()} 与
     * {@code waxedMapping()} 各自的 {@code (源, 目标)} 访问器对。
     */
    private static Map<String, List<String[]>> mappingPairs(Path srcRoot) throws Exception {
        String src = Files.readString(srcRoot.resolve("world/level/block/WeatheringCopperBlocks.java"),
                StandardCharsets.UTF_8);
        Map<String, List<String[]>> out = new LinkedHashMap<>();
        for (String method : new String[]{"weatheringMapping", "waxedMapping"}) {
            Matcher m = Pattern.compile(Pattern.quote(method) + "\\(\\)\\s*\\{\\s*return ImmutableBiMap\\.of\\(([^;]*)\\);")
                    .matcher(src);
            if (!m.find()) throw new IllegalStateException("解析不到 " + method);
            List<String> args = new ArrayList<>();
            Matcher a = Pattern.compile("this\\.([a-zA-Z]+)").matcher(m.group(1));
            while (a.find()) args.add(a.group(1));
            if (args.size() % 2 != 0) throw new IllegalStateException(method + " 参数个数不成对");
            List<String[]> pairs = new ArrayList<>();
            for (int i = 0; i < args.size(); i += 2) {
                pairs.add(new String[]{args.get(i), args.get(i + 1)});
            }
            out.put(method, pairs);
            System.out.printf("%s：%s%n", method, pairs.stream()
                    .map(p -> p[0] + "->" + p[1]).reduce((x, y) -> x + ", " + y).orElse(""));
        }
        return out;
    }

    /**
     * {@code Blocks} 里的 {@code WeatheringCopperBlocks} 字段名 -> 8 个成员标识符，
     * 顺序与 {@link #ACCESSORS} 一致。前缀取自官方 {@code WeatheringCopperBlocks.create}
     * 里的字符串拼接。
     */
    private static Map<String, String[]> weatheringGroups(Path srcRoot) throws Exception {
        String src = Files.readString(srcRoot.resolve("world/level/block/Blocks.java"), StandardCharsets.UTF_8);
        Map<String, String[]> out = new LinkedHashMap<>();
        Matcher m = Pattern.compile(
                "WeatheringCopperBlocks ([A-Z0-9_]+) = WeatheringCopperBlocks\\.create\\(\\s*\"([a-z0-9_]+)\"")
                .matcher(src);
        String[] prefixes = {"", "exposed_", "weathered_", "oxidized_",
                "waxed_", "waxed_exposed_", "waxed_weathered_", "waxed_oxidized_"};
        while (m.find()) {
            String base = m.group(2);
            String[] members = new String[8];
            for (int i = 0; i < 8; i++) members[i] = prefixes[i] + base;
            out.put(m.group(1), members);
        }
        return out;
    }

    private static Map<String, String> blockFieldIds(Path srcRoot) throws Exception {
        Map<String, String> refs = new HashMap<>();
        Matcher rm = Pattern.compile("ResourceKey<Block> ([A-Z0-9_]+) = createKey\\(\"([a-z0-9_]+)\"\\)")
                .matcher(Files.readString(srcRoot.resolve("references/Blocks.java"), StandardCharsets.UTF_8));
        while (rm.find()) refs.put(rm.group(1), rm.group(2));

        Map<String, String> out = new HashMap<>();
        // registerStair / registerLegacyStair / registerBed / registerStainedGlass 都是转发给
        // register 的包装，第一个参数同样是标识符，所以方法名后面允许有后缀。
        Matcher m = Pattern.compile("public static final Block ([A-Z0-9_]+) = register[A-Za-z]*\\(\\s*"
                + "(?:\"([a-z0-9_]+)\"|net\\.minecraft\\.references\\.Blocks\\.([A-Z0-9_]+))")
                .matcher(Files.readString(srcRoot.resolve("world/level/block/Blocks.java"), StandardCharsets.UTF_8));
        while (m.find()) {
            String id = m.group(2) != null ? m.group(2) : refs.get(m.group(3));
            if (id != null) out.put(m.group(1), id);
        }
        return out;
    }

    private static String find(Map<String, String> parent, String x) {
        while (!parent.get(x).equals(x)) x = parent.get(x);
        return x;
    }

    private static void union(Map<String, String> parent, String a, String b) {
        parent.put(find(parent, a), find(parent, b));
    }
}
