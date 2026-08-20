package verify;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.crossversion.ModernBlockStateMap;
import net.minecraft.crossversion.ModernRegistry;
import net.minecraft.util.registry.Bootstrap;
import net.minecraft.util.registry.Registry;

/**
 * 「1.21.11 方块状态 ID -> 本地状态 ID」映射表的自检。
 *
 * <p>在<b>活注册表</b>上跑（{@code Bootstrap.register()} 之后），所以量出来的是真实覆盖率，
 * 不是生成器一侧的估算。产出全部写到 {@code target/crossversion-check/blockstate-map-*.txt}。
 *
 * <p>除了统计，这里还有两道端到端校验，用来抓「表建错位」这类静默错块：
 *
 * <ol>
 *   <li><b>身份校验</b>：每一个有映射的官方状态，反查出来的本地方块标识符必须等于
 *       {@code ModernRegistry.normalize(官方标识符)}。错位一格就会在这里炸出来</li>
 *   <li><b>抽样校验</b>：几个已知答案的状态（深板岩 axis、蜡烛支数、海泡菜）逐一核对属性值，
 *       确认属性是真的对上了，而不是恰好落在同一个 ID 上</li>
 * </ol>
 *
 * <p>用法：{@code java verify.BlockStateMapCheck [repo]}
 */
public class BlockStateMapCheck {
    private static int failures = 0;

    public static void main(String[] args) throws Exception {
        Path repo = Paths.get(args.length > 0 ? args[0] : ".");
        Bootstrap.register();

        long started = System.nanoTime();
        ModernBlockStateMap.Report report = ModernBlockStateMap.report();
        long elapsedNanos = System.nanoTime() - started;

        if (report == null) {
            System.out.println("** 建表失败：report() 返回 null（资源缺失或注册表未就绪）");
            System.exit(1);
        }

        printSummary(report, elapsedNanos);
        checkIdentity(report);
        checkNoCollision(report);
        checkSpots();
        writeReports(repo, report);

        System.out.println(failures == 0 ? "\n=== 全部校验通过 ===" : "\n=== 有 " + failures + " 项校验失败 ===");
        System.exit(failures == 0 ? 0 : 1);
    }

    // ------------------------------------------------------------------
    // 统计
    // ------------------------------------------------------------------

    /**
     * 尽量稳定的已用堆读数曾经放在这里，现在不用了：{@code Bootstrap.register()} 之后堆状态很脏，
     * 前后差值受 GC 与 log4j 首次初始化影响能差两个数量级，得不出关于这张表的任何结论。
     * 常驻量改用 {@link #estimateRetainedBytes} 按对象布局算。
     */
    private static void printSummary(ModernBlockStateMap.Report report, long elapsedNanos) {
        System.out.println("=== 1.21.11 方块状态映射表 ===");
        System.out.printf("官方方块        %d%n", report.modernBlocks);
        System.out.printf("官方方块状态    %d%n", report.modernStates);
        System.out.printf("本地方块        %d（方块状态 %d）%n",
                Registry.BLOCK.keySet().size(), Block.BLOCK_STATE_IDS.size());
        System.out.println();
        System.out.printf("有本地对应      %d 个状态（%.2f%%）%n",
                report.mappedStates, 100.0 * report.mappedStates / report.modernStates);
        System.out.printf("无本地对应      %d 个状态（%.2f%%）%n",
                report.unmappedStates(), 100.0 * report.unmappedStates() / report.modernStates);
        System.out.println();
        System.out.printf("方块 完全匹配   %d%n", report.exactBlocks);
        System.out.printf("方块 部分匹配   %d（其中属性名对不上 %d、取值不支持 %d）%n",
                report.partialBlocks, report.propertyNameGapBlocks, report.valueGapBlocks);
        System.out.printf("方块 本地缺失   %d%n", report.absentBlocks);
        System.out.println();
        System.out.printf("建表耗时        %.1f ms（读资源 + 展开 %d 个状态；不含首次日志初始化）%n",
                report.buildNanos / 1_000_000.0, report.modernStates);
        System.out.printf("首次调用墙钟    %.1f ms（第一次 report() 的全部耗时，含 log4j 首次初始化）%n",
                elapsedNanos / 1_000_000.0);
        System.out.printf("常驻内存        约 %.0f KB（估算，见下）%n", estimateRetainedBytes(report) / 1024.0);
        System.out.printf("  int[%d]       %.1f KB —— 表本身，这是唯一进热路径的东西%n",
                report.modernStates, (16 + 4.0 * report.modernStates) / 1024.0);
        System.out.printf("  逐方块统计    约 %.0f KB（%d 个 BlockReport + 标识符字符串），只在诊断时用%n",
                (report.modernBlocks * 96.0) / 1024.0, report.modernBlocks);
    }

    /**
     * 建表后常驻的字节数估算。
     *
     * <p>不用实测的堆差值当结论：{@code Bootstrap.register()} 之后的堆状态很脏，而且第一次
     * {@code LOGGER.info} 会把 log4j 的格式化栈拉起来，实测差值能有十几 MB，跟这张表无关。
     * 这里按 64 位 JVM + 压缩指针的对象布局逐项算。
     */
    private static long estimateRetainedBytes(ModernBlockStateMap.Report report) {
        long bytes = 16L + 4L * report.modernStates;              // int[] 表本身
        bytes += report.modernBlocks * 48L;                       // BlockReport 对象
        bytes += report.modernBlocks * 48L;                       // 标识符字符串 + 其 byte[]
        bytes += report.modernBlocks * 4L;                        // 承载清单的 ArrayList 后备数组
        for (ModernBlockStateMap.BlockReport block : report.blocks) {
            // 延迟分配：只有真的有内容的方块才付这份钱。
            int lists = (block.missingProperties().isEmpty() ? 0 : 1)
                    + (block.extraProperties().isEmpty() ? 0 : 1)
                    + (block.unsupportedValues().isEmpty() ? 0 : 1);
            bytes += lists * 64L;
        }
        return bytes;
    }

    // ------------------------------------------------------------------
    // 端到端校验
    // ------------------------------------------------------------------

    /** 每个有映射的官方状态，反查的本地方块必须是同一个东西。 */
    private static void checkIdentity(ModernBlockStateMap.Report report) {
        System.out.println("\n=== 身份校验（映射结果反查的方块必须与官方标识符一致）===");
        int checked = 0;
        List<String> wrong = new ArrayList<>();

        for (ModernBlockStateMap.BlockReport block : report.blocks) {
            if (block.absent) {
                continue;
            }
            String expected = ModernRegistry.normalize(block.identifier);
            for (int offset = 0; offset < block.stateCount; offset++) {
                int modernId = block.firstStateId + offset;
                BlockState state = ModernBlockStateMap.toBlockState(modernId);
                if (state == null) {
                    continue;
                }
                checked++;
                String actual = Registry.BLOCK.getKey(state.getBlock()).getPath();
                if (!expected.equals(actual) && wrong.size() < 20) {
                    wrong.add(String.format("  %s 状态 %d -> 本地 %s（应为 %s）",
                            block.identifier, modernId, actual, expected));
                }
            }
        }

        System.out.printf("核对了 %d 个有映射的状态%n", checked);
        if (wrong.isEmpty()) {
            System.out.println("全部一致");
        } else {
            wrong.forEach(System.out::println);
            failures++;
        }
    }

    /**
     * 两个不同的官方方块不能落到同一个本地方块。
     *
     * <p>{@code ModernRegistry.normalize} 会把 {@code short_grass -> grass}、
     * {@code iron_chain -> chain} 这类改名还原。只要官方那边<b>同时</b>存在改名前后两个名字，
     * 归一化就会把它们撞到一起，其中一个必然是错块 —— 而且是最难查的那种错块。
     * 现在 1.21.11 没有这种情况（官方只有 {@code short_grass} / {@code iron_chain}，
     * 没有 {@code grass} / {@code chain}），但以后往 {@code RENAMED_TO_LEGACY} 加条目时
     * 这道检查会挡住。
     */
    private static void checkNoCollision(ModernBlockStateMap.Report report) {
        System.out.println("\n=== 归一化撞名校验 ===");
        Map<String, List<String>> byLocal = new LinkedHashMap<>();
        for (ModernBlockStateMap.BlockReport block : report.blocks) {
            if (block.absent) {
                continue;
            }
            byLocal.computeIfAbsent(ModernRegistry.normalize(block.identifier), key -> new ArrayList<>())
                    .add(block.identifier);
        }

        int collisions = 0;
        for (Map.Entry<String, List<String>> entry : byLocal.entrySet()) {
            if (entry.getValue().size() > 1) {
                System.out.printf("  ** 本地 %s 同时被 %s 映射到%n", entry.getKey(), entry.getValue());
                collisions++;
            }
        }
        if (collisions == 0) {
            System.out.printf("%d 个官方方块一对一落到 %d 个本地方块，无撞名%n",
                    report.modernBlocks - report.absentBlocks, byLocal.size());
        } else {
            failures++;
        }
    }

    /** 几个已知答案的抽样，确认属性是真的对上而不是碰巧同 ID。 */
    private static void checkSpots() {
        System.out.println("\n=== 抽样校验 ===");
        // 官方 blocks.json：deepslate 首个状态 27721，属性 axis=x,y,z（字典序只有一个属性）。
        spot("deepslate", 27721, "deepslate", "axis", "x");
        spot("deepslate", 27722, "deepslate", "axis", "y");
        spot("deepslate", 27723, "deepslate", "axis", "z");
        // candle 首个状态 22894，属性字典序 candles=1,2,3,4|lit=true,false|waterlogged=true,false，
        // 末位变化最快 -> +0 是 (1,true,true)，+5 是 (2,true,false)。
        spot("candle", 22894, "candle", "candles", "1");
        spot("candle", 22899, "candle", "candles", "2");
        // sea_pickle 首个状态 15065，属性 pickles=1,2,3,4|waterlogged=true,false。
        // 1.16.4 原生就有这个方块，属性名两侧都是 pickles。
        spot("sea_pickle", 15065, "sea_pickle", "pickles", "1");
        spot("sea_pickle", 15067, "sea_pickle", "pickles", "2");
        // 改名项：官方 short_grass -> 本地 grass。
        spot("short_grass", -1, "grass", null, null);
    }

    private static void spot(String label, int modernId, String expectedBlock, String property, String value) {
        if (modernId < 0) {
            // 只验方块层面的改名映射。
            Block block = ModernRegistry.blockByModernId(label);
            String actual = block == null ? "(未找到)" : Registry.BLOCK.getKey(block).getPath();
            boolean ok = expectedBlock.equals(actual);
            System.out.printf("  %-14s -> %-24s %s%n", label, actual, ok ? "OK" : "** 应为 " + expectedBlock);
            if (!ok) {
                failures++;
            }
            return;
        }

        BlockState state = ModernBlockStateMap.toBlockState(modernId);
        if (state == null) {
            System.out.printf("  %-14s 状态 %-6d -> (无映射) ** 应为 %s%n", label, modernId, expectedBlock);
            failures++;
            return;
        }

        String actualBlock = Registry.BLOCK.getKey(state.getBlock()).getPath();
        String actualValue = propertyValue(state, property);
        boolean ok = expectedBlock.equals(actualBlock) && (value == null || value.equals(actualValue));
        System.out.printf("  %-14s 状态 %-6d -> %s[%s=%s] 本地 id=%d %s%n",
                label, modernId, actualBlock, property, actualValue,
                ModernBlockStateMap.toNativeId(modernId),
                ok ? "OK" : "** 应为 " + expectedBlock + "[" + property + "=" + value + "]");
        if (!ok) {
            failures++;
        }
    }

    private static String propertyValue(BlockState state, String propertyName) {
        if (propertyName == null) {
            return "-";
        }
        net.minecraft.state.Property<?> property = state.getBlock().getStateContainer().getProperty(propertyName);
        return property == null ? "(本地无此属性)" : name(state, property);
    }

    private static <T extends Comparable<T>> String name(BlockState state, net.minecraft.state.Property<T> property) {
        return property.getName(state.get(property));
    }

    // ------------------------------------------------------------------
    // 清单输出
    // ------------------------------------------------------------------

    private static void writeReports(Path repo, ModernBlockStateMap.Report report) throws Exception {
        Path dir = repo.resolve("target/crossversion-check");
        Files.createDirectories(dir);

        List<String> exact = new ArrayList<>();
        List<String> absent = new ArrayList<>();
        List<String> partial = new ArrayList<>();
        List<String> nameGaps = new ArrayList<>();

        nameGaps.add("# 属性名两侧对不上的方块。每行：标识符  官方独有属性  本地独有属性  本地不支持的取值");
        nameGaps.add("# 「官方独有」而「本地独有」也非空 -> 很可能是本地把属性起了别的名字（如 candles / pickles），");
        nameGaps.add("# 应当改本地属性名而不是加模糊匹配。两者只有一侧非空 -> 那个属性单纯没移植。");
        partial.add("# 部分匹配的方块。official=官方状态数 mapped=有本地对应的状态数 distinct=落到的不同本地状态数");
        partial.add("# distinct < official 说明官方多个状态被折叠成同一个本地状态（缺属性导致）。");

        for (ModernBlockStateMap.BlockReport block : report.blocks) {
            if (block.absent) {
                absent.add(String.format("%s  官方状态=%d  首个官方状态ID=%d",
                        block.identifier, block.stateCount, block.firstStateId));
                continue;
            }
            if (block.isExact()) {
                exact.add(String.format("%s  状态=%d", block.identifier, block.stateCount));
                continue;
            }

            partial.add(String.format("%s  official=%d mapped=%d distinct=%d%s%s%s%s",
                    block.identifier, block.stateCount, block.mappedStates, distinctNativeIds(block),
                    block.missingProperties().isEmpty() ? "" : "  官方独有属性=" + block.missingProperties(),
                    block.extraProperties().isEmpty() ? "" : "  本地独有属性=" + block.extraProperties(),
                    block.unsupportedValues().isEmpty() ? "" : "  本地不支持取值=" + block.unsupportedValues(),
                    block.statesWithoutNativeId == 0 ? ""
                            : "  ** 有 " + block.statesWithoutNativeId + " 个状态没登记进 BLOCK_STATE_IDS"));

            if (!block.missingProperties().isEmpty() || !block.unsupportedValues().isEmpty()) {
                nameGaps.add(String.format("%-34s 官方独有=%-40s 本地独有=%-24s 不支持取值=%s",
                        block.identifier, block.missingProperties(), block.extraProperties(),
                        block.unsupportedValues()));
            }

            if (block.statesWithoutNativeId > 0) {
                System.out.printf("** %s 有 %d 个状态没登记进 BLOCK_STATE_IDS —— ModernBlocks 漏登记%n",
                        block.identifier, block.statesWithoutNativeId);
                failures++;
            }
        }

        List<String> summary = new ArrayList<>();
        summary.add(report.summary());
        summary.add("");
        summary.add("官方方块 " + report.modernBlocks + "，官方方块状态 " + report.modernStates);
        summary.add("有本地对应的状态 " + report.mappedStates + "，无对应 " + report.unmappedStates());
        summary.add("完全匹配方块 " + report.exactBlocks + "，部分匹配 " + report.partialBlocks
                + "，本地缺失 " + report.absentBlocks);
        summary.add("属性名对不上的方块 " + report.propertyNameGapBlocks
                + "，取值不支持的方块 " + report.valueGapBlocks);

        write(dir.resolve("blockstate-map-summary.txt"), summary);
        write(dir.resolve("blockstate-map-exact.txt"), exact);
        write(dir.resolve("blockstate-map-partial.txt"), partial);
        write(dir.resolve("blockstate-map-absent.txt"), absent);
        write(dir.resolve("blockstate-map-property-gaps.txt"), nameGaps);

        System.out.println("\n清单已写到 target/crossversion-check/blockstate-map-*.txt");
    }

    private static int distinctNativeIds(ModernBlockStateMap.BlockReport block) {
        Set<Integer> ids = new HashSet<>();
        for (int offset = 0; offset < block.stateCount; offset++) {
            int nativeId = ModernBlockStateMap.toNativeId(block.firstStateId + offset);
            if (nativeId != ModernBlockStateMap.NO_MAPPING) {
                ids.add(nativeId);
            }
        }
        return ids.size();
    }

    private static void write(Path path, List<String> lines) throws Exception {
        Files.write(path, lines, StandardCharsets.UTF_8);
    }
}
