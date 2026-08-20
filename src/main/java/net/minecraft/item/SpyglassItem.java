package net.minecraft.item;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.stats.Stats;
import net.minecraft.util.ActionResult;
import net.minecraft.util.DrinkHelper;
import net.minecraft.util.Hand;
import net.minecraft.util.SoundEvent;
import net.minecraft.util.SoundEvents;
import net.minecraft.world.World;

/**
 * Backport of the 1.17 spyglass.
 *
 * Official source: net/minecraft/world/item/SpyglassItem.java (1.21.11 / MCP-Reborn-release).
 * All constants below are copied verbatim from that file:
 *   USE_DURATION       = 1200
 *   ZOOM_FOV_MODIFIER  = 0.1F
 *
 * Behaviour chain (official):
 *   use()             -> play SPYGLASS_USE, award stat, ItemUtils.startUsingInstantly
 *   finishUsingItem() -> stopUsing()
 *   releaseUsing()    -> stopUsing()
 *   stopUsing()       -> play SPYGLASS_STOP_USING
 *
 * The actual zoom is applied client-side, see (all changes tagged "spyglass backport"):
 *   - {@link net.minecraft.entity.player.PlayerEntity#isScoping()}
 *   - {@link net.minecraft.client.entity.player.AbstractClientPlayerEntity#getFovModifier()}
 *   - {@link net.minecraft.client.renderer.GameRenderer}      (bypasses the OptiFine dynamic-FOV gate)
 *   - {@link net.minecraft.client.gui.IngameGui}              (scope overlay)
 *   - {@link net.minecraft.client.MouseHelper}                (reduced turn speed while scoping)
 *   - {@link net.minecraft.client.renderer.FirstPersonRenderer} (hand is hidden while scoping)
 */
public class SpyglassItem extends Item
{
    /** Official SpyglassItem.USE_DURATION. */
    public static final int USE_DURATION = 1200;
    /** Official SpyglassItem.ZOOM_FOV_MODIFIER - the FOV multiplier applied while scoping. */
    public static final float ZOOM_FOV_MODIFIER = 0.1F;

    /**
     * 1.16.4 has no "item.spyglass.use" sound event (added in 1.17), and this project ships no extra
     * sound assets, so the closest stock mechanical sound is used instead.
     * Official sound: SoundEvents.SPYGLASS_USE ("item.spyglass.use").
     */
    private static final SoundEvent SPYGLASS_USE = SoundEvents.BLOCK_PISTON_CONTRACT;
    /**
     * Official sound: SoundEvents.SPYGLASS_STOP_USING ("item.spyglass.stop_using").
     * Substituted for the same reason as above.
     */
    private static final SoundEvent SPYGLASS_STOP_USING = SoundEvents.BLOCK_PISTON_EXTEND;

    public SpyglassItem(Item.Properties builder)
    {
        super(builder);
    }

    /**
     * How long it takes to use or consume an item. Official: 1200.
     */
    public int getUseDuration(ItemStack stack)
    {
        return USE_DURATION;
    }

    /**
     * Official: UseAnim.SPYGLASS. {@link UseAction#SPYGLASS} was added to the enum for this backport.
     */
    public UseAction getUseAction(ItemStack stack)
    {
        return UseAction.SPYGLASS;
    }

    public ActionResult<ItemStack> onItemRightClick(World worldIn, PlayerEntity playerIn, Hand handIn)
    {
        // Official: p_151219_.playSound(SoundEvents.SPYGLASS_USE, 1.0F, 1.0F);
        playerIn.playSound(SPYGLASS_USE, 1.0F, 1.0F);
        playerIn.addStat(Stats.ITEM_USED.get(this));
        // Official: ItemUtils.startUsingInstantly(...) == 1.16.4 DrinkHelper.startDrinking(...)
        // (both do startUsingItem(hand) + InteractionResultHolder.consume(heldItem))
        return DrinkHelper.startDrinking(worldIn, playerIn, handIn);
    }

    /**
     * Called when the player finishes using this Item (i.e. the full 1200 ticks elapsed).
     */
    public ItemStack onItemUseFinish(ItemStack stack, World worldIn, LivingEntity entityLiving)
    {
        this.stopUsing(entityLiving);
        return stack;
    }

    /**
     * Called when the player releases the use item button.
     */
    public void onPlayerStoppedUsing(ItemStack stack, World worldIn, LivingEntity entityLiving, int timeLeft)
    {
        this.stopUsing(entityLiving);
    }

    private void stopUsing(LivingEntity entityLiving)
    {
        // Official: p_151207_.playSound(SoundEvents.SPYGLASS_STOP_USING, 1.0F, 1.0F);
        entityLiving.playSound(SPYGLASS_STOP_USING, 1.0F, 1.0F);
    }
}
