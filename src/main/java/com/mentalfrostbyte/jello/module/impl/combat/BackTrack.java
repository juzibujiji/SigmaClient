package com.mentalfrostbyte.jello.module.impl.combat;

import com.mentalfrostbyte.jello.module.data.ModuleCategory;
import com.mentalfrostbyte.jello.module.data.ModuleWithModuleSettings;
import com.mentalfrostbyte.jello.module.impl.combat.backtrack.GptBackTrack;
import com.mentalfrostbyte.jello.module.impl.combat.backtrack.LegacyBackTrack;

//问题有点多有时间我会修复的 完成度85%
//这个只适合在GrimAC使用 其他AC(AntiCheat)不敢保证
//Legacy / GPT 两种实现已拆分为独立子模块，通过 Mode 切换（同 Aimbot 架构）
public class BackTrack extends ModuleWithModuleSettings {
    public BackTrack() {
        super(ModuleCategory.COMBAT, "BackTrack", "Track and render entity real positions", "Mode",
                new LegacyBackTrack(),
                new GptBackTrack());
    }
}
