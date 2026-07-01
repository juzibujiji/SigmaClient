package com.mentalfrostbyte.jello.module;

import com.mentalfrostbyte.jello.module.data.ModuleCategory;

public class RenderModule extends Module implements Draggable {
    public float getX(){
        return 0;
    }
    public float getY(){
        return 0;
    }
    public void setX(float v){
    }
    public void setY(float v){
    }
    @Override
    public boolean isHover(double mx, double my) {
        return false;
    }
    public RenderModule(ModuleCategory category, String name, String desc) {
        super(category,name, desc);
    }
}
