package com.mentalfrostbyte.jello.gui.impl.others;

import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import net.minecraft.network.play.client.CChatMessagePacket;
import org.newdawn.slick.TrueTypeFont;
import org.newdawn.slick.Color;
import net.minecraft.client.Minecraft;
import net.minecraft.util.text.StringTextComponent;

import static com.mentalfrostbyte.jello.module.Module.mc;

public class ChatUtil {
   private static Pattern field40303;
   private static Matcher field40304;
   private static final String field40305 = "^[_A-Za-z0-9-\\+]+(\\.[_A-Za-z0-9-]+)*@[A-Za-z0-9-]+(\\.[A-Za-z0-9]+)*(\\.[A-Za-z]{2,3})$";

   public static boolean method32486(char var0) {
      return var0 != 167 && var0 >= ' ' && var0 != 127;
   }

   public static void printMessage(String message) {
      Minecraft.getInstance().ingameGUI.getChatGUI().printChatMessage(new StringTextComponent(message));
   }
   public static void sendChatMessage(String text) {
      mc.getConnection().sendPacket(new CChatMessagePacket(text));
   }

   public static boolean method32488(String var0) {
      for (char var6 : var0.toCharArray()) {
         if (!Character.isLetterOrDigit(var6) && var6 != '_') {
            return false;
         }
      }

      return true;
   }

   public static boolean method32489(String var0) {
      for (char var6 : var0.toCharArray()) {
         if (!Character.isLetterOrDigit(var6) && var6 != '_' && var6 != '@' && var6 != '.') {
            return false;
         }
      }

      return method32490(var0);
   }

   public static boolean method32490(String var0) {
      field40303 = Pattern.compile("^[_A-Za-z0-9-\\+]+(\\.[_A-Za-z0-9-]+)*@[A-Za-z0-9-]+(\\.[A-Za-z0-9]+)*(\\.[A-Za-z]{2,3})$");
      field40304 = field40303.matcher(var0);
      return field40304.matches();
   }

   public static boolean method32491(String var0) {
      try {
         Integer.parseInt(var0);
         return true;
      } catch (Exception var4) {
         return false;
      }
   }

   public static String paste(String original, String content, int maxLen) {
      try {
         String start = original.substring(0, maxLen);
         String end = original.substring(maxLen);
         return start + content + end;
      } catch (Exception var7) {
         return original;
      }
   }

   public static String cut(String var0, String content, int cutStart, int cutEnd) {
      cutStart = Math.min(Math.max(0, cutStart), var0.length());
      cutEnd = Math.min(Math.max(0, cutEnd), var0.length());
      String start = var0.substring(0, Math.min(cutStart, cutEnd));
      String end = var0.substring(Math.max(cutStart, cutEnd));
      return start + content + end;
   }

   public static int getStringLen(String text, TrueTypeFont font, float var2, int height, float var4) {
      int var7 = -1;
      int width = -1;

      for (int var9 = 0; var9 <= text.length(); var9++) {
         int var10 = font.getWidth(text.substring(0, Math.max(var9 - 1, 0)));
         int var11 = font.getWidth(text.substring(0, var9));
         if ((float)var11 > (float)height - var2 - var4) {
            var7 = var10;
            width = var11;
            break;
         }
      }

      if ((float)height - var2 - var4 >= (float)font.getWidth(text)) {
         width = font.getWidth(text);
      }

      int len = !(Math.abs((float)height - var2 - var4 - (float)var7) < Math.abs((float)height - var2 - var4 - (float)width)) ? width : var7;

      for (int i = 0; i < text.length(); i++) {
         if (font.getWidth(text.substring(0, i)) == len) {
            len = i;
            break;
         }
      }

      if (len > text.length()) {
         len = text.length();
      }

      return len;
   }

   public static Color method32496(char var0) {
      char[] var3 = new char[]{'0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'a', 'b', 'c', 'd', 'e', 'f'};
      int[] var4 = new int[]{
         -16777216,
         -16777046,
         -16733696,
         -16733526,
         -5636096,
         -5635926,
         -22016,
         -5592406,
         -11184811,
         -11184641,
         -11141291,
         -11141121,
         -43691,
         -43521,
         -171,
         -65794
      };
      int var5 = -1;

      for (int var6 = 0; var6 < var3.length; var6++) {
         if (var3[var6] == var0) {
            var5 = var4[var6];
            break;
         }
      }

      return var5 != -1 ? new Color(var5) : null;
   }

   public static String method32497(String var0) {
      ArrayList<String> var3 = new ArrayList();

      for (int var4 = 0; var4 < var0.length(); var4++) {
         char var5 = var0.charAt(var4);
         if (var5 == 167) {
            var3.add(var0.substring(var4, Math.min(var4 + 2, var0.length())));
         }
      }

      for (String var7 : var3) {
         var0 = var0.replace(var7, "");
      }

      return var0;
   }
}
