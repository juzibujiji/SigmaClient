package com.mentalfrostbyte.jello.module.impl.player;


import com.mentalfrostbyte.Client;
import com.mentalfrostbyte.jello.event.impl.player.EventHandAnimation;
import com.mentalfrostbyte.jello.event.impl.player.movement.EventMotion;
import com.mentalfrostbyte.jello.event.impl.game.network.EventReceivePacket;
import com.mentalfrostbyte.jello.gui.base.JelloPortal;
import com.mentalfrostbyte.jello.managers.ViaManager;
import com.mentalfrostbyte.jello.module.Module;
import com.mentalfrostbyte.jello.module.data.ModuleCategory;
import com.mentalfrostbyte.jello.module.impl.combat.KillAura;
import com.mentalfrostbyte.jello.module.settings.impl.ModeSetting;
import com.mentalfrostbyte.jello.module.settings.impl.NumberSetting;
import com.mentalfrostbyte.jello.util.game.player.combat.CombatUtil;
import com.mojang.blaze3d.matrix.MatrixStack;
import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import net.minecraft.block.*;
import net.minecraft.inventory.EquipmentSlotType;
import net.minecraft.item.Items;
import net.minecraft.item.ShieldItem;
import net.minecraft.item.SwordItem;
import net.minecraft.network.play.server.SEntityEquipmentPacket;
import net.minecraft.util.HandSide;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.BlockRayTraceResult;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.vector.Vector3f;
import team.sdhq.eventBus.annotations.EventTarget;
import team.sdhq.eventBus.annotations.priority.HigherPriority;
import team.sdhq.eventBus.annotations.priority.LowerPriority;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class OldHitting extends Module {
    public static boolean field23408 = false;
    private boolean field23409;

    public OldHitting() {
        super(ModuleCategory.PLAYER, "OldHitting", "Reverts to 1.7/1.8 hitting");
        this.registerSetting(new ModeSetting("Animation", "Animation mode", 0, "Vanilla", "Tap", "Tap2", "Slide", "Slide2", "Scale", "Leaked", "Ninja", "Down", "Tomy"));
        this.registerSetting(new NumberSetting<>("XPos", "Default X position of the main hand", 0, -1, 1, 0.01F));
        this.registerSetting(new NumberSetting<>("YPos", "Default Y position of the main hand", 0, -1, 1, 0.01F));
        this.registerSetting(new NumberSetting<>("ZPos", "Default Z position of the main hand", 0, -1, 1, 0.01F));
        this.setAvailableOnClassic(true);
    }

    @EventTarget
    @HigherPriority
    public void onUpdate(EventMotion event) {
        if (this.isEnabled() || mc.gameSettings.keyBindUseItem.isKeyDown() || JelloPortal.getVersion().equalTo(ProtocolVersion.v1_8)) {
            if (event.isPre()) {
                boolean var4 = mc.player.getHeldItemMainhand() != null && mc.player.getHeldItemMainhand().getItem() instanceof SwordItem;
                boolean var5 = Client.getInstance().moduleManager.getModuleByClass(KillAura.class).isEnabled2();
                boolean var6 = true;
                if (!mc.player.isSneaking()
                        && mc.objectMouseOver.getType() == RayTraceResult.Type.BLOCK
                        && !Client.getInstance().moduleManager.getModuleByClass(KillAura.class).isEnabled2()) {
                    BlockRayTraceResult blockRayTrace = (BlockRayTraceResult) mc.objectMouseOver;
                    BlockPos blockPos = blockRayTrace.getPos();
                    Block block = mc.world.getBlockState(blockPos).getBlock();
                    List<Block> blocks = new ArrayList<>(
                            Arrays.asList(
                                    Blocks.CHEST,
                                    Blocks.ENDER_CHEST,
                                    Blocks.TRAPPED_CHEST,
                                    Blocks.CRAFTING_TABLE,
                                    Blocks.BEACON,
                                    Blocks.FURNACE,
                                    Blocks.BLAST_FURNACE,
                                    Blocks.ENCHANTING_TABLE,
                                    Blocks.ANVIL,
                                    Blocks.CHIPPED_ANVIL,
                                    Blocks.DAMAGED_ANVIL,
                                    Blocks.DISPENSER,
                                    Blocks.NOTE_BLOCK,
                                    Blocks.LEVER,
                                    Blocks.HOPPER,
                                    Blocks.DROPPER,
                                    Blocks.REPEATER,
                                    Blocks.COMPARATOR
                            )
                    );
                    if (blocks.contains(block)
                            || block instanceof WoodButtonBlock
                            || block instanceof StoneButtonBlock
                            || block instanceof FenceGateBlock
                            || block instanceof DoorBlock && block != Blocks.IRON_DOOR) {
                        var6 = false;
                    }
                }

                field23408 = mc.gameSettings.keyBindUseItem.isKeyDown() && var4 && var6 && var6 || var5;
                if (!field23408) {
                    if (ViaManager.entities.contains(mc.player)) {
                        ViaManager.entities.remove(mc.player);
                    }
                } else if (!ViaManager.entities.contains(mc.player)) {
                    ViaManager.entities.add(mc.player);
                }

                if (field23408 && !this.field23409) {
                    this.field23409 = !this.field23409;
                    if (!var5) {
                        CombatUtil.block();
                    }
                } else if (!field23408 && this.field23409) {
                    this.field23409 = !this.field23409;
                }
            }
        }
    }

    @EventTarget
    @LowerPriority
    public void onPacketReceive(EventReceivePacket event) {
        if (this.isEnabled() || mc.gameSettings.keyBindUseItem.isKeyDown() || JelloPortal.getVersion().equalTo(ProtocolVersion.v1_8)) {
            if (mc.player != null) {
                if (event.packet instanceof SEntityEquipmentPacket pack) {
                    pack.func_241790_c_().removeIf(pair -> pack.getEntityID() == mc.player.getEntityId()
                            && pair.getFirst() == EquipmentSlotType.OFFHAND
                            && pair.getSecond() != null
                            && pair.getSecond().getItem() == Items.SHIELD);
                }
            }
        }
    }

    @EventTarget
    @LowerPriority
    public void method16022(EventHandAnimation event) {
        if (this.isEnabled() || mc.gameSettings.keyBindUseItem.isKeyDown() || JelloPortal.getVersion().equalTo(ProtocolVersion.v1_8)) {
            float swingProgress = event.getSwingProgress();
            event.getMatrix().translate(getNumberValueBySettingName("XPos"), getNumberValueBySettingName("YPos"), getNumberValueBySettingName("ZPos"));

            if (event.method13926() && event.getHand() == HandSide.LEFT && event.getItemStack().getItem() instanceof ShieldItem) {
                event.renderBlocking(false);
            } else if (event.getHand() != HandSide.LEFT || !field23408) {
                if (field23408 && event.method13926() && this.isEnabled() && event.getItemStack().getItem() instanceof SwordItem) {
                    event.cancelled = true;
                    switch (this.getStringSettingValueByName("Animation")) {
                        case "Vanilla":
                            this.doVanilla(0.0F, swingProgress, event.getMatrix());
                            break;
                        case "Tap":
                            this.doTap(0.0F, swingProgress, event.getMatrix());
                            break;
                        case "Tap2":
                            this.doTap2(0.0F, swingProgress, event.getMatrix());
                            break;
                        case "Slide":
                            this.doSlide(0.0F, swingProgress, event.getMatrix());
                            break;
                        case "Slide2":
                            this.doSlide2(0.0F, swingProgress, event.getMatrix());
                            break;
                        case "Scale":
                            this.doScale(0.0F, swingProgress, event.getMatrix());
                            break;
                        case "Leaked":
                            this.doLeaked(0.0F, swingProgress, event.getMatrix());
                            break;
                        case "Ninja":
                            this.doNinja(0.0F, swingProgress, event.getMatrix());
                            break;
                        case "Tomy":
                            this.doTomy(0.0F, swingProgress, event.getMatrix());
                            break;
                        case "Down":
                            this.doDown(0.0F, swingProgress, event.getMatrix());
                    }
                }
            }
        }
    }

    private void rotate(float var1, float var2, float var3, float var4, MatrixStack var5) {
        var5.rotate(new Vector3f(var2, var3, var4).rotationDegrees(var1));
    }

    private void doTomy(float var1, float var2, MatrixStack var3) {
        var3.translate(0.48F, -0.55F, -0.71999997F);
        var3.translate(0.0, (double) (var1 * -0.6F), 0.0);
        this.rotate(77.0F, 0.0F, 1.0F, 0.0F, var3);
        this.rotate(-10.0F, 0.0F, 0.0F, 1.0F, var3);
        float var6 = MathHelper.sin(var2 * var2 * (float) Math.PI);
        float var7 = MathHelper.sin(MathHelper.sqrt(var2) * (float) Math.PI);
        this.rotate(var6 * -20.0F, 0.0F, 1.0F, 0.0F, var3);
        this.rotate(var7 * -20.0F, 0.0F, 0.0F, 1.0F, var3);
        this.rotate(var7 * -69.0F, 1.0F, 0.0F, 0.0F, var3);
        this.rotate(-80.0F, 1.0F, 0.0F, 0.0F, var3);
        float var8 = 1.2F;
        var3.scale(var8, var8, var8);
    }

    private void doNinja(float var1, float var2, MatrixStack var3) {
        var3.translate(0.48F, -0.39F, -0.71999997F);
        var3.translate(0.0, (double) (var1 * -0.6F), 0.0);
        this.rotate(100.0F, 0.0F, 1.0F, 0.0F, var3);
        this.rotate(-50.0F, 0.0F, 0.0F, 1.0F, var3);
        float var6 = MathHelper.sin(var2 * (float) Math.PI);
        float var7 = MathHelper.sin(var2 * (float) Math.PI);
        this.rotate(var6 * -10.0F, 0.0F, 1.0F, 0.0F, var3);
        this.rotate(var7 * -30.0F, 0.0F, 0.0F, 1.0F, var3);
        this.rotate(var7 * 109.0F, 1.0F, 0.0F, 0.0F, var3);
        this.rotate(-90.0F, 1.0F, 0.0F, 0.0F, var3);
        float var8 = 1.2F;
        var3.scale(var8, var8, var8);
    }

    private void doVanilla(float var1, float var2, MatrixStack var3) {
        var3.translate(0.48F, -0.55F, -0.71999997F);
        var3.translate(0.0, (double) (var1 * -0.6F), 0.0);
        this.rotate(77.0F, 0.0F, 1.0F, 0.0F, var3);
        this.rotate(-10.0F, 0.0F, 0.0F, 1.0F, var3);
        float var6 = MathHelper.sin(var2 * var2 * (float) Math.PI);
        float var7 = MathHelper.sin(MathHelper.sqrt(var2) * (float) Math.PI);
        this.rotate(var6 * -20.0F, 0.0F, 1.0F, 0.0F, var3);
        this.rotate(var7 * -20.0F, 0.0F, 0.0F, 1.0F, var3);
        this.rotate(var7 * -69.0F, 1.0F, 0.0F, 0.0F, var3);
        this.rotate(-80.0F, 1.0F, 0.0F, 0.0F, var3);
        float var8 = 1.2F;
        var3.scale(var8, var8, var8);
    }

    private void doTap(float var1, float var2, MatrixStack var3) {
        var3.translate(0.0, -3.5, 0.0);
        var3.translate(0.56F, -0.52F, -0.72F);
        var3.translate(0.56F, -0.22F, -0.71999997F);
        this.rotate(45.0F, 0.0F, 1.0F, 0.0F, var3);
        float var6 = MathHelper.sin(MathHelper.sqrt(var2) * (float) Math.PI);
        this.rotate(0.0F, 0.0F, 0.0F, 1.0F, var3);
        this.rotate(var6 * -9.0F, 1.0F, 0.0F, 0.0F, var3);
        this.rotate(-9.0F, 0.0F, 0.0F, 1.0F, var3);
        var3.translate(0.0, 3.2F, 0.0);
        this.rotate(-80.0F, 1.0F, 0.0F, 0.0F, var3);
        var3.scale(2.7F, 2.7F, 2.7F);
    }

    private void doTap2(float var1, float var2, MatrixStack var3) {
        var3.translate(0.648F, -0.55F, -0.71999997F);
        var3.translate(0.0, (double) (var1 * -0.6F), 0.0);
        this.rotate(77.0F, 0.0F, 1.0F, 0.0F, var3);
        this.rotate(-10.0F, 0.0F, 0.0F, 1.0F, var3);
        float var6 = MathHelper.sin(MathHelper.sqrt(var2) * (float) Math.PI);
        this.rotate(-80.0F, 1.0F, 0.0F, 0.0F, var3);
        this.rotate(-var6 * 10.0F, 1.0F, -2.0F, 3.0F, var3);
        float var7 = 1.2F;
        var3.scale(var7, var7, var7);
    }

    private void doSlide(float var1, float var2, MatrixStack var3) {
        var3.translate(0.648F, -0.55F, -0.71999997F);
        var3.translate(0.0, (double) (var1 * -0.6F), 0.0);
        this.rotate(77.0F, 0.0F, 1.0F, 0.0F, var3);
        this.rotate(-10.0F, 0.0F, 0.0F, 1.0F, var3);
        float var6 = MathHelper.sin(MathHelper.sqrt(var2) * (float) Math.PI);
        this.rotate(-80.0F, 1.0F, 0.0F, 0.0F, var3);
        this.rotate(-var6 * 20.0F, 1.0F, 0.0F, 0.0F, var3);
        float var7 = 1.2F;
        var3.scale(var7, var7, var7);
    }

    private void doSlide2(float var1, float var2, MatrixStack var3) {
        var3.translate(0.48F, -0.55F, -0.71999997F);
        var3.translate(0.0, (double) (var1 * -0.6F), 0.0);
        this.rotate(77.0F, 0.0F, 1.0F, 0.0F, var3);
        this.rotate(-10.0F, 0.0F, 0.0F, 1.0F, var3);
        float var6 = MathHelper.sin(var2 * var2 * (float) Math.PI);
        float var7 = MathHelper.sin(MathHelper.sqrt(var2) * (float) Math.PI);
        this.rotate(var6 * -20.0F, 0.0F, 1.0F, 0.0F, var3);
        this.rotate(var7 * -20.0F, 0.0F, 0.0F, 1.0F, var3);
        this.rotate(var7 * -69.0F, 1.0F, 0.0F, 0.0F, var3);
        this.rotate(-80.0F, 1.0F, 0.0F, 0.0F, var3);
        float var8 = 1.2F;
        var3.scale(var8, var8, var8);
    }

    private void doScale(float var1, float var2, MatrixStack var3) {
        var3.translate(0.48F, -0.55F, -0.71999997F);
        var3.translate(0.0, (double) (var1 * -0.2F), 0.0);
        this.rotate(77.0F, 0.0F, 1.0F, 0.0F, var3);
        this.rotate(-10.0F, 0.0F, 0.0F, 1.0F, var3);
        float var6 = MathHelper.sin(MathHelper.sqrt(var2) * (float) Math.PI);
        this.rotate(-80.0F, 1.0F, 0.0F, 0.0F, var3);
        float var7 = 1.2F - var6 * 0.3F;
        var3.scale(var7, var7, var7);
    }

    private void doLeaked(float var1, float var2, MatrixStack var3) {
        var3.translate(0.56, -0.52, -0.72);
        float var6 = MathHelper.sin(MathHelper.sqrt(var2) * (float) Math.PI);
        this.rotate(77.0F, 0.0F, 1.0F, 0.0F, var3);
        this.rotate(-10.0F, 0.0F, 0.0F, 1.0F, var3);
        this.rotate(-80.0F, 1.0F, 0.0F, 0.0F, var3);
        this.rotate(var6 * 10.0F, -4.0F, -2.0F, 5.0F, var3);
        this.rotate(var6 * 30.0F, 1.0F, -0.0F, -1.0F, var3);
    }

    private void doDown(float var1, float var2, MatrixStack var3) {
        float var6 = MathHelper.sin(MathHelper.sqrt(var2) * (float) Math.PI);
        var3.translate(0.48F, -0.55F, -0.71999997F);
        var3.translate(0.0, (double) (var6 * -0.2F), 0.0);
        this.rotate(77.0F, 0.0F, 1.0F, 0.0F, var3);
        this.rotate(-10.0F, 0.0F, 0.0F, 1.0F, var3);
        this.rotate(-80.0F, 1.0F, 0.0F, 0.0F, var3);
        float var7 = 1.2F;
        var3.scale(var7, var7, var7);
    }
}
