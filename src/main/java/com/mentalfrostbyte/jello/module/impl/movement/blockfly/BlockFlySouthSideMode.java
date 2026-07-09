/*
 * Ported from OpenSSNGScaffoldAndClutch (https://github.com/zyyzs/OpenSSNGScaffoldAndClutch)
 * Original: dev.southside.module.impl.world.Scaffold — integrated into SigmaClient as the
 * "SouthSide" mode of BlockFly per the port plan.
 *
 * Port notes (Yarn 1.21 / Fabric / oneconfig -> MCP 1.16.5 / SigmaClient):
 *   - oneconfig Dropdown/Switch/Slider/Color -> ModeSetting/BooleanSetting/NumberSetting/
 *     ColorSetting; hideIf(...) -> Setting.setHidden(Supplier).
 *   - Upstream event -> host event mapping:
 *       UpdateFoVEvent        -> EventGetFovModifier
 *       Render3DEvent         -> EventRender3D (+ SSRender3D)
 *       Render2DEvent         -> EventRender2DOffset (window-pixel HUD space; the NanoVG
 *                                Retro card is redrawn with RenderUtil rounded-rect/scissor/
 *                                TrueTypeFont primitives — oneconfig NanoVG does not exist here)
 *       UpdateHeldItemEvent   -> EventUpdateHeldItem
 *       StrafeEvent           -> EventMoveInput (onStrafe: telly jump logic)
 *       MovementInputEvent    -> EventMoveInput (onMovementInput: eagle sneak modulation)
 *       PlayerSlowdownEvent   -> EventSlowDown (upstream mutates the raw movement input)
 *       PlaceEvent (cancel)   -> EventClick RIGHT cancel
 *       RotationEvent         -> SSRotationEvent (fired before GameRenderer#getMouseOver)
 *       RotationAppliedEvent  -> SSRotationAppliedEvent (fired before processKeyBinds)
 *       TickEvent Stage.POST  -> EventRunTicks post
 *   - MC mappings: getInventory().selectedSlot -> inventory.currentItem, Vec3d -> Vector3d,
 *     Box -> AxisAlignedBB, options.jumpKey.isPressed() -> gameSettings.keyBindJump.isKeyDown(),
 *     interactionManager.interactBlock -> playerController.func_217292_a,
 *     interactionManager.interactItem -> playerController.processRightClick,
 *     HandSwingC2SPacket -> CAnimateHandPacket,
 *     PlayerActionC2SPacket.SWAP_ITEM_WITH_OFFHAND -> CPlayerDiggingPacket.Action.SWAP_ITEM_WITH_OFFHAND,
 *     player.jumpingCooldown -> player.jumpTicks, swingHand -> swingArm,
 *     List.getFirst() -> get(0) (Java 17 target).
 *   - Blocks that do not exist in 1.16.5 were dropped from the upstream lists
 *     (mangrove/cherry/bamboo signs, hanging signs, WATER_CAULDRON->CAULDRON,
 *     BAMBOO_PRESSURE_PLATE, TORCHFLOWER, CHERRY_BUTTON, SHORT_GRASS->GRASS).
 *   - setSuffix(mode) has no host equivalent for BlockFly sub-modes (the parent renders the
 *     active mode name); dropped.
 *   - Upstream obfuscator annotations (@NativeDefine/@BytecodeInline) dropped.
 *   - Handlers gate on isEnabled() because host sub-modes stay registered on the event bus
 *     (same pattern as the other BlockFly modes).
 */
package com.mentalfrostbyte.jello.module.impl.movement.blockfly;

import com.mentalfrostbyte.jello.event.impl.game.action.EventClick;
import com.mentalfrostbyte.jello.event.impl.game.render.EventRender2DOffset;
import com.mentalfrostbyte.jello.event.impl.game.render.EventRender3D;
import com.mentalfrostbyte.jello.event.impl.player.EventGetFovModifier;
import com.mentalfrostbyte.jello.event.impl.player.EventRunTicks;
import com.mentalfrostbyte.jello.event.impl.player.EventUpdateHeldItem;
import com.mentalfrostbyte.jello.event.impl.player.movement.EventMoveInput;
import com.mentalfrostbyte.jello.event.impl.player.movement.EventSlowDown;
import com.mentalfrostbyte.jello.module.Module;
import com.mentalfrostbyte.jello.module.data.ModuleCategory;
import com.mentalfrostbyte.jello.module.settings.impl.BooleanSetting;
import com.mentalfrostbyte.jello.module.settings.impl.ColorSetting;
import com.mentalfrostbyte.jello.module.settings.impl.ModeSetting;
import com.mentalfrostbyte.jello.module.settings.impl.NumberSetting;
import com.mentalfrostbyte.jello.southside.event.SSRotationAppliedEvent;
import com.mentalfrostbyte.jello.southside.event.SSRotationEvent;
import com.mentalfrostbyte.jello.southside.manager.SSPacketOrderManager;
import com.mentalfrostbyte.jello.southside.manager.SSServerPacketManager;
import com.mentalfrostbyte.jello.southside.render.SSRender3D;
import com.mentalfrostbyte.jello.southside.utils.SSBlockData;
import com.mentalfrostbyte.jello.southside.utils.SSChatUtils;
import com.mentalfrostbyte.jello.southside.utils.SSInventoryUtil;
import com.mentalfrostbyte.jello.southside.utils.math.SSMathUtils;
import com.mentalfrostbyte.jello.southside.utils.math.SSRandomUtils;
import com.mentalfrostbyte.jello.southside.utils.misc.SSBlinkUtils;
import com.mentalfrostbyte.jello.southside.utils.network.SSPacketUtils;
import com.mentalfrostbyte.jello.southside.utils.player.SSFallingPlayer;
import com.mentalfrostbyte.jello.southside.utils.player.SSMovementUtils;
import com.mentalfrostbyte.jello.southside.utils.player.SSPlayerUtils;
import com.mentalfrostbyte.jello.southside.utils.player.SSSlotUtils;
import com.mentalfrostbyte.jello.southside.utils.raytrace.SSClientRayTraceUtil;
import com.mentalfrostbyte.jello.southside.utils.rotation.SSMoveFixMode;
import com.mentalfrostbyte.jello.southside.utils.rotation.SSPriority;
import com.mentalfrostbyte.jello.southside.utils.rotation.SSRotationUtils;
import com.mentalfrostbyte.jello.southside.utils.types.SSRotation;
import com.mentalfrostbyte.jello.util.client.render.ResourceRegistry;
import com.mentalfrostbyte.jello.util.game.render.RenderUtil;
import net.minecraft.block.AirBlock;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.block.DoorBlock;
import net.minecraft.block.FenceGateBlock;
import net.minecraft.block.TrapDoorBlock;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.network.play.client.CAnimateHandPacket;
import net.minecraft.network.play.client.CPlayerDiggingPacket;
import net.minecraft.util.ActionResultType;
import net.minecraft.util.Direction;
import net.minecraft.util.Hand;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.BlockRayTraceResult;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.vector.Vector3d;
import org.newdawn.slick.TrueTypeFont;
import team.sdhq.eventBus.annotations.EventTarget;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static java.lang.Math.abs;
import static java.lang.Math.atan2;
import static java.lang.Math.cos;
import static java.lang.Math.floor;
import static java.lang.Math.sin;
import static java.lang.Math.sqrt;
import static java.lang.Math.toDegrees;
import static java.lang.Math.toRadians;

public class BlockFlySouthSideMode extends Module {
    public BlockFlySouthSideMode() {
        super(ModuleCategory.MOVEMENT, "SouthSide", "自动打撸 (OpenSSNGScaffoldAndClutch port)");
        this.registerSetting(mode = new ModeSetting("Mode", "Scaffold mode", 0, "Telly", "Snap", "Normal"));
        this.registerSetting(alwaysUpdateRot = new BooleanSetting("Always Update Rotation", "Always Update Rotation", false));
        this.registerSetting(placeTick = new NumberSetting<>("PlaceTick", "PlaceTick", 1.0F, 1.0F, 5.0F, 1.0F));
        this.registerSetting(rotTick = new NumberSetting<>("RotationTick", "RotationTick", 1.0F, 1.0F, 5.0F, 1.0F));
        this.registerSetting(spoofItem = new BooleanSetting("Spoof Item", "Spoof Item", true));
        this.registerSetting(noSwing = new BooleanSetting("No Swing", "No Swing", false));
        this.registerSetting(eagle = new BooleanSetting("Eagle", "Eagle", false));
        this.registerSetting(snap = new BooleanSetting("Snap", "Snap", false));
        this.registerSetting(noUptelly = new BooleanSetting("No Uptelly", "No Uptelly", true));
        this.registerSetting(godBridge = new BooleanSetting("GodBridge", "GodBridge", false));
        this.registerSetting(smoothed = new BooleanSetting("Heypixel UpTelly", "Heypixel UpTelly", true));
        this.registerSetting(safeMode = new BooleanSetting("Safe Mode", "Safe Mode", false));
        this.registerSetting(testOnGround = new BooleanSetting("Test OnGround", "Test OnGround", false));
        this.registerSetting(fixRotation = new BooleanSetting("Fix Rotation", "Fix Rotation", true));
        this.registerSetting(randomSlow = new BooleanSetting("SlowUpTelly", "SlowUpTelly", false));
        this.registerSetting(blockFly = new BooleanSetting("Block Fly", "Block Fly", false));
//        this.registerSetting(grimRotation = new BooleanSetting("Grim rotation", "Grim rotation", true));
        this.registerSetting(abuseRotation = new BooleanSetting("Abuse Rotation", "Abuse Rotation", true));
        this.registerSetting(blockSlotMode = new ModeSetting("Block Slot Mode", "Block Slot Mode", 0, "Farthest", "Most Blocks"));
        this.registerSetting(jumpMode = new ModeSetting("Jump Mode", "Jump Mode", 1, "Parkour", "Normal", "None"));
        this.registerSetting(safeDistance = new NumberSetting<>("Clutch Safe Distance", "Clutch Safe Distance", 4.5F, 1.0F, 5.0F, 0.25F));
        this.registerSetting(tellyEagleTick = new NumberSetting<>("EagleTick", "EagleTick", 1.0F, 1.0F, 5.0F, 1.0F));
        this.registerSetting(keepEagleSneakTick = new NumberSetting<>("KeepEagleTick", "KeepEagleTick", 1.0F, 1.0F, 5.0F, 1.0F));
        this.registerSetting(dbgV = new BooleanSetting("Debug", "Debug", false));
        this.registerSetting(keepFoV = new BooleanSetting("Keep FoV", "Keep FoV", true));
        this.registerSetting(fovValue = new NumberSetting<>("Fov", "Fov", 1.1F, 1.0F, 2.1F, 0.05F));
        this.registerSetting(mark = new BooleanSetting("Mark", "Mark", true));
        this.registerSetting(duplicateRotPlace = new BooleanSetting("DuplicateRotPlace", "DuplicateRotPlace", true));
        this.registerSetting(interactItem = new BooleanSetting("Interact Item Before Place", "Interact Item Before Place", false));
        this.registerSetting(markSideColor = new ColorSetting("Mark Side Color", "Mark Side Color", 0x46FFFFFF));
        this.registerSetting(markLineColor = new ColorSetting("Mark Line Color", "Mark Line Color", 0x96FFFFFF));
        this.registerSetting(blockCount = new BooleanSetting("BlockCount", "BlockCount", true));
        this.registerSetting(blockCountStyle = new ModeSetting("BlockCount Style", "BlockCount Style", 0, "Retro", "Old"));
        this.registerSetting(blockCountOffset = new NumberSetting<>("BlockCount Y Offset", "BlockCount Y Offset", 0.0F, 0.0F, 200.0F, 1.0F));

        // Upstream initPostRunnable() hideIf(...) wiring.
        jumpMode.setHidden(() -> !isMode(mode, "Telly"));
        placeTick.setHidden(() -> !isMode(mode, "Telly"));
        smoothed.setHidden(() -> !isMode(mode, "Telly"));
        safeMode.setHidden(() -> !smoothed.getCurrentValue());
        godBridge.setHidden(() -> !isMode(mode, "Normal"));
        tellyEagleTick.setHidden(() -> !eagle.getCurrentValue());
        keepEagleSneakTick.setHidden(() -> !eagle.getCurrentValue());
        fovValue.setHidden(() -> !keepFoV.getCurrentValue());
        markLineColor.setHidden(() -> !mark.getCurrentValue());
        markSideColor.setHidden(() -> !mark.getCurrentValue());
        blockCountStyle.setHidden(() -> !blockCount.getCurrentValue());
        blockCountOffset.setHidden(() -> !blockCount.getCurrentValue());
    }

    public final ModeSetting mode;
    public final BooleanSetting alwaysUpdateRot;
    public final NumberSetting<Float> placeTick;
    public final NumberSetting<Float> rotTick;
    public final BooleanSetting spoofItem;
    public final BooleanSetting noSwing;
    public final BooleanSetting eagle;
    public final BooleanSetting snap;
    public final BooleanSetting noUptelly;
    public final BooleanSetting godBridge;
    public final BooleanSetting smoothed;
    public final BooleanSetting safeMode;
    public final BooleanSetting testOnGround;
    public final BooleanSetting fixRotation;
    public final BooleanSetting randomSlow;
    public final BooleanSetting blockFly;
    public final BooleanSetting abuseRotation;
    public final ModeSetting blockSlotMode;
    public final ModeSetting jumpMode;
    public final NumberSetting<Float> safeDistance;
    public final NumberSetting<Float> tellyEagleTick;
    public final NumberSetting<Float> keepEagleSneakTick;
    public final BooleanSetting dbgV;
    public final BooleanSetting keepFoV;
    public final NumberSetting<Float> fovValue;
    public final BooleanSetting mark;
    private final BooleanSetting duplicateRotPlace;
    private final BooleanSetting interactItem;
    private final ColorSetting markSideColor;
    private final ColorSetting markLineColor;
    public final BooleanSetting blockCount;
    public final ModeSetting blockCountStyle;
    public final NumberSetting<Float> blockCountOffset;

    // 1.16.5 note: mangrove/cherry/bamboo signs and all hanging signs do not exist and were
    // dropped; WATER_CAULDRON -> CAULDRON; BAMBOO_PRESSURE_PLATE dropped.
    public static final List<Block> invalidBlocks = Arrays.asList(
            Blocks.ENCHANTING_TABLE, Blocks.OAK_SIGN, Blocks.CHEST, Blocks.ENDER_CHEST,
            Blocks.TRAPPED_CHEST, Blocks.ANVIL, Blocks.SAND, Blocks.COBWEB, Blocks.TORCH,
            Blocks.CRAFTING_TABLE, Blocks.FURNACE, Blocks.CAULDRON, Blocks.DISPENSER,
            Blocks.STONE_PRESSURE_PLATE, Blocks.NOTE_BLOCK,
            Blocks.DROPPER, Blocks.TNT, Blocks.REDSTONE_TORCH, Blocks.DAYLIGHT_DETECTOR,
            Blocks.OAK_SIGN, Blocks.BIRCH_SIGN, Blocks.SPRUCE_SIGN, Blocks.JUNGLE_SIGN,
            Blocks.ACACIA_SIGN, Blocks.DARK_OAK_SIGN,
            Blocks.CRIMSON_SIGN, Blocks.WARPED_SIGN);

    private SlotData slot, blockSlot;
    int count;
    int lastCount = 0;
    private int startHotbarCount = 1;
    private boolean canPlace;
    private SSBlockData blockData;
    private SSBlockData lastBlockData;
    private int rotateCount = 0;
    private double posY;
    private BlockPos lastPlacePosition = null;
    private int tellyJumpTicks;
    private boolean waitingForEagleSneak;
    private SSRotation lastRotation;
    private SSRotation rot;
    private double boxExpand = 0.15;
    int oldSlot;
    int placeCount = 0;
    int ups = 0;

    @Override
    public void onEnable() {
        placeCount = 0;
        ups = 0;
        if (mc.player == null) return;
        if (blockFly.getCurrentValue()) SSServerPacketManager.reset(true);
        this.boxExpand = SSRandomUtils.nextDouble(0.1, 0.2);

        lastRotation = new SSRotation(mc.player.rotationYaw, mc.player.rotationPitch);
        this.slot = new SlotData(mc.player.inventory.currentItem, Hand.MAIN_HAND);
        this.oldSlot = mc.player.inventory.currentItem;
        this.blockSlot = null;
        startHotbarCount = Math.max(1, getBlockCountHotbar());
        blockData = null;
        canPlace = true;
        lastPlacePosition = null;

        tellyJumpTicks = 0;
        waitingForEagleSneak = false;

        rot = null;
        super.onEnable();
    }

    @Override
    public void onDisable() {
        if (mc.player == null) return;
        mc.player.inventory.currentItem = slot.slot();
        mc.player.inventory.currentItem = oldSlot;
        mc.gameSettings.keyBindSneak.setPressed(false);
        SSMovementUtils.resetMove();
//        if (blockFly.getCurrentValue()) SSServerPacketManager.reset(false);
        super.onDisable();
    }

    @EventTarget
    public void onFoV(EventGetFovModifier event) {
        if (!this.isEnabled()) return;
        if (keepFoV.getCurrentValue() && SSPlayerUtils.isMoving()) {
            event.fovModifier = fovValue.getCurrentValue().floatValue() + SSPlayerUtils.getMoveSpeedEffectAmplifier() * 0.13F;
        }
    }

    @EventTarget
    public void onRender3D(EventRender3D event) {
        if (!this.isEnabled()) return;
        if (lastPlacePosition != null && mark.getCurrentValue()) {
            AxisAlignedBB box = new AxisAlignedBB(lastPlacePosition);

            SSRender3D.boxLines(box, new Color(markLineColor.getCurrentValue(), true));
            SSRender3D.boxSides(box, new Color(markSideColor.getCurrentValue(), true));
        }
    }

    @EventTarget
    public void onRender2D(EventRender2DOffset event) {
        if (!this.isEnabled() || mc.player == null || !blockCount.getCurrentValue()) return;
        int newCount = Math.max(0, getBlockCountHotbar());
        if (newCount > startHotbarCount) {
            startHotbarCount = newCount;
        }
        float rate = MathHelper.clamp((float) newCount / startHotbarCount, 0f, 1f);
        // Upstream draws in the scaled-GUI space; this HUD event runs in raw window pixels,
        // so every upstream dimension is multiplied by the GUI scale factor.
        float k = (float) mc.getMainWindow().getGuiScaleFactor();
        float centerX = mc.getMainWindow().getWidth() / 2f;
        float centerY = mc.getMainWindow().getHeight() / 2f;
        float y = centerY + (15f + blockCountOffset.getCurrentValue().floatValue()) * k;

        if (isMode(blockCountStyle, "Old")) {
            TrueTypeFont font = k >= 2.0F ? ResourceRegistry.JelloLightFont18 : ResourceRegistry.JelloLightFont12;
            String text = newCount + " Blocks";
            float x = centerX - font.getWidth(text) / 2f;
            RenderUtil.drawString(font, x, y, text, getBlockCountColor(newCount));
            lastCount = newCount;
            count = newCount;
            return;
        }
        if (isMode(blockCountStyle, "Retro")) {
            ItemStack displayStack = getHeldBlockStack();

            float scale = k; // upstream: 1.0f in scaled space
            float height = 26f * scale;
            float radius = 5f * scale;
            float pad = 5f * scale;
            float itemSize = 18f * scale;
            float itemOffsetX = 4f * scale;
            float gap = 3f * scale;

            TrueTypeFont labelFont = k >= 2.0F ? ResourceRegistry.JelloLightFont20 : ResourceRegistry.JelloLightFont12;
            TrueTypeFont countFont = k >= 2.0F ? ResourceRegistry.JelloMediumFont20 : ResourceRegistry.JelloMediumFont14;

            String label = "Blocks";
            String countText = String.valueOf(newCount);

            float labelWidth = labelFont.getWidth(label);
            float countWidth = countFont.getWidth(countText);
            float textWidth = labelWidth + gap + countWidth;

            float width = itemOffsetX + itemSize + pad + textWidth + pad;
            float x = centerX - (width / 2f);

            int bg = new Color(0, 0, 0, 90).getRGB();
            int revert = new Color(255, 255, 255, 90).getRGB();
            int textColor = getBlockCountColor(newCount);
            int revertedTextColor = getBlockCountColor_Black(newCount);

            float rightAreaLeft = x + itemOffsetX + itemSize + pad;
            float rightAreaRight = x + width - pad;
            float rightAreaWidth = Math.max(0f, rightAreaRight - rightAreaLeft);
            float groupX = rightAreaLeft + Math.max(0f, (rightAreaWidth - textWidth) / 2f);

            float labelY = y + (height - labelFont.getHeight(label)) / 2f;
            float countY = y + (height - countFont.getHeight(countText)) / 2f;

            // 先画底色 (base layer, clipped to the not-yet-consumed portion)
            if (width - width * rate > 0.5f) {
                RenderUtil.startScissor(x + width * rate, y, width - width * rate, height);
                RenderUtil.drawRoundedRect(x, y, width, height, radius, bg);
                RenderUtil.drawString(labelFont, groupX, labelY, label, new Color(220, 220, 220).getRGB());
                RenderUtil.drawString(countFont, groupX + labelWidth + gap, countY, countText, textColor);
                RenderUtil.restoreScissor();
            }
            // 再用 scissor 画进度层（只裁切宽度，不裁切高度）(progress layer)
            if (width * rate > 0.5f) {
                RenderUtil.startScissor(x, y, width * rate, height);
                RenderUtil.drawRoundedRect(x, y, width, height, radius, revert);
                RenderUtil.drawString(labelFont, groupX, labelY, label, new Color(0, 0, 0, 204).getRGB());
                RenderUtil.drawString(countFont, groupX + labelWidth + gap, countY, countText, revertedTextColor);
                RenderUtil.restoreScissor();
            }
            if (!displayStack.isEmpty()) {
                float itemX = x + itemOffsetX;
                float itemY = y + ((height - itemSize) / 2f);
                RenderUtil.drawItem(displayStack, (int) itemX, (int) itemY, (int) itemSize, (int) itemSize);
            }
        }

        lastCount = newCount;
        count = newCount;
    }

    @EventTarget
    public void onHeldItem(EventUpdateHeldItem event) {
        if (!this.isEnabled() || mc.player == null) return;
        if (spoofItem.getCurrentValue() && event.getHand() == Hand.MAIN_HAND) {
            event.setItem(mc.player.inventory.getStackInSlot(this.oldSlot));
        }
    }

    /** Upstream: onMoveInput(StrafeEvent) — the telly jump logic. */
    @EventTarget
    public void onStrafe(EventMoveInput event) {
        if (!this.isEnabled() || mc.player == null || mc.world == null) return;
        // Upstream calls this.setSuffix(mode.getMode()) here; host sub-modes have no suffix.
        if (this.blockSlot == null || blockSlot.check()) {
            return;
        }
        if (SSPlayerUtils.onGroundTicks > (smoothed.getCurrentValue() && safeMode.getCurrentValue() && !testOnGround.getCurrentValue() ? 1 : 0)
                && !mc.gameSettings.keyBindJump.isKeyDown() && SSPlayerUtils.isMoving() && isMode(mode, "Telly")) {

            switch (jumpMode.getCurrentValue()) {
                case "None" -> {
                }
                case "Normal" -> mc.player.jump();
                case "Parkour" -> {
                    double yaw = toRadians(mc.player.rotationYaw);
                    double forwardX = -sin(yaw);
                    double forwardZ = cos(yaw);

                    BlockPos frontPos1 = new BlockPos(
                            (int) (mc.player.getPosX() + forwardX),
                            (int) (mc.player.getPosY() - 0.1),
                            (int) (mc.player.getPosZ() + forwardZ)
                    );
                    BlockPos frontPos2 = new BlockPos(
                            (int) (mc.player.getPosX() + forwardX * 2),
                            (int) (mc.player.getPosY() - 0.1),
                            (int) (mc.player.getPosZ() + forwardZ * 2)
                    );

                    if (mc.world.getBlockState(frontPos1).getBlock() instanceof AirBlock ||
                            mc.world.getBlockState(frontPos2).getBlock() instanceof AirBlock) {
                        mc.player.jump();
                    }
                }
            }


            if (eagle.getCurrentValue() && isMode(mode, "Telly")) {
                waitingForEagleSneak = true;
                tellyJumpTicks = 0;
            }
        }
    }

    private SSRotation getBRot(boolean forceRotation) {
        SSRotation rotation = blockData != null ? SSRotationUtils.getClosestToBlockFace(blockData.pos(), blockData.facing(), SSRotationUtils.getServerRotation().yaw, SSRotationUtils.getServerRotation().pitch) : null;
        if (rotation == null) {
            if (SSRotationUtils.normalizeYawDiff(mc.player.rotationYaw + 100f, SSRotationUtils.getServerRotation().yaw) < SSRotationUtils.normalizeYawDiff(mc.player.rotationYaw - 100f, SSRotationUtils.getServerRotation().yaw)) {
                rotation = new SSRotation(mc.player.rotationYaw + 100f, SSRotationUtils.getServerRotation().pitch);
            } else {
                rotation = new SSRotation(mc.player.rotationYaw - 100f, SSRotationUtils.getServerRotation().pitch);
            }
        }
        if (SSMovementUtils.cancelMove) {
            return SSRotationUtils.getClosestToBlockFace(blockData.pos(), blockData.facing(), SSRotationUtils.getServerRotation().yaw, SSRotationUtils.getServerRotation().pitch);
        }
        double diff = SSRotationUtils.yawDiffDirectly(rotation.yaw, SSRotationUtils.getServerRotation().yaw);
        if (isMode(mode, "Telly")) {
            if (mc.gameSettings.keyBindJump.isKeyDown() && noUptelly.getCurrentValue()) {
                return rotation;
            }
            if (mc.gameSettings.keyBindJump.isKeyDown() && randomSlow.getCurrentValue()) {
                ups++;
                if (ups % 2 == 0) {
                    return rotation;
                }
            }
            if (smoothed.getCurrentValue() && (SSPlayerUtils.offGroundTicks < rotTick.getCurrentValue().intValue() || safeMode.getCurrentValue())) {
                if (SSPlayerUtils.onGroundTicks > 0) {
                    if (safeMode.getCurrentValue() && (!testOnGround.getCurrentValue() || mc.gameSettings.keyBindJump.isKeyDown())) {
                        switch (SSPlayerUtils.onGroundTicks) {
                            case 1: {
                                if (!forceRotation) {
                                    rotation.yaw = SSRotationUtils.getServerRotation().getYaw() + SSRotationUtils.smooth((float) diff, (float) (diff / 2f));
                                    rotation.pitch = 75.5f;
                                } else {
                                    rotation = SSRotationUtils.getClosestToBlockFace(blockData.pos(), blockData.facing(), mc.player.rotationYaw, SSRotationUtils.getServerRotation().pitch);
                                }
                                mc.player.jumpTicks = 2;
//                                SSMovementUtils.cancelMove();
                                break;
                            }
                            case 2: {
                                return new SSRotation(mc.player.rotationYaw, 75.5f);
                            }
                        }
                    } else {
                        return new SSRotation(mc.player.rotationYaw, 75.5f);
                    }
                } else {
                    float smooth = switch (SSPlayerUtils.offGroundTicks) {
                        case 1 -> 80f;
                        default -> 50.0f;
                    };
                    smooth -= SSRandomUtils.generateRandomFloat(0.001f, 0.005f);
                    rotation.yaw = SSRotationUtils.getServerRotation().getYaw() + SSRotationUtils.smooth((float) diff, smooth);
                }
            } else {
                if (snap.getCurrentValue() && mc.gameSettings.keyBindJump.isKeyDown()) {
                    if (lastBlockData == null || SSPlayerUtils.offGroundTicks < rotTick.getCurrentValue().intValue()) {
                        return new SSRotation(mc.player.rotationYaw, 85.0F + (float) Math.random());
                    }
                } else {
                    if (SSPlayerUtils.offGroundTicks < rotTick.getCurrentValue().intValue()) {
                        return new SSRotation(mc.player.rotationYaw, 85.0F + (float) Math.random());
                    }
                }
            }
        }
        if (lastRotation != null && blockData != null && SSClientRayTraceUtil.didHitBlockFace(mc.player, lastRotation.yaw, lastRotation.pitch, blockData.pos(), blockData.facing(), true)) {
            return lastRotation;
        }
        if (blockData != null && !alwaysUpdateRot.getCurrentValue() && SSPlayerUtils.offGroundTicks >= rotTick.getCurrentValue().intValue()) {
            if (!SSClientRayTraceUtil.didHitBlockFace(mc.player, rotation.yaw, rotation.pitch, blockData.pos(), blockData.facing(), true) && SSPlayerUtils.offGroundTicks >= rotTick.getCurrentValue().intValue()) {
                lastRotation.yaw += (float) Math.random();
                return lastRotation;
            }
        }
        lastRotation = rotation;
        return rotation;
    }

    private void place() {
        if (blockData != null && !SSBlinkUtils.blinking) {
            if (SSRotationUtils.getRotation() == null) {
                return;
            }

            if (mc.playerController == null) {
                return;
            }

            if (!canPlace) {
                return;
            }
            BlockRayTraceResult block = SSClientRayTraceUtil.getFacedBlock(SSRotationUtils.getRotation().yaw, SSRotationUtils.getRotation().pitch);
            if (!SSClientRayTraceUtil.didHitBlockFace(mc.player, SSRotationUtils.getRotation().yaw, SSRotationUtils.getRotation().pitch, blockData.pos(), blockData.facing(), true)) {
                return;
            }
            if (this.blockSlot.hand().equals(Hand.MAIN_HAND)) {
                mc.player.inventory.currentItem = this.blockSlot.slot();
            }
            if (duplicateRotPlace.getCurrentValue() && SSPlayerUtils.lastPitchDiff > 2.0d) {
                double xDiff = Math.abs(SSPlayerUtils.lastPitchDiff - SSPlayerUtils.lastPlacePitchDiff);
                if (xDiff < 0.0001d) {
                    return;
                }
            }
            if (blockFly.getCurrentValue()) {
                if (!SSServerPacketManager.deSyncing) {
                    SSServerPacketManager.setup();
                }
                if (placeCount == 0) {
                    SSPacketUtils.sendPacketNoEvent(new CPlayerDiggingPacket(
                            CPlayerDiggingPacket.Action.SWAP_ITEM_WITH_OFFHAND,
                            new BlockPos(0, 0, 0),
                            Direction.DOWN
                    ));
                    SSPacketOrderManager.swap = true;
                }
            }
            if (interactItem.getCurrentValue()) {
                mc.playerController.processRightClick(mc.player, mc.world, Hand.MAIN_HAND);
//                mc.playerController.processRightClick(mc.player, mc.world, Hand.MAIN_HAND);
            }
            if (block == null) {
                return;
            }
            ActionResultType result = mc.playerController.func_217292_a(mc.player, mc.world, this.blockSlot.hand(), block);
            SSPacketOrderManager.rightClicking = true;
            if (result == ActionResultType.SUCCESS) {
                placeCount++;
                lastPlacePosition = blockData.pos().offset(blockData.facing());
                if (SSPlayerUtils.lastPitchDiff > 0.0d) {
                    SSPlayerUtils.lastPlacePitchDiff = SSPlayerUtils.lastPitchDiff;
                }
                if (noSwing.getCurrentValue()) {
                    SSPacketUtils.sendPacket(new CAnimateHandPacket(this.blockSlot.hand()));
                } else {
                    mc.player.swingArm(this.blockSlot.hand());
                }
            }
        }
    }

    /** Upstream: onPlace(PlaceEvent) { event.setCancelled(true); } — suppress manual right-click placing. */
    @EventTarget
    public void onClick(EventClick event) {
        if (!this.isEnabled()) return;
        if (event.getButton() == EventClick.Button.RIGHT) {
            event.cancelled = true;
        }
    }

    @EventTarget
    public void onRotationApplied(SSRotationAppliedEvent event) {
        if (!this.isEnabled() || mc.player == null || mc.world == null) return;
//        if (SSPlayerUtils.onGroundTicks == 1 && safeMode.getCurrentValue()) {
//            double diff = SSRotationUtils.yawDiffDirectly(mc.player.rotationYaw, SSRotationUtils.getServerRotation().yaw);
//            SSChatUtils.info("sp");
//            SSPacketUtils.spoofRotation(new SSRotation(SSRotationUtils.getServerRotation().getYaw() + SSRotationUtils.smooth((float) diff, (float) (diff / 2f)), mc.player.rotationPitch));
//        }
        if (abuseRotation.getCurrentValue() && SSRotationUtils.rotation != null) {
            rotationAbuse(30f, SSRotationUtils.rotation.yaw);
        }
        place();
    }

    /** Upstream: onPostTick(TickEvent) with Stage.POST. */
    @EventTarget
    public void onPostTick(EventRunTicks event) {
        if (!this.isEnabled()) return;
        if (!event.isPre()) {
            if (blockFly.getCurrentValue() && SSServerPacketManager.deSyncTick > 16) {
                SSServerPacketManager.releaseTick(true);
            }
        }
    }

    @EventTarget
    public void onRotation(SSRotationEvent event) {
        if (!this.isEnabled() || mc.player == null || mc.world == null) return;
        this.blockSlot = null;

        if (SSInventoryUtil.isFullBlock(mc.player.getHeldItemOffhand()) && isValid(mc.player.getHeldItemOffhand().getItem())) {
            this.blockSlot = new SlotData(SSSlotUtils.OFFHAND, Hand.OFF_HAND);
        }

        if (blockSlot == null && !isMode(blockSlotMode, "Most Blocks")) {
            if (SSInventoryUtil.isFullBlock(mc.player.getHeldItemMainhand()) && isValid(mc.player.getHeldItemMainhand().getItem())) {
                this.blockSlot = new SlotData(mc.player.inventory.currentItem, Hand.MAIN_HAND);
            }
        }

        if (blockSlot == null) {
            int hotbarSlot = getHotbarBlockSlot();
            if (hotbarSlot != -1) {
                this.blockSlot = new SlotData(hotbarSlot, Hand.MAIN_HAND);
            }
        }

        if (this.blockSlot == null || blockSlot.check()) {
            return;
        }
        if (!SSRotationUtils.accepted(this.rotationPriority())) return;
        if (mc.player.isOnGround()) {
            posY = floor(mc.player.getPosY() - 1);
        }

        if (mc.gameSettings.keyBindJump.isKeyDown()) {
            posY = MathHelper.floor(mc.player.getPosY()) - 1;
        }
        SSBlockData possible = SSClientRayTraceUtil.isIgnoredBlock(mc.world.getBlockState(
                new BlockPos(
                        (int) floor(mc.player.getPosX()),
                        (int) floor(mc.player.getPosY()),
                        (int) floor(mc.player.getPosZ()))
        )) ?
                getBlockData(new BlockPos(
                        (int) floor(mc.player.getPosX()),
                        (int) posY,
                        (int) floor(mc.player.getPosZ()))
                ) : null;
        if (possible != null) {
            blockData = possible;
        }

        lastBlockData = possible;


        if (isMode(mode, "Normal")) {
            canPlace = true;
        } else if (isMode(mode, "Snap")) {
            canPlace = doesNotContainBlock(1);
        } else {
            canPlace = SSPlayerUtils.offGroundTicks >= placeTick.getCurrentValue().intValue();
            if (safeMode.getCurrentValue() && testOnGround.getCurrentValue() && !canPlace && mc.gameSettings.keyBindJump.isKeyDown()) {
                canPlace = SSPlayerUtils.onGroundTicks == 1;
            }
        }

        if (this.blockSlot.hand().equals(Hand.MAIN_HAND)) {
            mc.player.inventory.currentItem = this.blockSlot.slot();
        }
        SSFallingPlayer fallingPlayer = new SSFallingPlayer(mc.player); //玩家预测
        boolean reachable = true;
//                if (mc.player.getMotion().getY() < -0.1) {
        fallingPlayer.calculate(1);
        Vector3d nextEyePos = fallingPlayer.getEyePos();//1tick后的eyepos
        fallingPlayer.calculate(1); //再预测1tick
        SSBlockData placement = getBlockData(new BlockPos((int) floor(mc.player.getPosX()), (MathHelper.floor(mc.player.getPosY()) - 1), (int) floor(mc.player.getPosZ()))); //无samey搜寻方块
        boolean forceRotation = false;
        if (placement != null) {
            if (safeMode.getCurrentValue() && testOnGround.getCurrentValue() && SSPlayerUtils.onGroundTicks == 1 && mc.gameSettings.keyBindJump.isKeyDown()) {
                forceRotation = true;
            }
            double distance = nextEyePos.distanceTo(Vector3d.copyCentered(placement.pos()));
            if (distance >= safeDistance.getCurrentValue().doubleValue() || placement.pos().getY() > fallingPlayer.getY()) { // 这是大kb自救逻辑
                canPlace = true;
                reachable = false;
//                strict = true;
                blockData = lastBlockData = placement;
            }
        }
        if (blockData != null) {
            AxisAlignedBB base = new AxisAlignedBB(this.blockData.pos());
            AxisAlignedBB box = new AxisAlignedBB(
                    base.minX, this.blockData.pos().getY() - 1, base.minZ,
                    base.maxX, this.blockData.pos().getY() + 1, base.maxZ);
            if (blockData.pos().getY() > fallingPlayer.getY() && !box.contains(mc.player.getPositionVec())) { //!box.contains(...)这是防止碰撞箱冲突
                canPlace = true;
                reachable = false;
                posY = MathHelper.floor(mc.player.getPosY()) - 1;// 普通下落自救
                blockData = lastBlockData = getBlockData(new BlockPos((int) floor(mc.player.getPosX()), (int) floor(posY), (int) floor(mc.player.getPosZ())));
            }
        }
//                }
        if (!reachable && rotateCount < 8) {
            if (dbgV.getCurrentValue() && rotateCount == 1) {
                SSChatUtils.info("working");
            }
            SSMovementUtils.cancelMove();
            rotateCount++;
        } else {
            rotateCount = 0;
        }
        rot = getBRot(forceRotation);
        if (duplicateRotPlace.getCurrentValue() && rot != null) {
            rot.pitch -= SSRandomUtils.generateRandomFloat(0.001f, 0.003f);
            rot.yaw -= SSRandomUtils.generateRandomFloat(0.0001f, 0.0003f);
            do {
                rot.pitch -= SSRandomUtils.generateRandomFloat(0.001f, 0.003f); //drp dis
            } while (rot.pitch > 90f);
            if (rot.pitch < -90f) {
                rot.pitch = -90f;
            }
        }
        if (didHitBlockFace(blockData, rot)) {
            SSMovementUtils.resetMove();
            rotateCount = 0;
        }
        if (fixRotation.getCurrentValue()) {
            if (rot != null) {
                rot.fixedSensitivity((float) mc.gameSettings.mouseSensitivity);
            }
        }
        SSRotationUtils.setRotation(rot, rotationPriority(), SSMoveFixMode.Silent);
        if (blockData == null) return;
        if (mc.player.isSpectator()) {
            // Upstream: this.setEnable(false) — disable the whole BlockFly module here.
            this.access().setEnabled(false);
        }
        if (isMode(mode, "Telly")) {
            return;
        }
        if (waitingForEagleSneak) {
            tellyJumpTicks++;
            if (tellyJumpTicks == tellyEagleTick.getCurrentValue().intValue() && !mc.gameSettings.keyBindSneak.isKeyDown()) {
                mc.gameSettings.keyBindSneak.setPressed(true);
            }
            if (tellyJumpTicks == tellyEagleTick.getCurrentValue().intValue() + keepEagleSneakTick.getCurrentValue().intValue()) {
                mc.gameSettings.keyBindSneak.setPressed(false);
                waitingForEagleSneak = false;
                tellyJumpTicks = 0;
            }
        }
    }

    private static boolean didHitBlockFace(SSBlockData blockData, SSRotation rot) {
        return blockData == null || rot == null || !SSClientRayTraceUtil.didHitBlockFace(rot, blockData.pos(), blockData.facing(), true);
    }

    /** Upstream: onMoveInput(MovementInputEvent) — eagle sneak modulation. */
    @EventTarget
    public void onMovementInput(EventMoveInput event) {
        if (!this.isEnabled()) return;
        if (isMode(mode, "Telly") && eagle.getCurrentValue()) {
            event.sneaking = placeCount % 4 == 0;
        }
    }

    /** Upstream: onSlow(PlayerSlowdownEvent) — mutates the raw movement input directly. */
    @EventTarget
    public void onSlow(EventSlowDown event) {
        if (!this.isEnabled() || mc.player == null || mc.player.movementInput == null) return;
        if (SSPlayerUtils.onGroundTicks == 1 && testOnGround.getCurrentValue() && smoothed.getCurrentValue() && !noUptelly.getCurrentValue() && safeMode.getCurrentValue() && mc.gameSettings.keyBindJump.isKeyDown()) {
            mc.player.movementInput.moveForward *= 0.2f;
            mc.player.movementInput.moveStrafe *= 0.2f;
        }
    }

    public boolean doesNotContainBlock(int down) {
        return SSPlayerUtils.blockRelativeToPlayer(0, -down, 0).getDefaultState().isTransparent();
    }

    public static Vector3d getVec3(BlockPos pos, Direction face) {
        double x = (double) pos.getX() + 0.5;
        double y = (double) pos.getY() + 0.5;
        double z = (double) pos.getZ() + 0.5;
        if (face == Direction.UP || face == Direction.DOWN) {
            x += SSMathUtils.getRandomInRange(0.3, -0.3);
            z += SSMathUtils.getRandomInRange(0.3, -0.3);
        } else {
            y += SSMathUtils.getRandomInRange(0.3, -0.3);
        }
        if (face == Direction.WEST || face == Direction.EAST) {
            z += SSMathUtils.getRandomInRange(0.3, -0.3);
        }
        if (face == Direction.SOUTH || face == Direction.NORTH) {
            x += SSMathUtils.getRandomInRange(0.3, -0.3);
        }
        return new Vector3d(x, y, z);
    }

    public void getBlock(int switchSlot) {
        for (int i = 9; i < 45; ++i) {
            if (mc.player.inventory.getStackInSlot(i).isStackable()
                    && (mc.currentScreen == null)) {
                ItemStack is = mc.player.inventory.getStackInSlot(i);
                if (is.getItem() instanceof BlockItem block) {
                    if (isValid(block)) {
                        if (36 + switchSlot != i) {
                            SSInventoryUtil.swap(i, switchSlot);
                        }
                        break;
                    }
                }
            }
        }
    }

    public SSRotation getRotation(Vector3d vec, Vector3d eyes) {
        var diffX = vec.x - eyes.x;
        var diffY = vec.y - eyes.y;
        var diffZ = vec.z - eyes.z;

        return new SSRotation(
                MathHelper.wrapDegrees((float) toDegrees(atan2(diffZ, diffX)) - 90.0f),
                MathHelper.wrapDegrees(((float) -toDegrees(atan2(diffY, sqrt(diffX * diffX + diffZ * diffZ)))))
        );
    }

    private boolean isValid(final Item item) {
        return item instanceof BlockItem && !invalidBlocks.contains(((BlockItem) (item)).getBlock()) && SSSlotUtils.isGoodForBridging(item);
    }

    private int getHotbarBlockSlot() {
        if (isMode(blockSlotMode, "Most Blocks")) {
            return getMostBlocksHotbarSlot();
        }

        int slot = -1;
        for (int i = 0; i <= 8; i++) {
            ItemStack stack = mc.player.inventory.getStackInSlot(i);
            if (SSInventoryUtil.isFullBlock(stack) && isValid(stack.getItem())) {
                slot = i;
            }
        }
        return slot;
    }

    private int getMostBlocksHotbarSlot() {
        int selectedSlot = mc.player.inventory.currentItem;
        int bestSlot = -1;
        int bestCount = -1;

        ItemStack selectedStack = mc.player.inventory.getStackInSlot(selectedSlot);
        if (SSInventoryUtil.isFullBlock(selectedStack) && isValid(selectedStack.getItem())) {
            bestSlot = selectedSlot;
            bestCount = selectedStack.getCount();
        }

        for (int i = 0; i <= 8; i++) {
            ItemStack stack = mc.player.inventory.getStackInSlot(i);
            if (SSInventoryUtil.isFullBlock(stack) && isValid(stack.getItem()) && stack.getCount() > bestCount) {
                bestSlot = i;
                bestCount = stack.getCount();
            }
        }
        return bestSlot;
    }

    private ItemStack getHeldBlockStack() {
        if (mc.player == null) {
            return ItemStack.EMPTY;
        }
        ItemStack main = mc.player.getHeldItemMainhand();
        if (isDisplayBlock(main)) {
            return main;
        }
        ItemStack offhand = mc.player.getHeldItemOffhand();
        if (isDisplayBlock(offhand)) {
            return offhand;
        }
        return ItemStack.EMPTY;
    }

    private boolean isDisplayBlock(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        return isValid(stack.getItem());
    }

    private int getBlockCountColor(int count) {
        if (count < 16) {
            return new Color(255, 80, 80).getRGB();
        }
        if (count < 32) {
            return new Color(255, 220, 80).getRGB();
        }
        return Color.WHITE.getRGB();
    }

    private int getBlockCountColor_Black(int count) {
        if (count < 16) {
            return new Color(255, 80, 80).getRGB();
        }
        if (count < 32) {
            return new Color(255, 220, 80).getRGB();
        }
        return Color.BLACK.getRGB();
    }

    public int getBlockCountInventory() {
        int blockCount = 0;
        for (int i = 9; i < 45; ++i) {
            if (mc.player == null) return -1;
            if (mc.player.inventory.getStackInSlot(i).isStackable()) {
                ItemStack is = mc.player.inventory.getStackInSlot(i);
                if (is.getItem() instanceof BlockItem block) {
                    if (isValid(block)) {
                        blockCount += is.getCount();
                    }
                }
            }
        }
        return blockCount;
    }

    public int getBlockCountHotbar() {
        if (mc.player == null) return 0;
        int blockCount = 0;
        for (int i = 0; i <= 8; i++) {
            ItemStack stack = mc.player.inventory.getStackInSlot(i);
            if (!stack.isEmpty() && stack.getItem() instanceof BlockItem blockItem) {
                if (isValid(blockItem)) {
                    blockCount += stack.getCount();
                }
            }
        }

        ItemStack offhandStack = mc.player.getHeldItemOffhand();
        if (!offhandStack.isEmpty() && offhandStack.getItem() instanceof BlockItem offhandBlock) {
            if (isValid(offhandBlock)) {
                blockCount += offhandStack.getCount();
            }
        }

        return blockCount;
    }

    private SSBlockData getBlockData(BlockPos pos) {
        SSBlockData data;

        if (getPos(pos) == null) {
            BlockPos blockPos = getBlockPos();
            if (blockPos == null) return null;

            Direction direction = getPlaceSide(blockPos);
            if (direction == null) return null;

            data = new SSBlockData(blockPos, direction);
        } else {
            data = getPos(pos);
        }

        if (SSClientRayTraceUtil.isIgnoredBlock(mc.world.getBlockState(data.pos().offset(data.facing())))) {
            return data;
        }

        return null;
    }

    private Direction getPlaceSide(BlockPos blockPos) {
        List<SSBlockData> blockData = new ArrayList<>();

        BlockPos pos = new BlockPos((int) floor(mc.player.getPosX()), (int) floor(mc.player.getPosY()), (int) floor(mc.player.getPosZ()));

        if (isAirBlock(blockPos.east()) && !blockPos.east().equals(pos)) {
            blockData.add(new SSBlockData(blockPos.east(), Direction.EAST));
        }

        if (isAirBlock(blockPos.north()) && !blockPos.north().equals(pos)) {
            blockData.add(new SSBlockData(blockPos.north(), Direction.NORTH));
        }

        if (isAirBlock(blockPos.south()) && !blockPos.south().equals(pos)) {
            blockData.add(new SSBlockData(blockPos.south(), Direction.SOUTH));
        }

        if (isAirBlock(blockPos.west()) && !blockPos.west().equals(pos)) {
            blockData.add(new SSBlockData(blockPos.west(), Direction.WEST));
        }

        if (blockData.isEmpty()) return null;

        blockData.sort(Comparator.comparingDouble(vec3 -> vec3.pos().distanceSq(pos)));

        blockData.removeIf(blockData1 -> !SSClientRayTraceUtil.isIgnoredBlock(mc.world.getBlockState(blockData1.pos().offset(blockData1.facing()))));

        if (blockData.isEmpty()) return null;

        return blockData.get(0).facing();
    }


    private BlockPos getBlockPos() {
        BlockPos playerPos = new BlockPos((int) floor(mc.player.getPosX()), (int) floor(mc.player.getPosY()), (int) floor(mc.player.getPosZ()));

        ArrayList<BlockPos> positions = new ArrayList<>();

        Map<BlockPos, Block> searchBlock = searchBlocks(5);
        for (Map.Entry<BlockPos, Block> block : searchBlock.entrySet()) {
            if (isPosSolid(block.getKey())) {
                positions.add(block.getKey());
            }
        }

        positions.removeIf(pos -> pos.getY() >= playerPos.getY());

        if (positions.isEmpty()) return null;

        positions.sort(Comparator.comparingDouble(vec3 -> vec3.distanceSq(playerPos)));

        return positions.get(0);
    }

    public boolean isAirBlock(BlockPos blockPos) {
        return SSClientRayTraceUtil.isIgnoredBlock(mc.world.getBlockState(blockPos));
    }

    public float distanceTo(Entity entity, BlockPos pos) {
        float f = (float) (entity.getPosX() - pos.getX());
        float g = (float) (entity.getPosY() - pos.getY());
        float h = (float) (entity.getPosZ() - pos.getZ());
        return MathHelper.sqrt(f * f + g * g + h * h);
    }

    public Block getBlock(BlockPos pos) {
        return mc.world.getBlockState(pos).getBlock();
    }

    public Map<BlockPos, Block> searchBlocks(int radius) {
        Map<BlockPos, Block> blocks = new HashMap<>();
        PlayerEntity player = mc.player;
        if (player == null) {
            return blocks;
        }
        BlockPos base = player.getPosition();
        for (int x = radius; x >= -radius + 1; x--) {
            for (int y = radius; y >= -radius + 1; y--) {
                for (int z = radius; z >= -radius + 1; z--) {
                    BlockPos blockPos = new BlockPos(base.getX() + x, base.getY() + y, base.getZ() + z);
                    Block block = getBlock(blockPos);
                    if (block == null) {
                        continue;
                    }
                    blocks.put(blockPos, block);
                }
            }
        }
        return blocks;
    }

    public SSBlockData getPos(BlockPos pos) {
        if (isPosSolid(pos.add(-1, 0, 0))) {
            return new SSBlockData(pos.add(-1, 0, 0), Direction.EAST);
        } else if (isPosSolid(pos.add(1, 0, 0))) {
            return new SSBlockData(pos.add(1, 0, 0), Direction.WEST);
        } else if (isPosSolid(pos.add(0, 0, 1))) {
            return new SSBlockData(pos.add(0, 0, 1), Direction.NORTH);
        } else if (isPosSolid(pos.add(0, 0, -1))) {
            return new SSBlockData(pos.add(0, 0, -1), Direction.SOUTH);
        } else if (isPosSolid(pos.add(0, -1, 0))) {
            return new SSBlockData(pos.add(0, -1, 0), Direction.UP);
        }
        return null;
    }

    public boolean isPosSolid(BlockPos pos) {
        final Block block = mc.world.getBlockState(pos).getBlock();
        // Exclude interactable blocks — right-clicking them opens/closes instead of placing
        if (block instanceof TrapDoorBlock || block instanceof DoorBlock || block instanceof FenceGateBlock) {
            return false;
        }
        // 1.16.5 note: SHORT_GRASS -> GRASS; TORCHFLOWER and CHERRY_BUTTON do not exist and
        // were dropped from the upstream list.
        return !Arrays.asList(
                Blocks.ANVIL,
                Blocks.AIR,
                Blocks.WATER,
                Blocks.FIRE,
                Blocks.LAVA,
                Blocks.SKELETON_SKULL,
                Blocks.OAK_SIGN,
                Blocks.TRAPPED_CHEST,
                Blocks.CHEST,
                Blocks.ENCHANTING_TABLE,
                Blocks.ENDER_CHEST,
                Blocks.CRAFTING_TABLE,
                Blocks.DAYLIGHT_DETECTOR,
                Blocks.COBWEB,
                Blocks.GRASS,
                Blocks.FLOWER_POT,
                Blocks.CHORUS_FLOWER,
                Blocks.SUNFLOWER,
                Blocks.CORNFLOWER,
                Blocks.OAK_BUTTON,
                Blocks.ACACIA_BUTTON,
                Blocks.BIRCH_BUTTON,
                Blocks.CRIMSON_BUTTON,
                Blocks.DARK_OAK_BUTTON,
                Blocks.JUNGLE_BUTTON,
                Blocks.STONE_BUTTON,
                Blocks.WARPED_BUTTON,
                Blocks.SPRUCE_BUTTON,
                Blocks.NOTE_BLOCK,
                Blocks.PLAYER_HEAD
        ).contains(block) && !SSClientRayTraceUtil.isIgnoredBlock(mc.world.getBlockState(pos));
    }

    public void rotationAbuse(float step, float targetYaw) {
        double change = SSRotationUtils.yawDiffDirectly(SSRotationUtils.getLastRotation().yaw, targetYaw);
        int times = (int) (Math.abs(change) / step);
        float currentYaw = SSRotationUtils.getLastRotation().yaw;
        for (int i = 0; i < times; i++) {
            float smooth = SSRotationUtils.smooth((float) change, step);
            currentYaw += smooth;
            SSRotationUtils.rotation.yaw = currentYaw;
            mc.playerController.processRightClick(mc.player, mc.world, Hand.MAIN_HAND);
            SSPacketOrderManager.rightClicking = true;
        }
        SSRotationUtils.rotation.yaw = targetYaw;
    }

    record SlotData(int slot, Hand hand) {
        public boolean check() {
            if (mc.player == null) {
                return false;
            }

            if (hand.equals(Hand.OFF_HAND)) {
                ItemStack stack = mc.player.getHeldItemOffhand();
                return stack.isEmpty() || !(stack.getItem() instanceof BlockItem);
            }

            return mc.player.inventory.getStackInSlot(slot).isEmpty() || !(mc.player.inventory.getStackInSlot(slot).getItem() instanceof BlockItem);
        }
    }

    /** Upstream: @Override Module#rotationPriority() — plain method here (host Module has no such hook). */
    public int rotationPriority() {
        return SSPriority.Higher + 3;
    }

    private static boolean isMode(ModeSetting setting, String name) {
        return name.equals(setting.getCurrentValue());
    }
}
