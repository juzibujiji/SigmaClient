/*
 * Ported from OpenSSNGScaffoldAndClutch (https://github.com/zyyzs/OpenSSNGScaffoldAndClutch)
 * Original: dev.southside.utils.raytrace.ClientRayTraceUtil
 *
 * Port notes (Yarn 1.21 / Fabric -> MCP 1.16.5 / SigmaClient):
 *   MinecraftClient -> Minecraft, Vec3d -> Vector3d, Box -> AxisAlignedBB,
 *   BlockHitResult -> BlockRayTraceResult, BlockPos.ofFloored -> new BlockPos(Vector3d),
 *   Vec3d.fromPolar -> Vector3d.fromPitchYaw, box.raycast -> AxisAlignedBB.rayTrace,
 *   shape.getBoundingBoxes -> VoxelShape.toBoundingBoxList,
 *   state.getCollisionShape(w,p,ShapeContext.of(e)) -> getCollisionShape(w,p,ISelectionContext.forEntity(e)),
 *   state.getOutlineShape -> state.getShape, world.isAir -> world.isAirBlock,
 *   fluidState.isStill -> isSource, player.getBlockInteractionRange -> playerController.getBlockReachDistance,
 *   PlantBlock -> BushBlock, ShortPlantBlock -> TallGrassBlock, FluidBlock -> FlowingFluidBlock,
 *   pos.toCenterPos -> Vector3d.copyCentered(pos).
 *   The upstream obfuscator annotations (@NativeDefine/@BytecodeInline) do not exist here and
 *   were dropped; everything else is a 1:1 port.
 */
package com.mentalfrostbyte.jello.southside.utils.raytrace;

import com.mentalfrostbyte.jello.southside.utils.types.SSRotation;
import net.minecraft.block.AirBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.BushBlock;
import net.minecraft.block.ChestBlock;
import net.minecraft.block.EnderChestBlock;
import net.minecraft.block.FlowingFluidBlock;
import net.minecraft.block.ShulkerBoxBlock;
import net.minecraft.block.SnowBlock;
import net.minecraft.block.TallGrassBlock;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.fluid.FluidState;
import net.minecraft.fluid.Fluids;
import net.minecraft.util.Direction;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.BlockRayTraceResult;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.shapes.ISelectionContext;
import net.minecraft.util.math.shapes.VoxelShape;
import net.minecraft.util.math.shapes.VoxelShapes;
import net.minecraft.util.math.vector.Vector3d;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;
import java.util.function.Predicate;

public final class SSClientRayTraceUtil {
    private static final double EPSILON = 1.0E-7D;
    public static Vector3d eyePos = null;
    private static final Minecraft mc = Minecraft.getInstance();

    public static boolean didHitBlockFace(SSRotation rotation, BlockPos targetPos, Direction expectedFace, boolean strict) {
        return didHitBlockFace(mc.player, rotation.yaw, rotation.pitch, targetPos, expectedFace, strict, SSClientRayTraceUtil::isIgnoredBlock);
    }

    public static boolean didHitBlockFace(PlayerEntity player, float yaw, float pitch, BlockPos targetPos, Direction expectedFace, boolean strict) {
        return didHitBlockFace(player, yaw, pitch, targetPos, expectedFace, strict, SSClientRayTraceUtil::isIgnoredBlock);
    }

    public static boolean didHitBlockFace(SSRotation rotation, BlockPos targetPos, Direction expectedFace, boolean strict, Predicate<BlockState> ignorePredicate) {
        return didHitBlockFace(mc.player, rotation.yaw, rotation.pitch, targetPos, expectedFace, strict, ignorePredicate);
    }

    public static boolean didHitBlockFace(PlayerEntity player, float yaw, float pitch, BlockPos targetPos, Direction expectedFace, boolean strict, Predicate<BlockState> ignorePredicate) {
        if (player == null || expectedFace == null) {
            return false;
        }

        BlockRayTraceResult result = getFacedBlock(yaw, pitch, ignorePredicate);
        if (result == null || targetPos.getX() != result.getPos().getX() || targetPos.getY() != result.getPos().getY() || targetPos.getZ() != result.getPos().getZ() || (expectedFace != result.getFace() && strict)) {
            return false;
        }
        return true;
    }

    public static void updateEyePos() {
        if (mc.player == null) return;
        eyePos = mc.player.getEyePosition(1.0F);
    }

    @Nullable
    private static Direction getHitFace(Vector3d hitPos, AxisAlignedBB box) {
        if (Math.abs(hitPos.x - box.minX) < EPSILON) return Direction.WEST;
        if (Math.abs(hitPos.x - box.maxX) < EPSILON) return Direction.EAST;
        if (Math.abs(hitPos.y - box.minY) < EPSILON) return Direction.DOWN;
        if (Math.abs(hitPos.y - box.maxY) < EPSILON) return Direction.UP;
        if (Math.abs(hitPos.z - box.minZ) < EPSILON) return Direction.NORTH;
        if (Math.abs(hitPos.z - box.maxZ) < EPSILON) return Direction.SOUTH;

        return null;
    }

    public static BlockRayTraceResult getFacedBlock(float yaw, float pitch) {
        return getFacedBlock(yaw, pitch, SSClientRayTraceUtil::isIgnoredBlock);
    }

    public static BlockRayTraceResult getFacedBlock(float yaw, float pitch, Predicate<BlockState> ignorePredicate) { // DDA步进扫描RayTrace，高版本原版自带的raytrace返回有问题我不知道为什么就写了这个
        final Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.playerController == null || eyePos == null) {
            return null;
        }
        final double reachDistance = client.playerController.getBlockReachDistance();
        if (yaw == 0.0f && pitch == 0.0f) {
            return null;
        }

        Vector3d startPos = eyePos;
        Vector3d direction = Vector3d.fromPitchYaw(pitch, yaw);
        Vector3d endPos = startPos.add(direction.scale(reachDistance));
        if (direction.x == 0) direction = new Vector3d(EPSILON, direction.y, direction.z);
        if (direction.y == 0) direction = new Vector3d(direction.x, EPSILON, direction.z);
        if (direction.z == 0) direction = new Vector3d(direction.x, direction.y, EPSILON);

        BlockPos currentPos = new BlockPos(startPos);
        int stepX = (int) Math.signum(direction.x);
        int stepY = (int) Math.signum(direction.y);
        int stepZ = (int) Math.signum(direction.z);

        double nextBoundaryX = (stepX > 0) ? currentPos.getX() + 1 : currentPos.getX();
        double nextBoundaryY = (stepY > 0) ? currentPos.getY() + 1 : currentPos.getY();
        double nextBoundaryZ = (stepZ > 0) ? currentPos.getZ() + 1 : currentPos.getZ();

        double tMaxX = (nextBoundaryX - startPos.x) / direction.x;
        double tMaxY = (nextBoundaryY - startPos.y) / direction.y;
        double tMaxZ = (nextBoundaryZ - startPos.z) / direction.z;

        double tDeltaX = stepX / direction.x;
        double tDeltaY = stepY / direction.y;
        double tDeltaZ = stepZ / direction.z;

        final World world = mc.player.getEntityWorld();
        AxisAlignedBB box;
        while (startPos.distanceTo(Vector3d.copyCentered(currentPos)) <= reachDistance) {
            if (!world.isAirBlock(currentPos)) {
                BlockState state = world.getBlockState(currentPos);
                if (!ignorePredicate.test(state)) {
                    VoxelShape shape;
                    FluidState fluidState = world.getFluidState(currentPos);
                    if (fluidState != null && fluidState.isSource() && fluidState.getFluid() == Fluids.WATER) {
                        shape = VoxelShapes.fullCube();
                    } else {
                        shape = state.getCollisionShape(world, currentPos, ISelectionContext.forEntity(client.player));
                    }

                    if (!shape.isEmpty()) {
                        for (AxisAlignedBB localBox : shape.toBoundingBoxList()) {
                            box = localBox.offset(currentPos);
                            Optional<Vector3d> intercept = box.rayTrace(startPos, endPos);

                            if (intercept.isPresent()) {
                                Vector3d hitVec = intercept.get();
                                Direction side = getHitFaceFromBox(hitVec, box);
                                boolean isInside = box.contains(startPos);
                                return new BlockRayTraceResult(hitVec, side, currentPos, isInside);
                            }
                        }
                    }
                }
            }
            if (tMaxX < tMaxY) {
                if (tMaxX < tMaxZ) {
                    currentPos = currentPos.add(stepX, 0, 0);
                    tMaxX += tDeltaX;
                } else {
                    currentPos = currentPos.add(0, 0, stepZ);
                    tMaxZ += tDeltaZ;
                }
            } else {
                if (tMaxY < tMaxZ) {
                    currentPos = currentPos.add(0, stepY, 0);
                    tMaxY += tDeltaY;
                } else {
                    currentPos = currentPos.add(0, 0, stepZ);
                    tMaxZ += tDeltaZ;
                }
            }
        }
        return null;
    }

    public static boolean isIgnoredBlock(BlockState state) {
        Block block = state.getBlock();
        // Yarn->MCP class mapping: PlantBlock->BushBlock, ShortPlantBlock->TallGrassBlock,
        // FluidBlock->FlowingFluidBlock (TallGrassBlock is a BushBlock subclass in 1.16 but is
        // kept for a faithful 1:1 predicate).
        return block instanceof BushBlock || block instanceof SnowBlock || block instanceof AirBlock || block instanceof TallGrassBlock || block instanceof FlowingFluidBlock;
    }

    private static Direction getHitFaceFromBox(Vector3d hit, AxisAlignedBB box) {
        final double eps = 1e-7;

        if (Math.abs(hit.x - box.minX) <= eps) return Direction.WEST;
        if (Math.abs(hit.x - box.maxX) <= eps) return Direction.EAST;
        if (Math.abs(hit.y - box.minY) <= eps) return Direction.DOWN;
        if (Math.abs(hit.y - box.maxY) <= eps) return Direction.UP;
        if (Math.abs(hit.z - box.minZ) <= eps) return Direction.NORTH;
        if (Math.abs(hit.z - box.maxZ) <= eps) return Direction.SOUTH;

        double dxMin = Math.abs(hit.x - box.minX);
        double dxMax = Math.abs(hit.x - box.maxX);
        double dyMin = Math.abs(hit.y - box.minY);
        double dyMax = Math.abs(hit.y - box.maxY);
        double dzMin = Math.abs(hit.z - box.minZ);
        double dzMax = Math.abs(hit.z - box.maxZ);

        double m = dxMin; Direction d = Direction.WEST;
        if (dxMax < m) { m = dxMax; d = Direction.EAST; }
        if (dyMin < m) { m = dyMin; d = Direction.DOWN; }
        if (dyMax < m) { m = dyMax; d = Direction.UP; }
        if (dzMin < m) { m = dzMin; d = Direction.NORTH; }
        if (dzMax < m) { /* m = dzMax; */ d = Direction.SOUTH; }

        return d;
    }

    public static BlockRayTraceResult getFacedContainerBlock(float yaw, float pitch) {
        final Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.playerController == null || eyePos == null) {
            return null;
        }
        final double reachDistance = client.playerController.getBlockReachDistance();
        Vector3d startPos = eyePos;
        Vector3d direction = Vector3d.fromPitchYaw(pitch, yaw);
        Vector3d endPos = startPos.add(direction.scale(reachDistance));

        if (direction.x == 0) direction = new Vector3d(EPSILON, direction.y, direction.z);
        if (direction.y == 0) direction = new Vector3d(direction.x, EPSILON, direction.z);
        if (direction.z == 0) direction = new Vector3d(direction.x, direction.y, EPSILON);

        BlockPos currentPos = new BlockPos(startPos);
        int stepX = (int) Math.signum(direction.x);
        int stepY = (int) Math.signum(direction.y);
        int stepZ = (int) Math.signum(direction.z);

        double nextBoundaryX = (stepX > 0) ? currentPos.getX() + 1 : currentPos.getX();
        double nextBoundaryY = (stepY > 0) ? currentPos.getY() + 1 : currentPos.getY();
        double nextBoundaryZ = (stepZ > 0) ? currentPos.getZ() + 1 : currentPos.getZ();

        double tMaxX = (nextBoundaryX - startPos.x) / direction.x;
        double tMaxY = (nextBoundaryY - startPos.y) / direction.y;
        double tMaxZ = (nextBoundaryZ - startPos.z) / direction.z;

        double tDeltaX = stepX / direction.x;
        double tDeltaY = stepY / direction.y;
        double tDeltaZ = stepZ / direction.z;

        while (startPos.distanceTo(Vector3d.copyCentered(currentPos)) <= reachDistance) {
            if (!mc.player.getEntityWorld().isAirBlock(currentPos) && isContainerBlock(mc.player.getEntityWorld().getBlockState(currentPos))) {
                AxisAlignedBB currentBox = new AxisAlignedBB(currentPos);
                Optional<Vector3d> intercept = currentBox.rayTrace(startPos, endPos);

                if (intercept.isPresent()) {
                    Vector3d hitVec = intercept.get();
                    Direction side = getHitFace(hitVec, currentBox);
                    boolean isInside = currentBox.contains(startPos);
                    return new BlockRayTraceResult(hitVec, side, currentPos, isInside);
                }
            }

            if (tMaxX < tMaxY) {
                if (tMaxX < tMaxZ) {
                    currentPos = currentPos.add(stepX, 0, 0);
                    tMaxX += tDeltaX;
                } else {
                    currentPos = currentPos.add(0, 0, stepZ);
                    tMaxZ += tDeltaZ;
                }
            } else {
                if (tMaxY < tMaxZ) {
                    currentPos = currentPos.add(0, stepY, 0);
                    tMaxY += tDeltaY;
                } else {
                    currentPos = currentPos.add(0, 0, stepZ);
                    tMaxZ += tDeltaZ;
                }
            }
        }
        return null;
    }

    private static boolean isContainerBlock(BlockState state) {
        return state.getBlock() instanceof ChestBlock ||
                state.getBlock() instanceof EnderChestBlock ||
                state.getBlock() instanceof ShulkerBoxBlock;
    }

    public static double getDistance(float yaw, float pitch, Entity target) {
        Vector3d dir = Vector3d.fromPitchYaw(pitch, yaw).normalize();
        AxisAlignedBB targetBox = target.getBoundingBox();
        return intersectRayAabb(eyePos, dir, targetBox);
    }

    public static boolean didHitEntity(float yaw, float pitch, double range, Entity target) {
        return overBox(yaw, pitch, range, target.getBoundingBox());
    }

    public static boolean overBox(float yawDeg,
                                  float pitchDeg,
                                  double range,
                                  AxisAlignedBB box) {
        if (box == null)
        {
            return false;
        }

        Vector3d start = eyePos;
        World world = Minecraft.getInstance().world;
        Vector3d dir = Vector3d.fromPitchYaw(pitchDeg, yawDeg).normalize();
        if (dir.lengthSquared() < 1e-12) return false;
        double tBox = intersectRayAabb(start, dir, box);
        if (!Double.isFinite(tBox) || tBox < 0.0 || tBox > range) {
            return false;
        }
        if (isOccludedBefore(world, start, dir, tBox, range, mc.player)) {
            return false;
        }
        return true;
    }

    private static boolean isOccludedBefore(World world,
                                            Vector3d origin,
                                            Vector3d dir,
                                            double tStop,
                                            double range,
                                            Entity viewer) {
        int x = MathHelper.floor(origin.x);
        int y = MathHelper.floor(origin.y);
        int z = MathHelper.floor(origin.z);

        int stepX = dir.x > 0 ? 1 : (dir.x < 0 ? -1 : 0);
        int stepY = dir.y > 0 ? 1 : (dir.y < 0 ? -1 : 0);
        int stepZ = dir.z > 0 ? 1 : (dir.z < 0 ? -1 : 0);

        double tMaxX = nextBoundaryT(origin.x, dir.x, x, stepX);
        double tMaxY = nextBoundaryT(origin.y, dir.y, y, stepY);
        double tMaxZ = nextBoundaryT(origin.z, dir.z, z, stepZ);

        double tDeltaX = stepX != 0 ? 1.0 / Math.abs(dir.x) : Double.POSITIVE_INFINITY;
        double tDeltaY = stepY != 0 ? 1.0 / Math.abs(dir.y) : Double.POSITIVE_INFINITY;
        double tDeltaZ = stepZ != 0 ? 1.0 / Math.abs(dir.z) : Double.POSITIVE_INFINITY;
        double tLimit = Math.min(tStop, range);
        if (blockOccludesHere(world, origin, dir, new BlockPos(x, y, z), tStop, viewer)) {
            return true;
        }
        while (true) {
            if (tMaxX < tMaxY) {
                if (tMaxX < tMaxZ) {
                    if (tMaxX > tLimit) break;
                    x += stepX;
                    if (blockOccludesHere(world, origin, dir, new BlockPos(x, y, z), tStop, viewer)) {
                        return true;
                    }
                    tMaxX += tDeltaX;
                } else {
                    if (tMaxZ > tLimit) break;
                    z += stepZ;
                    if (blockOccludesHere(world, origin, dir, new BlockPos(x, y, z), tStop, viewer)) {
                        return true;
                    }
                    tMaxZ += tDeltaZ;
                }
            } else {
                if (tMaxY < tMaxZ) {
                    if (tMaxY > tLimit) break;
                    y += stepY;
                    if (blockOccludesHere(world, origin, dir, new BlockPos(x, y, z), tStop, viewer)) {
                        return true;
                    }
                    tMaxY += tDeltaY;
                } else {
                    if (tMaxZ > tLimit) break;
                    z += stepZ;
                    if (blockOccludesHere(world, origin, dir, new BlockPos(x, y, z), tStop, viewer)) {
                        return true;
                    }
                    tMaxZ += tDeltaZ;
                }
            }
        }
        return false;
    }

    private static double nextBoundaryT(double o, double d, int cell, int step) {
        if (step == 0) return Double.POSITIVE_INFINITY;
        double boundary = step > 0 ? (cell + 1) : cell;
        return (boundary - o) / d;
    }


    private static boolean blockOccludesHere(World world,
                                             Vector3d origin,
                                             Vector3d dir,
                                             BlockPos pos,
                                             double tEntity,
                                             Entity viewer) {
        BlockState state = world.getBlockState(pos);
        if (state.isAir()) return false;
        VoxelShape shape = state.getShape(world, pos, viewer != null ? ISelectionContext.forEntity(viewer) : ISelectionContext.dummy());
        if (shape.isEmpty()) return false;
        for (AxisAlignedBB local : shape.toBoundingBoxList()) {
            AxisAlignedBB box = local.offset(pos);
            double tBox = intersectRayAabb(origin, dir, box);
            if (Double.isFinite(tBox) && tBox >= 0.0 && tBox < tEntity) {
                return true;
            }
        }
        return false;
    }

    public static double intersectRayAabb(Vector3d o, Vector3d d, AxisAlignedBB b) {
        double tMin = 0.0;
        double tMax = Double.POSITIVE_INFINITY;

        if (!axisSlab(o.x, d.x, b.minX, b.maxX, Holder.tmp)) return Double.POSITIVE_INFINITY;
        tMin = Math.max(tMin, Holder.tmp[0]); tMax = Math.min(tMax, Holder.tmp[1]);
        if (tMax < tMin) return Double.POSITIVE_INFINITY;

        if (!axisSlab(o.y, d.y, b.minY, b.maxY, Holder.tmp)) return Double.POSITIVE_INFINITY;
        tMin = Math.max(tMin, Holder.tmp[0]); tMax = Math.min(tMax, Holder.tmp[1]);
        if (tMax < tMin) return Double.POSITIVE_INFINITY;

        if (!axisSlab(o.z, d.z, b.minZ, b.maxZ, Holder.tmp)) return Double.POSITIVE_INFINITY;
        tMin = Math.max(tMin, Holder.tmp[0]); tMax = Math.min(tMax, Holder.tmp[1]);
        if (tMax < tMin) return Double.POSITIVE_INFINITY;

        return tMin;
    }

    private static boolean axisSlab(double o, double d, double min, double max, double[] out) {
        if (Math.abs(d) < 1e-12) {
            if (o < min || o > max) return false;
            out[0] = Double.NEGATIVE_INFINITY;
            out[1] = Double.POSITIVE_INFINITY;
            return true;
        } else {
            double inv = 1.0 / d;
            double t1 = (min - o) * inv;
            double t2 = (max - o) * inv;
            if (t1 > t2) { double tmp = t1; t1 = t2; t2 = tmp; }
            out[0] = t1;
            out[1] = t2;
            return true;
        }
    }

    private static final class Holder { static final double[] tmp = new double[2]; }

}
