package com.elfmcys.yesstevemodel.client.animation.molang;

public final class MolangValue {
    public static final MolangValue ZERO = new MolangValue(0.0D);
    public static final MolangValue ONE = new MolangValue(1.0D);

    private final double numberValue;
    private final String stringValue;
    private final double[] vectorValue;

    private MolangValue(double value) {
        this.numberValue = Double.isFinite(value) ? value : 0.0D;
        this.stringValue = null;
        this.vectorValue = null;
    }

    private MolangValue(String value) {
        this.numberValue = 0.0D;
        this.stringValue = value == null ? "" : value;
        this.vectorValue = null;
    }

    private MolangValue(double x, double y, double z) {
        this.numberValue = 0.0D;
        this.stringValue = null;
        this.vectorValue = new double[]{
                Double.isFinite(x) ? x : 0.0D,
                Double.isFinite(y) ? y : 0.0D,
                Double.isFinite(z) ? z : 0.0D
        };
    }

    public static MolangValue of(double value) {
        if (value == 0.0D) {
            return ZERO;
        }
        if (value == 1.0D) {
            return ONE;
        }
        return new MolangValue(value);
    }

    public static MolangValue of(boolean value) {
        return value ? ONE : ZERO;
    }

    public static MolangValue of(String value) {
        return new MolangValue(value);
    }

    public static MolangValue ofVector(double x, double y, double z) {
        return new MolangValue(x, y, z);
    }

    public boolean isString() {
        return this.stringValue != null;
    }

    public boolean isVector() {
        return this.vectorValue != null;
    }

    public String asString() {
        if (this.stringValue != null) {
            return this.stringValue;
        }
        if (this.vectorValue != null) {
            return "vec3{x=" + this.vectorValue[0] + ", y=" + this.vectorValue[1] + ", z=" + this.vectorValue[2] + "}";
        }
        if (this.numberValue == (long) this.numberValue) {
            return Long.toString((long) this.numberValue);
        }
        return Double.toString(this.numberValue);
    }

    public double asDouble() {
        if (this.vectorValue != null) {
            return 0.0D;
        }
        if (this.stringValue != null) {
            try {
                return Double.parseDouble(this.stringValue);
            } catch (NumberFormatException ignored) {
                return 0.0D;
            }
        }
        return this.numberValue;
    }

    public boolean asBoolean() {
        if (this.vectorValue != null) {
            return Math.abs(this.vectorValue[0]) > 0.000001D
                    || Math.abs(this.vectorValue[1]) > 0.000001D
                    || Math.abs(this.vectorValue[2]) > 0.000001D;
        }
        return this.stringValue != null ? !this.stringValue.isEmpty() : Math.abs(this.numberValue) > 0.000001D;
    }

    public MolangValue property(String name) {
        if (this.vectorValue == null || name == null) {
            return ZERO;
        }
        return switch (name) {
            case "x" -> of(this.vectorValue[0]);
            case "y" -> of(this.vectorValue[1]);
            case "z" -> of(this.vectorValue[2]);
            default -> ZERO;
        };
    }
}
