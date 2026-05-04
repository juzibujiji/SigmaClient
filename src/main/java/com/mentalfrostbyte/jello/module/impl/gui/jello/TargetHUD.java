package com.mentalfrostbyte.jello.module.impl.gui.jello;

import com.mentalfrostbyte.jello.event.impl.game.render.EventRender2D;
import com.mentalfrostbyte.jello.event.impl.player.EventRunTicks;
import com.mentalfrostbyte.jello.module.Module;
import com.mentalfrostbyte.jello.module.RenderModule;
import com.mentalfrostbyte.jello.module.data.ModuleCategory;
import com.mentalfrostbyte.jello.event.impl.game.render.EventRender2DOffset;
import com.mentalfrostbyte.jello.module.impl.combat.KillAura;
import com.mentalfrostbyte.jello.module.settings.impl.BooleanSetting;
import com.mentalfrostbyte.jello.module.settings.impl.ModeSetting;
import com.mentalfrostbyte.jello.module.settings.impl.NumberSetting;
import com.mentalfrostbyte.jello.util.client.render.ResourceRegistry;
import com.mentalfrostbyte.jello.util.client.render.theme.ClientColors;
import com.mentalfrostbyte.jello.util.game.render.PartialTicksAnim;
import com.mentalfrostbyte.jello.util.game.render.RenderUtil;
import com.mentalfrostbyte.Client;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.AbstractGui;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.network.play.NetworkPlayerInfo;
import net.minecraft.entity.LivingEntity;
import net.minecraft.client.renderer.entity.EntityRendererManager;
import net.minecraft.client.renderer.IRenderTypeBuffer;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.scoreboard.Score;
import net.minecraft.scoreboard.ScoreObjective;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.util.math.vector.Quaternion;
import net.minecraft.util.math.vector.Vector3f;
import com.mojang.blaze3d.matrix.MatrixStack;
import com.mojang.blaze3d.systems.RenderSystem;
import org.lwjgl.opengl.GL11;
import team.sdhq.eventBus.annotations.EventTarget;

import java.awt.*;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class TargetHUD extends RenderModule {
    public NumberSetting<Float> x = new NumberSetting<>("X", "X", 0,  0,10000,10) {
        @Override
        public boolean isHidden() {
            return true;
        }
    };
    public NumberSetting<Float> y = new NumberSetting<>("Y", "Y", 0,  0,10000, 10) {
        @Override
        public boolean isHidden() {
            return true;
        }
    };
    private BooleanSetting healthBypass = new BooleanSetting("Health Bypass", "",false);
    private ModeSetting animation = new ModeSetting("Animation","", "Jello", "Jello", "Smooth", "Ease");
    LivingEntity target;
    PartialTicksAnim scale;
    PartialTicksAnim alpha;
    PartialTicksAnim health;
    PartialTicksAnim mhealth;

    @Override
    public void setX(final float v) {
        this.x.currentValue = v;
    }

    @Override
    public void setY(final float v) {
        this.y.currentValue = v;
    }

    @Override
    public float getX() {
        return this.x.currentValue;
    }

    @Override
    public float getY() {
        return this.y.currentValue;
    }

    public TargetHUD() {
        super(ModuleCategory.GUI,"TargetHud", "Target HUD");
        this.target = null;
        this.scale = new PartialTicksAnim(0.0f);
        this.alpha = new PartialTicksAnim(0.0f);
        this.health = new PartialTicksAnim(0.0f);
        this.mhealth = new PartialTicksAnim(0.0f);
        this.registerSetting(x,y,healthBypass,animation);
    }

    @EventTarget
    public void onWindowUpdateEvent(final EventRunTicks event) {
        boolean s = false;
        if (KillAura.targetEntity instanceof PlayerEntity) {
            this.target = (LivingEntity) KillAura.targetEntity;
        } else if (TargetHUD.mc.currentScreen instanceof ChatScreen) {
            this.target = TargetHUD.mc.player;
        } else {
            s = true;
            if (this.scale.getValue() == 0.0f) {
                this.target = null;
            }
        }
        this.alpha.interpolate(s ? 0.0f : 10.0f, -1.0);
        this.scale.interpolate(s ? 0.0f : 10.0f, -0.6);
        if (this.target != null) {
            final float health = this.getHealth();
            this.health.interpolate(Math.max(Math.min(health / this.target.getMaxHealth(), 1.0f), 0.0f), 3.0);
            this.mhealth.interpolate(Math.max(Math.min(this.target.getAbsorptionAmount() / this.target.getMaxHealth(), 1.0f), 0.0f), 3.0);
        }
    }

    private float getHealth() {
        float health = this.target.getHealth();
        if (healthBypass.getCurrentValue()) {
            final LivingEntity target = this.target;
            if (target instanceof final PlayerEntity player) {
                final Scoreboard scoreboard = player.getWorldScoreboard();
                final ScoreObjective scoreobjective = scoreboard.getObjectiveInDisplaySlot(2);
                if (scoreobjective != null) {
                    final Score score = scoreboard.getOrCreateScore(player.getScoreboardName(), scoreobjective);
                    health = (float) score.getScorePoints();
                }
            }
        }
        return health;
    }

    public boolean isHover(final double mx, final double my) {
        final double x = this.x.getCurrentValue();
        final double y = this.y.getCurrentValue();
        final double width = 60.0;
        final double height = 40.0;
        return isClickable(x - width, y, x + width, y + height, mx, my);
    }

    public static boolean isClickable(double x, double y, double dx, double dy, double mx, double my){
        return mx >= x && mx <= dx && my >= y && my <= dy;
    }
    /*

    @EventTarget
    public void onRenderEvent(final RenderEvent event) {
        if (this.target != null) {
            final float health = this.getHealth();
            double scale = this.scale.getValue() / 10.0;
            final double alpha = this.alpha.getValue() / 10.0;
            final String s = TargetHUD.animation.getValue();
            switch (s) {
                case "Jello": {
                    scale = SomeAnim.interpolate((float) scale, 0.17, 1.0, 0.51, 1.0);
                    scale *= 1.1799999475479126;
                    if (scale > 1.090000033378601) {
                        scale = 1.18 - scale + 1.0;
                        break;
                    }
                    break;
                }
            }
            if (Minecraft.getInstance().isF3Enabled()) {
                return;
            }
            boolean enableBlur = Shader.isEnable();
            // Initialize Color Variables
            int tR = smoothAnimation(JelloTabGUI.tRed, JelloTabGUI.lasttRed);
            int tG = smoothAnimation(JelloTabGUI.tGreen, JelloTabGUI.lasttGreen);
            int tB = smoothAnimation(JelloTabGUI.tBlue, JelloTabGUI.lasttBlue);

            int bR = smoothAnimation(JelloTabGUI.bRed, JelloTabGUI.lastbRed);
            int bG = smoothAnimation(JelloTabGUI.bGreen, JelloTabGUI.lastbGreen);
            int bB = smoothAnimation(JelloTabGUI.bBlue, JelloTabGUI.lastbBlue);

            GlStateManager.resetColor();
            final double x = this.x.getValue().floatValue();
            final double y = this.y.getValue().floatValue();
            final double width = 60.0;
            final double height = 40.0;
            GL11.glPushMatrix();
            GlStateManager.translate(x, y + height / 2.0, 0.0);
            GlStateManager.scale(scale, scale, 1.0);
            GlStateManager.translate(-x, -(y + height / 2.0), 0.0);
            RenderUtils.drawShadowWithAlpha((float) (x - width), (float) y, (float) (width * 2.0), (float) height, 7.0f, 0.5f);
            if (enableBlur) {
                StencilUtil.initStencilToWrite();
                RenderUtils.drawRect((float) (x - width), (float) y, (float) (x - width) + (float) width * 2.0f, (float) y + height, new Color(255, 255, 255, (int) (255.0 * alpha)).getRGB());
                StencilUtil.readStencilBuffer(1);
                JelloSwapBlur.applyBlurEffect();
                StencilUtil.uninitStencilBuffer();
            } else {
                drawGradientRect(
                        (float) (x - width),
                        (float) y,
                        (float) (x - width) + (float) width * 2.0f,
                        (float) y + height,
                        new Color(tR, tG, tB, (int) (255.0 * alpha)).getRGB(),
                        new Color(bR, bG, bB, (int) (255.0 * alpha)).getRGB()
                );
            }
            final NetworkPlayerInfo info = TargetHUD.mc.getConnection().getPlayerInfo(this.target.getUniqueID());
            if (info != null) {
                RenderUtils.resetColor();
                GL11.glColor4f(1.0f, 1.0f, 1.0f, 1.0f);
                TargetHUD.mc.getTextureManager().bindTexture(info.getLocationSkin());
                StencilUtil.initStencilToWrite();
                RenderUtils.drawRoundedRect((float) (x - width + 4.0), (float) (y + 4.0), (float) (x - width + 4.0) + 32.0f, (float) (y + 4.0) + 32.0f, 4.0f, new Color(255, 255, 255, (int) (255.0 * alpha)).getRGB());
                StencilUtil.readStencilBuffer(1);
                AbstractGui.drawScaledCustomSizeModalRect((float) (x - width + 4.0), (float) (y + 4.0), 8.0f, 8.0f, 8.0f, 8.0f, 32.0f, 32.0f, 64.0f, 64.0f);
                StencilUtil.uninitStencilBuffer();
            }
            String n = this.target.getName().getUnformattedComponentText();
            if (n.length() > 10) {
                n = n.substring(0, 10) + "...";
            }
            JelloFontUtil.jelloFontBold18.drawString(n, (float) (x - width + 5.0 + 32.0 + 5.0), (float) y + 7.0f - 1.0f, new Color(230, 230, 230, (int) (255.0 * alpha)).getRGB());
            GL11.glPushMatrix();
            int index = 0;
            final float offX = -2.0f;
            final ArrayList<ItemStack> stackList = new ArrayList<ItemStack>();
            stackList.add(this.target.getHeldItemMainhand());
            stackList.add(this.target.getHeldItemOffhand());
            stackList.addAll((Collection) this.target.getArmorInventoryList());
            for (final ItemStack a : stackList) {
                RoundRectShader.drawRoundRect((int) (x - width + 5.0 + 32.0 + 5.0) + index * 9 + 2 + offX, (float) ((int) y + 16), 8.0f, 8.0f, 3.0f, new Color(255, 255, 255, (int) (50.0 * alpha)));
                GlStateManager.color(1.0f, 1.0f, 1.0f, (float) (alpha));
                TargetHUD.mc.ingameGUI.renderHotbarItemCustom((int) ((int) (x - width + 5.0 + 32.0 + 5.0) + index * 9 + offX), (int) y + 16 - 2, event.renderTime, TargetHUD.mc.player, a, 0.5f);
                ++index;
            }
            GL11.glPopMatrix();
            final double dd = (width * 2.0 - 5.0 - 32.0 - 5.0) * this.health.getValue() * 0.8999999761581421;
            final double dd2 = (width * 2.0 - 5.0 - 32.0 - 5.0) * 0.8999999761581421;
            final double dd3 = (width * 2.0 - 5.0 - 32.0 - 5.0) * this.mhealth.getValue() * 0.8999999761581421;
            RoundRectShader.drawRoundRect((float) (x - width + 5.0 + 32.0 + 5.0), (float) y + 7.0f + 11.0f + 8.0f + 2.0f, (float) dd2, 4.0f, 4.0f, new Color(30, 30, 30, (int) (255.0 * alpha * 0.800000011920929)));
            GradientGlowing.applyGradientCornerRL((float) (x - width + 5.0 + 32.0 + 5.0), (float) y + 7.0f + 11.0f + 8.0f + 2.0f, (float) dd, 4.0f, (float) Math.sqrt(alpha), ColorUtils.reAlpha(ColorChanger.getColor(0, 10), (int) (255.0 * Math.sqrt(alpha))), ColorUtils.reAlpha(ColorChanger.getColor(100, 10), (int) (255.0 * Math.sqrt(alpha))), 4.0f, 3.0f);
            GradientRoundRectShader.drawRoundRect((float) (x - width + 5.0 + 32.0 + 5.0), (float) y + 7.0f + 11.0f + 8.0f + 2.0f, (float) dd, 4.0f, 4.0f, ColorUtils.reAlpha(ColorChanger.getColor(0, 10), (int) (255.0 * Math.sqrt(alpha))), ColorUtils.reAlpha(ColorChanger.getColor(100, 10), (int) (255.0 * Math.sqrt(alpha))));
            if (dd3 != 0.0) {
                RoundedRectShader.drawRound((float) (x - width + 5.0 + 32.0 + 5.0), (float) y + 7.0f + 11.0f + 8.0f + 2.0f, (float) dd3, 4.0f, 4.0f, ColorUtils.reAlpha(ColorUtils.blend(ColorChanger.getColor(0, 10), new Color(255, 255, 255), 0.5), (int) (255.0 * Math.sqrt(alpha))));
            }
            JelloFontUtil.jelloFontBold14.drawCenteredString("" + Math.ceil(health), (float) ((float) (x - width + 5.0 + 32.0 + 5.0) + dd), (float) y + 7.0f + 11.0f + 17.0f, new Color(255, 255, 255, (int) (255.0 * alpha)).getRGB());
            GL11.glPopMatrix();
            RenderUtils.resetColor();
        }
    }*/
}