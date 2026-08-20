package net.minecraft.item;

import javax.annotation.Nullable;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.stats.Stats;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;

/**
 * Backport of the 1.19 goat horn.
 *
 * Official source: net/minecraft/world/item/InstrumentItem.java (1.21.11 / MCP-Reborn-release).
 *
 * Behaviour and numbers copied verbatim:
 *   use(level, player, hand):
 *       player.startUsingItem(hand);
 *       play(level, player, instrument);
 *       player.getCooldowns().addCooldown(itemstack, Mth.floor(instrument.useDuration() * 20.0F));  // 7.0F * 20 = 140 ticks
 *       player.awardStat(Stats.ITEM_USED.get(this));
 *       return InteractionResult.CONSUME;   // FAIL when the stack carries no instrument
 *   getUseDuration() = Mth.floor(instrument.useDuration() * 20.0F)                                 // 140 ticks
 *   getUseAnimation() = ItemUseAnimation.TOOT_HORN
 *   play(level, player, instrument):
 *       float f = instrument.range() / 16.0F;                                                       // 256 / 16 = 16.0F
 *       level.playSound(player, player, instrument.soundEvent(), SoundSource.RECORDS, f, 1.0F);
 *       level.gameEvent(GameEvent.INSTRUMENT_PLAY, player.position(), Context.of(player));
 *
 * Not ported: GameEvent.INSTRUMENT_PLAY (the 1.17 sculk game-event system does not exist in 1.16.4), so
 * nothing listens for a horn blast. Purely a sensor concern - the sound, range, animation, use duration and
 * cooldown are all official.
 */
public class InstrumentItem extends Item
{
    /** Matches the official DataComponents.INSTRUMENT registry id. */
    public static final String TAG_INSTRUMENT = "instrument";

    public InstrumentItem(Item.Properties builder)
    {
        super(builder);
    }

    /**
     * Official InstrumentItem#create(Item, Holder&lt;Instrument&gt;): builds a stack that carries an instrument.
     */
    public static ItemStack create(Item item, Instrument instrument)
    {
        ItemStack itemstack = new ItemStack(item);
        setInstrument(itemstack, instrument);
        return itemstack;
    }

    public static void setInstrument(ItemStack stack, Instrument instrument)
    {
        stack.getOrCreateTag().putString(TAG_INSTRUMENT, instrument.getId().toString());
    }

    /**
     * Official InstrumentItem#getInstrument reads DataComponents.INSTRUMENT and returns Optional.empty() when
     * the stack has none, which makes use() return FAIL and getUseDuration() return 0.
     */
    @Nullable
    public static Instrument getInstrument(ItemStack stack)
    {
        CompoundNBT compoundnbt = stack.getTag();

        if (compoundnbt == null || !compoundnbt.contains(TAG_INSTRUMENT, 8))
        {
            return null;
        }

        return Instruments.byId(ResourceLocation.tryCreate(compoundnbt.getString(TAG_INSTRUMENT)));
    }

    public ActionResult<ItemStack> onItemRightClick(World worldIn, PlayerEntity playerIn, Hand handIn)
    {
        ItemStack itemstack = playerIn.getHeldItem(handIn);
        Instrument instrument = getInstrument(itemstack);

        if (instrument == null)
        {
            // Official: return InteractionResult.FAIL;
            return ActionResult.resultFail(itemstack);
        }

        playerIn.setActiveHand(handIn);
        play(worldIn, playerIn, instrument);
        // Official: Mth.floor(instrument.useDuration() * 20.0F) -> 140 ticks for every goat horn.
        playerIn.getCooldownTracker().setCooldown(this, MathHelper.floor(instrument.getUseDuration() * 20.0F));
        playerIn.addStat(Stats.ITEM_USED.get(this));
        // Official: return InteractionResult.CONSUME;
        return ActionResult.resultConsume(itemstack);
    }

    /**
     * Official InstrumentItem#getUseDuration: instrument duration in ticks, or 0 with no instrument.
     */
    public int getUseDuration(ItemStack stack)
    {
        Instrument instrument = getInstrument(stack);
        return instrument == null ? 0 : MathHelper.floor(instrument.getUseDuration() * 20.0F);
    }

    /**
     * Official: ItemUseAnimation.TOOT_HORN. {@link UseAction#TOOT_HORN} was added to the enum for this backport.
     */
    public UseAction getUseAction(ItemStack stack)
    {
        return UseAction.TOOT_HORN;
    }

    /**
     * Official InstrumentItem#play. The volume is range/16 (16.0F for a goat horn), which is what makes the
     * horn audible across 256 blocks; SoundCategory.RECORDS matches the official SoundSource.RECORDS.
     *
     * Deviation: official uses level.playSound(player, player, ...), an entity-bound sound that follows the
     * blower. 1.16.4's ClientWorld#playMovingSound builds an EntityTickableSound with the 3-arg constructor
     * and therefore DISCARDS volume and pitch (a genuine 1.16.4 quirk, fixed by Mojang later) - which would
     * silently drop the 16.0F volume and with it the whole 256-block range. The positional playSound below
     * carries the official volume on both sides; the only thing lost is the sound tracking the player for
     * the 7 seconds it lasts.
     */
    private static void play(World worldIn, PlayerEntity playerIn, Instrument instrument)
    {
        float f = instrument.getRange() / 16.0F;
        worldIn.playSound(playerIn, playerIn.getPosX(), playerIn.getPosY(), playerIn.getPosZ(), instrument.getSoundEvent(), SoundCategory.RECORDS, f, 1.0F);
    }
}
