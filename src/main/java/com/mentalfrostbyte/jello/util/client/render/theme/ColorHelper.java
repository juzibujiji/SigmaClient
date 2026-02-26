package com.mentalfrostbyte.jello.util.client.render.theme;

import com.mentalfrostbyte.jello.util.game.render.RenderUtil2;
import com.mentalfrostbyte.jello.util.client.render.FontSizeAdjust;

/**
 * A utility class for managing colors, likely used for UI rendering.
 * It allows storing and manipulating different color components and related properties.
 */
public class ColorHelper {
   /**
    * A default instance of ColorHelper with a specific color value.
    */
   public static final ColorHelper field27961 = new ColorHelper(-12871171);
   /**
    * The primary color component.
    */
   public int primaryColor;
   /**
    * A secondary color component, often a shifted version of the primary color.
    */
   public int secondaryColor;
   /**
    * A tertiary color component, possibly used for accent or highlighting.
    */
   public int tertiaryColor;
   /**
    * The color used for text rendering in conjunction with this ColorHelper.
    */
   public int textColor;
   /**
    * An associated Class2218 instance, purpose unclear without further context.
    */
   public FontSizeAdjust field27966;
   /**
    * Another associated Class2218 instance, purpose unclear without further context.
    */
   public FontSizeAdjust field27967;

   /**
    * Constructs a ColorHelper with the given color and a darker shade of it.
    * The darker shade is calculated using {@link RenderUtil2#shiftTowardsBlack(int, float)}.
    *
    * @param color The base color for this ColorHelper.
    */
   public ColorHelper(int color) {
      this(color, RenderUtil2.shiftTowardsBlack(color, 0.05F));
   }

   /**
    * Constructs a ColorHelper with a primary color and a secondary color.
    * The tertiary color and text color are set to default values from {@link ClientColors}.
    *
    * @param primary The primary color.
    * @param secondary The secondary color.
    */
   public ColorHelper(int primary, int secondary) {
      this(primary, secondary, ClientColors.DEEP_TEAL.getColor());
   }

   /**
    * Constructs a ColorHelper with primary, secondary, and tertiary colors.
    * The text color is set to a default value from {@link ClientColors}.
    *
    * @param primary The primary color.
    * @param secondary The secondary color.
    * @param tertiary The tertiary color.
    */
   public ColorHelper(int primary, int secondary, int tertiary) {
      this(primary, secondary, tertiary, ClientColors.LIGHT_GREYISH_BLUE.getColor());
   }

   /**
    * Constructs a ColorHelper with primary, secondary, tertiary, and text colors.
    * The Class2218 instances are set to default values ({@link FontSizeAdjust#NEGATE_AND_DIVIDE_BY_2}).
    *
    * @param primary The primary color.
    * @param secondary The secondary color.
    * @param tertiary The tertiary color.
    * @param text The text color.
    */
   public ColorHelper(int primary, int secondary, int tertiary, int text) {
      this(primary, secondary, tertiary, text, FontSizeAdjust.NEGATE_AND_DIVIDE_BY_2, FontSizeAdjust.NEGATE_AND_DIVIDE_BY_2);
   }

   /**
    * Constructs a ColorHelper with all color components and Class2218 instances specified.
    *
    * @param primary The primary color.
    * @param secondary The secondary color.
    * @param tertiary The tertiary color.
    * @param text The text color.
    * @param var5 The first Class2218 instance.
    * @param var6 The second Class2218 instance.
    */
   public ColorHelper(int primary, int secondary, int tertiary, int text, FontSizeAdjust var5, FontSizeAdjust var6) {
      this.primaryColor = primary;
      this.secondaryColor = secondary;
      this.tertiaryColor = tertiary;
      this.textColor = text;
      this.field27966 = var5;
      this.field27967 = var6;
   }

   /**
    * Copy constructor to create a new ColorHelper instance from an existing one.
    *
    * @param var1 The ColorHelper instance to copy.
    */
   public ColorHelper(ColorHelper var1) {
      this(var1.primaryColor, var1.secondaryColor, var1.tertiaryColor, var1.textColor, var1.field27966, var1.field27967);
   }

   /**
    * Gets the secondary color component.
    *
    * @return The secondary color.
    */
   public int getSecondaryColor() {
      return this.secondaryColor;
   }

   /**
    * Sets the secondary color component.
    *
    * @param var1 The new secondary color.
    * @return This ColorHelper instance for chaining.
    */
   public ColorHelper setSecondaryColor(int var1) {
      this.secondaryColor = var1;
      return this;
   }

   /**
    * Gets the primary color component.
    *
    * @return The primary color.
    */
   public int getPrimaryColor() {
      return this.primaryColor;
   }

   /**
    * Sets the primary color component.
    *
    * @param primary The new primary color.
    * @return This ColorHelper instance for chaining.
    */
   public ColorHelper setPrimaryColor(int primary) {
      this.primaryColor = primary;
      return this;
   }

   /**
    * Gets the tertiary color component.
    *
    * @return The tertiary color.
    */
   public int getTertiary() {
      return this.tertiaryColor;
   }

   /**
    * Sets the tertiary color component.
    *
    * @param tertiary The new tertiary color.
    * @return This ColorHelper instance for chaining.
    */
   public ColorHelper setTertiary(int tertiary) {
      this.tertiaryColor = tertiary;
      return this;
   }

   /**
    * Gets the text color.
    *
    * @return The text color.
    */
   public int getTextColor() {
      return this.textColor;
   }

   /**
    * Sets the text color.
    *
    * @param color The new text color.
    * @return This ColorHelper instance for chaining.
    */
   public ColorHelper setTextColor(int color) {
      this.textColor = color;
      return this;
   }

   /**
    * Gets the first Class2218 instance.
    *
    * @return The first Class2218 instance.
    */
   public FontSizeAdjust method19411() {
      return this.field27966;
   }

   /**
    * Sets the first Class2218 instance.
    *
    * @param var1 The new Class2218 instance.
    * @return This ColorHelper instance for chaining.
    */
   public ColorHelper method19412(FontSizeAdjust var1) {
      this.field27966 = var1;
      return this;
   }

   /**
    * Gets the second Class2218 instance.
    *
    * @return The second Class2218 instance.
    */
   public FontSizeAdjust method19413() {
      return this.field27967;
   }

   /**
    * Sets the second Class2218 instance.
    *
    * @param var1 The new Class2218 instance.
    * @return This ColorHelper instance for chaining.
    */
   public ColorHelper method19414(FontSizeAdjust var1) {
      this.field27967 = var1;
      return this;
   }

   /**
    * Creates and returns a new ColorHelper instance with the same color values as this one.
    *
    * @return A new clone of this color helper.
    */
   @SuppressWarnings("all")
   @Override
   public ColorHelper clone() {
      return new ColorHelper(this.primaryColor, this.secondaryColor, this.tertiaryColor, this.textColor, this.field27966, this.field27967);
   }
}