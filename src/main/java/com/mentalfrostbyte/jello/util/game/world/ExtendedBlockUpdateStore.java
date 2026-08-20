package com.mentalfrostbyte.jello.util.game.world;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Captures raw 1.18+ serverbound block-change packets that ViaBackwards
 * 1.17 -> 1.16.4 cancels because their Y is outside the legacy 0..255 range.
 * {@link ChunkDataInterceptor} stores them before the Via pipeline, and
 * {@link ExtendedHeightBlockUpdateHandler} re-injects equivalent 1.16.4
 * packets after the Via decoder so the extended-height client world applies
 * server-side break/place updates below Y=0 and above Y=255.
 *
 * <p>The queue only stores primitives and raw payload bytes; no packet,
 * entity, world or channel references are kept.
 *
 * <h2>Two disjoint channels</h2>
 *
 * <p>{@link #QUEUE} is the <b>re-injection</b> channel: Y outside 0..255, i.e.
 * exactly the updates ViaBackwards throws away. Nothing reaches the client
 * unless we synthesize a packet, so these are queued and drained.
 *
 * <p>{@link #SINGLE_OVERRIDES} / {@link #MULTI_OVERRIDES} are the
 * <b>override</b> channel: Y inside 0..255, i.e. updates Via <b>does</b>
 * deliver - only with the block state downgraded (deepslate -&gt; stone). Here a
 * second packet would mean setting the same position twice and racing our own
 * fix against Via's; instead the raw state is parked under the position it
 * belongs to and {@link ExtendedHeightBlockUpdateHandler} patches the state id
 * into Via's own packet as it passes by. Keys are the wire-format packed
 * position / section-position longs, whose bit layout is identical in 1.16.4
 * and 1.17+, so the raw packet and the translated packet agree on them.
 *
 * <p>An override is only useful for as long as the translated packet is still
 * in flight - which is the same read burst, because the Via decoder is a
 * {@code MessageToMessageDecoder} that emits synchronously. Anything left over
 * at {@code channelReadComplete} was cancelled or reshaped by Via and is
 * dropped rather than kept around to corrupt a later packet at the same
 * position.
 */
public final class ExtendedBlockUpdateStore {
    private static final int MAX_STORED = 512;
    private static final ConcurrentLinkedQueue<CapturedUpdate> QUEUE = new ConcurrentLinkedQueue<>();

    /** Returned by {@link #takeSingleOverride(long)} when no raw state was parked. */
    public static final int NO_OVERRIDE = -1;

    /**
     * Bounded well below {@link #MAX_STORED}: overrides live for a single read
     * burst, so more than a few dozen pending entries means matching stopped
     * working. Dropping them all is the correct response - a stale override is
     * worth nothing and applying one to the wrong packet is worse than nothing.
     */
    private static final int MAX_OVERRIDES = 256;

    /** packed block position -> raw (server-version) block state id. */
    private static final ConcurrentHashMap<Long, Integer> SINGLE_OVERRIDES = new ConcurrentHashMap<>();

    /** packed section position -> raw SECTION_BLOCKS_UPDATE payload (suppress-light slot onward). */
    private static final ConcurrentHashMap<Long, byte[]> MULTI_OVERRIDES = new ConcurrentHashMap<>();

    private ExtendedBlockUpdateStore() {
    }

    public enum Kind {
        SINGLE,
        MULTI,
        DESTRUCTION
    }

    public static final class CapturedUpdate {
        public final Kind kind;
        public final int x;
        public final int y;
        public final int z;
        /** Raw 1.18+ block state id (SINGLE). */
        public final int stateId;
        /** Packed section position long (MULTI). */
        public final long sectionPos;
        /** Raw payload from the suppress-light boolean onward (MULTI). */
        public final byte[] payload;
        /** Breaking entity id (DESTRUCTION). */
        public final int entityId;
        /** Block break stage (DESTRUCTION). */
        public final int stage;

        private CapturedUpdate(Kind kind, int x, int y, int z, int stateId, long sectionPos, byte[] payload,
                               int entityId, int stage) {
            this.kind = kind;
            this.x = x;
            this.y = y;
            this.z = z;
            this.stateId = stateId;
            this.sectionPos = sectionPos;
            this.payload = payload;
            this.entityId = entityId;
            this.stage = stage;
        }
    }

    public static void putSingle(int x, int y, int z, int stateId) {
        put(new CapturedUpdate(Kind.SINGLE, x, y, z, stateId, 0L, null, 0, 0));
    }

    public static void putMulti(long sectionPos, byte[] payload) {
        put(new CapturedUpdate(Kind.MULTI, 0, 0, 0, 0, sectionPos, payload, 0, 0));
    }

    public static void putDestruction(int entityId, int x, int y, int z, int stage) {
        put(new CapturedUpdate(Kind.DESTRUCTION, x, y, z, 0, 0L, null, entityId, stage));
    }

    public static CapturedUpdate poll() {
        return QUEUE.poll();
    }

    /** Parks the raw state of an in-bounds BLOCK_UPDATE Via is about to downgrade. */
    public static void putSingleOverride(long packedPos, int rawStateId) {
        if (rawStateId < 0) {
            return;
        }

        if (SINGLE_OVERRIDES.size() >= MAX_OVERRIDES) {
            SINGLE_OVERRIDES.clear();
        }

        SINGLE_OVERRIDES.put(packedPos, rawStateId);
    }

    /** Parks the raw records of an in-bounds SECTION_BLOCKS_UPDATE Via is about to downgrade. */
    public static void putMultiOverride(long sectionPos, byte[] payload) {
        if (payload == null) {
            return;
        }

        if (MULTI_OVERRIDES.size() >= MAX_OVERRIDES) {
            MULTI_OVERRIDES.clear();
        }

        MULTI_OVERRIDES.put(sectionPos, payload);
    }

    /**
     * Consumes the parked raw state for a position. Removing on read is what
     * makes the override apply to exactly one packet: whichever translated
     * BLOCK_UPDATE reaches the handler first takes it, and a later packet at
     * the same position can no longer pick it up.
     */
    public static int takeSingleOverride(long packedPos) {
        Integer rawStateId = SINGLE_OVERRIDES.remove(packedPos);
        return rawStateId != null ? rawStateId : NO_OVERRIDE;
    }

    /** Consumes the parked raw records for a section position. */
    public static byte[] takeMultiOverride(long sectionPos) {
        return MULTI_OVERRIDES.remove(sectionPos);
    }

    /**
     * Cheap enough to call for every inbound packet: two field reads when the
     * maps have never been touched, which is the case for every target version
     * other than the one the modern state table was built for.
     */
    public static boolean hasOverrides() {
        return !SINGLE_OVERRIDES.isEmpty() || !MULTI_OVERRIDES.isEmpty();
    }

    /** Drops overrides whose translated packet never showed up. */
    public static void clearOverrides() {
        if (!SINGLE_OVERRIDES.isEmpty()) {
            SINGLE_OVERRIDES.clear();
        }

        if (!MULTI_OVERRIDES.isEmpty()) {
            MULTI_OVERRIDES.clear();
        }
    }

    public static void clearAll() {
        QUEUE.clear();
        clearOverrides();
    }

    private static void put(CapturedUpdate update) {
        while (QUEUE.size() >= MAX_STORED) {
            QUEUE.poll();
        }
        QUEUE.add(update);
    }
}
