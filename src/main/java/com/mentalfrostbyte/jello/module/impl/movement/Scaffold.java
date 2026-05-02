package com.mentalfrostbyte.jello.module.impl.movement;

import com.mentalfrostbyte.jello.event.impl.game.action.EventClick;
import com.mentalfrostbyte.jello.event.impl.player.EventGetFovModifier;
import com.mentalfrostbyte.jello.event.impl.player.EventRunTicks;
import com.mentalfrostbyte.jello.event.impl.player.EventUpdateHeldItem;
import com.mentalfrostbyte.jello.event.impl.player.movement.EventMotion;
import com.mentalfrostbyte.jello.managers.RotationManager;
import com.mentalfrostbyte.jello.module.Module;
import com.mentalfrostbyte.jello.module.data.ModuleCategory;
import com.mentalfrostbyte.jello.module.settings.impl.BooleanSetting;
import com.mentalfrostbyte.jello.module.settings.impl.ModeSetting;
import com.mentalfrostbyte.jello.module.settings.impl.NumberSetting;
import com.mentalfrostbyte.jello.util.game.player.MovementUtil;
import com.mentalfrostbyte.jello.util.game.player.constructor.Rotation;
import com.mentalfrostbyte.jello.util.game.player.prediction.FallingPlayer;
import com.mentalfrostbyte.jello.util.game.player.rotation.util.RotationUtils;
import net.minecraft.block.AirBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.BushBlock;
import net.minecraft.block.CropsBlock;
import net.minecraft.block.FlowerBlock;
import net.minecraft.block.FungusBlock;
import net.minecraft.block.SlabBlock;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.item.BlockItem;
import net.minecraft.item.BlockNamedItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.SkullItem;
import net.minecraft.potion.Effects;
import net.minecraft.util.ActionResultType;
import net.minecraft.util.Direction;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.BlockRayTraceResult;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RayTraceContext;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.vector.Vector3d;
import net.minecraft.util.math.vector.Vector3i;
import org.apache.commons.lang3.RandomUtils;
import org.lwjgl.glfw.GLFW;
import team.sdhq.eventBus.annotations.EventTarget;
import team.sdhq.eventBus.annotations.priority.HigherPriority;
import team.sdhq.eventBus.annotations.priority.LowestPriority;

import java.util.Arrays;
import java.util.List;
import java.util.Random;

/**
 * 1:1 port of HeyPixel-Mod / Naven's {@code Scaffold} module from
 * {@code com.heypixel.heypixelmod.obsoverlay.modules.impl.move.Scaffold}.
 *
 * <p>Every helper Naven calls into ({@code MathUtils.getRandomDoubleInRange},
 * {@code InventoryUtils.isItemValid}, {@code PlayerUtils.movementInput},
 * {@code PlayerUtils.getMoveSpeedEffectAmplifier},
 * {@code RotationUtils.getRotations(BlockPos, partialTicks)},
 * {@code RotationUtils.diffCalc}, {@code RotationUtils.randomization},
 * {@code RayTraceUtils.rayCast(float, Vector2f)}) is reproduced here verbatim
 * so the algorithm is byte-for-byte equivalent to the source.</p>
 *
 * <p>Mappings translated from 1.20.1 to 1.16.5:</p>
 * <ul>
 *   <li>{@code mc.options.keyJump} -> {@code mc.gameSettings.keyBindJump}</li>
 *   <li>{@code mc.options.keyShift} -> {@code mc.gameSettings.keyBindSneak}</li>
 *   <li>{@code mc.options.keyUse} -> {@code mc.gameSettings.keyBindUseItem}</li>
 *   <li>{@code mc.options.keySprint} -> {@code mc.gameSettings.keyBindSprint}</li>
 *   <li>{@code mc.player.getInventory().selected} -> {@code mc.player.inventory.currentItem}</li>
 *   <li>{@code InteractionResult.SUCCESS} -> {@code ActionResultType.SUCCESS}</li>
 *   <li>{@code mc.gameMode.useItemOn(...)} -> {@code mc.playerController.func_217292_a(...)}</li>
 *   <li>{@code BlockPos.containing(d,d,d)} -> {@code new BlockPos(d,d,d)}</li>
 *   <li>{@code BlockState#entityCanStandOn / entityCanStandOnFace} ->
 *       {@code Block.hasEnoughSolidSide} + air/replaceable check</li>
 *   <li>{@code mc.level.getCollisions(...)} -> {@code mc.world.getCollisionShapes(...)}</li>
 *   <li>{@code InputConstants.isKeyDown(...)} -> {@code GLFW.glfwGetKey(...)}</li>
 *   <li>{@code Player#getJumpBoostPower()} -> rebuilt via Effects.JUMP_BOOST</li>
 *   <li>{@code ItemNameBlockItem} -> {@code BlockNamedItem}</li>
 *   <li>{@code CropBlock} -> {@code CropsBlock}</li>
 * </ul>
 *
 * <p>EventRunTicks PRE is fired from the patched
 * {@link net.minecraft.client.Minecraft#runTick()} HEAD (see the Naven-style
 * dispatch added there) so the early-tick handler runs <em>before</em>
 * {@code processKeyBinds()} dispatches {@link EventClick}, matching Naven's
 * 1.20.1 ordering of {@code Minecraft#tick HEAD -> handleKeybinds -> tick TAIL}.</p>
 */
public class Scaffold extends Module {

    /**
     * Direct mirror of Naven's {@code blacklistedBlocks} list (kept verbatim,
     * including the duplicate Blocks.GLASS_PANE / Blocks.TRAPPED_CHEST entries
     * that exist in the source).
     */
    public static final List<Block> blacklistedBlocks = Arrays.asList(
            Blocks.AIR,
            Blocks.WATER,
            Blocks.LAVA,
            Blocks.ENCHANTING_TABLE,
            Blocks.GLASS_PANE,
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
            Blocks.TRAPPED_CHEST,
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

    private static final Random RANDOM = new Random();

    public Rotation correctRotation = new Rotation(0.0F, 0.0F);
    public Rotation rots = new Rotation(0.0F, 0.0F);
    public Rotation lastRots = new Rotation(0.0F, 0.0F);

    public final ModeSetting modeSetting;
    public final BooleanSetting eagle;
    public final BooleanSetting sneakSetting;
    public final BooleanSetting snap;
    public final BooleanSetting hideSnap;
    public final BooleanSetting renderItemSpoof;
    public final BooleanSetting keepFoV;
    public final NumberSetting<Float> fov;

    private int offGroundTicks = 0;
    int oldSlot;
    private BlockPosWithFacing pos;
    private int lastSneakTicks;
    public int baseY = -1;

    public Scaffold() {
        super(ModuleCategory.MOVEMENT, "Scaffold", "Automatically places blocks under you");
        this.registerSetting(this.modeSetting = new ModeSetting(
                "Mode", "Bridge style.", 0,
                "Normal", "Telly Bridge", "Keep Y"));
        this.registerSetting(this.eagle = new BooleanSetting(
                "Eagle", "Auto-sneak on block edges in Normal mode.", true) {
            @Override
            public boolean isHidden() {
                return !Scaffold.this.modeIs("Normal");
            }
        });
        this.registerSetting(this.sneakSetting = new BooleanSetting(
                "Sneak", "Pulse sneak periodically while scaffolding.", true));
        this.registerSetting(this.snap = new BooleanSetting(
                "Snap", "Snap yaw when the placement raycast already lines up.", true) {
            @Override
            public boolean isHidden() {
                return !Scaffold.this.modeIs("Normal");
            }
        });
        this.registerSetting(this.hideSnap = new BooleanSetting(
                "Hide Snap Rotation",
                "Keep snap correction out of first-person look rendering.", true) {
            @Override
            public boolean isHidden() {
                return !Scaffold.this.modeIs("Normal")
                        || !Scaffold.this.snap.getCurrentValue();
            }
        });
        this.registerSetting(this.renderItemSpoof = new BooleanSetting(
                "Render Item Spoof",
                "Render the pre-scaffold hotbar item while blocks are selected.", true));
        this.registerSetting(this.keepFoV = new BooleanSetting(
                "Keep FoV", "Lock movement FoV while scaffolding.", true));
        this.registerSetting(this.fov = new NumberSetting<>(
                "FoV", "FoV multiplier used while scaffolding.",
                1.15F, 1.0F, 2.0F, 0.05F) {
            @Override
            public boolean isHidden() {
                return !Scaffold.this.keepFoV.getCurrentValue();
            }
        });
    }

    // -----------------------------------------------------------------
    // Naven static helpers - Vector2f-equivalent (Sigma uses Rotation)
    // -----------------------------------------------------------------

    /**
     * Mirror of Naven's {@code Scaffold.isValidStack} - same predicate chain,
     * same rejection order. The {@code InventoryUtils.isItemValid} call is
     * inlined as {@link #isItemValid(ItemStack)} below.
     */
    public static boolean isValidStack(ItemStack stack) {
        if (stack == null
                || !(stack.getItem() instanceof BlockItem)
                || stack.getCount() <= 1) {
            return false;
        }
        if (!isItemValid(stack)) {
            return false;
        }
        String string = stack.getDisplayName().getString();
        if (string.contains("Click") || string.contains("\u70b9\u51fb")) {
            return false;
        }
        if (stack.getItem() instanceof BlockNamedItem) {
            return false;
        }
        Block block = ((BlockItem) stack.getItem()).getBlock();
        if (block instanceof FlowerBlock) {
            return false;
        }
        if (block instanceof BushBlock) {
            return false;
        }
        if (block instanceof FungusBlock) {
            return false;
        }
        if (block instanceof CropsBlock) {
            return false;
        }
        if (block instanceof SlabBlock) {
            return false;
        }
        return !blacklistedBlocks.contains(block);
    }

    /**
     * Verbatim port of Naven's {@code InventoryUtils.isItemValid} - rejects
     * placeholder hotbar items used by mini-game lobbies (English + Chinese).
     * {@code SkullItem} is the 1.16.5 mapping for Naven's
     * {@code PlayerHeadItem}.
     */
    public static boolean isItemValid(ItemStack s) {
        if (!s.isEmpty()) {
            if (s.getItem() instanceof SkullItem) {
                return false;
            }
            String string = s.getDisplayName().getString();
            if (string.contains("Click")) {
                return false;
            }
            if (string.contains("Right")) {
                return false;
            }
            if (string.contains("\u70b9\u51fb")) {
                return false;
            }
            if (string.contains("Teleport")) {
                return false;
            }
            if (string.contains("\u4f7f\u7528")) {
                return false;
            }
            if (string.contains("\u4f20\u9001")) {
                return false;
            }
            if (string.contains("\u518d\u6765")) {
                return false;
            }
        }
        return true;
    }

    /**
     * Mirror of Naven's {@code Scaffold.isOnBlockEdge} - returns true when no
     * collisions exist directly below the player after shrinking the bounding
     * box inward by {@code sensitivity}. Used by Eagle-mode sneak logic.
     */
    public static boolean isOnBlockEdge(float sensitivity) {
        return !mc.world
                .getCollisionShapes(mc.player,
                        mc.player.getBoundingBox()
                                .offset(0.0, -0.5, 0.0)
                                .grow(-sensitivity, 0.0, -sensitivity))
                .iterator()
                .hasNext();
    }

    /** Naven {@code MathUtils.getRandomDoubleInRange} (verbatim). */
    private static double getRandomDoubleInRange(double minDouble, double maxDouble) {
        return minDouble >= maxDouble
                ? minDouble
                : RANDOM.nextDouble() * (maxDouble - minDouble) + minDouble;
    }

    /**
     * Naven {@code PlayerUtils.movementInput} - polls the four cardinal
     * movement keys directly. This is what the source uses to gate Telly
     * Bridge / Keep Y jump-key forcing.
     */
    private static boolean movementInput() {
        return mc.gameSettings.keyBindForward.isKeyDown()
                || mc.gameSettings.keyBindBack.isKeyDown()
                || mc.gameSettings.keyBindLeft.isKeyDown()
                || mc.gameSettings.keyBindRight.isKeyDown();
    }

    /**
     * Naven {@code PlayerUtils.getMoveSpeedEffectAmplifier} - returns
     * effective amplifier (1-based) of the SPEED potion effect, or 0 if
     * absent.
     */
    private static int getMoveSpeedEffectAmplifier() {
        if (mc.player == null || !mc.player.isPotionActive(Effects.SPEED)) {
            return 0;
        }
        return mc.player.getActivePotionEffect(Effects.SPEED).getAmplifier() + 1;
    }

    /**
     * Naven {@code RotationUtils.randomization} (verbatim) - jitters a
     * coordinate by a small random delta to produce server-believable
     * rotation noise.
     */
    private static double randomization(double value) {
        return value + getRandomDoubleInRange(0.05, 0.08)
                * (getRandomDoubleInRange(0.0, 1.0) * 2.0 - 1.0);
    }

    /**
     * Naven {@code RotationUtils.diffCalc} (verbatim). Computes wrapped
     * yaw/pitch from a delta vector.
     */
    private static Rotation diffCalc(double diffX, double diffY, double diffZ) {
        double diffXZ = Math.sqrt(diffX * diffX + diffZ * diffZ);
        float yaw = (float) Math.toDegrees(Math.atan2(diffZ, diffX)) - 90.0F;
        float pitch = (float) -Math.toDegrees(Math.atan2(diffY, diffXZ));
        return new Rotation(MathHelper.wrapDegrees(yaw), MathHelper.wrapDegrees(pitch));
    }

    /**
     * Naven {@code RotationUtils.getRotations(BlockPos, partialTicks)} -
     * computes rotation from the player's eye to the block center, with
     * randomization jitter applied to each axis. partialTicks=0 in the
     * scaffold's call site, so the player vector is the current pos + eye.
     */
    private static Rotation getRotationsToBlock(BlockPos pos, float partialTicks) {
        Vector3d delta = mc.player.getMotion();
        Vector3d playerVector = new Vector3d(
                mc.player.getPosX() + delta.x * (double) partialTicks,
                mc.player.getPosY() + (double) mc.player.getEyeHeight()
                        + delta.y * (double) partialTicks,
                mc.player.getPosZ() + delta.z * (double) partialTicks);
        double x = (double) pos.getX() - playerVector.x + 0.5;
        double y = (double) pos.getY() - playerVector.y + 0.5;
        double z = (double) pos.getZ() - playerVector.z + 0.5;
        return diffCalc(randomization(x), randomization(y), randomization(z));
    }

    /**
     * Naven {@code RayTraceUtils.calculateViewVector(pXRot, pYRot)} - note
     * that the source intentionally takes (pitch, yaw) and the
     * {@link #rayCast(float, Rotation)} caller passes them in that swapped
     * order.
     */
    private static Vector3d calculateViewVector(float pXRot, float pYRot) {
        float f = pXRot * (float) (Math.PI / 180.0);
        float f1 = -pYRot * (float) (Math.PI / 180.0);
        float f2 = MathHelper.cos(f1);
        float f3 = MathHelper.sin(f1);
        float f4 = MathHelper.cos(f);
        float f5 = MathHelper.sin(f);
        return new Vector3d(f3 * f4, -f5, f2 * f4);
    }

    /**
     * Naven {@code RayTraceUtils.pick(distance, partialTicks, hitFluids, yaw, pitch)}
     * - eye position is hardcoded to {@code playerY + 1.62} just like the
     * source (NOT {@code getEyePosition()} - this matters for sneaking).
     */
    private static BlockRayTraceResult rayCast(float partialTicks, Rotation rotations) {
        if (mc.player == null || mc.world == null) {
            return null;
        }
        double distance = (double) mc.playerController.getBlockReachDistance();
        Vector3d eyes = new Vector3d(
                mc.player.getPosX(),
                mc.player.getPosY() + 1.62,
                mc.player.getPosZ());
        Vector3d look = calculateViewVector(rotations.pitch, rotations.yaw);
        Vector3d end = eyes.add(look.x * distance, look.y * distance, look.z * distance);
        return mc.world.rayTraceBlocks(new RayTraceContext(
                eyes,
                end,
                RayTraceContext.BlockMode.OUTLINE,
                RayTraceContext.FluidMode.ANY,
                mc.player));
    }

    // -----------------------------------------------------------------
    // FoV - direct mirror of Naven's onFoV
    // -----------------------------------------------------------------

    @EventTarget
    public void onFoV(EventGetFovModifier event) {
        if (!this.isEnabled()) {
            return;
        }
        if (this.keepFoV.getCurrentValue() && MovementUtil.isMoving()) {
            event.fovModifier = this.fov.getCurrentValue()
                    + (float) getMoveSpeedEffectAmplifier() * 0.13F;
        }
    }

    // -----------------------------------------------------------------
    // Lifecycle - mirror of Naven's onEnable / onDisable
    // -----------------------------------------------------------------

    @Override
    public void onEnable() {
        super.onEnable();
        if (mc.player == null) {
            return;
        }
        this.oldSlot = mc.player.inventory.currentItem;
        // Naven: rots.set(yRot - 180, xRot) - flips view backward 180 so the
        // server-side raycast points at the block being placed behind us.
        this.rots = new Rotation(mc.player.rotationYaw - 180.0F, mc.player.rotationPitch);
        this.lastRots = new Rotation(mc.player.prevRotationYaw - 180.0F, mc.player.prevRotationPitch);
        this.correctRotation = new Rotation(this.rots.yaw, this.rots.pitch);
        this.pos = null;
        // Naven seeds baseY to 10000 so the first tick reseats it from
        // floor(player.y) - 1.
        this.baseY = 10000;
    }

    @Override
    public void onDisable() {
        super.onDisable();
        if (mc.player == null) {
            return;
        }
        // Naven 1:1: restore real key states by reading what the user is
        // physically holding via OS input (GLFW in 1.16.5 == InputConstants
        // in 1.20.1).
        boolean isHoldingJump = isPhysicallyHeld(mc.gameSettings.keyBindJump);
        boolean isHoldingShift = isPhysicallyHeld(mc.gameSettings.keyBindSneak);
        mc.gameSettings.keyBindJump.setPressed(isHoldingJump);
        mc.gameSettings.keyBindSneak.setPressed(isHoldingShift);
        mc.gameSettings.keyBindUseItem.setPressed(false);
        if (this.oldSlot >= 0 && this.oldSlot < 9) {
            mc.player.inventory.currentItem = this.oldSlot;
        }
    }

    private static boolean isPhysicallyHeld(KeyBinding keyBinding) {
        return GLFW.glfwGetKey(
                mc.getMainWindow().getHandle(),
                keyBinding.keyCode.getKeyCode()) == GLFW.GLFW_PRESS;
    }

    // -----------------------------------------------------------------
    // Render-item spoof - mirror of Naven's onUpdateHeldItem
    // -----------------------------------------------------------------

    @EventTarget
    public void onUpdateHeldItem(EventUpdateHeldItem e) {
        if (!this.isEnabled()
                || !this.renderItemSpoof.getCurrentValue()
                || e.getHand() != Hand.MAIN_HAND
                || mc.player == null
                || this.oldSlot < 0
                || this.oldSlot >= 9) {
            return;
        }
        e.setItem(mc.player.inventory.getStackInSlot(this.oldSlot));
    }

    // -----------------------------------------------------------------
    // Per-tick logic - direct mirror of Naven's onEventEarlyTick
    // (@EventTarget(1) -> @HigherPriority for Sigma's eventBus)
    // -----------------------------------------------------------------

    @EventTarget
    @HigherPriority
    public void onEventEarlyTick(EventRunTicks e) {
        if (!this.isEnabled() || !e.isPre() || mc.currentScreen != null || mc.player == null) {
            return;
        }

        int slotID = -1;
        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.player.inventory.getStackInSlot(i);
            if (stack.getItem() instanceof BlockItem && isValidStack(stack)) {
                slotID = i;
                break;
            }
        }

        if (mc.player.isOnGround()) {
            this.offGroundTicks = 0;
        } else {
            this.offGroundTicks++;
        }

        if (slotID != -1 && mc.player.inventory.currentItem != slotID) {
            mc.player.inventory.currentItem = slotID;
            // 1.16.5 only: explicit slot sync so the server agrees with us
            // before the placement packet goes out. (1.20.1 syncs via the
            // setter directly.)
            mc.playerController.syncCurrentPlayItem();
        }

        boolean isHoldingJump = isPhysicallyHeld(mc.gameSettings.keyBindJump);

        // baseY locking - exact 6-clause OR predicate from the source.
        if (this.baseY == -1
                || this.baseY > (int) Math.floor(mc.player.getPosY()) - 1
                || mc.player.isOnGround()
                || !movementInput()
                || isHoldingJump
                || this.modeIs("Normal")) {
            this.baseY = (int) Math.floor(mc.player.getPosY()) - 1;
        }

        this.getBlockPos();
        if (this.pos != null) {
            this.correctRotation = this.getPlayerYawRotation();
            if (this.modeIs("Normal") && this.snap.getCurrentValue()) {
                this.rots.yaw = this.correctRotation.yaw;
            } else {
                this.rots.yaw = RotationUtils.updateRotation(
                        this.rots.yaw, this.correctRotation.yaw, 180.0F);
            }
            this.rots.pitch = this.correctRotation.pitch;
        }

        // Sneak pulse cycle: hold sneak from tick 18 through 20, release at
        // tick 21, reset. Source also kills sprint at the rising edge.
        if (this.sneakSetting.getCurrentValue()) {
            this.lastSneakTicks++;
            if (this.lastSneakTicks == 18) {
                if (mc.player.isSprinting()) {
                    mc.gameSettings.keyBindSprint.setPressed(false);
                    mc.player.setSprinting(false);
                }
                mc.gameSettings.keyBindSneak.setPressed(true);
            } else if (this.lastSneakTicks >= 21) {
                mc.gameSettings.keyBindSneak.setPressed(false);
                this.lastSneakTicks = 0;
            }
        }

        if (this.modeIs("Telly Bridge")) {
            mc.gameSettings.keyBindJump.setPressed(movementInput() || isHoldingJump);
            if (this.offGroundTicks < 1 && movementInput()) {
                // Telly Bridge ground-frame: smooth toward the player's real
                // yaw rather than the back-place yaw, so the takeoff jump
                // follows the camera.
                this.rots.yaw = RotationUtils.updateRotation(
                        this.rots.yaw, mc.player.rotationYaw, 180.0F);
                this.lastRots = new Rotation(this.rots.yaw, this.rots.pitch);
                this.publishVisibleRotation();
                return;
            }
        } else if (this.modeIs("Keep Y")) {
            mc.gameSettings.keyBindJump.setPressed(movementInput() || isHoldingJump);
        } else {
            // Normal mode
            if (this.eagle.getCurrentValue()) {
                mc.gameSettings.keyBindSneak.setPressed(
                        mc.player.isOnGround() && isOnBlockEdge(0.3F));
            }
            if (this.snap.getCurrentValue() && !isHoldingJump) {
                this.doSnap();
            }
        }

        this.lastRots = new Rotation(this.rots.yaw, this.rots.pitch);
        this.publishVisibleRotation();
    }

    /**
     * Pushes the visible rotation into the global rotation pipeline so the
     * outbound EventMotion picks it up. Uses {@code correctRotation} when
     * "Hide Snap Rotation" is on (so the snap jitter never reaches the
     * first-person camera) - this matches Naven's RotationManager.onPre
     * special-case for Scaffold.
     */
    private void publishVisibleRotation() {
        Rotation visible = this.hideSnap.getCurrentValue()
                && this.modeIs("Normal")
                && this.snap.getCurrentValue()
                ? this.correctRotation
                : this.rots;
        RotationManager.setRotations(visible.yaw, visible.pitch);
    }

    /**
     * Direct mirror of Naven's {@code doSnap}. Casts a ray with the current
     * rots; if it already lands on the target block (and not its top face),
     * keep yaw - otherwise jitter yaw by player.yaw + random[-0.25, +0.25].
     */
    private void doSnap() {
        if (this.pos == null) {
            return;
        }
        boolean shouldPlaceBlock = false;
        BlockRayTraceResult objectPosition = rayCast(1.0F, this.rots);
        if (objectPosition != null
                && objectPosition.getType() == RayTraceResult.Type.BLOCK
                && objectPosition.getPos().equals(this.pos.position())
                && objectPosition.getFace() != Direction.UP) {
            shouldPlaceBlock = true;
        }
        if (!shouldPlaceBlock) {
            this.rots.yaw = mc.player.rotationYaw
                    + RandomUtils.nextFloat(0.0F, 0.5F) - 0.25F;
        }
    }

    // -----------------------------------------------------------------
    // EventMotion - inject the actual yaw/pitch into the outbound packet.
    // EventMoveInput - keep the strafe direction consistent with rots.yaw.
    // -----------------------------------------------------------------

    @EventTarget
    @LowestPriority
    public void onMotion(EventMotion event) {
        if (!this.isEnabled() || !event.isPre() || mc.player == null) {
            return;
        }
        // Always send the back-place yaw to the server, regardless of what
        // the visible (hideSnap) rotation is.
        event.setYaw(this.rots.yaw);
        event.setPitch(this.rots.pitch);
        event.setMoving(true);
    }

    // EventMoveInput is handled by Sigma's RotationManager.onInput - it
    // already routes the standalone Scaffold's rots through
    // MovementUtil.fixMovement (see RotationManager.getMovementFixYaw and
    // RotationManager.onInput). No per-module hook needed.

    // -----------------------------------------------------------------
    // Click cancel / placement - direct mirror of Naven's onClick.
    // EventRunTicks PRE has already run by the time this fires (because we
    // dispatch it from runTick HEAD), so this.pos / this.rots are fresh.
    // -----------------------------------------------------------------

    @EventTarget
    public void onClick(EventClick e) {
        if (!this.isEnabled()) {
            return;
        }
        // Naven cancels EventClick unconditionally - we match for the RIGHT
        // button only (Sigma's EventClick is per-button, Naven's isn't).
        if (e.getButton() != EventClick.Button.RIGHT) {
            return;
        }
        e.cancelled = true;
        if (mc.currentScreen != null || mc.player == null || this.pos == null) {
            return;
        }
        if (this.modeIs("Telly Bridge") && this.offGroundTicks < 1) {
            return;
        }
        if (!this.checkPlace(this.pos)) {
            return;
        }
        this.placeBlock();
    }

    /**
     * Naven {@code Scaffold.checkPlace} (verbatim): reach <= 4.5 and the
     * placement face must be facing the player.
     */
    private boolean checkPlace(BlockPosWithFacing data) {
        Vector3d center = new Vector3d(
                (double) data.position.getX() + 0.5,
                (double) ((float) data.position.getY() + 0.5F),
                (double) data.position.getZ() + 0.5);
        Vector3i facingNormal = data.facing.getDirectionVec();
        Vector3d hit = center.add(
                (double) facingNormal.getX() * 0.5,
                (double) facingNormal.getY() * 0.5,
                (double) facingNormal.getZ() * 0.5);
        Vector3d relevant = hit.subtract(mc.player.getEyePosition(1.0F));
        if (relevant.lengthSquared() > 20.25) {
            return false;
        }
        Direction opposite = data.facing.getOpposite();
        Vector3i oppositeNormal = opposite.getDirectionVec();
        Vector3d oppositeVec = new Vector3d(
                oppositeNormal.getX(), oppositeNormal.getY(), oppositeNormal.getZ());
        return relevant.normalize().dotProduct(oppositeVec.normalize()) >= 0.0;
    }

    /**
     * Naven {@code Scaffold.placeBlock} (verbatim algorithm):
     * <ul>
     *   <li>Skip placement if mid-jump moving on a UP face in non-Normal modes.</li>
     *   <li>{@code shouldBuild()} guards against placing into a non-air spot.</li>
     *   <li>Use {@code mc.gameMode.useItemOn(...)} (1.16.5: {@code playerController.func_217292_a}).</li>
     *   <li>On SUCCESS, swing arm and clear pos.</li>
     * </ul>
     */
    private void placeBlock() {
        if (this.pos == null || !isValidStack(mc.player.getHeldItemMainhand())) {
            return;
        }
        Direction sbFace = this.pos.facing;
        if (sbFace == null) {
            return;
        }
        boolean isHoldingJump = isPhysicallyHeld(mc.gameSettings.keyBindJump);
        // Source predicate: place if NOT (face=UP and airborne and moving and
        // not jumping and not Normal). Re-expressed as a positive check.
        boolean upGate = sbFace != Direction.UP
                || mc.player.isOnGround()
                || !movementInput()
                || isHoldingJump
                || this.modeIs("Normal");
        if (!upGate || !this.shouldBuild()) {
            return;
        }
        BlockRayTraceResult hit = new BlockRayTraceResult(
                getVec3(this.pos.position(), sbFace),
                sbFace,
                this.pos.position(),
                false);
        ActionResultType result = mc.playerController.func_217292_a(
                mc.player, mc.world, Hand.MAIN_HAND, hit);
        if (result == ActionResultType.SUCCESS) {
            mc.player.swingArm(Hand.MAIN_HAND);
            this.pos = null;
        }
    }

    /**
     * Naven {@code Scaffold.getPlayerYawRotation} - just delegates to
     * {@link #getRotationsToBlock(BlockPos, float)} with partialTicks=0.
     */
    private Rotation getPlayerYawRotation() {
        if (mc.player == null || this.pos == null) {
            return new Rotation(0.0F, 0.0F);
        }
        Rotation r = getRotationsToBlock(this.pos.position(), 0.0F);
        return new Rotation(r.yaw, r.pitch);
    }

    private boolean shouldBuild() {
        BlockPos playerPos = new BlockPos(
                mc.player.getPosX(),
                mc.player.getPosY() - 0.5,
                mc.player.getPosZ());
        return mc.world.isAirBlock(playerPos)
                && isValidStack(mc.player.getHeldItemMainhand());
    }

    // -----------------------------------------------------------------
    // Block search - 6-layer pyramid expansion (Naven verbatim)
    // -----------------------------------------------------------------

    private void getBlockPos() {
        Vector3d baseVec = mc.player.getEyePosition(1.0F)
                .add(mc.player.getMotion().mul(2.0, 2.0, 2.0));

        if (mc.player.getMotion().y < 0.01) {
            FallingPlayer fallingPlayer = new FallingPlayer(mc.player);
            fallingPlayer.calculate(2);
            baseVec = new Vector3d(
                    baseVec.x,
                    Math.max(fallingPlayer.y + (double) mc.player.getEyeHeight(), baseVec.y),
                    baseVec.z);
        }

        BlockPos base = new BlockPos(baseVec.x, (double) ((float) this.baseY + 0.1F), baseVec.z);
        int baseX = base.getX();
        int baseZ = base.getZ();

        if (entityCanStandOn(base)) {
            return;
        }
        if (this.checkBlock(baseVec, base)) {
            return;
        }
        for (int d = 1; d <= 6; d++) {
            if (this.checkBlock(baseVec, new BlockPos(baseX, this.baseY - d, baseZ))) {
                return;
            }
            for (int x = 1; x <= d; x++) {
                for (int z = 0; z <= d - x; z++) {
                    int y = d - x - z;
                    for (int rev1 = 0; rev1 <= 1; rev1++) {
                        for (int rev2 = 0; rev2 <= 1; rev2++) {
                            if (this.checkBlock(baseVec, new BlockPos(
                                    baseX + (rev1 == 0 ? x : -x),
                                    this.baseY - y,
                                    baseZ + (rev2 == 0 ? z : -z)))) {
                                return;
                            }
                        }
                    }
                }
            }
        }
    }

    private boolean checkBlock(Vector3d baseVec, BlockPos bp) {
        if (!(mc.world.getBlockState(bp).getBlock() instanceof AirBlock)) {
            return false;
        }
        Vector3d center = new Vector3d(
                (double) bp.getX() + 0.5,
                (double) ((float) bp.getY() + 0.5F),
                (double) bp.getZ() + 0.5);
        for (Direction sbface : Direction.values()) {
            Vector3i normal = sbface.getDirectionVec();
            Vector3d hit = center.add(
                    (double) normal.getX() * 0.5,
                    (double) normal.getY() * 0.5,
                    (double) normal.getZ() * 0.5);
            BlockPos po = new BlockPos(
                    bp.getX() + normal.getX(),
                    bp.getY() + normal.getY(),
                    bp.getZ() + normal.getZ());
            if (canStandOnFace(po, sbface)) {
                Vector3d relevant = hit.subtract(baseVec);
                Vector3d normalVec = new Vector3d(normal.getX(), normal.getY(), normal.getZ());
                if (relevant.lengthSquared() <= 20.25
                        && relevant.normalize().dotProduct(normalVec.normalize()) >= 0.0) {
                    this.pos = new BlockPosWithFacing(new BlockPos(po), sbface.getOpposite());
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * 1.16.5 stand-in for 1.20.1's {@code BlockState#entityCanStandOn} -
     * non-air, non-replaceable, top face solid enough to support an entity.
     */
    private static boolean entityCanStandOn(BlockPos pos) {
        BlockState state = mc.world.getBlockState(pos);
        if (state.getBlock() instanceof AirBlock) {
            return false;
        }
        if (state.getMaterial().isReplaceable()) {
            return false;
        }
        return Block.hasEnoughSolidSide(mc.world, pos, Direction.UP);
    }

    /**
     * 1.16.5 stand-in for 1.20.1's
     * {@code BlockState#entityCanStandOnFace(level, pos, entity, face)}.
     */
    private static boolean canStandOnFace(BlockPos pos, Direction face) {
        BlockState state = mc.world.getBlockState(pos);
        if (state.getBlock() instanceof AirBlock) {
            return false;
        }
        if (state.getMaterial().isReplaceable()) {
            return false;
        }
        return Block.hasEnoughSolidSide(mc.world, pos, face);
    }

    /**
     * Naven {@code Scaffold.getVec3} (verbatim) - constructs the BlockHitResult
     * hit position with controlled randomness so the placement vector looks
     * believable to anti-cheats.
     */
    public static Vector3d getVec3(BlockPos pos, Direction face) {
        double x = (double) pos.getX() + 0.5;
        double y = (double) pos.getY() + 0.5;
        double z = (double) pos.getZ() + 0.5;
        if (face != Direction.UP && face != Direction.DOWN) {
            y += 0.08;
        } else {
            x += getRandomDoubleInRange(0.3, -0.3);
            z += getRandomDoubleInRange(0.3, -0.3);
        }
        if (face == Direction.WEST || face == Direction.EAST) {
            z += getRandomDoubleInRange(0.3, -0.3);
        }
        if (face == Direction.SOUTH || face == Direction.NORTH) {
            x += getRandomDoubleInRange(0.3, -0.3);
        }
        return new Vector3d(x, y, z);
    }

    private boolean modeIs(String name) {
        return name.equals(this.modeSetting.getCurrentValue());
    }

    /** Direct equivalent of Naven's {@code Scaffold.BlockPosWithFacing}. */
    public static record BlockPosWithFacing(BlockPos position, Direction facing) {
    }
}
