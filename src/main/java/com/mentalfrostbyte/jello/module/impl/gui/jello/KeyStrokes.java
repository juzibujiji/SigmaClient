package com.mentalfrostbyte.jello.module.impl.gui.jello;

import com.mentalfrostbyte.Client;
import com.mentalfrostbyte.jello.event.impl.game.action.EventKeyPress;
import com.mentalfrostbyte.jello.event.impl.game.render.EventRender2DOffset;
import com.mentalfrostbyte.jello.gui.base.animations.Animation;
import com.mentalfrostbyte.jello.module.Module;
import com.mentalfrostbyte.jello.module.data.ModuleCategory;
import com.mentalfrostbyte.jello.util.client.render.theme.ClientColors;
import com.mentalfrostbyte.jello.util.client.render.ResourceRegistry;
import com.mentalfrostbyte.jello.util.game.render.BlurEngine;
import com.mentalfrostbyte.jello.util.game.render.RenderUtil;
import com.mentalfrostbyte.jello.util.game.render.RenderUtil2;
import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;
import team.sdhq.eventBus.annotations.EventTarget;

import java.util.ArrayList;
import java.util.Iterator;

public class KeyStrokes extends Module {
    public int xBase = 10;
    public int yBase = 260;
    public ArrayList<KeyAnimationData> animations = new ArrayList<>();

    public KeyStrokes() {
        super(ModuleCategory.GUI, "KeyStrokes", "Shows what keybind you are pressing");
        this.setAvailableOnClassic(false);
    }

    public Keystroke getKeyStrokeForKey(int key) {
        Keystroke[] keystrokes = Keystroke.values();
        for (Keystroke keystroke : keystrokes) {
            assert keystroke.getKeyBinding() != null;
            if (key == keystroke.getKeyBinding().keyCode.getKeyCode()) {
                return keystroke;
            }
        }
        return null;
    }

    @EventTarget
    public void onRender(EventRender2DOffset event) {
        if (this.isEnabled() && mc.player != null) {
            if (!Minecraft.getInstance().gameSettings.showDebugInfo) {
                if (!Minecraft.getInstance().gameSettings.hideGUI) {
                    this.yBase = event.getYOffset();
                    if (Client.getInstance().guiManager.getGuiBlur()) {
                        for (Keystroke keystroke : Keystroke.values()) {
                            KeyPosition topLeftKey = keystroke.getTopLeftPosition();
                            KeyPosition bottomRightKey = keystroke.getBottomRightPosition();
                            RenderUtil.drawBlurredBackground(
                                    this.xBase + topLeftKey.x,
                                    this.yBase + topLeftKey.y,
                                    this.xBase + topLeftKey.x + bottomRightKey.x,
                                    this.yBase + topLeftKey.y + bottomRightKey.y
                            );
                            BlurEngine.drawBlur(this.xBase + topLeftKey.x, this.yBase + topLeftKey.y, bottomRightKey.x, bottomRightKey.y);
                            BlurEngine.endBlur();
                            RenderUtil.restoreScissor();
                        }
                    }

                    for (Keystroke keystroke : Keystroke.values()) {
                        KeyPosition topLeftKey = keystroke.getTopLeftPosition();
                        KeyPosition bottomRightKey = keystroke.getBottomRightPosition();
                        float topLeftOpacityMul = 1.0F;
                        float bottomRightOpacityMul = 1.0F;
                        if (Client.getInstance().guiManager.getGuiBlur()) {
                            bottomRightOpacityMul = 0.5F;
                            topLeftOpacityMul = 0.5F;
                        }

                        String keyName = RenderUtil.getKeyName(keystroke.bind.keyCode.getKeyCode());
                        if (keystroke.bind != mc.gameSettings.keyBindAttack) {
                            if (keystroke.bind == mc.gameSettings.keyBindUseItem) {
                                keyName = "R";
                            }
                        } else {
                            keyName = "L";
                        }

                        if (keystroke.getKeyBinding().isKeyDown()) {
                            RenderUtil.drawRoundedRect(
                                    (float) (this.xBase + topLeftKey.x),
                                    (float) (this.yBase + topLeftKey.y),
                                    (float) (this.xBase + topLeftKey.x + bottomRightKey.x),
                                    (float) (this.yBase + topLeftKey.y + bottomRightKey.y),
                                    RenderUtil2.applyAlpha(ClientColors.LIGHT_GREYISH_BLUE.getColor(), 0.5F * topLeftOpacityMul)
                            );
                        } else {
                            RenderUtil.drawRoundedRect(
                                    (float) (this.xBase + topLeftKey.x),
                                    (float) (this.yBase + topLeftKey.y),
                                    (float) (this.xBase + topLeftKey.x + bottomRightKey.x),
                                    (float) (this.yBase + topLeftKey.y + bottomRightKey.y),
                                    RenderUtil2.applyAlpha(ClientColors.DEEP_TEAL.getColor(), 0.5F * topLeftOpacityMul)
                            );
                        }

                        RenderUtil.drawRoundedRect(
                                (float) (this.xBase + topLeftKey.x),
                                (float) (this.yBase + topLeftKey.y),
                                (float) bottomRightKey.x,
                                (float) bottomRightKey.y,
                                10.0F,
                                0.75F * bottomRightOpacityMul
                        );
                        RenderUtil.drawString(
                                ResourceRegistry.JelloLightFont18,
                                (float) (this.xBase + topLeftKey.x + (bottomRightKey.x - ResourceRegistry.JelloLightFont18.getWidth(keyName)) / 2),
                                (float) (this.yBase + topLeftKey.y + 12),
                                keyName,
                                ClientColors.LIGHT_GREYISH_BLUE.getColor()
                        );
                    }

                    Iterator<KeyAnimationData> iter = this.animations.iterator();

                    while (iter.hasNext()) {
                        KeyAnimationData animationData = iter.next();
                        Keystroke keyStroke = animationData.keyStroke;
                        KeyPosition topLeftPosition = keyStroke.getTopLeftPosition();
                        KeyPosition bottomRightPosition = keyStroke.getBottomRightPosition();
                        RenderUtil.drawBlurredBackground(
                                this.xBase + topLeftPosition.x,
                                this.yBase + topLeftPosition.y,
                                this.xBase + topLeftPosition.x + bottomRightPosition.x,
                                this.yBase + topLeftPosition.y + bottomRightPosition.y
                        );
                        float maxAnimPercent = 0.7F;
                        int duplicates = 0;

                        for (KeyAnimationData animationData1 : this.animations) {
                            if (animationData1.keyStroke.equals(keyStroke)) {
                                duplicates++;
                            }
                        }

                        if (keyStroke.getKeyBinding().isKeyDown() && animationData.animation.calcPercent() >= maxAnimPercent && duplicates < 2) {
                            animationData.animation.updateStartTime(maxAnimPercent);
                        }

                        float animPercent = animationData.animation.calcPercent();
                        float alpha = (1.0F - animPercent * (0.5F + animPercent * 0.5F)) * 0.8F;
                        int color = RenderUtil2.applyAlpha(-5658199, alpha);
                        if (Client.getInstance().guiManager.getGuiBlur()) {
                            color = RenderUtil2.applyAlpha(-1, alpha);
                        }

                        RenderUtil.drawFilledArc(
                                (float) (this.xBase + topLeftPosition.x + bottomRightPosition.x / 2),
                                (float) (this.yBase + topLeftPosition.y + bottomRightPosition.y / 2),
                                (float) (bottomRightPosition.x - 4) * animPercent + 4.0F,
                                color
                        );
                        RenderUtil.restoreScissor();
                        if (animationData.animation.calcPercent() == 1.0F) {
                            iter.remove();
                        }
                    }

                    event.addOffset(160);
                }
            }
        }
    }

    @EventTarget
    public void onKeyPress(EventKeyPress event) {
        if (this.isEnabled() && mc.player != null) {
            if (this.getKeyStrokeForKey(event.getKey()) != null && !event.isPressed()) {
                this.animations.add(new KeyAnimationData(this.getKeyStrokeForKey(event.getKey())));
            }
        }
    }

    public enum Keystroke {
        Left(0.0F, 1.0F, mc.gameSettings.keyBindLeft),
        Right(2.0F, 1.0F, mc.gameSettings.keyBindRight),
        Forward(1.0F, 0.0F, mc.gameSettings.keyBindForward),
        Back(1.0F, 1.0F, mc.gameSettings.keyBindBack),
        Attack(0.0F, 2.0F, 74, mc.gameSettings.keyBindAttack),
        UseItem(1.02F, 2.0F, 73, mc.gameSettings.keyBindUseItem);

        public final float positionX;
        public final float positionY;
        public int width = 48;
        public int height = 48;
        public int padding = 3;
        public final KeyBinding bind;

        Keystroke(float positionX, float positionY, KeyBinding bind) {
            this.positionX = positionX;
            this.positionY = positionY;
            this.bind = bind;
        }

        Keystroke(float positionX, float positionY, int width, KeyBinding bind) {
            this.positionX = positionX;
            this.positionY = positionY;
            this.bind = bind;
            this.width = width;
        }

        public KeyPosition getTopLeftPosition() {
            return new KeyPosition(
                    this, (int) (this.positionX * (float) (this.width + this.padding)), (int) (this.positionY * (float) (this.height + this.padding))
            );
        }

        public KeyPosition getBottomRightPosition() {
            return new KeyPosition(this, this.width, this.height);
        }

        public KeyBinding getKeyBinding() {
            return switch (this) {
                case Left -> mc.gameSettings.keyBindLeft;
                case Right -> mc.gameSettings.keyBindRight;
                case Forward -> mc.gameSettings.keyBindForward;
                case Back -> mc.gameSettings.keyBindBack;
                case Attack -> mc.gameSettings.keyBindAttack;
                case UseItem -> mc.gameSettings.keyBindUseItem;
            };
        }
    }

    public static class KeyAnimationData {
        public Keystroke keyStroke;
        public Animation animation;

        public KeyAnimationData(Keystroke keyStroke) {
            this.animation = new Animation(300, 0);
            this.keyStroke = keyStroke;
        }
    }

    public static class KeyPosition {
        public int x;
        public int y;
        public final Keystroke keystroke;

        public KeyPosition(Keystroke keystroke, int x, int y) {
            this.keystroke = keystroke;
            this.x = x;
            this.y = y;
        }
    }
}
