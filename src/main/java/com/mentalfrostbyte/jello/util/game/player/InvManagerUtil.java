package com.mentalfrostbyte.jello.util.game.player;

import com.mentalfrostbyte.jello.gui.base.JelloPortal;
import com.mentalfrostbyte.jello.util.game.world.blocks.BlockUtil;
import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import net.minecraft.block.*;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.EquipmentSlotType;
import net.minecraft.inventory.container.ClickType;
import net.minecraft.inventory.container.Slot;
import net.minecraft.item.*;
import net.minecraft.network.play.client.CClickWindowPacket;
import net.minecraft.potion.EffectInstance;
import net.minecraft.potion.Effects;
import net.minecraft.potion.PotionUtils;

import java.util.*;

import static com.mentalfrostbyte.jello.module.Module.mc;

public class InvManagerUtil {
    public static boolean isSlotEmpty(int slotIndex) {
        ItemStack itemStack = mc.player.container.getSlot(slotIndex).getStack();
        return itemStack == null || itemStack.getItem() instanceof AirItem;
    }

    public static float getItemUseMultiplier(ItemStack itemStack) {
        if (itemStack == null) {
            return -1.0F;
        }

        Item item = itemStack.getItem();

        if (item instanceof SwordItem) {
            return 2.0F;
        }
        if (item instanceof BucketItem || item instanceof CompassItem || item instanceof ToolItem || item instanceof BowItem) {
            return 1.5F;
        }
        if (item instanceof PotionItem || item instanceof BlockItem) {
            return 1.0F;
        }
        if (itemStack.isFood()) {
            if (item.getFood() == Foods.GOLDEN_APPLE) {
                return 1.0F;
            }
            return 0.5F;
        }
        if (item instanceof EnderPearlItem) {
            return 1.0F;
        }
        if (item instanceof EggItem || item instanceof SnowballItem) {
            return 0.25F;
        }

        return 0.0F;
    }

    public static HashMap<Integer, Float> getItemUseMultipliers() {
        HashMap<Integer, Float> multipliers = new HashMap<>();

        for (int slotIndex = 0; slotIndex < 9; slotIndex++) {
            ItemStack itemStack = mc.player.inventory.getStackInSlot(slotIndex);
            float multiplier = getItemUseMultiplier(itemStack) * (mc.player.inventory.currentItem != slotIndex ? 1 : 2);
            multipliers.put(slotIndex, multiplier);
        }

        return multipliers;
    }

    public static int getSlotWithHighestItemUseMultiplier() {
        HashMap<Integer, Float> itemMultipliers = getItemUseMultipliers();
        TreeMap<Integer, Float> sortedMultipliers = new TreeMap<>(Collections.reverseOrder());
        sortedMultipliers.putAll(itemMultipliers);
        Map.Entry<Integer, Float> bestSlot = null;

        for (Map.Entry<Integer, Float> entry : sortedMultipliers.entrySet()) {
            if (bestSlot == null || bestSlot.getValue() > entry.getValue()) {
                bestSlot = entry;
            }
        }

        return bestSlot.getKey();
    }

    public static int swapToolToHotbar(int var0) {
        int var3 = getSlotWithHighestItemUseMultiplier();
        clickSlot(mc.player.container.windowId, var0, var3, ClickType.SWAP, mc.player);
        return var3;
    }

    public static int findItemInContainer(Item... items) {
        int highestCount = 0;
        int bestSlot = -1;

        for (int slotIndex = 44; slotIndex >= 9; slotIndex--) {
            ItemStack itemStack = mc.player.container.getSlot(slotIndex).getStack();
            if (!isSlotEmpty(slotIndex)) {
                for (Item item : items) {
                    if (itemStack.getItem() == item) {
                        int itemCount = itemStack.getCount();
                        if (itemCount > highestCount) {
                            bestSlot = slotIndex;
                            highestCount = itemCount;
                        }
                    }
                }
            }
        }

        return bestSlot;
    }

    public static void clickSlot(int slot) {
        mc.playerController.windowClick(mc.player.container.windowId, slot, 1, ClickType.THROW, mc.player);
    }

    public static boolean isPotionItem(ItemStack itemStack) {
        return itemStack != null && itemStack.getItem() instanceof PotionItem;
    }

    public static ItemStack getItemInSlot(int slotIndex) {
        return mc.player.container.getSlot(slotIndex).getStack();
    }

    public static int isHotbarEmpty() {
        for (int hotbarSlot = 0; hotbarSlot < 9; hotbarSlot++) {
            ItemStack itemStack = mc.player.inventory.getStackInSlot(hotbarSlot);
            if (itemStack == null) {
                return hotbarSlot;
            }
        }

        return 0;
    }

    public static int findBestToolFromHotbarSlotForBlock(BlockState state) {
        int slot = -1;
        float dmg = 1.0F;

        for (int hotbarSlot = 44; hotbarSlot >= 9; hotbarSlot--) {
            ItemStack item = mc.player.container.getSlot(hotbarSlot).getStack();
            if (item != null) {
                float damage;
                if (state == null) {
                    if (!(item.getItem() instanceof SwordItem)) {
                        continue;
                    }

                    damage = ((SwordItem) item.getItem()).getAttackDamage();
                } else {
                    damage = item.getDestroySpeed(state);
                }

                if (damage > dmg) {
                    slot = hotbarSlot;
                    dmg = damage;
                }
            }
        }

        return slot;
    }

    public static int getSlotWithMaxItemCount(Item... items) {
        int maxCount = 0;
        int maxCountSlot = -1;

        for (int slotIndex = 0; slotIndex < 9; slotIndex++) {
            ItemStack stackInSlot = mc.player.inventory.getStackInSlot(slotIndex);
            if (stackInSlot != null) {
                for (Item item : items) {
                    if (stackInSlot.getItem() == item) {
                        int itemCount = stackInSlot.getCount();
                        if (itemCount > maxCount) {
                            maxCountSlot = slotIndex;
                            maxCount = itemCount;
                        }
                    }
                }
            }
        }

        return maxCountSlot;
    }

    public static int findItemSlot(Item var0) {
        int var3 = 0;
        int var4 = -1;

        for (int var5 = 44; var5 >= 9; var5--) {
            ItemStack var6 = mc.player.container.getSlot(var5).getStack();
            if (!isSlotEmpty(var5) && var6.getItem() == var0) {
                int var7 = var6.getCount();
                if (var7 > var3) {
                    var4 = var5;
                    var3 = var7;
                }
            }
        }

        return var4;
    }

    public static int getArmorProtectionValue(ItemStack itemStack) {
        if (itemStack != null) {
            return itemStack.getItem() instanceof ArmorItem ? ((ArmorItem) itemStack.getItem()).getDamageReduceAmount() + EnchantmentHelper.getEnchantmentLevel(Enchantments.PROTECTION, itemStack) : 0;
        } else {
            return 0;
        }
    }

    public static int getTotalArmorProtection(PlayerEntity player) {
        int totalProtection = 0;

        for (int slot = 5; slot <= 8; slot++) {
            totalProtection += getArmorProtectionValue(player.inventory.getStackInSlot(slot));
        }

        return totalProtection;
    }

    public static boolean hasAllSlotsFilled() {
        for (Slot slot : mc.player.container.inventorySlots) {
            if (!slot.getHasStack() && slot.slotNumber > 8 && slot.slotNumber < 45) {
                return false;
            }
        }

        return true;
    }

    public static boolean isItemStackValid(ItemStack var0) {
        return var0 != null && !var0.getItem().equals(Items.AIR);
    }

    public static List<EffectInstance> getPotionEffects(ItemStack itemStack) {
        if (itemStack == null)
            return null;
        return itemStack.getItem() instanceof PotionItem ? PotionUtils.getEffectsFromStack(itemStack) : null;
    }

    public static boolean hasNegativePotionEffects(ItemStack itemStack) {
        if (itemStack != null && itemStack.getItem() instanceof PotionItem) {
            for (EffectInstance effect : getPotionEffects(itemStack)) {
                if (effect.getPotion() == Effects.POISON
                        || effect.getPotion() == Effects.INSTANT_DAMAGE
                        || effect.getPotion() == Effects.SLOWNESS
                        || effect.getPotion() == Effects.WEAKNESS) {
                    return true;
                }
            }
        }

        return false;
    }

    public static ItemStack clickSlot(int windowId, int slotId, int usedButton, ClickType mode, PlayerEntity entity) {
        return clickSlot(windowId, slotId, usedButton, mode, entity, false);
    }

    public static ItemStack clickSlot(int windowId, int slotId, int usedButton, ClickType mode, PlayerEntity entity, boolean fixed) {
        ItemStack clickedItem = null;
        if (slotId >= 0) {
            clickedItem = entity.openContainer.getSlot(slotId).getStack().copy();
        }

        short actionNumber = entity.openContainer.getNextTransactionID(mc.player.inventory);
        ItemStack item = entity.openContainer.slotClick(slotId, usedButton, mode, entity);
        if (mode == null) {
            return item;
        }
        if (clickedItem == null || JelloPortal.getVersion().newerThan(ProtocolVersion.v1_12) && !fixed || mode == ClickType.SWAP) {
            clickedItem = item;
        }

        mc.getConnection().sendPacket(new CClickWindowPacket(windowId, slotId, usedButton, mode, clickedItem, actionNumber));
        return item;
    }

    public static void clickSlot(int slot, int mouseButton) {
        mc.playerController.windowClick(mc.player.container.windowId, slot, mouseButton, ClickType.SWAP, mc.player);
    }

    public static boolean isBestArmorPiece(ItemStack stack) {
        if (stack != null && stack.getItem() instanceof ArmorItem) {
            float prot = (float) getArmorProtectionValue(stack);

            for (int var4 = 5; var4 < 45; var4++) {
                if (mc.player.container.getSlot(var4).getHasStack()) {
                    ItemStack st = mc.player.container.getSlot(var4).getStack();
                    Item i = st.getItem();
                    if (i instanceof ArmorItem it) {
                        float armorProtection = (float) getArmorProtectionValue(st);
                        if (armorProtection > prot && it.getEquipmentSlot() == ((ArmorItem) stack.getItem()).getEquipmentSlot()) {
                            return false;
                        }
                    }
                }
            }

            return true;
        } else {
            return false;
        }
    }

    public static boolean isArmor(int slot) {
        return isArmorStrongerThanSlot(mc.player.container.getSlot(slot).getStack());
    }

    public static boolean isHead(ArmorItem armorPiece) {
        return armorPiece.getEquipmentSlot() == EquipmentSlotType.HEAD;
    }

    public static boolean isChestplate(ArmorItem armorPiece) {
        return armorPiece.getEquipmentSlot() == EquipmentSlotType.CHEST;
    }

    public static boolean isLeggings(ArmorItem armorPiece) {
        return armorPiece.getEquipmentSlot() == EquipmentSlotType.LEGS;
    }

    public static boolean isBoots(ArmorItem armorPiece) {
        return armorPiece.getEquipmentSlot() == EquipmentSlotType.FEET;
    }

    public static int getDamageReduceFromSlot(int slot) {
        ItemStack stack = mc.player.container.getSlot(slot).getStack();

        if (stack.getItem() instanceof ArmorItem item) {
            Enchantment en = Objects.requireNonNull(Enchantment.getEnchantmentByID(0));
            return item.getDamageReduceAmount() + EnchantmentHelper.getEnchantmentLevel(en, stack);
        } else {
            return 0;
        }
    }

    public static boolean isArmorStrongerThanSlot(ItemStack itemStack) {
        if (itemStack.getItem() instanceof ArmorItem armorItem) {
			int protectionValue = getArmorProtectionValue(itemStack);

            if (!isHead(armorItem)) {
                if (!isChestplate(armorItem)) {
                    if (!isLeggings(armorItem)) {
                        return isBoots(armorItem) && protectionValue > getDamageReduceFromSlot(8);
                    } else {
                        return protectionValue > getDamageReduceFromSlot(7);
                    }
                } else {
                    return protectionValue > getDamageReduceFromSlot(6);
                }
            } else {
                return protectionValue > getDamageReduceFromSlot(5);
            }
        } else {
            return false;
        }
    }

    public static void clickSlot(int slotId, int mouseButton, boolean type) {
        mc.playerController.windowClick(mc.player.container.windowId, slotId, mouseButton, !type ? ClickType.THROW : ClickType.QUICK_MOVE, mc.player);
    }

    public static boolean shouldPlaceItem(Item item) {
        if (!(item instanceof BlockItem)) {
            return false;
        } else {
            Block var3 = ((BlockItem) item).getBlock();
            return !BlockUtil.blocksToNotPlace.contains(var3)
                    && !(var3 instanceof AbstractButtonBlock)
                    && !(var3 instanceof BushBlock)
                    && !(var3 instanceof TrapDoorBlock)
                    && !(var3 instanceof AbstractPressurePlateBlock)
                    && !(var3 instanceof SandBlock)
                    && !(var3 instanceof OreBlock)
                    && !(var3 instanceof SkullBlock)
                    && !(var3 instanceof BedBlock)
                    && !(var3 instanceof BannerBlock)
                    && !(var3 instanceof ChestBlock)
                    && !(var3 instanceof DoorBlock);
        }
    }
}
