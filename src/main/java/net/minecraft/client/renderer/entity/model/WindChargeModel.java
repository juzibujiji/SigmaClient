package net.minecraft.client.renderer.entity.model;

import com.mojang.blaze3d.matrix.MatrixStack;
import com.mojang.blaze3d.vertex.IVertexBuilder;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.model.Model;
import net.minecraft.client.renderer.model.ModelRenderer;

/**
 * Backport of the 1.20.5 wind charge entity model.
 *
 * Official source: net/minecraft/client/model/object/projectile/WindChargeModel.java
 * (1.21.11 / MCP-Reborn-release). Geometry copied verbatim from createBodyLayer():
 *
 *   LayerDefinition.create(meshdefinition, 64, 32)                     -> texture 64x32
 *   "bone"        : no cubes, PartPose.offset(0, 0, 0)
 *   "wind"        : texOffs(15, 20).addBox(-4, -1, -4, 8, 2, 8)
 *                   texOffs( 0,  9).addBox(-3, -2, -3, 6, 4, 6)
 *                   PartPose.offsetAndRotation(0, 0, 0, 0, -0.7854F, 0)
 *   "wind_charge" : texOffs( 0,  0).addBox(-2, -2, -2, 4, 4, 4)
 *                   PartPose.offset(0, 0, 0)
 *
 * And from setupAnim() (ROTATION_SPEED = 16):
 *   this.windCharge.yRot = -ageInTicks * 16.0F * (PI / 180)
 *   this.wind.yRot       =  ageInTicks * 16.0F * (PI / 180)
 * Note the official setupAnim assigns yRot outright, so the -0.7854F base rotation of "wind" from the
 * PartPose is overwritten every frame; it is still applied here for parity with the layer definition.
 */
public class WindChargeModel extends Model
{
    /** Official WindChargeModel.ROTATION_SPEED. */
    private static final float ROTATION_SPEED = 16.0F;
    private final ModelRenderer bone;
    private final ModelRenderer wind;
    private final ModelRenderer windCharge;

    public WindChargeModel()
    {
        // Official WindChargeModel extends EntityModel with RenderTypes::entityTranslucent.
        super(RenderType::getEntityTranslucent);
        this.textureWidth = 64;
        this.textureHeight = 32;
        this.bone = new ModelRenderer(this);
        this.bone.setRotationPoint(0.0F, 0.0F, 0.0F);
        this.wind = new ModelRenderer(this);
        this.wind.setTextureOffset(15, 20).addBox(-4.0F, -1.0F, -4.0F, 8.0F, 2.0F, 8.0F, 0.0F);
        this.wind.setTextureOffset(0, 9).addBox(-3.0F, -2.0F, -3.0F, 6.0F, 4.0F, 6.0F, 0.0F);
        this.wind.setRotationPoint(0.0F, 0.0F, 0.0F);
        this.wind.rotateAngleY = -0.7854F;
        this.windCharge = new ModelRenderer(this);
        this.windCharge.setTextureOffset(0, 0).addBox(-2.0F, -2.0F, -2.0F, 4.0F, 4.0F, 4.0F, 0.0F);
        this.windCharge.setRotationPoint(0.0F, 0.0F, 0.0F);
        this.bone.addChild(this.wind);
        this.bone.addChild(this.windCharge);
    }

    /**
     * Official WindChargeModel#setupAnim.
     */
    public void setRotationAngles(float ageInTicks)
    {
        this.windCharge.rotateAngleY = -ageInTicks * ROTATION_SPEED * ((float)Math.PI / 180F);
        this.wind.rotateAngleY = ageInTicks * ROTATION_SPEED * ((float)Math.PI / 180F);
    }

    public void render(MatrixStack matrixStackIn, IVertexBuilder bufferIn, int packedLightIn, int packedOverlayIn, float red, float green, float blue, float alpha)
    {
        this.bone.render(matrixStackIn, bufferIn, packedLightIn, packedOverlayIn, red, green, blue, alpha);
    }
}
