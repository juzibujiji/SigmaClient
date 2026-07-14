package com.mentalfrostbyte.jello.util.game.player.combat;

import com.mentalfrostbyte.jello.util.game.MinecraftUtil;
import de.florianmichael.viamcp.fixes.compat.InteractionProtocol;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.play.client.CPlayerDiggingPacket;
import net.minecraft.network.play.client.CPlayerTryUseItemPacket;
import net.minecraft.scoreboard.ScorePlayerTeam;
import net.minecraft.util.Direction;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.List;

public class CombatUtil implements MinecraftUtil {

    public static List<PlayerEntity> getAllPlayersInWorld() {
        List<PlayerEntity> players = new ArrayList<>();
        mc.world.entitiesById.forEach((id, entity) -> {
            if (entity instanceof PlayerEntity) {
                players.add((PlayerEntity) entity);
            }
        });
        return players;
    }

    public static List<PlayerEntity> getPlayers() {
        ArrayList<PlayerEntity> players = new ArrayList<>();
        mc.world.entitiesById.forEach((entityId, entity) -> {
            if (entity instanceof PlayerEntity) {
                players.add((PlayerEntity) entity);
            }
        });
        return players;
    }

    public static boolean block() {
        if (mc.getConnection() == null || mc.player == null) {
            return false;
        }

        mc.getConnection().sendPacket(new CPlayerTryUseItemPacket(Hand.MAIN_HAND));
        if (!InteractionProtocol.atOrOlderThan1_8()) {
            mc.getConnection().sendPacket(new CPlayerTryUseItemPacket(Hand.OFF_HAND));
        }
        return true;
    }

    public static boolean unblock() {
        if (mc.getConnection() == null) {
            return false;
        }

        mc.getConnection().sendPacket(new CPlayerDiggingPacket(CPlayerDiggingPacket.Action.RELEASE_USE_ITEM, new BlockPos(0, 0, 0), Direction.DOWN));
        return true;
    }

    public static boolean arePlayersOnSameTeam(PlayerEntity player) {
        return getPlayerTeamColorCode(mc.player) == getPlayerTeamColorCode(player);
    }

    public static int getPlayerTeamColorCode(PlayerEntity player) {
        ScorePlayerTeam team = (ScorePlayerTeam) player.getTeam();
        return team != null && team.getColor().getColor() != null ? team.getColor().getColor() : 16777215;
    }
}
