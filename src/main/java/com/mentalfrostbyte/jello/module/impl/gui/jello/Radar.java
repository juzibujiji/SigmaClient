package com.mentalfrostbyte.jello.module.impl.gui.jello;

import com.mentalfrostbyte.jello.module.Draggable;
import com.mentalfrostbyte.jello.module.data.ModuleCategory;
import com.mentalfrostbyte.jello.module.data.ModuleWithModuleSettings;
import com.mentalfrostbyte.jello.module.impl.gui.jello.radar.WarThunderRadar;
import com.mentalfrostbyte.jello.module.settings.impl.BooleanSetting;
import com.mentalfrostbyte.jello.module.settings.impl.ColorSetting;
import com.mentalfrostbyte.jello.module.settings.impl.NumberSetting;

/**
 * Radar 父模块，架构与 {@link TargetHUD} 一致（ModuleWithModuleSettings + Draggable）。
 * <p>
 * 通过 "Mode" 下拉切换雷达样式，当前实现 {@link WarThunderRadar}（战争雷霆风格
 * RWR 告警盘 + TWS 相控阵扫描屏）。
 * <p>
 * 设置：
 * <ul>
 *   <li>X / Y / Scale — 位置与缩放，坐标由父模块统一管理，支持 ClickGui 拖拽</li>
 *   <li>Color — 雷达主色（ColorSetting 调色盘，支持 rainbow）</li>
 *   <li>Range — 雷达探测半径（格）</li>
 *   <li>Warning Distance — 近敌告警距离（格），目标进入该距离触发锁定告警</li>
 *   <li>Background — 是否在 RWR/TWS 背后绘制半透明黑底</li>
 *   <li>Sound — 告警音效开关：目标在告警距离内播放锁定告警，其余距离播放扫描提示音</li>
 * </ul>
 * <p>
 * 快捷键 Alt+R：按下时锁定/切换准星最近的目标，被锁定目标在 RWR 与 TWS 中额外套一层粗体绿框。
 */
public class Radar extends ModuleWithModuleSettings implements Draggable {

    /** WarThunderRadar 面板的逻辑尺寸（缩放前），isHover 命中测试用 */
    public static final float PANEL_W = 300.0F;
    public static final float PANEL_H = 192.0F;

    public NumberSetting<Float> x = new NumberSetting<>("X", "X", 0, 0, 10000, 10);
    public NumberSetting<Float> y = new NumberSetting<>("Y", "Y", 0, 0, 10000, 10);
    public NumberSetting<Float> scale = new NumberSetting<>("Scale", "Radar scale", 1.0F, 0.5F, 2.0F, 0.1F);
    public NumberSetting<Float> range = new NumberSetting<>("Range", "Detection range in blocks", 32.0F, 8.0F, 64.0F, 4.0F);
    public NumberSetting<Float> warningDistance = new NumberSetting<>("Warning Distance", "Distance for close threat warning", 5.0F, 1.0F, 16.0F, 0.5F);
    public NumberSetting<Float> scanRate = new NumberSetting<>("Scan Rate", "TWS one-way scan sweeps per second", 0.55F, 0.10F, 2.00F, 0.05F);
    public BooleanSetting realistic = new BooleanSetting("Realistic", "TWS contacts update only when the scan line reaches them", false);
    public ColorSetting color = new ColorSetting("Color", "Radar display color", 0xFF61FF6A);
    public BooleanSetting background = new BooleanSetting("Background", "Draw translucent black backdrop behind RWR/TWS", true);
    public BooleanSetting sound = new BooleanSetting("Sound", "Play lock/scan warning sounds", true);

    public Radar() {
        super(ModuleCategory.GUI, "Radar", "Aircraft style threat radar", "Mode",
                new WarThunderRadar()
        );
        this.registerSetting(x, y, scale, range, warningDistance, scanRate, realistic, color, background, sound);
    }

    public void setX(float v) {
        this.x.currentValue = v;
    }

    public void setY(float v) {
        this.y.currentValue = v;
    }

    public float getX() {
        return this.x.currentValue;
    }

    public float getY() {
        return this.y.currentValue;
    }

    /**
     * 命中测试逻辑与 {@link TargetHUD#isHover} 相同：鼠标坐标在标准 GUI 空间，
     * 面板绘制在渲染空间，需除以渲染缩放系数后再比较。
     */
    @Override
    public boolean isHover(double mx, double my) {
        float rs = TargetHUD.getRenderScale();
        double mxR = mx / rs;
        double myR = my / rs;
        float s = this.scale.getCurrentValue();
        float xPos = this.x.getCurrentValue();
        float yPos = this.y.getCurrentValue();
        return mxR >= xPos && mxR <= xPos + PANEL_W * s && myR >= yPos && myR <= yPos + PANEL_H * s;
    }
}
