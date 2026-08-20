package net.minecraft.tileentity;

import com.google.common.collect.ImmutableSet;
import com.mojang.datafixers.types.Type;
import java.util.Set;
import java.util.function.Supplier;
import javax.annotation.Nullable;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.Util;
import net.minecraft.util.datafix.TypeReferences;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.registry.Registry;
import net.minecraft.world.IBlockReader;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class TileEntityType<T extends TileEntity>
{
    private static final Logger LOGGER = LogManager.getLogger();
    public static final TileEntityType<FurnaceTileEntity> FURNACE = register("furnace", TileEntityType.Builder.create(FurnaceTileEntity::new, Blocks.FURNACE));
    public static final TileEntityType<ChestTileEntity> CHEST = register("chest", TileEntityType.Builder.create(ChestTileEntity::new, Blocks.CHEST));
    public static final TileEntityType<TrappedChestTileEntity> TRAPPED_CHEST = register("trapped_chest", TileEntityType.Builder.create(TrappedChestTileEntity::new, Blocks.TRAPPED_CHEST));
    public static final TileEntityType<EnderChestTileEntity> ENDER_CHEST = register("ender_chest", TileEntityType.Builder.create(EnderChestTileEntity::new, Blocks.ENDER_CHEST));
    public static final TileEntityType<JukeboxTileEntity> JUKEBOX = register("jukebox", TileEntityType.Builder.create(JukeboxTileEntity::new, Blocks.JUKEBOX));
    public static final TileEntityType<DispenserTileEntity> DISPENSER = register("dispenser", TileEntityType.Builder.create(DispenserTileEntity::new, Blocks.DISPENSER));
    public static final TileEntityType<DropperTileEntity> DROPPER = register("dropper", TileEntityType.Builder.create(DropperTileEntity::new, Blocks.DROPPER));
    public static final TileEntityType<SignTileEntity> SIGN = register("sign", TileEntityType.Builder.create(SignTileEntity::new, Blocks.OAK_SIGN, Blocks.SPRUCE_SIGN, Blocks.BIRCH_SIGN, Blocks.ACACIA_SIGN, Blocks.JUNGLE_SIGN, Blocks.DARK_OAK_SIGN, Blocks.OAK_WALL_SIGN, Blocks.SPRUCE_WALL_SIGN, Blocks.BIRCH_WALL_SIGN, Blocks.ACACIA_WALL_SIGN, Blocks.JUNGLE_WALL_SIGN, Blocks.DARK_OAK_WALL_SIGN, Blocks.CRIMSON_SIGN, Blocks.CRIMSON_WALL_SIGN, Blocks.WARPED_SIGN, Blocks.WARPED_WALL_SIGN));
    public static final TileEntityType<MobSpawnerTileEntity> MOB_SPAWNER = register("mob_spawner", TileEntityType.Builder.create(MobSpawnerTileEntity::new, Blocks.SPAWNER));
    public static final TileEntityType<PistonTileEntity> PISTON = register("piston", TileEntityType.Builder.create(PistonTileEntity::new, Blocks.MOVING_PISTON));
    public static final TileEntityType<BrewingStandTileEntity> BREWING_STAND = register("brewing_stand", TileEntityType.Builder.create(BrewingStandTileEntity::new, Blocks.BREWING_STAND));
    public static final TileEntityType<EnchantingTableTileEntity> ENCHANTING_TABLE = register("enchanting_table", TileEntityType.Builder.create(EnchantingTableTileEntity::new, Blocks.ENCHANTING_TABLE));
    public static final TileEntityType<EndPortalTileEntity> END_PORTAL = register("end_portal", TileEntityType.Builder.create(EndPortalTileEntity::new, Blocks.END_PORTAL));
    public static final TileEntityType<BeaconTileEntity> BEACON = register("beacon", TileEntityType.Builder.create(BeaconTileEntity::new, Blocks.BEACON));
    public static final TileEntityType<SkullTileEntity> SKULL = register("skull", TileEntityType.Builder.create(SkullTileEntity::new, Blocks.SKELETON_SKULL, Blocks.SKELETON_WALL_SKULL, Blocks.CREEPER_HEAD, Blocks.CREEPER_WALL_HEAD, Blocks.DRAGON_HEAD, Blocks.DRAGON_WALL_HEAD, Blocks.ZOMBIE_HEAD, Blocks.ZOMBIE_WALL_HEAD, Blocks.WITHER_SKELETON_SKULL, Blocks.WITHER_SKELETON_WALL_SKULL, Blocks.PLAYER_HEAD, Blocks.PLAYER_WALL_HEAD));
    public static final TileEntityType<DaylightDetectorTileEntity> DAYLIGHT_DETECTOR = register("daylight_detector", TileEntityType.Builder.create(DaylightDetectorTileEntity::new, Blocks.DAYLIGHT_DETECTOR));
    public static final TileEntityType<HopperTileEntity> HOPPER = register("hopper", TileEntityType.Builder.create(HopperTileEntity::new, Blocks.HOPPER));
    public static final TileEntityType<ComparatorTileEntity> COMPARATOR = register("comparator", TileEntityType.Builder.create(ComparatorTileEntity::new, Blocks.COMPARATOR));
    public static final TileEntityType<BannerTileEntity> BANNER = register("banner", TileEntityType.Builder.create(BannerTileEntity::new, Blocks.WHITE_BANNER, Blocks.ORANGE_BANNER, Blocks.MAGENTA_BANNER, Blocks.LIGHT_BLUE_BANNER, Blocks.YELLOW_BANNER, Blocks.LIME_BANNER, Blocks.PINK_BANNER, Blocks.GRAY_BANNER, Blocks.LIGHT_GRAY_BANNER, Blocks.CYAN_BANNER, Blocks.PURPLE_BANNER, Blocks.BLUE_BANNER, Blocks.BROWN_BANNER, Blocks.GREEN_BANNER, Blocks.RED_BANNER, Blocks.BLACK_BANNER, Blocks.WHITE_WALL_BANNER, Blocks.ORANGE_WALL_BANNER, Blocks.MAGENTA_WALL_BANNER, Blocks.LIGHT_BLUE_WALL_BANNER, Blocks.YELLOW_WALL_BANNER, Blocks.LIME_WALL_BANNER, Blocks.PINK_WALL_BANNER, Blocks.GRAY_WALL_BANNER, Blocks.LIGHT_GRAY_WALL_BANNER, Blocks.CYAN_WALL_BANNER, Blocks.PURPLE_WALL_BANNER, Blocks.BLUE_WALL_BANNER, Blocks.BROWN_WALL_BANNER, Blocks.GREEN_WALL_BANNER, Blocks.RED_WALL_BANNER, Blocks.BLACK_WALL_BANNER));
    public static final TileEntityType<StructureBlockTileEntity> STRUCTURE_BLOCK = register("structure_block", TileEntityType.Builder.create(StructureBlockTileEntity::new, Blocks.STRUCTURE_BLOCK));
    public static final TileEntityType<EndGatewayTileEntity> END_GATEWAY = register("end_gateway", TileEntityType.Builder.create(EndGatewayTileEntity::new, Blocks.END_GATEWAY));
    public static final TileEntityType<CommandBlockTileEntity> COMMAND_BLOCK = register("command_block", TileEntityType.Builder.create(CommandBlockTileEntity::new, Blocks.COMMAND_BLOCK, Blocks.CHAIN_COMMAND_BLOCK, Blocks.REPEATING_COMMAND_BLOCK));
    public static final TileEntityType<ShulkerBoxTileEntity> SHULKER_BOX = register("shulker_box", TileEntityType.Builder.create(ShulkerBoxTileEntity::new, Blocks.SHULKER_BOX, Blocks.BLACK_SHULKER_BOX, Blocks.BLUE_SHULKER_BOX, Blocks.BROWN_SHULKER_BOX, Blocks.CYAN_SHULKER_BOX, Blocks.GRAY_SHULKER_BOX, Blocks.GREEN_SHULKER_BOX, Blocks.LIGHT_BLUE_SHULKER_BOX, Blocks.LIGHT_GRAY_SHULKER_BOX, Blocks.LIME_SHULKER_BOX, Blocks.MAGENTA_SHULKER_BOX, Blocks.ORANGE_SHULKER_BOX, Blocks.PINK_SHULKER_BOX, Blocks.PURPLE_SHULKER_BOX, Blocks.RED_SHULKER_BOX, Blocks.WHITE_SHULKER_BOX, Blocks.YELLOW_SHULKER_BOX));
    public static final TileEntityType<BedTileEntity> BED = register("bed", TileEntityType.Builder.create(BedTileEntity::new, Blocks.RED_BED, Blocks.BLACK_BED, Blocks.BLUE_BED, Blocks.BROWN_BED, Blocks.CYAN_BED, Blocks.GRAY_BED, Blocks.GREEN_BED, Blocks.LIGHT_BLUE_BED, Blocks.LIGHT_GRAY_BED, Blocks.LIME_BED, Blocks.MAGENTA_BED, Blocks.ORANGE_BED, Blocks.PINK_BED, Blocks.PURPLE_BED, Blocks.WHITE_BED, Blocks.YELLOW_BED));
    public static final TileEntityType<ConduitTileEntity> CONDUIT = register("conduit", TileEntityType.Builder.create(ConduitTileEntity::new, Blocks.CONDUIT));
    public static final TileEntityType<BarrelTileEntity> BARREL = register("barrel", TileEntityType.Builder.create(BarrelTileEntity::new, Blocks.BARREL));
    public static final TileEntityType<SmokerTileEntity> SMOKER = register("smoker", TileEntityType.Builder.create(SmokerTileEntity::new, Blocks.SMOKER));
    public static final TileEntityType<BlastFurnaceTileEntity> BLAST_FURNACE = register("blast_furnace", TileEntityType.Builder.create(BlastFurnaceTileEntity::new, Blocks.BLAST_FURNACE));
    public static final TileEntityType<LecternTileEntity> LECTERN = register("lectern", TileEntityType.Builder.create(LecternTileEntity::new, Blocks.LECTERN));
    public static final TileEntityType<BellTileEntity> BELL = register("bell", TileEntityType.Builder.create(BellTileEntity::new, Blocks.BELL));
    public static final TileEntityType<JigsawTileEntity> JIGSAW = register("jigsaw", TileEntityType.Builder.create(JigsawTileEntity::new, Blocks.JIGSAW));
    public static final TileEntityType<CampfireTileEntity> CAMPFIRE = register("campfire", TileEntityType.Builder.create(CampfireTileEntity::new, Blocks.CAMPFIRE, Blocks.SOUL_CAMPFIRE));
    public static final TileEntityType<BeehiveTileEntity> BEEHIVE = register("beehive", TileEntityType.Builder.create(BeehiveTileEntity::new, Blocks.BEE_NEST, Blocks.BEEHIVE));
    /**
     * 悬挂告示牌方块实体类型（1.19 加入，官方 id {@code minecraft:hanging_sign}）。
     *
     * <p>官方是独立类型而非复用 {@code sign}，跨版本透传时服务器发来的就是
     * {@code minecraft:hanging_sign}，所以必须单独注册。
     *
     * <p><b>合法方块列表暂时为空。</b>24 个悬挂告示牌方块在 {@code ModernBlocks} 里注册，
     * 而 {@code ModernBlocks} 依赖本类完成静态初始化，直接引用会成环。
     * 本项目里 {@link #isValidBlock(Block)} 没有任何调用点（已全量搜索确认），
     * 所以空列表不影响行为，只是无法做校验。若日后要补，用
     * {@link #registerValidBlocks(TileEntityType, Block...)} 在 ModernBlocks 初始化完成后追加。
     */
    public static final TileEntityType<ModernHangingSignTileEntity> HANGING_SIGN = registerWithoutBlocks("hanging_sign", ModernHangingSignTileEntity::new);
    private final Supplier <? extends T > factory;
    /** 非 final：{@link #registerValidBlocks} 需要在注册后追加（见 HANGING_SIGN 的注释）。 */
    private Set<Block> validBlocks;
    private final Type<?> datafixerType;

    @Nullable
    public static ResourceLocation getId(TileEntityType<?> tileEntityTypeIn)
    {
        return Registry.BLOCK_ENTITY_TYPE.getKey(tileEntityTypeIn);
    }

    private static <T extends TileEntity> TileEntityType<T> register(String key, TileEntityType.Builder<T> builder)
    {
        if (builder.blocks.isEmpty())
        {
            LOGGER.warn("Block entity type {} requires at least one valid block to be defined!", (Object)key);
        }

        Type<?> type = Util.attemptDataFix(TypeReferences.BLOCK_ENTITY, key);
        return Registry.register(Registry.BLOCK_ENTITY_TYPE, key, builder.build(type));
    }

    /**
     * 注册一个暂时没有合法方块列表的类型，且不打「requires at least one valid block」警告。
     *
     * <p>仅供承载方块定义在 {@code ModernBlocks} 里的移植类型使用 —— 那些方块的静态初始化
     * 依赖本类，在这里引用它们会成环。
     */
    private static <T extends TileEntity> TileEntityType<T> registerWithoutBlocks(String key, Supplier <? extends T > factory)
    {
        Type<?> type = Util.attemptDataFix(TypeReferences.BLOCK_ENTITY, key);
        return Registry.register(Registry.BLOCK_ENTITY_TYPE, key, new TileEntityType<>(factory, ImmutableSet.of(), type));
    }

    /**
     * 在类型注册之后补充合法方块列表。给 {@link #registerWithoutBlocks} 注册的类型收尾用，
     * 需要在承载方块（如 {@code ModernBlocks}）完成静态初始化之后调用。
     */
    public static void registerValidBlocks(TileEntityType<?> typeIn, Block... blocksIn)
    {
        typeIn.validBlocks = ImmutableSet.<Block>builder().addAll(typeIn.validBlocks).add(blocksIn).build();
    }

    public TileEntityType(Supplier <? extends T > factoryIn, Set<Block> validBlocksIn, Type<?> dataFixerType)
    {
        this.factory = factoryIn;
        this.validBlocks = validBlocksIn;
        this.datafixerType = dataFixerType;
    }

    @Nullable
    public T create()
    {
        return this.factory.get();
    }

    public boolean isValidBlock(Block blockIn)
    {
        return this.validBlocks.contains(blockIn);
    }

    @Nullable
    public T getIfExists(IBlockReader blockReader, BlockPos pos)
    {
        TileEntity tileentity = blockReader.getTileEntity(pos);
        return (T)(tileentity != null && tileentity.getType() == this ? tileentity : null);
    }

    public static final class Builder<T extends TileEntity>
    {
        private final Supplier <? extends T > factory;
        private final Set<Block> blocks;

        private Builder(Supplier <? extends T > factoryIn, Set<Block> validBlocks)
        {
            this.factory = factoryIn;
            this.blocks = validBlocks;
        }

        public static <T extends TileEntity> TileEntityType.Builder<T> create(Supplier <? extends T > factoryIn, Block... validBlocks)
        {
            return new TileEntityType.Builder<>(factoryIn, ImmutableSet.copyOf(validBlocks));
        }

        public TileEntityType<T> build(Type<?> datafixerType)
        {
            return new TileEntityType<>(this.factory, this.blocks, datafixerType);
        }
    }
}
