package com.mentalfrostbyte.jello.module.impl.movement.blockfly;

import com.mentalfrostbyte.Client;
import com.mentalfrostbyte.jello.event.impl.game.network.EventSendPacket;
import com.mentalfrostbyte.jello.event.impl.player.movement.EventJump;
import com.mentalfrostbyte.jello.event.impl.player.movement.EventMotion;
import com.mentalfrostbyte.jello.event.impl.player.movement.EventMove;
import com.mentalfrostbyte.jello.event.impl.player.movement.EventSafeWalk;
import com.mentalfrostbyte.jello.module.Module;
import com.mentalfrostbyte.jello.module.data.ModuleCategory;
import com.mentalfrostbyte.jello.module.impl.movement.BlockFly;
import com.mentalfrostbyte.jello.module.impl.movement.SafeWalk;
import com.mentalfrostbyte.jello.module.impl.movement.Speed;
import com.mentalfrostbyte.jello.module.settings.impl.BooleanSetting;
import com.mentalfrostbyte.jello.module.settings.impl.ModeSetting;
import com.mentalfrostbyte.jello.module.settings.impl.NumberSetting;
import com.mentalfrostbyte.jello.util.game.player.MovementUtil;
import com.mentalfrostbyte.jello.util.game.world.blocks.BlockUtil;
import net.minecraft.block.BlockState;
import net.minecraft.network.play.client.CAnimateHandPacket;
import net.minecraft.network.play.client.CHeldItemChangePacket;
import net.minecraft.util.ActionResultType;
import net.minecraft.util.Direction;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.BlockRayTraceResult;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.vector.Vector3d;
import team.sdhq.eventBus.annotations.EventTarget;
import team.sdhq.eventBus.annotations.priority.HigherPriority;
import team.sdhq.eventBus.annotations.priority.LowerPriority;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.Set;

/**
 * <b>GrimSemi</b> — multi-face scaffold hardened against GrimAntiCheat.
 * <p>
 * <b>Strategy summary.</b> For every tick the player has a place-item in hand, we
 * enumerate a small set of (target position, click face) pairs, compute the exact
 * {@code (yaw, pitch)} that aims the client's eye at the centre of each candidate
 * face, filter by Grim's reach / solidity rules, and pick the candidate whose
 * required pitch is closest to the configured {@code downPitch} (default 80°).
 * We then spoof <i>only the Flying packet's</i> rotation to those angles — the
 * client's {@code rotationYaw / rotationPitch} and {@code getMotion()} are never
 * written — and emit {@code UseItemOn} in post-phase with the hit-vec / face / pos
 * triple used to generate the rotation. A handful of surgical hardenings
 * (documented inline as {@code FIX #1…#6}) cover the remaining detection paths.
 *
 * <h3>Grim checks explicitly addressed</h3>
 * <ol>
 *   <li><b>{@code blockplace.AirLiquidPlace}</b> — Dual-guarded: the primary and
 *       fallback searches both gate on {@link BlockUtil#isValidBlockPosition} for
 *       the clicked block, and the post-phase re-runs the same check right before
 *       {@code func_217292_a} to catch pre→post chunk/block-state mutations.</li>
 *
 *   <li><b>{@code blockplace.FabricatedPlace}</b> — The hit-vec is the deterministic
 *       geometric centre of the clicked face, which lies by construction on the
 *       face plane inside {@code [0, 1]³} local to the block.</li>
 *
 *   <li><b>{@code blockplace.PositionPlace / rotation leg}</b> — The Flying packet
 *       carries exactly the rotation computed from {@code (eyePos, hitVec)}, so
 *       Grim's server-side re-raycast lands on the same face we're clicking.</li>
 *
 *   <li><b>{@code combat.Reach / PositionPlace reach leg}</b> — 4.5² hard cap on
 *       eye→hitVec squared distance, using the eye position the Flying packet for
 *       this tick will actually carry ({@code event.get* + eyeHeight}).</li>
 *
 *   <li><b>{@code predictionengine.MovementTicker / MoveCorrector}</b> — FIX #3:
 *       if {@code |spoofedYaw − motionYaw| > 50°}, the sprint input direction and
 *       the reported look direction disagree, which Grim flags as an impossible
 *       sprint state. We temporarily clear {@code isSprinting()} before Flying so
 *       the client-side sprint action packet emits STOP_SPRINTING, then restore
 *       sprint via {@link EventMotion#attackPost} so the next tick re-sprints.</li>
 *
 *   <li><b>{@code post.Post / 200+ variants}</b> — FIX #4: when the spoofed rotation
 *       equals last tick's spoofed rotation AND the player's real rotation is
 *       stationary, {@code rotMoved} would be false and the client would emit a
 *       bare Position packet (no rotation) — Post flags the UseItemOn that follows
 *       because the server never received a Flying update to anchor the raycast.
 *       A per-tick alternating ±0.001° yaw jitter keeps {@code deltaYaw ≠ 0}.</li>
 *
 *   <li><b>{@code badpackets.BadPacketsW}</b> — Swing is emitted after UseItemOn,
 *       and only on {@link ActionResultType#isSuccessOrConsume} (FIX #2), so we
 *       never ghost-swing on FAIL/PASS.</li>
 *
 *   <li><b>Post HELD_ITEM_CHANGE interleave (2.3.67+)</b> — The ItemSpoof slot
 *       switch is dispatched in pre-phase (before Flying), not between Flying
 *       and UseItemOn; the Flying→UseItemOn gap stays completely empty.</li>
 * </ol>
 *
 * <h3>Render-layer fix</h3>
 * <ul>
 *   <li>FIX #5: {@link EventMotion#postUpdate()} copies {@code event.yaw/pitch}
 *       into the static {@code _prevYaw/_prevPitch} fields used by the third-person
 *       render. The runnables list runs before {@code postUpdate}, so we reset
 *       {@code event.setYaw/setPitch} to the real values inside an
 *       {@link EventMotion#attackPost} runnable — the Flying packet has already
 *       been flushed with the spoofed rotation, but the render never learns of it.</li>
 * </ul>
 *
 * <h3>Liveness under motion</h3>
 * <ul>
 *   <li>FIX #1: candidate targets include both the current feet block and a
 *       1-tick motion-predicted position (extended down to {@code feet.down(2)}
 *       for airborne recovery), with per-face rotation computation so bridging at
 *       speed / over gaps / in-air never starves the placement pipeline.</li>
 *   <li>FIX #6: on successful placement we enqueue the placed block's neighbours
 *       as fallback targets. When the primary search comes up empty (chunk edge
 *       timing, fast horizontal motion) we consult the fallback queue before
 *       giving up the tick.</li>
 * </ul>
 *
 * <h3>Invariants</h3>
 * <ul>
 *   <li>{@code mc.player.rotationYaw} and {@code rotationPitch} are never written.</li>
 *   <li>{@code mc.player.getMotion()} is never rewritten by this class.</li>
 *   <li>The Flying packet's yaw/pitch are written only for the tick of a
 *       successful candidate, and are restored to the real values (via the
 *       {@code attackPost} runnable) before {@code postUpdate()} copies them into
 *       the render state.</li>
 * </ul>
 */
public class BlockFlyGrimSemiMode extends Module {
    /** Grim's default survival reach lenience ceiling (4.5 blocks, squared). */
    private static final float GRIM_REACH_SQ = 4.5F * 4.5F;
    /**
     * Candidate face set sampled on each target block. UP is listed first because
     * for a bridging scaffold the ergonomic placement is on top of an adjacent
     * block (neighbour above the target → click DOWN face), which naturally yields
     * the steepest pitch and wins the {@link #downPitch} score in the common case.
     */
    private static final Direction[] CANDIDATE_FACES = {
            Direction.UP, Direction.NORTH, Direction.SOUTH, Direction.EAST,
            Direction.WEST, Direction.DOWN
    };
    /** Hard cap on {@link #fallbackQueue} size — bounds memory on long bridges. */
    private static final int FALLBACK_CAP = 16;

    private BlockFly blockFly;
    /**
     * Selected placement candidate for this tick. Produced in pre-phase by
     * {@link #findBestCandidate} (or {@link #tryFallback}) and consumed verbatim
     * by {@link #postSendPlace} — the hit-vec, face and click-pos match exactly
     * the (yaw, pitch) we wrote onto the Flying packet, so Grim's server-side
     * re-raycast lands on the identical face.
     */
    private PlaceCandidate stashedCandidate;
    private Hand hand = Hand.MAIN_HAND;
    private int previousSlot = -1;
    /**
     * Slot the ItemSpoof logic switched to in pre-phase (before Flying) so the post-phase
     * can restore it. {@code -1} means no switch happened this tick.
     */
    private int stashedSpoofedSlot = -1;
    /** The slot the player held before {@link #stashedSpoofedSlot}. */
    private int stashedPrevSlot = -1;
    /** ItemSpoof mode captured in pre-phase so post-phase uses a consistent value. */
    private String stashedItemSpoof = "None";
    /**
     * Position of the most recent successful placement. Its 6 axial neighbours are
     * enqueued onto {@link #fallbackQueue} so the next tick can retry around the
     * same area if the primary search starves (FIX #6).
     */
    private BlockPos lastPlacedPos;
    /**
     * Rolling fallback target queue. Consulted when {@link #findBestCandidate}
     * returns {@code null} — e.g. when the player briefly leaves the search radius
     * of a bridging target due to fast horizontal motion or a chunk edge. Entries
     * are requeued at the tail on each touch so the structure naturally self-prunes.
     */
    private final Deque<BlockPos> fallbackQueue = new ArrayDeque<>();

    private final NumberSetting<Float> constantSpeed;
    private final BooleanSetting strictReach;
    /**
     * Preferred Flying pitch (degrees). Among all legal (target, face) pairs found
     * by {@link #findBestCandidate}, the one whose <i>required</i> pitch is closest
     * to this value wins. 80° ≈ looking nearly straight down, matching a classic
     * scaffold ergonomic pose while still leaving enough horizontal component that
     * side-face candidates remain reachable when the vertical face is blocked.
     */
    private final NumberSetting<Float> downPitch;

    public BlockFlyGrimSemiMode() {
        super(ModuleCategory.MOVEMENT, "GrimSemi",
                "Multi-face scaffold tuned to bypass GrimAntiCheat without yaw-motion desync.");
        this.registerSetting(new ModeSetting("Speed Mode", "Speed mode", 0,
                "None", "Jump", "Constant", "Slow", "Sneak"));
        this.registerSetting(this.constantSpeed = new NumberSetting<>(
                "Constant Speed", "Constant speed", 0.0F, 0.0F, 6.0F, 0.1F));
        this.registerSetting(this.strictReach = new BooleanSetting(
                "Strict Reach", "Abort the tick if eye->hitVec > 4.5 blocks (Grim reach guard).", true));
        this.registerSetting(this.downPitch = new NumberSetting<>(
                "Down Pitch",
                "Preferred spoofed pitch (deg) for the Flying packet. Among all legal (target, face) candidates, the one whose required pitch is closest to this value wins.",
                80.0F, 60.0F, 90.0F, 1.0F));
    }

    @Override
    public void initialize() {
        this.blockFly = (BlockFly) this.access();
    }

    @Override
    public void onEnable() {
        this.previousSlot = mc.player.inventory.currentItem;
        this.stashedCandidate = null;
        this.stashedSpoofedSlot = -1;
        this.stashedPrevSlot = -1;
        this.stashedItemSpoof = "None";
        this.lastPlacedPos = null;
        this.fallbackQueue.clear();
        ((BlockFly) this.access()).lastSpoofedSlot = -1;
    }

    @Override
    public void onDisable() {
        if (this.previousSlot != -1
                && this.access().getStringSettingValueByName("ItemSpoof").equals("Switch")) {
            mc.player.inventory.currentItem = this.previousSlot;
        }
        this.previousSlot = -1;

        if (((BlockFly) this.access()).lastSpoofedSlot >= 0) {
            mc.getConnection().sendPacket(new CHeldItemChangePacket(mc.player.inventory.currentItem));
            ((BlockFly) this.access()).lastSpoofedSlot = -1;
        }
        this.stashedCandidate = null;
        this.stashedSpoofedSlot = -1;
        this.stashedPrevSlot = -1;
        this.stashedItemSpoof = "None";
        this.lastPlacedPos = null;
        this.fallbackQueue.clear();
        mc.timer.timerSpeed = 1.0F;
    }

    // ---------------------------------------------------------------------
    // Event wiring
    // ---------------------------------------------------------------------

    @EventTarget
    public void onSafeWalk(EventSafeWalk event) {
        if (this.isEnabled()
                && mc.player.isOnGround()
                && Client.getInstance().moduleManager.getModuleByClass(SafeWalk.class).isEnabled()) {
            event.setSafe(true);
        }
    }

    @EventTarget
    @LowerPriority
    public void onPacket(EventSendPacket event) {
        // Mirrors the pattern used by the other BlockFly sub-modes: suppress the extra
        // HeldItemChange we already emitted manually during ItemSpoof so the client-side
        // inventory does not round-trip.
        if (this.isEnabled() && mc.player != null
                && event.packet instanceof CHeldItemChangePacket
                && ((BlockFly) this.access()).lastSpoofedSlot >= 0) {
            event.cancelled = true;
        }
    }

    @EventTarget
    @HigherPriority
    public void onMove(EventMove event) {
        if (!this.isEnabled() || this.blockFly.getValidItemCount() == 0) {
            return;
        }

        if (this.access().getBooleanValueFromSettingName("No Sprint")) {
            mc.player.setSprinting(false);
        }

        switch (this.getStringSettingValueByName("Speed Mode")) {
            case "Jump":
                if (mc.player.isOnGround() && MovementUtil.isMoving() && !mc.player.isSneaking()) {
                    mc.player.jump();
                    ((Speed) Client.getInstance().moduleManager.getModuleByClass(Speed.class))
                            .callHypixelSpeedMethod();
                    event.setY(mc.player.getMotion().y);
                    event.setX(mc.player.getMotion().x);
                    event.setZ(mc.player.getMotion().z);
                }
                break;
            case "Constant": {
                double speed = this.constantSpeed.currentValue;
                if (!MovementUtil.isMoving()) {
                    speed = 0.0;
                }
                MovementUtil.setMotion(event, speed);
                break;
            }
            case "Slow":
                event.setX(event.getX() * (mc.player.isOnGround() ? 0.75 : 0.93));
                event.setZ(event.getZ() * (mc.player.isOnGround() ? 0.75 : 0.93));
                break;
            case "Sneak":
                event.setX(event.getX() * (mc.player.isOnGround() ? 0.65 : 0.85));
                event.setZ(event.getZ() * (mc.player.isOnGround() ? 0.65 : 0.85));
                break;
            default:
                break;
        }

        this.blockFly.onMove(event);
    }

    @EventTarget
    public void onJump(EventJump event) {
        if (this.isEnabled()
                && this.access().getStringSettingValueByName("Tower Mode").equalsIgnoreCase("Vanilla")
                && (!MovementUtil.isMoving()
                        || this.access().getBooleanValueFromSettingName("Tower while moving"))) {
            event.cancelled = true;
        }
    }

    /**
     * The core hook. Runs twice per tick:
     * <ul>
     *   <li>{@code isPre() == true}  → enumerate candidate (target, face) pairs, pick
     *       the one whose required pitch is closest to {@link #downPitch}, and spoof
     *       the Flying packet's yaw/pitch onto that rotation. FIX #3 / #4 / #5 fire
     *       here: sprint-desync guard, yaw jitter for {@code rotMoved}, and render
     *       reset via {@link EventMotion#attackPost}.</li>
     *   <li>{@code isPre() == false} → after Flying has been flushed, emit UseItemOn
     *       with the stashed candidate's geometry, swing only on
     *       {@link ActionResultType#isSuccessOrConsume} (FIX #2), and on success
     *       enqueue the placed block's neighbours onto the fallback queue (FIX #6).</li>
     * </ul>
     */
    @EventTarget
    @LowerPriority
    public void onUpdate(EventMotion event) {
        if (!this.isEnabled() || this.blockFly.getValidItemCount() == 0) {
            return;
        }

        if (event.isPre()) {
            this.preSelectTarget(event);
        } else {
            this.postSendPlace();
        }
    }

    // ---------------------------------------------------------------------
    // Grim-safe placement pipeline
    // ---------------------------------------------------------------------

    /**
     * Pre-phase: pick the best (target, face) candidate and spoof the Flying packet's
     * rotation onto the yaw/pitch required to aim the eye at that face's centre.
     * <p>
     * Invariants on success:
     * <ul>
     *   <li>{@code stashedCandidate.clickPos} passes
     *       {@link BlockUtil#isValidBlockPosition}.</li>
     *   <li>{@code stashedCandidate.hitVec} is the geometric centre of
     *       {@code clickPos}'s {@code face} — on the face plane, inside {@code [0,1]³}
     *       local (FabricatedPlace safe).</li>
     *   <li>Eye-to-hitVec squared distance ≤ {@link #GRIM_REACH_SQ} when
     *       {@link #strictReach} is on.</li>
     *   <li>Flying yaw = {@code candidate.yaw + jitter} (FIX #4).</li>
     *   <li>Flying pitch = {@code candidate.pitch} (matches Grim's server-side
     *       re-raycast target, defeating {@code RotationPlace} / {@code PositionPlace}).</li>
     *   <li>An {@link EventMotion#attackPost} runnable is queued that (a) restores
     *       sprint if we cleared it for the desync guard (FIX #3) and (b) resets
     *       {@code event.yaw/pitch} to the real rotation so {@code postUpdate()}
     *       copies real values into the render-layer {@code _prevYaw/_prevPitch}
     *       (FIX #5).</li>
     * </ul>
     */
    private void preSelectTarget(EventMotion event) {
        this.hand = Hand.MAIN_HAND;
        this.stashedCandidate = null;

        if (!this.blockFly.canPlaceItem(this.hand)) {
            return;
        }

        // Eye position the Flying packet for this tick will carry. Using event
        // values (not mc.player.getPos*) mirrors exactly what Grim reconstructs on
        // the server after applying Flying, so reach / cursor geometry matches
        // byte-for-byte what the check re-derives.
        final double feetX = event.getX();
        final double feetY = event.getY();
        final double feetZ = event.getZ();
        final double eyeX = feetX;
        final double eyeY = feetY + mc.player.getEyeHeight();
        final double eyeZ = feetZ;

        // --- FIX #1: multi-target / multi-face search ----------------------------
        // The previous revision cast a single fixed-pitch ray; that starved completely
        // when the player was mid-jump or bridging forward at speed. We now iterate
        // a small set of target positions (current feet, 1-tick motion-predicted feet,
        // each extended down by 2) × the 6 cardinal faces, and pick the candidate
        // whose required pitch is closest to downPitch.
        final double mx = mc.player.getMotion().x;
        final double mz = mc.player.getMotion().z;
        PlaceCandidate best = this.findBestCandidate(feetX, feetY, feetZ,
                eyeX, eyeY, eyeZ, mx, mz);

        // --- FIX #6: fallback queue -----------------------------------------------
        // On fast horizontal motion / chunk edges the primary search can momentarily
        // miss all targets. Consult positions seeded by the last successful placement.
        if (best == null) {
            best = this.tryFallback(eyeX, eyeY, eyeZ);
        }

        if (best == null) {
            // No placement this tick. Leave event untouched — Flying carries the
            // client's real rotation, no desync, no jitter. Post-phase is a no-op.
            return;
        }

        // --- FIX #3: sprint desync guard -----------------------------------------
        // Grim's MovementTicker / MoveCorrector flags "sprint with input perpendicular
        // to look direction" as an impossible state. When |spoofedYaw - motionYaw|
        // exceeds 50°, clear sprint client-side so the sprint-action sync inside
        // onUpdateWalkingPlayer (which fires BEFORE Flying — see ClientPlayerEntity
        // lines 267-274) emits STOP_SPRINTING. Restore sprint via attackPost so the
        // NEXT tick's sync emits START_SPRINTING; the net effect is a one-tick gap in
        // server-side sprint state, perfectly aligned with the one-tick rotation spoof.
        final double motionSq = mx * mx + mz * mz;
        if (motionSq > 1.0e-4 && mc.player.isSprinting()) {
            final float motionYaw = (float) (Math.toDegrees(Math.atan2(mz, mx)) - 90.0);
            final float diff = Math.abs(MathHelper.wrapDegrees(best.yaw - motionYaw));
            if (diff > 50.0F) {
                mc.player.setSprinting(false);
                event.attackPost(() -> mc.player.setSprinting(true));
            }
        }

        // --- FIX #4: guarantee rotMoved=true -------------------------------------
        // onUpdateWalkingPlayer computes rotMoved = (deltaYaw != 0 || deltaPitch != 0)
        // against lastReportedYaw/Pitch. Without jitter, a stationary player targeting
        // the same block two ticks in a row produces deltaYaw=0 and deltaPitch=0, so
        // a bare Position packet (no rotation) is sent. Grim's Post check then rejects
        // the UseItemOn because the server never saw a Flying update to anchor the
        // raycast for this tick's placement. ±0.001° alternating jitter on yaw is below
        // Grim's Rotations GCD threshold but mathematically non-zero.
        final float realYaw = mc.player.rotationYaw;
        final float realPitch = mc.player.rotationPitch;
        final float jitter = ((System.currentTimeMillis() & 1L) == 0L) ? 0.001F : -0.001F;
        event.setYaw(best.yaw + jitter);
        event.setPitch(best.pitch);

        // --- FIX #5: third-person head stays pointing at the real rotation -------
        // ClientPlayerEntity#onUpdateWalkingPlayer order:
        //     (1) pre-hook                         ← we are here, spoofing above
        //     (2) sprint action packet (flushes our setSprinting(false) from FIX #3)
        //     (3) Flying packet (flushes spoofed yaw/pitch)
        //     (4) lastReportedYaw/Pitch = event.getYaw()/getPitch()   ← spoofed
        //     (5) for (Runnable r : getRunnableList()) r.run()        ← our attackPost
        //     (6) event.postUpdate()   copies event.yaw/pitch into _prevYaw/_prevPitch
        //     (7) post-hook                        ← postSendPlace emits UseItemOn
        // The render layer reads _prevYaw/_prevPitch for third-person head interpolation.
        // By resetting event.yaw/pitch to the real values inside (5), postUpdate (6)
        // copies REAL values into the render state — the head stays pointing where the
        // mouse points, while lastReportedYaw/Pitch (already stored in (4) with spoofed
        // values) remain consistent with the Flying packet the server observed.
        event.attackPost(() -> {
            event.setYaw(realYaw);
            event.setPitch(realPitch);
        });

        // --- Post HELD_ITEM_CHANGE interleave guard ------------------------------
        // Grim 2.3.67 added HELD_ITEM_CHANGE to the Post tracker. Dispatching the slot
        // switch BETWEEN Flying and UseItemOn would be flagged as "interleaved
        // HELD_ITEM_CHANGE"; dispatching here (pre-Flying, since the pre-hook fires in
        // onUpdateWalkingPlayer BEFORE the Flying packet is serialised) keeps the
        // post-phase gap between Flying and UseItemOn empty.
        final int currentItem = mc.player.inventory.currentItem;
        this.stashedPrevSlot = currentItem;
        this.stashedItemSpoof = this.access().getStringSettingValueByName("ItemSpoof");
        this.stashedSpoofedSlot = -1;
        if (!"None".equals(this.stashedItemSpoof)) {
            final int slot = this.blockFly.getValidHotbarItemSlot();
            if (slot != -1 && slot != currentItem) {
                mc.getConnection().sendPacket(new CHeldItemChangePacket(slot));
                mc.player.inventory.currentItem = slot;
                ((BlockFly) this.access()).lastSpoofedSlot = slot;
                this.stashedSpoofedSlot = slot;
            }
        }

        this.stashedCandidate = best;
    }

    /**
     * Post-phase: the Flying packet (yaw=spoofed+jitter, pitch=spoofed) has already
     * been serialised and flushed; the ItemSpoof slot switch, if any, was dispatched
     * in pre-phase. Post-phase emits UseItemOn + gated swing + optional slot restore,
     * and on success seeds the fallback queue for the next tick (FIX #6).
     * <p>
     * Canonical packet order the server sees for a placement tick:
     * <pre>
     *   pre-phase  →  [CEntityActionPacket STOP_SPRINTING]   [only if FIX #3 fired]
     *              →  CHeldItemChangePacket(spoofSlot)       [only when ItemSpoof != "None" and slots differ]
     *   Flying     →  CPlayerPacket.PositionRotationPacket   (yaw=spoofed+jitter, pitch=spoofed)
     *   (attackPost: client-side restores sprint + resets event.yaw/pitch to real for render)
     *   post-phase →  CPlayerTryUseItemOnBlockPacket         (candidate.clickPos, .face, .hitVec)
     *              →  CAnimateHandPacket                     (swing — BadPacketsW order) [only on success]
     *              →  CHeldItemChangePacket(prevSlot)        [only for ItemSpoof = Spoof / LiteSpoof]
     * </pre>
     */
    private void postSendPlace() {
        final PlaceCandidate c = this.stashedCandidate;
        if (c == null) {
            return;
        }

        // --- AirLiquidPlace guard #2 ---------------------------------------------
        // Re-run the solidity check right before func_217292_a. Between pre (where
        // findBestCandidate already gated on isValidBlockPosition) and post, a chunk
        // unload / neighbour BUD / same-tick place from another module could have
        // invalidated clickPos. Abort silently if so — ItemSpoof slot state is still
        // restored so the hotbar never ends up out of sync with the server.
        if (!BlockUtil.isValidBlockPosition(c.clickPos)) {
            this.restoreSpoofedSlot();
            this.stashedCandidate = null;
            return;
        }

        // Refresh the hotbar pick right before place, identical to every other sub-mode.
        this.blockFly.method16736();

        // Reconstruct the RTR from the stashed candidate. All three fields (hitVec,
        // face, clickPos) were produced by the same buildCandidate call, so the triple
        // is internally consistent — FabricatedPlace clean.
        final BlockRayTraceResult rtr = new BlockRayTraceResult(c.hitVec, c.face, c.clickPos, false);
        final ActionResultType result = mc.playerController.func_217292_a(mc.player, mc.world, this.hand, rtr);

        // --- FIX #2: swing ONLY on actual success --------------------------------
        // func_217292_a returns FAIL when the server would reject the place (cooldown,
        // wrong side, item restrictions, world height, etc.) or PASS when the click
        // hit an interactable with no right-click behaviour. Swinging on FAIL/PASS
        // produced the constant arm-flapping animation and leaked UseItemOn noise
        // that BadPacketsW could cluster-detect. Gate the swing on isSuccessOrConsume
        // so only genuine placements emit the Arm packet.
        if (result.isSuccessOrConsume()) {
            if (!this.access().getBooleanValueFromSettingName("NoSwing")) {
                mc.player.swingArm(this.hand);
            } else {
                mc.getConnection().sendPacket(new CAnimateHandPacket(this.hand));
            }

            // --- FIX #6 seed: the block we just placed is now a valid click surface ---
            // Enqueue positions AROUND the placed block (its 6 neighbours) as fallback
            // targets. The placed block itself is at clickPos.offset(face).
            final BlockPos placedPos = c.clickPos.offset(c.face);
            this.lastPlacedPos = placedPos;
            this.enqueueFallback(placedPos);
        }

        // Restore slot for Spoof / LiteSpoof modes (Switch mode keeps the new slot)
        // regardless of success — otherwise a rejected place would leak the spoofed
        // hotbar slot into subsequent ticks.
        this.restoreSpoofedSlot();

        this.stashedCandidate = null;
    }

    /**
     * Rolls back the pre-phase ItemSpoof slot switch. Called from {@link #postSendPlace}
     * both on success (after UseItemOn) and on failure (if the AirLiquidPlace guard
     * aborts), so the hotbar never ends up out of sync with the server.
     */
    private void restoreSpoofedSlot() {
        if (this.stashedSpoofedSlot != -1
                && this.stashedPrevSlot != -1
                && this.stashedSpoofedSlot != this.stashedPrevSlot
                && ("Spoof".equals(this.stashedItemSpoof)
                        || "LiteSpoof".equals(this.stashedItemSpoof))) {
            mc.getConnection().sendPacket(new CHeldItemChangePacket(this.stashedPrevSlot));
            mc.player.inventory.currentItem = this.stashedPrevSlot;
            ((BlockFly) this.access()).lastSpoofedSlot = -1;
        }
        this.stashedSpoofedSlot = -1;
        this.stashedPrevSlot = -1;
        this.stashedItemSpoof = "None";
    }

    // ---------------------------------------------------------------------
    // Multi-face candidate search  (FIX #1)
    // ---------------------------------------------------------------------

    /**
     * Primary candidate search. Gathers a small deduplicated set of target positions
     * (current feet air block, 1-tick motion-predicted feet air block, each extended
     * down by up to 2 blocks so falls and bridging-over-gaps still find a viable
     * target) and for each target iterates {@link #CANDIDATE_FACES}. A (target, face)
     * pair produces a candidate only when:
     * <ul>
     *   <li>{@code target} is replaceable (currently air / water / tall grass / etc.),</li>
     *   <li>{@code target.offset(face)} is a valid click surface per
     *       {@link BlockUtil#isValidBlockPosition},</li>
     *   <li>eye → hitVec squared distance is within {@link #GRIM_REACH_SQ} when
     *       {@link #strictReach} is on.</li>
     * </ul>
     * Candidates are scored by {@code |pitch − downPitch|}; lowest score wins.
     */
    private PlaceCandidate findBestCandidate(double feetX, double feetY, double feetZ,
                                             double eyeX, double eyeY, double eyeZ,
                                             double motionX, double motionZ) {
        final Set<BlockPos> targets = new HashSet<>(8);
        final BlockPos feetAir = new BlockPos(feetX, feetY, feetZ);
        targets.add(feetAir.down());
        targets.add(feetAir.down(2));

        // 1-tick motion-predicted feet position. Helps when bridging forward at speed
        // because the current feet block can already be directly above a solid block
        // (making feetAir.down() non-replaceable) while the next tick's feet block
        // still lies over air.
        final BlockPos stepFeetAir = new BlockPos(feetX + motionX, feetY, feetZ + motionZ);
        targets.add(stepFeetAir.down());
        targets.add(stepFeetAir.down(2));

        PlaceCandidate best = null;
        float bestScore = Float.MAX_VALUE;
        final float pref = this.downPitch.currentValue;
        final boolean strict = this.strictReach.getCurrentValue();

        for (BlockPos t : targets) {
            if (!isReplaceable(t)) {
                continue;
            }
            for (Direction d : CANDIDATE_FACES) {
                final BlockPos neighbour = t.offset(d);
                if (!BlockUtil.isValidBlockPosition(neighbour)) {
                    continue;
                }
                // t = target (empty, we're placing into it). neighbour = existing
                // block we click. The face of `neighbour` that points back at `t`
                // is d.getOpposite() (since t.offset(d) == neighbour ⇒
                // neighbour.offset(d.getOpposite()) == t).
                final Direction clickFace = d.getOpposite();
                final PlaceCandidate cand = buildCandidate(neighbour, clickFace,
                        eyeX, eyeY, eyeZ);
                if (cand == null) {
                    continue;
                }
                if (strict) {
                    final double dSq = cand.hitVec.squareDistanceTo(eyeX, eyeY, eyeZ);
                    if (dSq > GRIM_REACH_SQ) {
                        continue;
                    }
                }
                final float score = Math.abs(cand.pitch - pref);
                if (score < bestScore) {
                    bestScore = score;
                    best = cand;
                }
            }
        }
        return best;
    }

    /**
     * Fallback search using {@link #fallbackQueue}. Each entry is a target position
     * around the last successful placement; we try each entry's faces and return the
     * best scoring candidate. Stale entries (no longer replaceable) are silently
     * dropped; live entries are rotated to the tail so the queue naturally cycles.
     */
    private PlaceCandidate tryFallback(double eyeX, double eyeY, double eyeZ) {
        if (this.fallbackQueue.isEmpty()) {
            return null;
        }
        final int n = this.fallbackQueue.size();
        final boolean strict = this.strictReach.getCurrentValue();
        final float pref = this.downPitch.currentValue;
        PlaceCandidate best = null;
        float bestScore = Float.MAX_VALUE;

        for (int i = 0; i < n; i++) {
            final BlockPos t = this.fallbackQueue.pollFirst();
            if (t == null) break;
            if (!isReplaceable(t)) {
                // Stale — filled in since enqueued, drop silently.
                continue;
            }
            // Live entry: keep for next tick.
            this.fallbackQueue.addLast(t);

            for (Direction d : CANDIDATE_FACES) {
                final BlockPos neighbour = t.offset(d);
                if (!BlockUtil.isValidBlockPosition(neighbour)) {
                    continue;
                }
                final Direction clickFace = d.getOpposite();
                final PlaceCandidate cand = buildCandidate(neighbour, clickFace,
                        eyeX, eyeY, eyeZ);
                if (cand == null) continue;
                if (strict) {
                    final double dSq = cand.hitVec.squareDistanceTo(eyeX, eyeY, eyeZ);
                    if (dSq > GRIM_REACH_SQ) continue;
                }
                final float score = Math.abs(cand.pitch - pref);
                if (score < bestScore) {
                    bestScore = score;
                    best = cand;
                }
            }
        }
        return best;
    }

    /**
     * Build a {@link PlaceCandidate} for the (clickPos, clickFace) pair using the
     * geometric centre of that face as the hit-vec and computing the yaw/pitch that
     * aims the eye at it. Returns {@code null} only for degenerate geometry (eye
     * exactly coincident with the face centre).
     */
    private static PlaceCandidate buildCandidate(BlockPos clickPos, Direction clickFace,
                                                 double eyeX, double eyeY, double eyeZ) {
        // Hit-vec = block centre + 0.5 × face normal. That lies exactly on the face
        // plane (normal axis has value 0 or 1 relative to the block corner) and at
        // the centre of the face (tangential axes at 0.5). FabricatedPlace clean.
        final double hx = clickPos.getX() + 0.5 + clickFace.getXOffset() * 0.5;
        final double hy = clickPos.getY() + 0.5 + clickFace.getYOffset() * 0.5;
        final double hz = clickPos.getZ() + 0.5 + clickFace.getZOffset() * 0.5;
        final double dx = hx - eyeX;
        final double dy = hy - eyeY;
        final double dz = hz - eyeZ;
        final double distSq = dx * dx + dy * dy + dz * dz;
        if (distSq < 1.0e-8) {
            return null;
        }
        final double distXZ = Math.sqrt(dx * dx + dz * dz);
        float yaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0);
        yaw = MathHelper.wrapDegrees(yaw);
        final float pitch = (float) (-Math.toDegrees(Math.atan2(dy, distXZ)));
        final Vector3d hit = new Vector3d(hx, hy, hz);
        return new PlaceCandidate(clickPos, clickFace, hit, yaw, pitch);
    }

    /**
     * Seed the fallback queue with the 6 axial neighbours of {@code placed}. The
     * placed block itself is now a valid click surface, so the useful target
     * positions next tick are the 6 air spaces immediately around it. Capped at
     * {@link #FALLBACK_CAP} entries — oldest (head) are evicted first.
     */
    private void enqueueFallback(BlockPos placed) {
        for (Direction d : CANDIDATE_FACES) {
            final BlockPos neigh = placed.offset(d);
            if (!this.fallbackQueue.contains(neigh)) {
                this.fallbackQueue.addLast(neigh);
            }
        }
        while (this.fallbackQueue.size() > FALLBACK_CAP) {
            this.fallbackQueue.pollFirst();
        }
    }

    /**
     * {@code true} iff the block at {@code pos} can be replaced by a placement
     * (air / water / tall grass / etc.). Uses the vanilla
     * {@code Material.isReplaceable()} directly so the classification matches
     * server-side decision-making exactly.
     */
    private static boolean isReplaceable(BlockPos pos) {
        if (pos == null) {
            return false;
        }
        final BlockState state = mc.world.getBlockState(pos);
        return state.getMaterial().isReplaceable();
    }

    // ---------------------------------------------------------------------
    // Stashed candidate DTO
    // ---------------------------------------------------------------------

    /**
     * Immutable tuple carried from pre-phase {@link #findBestCandidate} /
     * {@link #tryFallback} to post-phase {@link #postSendPlace}. Holds the
     * clicked block position, clicked face, exact hit-vec on that face, and the
     * (yaw, pitch) that aims the eye at the hit-vec. All fields are produced
     * atomically by {@link #buildCandidate} so the geometry is self-consistent.
     */
    private static final class PlaceCandidate {
        final BlockPos clickPos;
        final Direction face;
        final Vector3d hitVec;
        final float yaw;
        final float pitch;

        PlaceCandidate(BlockPos clickPos, Direction face, Vector3d hitVec,
                       float yaw, float pitch) {
            this.clickPos = clickPos;
            this.face = face;
            this.hitVec = hitVec;
            this.yaw = yaw;
            this.pitch = pitch;
        }
    }
}
