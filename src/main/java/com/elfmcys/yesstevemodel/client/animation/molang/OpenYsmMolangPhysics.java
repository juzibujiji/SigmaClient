package com.elfmcys.yesstevemodel.client.animation.molang;

import net.minecraft.util.math.MathHelper;

import java.util.LinkedHashMap;
import java.util.Map;

final class OpenYsmMolangPhysics {
    private static final int MAX_STATES = 4096;
    private static final Map<String, PhysicsState> STATES = new LinkedHashMap<String, PhysicsState>(256, 0.75F, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, PhysicsState> eldest) {
            return size() > MAX_STATES;
        }
    };

    private OpenYsmMolangPhysics() {
    }

    static double firstOrder(String scope, String name, double input, double response, double ageInTicks) {
        String key = key("first", scope, name);
        synchronized (STATES) {
            PhysicsState state = STATES.get(key);
            if (!(state instanceof FirstOrderState)) {
                STATES.put(key, new FirstOrderState(input, response, ageInTicks));
                return input;
            }
            state.updateTo(ageInTicks);
            state.setArgs(input, response, 0.0D, 0.0D);
            return state.value();
        }
    }

    static double secondOrder(String scope, String name, double input, double frequency, double coefficient,
                              double response, double ageInTicks) {
        String key = key("second", scope, name);
        synchronized (STATES) {
            PhysicsState state = STATES.get(key);
            if (!(state instanceof SecondOrderState)) {
                STATES.put(key, new SecondOrderState(input, frequency, coefficient, response, ageInTicks));
                return input;
            }
            state.updateTo(ageInTicks);
            state.setArgs(input, frequency, coefficient, response);
            return state.value();
        }
    }

    private static String key(String type, String scope, String name) {
        String safeName = name == null ? "" : name;
        if (safeName.length() > 128) {
            safeName = safeName.substring(0, 128);
        }
        return type + "|" + (scope == null ? "" : scope) + "|" + safeName;
    }

    private abstract static class PhysicsState {
        private double lastAgeInTicks;

        PhysicsState(double ageInTicks) {
            this.lastAgeInTicks = ageInTicks;
        }

        final void updateTo(double ageInTicks) {
            if (ageInTicks <= this.lastAgeInTicks) {
                return;
            }
            double timeStep = Math.min(0.25D, (ageInTicks - this.lastAgeInTicks) / 20.0D);
            this.lastAgeInTicks = ageInTicks;
            if (timeStep > 0.0D) {
                update(timeStep);
            }
        }

        abstract void update(double timeStep);

        abstract void setArgs(double arg0, double arg1, double arg2, double arg3);

        abstract double value();
    }

    private static final class FirstOrderState extends PhysicsState {
        private double input;
        private double response;
        private double lastSimulation;

        FirstOrderState(double input, double response, double ageInTicks) {
            super(ageInTicks);
            this.input = input;
            this.response = response;
        }

        @Override
        void update(double timeStep) {
            double safeResponse = Math.max(0.0001D, this.response);
            this.lastSimulation = ((1.0D - (timeStep / safeResponse)) * this.lastSimulation)
                    + ((timeStep / safeResponse) * this.input);
        }

        @Override
        void setArgs(double arg0, double arg1, double arg2, double arg3) {
            this.input = arg0;
            this.response = arg1;
        }

        @Override
        double value() {
            return this.lastSimulation;
        }
    }

    private static final class SecondOrderState extends PhysicsState {
        private double inputFunction;
        private double lastSimulation;
        private double lastSimulationDot;
        private double input;
        private double frequency;
        private double coefficient;
        private double response;

        SecondOrderState(double input, double frequency, double coefficient, double response, double ageInTicks) {
            super(ageInTicks);
            this.input = input;
            this.frequency = frequency;
            this.coefficient = coefficient;
            this.response = response;
        }

        @Override
        void update(double timeStep) {
            double safeFrequency = Math.max(0.0001D, MathHelper.clamp((float) this.frequency, 0.0F, 5.0F));
            double safeCoefficient = MathHelper.clamp((float) this.coefficient, 0.0F, 1.0F);
            double k1 = safeCoefficient / Math.PI / safeFrequency;
            double k2 = 1.0D / (2.0D * Math.PI * safeFrequency) / (2.0D * Math.PI * safeFrequency);
            double k3 = this.response * safeCoefficient / 2.0D / Math.PI / safeFrequency;

            double inputFunctionDot = (this.input - this.inputFunction) / timeStep;
            this.inputFunction = this.input;

            double maxTimeStep = Math.sqrt(4.0D * k2 + k1 * k1) - k1;
            int cycleTime = Math.max(1, (int) Math.ceil(timeStep / Math.max(0.0001D, maxTimeStep)));
            double step = timeStep / cycleTime;

            double simulation = this.lastSimulation;
            double simulationDot = this.lastSimulationDot;
            for (; cycleTime > 0; cycleTime--) {
                simulation = simulation + step * simulationDot;
                simulationDot = simulationDot + step
                        * (k3 * inputFunctionDot + this.input - simulation - k1 * simulationDot) / k2;
            }
            this.lastSimulation = simulation;
            this.lastSimulationDot = simulationDot;
        }

        @Override
        void setArgs(double arg0, double arg1, double arg2, double arg3) {
            this.input = arg0;
            this.frequency = arg1;
            this.coefficient = arg2;
            this.response = arg3;
        }

        @Override
        double value() {
            return this.lastSimulation;
        }
    }
}
