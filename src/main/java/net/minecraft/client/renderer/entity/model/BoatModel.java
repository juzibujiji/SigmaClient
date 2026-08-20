package net.minecraft.client.renderer.entity.model;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableList.Builder;
import java.util.Arrays;
import net.minecraft.client.renderer.model.ModelRenderer;
import net.minecraft.entity.item.BoatEntity;
import net.minecraft.util.math.MathHelper;

/**
 * Boat model, and (since the 1.21.11 port) the base for the raft and chest variants.
 *
 * Geometry sources - official 1.21.11:
 *   net/minecraft/client/model/object/boat/BoatModel#addCommonParts / createChestBoatModel /
 *   createWaterPatch, and .../RaftModel#addCommonParts / createChestRaftModel.
 * Texture sheet sizes come from the LayerDefinition.create calls there: 128x64 for the plain
 * boat and raft, 128x128 for both chest variants.
 */
public class BoatModel extends SegmentedModel<BoatEntity>
{
    private final ModelRenderer[] paddles = new ModelRenderer[2];
    private final ModelRenderer noWater;
    private final ImmutableList<ModelRenderer> field_228243_f_;
    private final boolean raft;
    private final int sheetHeight;

    public BoatModel()
    {
        this(false, false);
    }

    protected BoatModel(boolean raftIn, boolean chestIn)
    {
        this.raft = raftIn;
        this.sheetHeight = chestIn ? 128 : 64;
        Builder<ModelRenderer> builder = ImmutableList.builder();
        builder.addAll(Arrays.asList(raftIn ? this.makeRaftHull() : this.makeBoatHull()));
        this.makePaddles(raftIn);
        builder.addAll(Arrays.asList(this.paddles));

        if (chestIn)
        {
            builder.addAll(Arrays.asList(this.makeChest(raftIn)));
        }

        this.noWater = this.makeModelRenderer(0, 0);
        this.noWater.addBox(-14.0F, -9.0F, -3.0F, 28.0F, 16.0F, 3.0F, 0.0F);
        this.noWater.setRotationPoint(0.0F, -3.0F, 1.0F);
        this.noWater.rotateAngleX = ((float)Math.PI / 2F);
        this.field_228243_f_ = builder.build();
    }

    private ModelRenderer makeModelRenderer(int texX, int texY)
    {
        return (new ModelRenderer(this, texX, texY)).setTextureSize(128, this.sheetHeight);
    }

    /**
     * bottom / back / front / right / left, from official BoatModel#addCommonParts.
     */
    private ModelRenderer[] makeBoatHull()
    {
        ModelRenderer[] amodelrenderer = new ModelRenderer[] {this.makeModelRenderer(0, 0), this.makeModelRenderer(0, 19), this.makeModelRenderer(0, 27), this.makeModelRenderer(0, 35), this.makeModelRenderer(0, 43)};
        int i = 32;
        int j = 6;
        int k = 20;
        int l = 4;
        int i1 = 28;
        amodelrenderer[0].addBox(-14.0F, -9.0F, -3.0F, 28.0F, 16.0F, 3.0F, 0.0F);
        amodelrenderer[0].setRotationPoint(0.0F, 3.0F, 1.0F);
        amodelrenderer[1].addBox(-13.0F, -7.0F, -1.0F, 18.0F, 6.0F, 2.0F, 0.0F);
        amodelrenderer[1].setRotationPoint(-15.0F, 4.0F, 4.0F);
        amodelrenderer[2].addBox(-8.0F, -7.0F, -1.0F, 16.0F, 6.0F, 2.0F, 0.0F);
        amodelrenderer[2].setRotationPoint(15.0F, 4.0F, 0.0F);
        amodelrenderer[3].addBox(-14.0F, -7.0F, -1.0F, 28.0F, 6.0F, 2.0F, 0.0F);
        amodelrenderer[3].setRotationPoint(0.0F, 4.0F, -9.0F);
        amodelrenderer[4].addBox(-14.0F, -7.0F, -1.0F, 28.0F, 6.0F, 2.0F, 0.0F);
        amodelrenderer[4].setRotationPoint(0.0F, 4.0F, 9.0F);
        amodelrenderer[0].rotateAngleX = ((float)Math.PI / 2F);
        amodelrenderer[1].rotateAngleY = ((float)Math.PI * 1.5F);
        amodelrenderer[2].rotateAngleY = ((float)Math.PI / 2F);
        amodelrenderer[3].rotateAngleY = (float)Math.PI;
        return amodelrenderer;
    }

    /**
     * The raft has no separate walls: official RaftModel#addCommonParts builds its "bottom"
     * from two flat slabs, which is what makes a raft look wider and flatter than a boat.
     */
    private ModelRenderer[] makeRaftHull()
    {
        ModelRenderer modelrenderer = this.makeModelRenderer(0, 0);
        modelrenderer.addBox(-14.0F, -11.0F, -4.0F, 28.0F, 20.0F, 4.0F, 0.0F);
        modelrenderer.setTextureOffset(0, 0);
        modelrenderer.addBox(-14.0F, -9.0F, -8.0F, 28.0F, 16.0F, 4.0F, 0.0F);
        modelrenderer.setRotationPoint(0.0F, -2.1F, 1.0F);
        modelrenderer.rotateAngleX = 1.5708F;
        return new ModelRenderer[] {modelrenderer};
    }

    private void makePaddles(boolean raftIn)
    {
        this.paddles[0] = this.makePaddle(true);
        this.paddles[1] = this.makePaddle(false);
        float f = raftIn ? -4.0F : -5.0F;
        this.paddles[0].setRotationPoint(3.0F, f, 9.0F);
        this.paddles[1].setRotationPoint(3.0F, f, -9.0F);
        this.paddles[1].rotateAngleY = (float)Math.PI;
        this.paddles[0].rotateAngleZ = 0.19634955F;
        this.paddles[1].rotateAngleZ = 0.19634955F;
    }

    /**
     * chest_bottom / chest_lid / chest_lock. The raft variant sits 5.1px higher because its
     * deck is lower - see official RaftModel#createChestRaftModel.
     */
    private ModelRenderer[] makeChest(boolean raftIn)
    {
        float f = raftIn ? -5.1F : 0.0F;
        ModelRenderer modelrenderer = this.makeModelRenderer(0, 76);
        modelrenderer.addBox(0.0F, 0.0F, 0.0F, 12.0F, 8.0F, 12.0F, 0.0F);
        modelrenderer.setRotationPoint(-2.0F, -5.0F + f, -6.0F);
        modelrenderer.rotateAngleY = (-(float)Math.PI / 2F);
        ModelRenderer modelrenderer1 = this.makeModelRenderer(0, 59);
        modelrenderer1.addBox(0.0F, 0.0F, 0.0F, 12.0F, 4.0F, 12.0F, 0.0F);
        modelrenderer1.setRotationPoint(-2.0F, -9.0F + f, -6.0F);
        modelrenderer1.rotateAngleY = (-(float)Math.PI / 2F);
        ModelRenderer modelrenderer2 = this.makeModelRenderer(0, 59);
        modelrenderer2.addBox(0.0F, 0.0F, 0.0F, 2.0F, 4.0F, 1.0F, 0.0F);
        modelrenderer2.setRotationPoint(-1.0F, -6.0F + f, -1.0F);
        modelrenderer2.rotateAngleY = (-(float)Math.PI / 2F);
        return new ModelRenderer[] {modelrenderer, modelrenderer1, modelrenderer2};
    }

    /**
     * Sets this entity's model rotation angles
     */
    public void setRotationAngles(BoatEntity entityIn, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch)
    {
        this.func_228244_a_(entityIn, 0, limbSwing);
        this.func_228244_a_(entityIn, 1, limbSwing);
    }

    public ImmutableList<ModelRenderer> getParts()
    {
        return this.field_228243_f_;
    }

    public ModelRenderer func_228245_c_()
    {
        return this.noWater;
    }

    /**
     * Official 1.21.11 RaftRenderer, unlike BoatRenderer, never submits the water patch model.
     */
    public boolean hasWaterPatch()
    {
        return !this.raft;
    }

    protected ModelRenderer makePaddle(boolean p_187056_1_)
    {
        ModelRenderer modelrenderer = this.raft ? this.makeModelRenderer(p_187056_1_ ? 0 : 40, 24) : this.makeModelRenderer(62, p_187056_1_ ? 0 : 20);
        int i = 20;
        int j = 7;
        int k = 6;
        float f = -5.0F;
        modelrenderer.addBox(-1.0F, 0.0F, -5.0F, 2.0F, 2.0F, 18.0F);
        modelrenderer.addBox(p_187056_1_ ? -1.001F : 0.001F, -3.0F, 8.0F, 1.0F, 6.0F, 7.0F);
        return modelrenderer;
    }

    protected void func_228244_a_(BoatEntity p_228244_1_, int p_228244_2_, float p_228244_3_)
    {
        float f = p_228244_1_.getRowingTime(p_228244_2_, p_228244_3_);
        ModelRenderer modelrenderer = this.paddles[p_228244_2_];
        modelrenderer.rotateAngleX = (float)MathHelper.clampedLerp((double)(-(float)Math.PI / 3F), (double) - 0.2617994F, (double)((MathHelper.sin(-f) + 1.0F) / 2.0F));
        modelrenderer.rotateAngleY = (float)MathHelper.clampedLerp((double)(-(float)Math.PI / 4F), (double)((float)Math.PI / 4F), (double)((MathHelper.sin(-f + 1.0F) + 1.0F) / 2.0F));

        if (p_228244_2_ == 1)
        {
            modelrenderer.rotateAngleY = (float)Math.PI - modelrenderer.rotateAngleY;
        }
    }
}
