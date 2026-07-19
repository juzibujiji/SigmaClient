package com.mentalfrostbyte.jello.module.impl.render.jello.nametags;

import net.minecraft.resources.IFutureReloadListener;

import java.util.concurrent.CompletableFuture;


//修了熔炉nametag掉帧卡顿 已经不需要这个了
public class RecipeReloadListener implements IFutureReloadListener.IStage {
    public final FurnaceTracker field30642;

    public RecipeReloadListener(FurnaceTracker var1) {
        this.field30642 = var1;
    }

    @Override
    public <T> CompletableFuture<T> markCompleteAwaitingOthers(T backgroundResult) {
        return CompletableFuture.completedFuture(backgroundResult);
    }
}
