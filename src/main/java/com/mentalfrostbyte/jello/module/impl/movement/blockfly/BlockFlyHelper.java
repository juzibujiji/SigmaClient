package com.mentalfrostbyte.jello.module.impl.movement.blockfly;

import com.mentalfrostbyte.jello.util.game.player.MovementPredictor;
import com.mentalfrostbyte.jello.util.game.player.rotation.RotationCore;
import com.mentalfrostbyte.jello.util.game.world.PositionFacing;
import com.mentalfrostbyte.jello.util.game.world.blocks.BlockUtil;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Direction;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.BlockRayTraceResult;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RayTraceContext;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.shapes.VoxelShape;
import net.minecraft.util.math.vector.Vector3d;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class BlockFlyHelper {
    private static final Minecraft mc = Minecraft.getInstance();
    /** findPlacement 最多看多少个格子 */
    private static final int MAX_SEARCH = 64;
    /** 往外找路时只往下和水平方向，不往目标上面搭 */
    private static final Direction[] SEARCH_DIRECTIONS = {
            Direction.DOWN, Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST
    };
    /** 面的四周各留出边长的这么多不瞄，太靠边的点服务器容易算成打到别的面 */
    private static final double FACE_MARGIN = 0.15D;
    /** 预瞄时假设在越过边缘多远的地方放下一格 */
    private static final double EDGE_OVERSHOOT = 0.2D;

    /**
     * 一次放置：贴着 support 的 face 面放，从 eyes 瞄面上的 point。
     * preAim 表示人还站在方块上，eyes 是预计走到边上时的眼睛位置。
     */
    public record Placement(BlockPos support, Direction face, Vector3d point, Vector3d eyes, boolean preAim) {
        /** 按 (yaw, pitch) 放能不能打中这个面；Strict Predict 放置用的是这一 tick 移动后的位置 */
        public boolean hitBy(float yaw, float pitch, boolean strict) {
            Vector3d from = strict && !this.preAim ? MovementPredictor.predictEyes(yaw) : this.eyes;
            return BlockUtil.hitsFace(from, new float[]{yaw, pitch}, this.support, this.face);
        }

        /** 瞄 point 要转到的角度 */
        public float[] rotation(boolean strict) {
            return this.preAim
                    ? BlockUtil.rotationTo(this.eyes, this.point)
                    : BlockUtil.rotationsToPoint(this.point, this.support, this.face, strict);
        }
    }

    /**
     * 找贴着哪个方块的哪个面放、瞄面上哪一点，才能把 target 这一格填上。找不到返回 null。
     * <ol>
     *     <li>target 已经有方块（人还站在上面）：沿水平速度找到走出这一格的边缘，按越过边缘 0.2 格时的眼睛位置
     *     提前瞄下一格。走到边上时角度已经到位，不会越过边缘才回头甩；</li>
     *     <li>从 target 开始一圈一圈往外找（先 target，再旁边和下面一格，每圈按离脚的距离排），第一个能放的格子就放。
     *     target 没有支撑时先放旁边那格，下一 tick 再放 target；</li>
     *     <li>贴哪个面：现在的视线已经打在能用的面上就用它，不转头；否则挑眼睛最正对的面，
     *     瞄面上（四周留 15%）离现在视线最近、看得到的点。</li>
     * </ol>
     *
     * @param allowUp 能不能贴着方块顶面放（Telly 不按跳跃键时不往上搭）
     */
    public static Placement findPlacement(BlockPos target, boolean allowUp) {
        Vector3d feet = mc.player.getPositionVec();
        boolean preAim = BlockUtil.isValidBlockPosition(target);
        if (preAim) {
            feet = edgeAhead(feet, target);
            if (feet == null) {
                return null;
            }

            target = new BlockPos(feet.x, target.getY(), feet.z);
            if (BlockUtil.isValidBlockPosition(target)) {
                return null;
            }
        }

        Vector3d eyes = feet.add(0.0D, mc.player.getEyeHeight(), 0.0D);
        Vector3d look = mc.player.getLookCustom(1.0F, RotationCore.lastYaw, RotationCore.lastPitch);
        double reach = mc.playerController.getBlockReachDistance();
        BlockRayTraceResult current = mc.world.rayTraceBlocks(new RayTraceContext(eyes, eyes.add(look.scale(reach)),
                RayTraceContext.BlockMode.OUTLINE, RayTraceContext.FluidMode.NONE, mc.player));

        List<BlockPos> ring = List.of(target);
        Set<BlockPos> visited = new HashSet<>(ring);
        while (!ring.isEmpty()) {
            for (BlockPos cell : ring) {
                Placement placement = placementFor(cell, eyes, look, reach, current, allowUp, preAim);
                if (placement != null) {
                    return placement;
                }
            }

            ring = nextRing(ring, visited, feet, eyes, reach);
        }

        return null;
    }

    /** 人站在 cell 上：沿水平速度走出 cell 的那一点再往前 EDGE_OVERSHOOT。没在动返回 null */
    private static Vector3d edgeAhead(Vector3d feet, BlockPos cell) {
        Vector3d motion = mc.player.getMotion();
        double speed = Math.sqrt(motion.x * motion.x + motion.z * motion.z);
        if (speed < 0.01D) {
            return null;
        }

        double dx = motion.x / speed;
        double dz = motion.z / speed;
        double distance = Math.min(exitDistance(feet.x, cell.getX(), dx), exitDistance(feet.z, cell.getZ(), dz))
                + EDGE_OVERSHOOT;
        return feet.add(dx * distance, 0.0D, dz * distance);
    }

    /** 从 position 沿 direction 走多远离开 [min, min + 1] 这一格 */
    private static double exitDistance(double position, int min, double direction) {
        if (direction > 1.0E-6D) {
            return (min + 1 - position) / direction;
        }

        return direction < -1.0E-6D ? (min - position) / direction : Double.MAX_VALUE;
    }

    /** 填 cell 的放法：现在的视线已经打在 cell 旁边能用的面上就用它，否则挑眼睛最正对、看得到的支撑面 */
    private static Placement placementFor(BlockPos cell, Vector3d eyes, Vector3d look, double reach,
                                          BlockRayTraceResult current, boolean allowUp, boolean preAim) {
        if (!BlockUtil.canPlaceBlockAt(Blocks.STONE, cell)) {
            return null;
        }

        if (current.getType() == RayTraceResult.Type.BLOCK && current.getPos().offset(current.getFace()).equals(cell)
                && (allowUp || current.getFace() != Direction.UP) && BlockUtil.isValidBlockPosition(current.getPos())) {
            return new Placement(current.getPos(), current.getFace(), current.getHitVec(), eyes, preAim);
        }

        Placement best = null;
        double bestFacing = 0.0D;
        for (Direction face : Direction.values()) {
            BlockPos support = cell.offset(face.getOpposite());
            if (face == Direction.UP && !allowUp || !BlockUtil.isValidBlockPosition(support)) {
                continue;
            }

            Vector3d point = visiblePoint(support, face, eyes, look, reach);
            if (point == null) {
                continue;
            }

            // 1 是正对着面，接近 0 是贴着面看；越正对越不容易点到别的面上
            Vector3d toEyes = eyes.subtract(point);
            double facing = (toEyes.x * face.getXOffset() + toEyes.y * face.getYOffset() + toEyes.z * face.getZOffset())
                    / toEyes.length();
            if (facing > bestFacing) {
                bestFacing = facing;
                best = new Placement(support, face, point, eyes, preAim);
            }
        }

        return best;
    }

    /**
     * support 的 face 面上要瞄的点：先取离现在视线最近的点（视线和面所在平面的交点夹到面里，视线背对这个面就取面中心），
     * 被挡住就在面上 3×3 个点里挑离视线最近、看得到的。眼睛在面背后、够不着或者整个面都被挡住返回 null。
     */
    private static Vector3d visiblePoint(BlockPos support, Direction face, Vector3d eyes, Vector3d look, double reach) {
        VoxelShape shape = mc.world.getBlockState(support).getShape(mc.world, support);
        AxisAlignedBB box = (shape.isEmpty() ? new AxisAlignedBB(0.0D, 0.0D, 0.0D, 1.0D, 1.0D, 1.0D)
                : shape.getBoundingBox()).offset(support);
        Direction.Axis axis = face.getAxis();
        double plane = face.getAxisDirection() == Direction.AxisDirection.POSITIVE ? box.getMax(axis) : box.getMin(axis);
        if ((eyes.getCoordinate(axis) - plane) * face.getAxisDirection().getOffset() <= 0.0D) {
            return null;
        }

        double along = look.getCoordinate(axis);
        double t = Math.abs(along) < 1.0E-6D ? -1.0D : (plane - eyes.getCoordinate(axis)) / along;
        Vector3d nearest = pointOnFace(box, axis, plane, t > 0.0D ? eyes.add(look.scale(t)) : box.getCenter());
        if (isVisible(eyes, nearest, support, face, reach)) {
            return nearest;
        }

        Vector3d best = null;
        double bestAlignment = -2.0D;
        for (double a = 0.0D; a <= 1.0D; a += 0.5D) {
            for (double b = 0.0D; b <= 1.0D; b += 0.5D) {
                Vector3d point = pointOnFace(box, axis, plane, new Vector3d(
                        MathHelper.lerp(a, box.minX, box.maxX),
                        MathHelper.lerp(axis == Direction.Axis.X ? a : b, box.minY, box.maxY),
                        MathHelper.lerp(b, box.minZ, box.maxZ)));
                double alignment = look.dotProduct(point.subtract(eyes).normalize());
                if (alignment > bestAlignment && isVisible(eyes, point, support, face, reach)) {
                    bestAlignment = alignment;
                    best = point;
                }
            }
        }

        return best;
    }

    /** 把 hint 放到面上：面所在的轴取 plane，另外两个轴夹在留过边的范围里 */
    private static Vector3d pointOnFace(AxisAlignedBB box, Direction.Axis axis, double plane, Vector3d hint) {
        return new Vector3d(
                axis == Direction.Axis.X ? plane : inFace(hint.x, box.minX, box.maxX),
                axis == Direction.Axis.Y ? plane : inFace(hint.y, box.minY, box.maxY),
                axis == Direction.Axis.Z ? plane : inFace(hint.z, box.minZ, box.maxZ));
    }

    private static double inFace(double value, double min, double max) {
        double margin = (max - min) * FACE_MARGIN;
        return MathHelper.clamp(value, min + margin, max - margin);
    }

    /** 从 eyes 直线看 point 能不能打到 support 的 face 面：够得着、不被别的方块挡住 */
    private static boolean isVisible(Vector3d eyes, Vector3d point, BlockPos support, Direction face, double reach) {
        Vector3d direction = point.subtract(eyes);
        if (direction.lengthSquared() > reach * reach) {
            return false;
        }

        BlockRayTraceResult hit = mc.world.rayTraceBlocks(new RayTraceContext(eyes,
                point.add(direction.normalize().scale(0.05D)),
                RayTraceContext.BlockMode.OUTLINE, RayTraceContext.FluidMode.NONE, mc.player));
        return hit.getType() == RayTraceResult.Type.BLOCK && hit.getPos().equals(support) && hit.getFace() == face;
    }

    /** 下一圈：从这一圈往下和水平方向扩一格，只要能放、够得着的格子，按离脚的距离排 */
    private static List<BlockPos> nextRing(List<BlockPos> ring, Set<BlockPos> visited, Vector3d feet, Vector3d eyes,
                                           double reach) {
        List<BlockPos> next = new ArrayList<>();
        for (BlockPos cell : ring) {
            for (Direction direction : SEARCH_DIRECTIONS) {
                BlockPos neighbor = cell.offset(direction);
                if (visited.size() < MAX_SEARCH
                        && eyes.squareDistanceTo(Vector3d.copyCentered(neighbor)) < (reach + 1.0D) * (reach + 1.0D)
                        && visited.add(neighbor)
                        && BlockUtil.canPlaceBlockAt(Blocks.STONE, neighbor)) {
                    next.add(neighbor);
                }
            }
        }

        next.sort(Comparator.comparingDouble(cell -> feet.squareDistanceTo(Vector3d.copyCentered(cell))));
        return next;
    }
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
            if (!BlockUtil.canPlaceBlockAt(var0, var1)) {
                return var5;
            } else {
                // Order matters: prefer placing on the top face of the block below (most natural
                // for scaffold), then the four side faces, and only fall back to the bottom face
                // of a block above the target. The previous order put `var1.up(), Direction.DOWN`
                // first, which made the algorithm aim at the ceiling/player's head.
                PositionFacing[] tryThese = new PositionFacing[]{
                        new PositionFacing(var1.down(), Direction.UP),
                        new PositionFacing(var1.north(), Direction.SOUTH),
                        new PositionFacing(var1.east(), Direction.WEST),
                        new PositionFacing(var1.south(), Direction.NORTH),
                        new PositionFacing(var1.west(), Direction.EAST),
                        new PositionFacing(var1.up(), Direction.DOWN)
                };

                for (PositionFacing var10 : tryThese) {
                    // Use a direct solid-block presence check. The previous `!canPlaceBlockAt(...)`
                    // proxy also returned true for "above-player" positions and positions with
                    // entity collision, which let the algorithm pick the player's own body as
                    // a phantom anchor.
                    if (BlockUtil.isValidBlockPosition(var10.blockPos())) {
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

