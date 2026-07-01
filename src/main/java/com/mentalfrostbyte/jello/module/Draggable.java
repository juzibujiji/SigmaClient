package com.mentalfrostbyte.jello.module;

/**
 * 可拖拽 HUD 模块的统一契约。
 * <p>
 * 任何需要通过鼠标拖动定位的 HUD 模块（TargetHUD、歌词、KeyStrokes 等）
 * 都应实现此接口，{@link com.mentalfrostbyte.jello.util.game.render.ChatChangeXY}
 * 会自动发现并处理所有 {@code Draggable} 实例的拖拽逻辑。
 */
public interface Draggable {

    float getX();

    float getY();

    void setX(float v);

    void setY(float v);

    /**
     * 判断鼠标坐标是否悬停在此模块的可拖拽区域内。
     *
     * @param mx 鼠标 X 坐标
     * @param my 鼠标 Y 坐标
     * @return 是否命中
     */
    boolean isHover(double mx, double my);
}
