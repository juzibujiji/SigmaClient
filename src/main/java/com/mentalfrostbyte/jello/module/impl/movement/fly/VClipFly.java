package com.mentalfrostbyte.jello.module.impl.movement.fly;

import com.mentalfrostbyte.jello.event.impl.game.action.EventKeyPress;
import com.mentalfrostbyte.jello.event.impl.game.action.EventMouseHover;
import com.mentalfrostbyte.jello.event.impl.game.render.EventRender2D;
import com.mentalfrostbyte.jello.event.impl.player.movement.EventMove;
import com.mentalfrostbyte.jello.event.impl.player.movement.EventMotion;
import com.mentalfrostbyte.jello.gui.base.JelloPortal;
import com.mentalfrostbyte.jello.module.Module;
import com.mentalfrostbyte.jello.module.data.ModuleCategory;
import com.mentalfrostbyte.jello.module.settings.impl.BooleanSetting;
import com.mentalfrostbyte.jello.module.settings.impl.ModeSetting;
import com.mentalfrostbyte.jello.module.settings.impl.NumberSetting;
import com.mentalfrostbyte.jello.util.game.player.MovementUtil;
import com.mentalfrostbyte.jello.util.game.world.blocks.BlockUtil;
import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import net.minecraft.network.play.client.CPlayerPacket;
import net.minecraft.network.play.server.SPlayerPositionLookPacket;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.shapes.VoxelShape;
import team.sdhq.eventBus.annotations.EventTarget;

import java.util.*;

public class VClipFly extends Module {
    private final BooleanSetting hideMotion;
    private final NumberSetting<Float> speed;
    private boolean sneakCancelled;
    private int ticksInAir;
    public int ticksSinceLastVClip;
    private double previousY;
    private final NumberSetting<Float> minFallDistance;
    private final ModeSetting clipDistanceMode;
    private final ModeSetting whenToClip;
    private final NumberSetting<Float> delay;
    private final NumberSetting<Float> constantClipDistance;

    public VClipFly() {
        super(ModuleCategory.MOVEMENT, "VClip", "Fly that uses VClip");
        this.registerSetting(
                this.speed = new NumberSetting<>(
                        "Speed",
                        "Fly speed",
                        4.0F,
                        0.28F,
                        10.0F,
                        0.01F
                )
        );
        this.registerSetting(
                whenToClip = new ModeSetting(
                        "When",
                        "When do we VClip?",
                        "Fall distance",
                        "Fall distance",
                        "Time"
                )
        );
        this.registerSetting(
                delay = new NumberSetting<>(
                        "Delay",
                        "Delay",
                        3.0F,
                        0.1F,
                        10.0F,
                        0.1F
                )
        );
        this.registerSetting(
                minFallDistance = new NumberSetting<>(
                        "Fall distance",
                        "Minimum fall distance before we VClip back up",
                        1.0F,
                        0.3F,
                        25.5F,
                        0.1F
                )
        );
        this.registerSetting(
                clipDistanceMode = new ModeSetting(
                        "Clip distance mode",
                        "Minimum fall distance before we VClip back up",
                        "Fall distance",
                        "Fall distance",
                        "Constant"
                )
        );
        this.registerSetting(
                constantClipDistance = new NumberSetting<>(
                        "Clip distance (if constant)",
                        "Blocks to clip up after falling the minimum distance",
                        1.0F,
                        0.1F,
                        25.5F,
                        0.001F
                )
        );
        this.registerSetting(new BooleanSetting("Kick bypass", "Bypass vanilla kick for flying", true));
        this.registerSetting(
                this.hideMotion = new BooleanSetting(
                    "Fake",
                    "Hides the horrible constant clipping, disable if you want a seizure",
                    true
                )
        );
    }

    @Override
    public void onEnable() {
        if (!mc.gameSettings.keyBindSneak.isKeyDown()) {
            this.sneakCancelled = false;
        } else {
            mc.gameSettings.keyBindSneak.setPressed(false);
            this.sneakCancelled = true;
        }
        previousY = mc.player.getPosY();
    }

    @Override
    public void onDisable() {
        mc.player.setMotion(mc.player.getMotion().x, -0.08, mc.player.getMotion().z);
        double plrSpeed = MovementUtil.getSmartSpeed();
        MovementUtil.moveInDirection(plrSpeed);
        if (this.sneakCancelled) {
            mc.gameSettings.keyBindSneak.setPressed(true);
        }
    }

    @EventTarget
    public void onKeyPress(EventKeyPress event) {
        if (this.isEnabled()) {
            if (event.getKey() == mc.gameSettings.keyBindSneak.keyCode.getKeyCode()) {
                event.cancelled = true;
                this.sneakCancelled = true;
            }
        }
    }

    @EventTarget
    public void onMouseHover(EventMouseHover event) {
        if (this.isEnabled()) {
            if (event.getMouseButton() == mc.gameSettings.keyBindSneak.keyCode.getKeyCode()) {
                event.cancelled = true;
                this.sneakCancelled = false;
            }
        }
    }

    @EventTarget
    public void onRender(EventRender2D __) {
        if (!hideMotion.currentValue) return;
        double newY = previousY + getVerticalSpeed();
        mc.player.setPosition(mc.player.getPosX(), newY, mc.player.getPosZ());
        mc.player.lastTickPosY = newY;
        mc.player.chasingPosY = newY;
        mc.player.prevPosY = newY;
    }

    @EventTarget
    public void onUpdate(EventMotion event) {
        if (!this.isEnabled()) return;
        if (mc.player.isOnGround()) return;
        if (!this.getBooleanValueFromSettingName("Kick bypass")) return;

        if (this.ticksInAir > 0 && this.ticksInAir % 30 == 0
                && !BlockUtil.isAboveBounds(mc.player, 0.01F)) {

            if (!JelloPortal.getVersion().equalTo(ProtocolVersion.v1_8)) {
                event.setY(event.getY() - 0.04);
            } else {
                double collisionHeight = this.getGroundCollisionHeight();
                if (collisionHeight < 0.0) {
                    return;
                }

                double yPosition = event.getY();
                List<Double> yPositions = new ArrayList<>();
                if (!(yPosition - collisionHeight > 9.0)) {
                    mc.getConnection().sendPacket(
                            new CPlayerPacket.PositionPacket(event.getX(), collisionHeight, event.getZ(), true));
                } else {
                    while (yPosition > collisionHeight + 9.0) {
                        yPosition -= 9.0;
                        yPositions.add(yPosition);
                        mc.getConnection().sendPacket(
                                new CPlayerPacket.PositionPacket(event.getX(), yPosition, event.getZ(), true));
                    }

                    for (Double intermediateY : yPositions) {
                        mc.getConnection().sendPacket(
                                new CPlayerPacket.PositionPacket(event.getX(), intermediateY, event.getZ(), true));
                    }

                    mc.getConnection().sendPacket(
                            new CPlayerPacket.PositionPacket(event.getX(), collisionHeight, event.getZ(), true));
                    Collections.reverse(yPositions);

                    for (Double intermediateYReversed : yPositions) {
                        mc.getConnection().sendPacket(new CPlayerPacket.PositionPacket(event.getX(),
                                intermediateYReversed, event.getZ(), true));
                    }

                    mc.getConnection().sendPacket(
                            new CPlayerPacket.PositionPacket(event.getX(), event.getY(), event.getZ(), true));
                }

                this.ticksInAir = 0;
            }
        }
    }

    double getVerticalSpeed() {
        double verticalSpeed = !mc.gameSettings.keyBindJump.isPressed() ? 0.0 : speed.currentValue / 2.0;
        if (mc.gameSettings.keyBindJump.isPressed() && mc.gameSettings.keyBindSneak.isPressed()) {
            verticalSpeed = 0.0;
        } else if (!this.sneakCancelled) {
            if (mc.gameSettings.keyBindJump.isPressed()) {
                verticalSpeed = speed.currentValue / 2.0;
            }
        } else {
            verticalSpeed = -speed.currentValue / 2.0;
        }
        return verticalSpeed;
    }

    private void doVClip(double fallDistance) {
        double clipY = (Objects.equals(clipDistanceMode.currentValue, "Constant") ? constantClipDistance.currentValue : fallDistance);
        assert mc.player != null;
        double newY = (mc.player.getPosY() + clipY) + getVerticalSpeed();
        Objects.requireNonNull(mc.getConnection())
                .handlePlayerPosLook(
                        new SPlayerPositionLookPacket(
                                mc.player.getPosX(),
                                newY,
                                mc.player.getPosZ(),
                                mc.player.rotationYaw,
                                mc.player.rotationPitch,
                                Collections.emptySet(),
                                (int) (2.147483647E9 * Math.random())
                        )
                );
        previousY = newY;
    }

    @EventTarget
    public void onMove(EventMove event) {
        if (this.isEnabled()) {
            if (!BlockUtil.isAboveBounds(mc.player, 0.01F)) {
                this.ticksInAir++;
            } else {
                this.ticksInAir = 0;
            }

            double speed = this.getNumberValueBySettingName("Speed");
            double verticalSpeed = getVerticalSpeed();

            MovementUtil.setMotion(event, speed);
            double fallDistance = mc.player.getPosY() - previousY;
            if (fallDistance < 0) fallDistance = -fallDistance;
            switch (whenToClip.currentValue) {
                case "Fall distance":
                    if (fallDistance > this.minFallDistance.currentValue && !mc.player.isOnGround()) {
                        doVClip(fallDistance);
                    }
                    break;
                case "Time":
                    ticksSinceLastVClip++;
                    if (ticksSinceLastVClip >= this.delay.currentValue) {
                        doVClip(fallDistance);
                        ticksSinceLastVClip = 0;
                    }
                    break;
            }
        }
    }

    private double getGroundCollisionHeight() {
        if (!(mc.player.getPositionVec().y < 1.0)) {
            if (!mc.player.isOnGround()) {
                AxisAlignedBB alignedBB = mc.player.getBoundingBox().expand(0.0, -mc.player.getPositionVec().y, 0.0);
                Iterator<VoxelShape> shapeIterator = mc.world.getCollisionShapes(mc.player, alignedBB).iterator();
                double maxCollisionHeight = -1.0;

                while (shapeIterator.hasNext()) {
                    VoxelShape voxelShape = shapeIterator.next();
                    if (voxelShape.getBoundingBox().maxY > maxCollisionHeight) {
                        maxCollisionHeight = voxelShape.getBoundingBox().maxY;
                    }
                }

                return maxCollisionHeight;
            } else {
                return mc.player.getPosY();
            }
        } else {
            return -1.0;
        }
    }
}
