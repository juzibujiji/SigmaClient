package com.mentalfrostbyte.jello.util.system.other;

public class SimpleEntryPair<key, value> {
    private final key keyVal;
    private final value val;

    public SimpleEntryPair(key keyVal, value val) {
        this.keyVal = keyVal;
        this.val = val;
    }

    public key getKey() {
        return this.keyVal;
    }

    public value getValue() {
        return this.val;
    }
}
