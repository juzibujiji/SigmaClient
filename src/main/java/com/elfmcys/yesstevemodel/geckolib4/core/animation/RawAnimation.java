package com.elfmcys.yesstevemodel.geckolib4.core.animation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * GeckoLib 4 style raw animation descriptor.
 */
public final class RawAnimation {
    private final List<Stage> stages = new ArrayList<>();

    private RawAnimation() {
    }

    public static RawAnimation begin() {
        return new RawAnimation();
    }

    public RawAnimation thenLoop(String animationName) {
        this.stages.add(new Stage(animationName, LoopType.LOOP));
        return this;
    }

    public RawAnimation thenPlay(String animationName) {
        this.stages.add(new Stage(animationName, LoopType.PLAY_ONCE));
        return this;
    }

    public RawAnimation thenPlayAndHold(String animationName) {
        this.stages.add(new Stage(animationName, LoopType.HOLD_ON_LAST_FRAME));
        return this;
    }

    public List<Stage> stages() {
        return Collections.unmodifiableList(this.stages);
    }

    public enum LoopType {
        LOOP,
        PLAY_ONCE,
        HOLD_ON_LAST_FRAME
    }

    public static final class Stage {
        private final String animationName;
        private final LoopType loopType;

        private Stage(String animationName, LoopType loopType) {
            this.animationName = animationName;
            this.loopType = loopType;
        }

        public String animationName() {
            return this.animationName;
        }

        public LoopType loopType() {
            return this.loopType;
        }
    }
}
