package com.elfmcys.yesstevemodel.gui;

import com.elfmcys.yesstevemodel.OpenYsmModelEntry;
import com.elfmcys.yesstevemodel.OpenYsmTextureOption;
import com.elfmcys.yesstevemodel.YesSteveModel;
import com.mojang.blaze3d.matrix.MatrixStack;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.util.Util;
import net.minecraft.util.text.StringTextComponent;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class OpenYsmModelSelectionScreen extends Screen {
    private static final int LEFT_WIDTH = 330;
    private static final int RIGHT_WIDTH = 250;
    private static final int ROW_HEIGHT = 28;
    private static final int TOP = 78;
    private static final int BOTTOM_PADDING = 44;

    private final OpenYsmGuiService service = OpenYsmGuiService.INSTANCE;
    private final OpenYsmScreenSkin skin = VanillaOpenYsmSkin.INSTANCE;
    private final List<OpenYsmModelEntry> entries = new ArrayList<>();
    private final List<OpenYsmTextureOption> textures = new ArrayList<>();
    private String search = "";
    private String status = "";
    private int scroll;
    private int textureScroll;

    public OpenYsmModelSelectionScreen() {
        super(new StringTextComponent("YSM Models"));
    }

    @Override
    protected void init() {
        this.reloadEntries(false);
        this.reloadTextures();
    }

    @Override
    public void render(MatrixStack matrices, int mouseX, int mouseY, float delta) {
        this.renderBackground(matrices);
        Layout layout = this.layout();
        int visibleRows = this.visibleRows();
        this.scroll = clamp(this.scroll, 0, this.maxScroll(visibleRows));
        this.textureScroll = clamp(this.textureScroll, 0, Math.max(0, this.textures.size() - this.textureRows()));

        drawCenteredString(matrices, this.font, "YSM Models", this.width / 2, 18, 0xFFFFFF);
        drawString(matrices, this.font, "Search: " + (this.search.isEmpty() ? "<type to filter>" : this.search),
                layout.left, 42, 0xD7E4EF);
        drawString(matrices, this.font, "R reload | A wheel | F folder | Backspace edit",
                layout.left, 56, 0x91A3B0);

        this.skin.panel(matrices, layout.left - 2, TOP - 2, layout.left + LEFT_WIDTH + 2, layout.bottom + 2);
        this.skin.panel(matrices, layout.right - 2, TOP - 2, layout.right + RIGHT_WIDTH + 2, layout.bottom + 2);
        this.renderModelList(matrices, mouseX, mouseY, layout, visibleRows);
        this.renderDetails(matrices, mouseX, mouseY, layout);

        if (!this.status.isEmpty()) {
            drawString(matrices, this.font, this.shorten(this.status, layout.totalWidth), layout.left, layout.bottom + 10, 0xD7E4EF);
        }
        super.render(matrices, mouseX, mouseY, delta);
    }

    private void renderModelList(MatrixStack matrices, int mouseX, int mouseY, Layout layout, int visibleRows) {
        if (!YesSteveModel.isAvailable()) {
            drawCenteredString(matrices, this.font, "OpenYSM not initialized", layout.left + LEFT_WIDTH / 2, TOP + 22, 0xFF7777);
            return;
        }
        if (this.entries.isEmpty()) {
            drawCenteredString(matrices, this.font, "No models match", layout.left + LEFT_WIDTH / 2, TOP + 22, 0xD7E4EF);
            return;
        }
        int hoverIndex = this.modelIndexAt(mouseX, mouseY, layout);
        String selectedId = YesSteveModel.getClientConfig().getSelectedModelId();
        for (int row = 0; row < visibleRows; row++) {
            int index = this.scroll + row;
            if (index >= this.entries.size()) {
                break;
            }
            OpenYsmModelEntry entry = this.entries.get(index);
            int y = TOP + row * ROW_HEIGHT;
            boolean selected = entry.getId().equals(selectedId);
            this.skin.row(matrices, layout.left, y, layout.left + LEFT_WIDTH, y + ROW_HEIGHT - 2,
                    index == hoverIndex, selected);
            drawString(matrices, this.font, this.shorten(entry.getName(), LEFT_WIDTH - 14),
                    layout.left + 7, y + 4, selected ? 0xFFFFFF : 0xD7E4EF);
            String source = entry.getSourceType().name().toLowerCase(Locale.ROOT) + " | " + entry.getId();
            drawString(matrices, this.font, this.shorten(source, LEFT_WIDTH - 14), layout.left + 7, y + 16, 0x91A3B0);
        }
    }

    private void renderDetails(MatrixStack matrices, int mouseX, int mouseY, Layout layout) {
        int x = layout.right + 10;
        int y = TOP + 10;
        drawString(matrices, this.font, "Selected", x, y, 0xFFFFFF);
        y += 16;
        drawString(matrices, this.font, this.shorten(selectedModelText(), RIGHT_WIDTH - 20), x, y, 0xD7E4EF);
        y += 18;
        drawString(matrices, this.font, "Texture: " + textureName(), x, y, 0xD7E4EF);
        y += 22;
        this.skin.button(matrices, this.font, "Open Action Wheel", x, y, x + RIGHT_WIDTH - 20, y + 22,
                isInside(mouseX, mouseY, x, y, x + RIGHT_WIDTH - 20, y + 22), 0xFFFFFF);
        y += 32;
        drawString(matrices, this.font, "Textures", x, y, 0xFFFFFF);
        y += 14;
        if (this.textures.isEmpty()) {
            drawString(matrices, this.font, "No texture list", x, y, 0x91A3B0);
            return;
        }
        int rows = this.textureRows();
        String selectedTexture = YesSteveModel.getClientConfig().getSelectedTextureId();
        for (int row = 0; row < rows; row++) {
            int index = this.textureScroll + row;
            if (index >= this.textures.size()) {
                break;
            }
            OpenYsmTextureOption texture = this.textures.get(index);
            int rowTop = y + row * 20;
            boolean selected = texture.getId().equals(selectedTexture) || (selectedTexture.isEmpty() && row == 0);
            this.skin.row(matrices, x, rowTop, x + RIGHT_WIDTH - 20, rowTop + 18,
                    this.textureIndexAt(mouseX, mouseY, layout) == index, selected);
            drawString(matrices, this.font, this.shorten(texture.getId(), RIGHT_WIDTH - 30), x + 5, rowTop + 5,
                    selected ? 0xFFFFFF : 0xD7E4EF);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) {
            return super.mouseClicked(mouseX, mouseY, button);
        }
        Layout layout = this.layout();
        int index = this.modelIndexAt((int) mouseX, (int) mouseY, layout);
        if (index >= 0 && index < this.entries.size()) {
            this.selectEntry(this.entries.get(index), "");
            return true;
        }
        int textureIndex = this.textureIndexAt((int) mouseX, (int) mouseY, layout);
        if (textureIndex >= 0 && textureIndex < this.textures.size()) {
            this.service.selectedEntry().ifPresent(entry -> this.selectEntry(entry, this.textures.get(textureIndex).getId()));
            return true;
        }
        int x = layout.right + 10;
        int y = TOP + 66;
        if (isInside((int) mouseX, (int) mouseY, x, y, x + RIGHT_WIDTH - 20, y + 22)) {
            OpenYsmScreens.openActionWheel();
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        Layout layout = this.layout();
        if (mouseX >= layout.right) {
            this.textureScroll += delta > 0.0D ? -1 : 1;
        } else {
            this.scroll += delta > 0.0D ? -1 : 1;
        }
        return true;
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (!Character.isISOControl(codePoint)) {
            this.search += codePoint;
            this.reloadEntries(false);
            return true;
        }
        return super.charTyped(codePoint, modifiers);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 259 && !this.search.isEmpty()) {
            this.search = this.search.substring(0, this.search.length() - 1);
            this.reloadEntries(false);
            return true;
        }
        if (keyCode == 82) {
            this.reloadEntries(true);
            this.reloadTextures();
            return true;
        }
        if (keyCode == 65) {
            OpenYsmScreens.openActionWheel();
            return true;
        }
        if (keyCode == 70) {
            Util.getOSType().openFile(YesSteveModel.getConfigDirectory().toFile());
            return true;
        }
        if (keyCode == 265 || keyCode == 264) {
            this.scroll += keyCode == 265 ? -1 : 1;
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private void reloadEntries(boolean rebuildIndex) {
        this.entries.clear();
        if (!YesSteveModel.isAvailable()) {
            this.status = "OpenYSM not initialized";
            return;
        }
        try {
            if (rebuildIndex) {
                YesSteveModel.reload(this.minecraft.getResourceManager());
            }
            this.entries.addAll(this.service.listModels(this.minecraft, this.search));
            this.status = rebuildIndex ? "Reloaded " + YesSteveModel.getModelIndex().getEntries().size() + " models" : "";
        } catch (RuntimeException exception) {
            this.status = "Reload failed: " + safeText(exception.getMessage());
        }
    }

    private void reloadTextures() {
        this.textures.clear();
        this.service.selectedEntry().ifPresent(entry -> this.textures.addAll(this.service.listTextures(this.minecraft, entry)));
    }

    private void selectEntry(OpenYsmModelEntry entry, String textureId) {
        if (entry == null) {
            return;
        }
        if (YesSteveModel.selectModel(entry.getId(), textureId == null ? "" : textureId)) {
            YesSteveModel.setRenderPlayers(true);
            this.status = "Selected: " + safeText(entry.getName());
            this.reloadTextures();
        } else {
            this.status = "Model not found: " + safeText(entry.getId());
        }
    }

    private String selectedModelText() {
        String selectedId = YesSteveModel.isAvailable() ? YesSteveModel.getClientConfig().getSelectedModelId() : "";
        return selectedId == null || selectedId.isEmpty() ? "none" : selectedId;
    }

    private String textureName() {
        String textureId = YesSteveModel.getClientConfig().getSelectedTextureId();
        return textureId == null || textureId.isEmpty() ? "default" : textureId;
    }

    private int visibleRows() {
        return Math.max(1, (this.height - TOP - BOTTOM_PADDING) / ROW_HEIGHT);
    }

    private int textureRows() {
        return Math.max(1, (this.height - TOP - 148) / 20);
    }

    private int maxScroll(int visibleRows) {
        return Math.max(0, this.entries.size() - visibleRows);
    }

    private int modelIndexAt(int mouseX, int mouseY, Layout layout) {
        if (!isInside(mouseX, mouseY, layout.left, TOP, layout.left + LEFT_WIDTH, layout.bottom)) {
            return -1;
        }
        int index = this.scroll + (mouseY - TOP) / ROW_HEIGHT;
        return index < this.entries.size() ? index : -1;
    }

    private int textureIndexAt(int mouseX, int mouseY, Layout layout) {
        int x = layout.right + 10;
        int top = TOP + 112;
        if (!isInside(mouseX, mouseY, x, top, x + RIGHT_WIDTH - 20, layout.bottom)) {
            return -1;
        }
        int index = this.textureScroll + (mouseY - top) / 20;
        return index < this.textures.size() ? index : -1;
    }

    private Layout layout() {
        int total = LEFT_WIDTH + 16 + RIGHT_WIDTH;
        int left = Math.max(12, (this.width - total) / 2);
        return new Layout(left, left + LEFT_WIDTH + 16, total, Math.max(TOP + ROW_HEIGHT, this.height - BOTTOM_PADDING));
    }

    private String shorten(String text, int maxWidth) {
        String value = safeText(text);
        if (this.font.getStringWidth(value) <= maxWidth) {
            return value;
        }
        while (value.length() > 3 && this.font.getStringWidth(value + "...") > maxWidth) {
            value = value.substring(0, value.length() - 1);
        }
        return value + "...";
    }

    private static boolean isInside(int mouseX, int mouseY, int left, int top, int right, int bottom) {
        return mouseX >= left && mouseX <= right && mouseY >= top && mouseY <= bottom;
    }

    private static String safeText(String text) {
        return text == null || text.isEmpty() ? "unknown" : text.replace('\n', ' ').replace('\r', ' ');
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static final class Layout {
        private final int left;
        private final int right;
        private final int totalWidth;
        private final int bottom;

        private Layout(int left, int right, int totalWidth, int bottom) {
            this.left = left;
            this.right = right;
            this.totalWidth = totalWidth;
            this.bottom = bottom;
        }
    }
}
