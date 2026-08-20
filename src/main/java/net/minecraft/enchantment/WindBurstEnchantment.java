package net.minecraft.enchantment;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.EquipmentSlotType;
import net.minecraft.item.ItemStack;
import net.minecraft.world.WindExplosion;

/**
 * 风爆（Wind Burst，1.20.5 加入）。命中砸落攻击后在自己脚下炸一团气浪，把自己弹起来。
 *
 * <p>官方定义在 {@code world/item/enchantment/Enchantments.java} 第 1155-1194 行：
 * <pre>
 * register(p_343249_, WIND_BURST,
 *     Enchantment.enchantment(
 *         Enchantment.definition(
 *             holdergetter2.getOrThrow(ItemTags.MACE_ENCHANTABLE),  // = {minecraft:mace}
 *             2,                                                   // weight
 *             3,                                                   // maxLevel
 *             Enchantment.dynamicCost(15, 9),                      // minCost
 *             Enchantment.dynamicCost(65, 9),                      // maxCost
 *             4,                                                   // anvilCost
 *             EquipmentSlotGroup.MAINHAND))
 *     .withEffect(
 *         EnchantmentEffectComponents.POST_ATTACK,
 *         EnchantmentTarget.ATTACKER,          // enchanted（谁的附魔）
 *         EnchantmentTarget.ATTACKER,          // affected（作用在谁身上）—— 两个都是攻击者
 *         new ExplodeEffect(
 *             false,                                        // attributeToUser
 *             Optional.empty(),                             // damageType → damagesEntities = false，不掉血
 *             Optional.of(LevelBasedValue.lookup(List.of(1.2F, 1.75F, 2.2F),
 *                                                LevelBasedValue.perLevel(1.5F, 0.35F))),  // ← 注意：这是 radius
 *             BLOCKS_WIND_CHARGE_EXPLOSIONS,                // immuneBlocks
 *             Vec3.ZERO,                                    // offset
 *             LevelBasedValue.constant(3.5F),               // ← 这是 knockbackMultiplier... 见下
 *             false,                                        // createFire
 *             Level.ExplosionInteraction.TRIGGER,
 *             ParticleTypes.GUST_EMITTER_SMALL,
 *             ParticleTypes.GUST_EMITTER_LARGE,
 *             WeightedList.of(),
 *             SoundEvents.WIND_CHARGE_BURST),
 *         LootItemEntityPropertyCondition.hasProperties(
 *             LootContext.EntityTarget.DIRECT_ATTACKER,
 *             EntityPredicate.Builder.entity()
 *                 .flags(EntityFlagsPredicate.Builder.flags().setIsFlying(false))
 *                 .moving(MovementPredicate.fallDistance(MinMaxBounds.Doubles.atLeast(1.5)))));
 * </pre>
 *
 * <p><b>参数对位</b>（照 {@code effects/ExplodeEffect.java} 的 record 顺序读，别按名字猜）：
 * record 是 {@code (attributeToUser, damageType, knockbackMultiplier, immuneBlocks, offset,
 * radius, createFire, blockInteraction, smallParticle, largeParticle, blockParticles, sound)}。
 * 所以第 3 个位置（查表 {@code [1.2, 1.75, 2.2]}）是 <b>knockbackMultiplier</b>，
 * 第 6 个位置（{@code constant(3.5F)}）才是 <b>radius</b>。
 *
 * <p>weight 2 → 1.16.4 的 {@link Enchantment.Rarity#RARE}；官方 anvilCost=4 由
 * {@code RepairContainer} 的 Rarity 表自动得到。
 *
 * <p><b>宝藏附魔</b>：官方 {@code VanillaEnchantmentTagsProvider} 第 72-81 行把 WIND_BURST 放进了
 * {@code EnchantmentTags.TREASURE}，而 {@code IN_ENCHANTING_TABLE}、{@code TRADEABLE}、
 * {@code ON_RANDOM_LOOT} 三个标签都只包含 {@code NON_TREASURE}（第 122-130 行）。
 * 也就是说官方的风爆<b>不会出现在附魔台、村民交易和普通战利品里</b>，只能从不祥之匣拿。
 * 见 {@link #isTreasureEnchantment()} 上的说明。
 */
public class WindBurstEnchantment extends Enchantment
{
    /**
     * 官方 {@code LevelBasedValue.lookup(List.of(1.2F, 1.75F, 2.2F), perLevel(1.5F, 0.35F))}
     * 的查表部分：1/2/3 级的击退倍率。
     */
    private static final float[] KNOCKBACK_MULTIPLIER_BY_LEVEL = new float[] {1.2F, 1.75F, 2.2F};

    /** 查表越界时官方的兜底 {@code perLevel(1.5F, 0.35F)} = {@code 1.5 + 0.35 * (level - 1)}。 */
    private static final float KNOCKBACK_FALLBACK_BASE = 1.5F;
    private static final float KNOCKBACK_FALLBACK_PER_LEVEL_ABOVE_FIRST = 0.35F;

    /** 官方 {@code LevelBasedValue.constant(3.5F)}：爆炸半径，不随等级变。 */
    private static final float RADIUS = 3.5F;

    /**
     * 官方生效条件里的 {@code MovementPredicate.fallDistance(MinMaxBounds.Doubles.atLeast(1.5))}。
     *
     * <p>注意和 {@code MaceItem.SMASH_ATTACK_FALL_THRESHOLD} 的区别：砸落攻击本身要求
     * {@code fallDistance > 1.5}（严格大于），风爆要求 {@code >= 1.5}。
     */
    private static final float MIN_FALL_DISTANCE = 1.5F;

    /** 官方 {@code Enchantment.dynamicCost(15, 9)} 的 base。 */
    private static final int MIN_COST_BASE = 15;

    /** 官方 {@code Enchantment.dynamicCost(65, 9)} 的 base。 */
    private static final int MAX_COST_BASE = 65;

    /** 官方两个 {@code dynamicCost} 共用的 perLevelAboveFirst。 */
    private static final int COST_PER_LEVEL_ABOVE_FIRST = 9;

    /** 官方 definition 的 maxLevel。 */
    private static final int MAX_LEVEL = 3;

    public WindBurstEnchantment(Enchantment.Rarity rarityIn, EquipmentSlotType... slots)
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
     * 【刻意偏离官方】官方 {@code EnchantmentTags.TREASURE} 含 WIND_BURST，所以不进附魔台。
     *
     * <p>1.16.4 的 {@code EnchantmentHelper.getEnchantmentDatas} 第 505 行用
     * {@code !isTreasureEnchantment() || allowTreasure} 过滤，附魔台传的
     * {@code allowTreasure} 是 false —— 照官方返回 {@code true} 的话，
     * 这一条就等价于官方的 {@code IN_ENCHANTING_TABLE = NON_TREASURE}。
     *
     * <p><b>为什么偏离</b>：官方风爆的唯一获取途径是不祥试炼的宝库，而 1.16.4 既没有试炼密室
     * 也没有宝库，照抄「宝藏附魔」等于让它在游戏内<b>永远拿不到</b>，只能靠
     * {@code /enchant} 或创造模式发书 —— 那就不叫实现了。用户明确要求这几个附魔要能用，
     * 所以放开成普通附魔。
     *
     * <p>{@link #canGenerateInLoot()} 必须跟着一起放开：1.16.4 里那两条过滤是并列的，
     * 只改一个改不动。
     */
    public boolean isTreasureEnchantment()
    {
        return false;
    }

    /**
     * 官方 {@code EnchantmentTags.TRADEABLE} = {@code NON_TREASURE} + 四个诅咒/霜行/经验修补，
     * 不含 WIND_BURST，所以村民不卖。这一条<b>照官方保留</b> ——
     * 放开附魔台已经解决了「拿不到」，没必要连村民交易一起改。
     */
    public boolean canVillagerTrade()
    {
        return false;
    }

    /**
     * 官方 {@code EnchantmentTags.ON_RANDOM_LOOT} 不含 WIND_BURST，但见
     * {@link #isTreasureEnchantment()}：1.16.4 两条过滤并列，要进附魔台就得一起放开。
     */
    public boolean canGenerateInLoot()
    {
        return true;
    }

    /**
     * 官方 {@code LevelBasedValue.lookup}：等级在表内取表值，越界用兜底的线性式。
     */
    public static float getKnockbackMultiplier(int level)
    {
        if (level >= 1 && level <= KNOCKBACK_MULTIPLIER_BY_LEVEL.length)
        {
            return KNOCKBACK_MULTIPLIER_BY_LEVEL[level - 1];
        }

        return KNOCKBACK_FALLBACK_BASE + KNOCKBACK_FALLBACK_PER_LEVEL_ABOVE_FIRST * (float)(level - 1);
    }

    /**
     * 官方生效条件，对 {@code LootContext.EntityTarget.DIRECT_ATTACKER}（= 攻击者本人）判：
     * <ul>
     *   <li>{@code setIsFlying(false)} —— 官方 {@code EntityFlagsPredicate#matches} 第 49-54 行把
     *       {@code isFlying} 定义成 {@code livingEntity.isFallFlying() ||
     *       (entity instanceof Player p && p.getAbilities().flying)}，
     *       所以「鞘翅滑翔」和「创造飞行」都算 flying，都不许触发；</li>
     *   <li>{@code fallDistance >= 1.5}。</li>
     * </ul>
     */
    public static boolean canTrigger(LivingEntity attacker)
    {
        if (attacker.isElytraFlying())
        {
            return false;
        }

        if (attacker instanceof PlayerEntity && ((PlayerEntity)attacker).abilities.isFlying)
        {
            return false;
        }

        return attacker.fallDistance >= MIN_FALL_DISTANCE;
    }

    /**
     * 官方 {@code POST_ATTACK} 效果的执行体，由 {@link net.minecraft.item.MaceItem} 在
     * 命中结算后调用。
     *
     * <p>爆心是官方 {@code entityContext} 里的 {@code entity.position()}，即攻击者的<b>脚部</b>坐标
     * （{@code offset} 是 {@code Vec3.ZERO}）。
     *
     * @param attacker 攻击者（官方 affected == enchanted == ATTACKER）
     * @param stack    攻击者主手的重锤
     * @return 是否真的触发了
     */
    public static boolean applyPostAttack(LivingEntity attacker, ItemStack stack)
    {
        int level = EnchantmentHelper.getEnchantmentLevel(Enchantments.WIND_BURST, stack);

        if (level <= 0 || !canTrigger(attacker))
        {
            return false;
        }

        // 爆炸源传 null 而<b>不是</b> attacker —— 这一点决定了风爆到底会不会把人弹起来。
        //
        // 官方 ExplodeEffect 的第一个字段是 attributeToUser，风爆注册时给的是 false
        // （Enchantments.java:1157 起的 new ExplodeEffect(false, ...)），而 ExplodeEffect.apply
        // 里写的是 `this.attributeToUser ? entity : null`，所以官方传给 level.explode 的
        // 源实体是 null —— 谁都不排除，攻击者自己也在受推列表里，这才是「砸完把自己弹起来」。
        //
        // 传 attacker 会被 getEntitiesWithinAABBExcludingEntity 排除掉，
        // 表现就是「风爆装了跟没装一样，不会往上弹」。
        WindExplosion.explode(attacker.world, null, attacker.getPositionVec(), RADIUS, getKnockbackMultiplier(level));
        return true;
    }
}
