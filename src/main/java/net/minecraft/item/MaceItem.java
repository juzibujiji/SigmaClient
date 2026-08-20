package net.minecraft.item;

import java.util.List;
import java.util.function.Predicate;

import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.ImmutableMultimap.Builder;
import com.google.common.collect.Multimap;

import net.minecraft.block.BlockState;
import net.minecraft.enchantment.DensityEnchantment;
import net.minecraft.enchantment.IVanishable;
import net.minecraft.enchantment.WindBurstEnchantment;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.attributes.Attribute;
import net.minecraft.entity.ai.attributes.AttributeModifier;
import net.minecraft.entity.ai.attributes.Attributes;
import net.minecraft.entity.item.ArmorStandEntity;
import net.minecraft.entity.passive.TameableEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.inventory.EquipmentSlotType;
import net.minecraft.network.play.server.SEntityVelocityPacket;
import net.minecraft.util.SoundEvent;
import net.minecraft.util.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.vector.Vector3d;
import net.minecraft.world.World;

/**
 * 重锤（1.20.5 / 1.21 加入）。
 *
 * <p>本类是官方 {@code net.minecraft.world.item.MaceItem}（1.21.11）在 1.16.4 / MCP 命名下的
 * 移植。所有数值都直接来自官方源码，方法注释里标注了对应的官方成员，便于日后核对：
 *
 * <ul>
 *   <li>属性（攻击力 5.0、攻击速度 -3.4）来自官方 {@code MaceItem#createAttributes()}；</li>
 *   <li>耐久 500、附魔能力 15、稀有度 epic、以旋风棒修复来自官方 {@code Items#MACE} 的
 *       {@code Item.Properties}；</li>
 *   <li>砸落攻击的阈值与击退常量来自官方 {@code MaceItem} 的
 *       {@code SMASH_ATTACK_FALL_THRESHOLD} / {@code SMASH_ATTACK_HEAVY_THRESHOLD} /
 *       {@code SMASH_ATTACK_KNOCKBACK_RADIUS} / {@code SMASH_ATTACK_KNOCKBACK_POWER}；</li>
 *   <li>分段伤害曲线来自官方 {@code MaceItem#getAttackDamageBonus(Entity, float, DamageSource)}。</li>
 * </ul>
 *
 * <p>砸落攻击的表现（音效、粒子、击退波、清零下落距离）在官方是
 * {@code hurtEnemy} / {@code postHurtEnemy} 里跑的，且只在服务端执行；1.16.4 的对应回调是
 * {@link #hitEntity(ItemStack, LivingEntity, LivingEntity)}，同样只在服务端被调用
 * （见 {@code PlayerEntity#attackTargetEntityWithCurrentItem} 里的 {@code !world.isRemote} 判断）。
 * 因此单机（内置服务端）行为完整；联机时由服务端裁决，本地只做伤害预测。
 */
public class MaceItem extends Item implements IVanishable {
    /**
     * 官方基础攻击力，来自 {@code MaceItem#createAttributes()} 的
     * {@code Attributes.ATTACK_DAMAGE} 修正值（官方常量 {@code DEFAULT_ATTACK_DAMAGE = 5}）。
     *
     * <p>tools/crossversion/RegistryCheck.java 断言了这个常量名和值，不要改。
     */
    public static final double ATTACK_DAMAGE = 5.0D;

    /**
     * 官方攻击速度修正，来自 {@code MaceItem#createAttributes()}
     * （官方常量 {@code DEFAULT_ATTACK_SPEED = -3.4F}）。
     *
     * <p>tools/crossversion/RegistryCheck.java 断言了这个常量名和值，不要改。
     */
    public static final double ATTACK_SPEED = -3.4D;

    /** 官方 {@code MaceItem.SMASH_ATTACK_FALL_THRESHOLD = 1.5F}：触发砸落攻击的最小下落距离。 */
    public static final float SMASH_ATTACK_FALL_THRESHOLD = 1.5F;

    /**
     * 官方 {@code MaceItem.SMASH_ATTACK_HEAVY_THRESHOLD = 5.0F}：超过这个下落距离算「重砸」，
     * 换更沉的落地音效，且击退强度翻倍。
     */
    public static final float SMASH_ATTACK_HEAVY_THRESHOLD = 5.0F;

    /** 官方 {@code MaceItem.SMASH_ATTACK_KNOCKBACK_RADIUS = 3.5F}：击退波半径（以被击中者为圆心）。 */
    public static final float SMASH_ATTACK_KNOCKBACK_RADIUS = 3.5F;

    /** 官方 {@code MaceItem.SMASH_ATTACK_KNOCKBACK_POWER = 0.7F}：击退波强度系数，同时也是竖直分量。 */
    public static final float SMASH_ATTACK_KNOCKBACK_POWER = 0.7F;

    /**
     * 触发砸落攻击的最小下落距离。
     *
     * @deprecated 用 {@link #SMASH_ATTACK_FALL_THRESHOLD}，名字与官方常量对齐。
     */
    @Deprecated
    public static final float MIN_FALL_DISTANCE = SMASH_ATTACK_FALL_THRESHOLD;

    /** 官方 {@code Items#MACE} 的 {@code .durability(500)}。 */
    public static final int MAX_DAMAGE = 500;

    /** 官方 {@code Items#MACE} 的 {@code .enchantable(15)}。 */
    public static final int ENCHANTABILITY = 15;

    /** 官方 {@code Items#MACE} 的 {@code .component(DataComponents.WEAPON, new Weapon(1))}：每次攻击掉 1 耐久。 */
    private static final int DURABILITY_PER_ATTACK = 1;

    /**
     * 官方 {@code MaceItem#createToolProperties()} = {@code new Tool(List.of(), 1.0F, 2, false)}
     * 的第三个参数：挖掉一个方块掉 2 耐久。
     */
    private static final int DURABILITY_PER_BLOCK = 2;

    /** 官方 {@code MaceItem#createToolProperties()} 的 defaultMiningSpeed = 1.0F，且 rules 为空。 */
    private static final float MINING_SPEED = 1.0F;

    /**
     * 官方在空中砸空（被击中者不在地面）时播 {@code SoundEvents.MACE_SMASH_AIR}
     * （{@code item.mace.smash_air}）。1.16.4 没有这个音效，也没有任何风类音效，
     * 用挥砍破空声 {@code entity.player.attack.sweep} 压低音调近似。
     */
    private static final SoundEvent SMASH_AIR = SoundEvents.ENTITY_PLAYER_ATTACK_SWEEP;

    /**
     * 官方落地砸击播 {@code SoundEvents.MACE_SMASH_GROUND}（{@code item.mace.smash_ground}）。
     * 1.16.4 没有，用铁砧落地的重击声 {@code block.anvil.land} 近似。
     */
    private static final SoundEvent SMASH_GROUND = SoundEvents.BLOCK_ANVIL_LAND;

    /**
     * 官方下落超过 {@link #SMASH_ATTACK_HEAVY_THRESHOLD} 时播
     * {@code SoundEvents.MACE_SMASH_GROUND_HEAVY}（{@code item.mace.smash_ground_heavy}）。
     * 1.16.4 没有，仍用 {@code block.anvil.land}，靠更低的音调体现「更重」。
     */
    private static final SoundEvent SMASH_GROUND_HEAVY = SoundEvents.BLOCK_ANVIL_LAND;

    /** 替代音效的音调。官方三个音效都是 volume 1.0 / pitch 1.0，这里只能靠音调区分轻重。 */
    private static final float PITCH_AIR = 0.8F;
    private static final float PITCH_GROUND = 1.0F;
    private static final float PITCH_GROUND_HEAVY = 0.6F;

    /**
     * 官方 {@code MaceItem#knockback} 里 {@code level.levelEvent(2013, target.getOnPos(), 750)}
     * 的事件号与数据；2013 就是 {@code LevelEvent.PARTICLES_SMASH_ATTACK}。
     * 客户端在 {@code WorldRenderer#playEvent} 的 case 2013 里还原成粒子。
     */
    public static final int SMASH_ATTACK_PARTICLE_EVENT = 2013;

    /** 官方传给事件 2013 的 data 值。{@code ParticleUtils#spawnSmashAttackParticles} 用它算粒子数量。 */
    public static final int SMASH_ATTACK_PARTICLE_DATA = 750;

    private final Multimap<Attribute, AttributeModifier> maceAttributes;

    public MaceItem(Item.Properties builderIn) {
        // 官方 Items#MACE 在 Item.Properties 上设 .durability(500)。这里兜一层是为了让
        // 耐久跟着本类走，注册点漏写也不会静默退化成「不可损坏」。
        //
        // 注意：本项目的注册点 ModernItems#MACE 现在也写了 .maxDamage(500)，两边同值，
        // 重复调用是幂等的（只是再赋一次 maxDamage 与 maxStackSize=1）。如果决定让注册点
        // 当唯一来源，可以直接删掉这里的 maxDamage 调用，但那样 MaceItem 单独用就没耐久了。
        super(builderIn.maxDamage(MAX_DAMAGE));
        Builder<Attribute, AttributeModifier> builder = ImmutableMultimap.builder();
        builder.put(Attributes.ATTACK_DAMAGE, new AttributeModifier(
                ATTACK_DAMAGE_MODIFIER, "Weapon modifier", ATTACK_DAMAGE, AttributeModifier.Operation.ADDITION));
        builder.put(Attributes.ATTACK_SPEED, new AttributeModifier(
                ATTACK_SPEED_MODIFIER, "Weapon modifier", ATTACK_SPEED, AttributeModifier.Operation.ADDITION));
        this.maceAttributes = builder.build();
    }

    @Override
    public Multimap<Attribute, AttributeModifier> getAttributeModifiers(EquipmentSlotType equipmentSlot) {
        return equipmentSlot == EquipmentSlotType.MAINHAND
                ? this.maceAttributes
                : super.getAttributeModifiers(equipmentSlot);
    }

    /** 官方 {@code Items#MACE} 的 {@code .enchantable(15)}。 */
    @Override
    public int getItemEnchantability() {
        return ENCHANTABILITY;
    }

    /** 官方 {@code Items#MACE} 的 {@code .repairable(BREEZE_ROD)}：用旋风棒在铁砧上修。 */
    @Override
    public boolean getIsRepairable(ItemStack toRepair, ItemStack repair) {
        return repair.getItem() == ModernItems.BREEZE_ROD || super.getIsRepairable(toRepair, repair);
    }

    /**
     * 是否满足砸落攻击条件。
     *
     * <p>官方 {@code MaceItem#canSmashAttack(LivingEntity)}：
     * {@code fallDistance > 1.5 && !isFallFlying()}。注意判的是「没在鞘翼滑翔」，
     * 不是「不在地面」——鞘翼滑翔时下落距离可以很大但不允许砸落。
     */
    public static boolean canSmash(LivingEntity attacker) {
        return attacker.fallDistance > SMASH_ATTACK_FALL_THRESHOLD && !attacker.isElytraFlying();
    }

    /**
     * 重锤的砸落伤害加成，按下落距离分三段递增。
     *
     * <p>完全照抄官方 {@code MaceItem#getAttackDamageBonus}：
     * <pre>
     *   d1 = fallDistance
     *   d1 &lt;= 3  ->  4 * d1
     *   d1 &lt;= 8  ->  12 + 2 * (d1 - 3)
     *   否则      ->  22 + (d1 - 8)
     * </pre>
     * 即前 3 格每格 +4，第 4~8 格每格 +2，8 格以上每格 +1。三段在边界处连续
     * （3 格 = 12，8 格 = 22）。
     *
     * <p>官方在这之后还会加上「密役」（Density）附魔的
     * {@code SMASH_DAMAGE_PER_FALLEN_BLOCK * fallDistance}，见
     * {@link #getSmashDamageBonus(float, float)}。
     *
     * @param fallDistance 命中瞬间攻击者的下落距离
     * @return 附加伤害，未达 {@link #SMASH_ATTACK_FALL_THRESHOLD} 时为 0
     */
    public static float getSmashDamageBonus(float fallDistance) {
        return getSmashDamageBonus(fallDistance, 0.0F);
    }

    /**
     * 带「密役」加成的砸落伤害。
     *
     * <p>官方 {@code MaceItem#getAttackDamageBonus} 的完整式子是
     * {@code d2 + EnchantmentHelper.modifyFallBasedDamage(...) * d1}，其中
     * {@code modifyFallBasedDamage} 汇总的是 {@code SMASH_DAMAGE_PER_FALLEN_BLOCK}
     * 效果值；官方 Density 附魔（{@code Enchantments#DENSITY}，最高 5 级）注册的是
     * {@code new AddValue(LevelBasedValue.perLevel(0.5F))}，即每级每格 +0.5。
     *
     * @param fallDistance                下落距离
     * @param smashDamagePerFallenBlock   每格额外伤害（Density 等级 × 0.5）
     */
    public static float getSmashDamageBonus(float fallDistance, float smashDamagePerFallenBlock) {
        if (fallDistance <= SMASH_ATTACK_FALL_THRESHOLD) {
            return 0.0F;
        }

        double bonus;

        if (fallDistance <= 3.0F) {
            bonus = 4.0D * (double) fallDistance;
        } else if (fallDistance <= 8.0F) {
            bonus = 12.0D + 2.0D * ((double) fallDistance - 3.0D);
        } else {
            bonus = 22.0D + (double) fallDistance - 8.0D;
        }

        return (float) (bonus + (double) smashDamagePerFallenBlock * (double) fallDistance);
    }

    /**
     * 官方 {@code MaceItem#getAttackDamageBonus(Entity, float, DamageSource)} 的等价入口：
     * 不满足砸落条件时返回 0。
     *
     * <p>1.16.4 的 {@code Item} 没有这个回调，由
     * {@code PlayerEntity#attackTargetEntityWithCurrentItem} 显式调用。
     */
    public static float getAttackDamageBonus(LivingEntity attacker) {
        if (!canSmash(attacker)) {
            return 0.0F;
        }

        return getSmashDamageBonus(attacker.fallDistance, densityBonusPerFallenBlock(attacker.getHeldItemMainhand()));
    }

    /**
     * 「致密」（Density）每格附加伤害。
     *
     * <p>官方 {@code Enchantments#DENSITY} 注册的是
     * {@code SMASH_DAMAGE_PER_FALLEN_BLOCK = AddValue(perLevel(0.5F))}，即每级每格 +0.5，
     * 最高 5 级。
     */
    private static float densityBonusPerFallenBlock(ItemStack stack) {
        return DensityEnchantment.getSmashDamagePerFallenBlock(stack);
    }

    /**
     * 官方 {@code MaceItem#hurtEnemy} + {@code MaceItem#postHurtEnemy} 的合并实现。
     *
     * <p>官方顺序：{@code hurtEnemy}（砸落表现）→ {@code postHurtEnemy}（清零下落距离）
     * → {@code ItemStack#hurtAndBreak}（掉耐久）。这里保持同样的顺序，因为
     * {@link #applySmashAttack} 依赖还没被清零的 {@code fallDistance}。
     */
    @Override
    public boolean hitEntity(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        boolean smash = canSmash(attacker);

        if (smash) {
            applySmashAttack(target, attacker);
        }

        // 风爆（Wind Burst）。官方 Player#itemAttackInteraction 的三步顺序是：
        //   1) stack.hurtEnemy(...)                                     -> 上面的 applySmashAttack
        //   2) EnchantmentHelper.doPostAttackEffectsWithItemSource(...)  -> POST_ATTACK，风爆在这
        //   3) stack.postHurtEnemy(...)                                  -> 下面那块
        // 风爆的生效条件要读 fallDistance（官方要求 >= 1.5，注意重锤自己的门槛是 > 1.5），
        // 所以必须夹在 1) 和 3) 之间。
        WindBurstEnchantment.applyPostAttack(attacker, stack);

        if (smash) {
            // 官方 MaceItem#postHurtEnemy：attacker.resetFallDistance()。
            //
            // 这条<b>不是</b>「一次下落只能砸一次」—— 清零之后只要还在下落，fallDistance
            // 会继续重新累积，所以下一次砸的加成按「距上次砸击又落了多少」算。
            // 真正被它挡住的是「悬空不落地无限砸满伤害」：不清零的话 fallDistance 只增不减，
            // 空中连点就能每次都吃满加成。用户实测确认了这个问题，所以恢复官方行为。
            // 官方 MaceItem#postHurtEnemy：attacker.resetFallDistance()。
            // 1.16.4 没有那个方法，fallDistance 是公开字段，原版各处也是直接赋 0。
            attacker.fallDistance = 0.0F;

            // 官方 MaceItem#hurtEnemy 里的 ServerPlayer#setIgnoreFallDamageFromCurrentImpulse(true)。
            // 与上面的清零<b>并存</b>，不是二选一：清零只保证「这一跳的摔伤」没了，
            // 而砸击本身会把 Y 轴速度设成 0.01（见 applySmashAttack），之后重新加速下落
            // 攒出的那段距离仍会造成摔伤，要靠这个标记免掉。
            attacker.setIgnoreFallDamageFromSmash(true);
        }

        stack.damageItem(DURABILITY_PER_ATTACK, attacker, (entity) -> {
            entity.sendBreakAnimation(EquipmentSlotType.MAINHAND);
        });
        return true;
    }

    /**
     * 砸落攻击的表现部分，对应官方 {@code MaceItem#hurtEnemy} 的方法体。
     *
     * <p>注意官方对「攻击者」和「被击中者」的用法很不对称，这里逐条对齐：
     * <ul>
     *   <li>竖直速度钉死、音效位置、音效轻重、击退强度 → 看<b>攻击者</b>；</li>
     *   <li>是否播落地音（而不是破空音）、击退波圆心、粒子位置 → 看<b>被击中者</b>。</li>
     * </ul>
     */
    private static void applySmashAttack(LivingEntity target, LivingEntity attacker) {
        World world = attacker.world;

        // 官方：attacker.setDeltaMovement(getDeltaMovement().with(Direction.Axis.Y, 0.01F))。
        // 命中瞬间把竖直速度钉在 0.01，砸完会「顿」一下而不是继续俯冲。
        Vector3d motion = attacker.getMotion();
        attacker.setMotion(motion.x, 0.01D, motion.z);

        if (attacker instanceof ServerPlayerEntity) {
            ServerPlayerEntity serverAttacker = (ServerPlayerEntity) attacker;
            // 官方在这里 send(new ClientboundSetEntityMotionPacket(serverplayer))，
            // 否则客户端预测不到这次速度归零。
            serverAttacker.connection.sendPacket(new SEntityVelocityPacket(serverAttacker));
        }

        if (target.isOnGround()) {
            if (attacker instanceof ServerPlayerEntity) {
                // 官方 ServerPlayer#setSpawnExtraParticlesOnFall(true)：攻击者随后落地时
                // 会多喷一圈方块碎屑，见 ServerPlayerEntity#handleFalling。
                ((ServerPlayerEntity) attacker).setSpawnExtraParticlesOnFall(true);
            }

            boolean heavy = attacker.fallDistance > SMASH_ATTACK_HEAVY_THRESHOLD;
            world.playSound(null, attacker.getPosX(), attacker.getPosY(), attacker.getPosZ(),
                    heavy ? SMASH_GROUND_HEAVY : SMASH_GROUND, attacker.getSoundCategory(),
                    1.0F, heavy ? PITCH_GROUND_HEAVY : PITCH_GROUND);
        } else {
            world.playSound(null, attacker.getPosX(), attacker.getPosY(), attacker.getPosZ(),
                    SMASH_AIR, attacker.getSoundCategory(), 1.0F, PITCH_AIR);
        }

        smashKnockback(world, attacker, target);
    }

    /**
     * 砸落冲击波，对应官方 {@code MaceItem#knockback(Level, Entity, Entity)}。
     *
     * <p>圆心是<b>被击中者</b>，强度看<b>攻击者</b>的下落距离。
     */
    private static void smashKnockback(World world, LivingEntity attacker, LivingEntity target) {
        // 官方：level.levelEvent(2013, target.getOnPos(), 750)
        world.playEvent(null, SMASH_ATTACK_PARTICLE_EVENT, getSmashOnPos(target), SMASH_ATTACK_PARTICLE_DATA);

        List<LivingEntity> victims = world.getEntitiesWithinAABB(LivingEntity.class,
                target.getBoundingBox().grow(SMASH_ATTACK_KNOCKBACK_RADIUS),
                knockbackPredicate(attacker, target));

        for (LivingEntity victim : victims) {
            Vector3d away = victim.getPositionVec().subtract(target.getPositionVec());
            double power = getKnockbackPower(attacker, victim, away);

            if (power > 0.0D) {
                Vector3d push = away.normalize().scale(power);
                // 官方：victim.push(vec.x, 0.7F, vec.z) —— 竖直分量是固定的
                // SMASH_ATTACK_KNOCKBACK_POWER，不随距离衰减。
                victim.addVelocity(push.x, SMASH_ATTACK_KNOCKBACK_POWER, push.z);

                if (victim instanceof ServerPlayerEntity) {
                    ((ServerPlayerEntity) victim).connection.sendPacket(new SEntityVelocityPacket(victim));
                }
            }
        }
    }

    /**
     * 官方 {@code MaceItem#knockbackPredicate(Entity, Entity)}：谁会被冲击波掀飞。
     *
     * <p>排除：旁观者、攻击者与被击中者本人、攻击者的队友、<b>被击中者</b>驯服的宠物
     * （官方就是拿被击中者当主人判的）、marker 盔甲架、圆心距离超过
     * {@link #SMASH_ATTACK_KNOCKBACK_RADIUS} 的、以及创造模式飞行中的玩家。
     */
    private static Predicate<LivingEntity> knockbackPredicate(LivingEntity attacker, LivingEntity target) {
        return (candidate) -> {
            if (candidate.isSpectator()) {
                return false;
            }

            if (candidate == attacker || candidate == target) {
                return false;
            }

            if (attacker.isOnSameTeam(candidate)) {
                return false;
            }

            if (candidate instanceof TameableEntity) {
                TameableEntity pet = (TameableEntity) candidate;

                if (pet.isTamed() && pet.getOwner() == target) {
                    return false;
                }
            }

            if (candidate instanceof ArmorStandEntity && ((ArmorStandEntity) candidate).hasMarker()) {
                return false;
            }

            if (target.getDistanceSq(candidate)
                    > (double) (SMASH_ATTACK_KNOCKBACK_RADIUS * SMASH_ATTACK_KNOCKBACK_RADIUS)) {
                return false;
            }

            if (candidate instanceof PlayerEntity) {
                PlayerEntity player = (PlayerEntity) candidate;

                if (player.isCreative() && player.abilities.isFlying) {
                    return false;
                }
            }

            return true;
        };
    }

    /**
     * 官方 {@code MaceItem#getKnockbackPower(Entity, LivingEntity, Vec3)}：
     * {@code (3.5 - dist) * 0.7 * (attacker.fallDistance > 5 ? 2 : 1) * (1 - 抗击退)}。
     *
     * <p>离圆心越近越强，重砸（下落 > 5 格）翻倍，再乘上目标的抗击退。
     */
    private static double getKnockbackPower(LivingEntity attacker, LivingEntity victim, Vector3d away) {
        return ((double) SMASH_ATTACK_KNOCKBACK_RADIUS - away.length())
                * (double) SMASH_ATTACK_KNOCKBACK_POWER
                * (attacker.fallDistance > SMASH_ATTACK_HEAVY_THRESHOLD ? 2.0D : 1.0D)
                * (1.0D - victim.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE));
    }

    /**
     * 官方 {@code Entity#getOnPos()} 的等价实现，即 {@code getOnPos(1.0E-5F)}：
     * {@code new BlockPos(floor(x), floor(y - 1.0E-5), floor(z))}。
     *
     * <p>不能直接用 1.16.4 的 {@code Entity#getOnPosition()}：那个用的是 0.2F 偏移
     * （官方叫 {@code getOnPosLegacy()}），站在方块上时会取到脚下往下第二格，粒子会用错方块材质；
     * 而且它是 protected，本类也调不到。
     */
    private static BlockPos getSmashOnPos(Entity entity) {
        return new BlockPos(MathHelper.floor(entity.getPosX()),
                MathHelper.floor(entity.getPosY() - 1.0E-5D),
                MathHelper.floor(entity.getPosZ()));
    }

    /**
     * 官方 {@code MaceItem#createToolProperties()} 的 damagePerBlock = 2。
     */
    @Override
    public boolean onBlockDestroyed(ItemStack stack, World worldIn, BlockState state, BlockPos pos,
            LivingEntity entityLiving) {
        if (state.getBlockHardness(worldIn, pos) != 0.0F) {
            stack.damageItem(DURABILITY_PER_BLOCK, entityLiving, (entity) -> {
                entity.sendBreakAnimation(EquipmentSlotType.MAINHAND);
            });
        }

        return true;
    }

    /**
     * 与剑一致：创造模式下持有时不因左键而破坏方块，避免误操作。
     */
    @Override
    public boolean canPlayerBreakBlockWhileHolding(BlockState state, World worldIn, BlockPos pos, PlayerEntity player) {
        return false;
    }

    /** 重锤不是挖掘工具：官方 tool 组件的 rules 为空，defaultMiningSpeed = 1.0F。 */
    @Override
    public float getDestroySpeed(ItemStack stack, BlockState state) {
        return MINING_SPEED;
    }
}
