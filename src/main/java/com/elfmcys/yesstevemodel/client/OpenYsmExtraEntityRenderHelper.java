package com.elfmcys.yesstevemodel.client;

import com.elfmcys.yesstevemodel.YesSteveModel;
import com.elfmcys.yesstevemodel.client.animation.ActiveAnimationSet;
import com.elfmcys.yesstevemodel.client.animation.OpenYsmExtraEntityAnimationResolver;
import com.mojang.blaze3d.matrix.MatrixStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.IRenderTypeBuffer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.resources.IResourceManager;
import net.minecraft.util.ResourceLocation;

public final class OpenYsmExtraEntityRenderHelper {
    private OpenYsmExtraEntityRenderHelper() {
    }

    public static OpenYsmExtraEntityModel find(Entity entity, OpenYsmExtraEntityModel.Kind kind, String fallbackId) {
        if (entity == null || !YesSteveModel.getClientConfig().isRenderPlayers()) {
            return null;
        }

        OpenYsmBakedPlayerModel playerModel = findSourcePlayerModel(entity, kind);
        if (playerModel == null) {
            return null;
        }

        ResourceLocation entityId = EntityType.getKey(entity.getType());
        OpenYsmExtraEntityModel model = playerModel.findExtraEntityModel(kind, entityId == null ? "" : entityId.toString());
        if (model == null && fallbackId != null && !fallbackId.isEmpty()) {
            model = playerModel.findExtraEntityModel(kind, fallbackId);
        }
        return model;
    }

    private static OpenYsmBakedPlayerModel findSourcePlayerModel(Entity entity, OpenYsmExtraEntityModel.Kind kind) {
        IResourceManager resourceManager = Minecraft.getInstance().getResourceManager();
        OpenYsmBakedPlayerModel syncedModel = OpenYsmPlayerModelState.getBakedModelForExtraEntity(entity, resourceManager);
        if (syncedModel != null) {
            return syncedModel;
        }

        PlayerEntity sourcePlayer = findSourcePlayer(entity, kind);
        if (sourcePlayer != null) {
            return OpenYsmPlayerModelState.getBakedModelForPlayer(sourcePlayer, resourceManager);
        }
        return YesSteveModel.getSelectedPlayerModel(resourceManager);
    }

    private static PlayerEntity findSourcePlayer(Entity entity, OpenYsmExtraEntityModel.Kind kind) {
        if (kind == OpenYsmExtraEntityModel.Kind.PROJECTILE && entity instanceof ProjectileEntity) {
            Entity shooter = ((ProjectileEntity) entity).func_234616_v_();
            return shooter instanceof PlayerEntity ? (PlayerEntity) shooter : null;
        }
        if (kind == OpenYsmExtraEntityModel.Kind.VEHICLE) {
            for (Entity passenger : entity.getPassengers()) {
                if (passenger instanceof PlayerEntity) {
                    return (PlayerEntity) passenger;
                }
            }
        }
        return null;
    }

    public static void render(OpenYsmExtraEntityModel model, Entity entity, float partialTicks, MatrixStack matrixStackIn,
                              IRenderTypeBuffer bufferIn, int packedLightIn) {
        if (model == null) {
            return;
        }
        model.resetPose();
        if (model.getAnimations() != null) {
            ActiveAnimationSet active = OpenYsmExtraEntityAnimationResolver.resolve(model, entity, partialTicks);
            model.getAnimations().apply(model.getBones(), active, partialTicks);
        }
        model.render(matrixStackIn, bufferIn.getBuffer(model.getRenderType()), packedLightIn,
                OverlayTexture.NO_OVERLAY, 1.0F, 1.0F, 1.0F, 1.0F);
    }
}
