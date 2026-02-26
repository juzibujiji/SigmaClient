package com.mentalfrostbyte.jello.module.impl.gui.jello;

import com.mentalfrostbyte.Client;
import com.mentalfrostbyte.jello.event.impl.game.render.EventRender2DOffset;
import com.mentalfrostbyte.jello.event.impl.game.render.EventRenderChat;
import com.mentalfrostbyte.jello.module.Module;
import com.mentalfrostbyte.jello.module.data.ModuleCategory;
import com.mentalfrostbyte.jello.module.settings.impl.BooleanSetting;
import com.mentalfrostbyte.jello.module.settings.impl.ModeSetting;
import com.mentalfrostbyte.jello.util.client.render.ResourceRegistry;
import com.mentalfrostbyte.jello.util.client.render.theme.ClientColors;
import com.mentalfrostbyte.jello.util.game.render.RenderUtil;
import com.mojang.blaze3d.matrix.MatrixStack;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screen.IngameMenuScreen;
import net.minecraft.client.renderer.IRenderTypeBuffer;
import net.minecraft.client.renderer.entity.EntityRendererManager;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.AirItem;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.vector.Quaternion;
import net.minecraft.util.math.vector.Vector3f;
import org.lwjgl.opengl.GL11;
import team.sdhq.eventBus.annotations.EventTarget;

public class InfoHUD extends Module {

    public InfoHUD() {
        super(ModuleCategory.GUI, "Info HUD", "Shows a bunch of usefull stuff");
        this.registerSetting(new ModeSetting("Cords", "Coordinate display type", 1, "None", "Normal", "Precise"));
        this.registerSetting(new BooleanSetting("Show Player", "Renders a miniature version of your character", true));
        this.registerSetting(new BooleanSetting("Show Armor", "Shows your armor's status", true));
        this.registerSetting(new BooleanSetting("Move chat up", "Moves the chat gui up", true));
        this.setAvailableOnClassic(false);
    }

    @EventTarget
    public void onRenderChat(EventRenderChat eventRenderChat) {
        if (getBooleanValueFromSettingName("Move chat up")) {
            eventRenderChat.addOffset(-40);
        }
    }

    @EventTarget
    public void onRender2D(EventRender2DOffset event) {
        if (this.isEnabled() && mc.player != null) {
            if (!Minecraft.getInstance().gameSettings.hideGUI) {
                if (!(mc.currentScreen instanceof IngameMenuScreen)) {
                    int xOffset = 14;

                    if (this.getBooleanValueFromSettingName("Show Player")) {
                        int y = mc.mainWindow.getHeight() - 22;
                        if (Client.getInstance().musicManager.isPlayingSong())
                            y -= 105;

                        xOffset += this.renderPlayerModel(0, y, 114);
                    }

                    if (this.getBooleanValueFromSettingName("Show Armor")) {
                        int y = mc.mainWindow.getHeight() - 14;
                        if (Client.getInstance().musicManager.isPlayingSong())
                            y -= 105;

                        xOffset += this.renderArmorStatus(xOffset, y) + 10;
                    }

                    if (!this.getStringSettingValueByName("Cords").equals("None")) {
                        int y = 42;
                        if (Client.getInstance().musicManager.isPlayingSong())
                            y += 105;
                        xOffset += this.renderCoordinates(xOffset, y) + 10;
                    }
                }
            }
        }
    }

    public String getFormattedCoordinates(boolean precise) {
        return !precise
                ? Math.round(mc.player.getPosX()) + " " +
                Math.round(mc.player.getPosY()) + " " +
                Math.round(mc.player.getPosZ())
                : (float) Math.round(mc.player.getPosX() * 10.0) / 10.0F + " " +
                (float) Math.round(mc.player.getPosY() * 10.0) / 10.0F + " " +
                (float) Math.round(mc.player.getPosZ() * 10.0) / 10.0F;
    }

    public int renderCoordinates(int x, int yOffset) {
        String direction = "Facing South";
        String coordinates = this.getFormattedCoordinates(this.getStringSettingValueByName("Cords").equals("Precise"));
        RenderUtil.drawString(
                ResourceRegistry.JelloMediumFont20,
                (float) x,
                (float) (mc.mainWindow.getHeight() - yOffset),
                coordinates,
                RenderUtil.applyAlpha(ClientColors.LIGHT_GREYISH_BLUE.getColor(), 0.8F));
        return Math.max(ResourceRegistry.JelloLightFont20.getWidth(direction),
                ResourceRegistry.JelloMediumFont20.getWidth(coordinates));
    }

    public int renderArmorStatus(int x, int y) {
        int armorCount = 0;

        for (int i = 0; i < mc.player.inventory.armorInventory.size(); i++) {
            ItemStack armorPiece = mc.player.inventory.armorInventory.get(i);
            if (!(armorPiece.getItem() instanceof AirItem)) {
                armorCount++;
                int armorY = y - 32 * armorCount;
                RenderUtil.renderItem(armorPiece, x, armorY, 32, 32);
                GL11.glDisable(GL11.GL_LIGHTING);
                float durability = 1.0F - (float) armorPiece.getDamage() / (float) armorPiece.getMaxDamage();
                if (durability != 1.0F) {
                    RenderUtil.drawRect2((float) (x + 2), (float) (armorY + 28), 28.0F, 5.0F,
                            RenderUtil.applyAlpha(ClientColors.DEEP_TEAL.getColor(), 0.5F));
                    RenderUtil.drawRect2(
                            (float) (x + 2),
                            (float) (armorY + 28),
                            28.0F * durability,
                            3.0F,
                            RenderUtil.applyAlpha(durability > 0.2 ? ClientColors.DARK_SLATE_GREY.getColor()
                                    : ClientColors.PALE_YELLOW.getColor(), 0.9F));
                }
            }
        }
        return armorCount != 0 ? 32 : -7;
    }

    public int renderPlayerModel(int x, int y, int height) {
        drawEntityOnScreen(x + height / 2, y, height / 2, mc.player);
        return height - 24;
    }

    private void drawEntityOnScreen(int posX, int posY, int scale, LivingEntity livingEntity) {
        float fixedYaw = livingEntity.rotationYaw;
        float fixedPitch = livingEntity.rotationPitch;

        RenderSystem.pushMatrix();
        RenderSystem.translatef((float) posX, (float) posY, 1050.0F);
        RenderSystem.scalef(1.0F, 1.0F, -1.0F);

        MatrixStack matrixstack = new MatrixStack();
        matrixstack.translate(0.0D, 0.0D, 1000.0D);
        matrixstack.scale((float) scale, (float) scale, (float) scale);

        Quaternion quaternion = Vector3f.ZP.rotationDegrees(180.0F);
        matrixstack.rotate(quaternion);

        float f2 = livingEntity.renderYawOffset;
        float f3 = livingEntity.rotationYaw;
        float f4 = livingEntity.rotationPitch;
        float f5 = livingEntity.prevRotationYawHead;
        float f6 = livingEntity.rotationYawHead;

        livingEntity.renderYawOffset = f2;
        livingEntity.rotationYaw = fixedYaw;
        livingEntity.rotationPitch = fixedPitch;
        livingEntity.rotationYawHead = f6;
        livingEntity.prevRotationYawHead = f5;

        EntityRendererManager entityrenderermanager = Minecraft.getInstance().getRenderManager();
        entityrenderermanager.setCameraOrientation(new Quaternion(0, 0, 0, 1)); // Set no camera rotation
        entityrenderermanager.setRenderShadow(false);

        IRenderTypeBuffer.Impl irendertypebuffer$impl = Minecraft.getInstance().getRenderTypeBuffers().getBufferSource();
        RenderSystem.runAsFancy(() -> {
            entityrenderermanager.renderEntityStatic(livingEntity, 0.0D, 0.0D, 0.0D, 0.0F, 1.0F, matrixstack, irendertypebuffer$impl, 15728880);
        });

        irendertypebuffer$impl.finish();
        entityrenderermanager.setRenderShadow(true);

        livingEntity.renderYawOffset = f2;
        livingEntity.rotationYaw = f3;
        livingEntity.rotationPitch = f4;
        livingEntity.prevRotationYawHead = f5;
        livingEntity.rotationYawHead = f6;

        RenderSystem.popMatrix();
    }
}
