package com.mentalfrostbyte.jello.module.impl.movement;

import com.mentalfrostbyte.jello.module.data.ModuleCategory;
import com.mentalfrostbyte.jello.module.data.ModuleWithModuleSettings;
import com.mentalfrostbyte.jello.module.impl.movement.crtmov.Sigma;
import com.mentalfrostbyte.jello.module.impl.movement.crtmov.Zen;
import com.mentalfrostbyte.jello.module.settings.impl.BooleanSetting;
public class CorrectMovement extends ModuleWithModuleSettings {
    public CorrectMovement() {
        super(ModuleCategory.MOVEMENT,
                "CorrectMovement",
                "juzi suck my dick" ,
                new Zen(),
                new Sigma()
        );

        this.registerSetting(new BooleanSetting("FixLook","FixLook",true));
        //SB桔子,居然欺诈跟有四correctmovement真实可用.
    }

    /**
     * Correction lives in the selected mode (see crtmov/CorrectorMode), not here and not in
     * managers/RotationManager, which is retired. This module only owns the mode list and the
     * shared FixLook setting.
     */
}