package com.mentalfrostbyte.jello.gui.base.elements.impl.button.types;

import com.mentalfrostbyte.jello.gui.base.animations.Animation;
import com.mentalfrostbyte.jello.gui.base.elements.impl.Slider;
import com.mentalfrostbyte.jello.gui.base.elements.impl.button.Button;
import com.mentalfrostbyte.jello.util.client.render.theme.ClientColors;
import com.mentalfrostbyte.jello.util.client.render.theme.ColorHelper;
import com.mentalfrostbyte.jello.util.game.render.RenderUtil;
import com.mentalfrostbyte.jello.util.game.render.RenderUtil2;

public class SliderButton extends Button {
    private final Slider field20600;
    private final Animation field20601 = new Animation(125, 125);

    public SliderButton(Slider var1, int var2) {
        super(var1, "sliderButton", 0, 0, var2, var2, new ColorHelper(ClientColors.LIGHT_GREYISH_BLUE.getColor()));
        this.field20601.changeDirection(Animation.Direction.BACKWARDS);
        this.method13215(true);
        this.field20886 = true;
        this.field20600 = var1;
    }

    @Override
    public void updatePanelDimensions(int newHeight, int newWidth) {
        super.updatePanelDimensions(newHeight, newWidth);
        float var5 = this.field20600.method13138();
        float var6 = (float) this.getXA() / (float) (this.parent.getWidthA() - this.getWidthA());
        if (!this.method13212() && !this.method13298() && !this.method13216()) {
            this.field20601.changeDirection(Animation.Direction.BACKWARDS);
        } else {
            this.field20601.changeDirection(Animation.Direction.FORWARDS);
        }

        this.field20600.method13139(var6);
    }

    @Override
    public void draw(float partialTicks) {
        if (!this.isHovered()) {
            float var10000 = 0.3F;
        } else if (!this.method13216()) {
            if (!this.method13212()) {
                Math.max(partialTicks * this.field20584, 0.0F);
            } else {
                float var8 = 1.5F;
            }
        } else {
            float var9 = 0.0F;
        }

        int var5 = 5;
        float var6 = (float) this.getWidthA();
        RenderUtil.drawRoundedRect(
                (float) (this.getXA() + var5),
                (float) (this.getYA() + var5),
                (float) (this.getWidthA() - var5 * 2),
                (float) (this.getHeightA() - var5 * 2),
                10.0F,
                partialTicks * 0.8F
        );
        RenderUtil.drawCircle(
                (float) (this.getXA() + this.getWidthA() / 2),
                (float) (this.getYA() + this.getWidthA() / 2),
                var6,
                RenderUtil2.applyAlpha(ClientColors.LIGHT_GREYISH_BLUE.getColor(), partialTicks)
        );

        super.drawChildren(partialTicks);
    }
}
