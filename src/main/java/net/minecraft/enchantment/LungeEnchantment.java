package net.minecraft.enchantment;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.inventory.EquipmentSlotType;
import net.minecraft.item.ItemStack;
import net.minecraft.network.play.server.SEntityVelocityPacket;
import net.minecraft.util.SoundEvent;
import net.minecraft.util.SoundEvents;
import net.minecraft.util.math.vector.Vector3d;

/**
 * 突进（Lunge，1.21.11 加入）。长矛穿刺攻击后沿视线方向把自己往前弹一段。
 *
 * <p>官方定义在 {@code world/item/enchantment/Enchantments.java} 第 977-1019 行：
 * <pre>
 * register(p_343249_, LUNGE,
 *     Enchantment.enchantment(
 *         Enchantment.definition(
 *             holdergetter2.getOrThrow(ItemTags.LUNGE_ENCHANTABLE),  // = ItemTags.SPEARS
 *             5,                                                    // weight
 *             3,                                                    // maxLevel
 *             Enchantment.dynamicCost(5, 8),                        // minCost
 *             Enchantment.dynamicCost(25, 8),                       // maxCost
 *             2,                                                    // anvilCost
 *             EquipmentSlotGroup.HAND))                             // ← 双手都算，不是只主手
 *     .withEffect(
 *         EnchantmentEffectComponents.POST_PIERCING_ATTACK,
 *         AllOf.entityEffects(
 *             new ChangeItemDamage(new LevelBasedValue.Constant(1.0F)),
 *             new ApplyExhaustion(LevelBasedValue.perLevel(4.0F)),
 *             new ApplyEntityImpulse(new Vec3(0.0, 0.0, 1.0), new Vec3(1.0, 0.0, 1.0),
 *                                    LevelBasedValue.perLevel(0.458F)),
 *             new PlaySoundEffect(List.of(SoundEvents.LUNGE_1, SoundEvents.LUNGE_2, SoundEvents.LUNGE_3),
 *                                 ConstantFloat.of(1.0F), ConstantFloat.of(1.0F))),
 *         AllOfCondition.allOf(
 *             InvertedLootItemCondition.invert(LootItemEntityPropertyCondition.hasProperties(
 *                 THIS, EntityPredicate.Builder.entity().vehicle(EntityPredicate.Builder.entity()))),
 *             LootItemEntityPropertyCondition.hasProperties(
 *                 THIS, EntityPredicate.Builder.entity().flags(flags().setIsFallFlying(false))),
 *             LootItemEntityPropertyCondition.hasProperties(
 *                 THIS, EntityPredicate.Builder.entity().flags(flags().setIsInWater(false)))));
 * </pre>
 *
 * <p>weight 5 → 1.16.4 的 {@link Enchantment.Rarity#UNCOMMON}；官方 anvilCost=2 由
 * {@code RepairContainer} 的 Rarity 表自动得到（UNCOMMON → 2）。
 *
 * <p><b>调用时机</b>见 {@link net.minecraft.item.SpearItem#piercingAttack}：
 * 官方 {@code PiercingWeapon.attack}（{@code world/item/component/PiercingWeapon.java} 第 89-108 行）
 * 在穿刺循环之后、命中音之前调 {@code lungeForwardMaybe()}，<b>不管有没有命中都调</b>。
 * {@code LivingEntity.lungeForwardMaybe()}（{@code LivingEntity.java:1647}）只在服务端跑，
 * 转给 {@code EnchantmentHelper.doLungeEffects}（{@code EnchantmentHelper.java:210}），
 * 后者只看<b>主手</b>物品（{@code getWeaponItem()} → {@code getMainHandItem()}）上的附魔。
 * {@code Player} 还覆写了一层饱食度门槛（{@code Player.java:1578}）。
 */
public class LungeEnchantment extends Enchantment
{
    /**
     * 官方 {@code ApplyEntityImpulse} 的 {@code magnitude = LevelBasedValue.perLevel(0.458F)}。
     * {@code perLevel(f)} = {@code Linear(base=f, perLevelAboveFirst=f)}，所以是 {@code 0.458 * level}。
     */
    public static final float IMPULSE_PER_LEVEL = 0.458F;

    /** 官方 {@code ApplyExhaustion(LevelBasedValue.perLevel(4.0F))}：{@code 4.0 * level} 点消耗度。 */
    public static final float EXHAUSTION_PER_LEVEL = 4.0F;

    /** 官方 {@code ChangeItemDamage(new LevelBasedValue.Constant(1.0F))}：额外掉 1 点耐久，不随等级变。 */
    public static final int EXTRA_ITEM_DAMAGE = 1;

    /**
     * 官方 {@code Player#hasEnoughFoodToDoExhaustiveManoeuvres} 用的
     * {@code FoodData#hasEnoughFood()}（{@code world/food/FoodData.java:92}）：
     * {@code getFoodLevel() > 6.0F}。
     */
    private static final int MIN_FOOD_LEVEL = 6;

    /** 官方 {@code Enchantment.dynamicCost(5, 8)} 的 base。 */
    private static final int MIN_COST_BASE = 5;

    /** 官方 {@code Enchantment.dynamicCost(25, 8)} 的 base。 */
    private static final int MAX_COST_BASE = 25;

    /** 官方两个 {@code dynamicCost} 共用的 perLevelAboveFirst。 */
    private static final int COST_PER_LEVEL_ABOVE_FIRST = 8;

    /** 官方 definition 的 maxLevel。 */
    private static final int MAX_LEVEL = 3;

    /**
     * 官方 {@code PlaySoundEffect(List.of(SoundEvents.LUNGE_1, LUNGE_2, LUNGE_3), 1.0F, 1.0F)}。
     *
     * <p>{@code item.lunge.1/2/3} 这三个音效 1.16.4 <b>不存在</b>。这里用三叉戟激流的
     * {@code item.trident.riptide_1/2/3} 代替：它同样是「三选一的向前冲刺音」，是原版里语义最接近的一组，
     * 而且数量也刚好是 3，能一一对应官方的随机三选一。
     */
    private static final SoundEvent[] LUNGE_SOUNDS = new SoundEvent[] {
        SoundEvents.ITEM_TRIDENT_RIPTIDE_1,
        SoundEvents.ITEM_TRIDENT_RIPTIDE_2,
        SoundEvents.ITEM_TRIDENT_RIPTIDE_3
    };

    public LungeEnchantment(Enchantment.Rarity rarityIn, EquipmentSlotType... slots)
    {
        super(rarityIn, EnchantmentType.SPEAR, slots);
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
     * 官方 {@code Player#lungeForwardMaybe} 的饱食度门槛（{@code Player.java:1578-1587}）：
     * {@code getFoodData().hasEnoughFood() || getAbilities().mayfly}。
     * 非玩家生物没有这层门槛（{@code LivingEntity#lungeForwardMaybe} 直接跑）。
     */
    private static boolean hasEnoughFoodToDoExhaustiveManoeuvres(LivingEntity user)
    {
        if (!(user instanceof PlayerEntity))
        {
            return true;
        }

        PlayerEntity player = (PlayerEntity)user;
        // 官方 mayfly → 1.16.4 PlayerAbilities.allowFlying。
        return player.getFoodStats().getFoodLevel() > MIN_FOOD_LEVEL || player.abilities.allowFlying;
    }

    /**
     * 官方生效条件 {@code AllOfCondition.allOf(...)}，三条都是对 {@code THIS}（受作用者 = 攻击者）判：
     * <ol>
     *   <li>{@code invert(hasProperties(THIS, entity().vehicle(entity())))} —— 取反后是
     *       「<b>没有</b>坐骑」，1.16.4 是 {@code !isPassenger()}；</li>
     *   <li>{@code flags().setIsFallFlying(false)} —— 没在鞘翅滑翔，1.16.4 是 {@code !isElytraFlying()}；</li>
     *   <li>{@code flags().setIsInWater(false)} —— 不在水里，1.16.4 是 {@code !isInWater()}。</li>
     * </ol>
     */
    public static boolean conditionsMet(LivingEntity user)
    {
        return !user.isPassenger() && !user.isElytraFlying() && !user.isInWater();
    }

    /**
     * 官方 {@code LivingEntity#lungeForwardMaybe} → {@code EnchantmentHelper.doLungeEffects}
     * → {@code Enchantment#doLunge} 的整条链，由 {@link net.minecraft.item.SpearItem#piercingAttack}
     * 在穿刺循环之后调用。
     *
     * <p>四个效果按官方 {@code AllOf.entityEffects} 的顺序执行：耐久 → 消耗度 → 冲量 → 音效。
     *
     * @param user  攻击者
     * @param stack 攻击者主手的长矛（官方 {@code getWeaponItem()}）
     * @return 是否真的突进了
     */
    public static boolean lungeForwardMaybe(LivingEntity user, ItemStack stack)
    {
        if (user.world.isRemote)
        {
            // 官方 lungeForwardMaybe 只在 ServerLevel 上跑；客户端那边
            // MultiPlayerGameMode 也调它，但同样被这层判断挡掉。
            return false;
        }

        if (!hasEnoughFoodToDoExhaustiveManoeuvres(user))
        {
            return false;
        }

        int level = EnchantmentHelper.getEnchantmentLevel(Enchantments.LUNGE, stack);

        if (level <= 0 || !conditionsMet(user))
        {
            return false;
        }

        // 1) 官方 ChangeItemDamage(Constant(1.0F))
        stack.damageItem(EXTRA_ITEM_DAMAGE, user, (entity) ->
        {
            entity.sendBreakAnimation(EquipmentSlotType.MAINHAND);
        });

        // 2) 官方 ApplyExhaustion(perLevel(4.0F))，只对玩家有意义
        //    （官方 ApplyExhaustion#apply 里就是 `if (entity instanceof Player player)`）。
        if (user instanceof PlayerEntity)
        {
            ((PlayerEntity)user).addExhaustion(EXHAUSTION_PER_LEVEL * (float)level);
        }

        // 3) 官方 ApplyEntityImpulse
        applyImpulse(user, level);

        // 4) 官方 PlaySoundEffect(三选一, volume 1.0, pitch 1.0)
        SoundEvent sound = LUNGE_SOUNDS[user.world.rand.nextInt(LUNGE_SOUNDS.length)];
        user.world.playSound(null, user.getPosX(), user.getPosY(), user.getPosZ(),
                sound, user.getSoundCategory(), 1.0F, 1.0F);
        return true;
    }

    /**
     * 官方 {@code effects/ApplyEntityImpulse#apply}（第 24-34 行）：
     * <pre>
     * Vec3 vec3  = entity.getLookAngle();
     * Vec3 vec31 = vec3.addLocalCoordinates(this.direction)   // direction = (0, 0, 1)
     *                  .multiply(this.coordinateScale)        // coordinateScale = (1, 0, 1)
     *                  .scale(this.magnitude.calculate(level));
     * entity.addDeltaMovement(vec31);
     * entity.hurtMarked = true;
     * entity.needsSync  = true;
     * if (entity instanceof Player player) player.applyPostImpulseGraceTime(10);
     * </pre>
     *
     * <p><b>{@code addLocalCoordinates} 到底算了什么</b>：它不是「加」，方法体是
     * {@code applyLocalCoordinatesToRotation(this.rotation(), p)}（{@code world/phys/Vec3.java}），
     * 即把局部坐标 {@code p} 变换到本向量朝向所定义的坐标系里。局部 {@code (0, 0, 1)} 就是该坐标系的
     * 「正前方」，所以结果<b>等于 {@code getLookAngle()} 本身</b>（视线向量已是单位向量）。
     * 接着 {@code multiply((1, 0, 1))} 把 Y 抹成 0，最后乘 {@code 0.458 * level}。
     *
     * <p>所以实际式子是：
     * <pre>
     * Δmotion = (look.x, 0, look.z) * 0.458 * level
     * </pre>
     * 水平冲量的模长是 {@code 0.458 * level * cos(pitch)} —— 抬头或俯身看时会变短，
     * 平视时最长（1 级 0.458、2 级 0.916、3 级 1.374 格/tick）。竖直方向永远是 0，
     * 突进不会把人抬起来。
     */
    private static void applyImpulse(LivingEntity user, int level)
    {
        // 官方 getLookAngle() → 1.16.4 Entity#getLookVec()（= getLook(1.0F)）。
        Vector3d look = user.getLookVec();
        double magnitude = (double)IMPULSE_PER_LEVEL * (double)level;
        double dx = look.x * magnitude;
        double dz = look.z * magnitude;

        // 官方 addDeltaMovement → 1.16.4 setMotion(getMotion().add(...))。
        user.setMotion(user.getMotion().add(dx, 0.0D, dz));
        // 官方 hurtMarked → 1.16.4 velocityChanged（同一个「本 tick 速度被外力改过，需要同步」标记）。
        // 官方的 needsSync 与 Player#applyPostImpulseGraceTime(10) 在 1.16.4 没有对应物，见报告。
        user.velocityChanged = true;

        if (user instanceof ServerPlayerEntity)
        {
            // velocityChanged 只让实体追踪器把速度发给「别人」的客户端；玩家本人不追踪自己，
            // 得显式补一个包，否则本地预测不到这次突进。与 MaceItem 里砸落击退的处理一致。
            ServerPlayerEntity serverplayerentity = (ServerPlayerEntity)user;
            serverplayerentity.connection.sendPacket(new SEntityVelocityPacket(serverplayerentity));
        }
    }
}
