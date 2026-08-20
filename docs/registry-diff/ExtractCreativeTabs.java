package extract;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.regex.*;

/**
 * 从 MCP-Reborn 的官方源码 {@code net/minecraft/world/item/CreativeModeTabs.java}
 * 解析出「创造栏 tab -> 物品顺序」。
 *
 * <p>替代之前那份用 awk 拼出来的 CSV。awk 版按行号猜 tab 边界，无法处理
 * {@code Items.COPPER_BARS.unaffected()} 这类访问器写法，也无法把
 * {@code generateSuspiciousStews(...)} 之类的辅助方法还原成底层物品。
 *
 * <p>解析策略（不靠缩进、不靠行号）：
 * <ol>
 *   <li>扫 {@code Registry.register(} ，其后第 2 行是 tab 的 ResourceKey 字段名，
 *       用<b>括号配平</b>确定这个 register 调用的结束位置，即该 tab 的正文范围</li>
 *   <li>正文里按源码顺序收集 {@code .accept(...)} 与 {@code generateXxx(...)}</li>
 *   <li>把参数还原成注册表标识符。无法还原的<b>报错并列出</b>，不静默丢弃</li>
 * </ol>
 *
 * <p>氧化铜家族的标识符不写死：从 {@code Items.java} 找出
 * {@code WeatheringCopperItems X = ...create(Blocks.Y, ...)}，再从 {@code Blocks.java}
 * 找出 {@code WeatheringCopperBlocks Y = ...create("base_id", ...)}，
 * 前缀取自 {@code WeatheringCopperBlocks.create} 里的字符串拼接。
 *
 * <p>用法：{@code java extract.ExtractCreativeTabs <MCP-Reborn 根目录> <SigmaClient 根目录>}
 */
public class ExtractCreativeTabs {

    /** {@code WeatheringCopperBlocks.create} 里的访问器 -> 标识符前缀（照抄官方字符串拼接）。 */
    private static final Map<String, String> WEATHER_PREFIX = new LinkedHashMap<>();

    static {
        WEATHER_PREFIX.put("unaffected", "");
        WEATHER_PREFIX.put("exposed", "exposed_");
        WEATHER_PREFIX.put("weathered", "weathered_");
        WEATHER_PREFIX.put("oxidized", "oxidized_");
        WEATHER_PREFIX.put("waxed", "waxed_");
        WEATHER_PREFIX.put("waxedExposed", "waxed_exposed_");
        WEATHER_PREFIX.put("waxedWeathered", "waxed_weathered_");
        WEATHER_PREFIX.put("waxedOxidized", "waxed_oxidized_");
    }

    /** ResourceKey 字段名 -> CSV 里用的短 tab 名。 */
    private static final Map<String, String> TAB_NAME = new LinkedHashMap<>();

    static {
        TAB_NAME.put("BUILDING_BLOCKS", "building");
        TAB_NAME.put("COLORED_BLOCKS", "colored");
        TAB_NAME.put("NATURAL_BLOCKS", "natural");
        TAB_NAME.put("FUNCTIONAL_BLOCKS", "functional");
        TAB_NAME.put("REDSTONE_BLOCKS", "redstone");
        TAB_NAME.put("TOOLS_AND_UTILITIES", "tools");
        TAB_NAME.put("COMBAT", "combat");
        TAB_NAME.put("FOOD_AND_DRINKS", "food");
        TAB_NAME.put("INGREDIENTS", "ingredients");
        TAB_NAME.put("SPAWN_EGGS", "spawn");
        TAB_NAME.put("OP_BLOCKS", "op");
        // HOTBAR / SEARCH / INVENTORY 是合成 tab，内容由运行时拼装，没有静态清单。
        TAB_NAME.put("HOTBAR", null);
        TAB_NAME.put("SEARCH", null);
        TAB_NAME.put("INVENTORY", null);
    }

    public static void main(String[] args) throws Exception {
        Path mcp = Paths.get(args[0]);
        Path repo = Paths.get(args[1]);
        Path srcRoot = mcp.resolve("src/main/java/net/minecraft");

        String tabsSrc = Files.readString(srcRoot.resolve("world/item/CreativeModeTabs.java"), StandardCharsets.UTF_8);
        itemIds = learnItemIds(srcRoot);
        Map<String, String> weathering = learnWeatheringGroups(srcRoot);
        Map<String, List<String>> helperItems = learnHelperItems(srcRoot, tabsSrc);

        Set<String> officialItems = new LinkedHashSet<>(
                Files.readAllLines(repo.resolve("docs/registry-diff/official-items-1.21.11.txt")));

        List<String> lines = Arrays.asList(tabsSrc.split("\r?\n", -1));
        List<String[]> rows = new ArrayList<>();
        List<String> unresolved = new ArrayList<>();
        List<String> notInRegistry = new ArrayList<>();

        Pattern acceptPat = Pattern.compile("\\.accept\\((.*)\\)\\s*;\\s*$");
        Pattern helperPat = Pattern.compile("\\b(generate[A-Za-z]+)\\(");

        for (Segment seg : segments(lines)) {
            String tab = TAB_NAME.get(seg.tabField);
            if (tab == null) {
                System.out.printf("跳过合成 tab %s（无静态清单）%n", seg.tabField);
                continue;
            }
            int index = 0;
            for (int i = seg.from; i <= seg.to; i++) {
                String line = lines.get(i);
                List<String> resolved = null;

                Matcher am = acceptPat.matcher(line);
                Matcher hm = helperPat.matcher(line);
                Matcher fm = FOR_EACH.matcher(line);
                if (fm.find()) {
                    // Items.COPPER_LANTERN.forEach(out::accept) 一次加入全部 8 个氧化态，
                    // 顺序照官方 WeatheringCopperItems.forEach 的方法体，不按字段声明顺序猜。
                    String base = weathering.get(fm.group(1));
                    if (base == null) {
                        unresolved.add((i + 1) + ": " + line.trim());
                        continue;
                    }
                    resolved = new ArrayList<>();
                    for (String accessor : weatherOrder) {
                        resolved.add(WEATHER_PREFIX.get(accessor) + base);
                    }
                } else if (am.find()) {
                    String id = resolveAccept(am.group(1), weathering);
                    if (id == null) {
                        unresolved.add((i + 1) + ": " + line.trim());
                        continue;
                    }
                    resolved = List.of(id);
                } else if (hm.find()) {                    // 辅助方法的调用常写成 `p_x -> generateXxx(` 并跨多行，
                    // 取到括号配平为止：先找调用参数里的 Items.X（药水、山羊角在这里指定），
                    // 没有再退回方法体里的 Items.X（烟花、可疑炖菜在方法体内构造）。
                    int[] endLine = new int[1];
                    String call = balanced(lines, i, line.indexOf('(', hm.start(1)), endLine);
                    List<String> inArgs = itemsIn(call);
                    resolved = !inArgs.isEmpty() ? inArgs : helperItems.get(hm.group(1));
                    if (resolved == null || resolved.isEmpty()) {
                        unresolved.add((i + 1) + ": " + line.trim());
                        continue;
                    }
                    // 整段调用已经处理完，跳过它占的后续行，避免安全网重复报警
                    i = Math.max(i, endLine[0]);
                } else if (line.contains("Items.") && !line.contains(".icon(")) {
                    // 安全网：tab 正文里凡是提到 Items.X 又没被上面两条消化掉的，
                    // 一律报出来。之前 `p_x -> generateInstrumentTypes(` 就是这样被
                    // 静默漏掉的（山羊角、药水箭、画少了三项而没有任何告警）。
                    unresolved.add((i + 1) + ": [未消化] " + line.trim());
                    continue;
                }

                if (resolved == null) continue;
                for (String id : resolved) {
                    if (!officialItems.contains(id)) {
                        notInRegistry.add(tab + " 行" + (i + 1) + ": " + id);
                        continue;
                    }
                    rows.add(new String[]{tab, String.valueOf(++index), id});
                }
            }
            System.out.printf("  %-11s %4d 项%n", tab, index);
        }

        if (!unresolved.isEmpty()) {
            System.out.println("\n无法还原的 accept（必须逐条处理，不能忽略）：");
            unresolved.forEach(s -> System.out.println("  " + s));
            throw new IllegalStateException("有 " + unresolved.size() + " 处 accept 未能还原成标识符");
        }
        if (!notInRegistry.isEmpty()) {
            System.out.println("\n还原出的标识符不在官方物品注册表里（字段名与注册名不一致？）：");
            notInRegistry.forEach(s -> System.out.println("  " + s));
            throw new IllegalStateException("有 " + notInRegistry.size() + " 个标识符对不上注册表");
        }

        StringBuilder csv = new StringBuilder();
        for (String[] r : rows) {
            csv.append(r[0]).append(',').append(r[1]).append(',').append(r[2]).append('\n');
        }
        Path out = repo.resolve("docs/registry-diff/official-creative-tabs-1.21.11.csv");
        Files.writeString(out, csv.toString(), StandardCharsets.UTF_8);

        long distinct = rows.stream().map(r -> r[2]).distinct().count();
        System.out.printf("%n共 %d 条（%d 个不同物品），已写入 %s%n", rows.size(), distinct, out);
    }

    // ------------------------------------------------------------------
    // tab 正文切段：靠括号配平，不靠缩进或行号
    // ------------------------------------------------------------------

    private static final class Segment {
        final String tabField;
        final int from;
        final int to;

        Segment(String tabField, int from, int to) {
            this.tabField = tabField;
            this.from = from;
            this.to = to;
        }
    }

    private static List<Segment> segments(List<String> lines) {
        List<Segment> out = new ArrayList<>();
        Pattern keyPat = Pattern.compile("^([A-Z][A-Z0-9_]*),$");

        for (int i = 0; i < lines.size(); i++) {
            if (!lines.get(i).contains("Registry.register(")) continue;

            String field = null;
            for (int j = i + 1; j <= Math.min(i + 3, lines.size() - 1); j++) {
                Matcher m = keyPat.matcher(lines.get(j).trim());
                if (m.matches() && TAB_NAME.containsKey(m.group(1))) {
                    field = m.group(1);
                    break;
                }
            }
            if (field == null) continue;

            int depth = 0;
            int end = i;
            outer:
            for (int j = i; j < lines.size(); j++) {
                String line = stripStrings(lines.get(j));
                for (int k = (j == i ? line.indexOf("Registry.register(") : 0); k < line.length(); k++) {
                    char c = line.charAt(k);
                    if (c == '(') depth++;
                    else if (c == ')' && --depth == 0) {
                        end = j;
                        break outer;
                    }
                }
            }
            out.add(new Segment(field, i, end));
            i = end;
        }
        return out;
    }

    /**
     * 取从 {@code lines[start]} 的第 {@code parenAt} 个字符起、括号配平的整段文本。
     * {@code endLine[0]} 回填这段文本结束所在的行号（0 基）。
     */
    private static String balanced(List<String> lines, int start, int parenAt, int[] endLine) {
        StringBuilder sb = new StringBuilder();
        int depth = 0;
        for (int j = start; j < lines.size(); j++) {
            String raw = lines.get(j);
            String scan = stripStrings(raw);
            for (int k = (j == start ? parenAt : 0); k < scan.length(); k++) {
                sb.append(raw.charAt(k));
                char c = scan.charAt(k);
                if (c == '(') depth++;
                else if (c == ')' && --depth == 0) {
                    endLine[0] = j;
                    return sb.toString();
                }
            }
            sb.append(' ');
        }
        endLine[0] = lines.size() - 1;
        return sb.toString();
    }

    /** 把字符串字面量换成同长度的空格，避免里面的括号干扰配平。 */
    private static String stripStrings(String line) {
        char[] c = line.toCharArray();
        boolean in = false;
        for (int i = 0; i < c.length; i++) {
            if (c[i] == '"' && (i == 0 || c[i - 1] != '\\')) {
                in = !in;
                c[i] = ' ';
            } else if (in) {
                c[i] = ' ';
            }
        }
        return new String(c);
    }

    // ------------------------------------------------------------------
    // 参数还原
    // ------------------------------------------------------------------

    private static final Pattern ITEMS_FIELD = Pattern.compile("\\bItems\\.([A-Z][A-Z0-9_]*)");
    private static final Pattern WEATHER_CALL =
            Pattern.compile("\\bItems\\.([A-Z][A-Z0-9_]*)\\.([a-zA-Z]+)\\(\\)");
    private static final Pattern FOR_EACH =
            Pattern.compile("\\bItems\\.([A-Z][A-Z0-9_]*)\\.forEach\\(");

    /**
     * 还原单个 {@code accept} 的实参。支持三种写法：
     * {@code Items.X}、{@code Items.X.accessor()}（氧化铜家族）、
     * 以及 {@code SomeBlock.setYOnStack(new ItemStack(Items.X), ...)} 这类包装。
     */
    private static String resolveAccept(String arg, Map<String, String> weathering) {
        Matcher wm = WEATHER_CALL.matcher(arg);
        if (wm.find()) {
            String base = weathering.get(wm.group(1));
            String prefix = WEATHER_PREFIX.get(wm.group(2));
            if (base == null || prefix == null) return null;
            return prefix + base;
        }
        // 命名旗帜等由工厂方法产出的旗帜实例，实参里没有 Items.X。
        if (arg.contains("getOminousBannerInstance")) return "white_banner";

        List<String> ids = itemsIn(arg);
        return ids.size() == 1 ? ids.get(0) : null;
    }

    /** 抽出文本里所有 {@code Items.FIELD}，按官方注册调用换成标识符（去重、保序）。 */
    private static List<String> itemsIn(String text) {
        LinkedHashSet<String> ids = new LinkedHashSet<>();
        Matcher m = ITEMS_FIELD.matcher(text);
        while (m.find()) {
            String id = itemIds.get(m.group(1));
            // 解析不到就原样留下字段名的小写形式，交给注册表校验去报错，
            // 不要在这里悄悄猜一个看起来像的标识符。
            ids.add(id != null ? id : m.group(1).toLowerCase(Locale.ROOT));
        }
        return new ArrayList<>(ids);
    }

    // ------------------------------------------------------------------
    // 从官方源码学几张表
    // ------------------------------------------------------------------

    /** {@code Items} 的字段名 -> 注册标识符。 */
    private static Map<String, String> itemIds = Collections.emptyMap();

    /**
     * 建立 {@code Items.FIELD -> 注册标识符} 的权威映射。
     *
     * <p>不能用 {@code FIELD.toLowerCase()}：官方字段名与注册名并不总是一致 ——
     * {@code CUT_STANDSTONE_SLAB} 是 Mojang 的拼写错误（注册名是 {@code cut_sandstone_slab}），
     * {@code DRY_SHORT_GRASS} 指向的方块叫 {@code short_dry_grass}。
     * {@code registerBlock} 取的是<b>方块</b>的注册名（见官方
     * {@code Items.registerBlock -> blockIdToItemId}），所以必须先解出方块名。
     */
    private static Map<String, String> learnItemIds(Path srcRoot) throws Exception {
        // 1) references/Blocks.java：ResourceKey<Block> FIELD = createKey("id")
        Map<String, String> refBlocks = new HashMap<>();
        matchAll(read(srcRoot, "references/Blocks.java"),
                "public static final ResourceKey<Block> ([A-Z0-9_]+) = createKey\\(\"([a-z0-9_]+)\"\\)",
                m -> refBlocks.put(m.group(1), m.group(2)));

        // 2) Blocks.java：register("id", ...) 或 register(net.minecraft.references.Blocks.CONST, ...)
        //    registerStair / registerLegacyStair / registerBed / registerStainedGlass 都是转发给
        //    register 的包装，第一个参数同样是标识符，所以方法名后面允许有后缀。
        Map<String, String> blocks = new HashMap<>();
        matchAll(read(srcRoot, "world/level/block/Blocks.java"),
                "public static final Block ([A-Z0-9_]+) = register[A-Za-z]*\\(\\s*"
                        + "(?:\"([a-z0-9_]+)\"|net\\.minecraft\\.references\\.Blocks\\.([A-Z0-9_]+))",
                m -> {
                    String id = m.group(2) != null ? m.group(2) : refBlocks.get(m.group(3));
                    if (id != null) blocks.put(m.group(1), id);
                });

        // 3) EntityType.java：刷怪蛋的名字来自实体注册名 + "_spawn_egg"（见 registerSpawnEgg）
        Map<String, String> entities = new HashMap<>();
        matchAll(read(srcRoot, "world/entity/EntityType.java"),
                "public static final EntityType<.*?> ([A-Z0-9_]+) = register\\(\\s*\"([a-z0-9_]+)\"",
                m -> entities.put(m.group(1), m.group(2)));

        // 4) Items.java 的三种注册写法
        Map<String, String> out = new HashMap<>();
        String itemsSrc = read(srcRoot, "world/item/Items.java");
        matchAll(itemsSrc,
                "public static final Item ([A-Z0-9_]+) = registerBlock\\(\\s*Blocks\\.([A-Z0-9_]+)",
                m -> {
                    String id = blocks.get(m.group(2));
                    if (id != null) out.put(m.group(1), id);
                });
        matchAll(itemsSrc,
                "public static final Item ([A-Z0-9_]+) = registerItem\\(\\s*\"([a-z0-9_]+)\"",
                m -> out.put(m.group(1), m.group(2)));
        matchAll(itemsSrc,
                "public static final Item ([A-Z0-9_]+) = registerSpawnEgg\\(\\s*EntityType\\.([A-Z0-9_]+)",
                m -> {
                    String id = entities.get(m.group(2));
                    if (id != null) out.put(m.group(1), id + "_spawn_egg");
                });

        long renamed = out.entrySet().stream()
                .filter(e -> !e.getKey().toLowerCase(Locale.ROOT).equals(e.getValue())).count();
        System.out.printf("Items 字段 -> 标识符 %d 条（其中 %d 条与字段名不一致）%n", out.size(), renamed);
        out.entrySet().stream()
                .filter(e -> !e.getKey().toLowerCase(Locale.ROOT).equals(e.getValue()))
                .sorted(Map.Entry.comparingByKey())
                .forEach(e -> System.out.printf("    Items.%-26s -> %s%n", e.getKey(), e.getValue()));
        return out;
    }

    private static String read(Path srcRoot, String rel) throws Exception {
        return Files.readString(srcRoot.resolve(rel), StandardCharsets.UTF_8);
    }

    private static void matchAll(String src, String regex, java.util.function.Consumer<Matcher> sink) {
        Matcher m = Pattern.compile(regex).matcher(src);
        while (m.find()) sink.accept(m);
    }

    /** {@code Items} 里的 WeatheringCopperItems 字段名 -> 底层标识符（copper_bars / copper_chain）。 */
    private static List<String> weatherOrder = new ArrayList<>(WEATHER_PREFIX.keySet());

    private static Map<String, String> learnWeatheringGroups(Path srcRoot) throws Exception {
        // forEach 的展开顺序照官方方法体读出来，不按 WEATHER_PREFIX 的声明顺序假定。
        String wciSrc = read(srcRoot, "world/item/WeatheringCopperItems.java");
        Matcher fe = Pattern.compile("public void forEach\\([^)]*\\)\\s*\\{").matcher(wciSrc);
        if (!fe.find()) throw new IllegalStateException("WeatheringCopperItems.forEach 解析失败");
        weatherOrder = new ArrayList<>();
        Matcher acc = Pattern.compile("this\\.([a-zA-Z]+)\\b").matcher(bodyAfter(wciSrc, fe.end()));
        while (acc.find()) {
            if (!WEATHER_PREFIX.containsKey(acc.group(1))) {
                throw new IllegalStateException("forEach 里出现未知访问器 " + acc.group(1));
            }
            weatherOrder.add(acc.group(1));
        }
        System.out.printf("氧化态展开顺序（照 WeatheringCopperItems.forEach）：%s%n", weatherOrder);

        Map<String, String> itemToBlockField = new LinkedHashMap<>();
        Pattern ip = Pattern.compile(
                "WeatheringCopperItems\\s+([A-Z0-9_]+)\\s*=\\s*WeatheringCopperItems\\.create\\(\\s*Blocks\\.([A-Z0-9_]+)");
        Matcher m = ip.matcher(Files.readString(srcRoot.resolve("world/item/Items.java"), StandardCharsets.UTF_8));
        while (m.find()) itemToBlockField.put(m.group(1), m.group(2));

        Map<String, String> blockFieldToId = new LinkedHashMap<>();
        Pattern bp = Pattern.compile(
                "WeatheringCopperBlocks\\s+([A-Z0-9_]+)\\s*=\\s*WeatheringCopperBlocks\\.create\\(\\s*\"([a-z0-9_]+)\"",
                Pattern.DOTALL);
        Matcher bm = bp.matcher(Files.readString(
                srcRoot.resolve("world/level/block/Blocks.java"), StandardCharsets.UTF_8));
        while (bm.find()) blockFieldToId.put(bm.group(1), bm.group(2));

        Map<String, String> out = new LinkedHashMap<>();
        itemToBlockField.forEach((itemField, blockField) -> {
            String id = blockFieldToId.get(blockField);
            if (id != null) out.put(itemField, id);
        });
        System.out.printf("氧化铜家族 %d 组：%s%n", out.size(), out);
        return out;
    }

    /**
     * {@code generateXxx} 方法名 -> 方法体里构造的底层物品。
     *
     * <p>方法体里直接 {@code new ItemStack(Items.X)} 的（可疑炖菜、不祥之瓶、烟花、画）
     * 一眼可得；委托给静态工厂的（附魔书走
     * {@code EnchantmentHelper.createBook}）再往里跟一层，仍然读官方源码而不是按名字猜。
     */
    private static Map<String, List<String>> learnHelperItems(Path srcRoot, String src) throws Exception {
        Map<String, List<String>> out = new LinkedHashMap<>();
        Pattern def = Pattern.compile(
                "private static void (generate[A-Za-z]+)\\(([^{]*)\\)\\s*\\{", Pattern.DOTALL);
        Matcher m = def.matcher(src);
        while (m.find()) {
            String body = bodyAfter(src, m.end());
            List<String> ids = itemsIn(body);
            if (ids.isEmpty()) {
                ids = followFactories(srcRoot, body);
            }
            out.put(m.group(1), ids);
        }
        System.out.printf("辅助方法 %d 个：%s%n", out.size(), out);
        return out;
    }

    /**
     * 方法体里没有 {@code Items.X} 时，跟进它调用的静态工厂再找一层。
     * 只跟一层，且只认「类名能在源码树里定位到同名文件」的调用。
     */
    private static List<String> followFactories(Path srcRoot, String body) throws Exception {
        LinkedHashSet<String> ids = new LinkedHashSet<>();
        Matcher call = Pattern.compile("\\b([A-Z][A-Za-z0-9]*)\\.([a-z][A-Za-z0-9]*)\\(").matcher(body);
        while (call.find()) {
            String cls = call.group(1);
            String method = call.group(2);
            Path file = classFiles(srcRoot).get(cls);
            if (file == null) continue;

            String outer = Files.readString(file, StandardCharsets.UTF_8);
            Matcher md = Pattern.compile("\\b" + Pattern.quote(method) + "\\([^;{)]*\\)\\s*\\{").matcher(outer);
            while (md.find()) {
                ids.addAll(itemsIn(bodyAfter(outer, md.end())));
            }
        }
        return new ArrayList<>(ids);
    }

    private static Map<String, Path> classFiles;

    private static Map<String, Path> classFiles(Path srcRoot) throws Exception {
        if (classFiles == null) {
            classFiles = new HashMap<>();
            try (var walk = Files.walk(srcRoot)) {
                walk.filter(p -> p.toString().endsWith(".java")).forEach(p -> {
                    String name = p.getFileName().toString();
                    classFiles.putIfAbsent(name.substring(0, name.length() - 5), p);
                });
            }
        }
        return classFiles;
    }

    /** 取 {@code src} 中从 {@code start}（紧跟在 '{' 之后）开始、到配对 '}' 为止的方法体。 */
    private static String bodyAfter(String src, int start) {
        int depth = 1;
        int i = start;
        while (i < src.length() && depth > 0) {
            char c = src.charAt(i++);
            if (c == '{') depth++;
            else if (c == '}') depth--;
        }
        return src.substring(start, i);
    }
}
