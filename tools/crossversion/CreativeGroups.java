package verify;

import com.google.gson.*;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.regex.*;

/**
 * 扩展物品的创造栏分类，全部由官方数据推导。
 *
 * <p>两条依据，按可靠性排序：
 *
 * <ol>
 *   <li><b>类型投票</b>：按 {@code blocks.json} 的 {@code definition.type} 找 1.16.4 同类型
 *       方块，取它们的 {@code ItemGroup}。原版 137 种类型里 136 种投票完全一致，
 *       所以这条只要有结果就非常可信 —— 门全是 REDSTONE、树叶全是 DECORATIONS。
 *       <p>泛型 {@code block}（也就是 {@code Block} 基类本身）也算票。它的 132 票里
 *       128 张投 BUILDING_BLOCKS，看着像「多数原版素方块是建筑方块」这种没信息量的统计，
 *       但留一验证显示<b>算它更准</b>（95.81% 对 95.49%）：木板的类型就是泛型
 *       {@code block}，不算的话新木种的 {@code mangrove_planks} 会被 building 栏里紧挨着的
 *       上一个木种的 {@code dark_oak_button}（1.16.4 属 REDSTONE）带进红石栏。</li>
 *   <li><b>邻居窗口投票</b>：官方把功能相近的东西排在一起，所以在
 *       {@code official-creative-tabs-1.21.11.csv} 里该物品所属的每个 tab 中,
 *       向前后各取最近 {@value #WINDOW} 个「1.16.4 已存在」的物品，
 *       按<b>实际距离</b>加权（权重 {@code 1/距离}）投票。
 *       <p>只取最近一个是不够的：{@code sculk_sensor} 在 natural 和 redstone 两个 tab
 *       里最近邻距离都是 1，靠单个邻居无法定夺，会随 tab 遍历顺序摇摆。
 *       <p>权重也不能只看「第几个原版邻居」而忽略实际距离：{@code copper_bulb} 前面连着
 *       一串 1.17+ 的新方块，11 格外的 {@code smooth_quartz_slab} 同样算「第 1 个原版邻居」，
 *       会把紧挨着它的 {@code redstone_lamp} 压下去。</li>
 * </ol>
 *
 * <p>两条都给不出结果才兜底，且会在报告里单独列出，不再靠 {@code id.contains("moss")}
 * 之类的名字规则 —— 那种规则已经误伤过幽匿块和苔藓块（见方案文档 §9.20）。
 *
 * <p><b>这套参数是量出来的，不是拍的。</b>{@code EvalGroups} 拿 1.16.4 原版 954 个
 * 已知分组的物品做留一验证（把某个原版物品当成未知，只用其他原版物品去预测它）：
 * 本配置 95.81%，纯邻居法 90.99%，只看 tab 众数 68.45%。
 * 剩下 40 个错例都是 1.16.4 自己的任意分配（{@code note_block} 在 REDSTONE 而
 * {@code bookshelf} 在 BUILDING_BLOCKS、{@code glass_bottle} 在 BREWING、
 * {@code elytra} 在 TRANSPORTATION），任何数据驱动的方法都还原不了。
 * 改参数前先跑 {@code EvalGroups} 看准确率是升还是降。
 */
public final class CreativeGroups {

    /** 邻居窗口大小：每个 tab 的每个方向各取这么多个「原版已存在」的物品。留一验证选出。 */
    public static final int WINDOW = 8;

    private final Map<String, List<String>> tabs;
    private final Map<String, String> vanillaGroup;
    private final Map<String, String> typeGroup;
    private final JsonObject blocksJson;
    /** 氧化铜家族：成员标识符 -> 同家族全部成员。取自官方两张映射表，见 ExtractBlockFamilies。 */
    private final Map<String, List<String>> family;

    private CreativeGroups(Map<String, List<String>> tabs, Map<String, String> vanillaGroup,
                           Map<String, String> typeGroup, JsonObject blocksJson,
                           Map<String, List<String>> family) {
        this.tabs = tabs;
        this.vanillaGroup = vanillaGroup;
        this.typeGroup = typeGroup;
        this.blocksJson = blocksJson;
        this.family = family;
    }

    /** 一次判定的结果，带依据，便于在报告里逐条复核。 */
    public static final class Decision {
        public final String group;
        /** {@code 类型投票} / {@code 邻居窗口} / {@code 兜底}。 */
        public final String basis;
        public final String detail;

        Decision(String group, String basis, String detail) {
            this.group = group;
            this.basis = basis;
            this.detail = detail;
        }

        @Override
        public String toString() {
            return group + "（" + basis + "：" + detail + "）";
        }
    }

    // ------------------------------------------------------------------
    // 判定
    // ------------------------------------------------------------------

    /**
     * 判定一个扩展物品的创造栏分组。{@code fallback} 是两条依据都失效时用的值。
     *
     * <p>属于氧化铜家族的按<b>整个家族</b>判定，全部 8 个成员得到同一个分组。
     * 逐个判定会把同一种方块劈成两半：{@code waxed_copper_bars} 的类型是普通
     * {@code iron_bars}（原版 DECORATIONS），而未打蜡的 {@code copper_bars} 没有原版同类，
     * 只能靠邻居，会落到 BUILDING_BLOCKS —— 玩家看到的就是铜栏杆和打蜡铜栏杆分在两栏。
     */
    public Decision decide(String id, String fallback) {
        List<String> kin = family.get(id);
        if (kin != null) {
            Decision d = decideFamily(kin);
            if (d != null) return d;
        }

        Decision byType = byType(id);
        if (byType != null) return byType;

        Decision bySuffix = bySuffix(id);
        if (bySuffix != null) return bySuffix;

        Decision byPureTab = byPureTab(id);
        if (byPureTab != null) return byPureTab;

        Decision byNeighbour = byNeighbour(id);
        if (byNeighbour != null) return byNeighbour;

        return new Decision(fallback, "兜底", "既无同类型原版方块，也不在任何官方 tab 里");
    }

    /** 原版同后缀的物品至少要有这么多个才采信，免得拿一两个孤例定案。 */
    public static final int MIN_SUFFIX_SIBLINGS = 3;

    /** 同后缀原版物品里，得票最多的分组至少要占这个比例，否则这条后缀太杂，不采信。 */
    public static final double MIN_SUFFIX_PURITY = 2.0 / 3.0;

    /**
     * 命中的后缀至少要覆盖标识符的这个比例（按下划线分段算），否则算「蹭到一个词」。
     *
     * <p>没有这道闸，{@code music_disc_creator_music_box} 会一路缩到 {@code _box}，
     * 撞上 17 个潜影盒（清一色 DECORATIONS，纯度还是 100%），被判成装饰品。
     * 5 段的标识符只对上 1 段，说明匹配到的是个巧合而不是同一类东西。
     * 反过来 {@code copper_axe} 用 {@code _axe} 对上 1/2 段，是实打实的同类。
     */
    public static final double MIN_SUFFIX_COVERAGE = 0.5;

    /**
     * 原版同后缀投票：拿 {@code id} 的后缀去 1.16.4 原版里找同后缀的物品，取众数。
     *
     * <p>这条专治<b>纯物品</b>。{@link #byType} 只对方块物品有效（类型取自 {@code blocks.json}），
     * 纯物品以前会直接落到邻居窗口，而邻居是「官方 tab 里挨着谁」——
     * 挨错一个就判错一个。实测过的两个误判：
     *
     * <table>
     *   <tr><th>物品</th><th>邻居法判成</th><th>原因</th><th>本条判成</th></tr>
     *   <tr><td>{@code field_masoned_banner_pattern}</td><td>BREWING</td>
     *       <td>官方 ingredients 栏里它紧挨着 {@code phantom_membrane}</td>
     *       <td>MISC（原版 6 个 {@code _banner_pattern} 全是 MISC）</td></tr>
     *   <tr><td>{@code copper_axe}</td><td>—</td>
     *       <td>官方把斧头放进 combat 栏，邻居全是武器</td>
     *       <td>TOOLS（原版 5 把 {@code _axe} 全是 TOOLS）</td></tr>
     * </table>
     *
     * <p>后缀<b>从长到短</b>试，否则 {@code copper_horse_armor} 会被 {@code _armor} 抢在
     * {@code _horse_armor} 之前。要求至少 {@link #MIN_SUFFIX_SIBLINGS} 个同后缀原版物品、
     * 且众数占比不低于 {@link #MIN_SUFFIX_PURITY} —— 达不到就说明这条后缀本身不成家族
     * （比如 {@code _block}），让位给后面的依据。
     */
    public Decision bySuffix(String id) {
        String[] tok = id.split("_");
        for (int start = 1; start < tok.length; start++) {
            int covered = tok.length - start;
            if ((double) covered / tok.length < MIN_SUFFIX_COVERAGE) break;

            String suffix = "_" + String.join("_", Arrays.copyOfRange(tok, start, tok.length));

            Map<String, Integer> votes = new TreeMap<>();
            for (Map.Entry<String, String> e : vanillaGroup.entrySet()) {
                if (e.getKey().equals(id) || !e.getKey().endsWith(suffix)) continue;
                votes.merge(normalizeGroup(e.getValue()), 1, Integer::sum);
            }

            int total = votes.values().stream().mapToInt(Integer::intValue).sum();
            if (total < MIN_SUFFIX_SIBLINGS) continue;

            Map.Entry<String, Integer> best = votes.entrySet().stream()
                    .max(Comparator.<Map.Entry<String, Integer>>comparingInt(Map.Entry::getValue)
                            .thenComparing(Map.Entry::getKey, Comparator.reverseOrder()))
                    .orElseThrow();
            if ((double) best.getValue() / total < MIN_SUFFIX_PURITY) continue;

            return new Decision(best.getKey(), "原版同后缀",
                    String.format(Locale.ROOT, "%s 共 %d 个原版物品，得 %s", suffix, total, votes));
        }
        return null;
    }

    /**
     * 一个官方 tab 里的原版物品至少要有这么大比例落在同一个 {@code ItemGroup}，
     * 这个 tab 的众数才可信。
     *
     * <p>方案文档记着「只看 tab 众数准确率 68.45%」，一直被当成 tab 不可用的证据。
     * 但那是把 12 个 tab 一视同仁算的平均数。{@code TabPurity} 分 tab 量出来是这样
     * （原版物品数 / 纯度 / 众数）：
     *
     * <pre>
     * spawn        64  100.0%  MISC              ← 可用
     * functional  125   88.8%  DECORATIONS       ← 可用
     * food         43   86.0%  FOOD              ← 可用
     * ingredients  86   82.6%  MISC              ← 可用
     * combat       54   72.2%  COMBAT            ← 可用（杂质是 6 把斧头，1.16.4 归 TOOLS）
     * building    247   69.2%  BUILDING_BLOCKS   ← 不可用，49 个在 1.16.4 属 REDSTONE
     * redstone     53   58.5%  REDSTONE          ← 不可用
     * colored     180   54.4%  DECORATIONS       ← 不可用，几乎对半劈
     * natural     163   50.9%  DECORATIONS       ← 不可用
     * tools        78   39.7%  TOOLS             ← 不可用，混了 28 个 MISC、19 个 TRANSPORTATION
     * </pre>
     *
     * <p>阈值 0.70 正好把 combat 收进来、把 building 挡在外面。
     * {@code combat} 那 6 把斧头不会因此判错 —— {@link #bySuffix} 排在本条<b>之前</b>，
     * {@code _axe} 有 5 个原版同后缀物品全是 TOOLS，斧头在上一条就定案了。
     */
    public static final double MIN_TAB_PURITY = 0.70;

    /**
     * 纯 tab 众数：物品所属官方 tab 里的原版物品高度一致时，直接用那个分组。
     *
     * <p>这条捞的是「1.21 全新的一类东西，原版连同后缀的都没有」。7 把长矛就是：
     * 原版没有任何 {@code _spear}，逐个看邻居的结果是木/石/铜/铁矛判 COMBAT，
     * 金/钻石/下界合金矛判 TOOLS —— 因为后三把在官方 combat 栏里恰好挨着 {@code wooden_axe}。
     * 同一类武器分到两栏，玩家一眼看得出不对。改看 tab，7 把全在 {@code combat}，
     * 一起归 COMBAT。
     *
     * <p>一个物品可能在多个 tab 里（{@code amethyst_block} 同时在 building / natural /
     * redstone），取纯度最高的那个 tab 的众数。
     */
    public Decision byPureTab(String id) {
        String bestTab = null;
        double bestPurity = 0;
        for (Map.Entry<String, List<String>> e : tabs.entrySet()) {
            if (!e.getValue().contains(id)) continue;
            double purity = tabPurity(e.getKey());
            if (purity > bestPurity) {
                bestPurity = purity;
                bestTab = e.getKey();
            }
        }
        if (bestTab == null || bestPurity < MIN_TAB_PURITY) return null;
        return new Decision(tabMode(bestTab), "纯 tab 众数",
                String.format(Locale.ROOT, "%s 纯度 %.1f%%", bestTab, bestPurity * 100));
    }

    private final Map<String, Map<String, Integer>> tabVoteCache = new HashMap<>();

    /** 某个 tab 里原版物品的 {@code ItemGroup} 票数。 */
    private Map<String, Integer> tabVotes(String tab) {
        return tabVoteCache.computeIfAbsent(tab, t -> {
            Map<String, Integer> votes = new TreeMap<>();
            for (String member : tabs.getOrDefault(t, Collections.emptyList())) {
                String g = vanillaGroup.get(member);
                if (g != null) votes.merge(normalizeGroup(g), 1, Integer::sum);
            }
            return votes;
        });
    }

    private double tabPurity(String tab) {
        Map<String, Integer> votes = tabVotes(tab);
        int total = votes.values().stream().mapToInt(Integer::intValue).sum();
        if (total == 0) return 0;
        return (double) votes.values().stream().mapToInt(Integer::intValue).max().orElse(0) / total;
    }

    private String tabMode(String tab) {
        return tabVotes(tab).entrySet().stream()
                .max(Comparator.<Map.Entry<String, Integer>>comparingInt(Map.Entry::getValue)
                        .thenComparing(Map.Entry::getKey, Comparator.reverseOrder()))
                .map(Map.Entry::getKey).orElse(null);
    }

    /** {@code ItemGroup.MATERIALS} 就是 {@code MISC} 的别名，投票时必须算作一票，否则票被劈开。 */
    private static String normalizeGroup(String group) {
        return "MATERIALS".equals(group) ? "MISC" : group;
    }

    /** 家族判定：先把全家的类型投票汇总，没有再把全家的邻居得分汇总。 */
    private Decision decideFamily(List<String> kin) {
        Map<String, Integer> typeVotes = new TreeMap<>();
        Map<String, String> typeDetail = new LinkedHashMap<>();
        for (String member : kin) {
            Decision d = byType(member);
            if (d != null) {
                typeVotes.merge(d.group, 1, Integer::sum);
                typeDetail.putIfAbsent(d.group, member + " 的 " + d.detail);
            }
        }
        if (!typeVotes.isEmpty()) {
            String win = typeVotes.entrySet().stream()
                    .max(Comparator.<Map.Entry<String, Integer>>comparingInt(Map.Entry::getValue)
                            .thenComparing(e -> e.getKey(), Comparator.reverseOrder()))
                    .map(Map.Entry::getKey).orElseThrow();
            return new Decision(win, "家族类型投票",
                    typeDetail.get(win) + "，家族 " + kin.size() + " 员得 " + typeVotes);
        }

        Map<String, Double> score = new TreeMap<>();
        for (String member : kin) {
            accumulateNeighbours(member, score, new LinkedHashMap<>());
        }
        String win = pick(score);
        if (win == null) return null;
        return new Decision(win, "家族邻居窗口",
                String.format(Locale.ROOT, "家族 %d 员合计 %s", kin.size(), fmt(score)));
    }

    /** 只按类型投票判定；用于报告里对照。 */
    public Decision byType(String id) {
        String type = typeOf(id);
        if (type == null) return null;

        String learned = typeGroup.get(type);
        if (learned != null) return new Decision(learned, "类型投票", type);

        // 1.21 的氧化铜家族用 weathering_copper_* 当独立类型，1.16.4 没有同类可参照。
        // 剥掉氧化前缀按底层形态（door / trapdoor / slab / stair…）再查一次 —— 不剥的话
        // 未打蜡的铜门会落到别处，而打过蜡的铜门（类型就是普通 door）在 REDSTONE，同一种门分两处。
        String canonical = stripWeathering(type);
        if (!canonical.equals(type)) {
            learned = typeGroup.get(canonical);
            // 官方类型名的下划线不统一：氧化活板门叫 weathering_copper_trap_door，
            // 剥出来是 trap_door，而原版橡木活板门的类型是 trapdoor。去掉下划线再试一次。
            if (learned == null) learned = typeGroup.get(canonical.replace("_", ""));
            if (learned != null) return new Decision(learned, "类型投票", type + " -> " + canonical);
        }
        return null;
    }

    /** 只按邻居窗口判定；用于报告里对照。 */
    public Decision byNeighbour(String id) {
        Map<String, Double> score = new TreeMap<>();
        Map<String, String> firstVia = new LinkedHashMap<>();
        accumulateNeighbours(id, score, firstVia);
        String win = pick(score);
        if (win == null) return null;
        return new Decision(win, "邻居窗口", firstVia.get(win) + " " + fmt(score));
    }

    /**
     * 把 {@code id} 在各官方 tab 里的邻居得分累加进 {@code score}。
     * 每个 tab 的每个方向各取最近 {@link #WINDOW} 个「1.16.4 已存在」的物品，
     * 权重按<b>实际距离</b>取 {@code 1/距离}（不能只按名次，理由见类文档）。
     */
    private void accumulateNeighbours(String id, Map<String, Double> score, Map<String, String> firstVia) {
        for (Map.Entry<String, List<String>> e : tabs.entrySet()) {
            List<String> list = e.getValue();
            int at = list.indexOf(id);
            if (at < 0) continue;

            for (int dir : new int[]{-1, 1}) {
                int taken = 0;
                for (int j = at + dir; j >= 0 && j < list.size() && taken < WINDOW; j += dir) {
                    String group = vanillaGroup.get(list.get(j));
                    if (group == null) continue;
                    taken++;
                    score.merge(group, 1.0 / Math.abs(j - at), Double::sum);
                    firstVia.putIfAbsent(group, e.getKey() + "#" + (at + 1) + " 的 " + list.get(j));
                }
            }
        }
    }

    /** 取得分最高的分组；同分时按分组名排序，保证结果稳定可复现。 */
    private static String pick(Map<String, Double> score) {
        return score.entrySet().stream()
                .max(Comparator.<Map.Entry<String, Double>>comparingDouble(Map.Entry::getValue)
                        .thenComparing(e -> e.getKey(), Comparator.reverseOrder()))
                .map(Map.Entry::getKey).orElse(null);
    }

    private static String fmt(Map<String, Double> score) {
        return score.entrySet().stream()
                .sorted(Comparator.comparingDouble(e -> -e.getValue()))
                .map(e -> String.format(Locale.ROOT, "%s %.2f", e.getKey(), e.getValue()))
                .reduce((a, b) -> a + " / " + b).orElse("");
    }

    public String typeOf(String id) {
        JsonElement e = blocksJson == null ? null : blocksJson.get("minecraft:" + id);
        if (e == null) return null;
        return e.getAsJsonObject().getAsJsonObject("definition").get("type")
                .getAsString().replace("minecraft:", "");
    }

    public boolean inAnyTab(String id) {
        return tabs.values().stream().anyMatch(l -> l.contains(id));
    }

    private static String stripWeathering(String type) {
        if (type.startsWith("weathering_copper_")) return type.substring("weathering_copper_".length());
        if (type.startsWith("weathering_")) return type.substring("weathering_".length());
        return type;
    }

    // ------------------------------------------------------------------
    // 加载
    // ------------------------------------------------------------------

    public static CreativeGroups load(Path repo, JsonObject blocksJson) throws Exception {
        Map<String, List<String>> tabs = readTabs(repo);
        Map<String, String> vanillaGroup = readVanillaGroups(repo);
        Map<String, String> typeGroup = learnTypeGroups(vanillaGroup, blocksJson);
        Map<String, List<String>> family = readFamilies(repo);
        return new CreativeGroups(tabs, vanillaGroup, typeGroup, blocksJson, family);
    }

    /**
     * 氧化铜家族表，{@code 家族名,成员} 两列。由 {@code ExtractBlockFamilies} 从官方
     * {@code WeatheringCopper.NEXT_BY_BLOCK} 与 {@code HoneycombItem.WAXABLES} 求连通分量得到。
     */
    static Map<String, List<String>> readFamilies(Path repo) throws Exception {
        Path csv = repo.resolve("docs/registry-diff/weathering-families-1.21.11.csv");
        Map<String, List<String>> byRoot = new LinkedHashMap<>();
        if (!Files.exists(csv)) return Collections.emptyMap();
        for (String line : Files.readAllLines(csv, StandardCharsets.UTF_8)) {
            String[] p = line.trim().split(",");
            if (p.length == 2) byRoot.computeIfAbsent(p[0], k -> new ArrayList<>()).add(p[1]);
        }
        Map<String, List<String>> byMember = new LinkedHashMap<>();
        byRoot.values().forEach(members -> members.forEach(m -> byMember.put(m, members)));
        return byMember;
    }

    /** 官方创造栏顺序：{@code tab -> 该 tab 内按官方顺序排列的物品}。 */
    static Map<String, List<String>> readTabs(Path repo) throws Exception {
        Map<String, List<String>> tabs = new LinkedHashMap<>();
        for (String line : Files.readAllLines(
                repo.resolve("docs/registry-diff/official-creative-tabs-1.21.11.csv"), StandardCharsets.UTF_8)) {
            String[] p = line.trim().split(",");
            if (p.length == 3) tabs.computeIfAbsent(p[0], k -> new ArrayList<>()).add(p[2]);
        }
        return tabs;
    }

    /**
     * 1.16.4 原版的 {@code 标识符 -> ItemGroup 名}。
     *
     * <p>{@code Items.java} 里三种写法：{@code register(Blocks.X, ItemGroup.Y)}、
     * {@code register(new XItem(Blocks.X, ...group(ItemGroup.Y)))}、
     * {@code register("id", ...group(ItemGroup.Y))}。前两种要经 {@code Blocks.java}
     * 把字段名换成标识符。没写 {@code ItemGroup} 的（命令方块、结构空位等）不进表 ——
     * 它们在原版也不出现在创造栏，不该当作邻居依据。
     */
    static Map<String, String> readVanillaGroups(Path repo) throws Exception {
        Map<String, String> blockIds = new HashMap<>();
        Matcher bm = Pattern.compile("public static final Block ([A-Z0-9_]+) = register\\(\"([a-z0-9_]+)\"")
                .matcher(Files.readString(repo.resolve("src/main/java/net/minecraft/block/Blocks.java"),
                        StandardCharsets.UTF_8));
        while (bm.find()) blockIds.put(bm.group(1), bm.group(2));

        Map<String, String> out = new LinkedHashMap<>();
        Pattern groupPat = Pattern.compile("ItemGroup\\.([A-Z_]+)");
        Pattern namePat = Pattern.compile("register\\(\"([a-z0-9_]+)\"");
        Pattern blockPat = Pattern.compile("Blocks\\.([A-Z0-9_]+)");

        for (String line : Files.readAllLines(repo.resolve("src/main/java/net/minecraft/item/Items.java"),
                StandardCharsets.UTF_8)) {
            if (!line.contains("public static final Item ")) continue;
            Matcher gm = groupPat.matcher(line);
            if (!gm.find()) continue;

            Matcher nm = namePat.matcher(line);
            Matcher bp = blockPat.matcher(line);
            String id = nm.find() ? nm.group(1) : (bp.find() ? blockIds.get(bp.group(1)) : null);
            if (id != null) out.put(id, gm.group(1));
        }
        return out;
    }

    /**
     * 方块类型 -> 1.16.4 的 {@code ItemGroup}，按同类型原版方块投票取众数。
     * 泛型 {@code block} 也算票，理由见类文档（留一验证显示算它更准）。
     */
    static Map<String, String> learnTypeGroups(Map<String, String> vanillaGroup, JsonObject blocksJson) {
        Map<String, Map<String, Integer>> votes = new TreeMap<>();
        for (Map.Entry<String, String> e : vanillaGroup.entrySet()) {
            JsonElement je = blocksJson.get("minecraft:" + e.getKey());
            if (je == null) continue;
            String type = je.getAsJsonObject().getAsJsonObject("definition").get("type")
                    .getAsString().replace("minecraft:", "");
            votes.computeIfAbsent(type, k -> new TreeMap<>()).merge(e.getValue(), 1, Integer::sum);
        }

        Map<String, String> out = new TreeMap<>();
        votes.forEach((type, v) -> v.entrySet().stream().max(Map.Entry.comparingByValue())
                .ifPresent(best -> out.put(type, best.getKey())));
        return out;
    }
}
