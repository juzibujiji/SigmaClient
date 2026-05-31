package com.elfmcys.yesstevemodel.gui;

import com.elfmcys.yesstevemodel.YesSteveModel;
import com.elfmcys.yesstevemodel.capability.OpenYsmPlayerAnimationState;
import com.elfmcys.yesstevemodel.client.OpenYsmBakedPlayerModel;
import com.elfmcys.yesstevemodel.client.animation.ActionEntry;
import com.elfmcys.yesstevemodel.client.animation.ActionSource;
import com.elfmcys.yesstevemodel.network.OpenYsmNetwork;
import com.mojang.blaze3d.matrix.MatrixStack;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.util.text.StringTextComponent;

import java.util.ArrayList;
import java.util.List;

public class OpenYsmActionWheelScreen extends Screen {
    private static final int PANEL_WIDTH = 360;
    private static final int ROW_HEIGHT = 24;
    private static final int TOP = 58;
    private static final int BOTTOM_PADDING = 42;

    private final List<ActionEntry> actions = new ArrayList<>();
    private String modelId = "";
    private String status = "";
    private int scroll;

    public OpenYsmActionWheelScreen() {
        super(new StringTextComponent("YSM Actions"));
    }

    @Override
    protected void init() {
        this.reloadActions();
    }

    @Override
    public void render(MatrixStack matrices, int mouseX, int mouseY, float delta) {
        this.renderBackground(matrices);
        int left = Math.max(12, (this.width - PANEL_WIDTH) / 2);
        int right = Math.min(this.width - 12, left + PANEL_WIDTH);
        int bottom = Math.max(TOP + ROW_HEIGHT, this.height - BOTTOM_PADDING);
        int visibleRows = this.visibleRows();
        this.scroll = clamp(this.scroll, 0, this.maxScroll(visibleRows));

        drawCenteredString(matrices, this.font, "YSM Action Wheel", this.width / 2, 20, 0xFFFFFF);
        drawString(matrices, this.font, "Model: " + (this.modelId.isEmpty() ? "none" : this.modelId), left, 40, 0xD7E4EF);
        String hint = "R reload | S stop";
        drawString(matrices, this.font, hint, right - this.font.getStringWidth(hint), 40, 0x91A3B0);

        fill(matrices, left - 2, TOP - 2, right + 2, bottom + 2, 0x88000000);
        if (this.actions.isEmpty()) {
            String empty = this.status.isEmpty() ? "No extra/custom actions for this model" : this.status;
            drawCenteredString(matrices, this.font, empty, this.width / 2, TOP + 22, 0xD7E4EF);
            super.render(matrices, mouseX, mouseY, delta);
            return;
        }

        int hoverIndex = this.indexAt(mouseX, mouseY);
        for (int row = 0; row < visibleRows; row++) {
            int index = this.scroll + row;
            if (index >= this.actions.size()) {
                break;
            }
            ActionEntry action = this.actions.get(index);
            int y = TOP + row * ROW_HEIGHT;
            boolean hovered = index == hoverIndex;
            fill(matrices, left, y, right, y + ROW_HEIGHT - 2, hovered ? 0x664A6C86 : 0x44000000);
            String source = action.isGlobal() ? "global" : action.getSourceType().name().toLowerCase();
            String label = shorten(action.getDisplayName() + "  [" + source + "]", right - left - 16);
            drawString(matrices, this.font, label, left + 8, y + 7, 0xEAF3FF);
        }

        int stopTop = bottom + 6;
        fill(matrices, left, stopTop, right, stopTop + 20, 0xAA7B2632);
        drawCenteredString(matrices, this.font, "Stop Animation", (left + right) / 2, stopTop + 6, 0xFFFFFF);
        if (!this.status.isEmpty()) {
            drawString(matrices, this.font, shorten(this.status, right - left), left, stopTop + 24, 0xD7E4EF);
        }
        super.render(matrices, mouseX, mouseY, delta);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            if (this.isStopButton((int) mouseX, (int) mouseY)) {
                this.stop();
                return true;
            }
            int index = this.indexAt((int) mouseX, (int) mouseY);
            if (index >= 0 && index < this.actions.size()) {
                this.play(this.actions.get(index));
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        this.scroll += delta > 0.0D ? -1 : 1;
        this.scroll = clamp(this.scroll, 0, this.maxScroll(this.visibleRows()));
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 82) {
            this.reloadActions();
            return true;
        }
        if (keyCode == 83) {
            this.stop();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private void reloadActions() {
        this.actions.clear();
        this.modelId = "";
        if (!YesSteveModel.isAvailable() || this.minecraft == null) {
            this.status = "OpenYSM is not initialized";
            return;
        }
        OpenYsmBakedPlayerModel model = YesSteveModel.getSelectedPlayerModel(this.minecraft.getResourceManager());
        if (model == null) {
            this.status = "Select and enable a YSM model first";
            return;
        }
        this.modelId = model.getId();
        this.actions.addAll(model.getAnimations().listWheelActions());
        this.status = this.actions.isEmpty() ? "No extra/custom actions registered" : "Click an action to play it; press S to stop.";
        this.scroll = clamp(this.scroll, 0, this.maxScroll(this.visibleRows()));
    }

    private void play(ActionEntry action) {
        if (this.minecraft == null || this.minecraft.player == null) {
            return;
        }
        OpenYsmPlayerAnimationState.play(this.minecraft.player, this.modelId, action.getAnimationName(), ActionSource.GUI_ACTION);
        OpenYsmNetwork.sendPlayExtraAnimation(this.modelId, action.getAnimationName());
        this.status = "Playing: " + action.getDisplayName();
    }

    private void stop() {
        if (this.minecraft == null || this.minecraft.player == null) {
            return;
        }
        OpenYsmPlayerAnimationState.stop(this.minecraft.player);
        OpenYsmNetwork.sendStopExtraAnimation(this.modelId);
        this.status = "Stopped action";
    }

    private int visibleRows() {
        return Math.max(1, (this.height - TOP - BOTTOM_PADDING) / ROW_HEIGHT);
    }

    private int maxScroll(int visibleRows) {
        return Math.max(0, this.actions.size() - visibleRows);
    }

    private int indexAt(int mouseX, int mouseY) {
        int left = Math.max(12, (this.width - PANEL_WIDTH) / 2);
        int right = Math.min(this.width - 12, left + PANEL_WIDTH);
        int bottom = Math.max(TOP + ROW_HEIGHT, this.height - BOTTOM_PADDING);
        if (mouseX < left || mouseX > right || mouseY < TOP || mouseY >= bottom) {
            return -1;
        }
        int index = this.scroll + (mouseY - TOP) / ROW_HEIGHT;
        return index < this.actions.size() ? index : -1;
    }

    private boolean isStopButton(int mouseX, int mouseY) {
        int left = Math.max(12, (this.width - PANEL_WIDTH) / 2);
        int right = Math.min(this.width - 12, left + PANEL_WIDTH);
        int bottom = Math.max(TOP + ROW_HEIGHT, this.height - BOTTOM_PADDING);
        int stopTop = bottom + 6;
        return mouseX >= left && mouseX <= right && mouseY >= stopTop && mouseY <= stopTop + 20;
    }

    private String shorten(String text, int maxWidth) {
        String value = text == null ? "" : text.replace('\n', ' ').replace('\r', ' ');
        if (this.font.getStringWidth(value) <= maxWidth) {
            return value;
        }
        while (value.length() > 3 && this.font.getStringWidth(value + "...") > maxWidth) {
            value = value.substring(0, value.length() - 1);
        }
        return value + "...";
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
