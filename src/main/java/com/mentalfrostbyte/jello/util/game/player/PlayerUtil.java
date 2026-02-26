package com.mentalfrostbyte.jello.util.game.player;


import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mentalfrostbyte.jello.util.system.other.SimpleEntryPair;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.FlyingEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.MobEntity;
import net.minecraft.entity.monster.MonsterEntity;
import net.minecraft.entity.monster.SlimeEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Direction;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.shapes.VoxelShape;
import net.minecraft.util.math.vector.Vector3d;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Stream;

import static com.mentalfrostbyte.jello.module.Module.mc;

public class PlayerUtil {

    public static final float[] field24951 = new float[4];
    public static final float[] field24952 = new float[4];

    public static Vector3d getBoundingBoxCenter(AxisAlignedBB var0) {
        double var3 = var0.getCenter().x;
        double var5 = var0.minY;
        double var7 = var0.getCenter().z;
        double var9 = (var0.maxY - var5) * 0.95;
        double var11 = (var0.maxX - var0.minX) * 0.95;
        double var13 = (var0.maxZ - var0.minZ) * 0.95;
        double var15 = Math.max(var5, Math.min(var5 + var9, mc.player.getPosY() + (double) mc.player.getEyeHeight()));
        double var17 = Math.max(var3 - var11 / 2.0, Math.min(var3 + var11 / 2.0, mc.player.getPosX()));
        double var19 = Math.max(var7 - var13 / 2.0, Math.min(var7 + var13 / 2.0, mc.player.getPosZ()));
        return new Vector3d(var17, var15, var19);
    }

    public static List<String> getMobOwners(String uuid) throws Exception {
        ArrayList<String> names = new ArrayList<>();
        String apiUrl = "https://api.ashcon.app/mojang/v2/user/" + uuid;

        try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
            HttpGet request = new HttpGet(apiUrl);
            try (CloseableHttpResponse response = httpClient.execute(request)) {
                int statusCode = response.getStatusLine().getStatusCode();
                if (statusCode == 404) {
                    names.add("Unknown owner");
                    return names;
                }

                String responseBody = EntityUtils.toString(response.getEntity());
                JsonObject json = JsonParser.parseString(responseBody).getAsJsonObject();
                if (!json.has("username")) {
                    names.add("Unknown owner");
                    return names;
                }

                String username = json.get("username").getAsString();
                names.add(username);
                return names;
            }
        }
    }

    public static List<Entity> getEntitesInWorld() {
        ArrayList<Entity> entities = new ArrayList<>();
        mc.world.entitiesById.forEach((entity1, entity2) -> entities.add(entity2));
        return entities;
    }

    public static SimpleEntryPair<Direction, Vector3d> findCollisionDirection(double var0) {
        AxisAlignedBB playerBoundBox = mc.player.getBoundingBox();
        Direction[] directions = new Direction[]{Direction.EAST, Direction.WEST, Direction.SOUTH, Direction.NORTH};

        for (Direction direction : directions) {
            Iterator<VoxelShape> collisionShapes = mc.world
                    .getCollisionShapes(mc.player,
                            playerBoundBox.expand(var0 * (double) direction.getXOffset(), 0.0, var0 * (double) direction.getZOffset()))
                    .iterator();
            if (collisionShapes.hasNext()) {
                Vector3d position = mc.player
                        .getPositionVec()
                        .add(mc.player
                                .getAllowedMovement(new Vector3d(direction.getXOffset(), 0.0, direction.getZOffset())));
                return new SimpleEntryPair<>(direction, position);
            }
        }

        return null;
    }

    public static boolean isCollidingWithSurroundingBlocks() {
        double buffer = 1.0E-7;
        return mc.world
                .getCollisionShapes(mc.player, mc.player.getBoundingBox().expand(buffer, 0.0, buffer).expand(-buffer, 0.0, -buffer))
                .findAny().isPresent();
    }

    public static List<Entity> getAllEntitiesInWorld() {
        List<Entity> entities = new ArrayList<>();
        mc.world.entitiesById.forEach((id, entity) -> entities.add(entity));
        return entities;
    }

    public static EntityTypeCategory getEntityCategory(Entity entity) {
        if (entity instanceof LivingEntity) {
            if (!(entity instanceof PlayerEntity)) {
                return !(entity instanceof MobEntity) && !(entity instanceof MonsterEntity) && !(entity instanceof SlimeEntity) && !(entity instanceof FlyingEntity)
                        ? EntityTypeCategory.NON_PLAYER
                        : EntityTypeCategory.MONSTER;
            } else {
                return EntityTypeCategory.PLAYER;
            }
        } else {
            return EntityTypeCategory.AIRBORNE;
        }
    }

    public static double getDistanceTo(Vector3d position) {
        double deltaX = mc.player.getPosX() - position.x;
        double deltaY = mc.player.getPosY() + (double) mc.player.getEyeHeight() - position.y;
        double deltaZ = mc.player.getPosZ() - position.z;
        return Math.sqrt(deltaX * deltaX + deltaY * deltaY + deltaZ * deltaZ);
    }

    public static double getDistanceToBoundingBox(AxisAlignedBB boundingBox) {
        Vector3d position = getBoundingBoxCenter(boundingBox);
        return getDistanceTo(position);
    }

    public static boolean isPlayerInCollision() {
        AxisAlignedBB playerBox = mc.player.getBoundingBox().offset(0.0, -1.0, 0.0);
        if (mc.player.getRidingEntity() != null) {
            double deltaX = mc.player.getRidingEntity().prevPosX - mc.player.getRidingEntity().getPosX();
            double deltaZ = mc.player.getRidingEntity().prevPosZ - mc.player.getRidingEntity().getPosZ();
            playerBox = mc.player.getRidingEntity().getBoundingBox().expand(Math.abs(deltaX), 1.0, Math.abs(deltaZ));
        }

        Stream<VoxelShape> collisionShapes = mc.world.getCollisionShapes(mc.player, playerBox);
        return collisionShapes.findAny().isPresent();
    }

    public static boolean inLiquid(Entity entity) {
        ClientWorld world = mc.world;
        AxisAlignedBB boundingBox = entity.getBoundingBox();
        return world.containsAnyLiquid(boundingBox);
    }

    public static boolean isEntityAboveGround(Entity entity) {
        if (!(entity.getPosY() < 1.0)) {
            if (!entity.isOnGround()) {
                AxisAlignedBB entityBoundingBox = entity.getBoundingBox();
                entityBoundingBox = entityBoundingBox.expand(0.0, -entity.getPosY(), 0.0);
                return mc.world.getCollisionShapes(mc.player, entityBoundingBox).count() == 0L;
            } else {
                return false;
            }
        } else {
            return true;
        }
    }

    public enum EntityTypeCategory {
        MONSTER,
        PLAYER,
        NON_PLAYER,
        AIRBORNE
    }
}