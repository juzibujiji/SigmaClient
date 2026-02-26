package com.mentalfrostbyte.jello.util.game.player.rotation;

import com.mentalfrostbyte.Client;

import java.io.*;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Manages training data and training process
 */
public class TrainingManager {
    // Training data
    private static final int MAX_SAMPLES = 1000;
    private static final int BATCH_SIZE = 12;
    private Queue<TrainingSample> trainingSamples = new ConcurrentLinkedQueue<>();
    private Thread trainingThread;
    private boolean trainingThreadRunning = false;

    // File paths for saving/loading training data
    private static final String TRAINING_FILE = "jelloai_training.dat";

    // Reference to neural network
    private final NeuralNetwork neuralNetwork;

    // Updated constructor to accept shared instance
    public TrainingManager(NeuralNetwork sharedNetwork) {
        this.neuralNetwork = sharedNetwork;
    }

    public TrainingManager() {
        this.neuralNetwork = new NeuralNetwork();
    }

    /**
     * Initialize the training manager
     */
    public void initialize() {
        // Delete existing training data file to avoid serialization issues
        deleteTrainingDataFile();
        loadTrainingData();
    }

    /**
     * Delete the training data file if it exists
     */
    private void deleteTrainingDataFile() {
        try {
            File file = new File(Client.getInstance().file, TRAINING_FILE);
            if (file.exists()) {
                boolean deleted = file.delete();
                if (deleted) {
                    Client.logger.info("JelloAI: Deleted old training data file");
                } else {
                    Client.logger.warn("JelloAI: Failed to delete old training data file");
                }
            }
        } catch (Exception e) {
            Client.logger.error("Error deleting JelloAI training data file", e);
        }
    }

    /**
     * Add a training sample
     */
    public void addTrainingSample(float[] inputs, float[] expected, float weight) {
        if (inputs.length != NeuralNetwork.INPUT_SIZE || expected.length != NeuralNetwork.OUTPUT_SIZE) {
            return;
        }

        // Add to the training queue
        synchronized (trainingSamples) {
            if (trainingSamples.size() >= MAX_SAMPLES) {
                trainingSamples.poll(); // Remove oldest sample
            }
            trainingSamples.add(new TrainingSample(inputs, expected, weight));
        }

        // If we have enough samples, trigger a batch training
        if (trainingSamples.size() >= BATCH_SIZE) {
            trainNetwork();
        }
    }

    /**
     * Train the neural network with current samples
     */
    private void trainNetwork() {
        if (trainingSamples.isEmpty()) return;

        // Convert queue to array for training
        TrainingSample[] samples = trainingSamples.toArray(new TrainingSample[0]);

        // Train the network with each sample individually
        for (TrainingSample sample : samples) {
            neuralNetwork.trainNetworkImmediate(
                    sample.getInputs(),
                    sample.getExpectedOutputs(),
                    sample.getWeight()
            );
        }

        // Clear the queue after training
        trainingSamples.clear();
    }

    /**
     * Start the background training thread
     */
    public void startTrainingThread() {
        if (trainingThreadRunning) return;

        trainingThreadRunning = true;
        trainingThread = new Thread(() -> {
            while (trainingThreadRunning) {
                try {
                    // Train the network
                    trainNetwork();

                    // Save weights and training data periodically
                    neuralNetwork.saveWeights();
                    saveTrainingData();

                    // Sleep to avoid excessive CPU usage
                    Thread.sleep(30000); // Train every 30 seconds
                } catch (Exception e) {
                    Client.logger.error("Error in JelloAI training thread", e);
                }
            }
        });

        trainingThread.setDaemon(true);
        trainingThread.start();
    }

    /**
     * Stop the training thread
     */
    public void stopTrainingThread() {
        trainingThreadRunning = false;
        if (trainingThread != null) {
            trainingThread.interrupt();
        }
    }

    /**
     * Save training data to file
     */
    private void saveTrainingData() {
        try {
            File file = new File(Client.getInstance().file, TRAINING_FILE);
            ObjectOutputStream out = new ObjectOutputStream(new FileOutputStream(file));

            // Convert queue to array for saving
            TrainingSample[] samples = trainingSamples.toArray(new TrainingSample[0]);
            out.writeObject(samples);

            out.close();
        } catch (Exception e) {
            Client.logger.error("Error saving JelloAI training data", e);
        }
    }

    /**
     * Load training data from file
     */
    private void loadTrainingData() {
        try {
            File file = new File(Client.getInstance().file, TRAINING_FILE);
            if (!file.exists()) return;

            ObjectInputStream in = new ObjectInputStream(new FileInputStream(file));

            // Read samples and add to queue
            TrainingSample[] samples = (TrainingSample[]) in.readObject();
            for (TrainingSample sample : samples) {
                trainingSamples.add(sample);
            }

            in.close();
        } catch (Exception e) {
            Client.logger.error("Error loading JelloAI training data", e);
        }
    }

    /**
     * Inner class for training samples
     */
    public static class TrainingSample implements Serializable {
        private static final long serialVersionUID = 1L;

        private float[] inputs;
        private float[] expected;
        private float weight;

        public TrainingSample(float[] inputs, float[] expected, float weight) {
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