package com.elfmcys.yesstevemodel.client.animation.controller;

public final class ControllerTransition {
    private final String targetState;
    private final String expression;

    public ControllerTransition(String targetState, String expression) {
        this.targetState = targetState == null ? "" : targetState.trim();
        this.expression = expression == null ? "" : expression.trim();
    }

    public String getTargetState() {
        return this.targetState;
    }

    public String getExpression() {
        return this.expression;
    }
}
