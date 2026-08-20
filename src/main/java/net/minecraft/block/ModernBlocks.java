package net.minecraft.block;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import net.minecraft.block.material.Material;
import net.minecraft.block.material.MaterialColor;
import net.minecraft.crossversion.ModernRegistry;
import net.minecraft.util.registry.Registry;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * 1.17-1.21.11 新增方块的注册表扩展。
 *
 * <p><b>本文件的字段由代码生成器产出</b>，不要手工编辑字段区。
 * 生成器：{@code tools/crossversion/GenerateBlocks.java}，重跑方式见
 * {@code docs/cross-version-registry-plan.md}。所有数值来自官方 1.21.11 的 jar：
 * 硬度与爆炸抗性、地图颜色 id、发光等级、音效、碰撞与随机 tick 由反射导出，
 * {@code Material} 由官方挖掘 tag 反推（1.20 起官方已移除 Material 系统）。
 *
 * <p><b>初始化顺序是硬约束。</b>本类必须在 {@link Blocks} 完全初始化之后才加载：
 * {@code Blocks} 末尾的 static 块会遍历<i>当时已注册</i>的方块来填充
 * {@link Block#BLOCK_STATE_IDS}，跑在它之后注册的方块不会被收录，因此本类要自己补上
 * （见 {@link #registerBlockStates()}）。这样方块状态 ID 的排布是：原版 0-17111，
 * 扩展内容从 17112 起。
 *
 * <p><b>字段顺序按官方注册顺序。</b>楼梯的构造需要 base 方块状态，而官方顺序里 base
 * 总是先于派生方块，顺着生成就满足 Java 静态初始化的先后依赖。
 */
public final class ModernBlocks {
    private static final Logger LOGGER = LogManager.getLogger();

    /** 本类注册的方块，按注册顺序，用于随后补充 BLOCK_STATE_IDS。 */
    private static final List<Block> REGISTERED = new ArrayList<>();

    // === 以下字段由生成器产出，勿手工编辑 =================================
    public static final Block CHERRY_PLANKS = register("cherry_planks", new Block(props(Material.WOOD, MaterialColor.WHITE_TERRACOTTA, 2.0F, 3.0F).sound(SoundType.WOOD)));
    public static final Block PALE_OAK_WOOD = register("pale_oak_wood", new RotatedPillarBlock(props(Material.WOOD, MaterialColor.STONE, 2.0F, 2.0F).sound(SoundType.WOOD)));
    public static final Block PALE_OAK_PLANKS = register("pale_oak_planks", new Block(props(Material.WOOD, MaterialColor.QUARTZ, 2.0F, 3.0F).sound(SoundType.WOOD)));
    public static final Block MANGROVE_PLANKS = register("mangrove_planks", new Block(props(Material.WOOD, MaterialColor.RED, 2.0F, 3.0F).sound(SoundType.WOOD)));
    public static final Block BAMBOO_PLANKS = register("bamboo_planks", new Block(props(Material.WOOD, MaterialColor.YELLOW, 2.0F, 3.0F).sound(SoundType.WOOD)));
    public static final Block BAMBOO_MOSAIC = register("bamboo_mosaic", new Block(props(Material.WOOD, MaterialColor.YELLOW, 2.0F, 3.0F).sound(SoundType.WOOD)));
    public static final Block DEEPSLATE_GOLD_ORE = register("deepslate_gold_ore", new OreBlock(props(Material.IRON, MaterialColor.DEEPSLATE, 4.5F, 3.0F).setRequiresTool().sound(SoundType.STONE)));
    public static final Block DEEPSLATE_IRON_ORE = register("deepslate_iron_ore", new OreBlock(props(Material.IRON, MaterialColor.DEEPSLATE, 4.5F, 3.0F).setRequiresTool().sound(SoundType.STONE)));
    public static final Block DEEPSLATE_COAL_ORE = register("deepslate_coal_ore", new OreBlock(props(Material.ROCK, MaterialColor.DEEPSLATE, 4.5F, 3.0F).setRequiresTool().sound(SoundType.STONE)));
    public static final Block CHERRY_LOG = register("cherry_log", new RotatedPillarBlock(props(Material.WOOD, MaterialColor.WHITE_TERRACOTTA, 2.0F, 2.0F).sound(SoundType.WOOD)));
    public static final Block PALE_OAK_LOG = register("pale_oak_log", new RotatedPillarBlock(props(Material.WOOD, MaterialColor.QUARTZ, 2.0F, 2.0F).sound(SoundType.WOOD)));
    public static final Block MANGROVE_LOG = register("mangrove_log", new RotatedPillarBlock(props(Material.WOOD, MaterialColor.RED, 2.0F, 2.0F).sound(SoundType.WOOD)));
    public static final Block MANGROVE_ROOTS = register("mangrove_roots", new ModernWaterloggedBlock(props(Material.WOOD, MaterialColor.OBSIDIAN, 0.7F, 0.7F).sound(SoundType.WOOD).notSolid()));
    public static final Block MUDDY_MANGROVE_ROOTS = register("muddy_mangrove_roots", new RotatedPillarBlock(props(Material.EARTH, MaterialColor.OBSIDIAN, 0.7F, 0.7F).sound(SoundType.GROUND)));
    public static final Block BAMBOO_BLOCK = register("bamboo_block", new RotatedPillarBlock(props(Material.WOOD, MaterialColor.YELLOW, 2.0F, 2.0F).sound(SoundType.WOOD)));
    public static final Block STRIPPED_CHERRY_LOG = register("stripped_cherry_log", new RotatedPillarBlock(props(Material.WOOD, MaterialColor.WHITE_TERRACOTTA, 2.0F, 2.0F).sound(SoundType.WOOD)));
    public static final Block STRIPPED_PALE_OAK_LOG = register("stripped_pale_oak_log", new RotatedPillarBlock(props(Material.WOOD, MaterialColor.QUARTZ, 2.0F, 2.0F).sound(SoundType.WOOD)));
    public static final Block STRIPPED_MANGROVE_LOG = register("stripped_mangrove_log", new RotatedPillarBlock(props(Material.WOOD, MaterialColor.RED, 2.0F, 2.0F).sound(SoundType.WOOD)));
    public static final Block STRIPPED_BAMBOO_BLOCK = register("stripped_bamboo_block", new RotatedPillarBlock(props(Material.WOOD, MaterialColor.YELLOW, 2.0F, 2.0F).sound(SoundType.WOOD)));
    public static final Block CHERRY_WOOD = register("cherry_wood", new RotatedPillarBlock(props(Material.WOOD, MaterialColor.GRAY_TERRACOTTA, 2.0F, 2.0F).sound(SoundType.WOOD)));
    public static final Block MANGROVE_WOOD = register("mangrove_wood", new RotatedPillarBlock(props(Material.WOOD, MaterialColor.RED, 2.0F, 2.0F).sound(SoundType.WOOD)));
    public static final Block STRIPPED_CHERRY_WOOD = register("stripped_cherry_wood", new RotatedPillarBlock(props(Material.WOOD, MaterialColor.PINK_TERRACOTTA, 2.0F, 2.0F).sound(SoundType.WOOD)));
    public static final Block STRIPPED_PALE_OAK_WOOD = register("stripped_pale_oak_wood", new RotatedPillarBlock(props(Material.WOOD, MaterialColor.QUARTZ, 2.0F, 2.0F).sound(SoundType.WOOD)));
    public static final Block STRIPPED_MANGROVE_WOOD = register("stripped_mangrove_wood", new RotatedPillarBlock(props(Material.WOOD, MaterialColor.RED, 2.0F, 2.0F).sound(SoundType.WOOD)));
    public static final Block CHERRY_LEAVES = register("cherry_leaves", new ModernLeavesBlock(props(Material.LEAVES, MaterialColor.PINK, 0.2F, 0.2F).tickRandomly().sound(SoundType.PLANT).notSolid()));
    public static final Block PALE_OAK_LEAVES = register("pale_oak_leaves", new ModernLeavesBlock(props(Material.LEAVES, MaterialColor.IRON, 0.2F, 0.2F).tickRandomly().sound(SoundType.PLANT).notSolid()));
    public static final Block AZALEA_LEAVES = register("azalea_leaves", new ModernLeavesBlock(props(Material.LEAVES, MaterialColor.FOLIAGE, 0.2F, 0.2F).tickRandomly().sound(SoundType.PLANT).notSolid()));
    public static final Block FLOWERING_AZALEA_LEAVES = register("flowering_azalea_leaves", new ModernLeavesBlock(props(Material.LEAVES, MaterialColor.FOLIAGE, 0.2F, 0.2F).tickRandomly().sound(SoundType.PLANT).notSolid()));
    public static final Block DEEPSLATE_LAPIS_ORE = register("deepslate_lapis_ore", new OreBlock(props(Material.ROCK, MaterialColor.DEEPSLATE, 4.5F, 3.0F).setRequiresTool().sound(SoundType.STONE)));
    public static final Block BUSH = register("bush", new BushBlock(props(Material.ROCK, MaterialColor.FOLIAGE, 0.0F, 0.0F).doesNotBlockMovement().sound(SoundType.PLANT)));
    public static final Block SHORT_DRY_GRASS = register("short_dry_grass", new BushBlock(props(Material.ROCK, MaterialColor.YELLOW, 0.0F, 0.0F).doesNotBlockMovement().sound(SoundType.PLANT)));
    public static final Block TALL_DRY_GRASS = register("tall_dry_grass", new BushBlock(props(Material.ROCK, MaterialColor.YELLOW, 0.0F, 0.0F).doesNotBlockMovement().sound(SoundType.PLANT)));
    public static final Block TORCHFLOWER = register("torchflower", new BushBlock(props(Material.ROCK, MaterialColor.FOLIAGE, 0.0F, 0.0F).doesNotBlockMovement().sound(SoundType.PLANT)));
    public static final Block DEEPSLATE_DIAMOND_ORE = register("deepslate_diamond_ore", new OreBlock(props(Material.ROCK, MaterialColor.DEEPSLATE, 4.5F, 3.0F).setRequiresTool().sound(SoundType.STONE)));
    public static final Block CHERRY_SIGN = register("cherry_sign", new StandingSignBlock(props(Material.WOOD, MaterialColor.WHITE_TERRACOTTA, 1.0F, 1.0F).doesNotBlockMovement().sound(SoundType.WOOD)));
    public static final Block PALE_OAK_SIGN = register("pale_oak_sign", new StandingSignBlock(props(Material.WOOD, MaterialColor.QUARTZ, 1.0F, 1.0F).doesNotBlockMovement().sound(SoundType.WOOD)));
    public static final Block MANGROVE_SIGN = register("mangrove_sign", new StandingSignBlock(props(Material.WOOD, MaterialColor.RED, 1.0F, 1.0F).doesNotBlockMovement().sound(SoundType.WOOD)));
    public static final Block BAMBOO_SIGN = register("bamboo_sign", new StandingSignBlock(props(Material.WOOD, MaterialColor.YELLOW, 1.0F, 1.0F).doesNotBlockMovement().sound(SoundType.WOOD)));
    public static final Block CHERRY_WALL_SIGN = register("cherry_wall_sign", new WallSignBlock(props(Material.WOOD, MaterialColor.WHITE_TERRACOTTA, 1.0F, 1.0F).doesNotBlockMovement().sound(SoundType.WOOD).lootFrom(CHERRY_SIGN)));
    public static final Block PALE_OAK_WALL_SIGN = register("pale_oak_wall_sign", new WallSignBlock(props(Material.WOOD, MaterialColor.QUARTZ, 1.0F, 1.0F).doesNotBlockMovement().sound(SoundType.WOOD).lootFrom(PALE_OAK_SIGN)));
    public static final Block MANGROVE_WALL_SIGN = register("mangrove_wall_sign", new WallSignBlock(props(Material.WOOD, MaterialColor.RED, 1.0F, 1.0F).doesNotBlockMovement().sound(SoundType.WOOD).lootFrom(MANGROVE_SIGN)));
    public static final Block BAMBOO_WALL_SIGN = register("bamboo_wall_sign", new WallSignBlock(props(Material.WOOD, MaterialColor.YELLOW, 1.0F, 1.0F).doesNotBlockMovement().sound(SoundType.WOOD).lootFrom(BAMBOO_SIGN)));
    public static final Block OAK_HANGING_SIGN = register("oak_hanging_sign", new ModernCeilingHangingSignBlock(props(Material.WOOD, MaterialColor.WOOD, 1.0F, 1.0F).doesNotBlockMovement().sound(SoundType.WOOD)));
    public static final Block SPRUCE_HANGING_SIGN = register("spruce_hanging_sign", new ModernCeilingHangingSignBlock(props(Material.WOOD, MaterialColor.OBSIDIAN, 1.0F, 1.0F).doesNotBlockMovement().sound(SoundType.WOOD)));
    public static final Block BIRCH_HANGING_SIGN = register("birch_hanging_sign", new ModernCeilingHangingSignBlock(props(Material.WOOD, MaterialColor.SAND, 1.0F, 1.0F).doesNotBlockMovement().sound(SoundType.WOOD)));
    public static final Block ACACIA_HANGING_SIGN = register("acacia_hanging_sign", new ModernCeilingHangingSignBlock(props(Material.WOOD, MaterialColor.ADOBE, 1.0F, 1.0F).doesNotBlockMovement().sound(SoundType.WOOD)));
    public static final Block CHERRY_HANGING_SIGN = register("cherry_hanging_sign", new ModernCeilingHangingSignBlock(props(Material.WOOD, MaterialColor.PINK_TERRACOTTA, 1.0F, 1.0F).doesNotBlockMovement().sound(SoundType.WOOD)));
    public static final Block JUNGLE_HANGING_SIGN = register("jungle_hanging_sign", new ModernCeilingHangingSignBlock(props(Material.WOOD, MaterialColor.DIRT, 1.0F, 1.0F).doesNotBlockMovement().sound(SoundType.WOOD)));
    public static final Block DARK_OAK_HANGING_SIGN = register("dark_oak_hanging_sign", new ModernCeilingHangingSignBlock(props(Material.WOOD, MaterialColor.BROWN, 1.0F, 1.0F).doesNotBlockMovement().sound(SoundType.WOOD)));
    public static final Block PALE_OAK_HANGING_SIGN = register("pale_oak_hanging_sign", new ModernCeilingHangingSignBlock(props(Material.WOOD, MaterialColor.QUARTZ, 1.0F, 1.0F).doesNotBlockMovement().sound(SoundType.WOOD)));
    public static final Block CRIMSON_HANGING_SIGN = register("crimson_hanging_sign", new ModernCeilingHangingSignBlock(props(Material.WOOD, MaterialColor.CRIMSON_STEM, 1.0F, 1.0F).doesNotBlockMovement().sound(SoundType.WOOD)));
    public static final Block WARPED_HANGING_SIGN = register("warped_hanging_sign", new ModernCeilingHangingSignBlock(props(Material.WOOD, MaterialColor.WARPED_STEM, 1.0F, 1.0F).doesNotBlockMovement().sound(SoundType.WOOD)));
    public static final Block MANGROVE_HANGING_SIGN = register("mangrove_hanging_sign", new ModernCeilingHangingSignBlock(props(Material.WOOD, MaterialColor.RED, 1.0F, 1.0F).doesNotBlockMovement().sound(SoundType.WOOD)));
    public static final Block BAMBOO_HANGING_SIGN = register("bamboo_hanging_sign", new ModernCeilingHangingSignBlock(props(Material.WOOD, MaterialColor.YELLOW, 1.0F, 1.0F).doesNotBlockMovement().sound(SoundType.WOOD)));
    public static final Block OAK_WALL_HANGING_SIGN = register("oak_wall_hanging_sign", new ModernWallHangingSignBlock(props(Material.WOOD, MaterialColor.WOOD, 1.0F, 1.0F).doesNotBlockMovement().sound(SoundType.WOOD).lootFrom(OAK_HANGING_SIGN)));
    public static final Block SPRUCE_WALL_HANGING_SIGN = register("spruce_wall_hanging_sign", new ModernWallHangingSignBlock(props(Material.WOOD, MaterialColor.WOOD, 1.0F, 1.0F).doesNotBlockMovement().sound(SoundType.WOOD).lootFrom(SPRUCE_HANGING_SIGN)));
    public static final Block BIRCH_WALL_HANGING_SIGN = register("birch_wall_hanging_sign", new ModernWallHangingSignBlock(props(Material.WOOD, MaterialColor.SAND, 1.0F, 1.0F).doesNotBlockMovement().sound(SoundType.WOOD).lootFrom(BIRCH_HANGING_SIGN)));
    public static final Block ACACIA_WALL_HANGING_SIGN = register("acacia_wall_hanging_sign", new ModernWallHangingSignBlock(props(Material.WOOD, MaterialColor.ADOBE, 1.0F, 1.0F).doesNotBlockMovement().sound(SoundType.WOOD).lootFrom(ACACIA_HANGING_SIGN)));
    public static final Block CHERRY_WALL_HANGING_SIGN = register("cherry_wall_hanging_sign", new ModernWallHangingSignBlock(props(Material.WOOD, MaterialColor.PINK_TERRACOTTA, 1.0F, 1.0F).doesNotBlockMovement().sound(SoundType.WOOD).lootFrom(CHERRY_HANGING_SIGN)));
    public static final Block JUNGLE_WALL_HANGING_SIGN = register("jungle_wall_hanging_sign", new ModernWallHangingSignBlock(props(Material.WOOD, MaterialColor.DIRT, 1.0F, 1.0F).doesNotBlockMovement().sound(SoundType.WOOD).lootFrom(JUNGLE_HANGING_SIGN)));
    public static final Block DARK_OAK_WALL_HANGING_SIGN = register("dark_oak_wall_hanging_sign", new ModernWallHangingSignBlock(props(Material.WOOD, MaterialColor.BROWN, 1.0F, 1.0F).doesNotBlockMovement().sound(SoundType.WOOD).lootFrom(DARK_OAK_HANGING_SIGN)));
    public static final Block PALE_OAK_WALL_HANGING_SIGN = register("pale_oak_wall_hanging_sign", new ModernWallHangingSignBlock(props(Material.WOOD, MaterialColor.QUARTZ, 1.0F, 1.0F).doesNotBlockMovement().sound(SoundType.WOOD).lootFrom(PALE_OAK_HANGING_SIGN)));
    public static final Block MANGROVE_WALL_HANGING_SIGN = register("mangrove_wall_hanging_sign", new ModernWallHangingSignBlock(props(Material.WOOD, MaterialColor.RED, 1.0F, 1.0F).doesNotBlockMovement().sound(SoundType.WOOD).lootFrom(MANGROVE_HANGING_SIGN)));
    public static final Block CRIMSON_WALL_HANGING_SIGN = register("crimson_wall_hanging_sign", new ModernWallHangingSignBlock(props(Material.WOOD, MaterialColor.CRIMSON_STEM, 1.0F, 1.0F).doesNotBlockMovement().sound(SoundType.WOOD).lootFrom(CRIMSON_HANGING_SIGN)));
    public static final Block WARPED_WALL_HANGING_SIGN = register("warped_wall_hanging_sign", new ModernWallHangingSignBlock(props(Material.WOOD, MaterialColor.WARPED_STEM, 1.0F, 1.0F).doesNotBlockMovement().sound(SoundType.WOOD).lootFrom(WARPED_HANGING_SIGN)));
    public static final Block BAMBOO_WALL_HANGING_SIGN = register("bamboo_wall_hanging_sign", new ModernWallHangingSignBlock(props(Material.WOOD, MaterialColor.YELLOW, 1.0F, 1.0F).doesNotBlockMovement().sound(SoundType.WOOD).lootFrom(BAMBOO_HANGING_SIGN)));
    public static final Block CHERRY_PRESSURE_PLATE = register("cherry_pressure_plate", new PressurePlateBlock(PressurePlateBlock.Sensitivity.EVERYTHING, props(Material.WOOD, MaterialColor.WHITE_TERRACOTTA, 0.5F, 0.5F).doesNotBlockMovement().sound(SoundType.WOOD)));
    public static final Block PALE_OAK_PRESSURE_PLATE = register("pale_oak_pressure_plate", new PressurePlateBlock(PressurePlateBlock.Sensitivity.EVERYTHING, props(Material.WOOD, MaterialColor.QUARTZ, 0.5F, 0.5F).doesNotBlockMovement().sound(SoundType.WOOD)));
    public static final Block MANGROVE_PRESSURE_PLATE = register("mangrove_pressure_plate", new PressurePlateBlock(PressurePlateBlock.Sensitivity.EVERYTHING, props(Material.WOOD, MaterialColor.RED, 0.5F, 0.5F).doesNotBlockMovement().sound(SoundType.WOOD)));
    public static final Block BAMBOO_PRESSURE_PLATE = register("bamboo_pressure_plate", new PressurePlateBlock(PressurePlateBlock.Sensitivity.EVERYTHING, props(Material.WOOD, MaterialColor.YELLOW, 0.5F, 0.5F).doesNotBlockMovement().sound(SoundType.WOOD)));
    public static final Block DEEPSLATE_REDSTONE_ORE = register("deepslate_redstone_ore", new RedstoneOreBlock(props(Material.ROCK, MaterialColor.DEEPSLATE, 4.5F, 3.0F).setRequiresTool().setLightLevel(state -> state.get(RedstoneOreBlock.LIT) ? 9 : 0).tickRandomly().sound(SoundType.STONE)));
    public static final Block CACTUS_FLOWER = register("cactus_flower", new BushBlock(props(Material.ROCK, MaterialColor.PINK, 0.0F, 0.0F).doesNotBlockMovement().sound(SoundType.PLANT)));
    public static final Block CHERRY_TRAPDOOR = register("cherry_trapdoor", new TrapDoorBlock(props(Material.WOOD, MaterialColor.WHITE_TERRACOTTA, 3.0F, 3.0F).sound(SoundType.WOOD).notSolid().setAllowsSpawn((s, r, p2, t) -> false)));
    public static final Block PALE_OAK_TRAPDOOR = register("pale_oak_trapdoor", new TrapDoorBlock(props(Material.WOOD, MaterialColor.QUARTZ, 3.0F, 3.0F).sound(SoundType.WOOD).notSolid().setAllowsSpawn((s, r, p2, t) -> false)));
    public static final Block MANGROVE_TRAPDOOR = register("mangrove_trapdoor", new TrapDoorBlock(props(Material.WOOD, MaterialColor.RED, 3.0F, 3.0F).sound(SoundType.WOOD).notSolid().setAllowsSpawn((s, r, p2, t) -> false)));
    public static final Block BAMBOO_TRAPDOOR = register("bamboo_trapdoor", new TrapDoorBlock(props(Material.WOOD, MaterialColor.YELLOW, 3.0F, 3.0F).sound(SoundType.WOOD).notSolid().setAllowsSpawn((s, r, p2, t) -> false)));
    public static final Block PACKED_MUD = register("packed_mud", new Block(props(Material.ROCK, MaterialColor.DIRT, 1.0F, 3.0F).setRequiresTool().sound(SoundType.STONE)));
    public static final Block MUD_BRICKS = register("mud_bricks", new Block(props(Material.ROCK, MaterialColor.LIGHT_GRAY_TERRACOTTA, 1.5F, 3.0F).setRequiresTool().sound(SoundType.STONE)));
    public static final Block COPPER_BARS = register("copper_bars", new PaneBlock(props(Material.IRON, MaterialColor.AIR, 5.0F, 6.0F).setRequiresTool().sound(SoundType.METAL).notSolid()));
    public static final Block EXPOSED_COPPER_BARS = register("exposed_copper_bars", new PaneBlock(props(Material.IRON, MaterialColor.AIR, 5.0F, 6.0F).setRequiresTool().sound(SoundType.METAL).notSolid()));
    public static final Block WEATHERED_COPPER_BARS = register("weathered_copper_bars", new PaneBlock(props(Material.IRON, MaterialColor.AIR, 5.0F, 6.0F).setRequiresTool().sound(SoundType.METAL).notSolid()));
    public static final Block OXIDIZED_COPPER_BARS = register("oxidized_copper_bars", new PaneBlock(props(Material.IRON, MaterialColor.AIR, 5.0F, 6.0F).setRequiresTool().sound(SoundType.METAL).notSolid()));
    public static final Block WAXED_COPPER_BARS = register("waxed_copper_bars", new PaneBlock(props(Material.IRON, MaterialColor.AIR, 5.0F, 6.0F).setRequiresTool().sound(SoundType.METAL).notSolid()));
    public static final Block WAXED_EXPOSED_COPPER_BARS = register("waxed_exposed_copper_bars", new PaneBlock(props(Material.IRON, MaterialColor.AIR, 5.0F, 6.0F).setRequiresTool().sound(SoundType.METAL).notSolid()));
    public static final Block WAXED_WEATHERED_COPPER_BARS = register("waxed_weathered_copper_bars", new PaneBlock(props(Material.IRON, MaterialColor.AIR, 5.0F, 6.0F).setRequiresTool().sound(SoundType.METAL).notSolid()));
    public static final Block WAXED_OXIDIZED_COPPER_BARS = register("waxed_oxidized_copper_bars", new PaneBlock(props(Material.IRON, MaterialColor.AIR, 5.0F, 6.0F).setRequiresTool().sound(SoundType.METAL).notSolid()));
    public static final Block COPPER_CHAIN = register("copper_chain", new ChainBlock(props(Material.IRON, MaterialColor.AIR, 5.0F, 6.0F).setRequiresTool().sound(SoundType.CHAIN).notSolid()));
    public static final Block EXPOSED_COPPER_CHAIN = register("exposed_copper_chain", new ChainBlock(props(Material.IRON, MaterialColor.AIR, 5.0F, 6.0F).setRequiresTool().sound(SoundType.CHAIN).notSolid()));
    public static final Block WEATHERED_COPPER_CHAIN = register("weathered_copper_chain", new ChainBlock(props(Material.IRON, MaterialColor.AIR, 5.0F, 6.0F).setRequiresTool().sound(SoundType.CHAIN).notSolid()));
    public static final Block OXIDIZED_COPPER_CHAIN = register("oxidized_copper_chain", new ChainBlock(props(Material.IRON, MaterialColor.AIR, 5.0F, 6.0F).setRequiresTool().sound(SoundType.CHAIN).notSolid()));
    public static final Block WAXED_COPPER_CHAIN = register("waxed_copper_chain", new ChainBlock(props(Material.IRON, MaterialColor.AIR, 5.0F, 6.0F).setRequiresTool().sound(SoundType.CHAIN).notSolid()));
    public static final Block WAXED_EXPOSED_COPPER_CHAIN = register("waxed_exposed_copper_chain", new ChainBlock(props(Material.IRON, MaterialColor.AIR, 5.0F, 6.0F).setRequiresTool().sound(SoundType.CHAIN).notSolid()));
    public static final Block WAXED_WEATHERED_COPPER_CHAIN = register("waxed_weathered_copper_chain", new ChainBlock(props(Material.IRON, MaterialColor.AIR, 5.0F, 6.0F).setRequiresTool().sound(SoundType.CHAIN).notSolid()));
    public static final Block WAXED_OXIDIZED_COPPER_CHAIN = register("waxed_oxidized_copper_chain", new ChainBlock(props(Material.IRON, MaterialColor.AIR, 5.0F, 6.0F).setRequiresTool().sound(SoundType.CHAIN).notSolid()));
    public static final Block GLOW_LICHEN = register("glow_lichen", new ModernGlowLichenBlock(props(Material.WOOD, MaterialColor.GLOW_LICHEN, 0.2F, 0.2F).setLightLevel(ModernGlowLichenBlock.lightFromFaces()).doesNotBlockMovement().sound(SoundType.PLANT)));
    public static final Block RESIN_CLUMP = register("resin_clump", new ModernMultifaceBlock(props(Material.ROCK, MaterialColor.ORANGE_TERRACOTTA, 0.0F, 0.0F).doesNotBlockMovement().sound(SoundType.STONE)));
    public static final Block MUD_BRICK_STAIRS = register("mud_brick_stairs", new StairsBlock(MUD_BRICKS.getDefaultState(), props(Material.ROCK, MaterialColor.LIGHT_GRAY_TERRACOTTA, 1.5F, 3.0F).setRequiresTool().sound(SoundType.STONE)));
    public static final Block RESIN_BLOCK = register("resin_block", new Block(props(Material.ROCK, MaterialColor.ORANGE_TERRACOTTA, 0.0F, 0.0F).sound(SoundType.STONE)));
    public static final Block RESIN_BRICKS = register("resin_bricks", new Block(props(Material.ROCK, MaterialColor.ORANGE_TERRACOTTA, 1.5F, 6.0F).setRequiresTool().sound(SoundType.STONE)));
    public static final Block RESIN_BRICK_STAIRS = register("resin_brick_stairs", new StairsBlock(RESIN_BRICKS.getDefaultState(), props(Material.ROCK, MaterialColor.ORANGE_TERRACOTTA, 1.5F, 6.0F).setRequiresTool().sound(SoundType.STONE)));
    public static final Block RESIN_BRICK_SLAB = register("resin_brick_slab", new SlabBlock(props(Material.ROCK, MaterialColor.ORANGE_TERRACOTTA, 1.5F, 6.0F).setRequiresTool().sound(SoundType.STONE)));
    public static final Block RESIN_BRICK_WALL = register("resin_brick_wall", new WallBlock(props(Material.ROCK, MaterialColor.ORANGE_TERRACOTTA, 1.5F, 6.0F).setRequiresTool().sound(SoundType.STONE)));
    public static final Block CHISELED_RESIN_BRICKS = register("chiseled_resin_bricks", new Block(props(Material.ROCK, MaterialColor.ORANGE_TERRACOTTA, 1.5F, 6.0F).setRequiresTool().sound(SoundType.STONE)));
    public static final Block DEEPSLATE_EMERALD_ORE = register("deepslate_emerald_ore", new OreBlock(props(Material.ROCK, MaterialColor.DEEPSLATE, 4.5F, 3.0F).setRequiresTool().sound(SoundType.STONE)));
    public static final Block CHERRY_BUTTON = register("cherry_button", new WoodButtonBlock(props(Material.WOOD, MaterialColor.AIR, 0.5F, 0.5F).doesNotBlockMovement().sound(SoundType.WOOD)));
    public static final Block PALE_OAK_BUTTON = register("pale_oak_button", new WoodButtonBlock(props(Material.WOOD, MaterialColor.AIR, 0.5F, 0.5F).doesNotBlockMovement().sound(SoundType.WOOD)));
    public static final Block MANGROVE_BUTTON = register("mangrove_button", new WoodButtonBlock(props(Material.WOOD, MaterialColor.AIR, 0.5F, 0.5F).doesNotBlockMovement().sound(SoundType.WOOD)));
    public static final Block BAMBOO_BUTTON = register("bamboo_button", new WoodButtonBlock(props(Material.WOOD, MaterialColor.AIR, 0.5F, 0.5F).doesNotBlockMovement().sound(SoundType.WOOD)));
    public static final Block CHERRY_STAIRS = register("cherry_stairs", new StairsBlock(CHERRY_PLANKS.getDefaultState(), props(Material.WOOD, MaterialColor.WHITE_TERRACOTTA, 2.0F, 3.0F).sound(SoundType.WOOD)));
    public static final Block PALE_OAK_STAIRS = register("pale_oak_stairs", new StairsBlock(PALE_OAK_PLANKS.getDefaultState(), props(Material.WOOD, MaterialColor.QUARTZ, 2.0F, 3.0F).sound(SoundType.WOOD)));
    public static final Block MANGROVE_STAIRS = register("mangrove_stairs", new StairsBlock(MANGROVE_PLANKS.getDefaultState(), props(Material.WOOD, MaterialColor.RED, 2.0F, 3.0F).sound(SoundType.WOOD)));
    public static final Block BAMBOO_STAIRS = register("bamboo_stairs", new StairsBlock(Blocks.BAMBOO.getDefaultState(), props(Material.WOOD, MaterialColor.YELLOW, 2.0F, 3.0F).sound(SoundType.WOOD)));
    public static final Block BAMBOO_MOSAIC_STAIRS = register("bamboo_mosaic_stairs", new StairsBlock(BAMBOO_MOSAIC.getDefaultState(), props(Material.WOOD, MaterialColor.YELLOW, 2.0F, 3.0F).sound(SoundType.WOOD)));
    public static final Block CHERRY_SLAB = register("cherry_slab", new SlabBlock(props(Material.WOOD, MaterialColor.WHITE_TERRACOTTA, 2.0F, 3.0F).sound(SoundType.WOOD)));
    public static final Block PALE_OAK_SLAB = register("pale_oak_slab", new SlabBlock(props(Material.WOOD, MaterialColor.QUARTZ, 2.0F, 3.0F).sound(SoundType.WOOD)));
    public static final Block MANGROVE_SLAB = register("mangrove_slab", new SlabBlock(props(Material.WOOD, MaterialColor.RED, 2.0F, 3.0F).sound(SoundType.WOOD)));
    public static final Block BAMBOO_SLAB = register("bamboo_slab", new SlabBlock(props(Material.WOOD, MaterialColor.YELLOW, 2.0F, 3.0F).sound(SoundType.WOOD)));
    public static final Block BAMBOO_MOSAIC_SLAB = register("bamboo_mosaic_slab", new SlabBlock(props(Material.WOOD, MaterialColor.YELLOW, 2.0F, 3.0F).sound(SoundType.WOOD)));
    public static final Block MUD_BRICK_SLAB = register("mud_brick_slab", new SlabBlock(props(Material.ROCK, MaterialColor.LIGHT_GRAY_TERRACOTTA, 1.5F, 3.0F).setRequiresTool().sound(SoundType.STONE)));
    public static final Block CHERRY_FENCE_GATE = register("cherry_fence_gate", new FenceGateBlock(props(Material.WOOD, MaterialColor.WHITE_TERRACOTTA, 2.0F, 3.0F).sound(SoundType.WOOD)));
    public static final Block PALE_OAK_FENCE_GATE = register("pale_oak_fence_gate", new FenceGateBlock(props(Material.WOOD, MaterialColor.QUARTZ, 2.0F, 3.0F).sound(SoundType.WOOD)));
    public static final Block MANGROVE_FENCE_GATE = register("mangrove_fence_gate", new FenceGateBlock(props(Material.WOOD, MaterialColor.RED, 2.0F, 3.0F).sound(SoundType.WOOD)));
    public static final Block BAMBOO_FENCE_GATE = register("bamboo_fence_gate", new FenceGateBlock(props(Material.WOOD, MaterialColor.YELLOW, 2.0F, 3.0F).sound(SoundType.WOOD)));
    public static final Block CHERRY_FENCE = register("cherry_fence", new FenceBlock(props(Material.WOOD, MaterialColor.WHITE_TERRACOTTA, 2.0F, 3.0F).sound(SoundType.WOOD)));
    public static final Block PALE_OAK_FENCE = register("pale_oak_fence", new FenceBlock(props(Material.WOOD, MaterialColor.QUARTZ, 2.0F, 3.0F).sound(SoundType.WOOD)));
    public static final Block MANGROVE_FENCE = register("mangrove_fence", new FenceBlock(props(Material.WOOD, MaterialColor.RED, 2.0F, 3.0F).sound(SoundType.WOOD)));
    public static final Block BAMBOO_FENCE = register("bamboo_fence", new FenceBlock(props(Material.WOOD, MaterialColor.YELLOW, 2.0F, 3.0F).sound(SoundType.WOOD)));
    public static final Block CHERRY_DOOR = register("cherry_door", new DoorBlock(props(Material.WOOD, MaterialColor.WHITE_TERRACOTTA, 3.0F, 3.0F).sound(SoundType.WOOD).notSolid()));
    public static final Block PALE_OAK_DOOR = register("pale_oak_door", new DoorBlock(props(Material.WOOD, MaterialColor.QUARTZ, 3.0F, 3.0F).sound(SoundType.WOOD).notSolid()));
    public static final Block MANGROVE_DOOR = register("mangrove_door", new DoorBlock(props(Material.WOOD, MaterialColor.RED, 3.0F, 3.0F).sound(SoundType.WOOD).notSolid()));
    public static final Block BAMBOO_DOOR = register("bamboo_door", new DoorBlock(props(Material.WOOD, MaterialColor.YELLOW, 3.0F, 3.0F).sound(SoundType.WOOD).notSolid()));
    public static final Block MUD_BRICK_WALL = register("mud_brick_wall", new WallBlock(props(Material.ROCK, MaterialColor.LIGHT_GRAY_TERRACOTTA, 1.5F, 3.0F).setRequiresTool().sound(SoundType.STONE)));
    public static final Block COPPER_LANTERN = register("copper_lantern", new LanternBlock(props(Material.IRON, MaterialColor.IRON, 3.5F, 3.5F).setRequiresTool().setLightLevel(state -> 15).sound(SoundType.LANTERN).notSolid()));
    public static final Block EXPOSED_COPPER_LANTERN = register("exposed_copper_lantern", new LanternBlock(props(Material.IRON, MaterialColor.IRON, 3.5F, 3.5F).setRequiresTool().setLightLevel(state -> 15).sound(SoundType.LANTERN).notSolid()));
    public static final Block WEATHERED_COPPER_LANTERN = register("weathered_copper_lantern", new LanternBlock(props(Material.IRON, MaterialColor.IRON, 3.5F, 3.5F).setRequiresTool().setLightLevel(state -> 15).sound(SoundType.LANTERN).notSolid()));
    public static final Block OXIDIZED_COPPER_LANTERN = register("oxidized_copper_lantern", new LanternBlock(props(Material.IRON, MaterialColor.IRON, 3.5F, 3.5F).setRequiresTool().setLightLevel(state -> 15).sound(SoundType.LANTERN).notSolid()));
    public static final Block WAXED_COPPER_LANTERN = register("waxed_copper_lantern", new LanternBlock(props(Material.IRON, MaterialColor.IRON, 3.5F, 3.5F).setRequiresTool().setLightLevel(state -> 15).sound(SoundType.LANTERN).notSolid()));
    public static final Block WAXED_EXPOSED_COPPER_LANTERN = register("waxed_exposed_copper_lantern", new LanternBlock(props(Material.IRON, MaterialColor.IRON, 3.5F, 3.5F).setRequiresTool().setLightLevel(state -> 15).sound(SoundType.LANTERN).notSolid()));
    public static final Block WAXED_WEATHERED_COPPER_LANTERN = register("waxed_weathered_copper_lantern", new LanternBlock(props(Material.IRON, MaterialColor.IRON, 3.5F, 3.5F).setRequiresTool().setLightLevel(state -> 15).sound(SoundType.LANTERN).notSolid()));
    public static final Block WAXED_OXIDIZED_COPPER_LANTERN = register("waxed_oxidized_copper_lantern", new LanternBlock(props(Material.IRON, MaterialColor.IRON, 3.5F, 3.5F).setRequiresTool().setLightLevel(state -> 15).sound(SoundType.LANTERN).notSolid()));
    public static final Block CANDLE = register("candle", new ModernCandleBlock(props(Material.ROCK, MaterialColor.SAND, 0.1F, 0.1F).setLightLevel(ModernCandleBlock.lightFromCandles()).sound(SoundType.CLOTH)));
    public static final Block WHITE_CANDLE = register("white_candle", new ModernCandleBlock(props(Material.ROCK, MaterialColor.WOOL, 0.1F, 0.1F).setLightLevel(ModernCandleBlock.lightFromCandles()).sound(SoundType.CLOTH)));
    public static final Block ORANGE_CANDLE = register("orange_candle", new ModernCandleBlock(props(Material.ROCK, MaterialColor.ADOBE, 0.1F, 0.1F).setLightLevel(ModernCandleBlock.lightFromCandles()).sound(SoundType.CLOTH)));
    public static final Block MAGENTA_CANDLE = register("magenta_candle", new ModernCandleBlock(props(Material.ROCK, MaterialColor.MAGENTA, 0.1F, 0.1F).setLightLevel(ModernCandleBlock.lightFromCandles()).sound(SoundType.CLOTH)));
    public static final Block LIGHT_BLUE_CANDLE = register("light_blue_candle", new ModernCandleBlock(props(Material.ROCK, MaterialColor.LIGHT_BLUE, 0.1F, 0.1F).setLightLevel(ModernCandleBlock.lightFromCandles()).sound(SoundType.CLOTH)));
    public static final Block YELLOW_CANDLE = register("yellow_candle", new ModernCandleBlock(props(Material.ROCK, MaterialColor.YELLOW, 0.1F, 0.1F).setLightLevel(ModernCandleBlock.lightFromCandles()).sound(SoundType.CLOTH)));
    public static final Block LIME_CANDLE = register("lime_candle", new ModernCandleBlock(props(Material.ROCK, MaterialColor.LIME, 0.1F, 0.1F).setLightLevel(ModernCandleBlock.lightFromCandles()).sound(SoundType.CLOTH)));
    public static final Block PINK_CANDLE = register("pink_candle", new ModernCandleBlock(props(Material.ROCK, MaterialColor.PINK, 0.1F, 0.1F).setLightLevel(ModernCandleBlock.lightFromCandles()).sound(SoundType.CLOTH)));
    public static final Block GRAY_CANDLE = register("gray_candle", new ModernCandleBlock(props(Material.ROCK, MaterialColor.GRAY, 0.1F, 0.1F).setLightLevel(ModernCandleBlock.lightFromCandles()).sound(SoundType.CLOTH)));
    public static final Block LIGHT_GRAY_CANDLE = register("light_gray_candle", new ModernCandleBlock(props(Material.ROCK, MaterialColor.LIGHT_GRAY, 0.1F, 0.1F).setLightLevel(ModernCandleBlock.lightFromCandles()).sound(SoundType.CLOTH)));
    public static final Block CYAN_CANDLE = register("cyan_candle", new ModernCandleBlock(props(Material.ROCK, MaterialColor.CYAN, 0.1F, 0.1F).setLightLevel(ModernCandleBlock.lightFromCandles()).sound(SoundType.CLOTH)));
    public static final Block PURPLE_CANDLE = register("purple_candle", new ModernCandleBlock(props(Material.ROCK, MaterialColor.PURPLE, 0.1F, 0.1F).setLightLevel(ModernCandleBlock.lightFromCandles()).sound(SoundType.CLOTH)));
    public static final Block BLUE_CANDLE = register("blue_candle", new ModernCandleBlock(props(Material.ROCK, MaterialColor.BLUE, 0.1F, 0.1F).setLightLevel(ModernCandleBlock.lightFromCandles()).sound(SoundType.CLOTH)));
    public static final Block BROWN_CANDLE = register("brown_candle", new ModernCandleBlock(props(Material.ROCK, MaterialColor.BROWN, 0.1F, 0.1F).setLightLevel(ModernCandleBlock.lightFromCandles()).sound(SoundType.CLOTH)));
    public static final Block GREEN_CANDLE = register("green_candle", new ModernCandleBlock(props(Material.ROCK, MaterialColor.GREEN, 0.1F, 0.1F).setLightLevel(ModernCandleBlock.lightFromCandles()).sound(SoundType.CLOTH)));
    public static final Block RED_CANDLE = register("red_candle", new ModernCandleBlock(props(Material.ROCK, MaterialColor.RED, 0.1F, 0.1F).setLightLevel(ModernCandleBlock.lightFromCandles()).sound(SoundType.CLOTH)));
    public static final Block BLACK_CANDLE = register("black_candle", new ModernCandleBlock(props(Material.ROCK, MaterialColor.BLACK, 0.1F, 0.1F).setLightLevel(ModernCandleBlock.lightFromCandles()).sound(SoundType.CLOTH)));
    public static final Block CANDLE_CAKE = register("candle_cake", new ModernCandleCakeBlock(CANDLE, props(Material.ROCK, MaterialColor.AIR, 0.5F, 0.5F).setLightLevel(ModernCandleCakeBlock.lightWhenLit()).sound(SoundType.CLOTH)));
    public static final Block WHITE_CANDLE_CAKE = register("white_candle_cake", new ModernCandleCakeBlock(WHITE_CANDLE, props(Material.ROCK, MaterialColor.AIR, 0.5F, 0.5F).setLightLevel(ModernCandleCakeBlock.lightWhenLit()).sound(SoundType.CLOTH)));
    public static final Block ORANGE_CANDLE_CAKE = register("orange_candle_cake", new ModernCandleCakeBlock(ORANGE_CANDLE, props(Material.ROCK, MaterialColor.AIR, 0.5F, 0.5F).setLightLevel(ModernCandleCakeBlock.lightWhenLit()).sound(SoundType.CLOTH)));
    public static final Block MAGENTA_CANDLE_CAKE = register("magenta_candle_cake", new ModernCandleCakeBlock(MAGENTA_CANDLE, props(Material.ROCK, MaterialColor.AIR, 0.5F, 0.5F).setLightLevel(ModernCandleCakeBlock.lightWhenLit()).sound(SoundType.CLOTH)));
    public static final Block LIGHT_BLUE_CANDLE_CAKE = register("light_blue_candle_cake", new ModernCandleCakeBlock(LIGHT_BLUE_CANDLE, props(Material.ROCK, MaterialColor.AIR, 0.5F, 0.5F).setLightLevel(ModernCandleCakeBlock.lightWhenLit()).sound(SoundType.CLOTH)));
    public static final Block YELLOW_CANDLE_CAKE = register("yellow_candle_cake", new ModernCandleCakeBlock(YELLOW_CANDLE, props(Material.ROCK, MaterialColor.AIR, 0.5F, 0.5F).setLightLevel(ModernCandleCakeBlock.lightWhenLit()).sound(SoundType.CLOTH)));
    public static final Block LIME_CANDLE_CAKE = register("lime_candle_cake", new ModernCandleCakeBlock(LIME_CANDLE, props(Material.ROCK, MaterialColor.AIR, 0.5F, 0.5F).setLightLevel(ModernCandleCakeBlock.lightWhenLit()).sound(SoundType.CLOTH)));
    public static final Block PINK_CANDLE_CAKE = register("pink_candle_cake", new ModernCandleCakeBlock(PINK_CANDLE, props(Material.ROCK, MaterialColor.AIR, 0.5F, 0.5F).setLightLevel(ModernCandleCakeBlock.lightWhenLit()).sound(SoundType.CLOTH)));
    public static final Block GRAY_CANDLE_CAKE = register("gray_candle_cake", new ModernCandleCakeBlock(GRAY_CANDLE, props(Material.ROCK, MaterialColor.AIR, 0.5F, 0.5F).setLightLevel(ModernCandleCakeBlock.lightWhenLit()).sound(SoundType.CLOTH)));
    public static final Block LIGHT_GRAY_CANDLE_CAKE = register("light_gray_candle_cake", new ModernCandleCakeBlock(LIGHT_GRAY_CANDLE, props(Material.ROCK, MaterialColor.AIR, 0.5F, 0.5F).setLightLevel(ModernCandleCakeBlock.lightWhenLit()).sound(SoundType.CLOTH)));
    public static final Block CYAN_CANDLE_CAKE = register("cyan_candle_cake", new ModernCandleCakeBlock(CYAN_CANDLE, props(Material.ROCK, MaterialColor.AIR, 0.5F, 0.5F).setLightLevel(ModernCandleCakeBlock.lightWhenLit()).sound(SoundType.CLOTH)));
    public static final Block PURPLE_CANDLE_CAKE = register("purple_candle_cake", new ModernCandleCakeBlock(PURPLE_CANDLE, props(Material.ROCK, MaterialColor.AIR, 0.5F, 0.5F).setLightLevel(ModernCandleCakeBlock.lightWhenLit()).sound(SoundType.CLOTH)));
    public static final Block BLUE_CANDLE_CAKE = register("blue_candle_cake", new ModernCandleCakeBlock(BLUE_CANDLE, props(Material.ROCK, MaterialColor.AIR, 0.5F, 0.5F).setLightLevel(ModernCandleCakeBlock.lightWhenLit()).sound(SoundType.CLOTH)));
    public static final Block BROWN_CANDLE_CAKE = register("brown_candle_cake", new ModernCandleCakeBlock(BROWN_CANDLE, props(Material.ROCK, MaterialColor.AIR, 0.5F, 0.5F).setLightLevel(ModernCandleCakeBlock.lightWhenLit()).sound(SoundType.CLOTH)));
    public static final Block GREEN_CANDLE_CAKE = register("green_candle_cake", new ModernCandleCakeBlock(GREEN_CANDLE, props(Material.ROCK, MaterialColor.AIR, 0.5F, 0.5F).setLightLevel(ModernCandleCakeBlock.lightWhenLit()).sound(SoundType.CLOTH)));
    public static final Block RED_CANDLE_CAKE = register("red_candle_cake", new ModernCandleCakeBlock(RED_CANDLE, props(Material.ROCK, MaterialColor.AIR, 0.5F, 0.5F).setLightLevel(ModernCandleCakeBlock.lightWhenLit()).sound(SoundType.CLOTH)));
    public static final Block BLACK_CANDLE_CAKE = register("black_candle_cake", new ModernCandleCakeBlock(BLACK_CANDLE, props(Material.ROCK, MaterialColor.AIR, 0.5F, 0.5F).setLightLevel(ModernCandleCakeBlock.lightWhenLit()).sound(SoundType.CLOTH)));
    public static final Block AMETHYST_BLOCK = register("amethyst_block", new ModernAmethystBlock(props(Material.ROCK, MaterialColor.PURPLE, 1.5F, 1.5F).setRequiresTool().sound(SoundType.GLASS)));
    public static final Block BUDDING_AMETHYST = register("budding_amethyst", new ModernAmethystBlock(props(Material.ROCK, MaterialColor.PURPLE, 1.5F, 1.5F).setRequiresTool().tickRandomly().sound(SoundType.GLASS)));
    public static final Block AMETHYST_CLUSTER = register("amethyst_cluster", ModernAmethystClusterBlock.forId("amethyst_cluster", props(Material.ROCK, MaterialColor.PURPLE, 1.5F, 1.5F).setRequiresTool().setLightLevel(state -> 5).sound(SoundType.STONE)));
    public static final Block LARGE_AMETHYST_BUD = register("large_amethyst_bud", ModernAmethystClusterBlock.forId("large_amethyst_bud", props(Material.ROCK, MaterialColor.PURPLE, 1.5F, 1.5F).setRequiresTool().setLightLevel(state -> 4).sound(SoundType.STONE)));
    public static final Block MEDIUM_AMETHYST_BUD = register("medium_amethyst_bud", ModernAmethystClusterBlock.forId("medium_amethyst_bud", props(Material.ROCK, MaterialColor.PURPLE, 1.5F, 1.5F).setRequiresTool().setLightLevel(state -> 2).sound(SoundType.STONE)));
    public static final Block SMALL_AMETHYST_BUD = register("small_amethyst_bud", ModernAmethystClusterBlock.forId("small_amethyst_bud", props(Material.ROCK, MaterialColor.PURPLE, 1.5F, 1.5F).setRequiresTool().setLightLevel(state -> 1).sound(SoundType.STONE)));
    public static final Block TUFF = register("tuff", new Block(props(Material.ROCK, MaterialColor.GRAY_TERRACOTTA, 1.5F, 6.0F).setRequiresTool().sound(SoundType.STONE)));
    public static final Block TUFF_SLAB = register("tuff_slab", new SlabBlock(props(Material.ROCK, MaterialColor.GRAY_TERRACOTTA, 1.5F, 6.0F).setRequiresTool().sound(SoundType.STONE)));
    public static final Block TUFF_STAIRS = register("tuff_stairs", new StairsBlock(TUFF.getDefaultState(), props(Material.ROCK, MaterialColor.GRAY_TERRACOTTA, 1.5F, 6.0F).setRequiresTool().sound(SoundType.STONE)));
    public static final Block TUFF_WALL = register("tuff_wall", new WallBlock(props(Material.ROCK, MaterialColor.GRAY_TERRACOTTA, 1.5F, 6.0F).setRequiresTool().sound(SoundType.STONE)));
    public static final Block POLISHED_TUFF = register("polished_tuff", new Block(props(Material.ROCK, MaterialColor.GRAY_TERRACOTTA, 1.5F, 6.0F).setRequiresTool().sound(SoundType.STONE)));
    public static final Block POLISHED_TUFF_SLAB = register("polished_tuff_slab", new SlabBlock(props(Material.ROCK, MaterialColor.GRAY_TERRACOTTA, 1.5F, 6.0F).setRequiresTool().sound(SoundType.STONE)));
    public static final Block POLISHED_TUFF_STAIRS = register("polished_tuff_stairs", new StairsBlock(POLISHED_TUFF.getDefaultState(), props(Material.ROCK, MaterialColor.GRAY_TERRACOTTA, 1.5F, 6.0F).setRequiresTool().sound(SoundType.STONE)));
    public static final Block POLISHED_TUFF_WALL = register("polished_tuff_wall", new WallBlock(props(Material.ROCK, MaterialColor.GRAY_TERRACOTTA, 1.5F, 6.0F).setRequiresTool().sound(SoundType.STONE)));
    public static final Block CHISELED_TUFF = register("chiseled_tuff", new Block(props(Material.ROCK, MaterialColor.GRAY_TERRACOTTA, 1.5F, 6.0F).setRequiresTool().sound(SoundType.STONE)));
    public static final Block TUFF_BRICKS = register("tuff_bricks", new Block(props(Material.ROCK, MaterialColor.GRAY_TERRACOTTA, 1.5F, 6.0F).setRequiresTool().sound(SoundType.STONE)));
    public static final Block TUFF_BRICK_SLAB = register("tuff_brick_slab", new SlabBlock(props(Material.ROCK, MaterialColor.GRAY_TERRACOTTA, 1.5F, 6.0F).setRequiresTool().sound(SoundType.STONE)));
    public static final Block TUFF_BRICK_STAIRS = register("tuff_brick_stairs", new StairsBlock(TUFF_BRICKS.getDefaultState(), props(Material.ROCK, MaterialColor.GRAY_TERRACOTTA, 1.5F, 6.0F).setRequiresTool().sound(SoundType.STONE)));
    public static final Block TUFF_BRICK_WALL = register("tuff_brick_wall", new WallBlock(props(Material.ROCK, MaterialColor.GRAY_TERRACOTTA, 1.5F, 6.0F).setRequiresTool().sound(SoundType.STONE)));
    public static final Block CHISELED_TUFF_BRICKS = register("chiseled_tuff_bricks", new Block(props(Material.ROCK, MaterialColor.GRAY_TERRACOTTA, 1.5F, 6.0F).setRequiresTool().sound(SoundType.STONE)));
    public static final Block CALCITE = register("calcite", new Block(props(Material.ROCK, MaterialColor.WHITE_TERRACOTTA, 0.75F, 0.75F).setRequiresTool().sound(SoundType.STONE)));
    public static final Block TINTED_GLASS = register("tinted_glass", new GlassBlock(props(Material.ROCK, MaterialColor.GRAY, 0.3F, 0.3F).sound(SoundType.GLASS)));
    public static final Block POWDER_SNOW = register("powder_snow", new Block(props(Material.ROCK, MaterialColor.SNOW, 0.25F, 0.25F).sound(SoundType.SNOW)));
    public static final Block SCULK = register("sculk", new Block(props(Material.PLANTS, MaterialColor.BLACK, 0.2F, 0.2F).sound(SoundType.STONE)));
    public static final Block SCULK_VEIN = register("sculk_vein", new ModernSculkVeinBlock(props(Material.PLANTS, MaterialColor.BLACK, 0.2F, 0.2F).doesNotBlockMovement().sound(SoundType.STONE)));
    public static final Block SCULK_CATALYST = register("sculk_catalyst", new ModernSculkCatalystBlock(props(Material.PLANTS, MaterialColor.BLACK, 3.0F, 3.0F).setLightLevel(state -> 6).sound(SoundType.STONE)));
    public static final Block SCULK_SHRIEKER = register("sculk_shrieker", new ModernSculkShriekerBlock(props(Material.PLANTS, MaterialColor.BLACK, 3.0F, 3.0F).sound(SoundType.STONE)));
    public static final Block COPPER_BLOCK = register("copper_block", new Block(props(Material.IRON, MaterialColor.ADOBE, 3.0F, 6.0F).setRequiresTool().sound(SoundType.METAL)));
    public static final Block EXPOSED_COPPER = register("exposed_copper", new Block(props(Material.IRON, MaterialColor.LIGHT_GRAY_TERRACOTTA, 3.0F, 6.0F).setRequiresTool().sound(SoundType.METAL)));
    public static final Block WEATHERED_COPPER = register("weathered_copper", new Block(props(Material.IRON, MaterialColor.WARPED_STEM, 3.0F, 6.0F).setRequiresTool().sound(SoundType.METAL)));
    public static final Block OXIDIZED_COPPER = register("oxidized_copper", new Block(props(Material.IRON, MaterialColor.WARPED_NYLIUM, 3.0F, 6.0F).setRequiresTool().sound(SoundType.METAL)));
    public static final Block COPPER_ORE = register("copper_ore", new OreBlock(props(Material.IRON, MaterialColor.STONE, 3.0F, 3.0F).setRequiresTool().sound(SoundType.STONE)));
    public static final Block DEEPSLATE_COPPER_ORE = register("deepslate_copper_ore", new OreBlock(props(Material.IRON, MaterialColor.DEEPSLATE, 4.5F, 3.0F).setRequiresTool().sound(SoundType.STONE)));
    public static final Block OXIDIZED_CUT_COPPER = register("oxidized_cut_copper", new Block(props(Material.IRON, MaterialColor.WARPED_NYLIUM, 3.0F, 6.0F).setRequiresTool().sound(SoundType.METAL)));
    public static final Block WEATHERED_CUT_COPPER = register("weathered_cut_copper", new Block(props(Material.IRON, MaterialColor.WARPED_STEM, 3.0F, 6.0F).setRequiresTool().sound(SoundType.METAL)));
    public static final Block EXPOSED_CUT_COPPER = register("exposed_cut_copper", new Block(props(Material.IRON, MaterialColor.LIGHT_GRAY_TERRACOTTA, 3.0F, 6.0F).setRequiresTool().sound(SoundType.METAL)));
    public static final Block CUT_COPPER = register("cut_copper", new Block(props(Material.IRON, MaterialColor.ADOBE, 3.0F, 6.0F).setRequiresTool().sound(SoundType.METAL)));
    public static final Block OXIDIZED_CHISELED_COPPER = register("oxidized_chiseled_copper", new Block(props(Material.IRON, MaterialColor.WARPED_NYLIUM, 3.0F, 6.0F).setRequiresTool().sound(SoundType.METAL)));
    public static final Block WEATHERED_CHISELED_COPPER = register("weathered_chiseled_copper", new Block(props(Material.IRON, MaterialColor.WARPED_STEM, 3.0F, 6.0F).setRequiresTool().sound(SoundType.METAL)));
    public static final Block EXPOSED_CHISELED_COPPER = register("exposed_chiseled_copper", new Block(props(Material.IRON, MaterialColor.LIGHT_GRAY_TERRACOTTA, 3.0F, 6.0F).setRequiresTool().sound(SoundType.METAL)));
    public static final Block CHISELED_COPPER = register("chiseled_copper", new Block(props(Material.IRON, MaterialColor.ADOBE, 3.0F, 6.0F).setRequiresTool().sound(SoundType.METAL)));
    public static final Block WAXED_OXIDIZED_CHISELED_COPPER = register("waxed_oxidized_chiseled_copper", new Block(props(Material.IRON, MaterialColor.WARPED_NYLIUM, 3.0F, 6.0F).setRequiresTool().sound(SoundType.METAL)));
    public static final Block WAXED_WEATHERED_CHISELED_COPPER = register("waxed_weathered_chiseled_copper", new Block(props(Material.IRON, MaterialColor.WARPED_STEM, 3.0F, 6.0F).setRequiresTool().sound(SoundType.METAL)));
    public static final Block WAXED_EXPOSED_CHISELED_COPPER = register("waxed_exposed_chiseled_copper", new Block(props(Material.IRON, MaterialColor.LIGHT_GRAY_TERRACOTTA, 3.0F, 6.0F).setRequiresTool().sound(SoundType.METAL)));
    public static final Block WAXED_CHISELED_COPPER = register("waxed_chiseled_copper", new Block(props(Material.IRON, MaterialColor.ADOBE, 3.0F, 6.0F).setRequiresTool().sound(SoundType.METAL)));
    public static final Block OXIDIZED_CUT_COPPER_STAIRS = register("oxidized_cut_copper_stairs", new StairsBlock(OXIDIZED_CUT_COPPER.getDefaultState(), props(Material.IRON, MaterialColor.WARPED_NYLIUM, 3.0F, 6.0F).setRequiresTool().sound(SoundType.METAL)));
    public static final Block WEATHERED_CUT_COPPER_STAIRS = register("weathered_cut_copper_stairs", new StairsBlock(WEATHERED_CUT_COPPER.getDefaultState(), props(Material.IRON, MaterialColor.WARPED_STEM, 3.0F, 6.0F).setRequiresTool().sound(SoundType.METAL)));
    public static final Block EXPOSED_CUT_COPPER_STAIRS = register("exposed_cut_copper_stairs", new StairsBlock(EXPOSED_CUT_COPPER.getDefaultState(), props(Material.IRON, MaterialColor.LIGHT_GRAY_TERRACOTTA, 3.0F, 6.0F).setRequiresTool().sound(SoundType.METAL)));
    public static final Block CUT_COPPER_STAIRS = register("cut_copper_stairs", new StairsBlock(CUT_COPPER.getDefaultState(), props(Material.IRON, MaterialColor.ADOBE, 3.0F, 6.0F).setRequiresTool().sound(SoundType.METAL)));
    public static final Block OXIDIZED_CUT_COPPER_SLAB = register("oxidized_cut_copper_slab", new SlabBlock(props(Material.IRON, MaterialColor.WARPED_NYLIUM, 3.0F, 6.0F).setRequiresTool().sound(SoundType.METAL)));
    public static final Block WEATHERED_CUT_COPPER_SLAB = register("weathered_cut_copper_slab", new SlabBlock(props(Material.IRON, MaterialColor.WARPED_STEM, 3.0F, 6.0F).setRequiresTool().sound(SoundType.METAL)));
    public static final Block EXPOSED_CUT_COPPER_SLAB = register("exposed_cut_copper_slab", new SlabBlock(props(Material.IRON, MaterialColor.LIGHT_GRAY_TERRACOTTA, 3.0F, 6.0F).setRequiresTool().sound(SoundType.METAL)));
    public static final Block CUT_COPPER_SLAB = register("cut_copper_slab", new SlabBlock(props(Material.IRON, MaterialColor.ADOBE, 3.0F, 6.0F).setRequiresTool().sound(SoundType.METAL)));
    public static final Block WAXED_COPPER_BLOCK = register("waxed_copper_block", new Block(props(Material.IRON, MaterialColor.ADOBE, 3.0F, 6.0F).setRequiresTool().sound(SoundType.METAL)));
    public static final Block WAXED_WEATHERED_COPPER = register("waxed_weathered_copper", new Block(props(Material.IRON, MaterialColor.WARPED_STEM, 3.0F, 6.0F).setRequiresTool().sound(SoundType.METAL)));
    public static final Block WAXED_EXPOSED_COPPER = register("waxed_exposed_copper", new Block(props(Material.IRON, MaterialColor.LIGHT_GRAY_TERRACOTTA, 3.0F, 6.0F).setRequiresTool().sound(SoundType.METAL)));
    public static final Block WAXED_OXIDIZED_COPPER = register("waxed_oxidized_copper", new Block(props(Material.IRON, MaterialColor.WARPED_NYLIUM, 3.0F, 6.0F).setRequiresTool().sound(SoundType.METAL)));
    public static final Block WAXED_OXIDIZED_CUT_COPPER = register("waxed_oxidized_cut_copper", new Block(props(Material.IRON, MaterialColor.WARPED_NYLIUM, 3.0F, 6.0F).setRequiresTool().sound(SoundType.METAL)));
    public static final Block WAXED_WEATHERED_CUT_COPPER = register("waxed_weathered_cut_copper", new Block(props(Material.IRON, MaterialColor.WARPED_STEM, 3.0F, 6.0F).setRequiresTool().sound(SoundType.METAL)));
    public static final Block WAXED_EXPOSED_CUT_COPPER = register("waxed_exposed_cut_copper", new Block(props(Material.IRON, MaterialColor.LIGHT_GRAY_TERRACOTTA, 3.0F, 6.0F).setRequiresTool().sound(SoundType.METAL)));
    public static final Block WAXED_CUT_COPPER = register("waxed_cut_copper", new Block(props(Material.IRON, MaterialColor.ADOBE, 3.0F, 6.0F).setRequiresTool().sound(SoundType.METAL)));
    public static final Block WAXED_OXIDIZED_CUT_COPPER_STAIRS = register("waxed_oxidized_cut_copper_stairs", new StairsBlock(WAXED_OXIDIZED_CUT_COPPER.getDefaultState(), props(Material.IRON, MaterialColor.WARPED_NYLIUM, 3.0F, 6.0F).setRequiresTool().sound(SoundType.METAL)));
    public static final Block WAXED_WEATHERED_CUT_COPPER_STAIRS = register("waxed_weathered_cut_copper_stairs", new StairsBlock(WAXED_WEATHERED_CUT_COPPER.getDefaultState(), props(Material.IRON, MaterialColor.WARPED_STEM, 3.0F, 6.0F).setRequiresTool().sound(SoundType.METAL)));
    public static final Block WAXED_EXPOSED_CUT_COPPER_STAIRS = register("waxed_exposed_cut_copper_stairs", new StairsBlock(WAXED_EXPOSED_CUT_COPPER.getDefaultState(), props(Material.IRON, MaterialColor.LIGHT_GRAY_TERRACOTTA, 3.0F, 6.0F).setRequiresTool().sound(SoundType.METAL)));
    public static final Block WAXED_CUT_COPPER_STAIRS = register("waxed_cut_copper_stairs", new StairsBlock(WAXED_CUT_COPPER.getDefaultState(), props(Material.IRON, MaterialColor.ADOBE, 3.0F, 6.0F).setRequiresTool().sound(SoundType.METAL)));
    public static final Block WAXED_OXIDIZED_CUT_COPPER_SLAB = register("waxed_oxidized_cut_copper_slab", new SlabBlock(props(Material.IRON, MaterialColor.WARPED_NYLIUM, 3.0F, 6.0F).setRequiresTool().sound(SoundType.METAL)));
    public static final Block WAXED_WEATHERED_CUT_COPPER_SLAB = register("waxed_weathered_cut_copper_slab", new SlabBlock(props(Material.IRON, MaterialColor.WARPED_STEM, 3.0F, 6.0F).setRequiresTool().sound(SoundType.METAL)));
    public static final Block WAXED_EXPOSED_CUT_COPPER_SLAB = register("waxed_exposed_cut_copper_slab", new SlabBlock(props(Material.IRON, MaterialColor.LIGHT_GRAY_TERRACOTTA, 3.0F, 6.0F).setRequiresTool().sound(SoundType.METAL)));
    public static final Block WAXED_CUT_COPPER_SLAB = register("waxed_cut_copper_slab", new SlabBlock(props(Material.IRON, MaterialColor.ADOBE, 3.0F, 6.0F).setRequiresTool().sound(SoundType.METAL)));
    public static final Block COPPER_DOOR = register("copper_door", new DoorBlock(props(Material.ROCK, MaterialColor.ADOBE, 3.0F, 6.0F).setRequiresTool().sound(SoundType.METAL).notSolid()));
    public static final Block EXPOSED_COPPER_DOOR = register("exposed_copper_door", new DoorBlock(props(Material.ROCK, MaterialColor.LIGHT_GRAY_TERRACOTTA, 3.0F, 6.0F).setRequiresTool().sound(SoundType.METAL).notSolid()));
    public static final Block OXIDIZED_COPPER_DOOR = register("oxidized_copper_door", new DoorBlock(props(Material.ROCK, MaterialColor.WARPED_NYLIUM, 3.0F, 6.0F).setRequiresTool().sound(SoundType.METAL).notSolid()));
    public static final Block WEATHERED_COPPER_DOOR = register("weathered_copper_door", new DoorBlock(props(Material.ROCK, MaterialColor.WARPED_STEM, 3.0F, 6.0F).setRequiresTool().sound(SoundType.METAL).notSolid()));
    public static final Block WAXED_COPPER_DOOR = register("waxed_copper_door", new DoorBlock(props(Material.ROCK, MaterialColor.ADOBE, 3.0F, 6.0F).setRequiresTool().sound(SoundType.METAL).notSolid()));
    public static final Block WAXED_EXPOSED_COPPER_DOOR = register("waxed_exposed_copper_door", new DoorBlock(props(Material.ROCK, MaterialColor.LIGHT_GRAY_TERRACOTTA, 3.0F, 6.0F).setRequiresTool().sound(SoundType.METAL).notSolid()));
    public static final Block WAXED_OXIDIZED_COPPER_DOOR = register("waxed_oxidized_copper_door", new DoorBlock(props(Material.ROCK, MaterialColor.WARPED_NYLIUM, 3.0F, 6.0F).setRequiresTool().sound(SoundType.METAL).notSolid()));
    public static final Block WAXED_WEATHERED_COPPER_DOOR = register("waxed_weathered_copper_door", new DoorBlock(props(Material.ROCK, MaterialColor.WARPED_STEM, 3.0F, 6.0F).setRequiresTool().sound(SoundType.METAL).notSolid()));
    public static final Block COPPER_TRAPDOOR = register("copper_trapdoor", new TrapDoorBlock(props(Material.ROCK, MaterialColor.ADOBE, 3.0F, 6.0F).setRequiresTool().sound(SoundType.METAL).notSolid().setAllowsSpawn((s, r, p2, t) -> false)));
    public static final Block EXPOSED_COPPER_TRAPDOOR = register("exposed_copper_trapdoor", new TrapDoorBlock(props(Material.ROCK, MaterialColor.LIGHT_GRAY_TERRACOTTA, 3.0F, 6.0F).setRequiresTool().sound(SoundType.METAL).notSolid().setAllowsSpawn((s, r, p2, t) -> false)));
    public static final Block OXIDIZED_COPPER_TRAPDOOR = register("oxidized_copper_trapdoor", new TrapDoorBlock(props(Material.ROCK, MaterialColor.WARPED_NYLIUM, 3.0F, 6.0F).setRequiresTool().sound(SoundType.METAL).notSolid().setAllowsSpawn((s, r, p2, t) -> false)));
    public static final Block WEATHERED_COPPER_TRAPDOOR = register("weathered_copper_trapdoor", new TrapDoorBlock(props(Material.ROCK, MaterialColor.WARPED_STEM, 3.0F, 6.0F).setRequiresTool().sound(SoundType.METAL).notSolid().setAllowsSpawn((s, r, p2, t) -> false)));
    public static final Block WAXED_COPPER_TRAPDOOR = register("waxed_copper_trapdoor", new TrapDoorBlock(props(Material.ROCK, MaterialColor.ADOBE, 3.0F, 6.0F).setRequiresTool().sound(SoundType.METAL).notSolid().setAllowsSpawn((s, r, p2, t) -> false)));
    public static final Block WAXED_EXPOSED_COPPER_TRAPDOOR = register("waxed_exposed_copper_trapdoor", new TrapDoorBlock(props(Material.ROCK, MaterialColor.LIGHT_GRAY_TERRACOTTA, 3.0F, 6.0F).setRequiresTool().sound(SoundType.METAL).notSolid().setAllowsSpawn((s, r, p2, t) -> false)));
    public static final Block WAXED_OXIDIZED_COPPER_TRAPDOOR = register("waxed_oxidized_copper_trapdoor", new TrapDoorBlock(props(Material.ROCK, MaterialColor.WARPED_NYLIUM, 3.0F, 6.0F).setRequiresTool().sound(SoundType.METAL).notSolid().setAllowsSpawn((s, r, p2, t) -> false)));
    public static final Block WAXED_WEATHERED_COPPER_TRAPDOOR = register("waxed_weathered_copper_trapdoor", new TrapDoorBlock(props(Material.ROCK, MaterialColor.WARPED_STEM, 3.0F, 6.0F).setRequiresTool().sound(SoundType.METAL).notSolid().setAllowsSpawn((s, r, p2, t) -> false)));
    public static final Block COPPER_GRATE = register("copper_grate", new ModernWaterloggedBlock(props(Material.IRON, MaterialColor.ADOBE, 3.0F, 6.0F).setRequiresTool().sound(SoundType.METAL).notSolid()));
    public static final Block EXPOSED_COPPER_GRATE = register("exposed_copper_grate", new ModernWaterloggedBlock(props(Material.IRON, MaterialColor.LIGHT_GRAY_TERRACOTTA, 3.0F, 6.0F).setRequiresTool().sound(SoundType.METAL).notSolid()));
    public static final Block WEATHERED_COPPER_GRATE = register("weathered_copper_grate", new ModernWaterloggedBlock(props(Material.IRON, MaterialColor.WARPED_STEM, 3.0F, 6.0F).setRequiresTool().sound(SoundType.METAL).notSolid()));
    public static final Block OXIDIZED_COPPER_GRATE = register("oxidized_copper_grate", new ModernWaterloggedBlock(props(Material.IRON, MaterialColor.WARPED_NYLIUM, 3.0F, 6.0F).setRequiresTool().sound(SoundType.METAL).notSolid()));
    public static final Block WAXED_COPPER_GRATE = register("waxed_copper_grate", new ModernWaterloggedBlock(props(Material.IRON, MaterialColor.ADOBE, 3.0F, 6.0F).setRequiresTool().sound(SoundType.METAL).notSolid()));
    public static final Block WAXED_EXPOSED_COPPER_GRATE = register("waxed_exposed_copper_grate", new ModernWaterloggedBlock(props(Material.IRON, MaterialColor.LIGHT_GRAY_TERRACOTTA, 3.0F, 6.0F).setRequiresTool().sound(SoundType.METAL).notSolid()));
    public static final Block WAXED_WEATHERED_COPPER_GRATE = register("waxed_weathered_copper_grate", new ModernWaterloggedBlock(props(Material.IRON, MaterialColor.WARPED_STEM, 3.0F, 6.0F).setRequiresTool().sound(SoundType.METAL).notSolid()));
    public static final Block WAXED_OXIDIZED_COPPER_GRATE = register("waxed_oxidized_copper_grate", new ModernWaterloggedBlock(props(Material.IRON, MaterialColor.WARPED_NYLIUM, 3.0F, 6.0F).setRequiresTool().sound(SoundType.METAL).notSolid()));
    public static final Block COPPER_BULB = register("copper_bulb", new ModernCopperBulbBlock(props(Material.IRON, MaterialColor.ADOBE, 3.0F, 6.0F).setRequiresTool().setLightLevel(ModernCopperBulbBlock.lightWhenLit(15)).sound(SoundType.METAL)));
    public static final Block EXPOSED_COPPER_BULB = register("exposed_copper_bulb", new ModernCopperBulbBlock(props(Material.IRON, MaterialColor.LIGHT_GRAY_TERRACOTTA, 3.0F, 6.0F).setRequiresTool().setLightLevel(ModernCopperBulbBlock.lightWhenLit(12)).sound(SoundType.METAL)));
    public static final Block WEATHERED_COPPER_BULB = register("weathered_copper_bulb", new ModernCopperBulbBlock(props(Material.IRON, MaterialColor.WARPED_STEM, 3.0F, 6.0F).setRequiresTool().setLightLevel(ModernCopperBulbBlock.lightWhenLit(8)).sound(SoundType.METAL)));
    public static final Block OXIDIZED_COPPER_BULB = register("oxidized_copper_bulb", new ModernCopperBulbBlock(props(Material.IRON, MaterialColor.WARPED_NYLIUM, 3.0F, 6.0F).setRequiresTool().setLightLevel(ModernCopperBulbBlock.lightWhenLit(4)).sound(SoundType.METAL)));
    public static final Block WAXED_COPPER_BULB = register("waxed_copper_bulb", new ModernCopperBulbBlock(props(Material.IRON, MaterialColor.ADOBE, 3.0F, 6.0F).setRequiresTool().setLightLevel(ModernCopperBulbBlock.lightWhenLit(15)).sound(SoundType.METAL)));
    public static final Block WAXED_EXPOSED_COPPER_BULB = register("waxed_exposed_copper_bulb", new ModernCopperBulbBlock(props(Material.IRON, MaterialColor.LIGHT_GRAY_TERRACOTTA, 3.0F, 6.0F).setRequiresTool().setLightLevel(ModernCopperBulbBlock.lightWhenLit(12)).sound(SoundType.METAL)));
    public static final Block WAXED_WEATHERED_COPPER_BULB = register("waxed_weathered_copper_bulb", new ModernCopperBulbBlock(props(Material.IRON, MaterialColor.WARPED_STEM, 3.0F, 6.0F).setRequiresTool().setLightLevel(ModernCopperBulbBlock.lightWhenLit(8)).sound(SoundType.METAL)));
    public static final Block WAXED_OXIDIZED_COPPER_BULB = register("waxed_oxidized_copper_bulb", new ModernCopperBulbBlock(props(Material.IRON, MaterialColor.WARPED_NYLIUM, 3.0F, 6.0F).setRequiresTool().setLightLevel(ModernCopperBulbBlock.lightWhenLit(4)).sound(SoundType.METAL)));
    public static final Block LIGHTNING_ROD = register("lightning_rod", new ModernLightningRodBlock(props(Material.ROCK, MaterialColor.ADOBE, 3.0F, 6.0F).setRequiresTool().sound(SoundType.METAL)));
    public static final Block EXPOSED_LIGHTNING_ROD = register("exposed_lightning_rod", new ModernLightningRodBlock(props(Material.ROCK, MaterialColor.LIGHT_GRAY_TERRACOTTA, 3.0F, 6.0F).setRequiresTool().sound(SoundType.METAL)));
    public static final Block WEATHERED_LIGHTNING_ROD = register("weathered_lightning_rod", new ModernLightningRodBlock(props(Material.ROCK, MaterialColor.WARPED_STEM, 3.0F, 6.0F).setRequiresTool().sound(SoundType.METAL)));
    public static final Block OXIDIZED_LIGHTNING_ROD = register("oxidized_lightning_rod", new ModernLightningRodBlock(props(Material.ROCK, MaterialColor.WARPED_NYLIUM, 3.0F, 6.0F).setRequiresTool().sound(SoundType.METAL)));
    public static final Block WAXED_LIGHTNING_ROD = register("waxed_lightning_rod", new ModernLightningRodBlock(props(Material.ROCK, MaterialColor.ADOBE, 3.0F, 6.0F).setRequiresTool().sound(SoundType.METAL)));
    public static final Block WAXED_EXPOSED_LIGHTNING_ROD = register("waxed_exposed_lightning_rod", new ModernLightningRodBlock(props(Material.ROCK, MaterialColor.LIGHT_GRAY_TERRACOTTA, 3.0F, 6.0F).setRequiresTool().sound(SoundType.METAL)));
    public static final Block WAXED_WEATHERED_LIGHTNING_ROD = register("waxed_weathered_lightning_rod", new ModernLightningRodBlock(props(Material.ROCK, MaterialColor.WARPED_STEM, 3.0F, 6.0F).setRequiresTool().sound(SoundType.METAL)));
    public static final Block WAXED_OXIDIZED_LIGHTNING_ROD = register("waxed_oxidized_lightning_rod", new ModernLightningRodBlock(props(Material.ROCK, MaterialColor.WARPED_NYLIUM, 3.0F, 6.0F).setRequiresTool().sound(SoundType.METAL)));
    public static final Block POINTED_DRIPSTONE = register("pointed_dripstone", new ModernPointedDripstoneBlock(props(Material.ROCK, MaterialColor.BROWN_TERRACOTTA, 1.5F, 3.0F).setRequiresTool().tickRandomly().sound(SoundType.STONE)));
    public static final Block DRIPSTONE_BLOCK = register("dripstone_block", new Block(props(Material.ROCK, MaterialColor.BROWN_TERRACOTTA, 1.5F, 1.0F).setRequiresTool().sound(SoundType.STONE)));
    public static final Block CAVE_VINES = register("cave_vines", new ModernCaveVinesBlock(props(Material.ROCK, MaterialColor.FOLIAGE, 0.0F, 0.0F).setLightLevel(ModernCaveVines.lightFromBerries()).tickRandomly().doesNotBlockMovement().sound(SoundType.PLANT)));
    public static final Block CAVE_VINES_PLANT = register("cave_vines_plant", new ModernCaveVinesPlantBlock(props(Material.ROCK, MaterialColor.FOLIAGE, 0.0F, 0.0F).setLightLevel(ModernCaveVines.lightFromBerries()).doesNotBlockMovement().sound(SoundType.PLANT)));
    public static final Block SPORE_BLOSSOM = register("spore_blossom", new Block(props(Material.ROCK, MaterialColor.FOLIAGE, 0.0F, 0.0F).doesNotBlockMovement().sound(SoundType.PLANT)));
    public static final Block AZALEA = register("azalea", new BushBlock(props(Material.ROCK, MaterialColor.FOLIAGE, 0.0F, 0.0F).sound(SoundType.PLANT)));
    public static final Block FLOWERING_AZALEA = register("flowering_azalea", new BushBlock(props(Material.ROCK, MaterialColor.FOLIAGE, 0.0F, 0.0F).sound(SoundType.PLANT)));
    public static final Block MOSS_BLOCK = register("moss_block", new Block(props(Material.PLANTS, MaterialColor.GREEN, 0.1F, 0.1F).sound(SoundType.PLANT)));
    public static final Block HANGING_ROOTS = register("hanging_roots", new ModernWaterloggedBlock(props(Material.ROCK, MaterialColor.DIRT, 0.0F, 0.0F).doesNotBlockMovement().sound(SoundType.PLANT).notSolid()));
    public static final Block ROOTED_DIRT = register("rooted_dirt", new Block(props(Material.EARTH, MaterialColor.DIRT, 0.5F, 0.5F).sound(SoundType.GROUND)));
    public static final Block MUD = register("mud", new Block(props(Material.EARTH, MaterialColor.CYAN_TERRACOTTA, 0.5F, 0.5F).sound(SoundType.GROUND)));
    public static final Block DEEPSLATE = register("deepslate", new RotatedPillarBlock(props(Material.ROCK, MaterialColor.DEEPSLATE, 3.0F, 6.0F).setRequiresTool().sound(SoundType.STONE)));
    public static final Block COBBLED_DEEPSLATE = register("cobbled_deepslate", new Block(props(Material.ROCK, MaterialColor.DEEPSLATE, 3.5F, 6.0F).setRequiresTool().sound(SoundType.STONE)));
    public static final Block COBBLED_DEEPSLATE_STAIRS = register("cobbled_deepslate_stairs", new StairsBlock(COBBLED_DEEPSLATE.getDefaultState(), props(Material.ROCK, MaterialColor.DEEPSLATE, 3.5F, 6.0F).setRequiresTool().sound(SoundType.STONE)));
    public static final Block COBBLED_DEEPSLATE_SLAB = register("cobbled_deepslate_slab", new SlabBlock(props(Material.ROCK, MaterialColor.DEEPSLATE, 3.5F, 6.0F).setRequiresTool().sound(SoundType.STONE)));
    public static final Block COBBLED_DEEPSLATE_WALL = register("cobbled_deepslate_wall", new WallBlock(props(Material.ROCK, MaterialColor.DEEPSLATE, 3.5F, 6.0F).setRequiresTool().sound(SoundType.STONE)));
    public static final Block POLISHED_DEEPSLATE = register("polished_deepslate", new Block(props(Material.ROCK, MaterialColor.DEEPSLATE, 3.5F, 6.0F).setRequiresTool().sound(SoundType.STONE)));
    public static final Block POLISHED_DEEPSLATE_STAIRS = register("polished_deepslate_stairs", new StairsBlock(POLISHED_DEEPSLATE.getDefaultState(), props(Material.ROCK, MaterialColor.DEEPSLATE, 3.5F, 6.0F).setRequiresTool().sound(SoundType.STONE)));
    public static final Block POLISHED_DEEPSLATE_SLAB = register("polished_deepslate_slab", new SlabBlock(props(Material.ROCK, MaterialColor.DEEPSLATE, 3.5F, 6.0F).setRequiresTool().sound(SoundType.STONE)));
    public static final Block POLISHED_DEEPSLATE_WALL = register("polished_deepslate_wall", new WallBlock(props(Material.ROCK, MaterialColor.DEEPSLATE, 3.5F, 6.0F).setRequiresTool().sound(SoundType.STONE)));
    public static final Block DEEPSLATE_TILES = register("deepslate_tiles", new Block(props(Material.ROCK, MaterialColor.DEEPSLATE, 3.5F, 6.0F).setRequiresTool().sound(SoundType.STONE)));
    public static final Block DEEPSLATE_TILE_STAIRS = register("deepslate_tile_stairs", new StairsBlock(DEEPSLATE_TILES.getDefaultState(), props(Material.ROCK, MaterialColor.DEEPSLATE, 3.5F, 6.0F).setRequiresTool().sound(SoundType.STONE)));
    public static final Block DEEPSLATE_TILE_SLAB = register("deepslate_tile_slab", new SlabBlock(props(Material.ROCK, MaterialColor.DEEPSLATE, 3.5F, 6.0F).setRequiresTool().sound(SoundType.STONE)));
    public static final Block DEEPSLATE_TILE_WALL = register("deepslate_tile_wall", new WallBlock(props(Material.ROCK, MaterialColor.DEEPSLATE, 3.5F, 6.0F).setRequiresTool().sound(SoundType.STONE)));
    public static final Block DEEPSLATE_BRICKS = register("deepslate_bricks", new Block(props(Material.ROCK, MaterialColor.DEEPSLATE, 3.5F, 6.0F).setRequiresTool().sound(SoundType.STONE)));
    public static final Block DEEPSLATE_BRICK_STAIRS = register("deepslate_brick_stairs", new StairsBlock(DEEPSLATE_BRICKS.getDefaultState(), props(Material.ROCK, MaterialColor.DEEPSLATE, 3.5F, 6.0F).setRequiresTool().sound(SoundType.STONE)));
    public static final Block DEEPSLATE_BRICK_SLAB = register("deepslate_brick_slab", new SlabBlock(props(Material.ROCK, MaterialColor.DEEPSLATE, 3.5F, 6.0F).setRequiresTool().sound(SoundType.STONE)));
    public static final Block DEEPSLATE_BRICK_WALL = register("deepslate_brick_wall", new WallBlock(props(Material.ROCK, MaterialColor.DEEPSLATE, 3.5F, 6.0F).setRequiresTool().sound(SoundType.STONE)));
    public static final Block CHISELED_DEEPSLATE = register("chiseled_deepslate", new Block(props(Material.ROCK, MaterialColor.DEEPSLATE, 3.5F, 6.0F).setRequiresTool().sound(SoundType.STONE)));
    public static final Block CRACKED_DEEPSLATE_BRICKS = register("cracked_deepslate_bricks", new Block(props(Material.ROCK, MaterialColor.DEEPSLATE, 3.5F, 6.0F).setRequiresTool().sound(SoundType.STONE)));
    public static final Block CRACKED_DEEPSLATE_TILES = register("cracked_deepslate_tiles", new Block(props(Material.ROCK, MaterialColor.DEEPSLATE, 3.5F, 6.0F).setRequiresTool().sound(SoundType.STONE)));
    public static final Block SMOOTH_BASALT = register("smooth_basalt", new Block(props(Material.ROCK, MaterialColor.BLACK, 1.25F, 4.2F).setRequiresTool().sound(SoundType.BASALT)));
    public static final Block RAW_IRON_BLOCK = register("raw_iron_block", new Block(props(Material.IRON, MaterialColor.RAW_IRON, 5.0F, 6.0F).setRequiresTool().sound(SoundType.STONE)));
    public static final Block RAW_COPPER_BLOCK = register("raw_copper_block", new Block(props(Material.IRON, MaterialColor.ADOBE, 5.0F, 6.0F).setRequiresTool().sound(SoundType.STONE)));
    public static final Block RAW_GOLD_BLOCK = register("raw_gold_block", new Block(props(Material.IRON, MaterialColor.GOLD, 5.0F, 6.0F).setRequiresTool().sound(SoundType.STONE)));
    public static final Block OCHRE_FROGLIGHT = register("ochre_froglight", new RotatedPillarBlock(props(Material.ROCK, MaterialColor.SAND, 0.3F, 0.3F).setLightLevel(state -> 15).sound(SoundType.PLANT)));
    public static final Block VERDANT_FROGLIGHT = register("verdant_froglight", new RotatedPillarBlock(props(Material.ROCK, MaterialColor.GLOW_LICHEN, 0.3F, 0.3F).setLightLevel(state -> 15).sound(SoundType.PLANT)));
    public static final Block PEARLESCENT_FROGLIGHT = register("pearlescent_froglight", new RotatedPillarBlock(props(Material.ROCK, MaterialColor.PINK, 0.3F, 0.3F).setLightLevel(state -> 15).sound(SoundType.PLANT)));
    public static final Block FROGSPAWN = register("frogspawn", new Block(props(Material.ROCK, MaterialColor.WATER, 0.0F, 0.0F).doesNotBlockMovement().sound(SoundType.PLANT)));
    public static final Block REINFORCED_DEEPSLATE = register("reinforced_deepslate", new Block(props(Material.ROCK, MaterialColor.DEEPSLATE, 55.0F, 1200.0F).sound(SoundType.STONE)));
    public static final Block HEAVY_CORE = register("heavy_core", new ModernWaterloggedBlock(props(Material.ROCK, MaterialColor.IRON, 10.0F, 1200.0F).setRequiresTool().sound(SoundType.STONE).notSolid()));
    public static final Block PALE_MOSS_BLOCK = register("pale_moss_block", new Block(props(Material.PLANTS, MaterialColor.LIGHT_GRAY, 0.1F, 0.1F).sound(SoundType.PLANT)));
    public static final Block OPEN_EYEBLOSSOM = register("open_eyeblossom", new BushBlock(props(Material.ROCK, MaterialColor.ADOBE, 0.0F, 0.0F).tickRandomly().doesNotBlockMovement().sound(SoundType.PLANT)));
    public static final Block CLOSED_EYEBLOSSOM = register("closed_eyeblossom", new BushBlock(props(Material.ROCK, MaterialColor.IRON, 0.0F, 0.0F).tickRandomly().doesNotBlockMovement().sound(SoundType.PLANT)));
    public static final Block FIREFLY_BUSH = register("firefly_bush", new BushBlock(props(Material.ROCK, MaterialColor.FOLIAGE, 0.0F, 0.0F).setLightLevel(state -> 2).doesNotBlockMovement().sound(SoundType.SWEET_BERRY_BUSH)));
    // === 生成字段结束 ======================================================

    private ModernBlocks() {
    }

    /** 生成代码用的属性构造快捷方式，等价于 create(...).hardnessAndResistance(...)。 */
    private static AbstractBlock.Properties props(Material material, MaterialColor color,
            float hardness, float resistance) {
        return AbstractBlock.Properties.create(material, color).hardnessAndResistance(hardness, resistance);
    }

    private static Block register(String key, Block block) {
        Block registered = Registry.register(Registry.BLOCK, key, block);
        REGISTERED.add(registered);
        ModernRegistry.markExtended(registered);
        return registered;
    }

    /**
     * 把本类注册的方块的所有方块状态补进 {@link Block#BLOCK_STATE_IDS}，并触发战利品表
     * 解析，对齐 {@link Blocks} 末尾 static 块的行为。由 {@code Bootstrap} 在原版注册
     * 完成后调用。
     */
    public static void registerBlockStates() {
        int before = Block.BLOCK_STATE_IDS.size();

        for (Block block : REGISTERED) {
            for (BlockState state : block.getStateContainer().getValidStates()) {
                Block.BLOCK_STATE_IDS.add(state);
            }

            block.getLootTable();
        }

        int after = Block.BLOCK_STATE_IDS.size();
        LOGGER.info("[CrossVersion] 已扩展 {} 个方块，方块状态 {} -> {}",
                REGISTERED.size(), before, after);

        // PalettedContainer 用 log2(size) 决定全局调色板位宽。原版 17112 与扩展后同为
        // 15 bit，与 ViaBackwards 重编码出的区块数据一致；一旦越过 32768 就会变成
        // 16 bit 并与服务器数据错位，所以这里主动报警而不是等到区块解析炸掉。
        if (after > 32768) {
            LOGGER.error("[CrossVersion] 方块状态总数 {} 超过 32768，全局调色板位宽将变为 16 位，"
                    + "会与跨版本区块数据错位", after);
        }
    }

    /** 本类注册的方块，按注册顺序。 */
    public static List<Block> registered() {
        return Collections.unmodifiableList(REGISTERED);
    }
}
