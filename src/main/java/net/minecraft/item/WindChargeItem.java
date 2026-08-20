package net.minecraft.item;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.WindChargeEntity;
import net.minecraft.stats.Stats;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.SoundEvent;
import net.minecraft.util.SoundEvents;
import net.minecraft.world.World;

/**
 * Backport of the 1.20.5 wind charge item.
 *
 * Official source: net/minecraft/world/item/WindChargeItem.java (1.21.11 / MCP-Reborn-release).
 *
 * Values taken verbatim from the official file:
 *   PROJECTILE_SHOOT_POWER = 1.5F
 *   Projectile.spawnProjectileFromRotation(..., 0.0F /* pitch offset *\/, PROJECTILE_SHOOT_POWER, 1.0F /* uncertainty *\/)
 *   throw sound: WIND_CHARGE_THROW, SoundSource.NEUTRAL, volume 0.5F,
 *                pitch 0.4F / (random.nextFloat() * 0.4F + 0.8F)
 *   spawn origin: player.position().x(), player.getEyePosition().y(), player.position().z()
 *   awardStat(ITEM_USED), itemstack.consume(1, player)
 *
 * Note the sound is played on both sides (outside the ServerLevel branch) and the item is consumed
 * unconditionally via ItemStack#consume, which is a no-op for creative players.
 */
public class WindChargeItem extends Item
{
    /** Official WindChargeItem.PROJECTILE_SHOOT_POWER. */
    public static float PROJECTILE_SHOOT_POWER = 1.5F;

    /**
     * 1.16.4 has no "entity.wind_charge.throw" sound (added in 1.20.5) and this project ships no extra sound
     * assets. The snowball throw is the natural stand-in: the official wind charge reuses exactly the same
     * volume (0.5F) and pitch formula (0.4F / (rand * 0.4F + 0.8F)) as the snowball.
     * Official sound: SoundEvents.WIND_CHARGE_THROW.
     */
    private static final SoundEvent WIND_CHARGE_THROW = SoundEvents.ENTITY_SNOWBALL_THROW;

    public WindChargeItem(Item.Properties builder)
    {
        super(builder);
    }

    public ActionResult<ItemStack> onItemRightClick(World worldIn, PlayerEntity playerIn, Hand handIn)
    {
        ItemStack itemstack = playerIn.getHeldItem(handIn);

        if (!worldIn.isRemote)
        {
            // Official spawn position: (position().x, getEyePosition().y, position().z) - note the X/Z come
            // from the feet position while Y comes from eye level.
            WindChargeEntity windchargeentity = new WindChargeEntity(playerIn, worldIn, playerIn.getPosX(), playerIn.getPosYEye(), playerIn.getPosZ());
            // Official spawnProjectileFromRotation(..., 0.0F, 1.5F, 1.0F): zero pitch offset, power 1.5F,
            // uncertainty 1.0F. 1.16.4's func_234612_a_ is the same shootFromRotation helper.
            windchargeentity.func_234612_a_(playerIn, playerIn.rotationPitch, playerIn.rotationYaw, 0.0F, PROJECTILE_SHOOT_POWER, 1.0F);
            worldIn.addEntity(windchargeentity);
        }

        // Official: played unconditionally on both sides, after the projectile spawn.
        worldIn.playSound(null, playerIn.getPosX(), playerIn.getPosY(), playerIn.getPosZ(), WIND_CHARGE_THROW, SoundCategory.NEUTRAL, 0.5F, 0.4F / (random.nextFloat() * 0.4F + 0.8F));
        playerIn.addStat(Stats.ITEM_USED.get(this));

        // Official: itemstack.consume(1, p_328676_) - ItemStack#consume skips the shrink for creative players.
        if (!playerIn.abilities.isCreativeMode)
        {
            itemstack.shrink(1);
        }

        // Official returns InteractionResult.SUCCESS.
        return ActionResult.resultSuccess(itemstack);
    }
}
