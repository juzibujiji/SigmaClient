package com.mentalfrostbyte.jello.gui.combined.impl;

import com.mentalfrostbyte.jello.util.client.render.theme.ClientColors;
import com.mentalfrostbyte.jello.util.game.render.RenderUtil2;
import com.mentalfrostbyte.jello.util.game.render.RenderUtil;
import com.mentalfrostbyte.jello.util.client.render.Resources;
import org.newdawn.slick.opengl.Texture;
import com.mojang.blaze3d.matrix.MatrixStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.LoadingGui;
import net.minecraft.resources.IAsyncReloader;
import net.minecraft.util.Util;
import org.lwjgl.opengl.GL11;

import java.util.Optional;
import java.util.function.Consumer;

public class LoadingScreen extends LoadingGui {

    private final Minecraft client;
    private final IAsyncReloader reloadMonitor;
    private final Consumer<Optional<Throwable>> exceptionHandler;
    private final boolean reloading;

    public static Texture sigmaLogo;
    public static Texture back;
    public static Texture background;

    private float progress;
    private long applyCompleteTime = -1L;
    private long prepareCompleteTime = -1L;

    public LoadingScreen(Minecraft client, IAsyncReloader monitor, Consumer<Optional<Throwable>> exceptionHandler, boolean reloading) {
        this.client = client;
        this.reloadMonitor = monitor;
        this.exceptionHandler = exceptionHandler;
        this.reloading = reloading;

        sigmaLogo = Resources.loadTexture("com/mentalfrostbyte/gui/resources/sigma/logo.png");
        back = Resources.loadTexture("com/mentalfrostbyte/gui/resources/loading/back.png");
        background = Resources.createScaledAndProcessedTexture2("com/mentalfrostbyte/gui/resources/loading/back.png", 0.25F, 25);
    }

    @Override
    public void render(MatrixStack matrices, int mouseX, int mouseY, float delta) {
        long var9 = Util.milliTime();
        if (this.reloading && (this.reloadMonitor.asyncPartDone() || this.client.currentScreen != null) && this.prepareCompleteTime == -1L) {
            this.prepareCompleteTime = var9;
        }

        float var11 = this.applyCompleteTime > -1L ? (float)(var9 - this.applyCompleteTime) / 200.0F : -1.0F;
        float var12 = this.prepareCompleteTime > -1L ? (float)(var9 - this.prepareCompleteTime) / 100.0F : -1.0F;
        float var13 = 1.0F;
        float var16 = this.reloadMonitor.estimateExecutionSpeed();
        this.progress = this.progress * 0.95F + var16 * 0.050000012F;
        GL11.glPushMatrix();
        float var17 = 1111.0F;
        if (this.client.getMainWindow().getWidth() != 0) {
            var17 = (float)(this.client.getMainWindow().getFramebufferWidth() / this.client.getMainWindow().getWidth());
        }

        float var18 = (float)this.client.getMainWindow().calcGuiScale(this.client.gameSettings.guiScale, this.client.getForceUnicodeFont()) * var17;
        GL11.glScalef(1.0F / var18, 1.0F / var18, 0.0F);
        xd(var13, this.progress);
        GL11.glPopMatrix();
        if (var11 >= 2.0F) {
            this.client.setLoadingGui(null);
        }

        if (this.applyCompleteTime == -1L && this.reloadMonitor.fullyDone() && (!this.reloading || var12 >= 2.0F)) {
            try {
                this.reloadMonitor.join();
                this.exceptionHandler.accept(Optional.empty());
            } catch (Throwable var20) {
                this.exceptionHandler.accept(Optional.of(var20));
            }

            this.applyCompleteTime = Util.milliTime();
            if (this.client.currentScreen != null) {
                this.client.currentScreen.init(this.client, this.client.getMainWindow().getScaledWidth(), this.client.getMainWindow().getScaledHeight());
            }
        }
    }

    public static void xd(float bgOpacity, float var1) {
        GL11.glEnable(3008);
        GL11.glEnable(3042);
        RenderUtil.drawImage(0.0F, 0.0F, (float) Minecraft.getInstance().getMainWindow().getWidth(), (float) Minecraft.getInstance().getMainWindow().getHeight(), background, bgOpacity);
        RenderUtil.drawRoundedRect2(0.0F, 0.0F, (float) Minecraft.getInstance().getMainWindow().getWidth(), (float) Minecraft.getInstance().getMainWindow().getHeight(), RenderUtil2.applyAlpha(0, 0.75F));
        int var4 = 455;
        int var5 = 78;
        int var6 = (Minecraft.getInstance().getMainWindow().getWidth() - var4) / 2;
        int var7 = Math.round((float)((Minecraft.getInstance().getMainWindow().getHeight() - var5) / 2) - 14.0F * bgOpacity);
        float var8 = 0.75F + bgOpacity * bgOpacity * bgOpacity * bgOpacity * 0.25F;
        GL11.glPushMatrix();
        GL11.glTranslatef((float)(Minecraft.getInstance().getMainWindow().getWidth() / 2), (float)(Minecraft.getInstance().getMainWindow().getHeight() / 2), 0.0F);
        GL11.glScalef(var8, var8, 0.0F);
        GL11.glTranslatef((float)(-Minecraft.getInstance().getMainWindow().getWidth() / 2), (float)(-Minecraft.getInstance().getMainWindow().getHeight() / 2), 0.0F);
        RenderUtil.drawImage((float)var6, (float)var7, (float)var4, (float)var5, sigmaLogo, RenderUtil2.applyAlpha(ClientColors.LIGHT_GREYISH_BLUE.getColor(), bgOpacity));
        float var9 = Math.min(1.0F, var1 * 1.02F);
        float var11 = 80;
        if (bgOpacity == 1.0F) {
            RenderUtil.drawRoundedRect(
                    (float)var6, var7 + var5 + var11, (float)var4, 20.0F, 10.0F, RenderUtil2.applyAlpha(ClientColors.LIGHT_GREYISH_BLUE.getColor(), 0.3F * bgOpacity)
            );
            RenderUtil.drawRoundedRect(
                    (float)(var6 + 1),
					var7 + var5 + var11 + 1,
                    (float)(var4 - 2),
                    18.0F,
                    9.0F,
                    RenderUtil2.applyAlpha(ClientColors.DEEP_TEAL.getColor(), bgOpacity)
            );
        }

        RenderUtil.drawRoundedRect(
                (float)(var6 + 2),
				var7 + var5 + var11 + 2,
                (float)((int)((float)(var4 - 4) * var9)),
                16.0F,
                8.0F,
                RenderUtil2.applyAlpha(ClientColors.LIGHT_GREYISH_BLUE.getColor(), 0.9F * bgOpacity)
        );
        GL11.glPopMatrix();
    }
}
