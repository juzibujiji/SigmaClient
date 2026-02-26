package com.mentalfrostbyte.jello.gui.combined.impl;

import com.mentalfrostbyte.Client;
import com.mentalfrostbyte.jello.util.client.ClientMode;
import com.mentalfrostbyte.jello.gui.base.animations.Animation;
import com.mentalfrostbyte.jello.gui.combined.CustomGuiScreen;
import com.mentalfrostbyte.jello.gui.base.elements.impl.critical.Screen;
import com.mentalfrostbyte.jello.gui.base.elements.impl.image.Image;
import com.mentalfrostbyte.jello.gui.base.elements.impl.image.types.FadedImage;
import com.mentalfrostbyte.jello.util.client.render.theme.ClientColors;
import com.mentalfrostbyte.jello.util.system.math.SmoothInterpolator;
import com.mentalfrostbyte.jello.util.game.render.RenderUtil2;
import com.mentalfrostbyte.jello.util.game.render.RenderUtil;
import com.mentalfrostbyte.jello.util.client.render.Resources;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screen.MainMenuHolder;
import net.minecraft.util.Util;

import java.net.MalformedURLException;
import java.net.URL;

public class SwitchScreen extends Screen {
    public static Animation anim = new Animation(1050, 200, Animation.Direction.BACKWARDS);
    public static float field21070;
    public static float field21071;

    public SwitchScreen() {
        super("Switch");
        this.setListening(false);
        Client.getInstance().clientMode = ClientMode.INDETERMINATE;

        int bigWidth = 537;
        int smallWidth = 264;
        int bigHeight = 93;
        int smallHeight = 61;
        int x = (this.getWidthA() - bigWidth) / 2;
        int y = (this.getHeightA() - bigHeight) / 2 + 14;

        FadedImage none;
        FadedImage jello;
        FadedImage classic;

        this.addToList(none = new FadedImage(this, "pb", x, y, bigWidth, bigHeight, Resources.noaddonsPNG));
        this.addToList(classic = new FadedImage(this, "pb2", x, bigHeight + y + 9, smallWidth, smallHeight, Resources.sigmaLigmaPNG));
        this.addToList(jello = new FadedImage(this, "pb3", x + smallWidth + 9, bigHeight + y + 9, smallWidth, smallHeight, Resources.jelloPNG));

        none.onClick((var0, var1) -> {
            Client.getInstance().setupClient(ClientMode.NOADDONS);
            Minecraft.getInstance().displayGuiScreen(new MainMenuHolder());
        });

        jello.onClick((var0, var1) -> {
            Client.getInstance().setupClient(ClientMode.JELLO);
            Minecraft.getInstance().displayGuiScreen(new MainMenuHolder());
        });

        classic.onClick((var0, var1) -> {
            Client.getInstance().setupClient(ClientMode.CLASSIC);
            Minecraft.getInstance().displayGuiScreen(new MainMenuHolder());
        });

        CustomGuiScreen socialButtonPanel = new CustomGuiScreen(this, "socialbtns", (this.getWidthA() - 174) / 2, this.getHeightA() - 70, 174, 34);
        Image youtubeBtn;

        socialButtonPanel.addToList(youtubeBtn = new Image(socialButtonPanel, "youtube", 0, 0, 65, 34, Resources.youtubePNG));
        Image redditBtn;

        socialButtonPanel.addToList(redditBtn = new Image(socialButtonPanel, "reddit", 85, 0, 36, 34, Resources.redditPNG));
        Image discordBtn;

        socialButtonPanel.addToList(discordBtn = new Image(socialButtonPanel, "guilded", 142, 0, 32, 34, Resources.guildedPNG));

        youtubeBtn.onClick((var0, var1) -> {
            try {
                Util.getOSType().openURL(new URL("https://jelloconnect.sigmaclient.cloud/urls/youtube.php"));
            } catch (MalformedURLException ignored) {
            }
        });

        redditBtn.onClick((var0, var1) -> {
            try {
                Util.getOSType().openURL(new URL("https://jelloconnect.sigmaclient.cloud/urls/reddit.php"));
            } catch (MalformedURLException ignored) {
            }
        });

        discordBtn.onClick((var0, var1) -> {
            try {
                Util.getOSType().openURL(new URL("https://jelloconnect.sigmaclient.cloud/urls/guilded.php"));
            } catch (MalformedURLException ignored) {
            }
        });

        this.addToList(socialButtonPanel);
    }

    @Override
    public void updatePanelDimensions(int newHeight, int newWidth) {
        float var5 = (float) newHeight - field21070;
        float var6 = (float) newWidth - field21071;
        field21070 += var5 * 0.09F;
        field21071 += var6 * 0.09F;
        super.updatePanelDimensions(newHeight, newWidth);
    }

    @Override
    public void draw(float partialTicks) {
        if (LoadingScreen.back != null) {
            anim.changeDirection(Animation.Direction.FORWARDS);
            int var4 = 40;
            float var5 = -field21070 / (float) Minecraft.getInstance().getMainWindow().getWidth();
            float var6 = -field21071 / (float) Minecraft.getInstance().getMainWindow().getHeight();
            RenderUtil.drawImage(
                    (float) var4 * var5,
                    (float) var4 * var6,
                    (float) (Minecraft.getInstance().getMainWindow().getWidth() + var4),
                    (float) (Minecraft.getInstance().getMainWindow().getHeight() + var4),
                    LoadingScreen.back
            );
            float var7 = SmoothInterpolator.interpolate(anim.calcPercent(), 0.16, 0.71, 0.0, 0.99);
            int var8 = (Minecraft.getInstance().getMainWindow().getWidth() - 455) / 2;
            int var9 = (int) ((float) ((Minecraft.getInstance().getMainWindow().getHeight() - 78) / 2 - 14) - 116.0F * var7);
            RenderUtil.drawRoundedRect2(
                    0.0F,
                    0.0F,
                    (float) Minecraft.getInstance().getMainWindow().getWidth(),
                    (float) Minecraft.getInstance().getMainWindow().getHeight(),
                    RenderUtil2.applyAlpha(ClientColors.DEEP_TEAL.getColor(), 0.3F)
            );
            super.draw(partialTicks);
            RenderUtil.drawImage(
                    0.0F,
                    0.0F,
                    (float) Minecraft.getInstance().getMainWindow().getWidth(),
                    (float) Minecraft.getInstance().getMainWindow().getHeight(),
                    LoadingScreen.background,
                    RenderUtil2.applyAlpha(ClientColors.LIGHT_GREYISH_BLUE.getColor(), 1.0F - anim.calcPercent())
            );
            RenderUtil.drawRoundedRect2(
                    0.0F,
                    0.0F,
                    (float) Minecraft.getInstance().getMainWindow().getWidth(),
                    (float) Minecraft.getInstance().getMainWindow().getHeight(),
                    RenderUtil2.applyAlpha(0, 0.75F * (1.0F - anim.calcPercent()))
            );
            RenderUtil.drawImage((float) var8, (float) var9, 455.0F, 78.0F, LoadingScreen.sigmaLogo);
        }
    }

    @Override
    public void keyPressed(int keyCode) {
        super.keyPressed(keyCode);
        if (keyCode == 256) {
            Minecraft.getInstance().displayGuiScreen(new MainMenuHolder());
        }
    }
}
