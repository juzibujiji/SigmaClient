package com.mentalfrostbyte.jello.module.data;

import com.mentalfrostbyte.jello.module.Module;

public interface ModuleStateListener {
   void onModuleEnabled(ModuleWithModuleSettings parent, Module module, boolean enabled);
}
