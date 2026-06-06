package de.florianmichael.viamcp.fixes.compat;

import java.util.concurrent.atomic.AtomicInteger;

public final class InteractionSequence {
    private static final AtomicInteger SEQUENCE = new AtomicInteger();

    private InteractionSequence() {
    }

    public static int next() {
        return SEQUENCE.incrementAndGet();
    }

    public static void set(int value) {
        SEQUENCE.set(value);
    }

    public static int get() {
        return SEQUENCE.get();
    }
}
