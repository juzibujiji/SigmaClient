package com.mentalfrostbyte.jello.module.impl.gui.chatui;

import com.mojang.blaze3d.matrix.MatrixStack;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mentalfrostbyte.jello.event.impl.game.render.EventRender2DOffset;
import com.mentalfrostbyte.jello.event.impl.game.render.EventRenderChat;
import com.mentalfrostbyte.jello.event.impl.player.EventUpdate;
import com.mentalfrostbyte.jello.managers.GuiManager;
import com.mentalfrostbyte.jello.module.Module;
import com.mentalfrostbyte.jello.module.data.ModuleCategory;
import com.mentalfrostbyte.jello.module.settings.impl.BooleanSetting;
import com.mentalfrostbyte.jello.module.settings.impl.NumberSetting;
import com.mentalfrostbyte.jello.util.game.render.RenderUtil;
import net.minecraft.client.gui.ChatLine;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.util.IReorderingProcessor;
import net.minecraft.util.text.Style;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL14;
import team.sdhq.eventBus.EventBus;
import team.sdhq.eventBus.annotations.EventTarget;

import java.util.List;

/**
 * Port of Rise 6.9.5's Chat visuals (Chat#renderChat / Chat#renderInputBox).
 * <p>
 * Like Rise, this consumes the vanilla-wrapped chat lines (drawnChatLines) and lets the
 * vanilla font pipeline handle every style — legacy codes, RGB hex colors, bold/obfuscated,
 * hover/click styles — instead of re-implementing wrapping. Rise draws its shapes through
 * its rounded-quad shader; here they are immediate-mode polygons because Sigma has no such
 * shader helper wired into the HUD pass. Rise's blur/bloom layers, image chat and the
 * pinyin IME are not ported. Input state stays in a vanilla ChatScreen subclass, so typing,
 * Tab completion, history and component click/hover keep vanilla behavior while the module
 * draws everything.
 */
public class RiseChatUI extends Module {
    private static final String[] WAVE_PREFIXES = {"[Sigma]", "[Rise]"};
    private static final int WAVE_FROM = new java.awt.Color(0, 170, 255).getRGB();
    private static final int WAVE_TO = new java.awt.Color(0, 255, 200).getRGB();
    private static final int BG_COLOR = new java.awt.Color(22, 22, 28, 0).getRGB();

    /**
     * Read directly by NewChatGui to skip the vanilla chat render. Derived from isEnabled()
     * (mode selected + parent on) because sub-module onEnable/onDisable only fire on mode
     * switches, not when the parent module is toggled with this mode already selected.
     */
    private static RiseChatUI instance;

    /** Face-culling state captured by setupShape and put back by restoreShape (render thread only). */
    private static boolean cullWasEnabled;

    public static boolean isActive() {
        return instance != null && instance.isEnabled();
    }

    private final Anim heightAnim = new Anim(0.0);
    private final Anim slideAnim = new Anim(0.0);
    private final Anim scrollAnim = new Anim(0.0);
    private final Anim openAnim = new Anim(0.0);
    private final Anim alphaAnim = new Anim(0.0);

    private int lastLineCount;
    private int visibleLines;
    private long lastDisappearance;
    private double scrollTarget;
    /** Negative pixels other HUD elements claim at the bottom of the screen; see onRender. */
    private int chatYOffset;

    private Style hoveredStyle;
    private String lastInputText = "";
    private int lastInputCursor;

    public RiseChatUI() {
        super(ModuleCategory.GUI, "Rise", "Rise-style chat: rounded panel and smooth animations.");
        this.registerSetting(new NumberSetting<>("Width", "Chat panel width", 320.0F, 60.0F, 460.0F, 1.0F));
        this.registerSetting(new NumberSetting<>("Open Height", "Max panel height while open", 130.0F, 40.0F, 200.0F, 1.0F));
        this.registerSetting(new NumberSetting<>("Max Closed Height", "Max panel height while closed", 130.0F, 0.0F, 500.0F, 1.0F));
        this.registerSetting(new NumberSetting<>("Disappearance", "Ms until closed chat loses one line", 5000.0F, 500.0F, 30000.0F, 100.0F));
        this.registerSetting(new NumberSetting<>("Rounding", "Corner radius", 6.0F, 0.0F, 12.0F, 0.5F));
        this.registerSetting(new NumberSetting<>("Background Opacity", "Panel background alpha", 160.0F, 0.0F, 255.0F, 1.0F));
        this.registerSetting(new BooleanSetting("Background", "Draw the panel background", true));
        instance = this;
    }

    @Override
    public void onDisable() {
        if (mc.currentScreen instanceof RiseChatScreen) {
            mc.displayGuiScreen(null);
        }
        this.scrollTarget = 0.0;
        this.hoveredStyle = null;
    }

    /** Replace the vanilla chat screen with the Rise one so vanilla input logic keeps working. */
    @EventTarget
    public void onUpdate(EventUpdate event) {
        if (!this.isEnabled() || mc.player == null) {
            return;
        }

        if (mc.currentScreen instanceof ChatScreen && !(mc.currentScreen instanceof RiseChatScreen)) {
            mc.displayGuiScreen(new RiseChatScreen(((ChatScreen) mc.currentScreen).getDefaultInputFieldText(), this));
        }
    }

    @EventTarget
    public void onRender(EventRender2DOffset event) {
        if (!this.isEnabled() || mc.player == null || mc.ingameGUI == null) {
            return;
        }
        // Vanilla hides the HUD (chat included) on F1 unless a screen is open; match it.
        if (mc.gameSettings.hideGUI && mc.currentScreen == null) {
            return;
        }

        // Same adaptive offset vanilla chat uses: listeners claim space at the bottom of the
        // screen (InfoHUD's player model -40, MusicManager's player -45) and the chat moves
        // above it. Vanilla only shifts the messages; the panel and input box are one unit
        // here, so both move together.
        EventRenderChat offsetEvent = new EventRenderChat();
        EventBus.call(offsetEvent);
        this.chatYOffset = offsetEvent.getYOffset();

        // EventRender2DOffset fires in window-pixel HUD space: GameRenderer pre-scales the
        // modelview by 1/guiScale (times the framebuffer ratio) before renderWatermark().
        // Vanilla chat lives in scaled GUI coords, so undo that factor here — otherwise the
        // panel lands at 1/guiScale of its intended position and the font renders at
        // 1/guiScale size (tiny, washed-out glyphs) whenever GUI scale isn't 1.
        float ratio = GuiManager.scaleFactor > 0.0F ? GuiManager.scaleFactor : 1.0F;
        float undo = (float) (mc.getMainWindow().getGuiScaleFactor() / (double) ratio);
        GL11.glPushMatrix();
        GL11.glScalef(undo, undo, 1.0F);
        this.renderPanel();
        this.renderInput();
        GL11.glPopMatrix();
    }

    private void renderPanel() {
        FontRenderer font = mc.fontRenderer;
        float lineHeight = font.FONT_HEIGHT;
        int screenHeight = mc.getMainWindow().getScaledHeight();
        float panelWidth = this.getNumberValue("Width");
        float rounding = this.getNumberValue("Rounding");
        boolean open = mc.currentScreen instanceof RiseChatScreen;

        List<ChatLine<IReorderingProcessor>> lines = mc.ingameGUI.getChatGUI().getDrawnChatLines();
        int total = lines.size();

        if (total > this.lastLineCount) {
            int delta = total - this.lastLineCount;
            if (!open) {
                this.slideAnim.jump(this.slideAnim.get() + delta * lineHeight);
                this.slideAnim.animate(0.0, 500, Anim.EASE_OUT_EXPO);
                int cap = Math.max(0, (int) (this.getNumberValue("Max Closed Height") / lineHeight));
                this.visibleLines = Math.min(cap, Math.max(0, this.visibleLines) + delta);
            } else if (this.scrollTarget > 0.5) {
                this.scrollTarget += delta * lineHeight;
            }
        }
        this.lastLineCount = total;

        long now = System.currentTimeMillis();
        if (!open && total > 0 && now - this.lastDisappearance >= (long) this.getNumberValue("Disappearance")) {
            this.lastDisappearance = now;
            this.visibleLines = Math.max(0, this.visibleLines - 1);
        }
        if (open) {
            this.visibleLines = total;
        }

        this.openAnim.animate(open ? 1.0 : 0.0, open ? 850 : 300, open ? Anim.EASE_OUT_ELASTIC : Anim.EASE_IN_EXPO);
        this.alphaAnim.animate(open ? 255.0 : 0.0, 400, Anim.LINEAR);
        // Align message rows with vanilla chat: vanilla draws line i's glyph top at
        // scaledHeight - 48 - 9i (IngameGui translates to height-48, NewChatGui to +8,
        // text at -8), plus the same adaptive offset. With 3px bottom padding,
        // panelBottom - 3 - lineHeight == height - 48 + offset.
        float panelBottom = screenHeight - 36.0F + this.chatYOffset;

        float contentLines = open ? total : Math.min(this.visibleLines, total);
        float contentHeight = contentLines * lineHeight;
        float cap = open ? this.getNumberValue("Open Height") : this.getNumberValue("Max Closed Height");
        if (mc.gameSettings.showDebugInfo) {
            // Sigma-style F3 adaptation: stay below the debug text column and the frame-time graph.
            List<String> debugLeft = mc.ingameGUI.overlayDebug.getLeftDebugLines();
            float debugBottom = Math.max(debugLeft.size() * lineHeight + 6.0F, screenHeight * 0.5F + 64.0F);
            cap = Math.min(cap, panelBottom - debugBottom - 4.0F);
        }
        float targetHeight = Math.min(cap, contentHeight + (contentHeight > 0.0F ? 7.0F : 0.0F));
        this.heightAnim.animate(targetHeight, 500, Anim.EASE_OUT_EXPO);
        float height = (float) this.heightAnim.get();

        // Vanilla chat background starts flush at x=0 with text at x=2; match both.
        float panelX = 0.0F;
        float panelY = panelBottom - height;

        this.hoveredStyle = null;
        if (height < 0.5F || total == 0) {
            return;
        }

        if (this.getBooleanValue("Background")) {
            drawRoundRect(panelX, panelY, panelWidth, height, rounding, this.backgroundArgb());
        }

        if (!open) {
            this.scrollTarget = 0.0;
        }
        float maxScroll = Math.max(0.0F, total * lineHeight + 7.0F - height);
        this.scrollTarget = Math.max(0.0, Math.min(this.scrollTarget, maxScroll));
        this.scrollAnim.animate(this.scrollTarget, 150, Anim.EASE_OUT_EXPO);
        double offset = this.scrollAnim.get() + this.slideAnim.get();

        double[] mouse = this.scaledMouse();
        // glScissor clips in framebuffer pixels and startScissor(scale=false) multiplies its
        // inputs by the framebuffer/window ratio, so convert our scaled GUI coords to window px.
        float windowScaleX = (float) mc.getMainWindow().getWidth() / (float) mc.getMainWindow().getScaledWidth();
        float windowScaleY = (float) mc.getMainWindow().getHeight() / (float) mc.getMainWindow().getScaledHeight();
        RenderUtil.startScissor((int) (panelX * windowScaleX), (int) ((panelY - 1.0F) * windowScaleY),
                (int) ((panelX + panelWidth) * windowScaleX), (int) ((panelBottom + 1.0F) * windowScaleY), false);
        MatrixStack matrices = new MatrixStack();
        float textX = panelX + 2.0F;

        for (int i = 0; i < total; i++) {
            float lineTop = (float) (panelBottom - 3.0 - (i + 1) * lineHeight + offset);
            if (lineTop + lineHeight < panelY - 4.0F || lineTop > panelBottom + 4.0F) {
                continue;
            }

            IReorderingProcessor line = lines.get(i).getLineString();
            float x = textX;
            IReorderingProcessor body = line;
            String head = head(line, 8);
            for (String prefix : WAVE_PREFIXES) {
                if (head.startsWith(prefix)) {
                    x += this.drawWave(font, matrices, prefix, x, lineTop);
                    body = skip(line, prefix.length());
                    break;
                }
            }

            font.func_238407_a_(matrices, body, x, lineTop, 0xFFFFFFFF);

            if (open && this.hoveredStyle == null) {
                float lineWidth = x - textX + font.getStringWidth(body);
                if (mouse[0] >= textX && mouse[0] <= textX + lineWidth
                        && mouse[1] >= lineTop && mouse[1] <= lineTop + lineHeight) {
                    this.hoveredStyle = font.getCharacterManager().func_243239_a(line, (int) (mouse[0] - textX));
                }
            }
        }
        RenderUtil.restoreScissor();

        if (open && maxScroll > 0.5F) {
            float barHeight = Math.max(8.0F, height * height / (total * lineHeight + 7.0F));
            float barY = panelY + (height - barHeight) * (float) (this.scrollTarget / maxScroll);
            fillRect(panelX + panelWidth - 2.5F, barY, panelX + panelWidth - 1.0F, barY + barHeight, 0x80FFFFFF);
        }
    }

    private void renderInput() {
        double openValue = this.openAnim.get();
        if (openValue <= 0.01) {
            return;
        }

        FontRenderer font = mc.fontRenderer;
        int screenHeight = mc.getMainWindow().getScaledHeight();
        float panelWidth = this.getNumberValue("Width");
        float rounding = this.getNumberValue("Rounding");
        float lineHeight = font.FONT_HEIGHT;
        float inputHeight = lineHeight + 8.0F;
        float x = 0.0F;
        float y = screenHeight - 2.0F - inputHeight + this.chatYOffset;
        double scale = openValue > 1.0 ? 1.0 + (openValue - 1.0) * 0.4 : openValue;
        double alpha = Math.max(0.0, Math.min(255.0, this.alphaAnim.get()));

        String text = this.lastInputText;
        int cursor = this.lastInputCursor;
        boolean open = mc.currentScreen instanceof RiseChatScreen;
        if (open) {
            text = ((RiseChatScreen) mc.currentScreen).getInputField().getText();
            cursor = ((RiseChatScreen) mc.currentScreen).getInputField().getCursorPosition();
            this.lastInputText = text;
            this.lastInputCursor = cursor;
        }

        float centerX = x + panelWidth / 2.0F;
        float centerY = y + inputHeight / 2.0F;
        GL11.glPushMatrix();
        GL11.glTranslated(centerX * (1.0 - scale), centerY * (1.0 - scale), 0.0);
        GL11.glScaled(scale, scale, 1.0);
        if (this.getBooleanValue("Background")) {
            drawRoundRect(x, y, panelWidth, inputHeight, rounding, this.backgroundArgb((int) alpha));
        }

        MatrixStack matrices = new MatrixStack();
        font.drawStringWithShadow(matrices, text, x + 5.0F, y + 4.0F, 0xFFFFFF | ((int) alpha << 24));
        if (open && (System.currentTimeMillis() / 500L) % 2L == 0L) {
            int shown = Math.min(cursor, text.length());
            float caretX = x + 5.0F + font.getStringWidth(text.substring(0, shown));
            fillRect(caretX, y + 3.0F, caretX + 1.0F, y + 3.0F + lineHeight, 0xE0FFFFFF);
        }
        GL11.glPopMatrix();
    }

    void scrollBy(double delta) {
        this.scrollTarget = Math.max(0.0, this.scrollTarget + delta * mc.fontRenderer.FONT_HEIGHT);
    }

    /** First maxChars visible characters of a line, for the wave-prefix check. */
    private static String head(IReorderingProcessor line, int maxChars) {
        StringBuilder builder = new StringBuilder(maxChars);
        line.accept((index, style, codePoint) -> {
            builder.appendCodePoint(codePoint);
            return builder.length() < maxChars;
        });
        return builder.toString();
    }

    /** View of a line with its first n visible characters removed (the wave prefix is drawn separately). */
    private static IReorderingProcessor skip(IReorderingProcessor line, int n) {
        return consumer -> line.accept((index, style, codePoint) ->
                index < n || consumer.accept(index - n, style, codePoint));
    }

    private float drawWave(FontRenderer font, MatrixStack matrices, String text, float x, float y) {
        long now = System.currentTimeMillis();
        int length = text.length();
        float cx = x;
        for (int i = 0; i < length; i++) {
            char c = text.charAt(i);
            double phase = (double) i / Math.max(1, length) * Math.PI * 2.0;
            double t = (Math.sin(now * 0.005 + phase) + 1.0) * 0.5;
            int color = blend(WAVE_FROM, WAVE_TO, t);
            font.drawStringWithShadow(matrices, String.valueOf(c), cx, y, color | 0xFF000000);
            cx += font.getStringWidth(String.valueOf(c));
        }
        return cx - x;
    }

    private static int blend(int from, int to, double t) {
        int r = (int) (((from >> 16) & 0xFF) + (((to >> 16) & 0xFF) - ((from >> 16) & 0xFF)) * t);
        int g = (int) (((from >> 8) & 0xFF) + (((to >> 8) & 0xFF) - ((from >> 8) & 0xFF)) * t);
        int b = (int) ((from & 0xFF) + ((to & 0xFF) - (from & 0xFF)) * t);
        return r << 16 | g << 8 | b;
    }

    /**
     * Immediate-mode shapes. Two rules, both learned the hard way in this HUD pass:
     * <p>
     * 1. Set every state through BOTH RenderSystem (keeps GlStateManager's cache in sync) and
     * raw GL (the actual state), because raw-GL callers elsewhere desync the cache and a cache
     * hit then skips the real call. This client's own RenderUtil.drawCircle does the same.
     * <p>
     * 2. Wind vertices counter-clockwise in screen space, like every working helper in
     * RenderUtil. InfoHUD renders a real entity in this same pass, which leaves face culling
     * enabled, so a clockwise polygon is silently back-face culled and never appears. Culling
     * is also disabled here as insurance and restored afterwards.
     */
    private static void setupShape(int argb) {
        float a = (float) (argb >>> 24 & 0xFF) / 255.0F;
        float r = (float) (argb >> 16 & 0xFF) / 255.0F;
        float g = (float) (argb >> 8 & 0xFF) / 255.0F;
        float b = (float) (argb & 0xFF) / 255.0F;
        RenderSystem.enableBlend();
        GL11.glEnable(GL11.GL_BLEND);
        RenderSystem.disableTexture();
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        // Query the real state rather than assuming: restoring it exactly keeps later HUD
        // listeners in this pass seeing what they saw before.
        cullWasEnabled = GL11.glIsEnabled(GL11.GL_CULL_FACE);
        RenderSystem.disableCull();
        GL11.glDisable(GL11.GL_CULL_FACE);
        // Alpha channel (ZERO, ONE) preserves destination alpha for OptiFine shader composites.
        RenderSystem.blendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
                GlStateManager.SourceFactor.ZERO, GlStateManager.DestFactor.ONE);
        GL14.glBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ZERO, GL11.GL_ONE);
        RenderSystem.color4f(r, g, b, a);
        GL11.glColor4f(r, g, b, a);
    }

    private static void restoreShape() {
        RenderSystem.color4f(1.0F, 1.0F, 1.0F, 1.0F);
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.enableTexture();
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        if (cullWasEnabled) {
            RenderSystem.enableCull();
            GL11.glEnable(GL11.GL_CULL_FACE);
        }
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
        GL11.glDisable(GL11.GL_BLEND);
    }

    /** Rise draws this through its rounded-quad shader; a convex polygon is the shaderless equivalent. */
    private static void drawRoundRect(float x, float y, float width, float height, float radius, int argb) {
        radius = Math.max(0.0F, Math.min(radius, Math.min(width, height) / 2.0F));
        setupShape(argb);
        GL11.glBegin(GL11.GL_POLYGON);
        // Counter-clockwise in screen space: down the left edge, right along the bottom,
        // up the right edge, back left along the top. One quarter arc per corner.
        arcVertices(x + radius, y + radius, radius, 90.0F, 180.0F);
        arcVertices(x + radius, y + height - radius, radius, 180.0F, 270.0F);
        arcVertices(x + width - radius, y + height - radius, radius, 270.0F, 360.0F);
        arcVertices(x + width - radius, y + radius, radius, 360.0F, 450.0F);
        GL11.glEnd();
        restoreShape();
    }

    /** Screen-space arc (y grows downward), inclusive of both endpoints. */
    private static void arcVertices(float centerX, float centerY, float radius, float fromDeg, float toDeg) {
        int segments = 6;
        for (int i = 0; i <= segments; i++) {
            double angle = Math.toRadians(fromDeg + (toDeg - fromDeg) * i / segments);
            GL11.glVertex2f((float) (centerX + radius * Math.cos(angle)), (float) (centerY - radius * Math.sin(angle)));
        }
    }

    /** Same counter-clockwise winding as RenderUtil's own quads, for the same culling reason. */
    private static void fillRect(float x, float y, float x2, float y2, int argb) {
        setupShape(argb);
        GL11.glBegin(GL11.GL_QUADS);
        GL11.glVertex2f(x, y2);
        GL11.glVertex2f(x2, y2);
        GL11.glVertex2f(x2, y);
        GL11.glVertex2f(x, y);
        GL11.glEnd();
        restoreShape();
    }

    private int backgroundArgb() {
        return this.backgroundArgb(255);
    }

    private int backgroundArgb(int alpha) {
        float setting = this.getNumberValue("Background Opacity");
        int a = (int) Math.min(alpha, setting);
        return BG_COLOR & 0x00FFFFFF | a << 24;
    }

    private float getNumberValue(String name) {
        return (float) this.getNumberValueBySettingName(name);
    }

    private boolean getBooleanValue(String name) {
        return this.getBooleanValueFromSettingName(name);
    }

    private double[] scaledMouse() {
        double width = mc.getMainWindow().getWidth();
        double height = mc.getMainWindow().getHeight();
        double scaledWidth = mc.getMainWindow().getScaledWidth();
        double scaledHeight = mc.getMainWindow().getScaledHeight();
        return new double[]{
                mc.mouseHelper.getMouseX() * scaledWidth / width,
                mc.mouseHelper.getMouseY() * scaledHeight / height
        };
    }

    /** Minimal port of Rise's Animation (value eased toward a target). */
    private static final class Anim {
        static final int LINEAR = 0;
        static final int EASE_OUT_EXPO = 1;
        static final int EASE_IN_EXPO = 2;
        static final int EASE_OUT_ELASTIC = 3;

        private double from;
        private double to;
        private double current;
        private long start;
        private long duration = 1L;
        private int easing = EASE_OUT_EXPO;

        Anim(double value) {
            this.from = value;
            this.to = value;
            this.current = value;
        }

        void animate(double target, long duration, int easing) {
            if (target == this.to && duration == this.duration && easing == this.easing) {
                return;
            }
            this.from = this.get();
            this.to = target;
            this.duration = Math.max(1L, duration);
            this.easing = easing;
            this.start = System.currentTimeMillis();
        }

        void jump(double value) {
            this.current = value;
            this.from = value;
        }

        double get() {
            double progress = Math.max(0.0, Math.min(1.0,
                    (double) (System.currentTimeMillis() - this.start) / this.duration));
            this.current = this.from + (this.to - this.from) * ease(this.easing, progress);
            return this.current;
        }

        static double ease(int type, double p) {
            switch (type) {
                case LINEAR:
                    return p;
                case EASE_IN_EXPO:
                    return p <= 0.0 ? 0.0 : Math.pow(2.0, 10.0 * (p - 1.0));
                case EASE_OUT_ELASTIC:
                    if (p <= 0.0) return 0.0;
                    if (p >= 1.0) return 1.0;
                    return Math.pow(2.0, -10.0 * p) * Math.sin((p * 10.0 - 0.75) * (Math.PI * 2.0 / 3.0)) + 1.0;
                case EASE_OUT_EXPO:
                default:
                    return p >= 1.0 ? 1.0 : 1.0 - Math.pow(2.0, -10.0 * p);
            }
        }
    }

    /** Chat screen whose vanilla visuals are replaced by {@link RiseChatUI}; input logic stays vanilla. */
    public static class RiseChatScreen extends ChatScreen {
        private final RiseChatUI owner;

        public RiseChatScreen(String initialText, RiseChatUI owner) {
            super(initialText);
            this.owner = owner;
        }

        public net.minecraft.client.gui.widget.TextFieldWidget getInputField() {
            return this.inputField;
        }

        @Override
        public void render(MatrixStack matrices, int mouseX, int mouseY, float delta) {
            // Vanilla draws its own input box and chat here; the module draws both instead.
            // The vanilla suggestion dropdown is also skipped (Rise prints completions, not a dropdown);
            // Tab still cycles completions into the input via keyPressed.
            if (this.owner.hoveredStyle != null && this.owner.hoveredStyle.getHoverEvent() != null) {
                this.renderComponentHoverEffect(matrices, this.owner.hoveredStyle, mouseX, mouseY);
            }
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            if (button == 0 && this.owner.hoveredStyle != null && this.owner.hoveredStyle.getClickEvent() != null) {
                this.handleComponentClicked(this.owner.hoveredStyle);
                return true;
            }
            return super.mouseClicked(mouseX, mouseY, button);
        }

        @Override
        public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
            this.owner.scrollBy(delta);
            return super.mouseScrolled(mouseX, mouseY, delta);
        }
    }
}
