/*
 * Ported from OpenSSNGScaffoldAndClutch (https://github.com/zyyzs/OpenSSNGScaffoldAndClutch)
 * Original: dev.southside.utils.rotation.RotationUtils — the upstream repo publishes the
 * core of this class (choose(), accepted(), setRotationDirectly(), the setRotation()
 * overloads); the remaining members (rotation storage, getServerRotation, smoothing and
 * block-face helpers) are reconstructed from their usage in Scaffold/FallingPlayer.
 *
 * Upstream architecture note (translated from the original Chinese comment):
 *   这套架构是 先在一个event里面让所有需要转头的模块计算好转头，并存入一个集合里面，携带的信息有movefix，rot和优先级
 *   然后再通过优先级选取转头
 *   在此方法执行完毕后，call一个event，在此event内，所有模块计算raytrace，可以完美避免rise那套架构存在的多模块raytrace打架问题
 *   ("All rotating modules compute their rotation inside one event and submit it — with
 *   movefix, rotation and priority — into a collection; the winner is then selected by
 *   priority. After that, a second event is fired in which every module validates its
 *   raytrace, which completely avoids the multi-module raytrace fighting of the Rise-style
 *   architecture.")
 *
 * choose() is invoked from Minecraft#runTick immediately before GameRenderer#getMouseOver
 * (the 1.16.5 equivalent of the upstream MixinMinecraftClient#hookRotation injection point),
 * and SSRotationAppliedEvent is fired immediately before processKeyBinds() (the equivalent
 * of the upstream hookRotationApplied injection).
 */
package com.mentalfrostbyte.jello.southside.utils.rotation;

import com.mentalfrostbyte.jello.southside.event.SSRotationEvent;
import com.mentalfrostbyte.jello.southside.manager.SSPacketOrderManager;
import com.mentalfrostbyte.jello.southside.manager.SSRotationManager;
import com.mentalfrostbyte.jello.southside.manager.SSServerPacketManager;
import com.mentalfrostbyte.jello.southside.utils.misc.SSBlinkUtils;
import com.mentalfrostbyte.jello.southside.utils.player.SSPlayerUtils;
import com.mentalfrostbyte.jello.southside.utils.raytrace.SSClientRayTraceUtil;
import com.mentalfrostbyte.jello.southside.utils.types.SSRotation;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Direction;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.vector.Vector3d;
import team.sdhq.eventBus.EventBus;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public final class SSRotationUtils {
    private static final Minecraft mc = Minecraft.getInstance();

    /** The rotation chosen for this tick; null = no module wants to rotate. */
    public static SSRotation rotation;
    public static SSMoveFixMode strafeFixMode = SSMoveFixMode.None;

    private static final List<RotationEntry> rotationList = new ArrayList<>();
    private static final SSRotationEvent rotationEvent = new SSRotationEvent();

    private record RotationEntry(SSRotation rotation, int priority) {
    }

    private SSRotationUtils() {
    }

    /*
     * Upstream body (published in the repo):
     *   rotationEvent.setDisableRotation(false);
     *   Southside.INSTANCE.getEventBus().call(rotationEvent);
     *   if (rotationList.isEmpty()) { setRotation(null); }
     *   RotationUtils.rotation = rotationEvent.isDisableRotation() ? null : getRandomRotation(rotationList);
     *   rotationList.clear();
     * The three per-tick bookkeeping calls at the top are reconstruction glue: upstream drives
     * PlayerUtils / ServerPacketManager / BlinkUtils from elsewhere inside its own client.
     */
    public static void choose() {
        if (mc.player != null) {
            SSPlayerUtils.update();
            SSBlinkUtils.update();
            SSPacketOrderManager.onTick();
            SSServerPacketManager.onTick();
            SSRotationManager.ensureRegistered();

            rotationEvent.setDisableRotation(false);
            EventBus.call(rotationEvent);
            if (rotationList.isEmpty()) {
                setRotation(null);
            }
            rotation = rotationEvent.isDisableRotation() ? null : getRandomRotation(rotationList);
            rotationList.clear();
        }
    }

    /** Upstream: a module may only rotate when its priority is >= every submitted priority. */
    public static boolean accepted(int priority) {
        int highestPriorityValue = rotationList.stream()
                .mapToInt(RotationEntry::priority)
                .max()
                .orElse(Integer.MIN_VALUE);

        return priority >= highestPriorityValue;
    }

    public static void setRotationDirectly(SSRotation rotation, SSMoveFixMode fixMode) {
        SSRotationUtils.rotation = rotation;
        SSRotationUtils.strafeFixMode = fixMode;
    }

    public static void setRotation(SSRotation rotation, int priority) {
        rotationList.add(new RotationEntry(rotation, priority));
    }

    public static void setRotation(SSRotation rotation, int priority, SSMoveFixMode mode) {
        setRotation(rotation, priority);
        strafeFixMode = mode;
    }

    /*
     * Upstream defaults the movefix to its StrafeFix module's mode
     * (Southside.INSTANCE.getModuleManager().get(StrafeFix.class).getMode()); that module is
     * not part of the published repo, so Silent — the mode the scaffold actually uses — is
     * the default here.
     */
    public static void setRotation(SSRotation angles) {
        setRotation(angles, SSPriority.Lower);
        strafeFixMode = SSMoveFixMode.Silent;
    }

    public static SSRotation getRotation() {
        return rotation;
    }

    /** Chosen rotation, falling back to the player's real rotation (used by SSFallingPlayer). */
    public static SSRotation getRotationSafely() {
        if (rotation != null) {
            return rotation;
        }
        if (mc.player != null) {
            return new SSRotation(mc.player.rotationYaw, mc.player.rotationPitch);
        }
        return new SSRotation(0.0F, 0.0F);
    }

    /**
     * The rotation the server currently believes the player has — the last yaw/pitch
     * actually reported by an outbound movement packet (ClientPlayerEntity ground truth).
     */
    public static SSRotation getServerRotation() {
        if (mc.player != null) {
            return new SSRotation(mc.player.lastReportedYaw, mc.player.lastReportedPitch);
        }
        return new SSRotation(0.0F, 0.0F);
    }

    /**
     * Baseline rotation for rotation-abuse interpolation. Upstream distinguishes
     * getLastRotation() from getServerRotation(); in every published call site both are read
     * before this tick's movement packet is sent, where they coincide with the last reported
     * server rotation.
     */
    public static SSRotation getLastRotation() {
        return getServerRotation();
    }

    /** Clamp a (signed) angle difference to ±step. */
    public static float smooth(float diff, float step) {
        float limit = Math.abs(step);
        return MathHelper.clamp(diff, -limit, limit);
    }

    /** Signed wrapped yaw difference a - b in [-180, 180]. */
    public static double yawDiffDirectly(float a, float b) {
        return MathHelper.wrapDegrees(a - b);
    }

    /** Absolute wrapped yaw difference between a and b. */
    public static double normalizeYawDiff(float a, float b) {
        return Math.abs(MathHelper.wrapDegrees(a - b));
    }

    /**
     * Rotation aimed at the point of the given block face that requires the least rotation
     * change from (baseYaw, basePitch) while still ray-hitting that exact face.
     *
     * Reconstructed: upstream does not publish this method. Approach — intersect the base
     * ray with the face plane and clamp the intersection into the face rectangle; if the
     * resulting rotation fails the strict didHitBlockFace check, grid-search points on the
     * face and pick the valid one angularly closest to the base rotation.
     */
    public static SSRotation getClosestToBlockFace(BlockPos pos, Direction face, float baseYaw, float basePitch) {
        if (mc.player == null || pos == null || face == null) {
            return null;
        }

        Vector3d eye = SSClientRayTraceUtil.eyePos != null ? SSClientRayTraceUtil.eyePos : mc.player.getEyePosition(1.0F);
        AxisAlignedBB box = new AxisAlignedBB(pos);

        // Face plane: coordinate along the face normal axis.
        double planeCoord = switch (face) {
            case WEST -> box.minX;
            case EAST -> box.maxX;
            case DOWN -> box.minY;
            case UP -> box.maxY;
            case NORTH -> box.minZ;
            case SOUTH -> box.maxZ;
        };

        final double margin = 0.05D;
        Vector3d dir = Vector3d.fromPitchYaw(basePitch, baseYaw);
        Vector3d target = null;

        // Intersect the base-rotation ray with the face plane (forward hits only).
        double dAxis = axisValue(dir, face.getAxis());
        double oAxis = axisValue(eye, face.getAxis());
        if (Math.abs(dAxis) > 1.0E-7D) {
            double t = (planeCoord - oAxis) / dAxis;
            if (t > 0.0D) {
                target = clampToFace(eye.add(dir.scale(t)), box, face, planeCoord, margin);
            }
        }
        if (target == null) {
            // Fall back to the perpendicular projection of the eye onto the face.
            target = clampToFace(eye, box, face, planeCoord, margin);
        }

        SSRotation result = getRotationByVector(eye, target, false);
        if (SSClientRayTraceUtil.didHitBlockFace(mc.player, result.yaw, result.pitch, pos, face, true)) {
            return result;
        }

        // Grid-search the face for a valid point closest in angle to the base rotation.
        SSRotation best = null;
        double bestScore = Double.MAX_VALUE;
        final int steps = 4;
        for (int i = 0; i <= steps; i++) {
            for (int j = 0; j <= steps; j++) {
                double u = margin + (1.0D - 2.0D * margin) * i / steps;
                double v = margin + (1.0D - 2.0D * margin) * j / steps;
                Vector3d point = facePoint(box, face, planeCoord, u, v);
                SSRotation candidate = getRotationByVector(eye, point, false);
                if (SSClientRayTraceUtil.didHitBlockFace(mc.player, candidate.yaw, candidate.pitch, pos, face, true)) {
                    double score = normalizeYawDiff(candidate.yaw, baseYaw) + Math.abs(candidate.pitch - basePitch);
                    if (score < bestScore) {
                        bestScore = score;
                        best = candidate;
                    }
                }
            }
        }

        return best != null ? best : result;
    }

    private static double axisValue(Vector3d vec, Direction.Axis axis) {
        return switch (axis) {
            case X -> vec.x;
            case Y -> vec.y;
            case Z -> vec.z;
        };
    }

    private static Vector3d clampToFace(Vector3d point, AxisAlignedBB box, Direction face, double planeCoord, double margin) {
        double x = MathHelper.clamp(point.x, box.minX + margin, box.maxX - margin);
        double y = MathHelper.clamp(point.y, box.minY + margin, box.maxY - margin);
        double z = MathHelper.clamp(point.z, box.minZ + margin, box.maxZ - margin);
        return switch (face.getAxis()) {
            case X -> new Vector3d(planeCoord, y, z);
            case Y -> new Vector3d(x, planeCoord, z);
            case Z -> new Vector3d(x, y, planeCoord);
        };
    }

    private static Vector3d facePoint(AxisAlignedBB box, Direction face, double planeCoord, double u, double v) {
        return switch (face.getAxis()) {
            case X -> new Vector3d(planeCoord, box.minY + u, box.minZ + v);
            case Y -> new Vector3d(box.minX + u, planeCoord, box.minZ + v);
            case Z -> new Vector3d(box.minX + u, box.minY + v, planeCoord);
        };
    }

    /**
     * Rotation pointing from {@code start} to {@code end}. The boolean flag mirrors the
     * upstream signature (RotationUtils.getRotationByVector(start, end, false)); its upstream
     * meaning is unpublished and it is unused here.
     */
    public static SSRotation getRotationByVector(Vector3d start, Vector3d end, boolean unusedFlag) {
        double diffX = end.x - start.x;
        double diffY = end.y - start.y;
        double diffZ = end.z - start.z;

        return new SSRotation(
                MathHelper.wrapDegrees((float) Math.toDegrees(Math.atan2(diffZ, diffX)) - 90.0F),
                MathHelper.wrapDegrees((float) -Math.toDegrees(Math.atan2(diffY, Math.sqrt(diffX * diffX + diffZ * diffZ))))
        );
    }

    /**
     * Winner selection among the submitted rotations: keep the highest priority, pick
     * randomly among ties (reconstructed; upstream name getRandomRotation).
     */
    private static SSRotation getRandomRotation(List<RotationEntry> list) {
        if (list.isEmpty()) {
            return null;
        }

        int highest = Integer.MIN_VALUE;
        for (RotationEntry entry : list) {
            if (entry.priority() > highest) {
                highest = entry.priority();
            }
        }

        List<RotationEntry> candidates = new ArrayList<>();
        for (RotationEntry entry : list) {
            if (entry.priority() == highest) {
                candidates.add(entry);
            }
        }

        return candidates.get(ThreadLocalRandom.current().nextInt(candidates.size())).rotation();
    }
}
