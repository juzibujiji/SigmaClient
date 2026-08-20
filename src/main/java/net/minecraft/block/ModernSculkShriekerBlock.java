package net.minecraft.block;

import java.util.Random;
import javax.annotation.Nullable;
import net.minecraft.fluid.FluidState;
import net.minecraft.fluid.Fluids;
import net.minecraft.item.BlockItemUseContext;
import net.minecraft.state.BooleanProperty;
import net.minecraft.state.StateContainer;
import net.minecraft.state.properties.BlockStateProperties;
import net.minecraft.util.Direction;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.shapes.ISelectionContext;
import net.minecraft.util.math.shapes.VoxelShape;
import net.minecraft.world.IBlockReader;
import net.minecraft.world.IWorld;
import net.minecraft.world.server.ServerWorld;

/**
 * 1.21.11 backport of {@code net.minecraft.world.level.block.SculkShriekerBlock} - PARTIAL.
 *
 * <p>State properties (official blocks.json, 8 states): {@code can_summon=[true,false]},
 * {@code shrieking=[true,false]}, {@code waterlogged=[true,false]}. 1.16.4 has neither
 * {@code shrieking} nor {@code can_summon}, so both are created here with the official serialized
 * names.
 *
 * <p>Shape: official {@code SHAPE_COLLISION = Block.column(16.0, 0.0, 8.0)} expands
 * (Block.java:184-188) to {@code box(0, 0, 0, 16, 8, 16)}. It is used for both the collision shape
 * and the light-occlusion shape ({@code useShapeForLightOcclusion} -> 1.16.4's
 * {@code isTransparent}); the official class does not override the outline shape.
 *
 * <p>Ported: state properties, collision/occlusion shape, waterlogging, and {@code tick} clearing
 * {@code shrieking}.
 *
 * <p>NOT ported: everything driven by {@code SculkShriekerBlockEntity} - the
 * {@code stepOn}/{@code tryShriek} warning sequence, the vibration listener
 * ({@code VibrationSystem.Ticker}), darkness effect, warden summoning, and
 * {@code spawnAfterBreak}'s {@code tryDropExperience(ConstantInt.of(5))}. 1.16.4 has no
 * {@code GameEvent}/vibration system, no Warden and no experience-drop helper on Block.
 * Official light level is 0, so no dynamic light hook is needed.
 */
public class ModernSculkShriekerBlock extends Block implements IWaterLoggable
{
    public static final BooleanProperty SHRIEKING = BooleanProperty.create("shrieking");
    public static final BooleanProperty WATERLOGGED = BlockStateProperties.WATERLOGGED;
    public static final BooleanProperty CAN_SUMMON = BooleanProperty.create("can_summon");
    private static final VoxelShape SHAPE_COLLISION = Block.makeCuboidShape(0.0D, 0.0D, 0.0D, 16.0D, 8.0D, 16.0D);
    /** Official: {@code SHAPE_COLLISION.max(Direction.Axis.Y)} == 8/16. */
    public static final double TOP_Y = 0.5D;

    public ModernSculkShriekerBlock(AbstractBlock.Properties properties)
    {
        super(properties);
        this.setDefaultState(this.stateContainer.getBaseState().with(SHRIEKING, Boolean.valueOf(false)).with(WATERLOGGED, Boolean.valueOf(false)).with(CAN_SUMMON, Boolean.valueOf(false)));
    }

    protected void fillStateContainer(StateContainer.Builder<Block, BlockState> builder)
    {
        builder.add(SHRIEKING);
        builder.add(WATERLOGGED);
        builder.add(CAN_SUMMON);
    }

    public void tick(BlockState state, ServerWorld worldIn, BlockPos pos, Random rand)
    {
        if (state.get(SHRIEKING))
        {
            worldIn.setBlockState(pos, state.with(SHRIEKING, Boolean.valueOf(false)), 3);
        }
    }

    public VoxelShape getCollisionShape(BlockState state, IBlockReader worldIn, BlockPos pos, ISelectionContext context)
    {
        return SHAPE_COLLISION;
    }

    /** 1.16.4 name for {@code getOcclusionShape}. */
    public VoxelShape getRenderShape(BlockState state, IBlockReader worldIn, BlockPos pos)
    {
        return SHAPE_COLLISION;
    }

    /** 1.16.4 name for {@code useShapeForLightOcclusion}. */
    public boolean isTransparent(BlockState state)
    {
        return true;
    }

    public BlockState updatePostPlacement(BlockState stateIn, Direction facing, BlockState facingState, IWorld worldIn, BlockPos currentPos, BlockPos facingPos)
    {
        if (stateIn.get(WATERLOGGED))
        {
            worldIn.getPendingFluidTicks().scheduleTick(currentPos, Fluids.WATER, Fluids.WATER.getTickRate(worldIn));
        }

        return super.updatePostPlacement(stateIn, facing, facingState, worldIn, currentPos, facingPos);
    }

    @Nullable
    public BlockState getStateForPlacement(BlockItemUseContext context)
    {
        return this.getDefaultState().with(WATERLOGGED, Boolean.valueOf(context.getWorld().getFluidState(context.getPos()).getFluid() == Fluids.WATER));
    }

    public FluidState getFluidState(BlockState state)
    {
        return state.get(WATERLOGGED) ? Fluids.WATER.getStillFluidState(false) : super.getFluidState(state);
    }
}
