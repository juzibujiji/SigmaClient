package net.minecraft.enchantment;

import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.inventory.EquipmentSlotType;
import net.minecraft.item.ItemStack;
import net.minecraft.util.DamageSource;

/**
 * 破甲（Breach，1.20.5 加入）。
 *
 * <p>官方定义在 {@code world/item/enchantment/Enchantments.java} 第 1138-1154 行：
 * <pre>
 * register(p_343249_, BREACH,
 *     Enchantment.enchantment(
 *         Enchantment.definition(
 *             holdergetter2.getOrThrow(ItemTags.MACE_ENCHANTABLE),  // = {minecraft:mace}
 *             2,                                                   // weight
 *             4,                                                   // maxLevel
 *             Enchantment.dynamicCost(15, 9),                      // minCost
 *             Enchantment.dynamicCost(65, 9),                      // maxCost
 *             4,                                                   // anvilCost
 *             EquipmentSlotGroup.MAINHAND))
 *     .exclusiveWith(holdergetter1.getOrThrow(EnchantmentTags.DAMAGE_EXCLUSIVE))
 *     .withEffect(EnchantmentEffectComponents.ARMOR_EFFECTIVENESS,
 *                 new AddValue(LevelBasedValue.perLevel(-0.15F)));
 * </pre>
 *
 * <p>weight 2 → 1.16.4 的 {@link Enchantment.Rarity#RARE}（权重 2，与原版 IMPALING / RIPTIDE 同级）。
 * 官方 anvilCost=4 由 {@code RepairContainer} 的 Rarity 表自动得到（RARE → 4）。
 *
 * <p><b>效果的接入点</b>：官方 {@code ARMOR_EFFECTIVENESS} 只在一个地方被读，
 * {@code world/damagesource/CombatRules.java} 第 16-30 行：
 * <pre>
 * float f  = 2.0F + toughness / 4.0F;
 * float f1 = Mth.clamp(armor - damage / f, armor * 0.2F, 20.0F);
 * float f2 = f1 / 25.0F;                                     // 护甲减伤比例
 * float f3 = Mth.clamp(EnchantmentHelper.modifyArmorEffectiveness(level, weapon, target, source, f2), 0.0F, 1.0F);
 * return damage * (1.0F - f3);
 * </pre>
 * 也就是说破甲改的是<b>护甲减伤比例</b> {@code f2}（每级 -0.15，最后夹到 [0, 1]），
 * 不是护甲点数。1.16.4 的等价函数是 {@code net.minecraft.util.CombatRules#getDamageAfterAbsorb}。
 */
public class BreachEnchantment extends Enchantment
{
    /** 官方 {@code AddValue(LevelBasedValue.perLevel(-0.15F))}：每级把护甲减伤比例压低 0.15。 */
    public static final float ARMOR_EFFECTIVENESS_PER_LEVEL = -0.15F;

    /** 官方 {@code Enchantment.dynamicCost(15, 9)} 的 base。 */
    private static final int MIN_COST_BASE = 15;

    /** 官方 {@code Enchantment.dynamicCost(65, 9)} 的 base。 */
    private static final int MAX_COST_BASE = 65;

    /** 官方两个 {@code dynamicCost} 共用的 perLevelAboveFirst。 */
    private static final int COST_PER_LEVEL_ABOVE_FIRST = 9;

    /** 官方 definition 的 maxLevel。 */
    private static final int MAX_LEVEL = 4;

    public BreachEnchantment(Enchantment.Rarity rarityIn, EquipmentSlotType... slots)
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
     * 与 {@link DensityEnchantment#canApplyTogether} 同理，对应官方
     * {@code EnchantmentTags.DAMAGE_EXCLUSIVE}。
     */
    protected boolean canApplyTogether(Enchantment ench)
    {
        return !(ench instanceof DamageEnchantment)
                && !(ench instanceof ImpalingEnchantment)
                && !(ench instanceof DensityEnchantment)
                && super.canApplyTogether(ench);
    }

    /**
     * 官方 {@code EnchantmentHelper.modifyArmorEffectiveness} 的等价物：把护甲减伤比例按破甲等级压低。
     *
     * <p>官方在 {@code CombatRules.getDamageAfterAbsorb} 里取的武器是
     * {@code damageSource.getWeaponItem()}，而它的定义是
     * {@code directEntity.getWeaponItem()}（{@code DamageSource.java:65-67}），
     * {@code LivingEntity.getWeaponItem()} 又是 {@code getMainHandItem()}
     * （{@code LivingEntity.java:2107-2109}）。1.16.4 没有 {@code getWeaponItem}，
     * 所以这里用 {@code source.getImmediateSource()}（= 官方 directEntity）的主手物品，语义一致。
     *
     * @param source        本次伤害来源
     * @param armorRatio    官方 {@code f2}，即 {@code clamp(armor - damage/f, armor*0.2, 20) / 25}
     * @return 修正并夹到 [0, 1] 之后的护甲减伤比例
     */
    public static float modifyArmorEffectiveness(DamageSource source, float armorRatio)
    {
        if (source == null)
        {
            return armorRatio;
        }

        Entity direct = source.getImmediateSource();

        if (!(direct instanceof LivingEntity))
        {
            return armorRatio;
        }

        ItemStack weapon = ((LivingEntity)direct).getHeldItemMainhand();
        int level = EnchantmentHelper.getEnchantmentLevel(Enchantments.BREACH, weapon);

        if (level <= 0)
        {
            return armorRatio;
        }

        // 官方 AddValue：f2 + (-0.15 * level)，随后 Mth.clamp(..., 0.0F, 1.0F)。
        return net.minecraft.util.math.MathHelper.clamp(
                armorRatio + ARMOR_EFFECTIVENESS_PER_LEVEL * (float)level, 0.0F, 1.0F);
    }
}
