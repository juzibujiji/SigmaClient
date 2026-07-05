package com.mentalfrostbyte.jello.module.impl.gui.jello;

import com.mentalfrostbyte.jello.managers.GuiManager;
import com.mentalfrostbyte.jello.module.Draggable;
import com.mentalfrostbyte.jello.module.data.ModuleCategory;
import com.mentalfrostbyte.jello.module.data.ModuleWithModuleSettings;
import com.mentalfrostbyte.jello.module.impl.gui.jello.targethud.ClassicTargetHUD;
import com.mentalfrostbyte.jello.module.impl.gui.jello.targethud.GeminiStyleJello;
import com.mentalfrostbyte.jello.module.impl.gui.jello.targethud.JelloTargetHUD;
import com.mentalfrostbyte.jello.module.settings.impl.NumberSetting;
import net.minecraft.client.Minecraft;

/**
 * TargetHUD 父模块，采用 ModuleWithModuleSettings 架构（与 NoFall / Disabler 一致）。
 * <p>
 * 通过 "Style" 下拉切换不同的渲染样式，每个样式是 {@link com.mentalfrostbyte.jello.module.RenderModule}
 * 子模块，位于 {@code targethud} 子包下，各自实现渲染逻辑。
 * <p>
 * 坐标 (X/Y) 由父模块统一管理，便于 ClickGui 拖拽定位与配置持久化。
 */
public class TargetHUD extends ModuleWithModuleSettings implements Draggable {

    public NumberSetting<Float> x = new NumberSetting<>("X", "X", 0, 0, 10000, 10) {
        /*@Override
        public boolean isHidden() {
            return true;
        }*/
    };

    public NumberSetting<Float> y = new NumberSetting<>("Y", "Y", 0, 0, 10000, 10) {
        /*@Override
        public boolean isHidden() {
            return true;
        }*/
    };

    public NumberSetting<Float> scale = new NumberSetting<>("Scale", "HUD scale", 1.0F, 0.5F, 2.0F, 0.1F);

    public NumberSetting<Float> opacity = new NumberSetting<>("Opacity", "Panel opacity", 1.0F, 0.1F, 1.0F, 0.05F);

    public NumberSetting<Float> panelAlpha = new NumberSetting<>("PanelAlpha", "Background transparency", 0.65F, 0.0F, 1.0F, 0.05F);

    public TargetHUD() {
        super(ModuleCategory.GUI, "TargetHud", "Target HUD", "Style",
                new JelloTargetHUD(),
                new ClassicTargetHUD(),
                new GeminiStyleJello()
        );
        this.registerSetting(x, y, scale, opacity, panelAlpha);
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
     * renderWatermark 的 GL 缩放系数：1/guiScale * GuiManager.scaleFactor。
     * 绘制坐标在渲染空间，鼠标坐标在标准 GUI 空间，两者相差此系数。
     */
    public static float getRenderScale() {
        return (1.0F / (float) Minecraft.getInstance().getMainWindow().getGuiScaleFactor()) * GuiManager.scaleFactor;
    }

    /**
     * 判断鼠标是否悬停在 TargetHUD 区域上，供聊天栏拖拽使用。
     * 鼠标坐标是标准 GUI 空间，面板绘制在渲染空间（经 renderWatermark 缩放），
     * 需要将鼠标坐标除以渲染缩放系数转换到渲染空间后再比较。
     */
    public boolean isHover(double mx, double my) {
        float rs = getRenderScale();
        double mxR = mx / rs;
        double myR = my / rs;
        float s = this.scale.getCurrentValue();
        float xPos = this.x.getCurrentValue();
        float yPos = this.y.getCurrentValue();
        float width = 220.0F * s;
        float height = 90.0F * s;
        return mxR >= xPos && mxR <= xPos + width && myR >= yPos && myR <= yPos + height;
    }

    public static boolean isClickable(double x, double y, double dx, double dy, double mx, double my) {
        return mx >= x && mx <= dx && my >= y && my <= dy;
    }
}
