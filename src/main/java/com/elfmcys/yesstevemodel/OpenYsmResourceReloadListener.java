package com.elfmcys.yesstevemodel;

import net.minecraft.resources.IResourceManager;
import net.minecraft.resources.IResourceManagerReloadListener;

public final class OpenYsmResourceReloadListener implements IResourceManagerReloadListener {
    @Override
    public void onResourceManagerReload(IResourceManager resourceManager) {
        YesSteveModel.reload(resourceManager);
    }
}
