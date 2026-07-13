package com.mentalfrostbyte.jello.module.impl.render;

import com.mentalfrostbyte.Client;
import com.mentalfrostbyte.jello.event.impl.player.EventHandAnimation;
import com.mentalfrostbyte.jello.module.Module;
import com.mentalfrostbyte.jello.module.data.ModuleCategory;
import com.mentalfrostbyte.jello.module.impl.combat.KillAura;
import com.mentalfrostbyte.jello.module.impl.combat.AutoClicker;
import com.mentalfrostbyte.jello.module.impl.player.OldHitting;
import com.mentalfrostbyte.jello.module.settings.impl.BooleanSetting;
import com.mentalfrostbyte.jello.module.settings.impl.ModeSetting;
import com.mentalfrostbyte.jello.module.settings.impl.NumberSetting;
import com.mojang.blaze3d.matrix.MatrixStack;
import net.minecraft.item.SwordItem;
import net.minecraft.util.HandSide;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.vector.Vector3f;
import team.sdhq.eventBus.annotations.EventTarget;

public class BlockAnimations extends Module {
    private static final String MODE = "Mode";
    private static final String ODD_SWING = "OddSwing";
    private static final String SWING_SPEED = "SwingSpeed";
    private static final String ITEM_SCALE = "ItemScale";
    private static final String HAND_X = "X";
    private static final String HAND_Y = "Y";
    private static final String HAND_ROTATION_X = "PositionRotationX";
    private static final String HAND_ROTATION_Y = "PositionRotationY";
    private static final String HAND_ROTATION_Z = "PositionRotationZ";
    private static final String ITEM_ROTATE = "ItemRotate";
    private static final String ITEM_ROTATION_MODE = "ItemRotateMode";
    private static final String ROTATE_SPEED = "RotateSpeed";

    private float itemRotation;

    public BlockAnimations() {
        super(ModuleCategory.RENDER, "BlockAnimations", "Customizes first-person sword blocking animations.");
        this.registerSetting(new ModeSetting(MODE, "Blocking animation mode.", 1,
                "OneSeven", "Pushdown", "NewPushdown", "Old", "Helium", "Argon", "Cesium", "Sulfur"));
        this.registerSetting(new BooleanSetting(ODD_SWING, "Removes the vanilla swing shove.", false));
        this.registerSetting(new NumberSetting<>(SWING_SPEED, "Sword swing animation speed.", 15, 0, 20, 1));
        this.registerSetting(new NumberSetting<>(ITEM_SCALE, "Forward item position offset.", 0, -5, 5, 0.1F));
        this.registerSetting(new NumberSetting<>(HAND_X, "Horizontal hand offset.", 0, -5, 5, 0.1F));
        this.registerSetting(new NumberSetting<>(HAND_Y, "Vertical hand offset.", 0, -5, 5, 0.1F));
        this.registerSetting(new NumberSetting<>(HAND_ROTATION_X, "Hand pitch offset.", 0, -50, 50, 1));
        this.registerSetting(new NumberSetting<>(HAND_ROTATION_Y, "Hand yaw offset.", 0, -50, 50, 1));
        this.registerSetting(new NumberSetting<>(HAND_ROTATION_Z, "Hand roll offset.", 0, -50, 50, 1));
        this.registerSetting(new BooleanSetting(ITEM_ROTATE, "Continuously rotates the blocking item.", false));
        this.registerSetting(new ModeSetting(ITEM_ROTATION_MODE, "Item rotation axis.", 0,
                "None", "Straight", "Forward", "Nano", "Uh"));
        this.registerSetting(new NumberSetting<>(ROTATE_SPEED, "Item rotation speed.", 8, 1, 15, 0.5F));
        this.setAvailableOnClassic(true);
    }

    public static int getSwingAnimationBase(int vanillaBase) {
        try {
            Module module = Client.getInstance().moduleManager.getModuleByClass(BlockAnimations.class);

            if (module instanceof BlockAnimations blockAnimations && blockAnimations.isEnabled()) {
                int swingSpeed = Math.round(blockAnimations.getNumberValueBySettingName(SWING_SPEED));
                int ticks = Math.round(2.0F + (20 - swingSpeed) * 0.9F);
                return Math.max(2, Math.min(20, ticks));
            }
        } catch (Throwable ignored) {
        }

        return vanillaBase;
    }

    @EventTarget
    public void onHandAnimation(EventHandAnimation event) {
        if (!this.isEnabled() || !event.method13926()) {
            return;
        }

        MatrixStack matrix = event.getMatrix();
        HandSide side = event.getHand();
        applyGlobalHandTransform(matrix, side);

        if (!(event.getItemStack().getItem() instanceof SwordItem)) {
            return;
        }

        if (shouldRenderBlocking()) {
            event.cancelled = true;
            applyBlockingAnimation(event.getSwingProgress(), event.method13925(), side, matrix);
        } else if (this.getBooleanValueFromSettingName(ODD_SWING)) {
            event.cancelled = true;
            transformFirstPersonItem(event.method13925(), event.getSwingProgress(), side, matrix);
            applyItemRotation(side, matrix);
        }
    }

    private boolean shouldRenderBlocking() {
        if (mc.player == null) {
            return false;
        }

        if (OldHitting.field23408 && mc.player.getHeldItemMainhand().getItem() instanceof SwordItem) {
            return true;
        }

        if (mc.gameSettings.keyBindUseItem.isKeyDown() && mc.player.getHeldItemMainhand().getItem() instanceof SwordItem) {
            return true;
        }

        Module autoClickerModule = Client.getInstance().moduleManager.getModuleByClass(AutoClicker.class);
        if (autoClickerModule instanceof AutoClicker autoClicker && autoClicker.isBlockHitBlocking()
                && mc.player.getHeldItemMainhand().getItem() instanceof SwordItem) {
            return true;
        }

        Module killAuraModule = Client.getInstance().moduleManager.getModuleByClass(KillAura.class);
        return killAuraModule != null
                && killAuraModule.isEnabled()
                && KillAura.targetEntity != null
                && "Fake".equals(killAuraModule.getStringSettingValueByName("Autoblock Mode"));
    }

    private void applyGlobalHandTransform(MatrixStack matrix, HandSide side) {
        int direction = direction(side);
        matrix.translate(
                this.getNumberValueBySettingName(HAND_X) * direction,
                this.getNumberValueBySettingName(HAND_Y),
                this.getNumberValueBySettingName(ITEM_SCALE));
        rotate(matrix, this.getNumberValueBySettingName(HAND_ROTATION_X), 1.0F, 0.0F, 0.0F, direction);
        rotate(matrix, this.getNumberValueBySettingName(HAND_ROTATION_Y), 0.0F, 1.0F, 0.0F, direction);
        rotate(matrix, this.getNumberValueBySettingName(HAND_ROTATION_Z), 0.0F, 0.0F, 1.0F, direction);
    }

    private void applyBlockingAnimation(float swingProgress, float equippedProgress, HandSide side, MatrixStack matrix) {
        switch (this.getStringSettingValueByName(MODE)) {
            case "OneSeven":
                doOneSeven(swingProgress, equippedProgress, side, matrix);
                break;
            case "Pushdown":
                doPushdown(swingProgress, equippedProgress, side, matrix);
                break;
            case "NewPushdown":
                doNewPushdown(swingProgress, equippedProgress, side, matrix);
                break;
            case "Old":
                doOld(swingProgress, equippedProgress, side, matrix);
                break;
            case "Helium":
                doHelium(swingProgress, equippedProgress, side, matrix);
                break;
            case "Argon":
                doArgon(swingProgress, equippedProgress, side, matrix);
                break;
            case "Cesium":
                doCesium(swingProgress, equippedProgress, side, matrix);
                break;
            case "Sulfur":
                doSulfur(swingProgress, equippedProgress, side, matrix);
                break;
            default:
                doPushdown(swingProgress, equippedProgress, side, matrix);
                break;
        }
    }

    private void doOneSeven(float swingProgress, float equippedProgress, HandSide side, MatrixStack matrix) {
        transformBlockingItem(equippedProgress, swingProgress, side, matrix,
                0.0F, 0.0F, 0.0F,
                72.0F, -78.0F, -10.0F);
        applyItemRotation(side, matrix);
    }

    private void doOld(float swingProgress, float equippedProgress, HandSide side, MatrixStack matrix) {
        transformBlockingItem(equippedProgress, swingProgress, side, matrix,
                0.0F, 0.0F, 0.0F,
                62.0F, -72.0F, -8.0F);
        applyItemRotation(side, matrix);
    }

    private void doPushdown(float swingProgress, float equippedProgress, HandSide side, MatrixStack matrix) {
        float swing = sinSqrt(swingProgress);
        transformBlockingItem(equippedProgress * 0.75F, swingProgress, side, matrix,
                0.0F, -swing * 0.12F, 0.0F,
                64.0F, -72.0F - swing * 14.0F, -8.0F - swing * 8.0F);
        applyItemRotation(side, matrix);
    }

    private void doNewPushdown(float swingProgress, float equippedProgress, HandSide side, MatrixStack matrix) {
        float swing = sinSqrt(swingProgress);
        transformBlockingItem(equippedProgress * 0.85F, swingProgress, side, matrix,
                0.02F, -swing * 0.10F, -0.01F,
                60.0F, -70.0F - swing * 16.0F, -6.0F - swing * 6.0F);
        applyItemRotation(side, matrix);
    }

    private void doHelium(float swingProgress, float equippedProgress, HandSide side, MatrixStack matrix) {
        float c0 = MathHelper.sin(swingProgress * equippedProgress * (float) Math.PI);
        float c1 = sinSqrt(swingProgress);
        transformBlockingItem(equippedProgress, swingProgress, side, matrix,
                0.0F, 0.02F, 0.0F,
                58.0F, -66.0F - c1 * 8.0F, -6.0F + c0 * 8.0F);
        applyItemRotation(side, matrix);
    }

    private void doArgon(float swingProgress, float equippedProgress, HandSide side, MatrixStack matrix) {
        float c2 = sinSqrt(swingProgress);
        float c3 = MathHelper.cos(MathHelper.sqrt(equippedProgress) * (float) Math.PI);
        transformBlockingItem(equippedProgress * 0.90F, swingProgress * 0.60F, side, matrix,
                0.01F, c2 * 0.04F, 0.0F,
                60.0F + c3 * 4.0F, -68.0F + c2 * 7.0F, -8.0F * c2);
        applyItemRotation(side, matrix);
    }

    private void doCesium(float swingProgress, float equippedProgress, HandSide side, MatrixStack matrix) {
        float c4 = sinSqrt(swingProgress);
        transformBlockingItem(equippedProgress, swingProgress, side, matrix,
                0.04F, 0.03F, 0.0F,
                52.0F - c4 * 6.0F, -62.0F - c4 * 8.0F, c4 * 10.0F);
        applyItemRotation(side, matrix);
    }

    private void doSulfur(float swingProgress, float equippedProgress, HandSide side, MatrixStack matrix) {
        float c5 = sinSqrt(swingProgress);
        float c6 = MathHelper.cos(MathHelper.sqrt(swingProgress) * (float) Math.PI);
        transformBlockingItem(equippedProgress, swingProgress, side, matrix,
                0.02F + c5 * 0.06F, 0.03F, 0.0F,
                58.0F, -66.0F - c5 * 10.0F, c6 * 8.0F);
        applyItemRotation(side, matrix);
    }

    private void transformFirstPersonItem(float equippedProgress, float swingProgress, HandSide side, MatrixStack matrix) {
        int direction = direction(side);
        translate(matrix, 0.56F, -0.52F + equippedProgress * -0.6F, -0.71999997F, direction);
        float swing = MathHelper.sin(swingProgress * swingProgress * (float) Math.PI);
        float swingRoot = sinSqrt(swingProgress);
        rotate(matrix, 45.0F + swing * -20.0F, 0.0F, 1.0F, 0.0F, direction);
        rotate(matrix, swingRoot * -20.0F, 0.0F, 0.0F, 1.0F, direction);
        rotate(matrix, swingRoot * -80.0F, 1.0F, 0.0F, 0.0F, direction);
        rotate(matrix, -45.0F, 0.0F, 1.0F, 0.0F, direction);
    }

    private void transformBlockingItem(float equippedProgress, float swingProgress, HandSide side, MatrixStack matrix,
                                       float x, float y, float z, float yaw, float pitch, float roll) {
        int direction = direction(side);
        float swing = MathHelper.sin(swingProgress * swingProgress * (float) Math.PI);
        float swingRoot = sinSqrt(swingProgress);

        translate(matrix, 0.56F + x, -0.52F + y + equippedProgress * -0.6F, -0.71999997F + z, direction);
        rotate(matrix, yaw + swing * -12.0F, 0.0F, 1.0F, 0.0F, direction);
        rotate(matrix, roll + swingRoot * -10.0F, 0.0F, 0.0F, 1.0F, direction);
        rotate(matrix, pitch + swingRoot * -14.0F, 1.0F, 0.0F, 0.0F, direction);
    }

    private void applyItemRotation(HandSide side, MatrixStack matrix) {
        if (!this.getBooleanValueFromSettingName(ITEM_ROTATE)) {
            return;
        }

        int direction = direction(side);
        String rotationMode = this.getStringSettingValueByName(ITEM_ROTATION_MODE);

        if ("Straight".equals(rotationMode)) {
            rotate(matrix, this.itemRotation, 0.0F, 1.0F, 0.0F, direction);
        } else if ("Forward".equals(rotationMode)) {
            rotate(matrix, this.itemRotation, 1.0F, 1.0F, 0.0F, direction);
        } else if ("Uh".equals(rotationMode)) {
            rotate(matrix, this.itemRotation, 1.0F, 0.0F, 1.0F, direction);
        } else {
            return;
        }

        this.itemRotation += this.getNumberValueBySettingName(ROTATE_SPEED);

        if (this.itemRotation > 360.0F) {
            this.itemRotation -= 360.0F;
        }
    }

    private void translate(MatrixStack matrix, float x, float y, float z, int direction) {
        matrix.translate((double) x * direction, y, z);
    }

    private void rotate(MatrixStack matrix, float angle, float x, float y, float z, int direction) {
        if (angle == 0.0F || x == 0.0F && y == 0.0F && z == 0.0F) {
            return;
        }

        matrix.rotate(new Vector3f(x, y * direction, z * direction).rotationDegrees(angle));
    }

    private float sinSqrt(float value) {
        return MathHelper.sin(MathHelper.sqrt(value) * (float) Math.PI);
    }

    private int direction(HandSide side) {
        return side == HandSide.RIGHT ? 1 : -1;
    }
}
