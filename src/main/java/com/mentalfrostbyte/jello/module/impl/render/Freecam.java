package com.mentalfrostbyte.jello.module.impl.render;

import com.mentalfrostbyte.jello.event.impl.game.action.EventKeyPress;
import com.mentalfrostbyte.jello.event.impl.game.action.EventMouseHover;
import com.mentalfrostbyte.jello.event.impl.game.network.EventReceivePacket;
import com.mentalfrostbyte.jello.event.impl.game.network.EventSendPacket;
import com.mentalfrostbyte.jello.event.impl.game.render.EventRender2D;
import com.mentalfrostbyte.jello.event.impl.game.render.EventRender3D;
import com.mentalfrostbyte.jello.event.impl.game.render.EventRenderEntity;
import com.mentalfrostbyte.jello.event.impl.game.render.EventRenderFire;
import com.mentalfrostbyte.jello.event.impl.game.world.EventLoadWorld;
import com.mentalfrostbyte.jello.event.impl.game.world.EventPushBlock;
import com.mentalfrostbyte.jello.event.impl.player.movement.EventJump;
import com.mentalfrostbyte.jello.event.impl.player.movement.EventMotion;
import com.mentalfrostbyte.jello.event.impl.player.movement.EventMoveInput;
import com.mentalfrostbyte.jello.module.Module;
import com.mentalfrostbyte.jello.module.data.ModuleCategory;
import com.mentalfrostbyte.jello.module.settings.impl.BooleanSetting;
import com.mentalfrostbyte.jello.module.settings.impl.NumberSetting;
import com.mentalfrostbyte.jello.util.game.player.MovementUtil;
import com.mojang.authlib.GameProfile;
import net.minecraft.client.entity.player.ClientPlayerEntity;
import net.minecraft.client.entity.player.RemoteClientPlayerEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.play.client.CAnimateHandPacket;
import net.minecraft.network.play.client.CUseEntityPacket;
import net.minecraft.network.play.server.SPlayerPositionLookPacket;
import net.minecraft.util.Hand;
import net.minecraft.util.math.AxisAlignedBB;
import team.sdhq.eventBus.annotations.EventTarget;

public class Freecam extends Module {
    public static PlayerEntity player;

    private double posX, posY, posZ;
    private double prevPosX, prevPosY, prevPosZ;
    private float yaw, pitch;
    private int entityId;

    private boolean forward, backward, left, right, jump, sneak;
    private float moveForward, moveStrafe;

    public Freecam() {
        super(ModuleCategory.RENDER, "Freecam", "Move client side but not server side");
        this.registerSetting(new NumberSetting<Float>("Speed", "Speed value", 4.0F, 1.0F, 10.0F, 0.1F));
        this.registerSetting(new BooleanSetting("AllowCameraInteract", "Allow interacting with blocks/entities from camera position", true));
        this.registerSetting(new BooleanSetting("AllowRotationChange", "Allow rotation changes while in freecam", true));
    }

    @Override
    public void onEnable() {
        this.posX = mc.player.getPosX();
        this.posY = mc.player.getPosY();
        this.posZ = mc.player.getPosZ();
        this.prevPosX = this.posX;
        this.prevPosY = this.posY;
        this.prevPosZ = this.posZ;
        this.yaw = mc.player.rotationYaw;
        this.pitch = mc.player.rotationPitch;

        String name = mc.player.getName().getString();
        GameProfile profile = new GameProfile(mc.player.getGameProfile().getId(), name);
        player = new RemoteClientPlayerEntity(mc.world, profile);
        player.inventory = mc.player.inventory;
        player.setPositionAndRotation(this.posX, this.posY, this.posZ, this.yaw, this.pitch);
        player.noClip = true;
        player.entityCollisionReduction = mc.player.entityCollisionReduction;
        player.rotationYawHead = this.yaw;
        player.prevRotationYawHead = this.yaw;
        player.renderYawOffset = this.yaw;
        player.prevRenderYawOffset = this.yaw;
        this.entityId = (int) (Math.random() * -10000.0);
        mc.world.addEntity(this.entityId, player);

        this.forward = mc.gameSettings.keyBindForward.isKeyDown();
        this.backward = mc.gameSettings.keyBindBack.isKeyDown();
        this.left = mc.gameSettings.keyBindLeft.isKeyDown();
        this.right = mc.gameSettings.keyBindRight.isKeyDown();
        this.jump = mc.gameSettings.keyBindJump.isKeyDown();
        this.sneak = mc.gameSettings.keyBindSneak.isKeyDown();

        this.moveForward = this.forward != this.backward ? (this.forward ? 1.0F : -1.0F) : 0.0F;
        this.moveStrafe = this.left != this.right ? (this.left ? 1.0F : -1.0F) : 0.0F;

        mc.gameSettings.keyBindForward.pressed = false;
        mc.gameSettings.keyBindBack.pressed = false;
        mc.gameSettings.keyBindLeft.pressed = false;
        mc.gameSettings.keyBindRight.pressed = false;
        mc.gameSettings.keyBindJump.pressed = false;
        mc.gameSettings.keyBindSneak.pressed = false;
    }

    @Override
    public void onDisable() {
        mc.gameSettings.keyBindForward.pressed = this.forward;
        mc.gameSettings.keyBindBack.pressed = this.backward;
        mc.gameSettings.keyBindLeft.pressed = this.left;
        mc.gameSettings.keyBindRight.pressed = this.right;
        mc.gameSettings.keyBindJump.pressed = this.jump;
        mc.gameSettings.keyBindSneak.pressed = this.sneak;
        mc.world.removeEntityFromWorld(this.entityId);
        mc.player.resetPositionToBB();
        if (player != null) {
            mc.player.entityCollisionReduction = player.entityCollisionReduction;
        }
        player = null;
    }

    @EventTarget
    public void onRenderEntity(EventRenderEntity event) {
        if (this.isEnabled()) {
            if (event.getEntity() instanceof ClientPlayerEntity && event.getEntity() != player) {
                event.cancelled = true;
            }
        }
    }

    @EventTarget
    public void onRender2D(EventRender2D event) {
        if (this.isEnabled()) {
            if (player == null) {
                this.onEnable();
            }

            mc.player.lastReportedPitch = mc.player.rotationPitch;
            AxisAlignedBB bb = mc.player.boundingBox;
            player.setPosition((bb.minX + bb.maxX) / 2.0, bb.minY, (bb.minZ + bb.maxZ) / 2.0);

            double interpolatedX = this.prevPosX + (this.posX - this.prevPosX) * (double) event.partialTicks;
            double interpolatedY = this.prevPosY + (this.posY - this.prevPosY) * (double) event.partialTicks;
            double interpolatedZ = this.prevPosZ + (this.posZ - this.prevPosZ) * (double) event.partialTicks;

            mc.player.positionVec.x = interpolatedX;
            mc.player.lastTickPosX = interpolatedX;
            mc.player.chasingPosX = interpolatedX;
            mc.player.prevPosX = interpolatedX;
            mc.player.positionVec.y = interpolatedY;
            mc.player.lastTickPosY = interpolatedY;
            mc.player.chasingPosY = interpolatedY;
            mc.player.prevPosY = interpolatedY;
            mc.player.positionVec.z = interpolatedZ;
            mc.player.lastTickPosZ = interpolatedZ;
            mc.player.chasingPosZ = interpolatedZ;
            mc.player.prevPosZ = interpolatedZ;

            if (MovementUtil.isMoving()) {
                mc.player.cameraYaw = 0.099999994F;
            }
        }
    }

    @EventTarget
    public void onRender3D(EventRender3D event) {
        if (this.isEnabled()) {
            player.resetPositionToBB();
            player.boundingBox = new AxisAlignedBB(0.0, 0.0, 0.0, 0.0, 0.0, 0.0);
        }
    }

    @EventTarget
    public void onMoveInput(EventMoveInput event) {
        if (this.isEnabled()) {
            double speed = this.getNumberValueBySettingName("Speed") / 2.0;

            float[] dir = MovementUtil.getDirectionArray(this.moveForward, this.moveStrafe);
            float forwardDir = dir[1];
            float strafeDir = dir[2];
            float yawDir = dir[0];

            double cos = Math.cos(Math.toRadians(yawDir));
            double sin = Math.sin(Math.toRadians(yawDir));

            this.prevPosX = this.posX;
            this.prevPosY = this.posY;
            this.prevPosZ = this.posZ;

            this.posX += (forwardDir * cos + strafeDir * sin) * speed;
            this.posZ += (forwardDir * sin - strafeDir * cos) * speed;

            if (this.jump) {
                this.posY += speed;
            }
            if (this.sneak) {
                this.posY -= speed;
            }

            event.forward = 0;
            event.strafe = 0;
            event.jumping = false;
            event.sneaking = false;
        }
    }

    @EventTarget
    public void onMotion(EventMotion event) {
        if (this.isEnabled() && event.isPre()) {
            if (this.getBooleanValueFromSettingName("AllowRotationChange")) {
                event.setYaw(this.yaw % 360.0F);
                event.setPitch(this.pitch);
                mc.player.lastReportedYaw = this.yaw;
                mc.player.lastReportedPitch = this.pitch;
            }
        }
    }

    @EventTarget
    public void onKeyPress(EventKeyPress event) {
        if (this.isEnabled()) {
            int key = event.getKey();
            if (key == mc.gameSettings.keyBindForward.keyCode.getKeyCode()) {
                event.cancelled = true;
                this.forward = true;
            } else if (key == mc.gameSettings.keyBindBack.keyCode.getKeyCode()) {
                event.cancelled = true;
                this.backward = true;
            } else if (key == mc.gameSettings.keyBindLeft.keyCode.getKeyCode()) {
                event.cancelled = true;
                this.left = true;
            } else if (key == mc.gameSettings.keyBindRight.keyCode.getKeyCode()) {
                event.cancelled = true;
                this.right = true;
            } else if (key == mc.gameSettings.keyBindJump.keyCode.getKeyCode()) {
                event.cancelled = true;
                this.jump = true;
            } else if (key == mc.gameSettings.keyBindSneak.keyCode.getKeyCode()) {
                event.cancelled = true;
                this.sneak = true;
            }

            this.moveForward = this.forward != this.backward ? (this.forward ? 1.0F : -1.0F) : 0.0F;
            this.moveStrafe = this.left != this.right ? (this.left ? 1.0F : -1.0F) : 0.0F;
        }
    }

    @EventTarget
    public void onMouseHover(EventMouseHover event) {
        if (this.isEnabled()) {
            int button = event.getMouseButton();
            if (button == mc.gameSettings.keyBindForward.keyCode.getKeyCode()) {
                event.cancelled = true;
                this.forward = false;
            } else if (button == mc.gameSettings.keyBindBack.keyCode.getKeyCode()) {
                event.cancelled = true;
                this.backward = false;
            } else if (button == mc.gameSettings.keyBindLeft.keyCode.getKeyCode()) {
                event.cancelled = true;
                this.left = false;
            } else if (button == mc.gameSettings.keyBindRight.keyCode.getKeyCode()) {
                event.cancelled = true;
                this.right = false;
            } else if (button == mc.gameSettings.keyBindJump.keyCode.getKeyCode()) {
                event.cancelled = true;
                this.jump = false;
            } else if (button == mc.gameSettings.keyBindSneak.keyCode.getKeyCode()) {
                event.cancelled = true;
                this.sneak = false;
            }

            this.moveForward = this.forward != this.backward ? (this.forward ? 1.0F : -1.0F) : 0.0F;
            this.moveStrafe = this.left != this.right ? (this.left ? 1.0F : -1.0F) : 0.0F;
        }
    }

    @EventTarget
    public void onJump(EventJump event) {
        if (this.isEnabled()) {
            event.cancelled = true;
        }
    }

    @EventTarget
    public void onReceivePacket(EventReceivePacket event) {
        if (this.isEnabled()) {
            if (mc.player != null) {
                if (event.packet instanceof SPlayerPositionLookPacket packet) {
                    this.yaw = packet.yaw;
                    this.pitch = packet.pitch;
                    packet.yaw = mc.player.rotationYaw;
                    packet.pitch = mc.player.rotationPitch;
                    double x = packet.x;
                    double y = packet.y;
                    double z = packet.z;
                    float w = PlayerEntity.STANDING_SIZE.width;
                    float h = PlayerEntity.STANDING_SIZE.height;
                    mc.player.setBoundingBox(new AxisAlignedBB(
                            x - (double) w, y, z - (double) w,
                            x + (double) w, y + (double) h, z + (double) w));
                    event.cancelled = true;
                    player.setMotion(0.0, 0.0, 0.0);
                }
            }
        }
    }

    @EventTarget
    public void onSendPacket(EventSendPacket event) {
        if (this.isEnabled()) {
            if (event.packet instanceof CAnimateHandPacket) {
                player.swingArm(Hand.MAIN_HAND);
            }
            if (event.packet instanceof CUseEntityPacket usePacket) {
                if (usePacket.getEntityFromWorld(mc.world) == null) {
                    event.cancelled = true;
                }
            }
        }
    }

    @EventTarget
    public void onRenderFire(EventRenderFire event) {
        if (this.isEnabled()) {
            event.cancelled = true;
        }
    }

    @EventTarget
    public void onPushBlock(EventPushBlock event) {
        if (this.isEnabled()) {
            event.cancelled = true;
        }
    }

    @EventTarget
    public void onLoadWorld(EventLoadWorld event) {
        if (this.isEnabled()) {
            this.setState(false);
        }
    }
}
