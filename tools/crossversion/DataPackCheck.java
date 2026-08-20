package verify;

import com.google.gson.*;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Stream;

/**
 * 数据包完整性检查。在启动客户端之前就把「数据包出现错误，世界无法加载」挡住。
 *
 * <p>1.16.4 对数据文件的容错很不一致，两类错误的表现天差地别：
 * <ul>
 *   <li><b>tag</b>：引用缺失时 {@code TagCollectionReader} 记 error 并<b>丢弃整张 tag</b>，
 *       进而导致数据包加载失败、世界打不开。而且 tag 引用的前缀位置与方块 id 不同
 *       （{@code #minecraft:wooden_doors} 的 {@code #} 在命名空间之前），拼错了同样致命。</li>
 *   <li><b>战利品表</b>：引用未注册物品时 {@code JSONUtils.getItem} 直接抛
 *       JsonSyntaxException，挖那个方块就报错。</li>
 * </ul>
 *
 * <p>四项检查：JSON 语法、tag 引用可解析、tag 引用的方块已注册、战利品表引用的物品已注册。
 */
public class DataPackCheck {
    static int failures = 0;

    public static void main(String[] args) throws Exception {
        if (args.length < 3) {
            System.err.println("用法: DataPackCheck <data/minecraft 目录> <已注册方块清单> <已注册物品清单>");
            System.exit(2);
        }
        Path data = Paths.get(args[0]);
        Set<String> blocks = readSet(args[1]);
        Set<String> items = readSet(args[2]);

        Path tagDir = data.resolve("tags/blocks");
        Path lootDir = data.resolve("loot_tables/blocks");

        System.out.println("=== 数据包完整性检查 ===");
        checkJsonSyntax(data);
        checkTagRefs(tagDir);
        checkTagBlocks(tagDir, blocks);
        checkLootItems(lootDir, items);

        System.out.println(failures == 0 ? "\n数据包检查：全部通过" : "\n数据包检查：" + failures + " 项失败");
        if (failures != 0) System.exit(1);
    }

    static Set<String> readSet(String path) throws Exception {
        Set<String> out = new HashSet<>();
        for (String line : Files.readAllLines(Paths.get(path))) {
            String t = line.trim().replace("minecraft:", "");
            if (!t.isEmpty()) out.add(t);
        }
        return out;
    }

    /** 任何一个 JSON 语法错误都会让整个数据包加载失败。 */
    static void checkJsonSyntax(Path root) throws Exception {
        int checked = 0, bad = 0;
        try (Stream<Path> files = Files.walk(root)) {
            for (Path p : (Iterable<Path>) files.filter(f -> f.toString().endsWith(".json"))::iterator) {
                checked++;
                try {
                    JsonParser.parseString(Files.readString(p, StandardCharsets.UTF_8));
                } catch (Exception e) {
                    System.out.println("  ** JSON 语法错误 " + root.relativize(p) + ": " + e.getMessage());
                    bad++;
                }
            }
        }
        System.out.printf("  JSON 语法：检查 %d 个文件，%d 个有问题 %s%n",
                checked, bad, bad == 0 ? "OK" : "**");
        failures += bad;
    }

    /** tag 之间的引用必须能解析到实际文件，否则整张 tag 被丢弃。 */
    static void checkTagRefs(Path tagDir) throws Exception {
        if (!Files.exists(tagDir)) return;
        int refs = 0, missing = 0, badPrefix = 0;
        try (Stream<Path> files = Files.list(tagDir)) {
            for (Path p : (Iterable<Path>) files.filter(f -> f.toString().endsWith(".json"))::iterator) {
                for (String v : values(p)) {
                    // 前缀拼错：# 必须在命名空间之前
                    if (v.contains(":#") || v.startsWith("minecraft:#")) {
                        System.out.println("  ** 前缀顺序错误 " + p.getFileName() + " -> " + v
                                + "（应为 #minecraft:...）");
                        badPrefix++;
                        continue;
                    }
                    if (!v.startsWith("#")) continue;
                    refs++;
                    String target = v.substring(1).replace("minecraft:", "");
                    if (!Files.exists(tagDir.resolve(target + ".json"))) {
                        System.out.println("  ** tag 引用缺失 " + p.getFileName() + " -> " + v);
                        missing++;
                    }
                }
            }
        }
        System.out.printf("  tag 引用：%d 个引用，%d 个缺失，%d 个前缀错误 %s%n",
                refs, missing, badPrefix, (missing + badPrefix) == 0 ? "OK" : "**");
        failures += missing + badPrefix;
    }

    static void checkTagBlocks(Path tagDir, Set<String> registered) throws Exception {
        if (!Files.exists(tagDir) || registered.isEmpty()) return;
        Set<String> unknown = new TreeSet<>();
        try (Stream<Path> files = Files.list(tagDir)) {
            for (Path p : (Iterable<Path>) files.filter(f -> f.toString().endsWith(".json"))::iterator) {
                for (String v : values(p)) {
                    if (v.startsWith("#")) continue;
                    String id = v.replace("minecraft:", "");
                    if (!registered.contains(id)) unknown.add(p.getFileName() + " -> " + id);
                }
            }
        }
        System.out.printf("  tag 里的方块：%d 个未注册 %s%n", unknown.size(), unknown.isEmpty() ? "OK" : "**");
        unknown.stream().limit(10).forEach(u -> System.out.println("    ** " + u));
        failures += unknown.size();
    }

    static void checkLootItems(Path lootDir, Set<String> registered) throws Exception {
        if (!Files.exists(lootDir) || registered.isEmpty()) return;
        Set<String> unknown = new TreeSet<>();
        int tables = 0;
        try (Stream<Path> files = Files.list(lootDir)) {
            for (Path p : (Iterable<Path>) files.filter(f -> f.toString().endsWith(".json"))::iterator) {
                tables++;
                collectItemNames(JsonParser.parseString(
                        Files.readString(p, StandardCharsets.UTF_8)), p.getFileName().toString(),
                        registered, unknown);
            }
        }
        System.out.printf("  战利品表：%d 张，%d 处引用未注册物品 %s%n",
                tables, unknown.size(), unknown.isEmpty() ? "OK" : "**");
        unknown.stream().limit(10).forEach(u -> System.out.println("    ** " + u));
        failures += unknown.size();
    }

    /** 只看 type=item 的条目：dynamic 条目的 name（如潜影盒的 contents）不是物品标识符。 */
    static void collectItemNames(JsonElement node, String file, Set<String> registered, Set<String> unknown) {
        if (node.isJsonObject()) {
            JsonObject o = node.getAsJsonObject();
            if (o.has("type") && o.has("name")
                    && "minecraft:item".equals(o.get("type").getAsString())) {
                String id = o.get("name").getAsString().replace("minecraft:", "");
                if (!registered.contains(id)) unknown.add(file + " -> " + id);
            }
            for (Map.Entry<String, JsonElement> e : o.entrySet()) {
                collectItemNames(e.getValue(), file, registered, unknown);
            }
        } else if (node.isJsonArray()) {
            for (JsonElement e : node.getAsJsonArray()) collectItemNames(e, file, registered, unknown);
        }
    }

    static List<String> values(Path p) throws Exception {
        List<String> out = new ArrayList<>();
        JsonObject root = JsonParser.parseString(Files.readString(p, StandardCharsets.UTF_8)).getAsJsonObject();
        JsonElement values = root.get("values");
        if (values == null || !values.isJsonArray()) return out;
        for (JsonElement e : values.getAsJsonArray()) {
            if (e.isJsonObject()) {
                JsonElement id = e.getAsJsonObject().get("id");
                if (id != null) out.add(id.getAsString());
            } else {
                out.add(e.getAsString());
            }
        }
        return out;
    }
}
