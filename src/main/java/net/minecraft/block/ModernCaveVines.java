package net.minecraft.block;

import java.util.function.ToIntFunction;
import net.minecraft.entity.Entity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.state.BooleanProperty;
import net.minecraft.util.ActionResultType;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.shapes.VoxelShape;
import net.minecraft.util.registry.Registry;
import net.minecraft.world.World;

/**
 * 1.21.11 backport of the {@code net.minecraft.world.level.block.CaveVines} interface (shared glow
 * berry state, shape, harvesting and light emission for {@code cave_vines} / {@code cave_vines_plant}).
 *
 * <p>1.16.4's {@code BlockStateProperties} has no {@code berries} property, so it is created here
 * with the official serialized name {@code "berries"}.
 *
 * <p>Shape: official {@code Block.column(14.0, 0.0, 16.0)} expands (Block.java:184-188) to
 * {@code box(1, 0, 1, 15, 16, 15)}.
 */
public interface ModernCaveVines
{
    VoxelShape SHAPE = Block.makeCuboidShape(1.0D, 0.0D, 1.0D, 15.0D, 16.0D, 15.0D);
    BooleanProperty BERRIES = BooleanProperty.create("berries");

    /**
     * Official {@code CaveVines#use}: drops the {@code minecraft:harvest/cave_vine} loot table
     * (exactly one {@code glow_berries}), plays {@code SoundEvents.CAVE_VINES_PICK_BERRIES} at pitch
     * {@code Mth.randomBetween(random, 0.8F, 1.2F)} and clears the {@code berries} property.
     *
     * <p>1.16.4 has neither that loot table nor that sound event: the single glow berry is dropped
     * directly and {@code item.sweet_berries.pick_from_bush} is used as the closest vanilla sound.
     */
    static ActionResultType use(Entity entity, BlockState state, World world, BlockPos pos)
    {
        if (state.get(BERRIES))
        {
            if (!world.isRemote())
            {
                Item item = ModernCaveVines.getGlowBerriesItem();

                if (item != Items.AIR)
                {
                    Block.spawnAsEntity(world, pos, new ItemStack(item, 1));
                }

                float f = MathHelper.nextFloat(world.rand, 0.8F, 1.2F);
                world.playSound((net.minecraft.entity.player.PlayerEntity)null, pos, SoundEvents.ITEM_SWEET_BERRIES_PICK_FROM_BUSH, SoundCategory.BLOCKS, 1.0F, f);
                world.setBlockState(pos, state.with(BERRIES, Boolean.valueOf(false)), 2);
            }

            return ActionResultType.SUCCESS;
        }
        else
        {
            return ActionResultType.PASS;
        }
    }

    static boolean hasGlowBerries(BlockState state)
    {
        return state.hasProperty(BERRIES) && state.get(BERRIES);
    }

    /**
     * Dynamic light provider. Official registration uses {@code lightLevel(CaveVines.emission(14))}
     * for both {@code cave_vines} and {@code cave_vines_plant}, i.e. 14 with berries, 0 without.
     */
    static ToIntFunction<BlockState> emission(final int lightLevel)
    {
        return (state) ->
        {
            return state.get(BERRIES) ? lightLevel : 0;
        };
    }

    /** Convenience alias for the generator's DYNAMIC_LIGHT hook: {@code emission(14)}. */
    static ToIntFunction<BlockState> lightFromBerries()
    {
        return emission(14);
    }

    /**
     * {@code Items.GLOW_BERRIES} does not exist in 1.16.4 and is itself a backported item, so it is
     * resolved from the registry on demand. Returns {@code Items.AIR} when it has not been registered.
     */
    static Item getGlowBerriesItem()
    {
        return Registry.ITEM.getOrDefault(new ResourceLocation("glow_berries"));
    }
}
