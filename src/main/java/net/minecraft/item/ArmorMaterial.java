package net.minecraft.item;

import java.util.function.Supplier;
import net.minecraft.inventory.EquipmentSlotType;
import net.minecraft.item.crafting.Ingredient;
import net.minecraft.util.LazyValue;
import net.minecraft.util.SoundEvent;
import net.minecraft.util.SoundEvents;

public enum ArmorMaterial implements IArmorMaterial
{
    LEATHER("leather", 5, new int[]{1, 2, 3, 1}, 15, SoundEvents.ITEM_ARMOR_EQUIP_LEATHER, 0.0F, 0.0F, () -> {
        return Ingredient.fromItems(Items.LEATHER);
    }),
    CHAIN("chainmail", 15, new int[]{1, 4, 5, 2}, 12, SoundEvents.ITEM_ARMOR_EQUIP_CHAIN, 0.0F, 0.0F, () -> {
        return Ingredient.fromItems(Items.IRON_INGOT);
    }),
    IRON("iron", 15, new int[]{2, 5, 6, 2}, 9, SoundEvents.ITEM_ARMOR_EQUIP_IRON, 0.0F, 0.0F, () -> {
        return Ingredient.fromItems(Items.IRON_INGOT);
    }),
    GOLD("gold", 7, new int[]{1, 3, 5, 2}, 25, SoundEvents.ITEM_ARMOR_EQUIP_GOLD, 0.0F, 0.0F, () -> {
        return Ingredient.fromItems(Items.GOLD_INGOT);
    }),
    DIAMOND("diamond", 33, new int[]{3, 6, 8, 3}, 10, SoundEvents.ITEM_ARMOR_EQUIP_DIAMOND, 2.0F, 0.0F, () -> {
        return Ingredient.fromItems(Items.DIAMOND);
    }),
    TURTLE("turtle", 25, new int[]{2, 5, 6, 2}, 9, SoundEvents.ITEM_ARMOR_EQUIP_TURTLE, 0.0F, 0.0F, () -> {
        return Ingredient.fromItems(Items.SCUTE);
    }),
    NETHERITE("netherite", 37, new int[]{3, 6, 8, 3}, 15, SoundEvents.ITEM_ARMOR_EQUIP_NETHERITE, 3.0F, 0.1F, () -> {
        return Ingredient.fromItems(Items.NETHERITE_INGOT);
    }),
    /**
     * 跨版本扩展（1.21.9+ 铜盔甲）。数值照抄官方 {@code ArmorMaterials.COPPER}：
     * {@code new ArmorMaterial(11, makeDefense(1, 3, 4, 2, 4), 8, ARMOR_EQUIP_COPPER, 0.0F, 0.0F, ...)}。
     *
     * <p>官方 {@code makeDefense} 的参数顺序是 (靴子, 护腿, 胸甲, 头盔, 身体)，
     * 1.16.4 的 {@code damageReductionAmountArray} 索引顺序由 {@code EquipmentSlotType.getIndex()}
     * 决定，恰好也是 (靴子, 护腿, 胸甲, 头盔)，所以前四个参数可以直接搬。
     * 第五个「身体」槽 1.16.4 没有，它在官方是马铠的护甲值，已用在
     * {@code ModernItems.COPPER_HORSE_ARMOR} 上。
     *
     * <p>耐久倍率 11 配 {@code MAX_DAMAGE_ARRAY {13,15,16,11}} 得出靴 143 / 腿 165 /
     * 胸 176 / 头 121，与官方 {@code ArmorType.getDurability(11)} 一致。
     *
     * <p>装备音效官方是 {@code ARMOR_EQUIP_COPPER}，1.16.4 没有这个 SoundEvent，
     * 退回同为金属的 {@code ITEM_ARMOR_EQUIP_IRON}。
     */
    COPPER("copper", 11, new int[]{1, 3, 4, 2}, 8, SoundEvents.ITEM_ARMOR_EQUIP_IRON, 0.0F, 0.0F, () -> {
        return Ingredient.fromItems(ModernItems.COPPER_INGOT);
    });

    private static final int[] MAX_DAMAGE_ARRAY = new int[]{13, 15, 16, 11};
    private final String name;
    private final int maxDamageFactor;
    private final int[] damageReductionAmountArray;
    private final int enchantability;
    private final SoundEvent soundEvent;
    private final float toughness;
    private final float knockbackResistance;
    private final LazyValue<Ingredient> repairMaterial;

    private ArmorMaterial(String p_i231593_3_, int p_i231593_4_, int[] p_i231593_5_, int p_i231593_6_, SoundEvent p_i231593_7_, float p_i231593_8_, float p_i231593_9_, Supplier<Ingredient> p_i231593_10_)
    {
        this.name = p_i231593_3_;
        this.maxDamageFactor = p_i231593_4_;
        this.damageReductionAmountArray = p_i231593_5_;
        this.enchantability = p_i231593_6_;
        this.soundEvent = p_i231593_7_;
        this.toughness = p_i231593_8_;
        this.knockbackResistance = p_i231593_9_;
        this.repairMaterial = new LazyValue<>(p_i231593_10_);
    }

    public int getDurability(EquipmentSlotType slotIn)
    {
        return MAX_DAMAGE_ARRAY[slotIn.getIndex()] * this.maxDamageFactor;
    }

    public int getDamageReductionAmount(EquipmentSlotType slotIn)
    {
        return this.damageReductionAmountArray[slotIn.getIndex()];
    }

    public int getEnchantability()
    {
        return this.enchantability;
    }

    public SoundEvent getSoundEvent()
    {
        return this.soundEvent;
    }

    public Ingredient getRepairMaterial()
    {
        return this.repairMaterial.getValue();
    }

    public String getName()
    {
        return this.name;
    }

    public float getToughness()
    {
        return this.toughness;
    }

    /**
     * Gets the percentage of knockback resistance provided by armor of the material.
     */
    public float getKnockbackResistance()
    {
        return this.knockbackResistance;
    }
}
