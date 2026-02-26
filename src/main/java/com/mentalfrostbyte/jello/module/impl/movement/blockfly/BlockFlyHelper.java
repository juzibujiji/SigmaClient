package com.mentalfrostbyte.jello.module.impl.movement.blockfly;

import com.mentalfrostbyte.jello.util.game.world.PositionFacing;
import com.mentalfrostbyte.jello.util.game.world.blocks.BlockUtil;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.util.Direction;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.List;

public class BlockFlyHelper {
    public static boolean method35028(List<PositionFacing> list) {
		if (!list.isEmpty()) {
			BlockPos blockPos = list.get(0).blockPos();
			PositionFacing[] possible = new PositionFacing[]{
					new PositionFacing(blockPos.north(), Direction.SOUTH),
					new PositionFacing(blockPos.east(), Direction.WEST),
					new PositionFacing(blockPos.south(), Direction.NORTH),
					new PositionFacing(blockPos.west(), Direction.EAST),
					new PositionFacing(blockPos.down(), Direction.UP),
					new PositionFacing(blockPos.up(), Direction.DOWN)
			};

			for (PositionFacing var8 : possible) {
				if (BlockUtil.getBlockFromPosition(var8.blockPos()) != Blocks.AIR) {
					return true;
				}
			}

		}
		return false;
	}

    public static List<PositionFacing> reverseValues(List<PositionFacing> list) {
        var var3 = new ArrayList<PositionFacing>();

        for (int i = list.size() - 1; i >= 0; i--) {
            var3.add(list.get(i));
        }

        return var3;
    }

    public static List<PositionFacing> method35030(Block var0, BlockPos var1, int var2) {
        ArrayList var5 = new ArrayList();
        if (var1 != null && var2 >= 0) {
            if (!BlockUtil.method34538(var0, var1)) {
                return var5;
            } else {
                PositionFacing[] tryThese = new PositionFacing[]{
                        new PositionFacing(var1.up(), Direction.DOWN),
                        new PositionFacing(var1.north(), Direction.SOUTH),
                        new PositionFacing(var1.east(), Direction.WEST),
                        new PositionFacing(var1.south(), Direction.NORTH),
                        new PositionFacing(var1.west(), Direction.EAST),
                        new PositionFacing(var1.down(), Direction.UP)
                };

                for (PositionFacing var10 : tryThese) {
                    if (!BlockUtil.method34538(var0, var10.blockPos())) {
                        var5.add(var10);
                        return var5;
                    }
                }

                for (int i = 1; i < var2; i++) {
                    for (PositionFacing facing : tryThese) {
                        var var12 = method35030(var0, facing.blockPos(), i);
                        if (method35028(reverseValues(var12))) {
                            var5.addAll(var12);
                            return(var5.size() <= 1 ? var5 : reverseValues(var5));
                        }
                    }
                }

                return var5;
            }
        } else {
            return var5;
        }
    }
}

