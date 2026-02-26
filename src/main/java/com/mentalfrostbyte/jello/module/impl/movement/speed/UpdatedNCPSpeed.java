package com.mentalfrostbyte.jello.module.impl.movement.speed;

import com.mentalfrostbyte.jello.event.impl.player.movement.EventMotion;
import com.mentalfrostbyte.jello.module.Module;
import com.mentalfrostbyte.jello.module.data.ModuleCategory;
import com.mentalfrostbyte.jello.module.settings.impl.ModeSetting;
import com.mentalfrostbyte.jello.util.game.player.MovementUtil;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import team.sdhq.eventBus.annotations.EventTarget;

/**
 * @author StormingMoon
 */
public class UpdatedNCPSpeed extends Module {
    private final ModeSetting mode;
    public UpdatedNCPSpeed(){
        super(ModuleCategory.MOVEMENT,"UpdatedNCP","Speed for UpdatedNCP");
        registerSetting(
                this.mode = new ModeSetting("Mode", "Mode",0, "Basic", "Low", "Ground"));
    }
    public BlockState blockStateUnder() {
        assert mc.player != null;
        BlockPos under = new BlockPos(
                mc.player.getPosX(),
                mc.player.getBoundingBox().minY - 0.5000001D,
                mc.player.getPosZ()
        );
        assert mc.world != null;
        return mc.world.getBlockState(under);
    }
    @Override
    public void onDisable() {
        super.onDisable();
        mc.timer.timerSpeed = 1.0F;
    }
    @EventTarget
    public void onMotion(EventMotion event) {
        assert mc.player != null;
        switch (mode.currentValue) {
            case "Basic" -> {
                if (mc.player.onGround) {
                    if (MovementUtil.isMoving())
                        mc.player.jump();
                    mc.timer.timerSpeed = 1.09F;
                    MovementUtil.strafe(0.48);
                }
            }
            case "Low" -> {
                if (mc.player.onGround) {
                    if (MovementUtil.isMoving())
                        mc.player.setMotion(mc.player.getPosX(), 0.40, mc.player.getPosZ());
                    MovementUtil.strafe(0.48);
                    mc.timer.timerSpeed = 1.09F;
                }
            }
            case "Ground" -> {
                if (mc.player.onGround) {
                    if (MovementUtil.isMoving())
                        mc.player.setMotion(mc.player.getPosX(), 0.05F, mc.player.getPosZ());
                    mc.timer.timerSpeed = 1.09F;
                    MovementUtil.strafe(0.25);
                }
            }
        }
    }
}