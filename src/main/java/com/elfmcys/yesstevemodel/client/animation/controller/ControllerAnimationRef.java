package com.elfmcys.yesstevemodel.client.animation.controller;

public final class ControllerAnimationRef {
    private final String animationName;
    private final String weightExpression;

    public ControllerAnimationRef(String animationName, String weightExpression) {
        this.animationName = animationName == null ? "" : animationName.trim();
        this.weightExpression = weightExpression == null || weightExpression.trim().isEmpty() ? "1" : weightExpression.trim();
    }

    public String getAnimationName() {
        return this.animationName;
    }

    public String getWeightExpression() {
        return this.weightExpression;
    }
}
