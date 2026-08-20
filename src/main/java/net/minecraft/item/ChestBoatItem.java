package net.minecraft.item;

import net.minecraft.entity.item.BoatEntity;
import net.minecraft.entity.item.ChestBoatEntity;
import net.minecraft.world.World;

/**
 * Places a ChestBoatEntity instead of a plain BoatEntity.
 *
 * Official 1.21.11 has no separate class for this: world/item/Items.java registers every chest
 * boat as new BoatItem(EntityType.X_CHEST_BOAT, props) and BoatItem spawns whatever EntityType
 * it was given. This project only has EntityType.BOAT / EntityType.CHEST_BOAT plus the
 * BoatEntity.Type enum, so the choice of entity is made by a subclass instead.
 */
public class ChestBoatItem extends BoatItem
{
    public ChestBoatItem(BoatEntity.Type typeIn, Item.Properties properties)
    {
        super(typeIn, properties);
    }

    protected BoatEntity createBoat(World worldIn, double x, double y, double z)
    {
        return new ChestBoatEntity(worldIn, x, y, z);
    }
}
