package com.mentalfrostbyte.jello.module.impl.combat;

import com.mentalfrostbyte.Client;
import com.mentalfrostbyte.jello.module.data.ModuleCategory;
import com.mentalfrostbyte.jello.module.data.ModuleWithModuleSettings;
import com.mentalfrostbyte.jello.module.impl.combat.aimbot.*;
import com.mentalfrostbyte.jello.module.settings.impl.BooleanSetting;
import com.mentalfrostbyte.jello.util.game.player.combat.CombatUtil;
import com.mentalfrostbyte.jello.util.game.world.EntityUtil;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.item.ArmorStandEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.MathHelper;

import java.util.List;

public class Aimbot extends ModuleWithModuleSettings {
    public Aimbot() {
        super(ModuleCategory.COMBAT,
                "Aimbot",
                "Automatically aim at players",
                new BasicAimbot(),
                new SmoothAimbot(),
                new JelloAIAimbot());
        this.registerSetting(new BooleanSetting("Players", "Aim at players", true));
        this.registerSetting(new BooleanSetting("Animals/Monsters", "Aim at animals and monsters", false));
        this.registerSetting(new BooleanSetting("Invisible", "Aim at invisible entites", true));
        this.registerSetting(new BooleanSetting("Through walls", "Aim at targets that are not visible", true));
        this.registerSetting(new BooleanSetting("Require click", "Only assist while the attack key is held", false));
    }

    public Entity getTarget(float maxDistance) {
        return this.getTarget(maxDistance, 180.0F, null);
    }

    public Entity getTarget(float maxDistance, float fov, Entity preferred) {
        List<Entity> entities = EntityUtil.getEntitesInWorld(__ -> true);
        Entity target = null;
        float bestScore = Float.MAX_VALUE;

        if (this.isValidTarget(preferred, maxDistance, Math.min(180.0F, fov + 12.0F))) {
            target = preferred;
            bestScore = this.targetScore(preferred) - 8.0F;
        }

        for (Entity entity : entities) {
            if (!this.isValidTarget(entity, maxDistance, fov)) {
                continue;
            }

            float score = this.targetScore(entity);
            if (target == null || score < bestScore) {
                target = entity;
                bestScore = score;
            }
        }

        return target;
    }

    private boolean isValidTarget(Entity entity, float maxDistance, float fov) {
        if (!(entity instanceof LivingEntity) || entity == mc.player || mc.player == null) return false;
        LivingEntity living = (LivingEntity) entity;
        if (!living.isAlive() || living.getHealth() <= 0.0F || entity instanceof ArmorStandEntity) return false;
        if (mc.player.getDistance(entity) > maxDistance || !mc.player.canAttack(living)) return false;
        if (Client.getInstance().friendManager.isFriendPure(entity) || entity.isInvulnerable()) return false;
        if (!this.getBooleanValueFromSettingName("Through walls") && !mc.player.canEntityBeSeen(entity)) return false;
        if (!this.getBooleanValueFromSettingName("Players") && entity instanceof PlayerEntity) return false;
        if (entity instanceof PlayerEntity && Client.getInstance().botManager.isBot(entity)) return false;
        if (!this.getBooleanValueFromSettingName("Invisible") && entity.isInvisible()) return false;
        if (!this.getBooleanValueFromSettingName("Animals/Monsters") && !(entity instanceof PlayerEntity)) return false;
        if (entity.equals(mc.player.getRidingEntity())) return false;
        if (entity instanceof PlayerEntity
                && CombatUtil.arePlayersOnSameTeam((PlayerEntity) entity)
                && Client.getInstance().moduleManager.getModuleByClass(Teams.class).isEnabled()) return false;
        return this.angleFromCrosshair(entity) <= MathHelper.clamp(fov, 1.0F, 180.0F);
    }

    private float targetScore(Entity entity) {
        return this.angleFromCrosshair(entity) * 1.35F + mc.player.getDistance(entity) * 2.5F;
    }

    private float angleFromCrosshair(Entity entity) {
        double dx = entity.getPosX() - mc.player.getPosX();
        double dz = entity.getPosZ() - mc.player.getPosZ();
        double dy = entity.getPosY() + entity.getEyeHeight() * 0.75D
                - (mc.player.getPosY() + mc.player.getEyeHeight());
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        float yaw = (float) Math.toDegrees(Math.atan2(dz, dx)) - 90.0F;
        float pitch = (float) -Math.toDegrees(Math.atan2(dy, horizontal));
        float yawDelta = MathHelper.wrapDegrees(yaw - mc.player.rotationYaw);
        float pitchDelta = pitch - mc.player.rotationPitch;
        return Math.min(180.0F, (float) Math.sqrt(yawDelta * yawDelta + pitchDelta * pitchDelta));
    }
}
