package net.minecraft.item;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.block.ShulkerBoxBlock;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.container.Container;
import net.minecraft.inventory.container.Slot;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.nbt.ListNBT;
import org.apache.commons.lang3.math.Fraction;

/**
 * 1.16.4 backport of 1.21.11 {@code net.minecraft.world.item.component.BundleContents}.
 *
 * <p>1.21.11 stores the contents in the {@code minecraft:bundle_contents} data component. 1.16.4 has no data
 * component system, so this class is a plain value object that is serialized into the {@link ItemStack}'s NBT
 * {@code tag}. The layout mirrors the pre-1.20.5 vanilla bundle NBT layout (and the 1.16.4 shulker-box
 * {@code BlockEntityTag.Items} idiom): a {@code ListNBT} of item compounds under the key {@code "Items"},
 * index 0 being the most recently inserted stack.
 *
 * <p>All weights/capacities below are copied verbatim from the official sources; each is annotated with its
 * origin.
 */
public final class BundleContents
{
    /** NBT key holding the contents list. Matches pre-1.20.5 vanilla bundle NBT. */
    public static final String TAG_ITEMS = "Items";

    /**
     * NBT key holding the currently highlighted item index.
     *
     * <p>Divergence from 1.21.11: official {@code BundleContents.CODEC} does <em>not</em> persist
     * {@code selectedItem} - it lives only in the in-memory data-component instance and is pushed to the server
     * with {@code ServerboundSelectBundleItemPacket}. With NBT-backed storage there is no in-memory component to
     * hold it, so it has to round-trip through NBT. It is omitted when -1 so that an unselected bundle's NBT is
     * byte-identical to a bundle that never had a selection (keeps stacks mergeable).
     */
    public static final String TAG_SELECTED_ITEM = "SelectedItem";

    /** Official {@code BundleContents.EMPTY}. */
    public static final BundleContents EMPTY = new BundleContents(Collections.<ItemStack>emptyList());

    /**
     * Official {@code BundleContents.BUNDLE_IN_BUNDLE_WEIGHT = Fraction.getFraction(1, 16)}
     * (1.21.11 world/item/component/BundleContents.java:29).
     */
    private static final Fraction BUNDLE_IN_BUNDLE_WEIGHT = Fraction.getFraction(1, 16);

    /** Official {@code BundleContents.NO_SELECTED_ITEM_INDEX = -1} (BundleContents.java:31). */
    public static final int NO_SELECTED_ITEM_INDEX = -1;

    final List<ItemStack> items;
    final Fraction weight;
    final int selectedItem;

    BundleContents(List<ItemStack> items, Fraction weight, int selectedItem)
    {
        this.items = items;
        this.weight = weight;
        this.selectedItem = selectedItem;
    }

    public BundleContents(List<ItemStack> items)
    {
        this(items, computeContentWeight(items), NO_SELECTED_ITEM_INDEX);
    }

    /** Official {@code BundleContents.computeContentWeight} (BundleContents.java:55). */
    private static Fraction computeContentWeight(List<ItemStack> items)
    {
        Fraction fraction = Fraction.ZERO;

        for (ItemStack itemstack : items)
        {
            fraction = fraction.add(getWeight(itemstack).multiplyBy(Fraction.getFraction(itemstack.getCount(), 1)));
        }

        return fraction;
    }

    /**
     * Official {@code BundleContents.getWeight} (BundleContents.java:65).
     *
     * <p>A bundle inside a bundle costs {@code 1/16} plus the inner bundle's own weight; a stack carrying bees
     * costs a whole slot; everything else costs {@code 1 / maxStackSize}, i.e. a bundle holds exactly one
     * "stack worth" of items in total.
     */
    static Fraction getWeight(ItemStack stack)
    {
        BundleContents bundlecontents = BundleItem.getContentsOrNull(stack);

        if (bundlecontents != null)
        {
            return BUNDLE_IN_BUNDLE_WEIGHT.add(bundlecontents.weight());
        }
        else
        {
            // 1.21.11 reads the DataComponents.BEES component. The 1.16.4 equivalent is the beehive/bee_nest
            // BlockItem's BlockEntityTag.Bees list.
            return hasBees(stack) ? Fraction.ONE : Fraction.getFraction(1, stack.getMaxStackSize());
        }
    }

    private static boolean hasBees(ItemStack stack)
    {
        CompoundNBT compoundnbt = stack.getChildTag("BlockEntityTag");
        return compoundnbt != null && !compoundnbt.getList("Bees", 10).isEmpty();
    }

    /** Official {@code BundleContents.canItemBeInBundle} (BundleContents.java:75). */
    public static boolean canItemBeInBundle(ItemStack stack)
    {
        return !stack.isEmpty() && canFitInsideContainerItems(stack.getItem());
    }

    /**
     * 1.16.4 stand-in for {@code Item.canFitInsideContainerItems()}. Official default is {@code true}
     * (1.21.11 world/item/Item.java:356) and {@code BlockItem} overrides it to exclude shulker boxes
     * (world/item/BlockItem.java:190). Implemented locally so {@code Item.java} does not have to be touched.
     */
    public static boolean canFitInsideContainerItems(Item item)
    {
        return !(item instanceof BlockItem) || !(((BlockItem)item).getBlock() instanceof ShulkerBoxBlock);
    }

    /** Official {@code BundleContents.getNumberOfItemsToShow} (BundleContents.java:79). */
    public int getNumberOfItemsToShow()
    {
        int i = this.size();
        int j = i > BundleItem.MAX_SHOWN_GRID_ITEMS ? BundleItem.OVERFLOWING_MAX_SHOWN_GRID_ITEMS : BundleItem.MAX_SHOWN_GRID_ITEMS;
        int k = i % BundleItem.MAX_SHOWN_GRID_ITEMS_X;
        int l = k == 0 ? 0 : BundleItem.MAX_SHOWN_GRID_ITEMS_X - k;
        return Math.min(i, j - l);
    }

    public ItemStack getItemUnsafe(int index)
    {
        return this.items.get(index);
    }

    public List<ItemStack> itemCopyList()
    {
        List<ItemStack> list = new ArrayList<ItemStack>(this.items.size());

        for (ItemStack itemstack : this.items)
        {
            list.add(itemstack.copy());
        }

        return list;
    }

    public List<ItemStack> items()
    {
        return Collections.unmodifiableList(this.items);
    }

    public int size()
    {
        return this.items.size();
    }

    public Fraction weight()
    {
        return this.weight;
    }

    public boolean isEmpty()
    {
        return this.items.isEmpty();
    }

    public int getSelectedItem()
    {
        return this.selectedItem;
    }

    public boolean hasSelectedItem()
    {
        return this.selectedItem != NO_SELECTED_ITEM_INDEX;
    }

    /**
     * Reads the contents out of an item stack's NBT. Returns {@link #EMPTY} when the stack carries no bundle NBT
     * at all, which mirrors the official {@code getOrDefault(DataComponents.BUNDLE_CONTENTS, EMPTY)} behaviour.
     */
    public static BundleContents fromTag(@Nullable CompoundNBT tag)
    {
        if (tag == null || !tag.contains(TAG_ITEMS, 9))
        {
            return EMPTY;
        }

        ListNBT listnbt = tag.getList(TAG_ITEMS, 10);
        List<ItemStack> list = new ArrayList<ItemStack>(listnbt.size());

        for (int i = 0; i < listnbt.size(); ++i)
        {
            ItemStack itemstack = ItemStack.read(listnbt.getCompound(i));

            if (!itemstack.isEmpty())
            {
                list.add(itemstack);
            }
        }

        int j = tag.contains(TAG_SELECTED_ITEM, 99) ? tag.getInt(TAG_SELECTED_ITEM) : NO_SELECTED_ITEM_INDEX;

        if (j < 0 || j >= list.size())
        {
            j = NO_SELECTED_ITEM_INDEX;
        }

        return new BundleContents(list, computeContentWeight(list), j);
    }

    /**
     * Writes these contents into the given stack's NBT, clearing the keys again when the bundle ends up empty so
     * that empty bundles stack with freshly crafted ones.
     */
    public void save(ItemStack stack)
    {
        if (this.items.isEmpty())
        {
            if (stack.hasTag())
            {
                // removeChildTag drops the whole tag once it is empty, so a fully emptied bundle stacks with a
                // freshly crafted one again.
                stack.removeChildTag(TAG_ITEMS);
                stack.removeChildTag(TAG_SELECTED_ITEM);
            }

            return;
        }

        ListNBT listnbt = new ListNBT();

        for (ItemStack itemstack : this.items)
        {
            listnbt.add(itemstack.write(new CompoundNBT()));
        }

        CompoundNBT compoundnbt1 = stack.getOrCreateTag();
        compoundnbt1.put(TAG_ITEMS, listnbt);

        if (this.selectedItem == NO_SELECTED_ITEM_INDEX)
        {
            compoundnbt1.remove(TAG_SELECTED_ITEM);
        }
        else
        {
            compoundnbt1.putInt(TAG_SELECTED_ITEM, this.selectedItem);
        }
    }

    public boolean equals(Object other)
    {
        if (this == other)
        {
            return true;
        }
        else if (!(other instanceof BundleContents))
        {
            return false;
        }
        else
        {
            BundleContents bundlecontents = (BundleContents)other;

            if (!this.weight.equals(bundlecontents.weight) || this.items.size() != bundlecontents.items.size())
            {
                return false;
            }

            for (int i = 0; i < this.items.size(); ++i)
            {
                if (!ItemStack.areItemStacksEqual(this.items.get(i), bundlecontents.items.get(i)))
                {
                    return false;
                }
            }

            return true;
        }
    }

    public int hashCode()
    {
        int i = 1;

        for (ItemStack itemstack : this.items)
        {
            i = i * 31 + (itemstack.getItem().hashCode() * 31 + itemstack.getCount());
        }

        return i;
    }

    public String toString()
    {
        return "BundleContents" + this.items;
    }

    /** Official {@code BundleContents.Mutable} (BundleContents.java:144). */
    public static class Mutable
    {
        private final List<ItemStack> items;
        private Fraction weight;
        private int selectedItem;

        public Mutable(BundleContents contents)
        {
            this.items = new ArrayList<ItemStack>(contents.items);
            this.weight = contents.weight;
            this.selectedItem = contents.selectedItem;
        }

        public BundleContents.Mutable clearItems()
        {
            this.items.clear();
            this.weight = Fraction.ZERO;
            this.selectedItem = NO_SELECTED_ITEM_INDEX;
            return this;
        }

        /** Official {@code Mutable.findStackIndex} (BundleContents.java:162). */
        private int findStackIndex(ItemStack stack)
        {
            if (!stack.isStackable())
            {
                return -1;
            }
            else
            {
                for (int i = 0; i < this.items.size(); ++i)
                {
                    // 1.21.11 uses ItemStack.isSameItemSameComponents; the 1.16.4 equivalent is
                    // Container.areItemsAndTagsEqual (same item + equal NBT).
                    if (Container.areItemsAndTagsEqual(this.items.get(i), stack))
                    {
                        return i;
                    }
                }

                return -1;
            }
        }

        /** Official {@code Mutable.getMaxAmountToAdd} (BundleContents.java:176). */
        private int getMaxAmountToAdd(ItemStack stack)
        {
            // Capacity is exactly Fraction.ONE - see official BundleItem.isBarVisible/getBarColor, which compare
            // the content weight against Fraction.ONE (1.21.11 world/item/BundleItem.java:144/156).
            Fraction fraction = Fraction.ONE.subtract(this.weight);
            return Math.max(fraction.divideBy(BundleContents.getWeight(stack)).intValue(), 0);
        }

        /** Official {@code Mutable.tryInsert} (BundleContents.java:181). */
        public int tryInsert(ItemStack stack)
        {
            if (!BundleContents.canItemBeInBundle(stack))
            {
                return 0;
            }
            else
            {
                int i = Math.min(stack.getCount(), this.getMaxAmountToAdd(stack));

                if (i == 0)
                {
                    return 0;
                }
                else
                {
                    this.weight = this.weight.add(BundleContents.getWeight(stack).multiplyBy(Fraction.getFraction(i, 1)));
                    int j = this.findStackIndex(stack);

                    if (j != -1)
                    {
                        ItemStack itemstack = this.items.remove(j);
                        ItemStack itemstack1 = itemstack.copy();
                        itemstack1.setCount(itemstack.getCount() + i);
                        stack.shrink(i);
                        this.items.add(0, itemstack1);
                    }
                    else
                    {
                        this.items.add(0, stack.split(i));
                    }

                    return i;
                }
            }
        }

        /** Official {@code Mutable.tryTransfer} (BundleContents.java:205). */
        public int tryTransfer(Slot slot, PlayerEntity player)
        {
            ItemStack itemstack = slot.getStack();
            int i = this.getMaxAmountToAdd(itemstack);
            return BundleContents.canItemBeInBundle(itemstack)
                   ? this.tryInsert(BundleItem.safeTake(slot, itemstack.getCount(), i, player))
                   : 0;
        }

        /** Official {@code Mutable.toggleSelectedItem} (BundleContents.java:211). */
        public void toggleSelectedItem(int index)
        {
            this.selectedItem = this.selectedItem != index && !this.indexIsOutsideAllowedBounds(index)
                                ? index
                                : NO_SELECTED_ITEM_INDEX;
        }

        private boolean indexIsOutsideAllowedBounds(int index)
        {
            return index < 0 || index >= this.items.size();
        }

        /** Official {@code Mutable.removeOne} (BundleContents.java:219). */
        @Nullable
        public ItemStack removeOne()
        {
            if (this.items.isEmpty())
            {
                return null;
            }
            else
            {
                int i = this.indexIsOutsideAllowedBounds(this.selectedItem) ? 0 : this.selectedItem;
                ItemStack itemstack = this.items.remove(i).copy();
                this.weight = this.weight.subtract(BundleContents.getWeight(itemstack).multiplyBy(Fraction.getFraction(itemstack.getCount(), 1)));
                this.toggleSelectedItem(NO_SELECTED_ITEM_INDEX);
                return itemstack;
            }
        }

        public Fraction weight()
        {
            return this.weight;
        }

        public BundleContents toImmutable()
        {
            return new BundleContents(new ArrayList<ItemStack>(this.items), this.weight, this.selectedItem);
        }
    }
}
