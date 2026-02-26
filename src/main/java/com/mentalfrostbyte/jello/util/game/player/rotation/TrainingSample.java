package com.mentalfrostbyte.jello.util.game.player.rotation;

import java.io.Serializable;

/**
 * Represents a single training sample for the neural network
 */
public class TrainingSample implements Serializable {
    // Add a serialVersionUID for version control
    private static final long serialVersionUID = 1L;

    private float[] inputs;
    private float[] expectedOutputs;
    private float weight;

    /**
     * Create a new training sample
     *
     * @param inputs The input values for the neural network
     * @param expectedOutputs The expected output values
     * @param weight The importance weight of this sample (higher = more important)
     */
    public TrainingSample(float[] inputs, float[] expectedOutputs, float weight) {
        this.inputs = inputs;
        this.expectedOutputs = expectedOutputs;
        this.weight = weight;
    }

    /**
     * Create a new training sample with default weight of 1.0
     *
     * @param inputs The input values for the neural network
     * @param expectedOutputs The expected output values
     */
    public TrainingSample(float[] inputs, float[] expectedOutputs) {
        this(inputs, expectedOutputs, 1.0f);
    }

    /**
     * Get the input values
     */
    public float[] getInputs() {
        return inputs;
    }

    /**
     * Get the expected output values
     */
    public float[] getExpectedOutputs() {
        return expectedOutputs;
    }

    /**
     * Get the importance weight of this sample
     */
    public float getWeight() {
        return weight;
    }

    /**
     * Set the importance weight of this sample
     */
    public void setWeight(float weight) {
        this.weight = weight;
    }
}