package com.mentalfrostbyte.jello.module.impl.gui.jello.radar;

import com.mentalfrostbyte.Client;
import com.mentalfrostbyte.jello.event.impl.game.render.EventRender2DOffset;
import com.mentalfrostbyte.jello.managers.GuiManager;
import com.mentalfrostbyte.jello.module.RenderModule;
import com.mentalfrostbyte.jello.module.data.ModuleCategory;
import com.mentalfrostbyte.jello.module.impl.gui.jello.Radar;
import com.mentalfrostbyte.jello.module.impl.gui.jello.radar.threat.BallisticThreatTracker;
import com.mentalfrostbyte.jello.module.impl.gui.jello.radar.threat.EnemyLockDetector;
import com.mentalfrostbyte.jello.module.impl.gui.jello.radar.threat.LockAssessment;
import com.mentalfrostbyte.jello.module.impl.gui.jello.radar.threat.LookLineLockDetector;
import com.mentalfrostbyte.jello.module.impl.gui.jello.radar.threat.ProjectileThreat;
import com.mentalfrostbyte.jello.module.impl.gui.jello.radar.threat.ProjectileThreatTracker;
import com.mentalfrostbyte.jello.util.game.render.RenderUtil;
import com.mentalfrostbyte.jello.util.game.sound.RadarSoundPlayer;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.util.InputMappings;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.item.TNTEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.AbstractArrowEntity;
import net.minecraft.entity.projectile.DamagingProjectileEntity;
import net.minecraft.entity.projectile.EggEntity;
import net.minecraft.entity.projectile.ProjectileEntity;
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
 * <p>威胁检测（接口在 radar.threat 包，可替换实现）：
 * 敌锁定 {@link EnemyLockDetector} —— 末影人式视线判定，检测其他玩家是否正在瞄准我们；
 * 敌跟踪 {@link ProjectileThreatTracker} —— 投掷物弹道逐 tick 推演，预测是否命中我们。
 * 任一威胁激活时在 RWR 上方渲染闪烁的绿框警示牌（LOCKED / INBOUND，
 * 文字随 Color 调色盘、底色随 Background 开关），对应接触点套闪烁绿框，
 * 并抢占主威胁选取与锁定告警音。
 *
 * <p>音效（父模块 Sound 开关控制，资源在 res 的 audio 目录）：
 * 有目标处于近敌告警距离（Warning Distance）内，或存在敌锁定/敌跟踪威胁
 * → 循环播放锁定告警 radar_lock.mp3（抢占扫描音）；
 * 其余距离上存在目标 → 播放扫描提示音 radar_scan.mp3。
 * 告警状态带 400ms 保持时间，避免目标在告警距离边界抖动时来回切换音效。
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

    /** Alt+R 手动锁定框颜色（固定亮绿，不随主色变化以便区分） */
    private static final int LOCK_GREEN = 0xFF55FF55;

    /** TWS 扫描线拖影宽度（px） */
    private static final float SCAN_TRAIL_W = 30.0F;
    private static final int SCAN_TRAIL_LINES = 8;
    private static final float SCAN_TRAIL_LINE_GAP = 4.0F;
    private static final float MIN_SCAN_RATE = 0.05F;
    private static final float TWS_SCAN_HIT_PAD = 2.5F;
    private static final int TRANSLUCENT_BLACK_BG = 0xA80B0D0F;
    private static final int OWN_PROJECTILE_LAUNCH_TICKS = 8;
    private static final float OWN_PROJECTILE_LAUNCH_XZ_TOLERANCE = 0.5F;
    private static final float OWN_PROJECTILE_LAUNCH_Y_TOLERANCE = 0.5F;
    private static final double OWN_PROJECTILE_MIN_SPEED = 0.04D;
    private static final double OWN_PROJECTILE_LOOK_DOT = 0.65D;
    private static final double OWN_PROJECTILE_AWAY_DOT = 0.10D;
    /** 近敌告警解除后锁定音的保持时间（ms），吸收目标在告警距离边界的抖动 */
    private static final long LOCK_SOUND_HOLD_MS = 400L;

    // ===== 威胁警示牌（RWR 上方，敌锁定/敌跟踪激活时闪烁）=====
    private static final float BADGE_H = 15.0F;
    private static final float BADGE_PAD_X = 5.0F;
    private static final float BADGE_GAP = 6.0F;
    /** 警示牌底边相对 RWR 盘顶部的间距（往上偏移） */
    private static final float BADGE_MARGIN = 20.0F;

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
    private final Map<Integer, Entity> ownProjectiles = new HashMap<>();
    private float previousScanX = TWS_X;
    private int previousScanDirection = 1;
    private boolean previousScanValid = false;
    /** 最近一次存在近敌告警的时间戳（ms），配合 LOCK_SOUND_HOLD_MS 做音效保持 */
    private long lastLockDangerTime = 0L;

    /** 敌锁定检测器（可替换实现，接口见 radar.threat.EnemyLockDetector） */
    private final EnemyLockDetector lockDetector = new LookLineLockDetector();
    /** 敌跟踪（投掷物弹道预测）检测器（可替换实现，接口见 radar.threat.ProjectileThreatTracker） */
    private final ProjectileThreatTracker projectileTracker = new BallisticThreatTracker();

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
        boolean lock;        // 距离小于近敌告警距离（Warning Distance）
        boolean marked;      // Alt+R 手动锁定
        boolean aimLock;     // 敌锁定：该玩家视线正瞄准我们（EnemyLockDetector）
        float aimAngle;      // 其视线与我们连线的夹角（度）
        boolean incoming;    // 敌跟踪：预测该投掷物将命中我们（ProjectileThreatTracker）
        int impactTicks = -1; // 预测命中剩余 tick（-1 = 不命中）
    }

    public WarThunderRadar() {
        super(ModuleCategory.GUI, "WarThunder", "War Thunder style RWR + TWS radar");
    }

    @Override
    public void onDisable() {
        super.onDisable();
        RadarSoundPlayer.stop();
        lockDetector.reset();
        projectileTracker.reset();
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
        float warningDistance = parent.warningDistance.getCurrentValue();
        ScanState twsScan = getTwsScanState(time, parent.scanRate.getCurrentValue());
        boolean realisticTws = parent.realistic.getCurrentValue();

        // ===== 目标获取 =====
        List<Contact> contacts = scanContacts(range, warningDistance,
                parent.enemyLock.getCurrentValue(), parent.enemyTrack.getCurrentValue());
        Contact primary = getPrimaryThreat(contacts);

        // ===== Alt+R 手动锁定（按下边沿触发，锁定后保持）=====
        handleLockKey(contacts);
        // 标记被锁定的目标；若锁定实体已消失则清除
        boolean lockAlive = false;
        for (Contact c : contacts) {
            if (c.entityId == lockedEntityId) { c.marked = true; lockAlive = true; }
        }
        if (lockedEntityId != -1 && !lockAlive) {
            Entity le = mc.world.getEntityByID(lockedEntityId);
            if (le == null || !le.isAlive() || le.getDistance(mc.player) > range || isRejectedByAntiBot(le) || isOwnProjectile(le)) lockedEntityId = -1;
        }
        List<Contact> twsContacts = getTwsContacts(contacts, twsScan, realisticTws);

        // ===== 告警音效 =====
        // 近敌告警带 400ms 保持：目标在告警距离边界抖动或实体列表瞬时丢失时不来回切换音效
        if (hasLockDanger(contacts)) lastLockDangerTime = time;
        boolean lockTone = time - lastLockDangerTime < LOCK_SOUND_HOLD_MS;
        if (!parent.sound.getCurrentValue()) {
            RadarSoundPlayer.stop();
        } else if (lockTone) {
            // 锁定告警优先：掐断正在播的扫描提示音，立即循环锁定音
            RadarSoundPlayer.stop("radar_scan");
            RadarSoundPlayer.play("radar_lock", 700);
        } else if (!contacts.isEmpty()) {
            RadarSoundPlayer.stop("radar_lock");
            RadarSoundPlayer.play("radar_scan", 1600);
        } else {
            RadarSoundPlayer.stop("radar_lock");
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

    private List<Contact> scanContacts(float range, float warningDistance, boolean lockDetect, boolean trackDetect) {
        List<Contact> list = new ArrayList<>();
        pruneOwnProjectileCache();
        for (Entity e : mc.world.getAllEntities()) {
            if (e == mc.player || !e.isAlive()) continue;
            if (isOwnProjectile(e)) continue;
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
            // 近敌告警：距离判定。径向速度每 tick 抖动（目标瞬时停顿即翻正），
            // 不能作为告警条件，否则锁定音会被反复掐断；接近率仅用于主威胁选取与读数显示
            c.lock = dist < warningDistance;

            // 敌锁定：其他玩家的头部视线是否正对准我们（迟滞判定，见 LookLineLockDetector）
            if (lockDetect && e instanceof PlayerEntity) {
                LockAssessment aim = lockDetector.assess((LivingEntity) e, mc.player);
                c.aimLock = aim.locking;
                c.aimAngle = aim.aimAngle;
            }
            // 敌跟踪：投掷物弹道推演是否命中我们。敌我识别沿用 isOwnProjectile
            // 出生点/方向启发式（拿不到服务器 owner 数据），已在上方过滤自己丢出的弹体
            if (trackDetect && e instanceof ProjectileEntity) {
                ProjectileThreat threat = projectileTracker.assess((ProjectileEntity) e, mc.player);
                c.incoming = threat.incoming;
                c.impactTicks = threat.ticksToImpact;
            }
            list.add(c);
        }
        return list;
    }

    private static boolean hasLockDanger(List<Contact> contacts) {
        for (Contact c : contacts) {
            // 敌锁定（被瞄准）与敌跟踪（弹道命中）等同于被锁定，触发锁定告警音
            if (c.lock || c.aimLock || c.incoming) return true;
        }
        return false;
    }

    private static boolean isRejectedByAntiBot(Entity entity) {
        return Client.getInstance().botManager != null && Client.getInstance().botManager.isBot(entity);
    }

    private void pruneOwnProjectileCache() {
        Iterator<Map.Entry<Integer, Entity>> it = ownProjectiles.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Integer, Entity> entry = it.next();
            Entity entity = entry.getValue();
            if (entity == null || !entity.isAlive() || mc.world.getEntityByID(entry.getKey()) != entity) {
                it.remove();
            }
        }
    }

    private boolean isOwnProjectile(Entity entity) {
        if (!(entity instanceof ProjectileEntity) || mc.player == null) return false;

        int entityId = entity.getEntityId();
        Entity cached = ownProjectiles.get(entityId);
        if (cached == entity) return true;
        if (cached != null) ownProjectiles.remove(entityId);

        if (!isLikelyLocalLaunch(entity)) return false;
        ownProjectiles.put(entityId, entity);
        return true;
    }

    private boolean isLikelyLocalLaunch(Entity entity) {
        if (entity.ticksExisted > OWN_PROJECTILE_LAUNCH_TICKS) return false;

        double launchDx = entity.getPosX() - mc.player.getPosX();
        double launchDz = entity.getPosZ() - mc.player.getPosZ();
        if (Math.abs(launchDx) > OWN_PROJECTILE_LAUNCH_XZ_TOLERANCE
                || Math.abs(launchDz) > OWN_PROJECTILE_LAUNCH_XZ_TOLERANCE) {
            return false;
        }

        double minLaunchY = mc.player.getPosY() - OWN_PROJECTILE_LAUNCH_Y_TOLERANCE;
        double maxLaunchY = mc.player.getPosYEye() + OWN_PROJECTILE_LAUNCH_Y_TOLERANCE;
        if (entity.getPosY() < minLaunchY || entity.getPosY() > maxLaunchY) return false;

        Vector3d motion = entity.getMotion().subtract(mc.player.getMotion());
        if (motion.length() < OWN_PROJECTILE_MIN_SPEED) motion = entity.getMotion();
        double motionLen = motion.length();
        if (motionLen < OWN_PROJECTILE_MIN_SPEED) return false;

        Vector3d motionDir = motion.scale(1.0D / motionLen);
        Vector3d look = mc.player.getLook(1.0F);
        double lookLen = look.length();
        if (lookLen < 0.01D) return false;
        if (motionDir.dotProduct(look.scale(1.0D / lookLen)) < OWN_PROJECTILE_LOOK_DOT) return false;

        double horizontalOffset = Math.sqrt(launchDx * launchDx + launchDz * launchDz);
        if (horizontalOffset < 0.01D) return true;

        Vector3d horizontalMotion = new Vector3d(motionDir.x, 0.0D, motionDir.z);
        double horizontalMotionLen = horizontalMotion.length();
        if (horizontalMotionLen < 0.01D) return true;

        Vector3d horizontalLaunch = new Vector3d(launchDx / horizontalOffset, 0.0D, launchDz / horizontalOffset);
        return horizontalMotion.scale(1.0D / horizontalMotionLen).dotProduct(horizontalLaunch) > OWN_PROJECTILE_AWAY_DOT;
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

    /**
     * 主威胁选取，优先级从高到低：
     * 1. 敌跟踪 —— 预测将命中的投掷物中剩余时间最短者；
     * 2. 敌锁定 —— 正瞄准我们的玩家中距离最近者；
     * 3. 接近率最大（径向速度最负）的目标，与 HTML 原型逻辑一致。
     */
    private static Contact getPrimaryThreat(List<Contact> contacts) {
        Contact incoming = null;
        for (Contact c : contacts) {
            if (c.incoming && (incoming == null || c.impactTicks < incoming.impactTicks)) incoming = c;
        }
        if (incoming != null) return incoming;

        Contact spike = null;
        for (Contact c : contacts) {
            if (c.aimLock && (spike == null || c.distance < spike.distance)) spike = c;
        }
        if (spike != null) return spike;

        return getMaxClosingContact(contacts);
    }

    /** 接近率最大（径向速度最负）的目标 */
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
        copy.aimLock = source.aimLock;
        copy.aimAngle = source.aimAngle;
        copy.incoming = source.incoming;
        copy.impactTicks = source.impactTicks;
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
            // 敌锁定/敌跟踪威胁：闪烁绿框（与 RWR 上方警示牌同步闪烁）
            if ((c.aimLock || c.incoming) && isThreatBlinkOn(time)) {
                strokeRect(x - 8.5F, y - 8.5F, 17.0F, 17.0F, 1.8F, LOCK_GREEN);
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

        // RWR 上方威胁警示牌：敌锁定 LOCKED / 敌跟踪 INBOUND，闪烁 + 绿框包裹
        drawThreatBadges(contacts, time, bright, bg);

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

            // 锁定目标闪烁（与 HTML 一致的占空比）；手动锁定/敌锁定/敌跟踪目标常亮不闪
            // （威胁目标自身不隐藏，闪烁交给绿框，避免 INC 倒计时读数消失）
            if (c.lock && !c.marked && !c.aimLock && !c.incoming && MathHelper.sin(time * 0.018F) <= -0.45F) continue;

            float[] p = rwrContactPos(c, range);
            float cr = isPrimary ? 6.5F : 4.5F;
            int col = isPrimary ? bright : main;

            circle(p[0], p[1], cr, isPrimary ? 2.0F : 1.4F, col);
            // 敌锁定/敌跟踪威胁：闪烁绿框包裹（与警示牌同步闪烁）
            if ((c.aimLock || c.incoming) && isThreatBlinkOn(time)) {
                float thHalf = cr + 3.5F;
                strokeRect(p[0] - thHalf, p[1] - thHalf, thHalf * 2.0F, thHalf * 2.0F, 1.8F, LOCK_GREEN);
            }
            // Alt+R 手动锁定：额外套一层粗体绿框
            if (c.marked) {
                float half = cr + 6.0F;
                strokeRect(p[0] - half, p[1] - half, half * 2.0F, half * 2.0F, 2.6F, LOCK_GREEN);
            }
            drawCentered(font12, p[0], p[1] - cr - 13.0F, c.code, col);

            // 标签优先级：敌跟踪（含撞击倒计时）> 敌锁定 > 近敌/主威胁 LOCK
            if (c.incoming) {
                String inc = c.impactTicks >= 0
                        ? String.format(Locale.US, "INC %.1fs", c.impactTicks / 20.0F)
                        : "INC";
                drawCentered(font12, p[0], p[1] + cr + 2.0F, inc, LOCK_GREEN);
            } else if (c.aimLock) {
                drawCentered(font12, p[0], p[1] + cr + 2.0F, "SPK", LOCK_GREEN);
            } else if (c.lock || isPrimary) {
                drawCentered(font12, p[0], p[1] + cr + 2.0F, "LOCK", col);
            }
            if (isPrimary) {
                String cl = String.format(Locale.US, "%.1f CL", Math.abs(c.closingSpeed));
                drawCentered(font12, p[0], p[1] + cr + 13.0F, cl, soft);
            }
        }

        // 状态行（优先显示敌跟踪/敌锁定威胁）
        String status;
        if (primary != null && primary.incoming) {
            status = primary.impactTicks >= 0
                    ? String.format(Locale.US, "INBOUND %s %.1fs", primary.code, primary.impactTicks / 20.0F)
                    : String.format(Locale.US, "INBOUND %s", primary.code);
        } else if (primary != null && primary.aimLock) {
            status = String.format(Locale.US, "SPIKE %s %.0fm", primary.code, primary.distance);
        } else if (primary != null) {
            status = String.format(Locale.US, "PRI %s CLOSING %.1f", primary.code, Math.abs(primary.closingSpeed));
        } else {
            status = "RWR ACTIVE";
        }
        drawCentered(font12, RWR_CX, RWR_CY + RWR_R + 3.0F, status, main);
    }

    /** 威胁闪烁相位（警示牌与接触点绿框共用，保证同步）：约 65% 占空比 */
    private static boolean isThreatBlinkOn(long time) {
        return MathHelper.sin(time * 0.02F) > -0.45F;
    }

    /**
     * RWR 上方威胁警示牌：敌锁定 → LOCKED，敌跟踪 → INBOUND（可同时显示）。
     * 文字颜色随父模块 Color 调色盘，底色随 Background 开关（与面板底色一致），
     * 外层绿框包裹，随 {@link #isThreatBlinkOn} 闪烁。
     */
    private void drawThreatBadges(List<Contact> contacts, long time, int bright, int bg) {
        boolean anyAimLock = false;
        boolean anyIncoming = false;
        for (Contact c : contacts) {
            anyAimLock |= c.aimLock;
            anyIncoming |= c.incoming;
        }
        if (!anyAimLock && !anyIncoming) return;
        if (!isThreatBlinkOn(time)) return;

        List<String> labels = new ArrayList<>();
        if (anyAimLock) labels.add("LOCKED");
        if (anyIncoming) labels.add("INBOUND");

        float totalW = 0.0F;
        for (String label : labels) {
            totalW += font12.getWidth(label) + BADGE_PAD_X * 2.0F;
        }
        totalW += BADGE_GAP * (labels.size() - 1);

        float x = RWR_CX - totalW / 2.0F;
        float y = RWR_CY - RWR_R - BADGE_MARGIN - BADGE_H;
        for (String label : labels) {
            float w = font12.getWidth(label) + BADGE_PAD_X * 2.0F;
            fillRect(x, y, w, BADGE_H, bg);
            strokeRect(x, y, w, BADGE_H, 1.8F, LOCK_GREEN);
            drawCentered(font12, x + w / 2.0F, y + 1.0F, label, bright);
            x += w + BADGE_GAP;
        }
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
        // 面剔除只作用于填充图元：开着时背景矩形/圆盘会被整个剔除而线条照常显示。
        // RenderSystem 走 GlStateManager 缓存，再补一次裸调用防缓存与真实 GL 状态脱节
        RenderSystem.disableCull();
        GL11.glDisable(GL11.GL_CULL_FACE);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glEnable(GL11.GL_LINE_SMOOTH);
        GL11.glHint(GL11.GL_LINE_SMOOTH_HINT, GL11.GL_NICEST);
        GL11.glLineWidth(Math.max(1.0F, lineWidth * lineScale));
    }

    private static void postDraw() {
        GL11.glDisable(GL11.GL_LINE_SMOOTH);
        GL11.glDisable(GL11.GL_BLEND);
        RenderSystem.enableCull();
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
        // 负向扫角 → GUI 投影下的正面朝向，理由同 fillRect
        GL11.glBegin(GL11.GL_TRIANGLE_FAN);
        GL11.glVertex2f(cx, cy);
        for (int i = 0; i <= 60; i++) {
            double a = -Math.PI * 2.0 * i / 60.0;
            GL11.glVertex2f(cx + (float) Math.cos(a) * r, cy + (float) Math.sin(a) * r);
        }
        GL11.glEnd();
        postDraw();
    }

    private void fillRect(float x, float y, float w, float h, int color) {
        preDraw(1.0F);
        setColor(color);
        // GUI 正交投影（y 轴翻转）下需逆序发顶点才是正面朝向，与 RenderUtil.drawRect 一致
        GL11.glBegin(GL11.GL_QUADS);
        GL11.glVertex2f(x, y + h);
        GL11.glVertex2f(x + w, y + h);
        GL11.glVertex2f(x + w, y);
        GL11.glVertex2f(x, y);
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
