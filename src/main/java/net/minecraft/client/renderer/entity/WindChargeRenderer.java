package net.minecraft.client.renderer.entity;

import com.mojang.blaze3d.matrix.MatrixStack;
import com.mojang.blaze3d.vertex.IVertexBuilder;
import net.minecraft.client.renderer.IRenderTypeBuffer;
import net.minecraft.client.renderer.entity.model.WindChargeModel;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.entity.projectile.AbstractWindChargeEntity;
import net.minecraft.util.ResourceLocation;

/**
 * Backport of the 1.20.5 wind charge renderer.
 *
 * Official source: net/minecraft/client/renderer/entity/WindChargeRenderer.java
 * (1.21.11 / MCP-Reborn-release).
 *
 * Official texture: "textures/entity/projectiles/wind_charge.png" (copied into this project's assets).
 *
 * Deviation: the official renderer draws the model with RenderTypes.breezeWind(texture, ageInTicks * 0.03F, 0),
 * a dedicated pipeline that scrolls the texture horizontally to sell the swirling wind. 1.16.4's RenderType
 * has no texture-transform state, so the model's own default render type (entityTranslucent, which is what
 * WindChargeModel's constructor passes) is used and the texture does not scroll. Purely cosmetic; the
 * per-part counter-rotation from WindChargeModel#setupAnim is intact.
 */
public class WindChargeRenderer extends EntityRenderer<AbstractWindChargeEntity>
{
    private static final ResourceLocation WIND_CHARGE_TEXTURE = new ResourceLocation("textures/entity/projectiles/wind_charge.png");
    private final WindChargeModel model = new WindChargeModel();

    public WindChargeRenderer(EntityRendererManager renderManagerIn)
    {
        super(renderManagerIn);
    }

    public void render(AbstractWindChargeEntity entityIn, float entityYaw, float partialTicks, MatrixStack matrixStackIn, IRenderTypeBuffer bufferIn, int packedLightIn)
    {
        matrixStackIn.push();
        // Official xOffset(ageInTicks) feeds the scrolling render type; the same value drives the model's
        // own rotation, which is all that is reproducible here.
        this.model.setRotationAngles((float)entityIn.ticksExisted + partialTicks);
        IVertexBuilder ivertexbuilder = bufferIn.getBuffer(this.model.getRenderType(WIND_CHARGE_TEXTURE));
        this.model.render(matrixStackIn, ivertexbuilder, packedLightIn, OverlayTexture.NO_OVERLAY, 1.0F, 1.0F, 1.0F, 1.0F);
        matrixStackIn.pop();
        super.render(entityIn, entityYaw, partialTicks, matrixStackIn, bufferIn, packedLightIn);
    }

    public ResourceLocation getEntityTexture(AbstractWindChargeEntity entity)
    {
        return WIND_CHARGE_TEXTURE;
    }
}
