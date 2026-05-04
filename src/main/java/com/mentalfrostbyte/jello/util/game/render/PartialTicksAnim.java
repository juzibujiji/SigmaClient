package com.mentalfrostbyte.jello.util.game.render;

import static com.mentalfrostbyte.jello.util.game.MinecraftUtil.mc;

public class PartialTicksAnim {
    private float value;
    private float lastValue;
    public boolean done;
    public PartialTicksAnim(float y) {
        this.value = y;
    }
    public static float calculateCompensation(float target, float current, double speed) {
        float diff = current - target;
        double max = speed * 3;
        if (diff > speed) {
            current -= max - 0.005;
            if (current < target) {
                current = target;
            }
        } else if (diff < -speed) {
            current += max + 0.005;
            if (current > target) {
                current = target;
            }
        } else {
            current = target;
        }
        return current;
    }
    public void interpolate(float targetY, double speed) {
        if(speed == 0) return;
        lastValue = value;
        double deltaY = Math.max((Math.abs(targetY - value) * 0.35f) / (10 / speed), 0.05);
        if(speed <= 0){
            deltaY = -speed;
        }
        value = calculateCompensation(targetY, value, deltaY);
        if(Math.abs(targetY - value) <= 0.05){
            value = targetY;
        }
    }

    public float getValueNoTrans() {
        return value;
    }
    public float getValue() {
//        System.out.println(value + "|" + lastValue);
        return smoothTrans(value, lastValue);
    }

    public void setLValue(float y) {
        this.lastValue = y;
    }
    public void setValue(float y) {
        this.lastValue = y;
        this.value = y;
    }
    public static float smoothTrans(double current, double last){
        return (float) (current * mc.timer.renderPartialTicks + (last * (1.0f - mc.timer.renderPartialTicks)));
    }
}
