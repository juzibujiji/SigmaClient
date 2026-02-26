package com.mentalfrostbyte.jello.module.impl.world.disabler;

import com.mentalfrostbyte.jello.event.impl.game.network.EventSendPacket;
import com.mentalfrostbyte.jello.gui.impl.others.ChatUtil;
import com.mentalfrostbyte.jello.module.Module;
import com.mentalfrostbyte.jello.module.data.ModuleCategory;
import com.mentalfrostbyte.jello.event.impl.game.network.EventReceivePacket;
import com.mentalfrostbyte.jello.module.settings.impl.*;
import com.mentalfrostbyte.jello.util.PacketCollectorUtil;
import net.minecraft.network.play.client.CEntityActionPacket;
import net.minecraft.network.play.client.CPlayerDiggingPacket;
import team.sdhq.eventBus.annotations.EventTarget;

import java.util.Arrays;

public class CustomDisabler  extends Module {

    public CustomDisabler() {
        super(ModuleCategory.EXPLOIT, "Custom", "Customizable disabler ");

        this.registerSetting(
                new BooleanSetting(
                        "Log cancelled packets",
                        "Logs what type of packets were cancelled",
                        false
                )
        );

        Arrays.stream(PacketCollectorUtil.getPacketClasses("Outgoing")).sorted()
                .map(packetClass -> new BooleanSetting(packetClass, "Cancel Outgoing " + packetClass, false))
                .forEach(this::registerSetting);

        Arrays.stream(PacketCollectorUtil.getPacketClasses("Incoming")).sorted()
                .map(packetClass -> new BooleanSetting(packetClass, "Cancel Incoming " + packetClass, false))
                .forEach(this::registerSetting);

        Arrays.stream(CEntityActionPacket.Action.values())
                .map(action -> new BooleanSetting(action.name(), "Cancel CEntityActionPacket " + action.name(), false))
                .forEach(this::registerSetting);

        Arrays.stream(CPlayerDiggingPacket.Action.values())
                .map(action -> new BooleanSetting(action.name(), "Cancel CPlayerDiggingPacket " + action.name(), false))
                .forEach(this::registerSetting);

    }

    public void logCancelled(String name, String type) {
        ChatUtil.printMessage("Cancelling " + type + ": " + name);
    }

    @EventTarget
    public void onRecievePacket(EventReceivePacket event) {
        String packetName = event.packet.getClass().getSimpleName();
        if (this.getBooleanValueFromSettingName(packetName)) {
            logCancelled(packetName, "INCOMING packet");
            event.cancelled = true;
        }
    }

    @EventTarget
    public void onSendPacket(EventSendPacket event) {
        String packetName = event.packet.getClass().getSimpleName();
        if (this.getBooleanValueFromSettingName(packetName)) {
            logCancelled(packetName, "OUTGOING packet");
            event.cancelled = true;
        }


        if (event.packet instanceof CPlayerDiggingPacket diggingPacket) {
            String actionName = diggingPacket.getAction().name();
            if (this.getBooleanValueFromSettingName(actionName)) {
                logCancelled(actionName, "CPlayerDiggingPacket action");
                ChatUtil.printMessage("Cancelling CPlayerDiggingPacket action: " + actionName);
                event.cancelled = true;
            }
        }

        if (event.packet instanceof CEntityActionPacket entityAction) {
            String actionName = entityAction.getAction().name();
            if (this.getBooleanValueFromSettingName(actionName)) {
                ChatUtil.printMessage("Cancelling CEntityActionPacket action: " + actionName);
                event.cancelled = true;
            }
        }
    }
}

