package net.minecraft.client.renderer.tileentity;

import com.mojang.blaze3d.matrix.MatrixStack;
import com.mojang.blaze3d.vertex.IVertexBuilder;
import java.util.List;
import net.minecraft.block.Block;
import net.minecraft.block.AbstractSignBlock;
import net.minecraft.block.BlockState;
import net.minecraft.block.ModernCeilingHangingSignBlock;
import net.minecraft.block.ModernRotationSegment;
import net.minecraft.block.ModernWallHangingSignBlock;
import net.minecraft.block.WoodType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.Atlases;
import net.minecraft.client.renderer.IRenderTypeBuffer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.model.Model;
import net.minecraft.client.renderer.model.ModelRenderer;
import net.minecraft.client.renderer.model.RenderMaterial;
import net.minecraft.client.renderer.texture.NativeImage;
import net.minecraft.entity.Entity;
import net.minecraft.tileentity.ModernHangingSignTileEntity;
import net.minecraft.util.IReorderingProcessor;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.vector.Vector3f;
import net.optifine.Config;
import net.optifine.CustomColors;
import net.optifine.shaders.Shaders;

/**
 * 悬挂告示牌渲染器（1.19 加入），移植自官方
 * {@code client/renderer/blockentity/HangingSignRenderer} 与它的基类
 * {@code AbstractSignRenderer}。
 *
 * <p>所有坐标、缩放、行高都注明了官方出处，没有一个是估的。
 * 变换链的结构与 1.16.4 的 {@link SignTileEntityRenderer} 一致
 * （先平移旋转、再压栈缩放画模型、出栈后画字），只是常量不同。
 */
public class ModernHangingSignTileEntityRenderer extends TileEntityRenderer<ModernHangingSignTileEntity>
{
    /**
     * 官方 {@code HangingSignRenderer.MODEL_RENDER_SCALE = 1.0F}。
     * 普通告示牌是 0.6666667，悬挂告示牌不缩放。
     */
    private static final float MODEL_RENDER_SCALE = 1.0F;

    /** 官方 {@code HangingSignRenderer.TEXT_RENDER_SCALE = 0.9F}。 */
    private static final float TEXT_RENDER_SCALE = 0.9F;

    /**
     * 官方 {@code AbstractSignRenderer.translateSignText} 里的
     * {@code float f = 0.015625F * getSignTextRenderScale()}。
     *
     * <p>对照：1.16.4 普通告示牌写死的 0.010416667 就是
     * {@code 0.015625 * 0.6666667}，同一个式子。
     */
    private static final float TEXT_SCALE = 0.015625F * TEXT_RENDER_SCALE;

    /** 官方 {@code HangingSignRenderer.TEXT_OFFSET = new Vec3(0.0, -0.32F, 0.073F)}。 */
    private static final float TEXT_OFFSET_Y = -0.32F;
    private static final float TEXT_OFFSET_Z = 0.073F;

    private final ModernHangingSignTileEntityRenderer.HangingSignModel model = new ModernHangingSignTileEntityRenderer.HangingSignModel();

    public ModernHangingSignTileEntityRenderer(TileEntityRendererDispatcher rendererDispatcherIn)
    {
        super(rendererDispatcherIn);
    }

    @Override
    public void render(ModernHangingSignTileEntity tileEntityIn, float partialTicks, MatrixStack matrixStackIn, IRenderTypeBuffer bufferIn, int combinedLightIn, int combinedOverlayIn)
    {
        BlockState blockstate = tileEntityIn.getBlockState();
        Block block = blockstate.getBlock();

        // 官方 AbstractSignRenderer.submitSignWithText 传入的是
        // -signblock.getYRotationDegrees(state)。
        float yRot;
        ModernHangingSignTileEntityRenderer.AttachmentType attachment;

        if (block instanceof ModernCeilingHangingSignBlock)
        {
            // 官方 CeilingHangingSignBlock.getYRotationDegrees =
            // RotationSegment.convertToDegrees(ROTATION)。
            yRot = -ModernRotationSegment.convertToDegrees(blockstate.get(ModernCeilingHangingSignBlock.ROTATION));
            // 官方 HangingSignRenderer.AttachmentType.byBlockState：
            // 吊顶时 attached=true -> CEILING_MIDDLE（一条 V 形短链），
            // false -> CEILING（两侧斜链）。
            attachment = blockstate.get(ModernCeilingHangingSignBlock.ATTACHED)
                    ? ModernHangingSignTileEntityRenderer.AttachmentType.CEILING_MIDDLE
                    : ModernHangingSignTileEntityRenderer.AttachmentType.CEILING;
        }
        else if (block instanceof ModernWallHangingSignBlock)
        {
            // 官方 WallHangingSignBlock.getYRotationDegrees = FACING.toYRot()，
            // 1.16.4 叫 getHorizontalAngle()。
            yRot = -blockstate.get(ModernWallHangingSignBlock.FACING).getHorizontalAngle();
            attachment = ModernHangingSignTileEntityRenderer.AttachmentType.WALL;
        }
        else
        {
            return;
        }

        matrixStackIn.push();

        // 官方 HangingSignRenderer.translateBase。
        matrixStackIn.translate(0.5D, 0.9375D, 0.5D);
        matrixStackIn.rotate(Vector3f.YP.rotationDegrees(yRot));
        matrixStackIn.translate(0.0D, -0.3125D, 0.0D);

        // 官方 AbstractSignRenderer.submitSign：scale(f, -f, -f)。
        matrixStackIn.push();
        matrixStackIn.scale(MODEL_RENDER_SCALE, -MODEL_RENDER_SCALE, -MODEL_RENDER_SCALE);
        RenderMaterial rendermaterial = getMaterial(block);
        IVertexBuilder ivertexbuilder = rendermaterial.getBuffer(bufferIn, this.model::getRenderType);
        this.model.setAttachmentType(attachment);
        this.model.renderParts(matrixStackIn, ivertexbuilder, combinedLightIn, combinedOverlayIn);
        matrixStackIn.pop();

        if (isRenderText(tileEntityIn))
        {
            this.renderText(tileEntityIn, matrixStackIn, bufferIn, combinedLightIn);
        }

        matrixStackIn.pop();
    }

    /**
     * 官方 {@code AbstractSignRenderer.submitSignText} + {@code translateSignText}。
     *
     * <p><b>只画正面。</b>官方会分别画 front / back 两面文本，1.16.4 的
     * {@code SignTileEntity} 只有一组四行文本，没有背面，所以省掉那一遍
     * （不是渲染缺陷，是数据模型层就没有背面文本，见报告）。
     */
    private void renderText(ModernHangingSignTileEntity tileEntityIn, MatrixStack matrixStackIn, IRenderTypeBuffer bufferIn, int combinedLightIn)
    {
        FontRenderer fontrenderer = this.renderDispatcher.getFontRenderer();
        matrixStackIn.translate(0.0D, (double)TEXT_OFFSET_Y, (double)TEXT_OFFSET_Z);
        matrixStackIn.scale(TEXT_SCALE, -TEXT_SCALE, TEXT_SCALE);

        int textColor = tileEntityIn.getTextColor().getTextColor();

        if (Config.isCustomColors())
        {
            textColor = CustomColors.getSignTextColor(textColor);
        }

        // 官方 AbstractSignRenderer.getDarkColor：非发光文本按 0.4 压暗。
        // 1.16.4 的 SignTileEntityRenderer 也是这么算的。
        double dim = 0.4D;
        int r = (int)((double)NativeImage.getRed(textColor) * dim);
        int g = (int)((double)NativeImage.getGreen(textColor) * dim);
        int b = (int)((double)NativeImage.getBlue(textColor) * dim);
        int packedColor = NativeImage.getCombined(0, b, g, r);

        // 官方 submitSignText：j = 4 * textLineHeight / 2，第 i 行的 y = i * lineHeight - j。
        // 悬挂告示牌 lineHeight = 9（普通告示牌是 10），所以 j = 18。
        int lineHeight = ModernHangingSignTileEntity.TEXT_LINE_HEIGHT;
        int yBase = 4 * lineHeight / 2;

        for (int line = 0; line < 4; ++line)
        {
            IReorderingProcessor ireorderingprocessor = tileEntityIn.func_242686_a(line, (text) ->
            {
                // 官方 maxTextLineWidth：悬挂告示牌 60，普通告示牌 90。
                List<IReorderingProcessor> list = fontrenderer.trimStringToWidth(text, ModernHangingSignTileEntity.MAX_TEXT_LINE_WIDTH);
                return list.isEmpty() ? IReorderingProcessor.field_242232_a : list.get(0);
            });

            if (ireorderingprocessor != null)
            {
                float x = (float)(-fontrenderer.getStringWidth(ireorderingprocessor) / 2);
                fontrenderer.func_238416_a_(ireorderingprocessor, x, (float)(line * lineHeight - yBase), packedColor, false, matrixStackIn.getLast().getMatrix(), bufferIn, false, 0, combinedLightIn);
            }
        }
    }

    /** 官方 {@code HangingSignRenderer.getSignMaterial} -> {@code Sheets.getHangingSignMaterial}。 */
    public static RenderMaterial getMaterial(Block blockIn)
    {
        WoodType woodtype = blockIn instanceof AbstractSignBlock
                ? ((AbstractSignBlock)blockIn).getWoodType()
                : WoodType.OAK;
        return Atlases.getHangingSignMaterial(woodtype);
    }

    /** 与 {@link SignTileEntityRenderer#isRenderText} 同一套 OptiFine 距离剔除。 */
    private static boolean isRenderText(ModernHangingSignTileEntity tileEntityIn)
    {
        if (Shaders.isShadowPass)
        {
            return false;
        }

        if (!Config.zoomMode)
        {
            BlockPos blockpos = tileEntityIn.getPos();
            Entity entity = Minecraft.getInstance().getRenderViewEntity();
            double distSq = entity.getDistanceSq((double)blockpos.getX(), (double)blockpos.getY(), (double)blockpos.getZ());

            if (distSq > SignTileEntityRenderer.getTextRenderDistanceSq())
            {
                return false;
            }
        }

        return true;
    }

    /** 官方 {@code HangingSignRenderer.AttachmentType}。 */
    public static enum AttachmentType
    {
        WALL,
        CEILING,
        CEILING_MIDDLE;
    }

    /**
     * 悬挂告示牌模型，逐个 cube 移植自官方
     * {@code HangingSignRenderer.createHangingSignLayer}
     * （{@code LayerDefinition.create(meshdefinition, 64, 32)} -> 贴图 64x32）。
     *
     * <p>官方按 {@code AttachmentType} 生成三套不同的 LayerDefinition；
     * 1.16.4 没有 LayerDefinition 机制，这里建一套完整部件、按类型开关
     * {@code showModel}，与 1.16.4 {@code SignModel} 用 {@code signStick.showModel}
     * 区分立牌/壁挂是同一个套路。
     */
    public static final class HangingSignModel extends Model
    {
        /** 官方 "board"：texOffs(0, 12) addBox(-7, 0, -1, 14, 10, 2)。所有形态都有。 */
        public final ModelRenderer board;
        /** 官方 "plank"：texOffs(0, 0) addBox(-8, -6, -2, 16, 2, 4)。只有 WALL 有。 */
        public final ModelRenderer plank;
        /** 官方 "chainL1"：texOffs(0, 6) addBox(-1.5, 0, 0, 3, 6, 0)，offsetAndRotation(-5, -6, 0, 0, -PI/4, 0)。 */
        public final ModelRenderer chainL1;
        /** 官方 "chainL2"：texOffs(6, 6)，同 box，rotY = +PI/4。 */
        public final ModelRenderer chainL2;
        /** 官方 "chainR1"：texOffs(0, 6)，offsetAndRotation(5, -6, 0, 0, -PI/4, 0)。 */
        public final ModelRenderer chainR1;
        /** 官方 "chainR2"：texOffs(6, 6)，offset(5, -6, 0)，rotY = +PI/4。 */
        public final ModelRenderer chainR2;
        /** 官方 "vChains"：texOffs(14, 6) addBox(-6, -6, 0, 12, 6, 0)。只有 CEILING_MIDDLE 有。 */
        public final ModelRenderer vChains;

        public HangingSignModel()
        {
            super(RenderType::getEntityCutoutNoCull);

            this.board = new ModelRenderer(64, 32, 0, 12);
            this.board.addBox(-7.0F, 0.0F, -1.0F, 14.0F, 10.0F, 2.0F, 0.0F);

            this.plank = new ModelRenderer(64, 32, 0, 0);
            this.plank.addBox(-8.0F, -6.0F, -2.0F, 16.0F, 2.0F, 4.0F, 0.0F);

            this.chainL1 = makeChain(0, -5.0F, -((float)Math.PI / 4.0F));
            this.chainL2 = makeChain(6, -5.0F, (float)Math.PI / 4.0F);
            this.chainR1 = makeChain(0, 5.0F, -((float)Math.PI / 4.0F));
            this.chainR2 = makeChain(6, 5.0F, (float)Math.PI / 4.0F);

            this.vChains = new ModelRenderer(64, 32, 14, 6);
            this.vChains.addBox(-6.0F, -6.0F, 0.0F, 12.0F, 6.0F, 0.0F, 0.0F);
        }

        /**
         * 链条是<b>零厚度的平面</b>（depth = 0），靠 {@code entityCutoutNoCull}
         * 双面可见 —— 官方就是这么做的，不是简化。
         */
        private static ModelRenderer makeChain(int texOffX, float offsetX, float rotY)
        {
            ModelRenderer chain = new ModelRenderer(64, 32, texOffX, 6);
            chain.addBox(-1.5F, 0.0F, 0.0F, 3.0F, 6.0F, 0.0F, 0.0F);
            chain.setRotationPoint(offsetX, -6.0F, 0.0F);
            chain.rotateAngleY = rotY;
            return chain;
        }

        /** 按挂法开关部件，对应官方三套 LayerDefinition 的差异。 */
        public void setAttachmentType(ModernHangingSignTileEntityRenderer.AttachmentType type)
        {
            // 官方：plank 只在 WALL 出现。
            this.plank.showModel = type == ModernHangingSignTileEntityRenderer.AttachmentType.WALL;
            // 官方：normalChains 在 WALL 与 CEILING 出现。
            boolean normalChains = type == ModernHangingSignTileEntityRenderer.AttachmentType.WALL
                    || type == ModernHangingSignTileEntityRenderer.AttachmentType.CEILING;
            this.chainL1.showModel = normalChains;
            this.chainL2.showModel = normalChains;
            this.chainR1.showModel = normalChains;
            this.chainR2.showModel = normalChains;
            // 官方：vChains 只在 CEILING_MIDDLE 出现。
            this.vChains.showModel = type == ModernHangingSignTileEntityRenderer.AttachmentType.CEILING_MIDDLE;
        }

        public void renderParts(MatrixStack matrixStackIn, IVertexBuilder bufferIn, int packedLightIn, int packedOverlayIn)
        {
            this.board.render(matrixStackIn, bufferIn, packedLightIn, packedOverlayIn);
            this.plank.render(matrixStackIn, bufferIn, packedLightIn, packedOverlayIn);
            this.chainL1.render(matrixStackIn, bufferIn, packedLightIn, packedOverlayIn);
            this.chainL2.render(matrixStackIn, bufferIn, packedLightIn, packedOverlayIn);
            this.chainR1.render(matrixStackIn, bufferIn, packedLightIn, packedOverlayIn);
            this.chainR2.render(matrixStackIn, bufferIn, packedLightIn, packedOverlayIn);
            this.vChains.render(matrixStackIn, bufferIn, packedLightIn, packedOverlayIn);
        }

        @Override
        public void render(MatrixStack matrixStackIn, IVertexBuilder bufferIn, int packedLightIn, int packedOverlayIn, float red, float green, float blue, float alpha)
        {
            this.board.render(matrixStackIn, bufferIn, packedLightIn, packedOverlayIn, red, green, blue, alpha);
            this.plank.render(matrixStackIn, bufferIn, packedLightIn, packedOverlayIn, red, green, blue, alpha);
            this.chainL1.render(matrixStackIn, bufferIn, packedLightIn, packedOverlayIn, red, green, blue, alpha);
            this.chainL2.render(matrixStackIn, bufferIn, packedLightIn, packedOverlayIn, red, green, blue, alpha);
            this.chainR1.render(matrixStackIn, bufferIn, packedLightIn, packedOverlayIn, red, green, blue, alpha);
            this.chainR2.render(matrixStackIn, bufferIn, packedLightIn, packedOverlayIn, red, green, blue, alpha);
            this.vChains.render(matrixStackIn, bufferIn, packedLightIn, packedOverlayIn, red, green, blue, alpha);
        }
    }
}
