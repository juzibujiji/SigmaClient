import com.viaversion.nbt.tag.CompoundTag;
import com.viaversion.viaversion.api.data.MappingDataLoader;
import com.viaversion.viaversion.api.data.Mappings;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * 验证 ViaBackwards 在降级物品时是否把「原始高版本物品 id」写进 NBT，
 * 以及那个 id 能否用该步骤源版本的 identifiers 表还原成标识符。
 *
 * <p>结论支撑 {@code ExtendedItemMapper}：客户端不需要在 Via 之前拦截字节流，
 * 因为 {@code BackwardsItemRewriter#handleItemToClient} /
 * {@code BackwardsStructuredItemRewriter#handleItemToClient} 会执行
 * {@code tag.putInt(nbtTagName("id"), item.identifier())}，随后才
 * {@code setIdentifier(mappedItem.id())}。原始身份因此随物品一起到达客户端。
 *
 * 编译运行：
 * <pre>
 * VIA=~/.m2/.../viaversion-5.9.1-SNAPSHOT.jar
 * VB=~/.m2/.../viabackwards-5.9.1-SNAPSHOT.jar
 * javac -encoding UTF-8 -cp "$VIA:$VB" -d /tmp/probe ItemTagProbe.java
 * java -cp "$VIA:$VB:/tmp/probe" ItemTagProbe [added-items.txt]
 * </pre>
 */
public class ItemTagProbe {

    /** 降级链：{源版本, 目标版本, ViaBackwards 协议类简名}。顺序 = clientbound 执行顺序。 */
    private static final String[][] STEPS = {
        {"1.21.11", "1.21.9", "Protocol1_21_11To1_21_9"},
        {"1.21.9",  "1.21.7", "Protocol1_21_9To1_21_7"},
        {"1.21.7",  "1.21.6", "Protocol1_21_7To1_21_6"},
        {"1.21.6",  "1.21.5", "Protocol1_21_6To1_21_5"},
        {"1.21.5",  "1.21.4", "Protocol1_21_5To1_21_4"},
        {"1.21.4",  "1.21.2", "Protocol1_21_4To1_21_2"},
        {"1.21.2",  "1.21",   "Protocol1_21_2To1_21"},
        {"1.21",    "1.20.5", "Protocol1_21To1_20_5"},
        {"1.20.5",  "1.20.3", "Protocol1_20_5To1_20_3"},
        {"1.20.3",  "1.20.2", "Protocol1_20_3To1_20_2"},
        {"1.20.2",  "1.20",   "Protocol1_20_2To1_20"},
        {"1.20",    "1.19.4", "Protocol1_20To1_19_4"},
        {"1.19.4",  "1.19.3", "Protocol1_19_4To1_19_3"},
        {"1.19.3",  "1.19",   "Protocol1_19_3To1_19_1"},
        {"1.19",    "1.18",   "Protocol1_19To1_18_2"},
        {"1.18",    "1.17",   "Protocol1_18To1_17_1"},
        {"1.17",    "1.16.2", "Protocol1_17To1_16_4"},
    };

    private static final class Step {
        final String from;
        final String to;
        final String protocolName;
        final Mappings items;
        /** itemnames 的 key 集合 = 该步骤会「替换成别的物品并覆盖显示名」的源版本 id。 */
        final Set<Integer> substituted;

        Step(String from, String to, String protocolName, Mappings items, Set<Integer> substituted) {
            this.from = from;
            this.to = to;
            this.protocolName = protocolName;
            this.items = items;
            this.substituted = substituted;
        }

        String tagKey() {
            return "VB|" + protocolName + "|id";
        }
    }

    public static void main(String[] args) throws Exception {
        MappingDataLoader.loadGlobalIdentifiers();
        MappingDataLoader vv = MappingDataLoader.INSTANCE;
        MappingDataLoader vb = new MappingDataLoader(
            Class.forName("com.viaversion.viabackwards.api.ViaBackwardsPlatform"),
            "assets/viabackwards/data/");

        Map<String, List<String>> identifiers = new LinkedHashMap<>();
        List<Step> chain = new ArrayList<>();

        for (String[] s : STEPS) {
            CompoundTag data = vb.loadNBT("mappings-" + s[0] + "to" + s[1] + ".nbt");
            if (data == null) {
                System.out.println("!! 缺少 mappings-" + s[0] + "to" + s[1] + ".nbt");
                continue;
            }
            Mappings items = vb.loadMappings(data, "items");
            Set<Integer> substituted = new HashSet<>();
            CompoundTag itemNames = data.getCompoundTag("itemnames");
            if (itemNames != null) {
                for (Map.Entry<String, ?> e : itemNames.entrySet()) {
                    substituted.add(Integer.parseInt(e.getKey()));
                }
            }
            chain.add(new Step(s[0], s[1], s[2], items, substituted));
            identifiers.computeIfAbsent(s[0], v -> loadIdentifiers(vv, v));
            identifiers.computeIfAbsent(s[1], v -> loadIdentifiers(vv, v));
        }

        List<String> top = identifiers.get(STEPS[0][0]);
        List<String> base = identifiers.get(STEPS[STEPS.length - 1][1]);
        System.out.printf("链长度=%d  1.21.11 物品=%d  1.16.2 物品=%d%n%n",
                chain.size(), top.size(), base.size());

        List<String> probes = args.length > 0
                ? Files.readAllLines(Path.of(args[0]))
                : List.of("mace", "netherite_spear", "copper_pickaxe", "copper_sword",
                          "deepslate", "cherry_planks", "breeze_rod", "trial_key",
                          "copper_bulb", "amethyst_shard", "turtle_scute", "short_grass");

        int recoverable = 0;
        int noTag = 0;
        int dropped = 0;
        int notInTop = 0;
        Map<String, Integer> byStep = new TreeMap<>();
        List<String> noTagList = new ArrayList<>();
        boolean verbose = args.length == 0;

        for (String raw : probes) {
            String probe = raw.trim();
            if (probe.isEmpty() || probe.startsWith("#")) {
                continue;
            }

            int id = top.indexOf(probe);
            if (id < 0) {
                ++notInTop;
                if (verbose) {
                    System.out.printf("%-20s 不在 1.21.11%n", probe);
                }
                continue;
            }

            int cur = id;
            Step tagStep = null;
            int tagId = -1;
            String diedAt = null;

            for (Step step : chain) {
                if (tagStep == null && step.substituted.contains(cur)) {
                    // 这一步会写入 VB|<Protocol>|id = cur（cur 是 step.from 版本的 id）
                    tagStep = step;
                    tagId = cur;
                }
                int next = step.items == null ? cur : step.items.getNewId(cur);
                if (next < 0) {
                    diedAt = step.from + "->" + step.to;
                    break;
                }
                cur = next;
            }

            String fallback = diedAt != null ? "【整包丢弃于 " + diedAt + "】"
                    : (cur >= 0 && cur < base.size() ? base.get(cur) : "越界 id=" + cur);

            if (diedAt != null) {
                ++dropped;
            }

            if (tagStep == null) {
                ++noTag;
                noTagList.add(probe + " -> " + fallback);
                if (verbose) {
                    System.out.printf("%-20s id=%-4d 无 VB 标签, 降级为 %s%n", probe, id, fallback);
                }
                continue;
            }

            List<String> fromIds = identifiers.get(tagStep.from);
            String resolved = tagId < fromIds.size() ? fromIds.get(tagId) : "越界";
            boolean ok = resolved.equals(probe);
            if (ok) {
                ++recoverable;
            }
            byStep.merge(tagStep.from + "->" + tagStep.to, 1, Integer::sum);

            if (verbose) {
                System.out.printf("%-20s id=%-4d 降级为 %-22s 标签 %s=%d -> 还原 %s %s%n",
                        probe, id, fallback, tagStep.tagKey(), tagId, resolved, ok ? "OK" : "!! 不匹配");
            }
        }

        System.out.printf("%n=== 汇总 ===%n可从 NBT 还原标识符: %d%n无标签(纯 id 重映射): %d%n"
                        + "其中整包被丢弃: %d%n不在 1.21.11: %d%n",
                recoverable, noTag, dropped, notInTop);
        System.out.println("\n写入标签的步骤分布:");
        byStep.forEach((k, v) -> System.out.printf("  %-16s %d%n", k, v));
        if (!noTagList.isEmpty()) {
            System.out.println("\n无标签样例(最多 40 条):");
            noTagList.stream().limit(40).forEach(s -> System.out.println("  " + s));
        }
    }

    private static List<String> loadIdentifiers(MappingDataLoader vv, String version) {
        CompoundTag tag = vv.loadNBT("identifiers-" + version + ".nbt");
        if (tag == null) {
            throw new IllegalStateException("缺少 identifiers-" + version + ".nbt");
        }
        return vv.identifiersFromGlobalIds(tag, "items");
    }
}
