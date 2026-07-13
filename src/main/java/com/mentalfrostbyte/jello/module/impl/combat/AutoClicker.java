package com.mentalfrostbyte.jello.module.impl.combat;

import com.mentalfrostbyte.jello.event.impl.game.world.EventLoadWorld;
import com.mentalfrostbyte.jello.event.impl.game.network.EventReceivePacket;
import com.mentalfrostbyte.jello.event.impl.player.action.EventPlace;
import com.mentalfrostbyte.jello.event.impl.player.action.EventUseItem;
import com.mentalfrostbyte.jello.module.Module;
import com.mentalfrostbyte.jello.module.data.ModuleCategory;
import com.mentalfrostbyte.jello.module.impl.combat.killaura.InteractAutoBlock;
import com.mentalfrostbyte.jello.module.settings.impl.BooleanSetting;
import com.mentalfrostbyte.jello.module.settings.impl.NumberSetting;
import com.mentalfrostbyte.jello.util.system.math.counter.Counter;
import net.minecraft.client.multiplayer.PlayerController;
import net.minecraft.item.SwordItem;
import net.minecraft.network.play.server.SRespawnPacket;
import net.minecraft.util.math.RayTraceResult;
import team.sdhq.eventBus.annotations.EventTarget;
import team.sdhq.eventBus.annotations.priority.HighestPriority;
import team.sdhq.eventBus.annotations.priority.LowestPriority;

public class AutoClicker extends Module {
    public static int del, cpsdel;
    private final Counter timer = new Counter();
    private final InteractAutoBlock blockController;
    private int nextClickDelay;

    public AutoClicker() {
        super(ModuleCategory.COMBAT, "AutoClicker", "Longpress your attack keybind to hit entities automaticly");
        this.registerSetting(new NumberSetting<Integer>("Base CPS", "Base click per seconds.", 1, 1, 20, 1));
        this.registerSetting(new NumberSetting<Integer>("Min CPS", "Minimum click per seconds randomization.", 1, 1, 20, 1));
        this.registerSetting(new NumberSetting<Integer>("Max CPS", "Maximum click per seconds randomization.", 1, 1, 20, 1));
        this.registerSetting(new BooleanSetting("AutoBlock", "Automatically blocks for you.", false));
        this.registerSetting(new BooleanSetting("Block Hit", "Release, attack, then re-block while holding a sword.", false));
        this.registerSetting(new BooleanSetting("Hover Check", "Blocks only if you are hovering the target.", false));
        this.registerSetting(new NumberSetting<Integer>("Auto Block Ticks", "Autoblock frecuency.", 1, 1, 5, 1));
        this.registerSetting(new BooleanSetting("1.9+ Cooldown", "Use attack cooldown (1.9+).", false));
        this.blockController = new InteractAutoBlock(this);

    }

    public static int randomNumber(int positiveRange, int negativeRange) {
        int positive = Math.max(0, positiveRange);
        int negative = Math.max(0, negativeRange);
        return -negative + (int) (Math.random() * (positive + negative + 1));
    }

    @Override
    public void onEnable() {
        super.onEnable();
        resetClickSchedule();
    }

    @Override
    public void onDisable() {
        stopBlockHit();
        resetClickSchedule();
        super.onDisable();
    }

    @EventTarget
    public void onWorldLoad(EventLoadWorld event) {
        clearBlockHitStateAtWorldBoundary();
    }

    @EventTarget
    public void onRespawnPacket(EventReceivePacket event) {
        if (event.packet instanceof SRespawnPacket) {
            mc.execute(this::clearBlockHitStateAtWorldBoundary);
        }
    }

    private void clearBlockHitStateAtWorldBoundary() {
        blockController.clearPredictionBlockState();
        resetClickSchedule();
    }

    @EventTarget
    @LowestPriority
    public void onUseItem(EventUseItem event) {
        if (!isAttackInputActive()) {
            stopBlockHit();
            resetClickSchedule();
            return;
        }
        if (isMiningBlock()) {
            stopBlockHit();
            resetClickSchedule();
            return;
        }

        if (blockController.usesLegacySwordUseItemPath()) {
            stopBlockHit();
            if (getBooleanValueFromSettingName("AutoBlock") && canBlockAtCrosshair()) {
                int blockTicks = Math.max(1, (int) getNumberValueBySettingName("Auto Block Ticks"));
                if (mc.player.ticksExisted % blockTicks == 0) {
                    // Restore the original 1.8 path: Minecraft owns right-click handling
                    // and ViaVersion translates the resulting use-item packet.
                    event.useItem = true;
                }
            }
            return;
        }

        boolean blockHitRequested = isBlockHitEnabled() && canBlockAtCrosshair();
        boolean canStartBlockHit = blockHitRequested && blockController.hasPredictionBlockItem();
        if (consumePhysicalAttackPresses()) {
            // Vanilla consumes attack presses without attacking while another item is in use.
            if (mc.player.isHandActive() && !blockController.isBlocking()) {
                resetClickSchedule();
                return;
            }

            stopBlockHit();
            if (mc.player.isHandActive()) {
                resetClickSchedule();
                return;
            }
            mc.clickMouse();
            if (canStartBlockHit && blockController.startPredictionBlock()) {
                // We sent the use packet ourselves. Keep only our local shield use alive and
                // suppress Minecraft's generic right-click path (which would duplicate it).
                event.useItem = mc.player.isHandActive();
            }
            scheduleNextClick();
            return;
        }

        if (!blockHitRequested) {
            stopBlockHit();
            return;
        }

        if (blockController.isBlocking()) {
            // Block Hit owns use-item while it is active. This prevents a held physical
            // right-click or another combat listener from appending a second use packet.
            event.useItem = false;
            ensureClickScheduled();
            if (!blockController.isPredictionBlockItemStillHeld() || isClickDue()) {
                stopBlockHit();
            }
            event.useItem = mc.player.isHandActive();
            return;
        }

        // On modern protocols a sword without a shield cannot block. Do not steal the
        // physical right-click or another module's use-item action in that case.
        if (!canStartBlockHit) {
            return;
        }

        event.useItem = false;
        ensureClickScheduled();
        int blockTicks = Math.max(1, (int) getNumberValueBySettingName("Auto Block Ticks"));
        if (!isClickDue() && mc.player.ticksExisted % blockTicks == 0
                && blockController.startPredictionBlock() && mc.player.isHandActive()) {
            event.useItem = true;
        }
    }

    @EventTarget
    @HighestPriority
    public void onPlaceEvent(EventPlace var1) {
        if (!isAttackInputActive() || isMiningBlock()) {
            stopBlockHit();
            resetClickSchedule();
            return;
        }

        ensureClickScheduled();
        if (isClickDue()) {
            mc.clickMouse();
            if (isBlockHitEnabled() && canBlockAtCrosshair()) {
                blockController.startPredictionBlock();
            }
            scheduleNextClick();
        }
    }

    private void ensureClickScheduled() {
        if (nextClickDelay <= 0) {
            scheduleNextClick();
        }
    }

    private void scheduleNextClick() {
        int cps = (int) getNumberValueBySettingName("Base CPS");
        int minRandomization = (int) getNumberValueBySettingName("Min CPS");
        int maxRandomization = (int) getNumberValueBySettingName("Max CPS");

        int randomOffset = randomNumber(maxRandomization, minRandomization);
        cpsdel = Math.max(1, cps + randomOffset);
        del = Math.max(1, 1000 / cpsdel);
        nextClickDelay = del;
        timer.reset();
    }

    private void resetClickSchedule() {
        nextClickDelay = 0;
        timer.reset();
    }

    private boolean isClickDue() {
        return isCooldownReady() && timer.hasElapsed(nextClickDelay, false);
    }

    private boolean isCooldownReady() {
        return !getBooleanValueFromSettingName("1.9+ Cooldown")
                || (double) mc.player.getCooldownPeriod() < 1.26
                || mc.player.getCooledAttackStrength(0.0F) >= 1.0F;
    }

    private boolean isAttackInputActive() {
        return mc.player != null
                && mc.world != null
                && mc.playerController != null
                && mc.getConnection() != null
                && mc.currentScreen == null
                && mc.isGameFocused()
                && mc.mouseHelper.isMouseGrabbed()
                && mc.player.isAlive()
                && !mc.player.isSpectator()
                && mc.gameSettings.keyBindAttack.isKeyDown();
    }

    private boolean canBlockAtCrosshair() {
        if (!(mc.player.getHeldItemMainhand().getItem() instanceof SwordItem)) {
            return false;
        }
        if (mc.objectMouseOver != null && mc.objectMouseOver.getType() == RayTraceResult.Type.BLOCK) {
            return false;
        }
        return !getBooleanValueFromSettingName("Hover Check")
                || mc.objectMouseOver != null && mc.objectMouseOver.getType() == RayTraceResult.Type.ENTITY;
    }

    private boolean isBlockHitEnabled() {
        return getBooleanValueFromSettingName("AutoBlock")
                || getBooleanValueFromSettingName("Block Hit");
    }

    public boolean isBlockHitBlocking() {
        return isEnabled() && isBlockHitEnabled() && blockController.isBlocking();
    }

    private void stopBlockHit() {
        if (blockController.isBlocking()) {
            blockController.stopAutoBlock();
        }
    }

    private boolean isMiningBlock() {
        return PlayerController.isHittingBlock
                && mc.objectMouseOver != null
                && mc.objectMouseOver.getType() == RayTraceResult.Type.BLOCK;
    }

    private boolean consumePhysicalAttackPresses() {
        boolean pressed = false;
        while (mc.gameSettings.keyBindAttack.isPressed()) {
            pressed = true;
        }
        return pressed;
    }
}
