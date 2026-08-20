package net.minecraft.block;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import javax.annotation.Nullable;
import net.minecraft.util.Direction;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockReader;
import net.minecraft.world.IWorld;

/**
 * 1.21.11 backport of {@code net.minecraft.world.level.block.MultifaceSpreader}.
 *
 * <p>Straight port; the official stream pipelines are rewritten as plain loops because 1.16.4's
 * {@code Direction} has neither {@code stream()} nor {@code allShuffled(RandomSource)}.
 */
public class ModernMultifaceSpreader
{
    public static final ModernMultifaceSpreader.SpreadType[] DEFAULT_SPREAD_ORDER = new ModernMultifaceSpreader.SpreadType[] {ModernMultifaceSpreader.SpreadType.SAME_POSITION, ModernMultifaceSpreader.SpreadType.SAME_PLANE, ModernMultifaceSpreader.SpreadType.WRAP_AROUND};
    private final ModernMultifaceSpreader.SpreadConfig config;

    public ModernMultifaceSpreader(ModernMultifaceBlock block)
    {
        this(new ModernMultifaceSpreader.DefaultSpreaderConfig(block));
    }

    public ModernMultifaceSpreader(ModernMultifaceSpreader.SpreadConfig config)
    {
        this.config = config;
    }

    private static List<Direction> allShuffled(Random rand)
    {
        List<Direction> list = new ArrayList<>(6);
        Collections.addAll(list, Direction.values());
        Collections.shuffle(list, rand);
        return list;
    }

    public boolean canSpreadInAnyDirection(BlockState state, IBlockReader reader, BlockPos pos, Direction fromFace)
    {
        for (Direction direction : Direction.values())
        {
            if (this.getSpreadFromFaceTowardDirection(state, reader, pos, fromFace, direction, true) != null)
            {
                return true;
            }
        }

        return false;
    }

    @Nullable
    public ModernMultifaceSpreader.SpreadPos spreadFromRandomFaceTowardRandomDirection(BlockState state, IWorld world, BlockPos pos, Random rand)
    {
        for (Direction direction : allShuffled(rand))
        {
            if (this.config.canSpreadFrom(state, direction))
            {
                ModernMultifaceSpreader.SpreadPos spreadpos = this.spreadFromFaceTowardRandomDirection(state, world, pos, direction, rand, false);

                if (spreadpos != null)
                {
                    return spreadpos;
                }
            }
        }

        return null;
    }

    public long spreadAll(BlockState state, IWorld world, BlockPos pos, boolean markForPostprocessing)
    {
        long i = 0L;

        for (Direction direction : Direction.values())
        {
            if (this.config.canSpreadFrom(state, direction))
            {
                i += this.spreadFromFaceTowardAllDirections(state, world, pos, direction, markForPostprocessing);
            }
        }

        return i;
    }

    @Nullable
    public ModernMultifaceSpreader.SpreadPos spreadFromFaceTowardRandomDirection(BlockState state, IWorld world, BlockPos pos, Direction fromFace, Random rand, boolean markForPostprocessing)
    {
        for (Direction direction : allShuffled(rand))
        {
            ModernMultifaceSpreader.SpreadPos spreadpos = this.spreadFromFaceTowardDirection(state, world, pos, fromFace, direction, markForPostprocessing);

            if (spreadpos != null)
            {
                return spreadpos;
            }
        }

        return null;
    }

    private long spreadFromFaceTowardAllDirections(BlockState state, IWorld world, BlockPos pos, Direction fromFace, boolean markForPostprocessing)
    {
        long i = 0L;

        for (Direction direction : Direction.values())
        {
            if (this.spreadFromFaceTowardDirection(state, world, pos, fromFace, direction, markForPostprocessing) != null)
            {
                ++i;
            }
        }

        return i;
    }

    @Nullable
    public ModernMultifaceSpreader.SpreadPos spreadFromFaceTowardDirection(BlockState state, IWorld world, BlockPos pos, Direction fromFace, Direction towards, boolean markForPostprocessing)
    {
        ModernMultifaceSpreader.SpreadPos spreadpos = this.getSpreadFromFaceTowardDirection(state, world, pos, fromFace, towards, true);
        return spreadpos == null ? null : this.spreadToFace(world, spreadpos, markForPostprocessing);
    }

    /**
     * Official takes a {@code SpreadPredicate}; the only predicate ever passed is
     * {@code config::canSpreadInto}, so the flag simply selects between "test spreadability" and
     * "accept the first candidate position".
     */
    @Nullable
    public ModernMultifaceSpreader.SpreadPos getSpreadFromFaceTowardDirection(BlockState state, IBlockReader reader, BlockPos pos, Direction fromFace, Direction towards, boolean testCanSpreadInto)
    {
        if (towards.getAxis() == fromFace.getAxis())
        {
            return null;
        }
        else if (this.config.isOtherBlockValidAsSource(state) || this.config.hasFace(state, fromFace) && !this.config.hasFace(state, towards))
        {
            for (ModernMultifaceSpreader.SpreadType spreadtype : this.config.getSpreadTypes())
            {
                ModernMultifaceSpreader.SpreadPos spreadpos = spreadtype.getSpreadPos(pos, towards, fromFace);

                if (!testCanSpreadInto || this.config.canSpreadInto(reader, pos, spreadpos))
                {
                    return spreadpos;
                }
            }

            return null;
        }
        else
        {
            return null;
        }
    }

    @Nullable
    public ModernMultifaceSpreader.SpreadPos spreadToFace(IWorld world, ModernMultifaceSpreader.SpreadPos spreadPos, boolean markForPostprocessing)
    {
        BlockState blockstate = world.getBlockState(spreadPos.pos);
        return this.config.placeBlock(world, spreadPos, blockstate, markForPostprocessing) ? spreadPos : null;
    }

    public static class DefaultSpreaderConfig implements ModernMultifaceSpreader.SpreadConfig
    {
        protected ModernMultifaceBlock block;

        public DefaultSpreaderConfig(ModernMultifaceBlock block)
        {
            this.block = block;
        }

        @Nullable
        public BlockState getStateForPlacement(BlockState state, IBlockReader reader, BlockPos pos, Direction face)
        {
            return this.block.getStateForPlacement(state, reader, pos, face);
        }

        protected boolean stateCanBeReplaced(IBlockReader reader, BlockPos fromPos, BlockPos toPos, Direction face, BlockState state)
        {
            return state.isAir() || state.isIn(this.block) || state.isIn(Blocks.WATER) && state.getFluidState().isSource();
        }

        public boolean canSpreadInto(IBlockReader reader, BlockPos fromPos, ModernMultifaceSpreader.SpreadPos spreadPos)
        {
            BlockState blockstate = reader.getBlockState(spreadPos.pos);
            return this.stateCanBeReplaced(reader, fromPos, spreadPos.pos, spreadPos.face, blockstate) && this.block.isValidStateForPlacement(reader, blockstate, spreadPos.pos, spreadPos.face);
        }
    }

    public interface SpreadConfig
    {
        @Nullable
        BlockState getStateForPlacement(BlockState state, IBlockReader reader, BlockPos pos, Direction face);

        boolean canSpreadInto(IBlockReader reader, BlockPos fromPos, ModernMultifaceSpreader.SpreadPos spreadPos);

    default ModernMultifaceSpreader.SpreadType[] getSpreadTypes()
        {
            return DEFAULT_SPREAD_ORDER;
        }

    default boolean hasFace(BlockState state, Direction direction)
        {
            return ModernMultifaceBlock.hasFace(state, direction);
        }

    default boolean isOtherBlockValidAsSource(BlockState state)
        {
            return false;
        }

    default boolean canSpreadFrom(BlockState state, Direction direction)
        {
            return this.isOtherBlockValidAsSource(state) || this.hasFace(state, direction);
        }

    default boolean placeBlock(IWorld world, ModernMultifaceSpreader.SpreadPos spreadPos, BlockState state, boolean markForPostprocessing)
        {
            BlockState blockstate = this.getStateForPlacement(state, world, spreadPos.pos, spreadPos.face);

            if (blockstate != null)
            {
                if (markForPostprocessing)
                {
                    world.getChunk(spreadPos.pos).markBlockForPostprocessing(spreadPos.pos);
                }

                return world.setBlockState(spreadPos.pos, blockstate, 2);
            }
            else
            {
                return false;
            }
        }
    }

    public static final class SpreadPos
    {
        public final BlockPos pos;
        public final Direction face;

        public SpreadPos(BlockPos pos, Direction face)
        {
            this.pos = pos;
            this.face = face;
        }
    }

    public static enum SpreadType
    {
        SAME_POSITION
        {
            public ModernMultifaceSpreader.SpreadPos getSpreadPos(BlockPos pos, Direction towards, Direction fromFace)
            {
                return new ModernMultifaceSpreader.SpreadPos(pos, towards);
            }
        },
        SAME_PLANE
        {
            public ModernMultifaceSpreader.SpreadPos getSpreadPos(BlockPos pos, Direction towards, Direction fromFace)
            {
                return new ModernMultifaceSpreader.SpreadPos(pos.offset(towards), fromFace);
            }
        },
        WRAP_AROUND
        {
            public ModernMultifaceSpreader.SpreadPos getSpreadPos(BlockPos pos, Direction towards, Direction fromFace)
            {
                return new ModernMultifaceSpreader.SpreadPos(pos.offset(towards).offset(fromFace), towards.getOpposite());
            }
        };

        public abstract ModernMultifaceSpreader.SpreadPos getSpreadPos(BlockPos pos, Direction towards, Direction fromFace);
    }
}
