package com.mentalfrostbyte.jello.util.game.world.blocks;

import com.google.common.collect.ImmutableList;
import com.mentalfrostbyte.jello.event.impl.player.movement.EventMotion;
import com.mentalfrostbyte.jello.util.game.player.MovementUtil;
import com.mentalfrostbyte.jello.util.game.world.pathing.BlockCache;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.SnowBlock;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.state.StateContainer;
import net.minecraft.util.Direction;
import net.minecraft.util.math.*;
import net.minecraft.util.math.shapes.VoxelShape;
import net.minecraft.util.math.vector.Vector3d;
import net.minecraft.util.math.vector.Vector3i;
import org.apache.commons.lang3.RandomUtils;

import java.util.*;
import java.util.stream.Stream;

public class BlockUtil {
    public static Minecraft mc = Minecraft.getInstance();
    public static List<Block> blocksToNotPlace = Arrays.asList(
            Blocks.AIR,
            Blocks.WATER,
            Blocks.LAVA,
            Blocks.ENCHANTING_TABLE,
            Blocks.BLACK_CARPET,
            Blocks.GLASS_PANE,
            Blocks.IRON_BARS,
            Blocks.ICE,
            Blocks.PACKED_ICE,
            Blocks.CHEST,
            Blocks.TRAPPED_CHEST,
            Blocks.TORCH,
            Blocks.ANVIL,
            Blocks.TRAPPED_CHEST,
            Blocks.NOTE_BLOCK,
            Blocks.JUKEBOX,
            Blocks.TNT,
            Blocks.REDSTONE_WIRE,
            Blocks.LEVER,
            Blocks.COBBLESTONE_WALL,
            Blocks.OAK_FENCE,
            Blocks.TALL_GRASS,
            Blocks.TRIPWIRE,
            Blocks.TRIPWIRE_HOOK,
            Blocks.RAIL,
            Blocks.LILY_PAD,
            Blocks.RED_MUSHROOM,
            Blocks.BROWN_MUSHROOM,
            Blocks.VINE,
            Blocks.ACACIA_TRAPDOOR,
            Blocks.LADDER,
            Blocks.FURNACE,
            Blocks.SAND,
            Blocks.CACTUS,
            Blocks.DISPENSER,
            Blocks.DROPPER,
            Blocks.CRAFTING_TABLE,
            Blocks.COBWEB,
            Blocks.PUMPKIN,
            Blocks.ACACIA_SAPLING);

    public static BlockPos getBlockPosLookingAt(float yaw, float pitch, float reach) {
        BlockRayTraceResult result = rayTrace(yaw, pitch, reach);
        return result != null ? result.getPos() : null;
    }

    public static int getBlockStateId(BlockState state) {
        Block block = state.getBlock();
        StateContainer<Block, BlockState> stateContainer = block.getStateContainer();
        ImmutableList<BlockState> validStates = stateContainer.getValidStates();
        return validStates.indexOf(state);
    }

    public static boolean isValidBlockPosition(BlockPos blockPos) {
        if (blockPos != null) {
            Block block = mc.world.getBlockState(blockPos).getBlock();
            return (block.getDefaultState().isSolid() || !block.getDefaultState().getMaterial().isReplaceable()) && (!(block instanceof SnowBlock) || getBlockStateId(mc.world.getBlockState(blockPos)) != 0);
        } else {
            return false;
        }
    }

    public static float[] getRotationsToBlockDirection(BlockPos pos, Direction direction) {
        double diffX = (double) pos.getX() + 0.5 - mc.player.getPosX() + (double) direction.getXOffset() / 2.0;
        double diffZ = (double) pos.getZ() + 0.5 - mc.player.getPosZ() + (double) direction.getZOffset() / 2.0;
        double diffY = mc.player.getPosY() + (double) mc.player.getEyeHeight() - ((double) pos.getY() + 0.5);
        double horizontalDist = MathHelper.sqrt(diffX * diffX + diffZ * diffZ);
        float yaw = (float) (Math.atan2(diffZ, diffX) * 180.0 / Math.PI) - 90.0F;
        float pitch = (float) (Math.atan2(diffY, horizontalDist) * 180.0 / Math.PI);
        if (yaw < 0.0F) {
            yaw += 360.0F;
        }

        return new float[]{yaw, pitch};
    }

    /**
     * Calculates a Vec3 position based on a given direction and block position.
     * This method applies offsets and random variations to create a position within or adjacent to the specified block.
     *
     * @param dir The direction to offset the position. This affects which axis (X, Y, or Z) will receive the primary offset.
     * @param pos The base BlockPos from which to calculate the new position.
     * @return A Vec3 representing the calculated position, with applied offsets and potential random variations.
     */
    public static Vector3d getRandomlyOffsettedPos(Direction dir, BlockPos pos) {
        float dirXOffset = (float) Math.max(0, dir.getXOffset());
        float dirZOffset = (float) Math.max(0, dir.getZOffset());
        float x = (float) pos.getX() +
                dirXOffset + (dir.getXOffset() != 0 ?
                0.0F :
                (float) Math.random()
        );
        float y = (float) pos.getY() +
                (dir.getYOffset() != 0 ?
                        0.0F :
                        (dir.getYOffset() != 1 ?
                                (float) Math.random() :
                                1.0F));
        float z = (float) pos.getZ() +
                dirZOffset + (dir.getZOffset() != 0 ?
                0.0F :
                (float) Math.random()
        );
        return new Vector3d(x, y, z);
    }

    public static boolean canPlaceAt(PlayerEntity player, BlockPos placeAt) {
        return getDistance(player, placeAt) < getBlockReachDistance();
    }

    public static List<BlockPos> sortPositionsByDistance(List<BlockPos> positions) {
        positions.sort((a, b) -> {
            float distA = getDistance(mc.player, a);
            float distB = getDistance(mc.player, b);
            if (!(distA > distB)) {
                return distA != distB ? -1 : 0;
            } else {
                return 1;
            }
        });
        return positions;
    }

    public static float getDistance(Entity entity, BlockPos pos) {
        return getDistance(entity, pos.getX(), pos.getY(), pos.getZ());
    }

    public static float getDistance(Entity entity, double x, double y, double z) {
        float xDist = (float) (entity.getPosX() - x);
        float yDist = (float) (entity.getPosY() - y);
        float zDist = (float) (entity.getPosZ() - z);
        return getDistance(xDist, yDist, zDist);
    }

    public static float getDistance(float xD, float yD, float zD) {
        return MathHelper.sqrt((xD - 0.5F) * (xD - 0.5F) + (yD - 0.5F) * (yD - 0.5F) + (zD - 0.5F) * (zD - 0.5F));
    }

    public static Direction getBestFacingDirection(BlockPos pos) {
        Direction facing = Direction.UP;
        float yaw = MathHelper.wrapDegrees(getRotationsToBlockDirection(pos, Direction.UP)[0]);
        if (yaw >= 45.0F && yaw <= 135.0F) {
            facing = Direction.EAST;
        } else if ((!(yaw >= 135.0F) || !(yaw <= 180.0F)) && (!(yaw <= -135.0F) || !(yaw >= -180.0F))) {
            if (yaw <= -45.0F && yaw >= -135.0F) {
                facing = Direction.WEST;
            } else if (yaw >= -45.0F && yaw <= 0.0F || yaw <= 45.0F && yaw >= 0.0F) {
                facing = Direction.NORTH;
            }
        } else {
            facing = Direction.SOUTH;
        }

        if (MathHelper.wrapDegrees(getRotationsToBlockDirection(pos, Direction.UP)[1]) > 75.0F || MathHelper.wrapDegrees(getRotationsToBlockDirection(pos, Direction.UP)[1]) < -75.0F) {
            facing = Direction.UP;
        }

        return facing;
    }

    public static List<PlayerEntity> sortPlayersByDistance(List<PlayerEntity> players) {
        Collections.sort(players, new Class3583());
        return players;
    }

    public static BlockCache findValidBlockCache(BlockPos basePos, boolean excludeDown) {
        Vector3i[] relativePositions = new Vector3i[]{
                new Vector3i(0, 0, 0),
                new Vector3i(-1, 0, 0),
                new Vector3i(1, 0, 0),
                new Vector3i(0, 0, 1),
                new Vector3i(0, 0, -1)
        };
        PlacementPattern[] placementPatterns = new PlacementPattern[]{
                new PlacementPattern(1, 1, 1, false),
                new PlacementPattern(2, 1, 2, false),
                new PlacementPattern(3, 1, 3, false),
                new PlacementPattern(4, 1, 4, false),
                new PlacementPattern(0, -1, 0, true)
        };

        for (PlacementPattern pattern : placementPatterns) {
            for (Vector3i offset : relativePositions) {
                Vector3i positionToCheck = !pattern.isOffset
                        ? new Vector3i(offset.getX() * pattern.offsetX, offset.getY() * pattern.offsetY, offset.getZ() * pattern.offsetZ)
                        : new Vector3i(offset.getX() + pattern.offsetX, offset.getY() + pattern.offsetY, offset.getZ() + pattern.offsetZ);

                for (Direction direction : Direction.values()) {
                    if ((direction != Direction.DOWN || !excludeDown) && isValidBlockPosition(basePos.add(positionToCheck).offset(direction, -1))) {
                        return new BlockCache(basePos.add(positionToCheck).offset(direction, -1), direction);
                    }
                }
            }
        }

        return null;
    }


    public static BlockRayTraceResult rayTrace(float yaw, float pitch, float reach) {
        Vector3d start = new Vector3d(
                mc.player.lastReportedPosX, mc.player.lastReportedPosY + (double) mc.player.getEyeHeight(), mc.player.lastReportedPosZ
        );
        yaw = (float) Math.toRadians(yaw);
        pitch = (float) Math.toRadians(pitch);
        float dirX = -MathHelper.sin(yaw) * MathHelper.cos(pitch);
        float dirY = -MathHelper.sin(pitch);
        float dirZ = MathHelper.cos(yaw) * MathHelper.cos(pitch);
        if (reach == 0.0F) {
            reach = mc.playerController.getBlockReachDistance();
        }

        Vector3d end = new Vector3d(
                mc.player.lastReportedPosX + (double) (dirX * reach),
                mc.player.lastReportedPosY + (double) (dirY * reach) + (double) mc.player.getEyeHeight(),
                mc.player.lastReportedPosZ + (double) (dirZ * reach)
        );
        Entity viewEntity = mc.getRenderViewEntity();
        return mc.world.rayTraceBlocks(new RayTraceContext(start, end, RayTraceContext.BlockMode.OUTLINE, RayTraceContext.FluidMode.NONE, viewEntity));
    }

    /**
     * 沿指定视线方向发射一条射线，返回射线路径上命中的所有生物（按距离由近到远排序）。
     * 起点使用 lastReportedPos（服务端已知位置），方向使用原版 {@link Entity#getLookCustom} 计算，
     * 保证 raytrace 结果与 Grim 服务端补偿的位置/旋转自洽。
     *
     * @param yaw          射线的偏航角（度）
     * @param pitch        射线的俯仰角（度）
     * @param distance     最大射线距离；传 0 时使用原版攻击距离 3.0
     * @param throughWalls 是否允许穿墙：true 忽略方块阻挡；false 时被方块挡住的实体不计入
     * @return             命中的生物实体列表，按与射线起点的距离从近到远排序
     */
    public static List<LivingEntity> rayTraceEntities(float yaw, float pitch, float distance, boolean throughWalls) {
        List<LivingEntity> result = new ArrayList<>();
        if (mc.player == null || mc.world == null) {
            return result;
        }

        if (distance == 0.0F) {
            distance = 3.0F;
        }

        Vector3d start = new Vector3d(
                mc.player.lastReportedPosX,
                mc.player.lastReportedPosY + (double) mc.player.getEyeHeight(),
                mc.player.lastReportedPosZ
        );
        Vector3d look = mc.player.getLookCustom(1.0F, yaw, pitch);
        Vector3d end = start.add(look.x * distance, look.y * distance, look.z * distance);

        // 不穿墙时：先算出射线被方块挡住的距离平方，命中点超过此距离的实体忽略
        double wallDistSq = Double.MAX_VALUE;
        if (!throughWalls) {
            BlockRayTraceResult blockHit = mc.world.rayTraceBlocks(new RayTraceContext(
                    start, end, RayTraceContext.BlockMode.COLLIDER, RayTraceContext.FluidMode.NONE, mc.player));
            if (blockHit != null && blockHit.getType() != RayTraceResult.Type.MISS) {
                wallDistSq = start.squareDistanceTo(blockHit.getHitVec());
            }
        }

        // 只在射线可能经过的范围内取实体，减少遍历
        AxisAlignedBB searchBox = mc.player.getBoundingBox()
                .expand(look.x * distance, look.y * distance, look.z * distance)
                .grow(1.0, 1.0, 1.0);

        List<double[]> distances = new ArrayList<>();
        List<LivingEntity> hits = new ArrayList<>();

        for (Entity entity : mc.world.getEntitiesInAABBexcluding(mc.player, searchBox,
                e -> e instanceof LivingEntity && e.isAlive())) {
            AxisAlignedBB box = entity.getBoundingBox().grow(entity.getCollisionBorderSize());
            Optional<Vector3d> hit = box.rayTrace(start, end);

            boolean isHit = hit.isPresent();
            double hitDistSq = Double.MAX_VALUE;

            if (isHit) {
                hitDistSq = start.squareDistanceTo(hit.get());
            } else {
                if (box.contains(start)) {
                    isHit = true;
                    hitDistSq = 0.0;
                }
            }


            if (isHit) {
                // 穿墙检测（墙的距离过滤）
                if (hitDistSq > wallDistSq) {
                    continue;
                }
                hits.add((LivingEntity) entity);
                distances.add(new double[]{hits.size() - 1, hitDistSq});
            }
        }

        distances.sort(Comparator.comparingDouble(a -> a[1]));
        for (double[] entry : distances) {
            result.add(hits.get((int) entry[0]));
        }

        return result;
    }

    public static List<LivingEntity> rayTraceEntitiesnolastpos(float yaw, float pitch, float distance, boolean throughWalls) {
        List<LivingEntity> result = new ArrayList<>();
        if (mc.player == null || mc.world == null) {
            return result;
        }

        if (distance == 0.0F) {
            distance = 3.0F;
        }

        Vector3d start = new Vector3d(
                mc.player.getPosX(),
                mc.player.getPosY() + (double) mc.player.getEyeHeight(),
                mc.player.getPosZ()
        );
        Vector3d look = mc.player.getLookCustom(1.0F, yaw, pitch);
        Vector3d end = start.add(look.x * distance, look.y * distance, look.z * distance);

        // 不穿墙时：先算出射线被方块挡住的距离平方，命中点超过此距离的实体忽略
        double wallDistSq = Double.MAX_VALUE;
        if (!throughWalls) {
            BlockRayTraceResult blockHit = mc.world.rayTraceBlocks(new RayTraceContext(
                    start, end, RayTraceContext.BlockMode.COLLIDER, RayTraceContext.FluidMode.NONE, mc.player));
            if (blockHit != null && blockHit.getType() != RayTraceResult.Type.MISS) {
                wallDistSq = start.squareDistanceTo(blockHit.getHitVec());
            }
        }

        // 只在射线可能经过的范围内取实体，减少遍历
        AxisAlignedBB searchBox = mc.player.getBoundingBox()
                .expand(look.x * distance, look.y * distance, look.z * distance)
                .grow(1.0, 1.0, 1.0);

        List<double[]> distances = new ArrayList<>();
        List<LivingEntity> hits = new ArrayList<>();

        for (Entity entity : mc.world.getEntitiesInAABBexcluding(mc.player, searchBox,
                e -> e instanceof LivingEntity && e.isAlive())) {
            AxisAlignedBB box = entity.getBoundingBox().grow(entity.getCollisionBorderSize());
            Optional<Vector3d> hit = box.rayTrace(start, end);

            boolean isHit = hit.isPresent();
            double hitDistSq = Double.MAX_VALUE;

            if (isHit) {
                hitDistSq = start.squareDistanceTo(hit.get());
            } else {
                if (box.contains(start)) {
                    isHit = true;
                    hitDistSq = 0.0;
                }
            }


            if (isHit) {
                // 穿墙检测（墙的距离过滤）
                if (hitDistSq > wallDistSq) {
                    continue;
                }
                hits.add((LivingEntity) entity);
                distances.add(new double[]{hits.size() - 1, hitDistSq});
            }
        }

        distances.sort(Comparator.comparingDouble(a -> a[1]));
        for (double[] entry : distances) {
            result.add(hits.get((int) entry[0]));
        }

        return result;
    }

    /**
     * 对指定方块位置发射一条视线射线，返回精确的命中信息（命中点、命中面等）。
     * 起点使用 lastReportedPos（服务端已知位置），方向使用原版 {@link Entity#getLookCustom} 计算，
     * 保证 raytrace 结果与 Grim 服务端补偿的位置/旋转自洽。
     *
     * @param yaw          射线的偏航角（度）
     * @param pitch        射线的俯仰角（度）
     * @param distance     最大射线距离；传 0 时使用玩家默认交互距离
     * @param target       目标方块位置
     * @param throughWalls 是否允许穿墙：true 只对目标方块求交，忽略沿途其它方块；
     *                     false 时若射线在到达目标前被其它方块挡住则视为未命中
     * @return             若射线命中目标方块，返回带命中点/命中面的 {@link BlockRayTraceResult}；否则返回 MISS 类型的结果
     */
    public static BlockRayTraceResult rayTraceBlock(float yaw, float pitch, float distance, BlockPos target, boolean throughWalls) {
        if (distance == 0.0F) {
            distance = mc.playerController.getBlockReachDistance();
        }

        Vector3d start = new Vector3d(
                mc.player.lastReportedPosX,
                mc.player.lastReportedPosY + (double) mc.player.getEyeHeight(),
                mc.player.lastReportedPosZ
        );
        Vector3d look = mc.player.getLookCustom(1.0F, yaw, pitch);
        Vector3d end = start.add(look.x * distance, look.y * distance, look.z * distance);
        Direction missFace = Direction.getFacingFromVector(look.x, look.y, look.z).getOpposite();

        if (throughWalls) {
            VoxelShape shape = mc.world.getBlockState(target).getShape(mc.world, target);
            BlockRayTraceResult hit = shape.rayTrace(start, end, target);
            if (hit == null) {
                // 射线未命中方块形状（可能因为距离不够或角度偏差），返回 MISS
                return BlockRayTraceResult.createMiss(end, missFace, target);
            }
            return hit;
        }

        RayTraceResult result = mc.world.rayTraceBlocks(new RayTraceContext(
                start, end, RayTraceContext.BlockMode.OUTLINE, RayTraceContext.FluidMode.NONE, mc.player));
        if (result instanceof BlockRayTraceResult && result.getType() != RayTraceResult.Type.MISS) {
            return (BlockRayTraceResult) result;
        }
        return BlockRayTraceResult.createMiss(end, missFace, target);
    }

    public static BlockRayTraceResult rayTrace(float yaw, float pitch, float reach, EventMotion motion) {
        Vector3d start = new Vector3d(motion.getX(), (double) mc.player.getEyeHeight() + motion.getY(), motion.getZ());
        yaw = (float) Math.toRadians(yaw);
        pitch = (float) Math.toRadians(pitch);
        float dirX = -MathHelper.sin(yaw) * MathHelper.cos(pitch);
        float dirY = -MathHelper.sin(pitch);
        float dirZ = MathHelper.cos(yaw) * MathHelper.cos(pitch);
        if (reach == 0.0F) {
            reach = mc.playerController.getBlockReachDistance();
        }

        Vector3d end = new Vector3d(
                mc.player.lastReportedPosX + (double) (dirX * reach),
                mc.player.lastReportedPosY + (double) (dirY * reach) + (double) mc.player.getEyeHeight(),
                mc.player.lastReportedPosZ + (double) (dirZ * reach)
        );
        Entity viewEntity = mc.getRenderViewEntity();
        return mc.world.rayTraceBlocks(new RayTraceContext(start, end, RayTraceContext.BlockMode.OUTLINE, RayTraceContext.FluidMode.NONE, viewEntity));
    }

    public static float[] rotationsToBlock(BlockPos bp, Direction dir) {
        float offsetX = 0.0F;
        float offsetZ = 0.0F;
        float offsetY = (float) (0.4F + Math.random() * 0.1F);
        switch (dir) {
            case EAST:
                offsetX += 0.49F;
                break;
            case NORTH:
                offsetZ -= 0.49F;
                break;
            case SOUTH:
                offsetZ += 0.49F;
                break;
            case WEST:
                offsetX -= 0.49F;
                break;
        }

        if (offsetX == 0.0F) {
            offsetX = (float) (0.1F - Math.sin((double) (System.currentTimeMillis() - 500L) / 1200.0) * 0.2);
        }

        if (offsetZ == 0.0F) {
            offsetZ = (float) (0.1F - Math.sin((double) (System.currentTimeMillis() - 500L) / 1000.0) * 0.2);
        }

        if (offsetY == 0.0F) {
            offsetY = (float) (0.6F - Math.sin((double) (System.currentTimeMillis() - 500L) / 1600.0) * 0.2);
        }

        double diffX = (double) bp.getX() + 0.5 - Minecraft.getInstance().player.getPosX() + (double) offsetX;
        double diffY = (double) bp.getY()
                - 0.02
                - (Minecraft.getInstance().player.getPosY() + (double) Minecraft.getInstance().player.getEyeHeight())
                + (double) offsetY;
        double diffZ = (double) bp.getZ() + 0.5 - Minecraft.getInstance().player.getPosZ() + (double) offsetZ;
        double horizontalDist = MathHelper.sqrt(diffX * diffX + diffZ * diffZ);
        float yaw = (float) (Math.atan2(diffZ, diffX) * 180.0 / Math.PI) - 90.0F;
        float pitch = (float) (-(Math.atan2(diffY, horizontalDist) * 180.0 / Math.PI));
        return new float[]{
                Minecraft.getInstance().player.rotationYaw + MathHelper.wrapDegrees(yaw - Minecraft.getInstance().player.rotationYaw),
                Minecraft.getInstance().player.rotationPitch + MathHelper.wrapDegrees(pitch - Minecraft.getInstance().player.rotationPitch)
        };
    }


    public static RayTraceResult rayTraceWithOffset(float yaw, float pitch, float reach, float sideOffset) {
        double offsetX = Math.cos((double) MovementUtil.getYaw() * Math.PI / 180.0) * (double) sideOffset;
        double offsetZ = Math.sin((double) MovementUtil.getYaw() * Math.PI / 180.0) * (double) sideOffset;
        Vector3d start = new Vector3d(
                mc.player.getPosX() + offsetX,
                mc.player.getPosY() + (double) mc.player.getEyeHeight(),
                mc.player.getPosZ() + offsetZ
        );
        yaw = (float) Math.toRadians(yaw);
        pitch = (float) Math.toRadians(pitch);
        float dirX = -MathHelper.sin(yaw) * MathHelper.cos(pitch);
        float dirY = -MathHelper.sin(pitch);
        float dirZ = MathHelper.cos(yaw) * MathHelper.cos(pitch);
        if (reach == 0.0F) {
            reach = mc.playerController.getBlockReachDistance();
        }

        Vector3d end = new Vector3d(
                mc.player.lastReportedPosX + (double) (dirX * reach),
                mc.player.lastReportedPosY + (double) (dirY * reach) + (double) mc.player.getEyeHeight(),
                mc.player.lastReportedPosZ + (double) (dirZ * reach)
        );
        Entity viewEntity = mc.getRenderViewEntity();
        return mc.world.rayTraceBlocks(new RayTraceContext(start, end, RayTraceContext.BlockMode.OUTLINE, RayTraceContext.FluidMode.NONE, viewEntity));
    }

    public static RayTraceResult rayTraceBlock(BlockPos pos) {
        return rayTraceBlock(pos, false);
    }

    /**
     * 从玩家眼睛向目标方块发射射线。
     *
     * @param pos          目标方块位置
     * @param throughWalls 是否穿墙：false 走原版逻辑（被沿途方块阻挡）；
     *                     true 时忽略沿途其它方块，只对目标方块求交，保证返回命中目标本身
     */
    public static RayTraceResult rayTraceBlock(BlockPos pos, boolean throughWalls) {
        Vector3d start = new Vector3d(
                mc.player.getPosX(), mc.player.getPosY() + (double) mc.player.getEyeHeight(), mc.player.getPosZ()
        );
        Vector3d end = new Vector3d(
                (double) pos.getX() + 0.5 + RandomUtils.nextDouble(0.01, 0.04),
				pos.getY(),
                (double) pos.getZ() + 0.5 + RandomUtils.nextDouble(0.01, 0.04)
        );
        if (!throughWalls) {
            return mc.world.rayTraceBlocks(new RayTraceContext(start, end, RayTraceContext.BlockMode.OUTLINE, RayTraceContext.FluidMode.NONE, mc.getRenderViewEntity()));
        }

        // 穿墙：只对目标方块的形状求交，忽略沿途其它方块
        VoxelShape shape = mc.world.getBlockState(pos).getShape(mc.world, pos);
        BlockRayTraceResult hit = shape.rayTrace(start, end, pos);
        if (hit != null) {
            return hit;
        }

        // 射线未命中方块形状，返回 MISS
        Direction missFace = Direction.getFacingFromVector(
                start.x - ((double) pos.getX() + 0.5),
                start.y - ((double) pos.getY() + 0.5),
                start.z - ((double) pos.getZ() + 0.5));
        return BlockRayTraceResult.createMiss(end, missFace, pos);
    }

    public static float[] getRotationsToBlockFace(BlockPos pos, Direction direction) {
        float offsetX = 0.0F;
        float offsetZ = 0.0F;
        float offsetY = 0.0F;
        switch (direction) {
            case EAST:
                offsetX += 0.49F;
                break;
            case NORTH:
                offsetZ -= 0.49F;
                break;
            case SOUTH:
                offsetZ += 0.49F;
                break;
            case WEST:
                offsetX -= 0.49F;
                break;
            case UP:
                offsetY += 0.0F;
            case DOWN:
                offsetY++;
        }

        double diffX = (double) pos.getX() + 0.5 - Minecraft.getInstance().player.getPosX() + (double) offsetX;
        double diffY = (double) pos.getY()
                - 0.02
                - (Minecraft.getInstance().player.getPosY() + (double) Minecraft.getInstance().player.getEyeHeight())
                + (double) offsetY;
        double diffZ = (double) pos.getZ() + 0.5 - Minecraft.getInstance().player.getPosZ() + (double) offsetZ;
        double horizontalDist = MathHelper.sqrt(diffX * diffX + diffZ * diffZ);
        float yaw = (float) (Math.atan2(diffZ, diffX) * 180.0 / Math.PI) - 90.0F;
        float pitch = (float) (-(Math.atan2(diffY, horizontalDist) * 180.0 / Math.PI));
        return new float[]{
                Minecraft.getInstance().player.rotationYaw + MathHelper.wrapDegrees(yaw - Minecraft.getInstance().player.rotationYaw),
                Minecraft.getInstance().player.rotationPitch + MathHelper.wrapDegrees(pitch - Minecraft.getInstance().player.rotationPitch)
        };
    }


    public static boolean canPlaceBlockAt(Block block, BlockPos pos) {
        VoxelShape collisionShape = block.getDefaultState().getCollisionShape(mc.world, pos);
        return !isValidBlockPosition(pos)
                && mc.world.checkNoEntityCollision(mc.player, collisionShape)
                && pos.getY() <= mc.player.getPosition().getY();
    }

    public static final Block getBlockFromPosition(BlockPos blockPos) {
        return mc.world.getBlockState(blockPos).getBlock();
    }

    public static float getBlockReachDistance() {
        return mc.playerController.getBlockReachDistance();
    }

    public static BlockRayTraceResult rayTraceGroundBlock(float yaw) {
        Vector3d start = new Vector3d(mc.player.lastReportedPosX, mc.player.lastReportedPosY - 0.8F, mc.player.lastReportedPosZ);
        yaw = (float) Math.toRadians(yaw);
        float pitch = 0.0F;
        float dirX = -MathHelper.sin(yaw) * MathHelper.cos(pitch);
        float dirZ = MathHelper.cos(yaw) * MathHelper.cos(pitch);
        float reach = 2.3F;
        Vector3d end = new Vector3d(
                mc.player.lastReportedPosX + (double) (dirX * reach),
                mc.player.lastReportedPosY - 0.8F - (double) (!mc.player.isJumping ? 0.0F : 0.6F),
                mc.player.lastReportedPosZ + (double) (dirZ * reach)
        );
        Entity viewEntity = mc.getRenderViewEntity();
        return mc.world.rayTraceBlocks(new RayTraceContext(start, end, RayTraceContext.BlockMode.OUTLINE, RayTraceContext.FluidMode.NONE, viewEntity));
    }

    public static float[] getRotationsToBlock() {
        BlockRayTraceResult result = rayTraceGroundBlock(MovementUtil.getYaw() - 270.0F);
        if (result.getType() != RayTraceResult.Type.MISS) {
            double hitOffsetX = result.getHitVec().x - (double) result.getPos().getX();
            double hitOffsetZ = result.getHitVec().z - (double) result.getPos().getZ();
            double hitOffsetY = result.getHitVec().y - (double) result.getPos().getY();
            double diffX = (double) result.getPos().getX() - Minecraft.getInstance().player.getPosX() + hitOffsetX;
            double diffY = (double) result.getPos().getY()
                    - (Minecraft.getInstance().player.getPosY() + (double) Minecraft.getInstance().player.getEyeHeight())
                    + hitOffsetY;
            double diffZ = (double) result.getPos().getZ() - Minecraft.getInstance().player.getPosZ() + hitOffsetZ;
            double horizontalDist = MathHelper.sqrt(diffX * diffX + diffZ * diffZ);
            float yaw = (float) (Math.atan2(diffZ, diffX) * 180.0 / Math.PI) - 90.0F;
            float pitch = (float) (-(Math.atan2(diffY, horizontalDist) * 180.0 / Math.PI));
            return new float[]{
                    Minecraft.getInstance().player.rotationYaw + MathHelper.wrapDegrees(yaw - Minecraft.getInstance().player.rotationYaw),
                    Minecraft.getInstance().player.rotationPitch + MathHelper.wrapDegrees(pitch - Minecraft.getInstance().player.rotationPitch)
            };
        } else {
            return null;
        }
    }

    public static List<BlockPos> getBlockPositionsInRange(float range) {
        ArrayList<BlockPos> positions = new ArrayList<>();

        for (float y = -range; y <= range; y++) {
            for (float x = -range; x <= range; x++) {
                for (float z = -range; z <= range; z++) {
                    BlockPos pos = new BlockPos(
                            mc.player.getPosX() + (double) x,
                            mc.player.getPosY() + (double) y,
                            mc.player.getPosZ() + (double) z
                    );
                    positions.add(pos);
                }
            }
        }

        return positions;
    }

    public static boolean isAboveBounds(Entity entity, float yBounds) {
        AxisAlignedBB bounds = new AxisAlignedBB(
                entity.getBoundingBox().minX,
                entity.getBoundingBox().minY - (double) yBounds,
                entity.getBoundingBox().minZ,
                entity.getBoundingBox().maxX,
                entity.getBoundingBox().maxY,
                entity.getBoundingBox().maxZ
        );
        Stream<VoxelShape> collisionShapes = mc.world.getCollisionShapes(mc.player, bounds);
        return collisionShapes.findAny().isPresent();
    }

    public static class PlacementPattern {
        public int offsetX;
        public int offsetY;
        public int offsetZ;
        public boolean isOffset;

        public PlacementPattern(int offsetX, int offsetY, int offsetZ, boolean isOffset) {
            this.offsetX = offsetX;
            this.offsetY = offsetY;
            this.offsetZ = offsetZ;
            this.isOffset = isOffset;
        }
    }

    public static final class Class3583 implements Comparator<PlayerEntity> {
        private static String[] field19525;

        public int compare(PlayerEntity playerA, PlayerEntity playerB) {
            float distA = mc.player.getDistance(playerA);
            float distB = mc.player.getDistance(playerB);
            if (!(distA - distB < 0.0F)) {
                return distA - distB != 0.0F ? -1 : 0;
            } else {
                return 1;
            }
        }
    }
}
