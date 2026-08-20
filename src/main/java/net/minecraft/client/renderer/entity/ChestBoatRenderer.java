package net.minecraft.client.renderer.entity;

import net.minecraft.client.renderer.entity.model.BoatModel;
import net.minecraft.client.renderer.entity.model.ChestBoatModel;
import net.minecraft.client.renderer.entity.model.ChestRaftModel;
import net.minecraft.entity.item.BoatEntity;
import net.minecraft.util.ResourceLocation;

/**
 * Chest boat / chest raft renderer. Official 1.21.11 reuses BoatRenderer / RaftRenderer with a
 * per-wood ModelLayerLocation that resolves both the model and "textures/entity/<layer>.png";
 * here the wood still comes from BoatEntity.Type, so the texture is looked up by ordinal like
 * BoatRenderer does.
 */
public class ChestBoatRenderer extends BoatRenderer
{
    /**
     * Indexed by BoatEntity.Type.ordinal() - order must match the enum.
     */
    private static final ResourceLocation[] CHEST_BOAT_TEXTURES = new ResourceLocation[] {new ResourceLocation("textures/entity/chest_boat/oak.png"), new ResourceLocation("textures/entity/chest_boat/spruce.png"), new ResourceLocation("textures/entity/chest_boat/birch.png"), new ResourceLocation("textures/entity/chest_boat/jungle.png"), new ResourceLocation("textures/entity/chest_boat/acacia.png"), new ResourceLocation("textures/entity/chest_boat/dark_oak.png"), new ResourceLocation("textures/entity/chest_boat/cherry.png"), new ResourceLocation("textures/entity/chest_boat/pale_oak.png"), new ResourceLocation("textures/entity/chest_boat/mangrove.png"), new ResourceLocation("textures/entity/chest_boat/bamboo.png")};
    private final BoatModel modelChestBoat = new ChestBoatModel();
    private final BoatModel modelChestRaft = new ChestRaftModel();

    public ChestBoatRenderer(EntityRendererManager renderManagerIn)
    {
        super(renderManagerIn);
    }

    protected BoatModel getBoatModel(BoatEntity entityIn)
    {
        return entityIn.getBoatType() == BoatEntity.Type.BAMBOO ? this.modelChestRaft : this.modelChestBoat;
    }

    /**
     * Returns the location of an entity's texture.
     */
    public ResourceLocation getEntityTexture(BoatEntity entity)
    {
        return CHEST_BOAT_TEXTURES[entity.getBoatType().ordinal()];
    }
}
