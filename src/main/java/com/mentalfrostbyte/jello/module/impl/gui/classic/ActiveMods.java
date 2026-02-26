package com.mentalfrostbyte.jello.module.impl.gui.classic;

import com.mentalfrostbyte.Client;
import com.mentalfrostbyte.jello.event.impl.game.render.EventRender2DOffset;
import com.mentalfrostbyte.jello.gui.base.animations.Animation;
import com.mentalfrostbyte.jello.module.Module;
import com.mentalfrostbyte.jello.module.data.ModuleCategory;
import com.mentalfrostbyte.jello.module.settings.Setting;
import com.mentalfrostbyte.jello.module.settings.impl.BooleanSetting;
import com.mentalfrostbyte.jello.module.settings.impl.ModeSetting;
import com.mentalfrostbyte.jello.util.client.ClientMode;
import com.mentalfrostbyte.jello.util.client.render.Resources;
import com.mentalfrostbyte.jello.util.game.render.RenderUtil;
import com.mentalfrostbyte.jello.util.system.math.smoothing.QuadraticEasing;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import org.lwjgl.opengl.GL11;
import org.newdawn.slick.Color;
import org.newdawn.slick.TrueTypeFont;
import team.sdhq.eventBus.annotations.EventTarget;

import java.util.Map.Entry;
import java.util.TreeMap;

public class ActiveMods extends Module {
	private TreeMap<Module, Animation> animations = new TreeMap<>();
	private TrueTypeFont bold18;
	private TrueTypeFont bold16;
	private Animation field23965;

	public ActiveMods() {
		super(ModuleCategory.RENDER, "ActiveMods", "Shows active mods");
		this.registerSetting(new ModeSetting("Outline", "Outline", 0, "All", "Left", "Right", "None"));
		this.registerSetting(new ModeSetting("Animation", "Animation", 0, "Smooth", "Slide", "Both", "None"));
		this.registerSetting(new BooleanSetting("Sound", "Toggle sound", true));
	}

	public static int method16860(ActiveMods activeMods, Module module) {
		return activeMods.getModAndSuffixWidth(module);
	}

	@Override
	public void initialize() {
		this.method16853();
		this.method16852();
	}

	private void method16852() {
		this.field23965 = new Animation(2000, 2000, Animation.Direction.FORWARDS);
	}

	private void method16853() {
		this.animations.clear();
		this.bold18 = Resources.bold18;
		this.bold16 = Resources.bold16;
		this.animations = new TreeMap<>(new SortBySuffix(this));
	}

	@EventTarget
	public void onRender(EventRender2DOffset event) {
		if (Client.getInstance().clientMode == ClientMode.JELLO) return;
		if (this.isEnabled() && mc.player != null) {
			String var4 = this.getStringSettingValueByName("Animation");
			String var5 = this.getStringSettingValueByName("Outline");
			this.method16855();
			if (this.field23965.calcPercent() == 1.0F) {
				this.field23965.updateStartTime(0.0F);
			}

			int var6 = -2;
			int var7 = Minecraft.getInstance().mainWindow.getWidth() - 2;
			int var8 = -2;
			int color;
			int color1 = new java.awt.Color(0, 192, 255, 255).getRGB();
			int var11 = -7;
			float var12 = this.field23965.calcPercent();

			for (Entry animationEntry : this.animations.entrySet()) {
				Animation anim = (Animation) animationEntry.getValue();
				Module mod = (Module) animationEntry.getKey();
				if (mod.isEnabled() || anim.calcPercent() != 1.0F && !var4.equalsIgnoreCase("None")) {
					color = java.awt.Color.HSBtoRGB(var12, 1.0F, 1.0F);
					color1 = java.awt.Color.HSBtoRGB(var12, 1.0F, 1.0F);
					int var19 = this.getModAndSuffixWidth(mod);
					int textHeight = this.bold18.getHeight(mod.getName()) + var8;
					float var21 = 1.0F - QuadraticEasing.easeOutQuad(anim.calcPercent(), 0.0F, 1.0F, 1.0F);
					if (var4.equalsIgnoreCase("Smooth") || var4.equalsIgnoreCase("Both")) {
						textHeight = (int) ((float) textHeight * var21);
					}

					RenderSystem.pushMatrix();
					if (var5.equalsIgnoreCase("Right")) {
						GL11.glTranslated(-3.0, 0.0, 0.0);
					}

					RenderUtil.drawRect(
							(float) (var7 - var19 - 3), (float) (var6 + 1), (float) (var7 + 2), (float) (var6 + textHeight + 1), new java.awt.Color(0, 0, 0, 150).getRGB()
					);
					if (!var5.equalsIgnoreCase("None")) {
						if (!var5.equalsIgnoreCase("All")) {
							if (!var5.equalsIgnoreCase("Left")) {
								if (var5.equalsIgnoreCase("Right")) {
									RenderUtil.drawRect((float) (var7 + 2), (float) (var6 + 1), (float) (var7 + 7), (float) (var6 + 1 + textHeight), color1);
								}
							} else {
								RenderUtil.drawRect((float) (var7 - var19 - 6), (float) (var6 + 1), (float) (var7 - var19 - 3), (float) (var6 + 1 + textHeight), color1);
							}
						} else {
							RenderUtil.drawRect((float) (var7 - var19 - 5), (float) (var6 + 1), (float) (var7 - var19 - 3), (float) (var6 + 1 + textHeight), color1);
							RenderUtil.drawRect((float) (var7 - var19 - 3), (float) (var6 + 1), (float) (var7 - var11 - 5), (float) (var6 + 3), color1);
						}
					}

					RenderSystem.clearCurrentColor();
					RenderSystem.enableBlend();
					if (var4.equalsIgnoreCase("Slide") || var4.equalsIgnoreCase("Both")) {
						GL11.glTranslated((float) var19 * QuadraticEasing.easeOutQuad(anim.calcPercent(), 0.0F, 1.0F, 1.0F), 0.0, 0.0);
					}

					RenderUtil.drawBlurredBackground(
							(float) (var7 - var19 - 3),
							(float) (var6 + 1),
							(float) var7,
							(float) (var6 + textHeight) - QuadraticEasing.easeOutQuad(anim.calcPercent(), 0.0F, 1.0F, 1.0F)
					);
					this.bold18.drawString((float) (var7 - var19), (float) var6, mod.getName(), new Color(color));
					this.bold16
							.drawString(
									(float) (var7 - this.bold16.getWidth(this.getModSuffix(mod))),
									(float) var6 + 1.6F,
									this.getModSuffix(mod),
									new Color(160, 160, 160)
							);
					RenderUtil.restoreScissor();
					RenderSystem.disableBlend();
					var6 += textHeight;
					RenderSystem.popMatrix();
					var11 = var19;
					var12 = (float) ((double) var12 + 0.0196078431372549);
					if (var12 > 1.0F) {
						var12 = 0.0F;
					}
				}
			}

			if (var5.equalsIgnoreCase("All") && var11 > 0) {
				RenderUtil.drawRect((float) (var7 - var11 - 5), (float) (var6 + 1), (float) (var7 + 2), (float) (var6 + 3), color1);
			}
		}
	}

	private void method16855() {
		if (this.animations.isEmpty()) {
			this.animations.clear();

			for (Module var4 : Client.getInstance().moduleManager.getModuleMap().values()) {
				if (var4 != this && var4.getCategory() != ModuleCategory.GUI) {
					Animation var5 = new Animation(200, 200, !var4.isEnabled() ? Animation.Direction.FORWARDS : Animation.Direction.BACKWARDS);
					var5.updateStartTime(!var4.isEnabled() ? 1.0F : 0.0F);
					this.animations.put(var4, var5);
					Setting var6 = var4.getSettingMap().get("Type");
					if (var6 == null) {
						var6 = var4.getSettingMap().get("Mode");
						if (var6 != null) {
							var6.addObserver(var1 -> this.method16853());
						}
					} else {
						var6.addObserver(var1 -> this.method16853());
					}
				}
			}
		}

		for (Entry var8 : this.animations.entrySet()) {
			Module var9 = (Module) var8.getKey();
			Animation var11 = (Animation) var8.getValue();
			var11.changeDirection(!var9.isEnabled() ? Animation.Direction.FORWARDS : Animation.Direction.BACKWARDS);
		}
	}

	private String getModSuffix(Module mod) {
		String suffix = "";
		if (mod.getStringSettingValueByName("Type") == null) {
			if (mod.getStringSettingValueByName("Mode") != null) {
				suffix = suffix + " " + mod.getStringSettingValueByName("Mode");
			}
		} else {
			suffix = suffix + " " + mod.getStringSettingValueByName("Type");
		}

		return suffix;
	}

	private int getModAndSuffixWidth(Module mod) {
		String name = mod.getName();
		String suffix = this.getModSuffix(mod);
		return this.bold18.getWidth(name) + this.bold16.getWidth(suffix);
	}
}