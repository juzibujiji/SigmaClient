package net.minecraft.block;

import java.util.Optional;
import java.util.Random;
import java.util.function.BiPredicate;
import java.util.function.Predicate;
import javax.annotation.Nullable;
import net.minecraft.entity.Entity;
import net.minecraft.entity.item.FallingBlockEntity;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.entity.projectile.TridentEntity;
import net.minecraft.fluid.Fluid;
import net.minecraft.fluid.FluidState;
import net.minecraft.fluid.Fluids;
import net.minecraft.item.BlockItemUseContext;
import net.minecraft.particles.BasicParticleType;
import net.minecraft.particles.ParticleTypes;
import net.minecraft.state.BooleanProperty;
import net.minecraft.state.DirectionProperty;
import net.minecraft.state.EnumProperty;
import net.minecraft.state.StateContainer;
import net.minecraft.state.properties.BlockStateProperties;
import net.minecraft.state.properties.DripstoneThickness;
import net.minecraft.util.Direction;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.BlockRayTraceResult;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.shapes.IBooleanFunction;
import net.minecraft.util.math.shapes.ISelectionContext;
import net.minecraft.util.math.shapes.VoxelShape;
import net.minecraft.util.math.shapes.VoxelShapes;
import net.minecraft.util.math.vector.Vector3d;
import net.minecraft.util.registry.Registry;
import net.minecraft.pathfinding.PathType;
import net.minecraft.world.IBlockReader;
import net.minecraft.world.IWorld;
import net.minecraft.world.IWorldReader;
import net.minecraft.world.World;
import net.minecraft.world.server.ServerWorld;

/**
 * 1.21.11 backport of {@code net.minecraft.world.level.block.PointedDripstoneBlock}.
 *
 * <p>State properties (official blocks.json, 20 states):
 * {@code thickness=[tip_merge,tip,frustum,middle,base]}, {@code vertical_direction=[up,down]},
 * {@code waterlogged=[true,false]}.
 *
 * <p>Extends {@link FallingBlock} purely because 1.16.4 has no {@code IFallable} interface -
 * {@code FallingBlockEntity} dispatches its landing/breaking callbacks through
 * {@code instanceof FallingBlock} (FallingBlockEntity.java:197/235). All of FallingBlock's own
 * behaviour ({@code onBlockAdded}/{@code updatePostPlacement}/{@code tick}/{@code animateTick})
 * is overridden away below.
 *
 * <p>All constants below are copied from the official class (field names kept):
 * <pre>
 * MAX_SEARCH_LENGTH_WHEN_CHECKING_DRIP_TYPE            = 11
 * DELAY_BEFORE_FALLING                                 = 2
 * DRIP_PROBABILITY_PER_ANIMATE_TICK                    = 0.02F
 * DRIP_PROBABILITY_PER_ANIMATE_TICK_IF_UNDER_LIQUID    = 0.12F
 * WATER_TRANSFER_PROBABILITY_PER_RANDOM_TICK           = 0.17578125F
 * LAVA_TRANSFER_PROBABILITY_PER_RANDOM_TICK            = 0.05859375F
 * MIN_TRIDENT_VELOCITY_TO_BREAK_DRIPSTONE              = 0.6
 * STALACTITE_DAMAGE_PER_FALL_DISTANCE_AND_SIZE         = 1.0F
 * STALACTITE_MAX_DAMAGE                                = 40
 * MAX_STALACTITE_HEIGHT_FOR_DAMAGE_CALCULATION         = 6
 * STALAGMITE_FALL_DISTANCE_OFFSET                      = 2.5F
 * STALAGMITE_FALL_DAMAGE_MODIFIER                      = 2
 * GROWTH_PROBABILITY_PER_RANDOM_TICK                   = 0.011377778F  (avg 5 days per growth)
 * MAX_GROWTH_LENGTH                                    = 7
 * MAX_STALAGMITE_SEARCH_RANGE_WHEN_GROWING             = 10
 * </pre>
 *
 * <p>Shapes: official uses {@code Block.column(w, minY, maxY)} which expands (Block.java:184-188)
 * to {@code box(8-w/2, minY, 8-w/2, 8+w/2, maxY, 8+w/2)}:
 * <pre>
 * SHAPE_TIP_MERGE  column(6, 0, 16) -> box(5, 0, 5, 11, 16, 11)
 * SHAPE_TIP_UP     column(6, 0, 11) -> box(5, 0, 5, 11, 11, 11)
 * SHAPE_TIP_DOWN   column(6, 5, 16) -> box(5, 5, 5, 11, 16, 11)
 * SHAPE_FRUSTUM    column(8, 0, 16) -> box(4, 0, 4, 12, 16, 12)
 * SHAPE_MIDDLE     column(10,0, 16) -> box(3, 0, 3, 13, 16, 13)
 * SHAPE_BASE       column(12,0, 16) -> box(2, 0, 2, 14, 16, 14)
 * REQUIRED_SPACE_TO_DRIP_THROUGH_NON_SOLID_BLOCK column(4, 0, 16) -> box(6, 0, 6, 10, 16, 10)
 * </pre>
 */
public class ModernPointedDripstoneBlock extends FallingBlock implements IWaterLoggable
{
    public static final DirectionProperty TIP_DIRECTION = DirectionProperty.create("vertical_direction", Direction.UP, Direction.DOWN);
    public static final EnumProperty<DripstoneThickness> THICKNESS = EnumProperty.create("thickness", DripstoneThickness.class);
    public static final BooleanProperty WATERLOGGED = BlockStateProperties.WATERLOGGED;
    private static final int MAX_SEARCH_LENGTH_WHEN_CHECKING_DRIP_TYPE = 11;
    private static final int DELAY_BEFORE_FALLING = 2;
    private static final float DRIP_PROBABILITY_PER_ANIMATE_TICK = 0.02F;
    private static final float DRIP_PROBABILITY_PER_ANIMATE_TICK_IF_UNDER_LIQUID_SOURCE = 0.12F;
    private static final float WATER_TRANSFER_PROBABILITY_PER_RANDOM_TICK = 0.17578125F;
    private static final float LAVA_TRANSFER_PROBABILITY_PER_RANDOM_TICK = 0.05859375F;
    private static final double MIN_TRIDENT_VELOCITY_TO_BREAK_DRIPSTONE = 0.6D;
    private static final float STALACTITE_DAMAGE_PER_FALL_DISTANCE_AND_SIZE = 1.0F;
    private static final int STALACTITE_MAX_DAMAGE = 40;
    private static final int MAX_STALACTITE_HEIGHT_FOR_DAMAGE_CALCULATION = 6;
    private static final float STALAGMITE_FALL_DISTANCE_OFFSET = 2.5F;
    private static final int STALAGMITE_FALL_DAMAGE_MODIFIER = 2;
    private static final float GROWTH_PROBABILITY_PER_RANDOM_TICK = 0.011377778F;
    private static final int MAX_GROWTH_LENGTH = 7;
    private static final int MAX_STALAGMITE_SEARCH_RANGE_WHEN_GROWING = 10;
    private static final VoxelShape SHAPE_TIP_MERGE = Block.makeCuboidShape(5.0D, 0.0D, 5.0D, 11.0D, 16.0D, 11.0D);
    private static final VoxelShape SHAPE_TIP_UP = Block.makeCuboidShape(5.0D, 0.0D, 5.0D, 11.0D, 11.0D, 11.0D);
    private static final VoxelShape SHAPE_TIP_DOWN = Block.makeCuboidShape(5.0D, 5.0D, 5.0D, 11.0D, 16.0D, 11.0D);
    private static final VoxelShape SHAPE_FRUSTUM = Block.makeCuboidShape(4.0D, 0.0D, 4.0D, 12.0D, 16.0D, 12.0D);
    private static final VoxelShape SHAPE_MIDDLE = Block.makeCuboidShape(3.0D, 0.0D, 3.0D, 13.0D, 16.0D, 13.0D);
    private static final VoxelShape SHAPE_BASE = Block.makeCuboidShape(2.0D, 0.0D, 2.0D, 14.0D, 16.0D, 14.0D);
    /** Official: {@code SHAPE_TIP_DOWN.min(Direction.Axis.Y)} == 5/16. */
    private static final double STALACTITE_DRIP_START_PIXEL = 0.3125D;
    private static final VoxelShape REQUIRED_SPACE_TO_DRIP_THROUGH_NON_SOLID_BLOCK = Block.makeCuboidShape(6.0D, 0.0D, 6.0D, 10.0D, 16.0D, 10.0D);
    /** Resolved lazily: {@code dripstone_block} is itself a backported block and may register after this one. */
    private static Block dripstoneBlock;

    public ModernPointedDripstoneBlock(AbstractBlock.Properties properties)
    {
        super(properties);
        this.setDefaultState(this.stateContainer.getBaseState().with(TIP_DIRECTION, Direction.UP).with(THICKNESS, DripstoneThickness.TIP).with(WATERLOGGED, Boolean.valueOf(false)));
    }

    protected void fillStateContainer(StateContainer.Builder<Block, BlockState> builder)
    {
        builder.add(TIP_DIRECTION, THICKNESS, WATERLOGGED);
    }

    /** FallingBlock schedules a fall tick here; pointed dripstone must not. */
    public void onBlockAdded(BlockState state, World worldIn, BlockPos pos, BlockState oldState, boolean isMoving)
    {
    }

    public boolean isValidPosition(BlockState state, IWorldReader worldIn, BlockPos pos)
    {
        return isValidPointedDripstonePlacement(worldIn, pos, state.get(TIP_DIRECTION));
    }

    public BlockState updatePostPlacement(BlockState stateIn, Direction facing, BlockState facingState, IWorld worldIn, BlockPos currentPos, BlockPos facingPos)
    {
        if (stateIn.get(WATERLOGGED))
        {
            worldIn.getPendingFluidTicks().scheduleTick(currentPos, Fluids.WATER, Fluids.WATER.getTickRate(worldIn));
        }

        if (facing != Direction.UP && facing != Direction.DOWN)
        {
            return stateIn;
        }
        else
        {
            Direction direction = stateIn.get(TIP_DIRECTION);

            if (direction == Direction.DOWN && worldIn.getPendingBlockTicks().isTickScheduled(currentPos, this))
            {
                return stateIn;
            }
            else if (facing == direction.getOpposite() && !this.isValidPosition(stateIn, worldIn, currentPos))
            {
                if (direction == Direction.DOWN)
                {
                    worldIn.getPendingBlockTicks().scheduleTick(currentPos, this, DELAY_BEFORE_FALLING);
                }
                else
                {
                    worldIn.getPendingBlockTicks().scheduleTick(currentPos, this, 1);
                }

                return stateIn;
            }
            else
            {
                boolean flag = stateIn.get(THICKNESS) == DripstoneThickness.TIP_MERGE;
                DripstoneThickness dripstonethickness = this.calculateDripstoneThickness(worldIn, currentPos, direction, flag);
                return stateIn.with(THICKNESS, dripstonethickness);
            }
        }
    }

    public void onProjectileCollision(World worldIn, BlockState state, BlockRayTraceResult hit, ProjectileEntity projectile)
    {
        if (!worldIn.isRemote())
        {
            BlockPos blockpos = hit.getPos();

            // Official also checks projectile.mayInteract/mayBreak; 1.16.4 has no such hooks.
            if (projectile instanceof TridentEntity && projectile.getMotion().length() > MIN_TRIDENT_VELOCITY_TO_BREAK_DRIPSTONE)
            {
                worldIn.destroyBlock(blockpos, true);
            }
        }
    }

    /**
     * Official {@code fallOn}: {@code entity.causeFallDamage(fallDistance + 2.5, 2.0F, stalagmite())}.
     * 1.16.4 has no per-call DamageSource override on {@code onLivingFall}, so the damage source is
     * plain FALL instead of {@code minecraft:stalagmite}; the offset/multiplier are the official ones.
     */
    public void onFallenUpon(World worldIn, BlockPos pos, Entity entityIn, float fallDistance)
    {
        BlockState blockstate = worldIn.getBlockState(pos);

        if (blockstate.isIn(this) && blockstate.get(TIP_DIRECTION) == Direction.UP && blockstate.get(THICKNESS) == DripstoneThickness.TIP)
        {
            entityIn.onLivingFall(fallDistance + STALAGMITE_FALL_DISTANCE_OFFSET, (float)STALAGMITE_FALL_DAMAGE_MODIFIER);
        }
        else
        {
            super.onFallenUpon(worldIn, pos, entityIn, fallDistance);
        }
    }

    public void animateTick(BlockState stateIn, World worldIn, BlockPos pos, Random rand)
    {
        if (this.canDrip(stateIn))
        {
            float f = rand.nextFloat();

            if (!(f > DRIP_PROBABILITY_PER_ANIMATE_TICK_IF_UNDER_LIQUID_SOURCE))
            {
                Optional<ModernPointedDripstoneBlock.FluidInfo> optional = this.getFluidAboveStalactite(worldIn, pos, stateIn);

                if (optional.isPresent() && (f < DRIP_PROBABILITY_PER_ANIMATE_TICK || canFillCauldron(optional.get().fluid)))
                {
                    this.spawnDripParticle(worldIn, pos, stateIn, optional.get().fluid);
                }
            }
        }
    }

    public void tick(BlockState state, ServerWorld worldIn, BlockPos pos, Random rand)
    {
        if (this.isStalagmite(state) && !this.isValidPosition(state, worldIn, pos))
        {
            worldIn.destroyBlock(pos, true);
        }
        else
        {
            this.spawnFallingStalactite(state, worldIn, pos);
        }
    }

    public void randomTick(BlockState state, ServerWorld worldIn, BlockPos pos, Random random)
    {
        this.maybeTransferFluid(state, worldIn, pos, random.nextFloat());

        if (random.nextFloat() < GROWTH_PROBABILITY_PER_RANDOM_TICK && this.isStalactiteStartPos(state, worldIn, pos))
        {
            this.growStalactiteOrStalagmiteIfPossible(state, worldIn, pos, random);
        }
    }

    /**
     * Official {@code maybeTransferFluid} additionally converts mud to clay and fills cauldrons.
     * 1.16.4 has neither {@code Blocks.MUD} nor {@code AbstractCauldronBlock#canReceiveStalactiteDrip},
     * so only the probability gate and the tip search are reproduced (they still gate the
     * client-side drip particle rate via {@link #canDrip}).
     */
    public void maybeTransferFluid(BlockState state, ServerWorld worldIn, BlockPos pos, float rand)
    {
        if (!(rand > WATER_TRANSFER_PROBABILITY_PER_RANDOM_TICK) || !(rand > LAVA_TRANSFER_PROBABILITY_PER_RANDOM_TICK))
        {
            if (this.isStalactiteStartPos(state, worldIn, pos))
            {
                Optional<ModernPointedDripstoneBlock.FluidInfo> optional = this.getFluidAboveStalactite(worldIn, pos, state);

                if (optional.isPresent())
                {
                    Fluid fluid = optional.get().fluid;
                    float f;

                    if (fluid == Fluids.WATER)
                    {
                        f = WATER_TRANSFER_PROBABILITY_PER_RANDOM_TICK;
                    }
                    else
                    {
                        if (fluid != Fluids.LAVA)
                        {
                            return;
                        }

                        f = LAVA_TRANSFER_PROBABILITY_PER_RANDOM_TICK;
                    }

                    if (!(rand >= f))
                    {
                        BlockPos blockpos = this.findTip(state, worldIn, pos, MAX_SEARCH_LENGTH_WHEN_CHECKING_DRIP_TYPE, false);

                        if (blockpos != null)
                        {
                            // Official: level.levelEvent(1504, tip, 0) ("dripstone drips"). 1.16.4's
                            // client does not know event 1504, so it is a no-op there.
                            worldIn.playEvent(1504, blockpos, 0);
                        }
                    }
                }
            }
        }
    }

    @Nullable
    public BlockState getStateForPlacement(BlockItemUseContext context)
    {
        World world = context.getWorld();
        BlockPos blockpos = context.getPos();
        // Official: context.getNearestLookingVerticalDirection().getOpposite(), where
        // getNearestLookingVerticalDirection() == Direction.getFacingAxis(player, Axis.Y)
        // == (player.getXRot() < 0 ? UP : DOWN). 1.16.4 has no entity/axis overload on Direction.
        net.minecraft.entity.player.PlayerEntity playerentity = context.getPlayer();
        Direction direction = (playerentity != null && playerentity.rotationPitch < 0.0F ? Direction.UP : Direction.DOWN).getOpposite();
        Direction direction1 = this.calculateTipDirection(world, blockpos, direction);

        if (direction1 == null)
        {
            return null;
        }
        else
        {
            // Official: !context.isSecondaryUseActive(); 1.16.4 exposes the player instead.
            boolean flag = context.getPlayer() == null || !context.getPlayer().isSneaking();
            DripstoneThickness dripstonethickness = this.calculateDripstoneThickness(world, blockpos, direction1, flag);
            return this.getDefaultState().with(TIP_DIRECTION, direction1).with(THICKNESS, dripstonethickness).with(WATERLOGGED, Boolean.valueOf(world.getFluidState(blockpos).getFluid() == Fluids.WATER));
        }
    }

    public FluidState getFluidState(BlockState state)
    {
        return state.get(WATERLOGGED) ? Fluids.WATER.getStillFluidState(false) : super.getFluidState(state);
    }

    public VoxelShape getShape(BlockState state, IBlockReader worldIn, BlockPos pos, ISelectionContext context)
    {
        VoxelShape voxelshape;

        switch (state.get(THICKNESS))
        {
            case TIP_MERGE:
                voxelshape = SHAPE_TIP_MERGE;
                break;

            case TIP:
                voxelshape = state.get(TIP_DIRECTION) == Direction.DOWN ? SHAPE_TIP_DOWN : SHAPE_TIP_UP;
                break;

            case FRUSTUM:
                voxelshape = SHAPE_FRUSTUM;
                break;

            case MIDDLE:
                voxelshape = SHAPE_MIDDLE;
                break;

            case BASE:
            default:
                voxelshape = SHAPE_BASE;
                break;
        }

        Vector3d vector3d = state.getOffset(worldIn, pos);
        return voxelshape.withOffset(vector3d.x, vector3d.y, vector3d.z);
    }

    /**
     * Official registration uses {@code .offsetType(OffsetType.XZ)}. 1.16.4 hardcodes the horizontal
     * offset magnitude at +-0.25 (AbstractBlock.java:664); official pointed dripstone narrows it to
     * +-0.125 via {@code getMaxHorizontalOffset()}, a hook 1.16.4 does not have.
     */
    public AbstractBlock.OffsetType getOffsetType()
    {
        return AbstractBlock.OffsetType.XZ;
    }

    public boolean allowsMovement(BlockState state, IBlockReader worldIn, BlockPos pos, PathType type)
    {
        return false;
    }

    public void onBroken(World worldIn, BlockPos pos, FallingBlockEntity fallingBlock)
    {
        if (!fallingBlock.isSilent())
        {
            // Official: level.levelEvent(1045, pos, 0) ("pointed dripstone lands").
            worldIn.playEvent(1045, pos, 0);
        }
    }

    private void spawnFallingStalactite(BlockState state, ServerWorld worldIn, BlockPos pos)
    {
        BlockPos.Mutable mutable = pos.toMutable();
        BlockState blockstate = state;

        while (this.isStalactite(blockstate))
        {
            // 官方是 FallingBlockEntity.fall(level, pos, state)：先把源方块换成流体状态，再生成实体。
            //
            // 【1.16.4 必须反过来】不能提前清源方块。1.16.4 的 FallingBlockEntity.tick 在
            // fallTime == 0 那一 tick 会检查「自己脚下的方块是不是 fallTile 的方块」，
            // 不是就当场 remove() 并 return（FallingBlockEntity.java:125-133）。
            // 提前清掉的话实体一出生就自毁，表现是「石锥直接消失，看不到掉落过程」。
            //
            // 1.16.4 原版 FallingBlock.tick 的做法就是只生成实体、<b>不动方块</b>，
            // 由实体在那一 tick 里自己 removeBlock。这里照原版走。
            FallingBlockEntity fallingblockentity = new FallingBlockEntity(worldIn, (double)mutable.getX() + 0.5D, (double)mutable.getY(), (double)mutable.getZ() + 0.5D, blockstate);
            worldIn.addEntity(fallingblockentity);

            if (this.isTip(blockstate, true))
            {
                int i = Math.max(1 + pos.getY() - mutable.getY(), MAX_STALACTITE_HEIGHT_FOR_DAMAGE_CALCULATION);
                float f = STALACTITE_DAMAGE_PER_FALL_DISTANCE_AND_SIZE * (float)i;
                fallingblockentity.setHurtEntities(f, STALACTITE_MAX_DAMAGE);
                break;
            }

            mutable.move(Direction.DOWN);
            blockstate = worldIn.getBlockState(mutable);
        }
    }

    public void growStalactiteOrStalagmiteIfPossible(BlockState state, ServerWorld worldIn, BlockPos pos, Random rand)
    {
        BlockState blockstate = worldIn.getBlockState(pos.up(1));
        BlockState blockstate1 = worldIn.getBlockState(pos.up(2));

        if (canGrow(blockstate, blockstate1))
        {
            BlockPos blockpos = this.findTip(state, worldIn, pos, MAX_GROWTH_LENGTH, false);

            if (blockpos != null)
            {
                BlockState blockstate2 = worldIn.getBlockState(blockpos);

                if (this.canDrip(blockstate2) && this.canTipGrow(blockstate2, worldIn, blockpos))
                {
                    if (rand.nextBoolean())
                    {
                        this.grow(worldIn, blockpos, Direction.DOWN);
                    }
                    else
                    {
                        this.growStalagmiteBelow(worldIn, blockpos);
                    }
                }
            }
        }
    }

    private void growStalagmiteBelow(ServerWorld worldIn, BlockPos pos)
    {
        BlockPos.Mutable mutable = pos.toMutable();

        for (int i = 0; i < MAX_STALAGMITE_SEARCH_RANGE_WHEN_GROWING; ++i)
        {
            mutable.move(Direction.DOWN);
            BlockState blockstate = worldIn.getBlockState(mutable);

            if (!blockstate.getFluidState().isEmpty())
            {
                return;
            }

            if (this.isUnmergedTipWithDirection(blockstate, Direction.UP) && this.canTipGrow(blockstate, worldIn, mutable))
            {
                this.grow(worldIn, mutable, Direction.UP);
                return;
            }

            if (isValidPointedDripstonePlacement(worldIn, mutable, Direction.UP) && !worldIn.hasWater(mutable.down()))
            {
                this.grow(worldIn, mutable.down(), Direction.UP);
                return;
            }

            if (!canDripThrough(worldIn, mutable, blockstate))
            {
                return;
            }
        }
    }

    private void grow(ServerWorld worldIn, BlockPos pos, Direction direction)
    {
        BlockPos blockpos = pos.offset(direction);
        BlockState blockstate = worldIn.getBlockState(blockpos);

        if (this.isUnmergedTipWithDirection(blockstate, direction.getOpposite()))
        {
            this.createMergedTips(blockstate, worldIn, blockpos);
        }
        else if (blockstate.isAir() || blockstate.isIn(Blocks.WATER))
        {
            this.createDripstone(worldIn, blockpos, direction, DripstoneThickness.TIP);
        }
    }

    private void createDripstone(IWorld worldIn, BlockPos pos, Direction direction, DripstoneThickness thickness)
    {
        BlockState blockstate = this.getDefaultState().with(TIP_DIRECTION, direction).with(THICKNESS, thickness).with(WATERLOGGED, Boolean.valueOf(worldIn.getFluidState(pos).getFluid() == Fluids.WATER));
        worldIn.setBlockState(pos, blockstate, 3);
    }

    private void createMergedTips(BlockState state, IWorld worldIn, BlockPos pos)
    {
        BlockPos blockpos;
        BlockPos blockpos1;

        if (state.get(TIP_DIRECTION) == Direction.UP)
        {
            blockpos1 = pos;
            blockpos = pos.up();
        }
        else
        {
            blockpos = pos;
            blockpos1 = pos.down();
        }

        this.createDripstone(worldIn, blockpos, Direction.DOWN, DripstoneThickness.TIP_MERGE);
        this.createDripstone(worldIn, blockpos1, Direction.UP, DripstoneThickness.TIP_MERGE);
    }

    private void spawnDripParticle(World worldIn, BlockPos pos, BlockState state, Fluid fluid)
    {
        Vector3d vector3d = state.getOffset(worldIn, pos);
        double d0 = 0.0625D;
        double d1 = (double)pos.getX() + 0.5D + vector3d.x;
        double d2 = (double)pos.getY() + STALACTITE_DRIP_START_PIXEL - d0;
        double d3 = (double)pos.getZ() + 0.5D + vector3d.z;
        worldIn.addParticle(getDripParticle(fluid), d1, d2, d3, 0.0D, 0.0D, 0.0D);
    }

    @Nullable
    private BlockPos findTip(BlockState state, IWorld worldIn, BlockPos pos, int maxLength, boolean allowMerged)
    {
        if (this.isTip(state, allowMerged))
        {
            return pos;
        }
        else
        {
            final Direction direction = state.get(TIP_DIRECTION);
            BiPredicate<BlockPos, BlockState> bipredicate = (p, s) ->
            {
                return s.isIn(this) && s.get(TIP_DIRECTION) == direction;
            };
            final boolean flag = allowMerged;
            return findBlockVertical(worldIn, pos, direction.getAxisDirection(), bipredicate, (s) ->
            {
                return this.isTip(s, flag);
            }, maxLength).orElse((BlockPos)null);
        }
    }

    @Nullable
    private Direction calculateTipDirection(IWorldReader worldIn, BlockPos pos, Direction dir)
    {
        Direction direction;

        if (isValidPointedDripstonePlacement(worldIn, pos, dir))
        {
            direction = dir;
        }
        else
        {
            if (!isValidPointedDripstonePlacement(worldIn, pos, dir.getOpposite()))
            {
                return null;
            }

            direction = dir.getOpposite();
        }

        return direction;
    }

    private DripstoneThickness calculateDripstoneThickness(IWorldReader worldIn, BlockPos pos, Direction dir, boolean allowMerge)
    {
        Direction direction = dir.getOpposite();
        BlockState blockstate = worldIn.getBlockState(pos.offset(dir));

        if (this.isPointedDripstoneWithDirection(blockstate, direction))
        {
            return !allowMerge && blockstate.get(THICKNESS) != DripstoneThickness.TIP_MERGE ? DripstoneThickness.TIP : DripstoneThickness.TIP_MERGE;
        }
        else if (!this.isPointedDripstoneWithDirection(blockstate, dir))
        {
            return DripstoneThickness.TIP;
        }
        else
        {
            DripstoneThickness dripstonethickness = blockstate.get(THICKNESS);

            if (dripstonethickness != DripstoneThickness.TIP && dripstonethickness != DripstoneThickness.TIP_MERGE)
            {
                BlockState blockstate1 = worldIn.getBlockState(pos.offset(direction));
                return !this.isPointedDripstoneWithDirection(blockstate1, dir) ? DripstoneThickness.BASE : DripstoneThickness.MIDDLE;
            }
            else
            {
                return DripstoneThickness.FRUSTUM;
            }
        }
    }

    public boolean canDrip(BlockState state)
    {
        return this.isStalactite(state) && state.get(THICKNESS) == DripstoneThickness.TIP && !state.get(WATERLOGGED);
    }

    private boolean canTipGrow(BlockState state, ServerWorld worldIn, BlockPos pos)
    {
        Direction direction = state.get(TIP_DIRECTION);
        BlockPos blockpos = pos.offset(direction);
        BlockState blockstate = worldIn.getBlockState(blockpos);

        if (!blockstate.getFluidState().isEmpty())
        {
            return false;
        }
        else
        {
            return blockstate.isAir() ? true : this.isUnmergedTipWithDirection(blockstate, direction.getOpposite());
        }
    }

    private Optional<BlockPos> findRootBlock(IWorld worldIn, BlockPos pos, BlockState state, int maxLength)
    {
        final Direction direction = state.get(TIP_DIRECTION);
        BiPredicate<BlockPos, BlockState> bipredicate = (p, s) ->
        {
            return s.isIn(this) && s.get(TIP_DIRECTION) == direction;
        };
        return findBlockVertical(worldIn, pos, direction.getOpposite().getAxisDirection(), bipredicate, (s) ->
        {
            return !s.isIn(this);
        }, maxLength);
    }

    private static boolean isValidPointedDripstonePlacement(IWorldReader worldIn, BlockPos pos, Direction dir)
    {
        BlockPos blockpos = pos.offset(dir.getOpposite());
        BlockState blockstate = worldIn.getBlockState(blockpos);
        return blockstate.isSolidSide(worldIn, blockpos, dir) || blockstate.getBlock() instanceof ModernPointedDripstoneBlock && blockstate.get(TIP_DIRECTION) == dir;
    }

    private boolean isTip(BlockState state, boolean allowMerged)
    {
        if (!state.isIn(this))
        {
            return false;
        }
        else
        {
            DripstoneThickness dripstonethickness = state.get(THICKNESS);
            return dripstonethickness == DripstoneThickness.TIP || allowMerged && dripstonethickness == DripstoneThickness.TIP_MERGE;
        }
    }

    private boolean isUnmergedTipWithDirection(BlockState state, Direction dir)
    {
        return this.isTip(state, false) && state.get(TIP_DIRECTION) == dir;
    }

    private boolean isStalactite(BlockState state)
    {
        return this.isPointedDripstoneWithDirection(state, Direction.DOWN);
    }

    private boolean isStalagmite(BlockState state)
    {
        return this.isPointedDripstoneWithDirection(state, Direction.UP);
    }

    private boolean isStalactiteStartPos(BlockState state, IWorldReader worldIn, BlockPos pos)
    {
        return this.isStalactite(state) && !worldIn.getBlockState(pos.up()).isIn(this);
    }

    private boolean isPointedDripstoneWithDirection(BlockState state, Direction dir)
    {
        return state.isIn(this) && state.get(TIP_DIRECTION) == dir;
    }

    private Optional<ModernPointedDripstoneBlock.FluidInfo> getFluidAboveStalactite(World worldIn, BlockPos pos, BlockState state)
    {
        if (!this.isStalactite(state))
        {
            return Optional.empty();
        }
        else
        {
            Optional<BlockPos> optional = this.findRootBlock(worldIn, pos, state, MAX_SEARCH_LENGTH_WHEN_CHECKING_DRIP_TYPE);

            if (!optional.isPresent())
            {
                return Optional.empty();
            }
            else
            {
                BlockPos blockpos = optional.get().up();
                return Optional.of(new ModernPointedDripstoneBlock.FluidInfo(blockpos, worldIn.getFluidState(blockpos).getFluid()));
            }
        }
    }

    private static boolean canFillCauldron(Fluid fluid)
    {
        return fluid == Fluids.LAVA || fluid == Fluids.WATER;
    }

    /** Official: {@code state.is(Blocks.DRIPSTONE_BLOCK) && above.is(Blocks.WATER) && above.getFluidState().isSource()}. */
    private static boolean canGrow(BlockState state, BlockState above)
    {
        if (dripstoneBlock == null)
        {
            dripstoneBlock = Registry.BLOCK.getOrDefault(new ResourceLocation("dripstone_block"));
        }

        return dripstoneBlock != Blocks.AIR && state.isIn(dripstoneBlock) && above.isIn(Blocks.WATER) && above.getFluidState().isSource();
    }

    /**
     * Official uses {@code DRIPPING_DRIPSTONE_WATER}/{@code DRIPPING_DRIPSTONE_LAVA}, neither of which
     * exists in 1.16.4; falling back to the plain dripping particles.
     */
    private static BasicParticleType getDripParticle(Fluid fluid)
    {
        return fluid == Fluids.LAVA || fluid == Fluids.FLOWING_LAVA ? ParticleTypes.DRIPPING_LAVA : ParticleTypes.DRIPPING_WATER;
    }

    private static Optional<BlockPos> findBlockVertical(IWorld worldIn, BlockPos pos, Direction.AxisDirection axisDir, BiPredicate<BlockPos, BlockState> continuePredicate, Predicate<BlockState> foundPredicate, int maxLength)
    {
        Direction direction = Direction.getFacingFromAxisDirection(Direction.Axis.Y, axisDir);
        BlockPos.Mutable mutable = pos.toMutable();

        for (int i = 1; i < maxLength; ++i)
        {
            mutable.move(direction);
            BlockState blockstate = worldIn.getBlockState(mutable);

            if (foundPredicate.test(blockstate))
            {
                return Optional.of(mutable.toImmutable());
            }

            // Official: level.isOutsideBuildHeight(y); 1.16.4 world height is fixed at 0..255.
            if (mutable.getY() < 0 || mutable.getY() >= 256 || !continuePredicate.test(mutable, blockstate))
            {
                return Optional.empty();
            }
        }

        return Optional.empty();
    }

    private static boolean canDripThrough(IBlockReader reader, BlockPos pos, BlockState state)
    {
        if (state.isAir())
        {
            return true;
        }
        else if (state.isOpaqueCube(reader, pos))
        {
            return false;
        }
        else if (!state.getFluidState().isEmpty())
        {
            return false;
        }
        else
        {
            VoxelShape voxelshape = state.getCollisionShape(reader, pos);
            return !VoxelShapes.compare(REQUIRED_SPACE_TO_DRIP_THROUGH_NON_SOLID_BLOCK, voxelshape, IBooleanFunction.AND);
        }
    }

    static final class FluidInfo
    {
        final BlockPos pos;
        final Fluid fluid;

        FluidInfo(BlockPos pos, Fluid fluid)
        {
            this.pos = pos;
            this.fluid = fluid;
        }
    }
}
