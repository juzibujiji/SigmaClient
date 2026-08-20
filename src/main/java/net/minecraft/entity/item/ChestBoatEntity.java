package net.minecraft.entity.item;

import javax.annotation.Nullable;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.monster.piglin.PiglinTasks;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.InventoryHelper;
import net.minecraft.inventory.ItemStackHelper;
import net.minecraft.inventory.container.ChestContainer;
import net.minecraft.inventory.container.Container;
import net.minecraft.inventory.container.INamedContainerProvider;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.util.ActionResultType;
import net.minecraft.util.Hand;
import net.minecraft.util.NonNullList;
import net.minecraft.util.math.vector.Vector3d;
import net.minecraft.world.World;
import net.minecraft.world.server.ServerWorld;

/**
 * Chest boat / chest raft, ported from 1.21.11
 * net/minecraft/world/entity/vehicle/boat/AbstractChestBoat (+ ChestBoat / ChestRaft).
 *
 * Official 1.21 has ten separate EntityTypes (OAK_CHEST_BOAT, CHERRY_CHEST_BOAT, ...). This
 * project keeps the single-entity-plus-Type-enum layout, so there is one EntityType.CHEST_BOAT
 * whose wood comes from the inherited BoatEntity.Type - which is exactly how vanilla itself
 * modelled minecraft:chest_boat from 1.19 up to 1.21.1.
 *
 * The container plumbing follows ContainerMinecartEntity, which is how 1.16.4 implements a
 * vehicle with an inventory. Loot tables are deliberately not supported: official
 * EntityType.Builder for every chest boat calls noLootTable(), and nothing in vanilla
 * generates a pre-filled chest boat.
 */
public class ChestBoatEntity extends BoatEntity implements IInventory, INamedContainerProvider
{
    /** Official AbstractChestBoat.CONTAINER_SIZE = 27. */
    private static final int CONTAINER_SIZE = 27;
    private NonNullList<ItemStack> chestBoatItems = NonNullList.withSize(27, ItemStack.EMPTY);
    private boolean dropContentsWhenDead = true;

    public ChestBoatEntity(EntityType <? extends ChestBoatEntity > type, World world)
    {
        super(type, world);
    }

    public ChestBoatEntity(World worldIn, double x, double y, double z)
    {
        this(EntityType.CHEST_BOAT, worldIn);
        this.setPosition(x, y, z);
        this.setMotion(Vector3d.ZERO);
        this.prevPosX = x;
        this.prevPosY = y;
        this.prevPosZ = z;
    }

    /**
     * Official AbstractChestBoat#getSinglePassengerXOffset returns 0.15F.
     */
    protected float getSinglePassengerXOffset()
    {
        return 0.15F;
    }

    /**
     * Official AbstractChestBoat#getMaxPassengers returns 1.
     */
    protected int getMaxPassengers()
    {
        return 1;
    }

    public Item getItemBoat()
    {
        return this.getBoatType().asChestBoatItem();
    }

    public ActionResultType processInitialInteract(PlayerEntity player, Hand hand)
    {
        // Official AbstractChestBoat#interact: mounting wins unless the player is sneaking,
        // otherwise the container opens.
        if (this.canFitPassenger(player) && !player.isSecondaryUseActive())
        {
            return super.processInitialInteract(player, hand);
        }

        player.openContainer(this);

        if (!player.world.isRemote)
        {
            PiglinTasks.func_234478_a_(player, true);
            return ActionResultType.CONSUME;
        }
        else
        {
            return ActionResultType.SUCCESS;
        }
    }

    /**
     * Queues the entity for removal from the world on the next tick.
     */
    public void remove()
    {
        if (!this.world.isRemote && this.dropContentsWhenDead)
        {
            InventoryHelper.dropInventoryItems(this.world, this, this);
        }

        super.remove();
    }

    @Nullable
    public Entity changeDimension(ServerWorld server)
    {
        this.dropContentsWhenDead = false;
        return super.changeDimension(server);
    }

    protected void writeAdditional(CompoundNBT compound)
    {
        super.writeAdditional(compound);
        ItemStackHelper.saveAllItems(compound, this.chestBoatItems);
    }

    /**
     * (abstract) Protected helper method to read subclass entity data from NBT.
     */
    protected void readAdditional(CompoundNBT compound)
    {
        super.readAdditional(compound);
        this.chestBoatItems = NonNullList.withSize(this.getSizeInventory(), ItemStack.EMPTY);
        ItemStackHelper.loadAllItems(compound, this.chestBoatItems);
    }

    /**
     * Returns the number of slots in the inventory.
     */
    public int getSizeInventory()
    {
        return 27;
    }

    public boolean isEmpty()
    {
        for (ItemStack itemstack : this.chestBoatItems)
        {
            if (!itemstack.isEmpty())
            {
                return false;
            }
        }

        return true;
    }

    /**
     * Returns the stack in the given slot.
     */
    public ItemStack getStackInSlot(int index)
    {
        return this.chestBoatItems.get(index);
    }

    /**
     * Removes up to a specified number of items from an inventory slot and returns them in a new stack.
     */
    public ItemStack decrStackSize(int index, int count)
    {
        return ItemStackHelper.getAndSplit(this.chestBoatItems, index, count);
    }

    /**
     * Removes a stack from the given slot and returns it.
     */
    public ItemStack removeStackFromSlot(int index)
    {
        ItemStack itemstack = this.chestBoatItems.get(index);

        if (itemstack.isEmpty())
        {
            return ItemStack.EMPTY;
        }
        else
        {
            this.chestBoatItems.set(index, ItemStack.EMPTY);
            return itemstack;
        }
    }

    /**
     * Sets the given item stack to the specified slot in the inventory (can be crafting or armor sections).
     */
    public void setInventorySlotContents(int index, ItemStack stack)
    {
        this.chestBoatItems.set(index, stack);

        if (!stack.isEmpty() && stack.getCount() > this.getInventoryStackLimit())
        {
            stack.setCount(this.getInventoryStackLimit());
        }
    }

    public boolean replaceItemInInventory(int inventorySlot, ItemStack itemStackIn)
    {
        if (inventorySlot >= 0 && inventorySlot < this.getSizeInventory())
        {
            this.setInventorySlotContents(inventorySlot, itemStackIn);
            return true;
        }
        else
        {
            return false;
        }
    }

    /**
     * For tile entities, ensures the chunk containing the tile entity is saved to disk later - the game won't think it
     * hasn't changed and skip it.
     */
    public void markDirty()
    {
    }

    /**
     * Don't rename this method to canInteractWith due to conflicts with Container
     */
    public boolean isUsableByPlayer(PlayerEntity player)
    {
        if (this.removed)
        {
            return false;
        }
        else
        {
            return !(player.getDistanceSq(this) > 64.0D);
        }
    }

    public void clear()
    {
        this.chestBoatItems.clear();
    }

    @Nullable
    public Container createMenu(int id, PlayerInventory playerInventory, PlayerEntity player)
    {
        return ChestContainer.createGeneric9X3(id, playerInventory, this);
    }
}
