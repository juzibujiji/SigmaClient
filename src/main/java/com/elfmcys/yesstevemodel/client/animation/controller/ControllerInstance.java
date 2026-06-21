package com.elfmcys.yesstevemodel.client.animation.controller;

public final class ControllerInstance {
    private String currentState;
    private String previousState;
    private float stateStartedAtTicks;
    private float transitionStartedAtTicks;
    private float blendTransitionSeconds;
    private float[] blendTransitionTimes;
    private float[] blendTransitionValues;
    private float previousStateElapsedSeconds;

    public ControllerInstance(String initialState, float nowTicks) {
        this.currentState = initialState == null ? "" : initialState;
        this.previousState = "";
        this.stateStartedAtTicks = nowTicks;
        this.transitionStartedAtTicks = nowTicks;
        this.blendTransitionSeconds = 0.0F;
        this.blendTransitionTimes = new float[0];
        this.blendTransitionValues = new float[0];
        this.previousStateElapsedSeconds = 0.0F;
    }

    public String getCurrentState() {
        return this.currentState;
    }

    public String getPreviousState() {
        return this.previousState;
    }

    public float getStateStartedAtTicks() {
        return this.stateStartedAtTicks;
    }

    public float getPreviousStateElapsedSeconds() {
        return this.previousStateElapsedSeconds;
    }

    public float elapsedSeconds(float nowTicks) {
        return Math.max(0.0F, (nowTicks - this.stateStartedAtTicks) / 20.0F);
    }

    public float blendProgress(float nowTicks) {
        if (this.previousState.isEmpty()) {
            return 1.0F;
        }
        float elapsedSeconds = Math.max(0.0F, (nowTicks - this.transitionStartedAtTicks) / 20.0F);
        if (this.blendTransitionTimes.length > 1) {
            return sampleBlendCurve(elapsedSeconds);
        }
        if (this.blendTransitionSeconds <= 0.0F) {
            return 1.0F;
        }
        float elapsed = elapsedSeconds;
        return Math.min(1.0F, elapsed / this.blendTransitionSeconds);
    }

    public void transitionTo(String nextState, float nowTicks, float blendTransitionSeconds) {
        transitionTo(nextState, nowTicks, blendTransitionSeconds, new float[0], new float[0]);
    }

    public void transitionTo(String nextState, float nowTicks, ControllerStateDefinition targetState) {
        if (targetState == null) {
            transitionTo(nextState, nowTicks, 0.0F);
            return;
        }
        transitionTo(nextState, nowTicks, targetState.getBlendTransitionDurationSeconds(),
                targetState.getBlendTransitionTimes(), targetState.getBlendTransitionValues());
    }

    private void transitionTo(String nextState, float nowTicks, float blendTransitionSeconds,
                              float[] blendTransitionTimes, float[] blendTransitionValues) {
        if (nextState == null || nextState.isEmpty() || nextState.equals(this.currentState)) {
            return;
        }
        this.previousState = this.currentState;
        this.previousStateElapsedSeconds = elapsedSeconds(nowTicks);
        this.currentState = nextState;
        this.stateStartedAtTicks = nowTicks;
        this.transitionStartedAtTicks = nowTicks;
        this.blendTransitionSeconds = Math.max(0.0F, blendTransitionSeconds);
        this.blendTransitionTimes = blendTransitionTimes == null ? new float[0] : blendTransitionTimes.clone();
        this.blendTransitionValues = blendTransitionValues == null ? new float[0] : blendTransitionValues.clone();
    }

    public void finishBlendIfComplete(float nowTicks) {
        if (blendProgress(nowTicks) >= 1.0F) {
            this.previousState = "";
            this.previousStateElapsedSeconds = 0.0F;
            this.blendTransitionSeconds = 0.0F;
            this.blendTransitionTimes = new float[0];
            this.blendTransitionValues = new float[0];
        }
    }

    public void reset(String state, float nowTicks) {
        this.currentState = state == null ? "" : state;
        this.previousState = "";
        this.stateStartedAtTicks = nowTicks;
        this.transitionStartedAtTicks = nowTicks;
        this.blendTransitionSeconds = 0.0F;
        this.blendTransitionTimes = new float[0];
        this.blendTransitionValues = new float[0];
        this.previousStateElapsedSeconds = 0.0F;
    }

    private float sampleBlendCurve(float elapsedSeconds) {
        int count = Math.min(this.blendTransitionTimes.length, this.blendTransitionValues.length);
        if (count < 2) {
            return 1.0F;
        }
        if (elapsedSeconds <= this.blendTransitionTimes[0]) {
            return clamp01(1.0F - this.blendTransitionValues[0]);
        }
        for (int i = 0; i < count - 1; i++) {
            float startTime = this.blendTransitionTimes[i];
            float endTime = this.blendTransitionTimes[i + 1];
            if (elapsedSeconds <= endTime) {
                float duration = endTime - startTime;
                float alpha = duration <= 0.0F ? 1.0F : (elapsedSeconds - startTime) / duration;
                float startProgress = 1.0F - this.blendTransitionValues[i];
                float endProgress = 1.0F - this.blendTransitionValues[i + 1];
                return clamp01(startProgress + (endProgress - startProgress) * alpha);
            }
        }
        return clamp01(1.0F - this.blendTransitionValues[count - 1]);
    }

    private static float clamp01(float value) {
        if (value < 0.0F) {
            return 0.0F;
        }
        return Math.min(1.0F, value);
    }
}
