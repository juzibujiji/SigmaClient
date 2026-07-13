package com.mentalfrostbyte.jello.module.impl.combat.antibot;

import com.mentalfrostbyte.jello.event.impl.game.world.EventLoadWorld;
import com.mentalfrostbyte.jello.event.impl.game.world.EventTick;
import com.mentalfrostbyte.jello.managers.util.combat.AntiBotBase;
import com.mentalfrostbyte.jello.managers.util.combat.BotRecognitionTechnique;
import net.minecraft.client.entity.player.AbstractClientPlayerEntity;
import net.minecraft.client.network.play.NetworkPlayerInfo;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.StringUtils;
import team.sdhq.eventBus.annotations.EventTarget;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class HypixelAntiBot extends AntiBotBase {
    private static final int SPAWN_GRACE_TICKS = 20;
    private static final int INVALID_GROUND_LIMIT = 10;
    private static final int MAX_ENTITY_ID = 1_000_000_000;
    private static final double VERTICAL_EPSILON = 1.0E-4D;

    private final ConcurrentHashMap<Integer, Integer> invalidGroundViolations = new ConcurrentHashMap<>();

    public HypixelAntiBot() {
        super("Hypixel", "Detects Hypixel bots using profile, tab-list and movement evidence",
                BotRecognitionTechnique.SERVER);
    }

    @EventTarget
    public void onTick(EventTick event) {
        if (mc.world == null || mc.player == null) {
            this.clearState();
            return;
        }

        Set<Integer> activePlayerIds = new HashSet<>();
        for (AbstractClientPlayerEntity player : mc.world.getPlayers()) {
            if (player == mc.player) {
                continue;
            }

            int entityId = player.getEntityId();
            activePlayerIds.add(entityId);
            int violations = this.invalidGroundViolations.getOrDefault(entityId, 0);
            boolean invalidGround = player.isOnGround()
                    && Math.abs(player.getPosY() - player.lastTickPosY) > VERTICAL_EPSILON;
            if (invalidGround) {
                this.invalidGroundViolations.put(entityId, Math.min(INVALID_GROUND_LIMIT, violations + 1));
            } else if (violations <= 1) {
                this.invalidGroundViolations.remove(entityId);
            } else {
                this.invalidGroundViolations.put(entityId, violations - 1);
            }
        }

        this.invalidGroundViolations.keySet().retainAll(activePlayerIds);
    }

    @EventTarget
    public void onWorldLoad(EventLoadWorld event) {
        this.clearState();
    }

    @Override
    public boolean isBot(Entity entity) {
        if (!(entity instanceof PlayerEntity player) || player == mc.player || mc.getConnection() == null) {
            return false;
        }

        String profileName = player.getGameProfile().getName();
        String displayName = StringUtils.stripControlCodes(player.getDisplayName().getString());
        if (!this.isValidPlayerName(profileName)
                || displayName.toUpperCase(Locale.ROOT).contains("[NPC]")
                || player.getEntityId() <= 0
                || player.getEntityId() >= MAX_ENTITY_ID
                || Math.abs(player.rotationPitch) > 90.0F
                || player.isSpectator()
                || player.isCreative()) {
            return true;
        }

        if (this.invalidGroundViolations.getOrDefault(player.getEntityId(), 0) >= INVALID_GROUND_LIMIT) {
            return true;
        }

        if (player.ticksExisted < SPAWN_GRACE_TICKS) {
            return false;
        }

        NetworkPlayerInfo playerInfo = mc.getConnection().getPlayerInfo(player.getUniqueID());
        return playerInfo == null || playerInfo.getGameType() == null || this.hasDuplicateProfile(player);
    }

    @Override
    public boolean isNotBot(Entity entity) {
        return true;
    }

    @Override
    public void method22762() {
        this.clearState();
    }

    private boolean hasDuplicateProfile(PlayerEntity player) {
        String name = player.getGameProfile().getName();
        UUID uniqueId = player.getUniqueID();
        for (NetworkPlayerInfo playerInfo : mc.getConnection().getPlayerInfoMap()) {
            if (name.equalsIgnoreCase(playerInfo.getGameProfile().getName())
                    && !uniqueId.equals(playerInfo.getGameProfile().getId())) {
                return true;
            }
        }
        return false;
    }

    private boolean isValidPlayerName(String name) {
        if (name == null || name.length() < 3 || name.length() > 16) {
            return false;
        }

        for (int i = 0; i < name.length(); i++) {
            char character = name.charAt(i);
            if (!(character >= '0' && character <= '9')
                    && !(character >= 'a' && character <= 'z')
                    && !(character >= 'A' && character <= 'Z')
                    && character != '_') {
                return false;
            }
        }
        return true;
    }

    private void clearState() {
        this.invalidGroundViolations.clear();
    }
}
