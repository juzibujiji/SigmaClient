package net.minecraft.item;

import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.ImmutableMultimap.Builder;
import com.google.common.collect.Multimap;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.WeakHashMap;
import java.util.function.Predicate;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.LungeEnchantment;
import net.minecraft.enchantment.IVanishable;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.attributes.Attribute;
import net.minecraft.entity.ai.attributes.AttributeModifier;
import net.minecraft.entity.ai.attributes.Attributes;
import net.minecraft.entity.boss.dragon.EnderDragonPartEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.EquipmentSlotType;
import net.minecraft.util.ActionResult;
import net.minecraft.util.DamageSource;
import net.minecraft.util.Hand;
import net.minecraft.util.SoundEvent;
import net.minecraft.util.SoundEvents;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockRayTraceResult;
import net.minecraft.util.math.EntityRayTraceResult;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RayTraceContext;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.vector.Vector3d;
import net.minecraft.world.World;

/**
 * 1.21.11 的长矛。
 *
 * <p>对照官方 {@code Item.Properties.spear(ToolMaterial, ...)}（MCP-Reborn 的
 * {@code world/item/Item.java} 第 468 行）移植。官方靠数据组件驱动，1.16.4 没有组件系统，
 * 所以这里把组件里的<b>规则</b>直接写成 1.16.4 的代码，数值全部来自官方源码。
 *
 * <p>官方 {@code spear(...)} 的十个参数，本类字段对应关系（顺序与官方签名一致）：
 *
 * <table>
 *   <tr><th>官方参数</th><th>去向</th><th>本类字段</th></tr>
 *   <tr><td>1 {@code p_460317_}</td><td>{@code SwingAnimation(STAB, x*20)} 与攻速 {@code 1/x-4}</td><td>{@link #swingTicks}</td></tr>
 *   <tr><td>2 {@code p_452091_}</td><td>{@code KineticWeapon.damageMultiplier}</td><td>{@link #damageMultiplier}</td></tr>
 *   <tr><td>3 {@code p_453229_}</td><td>{@code KineticWeapon.delayTicks}（x*20）</td><td>{@link #delayTicks}</td></tr>
 *   <tr><td>4/5</td><td>{@code Condition.ofAttackerSpeed} → 掀下坐骑</td><td>{@link #dismountMaxDurationTicks} / {@link #dismountMinSpeed}</td></tr>
 *   <tr><td>6/7</td><td>{@code Condition.ofAttackerSpeed} → 击退</td><td>{@link #knockbackMaxDurationTicks} / {@link #knockbackMinSpeed}</td></tr>
 *   <tr><td>8/9</td><td>{@code Condition.ofRelativeSpeed} → 造成伤害</td><td>{@link #damageMaxDurationTicks} / {@link #damageMinRelativeSpeed}</td></tr>
 * </table>
 *
 * <p><b>已实现</b>：突刺（右键蓄势 + 达到速度阈值时结算）、穿透（左键一次打穿一条线上的所有实体）、
 * 加长且有下限的攻击距离、{@code STAB} 挥击时长、第一/第三人称突刺动画、近似音效。
 *
 * <p><b>没能实现</b>（1.16.4 缺基础设施，硬凑只会做出假货）：
 * <ul>
 *   <li>{@code DamageTypes.SPEAR}（{@code new DamageType("spear", 0.1F)}）—— 1.16.4 的
 *       {@code DamageSource} 是硬编码常量，没有数据驱动的伤害类型注册表，这里沿用
 *       {@code DamageSource.causePlayerDamage}/{@code causeMobDamage}。</li>
 *   <li>命中反馈的镜头/模型回弹：官方靠 {@code broadcastEntityEvent(attacker, (byte)2)} →
 *       {@code LivingEntity.onKineticHit()}。1.16.4 的实体状态 2 还是「受伤动画」，
 *       不能复用；改为直接播一次世界音效，回弹动画未实现。</li>
 *   <li>{@code SpearMobsTrigger} 成就触发器、{@code SpearApproach}/{@code SpearAttack}/
 *       {@code SpearRetreat}/{@code SpearUseGoal} 生物 AI —— 与「玩家手感」无关，未移植。</li>
 *   <li>官方长矛的专属音效（{@code item.spear.*}）1.16.4 没有对应 {@code SoundEvent}，
 *       用最接近的原版音效替代，见 {@link #getUseSound()} 等。</li>
 * </ul>
 *
 * <p>官方没有给长矛 {@code Tool} 组件，所以它挖任何方块都是空手速度，
 * 也不会因为挖方块掉耐久 —— 这里同样不覆盖 {@code getDestroySpeed} 和
 * {@code onBlockDestroyed}。
 */
public class SpearItem extends TieredItem implements IVanishable
{
    /* ------------------------------------------------------------------
     * AttackRange —— 官方 Item.java:507
     *   new AttackRange(2.0F, 4.5F, 2.0F, 6.5F, 0.125F, 0.5F)
     * 注意有<b>下限</b>：贴脸（<2.0 格）打不到。
     * ------------------------------------------------------------------ */
    /** {@code AttackRange.minRange}：生存模式最近可命中距离。 */
    public static final float MIN_RANGE = 2.0F;
    /** {@code AttackRange.maxRange}：生存模式最远可命中距离。 */
    public static final float MAX_RANGE = 4.5F;
    /** {@code AttackRange.minCreativeRange}。 */
    public static final float MIN_CREATIVE_RANGE = 2.0F;
    /** {@code AttackRange.maxCreativeRange}。 */
    public static final float MAX_CREATIVE_RANGE = 6.5F;
    /** {@code AttackRange.hitboxMargin}：碰撞箱外扩容差。 */
    public static final float HITBOX_MARGIN = 0.125F;
    /** {@code AttackRange.mobFactor}：非玩家生物的距离系数。 */
    public static final float MOB_FACTOR = 0.5F;

    /* ------------------------------------------------------------------
     * KineticWeapon 里所有长矛共用的常量 —— 官方 Item.java:483-498
     * ------------------------------------------------------------------ */
    /** {@code KineticWeapon.contactCooldownTicks}：同一目标的突刺再命中间隔。 */
    public static final int CONTACT_COOLDOWN_TICKS = 10;
    /** {@code KineticWeapon.forwardMovement}：动画里矛尖前伸量，供渲染用。 */
    public static final float FORWARD_MOVEMENT = 0.38F;
    /**
     * {@code MINIMUM_ATTACK_CHARGE = 1.0F}（官方 Item.java:508）：
     * 攻击冷却没走满时左键完全不生效，见 {@link #canAttackWith(PlayerEntity)}。
     */
    public static final float MINIMUM_ATTACK_CHARGE = 1.0F;

    /** {@code SwingAnimation(STAB, swingTicks)}，也就是挥击动画总时长。 */
    private final int swingTicks;
    /** {@code KineticWeapon.damageMultiplier}。 */
    private final float damageMultiplier;
    /** {@code KineticWeapon.delayTicks}：右键后要蓄多少 tick 才开始判定。 */
    private final int delayTicks;
    private final int dismountMaxDurationTicks;
    private final float dismountMinSpeed;
    private final int knockbackMaxDurationTicks;
    private final float knockbackMinSpeed;
    private final int damageMaxDurationTicks;
    private final float damageMinRelativeSpeed;
    /** 官方按 {@code mat == ToolMaterial.WOOD} 选 {@code SPEAR_WOOD_*} 一套音效。 */
    private final boolean woodenSounds;

    private final float attackDamage;
    private final Multimap<Attribute, AttributeModifier> attributeModifiers;

    /**
     * 兼容 {@code ModernItems} 现有的三参调用。官方 {@code Items.java} 里每把长矛都带九个
     * per-material 数值，这里只从调用方收第一个（挥击时长），其余八个按材质查
     * {@link #officialSpearArgs(IItemTier)}——那张表逐行抄自官方 {@code Items.java:1880-1900}。
     *
     * @param swingSeconds 官方 {@code spear(...)} 的第一个参数（{@code STAB} 动画时长，秒）。
     *                     直接收官方字面量而不是收算好的攻速，是为了让代码能和官方
     *                     {@code Items.java} 逐行对照 —— 官方写 {@code 0.65F}，这里也写 {@code 0.65F}。
     */
    public SpearItem(IItemTier tier, float swingSeconds, Item.Properties builderIn)
    {
        this(tier, swingSeconds, officialSpearArgs(tier), builderIn);
    }

    private SpearItem(IItemTier tier, float swingSeconds, float[] rest, Item.Properties builderIn)
    {
        this(tier, swingSeconds, rest[0], rest[1], rest[2], rest[3], rest[4], rest[5], rest[6], rest[7], builderIn);
    }

    /**
     * 参数顺序与官方 {@code Item.Properties.spear(...)} 完全一致，方便逐个对照。
     *
     * @param swingSeconds           官方参数 1：{@code STAB} 动画时长（秒）
     * @param damageMultiplier       官方参数 2：{@code KineticWeapon.damageMultiplier}
     * @param delaySeconds           官方参数 3：{@code KineticWeapon.delayTicks / 20}
     * @param dismountSeconds        官方参数 4：掀坐骑判定窗口（秒）
     * @param dismountMinSpeed       官方参数 5：掀坐骑所需自身速度（格/秒）
     * @param knockbackSeconds       官方参数 6：击退判定窗口（秒）
     * @param knockbackMinSpeed      官方参数 7：击退所需自身速度（格/秒）
     * @param damageSeconds          官方参数 8：伤害判定窗口（秒）
     * @param damageMinRelativeSpeed 官方参数 9：造成伤害所需相对接近速度（格/秒）
     */
    public SpearItem(IItemTier tier, float swingSeconds, float damageMultiplier, float delaySeconds,
                     float dismountSeconds, float dismountMinSpeed,
                     float knockbackSeconds, float knockbackMinSpeed,
                     float damageSeconds, float damageMinRelativeSpeed,
                     Item.Properties builderIn)
    {
        super(tier, builderIn);
        // 官方 KineticWeapon 里所有「秒」都是 (int)(x * 20.0F)，这里保持同样的截断方式。
        this.swingTicks = (int)(swingSeconds * 20.0F);
        this.damageMultiplier = damageMultiplier;
        this.delayTicks = (int)(delaySeconds * 20.0F);
        this.dismountMaxDurationTicks = (int)(dismountSeconds * 20.0F);
        this.dismountMinSpeed = dismountMinSpeed;
        this.knockbackMaxDurationTicks = (int)(knockbackSeconds * 20.0F);
        this.knockbackMinSpeed = knockbackMinSpeed;
        this.damageMaxDurationTicks = (int)(damageSeconds * 20.0F);
        this.damageMinRelativeSpeed = damageMinRelativeSpeed;
        this.woodenSounds = tier == ItemTier.WOOD;

        // 官方 createToolAttributes 的基础攻击力项，长矛恒为 0，只吃材质加成。
        this.attackDamage = 0.0F + tier.getAttackDamage();
        // 官方：new AttributeModifier(BASE_ATTACK_SPEED_ID, 1.0F / swingSeconds - 4.0, ADD_VALUE)
        float attackSpeed = 1.0F / swingSeconds - 4.0F;
        Builder<Attribute, AttributeModifier> builder = ImmutableMultimap.builder();
        builder.put(Attributes.ATTACK_DAMAGE, new AttributeModifier(ATTACK_DAMAGE_MODIFIER, "Weapon modifier", (double)this.attackDamage, AttributeModifier.Operation.ADDITION));
        builder.put(Attributes.ATTACK_SPEED, new AttributeModifier(ATTACK_SPEED_MODIFIER, "Weapon modifier", (double)attackSpeed, AttributeModifier.Operation.ADDITION));
        this.attributeModifiers = builder.build();
    }

    /**
     * 官方 {@code Items.java:1880-1900} 七把长矛的后八个参数，逐行照抄。
     * 顺序：damageMultiplier, delaySeconds, dismountSeconds, dismountMinSpeed,
     * knockbackSeconds, knockbackMinSpeed, damageSeconds, damageMinRelativeSpeed。
     */
    private static float[] officialSpearArgs(IItemTier tier)
    {
        if (tier == ItemTier.WOOD)
        {
            // spear(WOOD, 0.65F, 0.7F, 0.75F, 5.0F, 14.0F, 10.0F, 5.1F, 15.0F, 4.6F)
            return new float[] {0.7F, 0.75F, 5.0F, 14.0F, 10.0F, 5.1F, 15.0F, 4.6F};
        }
        if (tier == ItemTier.STONE)
        {
            // spear(STONE, 0.75F, 0.82F, 0.7F, 4.5F, 10.0F, 9.0F, 5.1F, 13.75F, 4.6F)
            return new float[] {0.82F, 0.7F, 4.5F, 10.0F, 9.0F, 5.1F, 13.75F, 4.6F};
        }
        if (tier == ItemTier.COPPER)
        {
            // spear(COPPER, 0.85F, 0.82F, 0.65F, 4.0F, 9.0F, 8.25F, 5.1F, 12.5F, 4.6F)
            return new float[] {0.82F, 0.65F, 4.0F, 9.0F, 8.25F, 5.1F, 12.5F, 4.6F};
        }
        if (tier == ItemTier.IRON)
        {
            // spear(IRON, 0.95F, 0.95F, 0.6F, 2.5F, 8.0F, 6.75F, 5.1F, 11.25F, 4.6F)
            return new float[] {0.95F, 0.6F, 2.5F, 8.0F, 6.75F, 5.1F, 11.25F, 4.6F};
        }
        if (tier == ItemTier.GOLD)
        {
            // spear(GOLD, 0.95F, 0.7F, 0.7F, 3.5F, 10.0F, 8.5F, 5.1F, 13.75F, 4.6F)
            return new float[] {0.7F, 0.7F, 3.5F, 10.0F, 8.5F, 5.1F, 13.75F, 4.6F};
        }
        if (tier == ItemTier.DIAMOND)
        {
            // spear(DIAMOND, 1.05F, 1.075F, 0.5F, 3.0F, 7.5F, 6.5F, 5.1F, 10.0F, 4.6F)
            return new float[] {1.075F, 0.5F, 3.0F, 7.5F, 6.5F, 5.1F, 10.0F, 4.6F};
        }
        if (tier == ItemTier.NETHERITE)
        {
            // spear(NETHERITE, 1.15F, 1.2F, 0.4F, 2.5F, 7.0F, 5.5F, 5.1F, 8.75F, 4.6F)
            return new float[] {1.2F, 0.4F, 2.5F, 7.0F, 5.5F, 5.1F, 8.75F, 4.6F};
        }
        // 官方没有这个材质的长矛：退回铁矛的曲线，至少行为完整而不是静默失效。
        return new float[] {0.95F, 0.6F, 2.5F, 8.0F, 6.75F, 5.1F, 11.25F, 4.6F};
    }

    public float getAttackDamage()
    {
        return this.attackDamage;
    }

    /** {@code SwingAnimation.duration}：给 {@code LivingEntity.getArmSwingAnimationEnd} 用。 */
    public int getSwingTicks()
    {
        return this.swingTicks;
    }

    /** {@code KineticWeapon.delayTicks}：给动画和突刺判定共用。 */
    public int getDelayTicks()
    {
        return this.delayTicks;
    }

    public int getDismountMaxDurationTicks()
    {
        return this.dismountMaxDurationTicks;
    }

    public int getKnockbackMaxDurationTicks()
    {
        return this.knockbackMaxDurationTicks;
    }

    public int getDamageMaxDurationTicks()
    {
        return this.damageMaxDurationTicks;
    }

    /* ------------------------------------------------------------------
     * 音效。官方是 item.spear.{use,hit,attack} 与 item.spear_wood.{...}，
     * 1.16.4 没有这些 SoundEvent，用最接近的原版音效替代。
     * ------------------------------------------------------------------ */

    /**
     * 官方 {@code SPEAR_USE} / {@code SPEAR_WOOD_USE}：右键架起长矛。
     * 1.16.4 木/金属两套都没有对应音效，这里共用三叉戟回收的低沉挥动声。
     */
    public SoundEvent getUseSound()
    {
        return SoundEvents.ITEM_TRIDENT_RETURN;
    }

    /** 官方 {@code SPEAR_ATTACK} / {@code SPEAR_WOOD_ATTACK}：左键穿刺时的挥空声。 */
    public SoundEvent getAttackSound()
    {
        return this.woodenSounds ? SoundEvents.ENTITY_PLAYER_ATTACK_SWEEP : SoundEvents.ITEM_TRIDENT_THROW;
    }

    /** 官方 {@code SPEAR_HIT} / {@code SPEAR_WOOD_HIT}：矛尖扎到东西。 */
    public SoundEvent getHitSound()
    {
        return this.woodenSounds ? SoundEvents.BLOCK_WOOD_HIT : SoundEvents.ITEM_TRIDENT_HIT;
    }

    /* ------------------------------------------------------------------
     * AttackRange 的判定逻辑 —— 官方 world/item/component/AttackRange.java
     * ------------------------------------------------------------------ */

    /** 主手拿着长矛就返回它，否则 null。官方对应 {@code getActiveItem().get(ATTACK_RANGE)}。 */
    public static SpearItem getHeldSpear(LivingEntity entity)
    {
        if (entity == null)
        {
            return null;
        }
        // 官方 LivingEntity.getActiveItem()：正在使用物品时取使用中的那只手，否则取主手。
        ItemStack stack = entity.isHandActive() ? entity.getActiveItemStack() : entity.getHeldItemMainhand();
        return stack.getItem() instanceof SpearItem ? (SpearItem)stack.getItem() : null;
    }

    /** 官方 {@code AttackRange.effectiveMinRange}。 */
    public static float effectiveMinRange(Entity entity)
    {
        if (entity instanceof PlayerEntity)
        {
            PlayerEntity player = (PlayerEntity)entity;
            if (player.isSpectator())
            {
                return 0.0F;
            }
            return player.isCreative() ? MIN_CREATIVE_RANGE : MIN_RANGE;
        }
        return MIN_RANGE * MOB_FACTOR;
    }

    /** 官方 {@code AttackRange.effectiveMaxRange}。 */
    public static float effectiveMaxRange(Entity entity)
    {
        if (entity instanceof PlayerEntity)
        {
            return ((PlayerEntity)entity).isCreative() ? MAX_CREATIVE_RANGE : MAX_RANGE;
        }
        return MAX_RANGE * MOB_FACTOR;
    }

    /**
     * 官方 {@code AttackRange.isInRange(LivingEntity, Vec3)}：命中点必须落在
     * [minRange - margin, maxRange + margin] 这个<b>环形</b>区间里。
     */
    public static boolean isInRange(LivingEntity attacker, Vector3d hitPos)
    {
        double dist = hitPos.distanceTo(attacker.getEyePosition(1.0F));
        double min = effectiveMinRange(attacker) - HITBOX_MARGIN;
        double max = effectiveMaxRange(attacker) + HITBOX_MARGIN;
        return dist >= min && dist <= max;
    }

    /**
     * 目标碰撞箱上离 {@code from} 最近的点。用于把客户端选中的目标折算成一个「命中点」，
     * 好交给 {@link #isInRange} 按官方的环形区间校验 —— 用实体中心会让贴脸的大体型生物
     * 被误判成超出最小距离。
     */
    private static Vector3d closestPointTo(Entity target, Vector3d from)
    {
        AxisAlignedBB box = target.getBoundingBox();
        return new Vector3d(
                MathHelper.clamp(from.x, box.minX, box.maxX),
                MathHelper.clamp(from.y, box.minY, box.maxY),
                MathHelper.clamp(from.z, box.minZ, box.maxZ));
    }

    /**
     * 官方 {@code Player.cannotAttackWithItem}（Player.java:1809）取反：
     * {@code MINIMUM_ATTACK_CHARGE = 1.0F} 意味着攻击冷却没走满时左键完全不生效
     * ——长矛不能连点。
     *
     * @param adjustTicks 官方 {@code p_456383_}：冷却进度的提前量。客户端
     *                    {@code Minecraft.startAttack} 传 0，服务端收到 STAB 包时传 5
     *                    （给网络延迟留余量），这里沿用同样的取值。
     */
    public static boolean canAttackWith(PlayerEntity player, int adjustTicks)
    {
        return player.getCooledAttackStrength((float)adjustTicks) >= MINIMUM_ATTACK_CHARGE;
    }

    /** 客户端用（官方 {@code cannotAttackWithItem(stack, 0)}）。 */
    public static boolean canAttackWith(PlayerEntity player)
    {
        return canAttackWith(player, 0);
    }

    /* ------------------------------------------------------------------
     * 突刺的「使用」生命周期 —— 官方 Item.use / Item.getUseDuration / Item.getUseAnimation
     * ------------------------------------------------------------------ */

    /**
     * 官方 {@code Item.getUseDuration}（Item.java:309）：带 {@code KINETIC_WEAPON} 的物品
     * 返回 72000，也就是「按住多久都行」。
     */
    public int getUseDuration(ItemStack stack)
    {
        return 72000;
    }

    /**
     * 官方 {@code Item.getUseAnimation} 返回 {@code ItemUseAnimation.SPEAR}（一个 1.21 新枚举）。
     *
     * <p>1.16.4 的 {@code UseAction.SPEAR} 是<b>三叉戟</b>的举矛待投姿势，语义不同，
     * 直接复用会渲染成投掷预备动作。这里返回 {@code NONE}，第一/第三人称的突刺姿势由
     * {@code FirstPersonRenderer} / {@code BipedModel} 里的 {@code instanceof SpearItem}
     * 分支接管（见 {@code net.minecraft.client.renderer.SpearAnimations}）。
     */
    public UseAction getUseAction(ItemStack stack)
    {
        return UseAction.NONE;
    }

    /**
     * 官方 {@code Item.java:524}：长矛的 {@code USE_EFFECTS} 是
     * {@code new UseEffects(true, false, 1.0F)} —— 倍率 1.0，<b>举着长矛不减速</b>。
     *
     * <p>1.16.4 对任何「使用中」的手一律降到 0.2，照搬会让长矛右键之后走不动路。
     */
    public float getUseSpeedMultiplier(ItemStack stack)
    {
        return 1.0F;
    }

    /**
     * 官方同一处的 {@code canSprint = true}：举着长矛可以起步疾跑。
     *
     * <p>这条不是可有可无的细节 —— 突刺的伤害判定要求自身速度达到阈值
     * （{@code KineticWeapon.Condition.ofAttackerSpeed}，见构造器里的
     * {@code dismountMinSpeed} / {@code knockbackMinSpeed}），不许疾跑就永远达不到，
     * 突刺等于废掉。
     */
    public boolean canSprintWhileUsing(ItemStack stack)
    {
        return true;
    }

    /**
     * 官方 {@code Item.use}（Item.java:198）：有 {@code KINETIC_WEAPON} 就
     * {@code startUsingItem} 并播放 use 音效，返回 {@code CONSUME}。
     */
    public ActionResult<ItemStack> onItemRightClick(World worldIn, PlayerEntity playerIn, Hand handIn)
    {
        ItemStack stack = playerIn.getHeldItem(handIn);
        playerIn.setActiveHand(handIn);
        // 官方 KineticWeapon.makeSound：以使用者为源播一次，周围玩家都能听见。
        worldIn.playSound(null, playerIn.getPosX(), playerIn.getPosY(), playerIn.getPosZ(),
                this.getUseSound(), playerIn.getSoundCategory(), 1.0F, 1.0F);
        // 官方 startUsingItem 在服务端会新建 recentKineticEnemies，这里同样重置命中记录。
        resetStabMemory(playerIn);
        return ActionResult.resultConsume(stack);
    }

    /* ------------------------------------------------------------------
     * 突刺的「最近已扎过谁」记录 —— 官方是 LivingEntity.recentKineticEnemies 字段
     * （配 wasRecentlyStabbed / rememberStabbedEntity / stabbedEntities）。
     * 为了不往 LivingEntity 里塞字段，这里用弱引用表存在 Item 上，语义相同。
     * ------------------------------------------------------------------ */
    private static final Map<LivingEntity, Map<Entity, Long>> RECENT_KINETIC_ENEMIES =
            Collections.synchronizedMap(new WeakHashMap<LivingEntity, Map<Entity, Long>>());

    /** 官方 {@code startUsingItem} 里 {@code recentKineticEnemies = new Object2LongOpenHashMap<>()}。 */
    private static void resetStabMemory(LivingEntity user)
    {
        RECENT_KINETIC_ENEMIES.put(user, Collections.synchronizedMap(new WeakHashMap<Entity, Long>()));
    }

    /** 官方 {@code LivingEntity.wasRecentlyStabbed}。 */
    private static boolean wasRecentlyStabbed(LivingEntity user, Entity target, int cooldownTicks)
    {
        Map<Entity, Long> seen = RECENT_KINETIC_ENEMIES.get(user);
        if (seen == null)
        {
            return false;
        }
        Long when = seen.get(target);
        return when != null && user.world.getGameTime() - when < cooldownTicks;
    }

    /** 官方 {@code LivingEntity.rememberStabbedEntity}。 */
    private static void rememberStabbedEntity(LivingEntity user, Entity target)
    {
        Map<Entity, Long> seen = RECENT_KINETIC_ENEMIES.get(user);
        if (seen != null)
        {
            seen.put(target, user.world.getGameTime());
        }
    }

    /* ------------------------------------------------------------------
     * 沿视线找出所有可命中实体 —— 官方 ProjectileUtil.getHitEntitiesAlong
     * （world/entity/projectile/ProjectileUtil.java:38 与 :172）
     * ------------------------------------------------------------------ */

    /**
     * 官方 {@code PiercingWeapon.canHitEntity}（PiercingWeapon.java:74）。
     *
     * <p>官方的 {@code canBeHitByProjectile()} 在 1.16.4 里对应 {@code canBeCollidedWith()}；
     * {@code Interaction} 实体 1.16.4 不存在，那条分支省略。
     */
    public static boolean canHitEntity(Entity attacker, Entity target)
    {
        if (target.isInvulnerable() || !target.isAlive())
        {
            return false;
        }
        if (!target.canBeCollidedWith())
        {
            return false;
        }
        if (target instanceof PlayerEntity && attacker instanceof PlayerEntity
                && !((PlayerEntity)attacker).canAttackPlayer((PlayerEntity)target))
        {
            return false;
        }
        return !attacker.isRidingSameEntity(target);
    }

    /**
     * 官方 {@code ProjectileUtil.getHitEntitiesAlong}：从眼睛出发，射线起点推到
     * {@code minRange}，终点是 {@code maxRange} 再加上「自身前冲速度」的补偿，
     * 中间被方块挡住就截断。返回值按距离由近到远排序。
     *
     * <p>官方返回 {@code Either<BlockHitResult, Collection<EntityHitResult>>}，
     * 被方块挡死时给左值；这里简化成「挡死就返回空表」，因为两个调用方都只关心实体。
     */
    public static List<EntityRayTraceResult> getHitEntitiesAlong(Entity attacker, Predicate<Entity> filter)
    {
        World world = attacker.world;
        Vector3d look = attacker.getLook(1.0F);
        Vector3d eye = attacker.getEyePosition(1.0F);
        Vector3d start = eye.add(look.scale(effectiveMinRange(attacker)));
        // 官方 d0 = getKnownMovement().dot(look)：往前冲时射线相应加长。
        //
        // 必须用 getKnownMovement() 而不是 getMotion()。服务端玩家的 motion 从不更新
        // （服务端只把玩家传送到客户端上报的坐标），读出来恒为 0，于是这段「前冲补偿」
        // 永远是 0，攻击距离比官方短一截 —— 实测表现为「右键感觉近了一点点」。
        double forward = attacker.getKnownMovement().dotProduct(look);
        Vector3d end = eye.add(look.scale(effectiveMaxRange(attacker) + Math.max(0.0D, forward)));

        // 官方从「眼睛」而不是从 minRange 起点做方块遮挡判定。
        BlockRayTraceResult blockHit = world.rayTraceBlocks(new RayTraceContext(
                eye, end, RayTraceContext.BlockMode.COLLIDER, RayTraceContext.FluidMode.NONE, attacker));
        if (blockHit.getType() != RayTraceResult.Type.MISS)
        {
            end = blockHit.getHitVec();
            if (eye.squareDistanceTo(end) < eye.squareDistanceTo(start))
            {
                // 墙比 minRange 还近，整条射线都被吃掉了。
                return Collections.emptyList();
            }
        }

        AxisAlignedBB search = AxisAlignedBB.withSizeAtOrigin(HITBOX_MARGIN, HITBOX_MARGIN, HITBOX_MARGIN)
                .offset(start).expand(end.subtract(start)).grow(1.0D);
        List<EntityRayTraceResult> hits = new ArrayList<EntityRayTraceResult>();

        for (Entity target : world.getEntitiesInAABBexcluding(attacker, search, filter))
        {
            AxisAlignedBB box = target.getBoundingBox();
            if (box.contains(start))
            {
                // 官方 checkStartInside=true：起点已经在碰撞箱里就直接算命中。
                hits.add(new EntityRayTraceResult(target, start));
                continue;
            }
            Optional<Vector3d> direct = box.rayTrace(start, end);
            if (direct.isPresent())
            {
                hits.add(new EntityRayTraceResult(target, direct.get()));
                continue;
            }
            // 官方的 margin 补偿：擦边也算，但要求从擦边点到碰撞箱中心之间没有方块。
            Optional<Vector3d> grazed = box.grow(HITBOX_MARGIN).rayTrace(start, end);
            if (grazed.isPresent())
            {
                Vector3d from = grazed.get();
                Vector3d center = box.getCenter();
                BlockRayTraceResult blocked = world.rayTraceBlocks(new RayTraceContext(
                        from, center, RayTraceContext.BlockMode.COLLIDER, RayTraceContext.FluidMode.NONE, attacker));
                if (blocked.getType() != RayTraceResult.Type.MISS)
                {
                    center = blocked.getHitVec();
                }
                Optional<Vector3d> viaCenter = target.getBoundingBox().rayTrace(from, center);
                if (viaCenter.isPresent())
                {
                    hits.add(new EntityRayTraceResult(target, viaCenter.get()));
                }
            }
        }

        // 官方靠 Collection 的迭代顺序，这里显式按距离排序，让穿透结算从近到远。
        final Vector3d origin = eye;
        Collections.sort(hits, new Comparator<EntityRayTraceResult>()
        {
            public int compare(EntityRayTraceResult a, EntityRayTraceResult b)
            {
                return Double.compare(origin.squareDistanceTo(a.getHitVec()), origin.squareDistanceTo(b.getHitVec()));
            }
        });
        return hits;
    }

    /**
     * 官方 {@code LivingEntity.stabAttack}（LivingEntity.java:2732）。突刺和穿透两条路都走它。
     *
     * @param doDamage  官方 {@code p_451169_}：是否真的扣血（突刺时由 damageConditions 决定）
     * @param knockback 官方 {@code p_460628_}：是否击退
     * @param dismount  官方 {@code p_459099_}：是否把目标从坐骑上掀下来
     * @return 是否算「命中」（官方：击退成立或扣血成立或掀下坐骑成立）
     */
    public static boolean stabAttack(LivingEntity attacker, ItemStack stack, Entity target,
                                    float damage, boolean doDamage, boolean knockback, boolean dismount)
    {
        if (attacker.world.isRemote)
        {
            // 官方 stabAttack 只在 ServerLevel 上结算。
            return false;
        }

        // 官方 itemstack.getDamageSource(this, () -> damageSources().mobAttack(this))：
        // 长矛的 DAMAGE_TYPE 组件是 DamageTypes.SPEAR。1.16.4 没有数据驱动的伤害类型，
        // 退回原版的玩家/生物攻击伤害源。
        DamageSource source = attacker instanceof PlayerEntity
                ? DamageSource.causePlayerDamage((PlayerEntity)attacker)
                : DamageSource.causeMobDamage(attacker);

        // 官方 EnchantmentHelper.modifyDamage：1.16.4 的等价物是针对生物类型的锋利/亡灵杀手加成。
        float finalDamage = damage;
        if (target instanceof LivingEntity)
        {
            finalDamage += EnchantmentHelper.getModifierForCreature(stack, ((LivingEntity)target).getCreatureAttribute());
        }

        Vector3d prevMotion = target.getMotion();
        boolean hurt = doDamage && target.attackEntityFrom(source, finalDamage);
        boolean anyEffect = knockback | hurt;

        if (knockback)
        {
            // 官方 causeExtraKnockback(target, 0.4F + getKnockback(target, src), prevMotion)
            // （LivingEntity.java:2587）：沿攻击者朝向推，然后攻击者自身水平速度打 0.6 折。
            float strength = 0.4F + EnchantmentHelper.getKnockbackModifier(attacker);
            if (strength > 0.0F && target instanceof LivingEntity)
            {
                float yaw = attacker.rotationYaw * ((float)Math.PI / 180.0F);
                ((LivingEntity)target).applyKnockback(strength, MathHelper.sin(yaw), -MathHelper.cos(yaw));
                attacker.setMotion(attacker.getMotion().mul(0.6D, 1.0D, 0.6D));
            }
            else if (strength > 0.0F)
            {
                // 非生物目标官方不推，但攻击者的减速照旧不适用（官方 causeExtraKnockback 整段跳过）。
                target.setMotion(prevMotion);
            }
        }

        if (dismount && target.isPassenger())
        {
            anyEffect = true;
            target.stopRiding();
        }

        if (target instanceof LivingEntity && attacker instanceof PlayerEntity)
        {
            // 官方 itemstack.hurtEnemy(livingentity, this)：1.16.4 的签名只吃 PlayerEntity。
            stack.hitEntity((LivingEntity)target, (PlayerEntity)attacker);
        }

        if (hurt)
        {
            // 官方 EnchantmentHelper.doPostAttackEffects：1.16.4 里对应荆棘与节肢杀手的回调。
            if (target instanceof LivingEntity)
            {
                EnchantmentHelper.applyThornEnchantments((LivingEntity)target, attacker);
            }
            EnchantmentHelper.applyArthropodEnchantments(attacker, target);
        }

        if (!anyEffect)
        {
            return false;
        }
        attacker.setLastAttackedEntity(target);
        return true;
    }

    /* ------------------------------------------------------------------
     * 突刺结算 —— 官方 KineticWeapon.damageEntities
     * （world/item/component/KineticWeapon.java:113），由 ItemStack.onUseTick 每 tick 调一次。
     * ------------------------------------------------------------------ */

    /**
     * 官方 {@code KineticWeapon.getMotion}：速度换算成「格/秒」，坐骑上的非玩家取最底层载具。
     *
     * <p>用 {@link Entity#getKnownMovement()} 而不是 {@code getMotion()}。这不是等价替换，
     * 是必须的：服务端玩家的 {@code motion} 从不更新（服务端只把玩家传送到客户端上报的坐标），
     * 疾跑时读出来接近 0，于是所有速度门槛都不成立 —— 表现就是「对准目标、满速疾跑、右键，
     * 没有任何伤害」。官方 1.20.5 引入 {@code getKnownMovement()} 正是为了这个问题。
     */
    private static Vector3d motionPerSecond(Entity entity)
    {
        if (!(entity instanceof PlayerEntity) && entity.isPassenger())
        {
            entity = entity.getLowestRidingEntity();
        }
        return entity.getKnownMovement().scale(20.0D);
    }

    /**
     * 官方 {@code KineticWeapon.Condition.test}（KineticWeapon.java:169）：
     * {@code i <= maxDurationTicks && speed >= minSpeed * factor && relSpeed >= minRelativeSpeed * factor}
     */
    private static boolean testCondition(int elapsed, double speed, double relativeSpeed, double factor,
                                         int maxDurationTicks, float minSpeed, float minRelativeSpeed)
    {
        return elapsed <= maxDurationTicks
                && speed >= minSpeed * factor
                && relativeSpeed >= minRelativeSpeed * factor;
    }

    /**
     * 1.16.4 里 {@code ItemStack.onItemUsed} 每 tick 会调到这里，等价于官方
     * {@code ItemStack.onUseTick} → {@code KineticWeapon.damageEntities}。
     *
     * @param count 剩余使用 tick 数（官方 {@code getUseItemRemainingTicks}）
     */
    public void onUse(World worldIn, LivingEntity livingEntityIn, ItemStack stack, int count)
    {
        if (worldIn.isRemote)
        {
            // 官方：KineticWeapon.damageEntities 只在 !isClientSide 时跑。
            return;
        }

        int elapsed = stack.getUseDuration() - count;
        if (elapsed < this.delayTicks)
        {
            return;
        }
        elapsed -= this.delayTicks;

        Vector3d look = livingEntityIn.getLook(1.0F);
        double ownSpeed = look.dotProduct(motionPerSecond(livingEntityIn));
        // 官方 f：玩家 1.0，其他生物 0.2（也就是生物的速度门槛低得多）。
        float factor = livingEntityIn instanceof PlayerEntity ? 1.0F : 0.2F;
        // 官方用 getAttributeBaseValue（基础值，不含长矛自己的攻击力修饰符）——
        // 所以突刺伤害只吃「基础攻击力 + 相对速度」，材质攻击力只影响左键穿刺。
        double baseDamage = livingEntityIn.getAttribute(Attributes.ATTACK_DAMAGE).getBaseValue();

        final LivingEntity user = livingEntityIn;
        boolean anyHit = false;

        for (EntityRayTraceResult hit : getHitEntitiesAlong(livingEntityIn, new Predicate<Entity>()
        {
            public boolean test(Entity candidate)
            {
                return canHitEntity(user, candidate);
            }
        }))
        {
            Entity target = hit.getEntity();
            if (target instanceof EnderDragonPartEntity)
            {
                // 官方：EnderDragonPart 的伤害要转给本体。
                target = ((EnderDragonPartEntity)target).dragon;
            }
            if (wasRecentlyStabbed(livingEntityIn, target, CONTACT_COOLDOWN_TICKS))
            {
                continue;
            }
            rememberStabbedEntity(livingEntityIn, target);

            double targetSpeed = look.dotProduct(motionPerSecond(target));
            // 官方 d3：相对接近速度，负数按 0 算（目标跑得比你快就不算撞上）。
            double relativeSpeed = Math.max(0.0D, ownSpeed - targetSpeed);

            boolean dismount = testCondition(elapsed, ownSpeed, relativeSpeed, factor,
                    this.dismountMaxDurationTicks, this.dismountMinSpeed, 0.0F);
            boolean knockback = testCondition(elapsed, ownSpeed, relativeSpeed, factor,
                    this.knockbackMaxDurationTicks, this.knockbackMinSpeed, 0.0F);
            boolean damage = testCondition(elapsed, ownSpeed, relativeSpeed, factor,
                    this.damageMaxDurationTicks, 0.0F, this.damageMinRelativeSpeed);

            if (dismount || knockback || damage)
            {
                // 官方：f1 = (float)d1 + Mth.floor(d3 * damageMultiplier)
                float dealt = (float)baseDamage + MathHelper.floor(relativeSpeed * this.damageMultiplier);
                anyHit |= stabAttack(livingEntityIn, stack, target, dealt, damage, knockback, dismount);
            }
        }

        if (anyHit)
        {
            // 官方是 broadcastEntityEvent(user, (byte)2) → 客户端 onKineticHit()：
            // 本地 hit 音效 + 模型回弹。1.16.4 的实体状态 2 还是「受伤动画」不能复用，
            // 所以退化成直接播一次世界音效，回弹动画未实现。
            worldIn.playSound(null, livingEntityIn.getPosX(), livingEntityIn.getPosY(), livingEntityIn.getPosZ(),
                    this.getHitSound(), livingEntityIn.getSoundCategory(), 1.0F, 1.0F);
        }
    }

    /* ------------------------------------------------------------------
     * 穿透攻击 —— 官方 PiercingWeapon.attack
     * （world/item/component/PiercingWeapon.java:88）
     * 长矛的组件是 new PiercingWeapon(true, false, SPEAR_ATTACK, SPEAR_HIT)：
     * 会击退（dealsKnockback=true），不掀坐骑（dismounts=false）。
     * ------------------------------------------------------------------ */

    /** {@code PiercingWeapon.dealsKnockback}。 */
    public static final boolean DEALS_KNOCKBACK = true;
    /** {@code PiercingWeapon.dismounts}。 */
    public static final boolean DISMOUNTS = false;

    /**
     * 官方 {@code PiercingWeapon.attack}：左键一次打穿视线上的<b>所有</b>实体。
     *
     * <p>两端都会跑：客户端跑到 {@link #stabAttack} 时因为 {@code isRemote} 直接返回 false，
     * 只留下挥手和音效（本地预测）；服务端（含单人的内置服务端）才真正结算伤害。
     *
     * <p>与官方的差异：官方客户端发一个 {@code Action.STAB} 包，服务端收到后调
     * {@code PiercingWeapon.attack}。1.16.4 的协议没有 STAB 动作，所以改成让
     * {@code PlayerEntity.attackTargetEntityWithCurrentItem} 在主手是长矛时转调本方法
     * ——客户端照旧只发一个普通攻击包，服务端把它展开成穿透攻击，行为链和官方一致。
     *
     * @return 是否命中了至少一个实体
     */
    public boolean piercingAttack(LivingEntity attacker, ItemStack stack)
    {
        return this.piercingAttack(attacker, stack, null);
    }

    /**
     * @param clientTarget 客户端准星实际选中的那个实体，没有就传 {@code null}。
     *
     * <p><b>为什么需要这个参数（刻意偏离官方的地方）。</b>官方左键长矛时客户端发的是
     * {@code Action.STAB}，不带目标，服务端完全靠自己重新射线。本项目的 1.16.4 协议没有
     * STAB 动作，只能借用普通攻击包，而那个包<b>是带目标的</b>。
     *
     * <p>如果照官方只信服务端自己的射线，就会出现「明明对着打却没伤害」：服务端的玩家朝向
     * 来自上一个移动包（晚一 tick），目标实体的位置也不走客户端那套插值，两边的射线结果
     * 并不总是一致 —— 稍微偏一点服务端就判不中。原版近战没这个问题，因为原版<b>只验距离、
     * 不重新射线</b>，命中判定完全交给客户端。
     *
     * <p>所以这里取两者的并集：服务端射线负责「一次打穿一条线上的多个目标」，
     * 客户端选中的目标则无条件计入（仍按官方 {@link #isInRange} 校验距离，
     * 不放宽攻击距离）。既保住穿透，又不比原版近战更难命中。
     */
    public boolean piercingAttack(LivingEntity attacker, ItemStack stack, Entity clientTarget)
    {
        World world = attacker.world;
        // 官方用 getAttributeValue（含长矛自己的攻击力修饰符），与突刺用 base 值不同。
        float damage = (float)attacker.getAttributeValue(Attributes.ATTACK_DAMAGE);
        final LivingEntity user = attacker;
        boolean anyHit = false;

        List<EntityRayTraceResult> hits = getHitEntitiesAlong(attacker, new Predicate<Entity>()
        {
            public boolean test(Entity candidate)
            {
                return canHitEntity(user, candidate);
            }
        });

        // 客户端选中的目标若不在服务端射线的结果里，补进去（去重，否则会打两次）。
        if (clientTarget != null && canHitEntity(attacker, clientTarget)
                && isInRange(attacker, closestPointTo(clientTarget, attacker.getEyePosition(1.0F))))
        {
            boolean already = false;

            for (EntityRayTraceResult hit : hits)
            {
                if (hit.getEntity() == clientTarget)
                {
                    already = true;
                    break;
                }
            }

            if (!already)
            {
                hits.add(new EntityRayTraceResult(clientTarget, closestPointTo(clientTarget, attacker.getEyePosition(1.0F))));
            }
        }

        for (EntityRayTraceResult hit : hits)
        {
            anyHit |= stabAttack(attacker, stack, hit.getEntity(), damage, true, DEALS_KNOCKBACK, DISMOUNTS);
        }

        // 官方 PiercingWeapon.attack 在这里依次调 onAttack() 与 lungeForwardMaybe()。
        // 前者在 LivingEntity 里是空实现，省略。
        //
        // 后者是「突进」（Lunge）附魔的效果，位置有三个讲究，都照官方：
        //   1) 必须在上面的循环<b>之后</b> —— 冲量会移动攻击者，射线判定中途改位置会改变命中列表
        //   2) 必须在 if (anyHit) <b>之外</b> —— 官方挥空也会突进
        //   3) 必须在下面两个 playSound <b>之前</b> —— 突进音效排在命中音和挥击音前面
        LungeEnchantment.lungeForwardMaybe(attacker, attacker.getHeldItemMainhand());

        PlayerEntity soundSource = attacker instanceof PlayerEntity ? (PlayerEntity)attacker : null;
        if (anyHit)
        {
            world.playSound(soundSource, attacker.getPosX(), attacker.getPosY(), attacker.getPosZ(),
                    this.getHitSound(), attacker.getSoundCategory(), 1.0F, 1.0F);
        }
        world.playSound(soundSource, attacker.getPosX(), attacker.getPosY(), attacker.getPosZ(),
                this.getAttackSound(), attacker.getSoundCategory(), 1.0F, 1.0F);
        attacker.swing(Hand.MAIN_HAND, false);
        return anyHit;
    }

    /** 官方 {@code Weapon(1)}：每次攻击掉 1 点耐久。 */
    public boolean hitEntity(ItemStack stack, LivingEntity target, LivingEntity attacker)
    {
        stack.damageItem(1, attacker, (entity) ->
        {
            entity.sendBreakAnimation(EquipmentSlotType.MAINHAND);
        });
        return true;
    }

    public Multimap<Attribute, AttributeModifier> getAttributeModifiers(EquipmentSlotType equipmentSlot)
    {
        return equipmentSlot == EquipmentSlotType.MAINHAND ? this.attributeModifiers : super.getAttributeModifiers(equipmentSlot);
    }
}
