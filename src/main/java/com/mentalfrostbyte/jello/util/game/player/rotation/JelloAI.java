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

    // Remove duplicate faceBlock method - there are two implementations

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
     */
    public static void applyServerRotation() {
        if (mc.player == null) return;

        // Store original rotations
        float originalYaw = mc.player.rotationYaw;
        float originalPitch = mc.player.rotationPitch;

        // Apply calculated rotations - but limit the change per tick to avoid server kicks
        float serverYaw = rotationManager.getCurrentYaw();
        float serverPitch = rotationManager.getCurrentPitch();

        // Limit rotation change per tick to avoid server anti-cheat
        float maxYawChange = 30.0f; // Maximum degrees per tick
        float maxPitchChange = 15.0f; // Maximum degrees per tick

        float yawDiff = MathHelper.wrapDegrees(serverYaw - mc.player.prevRotationYaw);
        if (Math.abs(yawDiff) > maxYawChange) {
            serverYaw = mc.player.prevRotationYaw + (maxYawChange * Math.signum(yawDiff));
        }

        float pitchDiff = serverPitch - mc.player.prevRotationPitch;
        if (Math.abs(pitchDiff) > maxPitchChange) {
            serverPitch = mc.player.prevRotationPitch + (maxPitchChange * Math.signum(pitchDiff));
        }

        // Apply limited rotations
        mc.player.rotationYaw = serverYaw;
        mc.player.rotationPitch = serverPitch;


    }

    // Remove duplicate methods and keep only one version of each method

    /**
     * Get rotations to a specific position
     */
    public static float[] getRotationsToPosition(double x, double y, double z) {
        if (mc.player == null) return new float[2];

        // Calculate inputs for the neural network
        float[] inputs = getPositionInputs(x, y, z);

        // Use predictRotations instead of direct predict
        float[] rotations = neuralNetwork.predict(inputs);
        if (rotations == null || rotations.length < 2) {
            return new float[2]; // Return zeros if prediction failed
        }

        // Convert normalized rotations to game angles - FIXED conversion for [-1,1] range
        float yawDegrees = MathHelper.wrapDegrees(rotations[0] * 180f);
        float pitchDegrees = MathHelper.clamp(rotations[1] * 90f, -90f, 90f);

        return new float[] {yawDegrees, pitchDegrees};
    }

    /**
     * Face a block with AI-calculated rotations
     */
    public static void faceBlock(BlockPos pos) {
        if (pos == null || mc.player == null) return;

        // Calculate inputs for the neural network
        float[] inputs = getBlockInputs(pos);

        // Use predictRotations instead of direct predict
        float[] rotations = neuralNetwork.predict(inputs);
        if (rotations == null || rotations.length < 2) {
            return; // Exit if prediction failed
        }

        // Convert normalized rotations to game angles - FIXED conversion for [-1,1] range
        float yawDegrees = MathHelper.wrapDegrees(rotations[0] * 180f);
        float pitchDegrees = MathHelper.clamp(rotations[1] * 90f, -90f, 90f);

        // Calculate "ideal" rotations for training
        float[] idealRotations = calculateIdealBlockRotations(pos);
        float[] expectedOutputs = normalizeRotations(idealRotations[0], idealRotations[1]);

        // Add to training samples
        trainingManager.addTrainingSample(inputs, expectedOutputs, 1.0f);

        // Set target rotation
        rotationManager.setTargetRotation(yawDegrees, pitchDegrees);
    }

    /**
     * Face an entity with AI-calculated rotations
     */
    public static void faceEntity(Entity entity) {
        if (entity == null || mc.player == null) return;

        // Calculate inputs for the neural network
        float[] inputs = getEntityInputs(entity);

        // Use predictRotations instead of direct predict
        float[] rotations = neuralNetwork.predict(inputs);
        if (rotations == null || rotations.length < 2) {
            return; // Exit if prediction failed
        }

        // Convert normalized rotations to game angles - FIXED conversion for [-1,1] range
        float yawDegrees = MathHelper.wrapDegrees(rotations[0] * 180f);
        float pitchDegrees = MathHelper.clamp(rotations[1] * 90f, -90f, 90f);

        // Calculate "ideal" rotations for training
        float[] idealRotations = calculateIdealRotations(entity);
        float[] expectedOutputs = normalizeRotations(idealRotations[0], idealRotations[1]);

        // Add to training samples every tick for supervised learning
        trainingManager.addTrainingSample(inputs, expectedOutputs, 1.0f);

        // Set target rotation
        rotationManager.setTargetRotation(yawDegrees, pitchDegrees);
    }

    // Helper methods
    // In getEntityInputs method
    private static float[] getEntityInputs(Entity entity) {
        if (entity == null || mc.player == null) return new float[NeuralNetwork.INPUT_SIZE];

        // If we need to expand input size, update NeuralNetwork.INPUT_SIZE constant
        float[] inputs = new float[NeuralNetwork.INPUT_SIZE];

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
        inputs[0] = (float) (diffX / 20.0);
        inputs[1] = (float) (diffY / 10.0);
        inputs[2] = (float) (diffZ / 20.0);

        // Entity velocity (normalized)
        inputs[3] = (float) (entity.getMotion().x / 2.0);
        inputs[4] = (float) (entity.getMotion().y / 2.0);
        inputs[5] = (float) (entity.getMotion().z / 2.0);

        // Current rotations (normalized)
        inputs[6] = mc.player.rotationYaw / 180.0f;
        inputs[7] = mc.player.rotationPitch / 90.0f;

        // Add player's own motion as inputs if INPUT_SIZE allows
        // If NeuralNetwork.INPUT_SIZE is 10 or more:
        if (inputs.length >= 10) {
            inputs[8] = (float) (mc.player.getMotion().x / 0.3); // Normalized to typical max speed
            inputs[9] = (float) (mc.player.getMotion().z / 0.3);
        }

        return inputs;
    }

    private static float[] getBlockInputs(BlockPos pos) {
        if (pos == null || mc.player == null) return new float[NeuralNetwork.INPUT_SIZE];

        float[] inputs = new float[NeuralNetwork.INPUT_SIZE];

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
        inputs[0] = (float) (diffX / 20.0);
        inputs[1] = (float) (diffY / 10.0);
        inputs[2] = (float) (diffZ / 20.0);

        // No velocity for blocks
        inputs[3] = 0;
        inputs[4] = 0;
        inputs[5] = 0;

        // Current rotations (normalized)
        inputs[6] = mc.player.rotationYaw / 180.0f;
        inputs[7] = mc.player.rotationPitch / 90.0f;

        return inputs;
    }

    private static float[] getPositionInputs(double targetX, double targetY, double targetZ) {
        if (mc.player == null) return new float[NeuralNetwork.INPUT_SIZE];

        float[] inputs = new float[NeuralNetwork.INPUT_SIZE];

        // Relative position
        double playerX = mc.player.getPosX();
        double playerY = mc.player.getPosY() + mc.player.getEyeHeight();
        double playerZ = mc.player.getPosZ();

        double diffX = targetX - playerX;
        double diffY = targetY - playerY;
        double diffZ = targetZ - playerZ;

        // Normalize inputs
        inputs[0] = (float) (diffX / 20.0);
        inputs[1] = (float) (diffY / 10.0);
        inputs[2] = (float) (diffZ / 20.0);

        // No velocity
        inputs[3] = 0;
        inputs[4] = 0;
        inputs[5] = 0;

        // Current rotations (normalized)
        inputs[6] = mc.player.rotationYaw / 180.0f;
        inputs[7] = mc.player.rotationPitch / 90.0f;

        return inputs;
    }

    // In calculateIdealRotations method
    private static float[] calculateIdealRotations(Entity entity) {
        double playerX = mc.player.getPosX();
        double playerY = mc.player.getPosY() + mc.player.getEyeHeight();
        double playerZ = mc.player.getPosZ();

        // FIXED: Use consistent eye height calculation
        double entityX = entity.getPosX();
        double entityY = entity.getPosY() + entity.getEyeHeight(); // Now matches getEntityInputs
        double entityZ = entity.getPosZ();

        double diffX = entityX - playerX;
        double diffY = entityY - playerY;
        double diffZ = entityZ - playerZ;

        double dist = Math.sqrt(diffX * diffX + diffZ * diffZ);
        float idealYaw = (float) (Math.atan2(diffZ, diffX) * 180.0D / Math.PI) - 90.0F;
        // Wrap yaw to ensure it's in the -180 to 180 range
        idealYaw = MathHelper.wrapDegrees(idealYaw);
        float idealPitch = (float) -(Math.atan2(diffY, dist) * 180.0D / Math.PI);

        return new float[] {idealYaw, idealPitch};
    }

    private static float[] calculateIdealBlockRotations(BlockPos pos) {
        double playerX = mc.player.getPosX();
        double playerY = mc.player.getPosY() + mc.player.getEyeHeight();
        double playerZ = mc.player.getPosZ();

        double blockX = pos.getX() + 0.5;
        double blockY = pos.getY() + 0.5;
        double blockZ = pos.getZ() + 0.5;

        double diffX = blockX - playerX;
        double diffY = blockY - playerY;
        double diffZ = blockZ - playerZ;

        double dist = Math.sqrt(diffX * diffX + diffZ * diffZ);
        float idealYaw = (float) (Math.atan2(diffZ, diffX) * 180.0D / Math.PI) - 90.0F;
        // Wrap yaw to ensure it's in the -180 to 180 range
        idealYaw = MathHelper.wrapDegrees(idealYaw);
        float idealPitch = (float) -(Math.atan2(diffY, dist) * 180.0D / Math.PI);

        return new float[] {idealYaw, idealPitch};
    }

    /**
     * Normalize rotations for neural network input
     */
    public static float[] normalizeRotations(float yaw, float pitch) {
        // FIXED: Normalize to [-1,1] range for tanh activation
        return new float[] {
                yaw / 180.0f,    // maps [-180,180] → [-1,1]
                pitch / 90.0f    // maps [-90,90] → [-1,1]
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

        // FIXED: Normalize rotations to [-1,1] for tanh
        float[] normalizedRotations = normalizeRotations(currentYaw, currentPitch);

        // Record hit with normalized rotations
        instance.reinforcementManager.recordHit(entity, wasMoving, normalizedRotations[0], normalizedRotations[1]);
    }

    /**
     * Record a missed attack
     */
    public static void recordMiss(Entity entity) {
        if (instance == null || entity == null || mc.player == null) return;

        // Calculate ideal rotations
        float[] idealRotations = calculateIdealRotations(entity);

        // Ensure yaw is wrapped properly
        idealRotations[0] = MathHelper.wrapDegrees(idealRotations[0]);

        // FIXED: Normalize rotations to [-1,1] for tanh
        float[] normalizedRotations = normalizeRotations(idealRotations[0], idealRotations[1]);

        // Get inputs for this entity
        float[] inputs = getEntityInputs(entity);

        // Create expected outputs with normalized rotations
        float[] expectedOutputs = new float[NeuralNetwork.OUTPUT_SIZE];
        expectedOutputs[0] = normalizedRotations[0];  // yaw in [-1,1]
        expectedOutputs[1] = normalizedRotations[1];  // pitch in [-1,1]

        // Record miss with normalized rotations
        instance.reinforcementManager.recordMiss(entity, inputs, expectedOutputs);
    }

    /**
     * Update method to be called every tick
     */
    public static void onTick() {
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