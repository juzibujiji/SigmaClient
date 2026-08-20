package net.minecraft.crossversion;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.state.Property;
import net.minecraft.state.StateContainer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * 「1.21.11 方块状态 ID -> 本地方块状态 ID」映射表。
 *
 * <p>这张表存在的理由：连 1.21.11 服务器时，区块数据里的方块状态 ID 是<b>服务器版本</b>的
 * ID。原来的路径是把它交给 ViaBackwards 的降级链，而那条链本来就是为「客户端没有深板岩」
 * 设计的 —— 它会成功地把深板岩降成石头。本客户端已经<b>真的注册了</b>深板岩，所以必须在
 * 进入降级链<b>之前</b>用标识符对上，而不是等降级完再补救。
 *
 * <p>Via 的 {@code MappingData} 帮不上忙：它能反查物品和方块的标识符
 * （{@code getFullItemMappings()} / {@code getFullBlockMappings()}），但<b>方块状态只有
 * ID -> ID，没有标识符</b>。所以标识符这一层必须自己带。
 *
 * <h2>为什么本地 ID 只能运行时算</h2>
 *
 * <p>本地方块状态 ID 由 {@code Blocks} + {@code ModernBlocks} 的<b>注册顺序</b>决定，
 * 每加一个方块后面全部顺移。把本地 ID 烧进资源文件，等于每次加方块都要重新生成，
 * 漏了不会在编译期暴露，只会在游戏里静默错块。所以资源文件
 * （{@value #RESOURCE}）里只有官方那一侧的事实：标识符 + 属性取值，
 * 本地 ID 一律在运行时由 {@link Block#getStateId} 现算。
 *
 * <h2>匹配规则</h2>
 *
 * <p>方块用 {@link ModernRegistry#blockByModernId} 取（它顺手处理了
 * {@code short_grass -> grass} 这类改名），属性<b>按名字严格匹配</b>：
 *
 * <ul>
 *   <li>官方有、本地<b>没有</b>这个属性名 —— 忽略该属性，其余属性照常匹配。结果是官方好几个
 *       状态落到同一个本地状态（部分匹配）。<b>不做模糊匹配</b>：1.16.4 的海泡菜用
 *       {@code pickles}、官方蜡烛用 {@code candles}，靠名字相似去猜只会把错块猜得更隐蔽</li>
 *   <li>属性名对上但官方的<b>取值</b>本地解析不了（枚举少了一项、整数范围更窄）——
 *       该状态<b>不给映射</b>，返回 {@link #NO_MAPPING}</li>
 *   <li>本地<b>多出</b>官方没有的属性 —— 保持本地默认状态里的取值</li>
 *   <li>本地整个方块都没有 —— 该方块全部状态 {@link #NO_MAPPING}</li>
 * </ul>
 *
 * <p><b>宁可不映射也不瞎猜。</b>降级成石头虽然难看，但看得见；静默映射成错块要难排查得多。
 * 所有 {@link #NO_MAPPING} 都让调用方走原来的 Via 兜底。
 *
 * <h2>只对 1.21.11 有效</h2>
 *
 * <p>表的下标是 1.21.11 的状态 ID。{@code ChunkDataInterceptor} 拿到的是
 * <b>Via 管道之前</b>的原始 ID，也就是服务器自己版本的 ID —— 所以调用方必须先确认目标版本
 * 确实是 {@value #SOURCE_VERSION}（见 {@link #SOURCE_VERSION}）。别的版本 ID 不同，
 * 拿这张表去查会静默错块。
 */
public final class ModernBlockStateMap {
    private static final Logger LOGGER = LogManager.getLogger();

    /** 查不到本地对应时的返回值。不用 0 —— 0 是空气的本地状态 ID，会把「查不到」和「真的是空气」混掉。 */
    public static final int NO_MAPPING = -1;

    /**
     * 这张表描述的服务器版本。调用方必须先确认目标版本与它相同
     * （{@code ProtocolVersion.v1_21_11.getName().equals(SOURCE_VERSION)}）。
     */
    public static final String SOURCE_VERSION = "1.21.11";

    private static final String RESOURCE = "/crossversion/blockstate-map-1.21.11.txt";

    private static final int[] EMPTY = new int[0];
    private static final Object LOCK = new Object();

    /** 下标 = 1.21.11 状态 ID，值 = 本地状态 ID 或 {@link #NO_MAPPING}。 */
    private static volatile int[] table;
    private static volatile Report report;
    private static final AtomicBoolean WARMUP_STARTED = new AtomicBoolean();

    private ModernBlockStateMap() {
    }

    // ---------------------------------------------------------------------
    // 公开接口
    // ---------------------------------------------------------------------

    /**
     * 1.21.11 状态 ID -> 本地状态 ID。
     *
     * @return 本地 {@code Block.getStateId} 的结果；本地没有对应的东西时返回
     *         {@link #NO_MAPPING}，调用方应当回退到原来的路径（Via 降级链）
     */
    public static int toNativeId(int modernStateId) {
        if (modernStateId < 0) {
            return NO_MAPPING;
        }
        int[] current = ensureTable();
        return modernStateId < current.length ? current[modernStateId] : NO_MAPPING;
    }

    /**
     * 1.21.11 状态 ID -> 本地 {@link BlockState}。
     *
     * @return 没有对应时返回 {@code null}（不返回石头 —— 兜底策略由调用方决定）
     */
    public static BlockState toBlockState(int modernStateId) {
        int nativeId = toNativeId(modernStateId);
        return nativeId == NO_MAPPING ? null : Block.BLOCK_STATE_IDS.getByValue(nativeId);
    }

    /** 表已经建好了没有。用于日志/调试，不影响 {@link #toNativeId} 的行为。 */
    public static boolean isReady() {
        return table != null;
    }

    /** 表覆盖的 1.21.11 状态 ID 个数（也就是 1.21.11 的方块状态总数）。表没建好返回 0。 */
    public static int coveredStates() {
        int[] current = table;
        return current == null ? 0 : current.length;
    }

    /**
     * 在后台线程把表建好，免得第一个区块包在 Netty 事件循环上付解析代价。幂等。
     *
     * <p>与 {@code ExtendedBlockStateMapper.warmupAsync()} 同一个用法，可以在
     * {@code handleJoinGame} 里并排调用。
     */
    public static void warmupAsync() {
        if (WARMUP_STARTED.getAndSet(true)) {
            return;
        }

        CompletableFuture.runAsync(() -> {
            try {
                ensureTable();
            } catch (Throwable t) {
                // 预热失败不能影响进服；懒加载路径还会再试一次。
                LOGGER.warn("[CrossVersion] 方块状态表预热失败，退回懒加载", t);
            }
        });
    }

    /**
     * 建表统计与「属性对不上」的清单。会触发建表。
     *
     * @return 表建不起来（注册表还没就绪 / 资源缺失）时返回 {@code null}
     */
    public static Report report() {
        ensureTable();
        return report;
    }

    // ---------------------------------------------------------------------
    // 建表
    // ---------------------------------------------------------------------

    private static int[] ensureTable() {
        int[] current = table;
        if (current != null) {
            return current;
        }

        synchronized (LOCK) {
            current = table;
            if (current != null) {
                return current;
            }
            if (!registryReady()) {
                // 注册表还没铺完就建表会把扩展方块全判成「本地没有」，而且那个错表会被缓存。
                // 这里<b>不缓存</b>，返回空表让调用方走兜底，下次再试。
                return EMPTY;
            }

            Builder builder = new Builder();
            int[] built = builder.build();
            if (built == null) {
                return EMPTY;
            }
            report = builder.report;
            table = built;
            LOGGER.info("[CrossVersion] {}", builder.report.summary());
            return built;
        }
    }

    /**
     * 扩展方块注册完了没有。{@code Bootstrap.register()} 里的
     * {@code ModernBlocks.registerBlockStates()} 跑完后，本地方块状态数会超过原版的
     * {@link ModernRegistry#VANILLA_BLOCK_STATE_COUNT}。
     */
    private static boolean registryReady() {
        return Block.BLOCK_STATE_IDS.size() > ModernRegistry.VANILLA_BLOCK_STATE_COUNT;
    }

    /**
     * 资源文件里的一行：一个方块的标识符、首个状态 ID、以及<b>按属性名字典序</b>排好的属性表。
     *
     * <p>字典序不是格式的装饰，是展开顺序的依据：官方 {@code StateDefinition} 与本地
     * {@code StateContainer} 都把属性存在 {@code ImmutableSortedMap} 里做笛卡尔积，
     * 最后一个属性变化最快。生成器已对全部 1166 个方块逐一核对过这条性质。
     */
    private static final class Line {
        final String identifier;
        final int firstStateId;
        final int stateCount;
        final String[] names;
        final String[][] values;

        Line(String identifier, int firstStateId, String[] names, String[][] values) {
            this.identifier = identifier;
            this.firstStateId = firstStateId;
            this.names = names;
            this.values = values;
            int count = 1;
            for (String[] allowed : values) {
                count *= allowed.length;
            }
            this.stateCount = count;
        }
    }

    private static final class Builder {
        private final List<BlockReport> blocks = new ArrayList<>();
        private Report report;
        private int mappedStates;

        private int[] build() {
            long started = System.nanoTime();

            List<Line> lines = readResource();
            if (lines == null || lines.isEmpty()) {
                return null;
            }

            int total = 0;
            for (Line line : lines) {
                total = Math.max(total, line.firstStateId + line.stateCount);
            }

            int[] out = new int[total];
            java.util.Arrays.fill(out, NO_MAPPING);
            for (Line line : lines) {
                resolve(out, line);
            }

            this.report = new Report(lines.size(), total, this.mappedStates, this.blocks,
                    System.nanoTime() - started);
            return out;
        }

        private void resolve(int[] out, Line line) {
            BlockReport blockReport = new BlockReport(line.identifier, line.firstStateId, line.stateCount);
            this.blocks.add(blockReport);

            Block block = ModernRegistry.blockByModernId(line.identifier);
            if (block == null) {
                // 本地根本没这个方块，整段留 NO_MAPPING。
                blockReport.absent = true;
                return;
            }

            int count = line.names.length;
            StateContainer<Block, BlockState> container = block.getStateContainer();
            Property<?>[] localProperties = new Property<?>[count];
            Comparable<?>[][] localValues = new Comparable<?>[count][];

            for (int p = 0; p < count; p++) {
                Property<?> property = container.getProperty(line.names[p]);
                if (property == null) {
                    // 属性名对不上。<b>不做模糊匹配</b>，只登记下来；该属性在匹配时被忽略，
                    // 于是官方好几个状态会落到同一个本地状态。
                    blockReport.addMissingProperty(line.names[p]);
                    continue;
                }

                localProperties[p] = property;
                String[] raw = line.values[p];
                Comparable<?>[] parsed = new Comparable<?>[raw.length];
                for (int v = 0; v < raw.length; v++) {
                    parsed[v] = parseValue(property, raw[v]);
                    if (parsed[v] == null) {
                        blockReport.addUnsupportedValue(line.names[p] + '=' + raw[v]);
                    }
                }
                localValues[p] = parsed;
            }

            Set<String> officialNames = new LinkedHashSet<>(java.util.Arrays.asList(line.names));
            Collection<Property<?>> localAll = container.getProperties();
            for (Property<?> property : localAll) {
                if (!officialNames.contains(property.getName())) {
                    blockReport.addExtraProperty(property.getName());
                }
            }

            BlockState defaultState = block.getDefaultState();
            int[] cursor = new int[count];
            for (int index = 0; index < line.stateCount; index++) {
                BlockState state = defaultState;
                boolean resolved = true;

                for (int p = 0; p < count; p++) {
                    Property<?> property = localProperties[p];
                    if (property == null) {
                        continue;
                    }
                    Comparable<?> value = localValues[p][cursor[p]];
                    if (value == null) {
                        // 官方这个取值本地解析不了 —— 该状态不给映射，绝不退到别的取值上。
                        resolved = false;
                        break;
                    }
                    state = withValue(state, property, value);
                }

                if (resolved) {
                    // 刻意不用 Block.getStateId：它对没登记进 BLOCK_STATE_IDS 的状态返回 0，
                    // 也就是空气 —— 那会把「ModernBlocks 漏登记」变成静默错块。
                    int nativeId = Block.BLOCK_STATE_IDS.getId(state);
                    if (nativeId >= 0) {
                        out[line.firstStateId + index] = nativeId;
                        blockReport.mappedStates++;
                        this.mappedStates++;
                    } else {
                        blockReport.statesWithoutNativeId++;
                    }
                }

                // 末位进位 —— 最后一个属性变化最快。
                for (int p = count - 1; p >= 0; p--) {
                    if (++cursor[p] < line.values[p].length) {
                        break;
                    }
                    cursor[p] = 0;
                }
            }
        }

        private List<Line> readResource() {
            try (InputStream in = ModernBlockStateMap.class.getResourceAsStream(RESOURCE)) {
                if (in == null) {
                    LOGGER.warn("[CrossVersion] 找不到方块状态表 {}，1.21.11 方块状态将全部走 Via 兜底", RESOURCE);
                    return null;
                }

                List<Line> lines = new ArrayList<>(1200);
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                    String raw;
                    while ((raw = reader.readLine()) != null) {
                        String text = raw.trim();
                        if (text.isEmpty() || text.charAt(0) == '#') {
                            continue;
                        }
                        Line line = parseLine(text);
                        if (line != null) {
                            lines.add(line);
                        }
                    }
                }
                return lines;
            } catch (IOException e) {
                LOGGER.warn("[CrossVersion] 读取方块状态表失败，1.21.11 方块状态将全部走 Via 兜底", e);
                return null;
            }
        }

        private Line parseLine(String text) {
            String[] parts = text.split(" ", 3);
            if (parts.length < 2) {
                LOGGER.warn("[CrossVersion] 方块状态表有坏行，已跳过：{}", text);
                return null;
            }

            int firstStateId;
            try {
                firstStateId = Integer.parseInt(parts[1]);
            } catch (NumberFormatException e) {
                LOGGER.warn("[CrossVersion] 方块状态表有坏行，已跳过：{}", text);
                return null;
            }

            if (parts.length == 2) {
                return new Line(parts[0], firstStateId, new String[0], new String[0][]);
            }

            String[] groups = parts[2].split("\\|");
            String[] names = new String[groups.length];
            String[][] values = new String[groups.length][];
            for (int i = 0; i < groups.length; i++) {
                int eq = groups[i].indexOf('=');
                if (eq <= 0) {
                    LOGGER.warn("[CrossVersion] 方块状态表有坏行，已跳过：{}", text);
                    return null;
                }
                names[i] = groups[i].substring(0, eq);
                values[i] = groups[i].substring(eq + 1).split(",");
            }
            return new Line(parts[0], firstStateId, names, values);
        }
    }

    /** 用官方给的字符串取本地属性的取值，本地不支持返回 {@code null}。 */
    private static <T extends Comparable<T>> Comparable<?> parseValue(Property<T> property, String raw) {
        return property.parseValue(raw).orElse(null);
    }

    /** {@code state.with(property, value)}，把通配符捕获收在一处。 */
    private static <T extends Comparable<T>> BlockState withValue(BlockState state, Property<T> property,
            Comparable<?> value) {
        return state.with(property, property.getValueClass().cast(value));
    }

    // ---------------------------------------------------------------------
    // 统计
    // ---------------------------------------------------------------------

    /** 单个方块的匹配情况。 */
    public static final class BlockReport {
        public final String identifier;
        public final int firstStateId;
        /** 官方 1.21.11 的状态数。 */
        public final int stateCount;

        /** 成功映射到本地状态的官方状态数。 */
        public int mappedStates;
        /** 本地根本没有这个方块。 */
        public boolean absent;
        /** 属性全都匹配上了，但组合出的本地状态没登记进 {@code BLOCK_STATE_IDS} —— 这是 bug。 */
        public int statesWithoutNativeId;

        // 这三个清单绝大多数方块都是空的（1166 个里只有 25 个非空），所以延迟分配 ——
        // 每个 ArrayList 连带它的后备数组约 64 字节，1166×3 个空表就是 200 KB 的纯浪费。
        private List<String> missingProperties = Collections.emptyList();
        private List<String> extraProperties = Collections.emptyList();
        private List<String> unsupportedValues = Collections.emptyList();

        BlockReport(String identifier, int firstStateId, int stateCount) {
            this.identifier = identifier;
            this.firstStateId = firstStateId;
            this.stateCount = stateCount;
        }

        /**
         * 官方有、本地没有这个属性名。可能是属性没移植，也可能是本地换了名字
         * （配合 {@link #extraProperties()} 一起看）。
         */
        public List<String> missingProperties() {
            return this.missingProperties;
        }

        /** 本地多出、官方没有的属性名；匹配时保持本地默认取值。 */
        public List<String> extraProperties() {
            return this.extraProperties;
        }

        /** 属性名对上但本地解析不了的官方取值，形如 {@code instrument=zombie}。 */
        public List<String> unsupportedValues() {
            return this.unsupportedValues;
        }

        /** 属性集合与取值两侧完全一致，且每个官方状态各自映射到一个本地状态。 */
        public boolean isExact() {
            return !this.absent
                    && this.missingProperties.isEmpty()
                    && this.extraProperties.isEmpty()
                    && this.unsupportedValues.isEmpty()
                    && this.statesWithoutNativeId == 0
                    && this.mappedStates == this.stateCount;
        }

        /** 有本地方块，但属性两侧对不齐（可能仍然映射了全部状态，只是有折叠）。 */
        public boolean isPartial() {
            return !this.absent && !isExact();
        }

        void addMissingProperty(String name) {
            this.missingProperties = append(this.missingProperties, name);
        }

        void addExtraProperty(String name) {
            this.extraProperties = append(this.extraProperties, name);
        }

        void addUnsupportedValue(String nameAndValue) {
            this.unsupportedValues = append(this.unsupportedValues, nameAndValue);
        }

        private static List<String> append(List<String> list, String value) {
            List<String> target = list.isEmpty() ? new ArrayList<>(2) : list;
            target.add(value);
            return target;
        }
    }

    /** 一次建表的完整统计。 */
    public static final class Report {
        public final int modernBlocks;
        public final int modernStates;
        public final int mappedStates;
        public final int exactBlocks;
        public final int partialBlocks;
        public final int absentBlocks;
        /** 属性名两侧对不上的方块数（{@link BlockReport#missingProperties} 非空）。 */
        public final int propertyNameGapBlocks;
        /** 属性名对上但取值本地不支持的方块数。 */
        public final int valueGapBlocks;
        public final long buildNanos;
        public final List<BlockReport> blocks;

        Report(int modernBlocks, int modernStates, int mappedStates, List<BlockReport> blocks, long buildNanos) {
            this.modernBlocks = modernBlocks;
            this.modernStates = modernStates;
            this.mappedStates = mappedStates;
            this.buildNanos = buildNanos;
            this.blocks = Collections.unmodifiableList(blocks);

            int exact = 0;
            int partial = 0;
            int absent = 0;
            int nameGap = 0;
            int valueGap = 0;
            for (BlockReport block : blocks) {
                if (block.absent) {
                    absent++;
                } else if (block.isExact()) {
                    exact++;
                } else {
                    partial++;
                }
                if (!block.missingProperties.isEmpty()) {
                    nameGap++;
                }
                if (!block.unsupportedValues.isEmpty()) {
                    valueGap++;
                }            }
            this.exactBlocks = exact;
            this.partialBlocks = partial;
            this.absentBlocks = absent;
            this.propertyNameGapBlocks = nameGap;
            this.valueGapBlocks = valueGap;
        }

        public int unmappedStates() {
            return this.modernStates - this.mappedStates;
        }

        public String summary() {
            return String.format(
                    "1.21.11 方块状态表：%d/%d 个状态有本地对应（%.1f%%），方块 完全匹配 %d / 部分匹配 %d / 本地缺失 %d，建表耗时 %.1f ms",
                    this.mappedStates, this.modernStates,
                    this.modernStates == 0 ? 0.0 : 100.0 * this.mappedStates / this.modernStates,
                    this.exactBlocks, this.partialBlocks, this.absentBlocks,
                    this.buildNanos / 1_000_000.0);
        }
    }
}
