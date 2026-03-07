package com.mentalfrostbyte.jello.gui.impl.jello.mainmenu;

import com.google.gson.JsonArray;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.mentalfrostbyte.Client;
import com.mentalfrostbyte.jello.gui.base.animations.Animation;
import com.mentalfrostbyte.jello.gui.base.elements.impl.Change;
import com.mentalfrostbyte.jello.gui.combined.CustomGuiScreen;
import com.mentalfrostbyte.jello.gui.impl.jello.buttons.ScrollableContentPanel;
import com.mentalfrostbyte.jello.gui.impl.jello.mainmenu.changelog.Class576;
import com.mentalfrostbyte.jello.util.client.render.ResourceRegistry;
import com.mentalfrostbyte.jello.util.client.render.theme.ClientColors;
import com.mentalfrostbyte.jello.util.game.render.RenderUtil;
import com.mentalfrostbyte.jello.util.game.render.RenderUtil2;
import com.mentalfrostbyte.jello.util.system.math.SmoothInterpolator;
import org.lwjgl.glfw.GLFW;
import org.newdawn.slick.TrueTypeFont;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static com.mentalfrostbyte.jello.util.game.MinecraftUtil.mc;

public class ChangelogScreen extends CustomGuiScreen {
    public Animation animation = new Animation(380, 200, Animation.Direction.BACKWARDS);
    public ScrollableContentPanel scrollPanel;
    private static JsonArray cachedChangelog;

    private int opencount = 0;
    private boolean wasHovered = false;
    public static boolean isEasteregg = false;

    private static final Path LOCAL_CHANGELOG_FILE = Path.of("run", "changelog.json");
    private static final String DEFAULT_LOCAL_CHANGELOG = """
            [
                {
                    "title": "5.1.1 (1.16.4) Update",
                    "changes": [
                        "Moved changelog source to local file: run/changelog.json",
                        "Switched changelog workflow to local-only to avoid publishing on GitHub",
                        "You can edit run/changelog.json directly for future updates"
                    ]
                },
                {
                    "title": "5.1.0 (1.16.4) Update",
                    "changes": [
                        "Added Verus glide flight - by @alarmingly_good",
                        "Added Verus speeds - by @alarmingly_good",
                        "Added Vulcan AntiKB and Speed",
                        "Added Verus NoFall",
                        "Added Switch button to Jello",
                        "Added Cloud configs",
                        "Added MiniBlox bypasses",
                        "Fixed JelloPortal",
                        "Fixed many rotation flags",
                        "Fixed music player issues",
                        "Fixed visual bugs + render modules"
                    ]
                }
            ]""";

    public ChangelogScreen(CustomGuiScreen var1, String var2, int var3, int var4, int var5, int var6) {
        super(var1, var2, var3, var4, var5, var6);
        this.setListening(false);
        this.scrollPanel = new ScrollableContentPanel(this, "scroll", 100, 200, var5 - 200, var6 - 200);
        this.scrollPanel.method13518(true);
        this.showAlert(this.scrollPanel);
        new Thread(() -> this.method13490(this.getChangelog())).start();
    }

    public void method13490(JsonArray var1) {
        if (var1 != null) {
            this.getParent().runThisOnDimensionUpdate(new Class576(this, var1));
        }
    }

    @Override
    public void updatePanelDimensions(int newHeight, int newWidth) {
        super.updatePanelDimensions(newHeight, newWidth);
        if (this.scrollPanel != null) {
            if (this.isHovered() && this.isSelfVisible()) {
                for (CustomGuiScreen var9 : this.scrollPanel.getButton().getChildren()) {
                    Change var10 = (Change) var9;
                    var10.animation2.changeDirection(Animation.Direction.FORWARDS);
                    if ((double) var10.animation2.calcPercent() < 0.5) {
                        break;
                    }
                }
            } else {
                for (CustomGuiScreen var6 : this.scrollPanel.getButton().getChildren()) {
                    Change var7 = (Change) var6;
                    var7.animation2.changeDirection(Animation.Direction.BACKWARDS);
                }
            }
        }
    }

    @Override
    public void draw(float partialTicks) {
        this.animation.changeDirection(!this.isHovered() ? Animation.Direction.BACKWARDS : Animation.Direction.FORWARDS);
        partialTicks *= this.animation.calcPercent();

        float fadeFactor = SmoothInterpolator.interpolate(this.animation.calcPercent(), 0.17f, 1.0f, 0.51f, 1.0f);

        if (this.animation.getDirection() == Animation.Direction.BACKWARDS) {
            fadeFactor = 1.0f;
        }

        this.drawBackground((int) (150.0f * (1.0f - fadeFactor)));
        this.method13225();
        RenderUtil.drawString(ResourceRegistry.JelloLightFont36, 100.0F, 100.0F, "Changelog", RenderUtil2.applyAlpha(ClientColors.LIGHT_GREYISH_BLUE.getColor(), partialTicks));
        TrueTypeFont jelloLightFont25 = ResourceRegistry.JelloLightFont25;
        String versionText = "You're currently using Sigma " + Client.FULL_VERSION;
        RenderUtil.drawString(
                jelloLightFont25,
                100.0f, 150.0f,
                versionText,
                RenderUtil2.applyAlpha(ClientColors.LIGHT_GREYISH_BLUE.getColor(), 0.6f * partialTicks)
        );
        if (opencount >= 10) {
            GLFW.glfwSetWindowTitle(mc.getMainWindow().getHandle(), "Sigma Never Die!!!");
            isEasteregg = true;
        }
        super.draw(partialTicks);
    }

    public JsonArray getChangelog() {
        if (cachedChangelog != null) {
            return cachedChangelog;
        } else {
            String jsonString = getChanges();
            try {
                cachedChangelog = JsonParser.parseString(jsonString).getAsJsonArray();
            } catch (JsonParseException e) {
                throw new RuntimeException("Invalid JSON format for changelog", e);
            }
            return cachedChangelog;
        }
    }

    private String getChanges() {
        try {
            ensureLocalChangelogFile();
            return Files.readString(LOCAL_CHANGELOG_FILE, StandardCharsets.UTF_8);
        } catch (IOException e) {
            Client.logger.error("Failed to read local changelog file: " + LOCAL_CHANGELOG_FILE, e);
            return DEFAULT_LOCAL_CHANGELOG;
        }
    }

    @Override
    public void setHovered(boolean hovered) {
        if (hovered && !wasHovered) {
            opencount++;
        }
        super.setHovered(hovered);
        wasHovered = hovered;
    }

    private void ensureLocalChangelogFile() throws IOException {
        if (Files.exists(LOCAL_CHANGELOG_FILE)) {
            return;
        }

        Path parent = LOCAL_CHANGELOG_FILE.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }

        Files.writeString(LOCAL_CHANGELOG_FILE, DEFAULT_LOCAL_CHANGELOG, StandardCharsets.UTF_8);
    }
}