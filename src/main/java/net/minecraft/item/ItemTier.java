package net.minecraft.item;

import java.util.function.Supplier;
import net.minecraft.item.crafting.Ingredient;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.LazyValue;

public enum ItemTier implements IItemTier
{
    WOOD(0, 59, 2.0F, 0.0F, 15, () -> {
        return Ingredient.fromTag(ItemTags.PLANKS);
    }),
    STONE(1, 131, 4.0F, 1.0F, 5, () -> {
        return Ingredient.fromTag(ItemTags.STONE_TOOL_MATERIALS);
    }),
    IRON(2, 250, 6.0F, 2.0F, 14, () -> {
        return Ingredient.fromItems(Items.IRON_INGOT);
    }),
    DIAMOND(3, 1561, 8.0F, 3.0F, 10, () -> {
        return Ingredient.fromItems(Items.DIAMOND);
    }),
    GOLD(0, 32, 12.0F, 0.0F, 22, () -> {
        return Ingredient.fromItems(Items.GOLD_INGOT);
    }),
    NETHERITE(4, 2031, 9.0F, 4.0F, 15, () -> {
        return Ingredient.fromItems(Items.NETHERITE_INGOT);
    }),
    /**
     * 跨版本扩展（1.21.9+ 铜工具）。参数取自官方 1.21.11 的 items.json：
     * 耐久 190、挖掘速度 5.0、附魔能力 13，均精确介于石头与铁之间。
     * harvestLevel 为 1（与石头同级）：官方 incorrect_for_copper_tool 与
     * incorrect_for_stone_tool 内容相同，都是 [needs_iron_tool, needs_diamond_tool]，
     * 所以铜的优势在速度与耐久，而不是能挖更硬的方块。
     * attackDamage 1.0 由 copper_pickaxe 官方攻击力 2.0 反推（镐 = 1 + tier）。
     */
    COPPER(1, 190, 5.0F, 1.0F, 13, () -> {
        return Ingredient.fromItems(ModernItems.COPPER_INGOT);
    });

    private final int harvestLevel;
    private final int maxUses;
    private final float efficiency;
    private final float attackDamage;
    private final int enchantability;
    private final LazyValue<Ingredient> repairMaterial;

    private ItemTier(int harvestLevelIn, int maxUsesIn, float efficiencyIn, float attackDamageIn, int enchantabilityIn, Supplier<Ingredient> repairMaterialIn)
    {
        this.harvestLevel = harvestLevelIn;
        this.maxUses = maxUsesIn;
        this.efficiency = efficiencyIn;
        this.attackDamage = attackDamageIn;
        this.enchantability = enchantabilityIn;
        this.repairMaterial = new LazyValue<>(repairMaterialIn);
    }

    public int getMaxUses()
    {
        return this.maxUses;
    }

    public float getEfficiency()
    {
        return this.efficiency;
    }

    public float getAttackDamage()
    {
        return this.attackDamage;
    }

    public int getHarvestLevel()
    {
        return this.harvestLevel;
    }

    public int getEnchantability()
    {
        return this.enchantability;
    }

    public Ingredient getRepairMaterial()
    {
        return this.repairMaterial.getValue();
    }
}
