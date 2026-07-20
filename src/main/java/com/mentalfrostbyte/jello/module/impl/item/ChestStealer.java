package com.mentalfrostbyte.jello.module.impl.item;

import com.mentalfrostbyte.Client;
import com.mentalfrostbyte.jello.event.impl.player.EventUpdate;
import com.mentalfrostbyte.jello.event.impl.game.render.EventRender2DOffset;
import com.mentalfrostbyte.jello.event.impl.game.world.EventLoadWorld;
import com.mentalfrostbyte.jello.managers.RotationManager;
import com.mentalfrostbyte.jello.module.Module;
import com.mentalfrostbyte.jello.module.data.ModuleCategory;
import com.mentalfrostbyte.jello.module.settings.impl.BooleanSetting;
import com.mentalfrostbyte.jello.module.settings.impl.NumberSetting;
import com.mentalfrostbyte.jello.util.game.player.InvManagerUtil;
import com.mentalfrostbyte.jello.util.game.player.rotation.RotationCore;
import com.mentalfrostbyte.jello.util.system.math.counter.TimerUtil;
import com.mentalfrostbyte.jello.util.game.player.combat.RotationUtil;
import com.mentalfrostbyte.jello.util.game.world.blocks.BlockUtil;
import net.minecraft.block.ChestBlock;
import net.minecraft.client.gui.screen.inventory.ChestScreen;
import net.minecraft.inventory.container.ClickType;
import net.minecraft.inventory.container.Slot;
import net.minecraft.item.*;
import net.minecraft.network.play.client.CAnimateHandPacket;
import net.minecraft.network.play.client.CPlayerTryUseItemOnBlockPacket;
import net.minecraft.tileentity.ChestTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.BlockRayTraceResult;
import team.sdhq.eventBus.annotations.EventTarget;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;

public class ChestStealer extends Module {
    public boolean field23621;
    private final ConcurrentHashMap<ChestTileEntity, Boolean> chests;
    private final TimerUtil field23623 = new TimerUtil();
    private final TimerUtil field23624 = new TimerUtil();
    private ChestTileEntity targetChest;

    public ChestStealer() {
        super(ModuleCategory.ITEM, "ChestStealer", "Steals items from chest");
        this.registerSetting(new BooleanSetting("Aura", "Automatically open chests near you.", false));
        this.registerSetting(new BooleanSetting("Through Walls", "Allow the Aura to open chests behind walls.", false));
        this.registerSetting(new BooleanSetting("Ignore Junk", "Ignores useless items.", true));
        this.registerSetting(new BooleanSetting("Fix ViaVersion", "Fixes ViaVersion delay.", true));
        this.registerSetting(new BooleanSetting("Close", "Automatically closes the chest when done", true));
        this.registerSetting(new NumberSetting<>("Delay", "Click delay", 0.2F, 0.0F, 1.0F, 0.01F));
        this.registerSetting(new NumberSetting<>("First Item", "Tick delay before grabbing first item", 0.2F, 0.0F, 1.0F, 0.01F));
        this.chests = new ConcurrentHashMap<>();
    }

    @Override
    public void onEnable() {
        this.targetChest = null;
        this.field23621 = false;
        if (!this.chests.isEmpty()) {
            this.chests.clear();
        }
    }

    @EventTarget
    public void onUpdate(EventUpdate var1) {
        if (this.isEnabled() /*&& var1.isPre()*/) {
            if (this.getBooleanValueFromSettingName("Aura")) {
                if (this.field23624.getElapsedTime() > 2000L && this.field23621) {
                    this.field23624.reset();
                    this.field23621 = false;
                }

                if (!this.field23624.isEnabled()) {
                    this.field23624.start();
                }

                this.method16370();
                if (this.targetChest != null && mc.currentScreen == null && this.field23624.getElapsedTime() > 1000L) {
                    boolean var3 = this.getBooleanValueFromSettingName("Through Walls");
                    BlockRayTraceResult var4 = BlockUtil.rayTraceBlock(RotationCore.lastYaw, RotationCore.lastPitch, 0.0F, this.targetChest.getPos(), var3);
                    if (var4.getType() != net.minecraft.util.math.RayTraceResult.Type.MISS
                            && var4.getPos().getX() == this.targetChest.getPos().getX()
                            && var4.getPos().getY() == this.targetChest.getPos().getY()
                            && var4.getPos().getZ() == this.targetChest.getPos().getZ()) {
                        this.field23621 = true;
                        mc.getConnection().sendPacket(new CPlayerTryUseItemOnBlockPacket(Hand.MAIN_HAND, var4));
                        mc.getConnection().sendPacket(new CAnimateHandPacket(Hand.MAIN_HAND));
                        this.field23624.reset();
                    }
                }

                boolean var14 = false;

                for (Entry var6 : this.chests.entrySet()) {
                    ChestTileEntity var7 = (ChestTileEntity) var6.getKey();
                    boolean var8 = (Boolean) var6.getValue();
                    float var9 = (float) var7.getPos().getX();
                    float var10 = (float) var7.getPos().getY() + 0.1F;
                    float var11 = (float) var7.getPos().getZ();
                    if (!this.field23621
                            && (
                            this.targetChest == null
                                    || mc.player.getDistanceSq(var9, var10, var11)
                                    > mc.player.getDistanceSq(var9, var10, var11)
                    )
                            && !var8
                            && Math.sqrt(mc.player.getDistanceSq(var9, var10, var11)) < 5.0
                            && this.field23624.getElapsedTime() > 1000L
                            && mc.currentScreen == null) {
                        float[] var16 = RotationUtil.rotationToPos((double) var7.getPos().getX() + 0.5,  (double) var7.getPos().getY() + 0.5, (double) var7.getPos().getZ() + 0.5);
                        BlockRayTraceResult var12 = BlockUtil.rayTraceBlock(var16[0], var16[1], 0.0F, var7.getPos(), this.getBooleanValueFromSettingName("Through Walls"));
                        if (var12.getType() != net.minecraft.util.math.RayTraceResult.Type.MISS
                                && var12.getPos().getX() == var7.getPos().getX()
                                && var12.getPos().getY() == var7.getPos().getY()
                                && var12.getPos().getZ() == var7.getPos().getZ()) {
                            this.targetChest = var7;
                            //var1.setYaw(var13[0]);
                            //var1.setPitch(var13[1]);
                            RotationManager.setRotations(var16[0], var16[1]);
                            var14 = true;
                        }
                    }
                }

                if (!var14 && mc.currentScreen == null && this.targetChest != null) {
                    this.chests.put(this.targetChest, true);
                    this.targetChest = null;
                }
            }
        }
    }

    @EventTarget
    public void method16366(EventLoadWorld var1) {
        if (!this.chests.isEmpty()) {
            this.chests.clear();
        }
    }

    @EventTarget
    public void method16367(EventRender2DOffset var1) {
        if (this.isEnabled()) {
            if (!(mc.currentScreen instanceof ChestScreen)) {
                this.field23621 = false;
                this.field23623.stop();
                this.field23623.reset();
                if (mc.currentScreen == null && InvManagerUtil.hasAllSlotsFilled()) {
                    this.field23624.reset();
                }
            } else {
                if (!this.field23623.isEnabled()) {
                    this.field23623.start();
                }

                if (!((float) Client.getInstance().playerTracker.getMode() < this.getNumberValueBySettingName("Delay") * 20.0F)) {
                    if (InvManagerUtil.hasAllSlotsFilled()) {
                        if (this.getBooleanValueFromSettingName("Close")) {
                            mc.player.closeScreen();
                        }
                    } else {
                        ChestScreen chestScreen = (ChestScreen) mc.currentScreen;
                        if (!this.shouldSteal(chestScreen)) {
                            if (this.targetChest != null) {
                                this.chests.put(this.targetChest, true);
                            }
                        } else {
                            boolean var5 = true;

                            for (Slot slot : chestScreen.getContainer().inventorySlots) {
                                if (slot.getHasStack() && slot.slotNumber < chestScreen.getContainer().getNumRows() * 9) {
                                    ItemStack var8 = slot.getStack();
                                    if (!this.method16369(var8)) {
                                        if (!this.field23621) {
                                            if ((float) this.field23623.getElapsedTime() < this.getNumberValueBySettingName("First Item") * 1000.0F) {
                                                return;
                                            }

                                            this.field23621 = !this.field23621;
                                        }

                                        if (!this.getBooleanValueFromSettingName("Fix ViaVersion")) {
                                            InvManagerUtil.clickSlot(chestScreen.getContainer().windowId, slot.slotNumber, 0, ClickType.QUICK_MOVE, mc.player);
                                        } else {
                                            InvManagerUtil.clickSlot(chestScreen.getContainer().windowId, slot.slotNumber, 0, ClickType.QUICK_MOVE, mc.player, true);
                                        }

                                        this.field23623.reset();
                                        var5 = false;
                                        if (this.getNumberValueBySettingName("Delay") > 0.0F) {
                                            break;
                                        }
                                    }
                                }
                            }

                            if (var5) {
                                if (this.field23621) {
                                    this.field23621 = !this.field23621;
                                }

                                if (this.getBooleanValueFromSettingName("Close")) {
                                    mc.player.closeScreen();
                                }

                                for (ChestTileEntity chest : this.chests.keySet()) {
                                    if (chest == this.targetChest) {
                                        this.targetChest = null;
                                        this.chests.put(chest, true);
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private boolean shouldSteal(ChestScreen chest) {
        List<BlockPos> positions = BlockUtil.getBlockPositionsInRange(8.0F);

        for (BlockPos pos : positions) {
            if (BlockUtil.getBlockFromPosition(pos) instanceof ChestBlock) {
                return true;
            }
        }
        return false;
    }

    private boolean method16369(ItemStack itemStack) {
        Item item = itemStack.getItem();
        // 如果没有启用"忽略垃圾物品"选项，则拿取所有物品
        if (!this.getBooleanValueFromSettingName("Ignore Junk")) {
            return false;
        }

        // 检查是否为武器
        if (item instanceof SwordItem) {
            // 如果当前没有更好的武器，则拿取
            return !InvManager.method16431(itemStack);
        }

        // 检查是否为工具
        if (item instanceof PickaxeItem) {
            // 如果这是最好的镐子，则拿取
            return !InvManager.method16442(itemStack);
        }

        if (item instanceof AxeItem) {
            // 如果这是最好的斧头，则拿取
            return !InvManager.method16444(itemStack);
        }

        if (item instanceof HoeItem) {
            // 如果这是最好的锄头，则拿取
            return !InvManager.isHoe(itemStack);
        }

        // 检查是否为药水
        if (item instanceof PotionItem) {
            // 如果药水有负面效果，则忽略
            if (InvManagerUtil.hasNegativePotionEffects(itemStack)) {
                return true;
            }
            // 否则拿取有用的药水
            return false;
        }

        // 检查是否为方块
        if (item instanceof BlockItem) {
            // 如果是有用的建筑方块，则拿取
            return !InvManagerUtil.shouldPlaceItem(item);
        }

        // 检查是否为弓箭
        if (item instanceof ArrowItem || (item instanceof BowItem && Client.getInstance().moduleManager.getModuleByClass(InvManager.class).getBooleanValueFromSettingName("Archery"))) {
            // 如果启用了弓箭选项，则拿取
            return false;
        }

        // 检查是否为水桶（用于AutoMLG）
        if (item == Items.WATER_BUCKET && Client.getInstance().moduleManager.getModuleByClass(AutoMLG.class).isEnabled()) {
            return false;
        }

        // 检查是否为护甲
        if (item instanceof ArmorItem) {
            // 如果这是最好的护甲，则拿取
            return !InvManagerUtil.isBestArmorPiece(itemStack);
        }

        // 检查是否为食物
        if (itemStack.isFood()) {
            // 如果是金苹果，则拿取
            if (item.getFood() == Foods.GOLDEN_APPLE || item == Items.ENCHANTED_GOLDEN_APPLE) {
                return false;
            }
            // 如果启用了食物清理选项，则忽略普通食物
            if (Client.getInstance().moduleManager.getModuleByClass(InvManager.class).getBooleanValueFromSettingName("Food")) {
                return true;
            }
            // 否则拿取食物
            return false;
        }

        // 检查是否为盾牌
        if (item instanceof ShieldItem && Client.getInstance().moduleManager.getModuleByClass(InvManager.class).getBooleanValueFromSettingName("Auto Shield")) {
            return false;
        }

        // 检查是否为末影珍珠
        if (item instanceof EnderPearlItem) {
            return false;
        }

        // 检查是否为火药
        if (item == Items.GUNPOWDER) {
            return false;
        }

        // 检查是否为粘液球
        if (item == Items.SLIME_BALL) {
            return false;
        }

        // 检查是否为TNT
        if (item == Items.TNT) {
            return false;
        }

        // 检查是否为末影箱
        if (item == Items.ENDER_CHEST) {
            return false;
        }

        // 以下物品被视为垃圾物品，将被忽略
        ArrayList<Item> junkItems = new ArrayList<>(
                Arrays.asList(
                        Items.COMPASS,
                        Items.FEATHER,
                        Items.FLINT,
                        Items.EGG,
                        Items.STRING,
                        Items.STICK,
                        Items.BUCKET,
                        Items.LAVA_BUCKET,
                        Items.WATER_BUCKET,
                        Items.SNOW,
                        Items.ENCHANTED_BOOK,
                        Items.EXPERIENCE_BOTTLE,
                        Items.SHEARS,
                        Items.ANVIL,
                        Items.TORCH,
                        Items.BEETROOT_SEEDS,
                        Items.MELON_SEEDS,
                        Items.PUMPKIN_SEEDS,
                        Items.WHEAT_SEEDS,
                        Items.LEATHER,
                        Items.GLASS_BOTTLE,
                        Items.PISTON,
                        Items.SNOWBALL,
                        Items.FISHING_ROD
                )
        );

        // 如果物品名称包含"seed"，则视为垃圾物品
        return junkItems.contains(item) || item.getName().getString().toLowerCase().contains("seed");
    }

    private void method16370() {
        List<TileEntity> var3 = mc.world.loadedTileEntityList;
        var3.removeIf(var0 -> !(var0 instanceof ChestTileEntity));

        for (TileEntity var5 : var3) {
            if (!this.chests.containsKey((ChestTileEntity) var5)) {
                this.chests.put((ChestTileEntity) var5, false);
            }
        }

        for (ChestTileEntity var7 : this.chests.keySet()) {
            if (!var3.contains(var7)) {
                this.chests.remove(var7);
            }
        }
    }
}
