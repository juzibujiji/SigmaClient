/*
 * Ported from OpenSSNGScaffoldAndClutch (https://github.com/zyyzs/OpenSSNGScaffoldAndClutch)
 * Original: dev.southside.utils.BlockData (referenced by upstream sources; not published in
 * the upstream repo — reconstructed from usage: record-style accessors pos() and facing(),
 * constructed as new BlockData(BlockPos, Direction)).
 *
 */
package com.mentalfrostbyte.jello.southside.utils;

import net.minecraft.util.Direction;
import net.minecraft.util.math.BlockPos;

public record SSBlockData(BlockPos pos, Direction facing) {
}
