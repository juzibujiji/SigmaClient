package com.mentalfrostbyte.jello.gui.impl.jello.ingame.buttons.keybind;

import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.mentalfrostbyte.Client;
import com.mentalfrostbyte.jello.module.Module;
import net.minecraft.client.gui.screen.Screen;

public class Bound {
   private int key = -1;
   private Object target;

   public Bound(JsonObject json) {
      this.loadFromJSON(json);
   }

   public Bound(int key, Module target) {
      this.key = key;
      this.target = target;
   }

   public Bound(int key, Class<? extends Screen> target) {
      this.key = key;
      this.target = target;
   }

   public void loadFromJSON(JsonObject from) {
      if (from.has("target")) {
         try {
            if (from.has("key")) {
               this.key = from.get("key").getAsInt();
            }

            if (from.has("type")) {
               String var4 = from.get("type").getAsString();
               switch (var4) {
                  case "mod":
                     for (Module module : Client.getInstance().moduleManager.getModuleMap().values()) {
                        if (from.get("target").getAsString().equals(module.getName())) {
                           this.target = module;
                        }
                     }
                  case "screen":
                     Class screen = Client.getInstance().guiManager.method33477(from.get("target").getAsString());
                     if (screen != null) {
                        this.target = screen;
                     }
               }
            }
         } catch (JsonParseException e) {
            throw new RuntimeException(e);
         }
      }
   }

   public JsonObject getKeybindData() {
      JsonObject obj = new JsonObject();
       switch (KeybindTypesWrapper.values[this.getKeybindTypes().ordinal()]) {
          case 1:
             obj.addProperty("type", "mod");
             obj.addProperty("target", ((Module)this.target).getName());
             break;
          case 2:
             obj.addProperty("type", "screen");
             obj.addProperty("target", Client.getInstance().guiManager.getNameForTarget((Class<? extends Screen>)this.target));
       }

       obj.addProperty("key", this.key);
       return obj;
   }

   public boolean hasTarget() {
      return this.target != null;
   }

   public int getKeybind() {
      return this.key;
   }

   public void setKey(int key) {
      this.key = key;
   }

   public KeybindTypes getKeybindTypes() {
      return !(this.target instanceof Module) ? KeybindTypes.SCREEN : KeybindTypes.MODULE;
   }

   public Object getTarget() {
      return this.target;
   }

   public Class<? extends Screen> getScreenTarget() {
      return (Class<? extends Screen>)this.target;
   }

   public Module getModuleTarget() {
      return (Module)this.target;
   }

   @Override
   public boolean equals(Object to) {
      if (to != this) {
         if (to instanceof Bound other) {
             return this.getTarget().equals(other.getTarget());
         } else {
            return false;
         }
      } else {
         return true;
      }
   }
}
