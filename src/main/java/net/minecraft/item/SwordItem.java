package net.minecraft.item;

import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;
import com.google.common.collect.ImmutableMultimap.Builder;
import de.florianmichael.viamcp.fixes.compat.InteractionProtocol;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.material.Material;
import net.minecraft.enchantment.IVanishable;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.attributes.Attribute;
import net.minecraft.entity.ai.attributes.AttributeModifier;
import net.minecraft.entity.ai.attributes.Attributes;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.EquipmentSlotType;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

public class SwordItem extends TieredItem implements IVanishable
{
    private final float attackDamage;
    private final Multimap<Attribute, AttributeModifier> attributeModifiers;

    public SwordItem(IItemTier tier, int attackDamageIn, float attackSpeedIn, Item.Properties builderIn)
    {
        super(tier, builderIn);
        this.attackDamage = (float)attackDamageIn + tier.getAttackDamage();
        Builder<Attribute, AttributeModifier> builder = ImmutableMultimap.builder();
        builder.put(Attributes.ATTACK_DAMAGE, new AttributeModifier(ATTACK_DAMAGE_MODIFIER, "Weapon modifier", (double)this.attackDamage, AttributeModifier.Operation.ADDITION));
        builder.put(Attributes.ATTACK_SPEED, new AttributeModifier(ATTACK_SPEED_MODIFIER, "Weapon modifier", (double)attackSpeedIn, AttributeModifier.Operation.ADDITION));
        this.attributeModifiers = builder.build();
    }

    public float getAttackDamage()
    {
        return this.attackDamage;
    }

    /**
     * Swords are continuous-use blocking items through 1.8.  Restoring that
     * native lifecycle lets PlayerController send one main-hand use packet,
     * LivingEntity own the active-hand state, and Minecraft send the matching
     * release packet when the key is released.
     */
    public ActionResult<ItemStack> onItemRightClick(World worldIn, PlayerEntity playerIn, Hand handIn)
    {
        if (InteractionProtocol.atOrOlderThan1_8() && this.isLegacyBlockingSword())
        {
            ItemStack itemstack = playerIn.getHeldItem(handIn);
            playerIn.setActiveHand(handIn);
            return ActionResult.resultConsume(itemstack);
        }

        return super.onItemRightClick(worldIn, playerIn, handIn);
    }

    public UseAction getUseAction(ItemStack stack)
    {
        return InteractionProtocol.atOrOlderThan1_8() && this.isLegacyBlockingSword()
                ? UseAction.BLOCK
                : super.getUseAction(stack);
    }

    public int getUseDuration(ItemStack stack)
    {
        return InteractionProtocol.atOrOlderThan1_8() && this.isLegacyBlockingSword()
                ? 72000
                : super.getUseDuration(stack);
    }

    /**
     * Only these five swords exist in the 1.8 registry.  A 1.16-only sword can
     * appear locally through the creative inventory or a desynchronised stack,
     * but must not be disguised as an unrelated legacy item on the wire.
     */
    public static boolean isLegacyBlockingSword(ItemStack stack)
    {
        return stack != null && !stack.isEmpty() && isLegacyBlockingSword(stack.getItem());
    }

    private boolean isLegacyBlockingSword()
    {
        return isLegacyBlockingSword(this);
    }

    private static boolean isLegacyBlockingSword(Item item)
    {
        return item == Items.WOODEN_SWORD
                || item == Items.STONE_SWORD
                || item == Items.IRON_SWORD
                || item == Items.GOLDEN_SWORD
                || item == Items.DIAMOND_SWORD;
    }

    public boolean canPlayerBreakBlockWhileHolding(BlockState state, World worldIn, BlockPos pos, PlayerEntity player)
    {
        return !player.isCreative();
    }

    public float getDestroySpeed(ItemStack stack, BlockState state)
    {
        if (state.isIn(Blocks.COBWEB))
        {
            return 15.0F;
        }
        else
        {
            Material material = state.getMaterial();
            return material != Material.PLANTS && material != Material.TALL_PLANTS && material != Material.CORAL && !state.isIn(BlockTags.LEAVES) && material != Material.GOURD ? 1.0F : 1.5F;
        }
    }

    /**
     * Current implementations of this method in child classes do not use the entry argument beside ev. They just raise
     * the damage on the stack.
     */
    public boolean hitEntity(ItemStack stack, LivingEntity target, LivingEntity attacker)
    {
        stack.damageItem(1, attacker, (entity) ->
        {
            entity.sendBreakAnimation(EquipmentSlotType.MAINHAND);
        });
        return true;
    }

    /**
     * Called when a Block is destroyed using this Item. Return true to trigger the "Use Item" statistic.
     */
    public boolean onBlockDestroyed(ItemStack stack, World worldIn, BlockState state, BlockPos pos, LivingEntity entityLiving)
    {
        if (state.getBlockHardness(worldIn, pos) != 0.0F)
        {
            stack.damageItem(2, entityLiving, (entity) ->
            {
                entity.sendBreakAnimation(EquipmentSlotType.MAINHAND);
            });
        }

        return true;
    }

    /**
     * Check whether this Item can harvest the given Block
     */
    public boolean canHarvestBlock(BlockState blockIn)
    {
        return blockIn.isIn(Blocks.COBWEB);
    }

    public Multimap<Attribute, AttributeModifier> getAttributeModifiers(EquipmentSlotType equipmentSlot)
    {
        return equipmentSlot == EquipmentSlotType.MAINHAND ? this.attributeModifiers : super.getAttributeModifiers(equipmentSlot);
    }
}
