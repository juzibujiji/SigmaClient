package net.minecraft.util;

import com.mentalfrostbyte.jello.event.impl.player.movement.EventMoveInput;
import com.mentalfrostbyte.jello.event.impl.player.movement.EventMoveButton;
import com.mentalfrostbyte.jello.gui.base.JelloPortal;
import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import de.florianmichael.viamcp.fixes.PacketFixFor1_21Plus;
import net.minecraft.client.GameSettings;
import team.sdhq.eventBus.EventBus;

public class MovementInputFromOptions extends MovementInput {
    private final GameSettings gameSettings;

    public MovementInputFromOptions(GameSettings gameSettings) {
        this.gameSettings = gameSettings;
    }

    public void tickMovement(boolean forcedDown) {
        if (PacketFixFor1_21Plus.shouldUseGrimVanillaMovement()) {
            tickVanillaMovement(forcedDown);
            return;
        }

        moveForward = 0.0f;
        moveStrafe = 0.0f;

        final EventMoveButton eventMoveButton = new EventMoveButton(
                this.gameSettings.keyBindForward.isKeyDown(),
                this.gameSettings.keyBindBack.isKeyDown(),
                this.gameSettings.keyBindLeft.isKeyDown(),
                this.gameSettings.keyBindRight.isKeyDown(),
                this.gameSettings.keyBindJump.isKeyDown()
                        && (!this.gameSettings.keyBindSneak.isKeyDown()
                                || JelloPortal.getVersion().newerThan(ProtocolVersion.v1_14)),
                this.gameSettings.keyBindSneak.isKeyDown()
        );
        EventBus.call(eventMoveButton);

        this.forwardKeyDown = eventMoveButton.forward;
        this.backKeyDown = eventMoveButton.back;
        this.leftKeyDown = eventMoveButton.left;
        this.rightKeyDown = eventMoveButton.right;

        if (eventMoveButton.forward) {
            ++this.moveForward;
        }

        if (eventMoveButton.back) {
            --this.moveForward;
        }

        if (eventMoveButton.left) {
            ++this.moveStrafe;
        }

        if (eventMoveButton.right) {
            --this.moveStrafe;
        }

        this.jump = eventMoveButton.jump;
        this.sneaking = eventMoveButton.sneak;

        final EventMoveInput eventMoveInput = new EventMoveInput(this.moveForward, this.moveStrafe, this.jump, this.sneaking, 0.3F);
        EventBus.call(eventMoveInput);

        this.moveStrafe = eventMoveInput.strafe;
        this.moveForward = eventMoveInput.forward;

        this.jump = eventMoveInput.jumping;
        this.sneaking = eventMoveInput.sneaking;

        PacketFixFor1_21Plus.normalizeRaw1_21_5MovementInput(this);

        if (forcedDown) {
            this.moveStrafe *= eventMoveInput.sneakFactor;
            this.moveForward *= eventMoveInput.sneakFactor;
        }
    }

    private void tickVanillaMovement(boolean forcedDown) {
        this.moveForward = 0.0F;
        this.moveStrafe = 0.0F;
        this.forwardKeyDown = this.gameSettings.keyBindForward.isKeyDown();
        this.backKeyDown = this.gameSettings.keyBindBack.isKeyDown();
        this.leftKeyDown = this.gameSettings.keyBindLeft.isKeyDown();
        this.rightKeyDown = this.gameSettings.keyBindRight.isKeyDown();

        if (this.forwardKeyDown) {
            ++this.moveForward;
        }

        if (this.backKeyDown) {
            --this.moveForward;
        }

        if (this.leftKeyDown) {
            ++this.moveStrafe;
        }

        if (this.rightKeyDown) {
            --this.moveStrafe;
        }

        this.jump = this.gameSettings.keyBindJump.isKeyDown();
        this.sneaking = this.gameSettings.keyBindSneak.isKeyDown();
        PacketFixFor1_21Plus.normalizeRaw1_21_5MovementInput(this);

        if (forcedDown) {
            this.moveStrafe *= 0.3F;
            this.moveForward *= 0.3F;
        }
    }
}
