package com.mentalfrostbyte.jello.util.game.render;

import com.mentalfrostbyte.Client;
import com.mentalfrostbyte.jello.module.Draggable;
import com.mentalfrostbyte.jello.module.Module;

import java.util.List;

/**
 * 通用的 HUD 拖拽管理器。
 * <p>
 * 通过 {@link Draggable} 接口统一处理所有可拖拽 HUD 模块（TargetHUD、歌词等）
 * 的鼠标拖拽定位。{@link #click} 会遍历所有已注册模块，筛选出 {@code Draggable}
 * 实例并做命中测试，命中谁就拖谁。
 * <p>
 * 新模块只需继承 {@link com.mentalfrostbyte.jello.module.RenderModule}
 * 或实现 {@link Draggable} 即可自动获得拖拽能力，无需修改本类。
 */
public class ChatChangeXY {
    public static ChatChangeXY in = new ChatChangeXY();
    public Draggable hoverModule = null;
    public double x = 100;
    public double y = 20;
    double lastClickX, lastClickY, startX, startY;
    boolean dragging = false;

    public void release() {
        dragging = false;
    }

    public void init() {
        dragging = false;
    }

    public void render(double mouseX, double mouseY) {
        if (hoverModule == null) {
            dragging = false;
            return;
        }
        if (dragging) {
            x = lastClickX + mouseX - startX;
            y = lastClickY + mouseY - startY;
            hoverModule.setX((float) x);
            hoverModule.setY((float) y);
        }
    }

    public void click(double mouseX, double mouseY) {
        dragging = false;
        List<Module> modules = Client.getInstance().moduleManager.getModules();
        if (modules != null) {
            for (Module module : modules) {
                if (module instanceof Draggable) {
                    Draggable draggable = (Draggable) module;
                    if (draggable.isHover(mouseX, mouseY)) {
                        hoverModule = draggable;
                        dragging = true;
                        break;
                    }
                }
            }
        }
        if (dragging) {
            startX = mouseX;
            startY = mouseY;
            lastClickX = hoverModule.getX();
            lastClickY = hoverModule.getY();
        }
    }
}
