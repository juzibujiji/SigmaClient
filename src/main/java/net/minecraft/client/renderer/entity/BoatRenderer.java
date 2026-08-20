package net.minecraft.client.renderer.entity;

import com.mojang.blaze3d.matrix.MatrixStack;
import com.mojang.blaze3d.vertex.IVertexBuilder;
import net.minecraft.client.renderer.IRenderTypeBuffer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.model.BoatModel;
import net.minecraft.client.renderer.entity.model.RaftModel;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.entity.item.BoatEntity;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.vector.Quaternion;
import net.minecraft.util.math.vector.Vector3f;

public class BoatRenderer extends EntityRenderer<BoatEntity>
{
    /**
     * Indexed by BoatEntity.Type.ordinal(), so the order here must match the enum exactly.
     * The last four are the woods ported from 1.21.11; official 1.21 derives the path from the
     * per-wood ModelLayerLocation instead (BoatRenderer: "textures/entity/" + layer + ".png").
     */
    private static final ResourceLocation[] BOAT_TEXTURES = new ResourceLocation[] {new ResourceLocation("textures/entity/boat/oak.png"), new ResourceLocation("textures/entity/boat/spruce.png"), new ResourceLocation("textures/entity/boat/birch.png"), new ResourceLocation("textures/entity/boat/jungle.png"), new ResourceLocation("textures/entity/boat/acacia.png"), new ResourceLocation("textures/entity/boat/dark_oak.png"), new ResourceLocation("textures/entity/boat/cherry.png"), new ResourceLocation("textures/entity/boat/pale_oak.png"), new ResourceLocation("textures/entity/boat/mangrove.png"), new ResourceLocation("textures/entity/boat/bamboo.png")};
    protected final BoatModel modelBoat = new BoatModel();

    /**
     * The bamboo variant is a raft in official 1.21.11 and gets its own model / renderer
     * (RaftModel + RaftRenderer). Kept as a separate field so OptiFine's custom entity models,
     * which reflectively replace BoatRenderer.modelBoat, keep working for regular boats.
     */
    protected final BoatModel modelRaft = new RaftModel();

    public BoatRenderer(EntityRendererManager renderManagerIn)
    {
        super(renderManagerIn);
        this.shadowSize = 0.8F;
    }

    public void render(BoatEntity entityIn, float entityYaw, float partialTicks, MatrixStack matrixStackIn, IRenderTypeBuffer bufferIn, int packedLightIn)
    {
        matrixStackIn.push();
        matrixStackIn.translate(0.0D, 0.375D, 0.0D);
        matrixStackIn.rotate(Vector3f.YP.rotationDegrees(180.0F - entityYaw));
        float f = (float)entityIn.getTimeSinceHit() - partialTicks;
        float f1 = entityIn.getDamageTaken() - partialTicks;

        if (f1 < 0.0F)
        {
            f1 = 0.0F;
        }

        if (f > 0.0F)
        {
            matrixStackIn.rotate(Vector3f.XP.rotationDegrees(MathHelper.sin(f) * f * f1 / 10.0F * (float)entityIn.getForwardDirection()));
        }

        float f2 = entityIn.getRockingAngle(partialTicks);

        if (!MathHelper.epsilonEquals(f2, 0.0F))
        {
            matrixStackIn.rotate(new Quaternion(new Vector3f(1.0F, 0.0F, 1.0F), entityIn.getRockingAngle(partialTicks), true));
        }

        matrixStackIn.scale(-1.0F, -1.0F, 1.0F);
        matrixStackIn.rotate(Vector3f.YP.rotationDegrees(90.0F));
        BoatModel boatmodel = this.getBoatModel(entityIn);
        boatmodel.setRotationAngles(entityIn, partialTicks, 0.0F, -0.1F, 0.0F, 0.0F);
        IVertexBuilder ivertexbuilder = bufferIn.getBuffer(boatmodel.getRenderType(this.getEntityTexture(entityIn)));
        boatmodel.render(matrixStackIn, ivertexbuilder, packedLightIn, OverlayTexture.NO_OVERLAY, 1.0F, 1.0F, 1.0F, 1.0F);

        if (boatmodel.hasWaterPatch() && !entityIn.canSwim())
        {
            IVertexBuilder ivertexbuilder1 = bufferIn.getBuffer(RenderType.getWaterMask());
            boatmodel.func_228245_c_().render(matrixStackIn, ivertexbuilder1, packedLightIn, OverlayTexture.NO_OVERLAY);
        }

        matrixStackIn.pop();
        super.render(entityIn, entityYaw, partialTicks, matrixStackIn, bufferIn, packedLightIn);
    }

    protected BoatModel getBoatModel(BoatEntity entityIn)
    {
        return entityIn.getBoatType() == BoatEntity.Type.BAMBOO ? this.modelRaft : this.modelBoat;
    }

    /**
     * Returns the location of an entity's texture.
     */
    public ResourceLocation getEntityTexture(BoatEntity entity)
    {
        return BOAT_TEXTURES[entity.getBoatType().ordinal()];
    }
}
