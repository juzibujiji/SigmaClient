package com.mentalfrostbyte.jello.module.impl.gui.jello.radar;

import com.mentalfrostbyte.Client;
import com.mentalfrostbyte.jello.event.impl.game.render.EventRender2DOffset;
import com.mentalfrostbyte.jello.managers.GuiManager;
import com.mentalfrostbyte.jello.module.RenderModule;
import com.mentalfrostbyte.jello.module.data.ModuleCategory;
import com.mentalfrostbyte.jello.module.impl.gui.jello.Radar;
import com.mentalfrostbyte.jello.util.game.render.RenderUtil;
import com.mentalfrostbyte.jello.util.game.sound.RadarSoundPlayer;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.util.InputMappings;
import net.minecraft.entity.Entity;
import net.minecraft.entity.item.TNTEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.AbstractArrowEntity;
import net.minecraft.entity.projectile.DamagingProjectileEntity;
import net.minecraft.entity.projectile.EggEntity;
import net.minecraft.entity.projectile.SnowballEntity;
import net.minecraft.entity.projectile.TridentEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.vector.Vector3d;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL11;
import org.newdawn.slick.TrueTypeFont;
import team.sdhq.eventBus.annotations.EventTarget;

import java.awt.Font;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * WarThunder 模式雷达 — 战争雷霆风格的双雷达显示。
 *
 * <p>左侧 TWS 相控阵扫描屏（B-Scope）：横轴为相对方位角（±90°），纵轴为距离
 * （底部 0 → 顶部最大探测距离），带纵向扫描线。原 HTML 原型中目标坐标映射在
 * 扫描矩形之外且纵轴误用高度，这里修正为标准 B-Scope 映射。
 *
 * <p>右侧 RWR 告警盘：以玩家视角为 FWD（正上），目标按相对方位与距离布点，
 * 最大接近率目标为主威胁 —— 虚线连接射线 + 脉冲圆环 + LOCK 标记。
 *
 * <p>目标获取：扫描范围内的投掷物（雪球/箭/三叉戟/火球/鸡蛋）、TNT 与其他玩家，
 * 接近率由目标与玩家的相对运动沿视线方向的分量计算（格/秒，负值为接近）。
 *
 * <p>音效（父模块 Sound 开关控制，资源在 res 的 audio 目录）：
 * 有目标正在接近且距离 &lt; 5 格 → 循环播放锁定告警 radar_lock.wav；
 * 其余距离上存在目标 → 播放扫描提示音 radar_scan.wav。
 *
 * <p>所有文本使用 TrueTypeFont（Consolas，纯英文，无中文渲染问题），
 * 颜色统一由父模块的 ColorSetting 调色盘控制。
 */
public class WarThunderRadar extends RenderModule {

    // ===== 布局常量（局部坐标，面板 300 x 192，见 Radar.PANEL_W/H）=====
    private static final float TWS_X = 4.0F;
    private static final float TWS_Y = 14.0F;
    private static final float TWS_W = 112.0F;
    private static final float TWS_H = 164.0F;

    private static final float RWR_CX = 210.0F;
    private static final float RWR_CY = 96.0F;
    private static final float RWR_R  = 76.0F;

    /** 触发锁定告警的距离（格） */
    private static final float LOCK_DISTANCE = 5.0F;

    /** Alt+R 手动锁定框颜色（固定亮绿，不随主色变化以便区分） */
    private static final int LOCK_GREEN = 0xFF55FF55;

    /** TWS 扫描线拖影宽度（px） */
    private static final float SCAN_TRAIL_W = 30.0F;
    private static final int SCAN_TRAIL_LINES = 8;
    private static final float SCAN_TRAIL_LINE_GAP = 4.0F;
    private static final float MIN_SCAN_RATE = 0.05F;
    private static final float TWS_SCAN_HIT_PAD = 2.5F;
    private static final int TRANSLUCENT_BLACK_BG = 0x5A0B0D0F;

    // ===== 字体（Consolas，懒加载 —— TrueTypeFont 需要 GL 上下文）=====
    private static TrueTypeFont font12, font14;

    private boolean posInit = false;
    /** 线宽缩放：glLineWidth 不受矩阵缩放影响，需手动乘用户缩放 */
    private float lineScale = 1.0F;

    /** Alt+R 手动锁定的目标实体 ID（-1 = 未锁定） */
    private int lockedEntityId = -1;
    /** Alt+R 组合键上一帧状态，用于边沿检测 */
    private boolean prevLockKey = false;
    private final Map<Integer, Contact> twsTracks = new HashMap<>();
    private float previousScanX = TWS_X;
    private int previousScanDirection = 1;
    private boolean previousScanValid = false;

    private static class ScanState {
        float x;
        int direction;
    }

    /** 雷达接触目标 */
    private static class Contact {
        Entity entity;
        int entityId;
        String code;
        float distance;      // 格
        float relBearing;    // 相对玩家视角的方位角，0 = 正前方
        float closingSpeed;  // 径向速度（格/秒），负值 = 接近
        float crosshairAngle; // 与准星视线的夹角（度），越小越靠近准星
        boolean lock;        // 正在接近且距离 < LOCK_DISTANCE
        boolean marked;      // Alt+R 手动锁定
    }

    public WarThunderRadar() {
        super(ModuleCategory.GUI, "WarThunder", "War Thunder style RWR + TWS radar");
    }

    private static void ensureFonts() {
        if (font12 == null) font12 = new TrueTypeFont(new Font("Consolas", Font.PLAIN, 12), true);
        if (font14 == null) font14 = new TrueTypeFont(new Font("Consolas", Font.BOLD, 14), true);
    }

    @EventTarget
    public void onRender(EventRender2DOffset event) {
        if (!this.isEnabled() || mc.player == null || mc.world == null) return;
        ensureFonts();

        Radar parent = (Radar) Client.getInstance().moduleManager.getModuleByClass(Radar.class);
        if (parent == null) return;

        long time = System.currentTimeMillis();
        float range = parent.range.getCurrentValue();
        ScanState twsScan = getTwsScanState(time, parent.scanRate.getCurrentValue());
        boolean realisticTws = parent.realistic.getCurrentValue();

        // ===== 目标获取 =====
        List<Contact> contacts = scanContacts(range);
        Contact primary = getMaxClosingContact(contacts);

        // ===== Alt+R 手动锁定（按下边沿触发，锁定后保持）=====
        handleLockKey(contacts);
        // 标记被锁定的目标；若锁定实体已消失则清除
        boolean lockAlive = false;
        for (Contact c : contacts) {
            if (c.entityId == lockedEntityId) { c.marked = true; lockAlive = true; }
        }
        if (lockedEntityId != -1 && !lockAlive) {
            Entity le = mc.world.getEntityByID(lockedEntityId);
            if (le == null || !le.isAlive() || le.getDistance(mc.player) > range || isRejectedByAntiBot(le)) lockedEntityId = -1;
        }
        List<Contact> twsContacts = getTwsContacts(contacts, twsScan, realisticTws);

        // ===== 告警音效 =====
        if (parent.sound.getCurrentValue() && !contacts.isEmpty()) {
            boolean lockDanger = false;
            for (Contact c : contacts) {
                if (c.lock) { lockDanger = true; break; }
            }
            if (lockDanger) {
                RadarSoundPlayer.play("radar_lock", 700);
            } else {
                RadarSoundPlayer.play("radar_scan", 1600);
            }
        }

        // ===== 初始位置 =====
        if (!posInit) {
            posInit = true;
            int sh = mc.getMainWindow().getScaledHeight();
            parent.setX(60.0F);
            parent.setY(sh - Radar.PANEL_H - 70.0F);
        }

        float userScale = parent.scale.getCurrentValue();
        lineScale = userScale * GuiManager.scaleFactor;
        boolean drawBg = parent.background.getCurrentValue();

        // ===== 颜色（由 ColorSetting 调色盘控制）=====
        int base = parent.color.getCurrentValue();
        int main   = withAlpha(base, 0.92F);
        int dim    = withAlpha(base, 0.40F);
        int soft   = withAlpha(base, 0.65F);
        int bright = withAlpha(lighten(base, 0.45F), 1.0F);
        // 半透明黑底（Background 开启时使用），否则用主色派生的暗底
        int bg     = drawBg ? TRANSLUCENT_BLACK_BG : withAlpha(scaleRgb(base, 0.10F), 0.28F);

        GL11.glPushMatrix();
        GL11.glTranslatef(parent.getX(), parent.getY(), 0.0F);
        GL11.glScalef(userScale, userScale, 1.0F);

        drawTws(twsContacts, primary, range, time, twsScan, main, dim, soft, bright, bg);
        drawRwr(contacts, primary, range, time, main, dim, soft, bright, bg);

        GL11.glPopMatrix();
        rememberTwsScan(twsScan);
        RenderSystem.color4f(1.0F, 1.0F, 1.0F, 1.0F);
    }

    /**
     * Alt+R 按键锁定：按下瞬间锁定准星最近的目标；若该目标已被锁定则解除，否则切换到新目标。
     */
    private void handleLockKey(List<Contact> contacts) {
        long handle = mc.getMainWindow().getHandle();
        boolean alt = InputMappings.isKeyDown(handle, GLFW.GLFW_KEY_LEFT_ALT)
                || InputMappings.isKeyDown(handle, GLFW.GLFW_KEY_RIGHT_ALT);
        boolean r = InputMappings.isKeyDown(handle, GLFW.GLFW_KEY_R);
        boolean combo = alt && r;

        if (combo && !prevLockKey) {
            Contact nearest = null;
            for (Contact c : contacts) {
                if (nearest == null || c.crosshairAngle < nearest.crosshairAngle) nearest = c;
            }
            if (nearest == null) {
                lockedEntityId = -1;
            } else if (nearest.entityId == lockedEntityId) {
                lockedEntityId = -1;
            } else {
                lockedEntityId = nearest.entityId;
            }
        }
        prevLockKey = combo;
    }

    // =====================================================================
    // 目标获取
    // =====================================================================

    private List<Contact> scanContacts(float range) {
        List<Contact> list = new ArrayList<>();
        for (Entity e : mc.world.getAllEntities()) {
            if (e == mc.player || !e.isAlive()) continue;
            if (isRejectedByAntiBot(e)) continue;
            String code = classify(e);
            if (code == null) continue;

            float dist = e.getDistance(mc.player);
            if (dist > range) continue;

            double dx = e.getPosX() - mc.player.getPosX();
            double dy = e.getPosY() - mc.player.getPosY();
            double dz = e.getPosZ() - mc.player.getPosZ();

            // 相对方位角：0 = 玩家正前方
            float targetYaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0);
            float rel = MathHelper.wrapDegrees(targetYaw - mc.player.rotationYaw);

            // 径向速度：相对运动沿视线方向的分量（格/秒），负值 = 接近
            Vector3d relMotion = e.getMotion().subtract(mc.player.getMotion());
            Vector3d toTarget = new Vector3d(dx, dy, dz);
            double len = toTarget.length();
            float radial = len > 0.01
                    ? (float) (relMotion.dotProduct(toTarget) / len * 20.0)
                    : 0.0F;

            // 与准星视线的夹角（用眼睛高度修正的方向向量），Alt+R 选取最靠准星的目标
            double eyeDy = (e.getPosY() + e.getEyeHeight()) - (mc.player.getPosY() + mc.player.getEyeHeight());
            Vector3d toEye = new Vector3d(dx, eyeDy, dz);
            float crosshair = 180.0F;
            if (toEye.length() > 0.01) {
                Vector3d look = mc.player.getLook(1.0F);
                double dot = clamp((float) look.dotProduct(toEye.normalize()), -1.0F, 1.0F);
                crosshair = (float) Math.toDegrees(Math.acos(dot));
            }

            Contact c = new Contact();
            c.entity = e;
            c.entityId = e.getEntityId();
            c.code = code;
            c.distance = dist;
            c.relBearing = rel;
            c.closingSpeed = radial;
            c.crosshairAngle = crosshair;
            c.lock = radial < -0.05F && dist < LOCK_DISTANCE;
            list.add(c);
        }
        return list;
    }

    private static boolean isRejectedByAntiBot(Entity entity) {
        return Client.getInstance().botManager != null && Client.getInstance().botManager.isBot(entity);
    }

    private static String classify(Entity e) {
        if (e instanceof SnowballEntity) return "SNB";
        if (e instanceof EggEntity) return "EGG";
        if (e instanceof TridentEntity) return "TRD";
        if (e instanceof AbstractArrowEntity) return "ARR";
        if (e instanceof DamagingProjectileEntity) return "FBL";
        if (e instanceof TNTEntity) return "EXP";
        if (e instanceof PlayerEntity) return "PLR";
        return null;
    }

    /** 主威胁 = 接近率最大（径向速度最负）的目标，与 HTML 原型逻辑一致 */
    private static Contact getMaxClosingContact(List<Contact> contacts) {
        Contact best = null;
        for (Contact c : contacts) {
            if (c.closingSpeed >= -0.05F) continue;
            if (best == null || c.closingSpeed < best.closingSpeed) best = c;
        }
        return best;
    }

    private static ScanState getTwsScanState(long time, float scanRate) {
        float rate = Math.max(MIN_SCAN_RATE, scanRate);
        double pass = time / 1000.0 * rate;
        long passIndex = (long) Math.floor(pass);
        float phase = (float) (pass - passIndex);
        boolean forward = (passIndex & 1L) == 0L;

        ScanState state = new ScanState();
        state.direction = forward ? 1 : -1;
        state.x = TWS_X + (forward ? phase : 1.0F - phase) * TWS_W;
        return state;
    }

    private List<Contact> getTwsContacts(List<Contact> liveContacts, ScanState scan, boolean realistic) {
        if (!realistic) {
            twsTracks.clear();
            return liveContacts;
        }

        Map<Integer, Contact> liveById = new HashMap<>();
        for (Contact contact : liveContacts) {
            liveById.put(contact.entityId, contact);
        }

        Iterator<Map.Entry<Integer, Contact>> iterator = twsTracks.entrySet().iterator();
        while (iterator.hasNext()) {
            if (!liveById.containsKey(iterator.next().getKey())) {
                iterator.remove();
            }
        }

        float tolerance = getScanHitTolerance(scan);
        for (Contact contact : liveContacts) {
            if (isScanPassingContact(twsContactX(contact), scan, tolerance)) {
                twsTracks.put(contact.entityId, copyContact(contact));
            }
        }

        for (Contact track : twsTracks.values()) {
            track.marked = track.entityId == lockedEntityId;
        }
        return new ArrayList<>(twsTracks.values());
    }

    private float getScanHitTolerance(ScanState scan) {
        if (!previousScanValid) {
            return TWS_SCAN_HIT_PAD;
        }
        return Math.max(TWS_SCAN_HIT_PAD, Math.abs(scan.x - previousScanX) + TWS_SCAN_HIT_PAD);
    }

    private boolean isScanPassingContact(float contactX, ScanState scan, float tolerance) {
        if (!previousScanValid) {
            return Math.abs(contactX - scan.x) <= tolerance;
        }

        float min = Math.min(previousScanX, scan.x) - tolerance;
        float max = Math.max(previousScanX, scan.x) + tolerance;
        if (contactX >= min && contactX <= max) {
            return true;
        }

        if (scan.direction != previousScanDirection) {
            float edge = scan.direction > 0 ? TWS_X : TWS_X + TWS_W;
            return Math.abs(contactX - edge) <= tolerance;
        }
        return false;
    }

    private void rememberTwsScan(ScanState scan) {
        previousScanX = scan.x;
        previousScanDirection = scan.direction;
        previousScanValid = true;
    }

    private static Contact copyContact(Contact source) {
        Contact copy = new Contact();
        copy.entity = source.entity;
        copy.entityId = source.entityId;
        copy.code = source.code;
        copy.distance = source.distance;
        copy.relBearing = source.relBearing;
        copy.closingSpeed = source.closingSpeed;
        copy.crosshairAngle = source.crosshairAngle;
        copy.lock = source.lock;
        copy.marked = source.marked;
        return copy;
    }

    private static float twsContactX(Contact c) {
        float az = clamp(c.relBearing / 90.0F, -1.0F, 1.0F);
        return TWS_X + (az + 1.0F) * 0.5F * (TWS_W - 16.0F) + 8.0F;
    }

    private static float twsContactY(Contact c, float range) {
        float ownY = TWS_Y + TWS_H - 10.0F;
        float distNorm = clamp(c.distance / range, 0.0F, 1.0F);
        return ownY - distNorm * (TWS_H - 26.0F);
    }

    // =====================================================================
    // TWS 相控阵扫描屏（B-Scope）
    // =====================================================================

    private void drawTws(List<Contact> contacts, Contact primary, float range, long time, ScanState scan,
                         int main, int dim, int soft, int bright, int bg) {
        // 背景 + 边框
        fillRect(TWS_X, TWS_Y, TWS_W, TWS_H, bg);
        strokeRect(TWS_X, TWS_Y, TWS_W, TWS_H, 1.4F, main);

        // 标题
        drawCentered(font14, TWS_X + TWS_W / 2.0F, TWS_Y - 14.0F, "TWS", main);

        // 内部分格：3 条竖线 + 5 条横线
        for (int i = 1; i <= 3; i++) {
            float x = TWS_X + TWS_W * i / 4.0F;
            line(x, TWS_Y, x, TWS_Y + TWS_H, 1.0F, dim);
        }
        for (int i = 1; i <= 5; i++) {
            float y = TWS_Y + TWS_H * i / 6.0F;
            line(TWS_X, y, TWS_X + TWS_W, y, 1.0F, dim);
        }

        float scanX = scan.x;
        drawScanTrail(scanX, scan.direction, TWS_Y, TWS_H, soft);
        line(scanX, TWS_Y, scanX, TWS_Y + TWS_H, 1.8F, bright);

        // 底部自机符号：圆 + 十字
        float ownX = TWS_X + TWS_W / 2.0F;
        float ownY = TWS_Y + TWS_H - 10.0F;
        circle(ownX, ownY, 6.0F, 1.4F, main);
        line(ownX, ownY - 8.0F, ownX, ownY + 8.0F, 1.4F, main);
        line(ownX - 8.0F, ownY, ownX + 8.0F, ownY, 1.4F, main);

        // 目标：X = 相对方位（±90° 裁剪），Y = 距离（底部 0 → 顶部 range）
        for (Contact c : contacts) {
            boolean isPrimary = primary != null && c.entityId == primary.entityId;

            float x = twsContactX(c);
            float y = twsContactY(c, range);

            int col = isPrimary ? bright : main;

            // 上指箭头符号
            lineStrip(new float[]{x - 5, y + 5, x, y - 5, x + 5, y + 5}, isPrimary ? 2.0F : 1.4F, col);
            if (isPrimary) {
                strokeRect(x - 8.0F, y - 8.0F, 16.0F, 16.0F, 1.6F, col);
            }
            // Alt+R 手动锁定：额外套一层粗体绿框
            if (c.marked) {
                strokeRect(x - 11.0F, y - 11.0F, 22.0F, 22.0F, 2.6F, LOCK_GREEN);
            }

            // 标签（防止超出右边界时画到左侧）
            String distStr = String.format(Locale.US, "%.0fm", c.distance);
            float labelX = x + 10.0F;
            if (labelX + font12.getWidth(c.code) > TWS_X + TWS_W - 2.0F) {
                labelX = x - 10.0F - font12.getWidth(c.code);
            }
            RenderUtil.drawString(font12, labelX, y - 12.0F, c.code, col);
            RenderUtil.drawString(font12, labelX, y - 1.0F, distStr, soft);
        }

        // 底部读数：左 = 主威胁距离，右 = 0m
        String rangeText = primary != null
                ? String.format(Locale.US, "%.1fm", primary.distance)
                : String.format(Locale.US, "%.0fm", range);
        RenderUtil.drawString(font12, TWS_X + 3.0F, TWS_Y + TWS_H + 2.0F, rangeText, main);
        String zero = "0m";
        RenderUtil.drawString(font12, TWS_X + TWS_W - font12.getWidth(zero) - 3.0F, TWS_Y + TWS_H + 2.0F, zero, main);
    }

    // =====================================================================
    // RWR 告警盘
    // =====================================================================

    private void drawRwr(List<Contact> contacts, Contact primary, float range, long time,
                         int main, int dim, int soft, int bright, int bg) {
        // 背景圆盘
        fillCircle(RWR_CX, RWR_CY, RWR_R, bg);

        // 外环 / 内环 / 中心十字
        circle(RWR_CX, RWR_CY, RWR_R, 2.0F, main);
        circle(RWR_CX, RWR_CY, RWR_R * 0.64F, 1.2F, dim);
        circle(RWR_CX, RWR_CY, 8.0F, 1.6F, main);
        line(RWR_CX, RWR_CY - 5.5F, RWR_CX, RWR_CY + 5.5F, 1.2F, main);
        line(RWR_CX - 5.5F, RWR_CY, RWR_CX + 5.5F, RWR_CY, 1.2F, main);

        // 36 个刻度，每 30° 一个主刻度
        for (int i = 0; i < 36; i++) {
            float ang = (float) Math.toRadians(i * 10.0F);
            boolean major = i % 3 == 0;
            float r1 = RWR_R - (major ? 6.5F : 3.5F);
            float sin = MathHelper.sin(ang), cos = MathHelper.cos(ang);
            line(RWR_CX + sin * r1, RWR_CY - cos * r1,
                    RWR_CX + sin * RWR_R, RWR_CY - cos * RWR_R,
                    major ? 1.6F : 1.0F, major ? soft : dim);
        }

        // FWD 标记（正上 = 玩家视角方向）
        drawCentered(font12, RWR_CX, RWR_CY - RWR_R - 13.0F, "FWD", soft);

        // 主威胁：动态虚线连接射线 + 脉冲圆环
        if (primary != null) {
            float[] p = rwrContactPos(primary, range);
            dashedLine(RWR_CX, RWR_CY, p[0], p[1], 5.0F, 3.2F, time * 0.05F, 1.8F, bright);
            float pulse = 2.5F + MathHelper.sin(time * 0.009F) * 1.5F;
            circle(p[0], p[1], 10.5F + pulse, 1.6F, bright);
        }

        // 目标
        for (Contact c : contacts) {
            boolean isPrimary = c == primary;

            // 锁定目标闪烁（与 HTML 一致的占空比）；手动锁定目标常亮不闪
            if (c.lock && !c.marked && MathHelper.sin(time * 0.018F) <= -0.45F) continue;

            float[] p = rwrContactPos(c, range);
            float cr = isPrimary ? 6.5F : 4.5F;
            int col = isPrimary ? bright : main;

            circle(p[0], p[1], cr, isPrimary ? 2.0F : 1.4F, col);
            // Alt+R 手动锁定：额外套一层粗体绿框
            if (c.marked) {
                float half = cr + 6.0F;
                strokeRect(p[0] - half, p[1] - half, half * 2.0F, half * 2.0F, 2.6F, LOCK_GREEN);
            }
            drawCentered(font12, p[0], p[1] - cr - 13.0F, c.code, col);

            if (c.lock || isPrimary) {
                drawCentered(font12, p[0], p[1] + cr + 2.0F, "LOCK", col);
            }
            if (isPrimary) {
                String cl = String.format(Locale.US, "%.1f CL", Math.abs(c.closingSpeed));
                drawCentered(font12, p[0], p[1] + cr + 13.0F, cl, soft);
            }
        }

        // 状态行
        String status = primary != null
                ? String.format(Locale.US, "PRI %s CLOSING %.1f", primary.code, Math.abs(primary.closingSpeed))
                : "RWR ACTIVE";
        drawCentered(font12, RWR_CX, RWR_CY + RWR_R + 3.0F, status, main);
    }

    /** RWR 布点：FWD 朝上，角度 = 相对方位，半径 = 距离归一化 */
    private static float[] rwrContactPos(Contact c, float range) {
        float ang = (float) Math.toRadians(c.relBearing);
        float rNorm = clamp(c.distance / range, 0.0F, 1.0F) * RWR_R * 0.88F;
        return new float[]{
                RWR_CX + MathHelper.sin(ang) * rNorm,
                RWR_CY - MathHelper.cos(ang) * rNorm
        };
    }

    // =====================================================================
    // GL 图元（自包含状态管理，模式同 RenderUtil.drawCircle）
    // =====================================================================

    private void preDraw(float lineWidth) {
        RenderSystem.enableBlend();
        RenderSystem.disableTexture();
        RenderSystem.blendFuncSeparate(770, 771, 0, 1);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glEnable(GL11.GL_LINE_SMOOTH);
        GL11.glHint(GL11.GL_LINE_SMOOTH_HINT, GL11.GL_NICEST);
        GL11.glLineWidth(Math.max(1.0F, lineWidth * lineScale));
    }

    private static void postDraw() {
        GL11.glDisable(GL11.GL_LINE_SMOOTH);
        GL11.glDisable(GL11.GL_BLEND);
        RenderSystem.enableTexture();
        RenderSystem.disableBlend();
        RenderSystem.color4f(1.0F, 1.0F, 1.0F, 1.0F);
    }

    private static void setColor(int argb) {
        float a = (argb >> 24 & 0xFF) / 255.0F;
        float r = (argb >> 16 & 0xFF) / 255.0F;
        float g = (argb >> 8 & 0xFF) / 255.0F;
        float b = (argb & 0xFF) / 255.0F;
        RenderSystem.color4f(r, g, b, a);
        GL11.glColor4f(r, g, b, a);
    }

    private void line(float x1, float y1, float x2, float y2, float width, int color) {
        preDraw(width);
        setColor(color);
        GL11.glBegin(GL11.GL_LINES);
        GL11.glVertex2f(x1, y1);
        GL11.glVertex2f(x2, y2);
        GL11.glEnd();
        postDraw();
    }

    private void lineStrip(float[] xy, float width, int color) {
        preDraw(width);
        setColor(color);
        GL11.glBegin(GL11.GL_LINE_STRIP);
        for (int i = 0; i + 1 < xy.length; i += 2) {
            GL11.glVertex2f(xy[i], xy[i + 1]);
        }
        GL11.glEnd();
        postDraw();
    }

    private void circle(float cx, float cy, float r, float width, int color) {
        preDraw(width);
        setColor(color);
        GL11.glBegin(GL11.GL_LINE_LOOP);
        for (int i = 0; i < 60; i++) {
            double a = Math.PI * 2.0 * i / 60.0;
            GL11.glVertex2f(cx + (float) Math.cos(a) * r, cy + (float) Math.sin(a) * r);
        }
        GL11.glEnd();
        postDraw();
    }

    private void fillCircle(float cx, float cy, float r, int color) {
        preDraw(1.0F);
        setColor(color);
        GL11.glBegin(GL11.GL_TRIANGLE_FAN);
        GL11.glVertex2f(cx, cy);
        for (int i = 0; i <= 60; i++) {
            double a = Math.PI * 2.0 * i / 60.0;
            GL11.glVertex2f(cx + (float) Math.cos(a) * r, cy + (float) Math.sin(a) * r);
        }
        GL11.glEnd();
        postDraw();
    }

    private void fillRect(float x, float y, float w, float h, int color) {
        preDraw(1.0F);
        setColor(color);
        GL11.glBegin(GL11.GL_QUADS);
        GL11.glVertex2f(x, y);
        GL11.glVertex2f(x + w, y);
        GL11.glVertex2f(x + w, y + h);
        GL11.glVertex2f(x, y + h);
        GL11.glEnd();
        postDraw();
    }

    /** 横向渐变填充：左边缘 leftColor → 右边缘 rightColor（用于扫描线拖影） */
    private void fillGradientH(float x, float y, float w, float h, int leftColor, int rightColor) {
        preDraw(1.0F);
        GL11.glBegin(GL11.GL_QUADS);
        setColor(leftColor);  GL11.glVertex2f(x, y);
        setColor(leftColor);  GL11.glVertex2f(x, y + h);
        setColor(rightColor); GL11.glVertex2f(x + w, y + h);
        setColor(rightColor); GL11.glVertex2f(x + w, y);
        GL11.glEnd();
        postDraw();
    }

    private void drawScanTrail(float scanX, int direction, float y, float h, int color) {
        int trailTail = color & 0x00FFFFFF;
        int trailLead = (color & 0x00FFFFFF) | 0x55000000;
        if (direction >= 0) {
            float trailStart = Math.max(TWS_X, scanX - SCAN_TRAIL_W);
            if (scanX > trailStart) {
                fillGradientH(trailStart, y, scanX - trailStart, h, trailTail, trailLead);
            }
        } else {
            float trailEnd = Math.min(TWS_X + TWS_W, scanX + SCAN_TRAIL_W);
            if (trailEnd > scanX) {
                fillGradientH(scanX, y, trailEnd - scanX, h, trailLead, trailTail);
            }
        }

        for (int i = 1; i <= SCAN_TRAIL_LINES; i++) {
            float x = scanX - direction * i * SCAN_TRAIL_LINE_GAP;
            if (x < TWS_X || x > TWS_X + TWS_W) {
                continue;
            }

            float fade = 1.0F - (float) i / (float) (SCAN_TRAIL_LINES + 1);
            float alpha = 0.38F * fade * fade;
            float width = 0.75F + 0.65F * fade;
            line(x, y, x, y + h, width, withAlpha(color, alpha));
        }
    }

    private void strokeRect(float x, float y, float w, float h, float width, int color) {
        preDraw(width);
        setColor(color);
        GL11.glBegin(GL11.GL_LINE_LOOP);
        GL11.glVertex2f(x, y);
        GL11.glVertex2f(x + w, y);
        GL11.glVertex2f(x + w, y + h);
        GL11.glVertex2f(x, y + h);
        GL11.glEnd();
        postDraw();
    }

    /** 动态虚线（offset 随时间推移产生流动效果） */
    private void dashedLine(float x1, float y1, float x2, float y2,
                            float dash, float gap, float offset, float width, int color) {
        float dx = x2 - x1, dy = y2 - y1;
        float len = (float) Math.sqrt(dx * dx + dy * dy);
        if (len < 0.01F) return;
        float ux = dx / len, uy = dy / len;
        float period = dash + gap;
        float start = -((offset % period) + period) % period;

        preDraw(width);
        setColor(color);
        GL11.glBegin(GL11.GL_LINES);
        for (float t = start; t < len; t += period) {
            float a = Math.max(0.0F, t);
            float b = Math.min(len, t + dash);
            if (b <= a) continue;
            GL11.glVertex2f(x1 + ux * a, y1 + uy * a);
            GL11.glVertex2f(x1 + ux * b, y1 + uy * b);
        }
        GL11.glEnd();
        postDraw();
    }

    private static void drawCentered(TrueTypeFont font, float cx, float y, String text, int color) {
        RenderUtil.drawString(font, cx - font.getWidth(text) / 2.0F, y, text, color);
    }

    // =====================================================================
    // 颜色工具
    // =====================================================================

    private static float clamp(float v, float min, float max) {
        return Math.max(min, Math.min(max, v));
    }

    private static int withAlpha(int color, float alpha) {
        int a = (int) (255 * clamp(alpha, 0.0F, 1.0F));
        return (a << 24) | (color & 0x00FFFFFF);
    }

    /** RGB 向白色插值提亮 */
    private static int lighten(int color, float t) {
        int r = (color >> 16) & 0xFF, g = (color >> 8) & 0xFF, b = color & 0xFF;
        r += (int) ((255 - r) * t);
        g += (int) ((255 - g) * t);
        b += (int) ((255 - b) * t);
        return (color & 0xFF000000) | (r << 16) | (g << 8) | b;
    }

    /** RGB 等比压暗（用于背景色） */
    private static int scaleRgb(int color, float t) {
        int r = (int) (((color >> 16) & 0xFF) * t);
        int g = (int) (((color >> 8) & 0xFF) * t);
        int b = (int) ((color & 0xFF) * t);
        return (color & 0xFF000000) | (r << 16) | (g << 8) | b;
    }
}
