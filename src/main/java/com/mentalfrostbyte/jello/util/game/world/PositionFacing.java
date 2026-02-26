package com.mentalfrostbyte.jello.util.game.world;

import net.minecraft.util.Direction;
import net.minecraft.util.math.BlockPos;

public record PositionFacing(BlockPos blockPos, Direction direction) {
}
