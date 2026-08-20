package net.minecraft.enchantment;

import net.minecraft.inventory.EquipmentSlotType;
import net.minecraft.item.ItemStack;

/**
 * 致密（Density，1.20.5 加入）。
 *
 * <p>官方定义在 {@code world/item/enchantment/Enchantments.java} 第 1121-1137 行：
 * <pre>
 * register(p_343249_, DENSITY,
 *     Enchantment.enchantment(
 *         Enchantment.definition(
 *             holdergetter2.getOrThrow(ItemTags.MACE_ENCHANTABLE),  // = {minecraft:mace}
 *             5,                                                   // weight
 *             5,                                                   // maxLevel
 *             Enchantment.dynamicCost(5, 8),                       // minCost
 *             Enchantment.dynamicCost(25, 8),                      // maxCost
 *             2,                                                   // anvilCost
 *             EquipmentSlotGroup.MAINHAND))
 *     .exclusiveWith(holdergetter1.getOrThrow(EnchantmentTags.DAMAGE_EXCLUSIVE))
 *     .withEffect(EnchantmentEffectComponents.SMASH_DAMAGE_PER_FALLEN_BLOCK,
 *                 new AddValue(LevelBasedValue.perLevel(0.5F)));
 * </pre>
 *
 * <p>换算依据：
 * <ul>
 *   <li>{@code Enchantment.Cost.calculate(level) = base + perLevelAboveFirst * (level - 1)}
 *       （官方 {@code Enchantment.java} 的 {@code record Cost}），所以
 *       {@code dynamicCost(5, 8)} 展开成 {@code 5 + 8 * (level - 1)}；</li>
 *   <li>weight 5 对应 1.16.4 的 {@link Enchantment.Rarity#UNCOMMON}
 *       —— 本项目 {@code Rarity} 的权重是 COMMON=10 / UNCOMMON=5 / RARE=2 / VERY_RARE=1，
 *       按<b>数值</b>对齐而不是按名字（原版 1.16.4 的 SMITE 官方 weight 也是 5，注册成 UNCOMMON）；</li>
 *   <li>官方 anvilCost=2 不需要额外代码：1.16.4 的
 *       {@code RepairContainer}（第 227-243 行）按 Rarity 推铁砧倍率，
 *       UNCOMMON 正好是 2，与官方一致。</li>
 * </ul>
 *
 * <p>{@code LevelBasedValue.perLevel(0.5F)} = {@code Linear(base=0.5, perLevelAboveFirst=0.5)}，
 * 即 {@code 0.5 * level}。取用处是 {@link net.minecraft.item.MaceItem}
 * 的砸落伤害（官方 {@code MaceItem#getAttackDamageBonus} 里的
 * {@code EnchantmentHelper.modifyFallBasedDamage}）。
 */
public class DensityEnchantment extends Enchantment
{
    /** 官方 {@code AddValue(LevelBasedValue.perLevel(0.5F))}：每级每格下落 +0.5 伤害。 */
    public static final float SMASH_DAMAGE_PER_FALLEN_BLOCK_PER_LEVEL = 0.5F;

    /** 官方 {@code Enchantment.dynamicCost(5, 8)} 的 base。 */
    private static final int MIN_COST_BASE = 5;

    /** 官方 {@code Enchantment.dynamicCost(25, 8)} 的 base。 */
    private static final int MAX_COST_BASE = 25;

    /** 官方两个 {@code dynamicCost} 共用的 perLevelAboveFirst。 */
    private static final int COST_PER_LEVEL_ABOVE_FIRST = 8;

    /** 官方 definition 的 maxLevel。 */
    private static final int MAX_LEVEL = 5;

    public DensityEnchantment(Enchantment.Rarity rarityIn, EquipmentSlotType... slots)
    {
        super(rarityIn, EnchantmentType.MACE, slots);
    }

    public int getMinEnchantability(int enchantmentLevel)
    {
        return MIN_COST_BASE + COST_PER_LEVEL_ABOVE_FIRST * (enchantmentLevel - 1);
    }

    public int getMaxEnchantability(int enchantmentLevel)
    {
        return MAX_COST_BASE + COST_PER_LEVEL_ABOVE_FIRST * (enchantmentLevel - 1);
    }

    public int getMaxLevel()
    {
        return MAX_LEVEL;
    }

    /**
     * 官方 {@code .exclusiveWith(EnchantmentTags.DAMAGE_EXCLUSIVE)}。该标签的成员见官方
     * {@code data/tags/VanillaEnchantmentTagsProvider.java} 第 66-69 行：
     * sharpness、smite、bane_of_arthropods、<b>impaling</b>、density、breach。
     *
     * <p>1.16.4 没有附魔标签，互斥靠 {@link Enchantment#isCompatibleWith} 实现，而它是双向的
     * （{@code this.canApplyTogether(o) && o.canApplyTogether(this)}），所以只在这里单边声明即可，
     * 不需要动 {@code DamageEnchantment} / {@code ImpalingEnchantment}（那两个类的 raw ID 与行为
     * 一动不动）。
     */
    protected boolean canApplyTogether(Enchantment ench)
    {
        return !(ench instanceof DamageEnchantment)
                && !(ench instanceof ImpalingEnchantment)
                && !(ench instanceof BreachEnchantment)
                && super.canApplyTogether(ench);
    }

    /**
     * 致密的每格附加伤害，供 {@link net.minecraft.item.MaceItem} 调用。
     *
     * @return {@code 0.5 * 等级}，没附魔时是 0
     */
    public static float getSmashDamagePerFallenBlock(ItemStack stack)
    {
        int level = EnchantmentHelper.getEnchantmentLevel(Enchantments.DENSITY, stack);
        return level <= 0 ? 0.0F : SMASH_DAMAGE_PER_FALLEN_BLOCK_PER_LEVEL * (float)level;
    }
}
