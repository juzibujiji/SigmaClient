package com.elfmcys.yesstevemodel.client.animation.molang;

import com.elfmcys.yesstevemodel.YesSteveModel;
import com.elfmcys.yesstevemodel.client.OpenYsmBone;
import com.elfmcys.yesstevemodel.client.animation.PlayerStateSnapshot;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ActiveRenderInfo;
import net.minecraft.client.settings.PointOfView;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.tags.ITag;
import net.minecraft.tags.TagCollectionManager;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.registry.Registry;

import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class MolangContext {
    private static final ThreadLocal<Map<String, OpenYsmBone>> BONE_SCOPE = new ThreadLocal<>();

    private final PlayerStateSnapshot snapshot;
    private final String modelId;
    private final String controllerName;
    private final MolangBindings bindings;
    private final boolean allAnimationsFinished;
    private final boolean anyAnimationFinished;
    private final boolean playingExtraAnimation;
    private final double controllerAnimTimeSeconds;

    private MolangContext(PlayerStateSnapshot snapshot, String modelId, String controllerName, MolangBindings bindings,
                          boolean allAnimationsFinished, boolean anyAnimationFinished,
                          boolean playingExtraAnimation, double controllerAnimTimeSeconds) {
        this.snapshot = snapshot;
        this.modelId = modelId == null ? "" : modelId;
        this.controllerName = controllerName == null ? "" : controllerName;
        this.bindings = bindings == null ? MolangBindings.EMPTY : bindings;
        this.allAnimationsFinished = allAnimationsFinished;
        this.anyAnimationFinished = anyAnimationFinished;
        this.playingExtraAnimation = playingExtraAnimation;
        this.controllerAnimTimeSeconds = Math.max(0.0D, controllerAnimTimeSeconds);
    }

    public static MolangContext controller(PlayerStateSnapshot snapshot, String modelId, String controllerName) {
        return controller(snapshot, modelId, controllerName, MolangBindings.EMPTY, false, false);
    }

    public static MolangContext controller(PlayerStateSnapshot snapshot, String modelId, String controllerName,
                                           MolangBindings bindings, boolean allAnimationsFinished,
                                           boolean anyAnimationFinished) {
        return controller(snapshot, modelId, controllerName, bindings, allAnimationsFinished, anyAnimationFinished,
                false, snapshot == null ? 0.0D : snapshot.ageInTicks / 20.0D);
    }

    public static MolangContext controller(PlayerStateSnapshot snapshot, String modelId, String controllerName,
                                           MolangBindings bindings, boolean allAnimationsFinished,
                                           boolean anyAnimationFinished, boolean playingExtraAnimation,
                                           double controllerAnimTimeSeconds) {
        return new MolangContext(snapshot, modelId, controllerName, bindings, allAnimationsFinished, anyAnimationFinished,
                playingExtraAnimation, controllerAnimTimeSeconds);
    }

    public static AutoCloseable pushBones(Map<String, OpenYsmBone> bones) {
        Map<String, OpenYsmBone> previous = BONE_SCOPE.get();
        if (bones == null || bones.isEmpty()) {
            BONE_SCOPE.remove();
        } else {
            BONE_SCOPE.set(bones);
        }
        return () -> {
            if (previous == null) {
                BONE_SCOPE.remove();
            } else {
                BONE_SCOPE.set(previous);
            }
        };
    }

    public MolangValue resolveIdentifier(String identifier) {
        String key = identifier == null ? "" : identifier.toLowerCase(Locale.ROOT);
        if (key.startsWith("ctrl.")) {
            return MolangValue.of(ctrlValue(key.substring("ctrl.".length())));
        }
        if (key.startsWith("query.") || key.startsWith("q.")) {
            int prefixLength = key.startsWith("query.") ? "query.".length() : "q.".length();
            return MolangValue.of(queryValue(key.substring(prefixLength)));
        }
        if (key.startsWith("ysm.")) {
            return ysmValue(key.substring("ysm.".length()));
        }
        if (key.startsWith("variable.") || key.startsWith("v.")) {
            int prefixLength = key.startsWith("variable.") ? "variable.".length() : "v.".length();
            String name = key.substring(prefixLength);
            if (!this.bindings.hasVariable(name)) {
                debugUnknown("variable", name);
            }
            return MolangValue.of(this.bindings.variable(name));
        }
        if (key.startsWith("temp.") || key.startsWith("t.")) {
            int prefixLength = key.startsWith("temp.") ? "temp.".length() : "t.".length();
            String name = key.substring(prefixLength);
            if (!this.bindings.hasTemp(name)) {
                debugUnknown("temp", name);
            }
            return MolangValue.of(this.bindings.temp(name));
        }
        debugUnknown("identifier", key);
        return MolangValue.ZERO;
    }

    public MolangValue callFunction(String name, List<MolangValue> args) {
        String key = name == null ? "" : name.toLowerCase(Locale.ROOT);
        List<MolangValue> safeArgs = args == null ? List.of() : args;
        return switch (key) {
            case "query.position", "q.position" -> MolangValue.of(positionFunction(safeArgs));
            case "query.position_delta", "q.position_delta" -> MolangValue.of(positionDeltaFunction(safeArgs));
            case "query.rotation_to_camera", "q.rotation_to_camera" -> MolangValue.of(rotationToCameraFunction(safeArgs));
            case "query.is_item_name_any", "q.is_item_name_any" -> MolangValue.of(isItemNameAnyFunction(safeArgs));
            case "ysm.bone_rot" -> boneRotationFunction(safeArgs);
            case "ysm.bone_pos" -> bonePositionFunction(safeArgs);
            case "ysm.bone_scale" -> boneScaleFunction(safeArgs);
            case "ysm.bone_pivot_abs" -> bonePivotAbsFunction(safeArgs);
            case "ysm.first_order" -> MolangValue.of(firstOrderFunction(safeArgs));
            case "ysm.second_order" -> MolangValue.of(secondOrderFunction(safeArgs));
            default -> {
                debugUnknown("function", key);
                yield MolangValue.ZERO;
            }
        };
    }

    private double ctrlValue(String key) {
        if (this.snapshot == null) {
            return 0.0D;
        }
        Double filteredValue = filteredCtrlValue(key);
        if (filteredValue != null) {
            return filteredValue;
        }
        return switch (key) {
            case "idle" -> this.snapshot.isMoving() ? 0.0D : 1.0D;
            case "walk", "moving" -> this.snapshot.isMoving() && !this.snapshot.sprinting && !this.snapshot.sneaking ? 1.0D : 0.0D;
            case "run", "sprint", "sprinting" -> this.snapshot.sprinting && this.snapshot.isMoving() ? 1.0D : 0.0D;
            case "sneak", "sneaking" -> this.snapshot.sneaking ? 1.0D : 0.0D;
            case "jump", "air", "in_air" -> !this.snapshot.onGround && !this.snapshot.swimming && !this.snapshot.elytraFlying ? 1.0D : 0.0D;
            case "fly", "flying" -> this.snapshot.creativeFlying ? 1.0D : 0.0D;
            case "elytra", "elytra_fly", "elytra_flying" -> this.snapshot.elytraFlying ? 1.0D : 0.0D;
            case "swim", "swimming" -> this.snapshot.swimming ? 1.0D : 0.0D;
            case "swim_stand" -> this.snapshot.inWater && !this.snapshot.swimming ? 1.0D : 0.0D;
            case "water", "in_water" -> this.snapshot.inWater ? 1.0D : 0.0D;
            case "death", "dead" -> this.snapshot.dead ? 1.0D : 0.0D;
            case "use", "using_item" -> this.snapshot.usingItem ? 1.0D : 0.0D;
            case "using_mainhand" -> this.snapshot.usingItem && this.snapshot.usingHand == Hand.MAIN_HAND ? 1.0D : 0.0D;
            case "using_offhand" -> this.snapshot.usingItem && this.snapshot.usingHand == Hand.OFF_HAND ? 1.0D : 0.0D;
            case "swing", "swinging" -> this.snapshot.swingInProgress ? 1.0D : 0.0D;
            case "swing_mainhand" -> this.snapshot.swingInProgress && this.snapshot.swingingHand == Hand.MAIN_HAND ? 1.0D : 0.0D;
            case "swing_offhand" -> this.snapshot.swingInProgress && this.snapshot.swingingHand == Hand.OFF_HAND ? 1.0D : 0.0D;
            case "hold_mainhand" -> this.snapshot.mainhandEmpty ? 0.0D : 1.0D;
            case "hold_offhand" -> this.snapshot.offhandEmpty ? 0.0D : 1.0D;
            case "attacked" -> this.snapshot.attacked ? 1.0D : 0.0D;
            case "playing_extra_animation" -> this.playingExtraAnimation ? 1.0D : 0.0D;
            case "carryon_is_princess", "tac_hold_gun" -> 0.0D;
            default -> {
                debugUnknown("ctrl", key);
                yield 0.0D;
            }
        };
    }

    private Double filteredCtrlValue(String key) {
        Double mainUsing = filteredCtrlValue(key, "using_mainhand_", Hand.MAIN_HAND,
                this.snapshot.usingItem && this.snapshot.usingHand == Hand.MAIN_HAND);
        if (mainUsing != null) {
            return mainUsing;
        }
        Double offUsing = filteredCtrlValue(key, "using_offhand_", Hand.OFF_HAND,
                this.snapshot.usingItem && this.snapshot.usingHand == Hand.OFF_HAND);
        if (offUsing != null) {
            return offUsing;
        }
        Double mainSwing = filteredCtrlValue(key, "swing_mainhand_", Hand.MAIN_HAND,
                this.snapshot.swingInProgress && this.snapshot.swingingHand == Hand.MAIN_HAND);
        if (mainSwing != null) {
            return mainSwing;
        }
        Double offSwing = filteredCtrlValue(key, "swing_offhand_", Hand.OFF_HAND,
                this.snapshot.swingInProgress && this.snapshot.swingingHand == Hand.OFF_HAND);
        if (offSwing != null) {
            return offSwing;
        }
        Double mainHold = filteredCtrlValue(key, "hold_mainhand_", Hand.MAIN_HAND, !this.snapshot.mainhandEmpty);
        if (mainHold != null) {
            return mainHold;
        }
        return filteredCtrlValue(key, "hold_offhand_", Hand.OFF_HAND, !this.snapshot.offhandEmpty);
    }

    private Double filteredCtrlValue(String key, String prefix, Hand hand, boolean active) {
        if (!key.startsWith(prefix)) {
            return null;
        }
        if (!active) {
            return 0.0D;
        }
        String filter = key.substring(prefix.length());
        return matchesItemFilter(this.snapshot.handStack(hand), filter) ? 1.0D : 0.0D;
    }

    private static boolean matchesItemFilter(ItemStack stack, String filter) {
        if (stack == null || stack.isEmpty() || filter == null || filter.isEmpty()) {
            return false;
        }
        if (filter.startsWith("suffix_")) {
            return matchesItemSuffix(stack, filter.substring("suffix_".length()));
        }
        if (filter.startsWith("item_")) {
            return matchesItemExact(stack, filter.substring("item_".length()));
        }
        if (filter.startsWith("tag_")) {
            return matchesItemTag(stack, filter.substring("tag_".length()));
        }
        return false;
    }

    private static boolean matchesItemSuffix(ItemStack stack, String encodedPath) {
        ResourceLocation id = Registry.ITEM.getKey(stack.getItem());
        if (id == null) {
            return false;
        }
        String path = id.getPath().toLowerCase(Locale.ROOT);
        String expected = decodeFilterToken(encodedPath);
        return path.equals(expected) || path.endsWith("_" + expected) || path.endsWith(expected);
    }

    private static boolean matchesItemExact(ItemStack stack, String encodedId) {
        ResourceLocation id = Registry.ITEM.getKey(stack.getItem());
        if (id == null) {
            return false;
        }
        String expected = decodeFilterToken(encodedId);
        String actual = id.toString().toLowerCase(Locale.ROOT);
        return actual.equals(expected) || actual.equals(expected.replace('_', ':'));
    }

    private static boolean matchesItemTag(ItemStack stack, String encodedTag) {
        String expected = decodeFilterToken(encodedTag);
        ResourceLocation tagId = parseResourceLocation(expected);
        if (tagId != null) {
            try {
                ITag<Item> tag = TagCollectionManager.getManager().getItemTags().get(tagId);
                if (tag != null && tag.contains(stack.getItem())) {
                    return true;
                }
            } catch (RuntimeException ignored) {
            }
        }
        return matchesCommonItemTag(stack.getItem(), expected);
    }

    private static boolean matchesCommonItemTag(Item item, String tagId) {
        String path = tagId.contains(":") ? tagId.substring(tagId.indexOf(':') + 1) : tagId;
        if ("swords".equals(path)) {
            return item == Items.WOODEN_SWORD || item == Items.STONE_SWORD || item == Items.IRON_SWORD
                    || item == Items.GOLDEN_SWORD || item == Items.DIAMOND_SWORD || item == Items.NETHERITE_SWORD;
        }
        if ("bows".equals(path)) {
            return item == Items.BOW || item == Items.CROSSBOW;
        }
        if ("shields".equals(path)) {
            return item == Items.SHIELD;
        }
        return false;
    }

    private static ResourceLocation parseResourceLocation(String value) {
        try {
            if (value == null || value.isEmpty()) {
                return null;
            }
            return value.contains(":") ? new ResourceLocation(value) : new ResourceLocation("minecraft", value);
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private static String decodeFilterToken(String value) {
        if (value == null) {
            return "";
        }
        String decoded = value.toLowerCase(Locale.ROOT);
        int first = decoded.indexOf('_');
        if (first > 0) {
            String namespace = decoded.substring(0, first);
            String path = decoded.substring(first + 1);
            if ("minecraft".equals(namespace)) {
                return namespace + ":" + path;
            }
        }
        return decoded;
    }

    private double queryValue(String key) {
        if (this.snapshot == null) {
            return 0.0D;
        }
        Double relativeBlockTag = relativeBlockTagQueryValue(key);
        if (relativeBlockTag != null) {
            return relativeBlockTag;
        }
        return switch (key) {
            case "life_time" -> this.snapshot.ageInTicks / 20.0D;
            case "anim_time" -> this.controllerAnimTimeSeconds;
            case "head_x_rotation" -> this.snapshot.headYaw;
            case "head_y_rotation" -> this.snapshot.headPitch;
            case "time_stamp" -> this.snapshot.world == null ? 0.0D : this.snapshot.world.getDayTime();
            case "time_of_day" -> this.snapshot.world == null ? 0.0D : normalizeTimeOfDay(this.snapshot.world.getDayTime());
            case "moon_phase" -> this.snapshot.world == null ? 0.0D : this.snapshot.world.getMoonPhase();
            case "delta_time" -> 1.0D / 20.0D;
            case "is_first_person" -> isFirstPerson() ? 1.0D : 0.0D;
            case "modified_move_speed" -> Math.abs(this.snapshot.limbSwingAmount);
            case "ground_speed" -> Math.abs(this.snapshot.limbSwingAmount);
            case "cardinal_facing_2d" -> this.snapshot.cardinalFacing2d;
            case "is_on_ground" -> this.snapshot.onGround ? 1.0D : 0.0D;
            case "is_sneaking" -> this.snapshot.sneaking ? 1.0D : 0.0D;
            case "is_sprinting" -> this.snapshot.sprinting ? 1.0D : 0.0D;
            case "is_jumping" -> !this.snapshot.onGround && !this.snapshot.swimming && !this.snapshot.elytraFlying ? 1.0D : 0.0D;
            case "is_in_water" -> this.snapshot.inWater ? 1.0D : 0.0D;
            case "is_swimming" -> this.snapshot.swimming ? 1.0D : 0.0D;
            case "is_flying" -> this.snapshot.creativeFlying ? 1.0D : 0.0D;
            case "is_elytra_flying" -> this.snapshot.elytraFlying ? 1.0D : 0.0D;
            case "is_alive" -> this.snapshot.dead ? 0.0D : 1.0D;
            case "is_dead" -> this.snapshot.dead ? 1.0D : 0.0D;
            case "is_attacked", "is_hurt" -> this.snapshot.attacked ? 1.0D : 0.0D;
            case "is_using_item" -> this.snapshot.usingItem ? 1.0D : 0.0D;
            case "is_using_mainhand" -> this.snapshot.usingItem && this.snapshot.usingHand == Hand.MAIN_HAND ? 1.0D : 0.0D;
            case "is_using_offhand" -> this.snapshot.usingItem && this.snapshot.usingHand == Hand.OFF_HAND ? 1.0D : 0.0D;
            case "all_animations_finished" -> this.allAnimationsFinished ? 1.0D : 0.0D;
            case "any_animation_finished" -> this.anyAnimationFinished ? 1.0D : 0.0D;
            default -> {
                debugUnknown("query", key);
                yield 0.0D;
            }
        };
    }

    private Double relativeBlockTagQueryValue(String key) {
        String prefix = "relative_block_has_any_tag_";
        if (!key.startsWith(prefix)) {
            return null;
        }
        if (this.snapshot.world == null) {
            return 0.0D;
        }
        String body = key.substring(prefix.length());
        int yIndex = body.indexOf("_y");
        int zIndex = body.indexOf("_z", yIndex + 2);
        int tagIndex = body.indexOf("_tag_", zIndex + 2);
        if (!body.startsWith("x") || yIndex < 0 || zIndex < 0 || tagIndex < 0) {
            return 0.0D;
        }
        double dx = decodeNumberToken(body.substring(1, yIndex));
        double dy = decodeNumberToken(body.substring(yIndex + 2, zIndex));
        double dz = decodeNumberToken(body.substring(zIndex + 2, tagIndex));
        String tag = decodeFilterToken(body.substring(tagIndex + "_tag_".length()));
        BlockPos pos = new BlockPos(MathHelper.floor(this.snapshot.posX + dx),
                MathHelper.floor(this.snapshot.posY + dy),
                MathHelper.floor(this.snapshot.posZ + dz));
        BlockState state = this.snapshot.world.getBlockState(pos);
        return matchesBlockTag(state, tag) ? 1.0D : 0.0D;
    }

    private static boolean matchesBlockTag(BlockState state, String tag) {
        if (state == null || tag == null || tag.isEmpty()) {
            return false;
        }
        String path = tag.contains(":") ? tag.substring(tag.indexOf(':') + 1) : tag;
        if ("replaceable".equals(path)) {
            return state.isAir() || state.getMaterial().isReplaceable();
        }
        ResourceLocation tagId = parseResourceLocation(tag);
        if (tagId != null) {
            try {
                ITag<Block> blockTag = TagCollectionManager.getManager().getBlockTags().get(tagId);
                return blockTag != null && state.isIn(blockTag);
            } catch (RuntimeException ignored) {
            }
        }
        return false;
    }

    private static double decodeNumberToken(String token) {
        if (token == null || token.isEmpty()) {
            return 0.0D;
        }
        String value = token.replace('m', '-').replace('p', '+').replace('d', '.');
        try {
            return Math.max(-16.0D, Math.min(16.0D, Double.parseDouble(value)));
        } catch (NumberFormatException ignored) {
            return 0.0D;
        }
    }

    private MolangValue ysmValue(String key) {
        if (this.snapshot == null) {
            return MolangValue.ZERO;
        }
        return switch (key) {
            case "head_yaw" -> MolangValue.of(this.snapshot.headYaw);
            case "head_pitch" -> MolangValue.of(this.snapshot.headPitch);
            case "texture_name" -> MolangValue.of(selectedTextureName());
            case "input_vertical", "move_forward" -> MolangValue.of(this.snapshot.inputVertical);
            case "input_horizontal", "move_strafe" -> MolangValue.of(this.snapshot.inputHorizontal);
            case "jump", "input_jump" -> MolangValue.of(this.snapshot.inputJumping);
            case "food_level" -> MolangValue.of(this.snapshot.foodLevel);
            case "person_view" -> MolangValue.of(cameraPointOfViewOrdinal());
            case "rendering_in_inventory" -> MolangValue.of(!isFirstPerson());
            case "is_pause", "is_spectator" -> MolangValue.ZERO;
            default -> {
                debugUnknown("ysm", key);
                yield MolangValue.ZERO;
            }
        };
    }

    private double positionFunction(List<MolangValue> args) {
        if (this.snapshot == null || args.size() != 1) {
            return 0.0D;
        }
        return switch ((int) args.get(0).asDouble()) {
            case 0 -> this.snapshot.posX;
            case 1 -> this.snapshot.posY;
            case 2 -> this.snapshot.posZ;
            default -> 0.0D;
        };
    }

    private double positionDeltaFunction(List<MolangValue> args) {
        if (this.snapshot == null || args.size() != 1) {
            return 0.0D;
        }
        return switch ((int) args.get(0).asDouble()) {
            case 0 -> this.snapshot.deltaX;
            case 1 -> this.snapshot.deltaY;
            case 2 -> this.snapshot.deltaZ;
            default -> 0.0D;
        };
    }

    private double rotationToCameraFunction(List<MolangValue> args) {
        if (args.size() != 1) {
            return 0.0D;
        }
        ActiveRenderInfo camera = Minecraft.getInstance().gameRenderer == null
                ? null : Minecraft.getInstance().gameRenderer.getActiveRenderInfo();
        if (camera == null) {
            return 0.0D;
        }
        return switch ((int) args.get(0).asDouble()) {
            case 0 -> -camera.getPitch();
            case 1 -> 180.0D + camera.getYaw();
            default -> 0.0D;
        };
    }

    private boolean isItemNameAnyFunction(List<MolangValue> args) {
        if (this.snapshot == null || args.size() != 2) {
            return false;
        }
        ItemStack stack = switch (args.get(0).asString().toLowerCase(Locale.ROOT)) {
            case "mainhand", "main_hand", "main" -> this.snapshot.mainhand;
            case "offhand", "off_hand", "off" -> this.snapshot.offhand;
            default -> ItemStack.EMPTY;
        };
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        ResourceLocation id = Registry.ITEM.getKey(stack.getItem());
        return id != null && id.toString().equals(args.get(1).asString().toLowerCase(Locale.ROOT));
    }

    private MolangValue boneRotationFunction(List<MolangValue> args) {
        OpenYsmBone bone = scopedBone(args);
        if (bone == null) {
            return MolangValue.ZERO;
        }
        return MolangValue.ofVector(-Math.toDegrees(bone.getRotationX()),
                -Math.toDegrees(bone.getRotationY()),
                Math.toDegrees(bone.getRotationZ()));
    }

    private MolangValue bonePositionFunction(List<MolangValue> args) {
        OpenYsmBone bone = scopedBone(args);
        if (bone == null) {
            return MolangValue.ZERO;
        }
        return MolangValue.ofVector(bone.getPositionX(), bone.getPositionY(), bone.getPositionZ());
    }

    private MolangValue boneScaleFunction(List<MolangValue> args) {
        OpenYsmBone bone = scopedBone(args);
        if (bone == null) {
            return MolangValue.ZERO;
        }
        return MolangValue.ofVector(bone.getScaleX(), bone.getScaleY(), bone.getScaleZ());
    }

    private MolangValue bonePivotAbsFunction(List<MolangValue> args) {
        OpenYsmBone bone = scopedBone(args);
        if (bone == null) {
            return MolangValue.ZERO;
        }
        double x = 0.0D;
        double y = 0.0D;
        double z = 0.0D;
        OpenYsmBone cursor = bone;
        int guard = 0;
        while (cursor != null && guard++ < 128) {
            x += cursor.getPositionX();
            y += cursor.getPositionY();
            z += cursor.getPositionZ();
            cursor = cursor.getParent();
        }
        return MolangValue.ofVector(x, y, z);
    }

    private OpenYsmBone scopedBone(List<MolangValue> args) {
        if (args == null || args.size() != 1) {
            return null;
        }
        String boneName = args.get(0).asString();
        if (boneName == null || boneName.isEmpty() || boneName.length() > 128) {
            return null;
        }
        Map<String, OpenYsmBone> bones = BONE_SCOPE.get();
        if (bones == null || bones.isEmpty()) {
            return null;
        }
        OpenYsmBone exact = bones.get(boneName);
        if (exact != null) {
            return exact;
        }
        for (Map.Entry<String, OpenYsmBone> entry : bones.entrySet()) {
            if (entry.getKey().equalsIgnoreCase(boneName)) {
                return entry.getValue();
            }
        }
        return null;
    }

    private double firstOrderFunction(List<MolangValue> args) {
        if (this.snapshot == null || args.size() < 2) {
            return 0.0D;
        }
        double response = args.size() >= 3 ? args.get(2).asDouble() : 1.0D;
        return OpenYsmMolangPhysics.firstOrder(physicsScope(), args.get(0).asString(), args.get(1).asDouble(),
                response, this.snapshot.ageInTicks);
    }

    private double secondOrderFunction(List<MolangValue> args) {
        if (this.snapshot == null || args.size() < 2) {
            return 0.0D;
        }
        double frequency = args.size() >= 3 ? args.get(2).asDouble() : 1.0D;
        double coefficient = args.size() >= 4 ? args.get(3).asDouble() : 1.0D;
        double response = args.size() >= 5 ? args.get(4).asDouble() : 1.0D;
        return OpenYsmMolangPhysics.secondOrder(physicsScope(), args.get(0).asString(), args.get(1).asDouble(),
                frequency, coefficient, response, this.snapshot.ageInTicks);
    }

    private String physicsScope() {
        return this.modelId + "|" + this.controllerName;
    }

    private static String selectedTextureName() {
        String textureId = YesSteveModel.getClientConfig().getSelectedTextureId();
        return textureId == null || textureId.isEmpty() ? "default" : textureId;
    }

    private static boolean isFirstPerson() {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft != null && minecraft.gameSettings != null
                && minecraft.gameSettings.getPointOfView().func_243192_a();
    }

    private static int cameraPointOfViewOrdinal() {
        Minecraft minecraft = Minecraft.getInstance();
        PointOfView pointOfView = minecraft == null || minecraft.gameSettings == null
                ? PointOfView.FIRST_PERSON : minecraft.gameSettings.getPointOfView();
        return pointOfView.ordinal();
    }

    private static double normalizeTimeOfDay(long dayTime) {
        long wrapped = Math.floorMod(dayTime, 24000L);
        return wrapped / 24000.0D;
    }

    private void debugUnknown(String kind, String name) {
        if (!Boolean.getBoolean("yes_steve_model.debugAnimationState")) {
            return;
        }
        YesSteveModel.LOGGER.info("[DEBUG-animation-state] model={} controller={} unknown {}={} default=0", this.modelId,
                this.controllerName, kind, name);
    }
}
