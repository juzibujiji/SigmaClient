package net.minecraft.client.renderer.entity;

import com.elfmcys.yesstevemodel.YesSteveModel;
import com.elfmcys.yesstevemodel.client.OpenYsmBakedPlayerModel;
import com.elfmcys.yesstevemodel.client.OpenYsmGl4PlayerModel;
import com.elfmcys.yesstevemodel.client.OpenYsmPlayerModel;
import com.elfmcys.yesstevemodel.client.OpenYsmBone;
import com.elfmcys.yesstevemodel.client.OpenYsmRendererMode;
import com.elfmcys.yesstevemodel.client.OpenYsmRendererSelector;
import com.mojang.blaze3d.matrix.MatrixStack;
import com.mojang.blaze3d.vertex.IVertexBuilder;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.player.AbstractClientPlayerEntity;
import net.minecraft.client.renderer.IRenderTypeBuffer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.layers.ArrowLayer;
import net.minecraft.client.renderer.entity.layers.BeeStingerLayer;
import net.minecraft.client.renderer.entity.layers.BipedArmorLayer;
import net.minecraft.client.renderer.entity.layers.CapeLayer;
import net.minecraft.client.renderer.entity.layers.Deadmau5HeadLayer;
import net.minecraft.client.renderer.entity.layers.ElytraLayer;
import net.minecraft.client.renderer.entity.layers.HeadLayer;
import net.minecraft.client.renderer.entity.layers.HeldItemLayer;
import net.minecraft.client.renderer.entity.layers.ParrotVariantLayer;
import net.minecraft.client.renderer.entity.layers.SpinAttackEffectLayer;
import net.minecraft.client.renderer.entity.model.BipedModel;
import net.minecraft.client.renderer.entity.model.PlayerModel;
import net.minecraft.client.renderer.model.ModelRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerModelPart;
import net.minecraft.item.CrossbowItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.UseAction;
import net.minecraft.scoreboard.Score;
import net.minecraft.scoreboard.ScoreObjective;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.util.Hand;
import net.minecraft.util.HandSide;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.vector.Vector3d;
import net.minecraft.util.math.vector.Vector3f;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.StringTextComponent;

public class PlayerRenderer extends LivingRenderer<AbstractClientPlayerEntity, PlayerModel<AbstractClientPlayerEntity>>
{
    private final boolean useSmallArms;
    private OpenYsmPlayerModel openYsmModel;
    private String openYsmModelId;
    private OpenYsmGl4PlayerModel openYsmGl4Model;
    private String openYsmGl4ModelId;

    public PlayerRenderer(EntityRendererManager renderManager)
    {
        this(renderManager, false);
    }

    public PlayerRenderer(EntityRendererManager renderManager, boolean useSmallArms)
    {
        super(renderManager, new PlayerModel<>(0.0F, useSmallArms), 0.5F);
        this.useSmallArms = useSmallArms;
        this.addLayer(new BipedArmorLayer<>(this, new BipedModel(0.5F), new BipedModel(1.0F)));
        this.addLayer(new HeldItemLayer<>(this));
        this.addLayer(new ArrowLayer<>(this));
        this.addLayer(new Deadmau5HeadLayer(this));
        this.addLayer(new CapeLayer(this));
        this.addLayer(new HeadLayer<>(this));
        this.addLayer(new ElytraLayer<>(this));
        this.addLayer(new ParrotVariantLayer<>(this));
        this.addLayer(new SpinAttackEffectLayer<>(this));
        this.addLayer(new BeeStingerLayer<>(this));
    }

    public void render(AbstractClientPlayerEntity entityIn, float entityYaw, float partialTicks, MatrixStack matrixStackIn, IRenderTypeBuffer bufferIn, int packedLightIn)
    {
        OpenYsmRendererMode rendererMode = OpenYsmRendererSelector.getMode();
        if (rendererMode.usesGl4Renderer())
        {
            OpenYsmGl4PlayerModel ysmModel = this.getOpenYsmGl4Model(entityIn);
            if (ysmModel != null)
            {
                PlayerModel<AbstractClientPlayerEntity> previousModel = this.entityModel;
                ResourceLocation previousTexture = this.getLocationTextureCustom();
                this.entityModel = ysmModel;
                this.setLocationTextureCustom(ysmModel.getTexture());
                try
                {
                    this.setModelVisibilities(entityIn);
                    super.render(entityIn, entityYaw, partialTicks, matrixStackIn, bufferIn, packedLightIn);
                }
                finally
                {
                    this.entityModel = previousModel;
                    this.setLocationTextureCustom(previousTexture);
                }
                return;
            }
        }
        else if (rendererMode.usesYsm())
        {
            OpenYsmPlayerModel ysmModel = this.getOpenYsmModel(entityIn);
            if (ysmModel != null)
            {
                PlayerModel<AbstractClientPlayerEntity> previousModel = this.entityModel;
                ResourceLocation previousTexture = this.getLocationTextureCustom();
                this.entityModel = ysmModel;
                this.setLocationTextureCustom(ysmModel.getTexture());
                try
                {
                    this.setModelVisibilities(entityIn);
                    super.render(entityIn, entityYaw, partialTicks, matrixStackIn, bufferIn, packedLightIn);
                }
                finally
                {
                    this.entityModel = previousModel;
                    this.setLocationTextureCustom(previousTexture);
                }
                return;
            }
        }

        this.setModelVisibilities(entityIn);
        super.render(entityIn, entityYaw, partialTicks, matrixStackIn, bufferIn, packedLightIn);
    }

    private OpenYsmPlayerModel getOpenYsmModel(AbstractClientPlayerEntity entityIn)
    {
        if (!YesSteveModel.getClientConfig().isRenderPlayers() || entityIn.isSpectator())
        {
            return null;
        }

        OpenYsmBakedPlayerModel bakedModel = YesSteveModel.getSelectedPlayerModel(Minecraft.getInstance().getResourceManager());
        if (bakedModel == null)
        {
            return null;
        }

        String modelKey = bakedModel.getId() + "|" + bakedModel.getTexture();
        if (this.openYsmModel == null || !modelKey.equals(this.openYsmModelId))
        {
            this.openYsmModel = new OpenYsmPlayerModel(bakedModel, this.useSmallArms);
            this.openYsmModelId = modelKey;
        }

        return this.openYsmModel;
    }

    private OpenYsmGl4PlayerModel getOpenYsmGl4Model(AbstractClientPlayerEntity entityIn)
    {
        if (!YesSteveModel.getClientConfig().isRenderPlayers() || entityIn.isSpectator())
        {
            return null;
        }

        OpenYsmBakedPlayerModel bakedModel = YesSteveModel.getSelectedPlayerModel(Minecraft.getInstance().getResourceManager());
        if (bakedModel == null || bakedModel.getGeoModel() == null)
        {
            return null;
        }

        String modelKey = bakedModel.getId() + "|" + bakedModel.getTexture();
        if (this.openYsmGl4Model == null || !modelKey.equals(this.openYsmGl4ModelId))
        {
            this.openYsmGl4Model = new OpenYsmGl4PlayerModel(bakedModel, this.useSmallArms);
            this.openYsmGl4ModelId = modelKey;
        }

        return this.openYsmGl4Model;
    }

    public Vector3d getRenderOffset(AbstractClientPlayerEntity entityIn, float partialTicks)
    {
        return entityIn.isCrouching() ? new Vector3d(0.0D, -0.125D, 0.0D) : super.getRenderOffset(entityIn, partialTicks);
    }

    private void setModelVisibilities(AbstractClientPlayerEntity clientPlayer)
    {
        PlayerModel<AbstractClientPlayerEntity> playermodel = this.getEntityModel();

        if (clientPlayer.isSpectator())
        {
            playermodel.setVisible(false);
            playermodel.bipedHead.showModel = true;
            playermodel.bipedHeadwear.showModel = true;
        }
        else
        {
            playermodel.setVisible(true);
            playermodel.bipedHeadwear.showModel = clientPlayer.isWearing(PlayerModelPart.HAT);
            playermodel.bipedBodyWear.showModel = clientPlayer.isWearing(PlayerModelPart.JACKET);
            playermodel.bipedLeftLegwear.showModel = clientPlayer.isWearing(PlayerModelPart.LEFT_PANTS_LEG);
            playermodel.bipedRightLegwear.showModel = clientPlayer.isWearing(PlayerModelPart.RIGHT_PANTS_LEG);
            playermodel.bipedLeftArmwear.showModel = clientPlayer.isWearing(PlayerModelPart.LEFT_SLEEVE);
            playermodel.bipedRightArmwear.showModel = clientPlayer.isWearing(PlayerModelPart.RIGHT_SLEEVE);
            playermodel.isSneak = clientPlayer.isCrouching();
            BipedModel.ArmPose bipedmodel$armpose = func_241741_a_(clientPlayer, Hand.MAIN_HAND);
            BipedModel.ArmPose bipedmodel$armpose1 = func_241741_a_(clientPlayer, Hand.OFF_HAND);

            if (bipedmodel$armpose.func_241657_a_())
            {
                bipedmodel$armpose1 = clientPlayer.getHeldItemOffhand().isEmpty() ? BipedModel.ArmPose.EMPTY : BipedModel.ArmPose.ITEM;
            }

            if (clientPlayer.getPrimaryHand() == HandSide.RIGHT)
            {
                playermodel.rightArmPose = bipedmodel$armpose;
                playermodel.leftArmPose = bipedmodel$armpose1;
            }
            else
            {
                playermodel.rightArmPose = bipedmodel$armpose1;
                playermodel.leftArmPose = bipedmodel$armpose;
            }
        }
    }

    private static BipedModel.ArmPose func_241741_a_(AbstractClientPlayerEntity p_241741_0_, Hand p_241741_1_)
    {
        ItemStack itemstack = p_241741_0_.getHeldItem(p_241741_1_);

        if (itemstack.isEmpty())
        {
            return BipedModel.ArmPose.EMPTY;
        }
        else
        {
            if (p_241741_0_.getActiveHand() == p_241741_1_ && p_241741_0_.getItemInUseCount() > 0)
            {
                UseAction useaction = itemstack.getUseAction();

                if (useaction == UseAction.BLOCK)
                {
                    return BipedModel.ArmPose.BLOCK;
                }

                if (useaction == UseAction.BOW)
                {
                    return BipedModel.ArmPose.BOW_AND_ARROW;
                }

                if (useaction == UseAction.SPEAR)
                {
                    return BipedModel.ArmPose.THROW_SPEAR;
                }

                if (useaction == UseAction.CROSSBOW && p_241741_1_ == p_241741_0_.getActiveHand())
                {
                    return BipedModel.ArmPose.CROSSBOW_CHARGE;
                }
            }
            else if (!p_241741_0_.isSwingInProgress && itemstack.getItem() == Items.CROSSBOW && CrossbowItem.isCharged(itemstack))
            {
                return BipedModel.ArmPose.CROSSBOW_HOLD;
            }

            return BipedModel.ArmPose.ITEM;
        }
    }

    /**
     * Returns the location of an entity's texture.
     */
    public ResourceLocation getEntityTexture(AbstractClientPlayerEntity entity)
    {
        return entity.getLocationSkin();
    }

    protected void preRenderCallback(AbstractClientPlayerEntity entitylivingbaseIn, MatrixStack matrixStackIn, float partialTickTime)
    {
        float f = 0.9375F;
        matrixStackIn.scale(0.9375F, 0.9375F, 0.9375F);
    }

    protected void renderName(AbstractClientPlayerEntity entityIn, ITextComponent displayNameIn, MatrixStack matrixStackIn, IRenderTypeBuffer bufferIn, int packedLightIn)
    {
        double d0 = this.renderManager.squareDistanceTo(entityIn);
        matrixStackIn.push();

        if (d0 < 100.0D)
        {
            Scoreboard scoreboard = entityIn.getWorldScoreboard();
            ScoreObjective scoreobjective = scoreboard.getObjectiveInDisplaySlot(2);

            if (scoreobjective != null)
            {
                Score score = scoreboard.getOrCreateScore(entityIn.getScoreboardName(), scoreobjective);
                super.renderName(entityIn, (new StringTextComponent(Integer.toString(score.getScorePoints()))).appendString(" ").append(scoreobjective.getDisplayName()), matrixStackIn, bufferIn, packedLightIn);
                matrixStackIn.translate(0.0D, (double)(9.0F * 1.15F * 0.025F), 0.0D);
            }
        }

        super.renderName(entityIn, displayNameIn, matrixStackIn, bufferIn, packedLightIn);
        matrixStackIn.pop();
    }

    public void renderRightArm(MatrixStack matrixStackIn, IRenderTypeBuffer bufferIn, int combinedLightIn, AbstractClientPlayerEntity playerIn)
    {
        this.renderItem(matrixStackIn, bufferIn, combinedLightIn, playerIn, (this.entityModel).bipedRightArm, (this.entityModel).bipedRightArmwear);
    }

    public void renderLeftArm(MatrixStack matrixStackIn, IRenderTypeBuffer bufferIn, int combinedLightIn, AbstractClientPlayerEntity playerIn)
    {
        this.renderItem(matrixStackIn, bufferIn, combinedLightIn, playerIn, (this.entityModel).bipedLeftArm, (this.entityModel).bipedLeftArmwear);
    }

    private void renderItem(MatrixStack matrixStackIn, IRenderTypeBuffer bufferIn, int combinedLightIn, AbstractClientPlayerEntity playerIn, ModelRenderer rendererArmIn, ModelRenderer rendererArmwearIn)
    {
        boolean rightArm = rendererArmIn == this.entityModel.bipedRightArm;
        OpenYsmRendererMode rendererMode = OpenYsmRendererSelector.getMode();
        if (rendererMode.usesGl4Renderer())
        {
            OpenYsmGl4PlayerModel ysmPlayerModel = this.getOpenYsmGl4Model(playerIn);
            if (ysmPlayerModel != null)
            {
                PlayerModel<AbstractClientPlayerEntity> previousModel = this.entityModel;
                ResourceLocation previousTexture = this.getLocationTextureCustom();
                this.entityModel = ysmPlayerModel;
                this.setLocationTextureCustom(ysmPlayerModel.getTexture());
                try
                {
                    this.setModelVisibilities(playerIn);
                    ysmPlayerModel.swingProgress = 0.0F;
                    ysmPlayerModel.isSneak = false;
                    ysmPlayerModel.swimAnimation = 0.0F;
                    ysmPlayerModel.setRotationAngles(playerIn, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F);

                    IVertexBuilder customBuffer = bufferIn.getBuffer(ysmPlayerModel.getRenderType(ysmPlayerModel.getTexture()));
                    if (ysmPlayerModel.renderBone(rightArm ? "RightArm" : "LeftArm", rightArm ? "MRightArm" : "MLeftArm",
                            matrixStackIn, customBuffer, combinedLightIn, OverlayTexture.NO_OVERLAY,
                            1.0F, 1.0F, 1.0F, 1.0F))
                    {
                        return;
                    }
                }
                finally
                {
                    this.entityModel = previousModel;
                    this.setLocationTextureCustom(previousTexture);
                }
            }
        }
        else if (rendererMode.usesYsm())
        {
            OpenYsmPlayerModel ysmPlayerModel = this.getOpenYsmModel(playerIn);
            if (ysmPlayerModel != null)
            {
                PlayerModel<AbstractClientPlayerEntity> previousModel = this.entityModel;
                ResourceLocation previousTexture = this.getLocationTextureCustom();
                this.entityModel = ysmPlayerModel;
                this.setLocationTextureCustom(ysmPlayerModel.getTexture());
                try
                {
                    this.setModelVisibilities(playerIn);
                    ysmPlayerModel.swingProgress = 0.0F;
                    ysmPlayerModel.isSneak = false;
                    ysmPlayerModel.swimAnimation = 0.0F;
                    ysmPlayerModel.setRotationAngles(playerIn, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F);

                    OpenYsmBone bone = this.getArmBone(ysmPlayerModel.getBakedModel(), rightArm);
                    if (bone != null)
                    {
                        float prevX = bone.getRenderer().rotateAngleX;
                        float prevY = bone.getRenderer().rotateAngleY;
                        float prevZ = bone.getRenderer().rotateAngleZ;
                        bone.getRenderer().rotateAngleX = 0.0F;
                        bone.getRenderer().rotateAngleY = 0.0F;
                        bone.getRenderer().rotateAngleZ = 0.0F;

                        try
                        {
                            IVertexBuilder customBuffer = bufferIn.getBuffer(ysmPlayerModel.getRenderType(ysmPlayerModel.getTexture()));
                            bone.getRenderer().render(matrixStackIn, customBuffer, combinedLightIn, OverlayTexture.NO_OVERLAY, 1.0F, 1.0F, 1.0F, 1.0F);
                        }
                        finally
                        {
                            bone.getRenderer().rotateAngleX = prevX;
                            bone.getRenderer().rotateAngleY = prevY;
                            bone.getRenderer().rotateAngleZ = prevZ;
                        }
                        return;
                    }
                }
                finally
                {
                    this.entityModel = previousModel;
                    this.setLocationTextureCustom(previousTexture);
                }
            }
        }

        PlayerModel<AbstractClientPlayerEntity> playermodel = this.getEntityModel();
        this.setModelVisibilities(playerIn);
        playermodel.swingProgress = 0.0F;
        playermodel.isSneak = false;
        playermodel.swimAnimation = 0.0F;
        playermodel.setRotationAngles(playerIn, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F);

        rendererArmIn.rotateAngleX = 0.0F;
        rendererArmIn.render(matrixStackIn, bufferIn.getBuffer(RenderType.getEntitySolid(playerIn.getLocationSkin())), combinedLightIn, OverlayTexture.NO_OVERLAY);
        rendererArmwearIn.rotateAngleX = 0.0F;
        rendererArmwearIn.render(matrixStackIn, bufferIn.getBuffer(RenderType.getEntityTranslucent(playerIn.getLocationSkin())), combinedLightIn, OverlayTexture.NO_OVERLAY);
    }

    private OpenYsmBone getArmBone(OpenYsmBakedPlayerModel bakedModel, boolean rightArm)
    {
        OpenYsmBone armBone = bakedModel.getBones().get(rightArm ? "RightArm" : "LeftArm");
        if (armBone != null)
        {
            return armBone;
        }
        return bakedModel.getBones().get(rightArm ? "MRightArm" : "MLeftArm");
    }

    protected void applyRotations(AbstractClientPlayerEntity entityLiving, MatrixStack matrixStackIn, float ageInTicks, float rotationYaw, float partialTicks)
    {
        float f = entityLiving.getSwimAnimation(partialTicks);

        if (entityLiving.isElytraFlying())
        {
            super.applyRotations(entityLiving, matrixStackIn, ageInTicks, rotationYaw, partialTicks);
            float f1 = (float)entityLiving.getTicksElytraFlying() + partialTicks;
            float f2 = MathHelper.clamp(f1 * f1 / 100.0F, 0.0F, 1.0F);

            if (!entityLiving.isSpinAttacking())
            {
                matrixStackIn.rotate(Vector3f.XP.rotationDegrees(f2 * (-90.0F - entityLiving.rotationPitch)));
            }

            Vector3d vector3d = entityLiving.getLook(partialTicks);
            Vector3d vector3d1 = entityLiving.getMotion();
            double d0 = Entity.horizontalMag(vector3d1);
            double d1 = Entity.horizontalMag(vector3d);

            if (d0 > 0.0D && d1 > 0.0D)
            {
                double d2 = (vector3d1.x * vector3d.x + vector3d1.z * vector3d.z) / Math.sqrt(d0 * d1);
                double d3 = vector3d1.x * vector3d.z - vector3d1.z * vector3d.x;
                matrixStackIn.rotate(Vector3f.YP.rotation((float)(Math.signum(d3) * Math.acos(d2))));
            }
        }
        else if (f > 0.0F)
        {
            super.applyRotations(entityLiving, matrixStackIn, ageInTicks, rotationYaw, partialTicks);
            float f3 = entityLiving.isInWater() ? -90.0F - entityLiving.rotationPitch : -90.0F;
            float f4 = MathHelper.lerp(f, 0.0F, f3);
            matrixStackIn.rotate(Vector3f.XP.rotationDegrees(f4));

            if (entityLiving.isActualySwimming())
            {
                matrixStackIn.translate(0.0D, -1.0D, (double)0.3F);
            }
        }
        else
        {
            super.applyRotations(entityLiving, matrixStackIn, ageInTicks, rotationYaw, partialTicks);
        }
    }
}
