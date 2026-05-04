package com.mentalfrostbyte.jello.util.game.render;

import com.mentalfrostbyte.Client;
import com.mentalfrostbyte.jello.module.RenderModule;
import com.mentalfrostbyte.jello.module.impl.gui.jello.TargetHUD;

public class ChatChangeXY {
    public static ChatChangeXY in = new ChatChangeXY();
    public RenderModule hoverModule = null;
    public double x = 100;
    public double y = 20;
    double lastClickX, lastClickY, startX, startY;
    boolean dragging = false;
    public void release(){
        dragging = false;
    }
    public void init(){
        dragging = false;
    }
    public void render(double mouseX, double mouseY){
        if(hoverModule == null) {
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
    public void click(double mouseX, double mouseY){
        dragging = false;
        if(((TargetHUD) Client.getInstance().moduleManager.getModuleByClass(TargetHUD.class)).isHover(mouseX, mouseY) && !dragging){
            hoverModule = ((TargetHUD)Client.getInstance().moduleManager.getModuleByClass(TargetHUD.class));
            dragging = true;
        }
        if(dragging){
            startX = mouseX;
            startY = mouseY;
            lastClickX = hoverModule.getX();
            lastClickY = hoverModule.getY();
        }
    }
}
