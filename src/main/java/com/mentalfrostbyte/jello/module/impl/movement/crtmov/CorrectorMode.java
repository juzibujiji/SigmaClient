package com.mentalfrostbyte.jello.module.impl.movement.crtmov;

import com.mentalfrostbyte.Client;
import com.mentalfrostbyte.jello.module.Module;
import com.mentalfrostbyte.jello.module.data.ModuleCategory;
import com.mentalfrostbyte.jello.module.impl.movement.CorrectMovement;
import com.mentalfrostbyte.jello.util.game.player.rotation.RotationCore;

/**
 * Shared gating for the CorrectMovement modes.
 *
 * <p>Each mode owns the whole correction for its mode: it writes the outgoing motion packet,
 * records {@link RotationCore#lastYaw} / {@link RotationCore#lastPitch}, corrects movement
 * input, jump direction and {@code moveRelative}, and applies the FixLook substitution.
 * Nothing else in the client does any of that any more.
 *
 * <p>Event handlers must be declared on the concrete mode rather than here: the event bus
 * registers via {@code getClass().getDeclaredMethods()}, which does not see inherited methods.
 */
public abstract class CorrectorMode extends Module {

    protected CorrectorMode(String name, String description) {
        super(ModuleCategory.MOVEMENT, name, description);
    }

    protected final CorrectMovement getCorrectMovement() {
        return (CorrectMovement) Client.getInstance()
                .moduleManager
                .getModuleByClass(CorrectMovement.class);
    }

    /**
     * True when CorrectMovement is on and this instance is the selected mode.
     *
     * <p>A mode cannot infer this from being registered on the event bus.
     * {@code ModuleWithModuleSettings} registers every mode in its constructor and only
     * unregisters the inactive ones once CorrectMovement is first enabled, so until then all
     * of them are listening.
     */
    protected final boolean isActiveMode() {
        CorrectMovement correctMovement = this.getCorrectMovement();

        return correctMovement != null
                && correctMovement.isEnabled()
                && correctMovement.getModWithTypeSetToName() == this;
    }

    protected final boolean hasRotation() {
        return !Float.isNaN(RotationCore.currentYaw) && !Float.isNaN(RotationCore.currentPitch);
    }

    /** True when this mode should be rewriting packets and movement right now. */
    protected final boolean canCorrect() {
        return mc.player != null && this.isActiveMode() && this.hasRotation();
    }

    protected final boolean fixLook() {
        CorrectMovement correctMovement = this.getCorrectMovement();

        return correctMovement != null
                && correctMovement.getBooleanValueFromSettingName("FixLook");
    }
}
