package net.minecraft.client.gui.screen.inventory;

import com.mojang.blaze3d.matrix.MatrixStack;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.AbstractGui;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.IRenderTypeBuffer;
import net.minecraft.client.renderer.ItemRenderer;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.WorldVertexBufferUploader;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.item.BundleContents;
import net.minecraft.item.BundleItem;
import net.minecraft.item.ItemStack;
import net.minecraft.util.IReorderingProcessor;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.vector.Matrix4f;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TranslationTextComponent;
import org.apache.commons.lang3.math.Fraction;

/**
 * 1.16.4 backport of 1.21.11 {@code client/gui/screens/inventory/tooltip/ClientBundleTooltip}.
 *
 * <p>1.16.4 has no {@code TooltipComponent} / {@code ClientTooltipComponent} infrastructure (added in 1.17), so
 * instead of returning a tooltip component from the item this class re-implements the tooltip frame layout of
 * {@code Screen#renderTooltip(MatrixStack, List, int, int)} with room for an "image" block underneath the text,
 * and is called from {@code Screen#renderTooltip(MatrixStack, ItemStack, int, int)}.
 *
 * <p>Two deliberate simplifications versus 1.21.11:
 * <ul>
 *   <li>1.20.2+ GUI sprite sheets / {@code .mcmeta} {@code nine_slice} scaling do not exist in 1.16.4, so the
 *       sprite PNGs are blitted directly and the nine-slice centre is <em>stretched</em> instead of tiled. The
 *       bundle bar sprites have flat centres, so the result is pixel-identical.</li>
 *   <li>The mouse-wheel item selection ({@code BundleMouseActions} + {@code ServerboundSelectBundleItemPacket})
 *       is not wired up; the highlight/selected-name rendering below is still implemented and honours the
 *       selection stored in NBT.</li>
 * </ul>
 */
public class BundleTooltipRenderer extends AbstractGui
{
    private static final String SPRITE_ROOT = "textures/gui/sprites/container/bundle/";
    private static final ResourceLocation PROGRESSBAR_BORDER_SPRITE = new ResourceLocation(SPRITE_ROOT + "bundle_progressbar_border.png");
    private static final ResourceLocation PROGRESSBAR_FILL_SPRITE = new ResourceLocation(SPRITE_ROOT + "bundle_progressbar_fill.png");
    private static final ResourceLocation PROGRESSBAR_FULL_SPRITE = new ResourceLocation(SPRITE_ROOT + "bundle_progressbar_full.png");
    private static final ResourceLocation SLOT_HIGHLIGHT_BACK_SPRITE = new ResourceLocation(SPRITE_ROOT + "slot_highlight_back.png");
    private static final ResourceLocation SLOT_HIGHLIGHT_FRONT_SPRITE = new ResourceLocation(SPRITE_ROOT + "slot_highlight_front.png");
    private static final ResourceLocation SLOT_BACKGROUND_SPRITE = new ResourceLocation(SPRITE_ROOT + "slot_background.png");

    /** Native sprite sizes taken from the 1.21.11 {@code .mcmeta} {@code nine_slice} width/height/border. */
    private static final int SLOT_SPRITE_SIZE = 24;
    private static final int SLOT_SPRITE_BORDER = 4;
    private static final int PROGRESSBAR_BORDER_SPRITE_SIZE = 12;
    private static final int PROGRESSBAR_BORDER_SPRITE_BORDER = 2;
    private static final int PROGRESSBAR_FILL_SPRITE_SIZE = 6;
    private static final int PROGRESSBAR_FILL_SPRITE_BORDER = 2;

    /** Official {@code ClientBundleTooltip.SLOT_MARGIN = 4} (1.21.11 ClientBundleTooltip.java:26). */
    private static final int SLOT_MARGIN = 4;

    /** Official {@code ClientBundleTooltip.SLOT_SIZE = 24} (ClientBundleTooltip.java:27). */
    private static final int SLOT_SIZE = 24;

    /** Official {@code ClientBundleTooltip.GRID_WIDTH = 96} (ClientBundleTooltip.java:28). */
    private static final int GRID_WIDTH = 96;

    /** Official {@code ClientBundleTooltip.PROGRESSBAR_HEIGHT = 13} (ClientBundleTooltip.java:29). */
    private static final int PROGRESSBAR_HEIGHT = 13;

    /** Official {@code ClientBundleTooltip.PROGRESSBAR_WIDTH = 96} (ClientBundleTooltip.java:30). */
    private static final int PROGRESSBAR_WIDTH = 96;

    /** Official {@code ClientBundleTooltip.PROGRESSBAR_BORDER = 1} (ClientBundleTooltip.java:31). */
    private static final int PROGRESSBAR_BORDER = 1;

    /** Official {@code ClientBundleTooltip.PROGRESSBAR_FILL_MAX = 94} (ClientBundleTooltip.java:32). */
    private static final int PROGRESSBAR_FILL_MAX = 94;

    /** Official {@code ClientBundleTooltip.PROGRESSBAR_MARGIN_Y = 4} (ClientBundleTooltip.java:33). */
    private static final int PROGRESSBAR_MARGIN_Y = 4;

    private static final ITextComponent BUNDLE_FULL_TEXT = new TranslationTextComponent("item.minecraft.bundle.full");
    private static final ITextComponent BUNDLE_EMPTY_TEXT = new TranslationTextComponent("item.minecraft.bundle.empty");
    private static final ITextComponent BUNDLE_EMPTY_DESCRIPTION = new TranslationTextComponent("item.minecraft.bundle.empty.description");

    /** Official {@code ClientBundleTooltip.drawEmptyBundleDescriptionText} colour {@code -5592406}. */
    private static final int EMPTY_DESCRIPTION_COLOR = -5592406;

    private BundleTooltipRenderer()
    {
    }

    /**
     * Entry point called from {@code Screen#renderTooltip(MatrixStack, ItemStack, int, int)}.
     *
     * @return {@code true} when the stack is a bundle and this class drew the tooltip.
     */
    public static boolean renderBundleTooltip(Screen screen, MatrixStack matrixStack, ItemStack stack, int mouseX, int mouseY)
    {
        if (!BundleItem.isBundle(stack))
        {
            return false;
        }

        Minecraft minecraft = Minecraft.getInstance();
        FontRenderer fontrenderer = minecraft.fontRenderer;
        BundleContents bundlecontents = BundleItem.getContents(stack);
        List<IReorderingProcessor> list = new ArrayList<IReorderingProcessor>();

        for (ITextComponent itextcomponent : screen.getTooltipFromItem(stack))
        {
            list.add(itextcomponent.func_241878_f());
        }

        int i = imageHeight(fontrenderer, bundlecontents);
        render(screen, matrixStack, fontrenderer, list, GRID_WIDTH, i, mouseX, mouseY, bundlecontents);
        return true;
    }

    // ------------------------------------------------------------------------------------------------
    // Layout - mirrors 1.16.4 Screen#renderTooltip plus 1.21.11's image-component sizing rules
    // ------------------------------------------------------------------------------------------------

    /** Official {@code ClientBundleTooltip.getHeight} (ClientBundleTooltip.java:44). */
    private static int imageHeight(FontRenderer font, BundleContents contents)
    {
        return contents.isEmpty()
               ? getEmptyBundleDescriptionTextHeight(font) + PROGRESSBAR_HEIGHT + 8
               : gridSizeY(contents) * SLOT_SIZE + PROGRESSBAR_HEIGHT + 8;
    }

    /** Official {@code ClientBundleTooltip.gridSizeY} (ClientBundleTooltip.java:74). */
    private static int gridSizeY(BundleContents contents)
    {
        // Official uses Mth.positiveCeilDiv (1.21.11 util/Mth.java:679), absent from 1.16.4's MathHelper.
        return -Math.floorDiv(-slotCount(contents), BundleItem.MAX_SHOWN_GRID_ITEMS_X);
    }

    /** Official {@code ClientBundleTooltip.slotCount} (ClientBundleTooltip.java:78). */
    private static int slotCount(BundleContents contents)
    {
        return Math.min(BundleItem.MAX_SHOWN_GRID_ITEMS, contents.size());
    }

    /** Official {@code ClientBundleTooltip.getContentXOffset} (ClientBundleTooltip.java:70). */
    private static int getContentXOffset(int tooltipWidth)
    {
        return (tooltipWidth - GRID_WIDTH) / 2;
    }

    /** Official {@code ClientBundleTooltip.getEmptyBundleDescriptionTextHeight} (ClientBundleTooltip.java:189). */
    private static int getEmptyBundleDescriptionTextHeight(FontRenderer font)
    {
        return font.trimStringToWidth(BUNDLE_EMPTY_DESCRIPTION, GRID_WIDTH).size() * 9;
    }

    /**
     * Copy of 1.16.4 {@code Screen#renderTooltip(MatrixStack, List, int, int)} extended with an image block that
     * is laid out below the text, exactly like 1.21.11's {@code renderTooltipInternal} treats
     * {@code ClientBundleTooltip}: text components contribute 10px each (minus 2 when there is only one component
     * in total), then a 2px gap after the first component, then the image height.
     */
    private static void render(Screen screen, MatrixStack matrixStack, FontRenderer font, List<IReorderingProcessor> tooltips, int imageWidth, int imageHeight, int mouseX, int mouseY, BundleContents contents)
    {
        int i = imageWidth;

        for (IReorderingProcessor ireorderingprocessor : tooltips)
        {
            int j = font.getStringWidth(ireorderingprocessor);

            if (j > i)
            {
                i = j;
            }
        }

        int i2 = mouseX + 12;
        int j2 = mouseY - 12;
        int k = tooltips.size() * 10 + imageHeight;

        if (i2 + i > screen.width)
        {
            i2 -= 28 + i;
        }

        if (j2 + k + 6 > screen.height)
        {
            j2 = screen.height - k - 6;
        }

        matrixStack.push();
        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder bufferbuilder = tessellator.getBuffer();
        bufferbuilder.begin(7, DefaultVertexFormats.POSITION_COLOR);
        Matrix4f matrix4f = matrixStack.getLast().getMatrix();
        fillGradient(matrix4f, bufferbuilder, i2 - 3, j2 - 4, i2 + i + 3, j2 - 3, 400, -267386864, -267386864);
        fillGradient(matrix4f, bufferbuilder, i2 - 3, j2 + k + 3, i2 + i + 3, j2 + k + 4, 400, -267386864, -267386864);
        fillGradient(matrix4f, bufferbuilder, i2 - 3, j2 - 3, i2 + i + 3, j2 + k + 3, 400, -267386864, -267386864);
        fillGradient(matrix4f, bufferbuilder, i2 - 4, j2 - 3, i2 - 3, j2 + k + 3, 400, -267386864, -267386864);
        fillGradient(matrix4f, bufferbuilder, i2 + i + 3, j2 - 3, i2 + i + 4, j2 + k + 3, 400, -267386864, -267386864);
        fillGradient(matrix4f, bufferbuilder, i2 - 3, j2 - 3 + 1, i2 - 3 + 1, j2 + k + 3 - 1, 400, 1347420415, 1344798847);
        fillGradient(matrix4f, bufferbuilder, i2 + i + 2, j2 - 3 + 1, i2 + i + 3, j2 + k + 3 - 1, 400, 1347420415, 1344798847);
        fillGradient(matrix4f, bufferbuilder, i2 - 3, j2 - 3, i2 + i + 3, j2 - 3 + 1, 400, 1347420415, 1347420415);
        fillGradient(matrix4f, bufferbuilder, i2 - 3, j2 + k + 2, i2 + i + 3, j2 + k + 3, 400, 1344798847, 1344798847);
        RenderSystem.enableDepthTest();
        RenderSystem.disableTexture();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.shadeModel(7425);
        bufferbuilder.finishDrawing();
        WorldVertexBufferUploader.draw(bufferbuilder);
        RenderSystem.shadeModel(7424);
        RenderSystem.disableBlend();
        RenderSystem.enableTexture();
        IRenderTypeBuffer.Impl irendertypebuffer$impl = IRenderTypeBuffer.getImpl(Tessellator.getInstance().getBuffer());
        matrixStack.translate(0.0D, 0.0D, 400.0D);
        int l = j2;

        for (int i1 = 0; i1 < tooltips.size(); ++i1)
        {
            IReorderingProcessor ireorderingprocessor1 = tooltips.get(i1);

            if (ireorderingprocessor1 != null)
            {
                font.func_238416_a_(ireorderingprocessor1, (float)i2, (float)l, -1, true, matrix4f, irendertypebuffer$impl, false, 0, 15728880);
            }

            l += 10;

            if (i1 == 0)
            {
                l += 2;
            }
        }

        irendertypebuffer$impl.finish();
        renderImage(matrixStack, font, i2, l, i, contents);
        matrixStack.pop();
    }

    // ------------------------------------------------------------------------------------------------
    // Contents rendering
    // ------------------------------------------------------------------------------------------------

    /** Official {@code ClientBundleTooltip.renderImage} (ClientBundleTooltip.java:83). */
    private static void renderImage(MatrixStack matrixStack, FontRenderer font, int x, int y, int tooltipWidth, BundleContents contents)
    {
        if (contents.isEmpty())
        {
            // Official renderEmptyBundleTooltip (ClientBundleTooltip.java:91)
            drawEmptyBundleDescriptionText(matrixStack, x + getContentXOffset(tooltipWidth), y, font);
            drawProgressbar(matrixStack, x + getContentXOffset(tooltipWidth), y + getEmptyBundleDescriptionTextHeight(font) + PROGRESSBAR_MARGIN_Y, font, contents);
        }
        else
        {
            // Official renderBundleWithItemsTooltip (ClientBundleTooltip.java:96)
            boolean flag = contents.size() > BundleItem.MAX_SHOWN_GRID_ITEMS;
            List<ItemStack> list = getShownItems(contents, contents.getNumberOfItemsToShow());
            int i = x + getContentXOffset(tooltipWidth) + GRID_WIDTH;
            int j = y + gridSizeY(contents) * SLOT_SIZE;
            int k = 1;

            for (int l = 1; l <= gridSizeY(contents); ++l)
            {
                for (int i1 = 1; i1 <= BundleItem.MAX_SHOWN_GRID_ITEMS_X; ++i1)
                {
                    int j1 = i - i1 * SLOT_SIZE;
                    int k1 = j - l * SLOT_SIZE;

                    if (shouldRenderSurplusText(flag, i1, l))
                    {
                        renderCount(matrixStack, j1, k1, getAmountOfHiddenItems(contents, list), font);
                    }
                    else if (list.size() >= k)
                    {
                        renderSlot(matrixStack, k, j1, k1, list, font, contents);
                        ++k;
                    }
                }
            }

            drawSelectedItemTooltip(matrixStack, font, x, y, tooltipWidth, contents);
            drawProgressbar(matrixStack, x + getContentXOffset(tooltipWidth), y + gridSizeY(contents) * SLOT_SIZE + PROGRESSBAR_MARGIN_Y, font, contents);
        }
    }

    /** Official {@code ClientBundleTooltip.getShownItems} (ClientBundleTooltip.java:120). */
    private static List<ItemStack> getShownItems(BundleContents contents, int max)
    {
        int i = Math.min(contents.size(), max);
        return contents.itemCopyList().subList(0, i);
    }

    /** Official {@code ClientBundleTooltip.shouldRenderSurplusText} (ClientBundleTooltip.java:125). */
    private static boolean shouldRenderSurplusText(boolean overflowing, int column, int row)
    {
        return overflowing && column * row == 1;
    }

    /** Official {@code ClientBundleTooltip.getAmountOfHiddenItems} (ClientBundleTooltip.java:133). */
    private static int getAmountOfHiddenItems(BundleContents contents, List<ItemStack> shown)
    {
        int i = 0;
        List<ItemStack> list = contents.items();

        for (int j = shown.size(); j < list.size(); ++j)
        {
            i += list.get(j).getCount();
        }

        return i;
    }

    /** Official {@code ClientBundleTooltip.renderSlot} (ClientBundleTooltip.java:137). */
    private static void renderSlot(MatrixStack matrixStack, int slot, int x, int y, List<ItemStack> shown, FontRenderer font, BundleContents contents)
    {
        int i = shown.size() - slot;
        boolean flag = i == contents.getSelectedItem();
        ItemStack itemstack = shown.get(i);

        if (flag)
        {
            blitSprite(matrixStack, SLOT_HIGHLIGHT_BACK_SPRITE, x, y, SLOT_SIZE, SLOT_SIZE, SLOT_SPRITE_SIZE, SLOT_SPRITE_BORDER);
        }
        else
        {
            blitSprite(matrixStack, SLOT_BACKGROUND_SPRITE, x, y, SLOT_SIZE, SLOT_SIZE, SLOT_SPRITE_SIZE, SLOT_SPRITE_BORDER);
        }

        ItemRenderer itemrenderer = Minecraft.getInstance().getItemRenderer();
        float f = itemrenderer.zLevel;
        // 1.16.4 renders GUI items with their own matrix at z = 100 + zLevel; the tooltip frame lives at z = 400.
        itemrenderer.zLevel += 400.0F;
        itemrenderer.renderItemAndEffectIntoGUI(itemstack, x + SLOT_MARGIN, y + SLOT_MARGIN);
        itemrenderer.renderItemOverlayIntoGUI(font, itemstack, x + SLOT_MARGIN, y + SLOT_MARGIN, (String)null);
        itemrenderer.zLevel = f;
        RenderSystem.enableDepthTest();

        if (flag)
        {
            blitSprite(matrixStack, SLOT_HIGHLIGHT_FRONT_SPRITE, x, y, SLOT_SIZE, SLOT_SIZE, SLOT_SPRITE_SIZE, SLOT_SPRITE_BORDER);
        }
    }

    /** Official {@code ClientBundleTooltip.renderCount} (ClientBundleTooltip.java:154). */
    private static void renderCount(MatrixStack matrixStack, int x, int y, int hidden, FontRenderer font)
    {
        String s = "+" + hidden;
        font.drawStringWithShadow(matrixStack, s, (float)(x + 12 - font.getStringWidth(s) / 2), (float)(y + 10), -1);
    }

    /** Official {@code ClientBundleTooltip.drawSelectedItemTooltip} (ClientBundleTooltip.java:158). */
    private static void drawSelectedItemTooltip(MatrixStack matrixStack, FontRenderer font, int x, int y, int tooltipWidth, BundleContents contents)
    {
        if (contents.hasSelectedItem())
        {
            ItemStack itemstack = contents.getItemUnsafe(contents.getSelectedItem());
            ITextComponent itextcomponent = itemstack.getDisplayName();
            int i = font.getStringWidth(itextcomponent.getString());
            int j = x + tooltipWidth / 2 - 12;
            font.drawStringWithShadow(matrixStack, itextcomponent.getString(), (float)(j - i / 2), (float)(y - 15), -1);
        }
    }

    /** Official {@code ClientBundleTooltip.drawProgressbar} (ClientBundleTooltip.java:176). */
    private static void drawProgressbar(MatrixStack matrixStack, int x, int y, FontRenderer font, BundleContents contents)
    {
        blitSprite(matrixStack, getProgressBarTexture(contents), x + PROGRESSBAR_BORDER, y, getProgressBarFill(contents), PROGRESSBAR_HEIGHT, PROGRESSBAR_FILL_SPRITE_SIZE, PROGRESSBAR_FILL_SPRITE_BORDER);
        blitSprite(matrixStack, PROGRESSBAR_BORDER_SPRITE, x, y, PROGRESSBAR_WIDTH, PROGRESSBAR_HEIGHT, PROGRESSBAR_BORDER_SPRITE_SIZE, PROGRESSBAR_BORDER_SPRITE_BORDER);
        ITextComponent itextcomponent = getProgressBarFillText(contents);

        if (itextcomponent != null)
        {
            String s = itextcomponent.getString();
            font.drawStringWithShadow(matrixStack, s, (float)(x + 48 - font.getStringWidth(s) / 2), (float)(y + 3), -1);
        }
    }

    /** Official {@code ClientBundleTooltip.drawEmptyBundleDescriptionText} (ClientBundleTooltip.java:185). */
    private static void drawEmptyBundleDescriptionText(MatrixStack matrixStack, int x, int y, FontRenderer font)
    {
        List<IReorderingProcessor> list = font.trimStringToWidth(BUNDLE_EMPTY_DESCRIPTION, GRID_WIDTH);

        for (int i = 0; i < list.size(); ++i)
        {
            // func_238422_b_ == drawString without shadow, matching the official drawWordWrap call.
            font.func_238422_b_(matrixStack, list.get(i), (float)x, (float)(y + i * 9), EMPTY_DESCRIPTION_COLOR);
        }
    }

    /** Official {@code ClientBundleTooltip.getProgressBarFill} (ClientBundleTooltip.java:193). */
    private static int getProgressBarFill(BundleContents contents)
    {
        return MathHelper.clamp(BundleItem.mulAndTruncate(contents.weight(), PROGRESSBAR_FILL_MAX), 0, PROGRESSBAR_FILL_MAX);
    }

    /** Official {@code ClientBundleTooltip.getProgressBarTexture} (ClientBundleTooltip.java:197). */
    private static ResourceLocation getProgressBarTexture(BundleContents contents)
    {
        return contents.weight().compareTo(Fraction.ONE) >= 0 ? PROGRESSBAR_FULL_SPRITE : PROGRESSBAR_FILL_SPRITE;
    }

    /** Official {@code ClientBundleTooltip.getProgressBarFillText} (ClientBundleTooltip.java:201). */
    @Nullable
    private static ITextComponent getProgressBarFillText(BundleContents contents)
    {
        if (contents.isEmpty())
        {
            return BUNDLE_EMPTY_TEXT;
        }
        else
        {
            return contents.weight().compareTo(Fraction.ONE) >= 0 ? BUNDLE_FULL_TEXT : null;
        }
    }

    // ------------------------------------------------------------------------------------------------
    // Sprite blitting
    // ------------------------------------------------------------------------------------------------

    /**
     * Stand-in for {@code GuiGraphics.blitSprite} with {@code nine_slice} scaling. See the class javadoc: the
     * centre region is stretched rather than tiled, which is visually identical for these flat sprites.
     */
    private static void blitSprite(MatrixStack matrixStack, ResourceLocation texture, int x, int y, int width, int height, int spriteSize, int border)
    {
        if (width <= 0 || height <= 0)
        {
            return;
        }

        Minecraft.getInstance().getTextureManager().bindTexture(texture);
        RenderSystem.enableBlend();
        RenderSystem.blendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA, GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);

        if (width == spriteSize && height == spriteSize)
        {
            blit(matrixStack, x, y, width, height, 0.0F, 0.0F, spriteSize, spriteSize, spriteSize, spriteSize);
        }
        else
        {
            // Official GuiGraphics#blitNineSlicedSprite clamps the borders to half the target size.
            int i = Math.min(border, width / 2);
            int j = Math.min(border, height / 2);
            int k = spriteSize - 2 * border;
            int l = width - 2 * i;
            int i1 = height - 2 * j;

            // corners
            blit(matrixStack, x, y, i, j, 0.0F, 0.0F, border, border, spriteSize, spriteSize);
            blit(matrixStack, x + width - i, y, i, j, (float)(spriteSize - border), 0.0F, border, border, spriteSize, spriteSize);
            blit(matrixStack, x, y + height - j, i, j, 0.0F, (float)(spriteSize - border), border, border, spriteSize, spriteSize);
            blit(matrixStack, x + width - i, y + height - j, i, j, (float)(spriteSize - border), (float)(spriteSize - border), border, border, spriteSize, spriteSize);

            // edges
            if (l > 0)
            {
                blit(matrixStack, x + i, y, l, j, (float)border, 0.0F, k, border, spriteSize, spriteSize);
                blit(matrixStack, x + i, y + height - j, l, j, (float)border, (float)(spriteSize - border), k, border, spriteSize, spriteSize);
            }

            if (i1 > 0)
            {
                blit(matrixStack, x, y + j, i, i1, 0.0F, (float)border, border, k, spriteSize, spriteSize);
                blit(matrixStack, x + width - i, y + j, i, i1, (float)(spriteSize - border), (float)border, border, k, spriteSize, spriteSize);
            }

            // centre
            if (l > 0 && i1 > 0)
            {
                blit(matrixStack, x + i, y + j, l, i1, (float)border, (float)border, k, k, spriteSize, spriteSize);
            }
        }

        RenderSystem.disableBlend();
    }
}
