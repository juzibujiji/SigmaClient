package com.mentalfrostbyte.jello.module.impl.combat.killaura;

import com.mentalfrostbyte.Client;
import com.mentalfrostbyte.jello.managers.util.notifs.Notification;
import com.mentalfrostbyte.jello.module.impl.combat.KillAura;
import com.mentalfrostbyte.jello.module.impl.player.Blink;
import com.mentalfrostbyte.jello.module.impl.world.FakeLag;
import net.minecraft.client.Minecraft;
import net.minecraft.client.network.play.NetworkPlayerInfo;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.Hand;
import net.minecraft.util.math.MathHelper;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * Predicts the current aura target's next swing from a bounded history of
 * clientbound arm-animation packets and opens short defensive block windows.
 *
 * <p>A remote swing is not proof that the remote player attacked us. Distance,
 * visibility and facing checks are therefore applied on the game thread before
 * a prediction can block.</p>
 */
public final class PredictionAutoBlock {
    private static final int MAX_PENDING_SWINGS = 64;
    private static final int MAX_TRACKED_PLAYERS = 128;
    private static final int MAX_SWING_SAMPLES = 12;
    private static final int MAX_BLOCK_STARTS_PER_SECOND = 3;

    private static final long MIN_SWING_INTERVAL = TimeUnit.MILLISECONDS.toNanos(45L);
    private static final long MAX_SWING_INTERVAL = TimeUnit.MILLISECONDS.toNanos(1000L);
    private static final long MIN_PREDICTED_PERIOD = TimeUnit.MILLISECONDS.toNanos(50L);
    private static final long MAX_PREDICTED_PERIOD = TimeUnit.MILLISECONDS.toNanos(500L);
    private static final long MIN_BLOCK_START_INTERVAL = TimeUnit.MILLISECONDS.toNanos(180L);
    private static final long BLOCK_RATE_WINDOW = TimeUnit.SECONDS.toNanos(1L);
    private static final long TRACKER_TTL = TimeUnit.SECONDS.toNanos(5L);
    private static final long MIN_PATTERN_FRESHNESS = TimeUnit.MILLISECONDS.toNanos(750L);
    private static final long REACTIVE_SWING_FRESHNESS = TimeUnit.MILLISECONDS.toNanos(250L);
    private static final long SHIELD_MIN_HOLD = TimeUnit.MILLISECONDS.toNanos(320L);
    private static final long MAX_BLOCK_HOLD = TimeUnit.MILLISECONDS.toNanos(900L);

    private final Minecraft mc = Minecraft.getInstance();
    private final KillAura parent;
    private final InteractAutoBlock autoBlock;
    private final ArrayBlockingQueue<SwingSample> pendingSwings =
            new ArrayBlockingQueue<>(MAX_PENDING_SWINGS);
    private final Map<Integer, AttackPattern> patterns = new HashMap<>();
    private final ArrayDeque<Long> blockStarts = new ArrayDeque<>();

    private boolean modeActive;
    private boolean ownsBlock;
    private int activeTargetId = -1;
    private int pulsesRemaining;
    private long lastArmedSwing;
    private long predictedSwingAt;
    private long blockUntil;
    private long nextBlockAllowedAt;
    private long burstCooldownUntil;
    private long lastReactiveSwing;
    private int visualTargetId = -1;
    private boolean visualTargetSwinging;
    private boolean warnedMissingBlockItem;

    public PredictionAutoBlock(KillAura parent, InteractAutoBlock autoBlock) {
        this.parent = parent;
        this.autoBlock = autoBlock;
    }

    /** Called from the network event; no world/entity access is performed here. */
    public void recordSwing(int entityId) {
        if (entityId <= 0) {
            return;
        }

        SwingSample sample = new SwingSample(entityId, System.nanoTime());
        if (!this.pendingSwings.offer(sample)) {
            // The queue is deliberately bounded. Under packet spam, discard the oldest
            // observation instead of allowing unbounded memory growth.
            this.pendingSwings.poll();
            this.pendingSwings.offer(sample);
        }
    }

    /** Runs on the client tick after KillAura has selected its target. */
    public void tick(LivingEntity target) {
        long now = System.nanoTime();
        this.drainSwingSamples();
        this.pruneTrackers(now);

        boolean predictionMode = "Prediction".equals(this.parent.getStringSettingValueByName("Autoblock Mode"));
        if (!predictionMode) {
            if (this.modeActive) {
                this.releaseOwnedBlock(now);
                this.clearSchedule();
            }
            this.modeActive = false;
            this.warnedMissingBlockItem = false;
            return;
        }

        if (!this.modeActive) {
            // Do not inherit a server-side block state from another AutoBlock mode.
            if (this.autoBlock.isBlocking()) {
                this.autoBlock.stopAutoBlock();
            }
            this.clearSchedule();
            this.modeActive = true;
        }

        if (this.hasPacketBufferConflict()) {
            // Prediction is tied to real wall-clock swing timing. Buffered use/release
            // packets would arrive after their predicted window and can invert ordering.
            this.releaseOwnedBlock(now);
            this.resetTarget(-1);
            return;
        }

        if (this.ownsBlock && !this.autoBlock.isBlocking()) {
            // The normal KillAura attack path can release a Prediction block before
            // attacking. Reconcile ownership instead of assuming the packet state.
            this.ownsBlock = false;
            // Keep blockUntil: if the defensive window is still open, the rate-limited
            // resume path below may raise the shield/sword again after our attack.
        }

        if (this.ownsBlock && !this.autoBlock.isPredictionBlockItemStillHeld()) {
            this.releaseOwnedBlock(now);
        }

        if (!this.isThreat(target)) {
            this.releaseOwnedBlock(now);
            this.resetTarget(-1);
            return;
        }

        if (!this.autoBlock.hasPredictionBlockItem()) {
            this.releaseOwnedBlock(now);
            if (!this.warnedMissingBlockItem) {
                Client.getInstance().notificationManager.send(new Notification(
                        "Prediction AB",
                        "Use a shield on 1.9+; direct 1.8 sword blocking is disabled."));
                this.warnedMissingBlockItem = true;
            }
            return;
        }
        this.warnedMissingBlockItem = false;

        int targetId = target.getEntityId();
        if (targetId != this.activeTargetId) {
            this.releaseOwnedBlock(now);
            this.resetTarget(targetId);
        }

        // Packet events are the primary source. This rising-edge fallback also
        // covers server-side training dummies whose animation is applied locally
        // without passing through the normal packet event.
        this.observeTargetSwing(target, now);

        AttackPattern pattern = this.patterns.get(targetId);
        AttackEstimate estimate = pattern == null ? null : pattern.estimate();
        if (pattern == null) {
            return;
        }
        if (estimate == null) {
            if (this.ownsBlock) {
                if (now < this.blockUntil) {
                    return;
                }
                this.releaseOwnedBlock(now);
            }
            this.tryReactiveWarmupBlock(pattern, now);
            return;
        }
        if (now - pattern.lastSwing > Math.max(MIN_PATTERN_FRESHNESS, estimate.period * 3L)) {
            this.releaseOwnedBlock(now);
            return;
        }

        int ping = this.getLocalPing();
        long lead = this.predictionLead(ping, this.autoBlock.isPredictionShield());
        long afterWindow = this.afterPredictionWindow(estimate.period, ping);

        if (!this.ownsBlock && this.blockUntil > now
                && this.canStartBlock(now)
                && this.autoBlock.canPredictionBlock()
                && this.autoBlock.startPredictionBlock()) {
            this.ownsBlock = true;
            this.blockStarts.addLast(now);
            return;
        } else if (!this.ownsBlock && this.blockUntil <= now) {
            this.blockUntil = 0L;
        }

        if (this.ownsBlock) {
            if (this.pulsesRemaining > 0) {
                this.coverOverlappingPredictions(estimate.period, lead, afterWindow);
            }
            if (now < this.blockUntil) {
                return;
            }
            this.releaseOwnedBlock(now);
        }

        if (this.pulsesRemaining == 0
                && now >= this.burstCooldownUntil
                && pattern.lastSwing != this.lastArmedSwing) {
            this.armBurst(pattern, estimate, now, lead, afterWindow);
        }

        while (this.pulsesRemaining > 0 && now > this.predictedSwingAt + afterWindow) {
            this.predictedSwingAt += estimate.period;
            this.pulsesRemaining--;
        }

        if (this.pulsesRemaining == 0) {
            return;
        }

        if (now >= this.predictedSwingAt - lead
                && now <= this.predictedSwingAt + afterWindow
                && this.canStartBlock(now)
                && this.autoBlock.canPredictionBlock()
                && this.autoBlock.startPredictionBlock()) {
            this.ownsBlock = true;
            this.blockStarts.addLast(now);

            boolean shield = this.autoBlock.isPredictionShield();
            int coveredPulses = shield ? this.pulsesRemaining : 1;
            long lastCoveredSwing = this.predictedSwingAt + (long) (coveredPulses - 1) * estimate.period;
            long minimumEnd = shield ? now + SHIELD_MIN_HOLD : now + TimeUnit.MILLISECONDS.toNanos(80L);
            this.blockUntil = Math.min(now + MAX_BLOCK_HOLD,
                    Math.max(minimumEnd, lastCoveredSwing + afterWindow));

            this.pulsesRemaining -= coveredPulses;
            this.predictedSwingAt += (long) coveredPulses * estimate.period;
            if (this.pulsesRemaining == 0) {
                this.burstCooldownUntil = this.blockUntil
                        + Math.max(TimeUnit.MILLISECONDS.toNanos(220L), estimate.period * 2L);
            }
        }
    }

    public void reset() {
        this.releaseOwnedBlock(System.nanoTime());
        this.pendingSwings.clear();
        this.patterns.clear();
        this.blockStarts.clear();
        this.clearSchedule();
        this.modeActive = false;
        this.warnedMissingBlockItem = false;
    }

    private void armBurst(AttackPattern pattern, AttackEstimate estimate, long now, long lead, long afterWindow) {
        this.lastArmedSwing = pattern.lastSwing;
        this.pulsesRemaining = this.getBurstSize(estimate);
        this.predictedSwingAt = pattern.lastSwing + estimate.period;

        // If a tick arrived late, skip expired windows while retaining a future one.
        int skipped = 0;
        while (skipped < 3 && now > this.predictedSwingAt + afterWindow) {
            this.predictedSwingAt += estimate.period;
            skipped++;
        }

        // A shield needs several ticks raised before it actually mitigates damage. If
        // the calculated lead is longer than the attack period, arming immediately is
        // intentional; the block start rate limiter still prevents packet spam.
        if (this.predictedSwingAt - lead < now - afterWindow) {
            this.predictedSwingAt = now + lead;
        }
    }

    private void coverOverlappingPredictions(long period, long lead, long afterWindow) {
        while (this.pulsesRemaining > 0 && this.predictedSwingAt - lead <= this.blockUntil) {
            this.blockUntil = Math.min(System.nanoTime() + MAX_BLOCK_HOLD,
                    Math.max(this.blockUntil, this.predictedSwingAt + afterWindow));
            this.predictedSwingAt += period;
            this.pulsesRemaining--;
        }
        if (this.pulsesRemaining == 0) {
            this.burstCooldownUntil = Math.max(this.burstCooldownUntil,
                    this.blockUntil + Math.max(TimeUnit.MILLISECONDS.toNanos(220L), period * 2L));
        }
    }

    private int getBurstSize(AttackEstimate estimate) {
        int bursts = estimate.aps < 6.0D ? 1 : estimate.aps < 12.0D ? 2 : 3;
        if (estimate.jitterRatio > 0.35D) {
            bursts--;
        }
        return MathHelper.clamp(bursts, 1, 3);
    }

    private boolean canStartBlock(long now) {
        while (!this.blockStarts.isEmpty() && now - this.blockStarts.peekFirst() >= BLOCK_RATE_WINDOW) {
            this.blockStarts.removeFirst();
        }
        if (now < this.nextBlockAllowedAt || this.blockStarts.size() >= MAX_BLOCK_STARTS_PER_SECOND) {
            return false;
        }
        Long lastStart = this.blockStarts.peekLast();
        return lastStart == null || now - lastStart >= MIN_BLOCK_START_INTERVAL;
    }

    private void releaseOwnedBlock(long now) {
        if (this.ownsBlock) {
            if (this.autoBlock.isBlocking()) {
                this.autoBlock.stopAutoBlock();
            }
            this.ownsBlock = false;
            this.blockUntil = 0L;
            this.nextBlockAllowedAt = Math.max(this.nextBlockAllowedAt,
                    now + TimeUnit.MILLISECONDS.toNanos(120L));
        }
    }

    private boolean isThreat(LivingEntity target) {
        if (target == null || this.mc.player == null || !target.isAlive() || target == this.mc.player) {
            return false;
        }

        float rangeSetting = this.parent.getNumberValueBySettingName("Range");
        double configuredRange = Float.isFinite(rangeSetting)
                ? MathHelper.clamp(rangeSetting, 2.8F, 8.0F) : 4.0D;
        double defensiveRange = Math.min(5.5D, configuredRange + 0.75D);
        if (this.mc.player.getDistanceToEntityBox(target) > defensiveRange
                || !this.mc.player.canEntityBeSeen(target)) {
            return false;
        }

        // A server-side dummy often does not synchronize head yaw. A recent swing
        // from the selected close-range target is a stronger signal than that
        // cosmetic value, so do not reject otherwise valid targets on facing alone.
        return true;
    }

    private void observeTargetSwing(LivingEntity target, long now) {
        int targetId = target.getEntityId();
        if (targetId != this.visualTargetId) {
            this.visualTargetId = targetId;
            this.visualTargetSwinging = false;
        }

        boolean swingingMainHand = target.isSwingInProgress
                && (target.swingingHand == null || target.swingingHand == Hand.MAIN_HAND);
        if (swingingMainHand && !this.visualTargetSwinging) {
            AttackPattern existing = this.patterns.get(targetId);
            // Packet and visual callbacks describe the same animation. Only use the
            // visual fallback when no almost-simultaneous packet sample exists.
            if (existing == null || now - existing.lastSwing > TimeUnit.MILLISECONDS.toNanos(90L)) {
                this.addSwingObservation(targetId, now);
            }
        }
        this.visualTargetSwinging = swingingMainHand;
    }

    private void tryReactiveWarmupBlock(AttackPattern pattern, long now) {
        if (pattern.lastSwing == 0L
                || pattern.lastSwing == this.lastReactiveSwing
                || now < pattern.lastSwing
                || now - pattern.lastSwing > REACTIVE_SWING_FRESHNESS
                || !this.canStartBlock(now)
                || !this.autoBlock.canPredictionBlock()) {
            return;
        }

        boolean shield = this.autoBlock.isPredictionShield();
        if (this.autoBlock.startPredictionBlock()) {
            this.ownsBlock = true;
            this.lastReactiveSwing = pattern.lastSwing;
            this.blockStarts.addLast(now);
            long hold = shield ? SHIELD_MIN_HOLD : TimeUnit.MILLISECONDS.toNanos(100L);
            this.blockUntil = Math.min(now + MAX_BLOCK_HOLD, now + hold);
        }
    }

    private long predictionLead(int ping, boolean shield) {
        long halfPing = Math.max(0, Math.min(ping, 500)) / 2L;
        long leadMs = shield ? 275L + halfPing : 35L + halfPing;
        long minimum = shield ? 275L : 35L;
        long maximum = shield ? 450L : 140L;
        return TimeUnit.MILLISECONDS.toNanos(Math.max(minimum, Math.min(maximum, leadMs)));
    }

    private long afterPredictionWindow(long period, int ping) {
        long periodMs = TimeUnit.NANOSECONDS.toMillis(period);
        long windowMs = periodMs / 2L + Math.max(0, Math.min(ping, 500)) / 4L;
        return TimeUnit.MILLISECONDS.toNanos(Math.max(70L, Math.min(180L, windowMs)));
    }

    private int getLocalPing() {
        if (this.mc.player == null || this.mc.getConnection() == null || this.mc.isIntegratedServerRunning()) {
            return 0;
        }
        NetworkPlayerInfo info = this.mc.getConnection().getPlayerInfo(this.mc.player.getUniqueID());
        return info == null ? 0 : Math.max(0, info.getResponseTime());
    }

    private boolean hasPacketBufferConflict() {
        Blink blink = (Blink) Client.getInstance().moduleManager.getModuleByClass(Blink.class);
        FakeLag fakeLag = (FakeLag) Client.getInstance().moduleManager.getModuleByClass(FakeLag.class);
        return (blink != null && blink.isEnabled()) || (fakeLag != null && fakeLag.isEnabled());
    }

    private void drainSwingSamples() {
        SwingSample sample;
        while ((sample = this.pendingSwings.poll()) != null) {
            this.addSwingObservation(sample.entityId, sample.time);
        }
    }

    private void addSwingObservation(int entityId, long time) {
        AttackPattern pattern = this.patterns.get(entityId);
        if (pattern == null) {
            if (this.patterns.size() >= MAX_TRACKED_PLAYERS) {
                this.evictOldestTracker();
            }
            pattern = new AttackPattern();
            this.patterns.put(entityId, pattern);
        }
        pattern.add(time);
    }

    private void pruneTrackers(long now) {
        Iterator<Map.Entry<Integer, AttackPattern>> iterator = this.patterns.entrySet().iterator();
        while (iterator.hasNext()) {
            AttackPattern pattern = iterator.next().getValue();
            if (pattern.lastSwing == 0L || now - pattern.lastSwing > TRACKER_TTL) {
                iterator.remove();
            }
        }
    }

    private void evictOldestTracker() {
        Integer oldestId = null;
        long oldestSwing = Long.MAX_VALUE;
        for (Map.Entry<Integer, AttackPattern> entry : this.patterns.entrySet()) {
            if (entry.getValue().lastSwing < oldestSwing) {
                oldestSwing = entry.getValue().lastSwing;
                oldestId = entry.getKey();
            }
        }
        if (oldestId != null) {
            this.patterns.remove(oldestId);
        }
    }

    private void resetTarget(int targetId) {
        this.activeTargetId = targetId;
        this.pulsesRemaining = 0;
        this.lastArmedSwing = 0L;
        this.lastReactiveSwing = 0L;
        this.predictedSwingAt = 0L;
        this.burstCooldownUntil = 0L;
        if (targetId != this.visualTargetId) {
            this.visualTargetId = targetId;
            this.visualTargetSwinging = false;
        }
    }

    private void clearSchedule() {
        this.resetTarget(-1);
        this.ownsBlock = false;
        this.blockUntil = 0L;
        this.nextBlockAllowedAt = 0L;
        this.burstCooldownUntil = 0L;
    }

    private static long median(List<Long> values) {
        Collections.sort(values);
        int middle = values.size() / 2;
        return values.size() % 2 == 0
                ? (values.get(middle - 1) + values.get(middle)) / 2L
                : values.get(middle);
    }

    private static final class AttackPattern {
        private final ArrayDeque<Long> swings = new ArrayDeque<>();
        private long lastSwing;

        private void add(long time) {
            if (this.lastSwing != 0L && time - this.lastSwing < MIN_SWING_INTERVAL) {
                return;
            }
            this.lastSwing = time;
            this.swings.addLast(time);
            while (this.swings.size() > MAX_SWING_SAMPLES) {
                this.swings.removeFirst();
            }
        }

        private AttackEstimate estimate() {
            if (this.swings.size() < 2) {
                return null;
            }

            List<Long> intervals = new ArrayList<>();
            Long previous = null;
            for (Long swing : this.swings) {
                if (previous != null) {
                    long interval = swing - previous;
                    if (interval >= MIN_SWING_INTERVAL && interval <= MAX_SWING_INTERVAL) {
                        intervals.add(interval);
                    }
                }
                previous = swing;
            }
            if (intervals.isEmpty()) {
                return null;
            }

            long period = Math.max(MIN_PREDICTED_PERIOD,
                    Math.min(MAX_PREDICTED_PERIOD, median(new ArrayList<>(intervals))));
            List<Long> deviations = new ArrayList<>(intervals.size());
            for (Long interval : intervals) {
                deviations.add(Math.abs(interval - period));
            }
            long medianDeviation = median(deviations);
            double jitterRatio = period == 0L ? 1.0D : (double) medianDeviation / (double) period;
            double aps = (double) TimeUnit.SECONDS.toNanos(1L) / (double) period;
            return new AttackEstimate(period, aps, jitterRatio);
        }
    }

    private static final class AttackEstimate {
        private final long period;
        private final double aps;
        private final double jitterRatio;

        private AttackEstimate(long period, double aps, double jitterRatio) {
            this.period = period;
            this.aps = aps;
            this.jitterRatio = jitterRatio;
        }
    }

    private static final class SwingSample {
        private final int entityId;
        private final long time;

        private SwingSample(int entityId, long time) {
            this.entityId = entityId;
            this.time = time;
        }
    }
}
