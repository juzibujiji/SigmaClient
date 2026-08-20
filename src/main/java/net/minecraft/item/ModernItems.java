package net.minecraft.item;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import net.minecraft.block.Block;
import net.minecraft.block.ModernBlocks;
import net.minecraft.crossversion.ModernRegistry;
import net.minecraft.entity.item.BoatEntity;
import net.minecraft.inventory.EquipmentSlotType;
import net.minecraft.util.registry.Registry;

/**
 * 1.17-1.21.11 新增物品的注册表扩展。
 *
 * <p><b>字段区由代码生成器产出</b>，不要手工编辑。生成器：
 * {@code tools/crossversion/GenerateItems.java}。数值取自官方 1.21.11 的
 * {@code items.json} 默认组件（堆叠上限、耐久、稀有度、防火）。
 *
 * <p><b>初始化顺序是硬约束。</b>本类必须在 {@link Items} 完全初始化之后才加载，这样物品
 * raw ID 的排布是：原版 0-975，扩展内容从 976 起。任何插入到原版之前的注册都会让所有
 * 原版 ID 移位，导致连接 1.8 / 1.12 / 1.16.4 服务器时物品全部错位。
 *
 * <p>字段顺序按 1.21.11 的注册顺序，使生成器输出稳定可 diff。注意这个顺序<b>不是</b>
 * 创造栏顺序 —— 创造栏由 {@link ModernRegistry#creativeOrder()} 单独排列。
 */
public final class ModernItems {
    /** 本类注册的物品，按注册顺序。 */
    private static final List<Item> REGISTERED = new ArrayList<>();

    // === 以下字段由生成器产出，勿手工编辑 =================================
    public static final Item DEEPSLATE = register(ModernBlocks.DEEPSLATE, ItemGroup.BUILDING_BLOCKS);
    public static final Item COBBLED_DEEPSLATE = register(ModernBlocks.COBBLED_DEEPSLATE, ItemGroup.BUILDING_BLOCKS);
    public static final Item POLISHED_DEEPSLATE = register(ModernBlocks.POLISHED_DEEPSLATE, ItemGroup.BUILDING_BLOCKS);
    public static final Item CALCITE = register(ModernBlocks.CALCITE, ItemGroup.BUILDING_BLOCKS);
    public static final Item TUFF = register(ModernBlocks.TUFF, ItemGroup.BUILDING_BLOCKS);
    public static final Item TUFF_SLAB = register(ModernBlocks.TUFF_SLAB, ItemGroup.BUILDING_BLOCKS);
    public static final Item TUFF_STAIRS = register(ModernBlocks.TUFF_STAIRS, ItemGroup.BUILDING_BLOCKS);
    public static final Item TUFF_WALL = register(ModernBlocks.TUFF_WALL, ItemGroup.DECORATIONS);
    public static final Item CHISELED_TUFF = register(ModernBlocks.CHISELED_TUFF, ItemGroup.BUILDING_BLOCKS);
    public static final Item POLISHED_TUFF = register(ModernBlocks.POLISHED_TUFF, ItemGroup.BUILDING_BLOCKS);
    public static final Item POLISHED_TUFF_SLAB = register(ModernBlocks.POLISHED_TUFF_SLAB, ItemGroup.BUILDING_BLOCKS);
    public static final Item POLISHED_TUFF_STAIRS = register(ModernBlocks.POLISHED_TUFF_STAIRS, ItemGroup.BUILDING_BLOCKS);
    public static final Item POLISHED_TUFF_WALL = register(ModernBlocks.POLISHED_TUFF_WALL, ItemGroup.DECORATIONS);
    public static final Item TUFF_BRICKS = register(ModernBlocks.TUFF_BRICKS, ItemGroup.BUILDING_BLOCKS);
    public static final Item TUFF_BRICK_SLAB = register(ModernBlocks.TUFF_BRICK_SLAB, ItemGroup.BUILDING_BLOCKS);
    public static final Item TUFF_BRICK_STAIRS = register(ModernBlocks.TUFF_BRICK_STAIRS, ItemGroup.BUILDING_BLOCKS);
    public static final Item TUFF_BRICK_WALL = register(ModernBlocks.TUFF_BRICK_WALL, ItemGroup.DECORATIONS);
    public static final Item CHISELED_TUFF_BRICKS = register(ModernBlocks.CHISELED_TUFF_BRICKS, ItemGroup.BUILDING_BLOCKS);
    public static final Item DRIPSTONE_BLOCK = register(ModernBlocks.DRIPSTONE_BLOCK, ItemGroup.BUILDING_BLOCKS);
    public static final Item ROOTED_DIRT = register(ModernBlocks.ROOTED_DIRT, ItemGroup.BUILDING_BLOCKS);
    public static final Item MUD = register(ModernBlocks.MUD, ItemGroup.BUILDING_BLOCKS);
    public static final Item CHERRY_PLANKS = register(ModernBlocks.CHERRY_PLANKS, ItemGroup.BUILDING_BLOCKS);
    public static final Item PALE_OAK_PLANKS = register(ModernBlocks.PALE_OAK_PLANKS, ItemGroup.BUILDING_BLOCKS);
    public static final Item MANGROVE_PLANKS = register(ModernBlocks.MANGROVE_PLANKS, ItemGroup.BUILDING_BLOCKS);
    public static final Item BAMBOO_PLANKS = register(ModernBlocks.BAMBOO_PLANKS, ItemGroup.BUILDING_BLOCKS);
    public static final Item BAMBOO_MOSAIC = register(ModernBlocks.BAMBOO_MOSAIC, ItemGroup.BUILDING_BLOCKS);
    public static final Item DEEPSLATE_COAL_ORE = register(ModernBlocks.DEEPSLATE_COAL_ORE, ItemGroup.BUILDING_BLOCKS);
    public static final Item DEEPSLATE_IRON_ORE = register(ModernBlocks.DEEPSLATE_IRON_ORE, ItemGroup.BUILDING_BLOCKS);
    public static final Item COPPER_ORE = register(ModernBlocks.COPPER_ORE, ItemGroup.BUILDING_BLOCKS);
    public static final Item DEEPSLATE_COPPER_ORE = register(ModernBlocks.DEEPSLATE_COPPER_ORE, ItemGroup.BUILDING_BLOCKS);
    public static final Item DEEPSLATE_GOLD_ORE = register(ModernBlocks.DEEPSLATE_GOLD_ORE, ItemGroup.BUILDING_BLOCKS);
    public static final Item DEEPSLATE_REDSTONE_ORE = register(ModernBlocks.DEEPSLATE_REDSTONE_ORE, ItemGroup.BUILDING_BLOCKS);
    public static final Item DEEPSLATE_EMERALD_ORE = register(ModernBlocks.DEEPSLATE_EMERALD_ORE, ItemGroup.BUILDING_BLOCKS);
    public static final Item DEEPSLATE_LAPIS_ORE = register(ModernBlocks.DEEPSLATE_LAPIS_ORE, ItemGroup.BUILDING_BLOCKS);
    public static final Item DEEPSLATE_DIAMOND_ORE = register(ModernBlocks.DEEPSLATE_DIAMOND_ORE, ItemGroup.BUILDING_BLOCKS);
    public static final Item RAW_IRON_BLOCK = register(ModernBlocks.RAW_IRON_BLOCK, ItemGroup.BUILDING_BLOCKS);
    public static final Item RAW_COPPER_BLOCK = register(ModernBlocks.RAW_COPPER_BLOCK, ItemGroup.BUILDING_BLOCKS);
    public static final Item RAW_GOLD_BLOCK = register(ModernBlocks.RAW_GOLD_BLOCK, ItemGroup.BUILDING_BLOCKS);
    public static final Item HEAVY_CORE = register(ModernBlocks.HEAVY_CORE, ItemGroup.MISC);
    public static final Item AMETHYST_BLOCK = register(ModernBlocks.AMETHYST_BLOCK, ItemGroup.BUILDING_BLOCKS);
    public static final Item BUDDING_AMETHYST = register(ModernBlocks.BUDDING_AMETHYST, ItemGroup.BUILDING_BLOCKS);
    public static final Item COPPER_BLOCK = register(ModernBlocks.COPPER_BLOCK, ItemGroup.BUILDING_BLOCKS);
    public static final Item EXPOSED_COPPER = register(ModernBlocks.EXPOSED_COPPER, ItemGroup.BUILDING_BLOCKS);
    public static final Item WEATHERED_COPPER = register(ModernBlocks.WEATHERED_COPPER, ItemGroup.BUILDING_BLOCKS);
    public static final Item OXIDIZED_COPPER = register(ModernBlocks.OXIDIZED_COPPER, ItemGroup.BUILDING_BLOCKS);
    public static final Item CHISELED_COPPER = register(ModernBlocks.CHISELED_COPPER, ItemGroup.BUILDING_BLOCKS);
    public static final Item EXPOSED_CHISELED_COPPER = register(ModernBlocks.EXPOSED_CHISELED_COPPER, ItemGroup.BUILDING_BLOCKS);
    public static final Item WEATHERED_CHISELED_COPPER = register(ModernBlocks.WEATHERED_CHISELED_COPPER, ItemGroup.BUILDING_BLOCKS);
    public static final Item OXIDIZED_CHISELED_COPPER = register(ModernBlocks.OXIDIZED_CHISELED_COPPER, ItemGroup.BUILDING_BLOCKS);
    public static final Item CUT_COPPER = register(ModernBlocks.CUT_COPPER, ItemGroup.BUILDING_BLOCKS);
    public static final Item EXPOSED_CUT_COPPER = register(ModernBlocks.EXPOSED_CUT_COPPER, ItemGroup.BUILDING_BLOCKS);
    public static final Item WEATHERED_CUT_COPPER = register(ModernBlocks.WEATHERED_CUT_COPPER, ItemGroup.BUILDING_BLOCKS);
    public static final Item OXIDIZED_CUT_COPPER = register(ModernBlocks.OXIDIZED_CUT_COPPER, ItemGroup.BUILDING_BLOCKS);
    public static final Item CUT_COPPER_STAIRS = register(ModernBlocks.CUT_COPPER_STAIRS, ItemGroup.BUILDING_BLOCKS);
    public static final Item EXPOSED_CUT_COPPER_STAIRS = register(ModernBlocks.EXPOSED_CUT_COPPER_STAIRS, ItemGroup.BUILDING_BLOCKS);
    public static final Item WEATHERED_CUT_COPPER_STAIRS = register(ModernBlocks.WEATHERED_CUT_COPPER_STAIRS, ItemGroup.BUILDING_BLOCKS);
    public static final Item OXIDIZED_CUT_COPPER_STAIRS = register(ModernBlocks.OXIDIZED_CUT_COPPER_STAIRS, ItemGroup.BUILDING_BLOCKS);
    public static final Item CUT_COPPER_SLAB = register(ModernBlocks.CUT_COPPER_SLAB, ItemGroup.BUILDING_BLOCKS);
    public static final Item EXPOSED_CUT_COPPER_SLAB = register(ModernBlocks.EXPOSED_CUT_COPPER_SLAB, ItemGroup.BUILDING_BLOCKS);
    public static final Item WEATHERED_CUT_COPPER_SLAB = register(ModernBlocks.WEATHERED_CUT_COPPER_SLAB, ItemGroup.BUILDING_BLOCKS);
    public static final Item OXIDIZED_CUT_COPPER_SLAB = register(ModernBlocks.OXIDIZED_CUT_COPPER_SLAB, ItemGroup.BUILDING_BLOCKS);
    public static final Item WAXED_COPPER_BLOCK = register(ModernBlocks.WAXED_COPPER_BLOCK, ItemGroup.BUILDING_BLOCKS);
    public static final Item WAXED_EXPOSED_COPPER = register(ModernBlocks.WAXED_EXPOSED_COPPER, ItemGroup.BUILDING_BLOCKS);
    public static final Item WAXED_WEATHERED_COPPER = register(ModernBlocks.WAXED_WEATHERED_COPPER, ItemGroup.BUILDING_BLOCKS);
    public static final Item WAXED_OXIDIZED_COPPER = register(ModernBlocks.WAXED_OXIDIZED_COPPER, ItemGroup.BUILDING_BLOCKS);
    public static final Item WAXED_CHISELED_COPPER = register(ModernBlocks.WAXED_CHISELED_COPPER, ItemGroup.BUILDING_BLOCKS);
    public static final Item WAXED_EXPOSED_CHISELED_COPPER = register(ModernBlocks.WAXED_EXPOSED_CHISELED_COPPER, ItemGroup.BUILDING_BLOCKS);
    public static final Item WAXED_WEATHERED_CHISELED_COPPER = register(ModernBlocks.WAXED_WEATHERED_CHISELED_COPPER, ItemGroup.BUILDING_BLOCKS);
    public static final Item WAXED_OXIDIZED_CHISELED_COPPER = register(ModernBlocks.WAXED_OXIDIZED_CHISELED_COPPER, ItemGroup.BUILDING_BLOCKS);
    public static final Item WAXED_CUT_COPPER = register(ModernBlocks.WAXED_CUT_COPPER, ItemGroup.BUILDING_BLOCKS);
    public static final Item WAXED_EXPOSED_CUT_COPPER = register(ModernBlocks.WAXED_EXPOSED_CUT_COPPER, ItemGroup.BUILDING_BLOCKS);
    public static final Item WAXED_WEATHERED_CUT_COPPER = register(ModernBlocks.WAXED_WEATHERED_CUT_COPPER, ItemGroup.BUILDING_BLOCKS);
    public static final Item WAXED_OXIDIZED_CUT_COPPER = register(ModernBlocks.WAXED_OXIDIZED_CUT_COPPER, ItemGroup.BUILDING_BLOCKS);
    public static final Item WAXED_CUT_COPPER_STAIRS = register(ModernBlocks.WAXED_CUT_COPPER_STAIRS, ItemGroup.BUILDING_BLOCKS);
    public static final Item WAXED_EXPOSED_CUT_COPPER_STAIRS = register(ModernBlocks.WAXED_EXPOSED_CUT_COPPER_STAIRS, ItemGroup.BUILDING_BLOCKS);
    public static final Item WAXED_WEATHERED_CUT_COPPER_STAIRS = register(ModernBlocks.WAXED_WEATHERED_CUT_COPPER_STAIRS, ItemGroup.BUILDING_BLOCKS);
    public static final Item WAXED_OXIDIZED_CUT_COPPER_STAIRS = register(ModernBlocks.WAXED_OXIDIZED_CUT_COPPER_STAIRS, ItemGroup.BUILDING_BLOCKS);
    public static final Item WAXED_CUT_COPPER_SLAB = register(ModernBlocks.WAXED_CUT_COPPER_SLAB, ItemGroup.BUILDING_BLOCKS);
    public static final Item WAXED_EXPOSED_CUT_COPPER_SLAB = register(ModernBlocks.WAXED_EXPOSED_CUT_COPPER_SLAB, ItemGroup.BUILDING_BLOCKS);
    public static final Item WAXED_WEATHERED_CUT_COPPER_SLAB = register(ModernBlocks.WAXED_WEATHERED_CUT_COPPER_SLAB, ItemGroup.BUILDING_BLOCKS);
    public static final Item WAXED_OXIDIZED_CUT_COPPER_SLAB = register(ModernBlocks.WAXED_OXIDIZED_CUT_COPPER_SLAB, ItemGroup.BUILDING_BLOCKS);
    public static final Item CHERRY_LOG = register(ModernBlocks.CHERRY_LOG, ItemGroup.BUILDING_BLOCKS);
    public static final Item PALE_OAK_LOG = register(ModernBlocks.PALE_OAK_LOG, ItemGroup.BUILDING_BLOCKS);
    public static final Item MANGROVE_LOG = register(ModernBlocks.MANGROVE_LOG, ItemGroup.BUILDING_BLOCKS);
    public static final Item MANGROVE_ROOTS = register(ModernBlocks.MANGROVE_ROOTS, ItemGroup.BUILDING_BLOCKS);
    public static final Item MUDDY_MANGROVE_ROOTS = register(ModernBlocks.MUDDY_MANGROVE_ROOTS, ItemGroup.BUILDING_BLOCKS);
    public static final Item BAMBOO_BLOCK = register(ModernBlocks.BAMBOO_BLOCK, ItemGroup.BUILDING_BLOCKS);
    public static final Item STRIPPED_CHERRY_LOG = register(ModernBlocks.STRIPPED_CHERRY_LOG, ItemGroup.BUILDING_BLOCKS);
    public static final Item STRIPPED_PALE_OAK_LOG = register(ModernBlocks.STRIPPED_PALE_OAK_LOG, ItemGroup.BUILDING_BLOCKS);
    public static final Item STRIPPED_MANGROVE_LOG = register(ModernBlocks.STRIPPED_MANGROVE_LOG, ItemGroup.BUILDING_BLOCKS);
    public static final Item STRIPPED_CHERRY_WOOD = register(ModernBlocks.STRIPPED_CHERRY_WOOD, ItemGroup.BUILDING_BLOCKS);
    public static final Item STRIPPED_PALE_OAK_WOOD = register(ModernBlocks.STRIPPED_PALE_OAK_WOOD, ItemGroup.BUILDING_BLOCKS);
    public static final Item STRIPPED_MANGROVE_WOOD = register(ModernBlocks.STRIPPED_MANGROVE_WOOD, ItemGroup.BUILDING_BLOCKS);
    public static final Item STRIPPED_BAMBOO_BLOCK = register(ModernBlocks.STRIPPED_BAMBOO_BLOCK, ItemGroup.BUILDING_BLOCKS);
    public static final Item CHERRY_WOOD = register(ModernBlocks.CHERRY_WOOD, ItemGroup.BUILDING_BLOCKS);
    public static final Item PALE_OAK_WOOD = register(ModernBlocks.PALE_OAK_WOOD, ItemGroup.BUILDING_BLOCKS);
    public static final Item MANGROVE_WOOD = register(ModernBlocks.MANGROVE_WOOD, ItemGroup.BUILDING_BLOCKS);
    public static final Item CHERRY_LEAVES = register(ModernBlocks.CHERRY_LEAVES, ItemGroup.DECORATIONS);
    public static final Item PALE_OAK_LEAVES = register(ModernBlocks.PALE_OAK_LEAVES, ItemGroup.DECORATIONS);
    public static final Item AZALEA_LEAVES = register(ModernBlocks.AZALEA_LEAVES, ItemGroup.DECORATIONS);
    public static final Item FLOWERING_AZALEA_LEAVES = register(ModernBlocks.FLOWERING_AZALEA_LEAVES, ItemGroup.DECORATIONS);
    public static final Item TINTED_GLASS = register(ModernBlocks.TINTED_GLASS, ItemGroup.BUILDING_BLOCKS);
    public static final Item BUSH = register(ModernBlocks.BUSH, ItemGroup.DECORATIONS);
    public static final Item AZALEA = register(ModernBlocks.AZALEA, ItemGroup.DECORATIONS);
    public static final Item FLOWERING_AZALEA = register(ModernBlocks.FLOWERING_AZALEA, ItemGroup.DECORATIONS);
    public static final Item FIREFLY_BUSH = register(ModernBlocks.FIREFLY_BUSH, ItemGroup.DECORATIONS);
    public static final Item SHORT_DRY_GRASS = register(ModernBlocks.SHORT_DRY_GRASS, ItemGroup.DECORATIONS);
    public static final Item TALL_DRY_GRASS = register(ModernBlocks.TALL_DRY_GRASS, ItemGroup.DECORATIONS);
    public static final Item OPEN_EYEBLOSSOM = register(ModernBlocks.OPEN_EYEBLOSSOM, ItemGroup.DECORATIONS);
    public static final Item CLOSED_EYEBLOSSOM = register(ModernBlocks.CLOSED_EYEBLOSSOM, ItemGroup.DECORATIONS);
    public static final Item TORCHFLOWER = register(ModernBlocks.TORCHFLOWER, ItemGroup.DECORATIONS);
    public static final Item SPORE_BLOSSOM = register(ModernBlocks.SPORE_BLOSSOM, ItemGroup.DECORATIONS);
    public static final Item MOSS_BLOCK = register(ModernBlocks.MOSS_BLOCK, ItemGroup.BUILDING_BLOCKS);
    public static final Item PALE_MOSS_BLOCK = register(ModernBlocks.PALE_MOSS_BLOCK, ItemGroup.BUILDING_BLOCKS);
    public static final Item HANGING_ROOTS = register(ModernBlocks.HANGING_ROOTS, ItemGroup.DECORATIONS);
    public static final Item CHERRY_SLAB = register(ModernBlocks.CHERRY_SLAB, ItemGroup.BUILDING_BLOCKS);
    public static final Item PALE_OAK_SLAB = register(ModernBlocks.PALE_OAK_SLAB, ItemGroup.BUILDING_BLOCKS);
    public static final Item MANGROVE_SLAB = register(ModernBlocks.MANGROVE_SLAB, ItemGroup.BUILDING_BLOCKS);
    public static final Item BAMBOO_SLAB = register(ModernBlocks.BAMBOO_SLAB, ItemGroup.BUILDING_BLOCKS);
    public static final Item BAMBOO_MOSAIC_SLAB = register(ModernBlocks.BAMBOO_MOSAIC_SLAB, ItemGroup.BUILDING_BLOCKS);
    public static final Item MUD_BRICK_SLAB = register(ModernBlocks.MUD_BRICK_SLAB, ItemGroup.BUILDING_BLOCKS);
    public static final Item CACTUS_FLOWER = register(ModernBlocks.CACTUS_FLOWER, ItemGroup.DECORATIONS);
    public static final Item CHERRY_FENCE = register(ModernBlocks.CHERRY_FENCE, ItemGroup.DECORATIONS);
    public static final Item PALE_OAK_FENCE = register(ModernBlocks.PALE_OAK_FENCE, ItemGroup.DECORATIONS);
    public static final Item MANGROVE_FENCE = register(ModernBlocks.MANGROVE_FENCE, ItemGroup.DECORATIONS);
    public static final Item BAMBOO_FENCE = register(ModernBlocks.BAMBOO_FENCE, ItemGroup.DECORATIONS);
    public static final Item SMOOTH_BASALT = register(ModernBlocks.SMOOTH_BASALT, ItemGroup.BUILDING_BLOCKS);
    public static final Item PACKED_MUD = register(ModernBlocks.PACKED_MUD, ItemGroup.BUILDING_BLOCKS);
    public static final Item MUD_BRICKS = register(ModernBlocks.MUD_BRICKS, ItemGroup.BUILDING_BLOCKS);
    public static final Item DEEPSLATE_BRICKS = register(ModernBlocks.DEEPSLATE_BRICKS, ItemGroup.BUILDING_BLOCKS);
    public static final Item CRACKED_DEEPSLATE_BRICKS = register(ModernBlocks.CRACKED_DEEPSLATE_BRICKS, ItemGroup.BUILDING_BLOCKS);
    public static final Item DEEPSLATE_TILES = register(ModernBlocks.DEEPSLATE_TILES, ItemGroup.BUILDING_BLOCKS);
    public static final Item CRACKED_DEEPSLATE_TILES = register(ModernBlocks.CRACKED_DEEPSLATE_TILES, ItemGroup.BUILDING_BLOCKS);
    public static final Item CHISELED_DEEPSLATE = register(ModernBlocks.CHISELED_DEEPSLATE, ItemGroup.BUILDING_BLOCKS);
    public static final Item REINFORCED_DEEPSLATE = register(ModernBlocks.REINFORCED_DEEPSLATE, ItemGroup.BUILDING_BLOCKS);
    public static final Item COPPER_BARS = register(ModernBlocks.COPPER_BARS, ItemGroup.DECORATIONS);
    public static final Item EXPOSED_COPPER_BARS = register(ModernBlocks.EXPOSED_COPPER_BARS, ItemGroup.DECORATIONS);
    public static final Item WEATHERED_COPPER_BARS = register(ModernBlocks.WEATHERED_COPPER_BARS, ItemGroup.DECORATIONS);
    public static final Item OXIDIZED_COPPER_BARS = register(ModernBlocks.OXIDIZED_COPPER_BARS, ItemGroup.DECORATIONS);
    public static final Item WAXED_COPPER_BARS = register(ModernBlocks.WAXED_COPPER_BARS, ItemGroup.DECORATIONS);
    public static final Item WAXED_EXPOSED_COPPER_BARS = register(ModernBlocks.WAXED_EXPOSED_COPPER_BARS, ItemGroup.DECORATIONS);
    public static final Item WAXED_WEATHERED_COPPER_BARS = register(ModernBlocks.WAXED_WEATHERED_COPPER_BARS, ItemGroup.DECORATIONS);
    public static final Item WAXED_OXIDIZED_COPPER_BARS = register(ModernBlocks.WAXED_OXIDIZED_COPPER_BARS, ItemGroup.DECORATIONS);
    public static final Item COPPER_CHAIN = register(ModernBlocks.COPPER_CHAIN, ItemGroup.DECORATIONS);
    public static final Item EXPOSED_COPPER_CHAIN = register(ModernBlocks.EXPOSED_COPPER_CHAIN, ItemGroup.DECORATIONS);
    public static final Item WEATHERED_COPPER_CHAIN = register(ModernBlocks.WEATHERED_COPPER_CHAIN, ItemGroup.DECORATIONS);
    public static final Item OXIDIZED_COPPER_CHAIN = register(ModernBlocks.OXIDIZED_COPPER_CHAIN, ItemGroup.DECORATIONS);
    public static final Item WAXED_COPPER_CHAIN = register(ModernBlocks.WAXED_COPPER_CHAIN, ItemGroup.DECORATIONS);
    public static final Item WAXED_EXPOSED_COPPER_CHAIN = register(ModernBlocks.WAXED_EXPOSED_COPPER_CHAIN, ItemGroup.DECORATIONS);
    public static final Item WAXED_WEATHERED_COPPER_CHAIN = register(ModernBlocks.WAXED_WEATHERED_COPPER_CHAIN, ItemGroup.DECORATIONS);
    public static final Item WAXED_OXIDIZED_COPPER_CHAIN = register(ModernBlocks.WAXED_OXIDIZED_COPPER_CHAIN, ItemGroup.DECORATIONS);
    public static final Item GLOW_LICHEN = register(ModernBlocks.GLOW_LICHEN, ItemGroup.DECORATIONS);
    public static final Item RESIN_CLUMP = register(ModernBlocks.RESIN_CLUMP, ItemGroup.MISC);
    public static final Item RESIN_BLOCK = register(ModernBlocks.RESIN_BLOCK, ItemGroup.BUILDING_BLOCKS);
    public static final Item RESIN_BRICKS = register(ModernBlocks.RESIN_BRICKS, ItemGroup.BUILDING_BLOCKS);
    public static final Item RESIN_BRICK_STAIRS = register(ModernBlocks.RESIN_BRICK_STAIRS, ItemGroup.BUILDING_BLOCKS);
    public static final Item RESIN_BRICK_SLAB = register(ModernBlocks.RESIN_BRICK_SLAB, ItemGroup.BUILDING_BLOCKS);
    public static final Item RESIN_BRICK_WALL = register(ModernBlocks.RESIN_BRICK_WALL, ItemGroup.DECORATIONS);
    public static final Item CHISELED_RESIN_BRICKS = register(ModernBlocks.CHISELED_RESIN_BRICKS, ItemGroup.BUILDING_BLOCKS);
    public static final Item MUD_BRICK_STAIRS = register(ModernBlocks.MUD_BRICK_STAIRS, ItemGroup.BUILDING_BLOCKS);
    public static final Item SCULK = register(ModernBlocks.SCULK, ItemGroup.DECORATIONS);
    public static final Item SCULK_VEIN = register(ModernBlocks.SCULK_VEIN, ItemGroup.DECORATIONS);
    public static final Item SCULK_CATALYST = register(ModernBlocks.SCULK_CATALYST, ItemGroup.DECORATIONS);
    public static final Item SCULK_SHRIEKER = register(ModernBlocks.SCULK_SHRIEKER, ItemGroup.REDSTONE);
    public static final Item CHERRY_STAIRS = register(ModernBlocks.CHERRY_STAIRS, ItemGroup.BUILDING_BLOCKS);
    public static final Item PALE_OAK_STAIRS = register(ModernBlocks.PALE_OAK_STAIRS, ItemGroup.BUILDING_BLOCKS);
    public static final Item MANGROVE_STAIRS = register(ModernBlocks.MANGROVE_STAIRS, ItemGroup.BUILDING_BLOCKS);
    public static final Item BAMBOO_STAIRS = register(ModernBlocks.BAMBOO_STAIRS, ItemGroup.BUILDING_BLOCKS);
    public static final Item BAMBOO_MOSAIC_STAIRS = register(ModernBlocks.BAMBOO_MOSAIC_STAIRS, ItemGroup.BUILDING_BLOCKS);
    public static final Item MUD_BRICK_WALL = register(ModernBlocks.MUD_BRICK_WALL, ItemGroup.DECORATIONS);
    public static final Item COBBLED_DEEPSLATE_WALL = register(ModernBlocks.COBBLED_DEEPSLATE_WALL, ItemGroup.DECORATIONS);
    public static final Item POLISHED_DEEPSLATE_WALL = register(ModernBlocks.POLISHED_DEEPSLATE_WALL, ItemGroup.DECORATIONS);
    public static final Item DEEPSLATE_BRICK_WALL = register(ModernBlocks.DEEPSLATE_BRICK_WALL, ItemGroup.DECORATIONS);
    public static final Item DEEPSLATE_TILE_WALL = register(ModernBlocks.DEEPSLATE_TILE_WALL, ItemGroup.DECORATIONS);
    public static final Item COBBLED_DEEPSLATE_STAIRS = register(ModernBlocks.COBBLED_DEEPSLATE_STAIRS, ItemGroup.BUILDING_BLOCKS);
    public static final Item POLISHED_DEEPSLATE_STAIRS = register(ModernBlocks.POLISHED_DEEPSLATE_STAIRS, ItemGroup.BUILDING_BLOCKS);
    public static final Item DEEPSLATE_BRICK_STAIRS = register(ModernBlocks.DEEPSLATE_BRICK_STAIRS, ItemGroup.BUILDING_BLOCKS);
    public static final Item DEEPSLATE_TILE_STAIRS = register(ModernBlocks.DEEPSLATE_TILE_STAIRS, ItemGroup.BUILDING_BLOCKS);
    public static final Item COBBLED_DEEPSLATE_SLAB = register(ModernBlocks.COBBLED_DEEPSLATE_SLAB, ItemGroup.BUILDING_BLOCKS);
    public static final Item POLISHED_DEEPSLATE_SLAB = register(ModernBlocks.POLISHED_DEEPSLATE_SLAB, ItemGroup.BUILDING_BLOCKS);
    public static final Item DEEPSLATE_BRICK_SLAB = register(ModernBlocks.DEEPSLATE_BRICK_SLAB, ItemGroup.BUILDING_BLOCKS);
    public static final Item DEEPSLATE_TILE_SLAB = register(ModernBlocks.DEEPSLATE_TILE_SLAB, ItemGroup.BUILDING_BLOCKS);
    public static final Item LIGHTNING_ROD = register(ModernBlocks.LIGHTNING_ROD, ItemGroup.DECORATIONS);
    public static final Item EXPOSED_LIGHTNING_ROD = register(ModernBlocks.EXPOSED_LIGHTNING_ROD, ItemGroup.DECORATIONS);
    public static final Item WEATHERED_LIGHTNING_ROD = register(ModernBlocks.WEATHERED_LIGHTNING_ROD, ItemGroup.DECORATIONS);
    public static final Item OXIDIZED_LIGHTNING_ROD = register(ModernBlocks.OXIDIZED_LIGHTNING_ROD, ItemGroup.DECORATIONS);
    public static final Item WAXED_LIGHTNING_ROD = register(ModernBlocks.WAXED_LIGHTNING_ROD, ItemGroup.DECORATIONS);
    public static final Item WAXED_EXPOSED_LIGHTNING_ROD = register(ModernBlocks.WAXED_EXPOSED_LIGHTNING_ROD, ItemGroup.DECORATIONS);
    public static final Item WAXED_WEATHERED_LIGHTNING_ROD = register(ModernBlocks.WAXED_WEATHERED_LIGHTNING_ROD, ItemGroup.DECORATIONS);
    public static final Item WAXED_OXIDIZED_LIGHTNING_ROD = register(ModernBlocks.WAXED_OXIDIZED_LIGHTNING_ROD, ItemGroup.DECORATIONS);
    public static final Item CHERRY_BUTTON = register(ModernBlocks.CHERRY_BUTTON, ItemGroup.REDSTONE);
    public static final Item PALE_OAK_BUTTON = register(ModernBlocks.PALE_OAK_BUTTON, ItemGroup.REDSTONE);
    public static final Item MANGROVE_BUTTON = register(ModernBlocks.MANGROVE_BUTTON, ItemGroup.REDSTONE);
    public static final Item BAMBOO_BUTTON = register(ModernBlocks.BAMBOO_BUTTON, ItemGroup.REDSTONE);
    public static final Item CHERRY_PRESSURE_PLATE = register(ModernBlocks.CHERRY_PRESSURE_PLATE, ItemGroup.REDSTONE);
    public static final Item PALE_OAK_PRESSURE_PLATE = register(ModernBlocks.PALE_OAK_PRESSURE_PLATE, ItemGroup.REDSTONE);
    public static final Item MANGROVE_PRESSURE_PLATE = register(ModernBlocks.MANGROVE_PRESSURE_PLATE, ItemGroup.REDSTONE);
    public static final Item BAMBOO_PRESSURE_PLATE = register(ModernBlocks.BAMBOO_PRESSURE_PLATE, ItemGroup.REDSTONE);
    public static final Item CHERRY_DOOR = register(ModernBlocks.CHERRY_DOOR, ItemGroup.REDSTONE);
    public static final Item PALE_OAK_DOOR = register(ModernBlocks.PALE_OAK_DOOR, ItemGroup.REDSTONE);
    public static final Item MANGROVE_DOOR = register(ModernBlocks.MANGROVE_DOOR, ItemGroup.REDSTONE);
    public static final Item BAMBOO_DOOR = register(ModernBlocks.BAMBOO_DOOR, ItemGroup.REDSTONE);
    public static final Item COPPER_DOOR = register(ModernBlocks.COPPER_DOOR, ItemGroup.REDSTONE);
    public static final Item EXPOSED_COPPER_DOOR = register(ModernBlocks.EXPOSED_COPPER_DOOR, ItemGroup.REDSTONE);
    public static final Item WEATHERED_COPPER_DOOR = register(ModernBlocks.WEATHERED_COPPER_DOOR, ItemGroup.REDSTONE);
    public static final Item OXIDIZED_COPPER_DOOR = register(ModernBlocks.OXIDIZED_COPPER_DOOR, ItemGroup.REDSTONE);
    public static final Item WAXED_COPPER_DOOR = register(ModernBlocks.WAXED_COPPER_DOOR, ItemGroup.REDSTONE);
    public static final Item WAXED_EXPOSED_COPPER_DOOR = register(ModernBlocks.WAXED_EXPOSED_COPPER_DOOR, ItemGroup.REDSTONE);
    public static final Item WAXED_WEATHERED_COPPER_DOOR = register(ModernBlocks.WAXED_WEATHERED_COPPER_DOOR, ItemGroup.REDSTONE);
    public static final Item WAXED_OXIDIZED_COPPER_DOOR = register(ModernBlocks.WAXED_OXIDIZED_COPPER_DOOR, ItemGroup.REDSTONE);
    public static final Item CHERRY_TRAPDOOR = register(ModernBlocks.CHERRY_TRAPDOOR, ItemGroup.REDSTONE);
    public static final Item PALE_OAK_TRAPDOOR = register(ModernBlocks.PALE_OAK_TRAPDOOR, ItemGroup.REDSTONE);
    public static final Item MANGROVE_TRAPDOOR = register(ModernBlocks.MANGROVE_TRAPDOOR, ItemGroup.REDSTONE);
    public static final Item BAMBOO_TRAPDOOR = register(ModernBlocks.BAMBOO_TRAPDOOR, ItemGroup.REDSTONE);
    public static final Item COPPER_TRAPDOOR = register(ModernBlocks.COPPER_TRAPDOOR, ItemGroup.REDSTONE);
    public static final Item EXPOSED_COPPER_TRAPDOOR = register(ModernBlocks.EXPOSED_COPPER_TRAPDOOR, ItemGroup.REDSTONE);
    public static final Item WEATHERED_COPPER_TRAPDOOR = register(ModernBlocks.WEATHERED_COPPER_TRAPDOOR, ItemGroup.REDSTONE);
    public static final Item OXIDIZED_COPPER_TRAPDOOR = register(ModernBlocks.OXIDIZED_COPPER_TRAPDOOR, ItemGroup.REDSTONE);
    public static final Item WAXED_COPPER_TRAPDOOR = register(ModernBlocks.WAXED_COPPER_TRAPDOOR, ItemGroup.REDSTONE);
    public static final Item WAXED_EXPOSED_COPPER_TRAPDOOR = register(ModernBlocks.WAXED_EXPOSED_COPPER_TRAPDOOR, ItemGroup.REDSTONE);
    public static final Item WAXED_WEATHERED_COPPER_TRAPDOOR = register(ModernBlocks.WAXED_WEATHERED_COPPER_TRAPDOOR, ItemGroup.REDSTONE);
    public static final Item WAXED_OXIDIZED_COPPER_TRAPDOOR = register(ModernBlocks.WAXED_OXIDIZED_COPPER_TRAPDOOR, ItemGroup.REDSTONE);
    public static final Item CHERRY_FENCE_GATE = register(ModernBlocks.CHERRY_FENCE_GATE, ItemGroup.REDSTONE);
    public static final Item PALE_OAK_FENCE_GATE = register(ModernBlocks.PALE_OAK_FENCE_GATE, ItemGroup.REDSTONE);
    public static final Item MANGROVE_FENCE_GATE = register(ModernBlocks.MANGROVE_FENCE_GATE, ItemGroup.REDSTONE);
    public static final Item BAMBOO_FENCE_GATE = register(ModernBlocks.BAMBOO_FENCE_GATE, ItemGroup.REDSTONE);
    public static final Item WHITE_HARNESS = register("white_harness", new Item(new Item.Properties().group(ItemGroup.TRANSPORTATION).maxStackSize(1)));
    public static final Item ORANGE_HARNESS = register("orange_harness", new Item(new Item.Properties().group(ItemGroup.TRANSPORTATION).maxStackSize(1)));
    public static final Item MAGENTA_HARNESS = register("magenta_harness", new Item(new Item.Properties().group(ItemGroup.TRANSPORTATION).maxStackSize(1)));
    public static final Item LIGHT_BLUE_HARNESS = register("light_blue_harness", new Item(new Item.Properties().group(ItemGroup.TRANSPORTATION).maxStackSize(1)));
    public static final Item YELLOW_HARNESS = register("yellow_harness", new Item(new Item.Properties().group(ItemGroup.TRANSPORTATION).maxStackSize(1)));
    public static final Item LIME_HARNESS = register("lime_harness", new Item(new Item.Properties().group(ItemGroup.TRANSPORTATION).maxStackSize(1)));
    public static final Item PINK_HARNESS = register("pink_harness", new Item(new Item.Properties().group(ItemGroup.TRANSPORTATION).maxStackSize(1)));
    public static final Item GRAY_HARNESS = register("gray_harness", new Item(new Item.Properties().group(ItemGroup.TRANSPORTATION).maxStackSize(1)));
    public static final Item LIGHT_GRAY_HARNESS = register("light_gray_harness", new Item(new Item.Properties().group(ItemGroup.TRANSPORTATION).maxStackSize(1)));
    public static final Item CYAN_HARNESS = register("cyan_harness", new Item(new Item.Properties().group(ItemGroup.TRANSPORTATION).maxStackSize(1)));
    public static final Item PURPLE_HARNESS = register("purple_harness", new Item(new Item.Properties().group(ItemGroup.TRANSPORTATION).maxStackSize(1)));
    public static final Item BLUE_HARNESS = register("blue_harness", new Item(new Item.Properties().group(ItemGroup.TRANSPORTATION).maxStackSize(1)));
    public static final Item BROWN_HARNESS = register("brown_harness", new Item(new Item.Properties().group(ItemGroup.TRANSPORTATION).maxStackSize(1)));
    public static final Item GREEN_HARNESS = register("green_harness", new Item(new Item.Properties().group(ItemGroup.TRANSPORTATION).maxStackSize(1)));
    public static final Item RED_HARNESS = register("red_harness", new Item(new Item.Properties().group(ItemGroup.TRANSPORTATION).maxStackSize(1)));
    public static final Item BLACK_HARNESS = register("black_harness", new Item(new Item.Properties().group(ItemGroup.TRANSPORTATION).maxStackSize(1)));
    public static final Item OAK_CHEST_BOAT = register("oak_chest_boat", new ChestBoatItem(BoatEntity.Type.OAK, new Item.Properties().group(ItemGroup.TRANSPORTATION).maxStackSize(1)));
    public static final Item SPRUCE_CHEST_BOAT = register("spruce_chest_boat", new ChestBoatItem(BoatEntity.Type.SPRUCE, new Item.Properties().group(ItemGroup.TRANSPORTATION).maxStackSize(1)));
    public static final Item BIRCH_CHEST_BOAT = register("birch_chest_boat", new ChestBoatItem(BoatEntity.Type.BIRCH, new Item.Properties().group(ItemGroup.TRANSPORTATION).maxStackSize(1)));
    public static final Item JUNGLE_CHEST_BOAT = register("jungle_chest_boat", new ChestBoatItem(BoatEntity.Type.JUNGLE, new Item.Properties().group(ItemGroup.TRANSPORTATION).maxStackSize(1)));
    public static final Item ACACIA_CHEST_BOAT = register("acacia_chest_boat", new ChestBoatItem(BoatEntity.Type.ACACIA, new Item.Properties().group(ItemGroup.TRANSPORTATION).maxStackSize(1)));
    public static final Item CHERRY_BOAT = register("cherry_boat", new BoatItem(BoatEntity.Type.CHERRY, new Item.Properties().group(ItemGroup.TRANSPORTATION).maxStackSize(1)));
    public static final Item CHERRY_CHEST_BOAT = register("cherry_chest_boat", new ChestBoatItem(BoatEntity.Type.CHERRY, new Item.Properties().group(ItemGroup.TRANSPORTATION).maxStackSize(1)));
    public static final Item DARK_OAK_CHEST_BOAT = register("dark_oak_chest_boat", new ChestBoatItem(BoatEntity.Type.DARK_OAK, new Item.Properties().group(ItemGroup.TRANSPORTATION).maxStackSize(1)));
    public static final Item PALE_OAK_BOAT = register("pale_oak_boat", new BoatItem(BoatEntity.Type.PALE_OAK, new Item.Properties().group(ItemGroup.TRANSPORTATION).maxStackSize(1)));
    public static final Item PALE_OAK_CHEST_BOAT = register("pale_oak_chest_boat", new ChestBoatItem(BoatEntity.Type.PALE_OAK, new Item.Properties().group(ItemGroup.TRANSPORTATION).maxStackSize(1)));
    public static final Item MANGROVE_BOAT = register("mangrove_boat", new BoatItem(BoatEntity.Type.MANGROVE, new Item.Properties().group(ItemGroup.TRANSPORTATION).maxStackSize(1)));
    public static final Item MANGROVE_CHEST_BOAT = register("mangrove_chest_boat", new ChestBoatItem(BoatEntity.Type.MANGROVE, new Item.Properties().group(ItemGroup.TRANSPORTATION).maxStackSize(1)));
    public static final Item BAMBOO_RAFT = register("bamboo_raft", new BoatItem(BoatEntity.Type.BAMBOO, new Item.Properties().group(ItemGroup.TRANSPORTATION).maxStackSize(1)));
    public static final Item BAMBOO_CHEST_RAFT = register("bamboo_chest_raft", new ChestBoatItem(BoatEntity.Type.BAMBOO, new Item.Properties().group(ItemGroup.TRANSPORTATION).maxStackSize(1)));
    public static final Item ARMADILLO_SCUTE = register("armadillo_scute", new Item(new Item.Properties().group(ItemGroup.MISC)));
    public static final Item WOLF_ARMOR = register("wolf_armor", new Item(new Item.Properties().group(ItemGroup.MISC).maxDamage(64)));
    public static final Item AMETHYST_SHARD = register("amethyst_shard", new Item(new Item.Properties().group(ItemGroup.MISC)));
    public static final Item RAW_IRON = register("raw_iron", new Item(new Item.Properties().group(ItemGroup.MISC)));
    public static final Item RAW_COPPER = register("raw_copper", new Item(new Item.Properties().group(ItemGroup.MISC)));
    public static final Item COPPER_INGOT = register("copper_ingot", new Item(new Item.Properties().group(ItemGroup.MISC)));
    public static final Item RAW_GOLD = register("raw_gold", new Item(new Item.Properties().group(ItemGroup.MISC)));
    public static final Item COPPER_SWORD = register("copper_sword", new SwordItem(ItemTier.COPPER, 3, -2.4F, new Item.Properties().group(ItemGroup.COMBAT)));
    public static final Item COPPER_SHOVEL = register("copper_shovel", new ShovelItem(ItemTier.COPPER, 1.5F, -3.0F, new Item.Properties().group(ItemGroup.TOOLS)));
    public static final Item COPPER_PICKAXE = register("copper_pickaxe", new PickaxeItem(ItemTier.COPPER, 1, -2.8F, new Item.Properties().group(ItemGroup.TOOLS)));
    public static final Item COPPER_AXE = register("copper_axe", new AxeItem(ItemTier.COPPER, 7.0F, -3.2F, new Item.Properties().group(ItemGroup.TOOLS)));
    public static final Item COPPER_HOE = register("copper_hoe", new HoeItem(ItemTier.COPPER, -1, -2.0F, new Item.Properties().group(ItemGroup.TOOLS)));
    public static final Item COPPER_HELMET = register("copper_helmet", new ArmorItem(ArmorMaterial.COPPER, EquipmentSlotType.HEAD, new Item.Properties().group(ItemGroup.COMBAT)));
    public static final Item COPPER_CHESTPLATE = register("copper_chestplate", new ArmorItem(ArmorMaterial.COPPER, EquipmentSlotType.CHEST, new Item.Properties().group(ItemGroup.COMBAT)));
    public static final Item COPPER_LEGGINGS = register("copper_leggings", new ArmorItem(ArmorMaterial.COPPER, EquipmentSlotType.LEGS, new Item.Properties().group(ItemGroup.COMBAT)));
    public static final Item COPPER_BOOTS = register("copper_boots", new ArmorItem(ArmorMaterial.COPPER, EquipmentSlotType.FEET, new Item.Properties().group(ItemGroup.COMBAT)));
    public static final Item CHERRY_SIGN = register("cherry_sign", new SignItem(new Item.Properties().group(ItemGroup.DECORATIONS).maxStackSize(16), ModernBlocks.CHERRY_SIGN, ModernBlocks.CHERRY_WALL_SIGN));
    public static final Item PALE_OAK_SIGN = register("pale_oak_sign", new SignItem(new Item.Properties().group(ItemGroup.DECORATIONS).maxStackSize(16), ModernBlocks.PALE_OAK_SIGN, ModernBlocks.PALE_OAK_WALL_SIGN));
    public static final Item MANGROVE_SIGN = register("mangrove_sign", new SignItem(new Item.Properties().group(ItemGroup.DECORATIONS).maxStackSize(16), ModernBlocks.MANGROVE_SIGN, ModernBlocks.MANGROVE_WALL_SIGN));
    public static final Item BAMBOO_SIGN = register("bamboo_sign", new SignItem(new Item.Properties().group(ItemGroup.DECORATIONS).maxStackSize(16), ModernBlocks.BAMBOO_SIGN, ModernBlocks.BAMBOO_WALL_SIGN));
    public static final Item OAK_HANGING_SIGN = register("oak_hanging_sign", new ModernHangingSignItem(new Item.Properties().group(ItemGroup.DECORATIONS).maxStackSize(16), ModernBlocks.OAK_HANGING_SIGN, ModernBlocks.OAK_WALL_HANGING_SIGN));
    public static final Item SPRUCE_HANGING_SIGN = register("spruce_hanging_sign", new ModernHangingSignItem(new Item.Properties().group(ItemGroup.DECORATIONS).maxStackSize(16), ModernBlocks.SPRUCE_HANGING_SIGN, ModernBlocks.SPRUCE_WALL_HANGING_SIGN));
    public static final Item BIRCH_HANGING_SIGN = register("birch_hanging_sign", new ModernHangingSignItem(new Item.Properties().group(ItemGroup.DECORATIONS).maxStackSize(16), ModernBlocks.BIRCH_HANGING_SIGN, ModernBlocks.BIRCH_WALL_HANGING_SIGN));
    public static final Item JUNGLE_HANGING_SIGN = register("jungle_hanging_sign", new ModernHangingSignItem(new Item.Properties().group(ItemGroup.DECORATIONS).maxStackSize(16), ModernBlocks.JUNGLE_HANGING_SIGN, ModernBlocks.JUNGLE_WALL_HANGING_SIGN));
    public static final Item ACACIA_HANGING_SIGN = register("acacia_hanging_sign", new ModernHangingSignItem(new Item.Properties().group(ItemGroup.DECORATIONS).maxStackSize(16), ModernBlocks.ACACIA_HANGING_SIGN, ModernBlocks.ACACIA_WALL_HANGING_SIGN));
    public static final Item CHERRY_HANGING_SIGN = register("cherry_hanging_sign", new ModernHangingSignItem(new Item.Properties().group(ItemGroup.DECORATIONS).maxStackSize(16), ModernBlocks.CHERRY_HANGING_SIGN, ModernBlocks.CHERRY_WALL_HANGING_SIGN));
    public static final Item DARK_OAK_HANGING_SIGN = register("dark_oak_hanging_sign", new ModernHangingSignItem(new Item.Properties().group(ItemGroup.DECORATIONS).maxStackSize(16), ModernBlocks.DARK_OAK_HANGING_SIGN, ModernBlocks.DARK_OAK_WALL_HANGING_SIGN));
    public static final Item PALE_OAK_HANGING_SIGN = register("pale_oak_hanging_sign", new ModernHangingSignItem(new Item.Properties().group(ItemGroup.DECORATIONS).maxStackSize(16), ModernBlocks.PALE_OAK_HANGING_SIGN, ModernBlocks.PALE_OAK_WALL_HANGING_SIGN));
    public static final Item MANGROVE_HANGING_SIGN = register("mangrove_hanging_sign", new ModernHangingSignItem(new Item.Properties().group(ItemGroup.DECORATIONS).maxStackSize(16), ModernBlocks.MANGROVE_HANGING_SIGN, ModernBlocks.MANGROVE_WALL_HANGING_SIGN));
    public static final Item BAMBOO_HANGING_SIGN = register("bamboo_hanging_sign", new ModernHangingSignItem(new Item.Properties().group(ItemGroup.DECORATIONS).maxStackSize(16), ModernBlocks.BAMBOO_HANGING_SIGN, ModernBlocks.BAMBOO_WALL_HANGING_SIGN));
    public static final Item CRIMSON_HANGING_SIGN = register("crimson_hanging_sign", new ModernHangingSignItem(new Item.Properties().group(ItemGroup.DECORATIONS).maxStackSize(16), ModernBlocks.CRIMSON_HANGING_SIGN, ModernBlocks.CRIMSON_WALL_HANGING_SIGN));
    public static final Item WARPED_HANGING_SIGN = register("warped_hanging_sign", new ModernHangingSignItem(new Item.Properties().group(ItemGroup.DECORATIONS).maxStackSize(16), ModernBlocks.WARPED_HANGING_SIGN, ModernBlocks.WARPED_WALL_HANGING_SIGN));
    public static final Item BLUE_EGG = register("blue_egg", new Item(new Item.Properties().group(ItemGroup.MISC).maxStackSize(16)));
    public static final Item BROWN_EGG = register("brown_egg", new Item(new Item.Properties().group(ItemGroup.MISC).maxStackSize(16)));
    public static final Item RECOVERY_COMPASS = register("recovery_compass", new Item(new Item.Properties().group(ItemGroup.TOOLS).rarity(Rarity.UNCOMMON)));
    public static final Item BUNDLE = register("bundle", new BundleItem(new Item.Properties().group(ItemGroup.TOOLS).maxStackSize(1)));
    public static final Item WHITE_BUNDLE = register("white_bundle", new BundleItem(new Item.Properties().group(ItemGroup.TOOLS).maxStackSize(1)));
    public static final Item ORANGE_BUNDLE = register("orange_bundle", new BundleItem(new Item.Properties().group(ItemGroup.TOOLS).maxStackSize(1)));
    public static final Item MAGENTA_BUNDLE = register("magenta_bundle", new BundleItem(new Item.Properties().group(ItemGroup.TOOLS).maxStackSize(1)));
    public static final Item LIGHT_BLUE_BUNDLE = register("light_blue_bundle", new BundleItem(new Item.Properties().group(ItemGroup.TOOLS).maxStackSize(1)));
    public static final Item YELLOW_BUNDLE = register("yellow_bundle", new BundleItem(new Item.Properties().group(ItemGroup.TOOLS).maxStackSize(1)));
    public static final Item LIME_BUNDLE = register("lime_bundle", new BundleItem(new Item.Properties().group(ItemGroup.TOOLS).maxStackSize(1)));
    public static final Item PINK_BUNDLE = register("pink_bundle", new BundleItem(new Item.Properties().group(ItemGroup.TOOLS).maxStackSize(1)));
    public static final Item GRAY_BUNDLE = register("gray_bundle", new BundleItem(new Item.Properties().group(ItemGroup.TOOLS).maxStackSize(1)));
    public static final Item LIGHT_GRAY_BUNDLE = register("light_gray_bundle", new BundleItem(new Item.Properties().group(ItemGroup.TOOLS).maxStackSize(1)));
    public static final Item CYAN_BUNDLE = register("cyan_bundle", new BundleItem(new Item.Properties().group(ItemGroup.TOOLS).maxStackSize(1)));
    public static final Item PURPLE_BUNDLE = register("purple_bundle", new BundleItem(new Item.Properties().group(ItemGroup.TOOLS).maxStackSize(1)));
    public static final Item BLUE_BUNDLE = register("blue_bundle", new BundleItem(new Item.Properties().group(ItemGroup.TOOLS).maxStackSize(1)));
    public static final Item BROWN_BUNDLE = register("brown_bundle", new BundleItem(new Item.Properties().group(ItemGroup.TOOLS).maxStackSize(1)));
    public static final Item GREEN_BUNDLE = register("green_bundle", new BundleItem(new Item.Properties().group(ItemGroup.TOOLS).maxStackSize(1)));
    public static final Item RED_BUNDLE = register("red_bundle", new BundleItem(new Item.Properties().group(ItemGroup.TOOLS).maxStackSize(1)));
    public static final Item BLACK_BUNDLE = register("black_bundle", new BundleItem(new Item.Properties().group(ItemGroup.TOOLS).maxStackSize(1)));
    public static final Item SPYGLASS = register("spyglass", new SpyglassItem(new Item.Properties().group(ItemGroup.MISC).maxStackSize(1)));
    public static final Item GLOW_INK_SAC = register("glow_ink_sac", new Item(new Item.Properties().group(ItemGroup.MISC)));
    public static final Item CAMEL_SPAWN_EGG = register("camel_spawn_egg", new Item(new Item.Properties().group(ItemGroup.MISC)));
    public static final Item ARMADILLO_SPAWN_EGG = register("armadillo_spawn_egg", new Item(new Item.Properties().group(ItemGroup.MISC)));
    public static final Item GOAT_SPAWN_EGG = register("goat_spawn_egg", new Item(new Item.Properties().group(ItemGroup.MISC)));
    public static final Item AXOLOTL_SPAWN_EGG = register("axolotl_spawn_egg", new Item(new Item.Properties().group(ItemGroup.MISC)));
    public static final Item FROG_SPAWN_EGG = register("frog_spawn_egg", new Item(new Item.Properties().group(ItemGroup.MISC)));
    public static final Item GLOW_SQUID_SPAWN_EGG = register("glow_squid_spawn_egg", new Item(new Item.Properties().group(ItemGroup.MISC)));
    public static final Item NAUTILUS_SPAWN_EGG = register("nautilus_spawn_egg", new Item(new Item.Properties().group(ItemGroup.MISC)));
    public static final Item TADPOLE_SPAWN_EGG = register("tadpole_spawn_egg", new Item(new Item.Properties().group(ItemGroup.MISC)));
    public static final Item ALLAY_SPAWN_EGG = register("allay_spawn_egg", new Item(new Item.Properties().group(ItemGroup.MISC)));
    public static final Item SNIFFER_SPAWN_EGG = register("sniffer_spawn_egg", new Item(new Item.Properties().group(ItemGroup.MISC)));
    public static final Item COPPER_GOLEM_SPAWN_EGG = register("copper_golem_spawn_egg", new Item(new Item.Properties().group(ItemGroup.MISC)));
    public static final Item IRON_GOLEM_SPAWN_EGG = register("iron_golem_spawn_egg", new Item(new Item.Properties().group(ItemGroup.MISC)));
    public static final Item SNOW_GOLEM_SPAWN_EGG = register("snow_golem_spawn_egg", new Item(new Item.Properties().group(ItemGroup.MISC)));
    public static final Item BOGGED_SPAWN_EGG = register("bogged_spawn_egg", new Item(new Item.Properties().group(ItemGroup.MISC)));
    public static final Item CAMEL_HUSK_SPAWN_EGG = register("camel_husk_spawn_egg", new Item(new Item.Properties().group(ItemGroup.MISC)));
    public static final Item PARCHED_SPAWN_EGG = register("parched_spawn_egg", new Item(new Item.Properties().group(ItemGroup.MISC)));
    public static final Item WITHER_SPAWN_EGG = register("wither_spawn_egg", new Item(new Item.Properties().group(ItemGroup.MISC)));
    public static final Item ZOMBIE_NAUTILUS_SPAWN_EGG = register("zombie_nautilus_spawn_egg", new Item(new Item.Properties().group(ItemGroup.MISC)));
    public static final Item BREEZE_SPAWN_EGG = register("breeze_spawn_egg", new Item(new Item.Properties().group(ItemGroup.MISC)));
    public static final Item CREAKING_SPAWN_EGG = register("creaking_spawn_egg", new Item(new Item.Properties().group(ItemGroup.MISC)));
    public static final Item WARDEN_SPAWN_EGG = register("warden_spawn_egg", new Item(new Item.Properties().group(ItemGroup.MISC)));
    public static final Item HAPPY_GHAST_SPAWN_EGG = register("happy_ghast_spawn_egg", new Item(new Item.Properties().group(ItemGroup.MISC)));
    public static final Item ENDER_DRAGON_SPAWN_EGG = register("ender_dragon_spawn_egg", new Item(new Item.Properties().group(ItemGroup.MISC)));
    public static final Item WIND_CHARGE = register("wind_charge", new WindChargeItem(new Item.Properties().group(ItemGroup.COMBAT)));
    public static final Item BREEZE_ROD = register("breeze_rod", new Item(new Item.Properties().group(ItemGroup.MISC)));
    public static final Item MACE = register("mace", new MaceItem(new Item.Properties().group(ItemGroup.COMBAT).maxDamage(500).rarity(Rarity.EPIC)));
    public static final Item GLOW_ITEM_FRAME = register("glow_item_frame", new Item(new Item.Properties().group(ItemGroup.DECORATIONS)));
    public static final Item RESIN_BRICK = register("resin_brick", new Item(new Item.Properties().group(ItemGroup.MISC)));
    public static final Item COPPER_HORSE_ARMOR = register("copper_horse_armor", new HorseArmorItem(4, "copper", new Item.Properties().group(ItemGroup.MISC).maxStackSize(1)));
    public static final Item NETHERITE_HORSE_ARMOR = register("netherite_horse_armor", new HorseArmorItem(19, "netherite", new Item.Properties().group(ItemGroup.MISC).maxStackSize(1).isImmuneToFire()));
    public static final Item TORCHFLOWER_SEEDS = register("torchflower_seeds", new Item(new Item.Properties().group(ItemGroup.MISC)));
    public static final Item PITCHER_POD = register("pitcher_pod", new Item(new Item.Properties().group(ItemGroup.MATERIALS)));
    public static final Item WOODEN_SPEAR = register("wooden_spear", new SpearItem(ItemTier.WOOD, 0.65F, new Item.Properties().group(ItemGroup.COMBAT)));
    public static final Item STONE_SPEAR = register("stone_spear", new SpearItem(ItemTier.STONE, 0.75F, new Item.Properties().group(ItemGroup.COMBAT)));
    public static final Item COPPER_SPEAR = register("copper_spear", new SpearItem(ItemTier.COPPER, 0.85F, new Item.Properties().group(ItemGroup.COMBAT)));
    public static final Item IRON_SPEAR = register("iron_spear", new SpearItem(ItemTier.IRON, 0.95F, new Item.Properties().group(ItemGroup.COMBAT)));
    public static final Item GOLDEN_SPEAR = register("golden_spear", new SpearItem(ItemTier.GOLD, 0.95F, new Item.Properties().group(ItemGroup.COMBAT)));
    public static final Item DIAMOND_SPEAR = register("diamond_spear", new SpearItem(ItemTier.DIAMOND, 1.05F, new Item.Properties().group(ItemGroup.COMBAT)));
    public static final Item NETHERITE_SPEAR = register("netherite_spear", new SpearItem(ItemTier.NETHERITE, 1.15F, new Item.Properties().group(ItemGroup.COMBAT).isImmuneToFire()));
    public static final Item COPPER_NUGGET = register("copper_nugget", new Item(new Item.Properties().group(ItemGroup.MISC)));
    public static final Item MUSIC_DISC_CREATOR = register("music_disc_creator", new Item(new Item.Properties().group(ItemGroup.MISC).maxStackSize(1).rarity(Rarity.RARE)));
    public static final Item MUSIC_DISC_CREATOR_MUSIC_BOX = register("music_disc_creator_music_box", new Item(new Item.Properties().group(ItemGroup.MISC).maxStackSize(1).rarity(Rarity.UNCOMMON)));
    public static final Item MUSIC_DISC_LAVA_CHICKEN = register("music_disc_lava_chicken", new Item(new Item.Properties().group(ItemGroup.MISC).maxStackSize(1).rarity(Rarity.RARE)));
    public static final Item MUSIC_DISC_OTHERSIDE = register("music_disc_otherside", new Item(new Item.Properties().group(ItemGroup.MISC).maxStackSize(1).rarity(Rarity.RARE)));
    public static final Item MUSIC_DISC_RELIC = register("music_disc_relic", new Item(new Item.Properties().group(ItemGroup.MISC).maxStackSize(1).rarity(Rarity.UNCOMMON)));
    public static final Item MUSIC_DISC_5 = register("music_disc_5", new Item(new Item.Properties().group(ItemGroup.MISC).maxStackSize(1).rarity(Rarity.UNCOMMON)));
    public static final Item MUSIC_DISC_PRECIPICE = register("music_disc_precipice", new Item(new Item.Properties().group(ItemGroup.MISC).maxStackSize(1).rarity(Rarity.UNCOMMON)));
    public static final Item MUSIC_DISC_TEARS = register("music_disc_tears", new Item(new Item.Properties().group(ItemGroup.MISC).maxStackSize(1).rarity(Rarity.UNCOMMON)));
    public static final Item DISC_FRAGMENT_5 = register("disc_fragment_5", new Item(new Item.Properties().group(ItemGroup.MISC).rarity(Rarity.UNCOMMON)));
    public static final Item IRON_NAUTILUS_ARMOR = register("iron_nautilus_armor", new Item(new Item.Properties().group(ItemGroup.COMBAT).maxStackSize(1)));
    public static final Item GOLDEN_NAUTILUS_ARMOR = register("golden_nautilus_armor", new Item(new Item.Properties().group(ItemGroup.COMBAT).maxStackSize(1)));
    public static final Item DIAMOND_NAUTILUS_ARMOR = register("diamond_nautilus_armor", new Item(new Item.Properties().group(ItemGroup.COMBAT).maxStackSize(1)));
    public static final Item NETHERITE_NAUTILUS_ARMOR = register("netherite_nautilus_armor", new Item(new Item.Properties().group(ItemGroup.COMBAT).maxStackSize(1).isImmuneToFire()));
    public static final Item COPPER_NAUTILUS_ARMOR = register("copper_nautilus_armor", new Item(new Item.Properties().group(ItemGroup.COMBAT).maxStackSize(1)));
    public static final Item FLOW_BANNER_PATTERN = register("flow_banner_pattern", new Item(new Item.Properties().group(ItemGroup.MISC).maxStackSize(1).rarity(Rarity.RARE)));
    public static final Item GUSTER_BANNER_PATTERN = register("guster_banner_pattern", new Item(new Item.Properties().group(ItemGroup.MISC).maxStackSize(1).rarity(Rarity.RARE)));
    public static final Item FIELD_MASONED_BANNER_PATTERN = register("field_masoned_banner_pattern", new Item(new Item.Properties().group(ItemGroup.MISC).maxStackSize(1)));
    public static final Item BORDURE_INDENTED_BANNER_PATTERN = register("bordure_indented_banner_pattern", new Item(new Item.Properties().group(ItemGroup.MISC).maxStackSize(1)));
    public static final Item GOAT_HORN = register("goat_horn", new InstrumentItem(new Item.Properties().group(ItemGroup.MISC).maxStackSize(1).rarity(Rarity.UNCOMMON)));
    public static final Item COPPER_LANTERN = register(ModernBlocks.COPPER_LANTERN, ItemGroup.DECORATIONS);
    public static final Item EXPOSED_COPPER_LANTERN = register(ModernBlocks.EXPOSED_COPPER_LANTERN, ItemGroup.DECORATIONS);
    public static final Item WEATHERED_COPPER_LANTERN = register(ModernBlocks.WEATHERED_COPPER_LANTERN, ItemGroup.DECORATIONS);
    public static final Item OXIDIZED_COPPER_LANTERN = register(ModernBlocks.OXIDIZED_COPPER_LANTERN, ItemGroup.DECORATIONS);
    public static final Item WAXED_COPPER_LANTERN = register(ModernBlocks.WAXED_COPPER_LANTERN, ItemGroup.DECORATIONS);
    public static final Item WAXED_EXPOSED_COPPER_LANTERN = register(ModernBlocks.WAXED_EXPOSED_COPPER_LANTERN, ItemGroup.DECORATIONS);
    public static final Item WAXED_WEATHERED_COPPER_LANTERN = register(ModernBlocks.WAXED_WEATHERED_COPPER_LANTERN, ItemGroup.DECORATIONS);
    public static final Item WAXED_OXIDIZED_COPPER_LANTERN = register(ModernBlocks.WAXED_OXIDIZED_COPPER_LANTERN, ItemGroup.DECORATIONS);
    public static final Item GLOW_BERRIES = register("glow_berries", new Item(new Item.Properties().group(ItemGroup.FOOD)));
    public static final Item CANDLE = register(ModernBlocks.CANDLE, ItemGroup.DECORATIONS);
    public static final Item WHITE_CANDLE = register(ModernBlocks.WHITE_CANDLE, ItemGroup.DECORATIONS);
    public static final Item ORANGE_CANDLE = register(ModernBlocks.ORANGE_CANDLE, ItemGroup.DECORATIONS);
    public static final Item MAGENTA_CANDLE = register(ModernBlocks.MAGENTA_CANDLE, ItemGroup.DECORATIONS);
    public static final Item LIGHT_BLUE_CANDLE = register(ModernBlocks.LIGHT_BLUE_CANDLE, ItemGroup.DECORATIONS);
    public static final Item YELLOW_CANDLE = register(ModernBlocks.YELLOW_CANDLE, ItemGroup.DECORATIONS);
    public static final Item LIME_CANDLE = register(ModernBlocks.LIME_CANDLE, ItemGroup.DECORATIONS);
    public static final Item PINK_CANDLE = register(ModernBlocks.PINK_CANDLE, ItemGroup.DECORATIONS);
    public static final Item GRAY_CANDLE = register(ModernBlocks.GRAY_CANDLE, ItemGroup.DECORATIONS);
    public static final Item LIGHT_GRAY_CANDLE = register(ModernBlocks.LIGHT_GRAY_CANDLE, ItemGroup.DECORATIONS);
    public static final Item CYAN_CANDLE = register(ModernBlocks.CYAN_CANDLE, ItemGroup.DECORATIONS);
    public static final Item PURPLE_CANDLE = register(ModernBlocks.PURPLE_CANDLE, ItemGroup.DECORATIONS);
    public static final Item BLUE_CANDLE = register(ModernBlocks.BLUE_CANDLE, ItemGroup.DECORATIONS);
    public static final Item BROWN_CANDLE = register(ModernBlocks.BROWN_CANDLE, ItemGroup.DECORATIONS);
    public static final Item GREEN_CANDLE = register(ModernBlocks.GREEN_CANDLE, ItemGroup.DECORATIONS);
    public static final Item RED_CANDLE = register(ModernBlocks.RED_CANDLE, ItemGroup.DECORATIONS);
    public static final Item BLACK_CANDLE = register(ModernBlocks.BLACK_CANDLE, ItemGroup.DECORATIONS);
    public static final Item SMALL_AMETHYST_BUD = register(ModernBlocks.SMALL_AMETHYST_BUD, ItemGroup.BUILDING_BLOCKS);
    public static final Item MEDIUM_AMETHYST_BUD = register(ModernBlocks.MEDIUM_AMETHYST_BUD, ItemGroup.BUILDING_BLOCKS);
    public static final Item LARGE_AMETHYST_BUD = register(ModernBlocks.LARGE_AMETHYST_BUD, ItemGroup.BUILDING_BLOCKS);
    public static final Item AMETHYST_CLUSTER = register(ModernBlocks.AMETHYST_CLUSTER, ItemGroup.BUILDING_BLOCKS);
    public static final Item POINTED_DRIPSTONE = register(ModernBlocks.POINTED_DRIPSTONE, ItemGroup.BUILDING_BLOCKS);
    public static final Item OCHRE_FROGLIGHT = register(ModernBlocks.OCHRE_FROGLIGHT, ItemGroup.BUILDING_BLOCKS);
    public static final Item VERDANT_FROGLIGHT = register(ModernBlocks.VERDANT_FROGLIGHT, ItemGroup.BUILDING_BLOCKS);
    public static final Item PEARLESCENT_FROGLIGHT = register(ModernBlocks.PEARLESCENT_FROGLIGHT, ItemGroup.BUILDING_BLOCKS);
    public static final Item FROGSPAWN = register(ModernBlocks.FROGSPAWN, ItemGroup.DECORATIONS);
    public static final Item ECHO_SHARD = register("echo_shard", new Item(new Item.Properties().group(ItemGroup.MISC).rarity(Rarity.UNCOMMON)));
    public static final Item BRUSH = register("brush", new BrushItem(new Item.Properties().group(ItemGroup.TOOLS).maxDamage(64)));
    public static final Item NETHERITE_UPGRADE_SMITHING_TEMPLATE = register("netherite_upgrade_smithing_template", new SmithingTemplateItem(SmithingTemplateItem.Variant.NETHERITE_UPGRADE, new Item.Properties().group(ItemGroup.MISC).rarity(Rarity.UNCOMMON)));
    public static final Item SENTRY_ARMOR_TRIM_SMITHING_TEMPLATE = register("sentry_armor_trim_smithing_template", new SmithingTemplateItem(SmithingTemplateItem.Variant.ARMOR_TRIM, new Item.Properties().group(ItemGroup.MISC).rarity(Rarity.UNCOMMON)));
    public static final Item DUNE_ARMOR_TRIM_SMITHING_TEMPLATE = register("dune_armor_trim_smithing_template", new SmithingTemplateItem(SmithingTemplateItem.Variant.ARMOR_TRIM, new Item.Properties().group(ItemGroup.MISC).rarity(Rarity.UNCOMMON)));
    public static final Item COAST_ARMOR_TRIM_SMITHING_TEMPLATE = register("coast_armor_trim_smithing_template", new SmithingTemplateItem(SmithingTemplateItem.Variant.ARMOR_TRIM, new Item.Properties().group(ItemGroup.MISC).rarity(Rarity.UNCOMMON)));
    public static final Item WILD_ARMOR_TRIM_SMITHING_TEMPLATE = register("wild_armor_trim_smithing_template", new SmithingTemplateItem(SmithingTemplateItem.Variant.ARMOR_TRIM, new Item.Properties().group(ItemGroup.MISC).rarity(Rarity.UNCOMMON)));
    public static final Item WARD_ARMOR_TRIM_SMITHING_TEMPLATE = register("ward_armor_trim_smithing_template", new SmithingTemplateItem(SmithingTemplateItem.Variant.ARMOR_TRIM, new Item.Properties().group(ItemGroup.MISC).rarity(Rarity.RARE)));
    public static final Item EYE_ARMOR_TRIM_SMITHING_TEMPLATE = register("eye_armor_trim_smithing_template", new SmithingTemplateItem(SmithingTemplateItem.Variant.ARMOR_TRIM, new Item.Properties().group(ItemGroup.MISC).rarity(Rarity.RARE)));
    public static final Item VEX_ARMOR_TRIM_SMITHING_TEMPLATE = register("vex_armor_trim_smithing_template", new SmithingTemplateItem(SmithingTemplateItem.Variant.ARMOR_TRIM, new Item.Properties().group(ItemGroup.MISC).rarity(Rarity.RARE)));
    public static final Item TIDE_ARMOR_TRIM_SMITHING_TEMPLATE = register("tide_armor_trim_smithing_template", new SmithingTemplateItem(SmithingTemplateItem.Variant.ARMOR_TRIM, new Item.Properties().group(ItemGroup.MISC).rarity(Rarity.UNCOMMON)));
    public static final Item SNOUT_ARMOR_TRIM_SMITHING_TEMPLATE = register("snout_armor_trim_smithing_template", new SmithingTemplateItem(SmithingTemplateItem.Variant.ARMOR_TRIM, new Item.Properties().group(ItemGroup.MISC).rarity(Rarity.UNCOMMON)));
    public static final Item RIB_ARMOR_TRIM_SMITHING_TEMPLATE = register("rib_armor_trim_smithing_template", new SmithingTemplateItem(SmithingTemplateItem.Variant.ARMOR_TRIM, new Item.Properties().group(ItemGroup.MISC).rarity(Rarity.UNCOMMON)));
    public static final Item SPIRE_ARMOR_TRIM_SMITHING_TEMPLATE = register("spire_armor_trim_smithing_template", new SmithingTemplateItem(SmithingTemplateItem.Variant.ARMOR_TRIM, new Item.Properties().group(ItemGroup.MISC).rarity(Rarity.RARE)));
    public static final Item WAYFINDER_ARMOR_TRIM_SMITHING_TEMPLATE = register("wayfinder_armor_trim_smithing_template", new SmithingTemplateItem(SmithingTemplateItem.Variant.ARMOR_TRIM, new Item.Properties().group(ItemGroup.MISC).rarity(Rarity.UNCOMMON)));
    public static final Item SHAPER_ARMOR_TRIM_SMITHING_TEMPLATE = register("shaper_armor_trim_smithing_template", new SmithingTemplateItem(SmithingTemplateItem.Variant.ARMOR_TRIM, new Item.Properties().group(ItemGroup.MISC).rarity(Rarity.UNCOMMON)));
    public static final Item SILENCE_ARMOR_TRIM_SMITHING_TEMPLATE = register("silence_armor_trim_smithing_template", new SmithingTemplateItem(SmithingTemplateItem.Variant.ARMOR_TRIM, new Item.Properties().group(ItemGroup.MISC).rarity(Rarity.EPIC)));
    public static final Item RAISER_ARMOR_TRIM_SMITHING_TEMPLATE = register("raiser_armor_trim_smithing_template", new SmithingTemplateItem(SmithingTemplateItem.Variant.ARMOR_TRIM, new Item.Properties().group(ItemGroup.MISC).rarity(Rarity.UNCOMMON)));
    public static final Item HOST_ARMOR_TRIM_SMITHING_TEMPLATE = register("host_armor_trim_smithing_template", new SmithingTemplateItem(SmithingTemplateItem.Variant.ARMOR_TRIM, new Item.Properties().group(ItemGroup.MISC).rarity(Rarity.UNCOMMON)));
    public static final Item FLOW_ARMOR_TRIM_SMITHING_TEMPLATE = register("flow_armor_trim_smithing_template", new SmithingTemplateItem(SmithingTemplateItem.Variant.ARMOR_TRIM, new Item.Properties().group(ItemGroup.MISC).rarity(Rarity.UNCOMMON)));
    public static final Item BOLT_ARMOR_TRIM_SMITHING_TEMPLATE = register("bolt_armor_trim_smithing_template", new SmithingTemplateItem(SmithingTemplateItem.Variant.ARMOR_TRIM, new Item.Properties().group(ItemGroup.MISC).rarity(Rarity.UNCOMMON)));
    public static final Item ANGLER_POTTERY_SHERD = register("angler_pottery_sherd", new Item(new Item.Properties().group(ItemGroup.MISC).rarity(Rarity.UNCOMMON)));
    public static final Item ARCHER_POTTERY_SHERD = register("archer_pottery_sherd", new Item(new Item.Properties().group(ItemGroup.MISC).rarity(Rarity.UNCOMMON)));
    public static final Item ARMS_UP_POTTERY_SHERD = register("arms_up_pottery_sherd", new Item(new Item.Properties().group(ItemGroup.MISC).rarity(Rarity.UNCOMMON)));
    public static final Item BLADE_POTTERY_SHERD = register("blade_pottery_sherd", new Item(new Item.Properties().group(ItemGroup.MISC).rarity(Rarity.UNCOMMON)));
    public static final Item BREWER_POTTERY_SHERD = register("brewer_pottery_sherd", new Item(new Item.Properties().group(ItemGroup.MISC).rarity(Rarity.UNCOMMON)));
    public static final Item BURN_POTTERY_SHERD = register("burn_pottery_sherd", new Item(new Item.Properties().group(ItemGroup.MISC).rarity(Rarity.UNCOMMON)));
    public static final Item DANGER_POTTERY_SHERD = register("danger_pottery_sherd", new Item(new Item.Properties().group(ItemGroup.MISC).rarity(Rarity.UNCOMMON)));
    public static final Item EXPLORER_POTTERY_SHERD = register("explorer_pottery_sherd", new Item(new Item.Properties().group(ItemGroup.MISC).rarity(Rarity.UNCOMMON)));
    public static final Item FLOW_POTTERY_SHERD = register("flow_pottery_sherd", new Item(new Item.Properties().group(ItemGroup.MISC).rarity(Rarity.UNCOMMON)));
    public static final Item FRIEND_POTTERY_SHERD = register("friend_pottery_sherd", new Item(new Item.Properties().group(ItemGroup.MISC).rarity(Rarity.UNCOMMON)));
    public static final Item GUSTER_POTTERY_SHERD = register("guster_pottery_sherd", new Item(new Item.Properties().group(ItemGroup.MISC).rarity(Rarity.UNCOMMON)));
    public static final Item HEART_POTTERY_SHERD = register("heart_pottery_sherd", new Item(new Item.Properties().group(ItemGroup.MISC).rarity(Rarity.UNCOMMON)));
    public static final Item HEARTBREAK_POTTERY_SHERD = register("heartbreak_pottery_sherd", new Item(new Item.Properties().group(ItemGroup.MISC).rarity(Rarity.UNCOMMON)));
    public static final Item HOWL_POTTERY_SHERD = register("howl_pottery_sherd", new Item(new Item.Properties().group(ItemGroup.MISC).rarity(Rarity.UNCOMMON)));
    public static final Item MINER_POTTERY_SHERD = register("miner_pottery_sherd", new Item(new Item.Properties().group(ItemGroup.MISC).rarity(Rarity.UNCOMMON)));
    public static final Item MOURNER_POTTERY_SHERD = register("mourner_pottery_sherd", new Item(new Item.Properties().group(ItemGroup.MISC).rarity(Rarity.UNCOMMON)));
    public static final Item PLENTY_POTTERY_SHERD = register("plenty_pottery_sherd", new Item(new Item.Properties().group(ItemGroup.MISC).rarity(Rarity.UNCOMMON)));
    public static final Item PRIZE_POTTERY_SHERD = register("prize_pottery_sherd", new Item(new Item.Properties().group(ItemGroup.MISC).rarity(Rarity.UNCOMMON)));
    public static final Item SCRAPE_POTTERY_SHERD = register("scrape_pottery_sherd", new Item(new Item.Properties().group(ItemGroup.MISC).rarity(Rarity.UNCOMMON)));
    public static final Item SHEAF_POTTERY_SHERD = register("sheaf_pottery_sherd", new Item(new Item.Properties().group(ItemGroup.MISC).rarity(Rarity.UNCOMMON)));
    public static final Item SHELTER_POTTERY_SHERD = register("shelter_pottery_sherd", new Item(new Item.Properties().group(ItemGroup.MISC).rarity(Rarity.UNCOMMON)));
    public static final Item SKULL_POTTERY_SHERD = register("skull_pottery_sherd", new Item(new Item.Properties().group(ItemGroup.MISC).rarity(Rarity.UNCOMMON)));
    public static final Item SNORT_POTTERY_SHERD = register("snort_pottery_sherd", new Item(new Item.Properties().group(ItemGroup.MISC).rarity(Rarity.UNCOMMON)));
    public static final Item COPPER_GRATE = register(ModernBlocks.COPPER_GRATE, ItemGroup.BUILDING_BLOCKS);
    public static final Item EXPOSED_COPPER_GRATE = register(ModernBlocks.EXPOSED_COPPER_GRATE, ItemGroup.BUILDING_BLOCKS);
    public static final Item WEATHERED_COPPER_GRATE = register(ModernBlocks.WEATHERED_COPPER_GRATE, ItemGroup.BUILDING_BLOCKS);
    public static final Item OXIDIZED_COPPER_GRATE = register(ModernBlocks.OXIDIZED_COPPER_GRATE, ItemGroup.BUILDING_BLOCKS);
    public static final Item WAXED_COPPER_GRATE = register(ModernBlocks.WAXED_COPPER_GRATE, ItemGroup.BUILDING_BLOCKS);
    public static final Item WAXED_EXPOSED_COPPER_GRATE = register(ModernBlocks.WAXED_EXPOSED_COPPER_GRATE, ItemGroup.BUILDING_BLOCKS);
    public static final Item WAXED_WEATHERED_COPPER_GRATE = register(ModernBlocks.WAXED_WEATHERED_COPPER_GRATE, ItemGroup.BUILDING_BLOCKS);
    public static final Item WAXED_OXIDIZED_COPPER_GRATE = register(ModernBlocks.WAXED_OXIDIZED_COPPER_GRATE, ItemGroup.BUILDING_BLOCKS);
    public static final Item COPPER_BULB = register(ModernBlocks.COPPER_BULB, ItemGroup.REDSTONE);
    public static final Item EXPOSED_COPPER_BULB = register(ModernBlocks.EXPOSED_COPPER_BULB, ItemGroup.REDSTONE);
    public static final Item WEATHERED_COPPER_BULB = register(ModernBlocks.WEATHERED_COPPER_BULB, ItemGroup.REDSTONE);
    public static final Item OXIDIZED_COPPER_BULB = register(ModernBlocks.OXIDIZED_COPPER_BULB, ItemGroup.REDSTONE);
    public static final Item WAXED_COPPER_BULB = register(ModernBlocks.WAXED_COPPER_BULB, ItemGroup.REDSTONE);
    public static final Item WAXED_EXPOSED_COPPER_BULB = register(ModernBlocks.WAXED_EXPOSED_COPPER_BULB, ItemGroup.REDSTONE);
    public static final Item WAXED_WEATHERED_COPPER_BULB = register(ModernBlocks.WAXED_WEATHERED_COPPER_BULB, ItemGroup.REDSTONE);
    public static final Item WAXED_OXIDIZED_COPPER_BULB = register(ModernBlocks.WAXED_OXIDIZED_COPPER_BULB, ItemGroup.REDSTONE);
    public static final Item TRIAL_KEY = register("trial_key", new Item(new Item.Properties().group(ItemGroup.MISC)));
    public static final Item OMINOUS_TRIAL_KEY = register("ominous_trial_key", new Item(new Item.Properties().group(ItemGroup.MISC)));
    public static final Item OMINOUS_BOTTLE = register("ominous_bottle", new OminousBottleItem(new Item.Properties().group(ItemGroup.FOOD).rarity(Rarity.UNCOMMON)));
    // === 生成字段结束 ======================================================

    // === 以下为手工维护：需要专门物品类、生成器暂不覆盖的部分 ================

    // 目前是空的。铜镐曾经在这里 —— 生成器不认识它，只好手写。现在整套铜工具、铜盔甲、
    // 7 种长矛、19 种锻造模板、两种马铠都由生成器按 GenerateItems.SPECIAL_CLASS 产出，
    // 手写的那份已经删掉（留着会重复注册同一个标识符）。
    //
    // 还没覆盖的都在 target/crossversion-check/gen-items-skipped.txt：
    // 刷怪蛋 23（要 EntityType）、船与筏 14（要新实体）、桶 3（要液体/生物），
    // 以及 101 个「方块未生成」的方块物品。

    private ModernItems() {
    }

    private static Item register(String key, Item item) {
        Item registered = Registry.register(Registry.ITEM, key, item);
        REGISTERED.add(registered);
        ModernRegistry.markExtended(registered);
        return registered;
    }

    /**
     * 注册方块对应的物品。复刻 {@link Items} 的私有 register 行为：物品的注册名沿用方块的
     * 注册名，并登记进 {@link Item#BLOCK_TO_ITEM} 反查表，否则手持方块、拾取、创造栏取物
     * 都会拿不到对应物品。
     */
    private static Item register(Block block, ItemGroup group) {
        BlockItem item = new BlockItem(block, new Item.Properties().group(group));
        item.addToBlockToItemMap(Item.BLOCK_TO_ITEM, item);
        return register(Registry.BLOCK.getKey(block).getPath(), item);
    }

    /**
     * 触发本类的静态初始化。由 {@code Bootstrap} 在原版注册完成后调用 —— 单纯引用类名
     * 不保证初始化，必须调用一个方法。
     */
    public static void init() {
        // 字段初始化已在类加载时完成，这里只需要确保类被加载。
    }

    /** 本类注册的物品，按注册顺序。 */
    public static List<Item> registered() {
        return Collections.unmodifiableList(REGISTERED);
    }
}
