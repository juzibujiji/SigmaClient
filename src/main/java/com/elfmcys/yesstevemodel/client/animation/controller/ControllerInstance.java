package com.elfmcys.yesstevemodel.client.animation.controller;

public final class ControllerInstance {
    private String currentState;
    private String previousState;
    private float stateStartedAtTicks;
    private float transitionStartedAtTicks;
    private float blendTransitionSeconds;
    private float previousStateElapsedSeconds;

    public ControllerInstance(String initialState, float nowTicks) {
        this.currentState = initialState == null ? "" : initialState;
        this.previousState = "";
        this.stateStartedAtTicks = nowTicks;
        this.transitionStartedAtTicks = nowTicks;
        this.blendTransitionSeconds = 0.0F;
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
        if (this.blendTransitionSeconds <= 0.0F || this.previousState.isEmpty()) {
            return 1.0F;
        }
        float elapsed = Math.max(0.0F, (nowTicks - this.transitionStartedAtTicks) / 20.0F);
        return Math.min(1.0F, elapsed / this.blendTransitionSeconds);
    }

    public void transitionTo(String nextState, float nowTicks, float blendTransitionSeconds) {
        if (nextState == null || nextState.isEmpty() || nextState.equals(this.currentState)) {
            return;
        }
        this.previousState = this.currentState;
        this.previousStateElapsedSeconds = elapsedSeconds(nowTicks);
        this.currentState = nextState;
        this.stateStartedAtTicks = nowTicks;
        this.transitionStartedAtTicks = nowTicks;
        this.blendTransitionSeconds = Math.max(0.0F, blendTransitionSeconds);
    }

    public void finishBlendIfComplete(float nowTicks) {
        if (blendProgress(nowTicks) >= 1.0F) {
            this.previousState = "";
            this.previousStateElapsedSeconds = 0.0F;
            this.blendTransitionSeconds = 0.0F;
        }
    }

    public void reset(String state, float nowTicks) {
        this.currentState = state == null ? "" : state;
        this.previousState = "";
        this.stateStartedAtTicks = nowTicks;
        this.transitionStartedAtTicks = nowTicks;
        this.blendTransitionSeconds = 0.0F;
        this.previousStateElapsedSeconds = 0.0F;
    }
}
