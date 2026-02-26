package com.mentalfrostbyte.jello.util.system.other;

import java.lang.reflect.InvocationTargetException;

import com.mentalfrostbyte.Client;
import com.mentalfrostbyte.jello.util.client.ClientMode;
import com.mentalfrostbyte.jello.event.impl.game.action.EventMouseHover;
import com.mentalfrostbyte.jello.gui.impl.jello.ingame.buttons.keybind.Bound;
import com.mentalfrostbyte.jello.managers.GuiManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.StringTextComponent;
import team.sdhq.eventBus.EventBus;

public class ModuleKeyPress {
    private static final Minecraft mc = Minecraft.getInstance();

    public static void press(int key) {
        if (Client.getInstance().clientMode != ClientMode.NOADDONS) {
            if (key != -1) {
                for (Bound bindType : Client.getInstance().moduleManager.getKeyManager().getBindedObjects(key)) {
                    if (bindType != null && bindType.hasTarget()) {
                        switch (bindType.getKeybindTypes()) {
                            case MODULE -> bindType.getModuleTarget().toggle();
                            case SCREEN -> {
                                try {
                                    Screen sigmaScreen = bindType.getScreenTarget()
                                            .getDeclaredConstructor(ITextComponent.class)
                                            .newInstance(new StringTextComponent(GuiManager.screenToScreenName.get(bindType.getScreenTarget())));
                                    if (Client.getInstance().guiManager.hasReplacement(sigmaScreen)) {
                                        mc.displayGuiScreen(sigmaScreen);
                                    }
                                } catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException |
                                         NoSuchMethodException | SecurityException | InstantiationException exc) {
                                    exc.printStackTrace();
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    public static void listen(int button) {
        EventMouseHover eventMouseHover = new EventMouseHover(button);
        EventBus.call(eventMouseHover);
    }
}
