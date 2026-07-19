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
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.potion.Effect;
import net.minecraft.potion.EffectInstance;
import net.minecraft.potion.Effects;
import org.newdawn.slick.opengl.Texture;
import team.sdhq.eventBus.annotations.EventTarget;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * GeminiJello TargetHUD — 参考 Gemini 生成的 Advanced State Machine 设计。
 *
 * <p>特性（与 HTML 原型一一对应）：
 * <ul>
 *   <li>三层拼接血条：灰色轨道 + 红色拖影（缓动追踪总血量损失）+ 蓝色渐变真实血量 + 黄色渐变伤害吸收</li>
 *   <li>有吸收黄心时血量数字变黄</li>
 *   <li>药水状态机：获得/失去药水时子项弹性滑入/滑出，面板高度随之展开/折叠</li>
 *   <li>整体出现/消失带 easeOutBack 弹性缩放</li>
 *   <li>crafatar 异步拉取玩家头像，圆角裁切显示，未加载时回退绘制人形图标</li>
 * </ul>
 *
 * <p>渲染同 {@link JelloTargetHUD}：Skija 光栅化 + 脏标记纹理缓存。
 */
public class GeminiStyleJello extends RenderModule {

    // ===== 布局常量（对应 HTML）=====
    private static final float W_PANEL   = 260.0F;
    private static final float H_BASE    = 84.0F;
    private static final float H_EXPAND  = 38.0F;   // 药水扩展区高度
    private static final float R_PANEL   = 12.0F;
    private static final float PAD       = 16.0F;
    private static final float SZ_AVATAR = 52.0F;
    private static final float R_AVATAR  = 8.0F;
    private static final float GAP_ROW   = 15.0F;
    private static final float H_BAR     = 6.0F;
    private static final float R_BAR     = 3.0F;
    private static final float W_POT_ITEM = 74.0F; // 每个药水子项完全展开时的宽度

    // ===== 颜色（对应 HTML）=====
    private static final int C_PANEL      = 0xFFFFFFFF;
    private static final int C_NAME       = 0xFF282D37;
    private static final int C_SUB        = 0xFF8C92A0;
    private static final int C_HP_BLUE    = 0xFF4080FF;   // 无吸收时血量数字
    private static final int C_HP_GOLD    = 0xFFEAB308;   // 有吸收时血量数字
    private static final int C_BAR_BG     = 0xFFE1E4ED;
    private static final int C_BAR_TRAIL  = 0xFFFF7676;
    private static final int C_BLUE_START = 0xFF4080FF;
    private static final int C_BLUE_END   = 0xFF6CA0FF;
    private static final int C_GOLD_START = 0xFFFACC15;
    private static final int C_GOLD_END   = 0xFFFDE047;
    private static final int C_DIVIDER    = 0xFFF0F2F5;
    private static final int C_POT_NAME   = 0xFF282D37;
    private static final int C_POT_TIME   = 0xFF4080FF;
    private static final int C_AVATAR_BG  = 0xFF4080FF;

    // ===== 动画时长 =====
    private static final long D_SHOW = 260;
    private static final long D_HIDE = 180;

    // ===== 缓动 =====
    private static float easeInCubic(float t)  { return t * t * t; }

    /** 对应 cubic-bezier(0.34, 1.56, 0.64, 1) 的弹性过冲缓动 */
    private static float easeOutBack(float t) {
        final float c1 = 1.70158F;
        final float c3 = c1 + 1.0F;
        float p = t - 1.0F;
        return 1.0F + c3 * p * p * p + c1 * p * p;
    }

    // ===== 追踪的药水效果 =====
    private static class PotionDef {
        final Effect effect;
        final String name;
        final int dotColor;
        PotionDef(Effect effect, String name, int dotColor) {
            this.effect = effect; this.name = name; this.dotColor = dotColor;
        }
    }

    private static final PotionDef[] TRACKED_POTIONS = {
            new PotionDef(Effects.SPEED,           "Speed",    0xFF38BDF8),
            new PotionDef(Effects.STRENGTH,        "Strength", 0xFFEF4444),
            new PotionDef(Effects.RESISTANCE,      "Resist",   0xFFA78BFA),
            new PotionDef(Effects.REGENERATION,    "Regen",    0xFFF472B6),
            new PotionDef(Effects.FIRE_RESISTANCE, "FireRes",  0xFFFB923C),
    };

    /** 单个药水子项的动画状态（对应 HTML 的 PotionAnimState）*/
    private static class PotionState {
        final String name;
        final int dotColor;
        String time = "";
        float progress = 0.0F; // 0 → 1，控制宽度/透明度
        boolean removing = false;
        boolean matchedThisFrame = false;
        PotionState(String name, int dotColor) { this.name = name; this.dotColor = dotColor; }
    }

    // ===== 动画状态 =====
    private boolean wasShowing = false;
    private long panelAnimStart = 0;
    private long panelAnimDur = D_SHOW;

    private float dispHp = 20.0F;        // 蓝条（真实血量，快速跟随）
    private float dispAbs = 0.0F;        // 黄条（吸收血量，快速跟随）
    private float trailHp = 20.0F;       // 红色拖影追踪 (真实 + 吸收) 总和，缓慢衰减
    private float animatedHeight = H_BASE;
    private long lastFrameTime = 0;

    private final Map<String, PotionState> potionStates = new LinkedHashMap<>();

    private Entity currentTarget;

    // ===== crafatar 头像缓存（异步拉取，Skija 解码不需要 GL 上下文）=====
    private static final Map<UUID, Image> AVATAR_CACHE = new ConcurrentHashMap<>();
    private static final Set<UUID> AVATAR_PENDING = ConcurrentHashMap.newKeySet();

    // ===== 纹理缓存（脏标记优化）=====
    private Texture cachedHudTexture = null;
    private String cacheKey = "";

    public GeminiStyleJello() {
        super(ModuleCategory.GUI, "GeminiJello", "Gemini style TargetHUD");
    }

    @EventTarget
    public void onRender(EventRender2DOffset event) {
        if (!this.isEnabled() || mc.player == null) return;

        long now = System.currentTimeMillis();
        float delta = lastFrameTime == 0 ? 0.016F : Math.min(0.1F, (now - lastFrameTime) / 1000.0F);
        lastFrameTime = now;

        // ===== 获取目标 =====
        Entity target = KillAura.targetEntity;
        if (target == null && mc.currentScreen instanceof ChatScreen) target = mc.player;
        boolean show = target != null && target.isAlive();

        // ===== 面板出现/消失动画 =====
        if (show != wasShowing) {
            wasShowing = show;
            panelAnimStart = now;
            panelAnimDur = show ? D_SHOW : D_HIDE;
        }
        long pElapsed = now - panelAnimStart;
        float pT = panelAnimDur > 0 ? Math.min(1.0F, (float) pElapsed / panelAnimDur) : 1.0F;
        float progress = show ? pT : (1.0F - pT);
        float scaleAnim = show ? easeOutBack(pT) : (1.0F - easeInCubic(pT));
        float opacity = Math.min(1.0F, progress * 2.0F);

        if (opacity < 0.01F) {
            currentTarget = null;
            potionStates.clear();
            animatedHeight = H_BASE;
            releaseTexture();
            return;
        }

        if (target == null) {
            target = currentTarget;
            if (target == null) return;
        }

        // ===== 目标数据 =====
        float currentHp = 20.0F, absHp = 0.0F, maxHp = 20.0F;
        int armorPct = 0;
        if (target instanceof LivingEntity) {
            LivingEntity le = (LivingEntity) target;
            currentHp = le.getHealth();
            absHp = le.getAbsorptionAmount();
            maxHp = le.getMaxHealth();
            armorPct = Math.min(100, (int) (le.getTotalArmorValue() / 20.0F * 100.0F));
        }
        float totalHp = currentHp + absHp;

        // 切换目标时直接同步数值，避免血条从旧目标滑动
        if (target != currentTarget) {
            currentTarget = target;
            dispHp = currentHp;
            dispAbs = absHp;
            trailHp = totalHp;
            potionStates.clear();
        }

        // ===== 血条（冻结平滑动画：直接跟随真实值）=====
        // 之前 dispHp/dispAbs/trailHp 每帧 lerp → 每帧改变纹理内容与缓存 key
        // → 每帧重新光栅化 + GPU 回读 + 上传纹理，导致掉帧。改为直接取真实值，
        // 纹理只在血量真正变化时才重新烘焙。
        dispHp = currentHp;
        dispAbs = absHp;
        trailHp = totalHp;

        // ===== 药水状态机 =====
        updatePotionStates(target, delta);
        boolean anyPotionVisible = false;
        for (PotionState ps : potionStates.values()) {
            if (ps.progress > 0.05F) { anyPotionVisible = true; break; }
        }

        // ===== 面板高度展开/折叠（冻结缓动：直接跟随目标）=====
        float targetHeight = anyPotionVisible ? (H_BASE + H_EXPAND) : H_BASE;
        animatedHeight = targetHeight;

        // ===== 坐标 =====
        TargetHUD parent = (TargetHUD) Client.getInstance().moduleManager.getModuleByClass(TargetHUD.class);
        float posX = parent.getX();
        float posY = parent.getY();

        // ===== 头像 =====
        Image avatar = getAvatar(target);

        // ===== 数据 =====
        String name = target.getName().getString();
        float dist = target.getDistance(mc.player);

        // ===== 计算绘制位置（应用用户缩放设置）=====
        float userScale = parent.scale.getCurrentValue();
        float cx = posX + W_PANEL / 2.0F;
        float cy = posY + animatedHeight / 2.0F;
        float drawScale = scaleAnim * userScale;
        float drawX = cx + (posX - cx) * scaleAnim;
        float drawY = cy + (posY - cy) * scaleAnim;
        float drawW = W_PANEL * drawScale;
        float drawH = animatedHeight * drawScale;

        // ===== 脏标记：完全由内容 key 驱动 =====
        // opacity/scaleAnim/userScale 由 GL 层处理（tint + 绘制位置/缩放），不影响纹理内容，不进 key
        // animatedHeight 已冻结为离散目标值（H_BASE 或 H_BASE+H_EXPAND），只在展开/折叠时变
        // 血量取整（每 0.5 血才重建）、距离用 1 米精度，减少重建频率
        StringBuilder keyBuilder = new StringBuilder(128);
        keyBuilder.append(name)
                .append('|').append((int) dist)
                .append('|').append(armorPct)
                .append('|').append(String.format(Locale.US, "%.0f|%.0f|%.0f|%.1f",
                        dispHp * 2, dispAbs * 2, trailHp * 2, maxHp))
                .append('|').append((int) animatedHeight)
                .append('|').append(avatar != null);
        for (PotionState ps : potionStates.values()) {
            keyBuilder.append('|').append(ps.name).append(':').append(ps.time)
                    .append(':').append(ps.progress > 0.05F);
        }
        String newKey = keyBuilder.toString();

        if (!newKey.equals(cacheKey) || cachedHudTexture == null) {
            cacheKey = newKey;
            // opacity 不烘焙进纹理，统一由下方 GL tint 应用
            renderToTexture(name, dist, armorPct, currentHp, absHp, maxHp, 1.0F, avatar);
            if (cachedHudTexture == null) return;
        }

        // opacity 通过 GL tint 应用（面板出现/消失淡入淡出无需重烘焙纹理）
        int glTint = (Math.max(0, Math.min(255, (int) (opacity * 255))) << 24) | 0xFFFFFF;
        RenderUtil.drawImage(drawX, drawY, drawW, drawH, cachedHudTexture, glTint);
    }

    /**
     * 药水状态机：新药水加入并播放滑入动画，失去的药水播放滑出动画后剔除。
     */
    private void updatePotionStates(Entity target, float delta) {
        for (PotionState ps : potionStates.values()) ps.matchedThisFrame = false;

        if (target instanceof LivingEntity) {
            LivingEntity le = (LivingEntity) target;
            for (PotionDef def : TRACKED_POTIONS) {
                EffectInstance ei = le.getActivePotionEffect(def.effect);
                if (ei == null) continue;
                PotionState ps = potionStates.get(def.name);
                if (ps == null) {
                    ps = new PotionState(def.name, def.dotColor);
                    potionStates.put(def.name, ps);
                }
                ps.time = formatDuration(ei.getDuration());
                ps.matchedThisFrame = true;
                ps.removing = false; // 消失途中重新获得则取消消失
            }
        }

        // 冻结药水滑入/滑出：progress 瞬时切换（存在=1，失去=立即移除），
        // 避免动画期间每帧改变 progress 导致纹理重烘焙
        Iterator<PotionState> it = potionStates.values().iterator();
        while (it.hasNext()) {
            PotionState ps = it.next();
            if (!ps.matchedThisFrame) {
                it.remove();
            } else {
                ps.removing = false;
                ps.progress = 1.0F;
            }
        }
    }

    private static String formatDuration(int ticks) {
        int seconds = Math.max(0, ticks / 20);
        return String.format(Locale.US, "%d:%02d", seconds / 60, seconds % 60);
    }

    /**
     * 用 Skija 渲染整个面板到纹理，只在脏标记为 true 时调用。
     */
    private void renderToTexture(String name, float dist, int armorPct,
                                 float currentHp, float absHp, float maxHp,
                                 float opacity, Image avatar) {
        float panelH = animatedHeight;
        int texW = (int) Math.ceil(W_PANEL);
        int texH = (int) Math.ceil(panelH);
        SkijaHudRenderer hud = new SkijaHudRenderer(texW, texH, 2);

        // 面板背景（圆角白卡片）
        hud.drawRoundedRect(0, 0, W_PANEL, panelH, R_PANEL, withA(C_PANEL, opacity));

        // ===== 头像 =====
        float ax = PAD, ay = PAD;
        if (avatar != null) {
            hud.drawRoundedImage(avatar, ax, ay, SZ_AVATAR, SZ_AVATAR, R_AVATAR, opacity);
        } else {
            hud.drawRoundedRect(ax, ay, SZ_AVATAR, SZ_AVATAR, R_AVATAR, withA(C_AVATAR_BG, opacity));
            drawPersonIcon(hud, ax, ay, SZ_AVATAR, withA(0xFFFFFFFF, opacity));
        }

        // ===== 信息区 =====
        float infoX = ax + SZ_AVATAR + GAP_ROW;
        float infoW = W_PANEL - PAD - infoX;

        // 名字 + 总血量数字（有吸收时变黄）
        hud.drawText(name, infoX, 15.0F, 15, withA(C_NAME, opacity), true);
        String hpStr = String.format(Locale.US, "%.1f", currentHp + absHp);
        int hpColor = absHp > 0.01F ? C_HP_GOLD : C_HP_BLUE;
        float hpW = hud.measureText(hpStr, 13, true);
        hud.drawText(hpStr, infoX + infoW - hpW, 17.0F, 13, withA(hpColor, opacity), true);

        // 距离 / 护甲
        String distStr = String.format(Locale.US, "Dist: %.1fm", dist);
        hud.drawText(distStr, infoX, 37.0F, 11, withA(C_SUB, opacity), true);
        float distW = hud.measureText(distStr, 11, true);
        hud.drawText(String.format(Locale.US, "Arm: %d%%", armorPct), infoX + distW + 10.0F, 37.0F, 11, withA(C_SUB, opacity), true);

        // ===== 三层拼接血条 =====
        float barY = 57.0F;
        float displayMax = Math.max(maxHp, dispHp + dispAbs);
        if (displayMax < 0.01F) displayMax = 20.0F;
        float hpPct = Math.max(0.0F, Math.min(1.0F, dispHp / displayMax));
        float absPct = Math.max(0.0F, Math.min(1.0F - hpPct, dispAbs / displayMax));
        float trailPct = Math.max(0.0F, Math.min(1.0F, trailHp / displayMax));

        // 1. 轨道
        hud.drawRoundedRect(infoX, barY, infoW, H_BAR, R_BAR, withA(C_BAR_BG, opacity));
        // 2. 红色拖影（代表总血量的损失）
        if (trailPct > 0.003F) {
            hud.drawRoundedRect(infoX, barY, infoW * trailPct, H_BAR, R_BAR, withA(C_BAR_TRAIL, opacity));
        }
        // 3. 蓝色渐变真实血量
        if (hpPct > 0.003F) {
            hud.drawRoundedRectGradient(infoX, barY, infoW * hpPct, H_BAR, R_BAR,
                    withA(C_BLUE_START, opacity), withA(C_BLUE_END, opacity));
        }
        // 4. 黄色渐变伤害吸收（紧接蓝条，左移 1px 覆盖接缝）
        if (absPct > 0.003F) {
            float absX = infoX + infoW * hpPct;
            hud.drawRoundedRectGradient(absX - 1.0F, barY, infoW * absPct + 1.0F, H_BAR, R_BAR,
                    withA(C_GOLD_START, opacity), withA(C_GOLD_END, opacity));
        }

        // ===== 药水扩展区 =====
        if (panelH > H_BASE + 1.0F && !potionStates.isEmpty()) {
            float divY = H_BASE - 2.0F;
            // 分隔线透明度随展开程度淡入
            float expandRatio = Math.max(0.0F, Math.min(1.0F, (panelH - H_BASE) / H_EXPAND));
            hud.drawRect(PAD, divY, W_PANEL - PAD * 2, 1.0F, withA(C_DIVIDER, opacity * expandRatio));

            float itemX = PAD;
            float potTop = divY + 9.0F;
            List<PotionState> states = new ArrayList<>(potionStates.values());
            for (PotionState ps : states) {
                float ease = easeOutBack(Math.max(0.0F, Math.min(1.0F, ps.progress)));
                float itemAlpha = opacity * expandRatio * Math.max(0.0F, Math.min(1.0F, ps.progress));
                if (itemAlpha > 0.05F) {
                    // 彩色圆点代替图标
                    hud.drawCircle(itemX + 4.0F, potTop + 11.0F, 4.0F, withA(ps.dotColor, itemAlpha));
                    hud.drawText(ps.name, itemX + 14.0F, potTop, 11, withA(C_POT_NAME, itemAlpha), true);
                    hud.drawText(ps.time, itemX + 14.0F, potTop + 13.0F, 10, withA(C_POT_TIME, itemAlpha), true);
                }
                // 动态分配宽度：progress 增大时推挤后面的子项，实现挤压滑动
                itemX += W_POT_ITEM * ease;
            }
        }

        Texture newTexture = hud.toTexture();
        if (newTexture == null) return;
        releaseTexture();
        cachedHudTexture = newTexture;
    }

    private void releaseTexture() {
        if (cachedHudTexture != null) {
            try { cachedHudTexture.release(); } catch (Exception ignored) {}
            cachedHudTexture = null;
        }
    }

    private void drawPersonIcon(SkijaHudRenderer hud, float ax, float ay, float sz, int color) {
        float off = sz * (8.0F / 36.0F);
        float sc = sz * (20.0F / 36.0F) / 24.0F;
        hud.drawCircle(ax + off + 12 * sc, ay + off + 8 * sc, 4 * sc, color);
        hud.drawTopHalfOval(ax + off + 12 * sc, ay + off + 20 * sc, 8 * sc, 6 * sc, color);
    }

    /**
     * 异步从 crafatar 拉取玩家头像（带皮肤外层），解码为 Skija Image 缓存。
     * 未加载完成时返回 null，由调用方回退绘制占位图标。
     */
    private static Image getAvatar(Entity target) {
        if (!(target instanceof PlayerEntity)) return null;
        UUID id = target.getUniqueID();
        Image cached = AVATAR_CACHE.get(id);
        if (cached != null) return cached;
        if (AVATAR_PENDING.add(id)) {
            Thread t = new Thread(() -> {
                try {
                    HttpURLConnection conn = (HttpURLConnection) new URL(
                            "https://crafatar.com/avatars/" + id + "?size=64&overlay").openConnection();
                    conn.setRequestProperty("User-Agent", "Mozilla/5.0");
                    conn.setConnectTimeout(5000);
                    conn.setReadTimeout(5000);
                    try (InputStream is = conn.getInputStream();
                         ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
                        byte[] buf = new byte[4096];
                        int n;
                        while ((n = is.read(buf)) > 0) bos.write(buf, 0, n);
                        Image img = Image.makeFromEncoded(bos.toByteArray());
                        if (img != null) AVATAR_CACHE.put(id, img);
                    }
                } catch (Exception e) {
                    Client.logger.warn("GeminiJello: failed to load avatar for " + id);
                } finally {
                    // 失败也允许下次重试（缓存命中时不会重复拉取）
                    if (!AVATAR_CACHE.containsKey(id)) AVATAR_PENDING.remove(id);
                }
            }, "GeminiJello-Avatar");
            t.setDaemon(true);
            t.start();
        }
        return null;
    }

    private static int withA(int color, float a) {
        int baseA = (color >> 24) & 0xFF;
        int finalA = (int) (baseA * Math.max(0.0F, Math.min(1.0F, a)));
        return (finalA << 24) | (color & 0x00FFFFFF);
    }
}
