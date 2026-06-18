package com.mentalfrostbyte.jello.gui.impl.classic.irc;

import com.mentalfrostbyte.jello.gui.base.elements.impl.button.Button;
import com.mentalfrostbyte.jello.gui.combined.CustomGuiScreen;
import com.mentalfrostbyte.jello.gui.impl.classic.clickgui.buttons.Exit;
import com.mentalfrostbyte.jello.gui.impl.classic.clickgui.buttons.Input;
import com.mentalfrostbyte.jello.gui.impl.classic.clickgui.panel.ClickGuiPanel;
import com.mentalfrostbyte.jello.gui.impl.irc.IRCChatGuiUtil;
import com.mentalfrostbyte.jello.module.impl.gui.jello.IRCClient;
import com.mentalfrostbyte.jello.module.impl.gui.jello.irc.IRCChatHistory;
import com.mentalfrostbyte.jello.module.impl.gui.jello.irc.IRCManager;
import com.mentalfrostbyte.jello.module.impl.gui.jello.irc.IRCUtlis;
import com.mentalfrostbyte.jello.util.client.render.ResourceRegistry;
import com.mentalfrostbyte.jello.util.client.render.Resources;
import com.mentalfrostbyte.jello.util.client.render.theme.ClientColors;
import com.mentalfrostbyte.jello.util.client.render.theme.ColorHelper;
import com.mentalfrostbyte.jello.util.game.render.RenderUtil;
import com.mentalfrostbyte.jello.util.game.render.RenderUtil2;
import org.lwjgl.glfw.GLFW;
import org.newdawn.slick.TrueTypeFont;

import java.util.ArrayList;
import java.util.List;

public class ClassicIRCChatPanel extends ClickGuiPanel {
    public static final int PANEL_WIDTH = 520;
    public static final int PANEL_HEIGHT = 430;
    private static final int MESSAGE_LINE_HEIGHT = 18;

    private final Input input;
    private final Runnable closeAction;

    public ClassicIRCChatPanel(CustomGuiScreen parent, String name, int x, int y, Runnable closeAction) {
        super(parent, name, x, y, PANEL_WIDTH, PANEL_HEIGHT);
        this.closeAction = closeAction;

        ColorHelper inputColor = new ColorHelper(ClientColors.DEEP_TEAL.getColor(), ClientColors.DEEP_TEAL.getColor(), ClientColors.DEEP_TEAL.getColor(), ClientColors.LIGHT_GREYISH_BLUE.getColor());
        this.addToList(this.input = new Input(this, "message", 24, PANEL_HEIGHT - 54, PANEL_WIDTH - 116, 34, inputColor, "", "Message...", ResourceRegistry.LyricsFont));
        this.input.setFont(ResourceRegistry.LyricsFont);

        Button send = new Button(
                this,
                "send",
                PANEL_WIDTH - 82,
                PANEL_HEIGHT - 54,
                58,
                34,
                new ColorHelper(-3618616, -2500135, -2500135, ClientColors.DEEP_TEAL.getColor()),
                "Send",
                Resources.regular15
        );
        send.onClick((screen, mouseButton) -> this.sendCurrentMessage());
        this.addToList(send);

        Exit exit;
        this.addToList(exit = new Exit(this, "exit", PANEL_WIDTH - 43, 14));
        exit.onClick((screen, mouseButton) -> this.close());
        this.setListening(false);
    }

    @Override
    public void keyPressed(int keyCode) {
        if ((keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) && this.input.isFocused()) {
            this.sendCurrentMessage();
            return;
        }

        super.keyPressed(keyCode);
    }

    @Override
    public void draw(float partialTicks) {
        super.draw(partialTicks);
        float alpha = this.field21149.calcPercent();

        RenderUtil.drawRoundedRect2(
                (float) (this.xA + 22),
                (float) (this.yA + 58),
                (float) (this.widthA - 44),
                (float) (this.heightA - 128),
                RenderUtil2.applyAlpha(-3618616, 0.65F * alpha)
        );
        RenderUtil.method11429(
                (float) (this.xA + 22),
                (float) (this.yA + 58),
                (float) (this.xA + this.widthA - 22),
                (float) (this.yA + this.heightA - 70),
                1,
                RenderUtil2.applyAlpha(-2500135, 0.8F * alpha)
        );
        RenderUtil.drawHybridString(
                Resources.regular15,
                (float) (this.xA + 30),
                (float) (this.yA + 36),
                IRCChatGuiUtil.ellipsize(this.getStatusText(), Resources.regular15, this.widthA - 100),
                RenderUtil2.applyAlpha(ClientColors.DEEP_TEAL.getColor(), 0.55F * alpha)
        );
        this.drawMessages(alpha);
    }

    private void drawMessages(float alpha) {
        int left = this.xA + 32;
        int top = this.yA + 68;
        int width = this.widthA - 64;
        int height = this.heightA - 148;
        List<MessageLine> lines = this.collectVisibleLines(width, height);

        RenderUtil.startScissor(left, top, left + width, top + height, true);
        int y = top + 3;
        for (MessageLine line : lines) {
            int color = line.system
                    ? RenderUtil2.applyAlpha(ClientColors.DEEP_TEAL.getColor(), 0.45F * alpha)
                    : RenderUtil2.applyAlpha(ClientColors.DEEP_TEAL.getColor(), 0.86F * alpha);
            RenderUtil.drawHybridString(Resources.regular15, (float) left, (float) y, line.text, color);
            y += MESSAGE_LINE_HEIGHT;
        }
        RenderUtil.restoreScissor();
    }

    private List<MessageLine> collectVisibleLines(int width, int height) {
        int maxLines = Math.max(1, height / MESSAGE_LINE_HEIGHT);
        List<MessageLine> visible = new ArrayList<>();
        List<IRCChatHistory.Entry> entries = IRCChatHistory.snapshot();
        TrueTypeFont font = Resources.regular15;

        for (int i = entries.size() - 1; i >= 0 && visible.size() < maxLines; i--) {
            IRCChatHistory.Entry entry = entries.get(i);
            List<String> wrapped = IRCChatGuiUtil.wrapForWidth(entry.getDisplayText(), font, width);
            for (int j = wrapped.size() - 1; j >= 0 && visible.size() < maxLines; j--) {
                visible.add(0, new MessageLine(wrapped.get(j), entry.getType() == IRCChatHistory.Type.SYSTEM));
            }
        }

        return visible;
    }

    private void sendCurrentMessage() {
        String message = IRCChatHistory.sanitizeMessage(this.input.getText());
        if (message.isEmpty()) {
            return;
        }

        if (IRCManager.isConnected()) {
            IRCManager.sendMessage(message);
        } else {
            IRCUtlis.printMessage("[IRC] Not connected to server");
        }

        this.input.setText("");
        this.input.method13148();
    }

    private String getStatusText() {
        IRCClient client = IRCManager.getIRCClient();
        if (client != null && client.getConnection() != null && client.getConnection().isConnected()) {
            return "Connected to " + client.getConnection().getServerAddress();
        }

        return "Disconnected";
    }

    private void close() {
        if (this.closeAction != null) {
            this.closeAction.run();
        }
    }

    private record MessageLine(String text, boolean system) {
    }
}
