package com.mentalfrostbyte.jello.gui.impl.jello.ingame.clickgui.musicplayer.elements;

import com.mentalfrostbyte.Client;
import com.mentalfrostbyte.jello.gui.base.elements.Element;
import com.mentalfrostbyte.jello.gui.combined.CustomGuiScreen;
import com.mentalfrostbyte.jello.managers.MusicManager;
import com.mentalfrostbyte.jello.util.client.render.theme.ClientColors;
import com.mentalfrostbyte.jello.util.game.render.RenderUtil2;
import com.mentalfrostbyte.jello.util.game.render.RenderUtil;
import com.mentalfrostbyte.jello.util.client.render.Resources;

public class ProgressBar extends Element {
    private final MusicManager musicManager = Client.getInstance().musicManager;
    public float field21315 = -1.0F;
    private boolean dragging = false;

    public ProgressBar(CustomGuiScreen parentScreen, String var2, int var3, int var4, int var5, int var6) {
        super(parentScreen, var2, var3, var4, var5, var6, false);
    }

    @Override
    public boolean onClick(int mouseX, int mouseY, int mouseButton) {
        boolean result = super.onClick(mouseX, mouseY, mouseButton);
        if (mouseButton == 0 && this.musicManager.getDurationInt() > 0) {
            this.dragging = true;
            this.updateSeekPreview(mouseX);
        }
        return result;
    }

    @Override
    public void updatePanelDimensions(int mouseX, int mouseY) {
        super.updatePanelDimensions(mouseX, mouseY);
        if (this.dragging && this.musicManager.getDurationInt() > 0) {
            this.updateSeekPreview(mouseX);
        }
    }

    @Override
    public void onClick2(int mouseX, int mouseY, int mouseButton) {
        if (mouseButton == 0 && this.dragging) {
            this.updateSeekPreview(mouseX);
            this.seekToPreview();
            this.dragging = false;
        }
        super.onClick2(mouseX, mouseY, mouseButton);
    }

    private void updateSeekPreview(int mouseX) {
        this.field21315 = Math.min(Math.max((float) (mouseX - this.method13271()) / (float) this.getWidthA(), 0.0F), 1.0F);
    }

    private void seekToPreview() {
        int durationTotal = this.musicManager.getDurationInt();
        if (durationTotal > 0 && this.field21315 >= 0.0F) {
            int targetSecond = Math.min(Math.max(Math.round(this.field21315 * (float) durationTotal), 0), durationTotal);
            this.musicManager.setDuration(targetSecond);
        }
    }

    @Override
    public void draw(float partialTicks) {
        long durationLong = (int) this.musicManager.getDuration();
        double var5 = this.musicManager.method24322();
        int durationInt = this.musicManager.getDurationInt();
        float var8 = durationInt > 0 ? Math.max(0.0F, Math.min((float) durationLong / (float) durationInt, 1.0F))
                : 0.0F;
        float var9 = durationInt > 0 ? Math.max(0.0F, Math.min((float) var5 / (float) durationInt, 1.0F)) : 0.0F;
        if (this.dragging && durationInt > 0) {
            var8 = this.field21315;
        }

        if (durationLong == 0 && !this.musicManager.isPlayingSong()) {
            RenderUtil.drawRoundedRect2(
                    (float) this.getXA(),
                    (float) this.getYA(),
                    (float) this.getWidthA(),
                    (float) this.getHeightA(),
                    RenderUtil2.applyAlpha(ClientColors.DEEP_TEAL.getColor(), 0.43F * partialTicks));
        } else {
            RenderUtil.drawRoundedRect2(
                    (float) this.getXA(),
                    (float) this.getYA(),
                    (float) this.getWidthA(),
                    (float) this.getHeightA(),
                    RenderUtil2.applyAlpha(ClientColors.MID_GREY.getColor(), 0.075F));
            RenderUtil.drawRoundedRect2(
                    (float) this.getXA() + (float) this.getWidthA() * var9,
                    (float) this.getYA(),
                    (float) this.getWidthA() * (1.0F - var9),
                    (float) this.getHeightA(),
                    RenderUtil2.applyAlpha(ClientColors.DEEP_TEAL.getColor(), 0.43F * partialTicks));
            RenderUtil.drawRoundedRect2(
                    (float) this.getXA(),
                    (float) this.getYA(),
                    (float) this.getWidthA() * var8,
                    (float) this.getHeightA(),
                    RenderUtil2.applyAlpha(ClientColors.LIGHT_GREYISH_BLUE.getColor(), partialTicks * partialTicks));
            if (var8 != 0.0F) {
                RenderUtil.drawImage((float) this.getXA() + (float) this.getWidthA() * var8, (float) this.getYA(), 5.0F,
                        5.0F, Resources.shadowRightPNG);
            }
        }
    }
}
