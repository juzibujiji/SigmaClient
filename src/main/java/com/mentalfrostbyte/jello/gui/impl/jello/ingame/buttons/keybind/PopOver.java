package com.mentalfrostbyte.jello.gui.impl.jello.ingame.buttons.keybind;

import com.mentalfrostbyte.jello.gui.base.animations.Animation;
import com.mentalfrostbyte.jello.gui.base.elements.Element;
import com.mentalfrostbyte.jello.gui.base.elements.impl.button.types.TextButton;
import com.mentalfrostbyte.jello.gui.combined.CustomGuiScreen;
import com.mentalfrostbyte.jello.gui.impl.jello.ingame.KeyboardScreen;
import com.mentalfrostbyte.jello.util.client.render.ResourceRegistry;
import com.mentalfrostbyte.jello.util.client.render.Resources;
import com.mentalfrostbyte.jello.util.client.render.theme.ClientColors;
import com.mentalfrostbyte.jello.util.client.render.theme.ColorHelper;
import com.mentalfrostbyte.jello.util.game.render.RenderUtil;
import com.mentalfrostbyte.jello.util.game.render.RenderUtil2;
import com.mentalfrostbyte.jello.util.system.math.smoothing.EasingFunctions;
import com.mentalfrostbyte.jello.util.system.math.smoothing.QuadraticEasing;
import net.minecraft.client.Minecraft;
import org.lwjgl.opengl.GL11;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

public class PopOver extends Element {
    private final int keyCode;
    private final Animation animation;
    private boolean flipped = false;
    private final List<AddListener> addListeners = new ArrayList<AddListener>();

    public PopOver(CustomGuiScreen parent, String name, int x, int y, int keyCode, String text) {
        super(parent, name, x - 125, y, 250, 330, ColorHelper.field27961, text, false);
        if (this.yA + this.heightA <= Minecraft.getInstance().getMainWindow().getHeight()) {
            this.yA += 10;
        } else {
            this.yA -= 400;
            this.flipped = true;
        }

        this.keyCode = keyCode;
        this.animation = new Animation(250, 0);
        this.setReAddChildren(true);
        this.setListening(false);
        this.rebuildEntries();
        TextButton addButton;
        this.addToList(
                addButton = new TextButton(
                        this,
                        "addButton",
                        this.widthA - 70,
                        this.heightA - 70,
                        ResourceRegistry.JelloLightFont25.getWidth("Add"),
                        70,
                        ColorHelper.field27961,
                        "Add",
                        ResourceRegistry.JelloLightFont25
                )
        );
        addButton.onClick((mouseX, mouseY) -> this.notifyAdd());
    }

    public void rebuildEntries() {
        int index = 1;
        ArrayList existingNames = new ArrayList();

        for (CustomGuiScreen child : this.getChildren()) {
            if (child.getHeightA() != 0) {
                existingNames.add(child.getName());
            }
        }

        this.method13242();
        this.setFocused(true);
        this.clearChildren();

        for (BindTarget target : KeyboardScreen.method13328()) {
            int keybind = target.getKeybind();
            if (keybind == this.keyCode) {
                BindEntry entry;
                this.addToList(entry = new BindEntry(this, target.getDisplayName(), 0, 20 + 55 * index, this.widthA, 55, target, index++));
                entry.onPress(unused -> {
                    target.bind(0);
                    this.callUIHandlers();
                });
                if (existingNames.size() > 0 && !existingNames.contains(target.getDisplayName())) {
                    entry.beginRemove();
                }
            }
        }
    }

    @Override
    public void updatePanelDimensions(int newHeight, int newWidth) {
        Map<Integer, BindEntry> entries = new HashMap();

        for (CustomGuiScreen child : this.getChildren()) {
            if (child instanceof BindEntry) {
                entries.put(((BindEntry) child).keyCode, (BindEntry) child);
            }
        }

        int y = 75;

        for (Entry<Integer, BindEntry> entry : entries.entrySet()) {
            entry.getValue().setYA(y);
            y += entry.getValue().getHeightA();
        }

        super.updatePanelDimensions(newHeight, newWidth);
    }

    @Override
    public void draw(float partialTicks) {
        partialTicks = this.animation.calcPercent();
        float progress = EasingFunctions.easeOutBack(partialTicks, 0.0F, 1.0F, 1.0F);
        this.method13279(0.8F + progress * 0.2F, 0.8F + progress * 0.2F);
        this.method13284((int) ((float) this.widthA * 0.2F * (1.0F - progress)) * (!this.flipped ? 1 : -1));
        super.method13224();
        int color = RenderUtil2.applyAlpha(-723724, QuadraticEasing.easeOutQuad(partialTicks, 0.0F, 1.0F, 1.0F));
        RenderUtil.drawRoundedRect(
                (float) (this.xA + 10 / 2),
                (float) (this.yA + 10 / 2),
                (float) (this.widthA - 10),
                (float) (this.heightA - 10),
                35.0F,
                partialTicks
        );
        RenderUtil.drawRoundedRect(
                (float) (this.xA + 10 / 2),
                (float) (this.yA + 10 / 2),
                (float) (this.xA - 10 / 2 + this.widthA),
                (float) (this.yA - 10 / 2 + this.heightA),
                RenderUtil2.applyAlpha(ClientColors.DEEP_TEAL.getColor(), partialTicks * 0.25F)
        );
        RenderUtil.drawRoundedRect((float) this.xA, (float) this.yA, (float) this.widthA, (float) this.heightA, (float) 10, color);
        GL11.glPushMatrix();
        GL11.glTranslatef((float) this.xA, (float) this.yA, 0.0F);
        GL11.glRotatef(!this.flipped ? -90.0F : 90.0F, 0.0F, 0.0F, 1.0F);
        GL11.glTranslatef((float) (-this.xA), (float) (-this.yA), 0.0F);
        RenderUtil.drawImage(
                (float) (this.xA + (!this.flipped ? 0 : this.heightA)),
                (float) this.yA + (float) ((this.widthA - 47) / 2) * (!this.flipped ? 1.0F : -1.5F),
                18.0F,
                47.0F,
                Resources.selectPNG,
                color
        );
        GL11.glPopMatrix();
        RenderUtil.drawString(
                ResourceRegistry.JelloLightFont25,
                (float) (this.xA + 25),
                (float) (this.yA + 20),
                this.text + " Key",
                RenderUtil2.applyAlpha(ClientColors.DEEP_TEAL.getColor(), 0.8F * partialTicks)
        );
        RenderUtil.drawRoundedRect(
                (float) (this.xA + 25),
                (float) (this.yA + 68),
                (float) (this.xA + this.widthA - 25),
                (float) (this.yA + 69),
                RenderUtil2.applyAlpha(ClientColors.DEEP_TEAL.getColor(), 0.05F * partialTicks)
        );
        super.draw(partialTicks);
    }

    public final void addAddListener(AddListener listener) {
        this.addListeners.add(listener);
    }

    public final void notifyAdd() {
        for (AddListener listener : this.addListeners) {
            listener.onAdd(this);
        }
    }

    public interface AddListener {
        void onAdd(PopOver popOver);
    }
}
