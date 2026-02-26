package com.mentalfrostbyte.jello.module.impl.movement.fly;

import com.mentalfrostbyte.jello.event.impl.player.movement.EventMotion;
import com.mentalfrostbyte.jello.util.game.player.MovementUtil;
import com.mentalfrostbyte.jello.util.game.world.blocks.BlockUtil;
import team.sdhq.eventBus.annotations.EventTarget;
import com.mentalfrostbyte.jello.event.impl.game.action.EventMouseHover;
import com.mentalfrostbyte.jello.event.impl.game.action.EventKeyPress;
import com.mentalfrostbyte.jello.event.impl.player.movement.EventMove;
import com.mentalfrostbyte.jello.module.Module;
import com.mentalfrostbyte.jello.module.data.ModuleCategory;
import com.mentalfrostbyte.jello.module.settings.impl.BooleanSetting;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.network.play.client.CHeldItemChangePacket;
import net.minecraft.network.play.client.CPlayerTryUseItemOnBlockPacket;
import net.minecraft.util.Direction;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockRayTraceResult;

import java.util.Objects;

public class SpartanFly extends Module {
    private double minY;
    private boolean spoofGround;
    private boolean down;

    public SpartanFly() {
        super(ModuleCategory.MOVEMENT, "Spartan", "A fly for Spartan anticheat");
        this.registerSetting(new BooleanSetting("Ground Spoof", "Send on ground packets", true));
        this.registerSetting(new BooleanSetting("Fake Block", "Send on fake blockplacing packet", true));
    }

    @Override
    public void onEnable() {
        this.spoofGround = false;
        this.minY = -10.0;
        if (!mc.gameSettings.keyBindSneak.isKeyDown()) {
            this.down = false;
        } else {
            mc.gameSettings.keyBindSneak.setPressed(false);
            this.down = true;
        }
    }

    @EventTarget
    public void onKeyPress(EventKeyPress var1) {
        if (this.isEnabled()) {
            if (var1.getKey() == mc.gameSettings.keyBindSneak.keyCode.getKeyCode()) {
                var1.cancelled = true;
                this.down = true;
            }
        }
    }

    @EventTarget
    public void onMouseHover(EventMouseHover var1) {
        if (this.isEnabled()) {
            if (var1.getMouseButton() == mc.gameSettings.keyBindSneak.keyCode.getKeyCode()) {
                var1.cancelled = true;
                this.down = false;
            }
        }
    }

    @EventTarget
    public void onUpdate(EventMotion e) {
        if (this.isEnabled() && e.isPre() && this.getBooleanValueFromSettingName("Ground Spoof")) {
            if (this.spoofGround) {
                this.spoofGround = !this.spoofGround;
                e.setOnGround(true);
            }
        }
    }

    @EventTarget
    public void onMove(EventMove e) {
        if (this.isEnabled()) {
			assert mc.player != null;
			boolean doSpoofing = mc.player.isOnGround() || BlockUtil.isAboveBounds(mc.player, 0.001F);
            if (!doSpoofing) {
                if (e.getY() < 0.0) {
                    if (this.minY != mc.player.getPosY()) {
                        if (mc.player.getPositionVec().y + e.getY() < this.minY) {
                            this.spoofGround = true;
                            int var5 = this.getBlockSlot();
                            boolean fakeBlock = this.getBooleanValueFromSettingName("Fake Block");
                            if (var5 >= 0 && fakeBlock) {
                                mc.getConnection().sendPacket(new CHeldItemChangePacket(var5));
                            }

                            if (fakeBlock && (var5 >= 0
                                    || mc.player.getHeldItem(Hand.MAIN_HAND).getItem() instanceof BlockItem)) {
                                BlockRayTraceResult trace = new BlockRayTraceResult(
                                        mc.player.getPositionVec().add(0.0, -2.0, 0.0),
                                        Direction.UP,
                                        mc.player.getPosition().add(0, -2, 0),
                                        false);
                                CPlayerTryUseItemOnBlockPacket packet = new CPlayerTryUseItemOnBlockPacket(Hand.MAIN_HAND,
                                        trace);
                                Objects.requireNonNull(mc.getConnection()).sendPacket(packet);
                            }

                            if (var5 >= 0 && fakeBlock) {
                                mc.getConnection()
                                        .sendPacket(new CHeldItemChangePacket(mc.player.inventory.currentItem));
                            }

                            e.setY(this.minY - mc.player.getPositionVec().y);
                        }
                    } else {
                        mc.player.jump();
                        e.setY(mc.player.getMotion().y);
                        this.minY = !mc.gameSettings.keyBindJump.isKeyDown()
                                ? (!this.down ? mc.player.getPositionVec().y : mc.player.getPositionVec().y - 1.0)
                                : (!this.down ? mc.player.getPositionVec().y + 1.0
                                        : mc.player.getPositionVec().y);
                        MovementUtil.setMotion(e, 0.35);
                    }
                }
            } else {
                mc.player.jump();
                e.setY(mc.player.getMotion().y);
                MovementUtil.setMotion(e, 0.35);
                this.minY = !mc.gameSettings.keyBindJump.isKeyDown()
                        ? (!this.down ? mc.player.getPositionVec().y : mc.player.getPositionVec().y - 1.0)
                        : (!this.down ? mc.player.getPositionVec().y + 1.0 : mc.player.getPositionVec().y);
            }

            mc.player.setMotion(e.getX(), e.getY(), e.getZ());
        }
    }

    public int getBlockSlot() {
        for (int i = 36; i < 45; i++) {
			assert mc.player != null;
			if (mc.player.container.getSlot(i).getHasStack()) {
                ItemStack item = mc.player.container.getSlot(i).getStack();
                if (item.getItem() instanceof BlockItem) {
                    if (i - 36 == mc.player.inventory.currentItem) {
                        i = 34;
                    }

                    return i - 36;
                }
            }
        }

        return -1;
    }
}
