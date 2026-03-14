package com.mentalfrostbyte.jello.gui.combined.holders;

import com.mentalfrostbyte.Client;
import com.mentalfrostbyte.jello.gui.combined.impl.SwitchScreen;
import com.mentalfrostbyte.jello.util.client.render.Resources;
import com.mentalfrostbyte.jello.util.game.render.RenderUtil;
import com.mentalfrostbyte.jello.util.game.render.RenderUtil2;
import com.mentalfrostbyte.jello.util.client.render.theme.ClientColors;
import com.mojang.blaze3d.matrix.MatrixStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screen.MainMenuHolder;
import net.minecraft.client.gui.screen.OptionsScreen;
import net.minecraft.client.gui.screen.WorldSelectionScreen;
import net.minecraft.util.SharedConstants;
import org.lwjgl.opengl.GL11;
import org.newdawn.slick.opengl.Texture;

import java.util.Map;

public class MGWTHolder extends MainMenuHolder {

    // --- Constants matching Manosaba layout ---
    private static final float BUTTON_SCALE = 0.375f;
    private static final float LOGO_SCALE = 0.5f;
    private static final float IMAGE_SCALE = 1.3f;

    private static final long BG_ANIM_DURATION = 2500L; // ms
    private static final long UI_FADE_DELAY = 250L; // ms after bg anim
    private static final long UI_FADE_DURATION = 500L; // ms

    // --- Animation state ---
    private long startTime = -1;

    // --- Exit dialog state ---
    private boolean showExitDialog = false;
    private boolean exitDialogHoverCancel = false;
    private boolean exitDialogHoverConfirm = false;

    // --- Button hover states ---
    private final boolean[] buttonHovered = new boolean[5];

    // Button definitions: name suffix, Y-offset
    private static final String[] BUTTON_NAMES = {
            "LoadGame", "NewGame", "Gallery", "Options", "Exit"
    };
    private static final int[] BUTTON_Y_OFFSETS = { 20, -20, 20, -20, 20 };

    // --- Easing ---
    private static float easeOutCubic(float t) {
        t = Math.max(0f, Math.min(1f, t));
        float t1 = 1f - t;
        return 1f - t1 * t1 * t1;
    }

    @Override
    public void render(MatrixStack matrices, int mouseX, int mouseY, float delta) {
        // Initialize start time on first render
        if (startTime < 0) {
            startTime = System.currentTimeMillis();
        }

        long elapsed = System.currentTimeMillis() - startTime;

        // --- Calculate animation progress ---
        float bgRaw = Math.min(1f, (float) elapsed / BG_ANIM_DURATION);
        float bgProgress = easeOutCubic(bgRaw);

        long uiFadeStart = BG_ANIM_DURATION + UI_FADE_DELAY;
        float uiRaw = Math.max(0f, Math.min(1f, (float) (elapsed - uiFadeStart) / UI_FADE_DURATION));
        float uiAlpha = easeOutCubic(uiRaw);

        // --- Background with zoom animation ---
        float bgScale = 1.1f - (0.1f * bgProgress);
        float bgAlpha = bgProgress; // fade in with progress

        Texture bgTex = Resources.getMgwtBackgroundPNG();
        if (bgTex != null) {
            GL11.glPushMatrix();
            // Scale from center of screen
            float cx = this.width / 2f;
            float cy = this.height / 2f;
            GL11.glTranslatef(cx, cy, 0);
            GL11.glScalef(bgScale, bgScale, 1f);
            GL11.glTranslatef(-cx, -cy, 0);

            RenderUtil.drawImage(0, 0, this.width, this.height, bgTex, bgAlpha);

            GL11.glPopMatrix();
        }

        // --- Title Overlay (full-screen, fades in after bg) ---
        Map<String, Texture> titleSprites = Resources.getMgwtTitleSprites();
        if (titleSprites != null) {
            Texture overlayTex = titleSprites.get("TitleOverlay");
            if (overlayTex != null && uiAlpha > 0.01f) {
                RenderUtil.drawImage(0, 0, this.width, this.height, overlayTex, uiAlpha);
            }

            // --- Logo at top-right ---
            Texture logoTex = titleSprites.get("TitleLogo@Ja");
            if (logoTex != null && uiAlpha > 0.01f) {
                float logoW = logoTex.getImageWidth() * LOGO_SCALE;
                float logoH = logoTex.getImageHeight() * LOGO_SCALE;
                float logoX = this.width - logoW - 24;
                float logoY = 24;
                RenderUtil.drawImage(logoX, logoY, logoW, logoH, logoTex, uiAlpha);
            }

            // --- Sprite Buttons (bottom-left, horizontal row) ---
            if (uiAlpha > 0.01f) {
                drawSpriteButtons(titleSprites, mouseX, mouseY, uiAlpha);
            }
        }

        // --- Version text (bottom-right) ---
        if (uiAlpha > 0.01f) {
            String versionText = "Ver. " + SharedConstants.getVersion().getName();
            int textColor = RenderUtil2.applyAlpha(0xFFFFFFFF, uiAlpha);
            int textWidth = this.font.getStringWidth(versionText);
            drawString(matrices, this.font, versionText,
                    this.width - textWidth - 48, this.height - 24 - 9, textColor);
        }

        // --- Exit Dialog ---
        if (showExitDialog) {
            drawExitDialog(matrices, mouseX, mouseY);
        }
    }

    private void drawSpriteButtons(Map<String, Texture> sprites, int mouseX, int mouseY, float alpha) {
        float startX = 24;
        float baseY = this.height - 24; // bottom padding

        float xCursor = startX;

        for (int i = 0; i < BUTTON_NAMES.length; i++) {
            String name = BUTTON_NAMES[i];
            int yOffset = BUTTON_Y_OFFSETS[i];

            Texture normalTex = sprites.get("Button_" + name + "_Normal");
            Texture highlightTex = sprites.get("Button_" + name + "_Highlighted");

            if (normalTex == null)
                continue;

            // Button area dimensions
            float btnW = normalTex.getImageWidth() * BUTTON_SCALE;
            float btnH = normalTex.getImageHeight() * BUTTON_SCALE;
            float btnX = xCursor;
            float btnY = baseY - btnH + yOffset;

            // Hit detection
            buttonHovered[i] = mouseX >= btnX && mouseX <= btnX + btnW
                    && mouseY >= btnY && mouseY <= btnY + btnH;

            // Choose sprite
            Texture tex = (buttonHovered[i] && highlightTex != null) ? highlightTex : normalTex;

            // Draw the sprite at BUTTON_SCALE container, with IMAGE_SCALE on the image
            // itself
            float imgW = tex.getImageWidth() * BUTTON_SCALE * IMAGE_SCALE;
            float imgH = tex.getImageHeight() * BUTTON_SCALE * IMAGE_SCALE;
            float imgX = btnX + (btnW - imgW) / 2f;
            float imgY = btnY + (btnH - imgH) / 2f;

            RenderUtil.drawImage(imgX, imgY, imgW, imgH, tex, alpha);

            xCursor += btnW;
        }
    }

    private void drawExitDialog(MatrixStack matrices, int mouseX, int mouseY) {
        // Semi-transparent dark overlay
        RenderUtil.drawRect(0, 0, this.width, this.height,
                RenderUtil2.applyAlpha(0xFF000000, 0.5f));

        Map<String, Texture> dialogSprites = Resources.getMgwtDialogSprites();
        Map<String, Texture> commonSprites = Resources.getMgwtCommonSprites();

        // Dialog dimensions: center of screen, 50% height
        float dialogW = this.width;
        float dialogH = this.height * 0.5f;
        float dialogX = 0;
        float dialogY = (this.height - dialogH) / 2f;

        // Dialog base
        if (dialogSprites != null) {
            Texture dialogBase = dialogSprites.get("DialogBase");
            if (dialogBase != null) {
                RenderUtil.drawImage(dialogX, dialogY, dialogW, dialogH, dialogBase);
            }

            // Top frame
            Texture topFrame = dialogSprites.get("TopFrame");
            if (topFrame != null) {
                float frameH = topFrame.getImageHeight() * (dialogW / topFrame.getImageWidth());
                RenderUtil.drawImage(dialogX, dialogY, dialogW, frameH, topFrame);
            }

            // Bottom frame
            Texture bottomFrame = dialogSprites.get("BottomFrame");
            if (bottomFrame != null) {
                float frameH = bottomFrame.getImageHeight() * (dialogW / bottomFrame.getImageWidth());
                RenderUtil.drawImage(dialogX, dialogY + dialogH - frameH, dialogW, frameH, bottomFrame);
            }
        }

        // --- Dialog text: "即将结束游戏。" ---
        String dialogText = "\u5373\u5c06\u7ed3\u675f\u6e38\u620f\u3002";
        int textW = this.font.getStringWidth(dialogText);
        int textX = (this.width - textW) / 2;
        int textY = (int) (dialogY + dialogH / 2f - 30);
        drawString(matrices, this.font, dialogText, textX, textY, 0xFF332B2B);

        // --- Dialog buttons ---
        if (commonSprites != null) {
            Texture btnDefault = commonSprites.get("ButtonBase_Default");
            Texture btnHighlighted = commonSprites.get("ButtonBase_Highlighted");

            if (btnDefault != null) {
                float btnScale = 0.5f;
                float btnW = btnDefault.getImageWidth() * btnScale;
                float btnH = btnDefault.getImageHeight() * btnScale;
                float spacing = 40;
                float totalW = btnW * 2 + spacing;
                float cancelX = (this.width - totalW) / 2f;
                float confirmX = cancelX + btnW + spacing;
                float btnY = (int) (dialogY + dialogH / 2f + 10);

                // Cancel button
                exitDialogHoverCancel = mouseX >= cancelX && mouseX <= cancelX + btnW
                        && mouseY >= btnY && mouseY <= btnY + btnH;
                Texture cancelTex = (exitDialogHoverCancel && btnHighlighted != null) ? btnHighlighted : btnDefault;
                RenderUtil.drawImage(cancelX, btnY, btnW, btnH, cancelTex);

                // Cancel text
                String cancelText = "\u53d6\u6d88";
                int cancelTextW = this.font.getStringWidth(cancelText);
                drawString(matrices, this.font, cancelText,
                        (int) (cancelX + (btnW - cancelTextW) / 2),
                        (int) (btnY + (btnH - 8) / 2),
                        0xFFD1BDB7);

                // Confirm button
                exitDialogHoverConfirm = mouseX >= confirmX && mouseX <= confirmX + btnW
                        && mouseY >= btnY && mouseY <= btnY + btnH;
                Texture confirmTex = (exitDialogHoverConfirm && btnHighlighted != null) ? btnHighlighted : btnDefault;
                RenderUtil.drawImage(confirmX, btnY, btnW, btnH, confirmTex);

                // Confirm text "结束" with mixed colors
                String confirmText = "\u7ed3\u675f";
                int confirmTextW = this.font.getStringWidth(confirmText);
                drawString(matrices, this.font, confirmText,
                        (int) (confirmX + (btnW - confirmTextW) / 2),
                        (int) (btnY + (btnH - 8) / 2),
                        0xFFF7879B);
            }
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int mouseButton) {
        if (mouseButton != 0) {
            return super.mouseClicked(mouseX, mouseY, mouseButton);
        }

        // --- Exit dialog buttons ---
        if (showExitDialog) {
            if (exitDialogHoverCancel) {
                showExitDialog = false;
                return true;
            }
            if (exitDialogHoverConfirm) {
                Minecraft.getInstance().shutdown();
                return true;
            }
            // Clicking outside the buttons on the dialog overlay does nothing (consumes
            // click)
            return true;
        }

        // --- Main screen buttons ---
        Map<String, Texture> titleSprites = Resources.getMgwtTitleSprites();
        if (titleSprites == null)
            return super.mouseClicked(mouseX, mouseY, mouseButton);

        float startX = 24;
        float baseY = this.height - 24;

        float xCursor = startX;
        for (int i = 0; i < BUTTON_NAMES.length; i++) {
            Texture normalTex = titleSprites.get("Button_" + BUTTON_NAMES[i] + "_Normal");
            if (normalTex == null)
                continue;

            float btnW = normalTex.getImageWidth() * BUTTON_SCALE;
            float btnH = normalTex.getImageHeight() * BUTTON_SCALE;
            float btnX = xCursor;
            float btnY = baseY - btnH + BUTTON_Y_OFFSETS[i];

            if (mouseX >= btnX && mouseX <= btnX + btnW
                    && mouseY >= btnY && mouseY <= btnY + btnH) {
                handleButtonClick(i);
                return true;
            }

            xCursor += btnW;
        }

        return super.mouseClicked(mouseX, mouseY, mouseButton);
    }

    private void handleButtonClick(int index) {
        Minecraft mc = Minecraft.getInstance();
        switch (index) {
            case 0: // LoadGame -> Singleplayer
                mc.displayGuiScreen(new WorldSelectionScreen(this));
                break;
            case 1: // NewGame -> Multiplayer
                mc.displayGuiScreen(
                        new com.mentalfrostbyte.jello.gui.impl.jello.viamcp.JelloPortalScreen(this));
                break;
            case 2: // Gallery -> Switch
                Client.getInstance().setupClient(
                        com.mentalfrostbyte.jello.util.client.ClientMode.INDETERMINATE);
                Client.getInstance().isMGWT = false;
                mc.displayGuiScreen(new MainMenuHolder());
                break;
            case 3: // Options
                mc.displayGuiScreen(new OptionsScreen(this, mc.gameSettings));
                break;
            case 4: // Exit
                showExitDialog = true;
                break;
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
