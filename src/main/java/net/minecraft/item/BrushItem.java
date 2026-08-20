package net.minecraft.item;

import net.minecraft.block.BlockRenderType;
import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.ProjectileHelper;
import net.minecraft.particles.BlockParticleData;
import net.minecraft.particles.ParticleTypes;
import net.minecraft.util.ActionResultType;
import net.minecraft.util.Direction;
import net.minecraft.util.Hand;
import net.minecraft.util.HandSide;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.SoundEvent;
import net.minecraft.util.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.BlockRayTraceResult;
import net.minecraft.util.math.EntityRayTraceResult;
import net.minecraft.util.math.RayTraceContext;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.vector.Vector3d;
import net.minecraft.world.World;

/**
 * Backport of the 1.20 brush.
 *
 * Official source: net/minecraft/world/item/BrushItem.java (1.21.11 / MCP-Reborn-release).
 *
 * Constants copied verbatim:
 *   ANIMATION_DURATION = 10
 *   USE_DURATION       = 200
 *   brush tick fires when (USE_DURATION - remainingTicks + 1) % 10 == 5
 *   dust particle count: random.nextInt(7, 12) i.e. 7..11 particles
 *   dust spread:         delta * armSign * 3.0 * random.nextDouble()
 *   DustParticlesDelta:  ALONG_SIDE_DELTA = 1.0, OUT_FROM_SIDE_DELTA = 0.1
 *
 * NOT ported (no 1.16.4 infrastructure - see the handoff report):
 *   - suspicious_sand / suspicious_gravel (BrushableBlock + BrushableBlockEntity) do not exist in this
 *     project, so nothing is ever excavated and the brush never takes durability damage
 *     (official: brushableblockentity.brush(...) -> hurtAndBreak(1, player, slot)).
 *   - BrushableBlock#getBrushSound per-block override; only the generic brushing sound is used.
 */
public class BrushItem extends Item
{
    /** Official BrushItem.ANIMATION_DURATION. */
    public static final int ANIMATION_DURATION = 10;
    /** Official BrushItem.USE_DURATION. */
    private static final int USE_DURATION = 200;

    /**
     * Official Attributes.BLOCK_INTERACTION_RANGE default is 4.5, which is what the official BrushItem
     * passes to getHitResultOnViewVector via Player#blockInteractionRange(). 1.16.4 has no such attribute
     * (reach lives client-side in PlayerController), so the vanilla survival value is used on both sides.
     */
    private static final double BLOCK_INTERACTION_RANGE = 4.5D;

    /**
     * 1.16.4 has no "item.brush.brushing.generic" sound (added in 1.20). The soft sandy hit is the closest
     * stock match.
     * Official sound: SoundEvents.BRUSH_GENERIC.
     */
    private static final SoundEvent BRUSH_GENERIC = SoundEvents.BLOCK_SAND_HIT;

    public BrushItem(Item.Properties builder)
    {
        super(builder);
    }

    /**
     * Official BrushItem#useOn: start using the item if the player is actually looking at a block, and
     * always return CONSUME so the vanilla "use item on block" path does not also fire.
     */
    public ActionResultType onItemUse(ItemUseContext context)
    {
        PlayerEntity playerentity = context.getPlayer();

        if (playerentity != null && this.calculateHitResult(playerentity).getType() == RayTraceResult.Type.BLOCK)
        {
            playerentity.setActiveHand(context.getHand());
        }

        return ActionResultType.CONSUME;
    }

    /**
     * Official: UseAnim.BRUSH. {@link UseAction#BRUSH} was added to the enum for this backport.
     */
    public UseAction getUseAction(ItemStack stack)
    {
        return UseAction.BRUSH;
    }

    /**
     * How long it takes to use or consume an item. Official: 200.
     */
    public int getUseDuration(ItemStack stack)
    {
        return USE_DURATION;
    }

    /**
     * 1.16.4's Item#onUse is the official Item#onUseTick (count == remaining ticks).
     *
     * Official BrushItem#onUseTick:
     *   int i = this.getUseDuration(stack) - remainingTicks + 1;
     *   boolean flag = i % 10 == 5;                       // one brush "stroke" every 10 ticks
     *   if (flag) { spawn dust particles + play brushing sound + BrushableBlockEntity#brush }
     *   ... and releaseUsingItem() as soon as the player stops looking at a block.
     */
    public void onUse(World worldIn, LivingEntity livingEntityIn, ItemStack stack, int count)
    {
        if (count >= 0 && livingEntityIn instanceof PlayerEntity)
        {
            PlayerEntity playerentity = (PlayerEntity)livingEntityIn;
            RayTraceResult raytraceresult = this.calculateHitResult(playerentity);

            if (raytraceresult instanceof BlockRayTraceResult && raytraceresult.getType() == RayTraceResult.Type.BLOCK)
            {
                BlockRayTraceResult blockraytraceresult = (BlockRayTraceResult)raytraceresult;
                int i = this.getUseDuration(stack) - count + 1;
                boolean flag = i % ANIMATION_DURATION == 5;

                if (flag)
                {
                    BlockPos blockpos = blockraytraceresult.getPos();
                    BlockState blockstate = worldIn.getBlockState(blockpos);
                    HandSide handside = livingEntityIn.getActiveHand() == Hand.MAIN_HAND ? playerentity.getPrimaryHand() : playerentity.getPrimaryHand().opposite();

                    // Official also checks blockstate.shouldSpawnTerrainParticles(), a 1.20.5 block property
                    // that has no 1.16.4 equivalent (it is true for every block that existed in 1.16.4).
                    if (worldIn.isRemote && blockstate.getRenderType() != BlockRenderType.INVISIBLE)
                    {
                        this.spawnDustParticles(worldIn, blockraytraceresult, blockstate, livingEntityIn.getLook(0.0F), handside);
                    }

                    // Official picks BrushableBlock#getBrushSound() for suspicious sand/gravel and
                    // SoundEvents.BRUSH_GENERIC otherwise. Neither block exists here, so it is always generic.
                    worldIn.playSound(playerentity, blockpos, BRUSH_GENERIC, SoundCategory.BLOCKS, 1.0F, 1.0F);

                    // Official: BrushableBlockEntity#brush(...) then stack.hurtAndBreak(1, player, slot).
                    // No BrushableBlockEntity exists in 1.16.4, so nothing is excavated here.
                }
            }
            else
            {
                livingEntityIn.stopActiveHand();
            }
        }
        else
        {
            livingEntityIn.stopActiveHand();
        }
    }

    /**
     * Official BrushItem#calculateHitResult:
     *   ProjectileUtil.getHitResultOnViewVector(player, EntitySelector.CAN_BE_PICKED, player.blockInteractionRange())
     * which clips from the eye position along the view vector using ClipContext.Block.COLLIDER and then
     * checks entities inside the swept box. The 1.16.4 equivalents are World#rayTraceBlocks with a
     * RayTraceContext and ProjectileHelper#rayTraceEntities.
     */
    private RayTraceResult calculateHitResult(PlayerEntity player)
    {
        Vector3d vector3d = player.getEyePosition(1.0F);
        Vector3d vector3d1 = player.getLook(1.0F).scale(BLOCK_INTERACTION_RANGE);
        Vector3d vector3d2 = vector3d.add(vector3d1);
        RayTraceResult raytraceresult = player.world.rayTraceBlocks(new RayTraceContext(vector3d, vector3d2, RayTraceContext.BlockMode.COLLIDER, RayTraceContext.FluidMode.NONE, player));

        if (raytraceresult.getType() != RayTraceResult.Type.MISS)
        {
            vector3d2 = raytraceresult.getHitVec();
        }

        // Official uses EntitySelector.CAN_BE_PICKED (alive, not spectator, pickable).
        EntityRayTraceResult entityraytraceresult = ProjectileHelper.rayTraceEntities(
                player.world,
                player,
                vector3d,
                vector3d2,
                player.getBoundingBox().expand(vector3d1).grow(1.0D),
                (Entity entity) -> !entity.isSpectator() && entity.isAlive() && entity.canBeCollidedWith());

        return entityraytraceresult != null ? entityraytraceresult : raytraceresult;
    }

    /**
     * Direct port of official BrushItem#spawnDustParticles.
     */
    private void spawnDustParticles(World worldIn, BlockRayTraceResult hit, BlockState state, Vector3d viewVector, HandSide arm)
    {
        int i = arm == HandSide.RIGHT ? 1 : -1;
        // Official: p_278327_.getRandom().nextInt(7, 12) - a bounded nextInt over [7, 12).
        int j = 7 + worldIn.rand.nextInt(5);
        BlockParticleData blockparticledata = new BlockParticleData(ParticleTypes.BLOCK, state);
        Direction direction = hit.getFace();
        BrushItem.DustParticlesDelta dustparticlesdelta = BrushItem.DustParticlesDelta.fromDirection(viewVector, direction);
        Vector3d vector3d = hit.getHitVec();

        for (int k = 0; k < j; ++k)
        {
            worldIn.addParticle(
                    blockparticledata,
                    vector3d.x - (double)(direction == Direction.WEST ? 1.0E-6F : 0.0F),
                    vector3d.y,
                    vector3d.z - (double)(direction == Direction.NORTH ? 1.0E-6F : 0.0F),
                    dustparticlesdelta.xd * (double)i * 3.0D * worldIn.rand.nextDouble(),
                    0.0D,
                    dustparticlesdelta.zd * (double)i * 3.0D * worldIn.rand.nextDouble());
        }
    }

    /**
     * Direct port of the official BrushItem.DustParticlesDelta record.
     * ALONG_SIDE_DELTA = 1.0, OUT_FROM_SIDE_DELTA = 0.1.
     */
    static class DustParticlesDelta
    {
        final double xd;
        final double yd;
        final double zd;

        DustParticlesDelta(double xd, double yd, double zd)
        {
            this.xd = xd;
            this.yd = yd;
            this.zd = zd;
        }

        public static BrushItem.DustParticlesDelta fromDirection(Vector3d viewVector, Direction direction)
        {
            switch (direction)
            {
                case DOWN:
                case UP:
                    return new BrushItem.DustParticlesDelta(viewVector.z, 0.0D, -viewVector.x);

                case NORTH:
                    return new BrushItem.DustParticlesDelta(1.0D, 0.0D, -0.1D);

                case SOUTH:
                    return new BrushItem.DustParticlesDelta(-1.0D, 0.0D, 0.1D);

                case WEST:
                    return new BrushItem.DustParticlesDelta(-0.1D, 0.0D, -1.0D);

                case EAST:
                default:
                    return new BrushItem.DustParticlesDelta(0.1D, 0.0D, 1.0D);
            }
        }
    }
}
