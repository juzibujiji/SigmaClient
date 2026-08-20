package net.minecraft.enchantment;

import net.minecraft.inventory.EquipmentSlotType;
import net.minecraft.util.registry.Registry;

public class Enchantments
{
    private static final EquipmentSlotType[] ARMOR_SLOTS = new EquipmentSlotType[] {EquipmentSlotType.HEAD, EquipmentSlotType.CHEST, EquipmentSlotType.LEGS, EquipmentSlotType.FEET};
    public static final Enchantment PROTECTION = register("protection", new ProtectionEnchantment(Enchantment.Rarity.COMMON, ProtectionEnchantment.Type.ALL, ARMOR_SLOTS));
    public static final Enchantment FIRE_PROTECTION = register("fire_protection", new ProtectionEnchantment(Enchantment.Rarity.UNCOMMON, ProtectionEnchantment.Type.FIRE, ARMOR_SLOTS));
    public static final Enchantment FEATHER_FALLING = register("feather_falling", new ProtectionEnchantment(Enchantment.Rarity.UNCOMMON, ProtectionEnchantment.Type.FALL, ARMOR_SLOTS));
    public static final Enchantment BLAST_PROTECTION = register("blast_protection", new ProtectionEnchantment(Enchantment.Rarity.RARE, ProtectionEnchantment.Type.EXPLOSION, ARMOR_SLOTS));
    public static final Enchantment PROJECTILE_PROTECTION = register("projectile_protection", new ProtectionEnchantment(Enchantment.Rarity.UNCOMMON, ProtectionEnchantment.Type.PROJECTILE, ARMOR_SLOTS));
    public static final Enchantment RESPIRATION = register("respiration", new RespirationEnchantment(Enchantment.Rarity.RARE, ARMOR_SLOTS));
    public static final Enchantment AQUA_AFFINITY = register("aqua_affinity", new AquaAffinityEnchantment(Enchantment.Rarity.RARE, ARMOR_SLOTS));
    public static final Enchantment THORNS = register("thorns", new ThornsEnchantment(Enchantment.Rarity.VERY_RARE, ARMOR_SLOTS));
    public static final Enchantment DEPTH_STRIDER = register("depth_strider", new DepthStriderEnchantment(Enchantment.Rarity.RARE, ARMOR_SLOTS));
    public static final Enchantment FROST_WALKER = register("frost_walker", new FrostWalkerEnchantment(Enchantment.Rarity.RARE, EquipmentSlotType.FEET));
    public static final Enchantment BINDING_CURSE = register("binding_curse", new BindingCurseEnchantment(Enchantment.Rarity.VERY_RARE, ARMOR_SLOTS));
    public static final Enchantment SOUL_SPEED = register("soul_speed", new SoulSpeedEnchantment(Enchantment.Rarity.VERY_RARE, EquipmentSlotType.FEET));
    public static final Enchantment SHARPNESS = register("sharpness", new DamageEnchantment(Enchantment.Rarity.COMMON, 0, EquipmentSlotType.MAINHAND));
    public static final Enchantment SMITE = register("smite", new DamageEnchantment(Enchantment.Rarity.UNCOMMON, 1, EquipmentSlotType.MAINHAND));
    public static final Enchantment BANE_OF_ARTHROPODS = register("bane_of_arthropods", new DamageEnchantment(Enchantment.Rarity.UNCOMMON, 2, EquipmentSlotType.MAINHAND));
    public static final Enchantment KNOCKBACK = register("knockback", new KnockbackEnchantment(Enchantment.Rarity.UNCOMMON, EquipmentSlotType.MAINHAND));
    public static final Enchantment FIRE_ASPECT = register("fire_aspect", new FireAspectEnchantment(Enchantment.Rarity.RARE, EquipmentSlotType.MAINHAND));
    public static final Enchantment LOOTING = register("looting", new LootBonusEnchantment(Enchantment.Rarity.RARE, EnchantmentType.WEAPON, EquipmentSlotType.MAINHAND));
    public static final Enchantment SWEEPING = register("sweeping", new SweepingEnchantment(Enchantment.Rarity.RARE, EquipmentSlotType.MAINHAND));
    public static final Enchantment EFFICIENCY = register("efficiency", new EfficiencyEnchantment(Enchantment.Rarity.COMMON, EquipmentSlotType.MAINHAND));
    public static final Enchantment SILK_TOUCH = register("silk_touch", new SilkTouchEnchantment(Enchantment.Rarity.VERY_RARE, EquipmentSlotType.MAINHAND));
    public static final Enchantment UNBREAKING = register("unbreaking", new UnbreakingEnchantment(Enchantment.Rarity.UNCOMMON, EquipmentSlotType.MAINHAND));
    public static final Enchantment FORTUNE = register("fortune", new LootBonusEnchantment(Enchantment.Rarity.RARE, EnchantmentType.DIGGER, EquipmentSlotType.MAINHAND));
    public static final Enchantment POWER = register("power", new PowerEnchantment(Enchantment.Rarity.COMMON, EquipmentSlotType.MAINHAND));
    public static final Enchantment PUNCH = register("punch", new PunchEnchantment(Enchantment.Rarity.RARE, EquipmentSlotType.MAINHAND));
    public static final Enchantment FLAME = register("flame", new FlameEnchantment(Enchantment.Rarity.RARE, EquipmentSlotType.MAINHAND));
    public static final Enchantment INFINITY = register("infinity", new InfinityEnchantment(Enchantment.Rarity.VERY_RARE, EquipmentSlotType.MAINHAND));
    public static final Enchantment LUCK_OF_THE_SEA = register("luck_of_the_sea", new LootBonusEnchantment(Enchantment.Rarity.RARE, EnchantmentType.FISHING_ROD, EquipmentSlotType.MAINHAND));
    public static final Enchantment LURE = register("lure", new LureEnchantment(Enchantment.Rarity.RARE, EnchantmentType.FISHING_ROD, EquipmentSlotType.MAINHAND));
    public static final Enchantment LOYALTY = register("loyalty", new LoyaltyEnchantment(Enchantment.Rarity.UNCOMMON, EquipmentSlotType.MAINHAND));
    public static final Enchantment IMPALING = register("impaling", new ImpalingEnchantment(Enchantment.Rarity.RARE, EquipmentSlotType.MAINHAND));
    public static final Enchantment RIPTIDE = register("riptide", new RiptideEnchantment(Enchantment.Rarity.RARE, EquipmentSlotType.MAINHAND));
    public static final Enchantment CHANNELING = register("channeling", new ChannelingEnchantment(Enchantment.Rarity.VERY_RARE, EquipmentSlotType.MAINHAND));
    public static final Enchantment MULTISHOT = register("multishot", new MultishotEnchantment(Enchantment.Rarity.RARE, EquipmentSlotType.MAINHAND));
    public static final Enchantment QUICK_CHARGE = register("quick_charge", new QuickChargeEnchantment(Enchantment.Rarity.UNCOMMON, EquipmentSlotType.MAINHAND));
    public static final Enchantment PIERCING = register("piercing", new PiercingEnchantment(Enchantment.Rarity.COMMON, EquipmentSlotType.MAINHAND));
    public static final Enchantment MENDING = register("mending", new MendingEnchantment(Enchantment.Rarity.RARE, EquipmentSlotType.values()));
    public static final Enchantment VANISHING_CURSE = register("vanishing_curse", new VanishingCurseEnchantment(Enchantment.Rarity.VERY_RARE, EquipmentSlotType.values()));

    /* ------------------------------------------------------------------
     * 1.20.5 / 1.21 新增附魔。
     *
     * 只能追加在这里，绝对不能插到上面任何一行之前 —— Registry.ENCHANTMENT 的 raw ID 是按注册
     * 顺序发的，而本客户端要连 1.8 / 1.12 / 1.16.4 服务器，原版附魔的 raw ID 必须逐一保持不变。
     *
     * Rarity 按「官方 weight 的数值」对齐，不是按名字：本项目 Enchantment.Rarity 的权重是
     * COMMON=10 / UNCOMMON=5 / RARE=2 / VERY_RARE=1（Enchantment.java:217-235），
     * 所以官方 weight 5 → UNCOMMON，weight 2 → RARE。参照物：原版 SMITE 官方 weight 5，
     * 本项目注册成 UNCOMMON；RIPTIDE 官方 weight 2，本项目注册成 RARE。
     *
     * 官方 definition 的 anvilCost 不需要额外代码：1.16.4 的 RepairContainer（第 227-243 行）
     * 按 Rarity 推铁砧倍率，UNCOMMON=2 / RARE=4，与官方的 2 / 2 / 4 / 4 正好对上。
     * ------------------------------------------------------------------ */

    /** 官方 Enchantments.java:1121-1137，weight 5、maxLevel 5、MAINHAND。 */
    public static final Enchantment DENSITY = register("density", new DensityEnchantment(Enchantment.Rarity.UNCOMMON, EquipmentSlotType.MAINHAND));

    /** 官方 Enchantments.java:1138-1154，weight 2、maxLevel 4、MAINHAND。 */
    public static final Enchantment BREACH = register("breach", new BreachEnchantment(Enchantment.Rarity.RARE, EquipmentSlotType.MAINHAND));

    /** 官方 Enchantments.java:1155-1194，weight 2、maxLevel 3、MAINHAND，宝藏附魔。 */
    public static final Enchantment WIND_BURST = register("wind_burst", new WindBurstEnchantment(Enchantment.Rarity.RARE, EquipmentSlotType.MAINHAND));

    /**
     * 官方 Enchantments.java:977-1019，weight 5、maxLevel 3、<b>EquipmentSlotGroup.HAND</b>。
     * HAND 是主手 + 副手两个槽，所以这里传两个 EquipmentSlotType（对比 density/breach/wind_burst
     * 的 MAINHAND 只有主手）。
     */
    public static final Enchantment LUNGE = register("lunge", new LungeEnchantment(Enchantment.Rarity.UNCOMMON, EquipmentSlotType.MAINHAND, EquipmentSlotType.OFFHAND));

    private static Enchantment register(String key, Enchantment enchantment)
    {
        return Registry.register(Registry.ENCHANTMENT, key, enchantment);
    }
}
