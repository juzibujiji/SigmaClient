package com.mentalfrostbyte.jello.util.game.player.rotation;

import com.mentalfrostbyte.Client;
import com.mentalfrostbyte.jello.util.game.world.EntityRayTraceResult;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RayTraceResult;

/**
 * JelloAI - Neural network based rotation system
 * Uses machine learning to create human-like rotations
 */
public class JelloAI {
    private static final Minecraft mc = Minecraft.getInstance();

    // Create a single shared neural network instance
    private static final NeuralNetwork neuralNetwork = new NeuralNetwork();
    private static final RotationManager rotationManager = new RotationManager();

    // Pass the shared network to managers
    private static final TrainingManager trainingManager = new TrainingManager(neuralNetwork);
    private static final ReinforcementManager reinforcementManager =
            new ReinforcementManager(neuralNetwork, trainingManager);

    // Singleton instance for static access
    private static JelloAI instance;

    // Constants for reinforcement learning
    public static final float HIT_REWARD = 1.0f;
    public static final float MISS_PENALTY = -0.2f;

    // Rotation speed constants and limits
    public static final float BASE_MAX_YAW_CHANGE = 30.0f;
    public static final float BASE_MAX_PITCH_CHANGE = 15.0f;

    // Max deviation the network may add on top of the analytic aim direction.
    // Keeps aim on target even while the network is untrained.
    private static final float MAX_AI_YAW_OFFSET = 5.0f;
    private static final float MAX_AI_PITCH_OFFSET = 3.0f;
    private static float currentMaxYawChange = BASE_MAX_YAW_CHANGE;
    private static float currentMaxPitchChange = BASE_MAX_PITCH_CHANGE;

    // Static cached arrays to reduce GC pressure
    private static final float[] cachedInputs = new float[NeuralNetwork.INPUT_SIZE];
    private static final float[] cachedNormalized = new float[2];
    private static final float[] cachedIdealRotation = new float[2];
    private static final float[] cachedBlended = new float[2];

    // Human-like noise config
    private static boolean enableNoise = true;

    // Training throttling fields
    private static int tickCounter = 0;
    private static final int TRAINING_SAMPLE_INTERVAL = 5;

    /**
     * Initialize the AI system
     */
    public static void init() {
        instance = new JelloAI();
        rotationManager.initialize();
        neuralNetwork.initialize();
        trainingManager.initialize();
        trainingManager.startTrainingThread();
    }

    /**
     * Update rotations based on target
     */
    public static void updateRotations() {
        rotationManager.updateRotations();
    }

    /**
     * Get current yaw
     */
    public static float getCurrentYaw() {
        return rotationManager.getCurrentYaw();
    }

    /**
     * Get current pitch
     */
    public static float getCurrentPitch() {
        return rotationManager.getCurrentPitch();
    }

    /**
     * Apply server-side rotations without changing client-side camera
     * Incorporates GCD sensitivity correction and dynamic clamping.
     */
    public static void applyServerRotation() {
        if (mc.player == null) return;

        float serverYaw = rotationManager.getCurrentYaw();
        float serverPitch = rotationManager.getCurrentPitch();

        float yawDiff = MathHelper.wrapDegrees(serverYaw - mc.player.prevRotationYaw);
        float pitchDiff = serverPitch - mc.player.prevRotationPitch;

        // 1. Clamp rotation change per tick to avoid server anti-cheat
        yawDiff = MathHelper.clamp(yawDiff, -currentMaxYawChange, currentMaxYawChange);
        pitchDiff = MathHelper.clamp(pitchDiff, -currentMaxPitchChange, currentMaxPitchChange);

        // 2. Apply GCD sensitivity step correction (real mouse step emulation)
        float sensitivity = (float) mc.gameSettings.mouseSensitivity;
        float f = sensitivity * 0.6F + 0.2F;
        float f1 = f * f * f * 8.0F;
        float f2 = f1 * 0.15F;

        float gcdYaw = Math.round(yawDiff / f2) * f2;
        float gcdPitch = Math.round(pitchDiff / f2) * f2;

        // Apply corrected rotations
        mc.player.rotationYaw = mc.player.prevRotationYaw + gcdYaw;
        mc.player.rotationPitch = mc.player.prevRotationPitch + gcdPitch;
    }

    /**
     * Get rotations to a specific position
     */
    public static float[] getRotationsToPosition(double x, double y, double z) {
        if (mc.player == null) return new float[2];

        // Analytic aim is the backbone; the network only nudges it
        calculateIdealRotations(x, y, z, cachedIdealRotation);
        fillPositionInputs(x, y, z, cachedInputs);
        float[] blended = blendWithPrediction(cachedIdealRotation[0], cachedIdealRotation[1], cachedInputs);

        return new float[] {blended[0], blended[1]};
    }

    /**
     * Blend the analytic ideal rotation with the network prediction.
     * The network contribution is clamped to a few degrees and scaled by its
     * confidence, so an untrained (or badly trained) network can never pull
     * the aim off the target.
     */
    private static float[] blendWithPrediction(float idealYaw, float idealPitch, float[] inputs) {
        float yawDegrees = MathHelper.wrapDegrees(idealYaw);
        float pitchDegrees = MathHelper.clamp(idealPitch, -90f, 90f);

        float[] rotations = neuralNetwork.predict(inputs);
        if (rotations != null && rotations.length >= 2) {
            float nnYaw = MathHelper.wrapDegrees(rotations[0] * 180f);
            float nnPitch = MathHelper.clamp(rotations[1] * 90f, -90f, 90f);

            float confidence = neuralNetwork.getConfidence();
            float yawOffset = MathHelper.clamp(MathHelper.wrapDegrees(nnYaw - yawDegrees),
                    -MAX_AI_YAW_OFFSET, MAX_AI_YAW_OFFSET) * confidence;
            float pitchOffset = MathHelper.clamp(nnPitch - pitchDegrees,
                    -MAX_AI_PITCH_OFFSET, MAX_AI_PITCH_OFFSET) * confidence;

            yawDegrees = MathHelper.wrapDegrees(yawDegrees + yawOffset);
            pitchDegrees = MathHelper.clamp(pitchDegrees + pitchOffset, -90f, 90f);
        }

        cachedBlended[0] = yawDegrees;
        cachedBlended[1] = pitchDegrees;
        return cachedBlended;
    }

    /**
     * Face a block with AI-calculated rotations
     */
    public static void faceBlock(BlockPos pos) {
        if (pos == null || mc.player == null) return;

        // 1. Dynamic speed scaling based on distance
        double diffX = pos.getX() + 0.5 - mc.player.getPosX();
        double diffY = pos.getY() + 0.5 - (mc.player.getPosY() + mc.player.getEyeHeight());
        double diffZ = pos.getZ() + 0.5 - mc.player.getPosZ();
        double distance = Math.sqrt(diffX * diffX + diffY * diffY + diffZ * diffZ);
        float speedScale = getSpeedScale(distance);
        currentMaxYawChange = BASE_MAX_YAW_CHANGE * speedScale;
        currentMaxPitchChange = BASE_MAX_PITCH_CHANGE * speedScale;

        // 2. Fill inputs in the static cached array
        fillBlockInputs(pos, cachedInputs);

        // 3. Analytic aim blended with the network's small learned deviation
        calculateIdealBlockRotations(pos, cachedIdealRotation);
        float[] blended = blendWithPrediction(cachedIdealRotation[0], cachedIdealRotation[1], cachedInputs);
        float yawDegrees = blended[0];
        float pitchDegrees = blended[1];

        // 4. Noise injection
        if (enableNoise) {
            float noiseYaw = (float) ((Math.random() - 0.5) * 1.5);
            float noisePitch = (float) ((Math.random() - 0.5) * 1.0);
            yawDegrees = MathHelper.wrapDegrees(yawDegrees + noiseYaw);
            pitchDegrees = MathHelper.clamp(pitchDegrees + noisePitch, -90f, 90f);
        }

        // 5. Set target rotation
        rotationManager.setTargetRotation(yawDegrees, pitchDegrees);

        // 6. Training Throttling: only submit samples every N ticks
        if (tickCounter % TRAINING_SAMPLE_INTERVAL == 0) {
            normalizeRotations(cachedIdealRotation[0], cachedIdealRotation[1], cachedNormalized);

            // Pass cloned arrays to ensure training thread gets immutable snapshot
            trainingManager.addTrainingSample(cachedInputs.clone(), cachedNormalized.clone(), 1.0f);
        }
    }

    /**
     * Face an entity with AI-calculated rotations
     */
    public static void faceEntity(Entity entity) {
        if (entity == null || mc.player == null) return;

        // 1. Dynamic speed scaling based on distance
        double diffX = entity.getPosX() - mc.player.getPosX();
        double diffY = entity.getPosY() + entity.getEyeHeight() - (mc.player.getPosY() + mc.player.getEyeHeight());
        double diffZ = entity.getPosZ() - mc.player.getPosZ();
        double distance = Math.sqrt(diffX * diffX + diffY * diffY + diffZ * diffZ);
        float speedScale = getSpeedScale(distance);
        currentMaxYawChange = BASE_MAX_YAW_CHANGE * speedScale;
        currentMaxPitchChange = BASE_MAX_PITCH_CHANGE * speedScale;

        // 2. Fill inputs in the static cached array
        fillEntityInputs(entity, cachedInputs);

        // 3. Analytic aim blended with the network's small learned deviation
        calculateIdealRotations(entity, cachedIdealRotation);
        float[] blended = blendWithPrediction(cachedIdealRotation[0], cachedIdealRotation[1], cachedInputs);
        float yawDegrees = blended[0];
        float pitchDegrees = blended[1];

        // 4. Noise injection
        if (enableNoise) {
            float noiseYaw = (float) ((Math.random() - 0.5) * 1.5);
            float noisePitch = (float) ((Math.random() - 0.5) * 1.0);
            yawDegrees = MathHelper.wrapDegrees(yawDegrees + noiseYaw);
            pitchDegrees = MathHelper.clamp(pitchDegrees + noisePitch, -90f, 90f);
        }

        // 5. Set target rotation
        rotationManager.setTargetRotation(yawDegrees, pitchDegrees);

        // 6. Training Throttling: only submit samples every N ticks
        if (tickCounter % TRAINING_SAMPLE_INTERVAL == 0) {
            normalizeRotations(cachedIdealRotation[0], cachedIdealRotation[1], cachedNormalized);

            // Pass cloned arrays to ensure training thread gets immutable snapshot
            trainingManager.addTrainingSample(cachedInputs.clone(), cachedNormalized.clone(), 1.0f);
        }
    }

    // Helper method to compute distance-based speed scale
    private static float getSpeedScale(double distance) {
        if (distance <= 3.0) {
            return 1.0f;
        } else if (distance >= 15.0) {
            return 0.35f;
        } else {
            return (float) (1.0 - (distance - 3.0) / (15.0 - 3.0) * 0.65);
        }
    }

    // Input filling methods (non-allocating write pattern)
    private static void fillEntityInputs(Entity entity, float[] out) {
        if (entity == null || mc.player == null) {
            java.util.Arrays.fill(out, 0);
            return;
        }

        // Relative position
        double playerX = mc.player.getPosX();
        double playerY = mc.player.getPosY() + mc.player.getEyeHeight();
        double playerZ = mc.player.getPosZ();

        double entityX = entity.getPosX();
        double entityY = entity.getPosY() + entity.getEyeHeight();
        double entityZ = entity.getPosZ();

        double diffX = entityX - playerX;
        double diffY = entityY - playerY;
        double diffZ = entityZ - playerZ;

        // Normalize inputs
        out[0] = (float) (diffX / 20.0);
        out[1] = (float) (diffY / 10.0);
        out[2] = (float) (diffZ / 20.0);

        // Entity velocity (normalized)
        out[3] = (float) (entity.getMotion().x / 2.0);
        out[4] = (float) (entity.getMotion().y / 2.0);
        out[5] = (float) (entity.getMotion().z / 2.0);

        // Current rotations (normalized)
        out[6] = mc.player.rotationYaw / 180.0f;
        out[7] = mc.player.rotationPitch / 90.0f;
    }

    private static void fillBlockInputs(BlockPos pos, float[] out) {
        if (pos == null || mc.player == null) {
            java.util.Arrays.fill(out, 0);
            return;
        }

        // Relative position
        double playerX = mc.player.getPosX();
        double playerY = mc.player.getPosY() + mc.player.getEyeHeight();
        double playerZ = mc.player.getPosZ();

        double blockX = pos.getX() + 0.5;
        double blockY = pos.getY() + 0.5;
        double blockZ = pos.getZ() + 0.5;

        double diffX = blockX - playerX;
        double diffY = blockY - playerY;
        double diffZ = blockZ - playerZ;

        // Normalize inputs
        out[0] = (float) (diffX / 20.0);
        out[1] = (float) (diffY / 10.0);
        out[2] = (float) (diffZ / 20.0);

        // No velocity for blocks
        out[3] = 0;
        out[4] = 0;
        out[5] = 0;

        // Current rotations (normalized)
        out[6] = mc.player.rotationYaw / 180.0f;
        out[7] = mc.player.rotationPitch / 90.0f;
    }

    private static void fillPositionInputs(double targetX, double targetY, double targetZ, float[] out) {
        if (mc.player == null) {
            java.util.Arrays.fill(out, 0);
            return;
        }

        // Relative position
        double playerX = mc.player.getPosX();
        double playerY = mc.player.getPosY() + mc.player.getEyeHeight();
        double playerZ = mc.player.getPosZ();

        double diffX = targetX - playerX;
        double diffY = targetY - playerY;
        double diffZ = targetZ - playerZ;

        // Normalize inputs
        out[0] = (float) (diffX / 20.0);
        out[1] = (float) (diffY / 10.0);
        out[2] = (float) (diffZ / 20.0);

        // No velocity
        out[3] = 0;
        out[4] = 0;
        out[5] = 0;

        // Current rotations (normalized)
        out[6] = mc.player.rotationYaw / 180.0f;
        out[7] = mc.player.rotationPitch / 90.0f;
    }

    // Unified ideal rotation calculation helper
    private static void calculateIdealRotations(double tx, double ty, double tz, float[] out) {
        if (mc.player == null) {
            out[0] = 0;
            out[1] = 0;
            return;
        }
        double playerX = mc.player.getPosX();
        double playerY = mc.player.getPosY() + mc.player.getEyeHeight();
        double playerZ = mc.player.getPosZ();

        double diffX = tx - playerX;
        double diffY = ty - playerY;
        double diffZ = tz - playerZ;

        double dist = Math.sqrt(diffX * diffX + diffZ * diffZ);
        float idealYaw = (float) (Math.atan2(diffZ, diffX) * 180.0D / Math.PI) - 90.0F;
        idealYaw = MathHelper.wrapDegrees(idealYaw);
        float idealPitch = (float) -(Math.atan2(diffY, dist) * 180.0D / Math.PI);

        out[0] = idealYaw;
        out[1] = idealPitch;
    }

    private static void calculateIdealRotations(Entity entity, float[] out) {
        calculateIdealRotations(entity.getPosX(), entity.getPosY() + entity.getEyeHeight(), entity.getPosZ(), out);
    }

    private static void calculateIdealBlockRotations(BlockPos pos, float[] out) {
        calculateIdealRotations(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, out);
    }

    /**
     * Normalize rotations and write to target output array to avoid allocation
     */
    public static void normalizeRotations(float yaw, float pitch, float[] out) {
        out[0] = yaw / 180.0f;
        out[1] = pitch / 90.0f;
    }

    /**
     * Normalize rotations for neural network input (allocating version)
     */
    public static float[] normalizeRotations(float yaw, float pitch) {
        return new float[] {
                yaw / 180.0f,
                pitch / 90.0f
        };
    }

    /**
     * Record a successful hit on an entity
     */
    public static void recordHit(Entity entity, boolean wasMoving) {
        if (instance == null || entity == null || mc.player == null) return;

        // Get current rotations
        float currentYaw = instance.rotationManager.getCurrentYaw();
        float currentPitch = instance.rotationManager.getCurrentPitch();

        // Record hit with raw rotations (ReinforcementManager will normalize them)
        instance.reinforcementManager.recordHit(entity, wasMoving, currentYaw, currentPitch);
    }

    /**
     * Record a missed attack
     */
    public static void recordMiss(Entity entity) {
        if (instance == null || entity == null || mc.player == null) return;

        // Calculate ideal rotations using cache
        calculateIdealRotations(entity, cachedIdealRotation);

        // Normalize rotations using cache
        normalizeRotations(cachedIdealRotation[0], cachedIdealRotation[1], cachedNormalized);

        // Fill inputs using cache
        fillEntityInputs(entity, cachedInputs);

        // Create expected outputs
        float[] expectedOutputs = new float[NeuralNetwork.OUTPUT_SIZE];
        expectedOutputs[0] = cachedNormalized[0];
        expectedOutputs[1] = cachedNormalized[1];

        // Pass copies to reinforcementManager to avoid background modification issues
        instance.reinforcementManager.recordMiss(entity, cachedInputs.clone(), expectedOutputs);
    }

    /**
     * Update method to be called every tick
     */
    public static void onTick() {
        tickCounter++;

        if (mc.objectMouseOver != null && mc.objectMouseOver.getType() == RayTraceResult.Type.ENTITY) {
            Entity target = ((EntityRayTraceResult)mc.objectMouseOver).getEntity();
            if (target != null) {
                faceEntity(target);
            }
        }

        // Always update and apply rotations
        rotationManager.updateRotations();
        applyServerRotation();
    }
}