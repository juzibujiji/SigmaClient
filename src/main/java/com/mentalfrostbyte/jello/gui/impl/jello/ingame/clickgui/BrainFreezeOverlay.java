package com.mentalfrostbyte.jello.gui.impl.jello.ingame.clickgui;

import com.mentalfrostbyte.jello.gui.combined.CustomGuiScreen;
import com.mentalfrostbyte.jello.gui.combined.AnimatedIconPanel;
import com.mentalfrostbyte.jello.util.game.render.RenderUtil2;
import com.mentalfrostbyte.jello.util.game.render.RenderUtil;
import com.mentalfrostbyte.jello.util.system.math.RandomIntGenerator;
import com.mentalfrostbyte.jello.managers.AnimationManager;
import net.minecraft.client.Minecraft;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public class BrainFreezeOverlay extends AnimatedIconPanel {
    private final List<ParticleEffect> particles = new ArrayList<>();
    private final AnimationManager animationManager = new AnimationManager();
    public RandomIntGenerator random = new RandomIntGenerator();

    public BrainFreezeOverlay(CustomGuiScreen parentScreen, String name) {
        super(parentScreen, name, 0, 0, Minecraft.getInstance().getMainWindow().getWidth(), Minecraft.getInstance().getMainWindow().getHeight(), false);
        this.setFocused(false);
        this.setHovered(false);
        this.setReAddChildren(false);
        this.method13294(true);
        this.setListening(false);
    }

    @Override
    public void setFocused(boolean focused) {
        super.setFocused(false);
    }

    @Override
    public void updatePanelDimensions(int newHeight, int newWidth) {
        super.updatePanelDimensions(newHeight, newWidth);
    }

    @Override
    public void draw(float partialTicks) {
        int scaledWidth = Minecraft.getInstance().getMainWindow().getScaledWidth();
        int scaledHeight = Minecraft.getInstance().getMainWindow().getScaledHeight();
        int halfScreenWidth = scaledWidth / 2;

        boolean shouldUpdate;
        for (shouldUpdate = false; this.particles.size() < halfScreenWidth; shouldUpdate = true) {
            this.particles.add(new ParticleEffect((float) this.random.nextInt(scaledWidth), (float) this.random.nextInt(scaledHeight)));
        }

        while (this.particles.size() > halfScreenWidth) {
            this.particles.remove(0);
            shouldUpdate = true;
        }

        if (shouldUpdate) {
            for (ParticleEffect particle : this.particles) {
                particle.initialXPosition = (float) this.random.nextInt(scaledWidth);
                particle.yPosition = (float) this.random.nextInt(scaledHeight);
            }
        }

        this.animationManager.update();

        for (ParticleEffect particle : this.particles) {
            particle.updatePosition(this.animationManager);
            if (!(particle.initialXPosition < 0.0F)) {
                if (particle.initialXPosition > (float) scaledWidth) {
                    particle.initialXPosition = 0.0F;
                }
            } else {
                particle.initialXPosition = (float) scaledWidth;
            }

            if (!(particle.yPosition < 0.0F)) {
                if (particle.yPosition > (float) scaledHeight) {
                    particle.yPosition = 0.0F;
                }
            } else {
                particle.yPosition = (float) scaledHeight;
            }

            particle.render(partialTicks);
        }

        super.draw(partialTicks);
    }

    public static class ParticleEffect {
        private float xPosition;
        public float initialXPosition;
        public float yPosition;
        public float size;
        private float movementSpeed;
        private final RandomIntGenerator random = new RandomIntGenerator();
        public float direction;
        public Color color = new Color(1.0F, 1.0F, 1.0F, 0.5F);

        public ParticleEffect(float x, float y) {
            this.initialXPosition = this.xPosition = x;
            this.yPosition = y;
            this.size = (float) this.random.nextInt(1, 3) + this.random.nextFloat();
            this.initialize();
        }

        private void initialize() {
            float maxMovement = 1.0F;
            this.movementSpeed = this.random.nextFloat() % maxMovement;
            this.direction = this.random.nextFloat() / 2.0F;
            if (this.random.nextBoolean()) {
                this.direction *= -1.0F;
            }
        }

        public void render(float partialTicks) {
            RenderUtil.drawCircle(
                    this.initialXPosition * 2.0F, this.yPosition * 2.0F, this.size * 2.0F, RenderUtil2.applyAlpha(this.color.getRGB(), partialTicks * 0.7F)
            );
        }

        public void updatePosition(AnimationManager animationManager) {
            this.initialXPosition = this.initialXPosition + animationManager.getCurrentValue() + this.direction;
            this.xPosition = this.xPosition + animationManager.getCurrentValue() + this.direction;
            this.yPosition = this.yPosition + this.movementSpeed;
        }
    }
}
