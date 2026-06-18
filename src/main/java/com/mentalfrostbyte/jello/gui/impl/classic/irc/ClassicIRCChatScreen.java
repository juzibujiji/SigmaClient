package com.mentalfrostbyte.jello.gui.impl.classic.irc;

import com.google.gson.JsonObject;
import com.mentalfrostbyte.jello.gui.base.elements.impl.critical.Screen;
import com.mentalfrostbyte.jello.util.client.render.theme.ClientColors;
import com.mentalfrostbyte.jello.util.game.render.RenderUtil;
import com.mentalfrostbyte.jello.util.game.render.RenderUtil2;
import net.minecraft.client.Minecraft;

public class ClassicIRCChatScreen extends Screen {
    private static final Minecraft mc = Minecraft.getInstance();

    public ClassicIRCChatScreen() {
        super("ClassicIRCChatScreen");
        int x = (this.widthA - ClassicIRCChatPanel.PANEL_WIDTH) / 2;
        int y = (this.heightA - ClassicIRCChatPanel.PANEL_HEIGHT) / 2;
        this.addToList(new ClassicIRCChatPanel(this, "IRC Chat", x, y, () -> mc.displayGuiScreen(null)));
        RenderUtil2.blur();
    }

    @Override
    public int getFPS() {
        return Minecraft.getFps();
    }

    @Override
    public JsonObject toConfigWithExtra(JsonObject config) {
        RenderUtil2.resetShaders();
        return super.toConfigWithExtra(config);
    }

    @Override
    public void keyPressed(int keyCode) {
        super.keyPressed(keyCode);
        if (keyCode == 256) {
            RenderUtil2.resetShaders();
            mc.displayGuiScreen(null);
        }
    }

    @Override
    public void draw(float partialTicks) {
        RenderUtil.drawRoundedRect(
                (float) this.xA,
                (float) this.yA,
                (float) (this.xA + this.widthA),
                (float) (this.yA + this.heightA),
                RenderUtil2.applyAlpha(ClientColors.DEEP_TEAL.getColor(), 0.35F * partialTicks)
        );
        super.draw(partialTicks);
    }
}
