package com.mentalfrostbyte.jello.gui.impl.jello.ingame;

import com.google.gson.JsonObject;
import com.mentalfrostbyte.jello.gui.base.elements.impl.critical.Screen;
import com.mentalfrostbyte.jello.gui.impl.jello.ingame.irc.JelloIRCChatPanel;
import com.mentalfrostbyte.jello.util.client.render.theme.ClientColors;
import com.mentalfrostbyte.jello.util.game.render.RenderUtil;
import com.mentalfrostbyte.jello.util.game.render.RenderUtil2;
import net.minecraft.client.Minecraft;

public class IRCChatScreen extends Screen {
    private static final Minecraft mc = Minecraft.getInstance();

    public IRCChatScreen() {
        super("IRCChatScreen");
        int x = (this.widthA - JelloIRCChatPanel.PANEL_WIDTH) / 2;
        int y = (this.heightA - JelloIRCChatPanel.PANEL_HEIGHT) / 2;
        this.addToList(new JelloIRCChatPanel(this, "ircChat", x, y, () -> mc.displayGuiScreen(null)));
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
                RenderUtil2.applyAlpha(ClientColors.DEEP_TEAL.getColor(), 0.25F * partialTicks)
        );
        super.draw(partialTicks);
    }
}
