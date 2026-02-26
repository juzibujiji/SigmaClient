package com.mentalfrostbyte.jello.module.impl.combat.killaura;

/**
 * @author Avitld
 * @link <a href="https://github.com/OSClicker/OSClicker">OSClicker Github</a>
 */
public class ClickDelayCalculator {
    private double currentCPS = 10.0;
    private double targetCPS = 10.0;
    private long lastCpsUpdateTime = System.currentTimeMillis();

    // Configurable delay patterns (set externally)
    public double delayPattern1 = 90;
    public double delayPattern2 = 110;
    public double delayPattern3 = 130;
    public boolean patternEnabled = false;

    // CPS range
    public double minCPS;
    public double maxCPS;

    public void setMinMax(double minCPS, double maxCPS) {
        this.minCPS = minCPS;
        this.maxCPS = maxCPS;
    }

    public ClickDelayCalculator(double minCPS, double maxCPS) {
        this.minCPS = minCPS;
        this.maxCPS = maxCPS;
    }

    public long getClickDelay() {
        long now = System.currentTimeMillis();

        long changeInterval = 2000; // how often to change target CPS
        if (now - lastCpsUpdateTime > changeInterval) {
            lastCpsUpdateTime = now;
            targetCPS = minCPS + Math.random() * (maxCPS - minCPS);
        }

        double smoothingFactor = 0.05;
        currentCPS += (targetCPS - currentCPS) * smoothingFactor;

        double delay = 1000.0 / currentCPS;

        if (patternEnabled) {
            int pattern = 1 + (int) (Math.random() * 3);
            switch (pattern) {
                case 1 -> delay = delayPattern1;
                case 2 -> delay = delayPattern2;
                case 3 -> delay = delayPattern3;
            }
        }

        return (long) delay;
    }
}