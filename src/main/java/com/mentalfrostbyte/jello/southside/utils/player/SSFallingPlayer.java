/*
 * Ported from OpenSSNGScaffoldAndClutch (https://github.com/zyyzs/OpenSSNGScaffoldAndClutch)
 * Original: dev.southside.utils.player.FallingPlayer
 *
 * Port notes (Yarn 1.21 / Fabric -> MCP 1.16.5 / SigmaClient):
 *   MinecraftClient -> Minecraft, Vec3d -> Vector3d, BlockHitResult -> BlockRayTraceResult,
 *   player.getVelocity -> getMotion, player.getMovementSpeed -> getAIMoveSpeed,
 *   mc.player.input.movementSideways/movementForward -> movementInput.moveStrafe/moveForward,
 *   mc.player.dimensions.eyeHeight() -> mc.player.getEyeHeight(),
 *   HitResult.Type.BLOCK -> RayTraceResult.Type.BLOCK, result.getSide -> getFace.
 *   Lombok @Getter/@Setter (upstream) -> explicit accessors.
 *
 * This is the "Clutch" half of the upstream repo: an air-only per-tick motion simulation
 * (只有空中模拟) used by the scaffold to predict the eye position 1-2 ticks ahead and trigger
 * the safe-distance self-save placements. The commented-out ground-friction branches are
 * preserved from upstream.
 */
package com.mentalfrostbyte.jello.southside.utils.player;

import com.mentalfrostbyte.jello.southside.utils.raytrace.SSRayCastUtils;
import com.mentalfrostbyte.jello.southside.utils.rotation.SSRotationUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Direction;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.BlockRayTraceResult;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.vector.Vector3d;

public class SSFallingPlayer {

    private double x;
    private double y;
    private double z;
    private Vector3d motion;
    private Vector3d eyePos;
    private float yaw;
    private float strafe;
    private float forward;
    private float jumpMovementFactor;
    private float movementSpeed;
    private boolean onGround;
    private final Minecraft mc = Minecraft.getInstance();

    public SSFallingPlayer(PlayerEntity player) {
        this.x = player.getPosX();
        this.y = player.getPosY();
        this.z = player.getPosZ();
        this.motion = player.getMotion();
        this.yaw = SSRotationUtils.getRotationSafely().yaw;
        this.strafe = mc.player.movementInput.moveStrafe;
        this.forward = mc.player.movementInput.moveForward;
        this.movementSpeed = player.getAIMoveSpeed();
        this.onGround = player.isOnGround();
        this.jumpMovementFactor = player.isSprinting() ? 0.026f : 0.02f;
        this.eyePos = mc.player.getEyePosition(1.0F);
    }

    private void calculateForTick() { //只有空中模拟
//        float blockFriction = 0.6f;
        float dragX = 0.91f;
        float dragZ = dragX;
        float dragY = 0.98f;
        float acceleration;
//        if (this.onGround) {
//            float frictionCubed = blockFriction * blockFriction * blockFriction;
//            acceleration = this.movementSpeed * (0.21600002f / frictionCubed);
//        } else {
            acceleration = this.jumpMovementFactor;
//        }
        updateVelocity(acceleration, new Vector3d(this.strafe, 0, this.forward));
        this.x += motion.x;
        this.y += motion.y;
        this.z += motion.z;
        updateGroundState();
        double gravity = 0.08D;
//        if (!this.onGround || this.motion.y > 0) {
            this.motion = this.motion.add(0, -gravity, 0);
//        }
//        else {
//            this.motion = new Vector3d(this.motion.x, 0, this.motion.z);
//        }
        this.eyePos = new Vector3d(this.x, this.y + mc.player.getEyeHeight(), this.z);
        this.motion = new Vector3d(
                this.motion.x * dragX,
                this.motion.y * dragY,
                this.motion.z * dragZ
        );
    }

    private void updateGroundState() {
        Vector3d center = new Vector3d(x, y, z);
        Vector3d down = center.add(0, -0.2, 0);

        BlockRayTraceResult result = rayTraceHit(center, down);
        if (result != null && result.getType() == RayTraceResult.Type.BLOCK && result.getFace() == Direction.UP) {
            this.onGround = true;
        } else {
            this.onGround = false;
        }
    }

    private void updateVelocity(float speed, Vector3d input) {
        double lengthSquared = input.lengthSquared();
        if (lengthSquared < 1.0E-7D) {
            return;
        }

        Vector3d normalizedInput = (lengthSquared > 1.0D ? input.normalize() : input).scale(speed);

        float f = MathHelper.sin(this.yaw * ((float) Math.PI / 180F));
        float g = MathHelper.cos(this.yaw * ((float) Math.PI / 180F));
        double inputX = normalizedInput.x * (double) g - normalizedInput.z * (double) f;
        double inputZ = normalizedInput.z * (double) g + normalizedInput.x * (double) f;

        this.motion = this.motion.add(inputX, 0, inputZ);
    }

    public void calculate(int ticks) {
        for (int i = 0; i < ticks; i++) {
            calculateForTick();
        }
    }

    public BlockPos findCollision(int ticks) {
        float w = mc.player != null ? mc.player.getWidth() / 2f : 0.3f;
        for (int i = 0; i < ticks; i++) {
            Vector3d start = new Vector3d(x, y, z);
            calculateForTick();
            Vector3d end = new Vector3d(x, y, z);

            BlockPos raytracedBlock;
            if ((raytracedBlock = rayTrace(start, end)) != null) return raytracedBlock;
            if ((raytracedBlock = rayTrace(start.add(w, 0, w), end.add(w, 0, w))) != null) return raytracedBlock;
            if ((raytracedBlock = rayTrace(start.add(-w, 0, w), end.add(-w, 0, w))) != null) return raytracedBlock;
            if ((raytracedBlock = rayTrace(start.add(w, 0, -w), end.add(w, 0, -w))) != null) return raytracedBlock;
            if ((raytracedBlock = rayTrace(start.add(-w, 0, -w), end.add(-w, 0, -w))) != null) return raytracedBlock;
            if ((raytracedBlock = rayTrace(start.add(w, 0, 0), end.add(w, 0, 0))) != null) return raytracedBlock;
            if ((raytracedBlock = rayTrace(start.add(-w, 0, 0), end.add(-w, 0, 0))) != null) return raytracedBlock;
            if ((raytracedBlock = rayTrace(start.add(0, 0, w), end.add(0, 0, w))) != null) return raytracedBlock;
            if ((raytracedBlock = rayTrace(start.add(0, 0, -w), end.add(0, 0, -w))) != null) return raytracedBlock;
        }
        return null;
    }

    private BlockRayTraceResult rayTraceHit(Vector3d start, Vector3d end) {
        double distance = start.distanceTo(end);
        if (distance < 1.0E-7D) return null;
        return SSRayCastUtils.raycast(
                distance,
                SSRotationUtils.getRotationByVector(start, end, false),
                false,
                1
        );
    }

    private BlockPos rayTrace(Vector3d start, Vector3d end) {
        BlockRayTraceResult result = rayTraceHit(start, end);
        if (result != null && result.getType() == RayTraceResult.Type.BLOCK && result.getFace() == Direction.UP) {
            return result.getPos();
        }
        return null;
    }

    public Vector3d getEyePos() {
        return eyePos.add(0, 0, 0); //新建一个对象返回 (upstream: eyePos.add(0))
    }

    public Vector3d getPos() {
        return new Vector3d(x, y, z);
    }

    public double getX() {
        return this.x;
    }

    public double getY() {
        return this.y;
    }

    public double getZ() {
        return this.z;
    }

    public Vector3d getMotion() {
        return this.motion;
    }

    public float getYaw() {
        return this.yaw;
    }

    public boolean isOnGround() {
        return this.onGround;
    }
}
