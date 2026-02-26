package com.mentalfrostbyte.jello.gui.impl.classic.altmanager;

import com.mentalfrostbyte.Client;
import com.mentalfrostbyte.jello.gui.combined.CustomGuiScreen;
import com.mentalfrostbyte.jello.gui.impl.classic.altmanager.submenus.AddAltScreen;
import com.mentalfrostbyte.jello.gui.impl.classic.altmanager.submenus.DirectLoginScreen;
import com.mentalfrostbyte.jello.gui.impl.classic.altmanager.submenus.EditAltScreen;
import com.mentalfrostbyte.jello.gui.base.elements.impl.button.types.AltManagerButton;
import com.mentalfrostbyte.jello.gui.base.elements.impl.altmanager.Account;
import com.mentalfrostbyte.jello.util.client.render.theme.ClientColors;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screen.MainMenuHolder;

import java.util.Random;

public class ClassicAltScreenGroup extends CustomGuiScreen {
    public AltManagerButton field21140;
    public AltManagerButton field21141;
    public AltManagerButton field21142;
    public AltManagerButton field21143;
    public AltManagerButton field21144;
    public AltManagerButton field21145;
    public AltManagerButton field21146;
    public AltManagerButton field21147;

    public ClassicAltScreenGroup(CustomGuiScreen var1, String var2, int var3, int var4) {
        super(var1, var2, var3, var4, 760, 87);
        this.addToList(this.field21140 = new AltManagerButton(this, "reload", 0, 0, 120, 40, "Reload", ClientColors.DEEP_TEAL.getColor()));
        this.addToList(this.field21141 = new AltManagerButton(this, "back", 0, 48, 120, 40, "Back", ClientColors.DEEP_TEAL.getColor()));
        int var7 = 200;
        int var8 = 146;
        int var9 = this.getWidthA() - 625;
        this.addToList(this.field21142 = new AltManagerButton(this, "login", var9, 0, var7, 40, "Login", ClientColors.DEEP_TEAL.getColor()));
        this.addToList(this.field21145 = new AltManagerButton(this, "direct", var9 + var7 + 16, 0, var7, 40, "Direct Login", ClientColors.DEEP_TEAL.getColor()));
        this.addToList(this.field21147 = new AltManagerButton(this, "add", var9 + var7 * 2 + 32, 0, var7, 40, "Add", ClientColors.DEEP_TEAL.getColor()));
        this.addToList(this.field21143 = new AltManagerButton(this, "random", var9, 48, var8, 40, "Random", ClientColors.DEEP_TEAL.getColor()));
        this.addToList(this.field21144 = new AltManagerButton(this, "remove", var9 + var8 + 16, 48, var8, 40, "Remove", ClientColors.DEEP_TEAL.getColor()));
        this.addToList(this.field21146 = new AltManagerButton(this, "edit", var9 + var8 * 2 + 32, 48, var8, 40, "Edit", ClientColors.DEEP_TEAL.getColor()));
        ClassicAltScreen var10 = (ClassicAltScreen) this.getParent();
        this.field21143
                .onClick(
                        (var1x, var2x) -> {
                            com.mentalfrostbyte.jello.managers.util.account.microsoft.Account var5 = Client.getInstance()
                                    .accountManager
                                    .getAccounts()
                                    .get(new Random().nextInt(Client.getInstance().accountManager.getAccounts().size()));
                            var10.method13399(var5);
                        }
                );
        this.field21141.onClick((var0, var1x) -> Minecraft.getInstance().displayGuiScreen(new MainMenuHolder()));
        this.field21142.onClick((var1x, var2x) -> var10.method13396());
        this.field21144.onClick((var1x, var2x) -> var10.method13397());
        this.field21146.onClick((var1x, var2x) -> {
            Account var5 = var10.method13406();
            if (var5 != null) {
                Client.getInstance().guiManager.handleScreen(new EditAltScreen(var5.field21249));
            }
        });
        this.field21147.onClick((var0, var1x) -> Client.getInstance().guiManager.handleScreen(new AddAltScreen()));
        this.field21145.onClick((var0, var1x) -> Client.getInstance().guiManager.handleScreen(new DirectLoginScreen()));
    }

    @Override
    public void setHovered(boolean hovered) {
        this.field21144.setHovered(hovered);
        this.field21146.setHovered(hovered);
        this.field21142.setHovered(hovered);
    }

    public boolean method13469() {
        return this.field21144.isHovered();
    }
}