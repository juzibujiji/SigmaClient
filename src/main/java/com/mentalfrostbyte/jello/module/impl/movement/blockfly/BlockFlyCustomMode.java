package com.mentalfrostbyte.jello.module.impl.movement.blockfly;

import com.mentalfrostbyte.jello.event.impl.game.action.EventClick;
import com.mentalfrostbyte.jello.event.impl.player.EventRunTicks;
import com.mentalfrostbyte.jello.event.impl.player.EventUpdateHeldItem;
import com.mentalfrostbyte.jello.event.impl.player.movement.EventJump;
import com.mentalfrostbyte.jello.event.impl.player.movement.EventMove;
import com.mentalfrostbyte.jello.event.impl.player.movement.EventSafeWalk;
import com.mentalfrostbyte.jello.managers.RotationManager;
import com.mentalfrostbyte.jello.module.Module;
import com.mentalfrostbyte.jello.module.data.ModuleCategory;
import com.mentalfrostbyte.jello.module.impl.movement.BlockFly;
import com.mentalfrostbyte.jello.module.settings.impl.BooleanSetting;
import com.mentalfrostbyte.jello.module.settings.impl.ModeSetting;
import com.mentalfrostbyte.jello.util.game.player.MovementUtil;
import com.mentalfrostbyte.jello.util.game.player.constructor.Rotation;
import com.mentalfrostbyte.jello.util.game.player.rotation.util.RotationUtils;
import net.minecraft.block.AirBlock;
import net.minecraft.block.AnvilBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.BushBlock;
import net.minecraft.block.ChestBlock;
import net.minecraft.block.CraftingTableBlock;
import net.minecraft.block.CropsBlock;
import net.minecraft.block.EnchantingTableBlock;
import net.minecraft.block.EnderChestBlock;
import net.minecraft.block.FlowerBlock;
import net.minecraft.block.FlowingFluidBlock;
import net.minecraft.block.FungusBlock;
import net.minecraft.block.FurnaceBlock;
import net.minecraft.block.SlabBlock;
import net.minecraft.block.SnowBlock;
import net.minecraft.block.TallGrassBlock;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.item.BlockItem;
import net.minecraft.item.BlockNamedItem;
import net.minecraft.item.ItemStack;
import net.minecraft.network.play.client.CAnimateHandPacket;
import net.minecraft.util.ActionResultType;
import net.minecraft.util.Direction;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.BlockRayTraceResult;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RayTraceContext;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.vector.Vector3d;
import net.minecraft.util.math.shapes.VoxelShape;
import org.apache.commons.lang3.RandomUtils;
import org.lwjgl.glfw.GLFW;
import team.sdhq.eventBus.annotations.EventTarget;
import team.sdhq.eventBus.annotations.priority.HigherPriority;
import team.sdhq.eventBus.annotations.priority.LowestPriority;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class BlockFlyCustomMode extends Module {
    public static final List<Block> BLACKLISTED_BLOCKS = Arrays.asList(
            Blocks.AIR,
            Blocks.WATER,
            Blocks.LAVA,
            Blocks.ENCHANTING_TABLE,
            Blocks.GLASS_PANE,
            Blocks.IRON_BARS,
            Blocks.SNOW,
            Blocks.COAL_ORE,
            Blocks.DIAMOND_ORE,
            Blocks.EMERALD_ORE,
            Blocks.CHEST,
            Blocks.TRAPPED_CHEST,
            Blocks.TORCH,
            Blocks.ANVIL,
            Blocks.NOTE_BLOCK,
            Blocks.JUKEBOX,
            Blocks.TNT,
            Blocks.GOLD_ORE,
            Blocks.IRON_ORE,
            Blocks.LAPIS_ORE,
            Blocks.STONE_PRESSURE_PLATE,
            Blocks.LIGHT_WEIGHTED_PRESSURE_PLATE,
            Blocks.HEAVY_WEIGHTED_PRESSURE_PLATE,
            Blocks.STONE_BUTTON,
            Blocks.LEVER,
            Blocks.TALL_GRASS,
            Blocks.TRIPWIRE,
            Blocks.TRIPWIRE_HOOK,
            Blocks.RAIL,
            Blocks.CORNFLOWER,
            Blocks.RED_MUSHROOM,
            Blocks.BROWN_MUSHROOM,
            Blocks.VINE,
            Blocks.SUNFLOWER,
            Blocks.LADDER,
            Blocks.FURNACE,
            Blocks.SAND,
            Blocks.CACTUS,
            Blocks.DISPENSER,
            Blocks.DROPPER,
            Blocks.CRAFTING_TABLE,
            Blocks.COBWEB,
            Blocks.PUMPKIN,
            Blocks.COBBLESTONE_WALL,
            Blocks.OAK_FENCE,
            Blocks.REDSTONE_TORCH,
            Blocks.FLOWER_POT
    );

    public Rotation correctRotation = new Rotation(0.0F, 0.0F);
    public Rotation rots = new Rotation(0.0F, 0.0F);
    public Rotation lastRots = new Rotation(0.0F, 0.0F);

    private BlockFly blockFly;
    private final ModeSetting modeSetting;
    private final BooleanSetting eagle;
    private final BooleanSetting sneak;
    private final BooleanSetting advancedBlockSearch;
    private final BooleanSetting snap;
    private final BooleanSetting hideSnap;
    private final BooleanSetting renderItemSpoof;
    private BlockPos pos;
    private int oldSlot = -1;
    private int lastSneakTicks;
    private boolean placedJump;

    public BlockFlyCustomMode() {
        super(ModuleCategory.MOVEMENT, "Custom", "HeyPixel-style scaffold port.");
        this.registerSetting(this.modeSetting = new ModeSetting("Mode", "Bridge mode.", 0,
                "Normal", "Telly Bridge", "Keep Y"));
        this.registerSetting(this.eagle = new BooleanSetting("Eagle",
                "Auto-sneak on block edges in Normal mode.", true) {
            @Override
            public boolean isHidden() {
                return !BlockFlyCustomMode.this.isNormalMode();
            }
        });
        this.registerSetting(this.sneak = new BooleanSetting("Sneak",
                "Pulse sneak periodically while scaffolding.", true));
        this.registerSetting(this.advancedBlockSearch = new BooleanSetting("Advanced Block Search",
                "Search yaw/pitch offsets when the direct scaffold ray misses.", true));
        this.registerSetting(this.snap = new BooleanSetting("Snap",
                "Snap yaw on Normal placements.", true) {
            @Override
            public boolean isHidden() {
                return !BlockFlyCustomMode.this.isNormalMode();
            }
        });
        this.registerSetting(this.hideSnap = new BooleanSetting("Hide Snap Rotation",
                "Keep snap correction out of first-person look rendering.", true) {
            @Override
            public boolean isHidden() {
                return !BlockFlyCustomMode.this.isNormalMode()
                        || !BlockFlyCustomMode.this.snap.getCurrentValue();
            }
        });
        this.registerSetting(this.renderItemSpoof = new BooleanSetting("Render Item Spoof", "Render the pre-scaffold hotbar item while blocks are selected.", true));
    }

    @Override
    public void initialize() {
        this.blockFly = (BlockFly) this.access();
    }

    @Override
    public void onEnable() {
        if (mc.player == null) {
            return;
        }

        this.oldSlot = mc.player.inventory.currentItem;
        this.rots = new Rotation(mc.player.rotationYaw - 180.0F, mc.player.rotationPitch);
        this.lastRots = new Rotation(mc.player.prevRotationYaw - 180.0F, mc.player.prevRotationPitch);
        this.correctRotation = new Rotation(this.rots.yaw, this.rots.pitch);
        this.pos = null;
        this.lastSneakTicks = 0;
        this.placedJump = false;
        ((BlockFly) this.access()).lastSpoofedSlot = -1;
    }

    @Override
    public void onDisable() {
        if (mc.player == null) {
            return;
        }

        mc.gameSettings.keyBindJump.setPressed(this.isActuallyKeyDown(mc.gameSettings.keyBindJump));
        mc.gameSettings.keyBindSneak.setPressed(this.isActuallyKeyDown(mc.gameSettings.keyBindSneak));
        mc.gameSettings.keyBindUseItem.setPressed(false);

        if (this.oldSlot >= 0 && this.oldSlot < 9) {
            mc.player.inventory.currentItem = this.oldSlot;
            mc.playerController.syncCurrentPlayItem();
        }

        this.oldSlot = -1;
        this.pos = null;
        ((BlockFly) this.access()).lastSpoofedSlot = -1;
    }

    @EventTarget
    public void onUpdateHeldItem(EventUpdateHeldItem event) {
        if (!this.isEnabled()
                || !this.renderItemSpoof.getCurrentValue()
                || event.getHand() != Hand.MAIN_HAND
                || this.oldSlot < 0
                || this.oldSlot >= 9
                || mc.player == null) {
            return;
        }

        event.setItem(mc.player.inventory.getStackInSlot(this.oldSlot));
    }

    @EventTarget
    public void onClick(EventClick event) {
        if (this.isEnabled() && event.getButton() == EventClick.Button.RIGHT) {
            event.cancelled = true;
        }
    }

    @EventTarget
    public void onSafeWalk(EventSafeWalk event) {
        if (this.isEnabled()
                && mc.player != null
                && this.isNormalMode()
                && this.eagle.getCurrentValue()
                && mc.player.isOnGround()
                && this.isOnBlockEdge(0.3F)) {
            event.setSafe(true);
        }
    }

    @EventTarget
    @HigherPriority
    public void onMove(EventMove event) {
        if (!this.isEnabled() || mc.player == null || this.blockFly.getValidItemCount() == 0) {
            return;
        }

        if (this.access().getBooleanValueFromSettingName("No Sprint")) {
            mc.player.setSprinting(false);
        } else if (!this.access().getBooleanValueFromSettingName("UesGameSprint")) {
            mc.player.setSprinting(true);
        }

        this.blockFly.onMove(event);
    }

    @EventTarget
    public void onJump(EventJump event) {
        if (this.isEnabled()
                && this.placedJump
                && this.access().getStringSettingValueByName("Tower Mode").equalsIgnoreCase("Vanilla")
                && (!MovementUtil.isMoving()
                || this.access().getBooleanValueFromSettingName("Tower while moving"))) {
            event.cancelled = true;
        }
    }

    @EventTarget
    @LowestPriority
    public void onMotion(EventRunTicks event) {
        if (!this.isEnabled() || mc.player == null || mc.world == null) {
            return;
        }


        this.onPreMotion();
        this.placeBlock();
    }

    private void onPreMotion() {
        this.placedJump = false;
        this.selectHotbarBlock();
        this.pos = this.getBlockPos();

        if (this.pos != null) {
            this.correctRotation = this.getPlayerYawRotation();
            if (this.isNormalMode() && this.snap.getCurrentValue()) {
                this.rots.yaw = this.correctRotation.yaw;
            } else {
                this.rots.yaw = RotationUtils.updateRotation(this.rots.yaw, this.correctRotation.yaw, 75.0F);
            }

            this.rots.pitch = this.correctRotation.pitch;
        }

        boolean holdingJump = this.isActuallyKeyDown(mc.gameSettings.keyBindJump);
        this.handleSneakPulse();

        if (this.isTellyBridgeMode()) {
            mc.gameSettings.keyBindJump.setPressed(this.hasMovementInput() || holdingJump);
            if (mc.player.isOnGround() && this.hasMovementInput()) {
                this.rots.yaw = RotationUtils.updateRotation(this.rots.yaw, mc.player.rotationYaw, 180.0F);
                this.publishRotations();
                this.lastRots = new Rotation(this.rots.yaw, this.rots.pitch);
                return;
            }
        } else if (this.isKeepYMode()) {
            mc.gameSettings.keyBindJump.setPressed(this.hasMovementInput() || holdingJump);
        } else {
            if (this.eagle.getCurrentValue()) {
                mc.gameSettings.keyBindSneak.setPressed(mc.player.isOnGround() && this.isOnBlockEdge(0.3F));
            }

            if (this.snap.getCurrentValue() && !holdingJump) {
                this.doSnap();
            }

            mc.gameSettings.keyBindJump.setPressed(holdingJump);
        }

        this.publishRotations();
        this.lastRots = new Rotation(this.rots.yaw, this.rots.pitch);
    }

    private void publishRotations() {
        Rotation visibleRotation = this.hideSnap.getCurrentValue()
                && this.isNormalMode()
                && this.snap.getCurrentValue()
                ? this.correctRotation
                : this.rots;

        RotationManager.setRotations(visibleRotation.yaw, visibleRotation.pitch);
    }

    private void handleSneakPulse() {
        if (!this.sneak.getCurrentValue()) {
            if (!this.isActuallyKeyDown(mc.gameSettings.keyBindSneak)) {
                mc.gameSettings.keyBindSneak.setPressed(false);
            }
            return;
        }

        this.lastSneakTicks++;
        if (this.lastSneakTicks == 18) {
            if (mc.player.isSprinting()) {
                mc.gameSettings.keyBindSprint.setPressed(false);
                mc.player.setSprinting(false);
            }

            mc.gameSettings.keyBindSneak.setPressed(true);
        } else if (this.lastSneakTicks >= 21) {
            mc.gameSettings.keyBindSneak.setPressed(this.isActuallyKeyDown(mc.gameSettings.keyBindSneak));
            this.lastSneakTicks = 0;
        }
    }

    private void doSnap() {
        boolean shouldPlaceBlock = false;
        BlockRayTraceResult objectPosition = this.rayCast(1.0F, this.rots);
        if (objectPosition.getType() == RayTraceResult.Type.BLOCK
                && objectPosition.getPos().equals(this.pos)
                && objectPosition.getFace() != Direction.UP) {
            shouldPlaceBlock = true;
        }

        if (!shouldPlaceBlock) {
            this.rots.yaw = mc.player.rotationYaw + RandomUtils.nextFloat(0.0F, 0.5F) - 0.25F;
        }
    }

    private void placeBlock() {
        if (this.pos == null || !isValidStack(mc.player.getHeldItemMainhand())) {
            return;
        }

        BlockRayTraceResult position = this.rayCast(1.0F, this.rots);
        if (position.getType() != RayTraceResult.Type.BLOCK) {
            return;
        }

        boolean holdingJump = this.isActuallyKeyDown(mc.gameSettings.keyBindJump);
        if (!position.getPos().equals(this.pos)
                || (position.getFace() == Direction.UP
                && !mc.player.isOnGround()
                && this.hasMovementInput()
                && !holdingJump
                && !this.isNormalMode())) {
            return;
        }

        BlockRayTraceResult lastPosition = this.rayCast(1.0F, this.lastRots);
        boolean lastRotationValid = lastPosition.getType() == RayTraceResult.Type.BLOCK
                && lastPosition.getPos().equals(position.getPos());
        if (!lastRotationValid) {
            mc.playerController.processRightClick(mc.player, mc.world, Hand.MAIN_HAND);
        }

        ActionResultType result = mc.playerController.func_217292_a(mc.player, mc.world, Hand.MAIN_HAND, position);
        if (result.isSuccessOrConsume()) {
            if (!this.access().getBooleanValueFromSettingName("NoSwing")) {
                mc.player.swingArm(Hand.MAIN_HAND);
            } else {
                mc.getConnection().sendPacket(new CAnimateHandPacket(Hand.MAIN_HAND));
            }

            this.placedJump = true;
        }
    }

    private Rotation getPlayerYawRotation() {
        float rotationYaw = mc.player.rotationYaw - 180.0F;
        if (this.isTower()) {
            return new Rotation(rotationYaw, 90.0F);
        }

        float pitch = 82.0F;
        Rotation rotations = new Rotation(rotationYaw, pitch);
        float realYaw = mc.player.rotationYaw;
        float magic = RandomUtils.nextFloat(0.0F, 0.5F) - 0.25F;
        if (this.advancedBlockSearch.getCurrentValue()) {
            if (mc.gameSettings.keyBindBack.isKeyDown()) {
                realYaw += 180.0F;
                if (mc.gameSettings.keyBindLeft.isKeyDown()) {
                    realYaw += 45.0F;
                } else if (mc.gameSettings.keyBindRight.isKeyDown()) {
                    realYaw -= 45.0F;
                }
            } else if (mc.gameSettings.keyBindForward.isKeyDown()) {
                if (mc.gameSettings.keyBindLeft.isKeyDown()) {
                    realYaw -= 45.0F;
                } else if (mc.gameSettings.keyBindRight.isKeyDown()) {
                    realYaw += 45.0F;
                }
            } else if (mc.gameSettings.keyBindRight.isKeyDown()) {
                realYaw += 90.0F;
            } else if (mc.gameSettings.keyBindLeft.isKeyDown()) {
                realYaw -= 90.0F;
            }
        }

        float yaw = realYaw - 180.0F + magic;
        rotations.yaw = yaw;
        if (this.shouldBuild()) {
            BlockRayTraceResult initialHit = this.performRayCast(rotations);
            if (this.isHitValid(initialHit)) {
                return rotations;
            }

            ArrayList<Float> validPitches = this.findValidPitches(yaw);
            if (!validPitches.isEmpty()) {
                validPitches.sort(Comparator.comparingDouble(this::distanceToLastPitch));
                rotations.pitch = validPitches.get(0);
                return rotations;
            }

            if (this.advancedBlockSearch.getCurrentValue()) {
                Rotation optimalRotation = this.findOptimalRotation(yaw);
                if (optimalRotation != null) {
                    return optimalRotation;
                }
            }
        }

        return rotations;
    }

    private boolean shouldBuild() {
        BlockPos playerPos = new BlockPos(mc.player.getPosX(), mc.player.getPosY() - 0.5, mc.player.getPosZ());
        return mc.world.isAirBlock(playerPos) && isValidStack(mc.player.getHeldItemMainhand());
    }

    private double distanceToLastPitch(float pitch) {
        return Math.abs(pitch - this.rots.pitch);
    }

    private ArrayList<Float> findValidPitches(float yaw) {
        ArrayList<Float> validPitches = new ArrayList<>();

        for (float i = Math.max(this.rots.pitch - 30.0F, -90.0F);
             i < Math.min(this.rots.pitch + 20.0F, 90.0F);
             i += 0.3F) {
            Rotation fixed = this.getFixedRotation(yaw, i, this.rots.yaw, this.rots.pitch);
            BlockRayTraceResult position = this.performRayCast(new Rotation(yaw, fixed.pitch));
            if (this.isHitValid(position)) {
                validPitches.add(fixed.pitch);
            }
        }

        return validPitches;
    }

    private BlockRayTraceResult performRayCast(Rotation rotations) {
        return this.rayCast(1.0F, rotations);
    }

    private boolean isHitValid(BlockRayTraceResult hit) {
        if (hit.getType() != RayTraceResult.Type.BLOCK || this.pos == null) {
            return false;
        }

        return this.isValidBlock(hit.getPos())
                && this.isNearbyBlockPos(hit.getPos())
                && hit.getFace() != Direction.DOWN
                && hit.getFace() != Direction.UP;
    }

    private Rotation findOptimalRotation(float yaw) {
        for (float yawLoops = 0.0F; yawLoops < 180.0F; yawLoops++) {
            float currentPitch = this.rots.pitch;

            for (float pitchLoops = 0.0F; pitchLoops < 25.0F; pitchLoops++) {
                for (int i = 0; i < 2; i++) {
                    float pitch = currentPitch - pitchLoops * (i == 0 ? 1.0F : -1.0F);
                    float[][] offsets = new float[][]{
                            {yaw + yawLoops, pitch},
                            {yaw - yawLoops, pitch}
                    };

                    for (float[] rotation : offsets) {
                        float rayCastPitch = MathHelper.clamp(rotation[1], -90.0F, 90.0F);
                        Rotation fixedRotation = this.getFixedRotation(rotation[0], rayCastPitch,
                                this.rots.yaw, this.rots.pitch);
                        BlockRayTraceResult position = this.performRayCast(fixedRotation);
                        if (this.isHitValid(position)) {
                            return fixedRotation;
                        }
                    }
                }
            }
        }

        return null;
    }

    private boolean isNearbyBlockPos(BlockPos blockPos) {
        if (this.pos == null) {
            return false;
        }

        if (!mc.player.isOnGround()) {
            return blockPos.equals(this.pos);
        }

        for (int x = this.pos.getX() - 1; x <= this.pos.getX() + 1; x++) {
            for (int z = this.pos.getZ() - 1; z <= this.pos.getZ() + 1; z++) {
                if (blockPos.equals(new BlockPos(x, this.pos.getY(), z))) {
                    return true;
                }
            }
        }

        return false;
    }

    private BlockPos getBlockPos() {
        BlockPos playerPos = new BlockPos(mc.player.getPosX(), mc.player.getPosY() - 1.0, mc.player.getPosZ());
        ArrayList<Vector3d> positions = new ArrayList<>();
        Map<Vector3d, BlockPos> positionMap = new HashMap<>();

        for (int x = playerPos.getX() - 5; x <= playerPos.getX() + 5; x++) {
            for (int y = playerPos.getY() - 1; y <= playerPos.getY(); y++) {
                for (int z = playerPos.getZ() - 5; z <= playerPos.getZ() + 5; z++) {
                    BlockPos checkPosition = new BlockPos(x, y, z);
                    if (this.isValidBlock(checkPosition)) {
                        BlockState block = mc.world.getBlockState(checkPosition);
                        Vector3d vec3 = getVec3(checkPosition, block);
                        positions.add(vec3);
                        positionMap.put(vec3, checkPosition);
                    }
                }
            }
        }

        if (positions.isEmpty()) {
            return null;
        }

        positions.sort(Comparator.comparingDouble(this::getBlockDistance));
        BlockPos closest = positionMap.get(positions.get(0));
        if (this.isTower() && closest != null && closest.getY() != mc.player.getPosY() - 1.5) {
            return new BlockPos(mc.player.getPosX(), mc.player.getPosY() - 1.5, mc.player.getPosZ());
        }

        return closest;
    }

    private boolean isValidBlock(BlockPos blockPos) {
        Block block = mc.world.getBlockState(blockPos).getBlock();
        return !(block instanceof FlowingFluidBlock)
                && !(block instanceof AirBlock)
                && !(block instanceof ChestBlock)
                && !(block instanceof FurnaceBlock)
                && !(block instanceof EnderChestBlock)
                && !(block instanceof TallGrassBlock)
                && !(block instanceof SnowBlock)
                && !(block instanceof EnchantingTableBlock)
                && !(block instanceof AnvilBlock)
                && !(block instanceof CraftingTableBlock);
    }

    private boolean isTower() {
        return this.isActuallyKeyDown(mc.gameSettings.keyBindJump)
                && !mc.gameSettings.keyBindForward.isKeyDown()
                && !mc.gameSettings.keyBindBack.isKeyDown()
                && !mc.gameSettings.keyBindLeft.isKeyDown()
                && !mc.gameSettings.keyBindRight.isKeyDown();
    }

    private double getBlockDistance(Vector3d vec3) {
        return mc.player.getDistanceSq(vec3.x, vec3.y, vec3.z);
    }

    private void selectHotbarBlock() {
        int slot = this.getValidHotbarBlockSlot();
        if (slot != -1 && mc.player.inventory.currentItem != slot) {
            mc.player.inventory.currentItem = slot;
            mc.playerController.syncCurrentPlayItem();
        }
    }

    private int getValidHotbarBlockSlot() {
        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.player.inventory.getStackInSlot(i);
            if (isValidStack(stack)) {
                return i;
            }
        }

        return -1;
    }

    private Rotation getFixedRotation(float yaw, float pitch, float lastYaw, float lastPitch) {
        float[] fixed = RotationUtils.gcdFix(new float[]{yaw, pitch}, new float[]{lastYaw, lastPitch});
        return new Rotation(fixed[0], fixed[1]);
    }

    private BlockRayTraceResult rayCast(float partialTicks, Rotation rotations) {
        Vector3d eyes = mc.player.getEyePosition(partialTicks);
        float yaw = (float) Math.toRadians(rotations.yaw);
        float pitch = (float) Math.toRadians(rotations.pitch);
        float x = -MathHelper.sin(yaw) * MathHelper.cos(pitch);
        float y = -MathHelper.sin(pitch);
        float z = MathHelper.cos(yaw) * MathHelper.cos(pitch);
        double reach = mc.playerController.getBlockReachDistance();
        Vector3d end = eyes.add(x * reach, y * reach, z * reach);
        return mc.world.rayTraceBlocks(new RayTraceContext(
                eyes,
                end,
                RayTraceContext.BlockMode.OUTLINE,
                RayTraceContext.FluidMode.NONE,
                mc.getRenderViewEntity()
        ));
    }

    private boolean hasMovementInput() {
        return mc.player.movementInput.moveForward != 0.0F
                || mc.player.movementInput.moveStrafe != 0.0F
                || mc.gameSettings.keyBindForward.isKeyDown()
                || mc.gameSettings.keyBindBack.isKeyDown()
                || mc.gameSettings.keyBindLeft.isKeyDown()
                || mc.gameSettings.keyBindRight.isKeyDown();
    }

    private boolean isOnBlockEdge(float sensitivity) {
        return !mc.world.getCollisionShapes(mc.player,
                        mc.player.getBoundingBox().offset(0.0, -0.5, 0.0)
                                .grow(-sensitivity, 0.0, -sensitivity))
                .findAny()
                .isPresent();
    }

    private boolean isActuallyKeyDown(KeyBinding keyBinding) {
        return GLFW.glfwGetKey(mc.getMainWindow().getHandle(),
                keyBinding.keyCode.getKeyCode()) == GLFW.GLFW_PRESS;
    }

    private boolean isNormalMode() {
        return "Normal".equals(this.modeSetting.getCurrentValue());
    }

    private boolean isTellyBridgeMode() {
        return "Telly Bridge".equals(this.modeSetting.getCurrentValue());
    }

    private boolean isKeepYMode() {
        return "Keep Y".equals(this.modeSetting.getCurrentValue());
    }

    private static Vector3d getVec3(BlockPos checkPosition, BlockState block) {
        VoxelShape shape = block.getShape(mc.world, checkPosition);
        if (shape.isEmpty()) {
            return new Vector3d(checkPosition.getX() + 0.5, checkPosition.getY() + 0.5, checkPosition.getZ() + 0.5);
        }

        double ex = MathHelper.clamp(mc.player.getPosX(),
                checkPosition.getX() + shape.getStart(Direction.Axis.X),
                checkPosition.getX() + shape.getEnd(Direction.Axis.X));
        double ey = MathHelper.clamp(mc.player.getPosY(),
                checkPosition.getY() + shape.getStart(Direction.Axis.Y),
                checkPosition.getY() + shape.getEnd(Direction.Axis.Y));
        double ez = MathHelper.clamp(mc.player.getPosZ(),
                checkPosition.getZ() + shape.getStart(Direction.Axis.Z),
                checkPosition.getZ() + shape.getEnd(Direction.Axis.Z));
        return new Vector3d(ex, ey, ez);
    }

    public static boolean isValidStack(ItemStack stack) {
        if (stack == null || stack.isEmpty() || !(stack.getItem() instanceof BlockItem) || stack.getCount() <= 1) {
            return false;
        }

        if (hasBlockedDisplayName(stack)) {
            return false;
        }

        if (stack.getItem() instanceof BlockNamedItem) {
            return false;
        }

        Block block = ((BlockItem) stack.getItem()).getBlock();
        if (block instanceof FlowerBlock
                || block instanceof BushBlock
                || block instanceof FungusBlock
                || block instanceof CropsBlock
                || block instanceof SlabBlock) {
            return false;
        }

        return !BLACKLISTED_BLOCKS.contains(block);
    }

    private static boolean hasBlockedDisplayName(ItemStack stack) {
        String name = stack.getDisplayName().getString();
        return name.contains("Click")
                || name.contains("Right")
                || name.contains("Teleport")
                || name.contains("Use")
                || name.contains("\u70b9\u51fb")
                || name.contains("\u4f7f\u7528")
                || name.contains("\u4f20\u9001")
                || name.contains("\u518d\u6765");
    }
}
