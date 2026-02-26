package com.mentalfrostbyte.jello.util.game.player.rotation;

import com.mentalfrostbyte.Client;

import java.io.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * Neural network implementation for JelloAI
 */
public class NeuralNetwork {
    private static final Random random = new Random();

    // Neural Network parameters
    public static final int INPUT_SIZE = 8;
    public static final int HIDDEN_SIZE = 16;
    public static final int OUTPUT_SIZE = 2;

    // Neural Network weights
    private float[][] weightsInputToHidden = new float[INPUT_SIZE][HIDDEN_SIZE];
    private float[][] weightsHiddenToOutput = new float[HIDDEN_SIZE][OUTPUT_SIZE];

    // Biases
    private float[] hiddenBiases = new float[HIDDEN_SIZE];
    private float[] outputBiases = new float[OUTPUT_SIZE];

    // Learning parameters
    private static final float LEARNING_RATE = 0.03f;  // Increased from 0.01f
    private static final float MOMENTUM = 0.7f;

    // Previous weight changes (for momentum)
    private float[][] prevDeltaInputHidden = new float[INPUT_SIZE][HIDDEN_SIZE];
    private float[][] prevDeltaHiddenOutput = new float[HIDDEN_SIZE][OUTPUT_SIZE];

    // Batch training
    private List<TrainingSample> batchSamples = new ArrayList<>();
    private static final int BATCH_SIZE = 12;
    private static final int EPOCHS_PER_BATCH = 15;

    // File paths for saving/loading the neural network
    private static final String WEIGHTS_FILE = "jelloai_weights.dat";

    // Training counter for auto-save
    private int trainingCounter = 0;
    private static final int SAVE_INTERVAL = 50;

    // Track if network is initialized
    private boolean initialized = false;

    /**
     * Initialize the neural network
     */
    public void initialize() {
        // Try to load existing weights
        if (!loadWeights()) {
            // Initialize with random weights if loading fails
            initializeRandomWeights();
        }
        initialized = true;
    }

    /**
     * Initialize weights with small random values
     */
    private void initializeRandomWeights() {
        for (int i = 0; i < INPUT_SIZE; i++) {
            for (int j = 0; j < HIDDEN_SIZE; j++) {
                weightsInputToHidden[i][j] = (random.nextFloat() - 0.5f) * 0.1f;
                prevDeltaInputHidden[i][j] = 0;
            }
        }

        for (int i = 0; i < HIDDEN_SIZE; i++) {
            hiddenBiases[i] = (random.nextFloat() - 0.5f) * 0.1f;

            for (int j = 0; j < OUTPUT_SIZE; j++) {
                weightsHiddenToOutput[i][j] = (random.nextFloat() - 0.5f) * 0.1f;
                prevDeltaHiddenOutput[i][j] = 0;
            }
        }

        for (int i = 0; i < OUTPUT_SIZE; i++) {
            outputBiases[i] = (random.nextFloat() - 0.5f) * 0.1f;
        }
    }

    /**
     * Check if network is initialized
     */
    public boolean isInitialized() {
        return initialized;
    }

    // Add a sample counter to track training progress
    private int sampleCount = 0;
    private static final int CONFIDENCE_THRESHOLD = 100; // Samples needed for full confidence

    /**
     * Get confidence level of the network
     */
    public float getConfidence() {
        if (!initialized) return 0.0f;

        // Return a confidence that grows as we see more samples
        return Math.min(1.0f, (float)sampleCount / CONFIDENCE_THRESHOLD);
    }

    /**
     * Add a sample to the batch
     */
    public void addToBatch(float[] inputs, float[] expected, float weight) {
        if (batchSamples.size() >= BATCH_SIZE) {
            trainBatch();
        }

        batchSamples.add(new TrainingSample(inputs, expected, weight));
    }

    /**
     * Train on the current batch
     */
    public void trainBatch() {
        if (batchSamples.isEmpty()) return;

        float totalError = 0;

        // Train for multiple epochs on the batch
        for (int epoch = 0; epoch < EPOCHS_PER_BATCH; epoch++) {
            // Shuffle batch for better training
            Collections.shuffle(batchSamples);

            for (TrainingSample sample : batchSamples) {
                totalError += trainNetworkImmediate(
                        sample.inputs, sample.expected, sample.weight);
            }
        }

        // Log average error
        Client.logger.info("JelloAI: Batch training completed, average error: " +
                (totalError / (batchSamples.size() * EPOCHS_PER_BATCH)));

        // Clear batch after training
        batchSamples.clear();
    }

    /**
     * Train the neural network immediately on a single sample
     */
    public float trainNetworkImmediate(float[] inputs, float[] expectedOutputs, float weight) {
        // Increment sample counter for confidence calculation
        sampleCount++;

        // For negative weights, we want to push away from the target
        boolean pushAway = weight < 0;
        float absWeight = Math.abs(weight);

        // Forward pass
        float[] hiddenOutputs = new float[HIDDEN_SIZE];
        for (int i = 0; i < HIDDEN_SIZE; i++) {
            float sum = hiddenBiases[i];
            for (int j = 0; j < INPUT_SIZE; j++) {
                sum += inputs[j] * weightsInputToHidden[j][i];
            }
            hiddenOutputs[i] = sigmoid(sum);
        }

        float[] outputs = new float[OUTPUT_SIZE];
        for (int i = 0; i < OUTPUT_SIZE; i++) {
            float sum = outputBiases[i];
            for (int j = 0; j < HIDDEN_SIZE; j++) {
                sum += hiddenOutputs[j] * weightsHiddenToOutput[j][i];
            }
            outputs[i] = tanh(sum); // Changed to tanh
        }

        // Log expected vs predicted for debugging
        Client.logger.info("JelloAI Training - Expected: [" +
                expectedOutputs[0] + ", " + expectedOutputs[1] + "], Predicted: [" +
                outputs[0] + ", " + outputs[1] + "], Weight: " + weight);

        // Calculate error
        float totalError = 0;
        float[] outputErrors = new float[OUTPUT_SIZE];

        for (int i = 0; i < OUTPUT_SIZE; i++) {
            float error = expectedOutputs[i] - outputs[i];

            // If weight is negative, reverse the direction of the gradient
            if (pushAway) {
                error = -error;
            }

            outputErrors[i] = error * tanhDerivative(outputs[i]) * absWeight;
            totalError += error * error;
        }

        // Hidden layer error
        float[] hiddenErrors = new float[HIDDEN_SIZE];
        for (int i = 0; i < HIDDEN_SIZE; i++) {
            float error = 0;
            for (int j = 0; j < OUTPUT_SIZE; j++) {
                error += outputErrors[j] * weightsHiddenToOutput[i][j];
            }
            hiddenErrors[i] = error * sigmoidDerivative(hiddenOutputs[i]);
        }

        // Update weights and biases with momentum
        // Hidden to output weights
        for (int i = 0; i < HIDDEN_SIZE; i++) {
            for (int j = 0; j < OUTPUT_SIZE; j++) {
                float delta = LEARNING_RATE * outputErrors[j] * hiddenOutputs[i] + MOMENTUM * prevDeltaHiddenOutput[i][j];
                weightsHiddenToOutput[i][j] += delta;
                prevDeltaHiddenOutput[i][j] = delta;
            }
        }

        // Output biases
        for (int i = 0; i < OUTPUT_SIZE; i++) {
            outputBiases[i] += LEARNING_RATE * outputErrors[i];
        }

        // Input to hidden weights
        for (int i = 0; i < INPUT_SIZE; i++) {
            for (int j = 0; j < HIDDEN_SIZE; j++) {
                float delta = LEARNING_RATE * hiddenErrors[j] * inputs[i] + MOMENTUM * prevDeltaInputHidden[i][j];
                weightsInputToHidden[i][j] += delta;
                prevDeltaInputHidden[i][j] = delta;
            }
        }

        // Hidden biases
        for (int i = 0; i < HIDDEN_SIZE; i++) {
            hiddenBiases[i] += LEARNING_RATE * hiddenErrors[i];
        }

        // Auto-save weights periodically
        trainingCounter++;
        if (trainingCounter >= SAVE_INTERVAL) {
            saveWeights();
            trainingCounter = 0;
        }

        return totalError;
    }

    /**
     * Save neural network weights to file
     */
    public void saveWeights() {
        try {
            File file = new File(Client.getInstance().file, WEIGHTS_FILE);
            DataOutputStream out = new DataOutputStream(new FileOutputStream(file));

            // Save input to hidden weights
            for (int i = 0; i < INPUT_SIZE; i++) {
                for (int j = 0; j < HIDDEN_SIZE; j++) {
                    out.writeFloat(weightsInputToHidden[i][j]);
                }
            }

            // Save hidden biases
            for (int i = 0; i < HIDDEN_SIZE; i++) {
                out.writeFloat(hiddenBiases[i]);
            }

            // Save hidden to output weights
            for (int i = 0; i < HIDDEN_SIZE; i++) {
                for (int j = 0; j < OUTPUT_SIZE; j++) {
                    out.writeFloat(weightsHiddenToOutput[i][j]);
                }
            }

            // Save output biases
            for (int i = 0; i < OUTPUT_SIZE; i++) {
                out.writeFloat(outputBiases[i]);
            }

            out.close();
            Client.logger.info("JelloAI: Saved neural network weights");
        } catch (Exception e) {
            Client.logger.error("Error saving JelloAI weights", e);
        }
    }

    /**
     * Load neural network weights from file
     */
    private boolean loadWeights() {
        try {
            File file = new File(Client.getInstance().file, WEIGHTS_FILE);
            if (!file.exists()) return false;

            DataInputStream in = new DataInputStream(new FileInputStream(file));

            // Load input to hidden weights
            for (int i = 0; i < INPUT_SIZE; i++) {
                for (int j = 0; j < HIDDEN_SIZE; j++) {
                    weightsInputToHidden[i][j] = in.readFloat();
                }
            }

            // Load hidden biases
            for (int i = 0; i < HIDDEN_SIZE; i++) {
                hiddenBiases[i] = in.readFloat();
            }

            // Load hidden to output weights
            for (int i = 0; i < HIDDEN_SIZE; i++) {
                for (int j = 0; j < OUTPUT_SIZE; j++) {
                    weightsHiddenToOutput[i][j] = in.readFloat();
                }
            }

            // Load output biases
            for (int i = 0; i < OUTPUT_SIZE; i++) {
                outputBiases[i] = in.readFloat();
            }

            in.close();
            Client.logger.info("JelloAI: Loaded neural network weights");
            return true;
        } catch (Exception e) {
            Client.logger.error("Error loading JelloAI weights", e);
            return false;
        }
    }

    // Helper methods
    private float sigmoid(float x) {
        return (float) (1.0 / (1.0 + Math.exp(-x)));
    }

    private float sigmoidDerivative(float x) {
        return x * (1 - x);
    }

    // New tanh activation function for better angle prediction
    private float tanh(float x) {
        return (float) Math.tanh(x);
    }

    // Derivative of tanh for backpropagation
    private float tanhDerivative(float x) {
        return 1 - x * x;
    }

    private void shuffleArray(Object[] array) {
        for (int i = array.length - 1; i > 0; i--) {
            int index = random.nextInt(i + 1);
            Object temp = array[index];
            array[index] = array[i];
            array[i] = temp;
        }
    }

    /**
     * Train the neural network using backpropagation on a batch of samples
     */
    public void trainNetwork(TrainingSample[] samples) {
        if (samples.length == 0) return;

        // Increment sample counter for each batch of samples
        sampleCount += samples.length;

        // Shuffle samples for better training
        shuffleArray(samples);

        // Train for multiple epochs
        for (int epoch = 0; epoch < 3; epoch++) {
            for (TrainingSample sample : samples) {
                float[] inputs = sample.getInputs();
                float[] expectedOutputs = sample.getExpectedOutputs();
                float weight = sample.getWeight();

                // Use the immediate training method for each sample
                trainNetworkImmediate(inputs, expectedOutputs, weight);
            }
        }

        // Save weights after batch training
        saveWeights();
    }

    /**
     * Predict outputs for given inputs
     */
    public float[] predict(float[] inputs) {
        if (!initialized) {
            return new float[OUTPUT_SIZE]; // Return zeros if not initialized
        }

        // Hidden layer
        float[] hiddenOutputs = new float[HIDDEN_SIZE];
        for (int i = 0; i < HIDDEN_SIZE; i++) {
            float sum = hiddenBiases[i];
            for (int j = 0; j < INPUT_SIZE; j++) {
                sum += inputs[j] * weightsInputToHidden[j][i];
            }
            hiddenOutputs[i] = sigmoid(sum);
        }

        // Output layer
        float[] outputs = new float[OUTPUT_SIZE];
        for (int i = 0; i < OUTPUT_SIZE; i++) {
            float sum = outputBiases[i];
            for (int j = 0; j < HIDDEN_SIZE; j++) {
                sum += hiddenOutputs[j] * weightsHiddenToOutput[j][i];
            }
            outputs[i] = tanh(sum); // Using tanh for output
        }

        return outputs;
    }

    /**
     * Inner class for batch training samples
     */
    private static class TrainingSample {
        float[] inputs;
        float[] expected;
        float weight;

        TrainingSample(float[] inputs, float[] expected, float weight) {
            this.inputs = inputs;
            this.expected = expected;
            this.weight = weight;
        }

        public float[] getInputs() {
            return inputs;
        }

        public float[] getExpectedOutputs() {
            return expected;
        }

        public float getWeight() {
            return weight;
        }
    }
}