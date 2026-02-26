package com.mentalfrostbyte.jello.gui.impl.jello.ingame.options.buttons;

import com.mentalfrostbyte.Client;
import com.mentalfrostbyte.jello.util.client.ClientMode;
import com.mentalfrostbyte.jello.gui.impl.jello.ingame.holders.JelloOptionsHolder;
import com.mojang.blaze3d.matrix.MatrixStack;
import net.minecraft.client.gui.screen.IngameMenuScreen;
import net.minecraft.client.gui.widget.Widget;
import net.minecraft.client.gui.widget.button.Button;
import net.minecraft.util.text.StringTextComponent;

import java.io.IOException;
import java.util.Date;
import java.util.Iterator;

public class JelloOptionsButton extends IngameMenuScreen {
    public static Date field4622 = new Date(0L);

    public JelloOptionsButton() {
        super(true);
        if (field4622.before(new Date(System.currentTimeMillis() - 3000L))) {
            field4622 = new Date();
            Client.logger.info("Saving profiles...");

            try {
                Client.getInstance().moduleManager.getConfigurationManager().saveAndReplaceConfigs();
                Client.getInstance().saveClientData();
            } catch (IOException var4) {
                Client.logger.warn("Unable to save mod profiles...", var4);
            }
        }
    }

    @Override
    public void init() {
        if (Client.getInstance().clientMode == ClientMode.JELLO) {
            this.addButton(
                    new Button(
                            this.width / 2 - 102,
                            this.height - 45,
                            204,
                            20,
                            new StringTextComponent("Jello for Sigma Options"),
                            var1 -> this.minecraft.displayGuiScreen(new JelloOptionsHolder())
                    )
            );
        }

        super.init();
        Iterator var3 = this.buttons.iterator();

        while (var3.hasNext()) {
            Widget var4 = (Widget) var3.next();
            if (var4.y == this.height / 4 + 72 + -16) {
                var3.remove();
            }
        }
    }

    @Override
    public void render(MatrixStack matrices, int var2, int var3, float delta) {
        super.render(matrices, var2, var3, delta);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
