package com.mentalfrostbyte.jello.gui.impl.jello.ingame.panels;

import com.mentalfrostbyte.jello.gui.base.animations.Animation;
import com.mentalfrostbyte.jello.gui.base.elements.Element;
import com.mentalfrostbyte.jello.gui.base.elements.impl.button.Button;
import com.mentalfrostbyte.jello.gui.base.elements.impl.button.types.TextButton;
import com.mentalfrostbyte.jello.gui.combined.CustomGuiScreen;
import com.mentalfrostbyte.jello.gui.impl.jello.buttons.TextField;
import com.mentalfrostbyte.jello.gui.impl.jello.ingame.panels.others.BadgeSelect;
import com.mentalfrostbyte.jello.util.client.render.ResourceRegistry;
import com.mentalfrostbyte.jello.util.client.render.Resources;
import com.mentalfrostbyte.jello.util.client.render.theme.ClientColors;
import com.mentalfrostbyte.jello.util.client.render.theme.ColorHelper;
import com.mentalfrostbyte.jello.util.game.render.RenderUtil;
import com.mentalfrostbyte.jello.util.game.render.RenderUtil2;
import com.mentalfrostbyte.jello.util.system.math.smoothing.EasingFunctions;
import com.mentalfrostbyte.jello.util.system.math.smoothing.QuadraticEasing;
import net.minecraft.client.Minecraft;
import net.minecraft.util.math.vector.Vector3i;
import org.lwjgl.opengl.GL11;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class WaypointPanel extends Element {
    private final List<Button> field20723 = new ArrayList<Button>();
    private final Date field20724;
    private boolean field20725 = false;
    private Date field20726;
    public Vector3i field20727;
    public TextField field20728;
    public TextField field20729;
    public BadgeSelect field20730;
    private final List<Class9073> field20731 = new ArrayList<Class9073>();

    public WaypointPanel(CustomGuiScreen var1, String var2, int var3, int var4, Vector3i var5) {
        super(var1, var2, var3 - 107, var4 + 10, 214, 170, ColorHelper.field27961, "", false);
        this.field20727 = var5;
        if (this.yA + this.heightA <= Minecraft.getInstance().getMainWindow().getHeight()) {
            this.yA += 10;
        } else {
            this.yA = this.yA - (this.heightA + 27);
            this.field20725 = true;
        }

        this.field20724 = new Date();
        this.setReAddChildren(true);
        this.setListening(false);
        TextButton var8;
        this.addToList(
                var8 = new TextButton(
                        this,
                        "addButton",
                        this.widthA - 66,
                        this.heightA - 60,
                        ResourceRegistry.JelloLightFont25.getWidth("Add"),
                        50,
                        ColorHelper.field27961,
                        "Add",
                        ResourceRegistry.JelloLightFont25
                )
        );
        var8.onClick((var1x, var2x) -> this.method13132(this.field20729.getText(), this.method13130(), this.field20730.field21296));
        this.addToList(this.field20729 = new TextField(this, "Name", 20, 7, this.widthA - 40, 60, TextField.field20741, "My waypoint", "My waypoint"));
        this.field20729.method13148();
        this.field20729.setRoundedThingy(false);
        this.addToList(this.field20730 = new BadgeSelect(this, "badgeSelect", 0, 86));
        this.addToList(
                this.field20728 = new TextField(
                        this,
                        "Coords",
                        20,
                        this.heightA - 44,
                        this.widthA - 100,
                        20,
                        TextField.field20741,
                        var5.getX() + " " + var5.getZ(),
                        var5.getX() + " " + var5.getZ()
                )
        );
        this.field20728.setRoundedThingy(false);
        this.field20728.setFont(ResourceRegistry.JelloLightFont18);
    }

    public Vector3i method13130() {
        if (this.field20728.getText() != null && this.field20728.getText().contains(" ")) {
            String[] var3 = this.field20728.getText().split(" ");
            if (var3.length == 2 && var3[0].matches("-?\\d+") && var3[1].matches("-?\\d+")) {
                int var4 = Integer.valueOf(var3[0]);
                int var5 = Integer.valueOf(var3[1]);
                return new Vector3i(var4, 0, var5);
            }
        }

        return this.field20727;
    }

    @Override
    public void draw(float partialTicks) {
        partialTicks = Animation.calculateProgressWithReverse(this.field20724, this.field20726, 250.0F, 120.0F);
        float var4 = EasingFunctions.easeOutBack(partialTicks, 0.0F, 1.0F, 1.0F);
        this.method13279(0.8F + var4 * 0.2F, 0.8F + var4 * 0.2F);
        this.method13284((int) ((float) this.widthA * 0.2F * (1.0F - var4)) * (!this.field20725 ? 1 : -1));
        super.method13224();
        int var5 = 10;
        int var6 = RenderUtil2.applyAlpha(-723724, QuadraticEasing.easeOutQuad(partialTicks, 0.0F, 1.0F, 1.0F));
        RenderUtil.drawRoundedRect(
                (float) (this.xA + var5 / 2),
                (float) (this.yA + var5 / 2),
                (float) (this.widthA - var5),
                (float) (this.heightA - var5),
                35.0F,
                partialTicks
        );
        RenderUtil.drawRoundedRect(
                (float) (this.xA + var5 / 2),
                (float) (this.yA + var5 / 2),
                (float) (this.xA - var5 / 2 + this.widthA),
                (float) (this.yA - var5 / 2 + this.heightA),
                RenderUtil2.applyAlpha(ClientColors.DEEP_TEAL.getColor(), partialTicks * 0.25F)
        );
        RenderUtil.drawRoundedRect((float) this.xA, (float) this.yA, (float) this.widthA, (float) this.heightA, (float) var5, var6);
        GL11.glPushMatrix();
        GL11.glTranslatef((float) this.xA, (float) this.yA, 0.0F);
        GL11.glRotatef(!this.field20725 ? -90.0F : 90.0F, 0.0F, 0.0F, 1.0F);
        GL11.glTranslatef((float) (-this.xA), (float) (-this.yA), 0.0F);
        RenderUtil.drawImage(
                (float) (this.xA + (!this.field20725 ? 0 : this.heightA)),
                (float) this.yA + (float) ((this.widthA - 47) / 2) * (!this.field20725 ? 1.0F : -1.58F),
                18.0F,
                47.0F,
                Resources.selectPNG,
                var6
        );
        GL11.glPopMatrix();
        RenderUtil.drawRoundedRect(
                (float) (this.xA + 25),
                (float) (this.yA + 68),
                (float) (this.xA + this.widthA - 25),
                (float) (this.yA + 69),
                RenderUtil2.applyAlpha(ClientColors.DEEP_TEAL.getColor(), 0.05F * partialTicks)
        );
        super.draw(partialTicks);
    }

    public final void method13131(Class9073 var1) {
        this.field20731.add(var1);
    }

    public final void method13132(String var1, Vector3i var2, int var3) {
        for (Class9073 var7 : this.field20731) {
            var7.method33814(this, var1, var2, var3);
        }
    }

    public interface Class9073 {
       void method33814(WaypointPanel var1, String var2, Vector3i var3, int var4);
    }
}
