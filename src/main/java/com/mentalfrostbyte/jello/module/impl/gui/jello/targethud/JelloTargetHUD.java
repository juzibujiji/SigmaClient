package com.mentalfrostbyte.jello.module.impl.gui.jello.targethud;

import com.mentalfrostbyte.Client;
import com.mentalfrostbyte.jello.event.impl.game.render.EventRender2DOffset;
import com.mentalfrostbyte.jello.module.RenderModule;
import com.mentalfrostbyte.jello.module.data.ModuleCategory;
import com.mentalfrostbyte.jello.module.impl.combat.KillAura;
import com.mentalfrostbyte.jello.module.impl.gui.jello.TargetHUD;
import com.mentalfrostbyte.jello.util.client.render.SkijaHudRenderer;
import com.mentalfrostbyte.jello.util.game.render.RenderUtil;
import io.github.humbleui.skija.Image;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.network.play.NetworkPlayerInfo;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.potion.Effects;
import org.newdawn.slick.opengl.Texture;
import team.sdhq.eventBus.annotations.EventTarget;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Jello TargetHUD — 使用 Skija（Chrome 的 Skia 2D 引擎）渲染。
 *
 * <p>性能优化：脏标记缓存。每帧检查内容是否变化（名字/距离/血量/tags/动画状态），
 * 只有变化时才重新光栅化 Skija 纹理。动画结束后直接复用缓存纹理，零开销。
 */
public class JelloTargetHUD extends RenderModule {

    // ===== HTML 常量 =====
    private static final float W_PANEL   = 220.0F;
    private static final float PAD        = 10.0F;
    private static final float R_PANEL    = 6.0F;
    private static final float SZ_AVATAR  = 36.0F;
    private static final float R_AVATAR   = 4.0F;
    private static final float GAP_ROW    = 10.0F;
    private static final float MB_ROW     = 4.0F;
    private static final float H_BAR      = 10.0F;
    private static final float MT_BAR     = 4.0F;
    private static final float MT_HP      = 2.0F;
    private static final float H_TAG      = 15.5F;
    private static final float GAP_TAG    = 6.0F;
    private static final float PH_TAG     = 7.0F;
    private static final float R_TAG      = 3.0F;
    private static final float GAP_ICON   = 3.0F;
    private static final float SZ_ICON    = 11.0F;
    private static final float PT_TAGS    = 4.0F;
    private static final float PB_TAGS    = 2.0F;
    private static final float W_CONTENT  = W_PANEL - PAD * 2 - SZ_AVATAR - GAP_ROW; // 154

    // ===== 颜色（精确匹配 HTML）=====
    // HTML: background: rgba(255,255,255,0.55)，透明度由 panelAlpha 设置控制
    private static final int C_PANEL_RGB = 0x00FFFFFF;  // 仅 RGB，alpha 由设置动态计算
    private static final int C_SHADOW   = 0x4D000000;   // rgba(0,0,0,0.3)
    private static final int C_AVATAR   = 0xFF3A9BDC;
    private static final int C_NAME     = 0xFF2C2C2C;   // 比 #282828 浅一点
    private static final int C_DIST     = 0xFF555555;
    private static final int C_BAR_BG   = 0x1E000000;   // rgba(0,0,0,0.115)
    private static final int C_BAR_FILL = 0xFF3A9BDC;
    private static final int C_HP       = 0xFF555555;
    private static final int C_TAG_BG   = 0x323A9BDC;   // rgba(58,155,220,0.195)
    private static final int C_TAG_TXT  = 0xFF0C447C;
    private static final int C_INNER_BORDER = 0x40FFFFFF; // rgba(255,255,255,0.25)

    // ===== 动画时长（毫秒，匹配 HTML）=====
    private static final long D_SHOW    = 180;
    private static final long D_HIDE    = 140;
    // tag 状态切换时间戳仍记录，但透明度已改为瞬时切换（见 updateTagStates）
    private static final long D_TAG_IN  = 160;
    private static final long D_TAG_OUT = 140;

    // ===== 缓动 =====
    private static float easeOut(float t) { return 1 - (1 - t) * (1 - t) * (1 - t); }
    private static float easeIn(float t)  { return t * t * t; }

    // ===== 动画状态 =====
    private boolean wasShowing = false;
    private long panelAnimStart = 0;
    private long panelAnimDur = D_SHOW;

    private float dispHealth = 20.0F;
    private float dispMaxHp = 20.0F;

    private final Map<String, TagState> tagStates = new HashMap<>();
    private float tagsWrapperHeight = 0.0F;
    private float tagsWrapperTarget = 0.0F;

    private static class TagState {
        String text;
        Image icon;
        float opacity = 0.0F;
        long animStart;
        long duration;
        boolean entering;
        boolean alive = true;
    }

    private Entity currentTarget;

    // ===== Skija 图标缓存 =====
    private Image iconDiamond, iconSpeed, iconStrength, iconResist, iconRegen, iconMs;
    private boolean iconsLoaded = false;

    // ===== 纹理缓存（脏标记优化）=====
    private Texture cachedHudTexture = null;
    private String cacheKey = "";

    public JelloTargetHUD() {
        super(ModuleCategory.GUI, "Jello", "Jello style TargetHUD");
    }

    private void loadIcons() {
        if (iconsLoaded) return;
        iconsLoaded = true;
        iconDiamond  = loadSkijaIcon("diamond");
        iconSpeed    = loadSkijaIcon("speed");
        iconStrength = loadSkijaIcon("strength");
        iconResist   = loadSkijaIcon("resistance");
        iconRegen    = loadSkijaIcon("regen");
        iconMs       = loadSkijaIcon("ms");
    }

    private static Image loadSkijaIcon(String n) {
        try {
            InputStream is = Client.class.getClassLoader().getResourceAsStream("assets/sigma/textures/gui/hud/" + n + ".png");
            if (is != null) {
                byte[] bytes = is.readAllBytes();
                is.close();
                return Image.makeFromEncoded(bytes);
            }
        } catch (IOException e) {
            Client.logger.warn("Failed to load Skija icon: " + n);
        }
        return null;
    }

    private static int withA(int color, float a) {
        int baseA = (color >> 24) & 0xFF;
        int finalA = (int) (baseA * a);
        return (finalA << 24) | (color & 0x00FFFFFF);
    }

    @EventTarget
    public void onRender(EventRender2DOffset event) {
        if (!this.isEnabled() || mc.player == null) return;
        loadIcons();

        // ===== 获取目标 =====
        Entity target = KillAura.targetEntity;
        if (target == null && mc.currentScreen instanceof ChatScreen) target = mc.player;
        boolean show = target != null && target.isAlive();

        // ===== 面板出现/消失动画 =====
        if (show != wasShowing) {
            wasShowing = show;
            panelAnimStart = System.currentTimeMillis();
            panelAnimDur = show ? D_SHOW : D_HIDE;
        }
        long pElapsed = System.currentTimeMillis() - panelAnimStart;
        float pT = panelAnimDur > 0 ? Math.min(1.0F, (float) pElapsed / panelAnimDur) : 1.0F;
        float pEased = show ? easeOut(pT) : easeIn(pT);
        float progress = show ? pEased : (1.0F - pEased);
        float opacity = progress;
        float scale = 0.92F + 0.08F * progress;
        float ty = -4.0F * (1.0F - progress);

        if (opacity < 0.01F) {
            currentTarget = null;
            if (cachedHudTexture != null) {
                try { cachedHudTexture.release(); } catch (Exception ignored) {}
                cachedHudTexture = null;
            }
            cacheKey = "";
            return;
        }

        if (target == null) {
            target = currentTarget;
            if (target == null) return;
        }

        if (target != currentTarget) {
            currentTarget = target;
        }

        // ===== 血条（冻结平滑动画：直接跟随真实血量）=====
        // 之前血量用 250ms 缓动，战斗中 dispHealth 每帧变化 → 每帧改变纹理内容与缓存 key
        // → 每帧重新光栅化 + GPU 回读 + 上传纹理，导致掉帧。改为直接取真实血量，
        // 纹理只在血量真正变化时才重新烘焙。
        if (target instanceof LivingEntity) {
            LivingEntity le = (LivingEntity) target;
            dispHealth = le.getHealth() + le.getAbsorptionAmount();
            dispMaxHp = le.getMaxHealth();
        }

        // ===== 坐标 =====
        TargetHUD parent = (TargetHUD) Client.getInstance().moduleManager.getModuleByClass(TargetHUD.class);
        float posX = parent.getX();
        float posY = parent.getY();

        // ===== 数据 =====
        String name = target.getName().getString();
        float dist = target.getDistance(mc.player);
        List<TagData> newTags = buildTags(target);
        updateTagStates(newTags);

        // ===== 计算面板高度 =====
        float contentH = 14 + MT_BAR + H_BAR + MT_HP + 10;
        float rowH = Math.max(SZ_AVATAR, contentH);
        int aliveCount = 0;
        for (TagState ts : tagStates.values()) {
            if (ts.alive) aliveCount++;
        }
        float tagsContentH = aliveCount > 0 ? PT_TAGS + H_TAG + PB_TAGS : 0;
        tagsWrapperTarget = tagsContentH;
        // 冻结高度缓动：直接跟随目标，避免每帧微调高度导致纹理重烘焙
        tagsWrapperHeight = tagsWrapperTarget;

        float panelH = PAD + rowH + MB_ROW + tagsWrapperHeight + PAD;

        // ===== 计算绘制位置（应用用户缩放设置）=====
        float userScale = parent.scale.getCurrentValue();
        float userOpacity = parent.opacity.getCurrentValue();
        float panelAlpha = parent.panelAlpha.getCurrentValue();
        opacity *= userOpacity;
        int panelColor = ((int)(panelAlpha * 255) << 24) | C_PANEL_RGB;
        float cx = posX + W_PANEL / 2.0F;
        float cy = posY + panelH / 2.0F;
        float drawScale = scale * userScale;
        float drawX = cx + (posX - cx) * scale;
        float drawY = cy + (posY - cy) * scale + ty * userScale;
        float drawW = W_PANEL * drawScale;
        float drawH = panelH * drawScale;

        // ===== 脏标记：完全由内容 key 驱动 =====
        // 之前血条/tag/高度动画会在动画窗口内强制每帧 dirty → 战斗中每帧重烘焙纹理
        // （Skija 光栅化 + GPU 回读 + 纹理上传），这是掉帧根源。现已冻结这些动画：
        // 血量直接跟随真实值、tag 透明度瞬时切换、tag 高度直接跟随目标，因此这些状态
        // 只在真正变化时改变 key，纹理也只在此时重烘焙。
        //
        // 缓存 key：只包含影响纹理内容的状态
        // opacity/scale/ty 由 GL 层处理（tint + 绘制位置），不影响纹理内容，不进 key
        // 距离用 2 米精度（每 2 米才重建），血量取整（每 0.5 血才重建）减少重建频率
        String newKey = String.format(Locale.US, "%s|%d|%.0f|%.0f|%d|%d|%.2f",
                name, (int)(dist / 2), dispHealth * 2, dispMaxHp * 2, aliveCount,
                tagStates.size(), panelAlpha);

        // 只靠 key 变化判断是否重烘焙，动画不再强制 dirty
        boolean dirty = !newKey.equals(cacheKey) || cachedHudTexture == null;

        // ===== 只有脏时才重新渲染 Skija 纹理 =====
        if (dirty) {
            cacheKey = newKey;
            renderToTexture(name, dist, target, panelH, rowH, panelColor);
            if (cachedHudTexture == null) return;
        }

        // ===== 绘制纹理（opacity 通过 GL tint 应用，无需烘焙到纹理）=====
        int glTint = (Math.max(0, Math.min(255, (int)(opacity * 255))) << 24) | 0xFFFFFF;
        RenderUtil.drawImage(drawX, drawY, drawW, drawH, cachedHudTexture, glTint);
    }

    /**
     * 用 Skija 渲染整个面板到纹理。只在脏标记为 true 时调用。
     * opacity 不在此处应用——由 GL tint 在绘制时统一处理。
     */
    private void renderToTexture(String name, float dist, Entity target,
                                  float panelH, float rowH, int panelColor) {
        int ss = 2; // 2x 超采样（性能与清晰度平衡）
        int texW = (int) Math.ceil(W_PANEL);
        int texH = (int) Math.ceil(panelH);
        SkijaHudRenderer hud = new SkijaHudRenderer(texW, texH, ss);

        // 面板背景（alpha 由 panelAlpha 设置控制，opacity 由 GL tint 处理）
        hud.drawRoundedRect(0, 0, W_PANEL, panelH, R_PANEL, panelColor);

        // 内边框（HTML: inset 0 0 0 1px rgba(255,255,255,0.25)）
        hud.drawRoundedRectStroke(0.5F, 0.5F, W_PANEL - 1, panelH - 1, R_PANEL, 1.0F, C_INNER_BORDER);

        float ix = PAD;
        float iy = PAD;

        // 头像
        hud.drawRoundedRect(ix, iy, SZ_AVATAR, SZ_AVATAR, R_AVATAR, C_AVATAR);
        drawPersonIcon(hud, ix, iy, SZ_AVATAR, 0xFFFFFFFF);

        // 内容区
        float ctX = ix + SZ_AVATAR + GAP_ROW;
        float ctW = W_CONTENT;

        // 名字 — 14px medium
        hud.drawText(name, ctX, iy - 2, 14, C_NAME, true);
        // 距离 — 11px light（HTML: font-size: 11px）
        String dStr = String.format(Locale.US, "%.1fm", dist);
        float dW = hud.measureText(dStr, 11, false);
        hud.drawText(dStr, ctX + ctW - dW, iy, 11, C_DIST, false);

        // 血条（HTML: height: 10px; margin-top: 4px）
        float bY = iy + 14 + MT_BAR;
        hud.drawRect(ctX, bY, ctW, H_BAR, C_BAR_BG);
        float hpPct = dispMaxHp > 0 ? Math.min(dispHealth / dispMaxHp, 1.0F) : 0;
        float fW = ctW * hpPct;
        if (fW > 0.5F) hud.drawRect(ctX, bY, fW, H_BAR, C_BAR_FILL);

        // HP 文字 — 10px（HTML: font-size: 10px）
        float hpY = bY + H_BAR + MT_HP;
        String hpStr = String.format(Locale.US, "%.1f / %.1f", dispHealth, dispMaxHp);
        hud.drawText(hpStr, ctX, hpY, 10, C_HP, false);
        float hpLW = hud.measureText("HP", 10, false);
        hud.drawText("HP", ctX + ctW - hpLW, hpY, 10, C_HP, false);

        // Tags — 10px（HTML: font-size: 10px）
        if (tagsWrapperHeight > 0.5F) {
            float tagY = iy + rowH + MB_ROW + PT_TAGS;
            float tagX = ix;
            for (TagState ts : tagStates.values()) {
                if (!ts.alive && ts.opacity < 0.01F) continue;

                float tw = hud.measureText(ts.text, 10, false);
                float icW = ts.icon != null ? SZ_ICON : 0;
                float icGap = ts.icon != null ? GAP_ICON : 0;
                float totalW = PH_TAG + icW + icGap + tw + PH_TAG;
                float tagA = ts.opacity;

                hud.drawRoundedRect(tagX, tagY, totalW, H_TAG, R_TAG, withA(C_TAG_BG, tagA));

                float cur = tagX + PH_TAG;
                if (ts.icon != null) {
                    float icY = tagY + (H_TAG - SZ_ICON) / 2.0F;
                    hud.drawImage(ts.icon, cur, icY, SZ_ICON, SZ_ICON, withA(0xFFFFFFFF, tagA));
                    cur += icW + icGap;
                }
                float txtY = tagY + (H_TAG - 10) / 2.0F - 1;
                hud.drawText(ts.text, cur, txtY, 10, withA(C_TAG_TXT, tagA), false);
                tagX += totalW + GAP_TAG;
            }
        }

        // 生成新纹理
        Texture newTexture = hud.toTexture();
        if (newTexture == null) return;

        // 释放旧纹理
        if (cachedHudTexture != null) {
            try { cachedHudTexture.release(); } catch (Exception ignored) {}
        }
        cachedHudTexture = newTexture;
    }

    private void drawPersonIcon(SkijaHudRenderer hud, float ax, float ay, float sz, int color) {
        float off = sz * (8.0F / 36.0F);
        float sc = sz * (20.0F / 36.0F) / 24.0F;
        float hcx = ax + off + 12 * sc;
        float hcy = ay + off + 8 * sc;
        float hr = 4 * sc;
        hud.drawCircle(hcx, hcy, hr, color);
        float bcx = ax + off + 12 * sc;
        float bcy = ay + off + 20 * sc;
        float brw = 8 * sc;
        float brh = 6 * sc;
        hud.drawTopHalfOval(bcx, bcy, brw, brh, color);
    }

    private void updateTagStates(List<TagData> newTags) {
        long now = System.currentTimeMillis();
        for (TagData td : newTags) {
            if (!tagStates.containsKey(td.text)) {
                TagState ts = new TagState();
                ts.text = td.text;
                ts.icon = td.icon;
                ts.opacity = 0.0F;
                ts.animStart = now;
                ts.duration = D_TAG_IN;
                ts.entering = true;
                ts.alive = true;
                tagStates.put(td.text, ts);
            } else {
                TagState ts = tagStates.get(td.text);
                if (!ts.alive) {
                    ts.alive = true;
                    ts.entering = true;
                    ts.animStart = now;
                    ts.duration = D_TAG_IN;
                }
            }
        }
        for (TagState ts : tagStates.values()) {
            boolean stillExists = false;
            for (TagData td : newTags) {
                if (td.text.equals(ts.text)) { stillExists = true; break; }
            }
            if (!stillExists && ts.alive) {
                ts.alive = false;
                ts.entering = false;
                ts.animStart = now;
                ts.duration = D_TAG_OUT;
            }
        }
        // 冻结 tag 淡入淡出：透明度瞬时切换（存活=1，消失=立即移除），
        // 避免动画期间每帧改变 tag 透明度导致纹理重烘焙
        tagStates.values().removeIf(ts -> {
            if (ts.alive) {
                ts.opacity = 1.0F;
                return false;
            }
            return true;
        });
    }

    private List<TagData> buildTags(Entity target) {
        List<TagData> tags = new ArrayList<>();
        if (!(target instanceof PlayerEntity)) return tags;
        PlayerEntity p = (PlayerEntity) target;
        if (p.isPotionActive(Effects.SPEED))         tags.add(new TagData(iconSpeed, "Speed"));
        if (p.isPotionActive(Effects.STRENGTH))      tags.add(new TagData(iconStrength, "Strength"));
        if (p.isPotionActive(Effects.RESISTANCE))    tags.add(new TagData(iconResist, "Resistance"));
        if (p.isPotionActive(Effects.REGENERATION))  tags.add(new TagData(iconRegen, "Regen"));
        boolean dia = false;
        for (ItemStack s : p.getArmorInventoryList()) {
            Item it = s.getItem();
            if (it == Items.DIAMOND_HELMET || it == Items.DIAMOND_CHESTPLATE
                    || it == Items.DIAMOND_LEGGINGS || it == Items.DIAMOND_BOOTS) { dia = true; break; }
        }
        if (dia) tags.add(new TagData(iconDiamond, "Diamond"));
        if (mc.getConnection() != null) {
            NetworkPlayerInfo info = mc.getConnection().getPlayerInfo(p.getGameProfile().getId());
            if (info != null) tags.add(new TagData(iconMs, info.getResponseTime() + "ms"));
        }
        return tags;
    }

    private static class TagData {
        final Image icon;
        final String text;
        TagData(Image icon, String text) { this.icon = icon; this.text = text; }
    }
}
