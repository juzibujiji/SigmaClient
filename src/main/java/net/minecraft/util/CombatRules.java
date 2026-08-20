package net.minecraft.util;

import net.minecraft.enchantment.BreachEnchantment;
import net.minecraft.util.math.MathHelper;

public class CombatRules
{
    public static float getDamageAfterAbsorb(float damage, float totalArmor, float toughnessAttribute)
    {
        return getDamageAfterAbsorb(damage, totalArmor, toughnessAttribute, (DamageSource)null);
    }

    /**
     * 官方 {@code world/damagesource/CombatRules#getDamageAfterAbsorb}（1.21.11，第 16-30 行）：
     * <pre>
     * float f  = 2.0F + toughness / 4.0F;
     * float f1 = Mth.clamp(armor - damage / f, armor * 0.2F, 20.0F);
     * float f2 = f1 / 25.0F;
     * ItemStack itemstack = source.getWeaponItem();
     * float f3 = (itemstack != null &amp;&amp; target.level() instanceof ServerLevel serverlevel)
     *          ? Mth.clamp(EnchantmentHelper.modifyArmorEffectiveness(serverlevel, itemstack, target, source, f2), 0.0F, 1.0F)
     *          : f2;
     * return damage * (1.0F - f3);
     * </pre>
     *
     * <p>1.16.4 原本没有 {@code f2 / f3} 这一层，是直接 {@code damage * (1 - f1 / 25)}；
     * 官方 1.20.5 为了「破甲」（Breach）附魔把中间比例抽出来加了个附魔钩子。这里补上同一个钩子，
     * 没有破甲时 {@code f3 == f2}，与旧行为逐位一致。
     *
     * <p>{@code source} 允许为 null，对应官方 {@code getWeaponItem()} 返回 null 的分支。
     */
    public static float getDamageAfterAbsorb(float damage, float totalArmor, float toughnessAttribute, DamageSource source)
    {
        float f = 2.0F + toughnessAttribute / 4.0F;
        float f1 = MathHelper.clamp(totalArmor - damage / f, totalArmor * 0.2F, 20.0F);
        float f2 = f1 / 25.0F;
        float f3 = BreachEnchantment.modifyArmorEffectiveness(source, f2);
        return damage * (1.0F - f3);
    }

    public static float getDamageAfterMagicAbsorb(float damage, float enchantModifiers)
    {
        float f = MathHelper.clamp(enchantModifiers, 0.0F, 20.0F);
        return damage * (1.0F - f / 25.0F);
    }
}
