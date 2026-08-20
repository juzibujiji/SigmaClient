package net.minecraft.world;

import java.util.List;
import java.util.Set;

import com.google.common.collect.Sets;

import net.minecraft.block.AbstractButtonBlock;
import net.minecraft.block.BellBlock;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.DoorBlock;
import net.minecraft.block.FenceGateBlock;
import net.minecraft.block.LeverBlock;
import net.minecraft.block.TrapDoorBlock;
import net.minecraft.block.material.Material;
import net.minecraft.enchantment.ProtectionEnchantment;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.fluid.Fluids;
import net.minecraft.network.play.server.SEntityVelocityPacket;
import net.minecraft.particles.ParticleTypes;
import net.minecraft.state.properties.DoubleBlockHalf;
import net.minecraft.util.Direction;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.SoundEvent;
import net.minecraft.util.SoundEvents;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.vector.Vector3d;
import net.minecraft.world.server.ServerWorld;

/**
 * 「风爆」型爆炸 —— 不造成伤害、只有击退与方块触发的爆炸。
 *
 * <p>官方走的是数据驱动的
 * {@code Level#explode(..., SimpleExplosionDamageCalculator, ..., ExplosionInteraction.TRIGGER, ...)}：
 * <pre>
 * new SimpleExplosionDamageCalculator(
 *     true,                 // explodesBlocks  = blockInteraction != NONE
 *     false,                // damagesEntities = damageType.isPresent()，风爆没有 damageType → 不掉血
 *     Optional.of(k),       // knockbackMultiplier
 *     BLOCKS_WIND_CHARGE_EXPLOSIONS)
 * </pre>
 * 1.16.4 的 {@link Explosion} 是「先算伤害、再由伤害推击退」的一体式实现，
 * 没有 {@code shouldDamageEntity} / {@code getKnockbackMultiplier} 这两个钩子，
 * 所以这里把官方 {@code world/level/ServerExplosion.java} 的
 * {@code hurtEntities}（第 172-210 行）、{@code calculateExplodedPositions}、
 * {@code interactWithBlocks} 按风爆的参数重写一遍，参数化半径与击退倍率。
 *
 * <p>用处有两个：{@code WindChargeEntity}（旋风弹，半径 1.2 / 倍率 1.22）和
 * {@link net.minecraft.enchantment.WindBurstEnchantment}（风爆附魔，半径按等级查表 / 倍率 3.5）。
 * 目前 {@code WindChargeEntity} 里还留着它自己那份等价实现（本类是从那里提取参数化而来的），
 * 后续可以让它改调本类去重 —— 行为一致，本次没有动它，以免和正在进行的移植工作冲突。
 */
public final class WindExplosion
{
    /**
     * 官方 {@code SimpleExplosionDamageCalculator#getBlockExplosionResistance} 在
     * {@code immuneBlocks} 命中时返回的阻挡值。
     */
    private static final float IMMUNE_BLOCK_RESISTANCE = 3600000.0F;

    /**
     * 官方 {@code SoundEvents.WIND_CHARGE_BURST}（{@code entity.wind_charge.wind_burst}）。
     * 1.16.4 完全没有风类音效，用龙火球的爆裂声近似 —— 与本项目
     * {@code WindChargeEntity.WIND_CHARGE_BURST} 保持同一个替代音效。
     */
    private static final SoundEvent WIND_CHARGE_BURST = SoundEvents.ENTITY_DRAGON_FIREBALL_EXPLODE;

    private WindExplosion()
    {
    }

    /**
     * 触发一次风爆。只在服务端有意义（官方 {@code ServerExplosion} 同理）。
     *
     * @param world               世界
     * @param source              爆炸源实体，会被排除在受击列表外（官方 {@code this.source}）；可以为 null
     * @param center              爆心（官方 {@code this.center}）
     * @param radius              官方 {@code this.radius}
     * @param knockbackMultiplier 官方 {@code damageCalculator.getKnockbackMultiplier}
     */
    public static void explode(World world, Entity source, Vector3d center, float radius, float knockbackMultiplier)
    {
        if (world.isRemote)
        {
            return;
        }

        pushEntities(world, source, center, radius, knockbackMultiplier);
        triggerBlocks(world, center, radius);
        spawnBurstEffects(world, center, radius);
    }

    /**
     * 官方 {@code ServerExplosion#hurtEntities}（第 172-210 行）在 {@code shouldDamageEntity == false}
     * 时退化成的样子：
     * <pre>
     * float f  = this.radius * 2.0F;                                  // 注意是两倍半径
     * double d0 = sqrt(entity.distanceToSqr(center)) / f;
     * if (d0 &lt;= 1.0) {
     *     Vec3 vec31 = entity.getEyePosition().subtract(center).normalize();
     *     boolean flag = shouldDamageEntity(...);                     // 风爆恒 false
     *     float f1 = getKnockbackMultiplier(entity);
     *     float f2 = !flag &amp;&amp; f1 == 0.0F ? 0.0F : getSeenPercent(center, entity);
     *     double d1 = EXPLOSION_KNOCKBACK_RESISTANCE 属性;
     *     double d2 = (1.0 - d0) * f2 * f1 * (1.0 - d1);
     *     entity.push(vec31.scale(d2));
     * }
     * </pre>
     *
     * <p>两处等价替换：
     * <ul>
     *   <li>{@code getSeenPercent} → 1.16.4 的 {@link Explosion#getBlockDensity}（实现逐行相同）；</li>
     *   <li>{@code Attributes.EXPLOSION_KNOCKBACK_RESISTANCE}（1.20.5 才有，由爆炸保护附魔提供）
     *       → 1.16.4 由附魔直接算：{@link ProtectionEnchantment#getBlastDamageReduction}。
     *       它对传入值是线性缩放，所以先缩放 {@code (1 - d0) * f2} 再乘 {@code f1}，
     *       与官方 {@code (1-d0) * f2 * f1 * (1-d1)} 的结果一致。</li>
     * </ul>
     */
    private static void pushEntities(World world, Entity source, Vector3d center, float radius, float knockbackMultiplier)
    {
        float f = radius * 2.0F;
        int i = MathHelper.floor(center.x - (double)f - 1.0D);
        int j = MathHelper.floor(center.x + (double)f + 1.0D);
        int k = MathHelper.floor(center.y - (double)f - 1.0D);
        int l = MathHelper.floor(center.y + (double)f + 1.0D);
        int i1 = MathHelper.floor(center.z - (double)f - 1.0D);
        int j1 = MathHelper.floor(center.z + (double)f + 1.0D);
        List<Entity> list = world.getEntitiesWithinAABBExcludingEntity(source,
                new AxisAlignedBB((double)i, (double)k, (double)i1, (double)j, (double)l, (double)j1));

        for (Entity entity : list)
        {
            if (entity.isImmuneToExplosions())
            {
                continue;
            }

            double d0 = (double)MathHelper.sqrt(entity.getDistanceSq(center)) / (double)f;

            if (d0 > 1.0D)
            {
                continue;
            }

            double d3 = entity.getPosX() - center.x;
            double d4 = entity.getPosYEye() - center.y;
            double d5 = entity.getPosZ() - center.z;
            double d6 = (double)MathHelper.sqrt(d3 * d3 + d4 * d4 + d5 * d5);

            if (d6 == 0.0D)
            {
                continue;
            }

            d3 /= d6;
            d4 /= d6;
            d5 /= d6;
            float f1 = getKnockbackMultiplier(entity, knockbackMultiplier);
            // 官方：float f2 = !flag && f1 == 0.0F ? 0.0F : getSeenPercent(center, entity);
            // flag（shouldDamageEntity）对风爆恒为 false。
            float f2 = f1 == 0.0F ? 0.0F : Explosion.getBlockDensity(center, entity);
            double d7 = (1.0D - d0) * (double)f2;

            if (entity instanceof LivingEntity)
            {
                // 1.16.4 里爆炸保护的击退削减就在这一步（原版 Explosion 第 231 行同样写法）。
                d7 = ProtectionEnchantment.getBlastDamageReduction((LivingEntity)entity, d7);
            }

            d7 *= (double)f1;
            // 官方 entity.push(vec32)。1.16.4 的 addVelocity 就是
            // setMotion(getMotion().add(...)) + isAirBorne = true。
            entity.addVelocity(d3 * d7, d4 * d7, d5 * d7);

            // 官方把被推的玩家记进 hitPlayers，由 ClientboundExplodePacket 带给本人的客户端。
            // 1.16.4 的 SExplosionPacket 不能复用（客户端会跑 Explosion#doExplosionB，
            // 播原版爆炸音 + TNT 烟雾）；SEntityVelocityPacket 传的是同一份信息且没有副作用。
            if (entity instanceof ServerPlayerEntity)
            {
                ServerPlayerEntity serverplayerentity = (ServerPlayerEntity)entity;

                if (!serverplayerentity.isSpectator())
                {
                    serverplayerentity.connection.sendPacket(new SEntityVelocityPacket(serverplayerentity));
                }
            }
        }
    }

    /**
     * 官方 {@code SimpleExplosionDamageCalculator#getKnockbackMultiplier}（第 44-48 行）：
     * <pre>
     * boolean flag = entity instanceof Player player &amp;&amp; player.getAbilities().flying;
     * return flag ? 0.0F : knockbackMultiplier.orElseGet(...);
     * </pre>
     */
    private static float getKnockbackMultiplier(Entity entity, float knockbackMultiplier)
    {
        if (entity instanceof PlayerEntity && ((PlayerEntity)entity).abilities.isFlying)
        {
            return 0.0F;
        }

        return knockbackMultiplier;
    }

    /**
     * 官方 {@code ServerExplosion#interactWithBlocks} 配
     * {@code Explosion.BlockInteraction.TRIGGER_BLOCK}：默认的
     * {@code BlockBehaviour#onExplosionHit} 对 TRIGGER_BLOCK 什么都不做，
     * 只有覆写了它的那几个方块会反应。1.21.11 里是 ButtonBlock、LeverBlock、DoorBlock、
     * TrapDoorBlock、FenceGateBlock、BellBlock、AbstractCandleBlock、BeehiveBlock、
     * CreakingHeartBlock —— 后三个 1.16.4 没有对应物（蜡烛 / 幽匿魔）或不值得移植（蜂箱是激怒蜜蜂）。
     *
     * <p>这里不去给六个共享方块类加 {@code onExplosionHit} 覆写，而是走它们已有的公开 API，
     * 状态变化与音效与官方一致。逻辑与本项目 {@code WindChargeEntity#triggerBlocks} 相同。
     */
    private static void triggerBlocks(World world, Vector3d center, float radius)
    {
        for (BlockPos blockpos : calculateExplodedPositions(world, center, radius))
        {
            BlockState blockstate = world.getBlockState(blockpos);

            if (blockstate.getBlock() instanceof AbstractButtonBlock)
            {
                // 官方 ButtonBlock#onExplosionHit：if (!state.getValue(POWERED)) this.press(...)
                if (!blockstate.get(AbstractButtonBlock.POWERED))
                {
                    ((AbstractButtonBlock)blockstate.getBlock()).powerBlock(blockstate, world, blockpos);
                }
            }
            else if (blockstate.getBlock() instanceof LeverBlock)
            {
                // 官方 LeverBlock#onExplosionHit：this.pull(...)，无条件
                ((LeverBlock)blockstate.getBlock()).setPowered(blockstate, world, blockpos);
            }
            else if (blockstate.getBlock() instanceof DoorBlock)
            {
                DoorBlock doorblock = (DoorBlock)blockstate.getBlock();

                // 官方 DoorBlock#onExplosionHit：HALF == LOWER && type.canOpenByWindCharge() && !POWERED
                // canOpenByWindCharge() 对铁门是 false，1.16.4 用材质判铁门。
                if (blockstate.get(DoorBlock.HALF) == DoubleBlockHalf.LOWER
                        && blockstate.getMaterial() != Material.IRON
                        && !blockstate.get(DoorBlock.POWERED))
                {
                    doorblock.openDoor(world, blockstate, blockpos, !doorblock.isOpen(blockstate));
                }
            }
            else if (blockstate.getBlock() instanceof TrapDoorBlock)
            {
                // 官方 TrapDoorBlock#onExplosionHit：type.canOpenByWindCharge() && !POWERED，然后 toggle()
                if (blockstate.getMaterial() != Material.IRON && !blockstate.get(TrapDoorBlock.POWERED))
                {
                    BlockState blockstate1 = blockstate.func_235896_a_(TrapDoorBlock.OPEN);
                    world.setBlockState(blockpos, blockstate1, 2);

                    if (blockstate1.get(TrapDoorBlock.WATERLOGGED))
                    {
                        world.getPendingFluidTicks().scheduleTick(blockpos, Fluids.WATER, Fluids.WATER.getTickRate(world));
                    }

                    // 官方 toggle() 会播开合音；1.16.4 木活板门用世界事件 1007/1013。
                    world.playEvent(null, blockstate1.get(TrapDoorBlock.OPEN) ? 1007 : 1013, blockpos, 0);
                }
            }
            else if (blockstate.getBlock() instanceof FenceGateBlock)
            {
                // 官方 FenceGateBlock#onExplosionHit：!POWERED 时翻转 OPEN 并播门音。
                if (!blockstate.get(FenceGateBlock.POWERED))
                {
                    boolean flag = blockstate.get(FenceGateBlock.OPEN);
                    world.setBlockState(blockpos, blockstate.with(FenceGateBlock.OPEN, Boolean.valueOf(!flag)), 10);
                    world.playEvent(null, flag ? 1014 : 1008, blockpos, 0);
                }
            }
            else if (blockstate.getBlock() instanceof BellBlock)
            {
                // 官方 BellBlock#onExplosionHit：this.attemptToRing(level, pos, null)
                // 传 null 方向时 1.16.4 的 BellBlock#ring 会用钟自己的朝向。
                ((BellBlock)blockstate.getBlock()).ring(world, blockpos, (Direction)null);
            }
        }
    }

    /**
     * 官方 {@code ServerExplosion#calculateExplodedPositions} 的射线循环（与 1.16.4
     * {@code Explosion#doExplosionA} 的那段完全一致），代入风爆的 damage calculator：
     * <ul>
     *   <li>{@code getBlockExplosionResistance} 只对 {@code BLOCKS_WIND_CHARGE_EXPLOSIONS} 里的方块
     *       返回 3600000.0F，其余返回 {@code Optional.empty()}，所以风爆能直接穿过普通墙体；</li>
     *   <li>{@code shouldBlockExplode} 恒 true。</li>
     * </ul>
     *
     * <p><b>与官方的偏差</b>：{@code BLOCKS_WIND_CHARGE_EXPLOSIONS} 这个方块标签 1.16.4 没有，
     * 这里按本项目 {@code WindChargeEntity} 的同一处理，只把屏障与基岩当作免疫方块。
     */
    private static Set<BlockPos> calculateExplodedPositions(World world, Vector3d center, float radius)
    {
        Set<BlockPos> set = Sets.newHashSet();

        for (int j = 0; j < 16; ++j)
        {
            for (int k = 0; k < 16; ++k)
            {
                for (int l = 0; l < 16; ++l)
                {
                    if (j == 0 || j == 15 || k == 0 || k == 15 || l == 0 || l == 15)
                    {
                        double d0 = (double)((float)j / 15.0F * 2.0F - 1.0F);
                        double d1 = (double)((float)k / 15.0F * 2.0F - 1.0F);
                        double d2 = (double)((float)l / 15.0F * 2.0F - 1.0F);
                        double d3 = Math.sqrt(d0 * d0 + d1 * d1 + d2 * d2);
                        d0 /= d3;
                        d1 /= d3;
                        d2 /= d3;
                        float f = radius * (0.7F + world.rand.nextFloat() * 0.6F);
                        double d4 = center.x;
                        double d6 = center.y;
                        double d8 = center.z;

                        for (; f > 0.0F; f -= 0.22500001F)
                        {
                            BlockPos blockpos = new BlockPos(d4, d6, d8);
                            BlockState blockstate = world.getBlockState(blockpos);

                            if (blockstate.getBlock() == Blocks.BARRIER || blockstate.getBlock() == Blocks.BEDROCK)
                            {
                                f -= (IMMUNE_BLOCK_RESISTANCE + 0.3F) * 0.3F;
                            }

                            if (f > 0.0F)
                            {
                                set.add(blockpos);
                            }

                            d4 += d0 * (double)0.3F;
                            d6 += d1 * (double)0.3F;
                            d8 += d2 * (double)0.3F;
                        }
                    }
                }
            }
        }

        return set;
    }

    /**
     * 官方 {@code Level#explode} 会喷 {@code GUST_EMITTER_SMALL} / {@code GUST_EMITTER_LARGE}
     * 两种粒子并播 {@code WIND_CHARGE_BURST}。这两个粒子 1.20.5 才有，1.16.4 用 CLOUD 近似；
     * 粒子数量与散布是为「半径 radius 的气爆」调的，<b>不是官方数值</b>
     * （官方是单个 emitter 粒子，由粒子自己生成整团气浪）。
     */
    private static void spawnBurstEffects(World world, Vector3d center, float radius)
    {
        world.playSound(null, center.x, center.y, center.z, WIND_CHARGE_BURST, SoundCategory.BLOCKS,
                1.2F, 0.9F + world.rand.nextFloat() * 0.2F);

        if (world instanceof ServerWorld)
        {
            ServerWorld serverworld = (ServerWorld)world;
            serverworld.spawnParticle(ParticleTypes.CLOUD, center.x, center.y, center.z,
                    (int)(10.0F * radius), (double)radius * 0.5D, (double)radius * 0.5D, (double)radius * 0.5D, 0.05D);
        }
    }
}
