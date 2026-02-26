package com.mentalfrostbyte.jello.gui.base.elements.impl.image;

import com.mentalfrostbyte.jello.gui.combined.CustomGuiScreen;
import com.mentalfrostbyte.jello.gui.combined.AnimatedIconPanel;
import com.mentalfrostbyte.jello.util.client.render.theme.ClientColors;
import com.mentalfrostbyte.jello.util.game.render.RenderUtil;
import com.mentalfrostbyte.jello.util.game.render.RenderUtil2;
import org.newdawn.slick.opengl.Texture;

public class Image extends AnimatedIconPanel {
    private final Texture image;

    public Image(CustomGuiScreen screen, String text, int var3, int var4, int var5, int var6, Texture image) {
        super(screen, text, var3, var4, var5, var6, false);
        this.image = image;
    }

    @Override
    public void draw(float partialTicks) {
        RenderUtil.drawImage(
                (float) this.xA,
                (float) this.yA,
                (float) this.widthA,
                (float) this.heightA,
                this.image,
                RenderUtil2.shiftTowardsOther(
                        ClientColors.DEEP_TEAL.getColor(), ClientColors.LIGHT_GREYISH_BLUE.getColor(), !this.method13298() ? 0.0F : (!this.method13212() ? 0.15F : 0.3F)
                )
        );
        super.draw(partialTicks);
    }
}
