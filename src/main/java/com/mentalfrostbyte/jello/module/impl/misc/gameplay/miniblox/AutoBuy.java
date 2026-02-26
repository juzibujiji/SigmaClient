package com.mentalfrostbyte.jello.module.impl.misc.gameplay.miniblox;

import com.mentalfrostbyte.Client;
import com.mentalfrostbyte.jello.event.impl.game.world.EventLoadWorld;
import com.mentalfrostbyte.jello.event.impl.player.LivingDeathEvent;
import com.mentalfrostbyte.jello.event.impl.player.movement.EventMotion;
import com.mentalfrostbyte.jello.managers.util.notifs.Notification;
import com.mentalfrostbyte.jello.module.settings.impl.BooleanSetting;
import net.minecraft.block.Blocks;
import net.minecraft.item.*;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.registry.Registry;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

import static com.mentalfrostbyte.jello.util.game.MinecraftUtil.mc;

// I'm not adding another like 500 lines to the Miniblox gameplay module lol
// TODO: fix this
public class AutoBuy {

    public static boolean wasBuying;
    // SWORDS
    public static final BooleanSetting sword = new BooleanSetting("Sword", "Buy & Upgrade swords?", true);
    public static final BooleanSetting stoneSword = new BooleanSetting("Stone Sword", "Stone", false);
    public static final BooleanSetting ironSword = new BooleanSetting("Iron Sword", "Iron", true);
    public static final BooleanSetting diamondSword = new BooleanSetting("Diamond Sword", "Diamond", true);
    // ARMOR
    public static final BooleanSetting armor = new BooleanSetting("Armor", "Buy & Upgrade armor?", true);
    public static final BooleanSetting chainmailArmor = new BooleanSetting("Chainmail Armor", "", false);
    public static final BooleanSetting ironArmor = new BooleanSetting("Iron Armor", "", true);
    public static final BooleanSetting diamondArmor = new BooleanSetting("Diamond Armor", "", true);
    // UPGRADES
    public static final BooleanSetting doUpgrades = new BooleanSetting("Upgrades", "Buy upgrades?", true);

    public static final BooleanSetting sharpnessUpgrade = new BooleanSetting("Sharpness", "", true);
    public static final BooleanSetting protectionUpgrade = new BooleanSetting("Protection", "", true);
    public static final BooleanSetting hasteUpgrade = new BooleanSetting("Haste", "", true);
    public static final BooleanSetting healPoolUpgrade = new BooleanSetting("Heal Pool", "", true);
    public static final BooleanSetting forgeUpgrade = new BooleanSetting("Forge", "", true);
    public static final List<ShopItemMeta> allSwordItems = new ArrayList<>();
    public static final List<ShopItemMeta> allArmorItems = new ArrayList<>();
    public static final List<ShopItemMeta> swordItems = new ArrayList<>();
    public static final List<ShopItemMeta> armorItems = new ArrayList<>();
    public static final List<ShopUpgradeMeta> allUpgrades = new ArrayList<>();
    public static final List<ShopUpgradeMeta> upgrades = new ArrayList<>();

    static {
        //#region Items
        //#region Swords
        allSwordItems.add(new ShopItemMeta(Items.STONE_SWORD, 10, Items.IRON_INGOT, stoneSword));
        allSwordItems.add(new ShopItemMeta(Items.IRON_SWORD, 7, Items.GOLD_INGOT, ironSword));
        allSwordItems.add(new ShopItemMeta(Items.DIAMOND_SWORD, 4, Items.EMERALD, diamondSword));
        //#endregion
        //#region Armor
        allArmorItems.add(new ShopItemMeta(Items.CHAINMAIL_CHESTPLATE, 24, Items.IRON_INGOT, chainmailArmor));
        allArmorItems.add(new ShopItemMeta(Items.IRON_CHESTPLATE, 12, Items.GOLD_INGOT, ironArmor));
        allArmorItems.add(new ShopItemMeta(Items.DIAMOND_CHESTPLATE, 6, Items.EMERALD, diamondArmor));
        //#endregion
        //#region Sorting
        allSwordItems.sort(ShopItemMeta::compareTo);
        allArmorItems.sort(ShopItemMeta::compareTo);
        swordItems.addAll(allSwordItems);
        armorItems.addAll(allArmorItems);
        //#endregion
        //#region Upgrades
        int[] _4816 = new int[]{4, 8, 16};
        allUpgrades.add(new ShopUpgradeMeta("sharpness", _4816, sharpnessUpgrade));
        allUpgrades.add(new ShopUpgradeMeta("protection", _4816, protectionUpgrade));
        allUpgrades.add(new ShopUpgradeMeta("haste", new int[]{2, 4}, hasteUpgrade));
        allUpgrades.add(new ShopUpgradeMeta("healPool", 1, healPoolUpgrade));
        allUpgrades.add(new ShopUpgradeMeta("forge", new int[]{2, 4, 6, 8}, forgeUpgrade));
        //#endregion
        upgrades.addAll(allUpgrades);
    }

    static void runCommand(String command) {
        assert mc.player != null;
        mc.player.sendChatMessage("/" + command);
    }

    static void handleShopItems(List<ShopItemMeta> items) {
        for (ShopItemMeta shopItemMeta : items) {
            if (shopItemMeta.shouldBuy() && !wasBuying) {
                ResourceLocation resourceLocation = Registry.ITEM.getKey(shopItemMeta.item);
                String itemName = resourceLocation.getPath();
                wasBuying = true;
                Client.getInstance().notificationManager.send(new Notification("AutoBuy", "Buying " + itemName, (int)5e3));
                runCommand("buy " + itemName);
                items.remove(shopItemMeta); // we bought it, remove it from the list so we don't buy it again
            }
            wasBuying = false;
        }
    }

    static void resetState(boolean resetUpgrades) {
        wasBuying = false;
//        wasUpgrading = false;
        swordItems.clear();
        armorItems.clear();
        if (resetUpgrades)
            upgrades.clear();
        swordItems.addAll(allSwordItems);
        armorItems.addAll(allArmorItems);
        if (resetUpgrades)
            upgrades.addAll(allUpgrades);
    }


    public static void onWorldEvent(EventLoadWorld __) {
        resetState(true);
    }

    public static void onLivingDeathEvent(LivingDeathEvent event) {
        if (event.getEntity() == mc.player) {
            resetState(false);
        }
    }

    public static void onUpdateEvent(EventMotion __) {
        if (mc.player == null || mc.world == null) return;

        if (sword.currentValue) {
            handleShopItems(swordItems);
        }

        if (armor.currentValue) {
            handleShopItems(armorItems);
        }
        if (doUpgrades.currentValue) {
            for (ShopUpgradeMeta upgrade : upgrades) {
                if (upgrade.shouldBuy()) {
                    Client.getInstance().notificationManager.send(new Notification(
                                    "AutoBuy",
                                    "Upgrading " + upgrade.item)
                    );
                    runCommand("upgrade " + upgrade.item);
                    if (!upgrade.nextCost()) {  // TODO: verify if we actually got the item and it didn't error.
                        System.out.println("The upgrade " + upgrade.item + " has been maxed out.");
                        upgrades.remove(upgrade);
                        break;
                    }
                }
            }
        }
    }

    public static boolean hasItem(Item item) {
        for (ItemStack stack : mc.player.inventory.mainInventory) {
            if (stack != null && stack.getItem() == item) {
                return true;
            }
        }
        return false;
    }

    public static int countItem(Item item) {
        int count = 0;
        assert mc.player != null;
        for (ItemStack stack : mc.player.inventory.mainInventory) {
            if (stack != null && stack.getItem() == item) {
                count += stack.getMaxStackSize();
            }
        }
        return count;
    }

    public abstract static class PaidItem<T> {
        T item;
        int cost = 0;
        Item currency = null;
        /**
         * Set this to true if the item / downgraded will be removed from the inventory after death
         */
//        boolean removedAfterDeath = true;
        @Nullable
        BooleanSetting relatedSetting;
        public boolean canBuy() {
            return countItem(currency) >= cost;
        }
        public abstract boolean shouldBuy();
    }

    public static class ShopItemMeta extends PaidItem<Item> implements Comparable<ShopItemMeta> {
        ShopItemMeta(Item item, int cost, Item currency) {
            this.item = item;
            this.cost = cost;
            this.currency = currency;
        }
        ShopItemMeta(Item item, int cost, Item currency, BooleanSetting relatedSetting) {
            this.item = item;
            this.cost = cost;
            this.currency = currency;
            this.relatedSetting = relatedSetting;
        }

        public float getDefaultToolScore(ToolItem tool) {
            String name = tool.getTranslationKey().toLowerCase();
            float score;
            ItemStack stack = new ItemStack(tool, 1);
            if (tool instanceof PickaxeItem) {
                score = tool.getDestroySpeed(stack, Blocks.STONE.getDefaultState()) - (name.contains("gold") ? 5 : 0);
            } else if (tool instanceof ShovelItem) {
                score = tool.getDestroySpeed(stack, Blocks.DIRT.getDefaultState()) - (name.contains("gold") ? 5 : 0);
            } else {
                if (!(tool instanceof AxeItem)) return 1;
                score = tool.getDestroySpeed(stack, Blocks.OAK_LOG.getDefaultState()) - (name.contains("gold") ? 5 : 0);
            }
            return score;
        }

        public boolean shouldBuy() {
            return canBuy() && !AutoBuy.hasItem(item) && (relatedSetting != null && relatedSetting.currentValue);
        }

        /**
         * Compares the item tier and returns -1 if the item is less or 1 if the item is greater
         */
        @Override
        public int compareTo(ShopItemMeta o) {
            if (o.item instanceof ArmorItem armor2 && this.item instanceof ArmorItem armor1) {
				float reduce1 = armor1.getDamageReduceAmount();
                float reduce2 = armor2.getDamageReduceAmount();
                if (reduce1 == reduce2)
                    return 0;
                return reduce1 < reduce2 ? -1 : 1;
            }
            if (o.item instanceof SwordItem sword2 && this.item instanceof SwordItem sword1) {
				float damage1 = sword1.getAttackDamage();
                float damage2 = sword2.getAttackDamage();
                if (damage1 == damage2)
                    return 0;
                return damage1 < damage2 ? -1 : 1;
            }
            if (o.item instanceof ToolItem tool2 && this.item instanceof ToolItem tool1) {
				float tool1Score = getDefaultToolScore(tool1);
                float tool2Score = getDefaultToolScore(tool2);
                if (tool1Score == tool2Score)
                    return 0;
                return tool1Score < tool2Score ? -1 : 1;
            }
            throw new ClassCastException("Can't compare " + this.item.getClass() + " and " + o.item.getClass());
        }
    }
    public static class ShopUpgradeMeta extends PaidItem<String> {
        int[] costs;
        int currentCostIndex = 0;
        ShopUpgradeMeta(String item, int cost, Item currency) {
            this(item, new int[]{cost}, currency);
        }
        ShopUpgradeMeta(String item, int[] costs, Item currency) {
            this.item = item;
            this.costs = costs;
            this.cost = costs[0];
            this.currency = currency;
//            this.removedAfterDeath = false;
        }
        ShopUpgradeMeta(String item, int[] costs) {
            this(item, costs, Items.DIAMOND);
        }
        ShopUpgradeMeta(String item, int cost, BooleanSetting relatedSetting) {
            this(item, cost, Items.DIAMOND);
            this.relatedSetting = relatedSetting;
        }
        ShopUpgradeMeta(String item, int[] costs, BooleanSetting relatedSetting) {
            this(item, costs);
            this.relatedSetting = relatedSetting;
        }
        boolean nextCost() {
            currentCostIndex = currentCostIndex + 1;
            if (currentCostIndex < this.costs.length - 1 || currentCostIndex >= this.costs.length - 1) {
                return false;
//                throw new ArrayIndexOutOfBoundsException("Cost index out of bounds");
            }
            this.cost = this.costs[currentCostIndex];
            return true;
        }

        @Override
        public boolean shouldBuy() {
            return canBuy() && (relatedSetting != null && relatedSetting.currentValue);
        }
    }
}
