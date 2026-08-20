package net.minecraft.item;

import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.item.ItemEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.container.Container;
import net.minecraft.inventory.container.Slot;
import net.minecraft.stats.Stats;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.SoundEvent;
import net.minecraft.util.SoundEvents;
import net.minecraft.world.World;
import org.apache.commons.lang3.math.Fraction;

/**
 * 1.16.4 backport of 1.21.11 {@code net.minecraft.world.item.BundleItem}.
 *
 * <p>Contents live in the stack's NBT (see {@link BundleContents}) rather than in a data component.
 *
 * <p>1.16.4's {@code Item} has no {@code overrideStackedOnOther} / {@code overrideOtherStackedOnMe} hooks, so
 * {@link #tryItemClickBehaviourOverride} is called from
 * {@code net.minecraft.inventory.container.Container#func_241440_b_} (the 1.16.4 name of
 * {@code AbstractContainerMenu.doClick}) at the same point where 1.21.11 calls its own
 * {@code tryItemClickBehaviourOverride} (1.21.11 world/inventory/AbstractContainerMenu.java:441).
 */
public class BundleItem extends Item
{
    /** Official {@code BundleItem.MAX_SHOWN_GRID_ITEMS_X = 4} (1.21.11 world/item/BundleItem.java:30). */
    public static final int MAX_SHOWN_GRID_ITEMS_X = 4;

    /** Official {@code BundleItem.MAX_SHOWN_GRID_ITEMS_Y = 3} (BundleItem.java:31). */
    public static final int MAX_SHOWN_GRID_ITEMS_Y = 3;

    /** Official {@code BundleItem.MAX_SHOWN_GRID_ITEMS = 12} (BundleItem.java:32). */
    public static final int MAX_SHOWN_GRID_ITEMS = 12;

    /** Official {@code BundleItem.OVERFLOWING_MAX_SHOWN_GRID_ITEMS = 11} (BundleItem.java:33). */
    public static final int OVERFLOWING_MAX_SHOWN_GRID_ITEMS = 11;

    /**
     * Official {@code BundleItem.FULL_BAR_COLOR = ARGB.colorFromFloat(1.0F, 1.0F, 0.33F, 0.33F)}
     * (BundleItem.java:34). {@code ARGB.as8BitChannel} is {@code floor(f * 255)}, so this is
     * a=255 r=255 g=84 b=84.
     */
    public static final int FULL_BAR_COLOR = 0xFFFF5454;

    /**
     * Official {@code BundleItem.BAR_COLOR = ARGB.colorFromFloat(1.0F, 0.44F, 0.53F, 1.0F)}
     * (BundleItem.java:35) -> a=255 r=112 g=135 b=255.
     */
    public static final int BAR_COLOR = 0xFF7087FF;

    /** Official {@code BundleItem.TICKS_AFTER_FIRST_THROW = 10} (BundleItem.java:36). */
    private static final int TICKS_AFTER_FIRST_THROW = 10;

    /** Official {@code BundleItem.TICKS_BETWEEN_THROWS = 2} (BundleItem.java:37). */
    private static final int TICKS_BETWEEN_THROWS = 2;

    /** Official {@code BundleItem.TICKS_MAX_THROW_DURATION = 200} (BundleItem.java:38). */
    private static final int TICKS_MAX_THROW_DURATION = 200;

    public BundleItem(Item.Properties properties)
    {
        super(properties);
    }

    // ------------------------------------------------------------------------------------------------
    // NBT accessors (stand-ins for DataComponents.BUNDLE_CONTENTS get/set/getOrDefault)
    // ------------------------------------------------------------------------------------------------

    /** Equivalent of {@code stack.get(DataComponents.BUNDLE_CONTENTS)} - null when the stack is not a bundle. */
    @Nullable
    public static BundleContents getContentsOrNull(ItemStack stack)
    {
        return stack.getItem() instanceof BundleItem ? BundleContents.fromTag(stack.getTag()) : null;
    }

    /** Equivalent of {@code stack.getOrDefault(DataComponents.BUNDLE_CONTENTS, BundleContents.EMPTY)}. */
    public static BundleContents getContents(ItemStack stack)
    {
        BundleContents bundlecontents = getContentsOrNull(stack);
        return bundlecontents == null ? BundleContents.EMPTY : bundlecontents;
    }

    /** Equivalent of {@code stack.set(DataComponents.BUNDLE_CONTENTS, contents)}. */
    public static void setContents(ItemStack stack, BundleContents contents)
    {
        contents.save(stack);
    }

    public static boolean isBundle(ItemStack stack)
    {
        return !stack.isEmpty() && stack.getItem() instanceof BundleItem;
    }

    // ------------------------------------------------------------------------------------------------
    // Container click behaviour (official overrideStackedOnOther / overrideOtherStackedOnMe)
    // ------------------------------------------------------------------------------------------------

    /**
     * Official {@code AbstractContainerMenu.tryItemClickBehaviourOverride}
     * (1.21.11 world/inventory/AbstractContainerMenu.java:562).
     *
     * @param primaryClick {@code true} for {@code ClickAction.PRIMARY} (left mouse button, 1.16.4 dragType 0),
     *                     {@code false} for {@code ClickAction.SECONDARY} (right mouse button, dragType 1).
     * @return {@code true} when the bundle consumed the click and the vanilla pickup logic must be skipped.
     */
    public static boolean tryItemClickBehaviourOverride(Container container, PlayerEntity player, boolean primaryClick, Slot slot, ItemStack slotStack, ItemStack carriedStack)
    {
        if (isBundle(carriedStack) && overrideStackedOnOther(carriedStack, slot, primaryClick, player, container))
        {
            return true;
        }

        return isBundle(slotStack) && overrideOtherStackedOnMe(slotStack, carriedStack, slot, primaryClick, player, container);
    }

    /**
     * Official {@code BundleItem.overrideStackedOnOther} (BundleItem.java:50) - the bundle is on the cursor and
     * is being clicked onto {@code slot}.
     */
    public static boolean overrideStackedOnOther(ItemStack bundleStack, Slot slot, boolean primaryClick, PlayerEntity player, @Nullable Container container)
    {
        BundleContents bundlecontents = getContentsOrNull(bundleStack);

        if (bundlecontents == null)
        {
            return false;
        }
        else
        {
            ItemStack itemstack = slot.getStack();
            BundleContents.Mutable mutable = new BundleContents.Mutable(bundlecontents);

            if (primaryClick && !itemstack.isEmpty())
            {
                if (mutable.tryTransfer(slot, player) > 0)
                {
                    playInsertSound(player);
                }
                else
                {
                    playInsertFailSound(player);
                }

                setContents(bundleStack, mutable.toImmutable());
                broadcastChangesOnContainerMenu(player, container);
                return true;
            }
            else if (!primaryClick && itemstack.isEmpty())
            {
                ItemStack itemstack1 = mutable.removeOne();

                if (itemstack1 != null)
                {
                    ItemStack itemstack2 = safeInsert(slot, itemstack1, itemstack1.getCount());

                    if (itemstack2.getCount() > 0)
                    {
                        mutable.tryInsert(itemstack2);
                    }
                    else
                    {
                        playRemoveOneSound(player);
                    }
                }

                setContents(bundleStack, mutable.toImmutable());
                broadcastChangesOnContainerMenu(player, container);
                return true;
            }
            else
            {
                return false;
            }
        }
    }

    /**
     * Official {@code BundleItem.overrideOtherStackedOnMe} (BundleItem.java:88) - the bundle sits in {@code slot}
     * and something (possibly nothing) on the cursor is being clicked onto it.
     */
    public static boolean overrideOtherStackedOnMe(ItemStack bundleStack, ItemStack carriedStack, Slot slot, boolean primaryClick, PlayerEntity player, @Nullable Container container)
    {
        if (primaryClick && carriedStack.isEmpty())
        {
            // Left-clicking a bundle with an empty cursor just picks it up, but clears the highlight first.
            toggleSelectedItem(bundleStack, BundleContents.NO_SELECTED_ITEM_INDEX);
            return false;
        }
        else
        {
            BundleContents bundlecontents = getContentsOrNull(bundleStack);

            if (bundlecontents == null)
            {
                return false;
            }
            else
            {
                BundleContents.Mutable mutable = new BundleContents.Mutable(bundlecontents);

                if (primaryClick && !carriedStack.isEmpty())
                {
                    if (allowModification(slot, player) && mutable.tryInsert(carriedStack) > 0)
                    {
                        playInsertSound(player);
                    }
                    else
                    {
                        playInsertFailSound(player);
                    }

                    setContents(bundleStack, mutable.toImmutable());
                    slot.onSlotChanged();
                    broadcastChangesOnContainerMenu(player, container);
                    return true;
                }
                else if (!primaryClick && carriedStack.isEmpty())
                {
                    if (allowModification(slot, player))
                    {
                        ItemStack itemstack = mutable.removeOne();

                        if (itemstack != null)
                        {
                            playRemoveOneSound(player);
                            player.inventory.setItemStack(itemstack);
                        }
                    }

                    setContents(bundleStack, mutable.toImmutable());
                    slot.onSlotChanged();
                    broadcastChangesOnContainerMenu(player, container);
                    return true;
                }
                else
                {
                    toggleSelectedItem(bundleStack, BundleContents.NO_SELECTED_ITEM_INDEX);
                    return false;
                }
            }
        }
    }

    /** Official {@code BundleItem.broadcastChangesOnContainerMenu} (BundleItem.java:316). */
    private static void broadcastChangesOnContainerMenu(PlayerEntity player, @Nullable Container container)
    {
        Container container1 = container != null ? container : player.openContainer;

        if (container1 != null)
        {
            container1.onCraftMatrixChanged(player.inventory);
        }
    }

    // ------------------------------------------------------------------------------------------------
    // 1.16.4 stand-ins for the 1.17+ Slot helpers used by the official bundle code
    // ------------------------------------------------------------------------------------------------

    /** Official {@code Slot.allowModification} (1.21.11 world/inventory/Slot.java:147). */
    public static boolean allowModification(Slot slot, PlayerEntity player)
    {
        return slot.canTakeStack(player) && slot.isItemValid(slot.getStack());
    }

    /** Official {@code Slot.tryRemove} (Slot.java:97) followed by {@code Slot.onTake} - i.e. {@code Slot.safeTake}. */
    public static ItemStack safeTake(Slot slot, int count, int maxCount, PlayerEntity player)
    {
        if (!slot.canTakeStack(player))
        {
            return ItemStack.EMPTY;
        }
        else if (!allowModification(slot, player) && maxCount < slot.getStack().getCount())
        {
            return ItemStack.EMPTY;
        }
        else
        {
            ItemStack itemstack = slot.decrStackSize(Math.min(count, maxCount));

            if (itemstack.isEmpty())
            {
                return ItemStack.EMPTY;
            }
            else
            {
                if (slot.getStack().isEmpty())
                {
                    slot.putStack(ItemStack.EMPTY);
                }

                slot.onTake(player, itemstack);
                return itemstack;
            }
        }
    }

    /**
     * Official {@code Slot.safeInsert} (Slot.java:126). Returns the remainder of {@code stack} (mutated in place,
     * exactly like the official method).
     */
    public static ItemStack safeInsert(Slot slot, ItemStack stack, int count)
    {
        if (!stack.isEmpty() && slot.isItemValid(stack))
        {
            ItemStack itemstack = slot.getStack();
            int i = Math.min(Math.min(count, stack.getCount()), Math.min(slot.getSlotStackLimit(), stack.getMaxStackSize()) - itemstack.getCount());

            if (i <= 0)
            {
                return stack;
            }
            else
            {
                if (itemstack.isEmpty())
                {
                    slot.putStack(stack.split(i));
                }
                else if (Container.areItemsAndTagsEqual(itemstack, stack))
                {
                    stack.shrink(i);
                    itemstack.grow(i);
                    slot.putStack(itemstack);
                }

                return stack;
            }
        }
        else
        {
            return stack;
        }
    }

    // ------------------------------------------------------------------------------------------------
    // Selection (mouse-wheel highlighting inside the tooltip)
    // ------------------------------------------------------------------------------------------------

    /** Official {@code BundleItem.toggleSelectedItem} (BundleItem.java:159). */
    public static void toggleSelectedItem(ItemStack stack, int index)
    {
        BundleContents bundlecontents = getContentsOrNull(stack);

        if (bundlecontents != null)
        {
            BundleContents.Mutable mutable = new BundleContents.Mutable(bundlecontents);
            mutable.toggleSelectedItem(index);
            setContents(stack, mutable.toImmutable());
        }
    }

    /** Official {@code BundleItem.hasSelectedItem} (BundleItem.java:168). */
    public static boolean hasSelectedItem(ItemStack stack)
    {
        BundleContents bundlecontents = getContentsOrNull(stack);
        return bundlecontents != null && bundlecontents.getSelectedItem() != BundleContents.NO_SELECTED_ITEM_INDEX;
    }

    /** Official {@code BundleItem.getSelectedItem} (BundleItem.java:173). */
    public static int getSelectedItem(ItemStack stack)
    {
        return getContents(stack).getSelectedItem();
    }

    /** Official {@code BundleItem.getSelectedItemStack} (BundleItem.java:178). */
    public static ItemStack getSelectedItemStack(ItemStack stack)
    {
        BundleContents bundlecontents = getContentsOrNull(stack);
        return bundlecontents != null && bundlecontents.getSelectedItem() != BundleContents.NO_SELECTED_ITEM_INDEX
               ? bundlecontents.getItemUnsafe(bundlecontents.getSelectedItem())
               : ItemStack.EMPTY;
    }

    /** Official {@code BundleItem.getNumberOfItemsToShow} (BundleItem.java:183). */
    public static int getNumberOfItemsToShow(ItemStack stack)
    {
        return getContents(stack).getNumberOfItemsToShow();
    }

    // ------------------------------------------------------------------------------------------------
    // Fullness bar (official isBarVisible / getBarWidth / getBarColor)
    // ------------------------------------------------------------------------------------------------

    /** Official {@code BundleItem.getFullnessDisplay} (BundleItem.java:44). */
    public static float getFullnessDisplay(ItemStack stack)
    {
        return getContents(stack).weight().floatValue();
    }

    /** Official {@code BundleItem.isBarVisible} (BundleItem.java:142). */
    public static boolean isBarVisible(ItemStack stack)
    {
        return isBundle(stack) && getContents(stack).weight().compareTo(Fraction.ZERO) > 0;
    }

    /** Official {@code BundleItem.getBarWidth} (BundleItem.java:148). */
    public static int getBarWidth(ItemStack stack)
    {
        return Math.min(1 + mulAndTruncate(getContents(stack).weight(), 12), 13);
    }

    /** Official {@code BundleItem.getBarColor} (BundleItem.java:154). */
    public static int getBarColor(ItemStack stack)
    {
        return getContents(stack).weight().compareTo(Fraction.ONE) >= 0 ? FULL_BAR_COLOR : BAR_COLOR;
    }

    /** Official {@code Mth.mulAndTruncate} (1.21.11 util/Mth.java:758) - absent from 1.16.4's MathHelper. */
    public static int mulAndTruncate(Fraction fraction, int factor)
    {
        return fraction.getNumerator() * factor / fraction.getDenominator();
    }

    // ------------------------------------------------------------------------------------------------
    // Using the item (right click empties the bundle onto the ground)
    // ------------------------------------------------------------------------------------------------

    /**
     * Official {@code BundleItem.use} (BundleItem.java:129) - {@code player.startUsingItem(hand)} and
     * {@code InteractionResult.SUCCESS} (which swings the arm; note this differs from bows/food, which return
     * CONSUME). 1.16.4 name: {@code setActiveHand} / {@code ActionResult.resultSuccess}.
     */
    public ActionResult<ItemStack> onItemRightClick(World worldIn, PlayerEntity playerIn, Hand handIn)
    {
        ItemStack itemstack = playerIn.getHeldItem(handIn);
        playerIn.setActiveHand(handIn);
        return ActionResult.resultSuccess(itemstack);
    }

    /**
     * Official {@code BundleItem.onUseTick} (BundleItem.java:216). 1.16.4 calls this {@code Item.onUse} and passes
     * the remaining use ticks, exactly like {@code onUseTick}'s {@code remainingUseDuration}.
     */
    public void onUse(World worldIn, LivingEntity livingEntityIn, ItemStack stack, int count)
    {
        if (livingEntityIn instanceof PlayerEntity)
        {
            PlayerEntity playerentity = (PlayerEntity)livingEntityIn;
            int i = this.getUseDuration(stack);

            if (count == i || count < i - TICKS_AFTER_FIRST_THROW && count % TICKS_BETWEEN_THROWS == 0)
            {
                this.dropContent(worldIn, playerentity, stack);
            }
        }
    }

    /** Official {@code BundleItem.getUseDuration} (BundleItem.java:227). */
    public int getUseDuration(ItemStack stack)
    {
        return TICKS_MAX_THROW_DURATION;
    }

    /**
     * Official {@code BundleItem.getUseAnimation} returns {@code ItemUseAnimation.BUNDLE} (BundleItem.java:233).
     * 1.16.4's {@code UseAction} has no BUNDLE constant and its first-person renderer has no bundle pose, so the
     * closest available behaviour is NONE (the arm simply stays put while the bundle drains).
     */
    public UseAction getUseAction(ItemStack stack)
    {
        return UseAction.NONE;
    }

    /** Official {@code BundleItem.dropContent(Level, Player, ItemStack)} (BundleItem.java:134). */
    private void dropContent(World world, PlayerEntity player, ItemStack stack)
    {
        if (this.dropContent(stack, player))
        {
            playDropContentsSound(world, player);
            player.addStat(Stats.ITEM_USED.get(this));
        }
    }

    /** Official {@code BundleItem.dropContent(ItemStack, Player)} (BundleItem.java:188). */
    private boolean dropContent(ItemStack stack, PlayerEntity player)
    {
        BundleContents bundlecontents = getContentsOrNull(stack);

        if (bundlecontents != null && !bundlecontents.isEmpty())
        {
            ItemStack itemstack = removeOneItemFromBundle(stack, player, bundlecontents);

            if (itemstack != null)
            {
                player.dropItem(itemstack, true);
                return true;
            }
            else
            {
                return false;
            }
        }
        else
        {
            return false;
        }
    }

    /** Official {@code BundleItem.removeOneItemFromBundle} (BundleItem.java:203). */
    @Nullable
    private static ItemStack removeOneItemFromBundle(ItemStack stack, PlayerEntity player, BundleContents contents)
    {
        BundleContents.Mutable mutable = new BundleContents.Mutable(contents);
        ItemStack itemstack = mutable.removeOne();

        if (itemstack != null)
        {
            playRemoveOneSound(player);
            setContents(stack, mutable.toImmutable());
            return itemstack;
        }
        else
        {
            return null;
        }
    }

    /**
     * Official {@code BundleItem.onDestroyed} (BundleItem.java:245) spills the contents when the item entity is
     * destroyed. 1.16.4 has no {@code Item.onDestroyed} hook, so this is exposed as a static helper; see the
     * report for the (unmodified) call site that would be needed in {@code ItemEntity}.
     */
    public static void onDestroyed(ItemEntity itemEntity)
    {
        ItemStack itemstack = itemEntity.getItem();
        BundleContents bundlecontents = getContentsOrNull(itemstack);

        if (bundlecontents != null && !bundlecontents.isEmpty())
        {
            setContents(itemstack, BundleContents.EMPTY);
            List<ItemStack> list = bundlecontents.itemCopyList();

            for (ItemStack itemstack1 : list)
            {
                itemEntity.world.addEntity(new ItemEntity(itemEntity.world, itemEntity.getPosX(), itemEntity.getPosY(), itemEntity.getPosZ(), itemstack1));
            }
        }
    }

    // ------------------------------------------------------------------------------------------------
    // Sounds
    //
    // The four official sound events are registered (SoundEvents.ITEM_BUNDLE_*) and the real 1.21.11 .ogg files
    // ship in assets/minecraft/sounds/item/bundle/. However 1.16.4 resolves assets/minecraft/sounds.json through
    // VirtualAssetsPack, which consults the launcher's asset index BEFORE the classpath - and the 1.16 asset index
    // does contain minecraft/sounds.json. The bundled sounds.json fragment is therefore shadowed and the new sound
    // names end up with no Sound bound to them (SoundEngine just logs "Unable to play unknown soundEvent").
    //
    // So each sound is resolved once, lazily: the official name is used when it actually resolves (i.e. once the
    // sounds.json merge is solved project-wide), otherwise the closest 1.16.4 vanilla stand-in is played.
    // ------------------------------------------------------------------------------------------------

    /** Official {@code SoundEvents.BUNDLE_INSERT} = "item.bundle.insert" (1.21.11 sounds/SoundEvents.java:252). */
    private static SoundEvent insertSound;

    /** Official {@code SoundEvents.BUNDLE_INSERT_FAIL} = "item.bundle.insert_fail" (SoundEvents.java:253). */
    private static SoundEvent insertFailSound;

    /** Official {@code SoundEvents.BUNDLE_REMOVE_ONE} = "item.bundle.remove_one" (SoundEvents.java:254). */
    private static SoundEvent removeOneSound;

    /** Official {@code SoundEvents.BUNDLE_DROP_CONTENTS} = "item.bundle.drop_contents" (SoundEvents.java:251). */
    private static SoundEvent dropContentsSound;

    private static SoundEvent resolve(SoundEvent official, SoundEvent fallback)
    {
        try
        {
            net.minecraft.client.Minecraft minecraft = net.minecraft.client.Minecraft.getInstance();

            if (minecraft != null && minecraft.getSoundHandler() != null && minecraft.getSoundHandler().getAccessor(official.getName()) != null)
            {
                return official;
            }
        }
        catch (Throwable throwable)
        {
            // Sound handler not ready yet - fall through to the stand-in.
        }

        return fallback;
    }

    private static SoundEvent getInsertSound()
    {
        if (insertSound == null)
        {
            // Stand-in: "entity.item.pickup", vanilla's generic "item goes into storage" blip.
            insertSound = resolve(SoundEvents.ITEM_BUNDLE_INSERT, SoundEvents.ENTITY_ITEM_PICKUP);
        }

        return insertSound;
    }

    private static SoundEvent getInsertFailSound()
    {
        if (insertFailSound == null)
        {
            // Stand-in: "block.dispenser.fail", vanilla's canonical "this action did nothing" sound.
            insertFailSound = resolve(SoundEvents.ITEM_BUNDLE_INSERT_FAIL, SoundEvents.BLOCK_DISPENSER_FAIL);
        }

        return insertFailSound;
    }

    private static SoundEvent getRemoveOneSound()
    {
        if (removeOneSound == null)
        {
            // Stand-in: "entity.item_frame.remove_item", a short pluck very close to the bundle unpack sound.
            removeOneSound = resolve(SoundEvents.ITEM_BUNDLE_REMOVE_ONE, SoundEvents.ENTITY_ITEM_FRAME_REMOVE_ITEM);
        }

        return removeOneSound;
    }

    private static SoundEvent getDropContentsSound()
    {
        if (dropContentsSound == null)
        {
            // Stand-in: "block.beehive.exit", the closest 1.16.4 "something rustles out of a container" sound.
            dropContentsSound = resolve(SoundEvents.ITEM_BUNDLE_DROP_CONTENTS, SoundEvents.BLOCK_BEEHIVE_EXIT);
        }

        return dropContentsSound;
    }

    /** Official {@code BundleItem.playRemoveOneSound} (BundleItem.java:298). */
    public static void playRemoveOneSound(Entity entity)
    {
        entity.playSound(getRemoveOneSound(), 0.8F, 0.8F + entity.world.rand.nextFloat() * 0.4F);
    }

    /** Official {@code BundleItem.playInsertSound} (BundleItem.java:302). */
    public static void playInsertSound(Entity entity)
    {
        entity.playSound(getInsertSound(), 0.8F, 0.8F + entity.world.rand.nextFloat() * 0.4F);
    }

    /** Official {@code BundleItem.playInsertFailSound} (BundleItem.java:306). */
    public static void playInsertFailSound(Entity entity)
    {
        entity.playSound(getInsertFailSound(), 1.0F, 1.0F);
    }

    /** Official {@code BundleItem.playDropContentsSound} (BundleItem.java:310). */
    public static void playDropContentsSound(World world, Entity entity)
    {
        world.playSound((PlayerEntity)null, entity.getPosition(), getDropContentsSound(), SoundCategory.PLAYERS, 0.8F, 0.8F + entity.world.rand.nextFloat() * 0.4F);
    }
}
