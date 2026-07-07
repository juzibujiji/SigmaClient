package com.mentalfrostbyte.jello.gui.impl.jello.ingame.clickgui.panels;

import com.mentalfrostbyte.jello.gui.base.animations.Animation;
import com.mentalfrostbyte.jello.gui.base.elements.impl.*;
import com.mentalfrostbyte.jello.gui.base.elements.impl.button.Button;
import com.mentalfrostbyte.jello.gui.base.elements.impl.colorpicker.ColorPicker;
import com.mentalfrostbyte.jello.gui.base.interfaces.Class4342;
import com.mentalfrostbyte.jello.gui.combined.ContentSize;
import com.mentalfrostbyte.jello.gui.combined.CustomGuiScreen;
import com.mentalfrostbyte.jello.gui.impl.classic.clickgui.buttons.Textbox;
import com.mentalfrostbyte.jello.gui.impl.jello.buttons.ScrollableContentPanel;
import com.mentalfrostbyte.jello.gui.impl.jello.buttons.TextField;
import com.mentalfrostbyte.jello.module.Module;
import com.mentalfrostbyte.jello.module.data.ModuleWithModuleSettings;
import com.mentalfrostbyte.jello.module.settings.Setting;
import com.mentalfrostbyte.jello.module.settings.impl.*;
import com.mentalfrostbyte.jello.util.client.render.FontSizeAdjust;
import com.mentalfrostbyte.jello.util.client.render.theme.ClientColors;
import com.mentalfrostbyte.jello.util.client.render.ResourceRegistry;
import com.mentalfrostbyte.jello.util.client.render.theme.ColorHelper;
import com.mentalfrostbyte.jello.util.game.render.RenderUtil2;
import com.mentalfrostbyte.jello.util.game.render.RenderUtil;
import com.mentalfrostbyte.jello.util.system.math.SmoothInterpolator;
import org.lwjgl.opengl.GL11;

import java.util.HashMap;
import java.util.Map.Entry;

public class SettingPanel extends ScrollableContentPanel implements Class4342 {
    private final Module module;
    private boolean field21220;
    public int field21222 = 200;
    private final HashMap<Text, Setting> field21223 = new HashMap<Text, Setting>();
    public HashMap<Module, CustomGuiScreen> field21224 = new HashMap<Module, CustomGuiScreen>();
    public Animation field21225 = new Animation(114, 114);
    private String field21226 = "";
    private String field21227 = "";

    public SettingPanel(CustomGuiScreen var1, String var2, int var3, int var4, int var5, int var6, Module module) {
        super(var1, var2, var3, var4, var5, var6);
        this.module = module;
        this.setListening(false);
        this.method13511();
    }

    private int method13531(CustomGuiScreen panel, Setting setting, int var3, int var4, int var5) {
        switch (setting.getSettingType()) {
            case BOOLEAN:
                Text var37 = new Text(panel, setting.getName() + "lbl", var3, var4, this.field21222, 24,
                        Text.defaultColorHelper, setting.getName());
                Checkbox var45 = new Checkbox(panel, setting.getName() + "checkbox", panel.getWidthA() - 24 - var5,
                        var4 + 6, 24, 24);
                this.field21223.put(var37, setting);
                var45.method13705((Boolean) setting.getCurrentValue(), false);
                setting.addObserver(var1x -> {
                    if (var45.method13703() != (Boolean) var1x.getCurrentValue()) {
                        var45.method13705((Boolean) var1x.getCurrentValue(), false);
                    }
                });
                var45.onPress(var1x -> setting.setCurrentValue(((Checkbox) var1x).method13703()));
                var45.setSize((var1x, var2x) -> var1x.setXA(var2x.getWidthA() - 24 - var5));
                panel.addToList(var37);
                panel.addToList(var45);
                var4 += 24 + var5;
                break;
            case NUMBER:
                Text var36 = new Text(panel, setting.getName() + "lbl", var3, var4, this.field21222, 24,
                        Text.defaultColorHelper, setting.getName());
                this.field21223.put(var36, setting);
                NumberSetting numbaSetting = (NumberSetting) setting;
                Slider var47 = new Slider(panel, setting.getName() + "slider", panel.getWidthA() - 126 - var5, var4 + 6,
                        126, 24);
                var47.method13137().setFont(ResourceRegistry.JelloLightFont14);
                var47.setText(Float.toString((Float) setting.getCurrentValue()));
                var47.method13140(Slider.method13134(numbaSetting.getMin(), numbaSetting.getMax(),
                        (Float) numbaSetting.getCurrentValue()), false);
                var47.method13143(-1.0F);
                int var13 = numbaSetting.getDecimalPlaces();
                numbaSetting.addObserver(
                        var3x -> {
                            if (Slider.method13135(var47.method13138(), numbaSetting.getMin(), numbaSetting.getMax(),
                                    numbaSetting.getStep(), var13) != (Float) var3x.getCurrentValue()) {
                                var47.setText(Float.toString((Float) var3x.getCurrentValue()));
                                var47.method13140(Slider.method13134(numbaSetting.getMin(), numbaSetting.getMax(),
                                        (Float) var3x.getCurrentValue()), false);
                            }
                        });
                var47.onPress(var4x -> {
                    float var7 = ((Slider) var4x).method13138();
                    float var8x = Slider.method13135(var7, numbaSetting.getMin(), numbaSetting.getMax(),
                            numbaSetting.getStep(), var13);
                    if (var8x != (Float) setting.getCurrentValue()) {
                        var47.setText(Float.toString(var8x));
                        setting.setCurrentValue(var8x);
                    }
                });
                var47.setSize((var1x, var2x) -> var1x.setXA(var2x.getWidthA() - 126 - var5));
                panel.addToList(var36);
                panel.addToList(var47);
                var4 += 24 + var5;
                break;
            case INPUT:
                int var19 = 114;
                int var27 = 27;
                Text var43;
                this.addToList(
                        var43 = new Text(panel, setting.getName() + "lbl", var3, var4, this.field21222, var27,
                                Text.defaultColorHelper, setting.getName()));
                this.field21223.put(var43, setting);
                TextField var35;
                this.addToList(
                        var35 = new TextField(
                                panel,
                                setting.getName() + "txt",
                                panel.getWidthA() - var5 - var19,
                                var4 + var27 / 4 - 1,
                                var19,
                                var27,
                                TextField.field20741,
                                (String) setting.getCurrentValue()));
                var35.setFont(ResourceRegistry.JelloLightFont18);
                var35.addChangeListener(var1x -> setting.setCurrentValue(var1x.getText()));
                setting.addObserver(var2x -> {
                    if (var35.getText() != ((InputSetting) setting).getCurrentValue()) {
                        var35.setText(((InputSetting) setting).getCurrentValue());
                    }
                });
                var4 += var27 + var5;
                break;
            case MODE:
                Text var34 = new Text(panel, setting.getName() + "lbl", var3, var4 + 2, this.field21222, 27,
                        Text.defaultColorHelper, setting.getName());
                Dropdown var42 = new Dropdown(
                        panel,
                        setting.getName() + "btn",
                        panel.getWidthA() - var5,
                        var4 + 6 - 1,
                        123,
                        27,
                        ((ModeSetting) setting).getAvailableModes(),
                        ((ModeSetting) setting).getModeIndex());
                this.field21223.put(var34, setting);
                setting.addObserver(var2x -> {
                    if (var42.getIndex() != ((ModeSetting) setting).getModeIndex()) {
                        var42.method13656(((ModeSetting) setting).getModeIndex());
                    }
                });
                var42.onPress(var2x -> {
                    ((ModeSetting) setting).setModeByIndex(((Dropdown) var2x).getIndex());
                    var42.method13656(((ModeSetting) setting).getModeIndex());
                });
                var42.setSize((var2x, var3x) -> var2x.setXA(panel.getWidthA() - 123 - var5));
                panel.addToList(var34);
                panel.addToList(var42);
                var4 += 27 + var5;
                break;
            case UNUSED:
            default:
                break;
            case SUBOPTION:
                CustomGuiScreen var17 = new CustomGuiScreen(panel, setting.getName() + "view", var3, var4,
                        panel.getWidthA(), 0);
                int var25 = 0;

                for (Setting var41 : ((SubOptionSetting) setting).getSubSettings()) {
                    var25 = this.method13531(var17, var41, 0, var25, var5);
                }

                new ContentSize().setWidth(var17, panel);
                var17.setSize((var1x, var2x) -> var1x.setWidthA(var2x.getWidthA() - var5));
                panel.addToList(var17);
                var4 += var17.getHeightA() + var5;
                break;
            case TEXTBOX:
                Text var32 = new Text(panel, setting.getName() + "lbl", var3, var4, this.field21222, 27,
                        Text.defaultColorHelper, setting.getName());
                Textbox var40 = new Textbox(
                        panel, setting.getName() + "btn", panel.getWidthA() - var5, var4 + 6, 123, 27,
                        ((TextBoxSetting) setting).getOptions(), (Integer) setting.getCurrentValue());
                this.field21223.put(var32, setting);
                setting.addObserver(var1x -> {
                    if (var40.method13720() != (Integer) var1x.getCurrentValue()) {
                        var40.method13722((Integer) var1x.getCurrentValue(), false);
                    }
                });
                var40.onPress(var1x -> setting.setCurrentValue(((Textbox) var1x).method13720()));
                var40.setSize((var2x, var3x) -> var2x.setXA(panel.getWidthA() - 123 - var5));
                panel.addToList(var32);
                panel.addToList(var40);
                var4 += 27 + var5;
                break;
            case BOOLEAN2:
                Text var31 = new Text(panel, setting.getName() + "lbl", var3, var4, this.field21222, 200,
                        Text.defaultColorHelper, setting.getName());
                Picker var39 = new Picker(
                        panel,
                        setting.getName() + "picker",
                        panel.getWidthA() - var5,
                        var4 + 5,
                        175,
                        200,
                        ((BooleanListSetting) setting).enabled,
                        ((BooleanListSetting) setting).getCurrentValue().toArray(new String[0]));
                this.field21223.put(var31, setting);
                var39.onPress(var2x -> setting.setCurrentValue(var39.method13072()));
                var39.setSize((var2x, var3x) -> var2x.setXA(panel.getWidthA() - 175 - var5));
                panel.addToList(var31);
                panel.addToList(var39);
                var4 += 200 + var5;
                break;
            case COLOR:
                ColorSetting var30 = (ColorSetting) setting;
                Text var38 = new Text(panel, setting.getName() + "lbl", var3, var4, this.field21222, 24,
                        Text.defaultColorHelper, setting.getName());
                ColorPicker var46 = new ColorPicker(
                        panel, setting.getName() + "color", panel.getWidthA() - 160 - var5 + 10, var4, 160, 114,
                        (Integer) setting.getCurrentValue(), var30.rainbow);
                this.field21223.put(var38, setting);
                setting.addObserver(var3x -> {
                    var46.method13048((Integer) setting.getCurrentValue());
                    var46.method13046(var30.rainbow);
                });
                var46.onPress(var2x -> {
                    setting.updateCurrentValue(((ColorPicker) var2x).method13049(), false);
                    var30.rainbow = ((ColorPicker) var2x).method13047();
                });
                panel.addToList(var38);
                panel.addToList(var46);
                var4 += 114 + var5 - 10;
                break;
            case SPEEDRAMP:
                SpeedRampSetting.SpeedRamp var10 = (SpeedRampSetting.SpeedRamp) setting.getCurrentValue();
                Text var11 = new Text(panel, setting.getName() + "lbl", var3, var4, this.field21222, 24,
                        Text.defaultColorHelper, setting.getName());
                Bezier var12 = new Bezier(
                        panel,
                        setting.getName() + "color",
                        panel.getWidthA() - 150 - var5 + 10,
                        var4,
                        150,
                        150,
                        20,
                        var10.startValue,
                        var10.middleValue,
                        var10.endValue,
                        var10.maxValue);
                this.field21223.put(var11, setting);
                setting.addObserver(var2x -> {
                    SpeedRampSetting.SpeedRamp var5x = (SpeedRampSetting.SpeedRamp) setting.getCurrentValue();
                    var12.method13041(var5x.startValue, var5x.middleValue, var5x.endValue, var5x.maxValue);
                });
                var12.onPress(
                        var2x -> ((SpeedRampSetting) setting).updateValues(var12.method13040()[0],
                                var12.method13040()[1], var12.method13040()[2], var12.method13040()[3]));
                panel.addToList(var11);
                panel.addToList(var12);
                var4 += 150 + var5 - 10;
                break;
            case FONT_SWITCH:
                FontSwitch var52 = (FontSwitch) setting;
                int var56 = 500;
                int var57 = 4;
                CustomGuiScreen var53 = new CustomGuiScreen(panel, setting.getName() + "card", var3 - 4, var4,
                        panel.getWidthA() - var5, var56) {
                    private final int WINDOW_BG = 0xFFFFFFFF;
                    private final int JELLO_BLUE = 0xFF4080FF;
                    private final int JELLO_BLUE_LIGHT = 0xFF6CA0FF;
                    private final int TEXT_MAIN = 0xFF282D37;
                    private final int TEXT_MUTED = 0xFF787D87;
                    private final int TEXT_WHITE = 0xFFFFFFFF;
                    private final int TEXT_WHITE_MUTED = 0xD8FFFFFF;
                    private final int ITEM_BG_DEFAULT = 0xFFF5F6F8;
                    private final int DIVIDER_COLOR = 0xFFDCDDE6;
                    private int fontScroll;
                    private Animation[] fontAnimations = new Animation[0];
                    private String fontFingerprint = "";

                    @Override
                    public void draw(float partialTicks) {
                        int maxScroll = Math.max(0, var52.getFonts().size() - var57);
                        this.fontScroll = Math.min(this.fontScroll, maxScroll);
                        this.ensureFontAnimations();
                        float x = this.getXA();
                        float y = this.getYA();
                        float w = this.getWidthA();
                        float h = this.getHeightA();

                        if (this.fontAnimations != null) {
                            float listY = y + 190.0F;
                            float itemHeight = 42.0F;
                            float itemW = w - 60.0F;
                            this.drawGeminiFontPanel(partialTicks, x, y, w, h, listY, itemHeight, itemW);
                            super.draw(partialTicks);
                            return;
                        }

                        RenderUtil.drawRoundedRect(x, y, x + w, y + h, RenderUtil2.applyAlpha(WINDOW_BG, partialTicks));

                        RenderUtil.drawString(ResourceRegistry.JelloMediumFont20, x + 20.0F, y + 22.0F,
                                "Import", RenderUtil2.applyAlpha(TEXT_MAIN, partialTicks));
                        RenderUtil.drawString(ResourceRegistry.JelloMediumFont14, x + 30.0F, y + 80.0F,
                                "Size", RenderUtil2.applyAlpha(TEXT_MAIN, partialTicks));
                        RenderUtil.drawString(ResourceRegistry.JelloLightFont14, x + 30.0F, y + 166.0F,
                                "Fonts", RenderUtil2.applyAlpha(TEXT_MUTED, partialTicks));

                        float listY = y + 190.0F;
                        float itemHeight = 42.0F;
                        float itemW = w - 60.0F;
                        float prevAnimPercent = 1.0F;
                        int mouseX = this.getHeightO();
                        int mouseY = this.getWidthO();
                        int rowY = (int) listY;
                        if (this.fontAnimations != null) {
                            this.drawGeminiFontPanel(partialTicks, x, y, w, h, listY, itemHeight, itemW);
                            super.draw(partialTicks);
                            return;
                        }
                        int index = 0;
                        for (String fontName : var52.getFonts().subList(this.fontScroll, var52.getFonts().size())) {
                            if (index >= var57) {
                                break;
                            }
                            boolean selected = fontName.equals(var52.getCurrentValue());
                            boolean hovered = mouseX >= this.method13271() + 20
                                    && mouseX <= this.method13271() + this.getWidthA() - 20
                                    && mouseY >= rowY && mouseY <= rowY + 36;
                            int rowColor = selected ? 0x23528BFF : (hovered ? 0xC8FFFFFF : 0x90FFFFFF);
                            RenderUtil.drawRoundedRect(x + 20.0F, rowY, x + w - 20.0F, rowY + 36.0F,
                                    RenderUtil2.applyAlpha(rowColor, partialTicks));
                            if (selected) {
                                RenderUtil.drawRoundedRect(x + 20.0F, rowY, x + 21.0F, rowY + 36.0F,
                                        RenderUtil2.applyAlpha(0x46528BFF, partialTicks));
                            }
                            float nameX = x + 35.0F;
                            if (this.fontScroll + index == 0) {
                                RenderUtil.drawString(ResourceRegistry.JelloLightFont14, nameX, rowY + 12.0F,
                                        "内置鸿蒙", RenderUtil2.applyAlpha(0x8C2C3E50, partialTicks));
                                nameX += 62.0F;
                            }
                            RenderUtil.drawString(selected ? ResourceRegistry.JelloMediumFont14 : ResourceRegistry.JelloLightFont14,
                                    nameX, rowY + 12.0F, fontName, RenderUtil2.applyAlpha(0xFF2C3E50, partialTicks));
                            if (selected) {
                                RenderUtil.drawString(ResourceRegistry.JelloLightFont14, x + w - 74.0F, rowY + 12.0F,
                                        "Active", RenderUtil2.applyAlpha(0xFF528BFF, partialTicks));
                            }
                            rowY += 42;
                            index++;
                        }

                        float previewY = y + h - 95.0F;
                        RenderUtil.drawRoundedRect(x + 20.0F, previewY, x + w - 20.0F, previewY + 75.0F,
                                RenderUtil2.applyAlpha(0x2DFFFFFF, partialTicks));
                        RenderUtil.drawRoundedRect(x + 20.0F, previewY, x + w - 20.0F, previewY + 1.0F,
                                RenderUtil2.applyAlpha(0x19000000, partialTicks));
                        super.draw(partialTicks);
                    }

                    @Override
                    public void onClick3(int mouseX, int mouseY, int mouseButton) {
                        int localX = mouseX - this.method13271();
                        int localY = mouseY - this.method13272();
                        if (localX >= 30 && localX <= this.getWidthA() - 30 && localY >= 190 && localY < 190 + var57 * 50) {
                            int slotY = (localY - 190) % 50;
                            if (slotY > 42) {
                                super.onClick3(mouseX, mouseY, mouseButton);
                                return;
                            }
                            int index = this.fontScroll + (localY - 190) / 50;
                            if (index >= 0 && index < var52.getFonts().size()) {
                                var52.selectFont(var52.getFonts().get(index));
                                return;
                            }
                        }
                        super.onClick3(mouseX, mouseY, mouseButton);
                    }

                    @Override
                    public void voidEvent3(float scroll) {
                        int maxScroll = Math.max(0, var52.getFonts().size() - var57);
                        if (maxScroll > 0) {
                            this.fontScroll = Math.max(0, Math.min(maxScroll, this.fontScroll + (scroll < 0.0F ? 1 : -1)));
                        }
                        super.voidEvent3(scroll);
                    }

                    private void drawGeminiFontPanel(float partialTicks, float x, float y, float w, float h,
                                                     float listY, float itemHeight, float itemW) {
                        RenderUtil.drawRoundedRect(x, y, x + w, y + h, RenderUtil2.applyAlpha(WINDOW_BG, partialTicks));

                        RenderUtil.drawString(ResourceRegistry.JelloMediumFont20, x + 30.0F, y + 22.0F,
                                "Import", RenderUtil2.applyAlpha(TEXT_MAIN, partialTicks));
                        RenderUtil.drawString(ResourceRegistry.JelloMediumFont14, x + 30.0F, y + 80.0F,
                                "Size", RenderUtil2.applyAlpha(TEXT_MAIN, partialTicks));
                        RenderUtil.drawString(ResourceRegistry.JelloLightFont14, x + 30.0F, y + 166.0F,
                                "Fonts", RenderUtil2.applyAlpha(TEXT_MUTED, partialTicks));

                        float prevAnimPercent = 1.0F;
                        int visibleIndex = 0;
                        for (String fontName : var52.getFonts().subList(this.fontScroll, var52.getFonts().size())) {
                            if (visibleIndex >= var57) {
                                break;
                            }
                            int fontIndex = this.fontScroll + visibleIndex;
                            if (fontIndex < 0 || fontIndex >= this.fontAnimations.length) {
                                break;
                            }

                            float itemY = listY + visibleIndex * (itemHeight + 8.0F);
                            Animation currentAnim = this.fontAnimations[fontIndex];
                            if (prevAnimPercent > 0.2F) {
                                currentAnim.changeDirection(Animation.Direction.FORWARDS);
                            }

                            float animPercent = currentAnim.calcPercent();
                            float eased = SmoothInterpolator.interpolate(animPercent, 0.51, 0.82, 0.0, 0.99);
                            float xOffset = -((1.0F - eased) * (itemW + 30.0F));
                            float alphaProgress = Math.min(1.0F, animPercent / 0.3F) * partialTicks;
                            prevAnimPercent = animPercent;

                            float drawX = x + 30.0F + xOffset;
                            boolean selected = fontName.equals(var52.getCurrentValue());
                            if (selected) {
                                this.drawGradientRow(drawX, itemY, itemW, itemHeight, alphaProgress);
                                RenderUtil.drawString(ResourceRegistry.JelloLightFont14, drawX + 15.0F, itemY + 14.0F,
                                        "系统字体", RenderUtil2.applyAlpha(TEXT_WHITE_MUTED, alphaProgress));
                                RenderUtil.drawString(ResourceRegistry.JelloMediumFont14, drawX + 80.0F, itemY + 14.0F,
                                        fontName, RenderUtil2.applyAlpha(TEXT_WHITE, alphaProgress));
                            } else {
                                RenderUtil.drawRoundedRect(drawX, itemY, drawX + itemW, itemY + itemHeight,
                                        RenderUtil2.applyAlpha(ITEM_BG_DEFAULT, alphaProgress));
                                RenderUtil.drawString(ResourceRegistry.JelloLightFont14, drawX + 15.0F, itemY + 14.0F,
                                        "系统字体", RenderUtil2.applyAlpha(TEXT_MUTED, alphaProgress));
                                RenderUtil.drawString(ResourceRegistry.JelloLightFont14, drawX + 80.0F, itemY + 14.0F,
                                        fontName, RenderUtil2.applyAlpha(TEXT_MAIN, alphaProgress));
                            }
                            visibleIndex++;
                        }

                        float lineY = y + h - 90.0F;
                        RenderUtil.drawRoundedRect(x + 30.0F, lineY, x + w - 30.0F, lineY + 1.0F,
                                RenderUtil2.applyAlpha(DIVIDER_COLOR, partialTicks));
                    }

                    private void drawGradientRow(float x, float y, float width, float height, float alpha) {
                        int slices = 8;
                        float sliceWidth = width / slices;
                        for (int i = 0; i < slices; i++) {
                            float progress = i / (float) Math.max(1, slices - 1);
                            int color = RenderUtil2.shiftTowardsOther(JELLO_BLUE, JELLO_BLUE_LIGHT, progress);
                            float left = x + sliceWidth * i;
                            float right = i == slices - 1 ? x + width : left + sliceWidth + 1.0F;
                            RenderUtil.drawRoundedRect(left, y, right, y + height, RenderUtil2.applyAlpha(color, alpha));
                        }
                    }

                    private void ensureFontAnimations() {
                        int count = var52.getFonts().size();
                        StringBuilder fingerprintBuilder = new StringBuilder();
                        for (String fontName : var52.getFonts()) {
                            fingerprintBuilder.append(fontName).append('\u001F');
                        }
                        String newFingerprint = fingerprintBuilder.toString();
                        if (this.fontAnimations.length == count && this.fontFingerprint.equals(newFingerprint)) {
                            return;
                        }
                        this.fontFingerprint = newFingerprint;
                        this.fontAnimations = new Animation[count];
                        for (int i = 0; i < count; i++) {
                            this.fontAnimations[i] = new Animation(260, 260, Animation.Direction.BACKWARDS);
                        }
                    }
                };
                var53.setSize((var1x, var2x) -> var1x.setWidthA(var2x.getWidthA() - var5));
                Text var48 = new Text(var53, setting.getName() + "lbl", 12, 10, this.field21222, 24,
                        Text.defaultColorHelper, "");
                Text var49 = new Text(var53, setting.getName() + "value", 20, 42, this.field21222, 20,
                        Text.defaultColorHelper, String.valueOf(setting.getCurrentValue()), ResourceRegistry.JelloLightFont14);
                var49.setSelfVisible(false);
                Text var54 = new Text(var53, setting.getName() + "preview_cn", 30, var56 - 60, this.field21222, 22,
                        Text.defaultColorHelper, "\u4E2D\u56FD\u667A\u9020\uFF0C\u60E0\u53CA\u5168\u7403", var52.getPreviewFont());
                Text var55 = new Text(var53, setting.getName() + "preview_en", 30, var56 - 35, this.field21222, 22,
                        Text.defaultColorHelper, "The quick brown fox jumps over the lazy dog.", var52.getPreviewFont());
                ColorHelper var58 = new ColorHelper(0xFF4080FF, 0xFF286BF0, 0xFF4080FF, 0xFFFFFFFF,
                        FontSizeAdjust.NEGATE_AND_DIVIDE_BY_2, FontSizeAdjust.NEGATE_AND_DIVIDE_BY_2);
                Button var50 = new Button(var53, setting.getName() + "open", var53.getWidthA() - 220,
                        16, 90, 26, var58, "OpenFolder", ResourceRegistry.JelloLightFont14);
                Button var51 = new Button(var53, setting.getName() + "refresh", var53.getWidthA() - 120,
                        16, 90, 26, var58, "Refresh", ResourceRegistry.JelloLightFont14);
                var50.field20586 = 5;
                var51.field20586 = 5;
                this.field21223.put(var48, setting);
                var50.onClick((var1x, var2x) -> var52.openFolder());
                var51.onClick((var1x, var2x) -> var52.refresh());
                setting.addObserver(var1x -> {
                    var49.setText(String.valueOf(var1x.getCurrentValue()));
                    var54.setFont(var52.getPreviewFont());
                    var55.setFont(var52.getPreviewFont());
                });
                var50.setSize((var1x, var2x) -> var1x.setXA(var2x.getWidthA() - 220));
                var51.setSize((var1x, var2x) -> var1x.setXA(var2x.getWidthA() - 120));
                var53.addToList(var48);
                var53.addToList(var49);
                var53.addToList(var54);
                var53.addToList(var55);
                var53.addToList(var50);
                var53.addToList(var51);
                panel.addToList(var53);
                var4 += var56 + var5;
                break;
        }

        return var4 - (var5 - 10);
    }

    private void method13511() {
        int var4 = 20;

        for (Setting<?> setting : this.module.getSettingMap().values()) {
            if (setting.isHidden())
                continue;

            var4 = this.method13531(this, setting, 20, var4, 20);
        }

        int var17 = var4;
        if (this.module instanceof ModuleWithModuleSettings var18) {

            for (Module var10 : var18.moduleArray) {
                int var11 = 0;
                CustomGuiScreen var12 = new CustomGuiScreen(this, var10.getName() + "SubView", 0, var17, this.widthA,
                        this.heightA - var4);
                var12.setSize((var0, var1) -> var0.setWidthA(var1.getWidthA()));

                for (Setting var14 : var10.getSettingMap().values()) {
                    var11 = this.method13531(var12, var14, 20, var11, 20);
                }

                var4 = Math.max(var4 + var11, var4);

                for (CustomGuiScreen var20 : var12.getChildren()) {
                    if (var20 instanceof Dropdown var15) {
                        int var16 = var15.method13649() + var15.getYA() + var15.getHeightA() + 14;
                        var11 = Math.max(var11, var16);
                    }
                }

                var12.setHeightA(var11);
                this.addToList(var12);
                this.field21224.put(var10, var12);
            }

            var18.addModuleStateListener(
                    (parent, module, enabled) -> this.field21224.get(module).setSelfVisible(enabled));
            var18.calledOnEnable();
        }

        this.addToList(new CustomGuiScreen(this, "extentionhack", 0, var4, 0, 20));
    }

    @Override
    public void updatePanelDimensions(int newHeight, int newWidth) {
        super.updatePanelDimensions(newHeight, newWidth);
    }

    @Override
    public void draw(float partialTicks) {
        boolean var4 = false;

        for (Entry var6 : this.field21223.entrySet()) {
            Text var7 = (Text) var6.getKey();
            Setting var8 = (Setting) var6.getValue();
            if (var7.method13298() && var7.isVisible()) {
                var4 = true;
                this.field21226 = var8.getDescription();
                this.field21227 = var8.getName();
                break;
            }
        }

        GL11.glPushMatrix();
        super.draw(partialTicks);
        GL11.glPopMatrix();
        this.field21225.changeDirection(!var4 ? Animation.Direction.BACKWARDS : Animation.Direction.FORWARDS);
        RenderUtil.drawString(
                ResourceRegistry.JelloLightFont14,
                (float) (this.getXA() + 10),
                (float) (this.getYA() + this.getHeightA() + 24),
                this.field21227,
                RenderUtil2.applyAlpha(ClientColors.LIGHT_GREYISH_BLUE.getColor(),
                        0.5F * this.field21225.calcPercent()));
        RenderUtil.drawString(
                ResourceRegistry.JelloLightFont14,
                (float) (this.getXA() + 11),
                (float) (this.getYA() + this.getHeightA() + 24),
                this.field21227,
                RenderUtil2.applyAlpha(ClientColors.LIGHT_GREYISH_BLUE.getColor(),
                        0.5F * this.field21225.calcPercent()));
        RenderUtil.drawString(
                ResourceRegistry.JelloLightFont14,
                (float) (this.getXA() + 14 + ResourceRegistry.JelloLightFont14.getWidth(this.field21227) + 2),
                (float) (this.getYA() + this.getHeightA() + 24),
                this.field21226,
                RenderUtil2.applyAlpha(ClientColors.LIGHT_GREYISH_BLUE.getColor(),
                        0.5F * this.field21225.calcPercent()));
    }

    @Override
    public boolean method13525() {
        return this.field21220;
    }

}
