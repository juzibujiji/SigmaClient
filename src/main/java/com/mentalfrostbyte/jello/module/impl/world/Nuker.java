package com.mentalfrostbyte.jello.module.impl.world;

import com.mentalfrostbyte.jello.event.impl.game.action.EventKeyPress;
import com.mentalfrostbyte.jello.event.impl.game.render.EventRender3D;
import com.mentalfrostbyte.jello.event.impl.player.EventUpdate;
import com.mentalfrostbyte.jello.module.Module;
import com.mentalfrostbyte.jello.module.data.ModuleCategory;
import com.mentalfrostbyte.jello.module.settings.impl.*;
import com.mentalfrostbyte.jello.util.client.render.theme.ClientColors;
import com.mentalfrostbyte.jello.util.game.player.rotation.RotationCore;
import com.mentalfrostbyte.jello.util.game.world.BoundingBox;
import com.mentalfrostbyte.jello.util.game.world.blocks.BlockUtil;
import com.mentalfrostbyte.jello.util.game.player.combat.RotationUtil;
import com.mentalfrostbyte.jello.util.game.render.RenderUtil;

import net.minecraft.block.*;
import net.minecraft.network.play.client.CAnimateHandPacket;
import net.minecraft.network.play.client.CPlayerDiggingPacket;
import net.minecraft.state.properties.BedPart;
import net.minecraft.util.Direction;
import net.minecraft.util.Hand;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.BlockRayTraceResult;
import net.minecraft.world.GameType;
import org.lwjgl.opengl.GL11;
import team.sdhq.eventBus.EventBus;
import team.sdhq.eventBus.annotations.EventTarget;

import java.util.ArrayList;
import java.util.List;

public class Nuker extends Module {
    public BlockPos targetPos;
    public List<BlockPos> blocksToDestroy;

    public Nuker() {
        super(ModuleCategory.WORLD, "Nuker", "Destroys blocks around you");
        this.registerSetting(new NumberSetting<>("Range", "Range value for nuker", 6.0F, 2.0F, 8.0F, 0.5F));
        this.registerSetting(new ModeSetting("Mode", "Mode", 0, "All", "One hit", "Bed", "Egg"));
        this.registerSetting(new BooleanSetting("NoSwing", "Removes the swing animation.", false));
        this.registerSetting(new BooleanSetting("RayTrace","",false));
        this.registerSetting(new BooleanSetting("First BedOuter","Break Bed first outer bypass some Plugin",false) {
            @Override
            public boolean isHidden() {
                return !getStringSettingValueByName("Mode").equals("Bed");
            }
        });
        this.registerSetting(new BooleanListSetting("Blocks", "Blocks to destroy", true));
        this.registerSetting(new ColorSetting("Color", "The rendered block color", ClientColors.MID_GREY.getColor(), true));
    }

    public static void destroyBlock(BlockPos block) {
        mc.getConnection().sendPacket(new CPlayerDiggingPacket(CPlayerDiggingPacket.Action.START_DESTROY_BLOCK, block, Direction.UP));
        mc.getConnection().sendPacket(new CPlayerDiggingPacket(CPlayerDiggingPacket.Action.STOP_DESTROY_BLOCK, block, Direction.UP));
        mc.world.setBlockState(block, Blocks.AIR.getDefaultState());
    }

    @EventTarget
    public void onUpdate(EventUpdate event) {
        if (this.isEnabled()) {
            float range = this.getNumberValueBySettingName("Range");
            this.blocksToDestroy = this.getBlocksToDestroy(range);
            if (this.blocksToDestroy.isEmpty()) {
                this.targetPos = null;
            } else if (mc.playerController.getCurrentGameType() != GameType.CREATIVE) {
                if (this.targetPos != null) {
                    if (mc.world.getBlockState(this.targetPos).isAir()
                            || eyeDistanceTo(this.targetPos) > (double) range
                            || !this.blocksToDestroy.contains(this.targetPos)) {
                        this.targetPos = this.blocksToDestroy.get(0);
                    }

                    float[] rotations = BlockUtil.getBlockBestRotation(targetPos);
                    RotationCore.setRotations(rotations[0],rotations[1]);
                    EventKeyPress keyPress = new EventKeyPress(0, false, this.targetPos);
                    EventBus.call(keyPress);
                } else {
                    this.targetPos = this.blocksToDestroy.get(0);
                    float[] var6 = BlockUtil.getBlockBestRotation(targetPos);
                    RotationCore.setRotations(var6[0],var6[1]);
                    EventKeyPress keyPress = new EventKeyPress(0, false, this.targetPos);
                    EventBus.call(keyPress);
                }

                if (this.getBooleanValueFromSettingName("RayTrace")) {
                    BlockRayTraceResult raytrace = BlockUtil.rayTraceBlock(RotationCore.lastYaw, RotationCore.lastPitch, 0.0F, targetPos, true);
                    if (raytrace.getType() != net.minecraft.util.math.RayTraceResult.Type.MISS && raytrace.getPos() == targetPos) {
                        mc.playerController.onPlayerDamageBlock(this.targetPos, BlockUtil.getBestFacingDirection(this.targetPos));
                        if (!this.getBooleanValueFromSettingName("NoSwing")) {
                            mc.player.swingArm(Hand.MAIN_HAND);
                        } else {
                            mc.getConnection().sendPacket(new CAnimateHandPacket(Hand.MAIN_HAND));
                        }
                    }
                } else {
                    mc.playerController.onPlayerDamageBlock(this.targetPos, BlockUtil.getBestFacingDirection(this.targetPos));
                    if (!this.getBooleanValueFromSettingName("NoSwing")) {
                        mc.player.swingArm(Hand.MAIN_HAND);
                    } else {
                        mc.getConnection().sendPacket(new CAnimateHandPacket(Hand.MAIN_HAND));
                    }
                }
            } else {
                for (BlockPos var9 : this.blocksToDestroy) {
                    mc.getConnection().sendPacket(new CPlayerDiggingPacket(CPlayerDiggingPacket.Action.START_DESTROY_BLOCK, var9, BlockUtil.getBestFacingDirection(var9)));
                    if (!this.getBooleanValueFromSettingName("NoSwing")) {
                        mc.player.swingArm(Hand.MAIN_HAND);
                    } else {
                        mc.getConnection().sendPacket(new CAnimateHandPacket(Hand.MAIN_HAND));
                    }
                }
            }
        }
    }

    @EventTarget
    public void onRender(EventRender3D var1) {
        if (this.targetPos != null && !mc.world.getBlockState(this.targetPos).isAir()) {
            int var4 = RenderUtil.applyAlpha(this.parseSettingValueToIntBySettingName("Color"), 0.4F);
            GL11.glPushMatrix();
            GL11.glDisable(2929);
            double var5 = (double) this.targetPos.getX() - mc.gameRenderer.getActiveRenderInfo().getPos().getX();
            double var7 = (double) this.targetPos.getY() - mc.gameRenderer.getActiveRenderInfo().getPos().getY();
            double var9 = (double) this.targetPos.getZ() - mc.gameRenderer.getActiveRenderInfo().getPos().getZ();
            AxisAlignedBB var11 = mc.world.getBlockState(this.targetPos).getCollisionShape(mc.world, this.targetPos).getBoundingBox();
            BoundingBox var12 = new BoundingBox(
                    var5 + var11.minX,
                    var7 + var11.minY,
                    var9 + var11.minZ,
                    var5 + var11.maxX,
                    var7 + var11.maxY,
                    var9 + var11.maxZ
            );
            RenderUtil.render3DColoredBox(var12, var4);
            GL11.glEnable(2929);
            GL11.glPopMatrix();
        }
    }

    private static double eyeDistanceTo(BlockPos pos) {
        double dx = mc.player.getPosX() - ((double) pos.getX() + 0.5);
        double dy = mc.player.getPosYEye() - ((double) pos.getY() + 0.5);
        double dz = mc.player.getPosZ() - ((double) pos.getZ() + 0.5);
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    public boolean isReplaceable(BlockPos pos) {
        Block block = mc.world.getBlockState(pos).getBlock();
        return mc.world.getBlockState(pos).getMaterial().isReplaceable() || block instanceof BushBlock;
    }

    /**
     * 床周围需要清空的 8 个格子:上方 2、床头床尾各 1、左右各 2。
     * 纯几何计算,不读世界,便于单独校验。
     */
    public static BlockPos[] getBedSurroundings(BlockPos bed, Direction facing, BedPart part) {
        Direction toOther = part == BedPart.HEAD ? facing.getOpposite() : facing;
        BlockPos other = bed.offset(toOther);
        return new BlockPos[]{
                bed.up(), other.up(),
                other.offset(toOther), bed.offset(toOther.getOpposite()),
                bed.offset(facing.rotateY()), other.offset(facing.rotateY()),
                bed.offset(facing.rotateYCCW()), other.offset(facing.rotateYCCW())
        };
    }

    /**
     * 只要有一格是空气/流体就说明床已裸露,返回 null 直接挖床;
     * 否则返回一个可破坏的覆盖方块,先挖它。
     * 已经在挖的那格优先保留,换目标会让服务端 ABORT 并清空挖掘进度。
     */
    private BlockPos findBedCover(BlockPos bed, float range) {
        BlockState bedState = mc.world.getBlockState(bed);
        BlockPos best = null;

        for (BlockPos pos : getBedSurroundings(bed, bedState.get(BedBlock.HORIZONTAL_FACING), bedState.get(BedBlock.PART))) {
            BlockState state = mc.world.getBlockState(pos);
            if (state.isAir() || !state.getFluidState().isEmpty()) {
                return null;
            }
            if (state.getBlockHardness(mc.world, pos) < 0.0F || eyeDistanceTo(pos) >= (double) range) {
                continue;
            }
            if (pos.equals(this.targetPos)) {
                return pos;
            }
            if (best == null || eyeDistanceTo(pos) < eyeDistanceTo(best)) {
                best = pos;
            }
        }

        return best;
    }

    public List<BlockPos> getBlocksToDestroy(float range) {
        ArrayList<BlockPos> blocksToDestroy = new ArrayList<>();

        for (float y = range + 2.0F; y >= -range + 1.0F; y--) {
            for (float x = -range; x <= range; x++) {
                for (float z = -range; z <= range; z++) {
                    BlockPos pos = new BlockPos(
                            mc.player.getPosX() + (double) x,
                            mc.player.getPosY() + (double) y,
                            mc.player.getPosZ() + (double) z
                    );
                    if (!mc.world.getBlockState(pos).isAir()
                            && mc.world.getBlockState(pos).getFluidState().isEmpty()
                            && eyeDistanceTo(pos) < (double) range) {
                        String mode = this.getStringSettingValueByName("Mode");
                        switch (mode) {
                            case "One hit":
                                if (!this.isReplaceable(pos)) {
                                    continue;
                                }
                                break;
                            case "Bed":
                                if (!(mc.world.getBlockState(pos).getBlock() instanceof BedBlock)) {
                                    continue;
                                }
                                if (this.getBooleanValueFromSettingName("First BedOuter")) {
                                    BlockPos cover = this.findBedCover(pos, range);
                                    if (cover != null) {
                                        if (!blocksToDestroy.contains(cover)) {
                                            blocksToDestroy.add(cover);
                                        }
                                        continue;
                                    }
                                }
                                break;
                            case "Egg":
                                if (!(mc.world.getBlockState(pos).getBlock() instanceof DragonEggBlock)) {
                                    continue;
                                }
                        }

                        blocksToDestroy.add(pos);
                    }
                }
            }
        }

        return blocksToDestroy;
    }
}
