package net.minecraft.item;

import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.potion.EffectInstance;
import net.minecraft.potion.Effects;
import net.minecraft.stats.Stats;
import net.minecraft.util.ActionResult;
import net.minecraft.util.DrinkHelper;
import net.minecraft.util.Hand;
import net.minecraft.util.SoundEvent;
import net.minecraft.util.SoundEvents;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;

/**
 * Backport of the 1.20.5 ominous bottle.
 *
 * By 1.21.11 the standalone OminousBottleItem class no longer exists - the behaviour lives in two data
 * components. Official sources used here:
 *   net/minecraft/world/item/component/OminousBottleAmplifier.java
 *       EFFECT_DURATION = 120000
 *       MIN_AMPLIFIER   = 0
 *       MAX_AMPLIFIER   = 4
 *       onConsume -> addEffect(new MobEffectInstance(MobEffects.BAD_OMEN, 120000, value, false, false, true))
 *                                                             duration ^      ambient ^  visible ^  showIcon ^
 *   net/minecraft/world/item/component/Consumables.java
 *       OMINOUS_BOTTLE = defaultDrink().soundAfterConsume(SoundEvents.OMINOUS_BOTTLE_DISPOSE).build()
 *   net/minecraft/world/item/component/Consumable.java (Builder defaults)
 *       consumeSeconds = 1.6F  -> 32 ticks
 *       animation      = DRINK (defaultDrink)
 *       sound          = GENERIC_DRINK (defaultDrink)
 *
 * The amplifier is stored per-stack. 1.16.4 has no data components, so it is kept in the stack's NBT under
 * the same key the vanilla component uses ("ominous_bottle_amplifier"); absent means 0, matching the
 * component default.
 */
public class OminousBottleItem extends Item
{
    /** Official Consumable.Builder default consumeSeconds 1.6F * 20 ticks. */
    private static final int DRINK_DURATION = 32;
    /** Official OminousBottleAmplifier.EFFECT_DURATION. */
    public static final int EFFECT_DURATION = 120000;
    /** Official OminousBottleAmplifier.MIN_AMPLIFIER. */
    public static final int MIN_AMPLIFIER = 0;
    /** Official OminousBottleAmplifier.MAX_AMPLIFIER. */
    public static final int MAX_AMPLIFIER = 4;
    /** Matches the official DataComponents.OMINOUS_BOTTLE_AMPLIFIER registry id. */
    public static final String TAG_AMPLIFIER = "ominous_bottle_amplifier";

    /**
     * 1.16.4 has no "item.ominous_bottle.dispose" sound (added in 1.20.5). An emptying glass bottle is the
     * closest stock match.
     * Official sound: SoundEvents.OMINOUS_BOTTLE_DISPOSE, played by Consumable#soundAfterConsume.
     */
    private static final SoundEvent OMINOUS_BOTTLE_DISPOSE = SoundEvents.ITEM_BOTTLE_EMPTY;

    public OminousBottleItem(Item.Properties builder)
    {
        super(builder);
    }

    /** Official Consumable.Builder default: 1.6 seconds. */
    public int getUseDuration(ItemStack stack)
    {
        return DRINK_DURATION;
    }

    /** Official Consumables.defaultDrink() uses ItemUseAnimation.DRINK. */
    public UseAction getUseAction(ItemStack stack)
    {
        return UseAction.DRINK;
    }

    /** Official Consumables.defaultDrink() sound is SoundEvents.GENERIC_DRINK. */
    public SoundEvent getDrinkSound()
    {
        return SoundEvents.ENTITY_GENERIC_DRINK;
    }

    public SoundEvent getEatSound()
    {
        return SoundEvents.ENTITY_GENERIC_DRINK;
    }

    public ActionResult<ItemStack> onItemRightClick(World worldIn, PlayerEntity playerIn, Hand handIn)
    {
        // Official 1.20.5 OminousBottleItem used ItemUtils.startUsingInstantly; the 1.21 component pipeline
        // does the same thing for any Consumable. DrinkHelper.startDrinking is 1.16.4's identical helper.
        return DrinkHelper.startDrinking(worldIn, playerIn, handIn);
    }

    public ItemStack onItemUseFinish(ItemStack stack, World worldIn, LivingEntity entityLiving)
    {
        int i = getAmplifier(stack);

        if (entityLiving instanceof ServerPlayerEntity)
        {
            ServerPlayerEntity serverplayerentity = (ServerPlayerEntity)entityLiving;
            CriteriaTriggers.CONSUME_ITEM.trigger(serverplayerentity, stack);
            serverplayerentity.addStat(Stats.ITEM_USED.get(this));
        }

        if (!worldIn.isRemote)
        {
            // Official OminousBottleAmplifier#onConsume - exactly these flags:
            // ambient = false, visible = false, showIcon = true.
            entityLiving.addPotionEffect(new EffectInstance(Effects.BAD_OMEN, EFFECT_DURATION, i, false, false, true));
        }

        // Official Consumable#soundAfterConsume, played once the drink completes.
        entityLiving.playSound(OMINOUS_BOTTLE_DISPOSE, 1.0F, 1.0F);

        if (entityLiving instanceof PlayerEntity && !((PlayerEntity)entityLiving).abilities.isCreativeMode)
        {
            stack.shrink(1);
        }

        return stack;
    }

    /**
     * Reads the per-stack amplifier, clamped to the official [MIN_AMPLIFIER, MAX_AMPLIFIER] range enforced by
     * OminousBottleAmplifier.CODEC (ExtraCodecs.intRange(0, 4)).
     */
    public static int getAmplifier(ItemStack stack)
    {
        CompoundNBT compoundnbt = stack.getTag();

        if (compoundnbt == null || !compoundnbt.contains(TAG_AMPLIFIER, 99))
        {
            return MIN_AMPLIFIER;
        }

        return MathHelper.clamp(compoundnbt.getInt(TAG_AMPLIFIER), MIN_AMPLIFIER, MAX_AMPLIFIER);
    }

    public static void setAmplifier(ItemStack stack, int amplifier)
    {
        stack.getOrCreateTag().putInt(TAG_AMPLIFIER, MathHelper.clamp(amplifier, MIN_AMPLIFIER, MAX_AMPLIFIER));
    }
}
