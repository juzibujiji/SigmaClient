package net.minecraft.client.renderer.entity;

import com.elfmcys.yesstevemodel.YesSteveModel;
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
import net.minecraft.item.SpearItem;
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
        // mmdskin 接管优先级链：MMD 已选模型 -> MMD；否则 YSM 启用 -> YSM；否则原版。
        // 两套模型系统互不引用（isYsmActive 恒 false，见移植说明）。
        boolean mmdFirstPersonSkip = false;
        Minecraft mmdMc = Minecraft.getInstance();
        boolean mmdIsLocalPlayer = mmdMc.player != null && mmdMc.player.getUniqueID().equals(entityIn.getUniqueID());
        if (mmdIsLocalPlayer && mmdMc.gameSettings.getPointOfView().func_243192_a()
                && !com.shiroha.mmdskin.player.runtime.FirstPersonManager.shouldRenderFirstPerson())
        {
            com.shiroha.mmdskin.player.runtime.FirstPersonManager.reset();
            mmdFirstPersonSkip = true;
        }

        if (!mmdFirstPersonSkip)
        {
            com.shiroha.mmdskin.player.render.PlayerRenderAction mmdAction =
                    com.shiroha.mmdskin.player.render.PlayerRenderEntrypoint.handleRender(
                            entityIn, entityYaw, partialTicks, matrixStackIn, bufferIn, packedLightIn, false);
            com.shiroha.mmdskin.player.render.PlayerRenderEntrypoint.renderSceneOverlay(
                    entityIn, partialTicks, matrixStackIn, packedLightIn);
            if (mmdIsLocalPlayer)
            {
                com.shiroha.mmdskin.MmdSkinClientHooks.debugRenderDecision(entityIn.getName().getString(), mmdAction);
            }

            if (mmdAction == com.shiroha.mmdskin.player.render.PlayerRenderAction.CANCEL)
            {
                return;
            }
            if (mmdAction == com.shiroha.mmdskin.player.render.PlayerRenderAction.SUPER_RENDER)
            {
                super.render(entityIn, entityYaw, partialTicks, matrixStackIn, bufferIn, packedLightIn);
                return;
            }
        }

        // YSM re-port render chain (geckolib3): mirrors upstream ReplacePlayerRenderEvent routing.
        // Priority chain MMD -> YSM -> vanilla; MMD is handled above. The local player's
        // capability is first bound to the configured selection (syncSelectedModel), then
        // ticked. YSM only takes over when the model is both active and ready; otherwise we
        // fall through to vanilla rendering instead of cancelling into an invisible player.
        if (this.isOpenYsmRenderingEnabled() && !entityIn.isSpectator())
        {
            com.elfmcys.yesstevemodel.capability.PlayerCapability ysmCapability =
                    entityIn.getCapability(com.elfmcys.yesstevemodel.capability.PlayerCapabilityProvider.PLAYER_CAP).orElse(null);
            if (ysmCapability != null)
            {
                if (entityIn instanceof net.minecraft.client.entity.player.ClientPlayerEntity)
                {
                    com.elfmcys.yesstevemodel.client.ClientModelManager.syncSelectedModel(ysmCapability);
                }
                ysmCapability.tickModel();
                if (ysmCapability.isModelActive() && ysmCapability.isModelReady())
                {
                    com.elfmcys.yesstevemodel.client.renderer.RendererManager.getPlayerRenderer()
                            .render(entityIn, entityYaw, partialTicks, matrixStackIn, bufferIn, packedLightIn);
                    return;
                }
            }
        }

        this.setModelVisibilities(entityIn);
        super.render(entityIn, entityYaw, partialTicks, matrixStackIn, bufferIn, packedLightIn);
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
            // 1.21.11 长矛：官方 HumanoidMobRenderer.getArmPose / AvatarRenderer 只要物品带
            // STAB 挥击动画或在 SPEARS 标签里就用 ArmPose.SPEAR，不区分是否正在使用
            // ——「举矛蓄势」与「静态持矛」都由 SpearAnimations.thirdPersonHandUse 内部按
            // ticksUsingItem 区分。
            if (itemstack.getItem() instanceof SpearItem)
            {
                return BipedModel.ArmPose.SPEAR;
            }

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

                // ---- 1.17+ item backports, official client/renderer/entity/player/AvatarRenderer#getArmPose ----
                if (useaction == UseAction.SPYGLASS)
                {
                    return BipedModel.ArmPose.SPYGLASS;
                }

                if (useaction == UseAction.TOOT_HORN)
                {
                    return BipedModel.ArmPose.TOOT_HORN;
                }

                if (useaction == UseAction.BRUSH)
                {
                    return BipedModel.ArmPose.BRUSH;
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
        // MMD keeps top priority on the first-person arm path: while an MMD model is
        // selected, the YSM arm chain is skipped so the MMD/vanilla arm renders instead.
        boolean mmdSelected = this.isMmdModelSelected(playerIn);
        // YSM re-port first-person arm chain (geckolib3): bind the locally selected
        // model, tick, and render the custom arm only when the arm mesh actually
        // provides hand bones; any failure falls through to the vanilla arm.
        if (!mmdSelected && this.isOpenYsmRenderingEnabled() && !playerIn.isSpectator()
                && playerIn instanceof net.minecraft.client.entity.player.ClientPlayerEntity)
        {
            com.elfmcys.yesstevemodel.capability.PlayerCapability ysmArmCapability =
                    playerIn.getCapability(com.elfmcys.yesstevemodel.capability.PlayerCapabilityProvider.PLAYER_CAP).orElse(null);
            if (ysmArmCapability != null)
            {
                com.elfmcys.yesstevemodel.client.ClientModelManager.syncSelectedModel(ysmArmCapability);
                ysmArmCapability.tickModel();
                if (ysmArmCapability.isModelActive() && ysmArmCapability.isModelReady())
                {
                    com.elfmcys.yesstevemodel.client.model.ModelAssembly ysmArmAssembly = ysmArmCapability.getModelAssembly();
                    com.elfmcys.yesstevemodel.geckolib3.geo.render.built.GeoModel ysmArmModel =
                            ysmArmAssembly != null ? ysmArmAssembly.getAnimationBundle().getArmModel() : null;
                    boolean hasCustomHand = ysmArmModel != null
                            && (rightArm ? ysmArmModel.hasCustomRightHand : ysmArmModel.hasCustomLeftHand);
                    if (hasCustomHand && com.elfmcys.yesstevemodel.client.renderer.RendererManager.getHandRenderer()
                            .renderHandItem((net.minecraft.client.entity.player.ClientPlayerEntity) playerIn, ysmArmAssembly,
                                    ysmArmCapability, rightArm ? net.minecraft.util.HandSide.RIGHT : net.minecraft.util.HandSide.LEFT,
                                    matrixStackIn, bufferIn, combinedLightIn, Minecraft.getInstance().getRenderPartialTicks()))
                    {
                        return;
                    }
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

    protected void applyRotations(AbstractClientPlayerEntity entityLiving, MatrixStack matrixStackIn, float ageInTicks, float rotationYaw, float partialTicks)
    {
        float f = entityLiving.getSwimAnimation(partialTicks);

        if (entityLiving.isElytraFlying())
        {
            this.applyBaseRotations(entityLiving, matrixStackIn, ageInTicks, rotationYaw, partialTicks);
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
            this.applyBaseRotations(entityLiving, matrixStackIn, ageInTicks, rotationYaw, partialTicks);
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
            this.applyBaseRotations(entityLiving, matrixStackIn, ageInTicks, rotationYaw, partialTicks);
        }
    }

    private void applyBaseRotations(AbstractClientPlayerEntity entityLiving, MatrixStack matrixStackIn, float ageInTicks, float rotationYaw, float partialTicks)
    {
        super.applyRotations(entityLiving, matrixStackIn, ageInTicks, rotationYaw, partialTicks);
    }

    private boolean isOpenYsmRenderingEnabled()
    {
        return YesSteveModel.isEnabled() && YesSteveModel.getClientConfig().isRenderPlayers();
    }

    /**
     * True when mmdskin has a real model selected for this player (anything other than
     * the "default (vanilla)" entry). Used to keep the MMD -> YSM -> vanilla priority
     * chain intact on the first-person arm path.
     */
    private boolean isMmdModelSelected(AbstractClientPlayerEntity player)
    {
        if (!com.shiroha.mmdskin.MmdSkinClient.isInitialized())
        {
            return false;
        }
        String selectedModel = com.shiroha.mmdskin.ui.config.ModelSelectorConfig.getInstance()
                .getPlayerModel(player.getName().getString());
        return selectedModel != null && !selectedModel.isEmpty()
                && !com.shiroha.mmdskin.config.UIConstants.DEFAULT_MODEL_NAME.equals(selectedModel);
    }
}
