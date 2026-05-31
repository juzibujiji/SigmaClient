package com.elfmcys.yesstevemodel.gui;

import com.elfmcys.yesstevemodel.OpenYsmModelEntry;
import com.elfmcys.yesstevemodel.YesSteveModel;
import com.mojang.blaze3d.matrix.MatrixStack;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.util.text.StringTextComponent;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public class OpenYsmModelSelectionScreen extends Screen {
    private static final int LIST_WIDTH = 520;
    private static final int ROW_HEIGHT = 28;
    private static final int TOP = 62;
    private static final int BOTTOM_PADDING = 34;

    private final List<OpenYsmModelEntry> entries = new ArrayList<>();
    private int scroll;
    private String status = "";

    public OpenYsmModelSelectionScreen() {
        super(new StringTextComponent("YSM Models"));
    }

    @Override
    protected void init() {
        this.reloadEntries();
    }

    @Override
    public void render(MatrixStack matrices, int mouseX, int mouseY, float delta) {
        this.renderBackground(matrices);

        int left = Math.max(12, (this.width - LIST_WIDTH) / 2);
        int right = Math.min(this.width - 12, left + LIST_WIDTH);
        int bottom = Math.max(TOP + ROW_HEIGHT, this.height - BOTTOM_PADDING);
        int rowWidth = right - left;
        int visibleRows = this.visibleRows();
        this.scroll = clamp(this.scroll, 0, this.maxScroll(visibleRows));

        drawCenteredString(matrices, this.font, "YSM Models", this.width / 2, 20, 0xFFFFFF);
        drawString(matrices, this.font, "Selected: " + this.selectedModelText(), left, 42, 0xD7E4EF);

        String hint = "R reload | A actions";
        drawString(matrices, this.font, hint, right - this.font.getStringWidth(hint), 42, 0x91A3B0);

        fill(matrices, left - 2, TOP - 2, right + 2, bottom + 2, 0x88000000);

        if (!YesSteveModel.isAvailable()) {
            drawCenteredString(matrices, this.font, "OpenYSM not initialized", this.width / 2, TOP + 22, 0xFF7777);
            super.render(matrices, mouseX, mouseY, delta);
            return;
        }

        if (this.entries.isEmpty()) {
            String emptyText = this.status.isEmpty() ? "No YSM models indexed" : this.status;
            drawCenteredString(matrices, this.font, emptyText, this.width / 2, TOP + 22, 0xD7E4EF);
            super.render(matrices, mouseX, mouseY, delta);
            return;
        }

        int hoverIndex = this.indexAt(mouseX, mouseY);
        String selectedId = YesSteveModel.getClientConfig().getSelectedModelId();

        for (int row = 0; row < visibleRows; row++) {
            int index = this.scroll + row;
            if (index >= this.entries.size()) {
                break;
            }

            OpenYsmModelEntry entry = this.entries.get(index);
            int y = TOP + row * ROW_HEIGHT;
            boolean hovered = index == hoverIndex;
            boolean selected = entry.getId().equals(selectedId);
            int background = selected ? 0xAA2EA44F : hovered ? 0x663A4A5F : 0x44000000;
            fill(matrices, left, y, right, y + ROW_HEIGHT - 2, background);

            String title = this.shorten(this.sourceName(entry) + " | " + this.safeText(entry.getName()), rowWidth - 16);
            String id = this.shorten(entry.getId(), rowWidth - 16);
            drawString(matrices, this.font, title, left + 8, y + 4, selected ? 0xFFFFFF : 0xD7E4EF);
            drawString(matrices, this.font, id, left + 8, y + 16, 0x91A3B0);
        }

        if (!this.status.isEmpty()) {
            drawString(matrices, this.font, this.shorten(this.status, rowWidth), left, bottom + 8, 0xD7E4EF);
        }

        if (this.entries.size() > visibleRows) {
            int last = Math.min(this.entries.size(), this.scroll + visibleRows);
            String page = (this.scroll + 1) + "-" + last + "/" + this.entries.size();
            drawString(matrices, this.font, page, right - this.font.getStringWidth(page), bottom + 8, 0x91A3B0);
        }

        super.render(matrices, mouseX, mouseY, delta);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            int index = this.indexAt((int) mouseX, (int) mouseY);
            if (index >= 0 && index < this.entries.size()) {
                this.selectEntry(this.entries.get(index));
                return true;
            }
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (delta > 0.0D) {
            this.scroll--;
        } else if (delta < 0.0D) {
            this.scroll++;
        }

        this.scroll = clamp(this.scroll, 0, this.maxScroll(this.visibleRows()));
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 82) {
            this.reloadEntries();
            return true;
        }

        if (keyCode == 65) {
            this.minecraft.displayGuiScreen(new OpenYsmActionWheelScreen());
            return true;
        }

        if (keyCode == 265) {
            this.scroll = clamp(this.scroll - 1, 0, this.maxScroll(this.visibleRows()));
            return true;
        }

        if (keyCode == 264) {
            this.scroll = clamp(this.scroll + 1, 0, this.maxScroll(this.visibleRows()));
            return true;
        }

        if (keyCode == 266) {
            this.scroll = clamp(this.scroll - this.visibleRows(), 0, this.maxScroll(this.visibleRows()));
            return true;
        }

        if (keyCode == 267) {
            this.scroll = clamp(this.scroll + this.visibleRows(), 0, this.maxScroll(this.visibleRows()));
            return true;
        }

        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private void reloadEntries() {
        this.entries.clear();

        if (!YesSteveModel.isAvailable()) {
            this.status = "OpenYSM not initialized";
            this.scroll = 0;
            return;
        }

        try {
            YesSteveModel.reload(this.minecraft.getResourceManager());
            this.entries.addAll(YesSteveModel.getModelIndex().getEntries());
            this.entries.sort(Comparator
                .comparing((OpenYsmModelEntry entry) -> this.safeText(entry.getName()).toLowerCase(Locale.ROOT))
                .thenComparing(OpenYsmModelEntry::getId));
            this.status = this.entries.isEmpty() ? "No YSM models indexed" : "";
        } catch (RuntimeException e) {
            this.status = "Reload failed: " + this.safeText(e.getMessage());
        }

        this.scroll = clamp(this.scroll, 0, this.maxScroll(this.visibleRows()));
    }

    private void selectEntry(OpenYsmModelEntry entry) {
        if (YesSteveModel.selectModel(entry.getId())) {
            YesSteveModel.setRenderPlayers(true);
            this.status = "Selected: " + this.safeText(entry.getName());
        } else {
            this.status = "Model not found: " + this.safeText(entry.getId());
        }
    }

    private String selectedModelText() {
        if (!YesSteveModel.isAvailable()) {
            return "none";
        }

        String selectedId = YesSteveModel.getClientConfig().getSelectedModelId();
        return selectedId == null || selectedId.isEmpty() ? "none" : this.safeText(selectedId);
    }

    private int visibleRows() {
        return Math.max(1, (this.height - TOP - BOTTOM_PADDING) / ROW_HEIGHT);
    }

    private int maxScroll(int visibleRows) {
        return Math.max(0, this.entries.size() - visibleRows);
    }

    private int indexAt(int mouseX, int mouseY) {
        int left = Math.max(12, (this.width - LIST_WIDTH) / 2);
        int right = Math.min(this.width - 12, left + LIST_WIDTH);
        int bottom = Math.max(TOP + ROW_HEIGHT, this.height - BOTTOM_PADDING);

        if (mouseX < left || mouseX > right || mouseY < TOP || mouseY >= bottom) {
            return -1;
        }

        int row = (mouseY - TOP) / ROW_HEIGHT;
        int index = this.scroll + row;
        return index < this.entries.size() ? index : -1;
    }

    private String sourceName(OpenYsmModelEntry entry) {
        return entry.getSourceType().name().toLowerCase(Locale.ROOT);
    }

    private String safeText(String text) {
        if (text == null || text.isEmpty()) {
            return "unknown";
        }

        return text.replace('\n', ' ').replace('\r', ' ');
    }

    private String shorten(String text, int maxWidth) {
        String value = this.safeText(text);
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
