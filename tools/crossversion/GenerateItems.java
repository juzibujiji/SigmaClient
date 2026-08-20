package verify;

import com.google.gson.*;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/**
 * 生成 1.17-1.21.11 新增物品的注册代码。
 *
 * <p>物品分三类处理：
 * <ul>
 *   <li><b>方块物品</b>：标识符同时存在于方块注册表，生成 {@code register(ModernBlocks.X, group)}</li>
 *   <li><b>简单纯物品</b>：材料、食物、唱片等，属性可由官方 items.json 的组件直接表达</li>
 *   <li><b>需要专门类的</b>：工具、盔甲、长矛、刷怪蛋等，跳过并列出清单交给后续阶段</li>
 * </ul>
 *
 * <p>数值来自 {@code 1.21.11/reports/items.json} 的官方默认组件：
 * {@code max_stack_size}、{@code max_damage}、{@code rarity}、{@code food}。
 * 创造栏分组由 {@link CreativeGroups} 从官方数据推导（类型投票 + 官方创造栏邻居），
 * 准确率在 1.16.4 原版上留一验证为 95.81%，详见该类文档。
 */
public class GenerateItems {
    /**
     * 后缀 -> 需要专门物品类，本批跳过。
     *
     * <p>这是一道<b>护栏</b>而不是黑名单：{@link #SPECIAL_CLASS} 里显式登记过的标识符会先被
     * 取走，剩下撞到这些后缀的才跳过。这样将来官方再加一把什么剑，它会老老实实进
     * 跳过清单等人处理，而不是悄悄退化成一个没有攻击力的普通 {@code Item}。
     *
     * <p>曾经在这里但已移除的后缀，都是因为<b>官方本来就用普通 {@code Item}</b>，
     * 留在这里等于凭名字误判（对着 MCP-Reborn 的 {@code Items.java} 逐条确认过）：
     *
     * <table>
     *   <tr><th>后缀</th><th>官方写法</th></tr>
     *   <tr><td>{@code _pottery_sherd}</td><td>{@code new Item.Properties().rarity(UNCOMMON)}，23 个全是</td></tr>
     *   <tr><td>{@code _music_disc}</td><td>{@code stacksTo(1).rarity(...).jukeboxPlayable(...)}，唱片行为已挪进数据组件</td></tr>
     *   <tr><td>{@code _banner_pattern}</td><td>{@code stacksTo(1).rarity(...).component(PROVIDES_BANNER_PATTERNS, ...)}</td></tr>
     *   <tr><td>{@code _spawn_egg}</td><td>见下</td></tr>
     * </table>
     *
     * <p><b>刷怪蛋为什么也能用普通 {@code Item}：</b>1.20.5 之前刷怪蛋是一张灰度模板
     * （{@code item/template_spawn_egg}）加两个颜色染出来的，所以非得要 {@code SpawnEggItem}
     * 提供颜色。1.21 改成了<b>每个生物一张 PNG</b>（{@code item/generated} + {@code layer0}），
     * 外观不再依赖物品类。而 1.16.4 的 {@code SpawnEggItem} 构造要一个 {@code EntityType}，
     * 新生物在本项目里还不存在，硬塞会拿不到类型。
     *
     * <p>所以这 23 个刷怪蛋注册成普通 {@code Item}：图标、名字、创造栏位置全对，
     * 右键不会生成任何东西。这在<b>多人服务器场景下反而是正确的</b> ——
     * 生成由服务端裁决，客户端只需要物品存在且 ID 对得上。单机要能真的生成，
     * 得先有对应的生物实体，那是独立工程（见方案文档 §3「生物：独立工程，不在本次范围」）。
     */
    private static final String[] NEEDS_CLASS = {
        "_bucket", "_boat", "_chest_boat", "_raft",
        "_sign", "_hanging_sign", "_door", "_bed", "_minecart"
    };

    /**
     * 已经写好专门物品类的标识符 -> 构造表达式的「类名 + Properties 之前的参数」。
     * 这些不能退化成普通 {@code Item}，否则会丢掉专属行为
     * （例如重锤的砸落攻击全在 {@code MaceItem} 里）。创造栏分组不写在这里 ——
     * 由 {@link CreativeGroups} 判定（重锤按官方在三叉戟旁边，判出来就是 COMBAT）。
     *
     * <p>所有数值都取自 MCP-Reborn 的官方源码，并在 {@link #putTools} 等方法上注明出处。
     */
    private static final Map<String, String[]> SPECIAL_CLASS = new HashMap<>();

    /**
     * 标识符 -> 完整构造表达式模板，优先级高于 {@link #SPECIAL_CLASS}。
     * {@code {PROPS}} 会被替换成 {@code new Item.Properties()...} 那一整段。
     *
     * <p>{@link #SPECIAL_CLASS} 只能把参数拼在 {@code Properties} <b>之前</b>，
     * 而告示牌物品的 1.16.4 签名是 {@code (Properties, Block 地面/吊顶, Block 墙上)} ——
     * Properties 在最前面，后面才是方块。这类签名只能用模板表达。
     *
     * <p><b>声明必须在下面那个 static 块之前。</b>Java 按文本顺序初始化静态成员，
     * 声明放在 static 块之后会让 {@code putSignItems()} 拿到 null。</p>
     */
    private static final Map<String, String> CTOR_TEMPLATE = new HashMap<>();

    static {
        SPECIAL_CLASS.put("mace", new String[]{"MaceItem", ""});
        putTools();
        putArmor();
        putSpears();
        putSmithingTemplates();
        putBundles();
        putSmallBehaviourItems();
        putSignItems();
        putBoats();
    }

    /**
     * 4 种新木的船 + 10 种运输船。
     *
     * <p>官方 1.21 每种船都是独立的 {@code EntityType}
     * （{@code new BoatItem(EntityType.CHERRY_BOAT, props)}）；1.16.4 只有一个
     * {@code BoatEntity} 加 {@code Type} 枚举，所以这里传枚举值。那正是原版自己从
     * 1.19 用到 1.21.2 拆分之前的结构。
     *
     * <p>注意 {@code bamboo_raft} 的标识符里没有 {@code boat}，但用的是同一个
     * {@code BoatEntity.Type.BAMBOO} —— 竹筏的模型差异由渲染器按 Type 选，
     * 不是靠两个不同的实体。
     */
    private static void putBoats() {
        SPECIAL_CLASS.put("cherry_boat", new String[]{"BoatItem", "BoatEntity.Type.CHERRY"});
        SPECIAL_CLASS.put("pale_oak_boat", new String[]{"BoatItem", "BoatEntity.Type.PALE_OAK"});
        SPECIAL_CLASS.put("mangrove_boat", new String[]{"BoatItem", "BoatEntity.Type.MANGROVE"});
        SPECIAL_CLASS.put("bamboo_raft", new String[]{"BoatItem", "BoatEntity.Type.BAMBOO"});

        String[][] chest = {
            {"oak_chest_boat", "OAK"}, {"spruce_chest_boat", "SPRUCE"},
            {"birch_chest_boat", "BIRCH"}, {"jungle_chest_boat", "JUNGLE"},
            {"acacia_chest_boat", "ACACIA"}, {"dark_oak_chest_boat", "DARK_OAK"},
            {"cherry_chest_boat", "CHERRY"}, {"pale_oak_chest_boat", "PALE_OAK"},
            {"mangrove_chest_boat", "MANGROVE"}, {"bamboo_chest_raft", "BAMBOO"},
        };
        for (String[] row : chest) {
            SPECIAL_CLASS.put(row[0], new String[]{"ChestBoatItem", "BoatEntity.Type." + row[1]});
        }
    }

    /**
     * 一批 1.17+ 的单件物品，行为类由 subagent 对着官方源码写好了。
     * 官方 {@code Items.java} 里这些也各有专门类（或专门的 {@code Consumable} 组件），
     * 退化成普通 {@code Item} 就是右键没反应。
     */
    private static void putSmallBehaviourItems() {
        SPECIAL_CLASS.put("spyglass", new String[]{"SpyglassItem", ""});
        SPECIAL_CLASS.put("wind_charge", new String[]{"WindChargeItem", ""});
        SPECIAL_CLASS.put("brush", new String[]{"BrushItem", ""});
        SPECIAL_CLASS.put("ominous_bottle", new String[]{"OminousBottleItem", ""});
        // 官方是 InstrumentItem + Instruments 注册表；空的山羊角右键返回 FAIL 是官方行为。
        SPECIAL_CLASS.put("goat_horn", new String[]{"InstrumentItem", ""});
    }

    /**
     * 告示牌物品：12 个悬挂告示牌 + 4 种新木的普通告示牌。
     *
     * <p>悬挂告示牌<b>不能用 1.16.4 的 {@code SignItem}</b>。{@code SignItem} 继承
     * {@code WallOrFloorItem}，后者把附着方向写死成 {@code DOWN}，朝天花板放会得到壁挂形态、
     * 根本吊不上去。所以用新写的 {@code ModernHangingSignItem}。
     *
     * <p>两者的签名都是 {@code (Properties, Block, Block)}，Properties 在最前，
     * 所以走 {@link #CTOR_TEMPLATE} 而不是 {@link #SPECIAL_CLASS}。
     */
    private static void putSignItems() {
        for (String wood : new String[]{"oak", "spruce", "birch", "acacia", "cherry", "jungle",
                "dark_oak", "pale_oak", "crimson", "warped", "mangrove", "bamboo"}) {
            String up = wood.toUpperCase(Locale.ROOT);
            CTOR_TEMPLATE.put(wood + "_hanging_sign", "new ModernHangingSignItem({PROPS}, ModernBlocks."
                    + up + "_HANGING_SIGN, ModernBlocks." + up + "_WALL_HANGING_SIGN)");
        }
        // 1.16.4 已有 oak/spruce/birch/acacia/jungle/dark_oak/crimson/warped 的普通告示牌，
        // 只有这四种新木需要注册。
        for (String wood : new String[]{"cherry", "pale_oak", "mangrove", "bamboo"}) {
            String up = wood.toUpperCase(Locale.ROOT);
            CTOR_TEMPLATE.put(wood + "_sign", "new SignItem({PROPS}, ModernBlocks."
                    + up + "_SIGN, ModernBlocks." + up + "_WALL_SIGN)");
        }
    }

    /**
     * 17 种收纳袋。官方是 {@code new Item.Properties().stacksTo(1).component(BUNDLE_CONTENTS, EMPTY)}，
     * 没有专门的物品类 —— 装取物品的行为全在数据组件与 {@code AbstractContainerMenu} 的
     * {@code tryItemClickBehaviourOverride} 里。1.16.4 没有组件系统，所以本项目把内容存进
     * NBT 并新写了 {@code BundleItem}，那些行为必须由这个类提供，不能退化成普通 {@code Item}。
     *
     * <p>默认组件 {@code BUNDLE_CONTENTS = EMPTY} 不需要对应物：改用 NBT 存储后，
     * {@code BundleItem.getContents} 读不到 NBT 时本来就返回空内容。
     */
    private static void putBundles() {
        SPECIAL_CLASS.put("bundle", new String[]{"BundleItem", ""});
        for (String color : new String[]{"white", "orange", "magenta", "light_blue", "yellow",
                "lime", "pink", "gray", "light_gray", "cyan", "purple", "blue", "brown",
                "green", "red", "black"}) {
            SPECIAL_CLASS.put(color + "_bundle", new String[]{"BundleItem", ""});
        }
    }

    /**
     * 铜工具。取自官方 {@code Items.java}：
     * <pre>
     * COPPER_SWORD   = sword(ToolMaterial.COPPER, 3.0F, -2.4F)
     * COPPER_SHOVEL  = new ShovelItem(ToolMaterial.COPPER, 1.5F, -3.0F, ...)
     * COPPER_PICKAXE = pickaxe(ToolMaterial.COPPER, 1.0F, -2.8F)
     * COPPER_AXE     = new AxeItem(ToolMaterial.COPPER, 7.0F, -3.2F, ...)
     * COPPER_HOE     = new HoeItem(ToolMaterial.COPPER, -1.0F, -2.0F, ...)
     * </pre>
     * 1.16.4 的 {@code SwordItem} / {@code PickaxeItem} / {@code HoeItem} 攻击力参数是
     * {@code int}，{@code ShovelItem} / {@code AxeItem} 是 {@code float}，签名差异照 1.16.4 走。
     * 三处 {@code int} 的官方值恰好都是整数（3.0F / 1.0F / -1.0F），没有精度损失。
     */
    private static void putTools() {
        SPECIAL_CLASS.put("copper_sword", new String[]{"SwordItem", "ItemTier.COPPER, 3, -2.4F"});
        SPECIAL_CLASS.put("copper_shovel", new String[]{"ShovelItem", "ItemTier.COPPER, 1.5F, -3.0F"});
        SPECIAL_CLASS.put("copper_pickaxe", new String[]{"PickaxeItem", "ItemTier.COPPER, 1, -2.8F"});
        SPECIAL_CLASS.put("copper_axe", new String[]{"AxeItem", "ItemTier.COPPER, 7.0F, -3.2F"});
        SPECIAL_CLASS.put("copper_hoe", new String[]{"HoeItem", "ItemTier.COPPER, -1, -2.0F"});
    }

    /**
     * 铜盔甲与两种马铠。
     *
     * <p>盔甲：官方 {@code humanoidArmor(ArmorMaterials.COPPER, ArmorType.X)}，
     * 数值已并入 {@code ArmorMaterial.COPPER}。
     *
     * <p>马铠：官方 {@code horseArmor(ArmorMaterials.X)} 用的是
     * {@code createAttributes(ArmorType.BODY)}，也就是 {@code makeDefense} 的<b>第五个</b>参数。
     * 拿 1.16.4 已有的三种马铠验证过这个对应关系：铁 {@code makeDefense(2,5,6,2,5)} → 5、
     * 金 {@code (1,3,5,2,7)} → 7、钻石 {@code (3,6,8,3,11)} → 11，与 1.16.4
     * {@code HorseArmorItem(5/7/11, ...)} 逐一相符。所以铜取 4、下界合金取 19。
     */
    private static void putArmor() {
        SPECIAL_CLASS.put("copper_helmet", new String[]{"ArmorItem", "ArmorMaterial.COPPER, EquipmentSlotType.HEAD"});
        SPECIAL_CLASS.put("copper_chestplate", new String[]{"ArmorItem", "ArmorMaterial.COPPER, EquipmentSlotType.CHEST"});
        SPECIAL_CLASS.put("copper_leggings", new String[]{"ArmorItem", "ArmorMaterial.COPPER, EquipmentSlotType.LEGS"});
        SPECIAL_CLASS.put("copper_boots", new String[]{"ArmorItem", "ArmorMaterial.COPPER, EquipmentSlotType.FEET"});
        SPECIAL_CLASS.put("copper_horse_armor", new String[]{"HorseArmorItem", "4, \"copper\""});
        SPECIAL_CLASS.put("netherite_horse_armor", new String[]{"HorseArmorItem", "19, \"netherite\""});
    }

    /**
     * 7 种长矛。第二个参数是官方 {@code spear(...)} 的挥击动画时长，
     * {@code SpearItem} 按官方算式 {@code 1.0F / 时长 - 4.0} 自己推攻速 ——
     * 这样生成出来的代码能和官方 {@code Items.java} 的字面量直接对照。
     */
    private static void putSpears() {
        SPECIAL_CLASS.put("wooden_spear", new String[]{"SpearItem", "ItemTier.WOOD, 0.65F"});
        SPECIAL_CLASS.put("stone_spear", new String[]{"SpearItem", "ItemTier.STONE, 0.75F"});
        SPECIAL_CLASS.put("copper_spear", new String[]{"SpearItem", "ItemTier.COPPER, 0.85F"});
        SPECIAL_CLASS.put("iron_spear", new String[]{"SpearItem", "ItemTier.IRON, 0.95F"});
        SPECIAL_CLASS.put("golden_spear", new String[]{"SpearItem", "ItemTier.GOLD, 0.95F"});
        SPECIAL_CLASS.put("diamond_spear", new String[]{"SpearItem", "ItemTier.DIAMOND, 1.05F"});
        SPECIAL_CLASS.put("netherite_spear", new String[]{"SpearItem", "ItemTier.NETHERITE, 1.15F"});
    }

    /**
     * 19 种锻造模板。官方用两个静态工厂区分用途
     * （{@code createNetheriteUpgradeTemplate} 一个、{@code createArmorTrimTemplate} 十八个），
     * 这里换成 {@code SmithingTemplateItem.Variant} 枚举，好让生成器一个参数选定。
     */
    private static void putSmithingTemplates() {
        SPECIAL_CLASS.put("netherite_upgrade_smithing_template",
                new String[]{"SmithingTemplateItem", "SmithingTemplateItem.Variant.NETHERITE_UPGRADE"});
        for (String trim : new String[]{"sentry", "dune", "coast", "wild", "ward", "eye", "vex", "tide",
                "snout", "rib", "spire", "wayfinder", "shaper", "silence", "raiser", "host", "flow", "bolt"}) {
            SPECIAL_CLASS.put(trim + "_armor_trim_smithing_template",
                    new String[]{"SmithingTemplateItem", "SmithingTemplateItem.Variant.ARMOR_TRIM"});
        }
    }

    /** 官方数据驱动的创造栏分类器。 */
    private static CreativeGroups groups;

    public static void main(String[] args) throws Exception {
        Path repo = Paths.get(args.length > 0 ? args[0] : ".");
        Path diff = repo.resolve("docs/registry-diff");
        Path build = repo.resolve("target/crossversion-check");

        JsonObject items = JsonParser.parseString(Files.readString(
                repo.resolve("1.21.11/reports/items.json"), StandardCharsets.UTF_8)).getAsJsonObject();
        blocksJson = JsonParser.parseString(Files.readString(
                repo.resolve("1.21.11/reports/blocks.json"), StandardCharsets.UTF_8)).getAsJsonObject();
        groups = CreativeGroups.load(repo, blocksJson);
        Map<String, String> renamed = RenamedIds.load(repo);

        List<String> order = Files.readAllLines(diff.resolve("official-items-1.21.11.txt"));
        Set<String> wanted = new HashSet<>(Files.readAllLines(diff.resolve("added-items-1.16.4-to-1.21.11.txt")));
        // 已经生成好的方块，方块物品只能引用这些
        Set<String> blocksDone = new HashSet<>(Files.readAllLines(build.resolve("gen-blocks-done.txt")));

        StringBuilder out = new StringBuilder();
        List<String> skipped = new ArrayList<>();
        List<String> decisions = new ArrayList<>();
        List<String> fallbacks = new ArrayList<>();
        Map<String, Integer> groupCount = new TreeMap<>();
        int blockItems = 0, plainItems = 0;

        for (String id : order) {
            if (!wanted.contains(id)) continue;

            // 改名项不是新增内容：short_grass 就是 1.16.4 的 grass、turtle_scute 就是 scute。
            // 注册它们会让创造栏里出现两个一模一样的条目，映射交给 ModernRegistry.normalize。
            if (renamed.containsKey(id)) {
                skipped.add(id + " (改名而非新增 -> " + renamed.get(id) + ")");
                continue;
            }

            // 有构造模板的必须先取走，哪怕它同时是个方块 —— 告示牌就是这种：
            // 落到下面的 blocksDone 分支会被注册成普通 BlockItem，那样朝天花板放不上去，
            // 也不会跟墙上变体关联。
            if (CTOR_TEMPLATE.containsKey(id)) {
                JsonObject signComp = componentsOf(items, id);
                if (signComp == null) {
                    skipped.add(id + " (items.json 无数据)");
                    continue;
                }
                out.append(emitPlain(id, signComp, groupOf(id, "DECORATIONS", decisions, fallbacks, groupCount)));
                plainItems++;
                continue;
            }

            if (blocksDone.contains(id)) {
                String group = groupOf(id, "BUILDING_BLOCKS", decisions, fallbacks, groupCount);
                out.append("    public static final Item ").append(id.toUpperCase(Locale.ROOT))
                   .append(" = register(ModernBlocks.").append(id.toUpperCase(Locale.ROOT))
                   .append(", ItemGroup.").append(group).append(");\n");
                blockItems++;
                continue;
            }

            // 方块存在于 1.21.11 但本批没生成 -> 它的方块物品也要等
            if (isBlockOnly(diff, id)) {
                skipped.add(id + " (方块未生成)");
                continue;
            }

            // 显式登记过专门类的先取走，护栏只管剩下的（见 NEEDS_CLASS 的说明）。
            String needs = SPECIAL_CLASS.containsKey(id) ? null : needsDedicatedClass(id);
            if (needs != null) {
                skipped.add(id + " (需要 " + needs + " 类)");
                continue;
            }

            JsonObject comp = componentsOf(items, id);
            if (comp == null) {
                skipped.add(id + " (items.json 无数据)");
                continue;
            }
            out.append(emitPlain(id, comp, groupOf(id, "MISC", decisions, fallbacks, groupCount)));
            plainItems++;
        }

        Files.writeString(build.resolve("gen-items.java.txt"), out.toString(), StandardCharsets.UTF_8);
        Files.write(build.resolve("gen-items-skipped.txt"), skipped);
        Files.write(build.resolve("gen-item-groups.txt"), decisions);
        writeCreativeOrder(repo, order);

        System.out.printf("生成 %d 个方块物品 + %d 个纯物品 = %d，跳过 %d%n",
                blockItems, plainItems, blockItems + plainItems, skipped.size());
        System.out.println("创造栏分类分布：" + groupCount);
        System.out.println("分类依据已写入 target/crossversion-check/gen-item-groups.txt");
        if (!fallbacks.isEmpty()) {
            // 兜底意味着官方数据没覆盖到，值得人看一眼，不能静默通过
            System.out.printf("⚠ %d 个物品靠兜底分类（官方数据里既无同类型方块也不在任何 tab）：%s%n",
                    fallbacks.size(), fallbacks);
        }
        Map<String, Integer> reasons = new TreeMap<>();
        for (String s : skipped) reasons.merge(s.substring(s.indexOf('(')), 1, Integer::sum);
        reasons.entrySet().stream().sorted((a, b) -> b.getValue() - a.getValue()).limit(12)
                .forEach(e -> System.out.printf("  %-28s %d%n", e.getKey(), e.getValue()));
    }

    /** 判定分组并记账，让每次生成都留下可复核的依据。 */
    private static String groupOf(String id, String fallback, List<String> decisions,
                                  List<String> fallbacks, Map<String, Integer> groupCount) {
        CreativeGroups.Decision d = groups.decide(id, fallback);
        decisions.add(String.format("%-44s %-16s %-14s %s", id, d.group, d.basis, d.detail));
        if ("兜底".equals(d.basis)) fallbacks.add(id);
        groupCount.merge(d.group, 1, Integer::sum);
        return d.group;
    }

    static Set<String> allBlocks;

    static boolean isBlockOnly(Path diff, String id) {
        if (allBlocks == null) {
            try {
                allBlocks = new HashSet<>(Files.readAllLines(diff.resolve("official-blocks-1.21.11.txt")));
            } catch (Exception e) {
                allBlocks = Collections.emptySet();
            }
        }
        return allBlocks.contains(id);
    }

    static String needsDedicatedClass(String id) {
        for (String suffix : NEEDS_CLASS) {
            if (id.endsWith(suffix)) return suffix.substring(1);
        }
        return null;
    }

    static JsonObject componentsOf(JsonObject items, String id) {
        JsonElement e = items.get("minecraft:" + id);
        if (e == null || !e.isJsonObject()) return null;
        return e.getAsJsonObject().getAsJsonObject("components");
    }

    /** 生成纯物品。属性只取 1.16.4 能表达的部分：堆叠上限、耐久、稀有度、食物。 */
    static String emitPlain(String id, JsonObject comp, String group) {
        String[] special = SPECIAL_CLASS.get(id);
        String itemClass = special == null ? "Item" : special[0];
        String ctorPrefix = special == null || special[1].isEmpty() ? "" : special[1] + ", ";

        StringBuilder props = new StringBuilder("new Item.Properties().group(ItemGroup.")
                .append(group).append(")");

        int stack = comp.has("minecraft:max_stack_size")
                ? comp.get("minecraft:max_stack_size").getAsInt() : 64;
        int maxDamage = comp.has("minecraft:max_damage")
                ? comp.get("minecraft:max_damage").getAsInt() : 0;

        // 1.16.4 的 Properties 不允许同时设置 maxDamage 与 maxStackSize，会抛异常。
        if (maxDamage > 0) {
            if (!DURABILITY_FROM_MATERIAL.contains(itemClass)) {
                props.append(".maxDamage(").append(maxDamage).append(")");
            }
        } else if (stack != 64) {
            props.append(".maxStackSize(").append(stack).append(")");
        }

        if (comp.has("minecraft:rarity")) {
            String rarity = comp.get("minecraft:rarity").getAsString().toUpperCase(Locale.ROOT);
            if (!"COMMON".equals(rarity)) {
                props.append(".rarity(Rarity.").append(rarity).append(")");
            }
        }
        if (comp.has("minecraft:damage_resistant")) {
            props.append(".isImmuneToFire()");
        }

        return "    public static final Item " + id.toUpperCase(Locale.ROOT)
                + " = register(\"" + id + "\", " + ctorExpr(id, itemClass, ctorPrefix, props) + ");\n";
    }

    /** 有模板用模板，否则默认 {@code new 类名(前缀参数, props)}。 */
    private static String ctorExpr(String id, String itemClass, String ctorPrefix, CharSequence props) {
        String template = CTOR_TEMPLATE.get(id);
        if (template != null) {
            return template.replace("{PROPS}", props.toString());
        }
        return "new " + itemClass + "(" + ctorPrefix + props + ")";
    }

    /**
     * 这些物品类自己从 {@code IItemTier} / {@code IArmorMaterial} 取耐久
     * （1.16.4 的 {@code TieredItem} 和 {@code ArmorItem} 构造里调 {@code defaultMaxDamage}），
     * 所以生成的 {@code Properties} 里<b>不要</b>再写 {@code maxDamage}。
     *
     * <p>不是为了少写一句：{@code defaultMaxDamage} 只在耐久还是 0 时才生效，
     * 显式写死会盖住材质里的值 —— 万一 {@code ArmorMaterial.COPPER} 的倍率抄错了，
     * 显式值会把它遮住，让错误看不出来。交给类去取，抄错就会直接表现为耐久不对。
     *
     * <p><b>{@code MaceItem} 不在这里。</b>它 {@code extends Item} 而非 {@code TieredItem}，
     * 没有材质可取；官方也是在 {@code Properties} 里直接写 {@code .durability(500)}。
     * 一度把它列进来，结果重锤耐久变成 0，被回归检查的「mace 耐久」一项抓到。
     * 往这个集合里加类名之前，先确认那个类真的会调 {@code defaultMaxDamage}。
     */
    private static final Set<String> DURABILITY_FROM_MATERIAL = new HashSet<>(Arrays.asList(
            "SwordItem", "ShovelItem", "PickaxeItem", "AxeItem", "HoeItem",
            "ArmorItem", "SpearItem"));

    static JsonObject blocksJson;

    /** 运行时读的创造栏顺序表，由 {@link #writeCreativeOrder} 整体重写。 */
    private static final String ORDER_RESOURCE = "src/main/resources/crossversion/item-order-1.21.11.txt";

    /**
     * 写创造栏顺序表：按<b>官方创造栏</b>的 tab 顺序与 tab 内序号排列。
     *
     * <p>之前用的是 {@code official-items-1.21.11.txt}（按 protocol_id，也就是官方的注册顺序）。
     * 注册顺序与创造栏顺序<b>并不相同</b> —— 这就是用户反馈「分类对了但位置不对」的来源。
     * 举个例子：官方 building 栏按木种成组排（原木、木板、楼梯、台阶、栅栏、门…），
     * 而 protocol_id 是按方块注册表的书写顺序，两者差得很远。
     *
     * <p>一个物品可能出现在多个 tab（{@code amethyst_block} 同时在 building / natural / redstone），
     * 取<b>首次出现</b>的位置。这只影响同一个 1.16.4 分组内部的相对次序 ——
     * {@code ItemGroup.fill} 会按物品自己的分组过滤，所以跨 tab 的排列不会互相干扰。
     *
     * <p>不在任何官方 tab 里的（空气、成书、知识之书、石化橡木台阶等 7 项，官方创造栏里也没有）
     * 按 protocol_id 追加在末尾，保持稳定。
     *
     * <p>这个文件没有手工维护区，所以直接重写，不像 {@code ModernItems.java} 那样需要手工同步。
     */
    static void writeCreativeOrder(Path repo, List<String> protocolOrder) throws Exception {
        Map<String, List<String>> tabs = CreativeGroups.readTabs(repo);

        List<String> lines = new ArrayList<>();
        Set<String> placed = new LinkedHashSet<>();
        lines.add("# 由 tools/crossversion/GenerateItems.java 生成，勿手工编辑。");
        lines.add("# 顺序 = 官方 1.21.11 创造栏的 tab 顺序 + tab 内序号（不是 protocol_id）。");
        lines.add("# 数据源：docs/registry-diff/official-creative-tabs-1.21.11.csv");

        for (Map.Entry<String, List<String>> tab : tabs.entrySet()) {
            for (String id : tab.getValue()) {
                if (placed.add(id)) lines.add(id);
            }
        }
        int fromTabs = placed.size();
        for (String id : protocolOrder) {
            if (placed.add(id)) lines.add(id);
        }

        Path out = repo.resolve(ORDER_RESOURCE);
        Files.writeString(out, String.join("\n", lines) + "\n", StandardCharsets.UTF_8);
        System.out.printf("创造栏顺序表已重写：%d 项来自官方 tab，%d 项（不在任何 tab）按 protocol_id 追加 -> %s%n",
                fromTabs, placed.size() - fromTabs, ORDER_RESOURCE);
    }
}
