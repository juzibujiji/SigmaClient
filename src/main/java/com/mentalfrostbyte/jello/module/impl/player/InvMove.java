package com.mentalfrostbyte.jello.module.impl.player;

/*
 * Port notes (1.8.9 "cn.unfair" InvWalk -> this client, 1.16.5), with the old InvMove merged in:
 *   C16PacketClientStatus            -> CClientStatusPacket (State.OPEN_INVENTORY)
 *   C0EPacketClickWindow             -> CClickWindowPacket (getClickType(): CLONE = old mode 3, THROW = old mode 4)
 *   C0DPacketCloseWindow             -> CCloseWindowPacket
 *   GuiContainer / GuiInventory      -> ContainerScreen / InventoryScreen
 *   KeyBindUtil.updateKeyState(code) -> KeyBinding.setPressed(InputMappings.isKeyDown(window, keysym))
 *   PacketUtil.sendPacketNoEvent(p)  -> NetworkManager.sendNoEventPacket(p)
 *   TickEvent(PRE) / UpdateEvent(PRE)-> EventTick / EventUpdate
 * The open-inventory status packet only exists on <= 1.8 targets (ViaMCP), so VANILLA mode
 * falls back to "no packet to hide" on newer protocols instead of never activating.
 * From InvMove: the AACP sprint spoof, NoSprint, the Hypixel move slowdown and the
 * text-input screen guards. Its screen check was "not an InventoryScreen || not a ChestScreen",
 * which is always true, so the sprint state was only ever hidden and never restored.
 * Grim mode: Rise 6.9.5 GrimInventoryMove. GuiInventory/GuiChest -> InventoryScreen/ChestScreen.
 * Rise's SprintEvent fires after vanilla's sprint decisions, so its handler only needed
 * setSprinting(false); EventSprint fires before them, so it is also cancelled here, otherwise
 * vanilla instantly re-starts sprint and the server sees START/STOP_SPRINTING spam. Walks in
 * any container like Hypixel mode. Rise's "Manager Extra Sprint Ticks" setting only fed its
 * Manager module, which has no counterpart here.
 */

import com.mentalfrostbyte.Client;
import com.mentalfrostbyte.jello.event.impl.game.network.EventSendPacket;
import com.mentalfrostbyte.jello.event.impl.game.world.EventTick;
import com.mentalfrostbyte.jello.event.impl.player.EventSprint;
import com.mentalfrostbyte.jello.event.impl.player.movement.EventMove;
import com.mentalfrostbyte.jello.event.impl.player.EventUpdate;
import com.mentalfrostbyte.jello.gui.impl.jello.ingame.holders.KeyboardHolder;
import com.mentalfrostbyte.jello.module.Module;
import com.mentalfrostbyte.jello.module.data.ModuleCategory;
import com.mentalfrostbyte.jello.module.settings.impl.BooleanSetting;
import com.mentalfrostbyte.jello.module.settings.impl.ModeSetting;
import com.mentalfrostbyte.jello.gui.base.JelloPortal;
import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import net.minecraft.client.gui.screen.AbstractCommandBlockScreen;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.gui.screen.EditBookScreen;
import net.minecraft.client.gui.screen.EditSignScreen;
import net.minecraft.client.gui.screen.inventory.AnvilScreen;
import net.minecraft.client.gui.screen.inventory.ChestScreen;
import net.minecraft.client.gui.screen.inventory.ContainerScreen;
import net.minecraft.client.gui.screen.inventory.CreativeScreen;
import net.minecraft.client.gui.screen.inventory.InventoryScreen;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.client.util.InputMappings;
import net.minecraft.inventory.container.ClickType;
import net.minecraft.network.IPacket;
import net.minecraft.network.play.client.CClickWindowPacket;
import net.minecraft.network.play.client.CClientStatusPacket;
import net.minecraft.network.play.client.CCloseWindowPacket;
import net.minecraft.network.play.client.CEntityActionPacket;
import team.sdhq.eventBus.annotations.EventTarget;
import team.sdhq.eventBus.annotations.priority.LowestPriority;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

public class InvMove extends Module {
    private static final int VANILLA = 0;
    private static final int LEGIT = 1;
    private static final int HYPIXEL = 2;
    private static final int GRIM = 3;
    private static final int CLICK_DELAY_TICKS = 8;
    private static final int CREATIVE_SEARCH_TAB = 5;
    private static final double SLOWDOWN_FACTOR = 0.7;

    private final ModeSetting mode;
    private final BooleanSetting guiEnabled;
    private final BooleanSetting aacp;
    private final BooleanSetting noSprint;
    private final BooleanSetting slowdown;
    private final Queue<CClickWindowPacket> clickQueue = new ConcurrentLinkedQueue<>();
    private CClientStatusPacket pendingStatus = null;
    private boolean keysPressed = false;
    private boolean sprintHidden = false;
    private int delayTicks = 0;

    public InvMove() {
        super(ModuleCategory.PLAYER, "InvMove", "Move while a screen is open");
        this.mode = new ModeSetting("Mode", "Bypass strategy", VANILLA, "Vanilla", "Legit", "Hypixel", "Grim");
        this.guiEnabled = new BooleanSetting("ClickGUI", "Also move while a client screen is open", true);
        this.aacp = new BooleanSetting("AACP", "Hide the sprint state from the server while a container is open", false);
        this.noSprint = new BooleanSetting("NoSprint", "Stop sprinting client side while a container is open", false);
        this.slowdown = new BooleanSetting("Slowdown", "Bypass for Hypixel: move at 70% speed in containers", false);
        this.registerSetting(this.mode, this.guiEnabled, this.aacp, this.noSprint, this.slowdown);
    }

    @EventTarget
    @LowestPriority
    public void onTick(EventTick event) {
        if (!this.isEnabled() || mc.player == null || mc.getConnection() == null) {
            return;
        }

        CClickWindowPacket packet;
        while ((packet = this.clickQueue.poll()) != null) {
            this.sendNoEvent(packet);
        }
    }

    @EventTarget
    @LowestPriority
    public void onUpdate(EventUpdate event) {
        if (!this.isEnabled() || mc.player == null) {
            return;
        }

        this.updateSprintSpoof();

        if (this.guiEnabled.getCurrentValue() && this.isClientScreenOpen() && !this.isTypingInGui()) {
            this.pressMovementKeys();
            return;
        }

        if (this.canInvWalk() && this.delayTicks == 0) {
            this.pressMovementKeys();

            if (this.noSprint.getCurrentValue()) {
                mc.player.setSprinting(false);
            }
        } else {
            this.releaseMovementKeys();
            this.flushPendingStatus();

            if (this.delayTicks > 0) {
                this.delayTicks--;
            }
        }
    }

    @EventTarget
    public void onMove(EventMove event) {
        if (!this.isEnabled() || mc.player == null || !this.slowdown.getCurrentValue()) {
            return;
        }

        if (mc.currentScreen instanceof ContainerScreen) {
            event.setX(event.getX() * SLOWDOWN_FACTOR);
            event.setZ(event.getZ() * SLOWDOWN_FACTOR);
        }
    }

    /**
     * Grim: never sprint while the own inventory or a chest is open. Rise fires its SprintEvent
     * after vanilla's sprint decisions, so a plain setSprinting(false) stuck; EventSprint fires
     * before them, so cancelling is needed as well or vanilla re-starts sprint in the same tick.
     */
    @EventTarget
    public void onSprint(EventSprint event) {
        if (!this.isEnabled() || mc.player == null || this.mode.getModeIndex() != GRIM) {
            return;
        }

        if (mc.currentScreen instanceof InventoryScreen || mc.currentScreen instanceof ChestScreen) {
            mc.player.setSprinting(false);
            event.cancelled = true;
        }
    }

    @EventTarget
    public void onSendPacket(EventSendPacket event) {
        if (!this.isEnabled() || event.cancelled || mc.player == null) {
            return;
        }

        if (event.packet instanceof CEntityActionPacket actionPacket) {
            // While the sprint state is hidden, never let a fresh start-sprint reach the server.
            if (this.sprintHidden && this.aacp.getCurrentValue()
                    && actionPacket.getAction() == CEntityActionPacket.Action.START_SPRINTING) {
                event.cancelled = true;
            }
        } else if (event.packet instanceof CClientStatusPacket statusPacket) {
            if (this.mode.getModeIndex() == VANILLA && statusPacket.getStatus() == CClientStatusPacket.State.OPEN_INVENTORY) {
                event.cancelled = true;
                this.pendingStatus = statusPacket;
            }
        } else if (event.packet instanceof CCloseWindowPacket closePacket) {
            // The server was never told we opened our own inventory, so never tell it we closed it.
            if (this.pendingStatus != null && closePacket.getWindowId() == 0) {
                this.pendingStatus = null;
                event.cancelled = true;
            }
        } else if (event.packet instanceof CClickWindowPacket clickPacket) {
            switch (this.mode.getModeIndex()) {
                case VANILLA:
                    if (clickPacket.getWindowId() == 0) {
                        if (isDropOutside(clickPacket)) {
                            event.cancelled = true;
                            return;
                        }

                        if (this.pendingStatus != null) {
                            KeyBinding.unPressAllKeys();
                            this.keysPressed = false;
                            event.cancelled = true;
                            this.clickQueue.offer(clickPacket);
                        }
                    }
                    break;
                case LEGIT:
                    if (isDropOutside(clickPacket)) {
                        event.cancelled = true;
                    } else {
                        KeyBinding.unPressAllKeys();
                        this.keysPressed = false;
                        event.cancelled = true;
                        this.clickQueue.offer(clickPacket);
                        this.delayTicks = CLICK_DELAY_TICKS;
                    }
                    break;
                default:
                    break;
            }

            // Let the server open the inventory before the queued clicks reach it.
            this.flushPendingStatus();
        }
    }

    private boolean canInvWalk() {
        if (!(mc.currentScreen instanceof ContainerScreen) || this.isTextInputScreen()) {
            return false;
        }

        switch (this.mode.getModeIndex()) {
            case VANILLA:
                if (!(mc.currentScreen instanceof InventoryScreen)) {
                    return false;
                }

                return (!needsStatusPacket() || this.pendingStatus != null) && this.clickQueue.isEmpty();
            case LEGIT:
                return this.clickQueue.isEmpty();
            case HYPIXEL:
            default:
                return true;
        }
    }

    /**
     * Screens where the keyboard belongs to a text field, so driving the movement keys from the
     * raw key state would both type garbage and walk. The creative search tab is one of these.
     */
    private boolean isTextInputScreen() {
        if (mc.currentScreen instanceof CreativeScreen creativeScreen) {
            return creativeScreen.getSelectedTabIndex() == CREATIVE_SEARCH_TAB;
        }

        return mc.currentScreen instanceof ChatScreen
                || mc.currentScreen instanceof AnvilScreen
                || mc.currentScreen instanceof EditSignScreen
                || mc.currentScreen instanceof EditBookScreen
                || mc.currentScreen instanceof AbstractCommandBlockScreen
                || mc.currentScreen instanceof KeyboardHolder;
    }

    /**
     * AACP: tell the server we stopped sprinting for as long as a container is open, and restore
     * the sprint state once it closes. Without this the server sees a sprinting player who is
     * also browsing an inventory.
     */
    private void updateSprintSpoof() {
        if (!this.aacp.getCurrentValue()) {
            this.restoreSprintSpoof();
            return;
        }

        boolean containerOpen = mc.currentScreen instanceof ContainerScreen;

        if (containerOpen && !this.sprintHidden) {
            this.sprintHidden = true;

            if (mc.player.isSprinting()) {
                this.sendNoEvent(new CEntityActionPacket(mc.player, CEntityActionPacket.Action.STOP_SPRINTING));
            }
        } else if (!containerOpen) {
            this.restoreSprintSpoof();
        }
    }

    private void restoreSprintSpoof() {
        if (!this.sprintHidden) {
            return;
        }

        this.sprintHidden = false;

        if (mc.player != null && mc.player.isSprinting()) {
            this.sendNoEvent(new CEntityActionPacket(mc.player, CEntityActionPacket.Action.START_SPRINTING));
        }
    }

    private void pressMovementKeys() {
        KeyBinding[] movementKeys = new KeyBinding[]{
                mc.gameSettings.keyBindForward,
                mc.gameSettings.keyBindBack,
                mc.gameSettings.keyBindLeft,
                mc.gameSettings.keyBindRight,
                mc.gameSettings.keyBindJump,
                mc.gameSettings.keyBindSprint
        };

        long window = mc.getMainWindow().getHandle();

        for (KeyBinding keyBinding : movementKeys) {
            InputMappings.Input input = keyBinding.keyCode;
            if (input.getType() != InputMappings.Type.KEYSYM || input.getKeyCode() == InputMappings.INPUT_INVALID.getKeyCode()) {
                continue;
            }

            keyBinding.setPressed(InputMappings.isKeyDown(window, input.getKeyCode()));
        }

        if (Client.getInstance().moduleManager.getModuleByClass(AutoSprint.class).isEnabled()) {
            mc.gameSettings.keyBindSprint.setPressed(true);
        }

        this.keysPressed = true;
    }

    private void releaseMovementKeys() {
        if (!this.keysPressed) {
            return;
        }

        if (mc.currentScreen != null) {
            KeyBinding.unPressAllKeys();
        }

        this.keysPressed = false;
    }

    private void flushPendingStatus() {
        if (this.pendingStatus == null) {
            return;
        }

        this.sendNoEvent(this.pendingStatus);
        this.pendingStatus = null;
    }

    private void sendNoEvent(IPacket<?> packet) {
        if (mc.getConnection() == null) {
            return;
        }

        mc.getConnection().getNetworkManager().sendNoEventPacket(packet);
    }

    /**
     * True while one of the client's own screens (Click GUI, alt manager, ...) is showing, which is
     * where the ClickGUI setting applies. Its own container screens go through {@link #canInvWalk()}.
     */
    private boolean isClientScreenOpen() {
        return Client.getInstance().guiManager.getCurrentScreen() != null
                && !(mc.currentScreen instanceof ContainerScreen)
                && !this.isTextInputScreen();
    }

    private boolean isTypingInGui() {
        return Client.getInstance().guiManager.getCurrentScreen() != null
                && Client.getInstance().guiManager.getCurrentScreen().method13227();
    }

    /**
     * Clicking outside the window drops the held stack; while walking the cursor is over the
     * world, so those clicks are dropped instead of throwing items away.
     */
    private static boolean isDropOutside(CClickWindowPacket packet) {
        return packet.getSlotId() == -999
                && (packet.getClickType() == ClickType.CLONE || packet.getClickType() == ClickType.THROW);
    }

    /**
     * Only <= 1.8 targets announce "inventory opened" to the server, so only there is there
     * anything to withhold.
     */
    private static boolean needsStatusPacket() {
        return JelloPortal.getVersion().olderThanOrEqualTo(ProtocolVersion.v1_8);
    }

    @Override
    public void onDisable() {
        this.releaseMovementKeys();
        this.restoreSprintSpoof();
        this.flushPendingStatus();
        this.clickQueue.clear();
        this.delayTicks = 0;
    }

    //Override
    //我操，这个claude给stuff都skid来了
    //public String getFormattedName() {
        //return this.getName() + " §7" + this.mode.getCurrentValue();

}
