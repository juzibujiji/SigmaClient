package com.elfmcys.yesstevemodel.gui;

import com.elfmcys.yesstevemodel.OpenYsmClientConfig;
import com.elfmcys.yesstevemodel.YesSteveModel;
import com.mojang.blaze3d.matrix.MatrixStack;
import net.minecraft.client.gui.AbstractGui;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.inventory.InventoryScreen;
import net.minecraft.client.gui.widget.button.Button;
import net.minecraft.util.text.StringTextComponent;

public final class OpenYsmExtraPlayerRenderScreen extends Screen {
    private int playerX;
    private int playerY;
    private float scale;
    private float yawOffset;
    private boolean draggingPosition;
    private boolean draggingScale;

    public OpenYsmExtraPlayerRenderScreen() {
        super(new StringTextComponent("YSM Extra Player Render"));
        OpenYsmClientConfig config = YesSteveModel.getClientConfig();
        this.playerX = config.getExtraPlayerX();
        this.playerY = config.getExtraPlayerY();
        this.scale = config.getExtraPlayerScale();
        this.yawOffset = config.getExtraPlayerYawOffset();
    }

    @Override
    protected void init() {
        int center = this.width / 2;
        this.addButton(new Button(center - 105, this.height - 28, 100, 20,
                new StringTextComponent(YesSteveModel.getClientConfig().isExtraPlayerRender() ? "Overlay: ON" : "Overlay: OFF"),
                button -> {
                    boolean enabled = !YesSteveModel.getClientConfig().isExtraPlayerRender();
                    YesSteveModel.setExtraPlayerRender(enabled);
                    button.setMessage(new StringTextComponent(enabled ? "Overlay: ON" : "Overlay: OFF"));
                }));
        this.addButton(new Button(center + 5, this.height - 28, 100, 20, new StringTextComponent("Reset"),
                button -> {
                    this.playerX = 10;
                    this.playerY = 10;
                    this.scale = 40.0F;
                    this.yawOffset = 5.0F;
                }));
    }

    @Override
    public void render(MatrixStack matrices, int mouseX, int mouseY, float delta) {
        this.renderBackground(matrices);
        int boxRight = (int)(this.playerX + this.scale);
        int boxBottom = (int)(this.playerY + this.scale * 2.0F);
        AbstractGui.fill(matrices, this.playerX, this.playerY, boxRight, boxBottom, 0x33177DDC);
        AbstractGui.fill(matrices, this.playerX - 4, this.playerY - 4, this.playerX + 4, this.playerY + 4, 0xFF00FFFF);
        AbstractGui.fill(matrices, boxRight - 4, boxBottom - 4, boxRight + 4, boxBottom + 4, 0xFF4477FF);
        drawCenteredString(matrices, this.font, "YSM Extra Player Render", this.width / 2, 12, 0xFFFFFF);
        drawString(matrices, this.font, "Left drag cyan handle: position", 12, 28, 0xDDDDDD);
        drawString(matrices, this.font, "Right drag / blue handle: scale", 12, 40, 0xDDDDDD);
        drawString(matrices, this.font, "Drag empty area horizontally: yaw", 12, 52, 0xDDDDDD);
        if (this.minecraft != null && this.minecraft.player != null) {
            InventoryScreen.drawEntityOnScreen(this.playerX, this.playerY + (int)(this.scale * 2.0F),
                    (int)this.scale, this.yawOffset, 0.0F, this.minecraft.player);
        }
        super.render(matrices, mouseX, mouseY, delta);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int scaleX = (int)(this.playerX + this.scale);
        int scaleY = (int)(this.playerY + this.scale * 2.0F);
        if (button == 0 && near(mouseX, mouseY, this.playerX, this.playerY)) {
            this.draggingPosition = true;
            return true;
        }
        if ((button == 1 || button == 0) && near(mouseX, mouseY, scaleX, scaleY)) {
            this.draggingScale = true;
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        this.draggingPosition = false;
        this.draggingScale = false;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (this.draggingPosition) {
            this.playerX = Math.max(0, (int)mouseX);
            this.playerY = Math.max(0, (int)mouseY);
            return true;
        }
        if (this.draggingScale || button == 1) {
            this.scale = Math.max(8.0F, Math.min(360.0F, (float)Math.min(mouseX - this.playerX, (mouseY - this.playerY) / 2.0D)));
            return true;
        }
        this.yawOffset += (float)dragX * 2.0F;
        return true;
    }

    @Override
    public void onClose() {
        YesSteveModel.setExtraPlayerTransform(this.playerX, this.playerY, this.scale, this.yawOffset);
        super.onClose();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private static boolean near(double mouseX, double mouseY, int x, int y) {
        return mouseX >= x - 8 && mouseX <= x + 8 && mouseY >= y - 8 && mouseY <= y + 8;
    }
}
