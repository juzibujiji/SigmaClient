package com.mentalfrostbyte.jello.module.impl.combat;

import com.mentalfrostbyte.Client;
import com.mentalfrostbyte.jello.event.impl.game.network.EventReceivePacket;
import com.mentalfrostbyte.jello.event.impl.player.EventUpdate;
import com.mentalfrostbyte.jello.managers.util.notifs.Notification;
import com.mentalfrostbyte.jello.module.Module;
import com.mentalfrostbyte.jello.module.data.ModuleCategory;
import com.mentalfrostbyte.jello.module.settings.impl.BooleanSetting;
import com.mentalfrostbyte.jello.module.settings.impl.ModeSetting;
import com.mentalfrostbyte.jello.module.settings.impl.NumberSetting;
import com.mentalfrostbyte.jello.util.game.world.blocks.BlockUtil;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.play.server.SAnimateHandPacket;
import net.minecraft.network.play.server.SEntityHeadLookPacket;
import net.minecraft.network.play.server.SEntityPacket;
import net.minecraft.potion.EffectInstance;
import net.minecraft.potion.Effects;
import net.minecraft.util.math.MathHelper;
import team.sdhq.eventBus.annotations.EventTarget;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * HackDetector — a client-side, Grim-inspired watchdog that flags OTHER players
 * exhibiting cheat-like behaviour.
 *
 * <p>Because this runs on a vanilla client and not on the server, we cannot see
 * the packets a remote player sends to the server (their raw movement /
 * rotation / attack packets). Instead we reconstruct behaviour from the
 * clientbound packets the server relays about them and from their live entity
 * state. That is enough to port a handful of Grim's cheaper heuristics:
 *
 * <ul>
 *   <li><b>AutoClicker</b> — analyses swing-arm animation timing (CPS +
 *       inter-click standard deviation). Robotic, low-jitter clicking at high
 *       CPS is the tell.</li>
 *   <li><b>AutoBlock</b> — a player who keeps a shield/block raised while
 *       still swinging (attacking) in the same window. Impossible with vanilla
 *       controls; classic 1.8-via-ViaVersion sword autoblock.</li>
 *   <li><b>Aimbot</b> — a large rotation snap immediately followed by an
 *       attack swing (snap-then-hit).</li>
 *   <li><b>Simulation</b> — the centrepiece. A potion-aware movement model that
 *       flags remote players whose motion vanilla physics cannot produce:
 *       horizontal ground speed beyond a sprint-jump baseline (adjusted for
 *       Speed / Slowness), airborne hovering or upward drift with no jump
 *       (Fly), and jumps taller than Jump-Boost allows. Remote players' potion
 *       effects <i>are</i> visible client-side (the same data TargetHUD draws),
 *       so the baselines are computed per-target rather than guessed.</li>
 * </ul>
 *
 * Each check feeds a per-player violation level (VL). When a check's VL crosses
 * the configured threshold an alert is fired (notification and/or chat) with a
 * cooldown so a single cheater does not spam the HUD.
 */
public class HackDetector extends Module {

    /** How many swing timestamps we keep per player for CPS analysis. */
    private static final int SWING_SAMPLES = 20;
    /** A rotation snap counts as "recent" for this long when correlating with a hit (ms). */
    private static final long SNAP_HIT_WINDOW_MS = 300L;
    /** Minimum time between two alerts for the same player + check (ms). */
    private static final long ALERT_COOLDOWN_MS = 4000L;
    /** Entities not seen for this many ticks are dropped from tracking. */
    private static final int STALE_TICKS = 40;

    // --- Vanilla movement baselines (blocks / tick), used by the Simulation check. ---
    /** Sprint-jumping ground speed ceiling before potions. A sprint tap-jump peaks near this. */
    private static final double BASE_SPRINT_JUMP_SPEED = 0.36D;
    /** Each Speed level multiplies movement speed by +20%; Slowness by -15% (matches attribute modifiers). */
    private static final double SPEED_PER_LEVEL = 0.20D;
    private static final double SLOWNESS_PER_LEVEL = 0.15D;
    /** A normal jump lifts ~0.42 blocks on the first tick; each Jump-Boost level adds ~0.1. */
    private static final double BASE_JUMP_MOTION = 0.42D;
    private static final double JUMP_BOOST_PER_LEVEL = 0.1D;

    private final Map<Integer, TrackedPlayer> tracked = new HashMap<>();

    public HackDetector() {
        super(ModuleCategory.COMBAT, "HackDetector", "Grim-style local detection of other players' cheats");

        this.registerSetting(new ModeSetting("Alert", "Where detections are reported", "Both", "Notification", "Chat", "Both", "Silent"));
        this.registerSetting(new NumberSetting<>("Alert VL", "Violation level a check must reach before alerting", 5.0F, 1.0F, 20.0F, 1.0F));

        this.registerSetting(new BooleanSetting("AutoClicker", "Detect robotic / high-CPS clicking", true));
        this.registerSetting(new NumberSetting<>("Min CPS", "CPS above which clicking is scrutinised", 14.0F, 8.0F, 30.0F, 1.0F));

        this.registerSetting(new BooleanSetting("AutoBlock", "Detect attacking while a block is held up", true));
        this.registerSetting(new BooleanSetting("Aimbot", "Detect snap-rotation immediately before a hit", true));
        this.registerSetting(new NumberSetting<>("Snap Angle", "Yaw jump (degrees) treated as a snap", 35.0F, 10.0F, 90.0F, 1.0F));

        this.registerSetting(new BooleanSetting("Simulation", "Potion-aware movement model (speed / fly / jump)", true));
        this.registerSetting(new NumberSetting<>("Speed", "Ground blocks/tick above the potion-adjusted sprint baseline", 0.10F, 0.02F, 0.60F, 0.01F));
        this.registerSetting(new BooleanSetting("Fly", "Detect airborne hovering / upward drift with no jump", true));
        this.registerSetting(new NumberSetting<>("Fly Ticks", "Airborne ticks of near-zero fall before flagging Fly", 6.0F, 3.0F, 20.0F, 1.0F));
        this.registerSetting(new BooleanSetting("Jump", "Detect jumps taller than Jump-Boost allows", true));
    }

    @Override
    public void onDisable() {
        this.tracked.clear();
    }

    private TrackedPlayer data(int entityId) {
        return this.tracked.computeIfAbsent(entityId, id -> new TrackedPlayer());
    }

    @EventTarget
    public void onPacket(EventReceivePacket event) {
        if (mc.player == null || mc.world == null || event.packet == null) {
            return;
        }

        if (event.packet instanceof SAnimateHandPacket animate) {
            // type 0 == main-hand swing, i.e. an attack / left-click.
            if (animate.getAnimationType() != 0) {
                return;
            }

            Entity entity = mc.world.getEntityByID(animate.getEntityID());
            if (!(entity instanceof PlayerEntity player) || player == mc.player) {
                return;
            }

            this.onSwing(player, this.data(player.getEntityId()));
        } else if (event.packet instanceof SEntityPacket move && move.isRotating()) {
            // Look/Move packets carry the entity's new yaw for rotation analysis.
            Entity entity = move.getEntity(mc.world);
            if (entity instanceof PlayerEntity player && player != mc.player) {
                this.onRotation(this.data(player.getEntityId()), byteToDegrees(move.getYaw()));
            }
        } else if (event.packet instanceof SEntityHeadLookPacket head) {
            Entity entity = head.getEntity(mc.world);
            if (entity instanceof PlayerEntity player && player != mc.player) {
                this.onRotation(this.data(player.getEntityId()), byteToDegrees(head.getYaw()));
            }
        }
    }

    private void onSwing(PlayerEntity player, TrackedPlayer data) {
        long now = System.currentTimeMillis();
        data.lastSwingMs = now;
        data.swingTimes.addLast(now);
        while (data.swingTimes.size() > SWING_SAMPLES) {
            data.swingTimes.removeFirst();
        }

        // --- AutoBlock: swinging (attacking) while a block is actively raised. ---
        if (this.getBooleanValueFromSettingName("AutoBlock") && player.isActiveItemStackBlocking()) {
            this.flag(player, data, "AutoBlock", "attacking while blocking", 2.0F);
        }

        // --- Aimbot: a snap rotation immediately preceding this hit. ---
        if (this.getBooleanValueFromSettingName("Aimbot")
                && data.lastSnapMs != 0L
                && now - data.lastSnapMs <= SNAP_HIT_WINDOW_MS) {
            this.flag(player, data, "Aimbot", String.format("snap %.0f° then hit", data.lastSnapAngle), 1.5F);
            data.lastSnapMs = 0L; // consume so one snap flags once
        }

        // --- AutoClicker: high CPS with robotic (low-jitter) timing. ---
        if (this.getBooleanValueFromSettingName("AutoClicker") && data.swingTimes.size() >= 6) {
            float cps = data.cps(now);
            float minCps = this.getNumberValueBySettingName("Min CPS");
            if (cps >= minCps) {
                double stdDev = data.intervalStdDev();
                if (stdDev < 8.0) {
                    // Near-perfect timing that a human hand cannot reproduce.
                    this.flag(player, data, "AutoClicker",
                            String.format("%.0f CPS, σ=%.1fms", cps, stdDev), 1.5F);
                } else if (cps >= minCps + 6.0F) {
                    // Even with jitter, this CPS is beyond human range.
                    this.flag(player, data, "AutoClicker", String.format("%.0f CPS", cps), 1.0F);
                }
            }
        }
    }

    private void onRotation(TrackedPlayer data, float yaw) {
        if (data.hasYaw) {
            float delta = Math.abs(MathHelper.wrapDegrees(yaw - data.lastYaw));
            if (delta >= this.getNumberValueBySettingName("Snap Angle")) {
                data.lastSnapMs = System.currentTimeMillis();
                data.lastSnapAngle = delta;
            }
        }

        data.lastYaw = yaw;
        data.hasYaw = true;
    }

    @EventTarget
    public void onTick(EventUpdate event) {
        if (mc.player == null || mc.world == null) {
            return;
        }

        boolean simEnabled = this.getBooleanValueFromSettingName("Simulation");

        Iterator<Map.Entry<Integer, TrackedPlayer>> it = this.tracked.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Integer, TrackedPlayer> entry = it.next();
            Entity entity = mc.world.getEntityByID(entry.getKey());

            if (!(entity instanceof PlayerEntity player) || player == mc.player || !player.isAlive()) {
                // Prune players that left the world or otherwise became irrelevant.
                if (++entry.getValue().staleTicks > STALE_TICKS) {
                    it.remove();
                }
                continue;
            }

            TrackedPlayer data = entry.getValue();
            data.staleTicks = 0;

            if (simEnabled) {
                this.checkSimulation(player, data);
            } else {
                data.hasPrevPos = false;
            }

            // Slowly bleed off violation levels so past offenders can recover.
            data.decay();
        }
    }

    /**
     * Potion-aware movement simulation. We work from per-tick position deltas
     * (the only trustworthy remote-player signal) and compare them against a
     * vanilla physics envelope adjusted for the target's live potion effects:
     *
     * <ul>
     *   <li><b>Speed</b> — horizontal ground speed must stay under a sprint-jump
     *       baseline scaled by Speed / Slowness.</li>
     *   <li><b>Fly</b> — a player who is airborne (not near any block below) and
     *       neither falling nor rising for several ticks is hovering. Levitation
     *       and Slow Falling exempt this.</li>
     *   <li><b>Jump</b> — the peak height gained in a single airborne phase must
     *       not exceed what a jump (plus Jump-Boost) can reach.</li>
     * </ul>
     */
    private void checkSimulation(PlayerEntity player, TrackedPlayer data) {
        double posX = player.getPosX();
        double posY = player.getPosY();
        double posZ = player.getPosZ();

        if (!data.hasPrevPos) {
            data.rememberPos(posX, posY, posZ);
            return;
        }

        double dx = posX - data.prevX;
        double dy = posY - data.prevY;
        double dz = posZ - data.prevZ;
        double horizontal = Math.sqrt(dx * dx + dz * dz);

        boolean realGround = BlockUtil.isAboveBounds(player, 0.5F);
        boolean fluid = player.isInWater() || player.isInLava();
        boolean verticalExempt = fluid
                || player.isElytraFlying()
                || player.isPassenger()
                || player.isPotionActive(Effects.LEVITATION)
                || player.isPotionActive(Effects.SLOW_FALLING);

        // --- Speed: horizontal ground movement beyond the potion-adjusted baseline. ---
        if ((realGround || player.isOnGround()) && !fluid && !player.isPassenger()) {
            double allowed = BASE_SPRINT_JUMP_SPEED * horizontalMultiplier(player)
                    + this.getNumberValueBySettingName("Speed");
            if (horizontal > allowed) {
                if (++data.speedStreak >= 3) {
                    this.flag(player, data, "Simulation",
                            String.format("Speed %.2f>%.2f b/t%s", horizontal, allowed, effectSuffix(player)), 1.0F);
                }
            } else {
                data.speedStreak = 0;
            }
        } else {
            data.speedStreak = 0;
        }

        // --- Vertical phase tracking (Fly + Jump) ---
        // Deliberately keyed off a *real* block-below check, not the onGround
        // packet flag — Fly cheats routinely spoof onGround=true while hovering.
        if (realGround || verticalExempt) {
            // Standing / walking / legitimately-airborne: reset vertical state and
            // keep the ground baseline fresh for the next jump.
            if (realGround) {
                data.groundY = posY;
            }
            data.hoverTicks = 0;
            data.inAir = false;
        } else {
            // Genuinely airborne under normal gravity.
            if (!data.inAir) {
                data.inAir = true;
                data.jumpPeak = 0.0;
            }
            data.jumpPeak = Math.max(data.jumpPeak, posY - data.groundY);

            // Fly / hover: no meaningful vertical change while off the ground.
            if (this.getBooleanValueFromSettingName("Fly")) {
                if (Math.abs(dy) < 0.015D) {
                    data.hoverTicks++;
                    if (data.hoverTicks >= (int) this.getNumberValueBySettingName("Fly Ticks")) {
                        this.flag(player, data, "Simulation",
                                String.format("Fly (hover %dt)", data.hoverTicks), 1.5F);
                    }
                } else {
                    data.hoverTicks = 0;
                }
            }

            // Jump height: peak ascent within one airborne phase.
            if (this.getBooleanValueFromSettingName("Jump")) {
                double allowedPeak = maxJumpHeight(player);
                if (data.jumpPeak > allowedPeak) {
                    this.flag(player, data, "Simulation",
                            String.format("Jump %.2f>%.2f b%s", data.jumpPeak, allowedPeak, effectSuffix(player)), 1.5F);
                }
            }
        }

        data.rememberPos(posX, posY, posZ);
    }

    /** Movement-speed scalar from the target's Speed / Slowness effects. */
    private static double horizontalMultiplier(PlayerEntity player) {
        double mult = 1.0D;
        EffectInstance speed = player.getActivePotionEffect(Effects.SPEED);
        if (speed != null) {
            mult += SPEED_PER_LEVEL * (speed.getAmplifier() + 1);
        }
        EffectInstance slow = player.getActivePotionEffect(Effects.SLOWNESS);
        if (slow != null) {
            mult -= SLOWNESS_PER_LEVEL * (slow.getAmplifier() + 1);
        }
        return Math.max(mult, 0.05D);
    }

    /** Maximum single-jump height (blocks) allowed given the target's Jump-Boost, with tolerance. */
    private static double maxJumpHeight(PlayerEntity player) {
        int amplifier = -1; // -1 == no effect
        EffectInstance boost = player.getActivePotionEffect(Effects.JUMP_BOOST);
        if (boost != null) {
            amplifier = boost.getAmplifier();
        }
        double initial = BASE_JUMP_MOTION + Math.max(0, amplifier + 1) * JUMP_BOOST_PER_LEVEL;
        // Projectile-motion apex ~ v^2 / (2g) with g≈0.08, plus a generous tolerance
        // to swallow knockback pops and interpolation jitter.
        return (initial * initial) / (2.0D * 0.08D) + 0.75D;
    }

    /** Short human-readable note of the potion effects that widened the envelope. */
    private static String effectSuffix(PlayerEntity player) {
        StringBuilder sb = new StringBuilder();
        appendEffect(sb, player, Effects.SPEED, "Speed");
        appendEffect(sb, player, Effects.JUMP_BOOST, "Jump");
        appendEffect(sb, player, Effects.SLOWNESS, "Slow");
        return sb.length() == 0 ? "" : " [" + sb + "]";
    }

    private static void appendEffect(StringBuilder sb, PlayerEntity player, net.minecraft.potion.Effect effect, String label) {
        EffectInstance ei = player.getActivePotionEffect(effect);
        if (ei != null) {
            if (sb.length() > 0) {
                sb.append(' ');
            }
            sb.append(label).append(' ').append(ei.getAmplifier() + 1);
        }
    }

    private void flag(PlayerEntity player, TrackedPlayer data, String check, String detail, float amount) {
        float vl = data.addViolation(check, amount);
        if (vl < this.getNumberValueBySettingName("Alert VL")) {
            return;
        }

        long now = System.currentTimeMillis();
        Long last = data.lastAlertMs.get(check);
        if (last != null && now - last < ALERT_COOLDOWN_MS) {
            return;
        }
        data.lastAlertMs.put(check, now);

        String name = player.getName().getString();
        String mode = this.getStringSettingValueByName("Alert");
        String title = "HackDetector";
        String body = name + " · " + check + " (" + detail + ", vl " + (int) vl + ")";

        if ("Notification".equals(mode) || "Both".equals(mode)) {
            Client.getInstance().notificationManager.send(new Notification(title, body));
        }
        if ("Chat".equals(mode) || "Both".equals(mode)) {
            if (mc.ingameGUI != null) {
                mc.ingameGUI.getChatGUI().printChatMessage(
                        new net.minecraft.util.text.StringTextComponent(
                                "§8[§cHackDetector§8] §f" + name + " §7· §c" + check + " §8(" + detail + ")"));
            }
        }
    }

    private static float byteToDegrees(byte value) {
        return value * 360.0F / 256.0F;
    }

    /** Per-player rolling detection state. */
    private static final class TrackedPlayer {
        final Deque<Long> swingTimes = new ArrayDeque<>();
        final Map<String, Float> violations = new HashMap<>();
        final Map<String, Long> lastAlertMs = new HashMap<>();

        long lastSwingMs;
        long lastSnapMs;
        float lastSnapAngle;

        float lastYaw;
        boolean hasYaw;

        int speedStreak;
        int staleTicks;

        // --- Simulation (movement) state ---
        boolean hasPrevPos;
        double prevX;
        double prevY;
        double prevZ;
        double groundY;   // Y of the last on-ground tick, the jump baseline
        double jumpPeak;  // peak height gained in the current airborne phase
        boolean inAir;
        int hoverTicks;

        void rememberPos(double x, double y, double z) {
            this.prevX = x;
            this.prevY = y;
            this.prevZ = z;
            this.hasPrevPos = true;
        }

        /** Swings observed within the last second. */
        float cps(long now) {
            int count = 0;
            for (Long t : this.swingTimes) {
                if (now - t <= 1000L) {
                    count++;
                }
            }
            return count;
        }

        /** Standard deviation (ms) of the intervals between recent swings. */
        double intervalStdDev() {
            if (this.swingTimes.size() < 3) {
                return Double.MAX_VALUE;
            }

            Long[] times = this.swingTimes.toArray(new Long[0]);
            int n = times.length - 1;
            double sum = 0.0;
            for (int i = 1; i < times.length; i++) {
                sum += times[i] - times[i - 1];
            }
            double mean = sum / n;

            double variance = 0.0;
            for (int i = 1; i < times.length; i++) {
                double diff = (times[i] - times[i - 1]) - mean;
                variance += diff * diff;
            }
            return Math.sqrt(variance / n);
        }

        float addViolation(String check, float amount) {
            float vl = this.violations.getOrDefault(check, 0.0F) + amount;
            this.violations.put(check, vl);
            return vl;
        }

        void decay() {
            for (Map.Entry<String, Float> entry : this.violations.entrySet()) {
                float next = entry.getValue() - 0.05F;
                entry.setValue(Math.max(0.0F, next));
            }
        }
    }
}
