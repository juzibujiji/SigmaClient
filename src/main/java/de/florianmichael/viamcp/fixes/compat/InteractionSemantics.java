package de.florianmichael.viamcp.fixes.compat;

import net.minecraft.block.AbstractFireBlock;
import net.minecraft.block.BlockState;
import net.minecraft.block.SnowBlock;
import net.minecraft.client.entity.player.ClientPlayerEntity;
import net.minecraft.client.network.play.ClientPlayNetHandler;
import net.minecraft.inventory.container.ClickType;
import net.minecraft.item.ItemStack;
import net.minecraft.network.play.client.CPlayerPacket;
import net.minecraft.util.ActionResultType;
import net.minecraft.util.Direction;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.BlockRayTraceResult;
import net.minecraft.world.World;

public final class InteractionSemantics {
    private InteractionSemantics() {
    }

    public static boolean isSupportedHand(Hand hand) {
        return hand == Hand.MAIN_HAND || InteractionProtocol.supportsOffhand();
    }

    public static boolean canUseItemCooldown(ItemStack stack) {
        return InteractionProtocol.usesAttackAndItemCooldowns() && stack != null && !stack.isEmpty();
    }

    public static void sendPreUseMovement(ClientPlayNetHandler connection, ClientPlayerEntity player) {
        if (!InteractionProtocol.between1_17And1_20_5() || connection == null || player == null) {
            return;
        }

        connection.sendPacket(new CPlayerPacket.PositionRotationPacket(
                player.getPosX(),
                player.getPosY(),
                player.getPosZ(),
                player.rotationYaw,
                player.rotationPitch,
                player.onGround));
    }

    public static BlockRayTraceResult adjustBlockHit(World world, BlockRayTraceResult hit) {
        if (world == null || hit == null || !InteractionProtocol.atOrOlderThan1_12_2()) {
            return hit;
        }

        BlockState state = world.getBlockState(hit.getPos());
        if (state.getBlock() instanceof SnowBlock && state.get(SnowBlock.LAYERS) == 1 && hit.getFace() != Direction.UP) {
            return hit.withFace(Direction.UP);
        }

        return hit;
    }

    public static boolean extinguishFireBeforeBreak(World world, BlockPos pos) {
        if (world == null || pos == null || !InteractionProtocol.atOrOlderThan1_15_2()) {
            return false;
        }

        BlockState state = world.getBlockState(pos);
        if (!(state.getBlock() instanceof AbstractFireBlock)) {
            return false;
        }

        return world.removeBlock(pos, false);
    }

    public static ActionResultType legacyPlacementResult(ActionResultType result, ItemStack stack, int originalCount) {
        if (!InteractionProtocol.atOrOlderThan1_12_2()) {
            return result;
        }

        if (result == ActionResultType.CONSUME) {
            return ActionResultType.SUCCESS;
        }

        if (result == ActionResultType.PASS && stack != null && stack.getCount() != originalCount) {
            return ActionResultType.SUCCESS;
        }

        return result;
    }

    public static boolean isInventoryActionSupported(int slotId, int mouseButton, ClickType type) {
        if (!InteractionProtocol.atOrOlderThan1_8()) {
            return true;
        }

        if (slotId == 45) {
            return false;
        }

        return type != ClickType.SWAP || mouseButton != 40;
    }
}
