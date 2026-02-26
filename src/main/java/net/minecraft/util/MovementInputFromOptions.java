package net.minecraft.util;

import com.mentalfrostbyte.jello.event.impl.player.movement.EventMoveInput;
import com.mentalfrostbyte.jello.event.impl.player.movement.EventMoveButton;
import net.minecraft.client.GameSettings;
import team.sdhq.eventBus.EventBus;

public class MovementInputFromOptions extends MovementInput {
    private final GameSettings gameSettings;

    public MovementInputFromOptions(GameSettings gameSettings) {
        this.gameSettings = gameSettings;
    }

    public void tickMovement(boolean forcedDown) {
        moveForward = 0.0f;
        moveStrafe = 0.0f;

        final EventMoveButton eventMoveButton = new EventMoveButton(
                this.gameSettings.keyBindForward.isKeyDown(),
                this.gameSettings.keyBindBack.isKeyDown(),
                this.gameSettings.keyBindLeft.isKeyDown(),
                this.gameSettings.keyBindRight.isKeyDown(),
                this.gameSettings.keyBindJump.isKeyDown(),
                this.gameSettings.keyBindSneak.isKeyDown()
        );
        EventBus.call(eventMoveButton);

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

        if (this.sneaking) {
            this.moveStrafe *= eventMoveInput.sneakFactor;
            this.moveForward *= eventMoveInput.sneakFactor;
        }
    }
}
