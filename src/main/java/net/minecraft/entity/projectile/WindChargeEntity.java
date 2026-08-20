package net.minecraft.entity.projectile;

import com.google.common.collect.Sets;
import java.util.List;
import java.util.Set;
import net.minecraft.block.AbstractButtonBlock;
import net.minecraft.block.BellBlock;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.DoorBlock;
import net.minecraft.block.FenceGateBlock;
import net.minecraft.block.LeverBlock;
import net.minecraft.block.TrapDoorBlock;
import net.minecraft.block.material.Material;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
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
import net.minecraft.world.Explosion;
import net.minecraft.world.World;
import net.minecraft.world.server.ServerWorld;

/**
 * Backport of the 1.20.5 wind charge projectile.
 *
 * Official source: net/minecraft/world/entity/projectile/hurtingprojectile/windcharge/WindCharge.java
 * (1.21.11 / MCP-Reborn-release).
 *
 * Every number below comes from the official sources:
 *
 * WindCharge.java:
 *   private static final float RADIUS = 1.2F;
 *   private static final float MIN_CAMERA_DISTANCE_SQUARED = Mth.square(3.5F);
 *   private int noDeflectTicks = 5;
 *   explode() -> level.explode(this, null, EXPLOSION_DAMAGE_CALCULATOR, x, y, z, 1.2F, false,
 *                              ExplosionInteraction.TRIGGER,
 *                              GUST_EMITTER_SMALL, GUST_EMITTER_LARGE, WeightedList.of(), WIND_CHARGE_BURST);
 *   EXPLOSION_DAMAGE_CALCULATOR = new SimpleExplosionDamageCalculator(
 *       true,               // explodesBlocks   (shouldBlockExplode)
 *       false,              // damagesEntities  -> a wind burst deals NO explosion damage
 *       Optional.of(1.22F), // knockbackMultiplier
 *       BLOCKS_WIND_CHARGE_EXPLOSIONS)
 *
 * ExplosionInteraction.TRIGGER maps to Explosion.BlockInteraction.TRIGGER_BLOCK, and the default
 * BlockBehaviour#onExplosionHit does nothing for TRIGGER_BLOCK - so a wind burst never breaks or drops
 * blocks, it only "triggers" the handful of blocks that override onExplosionHit.
 */
public class WindChargeEntity extends AbstractWindChargeEntity
{
    /** Official WindCharge.RADIUS. */
    private static final float RADIUS = 1.2F;
    /** Official SimpleExplosionDamageCalculator knockbackMultiplier for the wind charge. */
    private static final float KNOCKBACK_MULTIPLIER = 1.22F;
    /** Official WindCharge.MIN_CAMERA_DISTANCE_SQUARED = Mth.square(3.5F). */
    private static final double MIN_CAMERA_DISTANCE_SQUARED = 3.5D * 3.5D;
    /**
     * Official BlockTags.BLOCKS_WIND_CHARGE_EXPLOSIONS. VanillaBlockTagsProvider defines it as exactly
     * {barrier, bedrock}, so it is inlined here instead of adding tag infrastructure.
     */
    private static final float IMMUNE_BLOCK_RESISTANCE = 3600000.0F;

    /**
     * 1.16.4 has no "entity.wind_charge.wind_burst" sound (added in 1.20.5) and this project ships no extra
     * sound assets. The dragon fireball's airy dispersal is the closest stock match.
     * Official sound: SoundEvents.WIND_CHARGE_BURST.
     */
    private static final SoundEvent WIND_CHARGE_BURST = SoundEvents.ENTITY_DRAGON_FIREBALL_EXPLODE;

    public WindChargeEntity(EntityType <? extends AbstractWindChargeEntity > typeIn, World worldIn)
    {
        super(typeIn, worldIn);
    }

    /** Official WindCharge(Player, Level, double, double, double). */
    public WindChargeEntity(PlayerEntity thrower, World worldIn, double x, double y, double z)
    {
        super(EntityType.WIND_CHARGE, worldIn, thrower, x, y, z);
    }

    /** Official WindCharge(Level, double, double, double, Vec3) - used by dispensers. */
    public WindChargeEntity(World worldIn, double x, double y, double z, Vector3d motion)
    {
        super(EntityType.WIND_CHARGE, worldIn, x, y, z, motion);
    }

    /**
     * Official WindCharge#shouldRenderAtSqrDistance: hides the projectile for its first 2 ticks when the
     * camera is closer than 3.5 blocks, so a thrown wind charge does not flash in the thrower's face.
     */
    public boolean isInRangeToRenderDist(double distance)
    {
        return this.ticksExisted < 2 && distance < MIN_CAMERA_DISTANCE_SQUARED ? false : super.isInRangeToRenderDist(distance);
    }

    /**
     * Official WindCharge#explode(Vec3). 1.16.4's Explosion class always damages entities and derives
     * knockback from that damage, and it has no ExplosionDamageCalculator#getKnockbackMultiplier /
     * #shouldDamageEntity hooks, so the burst is reimplemented here as a faithful port of
     * ServerExplosion#hurtEntities + ServerExplosion#interactWithBlocks with the wind charge's calculator
     * values (no damage, knockback x1.22, radius 1.2, TRIGGER_BLOCK).
     */
    protected void explode(Vector3d center)
    {
        if (this.world.isRemote)
        {
            return;
        }

        this.hurtEntities(center);
        this.triggerBlocks(center);
        this.spawnBurstEffects(center);
    }

    /**
     * Faithful port of official net/minecraft/world/level/ServerExplosion#hurtEntities:
     *
     *   float f = this.radius * 2.0F;                                   // 1.2F * 2 = 2.4F
     *   double d0 = sqrt(entity.distanceToSqr(center)) / f;
     *   if (d0 <= 1.0) {
     *       Vec3 vec3   = entity instanceof PrimedTnt ? entity.position() : entity.getEyePosition();
     *       Vec3 vec31  = vec3.subtract(center).normalize();
     *       boolean flag = damageCalculator.shouldDamageEntity(...);    // false for a wind charge
     *       float f1     = damageCalculator.getKnockbackMultiplier(entity);  // 1.22F, or 0 while flying
     *       float f2     = !flag && f1 == 0.0F ? 0.0F : getSeenPercent(center, entity);
     *       if (flag) { hurt } // never happens for a wind charge
     *       double d1 = EXPLOSION_KNOCKBACK_RESISTANCE attribute;
     *       double d2 = (1.0 - d0) * f2 * f1 * (1.0 - d1);
     *       entity.push(vec31.scale(d2));
     *       ... records hitPlayers for the client-side knockback packet ...
     *   }
     *
     * getSeenPercent is 1.16.4's Explosion.getBlockDensity (identical implementation).
     * 1.16.4 has no EXPLOSION_KNOCKBACK_RESISTANCE attribute, so d1 is 0 - see the report.
     */
    private void hurtEntities(Vector3d center)
    {
        float f = RADIUS * 2.0F;
        int i = MathHelper.floor(center.x - (double)f - 1.0D);
        int j = MathHelper.floor(center.x + (double)f + 1.0D);
        int k = MathHelper.floor(center.y - (double)f - 1.0D);
        int l = MathHelper.floor(center.y + (double)f + 1.0D);
        int i1 = MathHelper.floor(center.z - (double)f - 1.0D);
        int j1 = MathHelper.floor(center.z + (double)f + 1.0D);
        List<Entity> list = this.world.getEntitiesWithinAABBExcludingEntity(this, new AxisAlignedBB((double)i, (double)k, (double)i1, (double)j, (double)l, (double)j1));

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
            float f1 = this.getKnockbackMultiplier(entity);
            // Official: float f2 = !flag && f1 == 0.0F ? 0.0F : getSeenPercent(center, entity);
            // "flag" (shouldDamageEntity) is always false for a wind charge.
            float f2 = f1 == 0.0F ? 0.0F : Explosion.getBlockDensity(center, entity);
            double d7 = (1.0D - d0) * (double)f2 * (double)f1;
            // Official: entity.push(vec32). 1.16.4's Entity#addVelocity is the same
            // setMotion(getMotion().add(...)) + isAirBorne, and going through it (rather than setMotion
            // directly, as 1.16.4's own Explosion does) is what lets AbstractWindChargeEntity's empty
            // addVelocity override keep wind charges from blowing each other around.
            entity.addVelocity(d3 * d7, d4 * d7, d5 * d7);

            // Official ServerExplosion records knocked-back players in hitPlayers and ServerLevel forwards
            // the vector in ClientboundExplodePacket so the victim's own client applies it immediately.
            // 1.16.4's SExplosionPacket cannot be reused for that here: ClientPlayNetHandler#handleExplosion
            // runs Explosion#doExplosionB, which would play ENTITY_GENERIC_EXPLODE and spawn TNT smoke.
            // SEntityVelocityPacket carries the same information with no side effects - the client handler
            // just calls Entity#setVelocity, and ClientWorld#getEntityByID resolves the local player too.
            // Non-player entities are covered by the normal entity tracker, as with vanilla explosions.
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
     * Official SimpleExplosionDamageCalculator#getKnockbackMultiplier:
     *     boolean flag = entity instanceof Player player && player.getAbilities().flying;
     *     return flag ? 0.0F : knockbackMultiplier.orElseGet(...);
     */
    private float getKnockbackMultiplier(Entity entity)
    {
        if (entity instanceof PlayerEntity && ((PlayerEntity)entity).abilities.isFlying)
        {
            return 0.0F;
        }

        return KNOCKBACK_MULTIPLIER;
    }

    /**
     * Official ServerExplosion#interactWithBlocks with Explosion.BlockInteraction.TRIGGER_BLOCK: the
     * default BlockBehaviour#onExplosionHit is a no-op for TRIGGER_BLOCK, so only the blocks that override
     * it react. In 1.21.11 those are ButtonBlock, LeverBlock, DoorBlock, TrapDoorBlock, FenceGateBlock,
     * BellBlock, AbstractCandleBlock, BeehiveBlock and CreakingHeartBlock; the last three have no 1.16.4
     * counterpart (candles/creaking) or no wind-charge behaviour worth porting (beehive angers bees).
     *
     * Rather than adding an onExplosionHit override to six shared block classes, the triggers are applied
     * here through each block's existing public API, which performs the same state change + sound.
     */
    private void triggerBlocks(Vector3d center)
    {
        for (BlockPos blockpos : this.calculateExplodedPositions(center))
        {
            BlockState blockstate = this.world.getBlockState(blockpos);

            if (blockstate.getBlock() instanceof AbstractButtonBlock)
            {
                // Official ButtonBlock#onExplosionHit: if (!state.getValue(POWERED)) this.press(...)
                if (!blockstate.get(AbstractButtonBlock.POWERED))
                {
                    ((AbstractButtonBlock)blockstate.getBlock()).powerBlock(blockstate, this.world, blockpos);
                }
            }
            else if (blockstate.getBlock() instanceof LeverBlock)
            {
                // Official LeverBlock#onExplosionHit: this.pull(...) - unconditional
                ((LeverBlock)blockstate.getBlock()).setPowered(blockstate, this.world, blockpos);
            }
            else if (blockstate.getBlock() instanceof DoorBlock)
            {
                DoorBlock doorblock = (DoorBlock)blockstate.getBlock();

                // Official DoorBlock#onExplosionHit: HALF == LOWER && type.canOpenByWindCharge() && !POWERED
                // canOpenByWindCharge() is false for iron doors, which 1.16.4 identifies by material.
                if (blockstate.get(DoorBlock.HALF) == DoubleBlockHalf.LOWER
                        && blockstate.getMaterial() != Material.IRON
                        && !blockstate.get(DoorBlock.POWERED))
                {
                    doorblock.openDoor(this.world, blockstate, blockpos, !doorblock.isOpen(blockstate));
                }
            }
            else if (blockstate.getBlock() instanceof TrapDoorBlock)
            {
                // Official TrapDoorBlock#onExplosionHit: type.canOpenByWindCharge() && !POWERED, then toggle()
                if (blockstate.getMaterial() != Material.IRON && !blockstate.get(TrapDoorBlock.POWERED))
                {
                    BlockState blockstate1 = blockstate.func_235896_a_(TrapDoorBlock.OPEN);
                    this.world.setBlockState(blockpos, blockstate1, 2);

                    if (blockstate1.get(TrapDoorBlock.WATERLOGGED))
                    {
                        this.world.getPendingFluidTicks().scheduleTick(blockpos, Fluids.WATER, Fluids.WATER.getTickRate(this.world));
                    }

                    // Official toggle() plays the open/close sound; 1.16.4 uses world events 1007/1013 for
                    // wooden trapdoors (see TrapDoorBlock#playSound).
                    this.world.playEvent(null, blockstate1.get(TrapDoorBlock.OPEN) ? 1007 : 1013, blockpos, 0);
                }
            }
            else if (blockstate.getBlock() instanceof FenceGateBlock)
            {
                // Official FenceGateBlock#onExplosionHit: if (!POWERED) flip OPEN and play the gate sound.
                if (!blockstate.get(FenceGateBlock.POWERED))
                {
                    boolean flag = blockstate.get(FenceGateBlock.OPEN);
                    this.world.setBlockState(blockpos, blockstate.with(FenceGateBlock.OPEN, Boolean.valueOf(!flag)), 10);
                    this.world.playEvent(null, flag ? 1014 : 1008, blockpos, 0);
                }
            }
            else if (blockstate.getBlock() instanceof BellBlock)
            {
                // Official BellBlock#onExplosionHit: this.attemptToRing(level, pos, null)
                // A null direction makes 1.16.4's BellBlock#ring use the bell's own facing.
                ((BellBlock)blockstate.getBlock()).ring(this.world, blockpos, (Direction)null);
            }
        }
    }

    /**
     * Faithful port of the ray-casting loop in official ServerExplosion#calculateExplodedPositions (which is
     * unchanged from 1.16.4's Explosion#doExplosionA), using the wind charge's damage calculator:
     *   - getBlockExplosionResistance returns 3600000.0F for barrier/bedrock and Optional.empty() for
     *     everything else, so a wind burst passes straight through ordinary walls up to its radius.
     *   - shouldBlockExplode always returns true (explodesBlocks == true).
     */
    private Set<BlockPos> calculateExplodedPositions(Vector3d center)
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
                        float f = RADIUS * (0.7F + this.world.rand.nextFloat() * 0.6F);
                        double d4 = center.x;
                        double d6 = center.y;
                        double d8 = center.z;

                        for (; f > 0.0F; f -= 0.22500001F)
                        {
                            BlockPos blockpos = new BlockPos(d4, d6, d8);
                            BlockState blockstate = this.world.getBlockState(blockpos);

                            // Only barrier/bedrock have any resistance for a wind burst.
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
     * Official Level#explode spawns the small/large gust emitter particles plus the burst sound.
     * 1.16.4 has neither GUST_EMITTER_SMALL nor GUST_EMITTER_LARGE (both added in 1.20.5); CLOUD is the
     * closest stock particle. Counts/spreads below are chosen to read as an air blast of RADIUS 1.2 and are
     * NOT official numbers - the official particles are single emitter particles that spawn their own gust.
     */
    private void spawnBurstEffects(Vector3d center)
    {
        this.world.playSound(null, center.x, center.y, center.z, WIND_CHARGE_BURST, SoundCategory.BLOCKS, 1.2F, 0.9F + this.world.rand.nextFloat() * 0.2F);

        if (this.world instanceof ServerWorld)
        {
            ServerWorld serverworld = (ServerWorld)this.world;
            serverworld.spawnParticle(ParticleTypes.CLOUD, center.x, center.y, center.z, 12, (double)RADIUS * 0.5D, (double)RADIUS * 0.5D, (double)RADIUS * 0.5D, 0.05D);
        }
    }
}
