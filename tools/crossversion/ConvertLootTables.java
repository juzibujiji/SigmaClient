package verify;

import com.google.gson.*;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;

/**
 * 把官方 1.21.11 的方块战利品表转换成 1.16.4 能解析的格式。
 *
 * <p>与模型资源不同，战利品表<b>不能直接复制</b>，有四处不兼容：
 * <ul>
 *   <li>目录名：1.21 是 {@code data/minecraft/loot_table/}（单数），1.16.4 是
 *       {@code loot_tables/}（复数）</li>
 *   <li>{@code random_sequence}：1.20.3 新增的顶层字段，1.16.4 不认识</li>
 *   <li>{@code match_tool} 的谓词多了一层嵌套：
 *       {@code predicate.predicates["minecraft:enchantments"][].enchantments}
 *       在 1.16.4 是 {@code predicate.enchantments[].enchantment}</li>
 *   <li>{@code set_count} 多了 {@code add} 字段</li>
 * </ul>
 *
 * <p><b>失败时降级而不是放弃</b>：遇到无法可靠转换的结构，就生成一张「掉落自己」的
 * 简单表并记录清单。1.16.4 的战利品表解析很严格，塞进去一张它读不懂的表会导致
 * 挖方块时抛异常；掉落自己至少是可玩的，而且清单会告诉我们哪些需要人工补。
 */
public class ConvertLootTables {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    /** 1.16.4 不认识、需要整个删掉的键。 */
    private static final Set<String> DROP_KEYS = new HashSet<>(Arrays.asList(
            "random_sequence", "bonus_rolls", "add"));

    /**
     * 已注册的物品全集。战利品表里引用未注册物品会让 {@code JSONUtils.getItem} 的
     * {@code orElseThrow} 抛 JsonSyntaxException，加载失败后挖那个方块就会出错，
     * 所以引用了未注册物品的条目必须整条剔除。
     *
     * <p>典型场景：樱花树叶掉樱花树苗，但树苗的方块类型需要专门类、尚未注册。
     */
    private static Set<String> registered = Collections.emptySet();
    /** 被剔除的条目，供报告。 */
    private static final List<String> pruned = new ArrayList<>();

    public static void main(String[] args) throws Exception {
        if (args.length < 3) {
            System.err.println("用法: ConvertLootTables <server.jar> <方块清单> <输出目录> [已注册物品清单]");
            System.exit(2);
        }
        Path jarPath = Paths.get(args[0]);
        List<String> blocks = new ArrayList<>();
        for (String line : Files.readAllLines(Paths.get(args[1]))) {
            String t = line.trim();
            if (!t.isEmpty() && !t.startsWith("#")) blocks.add(t.replace("minecraft:", ""));
        }
        Path outDir = Paths.get(args[2]);
        Files.createDirectories(outDir);

        if (args.length > 3) {
            Set<String> reg = new HashSet<>();
            for (String line : Files.readAllLines(Paths.get(args[3]))) {
                String t = line.trim().replace("minecraft:", "");
                if (!t.isEmpty()) reg.add(t);
            }
            registered = reg;
            System.out.println("已注册物品白名单：" + registered.size() + " 项");
        }

        int converted = 0, fallback = 0, missing = 0, skippedExisting = 0, noItem = 0;
        List<String> fallbackList = new ArrayList<>();
        List<String> missingList = new ArrayList<>();
        List<String> noItemList = new ArrayList<>();

        try (ZipFile jar = new ZipFile(findInner(jarPath).toFile())) {
            for (String id : blocks) {
                Path target = outDir.resolve(id + ".json");
                if (Files.exists(target)) {
                    skippedExisting++;
                    continue;
                }

                ZipEntry entry = jar.getEntry("data/minecraft/loot_table/blocks/" + id + ".json");
                if (entry == null) {
                    // 官方没有这张表，通常意味着这个方块<b>没有自己的物品</b>：墙上告示牌、
                    // 墙上火把这类靠 .lootFrom(地面变体) 重定向到别人的表。
                    // 这时候写「掉落自己」会造出一张引用未注册物品的表，破坏时抛异常。
                    // 一次生成了 16 张墙上告示牌的自掉落表，被数据包检查抓到。
                    if (!canDropSelf(id)) {
                        noItem++;
                        noItemList.add(id);
                        continue;
                    }
                    missing++;
                    missingList.add(id);
                    Files.writeString(target, selfDrop(id), StandardCharsets.UTF_8);
                    continue;
                }

                String json = new String(jar.getInputStream(entry).readAllBytes(), StandardCharsets.UTF_8);
                String result;
                try {
                    JsonObject root = JsonParser.parseString(json).getAsJsonObject();
                    JsonElement cleaned = transform(root);
                    // 整张表被剔空（所有掉落物都未注册）时退回掉落自己
                    if (cleaned == null && !canDropSelf(id)) {
                        noItem++;
                        noItemList.add(id);
                        continue;
                    }
                    result = cleaned == null ? selfDrop(id) : GSON.toJson(cleaned) + "\n";
                    converted++;
                } catch (Exception e) {
                    if (!canDropSelf(id)) {
                        noItem++;
                        noItemList.add(id);
                        continue;
                    }
                    result = selfDrop(id);
                    fallback++;
                    fallbackList.add(id + " (" + e.getClass().getSimpleName() + ")");
                }
                Files.writeString(target, result, StandardCharsets.UTF_8);
            }
        }

        System.out.printf("转换 %d 张，降级为掉落自己 %d 张，官方无此表 %d 张，跳过已存在 %d 张，无对应物品跳过 %d 张%n",
                converted, fallback, missing, skippedExisting, noItem);
        if (!noItemList.isEmpty()) {
            System.out.println("这些方块没有对应物品，不写战利品表（应由 .lootFrom(...) 重定向）：");
            noItemList.forEach(s -> System.out.println("  " + s));
        }
        if (!pruned.isEmpty()) {
            Map<String, Integer> counts = new TreeMap<>();
            for (String p : pruned) counts.merge(p, 1, Integer::sum);
            System.out.println("剔除了引用未注册物品的条目（这些掉落物属于后续批次）：");
            counts.forEach((k, v) -> System.out.printf("  %-24s 出现 %d 次%n", k, v));
        }
        if (!fallbackList.isEmpty()) {
            System.out.println("降级清单（需人工复核）：");
            fallbackList.stream().limit(20).forEach(s -> System.out.println("  " + s));
        }
        if (!missingList.isEmpty()) {
            System.out.println("官方没有战利品表的方块（多半本就不掉落，例如氧化中间态）：");
            missingList.stream().limit(20).forEach(s -> System.out.println("  " + s));
            if (missingList.size() > 20) System.out.println("  ... 另有 " + (missingList.size() - 20) + " 项");
        }
    }

    /** bundler 格式的 server.jar 内层是 META-INF/versions/<ver>/server-<ver>.jar。 */
    static Path findInner(Path bundler) throws Exception {
        Path extracted = bundler.getParent().resolve("server-inner.jar");
        if (Files.exists(extracted)) return extracted;
        try (ZipFile zf = new ZipFile(bundler.toFile())) {
            for (Enumeration<? extends ZipEntry> e = zf.entries(); e.hasMoreElements(); ) {
                ZipEntry entry = e.nextElement();
                if (entry.getName().startsWith("META-INF/versions/") && entry.getName().endsWith(".jar")) {
                    Files.copy(zf.getInputStream(entry), extracted, StandardCopyOption.REPLACE_EXISTING);
                    return extracted;
                }
            }
        }
        // 不是 bundler，直接当普通 jar 用
        return bundler;
    }

    /**
     * 递归转换：删掉 1.16.4 不认的键、改写 match_tool 的谓词结构、
     * 剔除引用未注册物品的条目。
     *
     * @return 转换后的节点；返回 {@code null} 表示这个节点应当从父级删除
     */
    static JsonElement transform(JsonElement node) {
        if (node.isJsonObject()) {
            JsonObject src = node.getAsJsonObject();

            // 引用未注册物品的 item 条目整条剔除，否则 1.16.4 加载时会抛异常。
            // 只检查 type=item 的条目：dynamic 条目的 name（例如潜影盒的
            // minecraft:contents）不是物品标识符。
            if (!registered.isEmpty() && src.has("type") && src.has("name")
                    && "minecraft:item".equals(src.get("type").getAsString())) {
                String name = src.get("name").getAsString().replace("minecraft:", "");
                if (!registered.contains(name)) {
                    pruned.add(name);
                    return null;
                }
            }

            JsonObject out = new JsonObject();
            boolean isMatchTool = src.has("condition")
                    && "minecraft:match_tool".equals(src.get("condition").getAsString());

            for (Map.Entry<String, JsonElement> e : src.entrySet()) {
                String key = e.getKey();
                if (DROP_KEYS.contains(key)) continue;

                if (isMatchTool && "predicate".equals(key)) {
                    out.add("predicate", convertItemPredicate(e.getValue()));
                    continue;
                }

                JsonElement converted = transform(e.getValue());
                if (converted == null) continue;

                // entries / children / pools 被清空后，留着空数组会让 1.16.4 解析失败，
                // 整个父节点一并删除。
                if (converted.isJsonArray() && converted.getAsJsonArray().isEmpty()
                        && ("entries".equals(key) || "children".equals(key) || "pools".equals(key))) {
                    return null;
                }
                out.add(key, converted);
            }
            return out;
        }
        if (node.isJsonArray()) {
            JsonArray out = new JsonArray();
            for (JsonElement e : node.getAsJsonArray()) {
                JsonElement converted = transform(e);
                if (converted != null) out.add(converted);
            }
            return out;
        }
        return node;
    }

    /**
     * 1.21 的物品谓词：{@code {"predicates": {"minecraft:enchantments": [...]}}}
     * 1.16.4 的物品谓词：{@code {"enchantments": [{"enchantment": ..., "levels": ...}]}}
     *
     * <p>只处理附魔谓词 —— 方块战利品表里 match_tool 几乎只用它（判断精准采集）。
     * 遇到其他谓词类型就抛异常，由调用方降级成掉落自己，而不是塞一张 1.16.4 读不懂的表。
     */
    static JsonElement convertItemPredicate(JsonElement predicate) {
        if (!predicate.isJsonObject()) throw new IllegalStateException("predicate 不是对象");
        JsonObject src = predicate.getAsJsonObject();
        if (!src.has("predicates")) {
            // 已经是旧结构，原样通过
            return transform(src);
        }

        JsonObject predicates = src.getAsJsonObject("predicates");
        JsonObject out = new JsonObject();

        for (Map.Entry<String, JsonElement> e : predicates.entrySet()) {
            if (!"minecraft:enchantments".equals(e.getKey())) {
                throw new IllegalStateException("未支持的物品谓词: " + e.getKey());
            }
            JsonArray converted = new JsonArray();
            for (JsonElement ench : e.getValue().getAsJsonArray()) {
                JsonObject eo = ench.getAsJsonObject();
                JsonObject no = new JsonObject();
                // 1.21 的键是复数 enchantments（值可以是单个 id 或 tag），1.16.4 是单数 enchantment
                JsonElement id = eo.has("enchantments") ? eo.get("enchantments") : eo.get("enchantment");
                if (id == null || !id.isJsonPrimitive()) {
                    throw new IllegalStateException("附魔谓词不是单个 id");
                }
                no.add("enchantment", id);
                if (eo.has("levels")) no.add("levels", eo.get("levels"));
                converted.add(no);
            }
            out.add("enchantments", converted);
        }

        // 保留 predicates 之外的字段（例如 items / count）
        for (Map.Entry<String, JsonElement> e : src.entrySet()) {
            if (!"predicates".equals(e.getKey())) out.add(e.getKey(), transform(e.getValue()));
        }
        return out;
    }

    /** 最简单的「掉落自己」战利品表，1.16.4 格式。 */
    /**
     * 这个方块能不能「掉落自己」——也就是有没有同名的已注册物品。
     *
     * <p>没有物品的方块不能写自掉落表：表里的 {@code minecraft:<id>} 解析不到物品，
     * 1.16.4 会在破坏方块时抛异常。墙上告示牌就是这种，它们靠
     * {@code AbstractBlock.Properties.lootFrom(地面变体)} 重定向到别人的表。
     *
     * <p>没传物品白名单时（{@code registered} 为空）保持旧行为，一律允许 ——
     * 否则会把所有表都跳掉。
     */
    private static boolean canDropSelf(String id) {
        return registered.isEmpty() || registered.contains(id);
    }

    static String selfDrop(String id) {
        return "{\n"
                + "  \"type\": \"minecraft:block\",\n"
                + "  \"pools\": [\n"
                + "    {\n"
                + "      \"rolls\": 1,\n"
                + "      \"entries\": [\n"
                + "        {\n"
                + "          \"type\": \"minecraft:item\",\n"
                + "          \"name\": \"minecraft:" + id + "\"\n"
                + "        }\n"
                + "      ],\n"
                + "      \"conditions\": [\n"
                + "        {\n"
                + "          \"condition\": \"minecraft:survives_explosion\"\n"
                + "        }\n"
                + "      ]\n"
                + "    }\n"
                + "  ]\n"
                + "}\n";
    }
}
