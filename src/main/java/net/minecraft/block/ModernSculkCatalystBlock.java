package net.minecraft.block;

import java.util.Random;
import net.minecraft.state.BooleanProperty;
import net.minecraft.state.StateContainer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.server.ServerWorld;

/**
 * 1.21.11 backport of {@code net.minecraft.world.level.block.SculkCatalystBlock} - PARTIAL.
 *
 * <p>State properties (official blocks.json, 2 states): {@code bloom=[true,false]}. Note the
 * official Java field is called {@code PULSE} but the serialized property name is
 * {@code BlockStateProperties.BLOOM} == {@code "bloom"}; the serialized name is what matters for
 * cross-version state mapping. 1.16.4 has no {@code bloom} property, so it is created here.
 *
 * <p>Ported: the state property and {@code tick} (which clears {@code bloom} again).
 *
 * <p>NOT ported: everything driven by {@code SculkCatalystBlockEntity} - reacting to nearby mob
 * deaths, charging and spreading sculk via {@code SculkSpreader}, the bloom particle/sound effect,
 * and {@code spawnAfterBreak}'s {@code tryDropExperience(ConstantInt.of(5))}. 1.16.4 has no
 * {@code SculkSpreader}, no {@code GameEvent} system and no experience-drop helper on Block.
 * Official light level is a static 6, so no dynamic light hook is needed.
 */
public class ModernSculkCatalystBlock extends Block
{
    /** Official field name is PULSE; serialized name is "bloom". */
    public static final BooleanProperty BLOOM = BooleanProperty.create("bloom");

    public ModernSculkCatalystBlock(AbstractBlock.Properties properties)
    {
        super(properties);
        this.setDefaultState(this.stateContainer.getBaseState().with(BLOOM, Boolean.valueOf(false)));
    }

    protected void fillStateContainer(StateContainer.Builder<Block, BlockState> builder)
    {
        builder.add(BLOOM);
    }

    public void tick(BlockState state, ServerWorld worldIn, BlockPos pos, Random rand)
    {
        if (state.get(BLOOM))
        {
            worldIn.setBlockState(pos, state.with(BLOOM, Boolean.valueOf(false)), 3);
        }
    }
}
