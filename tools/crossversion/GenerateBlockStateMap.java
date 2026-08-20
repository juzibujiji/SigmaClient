package verify;

import com.google.gson.*;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/**
 * 生成「1.21.11 方块状态 ID -> 方块标识符 + 属性取值」的紧凑资源表。
 *
 * <p>产物：{@code src/main/resources/crossversion/blockstate-map-1.21.11.txt}，
 * 由 {@code net.minecraft.crossversion.ModernBlockStateMap} 在运行时展开成
 * {@code int[]}（下标 = 1.21.11 状态 ID，值 = 本地 {@code Block.getStateId}）。
 *
 * <p><b>为什么只存标识符 + 属性表，不存本地 ID。</b>本地方块状态 ID 由注册顺序决定
 * （见 {@code ModernRegistry} 的红线说明），任何一次 {@code ModernBlocks} 变动都会让
 * 硬编码的本地 ID 全部作废，而且不会在编译期暴露。所以资源文件里<b>只放官方那一侧</b>
 * 的事实，本地一侧永远运行时算。
 *
 * <h2>格式</h2>
 *
 * <p>一行一个方块（1166 行、71 KB），而不是一行一个状态（29671 行、2.2 MB）：
 *
 * <pre>
 * acacia_button 10560 face=floor,wall,ceiling|facing=north,south,west,east|powered=true,false
 * &lt;标识符&gt; &lt;首个状态 ID&gt; [&lt;属性&gt;=&lt;取值,取值,...&gt;|...]
 * </pre>
 *
 * <p>能这么压缩是因为官方 {@code StateDefinition}（1.21.11
 * {@code world/level/block/state/StateDefinition.java:33-66}）的两条硬性质：
 *
 * <ol>
 *   <li>{@code propertiesByName} 是 {@code ImmutableSortedMap}，所以属性的<b>迭代顺序
 *       是属性名的字典序</b>，与源码里 {@code createBlockStateDefinition} 的书写顺序无关</li>
 *   <li>状态列表由属性按该顺序做笛卡尔积生成（{@code stream.flatMap} 逐个属性展开），
 *       所以<b>最后一个属性变化最快</b>，且一个方块的状态 ID 是连续的一段</li>
 * </ol>
 *
 * <p>本客户端的 {@code net.minecraft.state.StateContainer:33-60} 是同一段逻辑
 * （同样 {@code ImmutableSortedMap} + 同样的 flatMap 顺序），所以两侧展开顺序一致。
 *
 * <p><b>这两条性质是被数据验证过的，不是推断</b>：本生成器会对 blocks.json 里全部 1166 个
 * 方块逐一核对「按属性名字典序做笛卡尔积得到的元组序列」是否与 {@code states} 数组按 ID
 * 升序排列后逐项相等，不相等就直接失败退出而不是悄悄写出一份错表。
 * 注意 blocks.json 里 {@code properties} 字段的<b>键顺序不可信</b> —— 有 12 个方块
 * （箱子家族、{@code piston_head}、{@code moving_piston}）的键顺序是源码书写顺序而非字典序，
 * 照着它展开会错位。
 */
public class GenerateBlockStateMap {
    /** 官方数据的版本号，写进资源文件头，运行时会校验。 */
    private static final String SOURCE_VERSION = "1.21.11";

    private static final String RESOURCE_PATH = "src/main/resources/crossversion/blockstate-map-1.21.11.txt";
    private static final String REPORT_DIR = "target/crossversion-check";

    public static void main(String[] args) throws Exception {
        Path repo = Paths.get(args.length > 0 ? args[0] : ".");
        Path blocksJson = args.length > 1
                ? Paths.get(args[1])
                : repo.resolve("1.21.11/reports/blocks.json");

        if (!Files.isReadable(blocksJson)) {
            System.err.println("找不到 blocks.json: " + blocksJson.toAbsolutePath());
            System.err.println("用法: GenerateBlockStateMap <repo> [blocks.json 路径]");
            System.exit(2);
        }

        JsonObject root = JsonParser.parseString(
                Files.readString(blocksJson, StandardCharsets.UTF_8)).getAsJsonObject();

        List<BlockEntry> entries = new ArrayList<>(root.size());
        for (Map.Entry<String, JsonElement> e : root.entrySet()) {
            entries.add(parse(e.getKey(), e.getValue().getAsJsonObject()));
        }
        entries.sort(Comparator.comparingInt(entry -> entry.firstStateId));

        List<String> problems = validate(entries);
        if (!problems.isEmpty()) {
            System.err.println("=== blocks.json 与假设不符，拒绝生成 ===");
            problems.forEach(System.err::println);
            System.exit(1);
        }

        write(repo, entries);
    }

    // ------------------------------------------------------------------
    // 解析
    // ------------------------------------------------------------------

    private static final class BlockEntry {
        final String identifier;
        final int firstStateId;
        final int stateCount;
        /** 属性名，已按字典序排好 —— 就是官方与本地共同的笛卡尔积展开顺序。 */
        final String[] propertyNames;
        /** 与 {@link #propertyNames} 同序的取值表。 */
        final String[][] propertyValues;
        /** blocks.json 原样的 states 数组，按 ID 升序，仅用于校验。 */
        final List<String[]> statesByIdAsc;

        BlockEntry(String identifier, int firstStateId, int stateCount,
                String[] propertyNames, String[][] propertyValues, List<String[]> statesByIdAsc) {
            this.identifier = identifier;
            this.firstStateId = firstStateId;
            this.stateCount = stateCount;
            this.propertyNames = propertyNames;
            this.propertyValues = propertyValues;
            this.statesByIdAsc = statesByIdAsc;
        }
    }

    private static BlockEntry parse(String registryName, JsonObject block) {
        String identifier = registryName.startsWith("minecraft:")
                ? registryName.substring("minecraft:".length())
                : registryName;

        // 属性名一律按字典序，不信 JSON 的键顺序（见类注释）。
        String[] names;
        String[][] values;
        if (block.has("properties")) {
            JsonObject properties = block.getAsJsonObject("properties");
            List<String> sorted = new ArrayList<>(properties.keySet());
            Collections.sort(sorted);
            names = sorted.toArray(new String[0]);
            values = new String[names.length][];
            for (int i = 0; i < names.length; i++) {
                JsonArray allowed = properties.getAsJsonArray(names[i]);
                values[i] = new String[allowed.size()];
                for (int v = 0; v < allowed.size(); v++) {
                    values[i][v] = allowed.get(v).getAsString();
                }
            }
        } else {
            names = new String[0];
            values = new String[0][];
        }

        JsonArray states = block.getAsJsonArray("states");
        int[] ids = new int[states.size()];
        Map<Integer, String[]> tuples = new TreeMap<>();
        for (int i = 0; i < states.size(); i++) {
            JsonObject state = states.get(i).getAsJsonObject();
            int id = state.get("id").getAsInt();
            ids[i] = id;
            String[] tuple = new String[names.length];
            JsonObject stateProps = state.has("properties")
                    ? state.getAsJsonObject("properties")
                    : new JsonObject();
            for (int p = 0; p < names.length; p++) {
                JsonElement value = stateProps.get(names[p]);
                tuple[p] = value == null ? null : value.getAsString();
            }
            tuples.put(id, tuple);
        }

        int min = Integer.MAX_VALUE;
        for (int id : ids) {
            min = Math.min(min, id);
        }
        return new BlockEntry(identifier, min, ids.length, names, values,
                new ArrayList<>(tuples.values()));
    }

    // ------------------------------------------------------------------
    // 校验：全部靠数据说话，任何一项不成立就不生成
    // ------------------------------------------------------------------

    private static List<String> validate(List<BlockEntry> entries) {
        List<String> problems = new ArrayList<>();
        int expectedNextId = 0;

        for (BlockEntry entry : entries) {
            // 1) 状态 ID 必须是连续一段，且整个 ID 空间无洞、从 0 开始。
            if (entry.firstStateId != expectedNextId) {
                problems.add(String.format("%s: 首个状态 ID %d，但上一个方块结束在 %d（ID 空间有洞或重叠）",
                        entry.identifier, entry.firstStateId, expectedNextId));
            }
            expectedNextId = entry.firstStateId + entry.stateCount;

            // 2) 笛卡尔积规模必须等于状态数。
            long product = 1;
            for (String[] values : entry.propertyValues) {
                product *= values.length;
            }
            if (product != entry.stateCount) {
                problems.add(String.format("%s: 属性笛卡尔积 %d != 状态数 %d",
                        entry.identifier, product, entry.stateCount));
                continue;
            }

            // 3) 按字典序展开的元组序列必须与 ID 升序的 states 逐项相同。
            //    这是「最后一个属性变化最快」这条性质的直接检验。
            String mismatch = firstExpansionMismatch(entry);
            if (mismatch != null) {
                problems.add(entry.identifier + ": " + mismatch);
            }
        }

        if (expectedNextId <= 0) {
            problems.add("没有解析到任何状态");
        }
        return problems;
    }

    /** 返回第一处展开不一致的描述，全部一致返回 {@code null}。 */
    private static String firstExpansionMismatch(BlockEntry entry) {
        int[] cursor = new int[entry.propertyNames.length];
        for (int index = 0; index < entry.stateCount; index++) {
            String[] actual = entry.statesByIdAsc.get(index);
            for (int p = 0; p < cursor.length; p++) {
                String expected = entry.propertyValues[p][cursor[p]];
                if (!expected.equals(actual[p])) {
                    return String.format("第 %d 个状态（ID %d）的 %s 期望 %s，实际 %s",
                            index, entry.firstStateId + index, entry.propertyNames[p], expected, actual[p]);
                }
            }
            // 末位进位 —— 最后一个属性变化最快。
            for (int p = cursor.length - 1; p >= 0; p--) {
                if (++cursor[p] < entry.propertyValues[p].length) {
                    break;
                }
                cursor[p] = 0;
            }
        }
        return null;
    }

    // ------------------------------------------------------------------
    // 输出
    // ------------------------------------------------------------------

    private static void write(Path repo, List<BlockEntry> entries) throws Exception {
        int totalStates = 0;
        int withProperties = 0;
        int maxProperties = 0;
        int maxStates = 0;
        String widest = "";
        Set<String> propertyNames = new TreeSet<>();

        StringBuilder out = new StringBuilder(96 * 1024);
        out.append("# 1.21.11 方块状态表 —— 由 tools/crossversion/GenerateBlockStateMap.java 生成，请勿手改。\n");
        out.append("# version ").append(SOURCE_VERSION).append('\n');
        out.append("# 格式：<标识符> <首个状态ID> [<属性>=<取值,...>|<属性>=<取值,...>]\n");
        out.append("# 属性已按属性名字典序排列 —— 官方 StateDefinition 与本地 StateContainer 都用\n");
        out.append("# ImmutableSortedMap 迭代属性做笛卡尔积，最后一个属性变化最快。展开时必须保持此序。\n");

        for (BlockEntry entry : entries) {
            totalStates += entry.stateCount;
            if (entry.propertyNames.length > 0) {
                withProperties++;
            }
            maxProperties = Math.max(maxProperties, entry.propertyNames.length);
            if (entry.stateCount > maxStates) {
                maxStates = entry.stateCount;
                widest = entry.identifier;
            }
            propertyNames.addAll(Arrays.asList(entry.propertyNames));

            out.append(entry.identifier).append(' ').append(entry.firstStateId);
            for (int p = 0; p < entry.propertyNames.length; p++) {
                out.append(p == 0 ? ' ' : '|').append(entry.propertyNames[p]).append('=');
                String[] values = entry.propertyValues[p];
                for (int v = 0; v < values.length; v++) {
                    if (v > 0) {
                        out.append(',');
                    }
                    out.append(values[v]);
                }
            }
            out.append('\n');
        }

        Path resource = repo.resolve(RESOURCE_PATH);
        Files.createDirectories(resource.getParent());
        Files.writeString(resource, out.toString(), StandardCharsets.UTF_8);

        Path reportDir = repo.resolve(REPORT_DIR);
        Files.createDirectories(reportDir);
        List<String> propertyReport = new ArrayList<>();
        propertyReport.add("# 1.21.11 全部方块状态属性名（" + propertyNames.size() + " 个）");
        propertyReport.addAll(propertyNames);
        Files.write(reportDir.resolve("blockstate-map-properties.txt"), propertyReport);

        System.out.println("=== 1.21.11 方块状态表 ===");
        System.out.printf("方块        %d（其中 %d 个带状态属性）%n", entries.size(), withProperties);
        System.out.printf("方块状态    %d（ID 0-%d，连续无洞）%n", totalStates, totalStates - 1);
        System.out.printf("属性名      %d 个不同的名字%n", propertyNames.size());
        System.out.printf("状态最多    %s（%d 个状态）；单方块最多 %d 个属性%n", widest, maxStates, maxProperties);
        System.out.printf("资源文件    %s（%.1f KB / %d 行数据）%n",
                RESOURCE_PATH, Files.size(resource) / 1024.0, entries.size());
        System.out.println("校验        全部方块的状态 ID 连续、笛卡尔积规模与展开顺序均与官方数据一致");
    }
}
