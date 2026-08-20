package net.minecraft.crossversion;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.minecraft.block.Block;
import net.minecraft.item.Item;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.registry.Registry;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * 跨版本注册表扩展的统一入口。
 *
 * <p>本客户端原生是 1.16.4（物品 raw ID 0-975、方块 0-762、方块状态 0-17111）。这些 ID
 * 由源码书写顺序决定，且被网络协议直接使用，因此<b>绝对不能移位</b> —— 一旦移位，连接
 * 1.8 / 1.12 / 1.16.4 服务器时所有物品都会错位。所有 1.17+ 的新内容只能<b>追加</b>在这
 * 之后，由 {@code ModernBlocks} 与 {@code ModernItems} 在原版类初始化完毕后注册。
 *
 * <p>1.21.11 的原生 ID 与本地扩展 ID <b>不同</b>（例如 deepslate 在 1.21.11 是 8，在这里
 * 是 763 之后的某个值），所以跨版本映射必须以<b>标识符字符串</b>为准，不能直接搬 ID。
 * 这个类持有那份映射。
 */
public final class ModernRegistry {
    private static final Logger LOGGER = LogManager.getLogger();

    /** 原版 1.16.4 的物品数量；raw ID 0-975 必须与原版逐一相同。 */
    public static final int VANILLA_ITEM_COUNT = 976;
    /** 原版 1.16.4 的方块数量；raw ID 0-762 必须与原版逐一相同。 */
    public static final int VANILLA_BLOCK_COUNT = 763;
    /** 原版 1.16.4 的方块状态数量。 */
    public static final int VANILLA_BLOCK_STATE_COUNT = 17112;

    /**
     * 高版本改名的注册项：高版本标识符 -> 本客户端的 1.16.4 标识符。
     * 这些不是新增内容，而是同一个东西换了名字，映射时必须先归一化。
     */
    private static final Map<String, String> RENAMED_TO_LEGACY = new HashMap<>();

    static {
        RENAMED_TO_LEGACY.put("short_grass", "grass");
        RENAMED_TO_LEGACY.put("dirt_path", "grass_path");
        RENAMED_TO_LEGACY.put("turtle_scute", "scute");
        // 1.21.9 把 chain 改名成 iron_chain。它不是新方块 —— 注册一个同物的新条目
        // 会让创造栏里出现两个一模一样的锁链。
        RENAMED_TO_LEGACY.put("iron_chain", "chain");
    }

    /** 由 ModernItems / ModernBlocks 在注册时登记，用于版本门控。 */
    private static final Set<Item> EXTENDED_ITEMS = new HashSet<>();
    private static final Set<Block> EXTENDED_BLOCKS = new HashSet<>();

    private ModernRegistry() {
    }

    /**
     * 把高版本标识符归一化成本客户端认识的标识符。对改名项做还原，其余原样返回。
     * 传入可以带或不带 {@code minecraft:} 前缀。
     */
    public static String normalize(String modernIdentifier) {
        String path = modernIdentifier.startsWith("minecraft:")
                ? modernIdentifier.substring("minecraft:".length())
                : modernIdentifier;
        return RENAMED_TO_LEGACY.getOrDefault(path, path);
    }

    /**
     * 按高版本标识符取物品。找不到返回 {@code null} —— 调用方负责决定回退策略，
     * 这里不返回 AIR，以免把「查不到」和「真的是空气」混在一起。
     */
    public static Item itemByModernId(String modernIdentifier) {
        ResourceLocation key = new ResourceLocation(normalize(modernIdentifier));
        return Registry.ITEM.keySet().contains(key) ? Registry.ITEM.getOrDefault(key) : null;
    }

    /** 按高版本标识符取方块。语义同 {@link #itemByModernId}。 */
    public static Block blockByModernId(String modernIdentifier) {
        ResourceLocation key = new ResourceLocation(normalize(modernIdentifier));
        return Registry.BLOCK.keySet().contains(key) ? Registry.BLOCK.getOrDefault(key) : null;
    }

    /** 由 {@code ModernItems} / {@code ModernBlocks} 在注册时调用，用于版本门控。 */
    public static void markExtended(Item item) {
        EXTENDED_ITEMS.add(item);
    }

    /** 由 {@code ModernItems} / {@code ModernBlocks} 在注册时调用，用于版本门控。 */
    public static void markExtended(Block block) {
        EXTENDED_BLOCKS.add(block);
    }

    /**
     * 该物品是否属于 1.17+ 的扩展内容。连接 1.16.4 或更老的服务器时，这些物品不能出现在
     * 创造栏、配方里，也不能出现在发往服务器的封包中 —— 老版本的 Via 映射表只覆盖
     * 0-975，遇到更大的 ID 会变成空气甚至断连。
     */
    public static boolean isExtended(Item item) {
        return EXTENDED_ITEMS.contains(item);
    }

    public static boolean isExtended(Block block) {
        return EXTENDED_BLOCKS.contains(block);
    }

    public static Set<Item> extendedItems() {
        return Collections.unmodifiableSet(EXTENDED_ITEMS);
    }

    public static Set<Block> extendedBlocks() {
        return Collections.unmodifiableSet(EXTENDED_BLOCKS);
    }

    // ---------------------------------------------------------------------
    // 创造模式物品栏顺序
    // ---------------------------------------------------------------------

    /**
     * 打包在 classpath 里的创造栏顺序表，一行一个标识符。
     *
     * <p>顺序是<b>官方创造栏</b>的 tab 顺序 + tab 内序号，不是 protocol_id。
     * 由 {@code tools/crossversion/GenerateItems.java} 从
     * {@code docs/registry-diff/official-creative-tabs-1.21.11.csv} 生成。
     */
    private static final String ORDER_RESOURCE = "/crossversion/item-order-1.21.11.txt";

    private static volatile List<Item> creativeOrder;

    /**
     * 创造模式物品栏的排列顺序。
     *
     * <p>原版 {@code ItemGroup.fill} 直接按 {@link Registry#ITEM} 的迭代顺序（也就是 raw ID
     * 顺序）填充。扩展内容的 raw ID 必须追加在原版之后（否则协议错位），结果就是新物品全部
     * 堆在创造栏末尾 —— 深板岩不在石头旁边，而在最后一页。
     *
     * <p>这里把「显示顺序」与「raw ID 顺序」解耦：按<b>官方创造栏</b>的排列顺序，
     * 让新旧物品交错回它们应该在的位置。注意官方的创造栏顺序与注册顺序（protocol_id）
     * <b>并不相同</b> —— 早先按 protocol_id 排，分类对了位置仍然不对，就是这个原因。
     *
     * <p>表里没有的物品（1.16.4 有而 1.21.11 已移除的、或官方创造栏本身就不收的，
     * 例如成书、知识之书、石化橡木台阶）排在最后，保持稳定。
     */
    public static List<Item> creativeOrder() {
        List<Item> cached = creativeOrder;
        if (cached != null) {
            return cached;
        }

        synchronized (ModernRegistry.class) {
            if (creativeOrder != null) {
                return creativeOrder;
            }
            creativeOrder = buildCreativeOrder();
            return creativeOrder;
        }
    }

    private static List<Item> buildCreativeOrder() {
        List<String> order = readOrderTable();
        if (order.isEmpty()) {
            // 读不到顺序表就退回原版行为，绝不能因为缺一个资源文件就让创造栏空掉。
            List<Item> fallback = new ArrayList<>();
            Registry.ITEM.forEach(fallback::add);
            return Collections.unmodifiableList(fallback);
        }

        List<Item> sorted = new ArrayList<>(Registry.ITEM.keySet().size());
        Set<Item> placed = new HashSet<>();

        for (String identifier : order) {
            Item item = itemByModernId(identifier);
            if (item != null && placed.add(item)) {
                sorted.add(item);
            }
        }
        // 顺序表里没有的（1.21.11 已移除或改名的原版物品）追加在末尾。
        for (Item item : Registry.ITEM) {
            if (placed.add(item)) {
                sorted.add(item);
            }
        }

        return Collections.unmodifiableList(sorted);
    }

    private static List<String> readOrderTable() {
        try (InputStream in = ModernRegistry.class.getResourceAsStream(ORDER_RESOURCE)) {
            if (in == null) {
                LOGGER.warn("[CrossVersion] 找不到物品顺序表 {}，创造栏退回注册顺序", ORDER_RESOURCE);
                return Collections.emptyList();
            }
            List<String> lines = new ArrayList<>();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    String trimmed = line.trim();
                    if (!trimmed.isEmpty() && !trimmed.startsWith("#")) {
                        lines.add(trimmed);
                    }
                }
            }
            return lines;
        } catch (IOException e) {
            LOGGER.warn("[CrossVersion] 读取物品顺序表失败，创造栏退回注册顺序", e);
            return Collections.emptyList();
        }
    }
}
