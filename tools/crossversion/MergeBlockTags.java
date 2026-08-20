package verify;

import com.google.gson.*;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;

/**
 * 把新方块并入 1.16.4 的结构性方块 tag。
 *
 * <p>没有这些 tag，方块之间就不会互相识别：{@code WallBlock} 靠
 * {@code state.isIn(BlockTags.WALLS)} 决定是否连接、{@code FenceBlock} 靠
 * {@code FENCES} 与 {@code WOODEN_FENCES} 判断同类 —— 所以新加的墙与栅栏
 * 彼此之间不会连起来，看着像断的。
 *
 * <p><b>必须过滤未注册的方块。</b>1.16.4 的 {@code TagCollectionReader} 遇到缺失引用会
 * 记 error 并<b>丢弃整个 tag</b>（"Couldn't load tag as it is missing following
 * references"），一个未注册的方块就能让整张 walls 表失效，连原版的墙也不连了。
 *
 * <p>目录名注意：1.21 是 {@code data/minecraft/tags/block/}（单数），
 * 1.16.4 是 {@code tags/blocks/}（复数）。
 */
public class MergeBlockTags {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    /**
     * 影响方块互相识别与放置行为的结构性 tag。
     *
     * <p>刻意收窄到「不加就会看出问题」的那几个：墙与栅栏的连接、门与活板门的同类判断、
     * 台阶楼梯的合并行为。杂项 tag（{@code guarded_by_piglins}、{@code dragon_immune} 之类）
     * 与新方块的表现无关，动它们只是徒增风险。
     *
     * <p>不含 {@code mineable/*}：1.16.4 用 Material 判断工具，那套 tag 在这里没有意义。
     */
    private static final String[] TAGS = {
        "walls", "wall_post_override",
        "wooden_fences", "fence_gates", "wooden_fence_gates",
        "wooden_doors", "wooden_trapdoors",
        "slabs", "wooden_slabs", "stairs", "wooden_stairs",
        "leaves", "planks", "logs", "logs_that_burn",
        "buttons", "wooden_buttons", "wooden_pressure_plates",
    };

    public static void main(String[] args) throws Exception {
        if (args.length < 3) {
            System.err.println("用法: MergeBlockTags <server.jar> <已注册方块清单> <1.16.4 tags/blocks 目录>");
            System.exit(2);
        }

        Set<String> registered = new HashSet<>();
        for (String line : Files.readAllLines(Paths.get(args[1]))) {
            String t = line.trim().replace("minecraft:", "");
            if (!t.isEmpty()) registered.add(t);
        }
        Path outDir = Paths.get(args[2]);
        System.out.println("已注册方块白名单：" + registered.size() + " 项");

        int updated = 0, unchanged = 0, absent = 0;
        int totalAdded = 0;

        try (ZipFile jar = new ZipFile(ConvertLootTables.findInner(Paths.get(args[0])).toFile())) {
            for (String tag : TAGS) {
                ZipEntry entry = jar.getEntry("data/minecraft/tags/block/" + tag + ".json");
                if (entry == null) {
                    absent++;
                    continue;
                }

                // 官方值里挑出「已注册」且「本地 tag 还没有」的
                Set<String> officialValues = readValues(
                        new String(jar.getInputStream(entry).readAllBytes(), StandardCharsets.UTF_8));

                Path localPath = outDir.resolve(tag + ".json");
                LinkedHashSet<String> local = new LinkedHashSet<>();
                boolean replace = false;
                if (Files.exists(localPath)) {
                    String localJson = Files.readString(localPath, StandardCharsets.UTF_8);
                    local.addAll(readValues(localJson));
                    JsonObject lo = JsonParser.parseString(localJson).getAsJsonObject();
                    replace = lo.has("replace") && lo.get("replace").getAsBoolean();
                }

                List<String> toAdd = new ArrayList<>();
                for (String v : officialValues) {
                    // tag 引用（#开头）不引入：它可能指向 1.16.4 没有的 tag，会让整张表失效
                    if (v.startsWith("#")) continue;
                    if (!registered.contains(v)) continue;
                    if (local.contains(v)) continue;
                    toAdd.add(v);
                }

                if (toAdd.isEmpty()) {
                    unchanged++;
                    continue;
                }

                local.addAll(toAdd);
                JsonObject out = new JsonObject();
                out.addProperty("replace", replace);
                JsonArray arr = new JsonArray();
                for (String v : local) arr.add(denormalize(v));
                out.add("values", arr);
                Files.createDirectories(localPath.getParent());
                Files.writeString(localPath, GSON.toJson(out) + "\n", StandardCharsets.UTF_8);

                System.out.printf("  %-28s +%d 项（共 %d）%n", tag, toAdd.size(), local.size());
                updated++;
                totalAdded += toAdd.size();
            }
        }

        System.out.printf("%n更新 %d 个 tag，新增 %d 条；%d 个无需改动，%d 个官方不存在%n",
                updated, totalAdded, unchanged, absent);
    }

    /**
     * 读 tag 的 values。元素可能是字符串或 {@code {"id":...}} 对象。
     *
     * <p><b>tag 引用与方块 id 的前缀位置不同</b>：方块是 {@code minecraft:iron_door}，
     * 而对另一个 tag 的引用是 {@code #minecraft:wooden_doors} —— {@code #} 在命名空间<b>之前</b>。
     * 归一化时必须保住这个区别，否则写回时会拼成 {@code minecraft:#wooden_doors}，
     * 1.16.4 解析不了这个引用，于是<b>整张 tag 被丢弃</b>，
     * 最终表现为「数据包出现错误，世界无法加载」。
     */
    static Set<String> readValues(String json) {
        LinkedHashSet<String> out = new LinkedHashSet<>();
        JsonObject root = JsonParser.parseString(json).getAsJsonObject();
        JsonElement values = root.get("values");
        if (values == null || !values.isJsonArray()) return out;
        for (JsonElement e : values.getAsJsonArray()) {
            String v;
            if (e.isJsonObject()) {
                JsonElement id = e.getAsJsonObject().get("id");
                if (id == null) continue;
                v = id.getAsString();
            } else {
                v = e.getAsString();
            }
            out.add(normalize(v));
        }
        return out;
    }

    /** {@code #minecraft:wooden_doors} -> {@code #wooden_doors}；{@code minecraft:iron_door} -> {@code iron_door}。 */
    static String normalize(String v) {
        return v.startsWith("#")
                ? "#" + v.substring(1).replace("minecraft:", "")
                : v.replace("minecraft:", "");
    }

    /** {@link #normalize} 的逆操作，把 {@code #} 放回命名空间之前。 */
    static String denormalize(String v) {
        return v.startsWith("#") ? "#minecraft:" + v.substring(1) : "minecraft:" + v;
    }
}
