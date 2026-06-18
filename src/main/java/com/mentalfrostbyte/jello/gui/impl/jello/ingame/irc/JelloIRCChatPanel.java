package com.mentalfrostbyte.jello.gui.impl.jello.ingame.irc;

import com.mentalfrostbyte.jello.gui.base.elements.impl.button.Button;
import com.mentalfrostbyte.jello.gui.base.elements.impl.image.types.SmallImage;
import com.mentalfrostbyte.jello.gui.combined.AnimatedIconPanel;
import com.mentalfrostbyte.jello.gui.combined.CustomGuiScreen;
import com.mentalfrostbyte.jello.gui.impl.irc.IRCChatGuiUtil;
import com.mentalfrostbyte.jello.gui.impl.jello.buttons.TextField;
import com.mentalfrostbyte.jello.module.impl.gui.jello.IRCClient;
import com.mentalfrostbyte.jello.module.impl.gui.jello.irc.IRCChatHistory;
import com.mentalfrostbyte.jello.module.impl.gui.jello.irc.IRCManager;
import com.mentalfrostbyte.jello.module.impl.gui.jello.irc.IRCUtlis;
import com.mentalfrostbyte.jello.util.client.render.FontSizeAdjust;
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

public class JelloIRCChatPanel extends AnimatedIconPanel {
    public static final int PANEL_WIDTH = 520;
    public static final int PANEL_HEIGHT = 360;
    private static final int HEADER_HEIGHT = 58;
    private static final int FOOTER_HEIGHT = 62;
    private static final int MESSAGE_LINE_HEIGHT = 18;

    private final TextField input;
    private final Runnable closeAction;

    public JelloIRCChatPanel(CustomGuiScreen parent, String name, int x, int y, Runnable closeAction) {
        super(parent, name, x, y, PANEL_WIDTH, PANEL_HEIGHT, false);
        this.closeAction = closeAction;
        this.method13215(true);
        this.saveSize = true;

        ColorHelper inputColor = new ColorHelper(
                RenderUtil2.applyAlpha(ClientColors.LIGHT_GREYISH_BLUE.getColor(), 0.18F),
                RenderUtil2.applyAlpha(ClientColors.LIGHT_GREYISH_BLUE.getColor(), 0.25F),
                RenderUtil2.applyAlpha(ClientColors.LIGHT_GREYISH_BLUE.getColor(), 0.25F),
                ClientColors.LIGHT_GREYISH_BLUE.getColor(),
                FontSizeAdjust.field14488,
                FontSizeAdjust.NEGATE_AND_DIVIDE_BY_2
        );
        this.addToList(this.input = new TextField(this, "message", 22, PANEL_HEIGHT - 48, PANEL_WIDTH - 112, 34, inputColor, "", "Message..."));
        this.input.setFont(ResourceRegistry.LyricsFont);

        ColorHelper buttonColor = new ColorHelper(
                RenderUtil2.applyAlpha(ClientColors.DEEP_TEAL.getColor(), 0.55F),
                RenderUtil2.applyAlpha(ClientColors.LIGHT_GREYISH_BLUE.getColor(), 0.22F),
                RenderUtil2.applyAlpha(ClientColors.LIGHT_GREYISH_BLUE.getColor(), 0.22F),
                ClientColors.LIGHT_GREYISH_BLUE.getColor()
        ).method19414(FontSizeAdjust.NEGATE_AND_DIVIDE_BY_2).method19412(FontSizeAdjust.NEGATE_AND_DIVIDE_BY_2);
        Button send = new Button(this, "send", PANEL_WIDTH - 82, PANEL_HEIGHT - 48, 60, 34, buttonColor, "Send", ResourceRegistry.JelloLightFont14);
        send.field20586 = 12;
        send.onClick((screen, mouseButton) -> this.sendCurrentMessage());
        this.addToList(send);

        SmallImage close = new SmallImage(
                this,
                "close",
                PANEL_WIDTH - 42,
                18,
                22,
                22,
                Resources.xmark,
                new ColorHelper(ClientColors.LIGHT_GREYISH_BLUE.getColor(), ClientColors.DEEP_TEAL.getColor())
        );
        close.onClick((screen, mouseButton) -> this.close());
        this.addToList(close);
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
        float alpha = Math.min(1.0F, Math.max(0.0F, partialTicks));
        RenderUtil.drawRoundedRect((float) this.xA, (float) this.yA, (float) this.widthA, (float) this.heightA, 16.0F, 0.65F * alpha);
        RenderUtil.drawRoundedRect(
                (float) this.xA,
                (float) this.yA,
                (float) this.widthA,
                (float) this.heightA,
                16.0F,
                RenderUtil2.applyAlpha(ClientColors.DEEP_TEAL.getColor(), 0.88F * alpha)
        );
        RenderUtil.drawRoundedRect(
                (float) this.xA,
                (float) this.yA,
                (float) (this.xA + this.widthA),
                (float) (this.yA + HEADER_HEIGHT),
                RenderUtil2.applyAlpha(ClientColors.LIGHT_GREYISH_BLUE.getColor(), 0.08F * alpha)
        );
        RenderUtil.drawHybridString(
                ResourceRegistry.JelloMediumFont25,
                (float) (this.xA + 22),
                (float) (this.yA + 17),
                "IRC Chat",
                RenderUtil2.applyAlpha(ClientColors.LIGHT_GREYISH_BLUE.getColor(), alpha)
        );
        RenderUtil.drawHybridString(
                ResourceRegistry.JelloLightFont14,
                (float) (this.xA + 130),
                (float) (this.yA + 27),
                IRCChatGuiUtil.ellipsize(this.getStatusText(), ResourceRegistry.JelloLightFont14, PANEL_WIDTH - 190),
                RenderUtil2.applyAlpha(ClientColors.LIGHT_GREYISH_BLUE.getColor(), 0.55F * alpha)
        );

        this.drawMessages(alpha);
        super.draw(alpha);
    }

    private void drawMessages(float alpha) {
        int left = this.xA + 22;
        int top = this.yA + HEADER_HEIGHT + 12;
        int width = this.widthA - 44;
        int height = this.heightA - HEADER_HEIGHT - FOOTER_HEIGHT - 22;
        List<MessageLine> lines = this.collectVisibleLines(width, height);

        RenderUtil.startScissor(left, top, left + width, top + height, true);
        int y = top + 4;
        for (MessageLine line : lines) {
            int color = line.system
                    ? RenderUtil2.applyAlpha(ClientColors.LIGHT_GREYISH_BLUE.getColor(), 0.48F * alpha)
                    : RenderUtil2.applyAlpha(ClientColors.LIGHT_GREYISH_BLUE.getColor(), 0.86F * alpha);
            RenderUtil.drawHybridString(ResourceRegistry.JelloLightFont14, (float) left, (float) y, line.text, color);
            y += MESSAGE_LINE_HEIGHT;
        }
        RenderUtil.restoreScissor();
    }

    private List<MessageLine> collectVisibleLines(int width, int height) {
        int maxLines = Math.max(1, height / MESSAGE_LINE_HEIGHT);
        List<MessageLine> visible = new ArrayList<>();
        List<IRCChatHistory.Entry> entries = IRCChatHistory.snapshot();
        TrueTypeFont font = ResourceRegistry.JelloLightFont14;

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
        } else {
            this.setSelfVisible(false);
        }
    }

    private record MessageLine(String text, boolean system) {
    }
}
