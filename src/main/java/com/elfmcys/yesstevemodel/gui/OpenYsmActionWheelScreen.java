package com.elfmcys.yesstevemodel.gui;

import com.elfmcys.yesstevemodel.client.OpenYsmBakedPlayerModel;
import com.elfmcys.yesstevemodel.client.animation.OpenYsmAnimationSet;
import com.mojang.blaze3d.matrix.MatrixStack;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.util.text.StringTextComponent;

import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;

public class OpenYsmActionWheelScreen extends Screen {
    private static final int SLOT_COUNT = 8;
    private static final int SLOT_WIDTH = 118;
    private static final int SLOT_HEIGHT = 30;
    private static final int CONFIG_WIDTH = 260;

    private final OpenYsmGuiService service = OpenYsmGuiService.INSTANCE;
    private final OpenYsmScreenSkin skin = VanillaOpenYsmSkin.INSTANCE;
    private final Deque<String> navigation = OpenYsmGuiService.newNavigation();
    private final List<OpenYsmWheelNode> nodes = new ArrayList<>();
    private final int heldKey;
    private OpenYsmBakedPlayerModel model;
    private OpenYsmAnimationSet.ExtraActionButton selectedConfig;
    private OpenYsmWheelNode hoveredNode;
    private String status = "";
    private int page;

    public OpenYsmActionWheelScreen() {
        this(-1);
    }

    public OpenYsmActionWheelScreen(int heldKey) {
        super(new StringTextComponent("YSM Action Wheel"));
        this.heldKey = heldKey;
    }

    @Override
    protected void init() {
        this.reload();
    }

    @Override
    public void render(MatrixStack matrices, int mouseX, int mouseY, float delta) {
        this.renderBackground(matrices);
        drawCenteredString(matrices, this.font, "YSM Action Wheel", this.width / 2, 18, 0xFFFFFF);
        drawCenteredString(matrices, this.font, subtitle(), this.width / 2, 34, 0x91A3B0);

        int centerX = this.width / 2 - (this.selectedConfig == null ? 0 : CONFIG_WIDTH / 2);
        int centerY = this.height / 2 + 8;
        int hoveredSlot = this.slotAt(mouseX, mouseY, centerX, centerY);
        this.hoveredNode = hoveredSlot >= 0 && hoveredSlot < this.nodes.size() ? this.nodes.get(hoveredSlot) : null;
        for (int i = 0; i < SLOT_COUNT; i++) {
            Slot slot = slot(i, centerX, centerY);
            OpenYsmWheelNode node = i < this.nodes.size() ? this.nodes.get(i) : null;
            String label = node == null ? "" : node.getLabel();
            this.skin.button(matrices, this.font, this.shorten(label, SLOT_WIDTH - 10), slot.left, slot.top,
                    slot.left + SLOT_WIDTH, slot.top + SLOT_HEIGHT, i == hoveredSlot, colorFor(node));
        }

        this.skin.button(matrices, this.font, "Stop", centerX - 34, centerY - 12, centerX + 34, centerY + 12,
                this.isStopAt(mouseX, mouseY, centerX, centerY), 0xFFFFFF);
        String pageText = "Page " + (this.page + 1) + "/" + this.service.pageCount(this.model, this.navigation)
                + (this.heldKey >= 0 ? " | release key to close" : " | R reload | M models | wheel scroll");
        drawCenteredString(matrices, this.font, pageText, centerX, centerY + 50, 0x91A3B0);
        if (!this.status.isEmpty()) {
            drawCenteredString(matrices, this.font, this.shorten(this.status, 360), centerX, centerY + 66, 0xD7E4EF);
        }
        if (this.selectedConfig != null) {
            this.renderConfig(matrices, mouseX, mouseY);
        }
        super.render(matrices, mouseX, mouseY, delta);
    }

    private void renderConfig(MatrixStack matrices, int mouseX, int mouseY) {
        int left = this.width - CONFIG_WIDTH - 18;
        int top = 70;
        int bottom = this.height - 36;
        this.skin.panel(matrices, left, top, left + CONFIG_WIDTH, bottom);
        drawString(matrices, this.font, this.shorten(this.selectedConfig.getName(), CONFIG_WIDTH - 18), left + 9, top + 10, 0xFFFFFF);
        int y = top + 30;
        if (!this.selectedConfig.getDescription().isEmpty()) {
            drawString(matrices, this.font, this.shorten(this.selectedConfig.getDescription(), CONFIG_WIDTH - 18), left + 9, y, 0x91A3B0);
            y += 18;
        }
        if (this.selectedConfig.getForms().isEmpty()) {
            drawString(matrices, this.font, "No config forms", left + 9, y, 0x91A3B0);
            return;
        }
        for (OpenYsmAnimationSet.ActionForm form : this.selectedConfig.getForms()) {
            int formTop = y;
            drawString(matrices, this.font, this.shorten(form.getTitle(), CONFIG_WIDTH - 18), left + 9, y, 0xD7E4EF);
            y += 14;
            if ("radio".equals(form.getType())) {
                for (Map.Entry<String, String> entry : form.getLabels().entrySet()) {
                    this.skin.button(matrices, this.font, this.shorten(entry.getValue(), CONFIG_WIDTH - 34),
                            left + 14, y, left + CONFIG_WIDTH - 14, y + 18,
                            isInside(mouseX, mouseY, left + 14, y, left + CONFIG_WIDTH - 14, y + 18), 0xFFFFFF);
                    y += 22;
                }
            } else if ("range".equals(form.getType())) {
                this.skin.button(matrices, this.font, "Apply " + rangeLabel(form), left + 14, y,
                        left + CONFIG_WIDTH - 14, y + 18,
                        isInside(mouseX, mouseY, left + 14, y, left + CONFIG_WIDTH - 14, y + 18), 0xFFFFFF);
                y += 22;
            } else {
                this.skin.button(matrices, this.font, "Toggle", left + 14, y, left + CONFIG_WIDTH - 14, y + 18,
                        isInside(mouseX, mouseY, left + 14, y, left + CONFIG_WIDTH - 14, y + 18), 0xFFFFFF);
                y += 22;
            }
            if (!form.getDescription().isEmpty() && y - formTop < 74) {
                drawString(matrices, this.font, this.shorten(form.getDescription(), CONFIG_WIDTH - 18), left + 9, y, 0x778899);
                y += 14;
            }
            y += 4;
            if (y > bottom - 24) {
                break;
            }
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) {
            return super.mouseClicked(mouseX, mouseY, button);
        }
        if (this.selectedConfig != null && this.clickConfig((int) mouseX, (int) mouseY)) {
            return true;
        }
        int centerX = this.width / 2 - (this.selectedConfig == null ? 0 : CONFIG_WIDTH / 2);
        int centerY = this.height / 2 + 8;
        if (this.isStopAt((int) mouseX, (int) mouseY, centerX, centerY)) {
            this.service.stop(this.minecraft, modelId());
            this.status = "Stopped action";
            return true;
        }
        int slot = this.slotAt((int) mouseX, (int) mouseY, centerX, centerY);
        if (slot >= 0 && slot < this.nodes.size()) {
            this.activate(this.nodes.get(slot));
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        int pages = this.service.pageCount(this.model, this.navigation);
        this.page = clamp(this.page + (delta > 0.0D ? -1 : 1), 0, pages - 1);
        this.reloadPage();
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 82) {
            this.reload();
            return true;
        }
        if (keyCode == 77) {
            OpenYsmScreens.openModelManager();
            return true;
        }
        if (keyCode == 259 && !this.navigation.isEmpty()) {
            this.navigation.removeLast();
            this.page = 0;
            this.reloadPage();
            return true;
        }
        if (keyCode == 256 && this.selectedConfig != null) {
            this.selectedConfig = null;
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
        if (this.heldKey >= 0 && keyCode == this.heldKey) {
            this.activateHoveredOnRelease();
            this.minecraft.displayGuiScreen(null);
            return true;
        }
        return super.keyReleased(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private void reload() {
        this.model = this.service.selectedModel(this.minecraft);
        this.status = this.model == null ? "Select and enable a YSM model first" : "";
        this.reloadPage();
    }

    private void reloadPage() {
        this.nodes.clear();
        this.nodes.addAll(this.service.wheelPage(this.model, this.navigation, this.page));
        if (this.nodes.isEmpty() && this.model != null) {
            this.status = "No extra actions or config forms for this model";
        }
    }

    private void activate(OpenYsmWheelNode node) {
        if (node == null) {
            return;
        }
        switch (node.getType()) {
            case ACTION:
                this.service.play(this.minecraft, modelId(), node.getAction());
                this.status = "Playing: " + node.getLabel();
                break;
            case CATEGORY:
                this.navigation.addLast(OpenYsmGuiService.normalizeGroupKey(node.getKey()));
                this.page = 0;
                this.selectedConfig = null;
                this.reloadPage();
                break;
            case CONFIG_GROUP:
                this.selectedConfig = node.getButton();
                this.status = "Editing: " + node.getLabel();
                break;
            case RETURN:
                if (!this.navigation.isEmpty()) {
                    this.navigation.removeLast();
                    this.page = 0;
                    this.reloadPage();
                }
                break;
            case STOP:
                this.service.stop(this.minecraft, modelId());
                this.status = "Stopped action";
                break;
            default:
                break;
        }
    }

    private void activateHoveredOnRelease() {
        if (this.hoveredNode == null || this.selectedConfig != null) {
            return;
        }
        switch (this.hoveredNode.getType()) {
            case ACTION:
            case STOP:
                this.activate(this.hoveredNode);
                break;
            default:
                break;
        }
    }

    private boolean clickConfig(int mouseX, int mouseY) {
        int left = this.width - CONFIG_WIDTH - 18;
        int y = 100 + (this.selectedConfig.getDescription().isEmpty() ? 0 : 18);
        for (OpenYsmAnimationSet.ActionForm form : this.selectedConfig.getForms()) {
            y += 14;
            if ("radio".equals(form.getType())) {
                for (Map.Entry<String, String> entry : form.getLabels().entrySet()) {
                    if (isInside(mouseX, mouseY, left + 14, y, left + CONFIG_WIDTH - 14, y + 18)) {
                        return this.applyForm(form, entry.getKey());
                    }
                    y += 22;
                }
            } else {
                if (isInside(mouseX, mouseY, left + 14, y, left + CONFIG_WIDTH - 14, y + 18)) {
                    return this.applyForm(form, nextExpression(form));
                }
                y += 22;
            }
            if (!form.getDescription().isEmpty() && y < this.height - 60) {
                y += 14;
            }
            y += 4;
        }
        return false;
    }

    private boolean applyForm(OpenYsmAnimationSet.ActionForm form, String expression) {
        if (this.minecraft == null || this.minecraft.player == null) {
            return false;
        }
        boolean applied = this.service.applyForm(this.minecraft.player, modelId(), form, expression);
        this.status = applied ? "Applied: " + form.getTitle() : "Ignored unsafe form: " + form.getTitle();
        return true;
    }

    private String nextExpression(OpenYsmAnimationSet.ActionForm form) {
        OpenYsmGuiService.Assignment assignment = OpenYsmGuiService.parseAssignment(form.getDefaultValue());
        if (assignment == null) {
            return form.getDefaultValue();
        }
        if ("range".equals(form.getType())) {
            double step = form.getStep() <= 0.0F ? 1.0D : form.getStep();
            double min = form.getMin();
            double max = form.getMax() <= min ? min + step : form.getMax();
            double next = assignment.getValue() + step;
            if (next > max) {
                next = min;
            }
            return "variable." + assignment.getName() + "=" + next;
        }
        return "variable." + assignment.getName() + "=" + (assignment.getValue() == 0.0D ? 1 : 0);
    }

    private Slot slot(int index, int centerX, int centerY) {
        int[][] offsets = new int[][]{
                {-SLOT_WIDTH / 2, -116},
                {76, -82},
                {104, -SLOT_HEIGHT / 2},
                {76, 54},
                {-SLOT_WIDTH / 2, 86},
                {-194, 54},
                {-222, -SLOT_HEIGHT / 2},
                {-194, -82}
        };
        return new Slot(centerX + offsets[index][0], centerY + offsets[index][1]);
    }

    private int slotAt(int mouseX, int mouseY, int centerX, int centerY) {
        for (int i = 0; i < SLOT_COUNT; i++) {
            Slot slot = slot(i, centerX, centerY);
            if (isInside(mouseX, mouseY, slot.left, slot.top, slot.left + SLOT_WIDTH, slot.top + SLOT_HEIGHT)) {
                return i;
            }
        }
        return -1;
    }

    private boolean isStopAt(int mouseX, int mouseY, int centerX, int centerY) {
        return isInside(mouseX, mouseY, centerX - 34, centerY - 12, centerX + 34, centerY + 12);
    }

    private String subtitle() {
        String modelText = this.model == null ? "none" : this.model.getId();
        return "Model: " + modelText + (this.navigation.isEmpty() ? "" : " | " + this.navigation.peekLast());
    }

    private String modelId() {
        return this.model == null ? "" : this.model.getId();
    }

    private String rangeLabel(OpenYsmAnimationSet.ActionForm form) {
        return form.getMin() + "..." + form.getMax();
    }

    private int colorFor(OpenYsmWheelNode node) {
        if (node == null) {
            return 0x777777;
        }
        return switch (node.getType()) {
            case CATEGORY -> 0xBFE3FF;
            case CONFIG_GROUP -> 0xF7D78A;
            case RETURN -> 0xBFD0DD;
            case STOP -> 0xFFB4B4;
            default -> 0xFFFFFF;
        };
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

    private static boolean isInside(int mouseX, int mouseY, int left, int top, int right, int bottom) {
        return mouseX >= left && mouseX <= right && mouseY >= top && mouseY <= bottom;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static final class Slot {
        private final int left;
        private final int top;

        private Slot(int left, int top) {
            this.left = left;
            this.top = top;
        }
    }
}
