package net.minecraft.enchantment;

import net.minecraft.block.Block;
import net.minecraft.inventory.EquipmentSlotType;
import net.minecraft.item.ArmorItem;
import net.minecraft.item.BowItem;
import net.minecraft.item.CrossbowItem;
import net.minecraft.item.FishingRodItem;
import net.minecraft.item.Item;
import net.minecraft.item.MaceItem;
import net.minecraft.item.SpearItem;
import net.minecraft.item.SwordItem;
import net.minecraft.item.ToolItem;
import net.minecraft.item.TridentItem;

public enum EnchantmentType
{
    ARMOR {
        public boolean canEnchantItem(Item itemIn)
        {
            return itemIn instanceof ArmorItem;
        }
    },
    ARMOR_FEET {
        public boolean canEnchantItem(Item itemIn)
        {
            return itemIn instanceof ArmorItem && ((ArmorItem)itemIn).getEquipmentSlot() == EquipmentSlotType.FEET;
        }
    },
    ARMOR_LEGS {
        public boolean canEnchantItem(Item itemIn)
        {
            return itemIn instanceof ArmorItem && ((ArmorItem)itemIn).getEquipmentSlot() == EquipmentSlotType.LEGS;
        }
    },
    ARMOR_CHEST {
        public boolean canEnchantItem(Item itemIn)
        {
            return itemIn instanceof ArmorItem && ((ArmorItem)itemIn).getEquipmentSlot() == EquipmentSlotType.CHEST;
        }
    },
    ARMOR_HEAD {
        public boolean canEnchantItem(Item itemIn)
        {
            return itemIn instanceof ArmorItem && ((ArmorItem)itemIn).getEquipmentSlot() == EquipmentSlotType.HEAD;
        }
    },
    WEAPON {
        public boolean canEnchantItem(Item itemIn)
        {
            return itemIn instanceof SwordItem;
        }
    },
    DIGGER {
        public boolean canEnchantItem(Item itemIn)
        {
            return itemIn instanceof ToolItem;
        }
    },
    FISHING_ROD {
        public boolean canEnchantItem(Item itemIn)
        {
            return itemIn instanceof FishingRodItem;
        }
    },
    TRIDENT {
        public boolean canEnchantItem(Item itemIn)
        {
            return itemIn instanceof TridentItem;
        }
    },
    BREAKABLE {
        public boolean canEnchantItem(Item itemIn)
        {
            return itemIn.isDamageable();
        }
    },
    BOW {
        public boolean canEnchantItem(Item itemIn)
        {
            return itemIn instanceof BowItem;
        }
    },
    WEARABLE {
        public boolean canEnchantItem(Item itemIn)
        {
            return itemIn instanceof IArmorVanishable || Block.getBlockFromItem(itemIn) instanceof IArmorVanishable;
        }
    },
    CROSSBOW {
        public boolean canEnchantItem(Item itemIn)
        {
            return itemIn instanceof CrossbowItem;
        }
    },
    VANISHABLE {
        public boolean canEnchantItem(Item itemIn)
        {
            return itemIn instanceof IVanishable || Block.getBlockFromItem(itemIn) instanceof IVanishable || BREAKABLE.canEnchantItem(itemIn);
        }
    },
    /**
     * 官方 {@code ItemTags.MACE_ENCHANTABLE}（{@code data/tags/VanillaItemTagsProvider.java:336}）
     * = {@code {minecraft:mace}}，只有重锤一件。
     *
     * <p>1.16.4 没有物品标签体系，附魔适用范围走 {@code EnchantmentType}，所以新增一个常量。
     * 追加在枚举末尾，不影响已有常量的 ordinal。
     */
    MACE {
        public boolean canEnchantItem(Item itemIn)
        {
            return itemIn instanceof MaceItem;
        }
    },
    /**
     * 官方 {@code ItemTags.LUNGE_ENCHANTABLE}（{@code VanillaItemTagsProvider.java:350}）
     * = {@code ItemTags.SPEARS}，即所有长矛。
     */
    SPEAR {
        public boolean canEnchantItem(Item itemIn)
        {
            return itemIn instanceof SpearItem;
        }
    };

    private EnchantmentType()
    {
    }

    /**
     * Return true if the item passed can be enchanted by a enchantment of this type.
     */
    public abstract boolean canEnchantItem(Item itemIn);
}
