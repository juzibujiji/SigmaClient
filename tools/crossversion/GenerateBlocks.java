package verify;

import com.google.gson.*;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/**
 * 生成 1.17-1.21.11 新增方块与物品的注册代码。
 *
 * <p>输入全部来自官方数据，不手抄：
 * <ul>
 *   <li>{@code blocks.json} 的 {@code definition.type} —— 方块类型，决定用哪个 1.16.4 方块类</li>
 *   <li>{@code block-props-1.21.11.csv} —— 硬度、爆炸抗性、地图颜色 id、发光、音效、
 *       碰撞、随机 tick（由 {@code ExtractBlockProps.java} 从官方 jar 反射导出）</li>
 *   <li>{@code tags/mineable/*.json} —— 推断 1.16.4 的 {@code Material}（1.20 起官方已移除
 *       Material 系统，只能反推）</li>
 *   <li>{@code official-blocks/items-1.21.11.txt} —— 注册顺序</li>
 * </ul>
 *
 * <p><b>按官方注册顺序生成。</b>楼梯/台阶/墙的构造需要 base 方块状态，而官方顺序里 base
 * 总是先于派生方块（已验证 cobbled_deepslate 1123 &lt; stairs 1124、cut_copper 1015 &lt;
 * stairs 1027），所以顺着生成就能满足 Java 静态初始化的先后依赖。
 *
 * <p><b>地图颜色按 id 而非名字映射。</b>1.21 与 1.16.4 的常量名大量不同
 * （{@code COLOR_BLACK} vs {@code BLACK}、{@code TERRACOTTA_BLACK} vs
 * {@code BLACK_TERRACOTTA}），但 id 是协议依赖的、稳定的。
 */
public class GenerateBlocks {
    /** 1.21 方块类型 -> 1.16.4 方块类。只收录构造参数能可靠推断的类型。 */
    private static final Map<String, String> TYPE_TO_CLASS = new HashMap<>();
    /** 需要 base 方块状态的类型（构造签名为 (BlockState, Properties)）。 */
    private static final Set<String> NEEDS_BASE = new HashSet<>(Arrays.asList("stair"));

    /**
     * 类名 -> 要拼在 {@code Properties} 之前的固定构造参数。
     *
     * <p>压力板的构造是 {@code (Sensitivity, Properties)}。本批新增的压力板全是木质，
     * 与原版 {@code OAK_PRESSURE_PLATE} 一致用 {@code EVERYTHING}（掉落物也能触发），
     * 石质的原版用 {@code MOBS}。
     */
    private static final Map<String, String> EXTRA_FIRST_ARG = new HashMap<>();

    /**
     * 类名 -> 动态发光等级表达式。
     *
     * <p>官方数据里每个方块只给一个静态亮度，但蜡烛的亮度取决于点燃状态与支数
     * （{@code 3 * candles}）。照静态值生成会让熄灭的蜡烛也发光。
     */
    private static final Map<String, String> DYNAMIC_LIGHT = new HashMap<>();

    /**
     * <b>标识符</b> -> 动态发光等级表达式，优先级高于 {@link #DYNAMIC_LIGHT}。
     *
     * <p>为什么按类名不够：8 个铜灯共用一个类，但四个氧化阶段点亮时的亮度<b>各不相同</b>
     * （官方 {@code Blocks} 注册处 {@code lightLevel(litBlockEmission(N))}：铜灯 15、
     * 斑驳 12、锈蚀 8、氧化 4，打蜡变种用 {@code ofFullCopy} 所以与对应未打蜡相同）。
     * 按类名只能给出一个值。
     *
     * <p>而且这里不配就是<b>永远不发光</b>，不只是「熄灭时也亮」：
     * {@code block-props-1.21.11.csv} 提取的是默认状态 {@code lit=false} 的亮度，
     * 8 个铜灯那一列全是 0。
     */
    private static final Map<String, String> DYNAMIC_LIGHT_BY_ID = new HashMap<>();

    /**
     * 类名 -> 构造表达式模板，用来替换默认的 {@code new 类名(...)}。
     * {@code {ID}} 会替换成方块标识符字符串字面量，{@code {PROPS}} 替换成 props(...) 那一段。
     *
     * <p>紫水晶簇需要这个：{@code amethyst_cluster}、{@code large_/medium_/small_amethyst_bud}
     * 四个方块<b>共用同一个官方 type</b>，但高度和宽度不同
     * （官方 {@code Blocks.java:5925-5951} 分别是 (7,10)/(5,10)/(4,10)/(3,8)）。
     * 尺寸表放在 {@code ModernAmethystClusterBlock.forId} 里，生成器只管把 id 传进去，
     * 免得在这里再抄一份尺寸。
     */
    private static final Map<String, String> FACTORY = new HashMap<>();

    static {
        EXTRA_FIRST_ARG.put("PressurePlateBlock", "PressurePlateBlock.Sensitivity.EVERYTHING");
        DYNAMIC_LIGHT.put("ModernCandleBlock", "ModernCandleBlock.lightFromCandles()");
        DYNAMIC_LIGHT.put("ModernCandleCakeBlock", "ModernCandleCakeBlock.lightWhenLit()");
        // 红石矿被踩/被点击后发光（lit=true 时亮度 9），熄灭时为 0。
        // 官方提取到的是默认状态的亮度 0，照抄会让点亮的矿石不发光。
        // 与 1.16.4 原版 REDSTONE_ORE 的 setLightLevel(getLightValueLit(9)) 等价 ——
        // 那是 Blocks 的私有方法，跨类引用不了，用等价 lambda。
        DYNAMIC_LIGHT.put("RedstoneOreBlock", "state -> state.get(RedstoneOreBlock.LIT) ? 9 : 0");
        // 发光浆果亮 14、无浆果 0（官方 CaveVines 的 emission(14)）。
        DYNAMIC_LIGHT.put("ModernCaveVinesBlock", "ModernCaveVines.lightFromBerries()");
        DYNAMIC_LIGHT.put("ModernCaveVinesPlantBlock", "ModernCaveVines.lightFromBerries()");
        // 发光地衣：只要有任意一面就亮 7（官方 GlowLichenBlock 的 emission(7)）。
        DYNAMIC_LIGHT.put("ModernGlowLichenBlock", "ModernGlowLichenBlock.lightFromFaces()");
        putCopperBulbLight();
        FACTORY.put("ModernAmethystClusterBlock", "ModernAmethystClusterBlock.forId(\"{ID}\", {PROPS})");
    }

    /**
     * 8 个铜灯点亮时的亮度，取自官方 {@code Blocks} 注册处的
     * {@code lightLevel(litBlockEmission(N))}。打蜡变种官方用 {@code ofFullCopy}
     * 复制对应未打蜡的属性，所以亮度成对相同。
     */
    private static void putCopperBulbLight() {
        int[] levels = {15, 12, 8, 4};
        String[] stages = {"", "exposed_", "weathered_", "oxidized_"};
        for (int i = 0; i < stages.length; i++) {
            String expr = "ModernCopperBulbBlock.lightWhenLit(" + levels[i] + ")";
            DYNAMIC_LIGHT_BY_ID.put(stages[i] + "copper_bulb", expr);
            DYNAMIC_LIGHT_BY_ID.put("waxed_" + stages[i] + "copper_bulb", expr);
        }
    }

    /** 1.21 音效 -> 1.16.4 音效，按前缀/关键词归类，兜底 STONE。 */
    private static final Map<String, String> SOUND_EXACT = new HashMap<>();

    /**
     * 1.16.4 各方块类实际提供的状态属性（由 DumpProps 从运行中的注册表导出）。
     *
     * <p><b>这是最重要的一道校验。</b>官方 blockstate json 会为每个状态组合指定模型，
     * 例如 {@code waxed_copper_bulb} 有 {@code lit=false,powered=true} 这样的变体。
     * 如果生成的方块类提供不了同一组属性，模型就匹配不上，方块渲染成紫黑块。
     * 因此属性集必须<b>完全一致</b>，不一致的方块只能等专门类，不能用近似类凑。
     */
    private static final Map<String, Set<String>> CLASS_PROPS = new HashMap<>();

    /**
     * 需要 {@code notSolid()} 的方块类。1.16.4 的渲染器默认把方块当完整立方体，
     * 会剔除相邻面；镂空方块（门、活板门、栏杆、锁链、灯笼、树叶）不声明就会出现
     * <b>能从缝隙看穿地形</b>的现象。原版这些方块都显式调了 notSolid()。
     */
    private static final Set<String> NEEDS_NOT_SOLID = new HashSet<>(Arrays.asList(
            "DoorBlock", "TrapDoorBlock", "PaneBlock", "ChainBlock", "LanternBlock",
            "LeavesBlock", "ModernLeavesBlock", "ModernWaterloggedBlock"));

    /** 活板门额外禁止生怪，与原版 OAK_TRAPDOOR 一致。 */
    private static final Set<String> NEEDS_NO_SPAWN = new HashSet<>(Arrays.asList("TrapDoorBlock"));

    /**
     * 按 1.21 类型判定需要 {@code notSolid()} 的方块。有些镂空方块在 1.16.4 只能用普通
     * {@code Block} 承载（例如铜格栅），光看类名判断不出来，得看官方类型。
     *
     * <p><b>{@code copper_bulb_block} 曾经在这里，是错的。</b>官方铜灯<b>没有</b>
     * {@code noOcclusion()} —— 它是正常剔除相邻面的完整立方体，真正有的是
     * {@code isRedstoneConductor(Blocks::never)}（不导红石）。留着 {@code notSolid()}
     * 会让打蜡铜灯错误地关掉遮挡剔除，还和未打蜡的表现不一致。
     * 那个「不导红石」的语义现在由 {@code ModernCopperBulbBlock} 的构造用
     * {@code setOpaque((s,r,p) -> false)} 表达。
     */
    private static final Set<String> TYPES_NOT_SOLID = new HashSet<>(Arrays.asList(
            "waterlogged_transparent", "transparent", "half_transparent",
            "weathering_copper_grate"));

    /**
     * 高版本改名而非新增的标识符（{@code short_grass} 就是 1.16.4 的 {@code grass}、
     * {@code iron_chain} 就是 {@code chain}）。注册它们会在创造栏里出现两个一模一样的条目。
     *
     * <p>清单从 {@code ModernRegistry.RENAMED_TO_LEGACY} 解析，不在这里另写一份 ——
     * 原先这里手写 {@code {"iron_chain"}}，与 ModernRegistry 的四项脱节，
     * {@code short_grass} 就这么被注册成新方块了（见 {@link RenamedIds}）。
     */
    private static Map<String, String> renamed = Collections.emptyMap();

    /**
     * 可以从官方 blockstate 里裁掉的属性。
     *
     * <p>1.19 给树叶、1.21.9 给铜格栅加了 {@code waterlogged}，1.16.4 的对应类没有这个属性。
     * 但它只影响「泡在水里」的外观，裁掉 {@code waterlogged=true} 的变体、把
     * {@code waterlogged=false} 提升为默认，方块就能正常渲染 —— 比整块放弃要好。
     * 需要裁剪的方块会写进 {@code gen-blocks-trim.txt}，由 ExtractAssets 处理 blockstate。
     */
    private static final Set<String> TRIMMABLE_PROPS = new HashSet<>(Arrays.asList("waterlogged"));

    /** 需要裁剪 blockstate 变体的方块：方块名 -> 要裁掉的属性。 */
    private static final Map<String, String> trimNeeded = new LinkedHashMap<>();

    static {
        TYPE_TO_CLASS.put("block", "Block");
        // 本轮由 subagent 对着官方源码新写的专门类。属性集见 CLASS_PROPS，
        // 每个都逐项核过官方 blocks.json 的属性名与状态总数。
        TYPE_TO_CLASS.put("weathering_lightning_rod", "ModernLightningRodBlock");
        TYPE_TO_CLASS.put("lightning_rod", "ModernLightningRodBlock");
        TYPE_TO_CLASS.put("weathering_copper_bulb", "ModernCopperBulbBlock");
        TYPE_TO_CLASS.put("amethyst_cluster", "ModernAmethystClusterBlock");
        TYPE_TO_CLASS.put("pointed_dripstone", "ModernPointedDripstoneBlock");
        TYPE_TO_CLASS.put("cave_vines", "ModernCaveVinesBlock");
        TYPE_TO_CLASS.put("cave_vines_plant", "ModernCaveVinesPlantBlock");
        TYPE_TO_CLASS.put("glow_lichen", "ModernGlowLichenBlock");
        TYPE_TO_CLASS.put("sculk_vein", "ModernSculkVeinBlock");
        TYPE_TO_CLASS.put("sculk_catalyst", "ModernSculkCatalystBlock");
        TYPE_TO_CLASS.put("sculk_shrieker", "ModernSculkShriekerBlock");
        // resin_clump 的官方 type 就叫 multiface，用裸的六面附着基类。
        TYPE_TO_CLASS.put("multiface", "ModernMultifaceBlock");
        // 告示牌。1.16.4 的 AbstractSignBlock 本来就带 waterlogged，状态数与官方一致，
        // 普通告示牌用现成类即可；悬挂告示牌 1.16.4 完全没有，是新写的。
        // 四个类都提供了单参 (Properties) 构造，木种由注册名反推，生成器零特殊处理。
        TYPE_TO_CLASS.put("ceiling_hanging_sign", "ModernCeilingHangingSignBlock");
        TYPE_TO_CLASS.put("wall_hanging_sign", "ModernWallHangingSignBlock");
        TYPE_TO_CLASS.put("standing_sign", "StandingSignBlock");
        TYPE_TO_CLASS.put("wall_sign", "WallSignBlock");
        TYPE_TO_CLASS.put("rotated_pillar", "RotatedPillarBlock");
        TYPE_TO_CLASS.put("slab", "SlabBlock");
        TYPE_TO_CLASS.put("stair", "StairsBlock");
        TYPE_TO_CLASS.put("wall", "WallBlock");
        TYPE_TO_CLASS.put("fence", "FenceBlock");
        TYPE_TO_CLASS.put("fence_gate", "FenceGateBlock");
        TYPE_TO_CLASS.put("door", "DoorBlock");
        TYPE_TO_CLASS.put("trapdoor", "TrapDoorBlock");
        TYPE_TO_CLASS.put("drop_experience", "OreBlock");
        // 1.19 起树叶带 waterlogged，用补了这个属性的子类
        TYPE_TO_CLASS.put("leaves", "ModernLeavesBlock");
        TYPE_TO_CLASS.put("untinted_particle_leaves", "ModernLeavesBlock");
        TYPE_TO_CLASS.put("iron_bars", "PaneBlock");
        TYPE_TO_CLASS.put("chain", "ChainBlock");
        TYPE_TO_CLASS.put("lantern", "LanternBlock");
        // 本批新增的按钮与压力板全是木质（cherry / pale_oak / mangrove / bamboo）。
        // 石质变体若将来出现，需要在这里按材质分流到 StoneButtonBlock。
        TYPE_TO_CLASS.put("button", "WoodButtonBlock");
        TYPE_TO_CLASS.put("pressure_plate", "PressurePlateBlock");

        // --- 无额外状态属性、可直接用 1.16.4 现成类承载的类型 ---
        // 矿物与土石类：普通实心方块，属性、硬度、音效由官方数据驱动。
        TYPE_TO_CLASS.put("amethyst", "ModernAmethystBlock");
        TYPE_TO_CLASS.put("budding_amethyst", "ModernAmethystBlock");
        TYPE_TO_CLASS.put("mud", "Block");
        TYPE_TO_CLASS.put("rooted_dirt", "Block");
        TYPE_TO_CLASS.put("sculk", "Block");
        TYPE_TO_CLASS.put("powder_snow", "Block");
        TYPE_TO_CLASS.put("frogspawn", "Block");
        // 苔藓块的骨粉催生行为属于服务端逻辑，先按静态方块生成。
        TYPE_TO_CLASS.put("bonemealable_feature_placer", "Block");
        // 染色玻璃：GlassBlock 带透明相关的重写，比普通 Block 正确。
        TYPE_TO_CLASS.put("tinted_glass", "GlassBlock");
        // 植物类：BushBlock 提供「必须种在有效地面上」的语义，比普通 Block 贴近原版。
        TYPE_TO_CLASS.put("azalea", "BushBlock");
        TYPE_TO_CLASS.put("bush", "BushBlock");
        TYPE_TO_CLASS.put("firefly_bush", "BushBlock");
        TYPE_TO_CLASS.put("cactus_flower", "BushBlock");
        TYPE_TO_CLASS.put("eyeblossom", "BushBlock");
        TYPE_TO_CLASS.put("flower", "BushBlock");
        TYPE_TO_CLASS.put("short_dry_grass", "BushBlock");
        TYPE_TO_CLASS.put("tall_dry_grass", "BushBlock");
        TYPE_TO_CLASS.put("tall_grass", "BushBlock");
        TYPE_TO_CLASS.put("spore_blossom", "Block");
        // 只有 waterlogged 一个属性，正好是 ModernWaterloggedBlock 的形状。
        TYPE_TO_CLASS.put("mangrove_roots", "ModernWaterloggedBlock");
        TYPE_TO_CLASS.put("hanging_roots", "ModernWaterloggedBlock");
        TYPE_TO_CLASS.put("heavy_core", "ModernWaterloggedBlock");
        // 深层红石矿：1.16.4 的 RedstoneOreBlock 自带 lit 属性，签名正好对上。
        TYPE_TO_CLASS.put("redstone_ore", "RedstoneOreBlock");
        // 蜡烛与蜡烛蛋糕（1.17）：1.16.4 无对应类，用补齐了官方属性的自写类。
        TYPE_TO_CLASS.put("candle", "ModernCandleBlock");
        TYPE_TO_CLASS.put("candle_cake", "ModernCandleCakeBlock");
        TYPE_TO_CLASS.put("half_transparent", "Block");
        TYPE_TO_CLASS.put("transparent", "Block");
        // 整块且可含水（铜格栅）
        TYPE_TO_CLASS.put("waterlogged_transparent", "ModernWaterloggedBlock");
        // 铜氧化家族：基类可复用，先按静态方块生成，氧化行为后补。
        TYPE_TO_CLASS.put("weathering_copper_full", "Block");
        TYPE_TO_CLASS.put("weathering_copper_slab", "SlabBlock");
        TYPE_TO_CLASS.put("weathering_copper_stair", "StairsBlock");
        TYPE_TO_CLASS.put("weathering_copper_bar", "PaneBlock");
        TYPE_TO_CLASS.put("weathering_copper_chain", "ChainBlock");
        TYPE_TO_CLASS.put("weathering_copper_trap_door", "TrapDoorBlock");
        TYPE_TO_CLASS.put("weathering_copper_door", "DoorBlock");
        TYPE_TO_CLASS.put("weathering_copper_grate", "ModernWaterloggedBlock");
        TYPE_TO_CLASS.put("weathering_lantern", "LanternBlock");
        TYPE_TO_CLASS.put("copper_bulb_block", "ModernCopperBulbBlock");
        // 石质
        for (String s : new String[]{"DEEPSLATE", "DEEPSLATE_BRICKS", "DEEPSLATE_TILES",
                "POLISHED_DEEPSLATE", "TUFF", "TUFF_BRICKS", "POLISHED_TUFF", "CALCITE",
                "DRIPSTONE_BLOCK", "POINTED_DRIPSTONE", "MUD_BRICKS", "PACKED_MUD",
                "RESIN_BRICKS", "RESIN", "NETHER_BRICKS", "SCULK", "SCULK_CATALYST",
                "SCULK_SENSOR", "SCULK_SHRIEKER", "SCULK_VEIN", "TRIAL_SPAWNER", "VAULT",
                "SPAWNER", "HEAVY_CORE", "CREAKING_HEART", "EMPTY", "AMETHYST_CLUSTER",
                "LARGE_AMETHYST_BUD", "MEDIUM_AMETHYST_BUD", "SMALL_AMETHYST_BUD",
                "CHISELED_BOOKSHELF", "DRIED_GHAST"}) {
            SOUND_EXACT.put(s, "STONE");
        }
        // 金属
        for (String s : new String[]{"COPPER", "COPPER_BULB", "COPPER_GRATE",
                "COPPER_GOLEM_STATUE", "IRON", "NETHERITE_BLOCK", "SHELF"}) {
            SOUND_EXACT.put(s, "METAL");
        }
        // 木质
        for (String s : new String[]{"CHERRY_WOOD", "BAMBOO_WOOD", "NETHER_WOOD",
                "HANGING_SIGN", "BAMBOO_WOOD_HANGING_SIGN", "CHERRY_WOOD_HANGING_SIGN",
                "NETHER_WOOD_HANGING_SIGN", "MANGROVE_ROOTS"}) {
            SOUND_EXACT.put(s, "WOOD");
        }
        // 植物
        for (String s : new String[]{"GRASS", "AZALEA", "AZALEA_LEAVES", "FLOWERING_AZALEA",
                "CHERRY_LEAVES", "CHERRY_SAPLING", "MOSS", "MOSS_CARPET", "PINK_PETALS",
                "LEAF_LITTER", "CAVE_VINES", "HANGING_ROOTS", "ROOTS", "SPORE_BLOSSOM",
                "BIG_DRIPLEAF", "SMALL_DRIPLEAF", "FROGSPAWN", "LILY_PAD", "WEEPING_VINES",
                "NETHER_SPROUTS", "WART_BLOCK", "CACTUS_FLOWER", "GLOW_LICHEN", "HARD_CROP",
                "FROGLIGHT"}) {
            SOUND_EXACT.put(s, "PLANT");
        }
        // 土石渣
        for (String s : new String[]{"GRAVEL", "MUD", "MUDDY_MANGROVE_ROOTS", "ROOTED_DIRT",
                "SUSPICIOUS_SAND", "SUSPICIOUS_GRAVEL", "SPONGE", "WET_SPONGE"}) {
            SOUND_EXACT.put(s, "GROUND");
        }
        SOUND_EXACT.put("WOOL", "CLOTH");
        SOUND_EXACT.put("CANDLE", "CLOTH");
        SOUND_EXACT.put("COBWEB", "CLOTH");
        SOUND_EXACT.put("AMETHYST", "GLASS");
        SOUND_EXACT.put("POWDER_SNOW", "SNOW");
        SOUND_EXACT.put("HONEY_BLOCK", "HONEY");
        SOUND_EXACT.put("SLIME_BLOCK", "SLIME");
        SOUND_EXACT.put("BONE_BLOCK", "BONE");
        SOUND_EXACT.put("CORAL_BLOCK", "CORAL");
        SOUND_EXACT.put("NETHER_GOLD_ORE", "NETHER_GOLD");

        // 由 tools/crossversion 的 DumpProps 从 1.16.4 运行时注册表导出
        CLASS_PROPS.put("Block", props());
        CLASS_PROPS.put("OreBlock", props());
        CLASS_PROPS.put("RotatedPillarBlock", props("axis"));
        CLASS_PROPS.put("SlabBlock", props("type", "waterlogged"));
        CLASS_PROPS.put("StairsBlock", props("facing", "half", "shape", "waterlogged"));
        CLASS_PROPS.put("WallBlock", props("east", "north", "south", "up", "waterlogged", "west"));
        CLASS_PROPS.put("FenceBlock", props("east", "north", "south", "waterlogged", "west"));
        CLASS_PROPS.put("FenceGateBlock", props("facing", "in_wall", "open", "powered"));
        CLASS_PROPS.put("DoorBlock", props("facing", "half", "hinge", "open", "powered"));
        CLASS_PROPS.put("TrapDoorBlock", props("facing", "half", "open", "powered", "waterlogged"));
        CLASS_PROPS.put("LeavesBlock", props("distance", "persistent"));
        // 补了 waterlogged 的子类，见 ModernLeavesBlock / ModernWaterloggedBlock
        CLASS_PROPS.put("ModernLeavesBlock", props("distance", "persistent", "waterlogged"));
        CLASS_PROPS.put("ModernWaterloggedBlock", props("waterlogged"));
        CLASS_PROPS.put("PaneBlock", props("east", "north", "south", "waterlogged", "west"));
        CLASS_PROPS.put("ChainBlock", props("axis", "waterlogged"));
        CLASS_PROPS.put("LanternBlock", props("hanging", "waterlogged"));

        // 本轮新增的专门类。属性集由各自的 agent 逐个对着官方 blocks.json 核过，
        // 状态总数也对齐了（这是将来做「1.21 blockstate ID → 本地 ID」映射表的前提）。
        CLASS_PROPS.put("ModernLightningRodBlock", props("facing", "powered", "waterlogged"));
        CLASS_PROPS.put("ModernCopperBulbBlock", props("lit", "powered"));
        CLASS_PROPS.put("ModernAmethystBlock", props());
        CLASS_PROPS.put("ModernAmethystClusterBlock", props("facing", "waterlogged"));
        CLASS_PROPS.put("ModernPointedDripstoneBlock", props("thickness", "vertical_direction", "waterlogged"));
        CLASS_PROPS.put("ModernCaveVinesBlock", props("age", "berries"));
        // 官方 CaveVinesPlantBlock.createBlockStateDefinition 故意不调 super，所以没有 age。
        CLASS_PROPS.put("ModernCaveVinesPlantBlock", props("berries"));
        Set<String> sixFace = props("down", "east", "north", "south", "up", "waterlogged", "west");
        CLASS_PROPS.put("ModernMultifaceBlock", sixFace);
        CLASS_PROPS.put("ModernGlowLichenBlock", sixFace);
        CLASS_PROPS.put("ModernSculkVeinBlock", sixFace);
        CLASS_PROPS.put("ModernSculkCatalystBlock", props("bloom"));
        CLASS_PROPS.put("ModernSculkShriekerBlock", props("can_summon", "shrieking", "waterlogged"));
        CLASS_PROPS.put("ModernCeilingHangingSignBlock", props("attached", "rotation", "waterlogged"));
        CLASS_PROPS.put("ModernWallHangingSignBlock", props("facing", "waterlogged"));
        CLASS_PROPS.put("StandingSignBlock", props("rotation", "waterlogged"));
        CLASS_PROPS.put("WallSignBlock", props("facing", "waterlogged"));
    }

    /**
     * 墙上变体 -> 战利品表要沿用的那个方块（{@code .lootFrom(...)}）。
     *
     * <p>墙上告示牌<b>没有自己的物品</b>，破坏时必须掉落对应的地面/吊顶变体。
     * 原版 {@code OAK_WALL_SIGN} 就是写 {@code .lootFrom(Blocks.OAK_SIGN)}。
     * 漏了这条的后果是<b>挖掉什么都不掉</b>，而且不会在编译期暴露。
     *
     * <p>返回 null 表示不需要。命名规律取自官方：{@code X_wall_sign} 掉 {@code X_sign}，
     * {@code X_wall_hanging_sign} 掉 {@code X_hanging_sign}。
     */
    static String lootFromTarget(String id) {
        String source = null;
        if (id.endsWith("_wall_hanging_sign")) {
            source = id.replace("_wall_hanging_sign", "_hanging_sign");
        } else if (id.endsWith("_wall_sign")) {
            source = id.replace("_wall_sign", "_sign");
        }
        if (source == null) return null;
        // 目标可能是本批生成的（新木），也可能是 1.16.4 原版已有的（oak 等 8 种）。
        if (generated.contains(source)) return source.toUpperCase(Locale.ROOT);
        if (isVanilla1164(source)) return "Blocks." + source.toUpperCase(Locale.ROOT);
        return null;
    }

    private static Set<String> props(String... names) {
        return new TreeSet<>(Arrays.asList(names));
    }

    static JsonObject blocksJson;
    static Map<String, Props> props = new HashMap<>();
    static Map<Integer, String> colorNames = new HashMap<>();
    static Set<String> minePickaxe = new HashSet<>(), mineAxe = new HashSet<>(),
            mineShovel = new HashSet<>(), mineHoe = new HashSet<>();
    static Set<String> needsStone = new HashSet<>(), needsIron = new HashSet<>(), needsDiamond = new HashSet<>();
    static Set<String> generated = new LinkedHashSet<>();

    record Props(float hardness, float resistance, int mapColorId, int light, String sound,
                 boolean hasCollision, boolean randomTick, boolean isAir) {}

    public static void main(String[] args) throws Exception {
        Path repo = Paths.get(args.length > 0 ? args[0] : ".");
        Path diff = repo.resolve("docs/registry-diff");

        blocksJson = JsonParser.parseString(Files.readString(
                repo.resolve("1.21.11/reports/blocks.json"), StandardCharsets.UTF_8)).getAsJsonObject();
        renamed = RenamedIds.load(repo);
        loadProps(diff.resolve("block-props-1.21.11.csv"));
        loadColorNames(repo.resolve("src/main/java/net/minecraft/block/material/MaterialColor.java"));

        tagDir = diff.resolve("tags-block");
        minePickaxe = loadTag("mineable/pickaxe");
        mineAxe = loadTag("mineable/axe");
        mineShovel = loadTag("mineable/shovel");
        mineHoe = loadTag("mineable/hoe");
        needsStone = loadTag("needs_stone_tool");
        needsIron = loadTag("needs_iron_tool");
        needsDiamond = loadTag("needs_diamond_tool");
        System.out.printf("tag 展开后：pickaxe=%d axe=%d shovel=%d hoe=%d%n",
                minePickaxe.size(), mineAxe.size(), mineShovel.size(), mineHoe.size());

        List<String> officialOrder = Files.readAllLines(diff.resolve("official-blocks-1.21.11.txt"));
        Set<String> wanted = new HashSet<>(Files.readAllLines(diff.resolve("added-blocks-1.16.4-to-1.21.11.txt")));

        StringBuilder blocks = new StringBuilder();
        List<String> skipped = new ArrayList<>();
        Map<String, Integer> skippedTypes = new TreeMap<>();

        for (String id : officialOrder) {
            if (!wanted.contains(id)) continue;
            if (renamed.containsKey(id)) {
                skipped.add(id);
                skippedTypes.merge("(改名而非新增，见 ModernRegistry.normalize)", 1, Integer::sum);
                continue;
            }
            String type = typeOf(id);
            String cls = TYPE_TO_CLASS.get(type);
            if (cls == null) {
                skipped.add(id);
                skippedTypes.merge(type, 1, Integer::sum);
                continue;
            }

            // 状态属性必须与 1.16.4 类完全一致，否则官方 blockstate 的部分变体匹配不到
            // 模型，渲染成紫黑块。唯一例外是可裁剪属性（waterlogged）：多出来时把对应
            // 变体裁掉即可，不必整块放弃。
            Set<String> official = officialProps(id);
            Set<String> provided = CLASS_PROPS.get(cls);
            if (provided != null && !provided.equals(official)) {
                Set<String> extra = new TreeSet<>(official);
                extra.removeAll(provided);
                boolean onlyTrimmable = !extra.isEmpty() && TRIMMABLE_PROPS.containsAll(extra)
                        && provided.containsAll(subtract(official, extra));

                if (onlyTrimmable) {
                    trimNeeded.put(id, String.join(",", extra));
                } else {
                    skipped.add(id);
                    skippedTypes.merge(type + " 属性不匹配 官方"
                            + (official.isEmpty() ? "{}" : official) + " vs " + cls
                            + (provided.isEmpty() ? "{}" : provided), 1, Integer::sum);
                    continue;
                }
            }

            String line = emit(id, type, cls);
            if (line == null) {
                skipped.add(id);
                skippedTypes.merge(type + "(缺 base)", 1, Integer::sum);
                continue;
            }
            blocks.append(line);
            generated.add(id);
        }

        Files.writeString(repo.resolve("target/crossversion-check/gen-blocks.java.txt"),
                blocks.toString(), StandardCharsets.UTF_8);
        Files.write(repo.resolve("target/crossversion-check/gen-blocks-done.txt"), generated);
        Files.write(repo.resolve("target/crossversion-check/gen-blocks-skipped.txt"), skipped);

        List<String> trimLines = new ArrayList<>();
        for (Map.Entry<String, String> e : trimNeeded.entrySet()) {
            if (generated.contains(e.getKey())) trimLines.add(e.getKey() + ":" + e.getValue());
        }
        Files.write(repo.resolve("target/crossversion-check/gen-blocks-trim.txt"), trimLines);

        System.out.printf("生成 %d 个方块，跳过 %d 个，其中 %d 个需要裁剪 blockstate 变体%n",
                generated.size(), skipped.size(), trimLines.size());
        System.out.println("跳过的类型分布：");
        skippedTypes.entrySet().stream().sorted((a, b) -> b.getValue() - a.getValue()).limit(20)
                .forEach(e -> System.out.printf("  %-34s %d%n", e.getKey(), e.getValue()));
    }

    static String typeOf(String id) {
        JsonElement e = blocksJson.get("minecraft:" + id);
        if (e == null) return "(不存在)";
        return e.getAsJsonObject().getAsJsonObject("definition").get("type").getAsString()
                .replace("minecraft:", "");
    }

    static Set<String> subtract(Set<String> a, Set<String> b) {
        Set<String> out = new TreeSet<>(a);
        out.removeAll(b);
        return out;
    }

    /** 官方 blockstate 的状态属性名集合。 */
    static Set<String> officialProps(String id) {
        JsonElement e = blocksJson.get("minecraft:" + id);
        if (e == null) return Collections.emptySet();
        JsonElement p = e.getAsJsonObject().get("properties");
        if (p == null || !p.isJsonObject()) return Collections.emptySet();
        return new TreeSet<>(p.getAsJsonObject().keySet());
    }

    /** 生成一行注册代码；需要 base 但推断不出时返回 null。 */
    static String emit(String id, String type, String cls) {
        Props p = props.get(id);
        if (p == null) return null;

        String base = null;
        if (NEEDS_BASE.contains(type) || cls.equals("StairsBlock")) {
            base = inferBase(id);
            if (base == null) return null;
        }

        // 蜡烛蛋糕的构造要传它插的是哪种蜡烛，用于吃掉时掉落、以及建立
        // 「手持蜡烛右键蛋糕」的反查映射。命名规律：white_candle_cake -> white_candle，
        // 无色的 candle_cake -> candle。
        if (cls.equals("ModernCandleCakeBlock")) {
            String candle = id.substring(0, id.length() - "_cake".length());
            if (!generated.contains(candle)) return null;
            base = candle.toUpperCase(Locale.ROOT);
        }

        StringBuilder sb = new StringBuilder();
        sb.append("    public static final Block ").append(id.toUpperCase(Locale.ROOT))
          .append(" = register(\"").append(id).append("\", ");

        StringBuilder ctorArgs = new StringBuilder();
        if (base != null) {
            ctorArgs.append(base).append(", ");
        } else {
            String extra = EXTRA_FIRST_ARG.get(cls);
            if (extra != null) {
                ctorArgs.append(extra).append(", ");
            }
        }
        StringBuilder propsExpr = new StringBuilder();
        propsExpr.append("props(Material.").append(material(id, p, cls)).append(", ")
          .append(color(p.mapColorId())).append(", ")
          .append(fmt(p.hardness())).append(", ").append(fmt(p.resistance())).append(")");
        if (requiresTool(id)) propsExpr.append(".setRequiresTool()");
        // 蜡烛的亮度随点燃状态与支数变化，官方是 3 * candles，静态值表达不了。
        // props 里提取到的是「某个状态」的亮度，直接用会让灭掉的蜡烛也发光。
        // 按 id 的表更精确（铜灯四个氧化阶段亮度各不相同），优先于按类名的表。
        String dynamicLight = DYNAMIC_LIGHT_BY_ID.get(id);
        if (dynamicLight == null) dynamicLight = DYNAMIC_LIGHT.get(cls);
        if (dynamicLight != null) {
            propsExpr.append(".setLightLevel(").append(dynamicLight).append(")");
        } else if (p.light() > 0) {
            propsExpr.append(".setLightLevel(state -> ").append(p.light()).append(")");
        }
        if (p.randomTick()) propsExpr.append(".tickRandomly()");
        if (!p.hasCollision()) propsExpr.append(".doesNotBlockMovement()");
        propsExpr.append(".sound(SoundType.").append(sound(p.sound())).append(")");
        // 镂空方块必须声明，否则相邻面被剔除，能从缝隙看穿地形
        if (NEEDS_NOT_SOLID.contains(cls) || TYPES_NOT_SOLID.contains(type)) propsExpr.append(".notSolid()");
        // 与原版 OAK_TRAPDOOR 一致禁止生怪。原版写的是 Blocks::neverAllowSpawn，
        // 但那是私有方法，跨类引用不了，用等价 lambda。
        if (NEEDS_NO_SPAWN.contains(cls)) propsExpr.append(".setAllowsSpawn((s, r, p2, t) -> false)");
        // 墙上告示牌没有自己的物品，必须沿用地面/吊顶变体的战利品表，否则挖了不掉东西。
        String lootFrom = lootFromTarget(id);
        if (lootFrom != null) propsExpr.append(".lootFrom(").append(lootFrom).append(")");

        String factory = FACTORY.get(cls);
        if (factory != null) {
            // 工厂方法自己带参数列表，ctorArgs 那套（base / EXTRA_FIRST_ARG）用不上。
            sb.append(factory.replace("{ID}", id).replace("{PROPS}", propsExpr.toString()));
        } else {
            sb.append("new ").append(cls).append("(").append(ctorArgs).append(propsExpr).append(")");
        }
        sb.append(");\n");
        return sb.toString();
    }

    /**
     * 推断楼梯的 base 方块。官方命名规律是去掉 _stairs 后缀，砖类还要还原复数
     * （deepslate_brick_stairs 的 base 是 deepslate_bricks）。
     * base 必须是原版已有或本批已生成的方块，否则返回 null 交给人工处理。
     */
    static String inferBase(String id) {
        String stem = id.endsWith("_stairs") ? id.substring(0, id.length() - "_stairs".length()) : null;
        if (stem == null) return null;
        // 木质楼梯的 base 是 <木>_planks（cherry_stairs -> cherry_planks），
        // 石质是本体（tuff_stairs -> tuff），砖类要还原复数（deepslate_brick_stairs -> deepslate_bricks）。
        for (String candidate : new String[]{stem, stem + "_planks", stem + "s",
                stem.replace("_brick", "_bricks")}) {
            if (generated.contains(candidate)) {
                return candidate.toUpperCase(Locale.ROOT) + ".getDefaultState()";
            }
            if (blocksJson.has("minecraft:" + candidate) && isVanilla1164(candidate)) {
                return "Blocks." + candidate.toUpperCase(Locale.ROOT) + ".getDefaultState()";
            }
        }
        return null;
    }

    static Set<String> vanilla1164;

    static boolean isVanilla1164(String id) {
        if (vanilla1164 == null) {
            try {
                vanilla1164 = new HashSet<>(Files.readAllLines(
                        Paths.get("docs/registry-diff/baseline-1.16.4-blocks.txt")));
            } catch (Exception e) {
                vanilla1164 = Collections.emptySet();
            }
        }
        return vanilla1164.contains(id);
    }

    /**
     * 推断 1.16.4 的 Material。官方 1.20 起移除了 Material 系统，只能从挖掘 tag 反推：
     * 这正是 Material 在 1.16.4 里的主要作用（决定哪种工具有效）。
     *
     * <p><b>门与活板门不能用 {@code Material.IRON}。</b>
     * {@code DoorBlock.onBlockActivated} 与 {@code TrapDoorBlock} 都会检查
     * {@code material == Material.IRON} 并直接返回 PASS —— 那是铁门「只能红石开」的语义。
     * 铜门在 1.21 是可以手开的，所以这里退到 {@code ROCK}：一样能用镐挖
     * （{@code PickaxeItem} 对 ROCK 与 IRON 同等对待），但不会被当成铁门。
     */
    static String material(String id, Props p, String cls) {
        if (p.isAir()) return "AIR";

        boolean doorLike = "DoorBlock".equals(cls) || "TrapDoorBlock".equals(cls);

        if (minePickaxe.contains(id)) {
            boolean metal = id.contains("copper") || id.contains("iron") || id.contains("gold")
                    || id.contains("netherite") || id.contains("chain") || id.contains("lantern")
                    || id.contains("bulb") || id.contains("grate") || id.contains("bars");
            if (metal && doorLike) return "ROCK";
            return metal ? "IRON" : "ROCK";
        }
        if (mineAxe.contains(id)) return "WOOD";
        if (mineShovel.contains(id)) return id.contains("sand") ? "SAND" : "EARTH";
        if (mineHoe.contains(id)) return id.contains("leaves") ? "LEAVES" : "PLANTS";
        return "ROCK";
    }

    /** 1.16.4 的 requiresTool 对应 1.21 的「需要正确工具才掉落」。 */
    static boolean requiresTool(String id) {
        return needsStone.contains(id) || needsIron.contains(id) || needsDiamond.contains(id)
                || minePickaxe.contains(id);
    }

    static String color(int id) {
        String name = colorNames.get(id);
        // 名字不存在时按 id 取：1.21 与 1.16.4 的常量名大量不同，但 id 稳定。
        return name != null ? "MaterialColor." + name : "MaterialColor.COLORS[" + id + "]";
    }

    static String sound(String s) {
        String mapped = SOUND_EXACT.get(s);
        if (mapped != null) return mapped;
        return s;
    }

    /** 生成 Java float 字面量。用 Float.toString 保证往返精度（0.75 不会变成 0.8）。 */
    static String fmt(float f) {
        return f + "F";
    }

    static void loadProps(Path csv) throws Exception {
        List<String> lines = Files.readAllLines(csv, StandardCharsets.UTF_8);
        for (int i = 1; i < lines.size(); i++) {
            String[] c = lines.get(i).split(",");
            if (c.length < 12) continue;
            props.put(c[0], new Props(Float.parseFloat(c[1]), Float.parseFloat(c[2]),
                    Integer.parseInt(c[3]), Integer.parseInt(c[4]), c[5],
                    Boolean.parseBoolean(c[6]), Boolean.parseBoolean(c[7]), Boolean.parseBoolean(c[8])));
        }
    }

    /** 从 1.16.4 源码解析 id -> 常量名，这样生成的代码可读（MaterialColor.DEEPSLATE）。 */
    static void loadColorNames(Path javaFile) throws Exception {
        for (String line : Files.readAllLines(javaFile, StandardCharsets.UTF_8)) {
            String t = line.trim();
            int eq = t.indexOf(" = new MaterialColor(");
            if (!t.startsWith("public static final MaterialColor ") || eq < 0) continue;
            String name = t.substring("public static final MaterialColor ".length(), eq);
            String rest = t.substring(eq + " = new MaterialColor(".length());
            int comma = rest.indexOf(',');
            if (comma < 0) continue;
            try {
                colorNames.put(Integer.parseInt(rest.substring(0, comma).trim()), name);
            } catch (NumberFormatException ignored) {
            }
        }
    }

    /** tag 名 -> 展开后的方块集合，缓存兼防循环引用。 */
    static final Map<String, Set<String>> TAG_CACHE = new HashMap<>();
    static Path tagDir;

    /**
     * 递归展开方块 tag。
     *
     * <p>官方 tag 大量使用 {@code #minecraft:planks} 这样的 tag 引用，
     * 不展开会导致漏判 —— 例如 {@code cherry_planks} 只通过 {@code #minecraft:planks}
     * 出现在 {@code mineable/axe} 里，不展开就会被推断成石头材质。
     *
     * <p>values 的元素可能是字符串，也可能是 {@code {"id": "...", "required": false}} 对象。
     */
    static Set<String> loadTag(String tagName) {
        Set<String> cached = TAG_CACHE.get(tagName);
        if (cached != null) return cached;

        Set<String> out = new HashSet<>();
        TAG_CACHE.put(tagName, out); // 先放进去，循环引用时直接拿到空集而不是无限递归

        Path path = tagDir.resolve(tagName + ".json");
        if (!Files.exists(path)) return out;

        try {
            JsonObject root = JsonParser.parseString(
                    Files.readString(path, StandardCharsets.UTF_8)).getAsJsonObject();
            JsonElement values = root.get("values");
            if (values == null || !values.isJsonArray()) return out;

            for (JsonElement e : values.getAsJsonArray()) {
                String v;
                if (e.isJsonObject()) {
                    JsonElement idEl = e.getAsJsonObject().get("id");
                    if (idEl == null) continue;
                    v = idEl.getAsString();
                } else {
                    v = e.getAsString();
                }
                v = v.replace("minecraft:", "");
                if (v.startsWith("#")) {
                    out.addAll(loadTag(v.substring(1)));
                } else {
                    out.add(v);
                }
            }
        } catch (Exception e) {
            System.err.println("读取 tag 失败 " + tagName + ": " + e);
        }
        return out;
    }
}
