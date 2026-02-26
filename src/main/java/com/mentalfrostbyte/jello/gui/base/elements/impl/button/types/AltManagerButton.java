package com.mentalfrostbyte.jello.gui.base.elements.impl.button.types;

import com.mentalfrostbyte.jello.gui.combined.CustomGuiScreen;
import com.mentalfrostbyte.jello.gui.combined.AnimatedIconPanel;
import com.mentalfrostbyte.jello.util.client.render.FontSizeAdjust;
import com.mentalfrostbyte.jello.util.client.render.ResourceRegistry;
import com.mentalfrostbyte.jello.util.client.render.theme.ClientColors;
import com.mentalfrostbyte.jello.util.game.render.RenderUtil;
import com.mentalfrostbyte.jello.util.game.render.RenderUtil2;
import net.minecraft.client.Minecraft;
import net.minecraft.client.audio.SimpleSound;
import net.minecraft.util.SoundEvents;

public class AltManagerButton extends AnimatedIconPanel {
    public int color;

    public AltManagerButton(CustomGuiScreen screen, String id, int x, int y, int width, int height, String text, int color) {
        super(screen, id, x, y, width, height, false);
        this.setText(text);
        this.onClick((sc, i) -> Minecraft.getInstance().getSoundHandler().play(SimpleSound.master(SoundEvents.UI_BUTTON_CLICK, 1.0F)));
        this.color = color;
    }

    @Override
    public void draw(float partialTicks) {
        this.setFont(ResourceRegistry.DefaultClientFont);
        RenderUtil.drawRoundedRect(
                (float) this.xA,
                (float) this.yA,
                (float) (this.xA + this.widthA),
                (float) (this.yA + this.heightA),
                RenderUtil2.applyAlpha(this.color, !this.isHovered() ? 0.25F : (!this.method13298() ? 0.4F : (!this.method13212() ? 0.5F : 0.6F)))
        );
        RenderUtil.method11429(
                (float) this.xA,
                (float) this.yA,
                (float) (this.xA + this.widthA),
                (float) (this.yA + this.heightA),
                2,
                RenderUtil2.applyAlpha(this.color, 0.2F)
        );
        RenderUtil.drawString(
                ResourceRegistry.DefaultClientFont,
                (float) (this.getXA() + this.getWidthA() / 2),
                (float) (this.getYA() + this.getHeightA() / 2),
                this.text,
                RenderUtil2.applyAlpha(ClientColors.LIGHT_GREYISH_BLUE.getColor(), !this.isHovered() ? 0.5F : 1.0F),
                FontSizeAdjust.NEGATE_AND_DIVIDE_BY_2,
                FontSizeAdjust.NEGATE_AND_DIVIDE_BY_2
        );
        super.draw(partialTicks);
    }
}
