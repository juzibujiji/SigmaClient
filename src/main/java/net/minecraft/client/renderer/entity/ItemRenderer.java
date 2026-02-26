package net.minecraft.client.renderer.entity;

import com.mentalfrostbyte.Client;
import com.mentalfrostbyte.jello.module.Module;
import com.mentalfrostbyte.jello.module.impl.render.ItemPhysics;
import com.mojang.blaze3d.matrix.MatrixStack;
import java.util.Random;
import net.minecraft.client.renderer.IRenderTypeBuffer;
import net.minecraft.client.renderer.model.IBakedModel;
import net.minecraft.client.renderer.model.ItemCameraTransforms;
import net.minecraft.client.renderer.texture.AtlasTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.item.ItemEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.vector.Vector3f;

public class ItemRenderer extends EntityRenderer<ItemEntity>
{
    private final net.minecraft.client.renderer.ItemRenderer itemRenderer;
    private final Random random = new Random();

    public ItemRenderer(EntityRendererManager renderManagerIn, net.minecraft.client.renderer.ItemRenderer itemRendererIn)
    {
        super(renderManagerIn);
        this.itemRenderer = itemRendererIn;
        this.shadowSize = 0.15F;
        this.shadowOpaque = 0.75F;
    }

    private int getModelCount(ItemStack stack)
    {
        int i = 1;

        if (stack.getCount() > 48)
        {
            i = 5;
        }
        else if (stack.getCount() > 32)
        {
            i = 4;
        }
        else if (stack.getCount() > 16)
        {
            i = 3;
        }
        else if (stack.getCount() > 1)
        {
            i = 2;
        }

        return i;
    }

    public void render(ItemEntity entityIn, float entityYaw, float partialTicks, MatrixStack matrixStackIn, IRenderTypeBuffer bufferIn, int packedLightIn) {
        Module module = Client.getInstance().moduleManager.getModuleByClass(ItemPhysics.class);
        matrixStackIn.push();
        ItemStack itemstack = entityIn.getItem();
        int i = itemstack.isEmpty() ? 187 : Item.getIdFromItem(itemstack.getItem()) + itemstack.getDamage();
        this.random.setSeed((long) i);
        IBakedModel ibakedmodel = this.itemRenderer.getItemModelWithOverrides(itemstack, entityIn.world, (LivingEntity) null);
        boolean is3D = ibakedmodel.isGui3d();
        int itemCount = this.getModelCount(itemstack);

        float hoverOffset = MathHelper.sin(((float) entityIn.getAge() + partialTicks) / 10.0F + entityIn.hoverStart) * 0.1F + 0.1F;
        if(module.isEnabled() && module.getBooleanValueFromSettingName("Disable Floating")){
            hoverOffset = 0;
        }
        matrixStackIn.translate(0.0D, (double) (hoverOffset + 0.1F), 0.0D);

        if (!entityIn.isOnGround()) {

            float rotationSpeed = 5.0F * module.getNumberValueBySettingName("Gravity Value");
            if(module.getBooleanValueFromSettingName("Enable Gravity") && module.isEnabled()){
                matrixStackIn.rotate(Vector3f.ZP.rotationDegrees(entityIn.getAge() * rotationSpeed));
                matrixStackIn.rotate(Vector3f.YP.rotationDegrees(-entityIn.getAge() * rotationSpeed));
            }

        }

        if ((module.isEnabled() && module.getBooleanValueFromSettingName("Loaf Always")) || entityIn.isOnGround() && module.isEnabled()) {
            matrixStackIn.rotate(Vector3f.XP.rotationDegrees(90.0F));
            matrixStackIn.translate(0.0D, -0.1D, 0.0D);
        }

        if(module.isEnabled()){
            if (is3D) matrixStackIn.scale(0.8F, 0.8F, 0.8F);
        }

        for (int k = 0; k < itemCount; ++k) {
            matrixStackIn.push();

            if (k > 0) {
                float offsetX = (this.random.nextFloat() * 2.0F - 1.0F) * 0.15F;
                float offsetY = (this.random.nextFloat() * 2.0F - 1.0F) * 0.15F;
                float offsetZ = (this.random.nextFloat() * 2.0F - 1.0F) * 0.15F;
                matrixStackIn.translate(offsetX, offsetY, offsetZ);
            }

            this.itemRenderer.renderItem(itemstack, ItemCameraTransforms.TransformType.GROUND, false, matrixStackIn, bufferIn, packedLightIn, OverlayTexture.NO_OVERLAY, ibakedmodel);
            matrixStackIn.pop();
        }

        matrixStackIn.pop();
        super.render(entityIn, entityYaw, partialTicks, matrixStackIn, bufferIn, packedLightIn);
    }


    /**
     * Returns the location of an entity's texture.
     */
    public ResourceLocation getEntityTexture(ItemEntity entity)
    {
        return AtlasTexture.LOCATION_BLOCKS_TEXTURE;
    }

    public boolean shouldSpreadItems()
    {
        return true;
    }

    public boolean shouldBob()
    {
        return true;
    }
}
