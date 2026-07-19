package com.mentalfrostbyte.jello.gui.base.elements.impl;

import com.mentalfrostbyte.Client;
import com.mentalfrostbyte.jello.gui.base.alerts.AlertComponent;
import com.mentalfrostbyte.jello.gui.base.alerts.ComponentType;
import com.mentalfrostbyte.jello.gui.base.animations.Animation;
import com.mentalfrostbyte.jello.gui.base.elements.Element;
import com.mentalfrostbyte.jello.gui.base.elements.impl.button.Button;
import com.mentalfrostbyte.jello.gui.combined.AnimatedIconPanel;
import com.mentalfrostbyte.jello.gui.combined.CustomGuiScreen;
import com.mentalfrostbyte.jello.gui.impl.jello.altmanager.AltManagerScreen;
import com.mentalfrostbyte.jello.gui.impl.jello.buttons.LoadingIndicator;
import com.mentalfrostbyte.jello.gui.impl.jello.buttons.TextField;
import com.mentalfrostbyte.jello.managers.util.account.microsoft.Account;
import com.mentalfrostbyte.jello.util.client.network.microsoft.CookieLoginUtil;
import com.mentalfrostbyte.jello.util.client.network.microsoft.MicrosoftLoginUtil;
import com.mentalfrostbyte.jello.util.client.network.microsoft.RandomLoginUtil;
import com.mentalfrostbyte.jello.util.client.render.ResourceRegistry;
import com.mentalfrostbyte.jello.util.client.render.theme.ClientColors;
import com.mentalfrostbyte.jello.util.client.render.theme.ColorHelper;
import com.mentalfrostbyte.jello.util.game.render.RenderUtil;
import com.mentalfrostbyte.jello.util.game.render.RenderUtil2;
import com.mentalfrostbyte.jello.util.system.FileUtil;
import com.mentalfrostbyte.jello.util.system.math.smoothing.QuadraticEasing;
import com.mentalfrostbyte.jello.util.system.network.ImageUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Session;
import org.newdawn.slick.opengl.Texture;
import org.newdawn.slick.util.BufferedImageUtil;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Alert extends Element {
    public CustomGuiScreen screen;
    public String alertName;
    public Texture field21281;
    private final Animation field21282 = new Animation(285, 100);
    public boolean field21283;
    public int field21284 = 240;
    public int field21285 = 0;
    private Map<String, String> inputMap;
    private final List<Class9448> field21287 = new ArrayList<>();

    public List<Button> buttons = new ArrayList<>();

    // "Logging in" overlay: a spinner + status line that temporarily replaces the
    // dialog's option controls while an interactive login (Web login) is in flight.
    private LoadingIndicator loadingSpinner;
    private Text statusText;
    private Text statusHint;
    private boolean loadingState;
    // True once the in-flight login failed and the overlay is showing the retry prompt;
    // a click inside the modal then returns to the option controls instead of closing.
    private boolean loginErrored;
    // Bumped every time we leave the loading state (cancel/close/finish). A background
    // login captures the value at start and only touches the UI if it still matches,
    // so a stale login can't hijack a dialog the user has since dismissed or reused.
    private int loginGeneration;
    // Executor for the in-flight interactive login, kept so a cancel can shut it down.
    private ExecutorService activeLoginExecutor;

    public Alert(CustomGuiScreen screen, String iconName, boolean var3, String name, AlertComponent... var5) {
        super(screen, iconName, 0, 0, Minecraft.getInstance().getMainWindow().getWidth(),
                Minecraft.getInstance().getMainWindow().getHeight(), false);
        this.field21283 = var3;
        this.alertName = name;
        this.setHovered(false);
        this.setReAddChildren(false);
        this.method13243();
        TextField var8 = null;
        TextField var9 = null;

        for (AlertComponent var13 : var5) {
            this.field21285 = this.field21285 + var13.field44773 + 10;
        }

        this.field21285 -= 10;
        this.addToList(
                this.screen = new CustomGuiScreen(
                        this, "modalContent", (this.widthA - this.field21284) / 2, (this.heightA - this.field21285) / 2,
                        this.field21284, this.field21285));
        int var17 = 0;
        int var18 = 0;

        for (AlertComponent component : var5) {
            var17++;
            if (component.componentType != ComponentType.FIRST_LINE) {
                if (component.componentType != ComponentType.SECOND_LINE) {
                    if (component.componentType != ComponentType.BUTTON) {
                        if (component.componentType == ComponentType.HEADER) {
                            this.screen
                                    .addToList(
                                            new Text(
                                                    this.screen,
                                                    "Item" + var17,
                                                    0,
                                                    var18,
                                                    this.field21284,
                                                    component.field44773,
                                                    new ColorHelper(
                                                            ClientColors.DEEP_TEAL.getColor(),
                                                            ClientColors.DEEP_TEAL.getColor(),
                                                            ClientColors.DEEP_TEAL.getColor(),
                                                            ClientColors.DEEP_TEAL.getColor()),
                                                    component.text,
                                                    ResourceRegistry.JelloLightFont36));
                        }
                    } else {
                        Button button;
                        this.screen.addToList(button = new Button(this.screen, "Item" + var17, 0, var18,
                                this.field21284, component.field44773, ColorHelper.field27961, component.text));
                        this.buttons.add(button);
                        button.field20586 = 4;
                        button.onClick((var1x, var2x) -> {
                            switch (button.text) {
                                case "Cookie login" -> {
                                    File file = FileUtil.getFileFromDialog();
                                    if (file != null) {
                                        try {
                                            CookieLoginUtil.LoginData session = CookieLoginUtil.loginWithCookie(file);
                                            if (session == null) {
                                                Client.getInstance().soundManager.play("error");
                                                this.inputMap = this.method13599();
                                                this.method13603(false);
                                                return;
                                            }

                                            Account account = this.createAuthenticatedAccount(
                                                    session.username, session.playerID, session.token);
                                            if (!Client.getInstance().accountManager.containsAccount(account)) {
                                                Client.getInstance().accountManager.updateAccount(account);
                                            }

                                            this.inputMap = this.method13599();
                                            this.method13603(false);
                                            AltManagerScreen.instance.updateAccountList(false);
                                        } catch (Exception e) {
                                            Client.getInstance().soundManager.play("error");
                                            this.inputMap = this.method13599();
                                            this.method13603(false);
                                        }
                                    }
                                }
                                case "Web login" -> {
                                    ExecutorService executor = Executors.newSingleThreadExecutor();
                                    this.activeLoginExecutor = executor;
                                    // Flip the dialog to the "logging in" overlay and remember which
                                    // login this is, so a cancel/reopen invalidates a stale result.
                                    this.enterLoadingState("Opening your browser…",
                                            "Sign in with Microsoft, then come back here.");
                                    int generation = this.currentLoginGeneration();
                                    MicrosoftLoginUtil.acquireMSAuthCodeSession(executor)
                                            .thenComposeAsync(authCodeSession -> {
                                                // The callback socket just received the auth code (the
                                                // "hook"): switch the status to the token-exchange phase.
                                                Minecraft.getInstance().execute(() -> {
                                                    if (this.currentLoginGeneration() == generation) {
                                                        this.setStatus("Authorization received…",
                                                                "Signing you in, hang tight.");
                                                    }
                                                });
                                                return MicrosoftLoginUtil
                                                        .loginWithAuthCodeSession(authCodeSession, executor);
                                            }, executor)
                                            .thenAccept(msSession -> Minecraft.getInstance().execute(() -> {
                                                // Persist the account regardless of dialog state: the login
                                                // succeeded, so the side effect must land. Only the UI
                                                // transition is gated on the generation still being current.
                                                Session session = msSession.session();
                                                Account account = this.createAuthenticatedAccount(
                                                        session.username, session.playerID, session.token,
                                                        msSession.refreshToken());
                                                if (!Client.getInstance().accountManager.containsAccount(account)) {
                                                    Client.getInstance().accountManager.updateAccount(account);
                                                }
                                                // Persist immediately so the refresh token survives a restart.
                                                Client.getInstance().accountManager.saveAlts();

                                                if (this.currentLoginGeneration() == generation) {
                                                    this.inputMap = this.method13599();
                                                    this.method13603(false);
                                                }
                                                AltManagerScreen.instance.updateAccountList(false);
                                            }))
                                            .exceptionally(error -> {
                                                Client.getInstance().logger.error("Microsoft web login failed", error);
                                                Minecraft.getInstance().execute(() -> {
                                                    // A user cancel already moved on; don't stomp the dialog.
                                                    if (this.currentLoginGeneration() != generation) {
                                                        return;
                                                    }
                                                    Client.getInstance().soundManager.play("error");
                                                    this.setStatus("Login didn't complete",
                                                            "Click here to go back and try again.");
                                                    this.loginErrored = true;
                                                    this.loadingSpinner.setHovered(false);
                                                });
                                                return null;
                                            })
                                            .whenComplete((ignored, error) -> {
                                                executor.shutdown();
                                                if (this.activeLoginExecutor == executor) {
                                                    this.activeLoginExecutor = null;
                                                }
                                            });
                                }
                                case "Token login" -> {
                                    this.inputMap = this.method13599();
                                    String token = this.inputMap.get("Email");
                                    if (token != null && !token.isEmpty()) {
                                        new Thread(() -> {
                                            Account account = new Account("Token Account", "Token ID", token);
                                            try {
                                                if (Client.getInstance().accountManager.login(account)) {
                                                    if (!Client.getInstance().accountManager.containsAccount(account)) {
                                                        Client.getInstance().accountManager.updateAccount(account);
                                                    }
                                                    Minecraft.getInstance().execute(() -> {
                                                        this.method13603(false);
                                                        AltManagerScreen.instance.updateAccountList(false);
                                                    });
                                                } else {
                                                    Client.getInstance().soundManager.play("error");
                                                }
                                            } catch (Exception e) {
                                                Client.getInstance().soundManager.play("error");
                                            }
                                        }).start();
                                    } else {
                                        Client.getInstance().soundManager.play("error");
                                    }
                                }
                                case "Random login" -> this.loginWithRandomOfflineAccount();
                                default -> this.onButtonClick();
                            }
                        });
                    }
                } else {
                    TextField var22;
                    this.screen
                            .addToList(
                                    var22 = new TextField(
                                            this.screen, "Item" + var17, 0, var18, this.field21284,
                                            component.field44773, TextField.field20741, "", component.text));
                    if (!component.text.contains("Password")) {
                        if (component.text.contains("Email")) {
                            var8 = var22;
                        }
                    } else {
                        var9 = var22;
                        var22.setCensorText(true);
                    }
                }
            } else {
                this.screen
                        .addToList(
                                new Text(
                                        this.screen,
                                        "Item" + var17,
                                        0,
                                        var18,
                                        this.field21284,
                                        component.field44773,
                                        new ColorHelper(
                                                ClientColors.MID_GREY.getColor(), ClientColors.MID_GREY.getColor(),
                                                ClientColors.MID_GREY.getColor(), ClientColors.MID_GREY.getColor()),
                                        component.text,
                                        ResourceRegistry.JelloLightFont20));
            }

            var18 += component.field44773 + 10;
        }

        if (var8 != null && var9 != null) {
            TextField var20 = var9;
            var8.addChangeListener(var2x -> {
                String var5x = var2x.getText();
                if (var5x != null && var5x.contains(":")) {
                    String[] var6 = var5x.split(":");
                    if (var6.length <= 2) {
                        if (var6.length > 0) {
                            var2x.setText(var6[0].replace("\n", ""));
                            if (var6.length == 2) {
                                var20.setText(var6[1].replace("\n", ""));
                            }
                        }
                    } else {
                        this.onButtonClick();
                    }
                }
            });
        }

        this.buildLoadingView();
    }

    /**
     * Adds the (initially hidden) spinner + status lines that overlay the modal while
     * an interactive login is running. They live in the same modal content area as the
     * option controls; {@link #enterLoadingState(String)} swaps which set is visible.
     */
    private void buildLoadingView() {
        int spinnerSize = 34;
        int centerX = this.field21284 / 2;
        int centerY = this.field21285 / 2;

        this.screen.addToList(this.loadingSpinner = new LoadingIndicator(
                this.screen, "loginSpinner", centerX - spinnerSize / 2, centerY - spinnerSize - 6,
                spinnerSize, spinnerSize));
        this.loadingSpinner.setHovered(false);

        this.screen.addToList(this.statusText = new Text(
                this.screen, "loginStatus", 0, centerY + 6, this.field21284, 20,
                new ColorHelper(
                        ClientColors.DEEP_TEAL.getColor(), ClientColors.DEEP_TEAL.getColor(),
                        ClientColors.DEEP_TEAL.getColor(), ClientColors.DEEP_TEAL.getColor()),
                "", ResourceRegistry.JelloLightFont20));

        this.screen.addToList(this.statusHint = new Text(
                this.screen, "loginHint", 0, centerY + 30, this.field21284, 16,
                new ColorHelper(
                        ClientColors.MID_GREY.getColor(), ClientColors.MID_GREY.getColor(),
                        ClientColors.MID_GREY.getColor(), ClientColors.MID_GREY.getColor()),
                "", ResourceRegistry.JelloLightFont20));

        this.loadingSpinner.setSelfVisible(false);
        this.statusText.setSelfVisible(false);
        this.statusHint.setSelfVisible(false);
    }

    /**
     * Hides the option controls and shows the spinner + status line. Safe to call off
     * the render thread only via {@code Minecraft.execute}; callers here already do.
     */
    public void enterLoadingState(String message, String hint) {
        this.loadingState = true;
        this.loginErrored = false;
        for (CustomGuiScreen child : this.screen.getChildren()) {
            boolean isStatusElement = child == this.loadingSpinner
                    || child == this.statusText || child == this.statusHint;
            child.setSelfVisible(isStatusElement);
        }
        if (this.loadingSpinner != null) {
            this.loadingSpinner.setHovered(true);
        }
        this.setStatus(message, hint);
    }

    /** Restores the normal option controls, hiding the loading overlay. */
    public void showOptionsState() {
        this.loadingState = false;
        this.loginErrored = false;
        this.loginGeneration++;
        for (CustomGuiScreen child : this.screen.getChildren()) {
            boolean isStatusElement = child == this.loadingSpinner
                    || child == this.statusText || child == this.statusHint;
            child.setSelfVisible(!isStatusElement);
        }
        if (this.loadingSpinner != null) {
            this.loadingSpinner.setHovered(false);
        }
    }

    /** Updates the two status lines shown during a login (centered by the Text width). */
    public void setStatus(String message, String hint) {
        if (this.statusText != null) {
            this.statusText.setText(message != null ? message : "");
            this.centerStatusLine(this.statusText, this.statusText.getText());
        }
        if (this.statusHint != null) {
            this.statusHint.setText(hint != null ? hint : "");
            this.centerStatusLine(this.statusHint, this.statusHint.getText());
        }
    }

    private void centerStatusLine(Text line, String text) {
        int textWidth = line.getFont().getWidth(text != null ? text : "");
        line.setXA((this.field21284 - textWidth) / 2);
    }

    public boolean isLoadingState() {
        return this.loadingState;
    }

    public int currentLoginGeneration() {
        return this.loginGeneration;
    }

    @Override
    public void setHovered(boolean hovered) {
        if (hovered) {
            for (CustomGuiScreen var5 : this.screen.getChildren()) {
                if (var5 instanceof TextField) {
                    var5.setText("");
                    ((TextField) var5).method13146();
                }
            }
        }

        this.field21282.changeDirection(!hovered ? Animation.Direction.BACKWARDS : Animation.Direction.FORWARDS);
        super.setHovered(hovered);
    }

    private Map<String, String> method13599() {
        HashMap var3 = new HashMap();

        for (CustomGuiScreen var5 : this.screen.getChildren()) {
            AnimatedIconPanel var6 = (AnimatedIconPanel) var5;
            if (var6 instanceof TextField var7) {
                var3.put(var7.getPlaceholder(), var7.getText());
            }
        }

        return var3;
    }

    private Account createAuthenticatedAccount(String username, String playerID, String token) {
        return this.createAuthenticatedAccount(username, playerID, token, null);
    }

    private Account createAuthenticatedAccount(String username, String playerID, String token, String refreshToken) {
        String safeUsername = !this.isBlank(username) ? username : "Unknown name";
        String safePlayerID = playerID != null ? playerID : "";
        Account account = new Account(safeUsername, safePlayerID, token);
        account.setName(safeUsername);

        if (!this.isBlank(safePlayerID)) {
            account.setUuid(Account.fixUUID(safePlayerID));
        }

        if (!this.isBlank(refreshToken)) {
            account.setRefreshToken(refreshToken);
        }

        return account;
    }

    /**
     * Cancels the in-flight interactive login (if any): invalidates the generation so a
     * late completion won't touch the UI, and interrupts the worker. The OAuth callback
     * socket is inside that executor, so shutdownNow unblocks the pending accept().
     */
    private void cancelActiveLogin() {
        this.loadingState = false;
        this.loginGeneration++;
        if (this.loadingSpinner != null) {
            this.loadingSpinner.setHovered(false);
        }
        if (this.activeLoginExecutor != null) {
            this.activeLoginExecutor.shutdownNow();
            this.activeLoginExecutor = null;
        }
    }

    private void loginWithRandomOfflineAccount() {
        new Thread(() -> {
            Account account = RandomLoginUtil.login(Client.getInstance().accountManager);
            Minecraft.getInstance().execute(() -> {
                if (account == null) {
                    Client.getInstance().soundManager.play("error");
                    return;
                }

                this.inputMap = this.method13599();
                this.method13603(false);
                Client.getInstance().soundManager.play("connect");

                if (AltManagerScreen.instance != null) {
                    AltManagerScreen.instance.updateAccountList(false);
                }
            });
        }, "RandomLogin").start();
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    public Map<String, String> getInputMap() {
        return this.inputMap;
    }

    public void onButtonClick() {
        this.inputMap = this.method13599();
        this.method13603(false);
        this.callUIHandlers();
    }

    @Override
    public void onClick3(int mouseX, int mouseY, int mouseButton) {
        super.onClick3(mouseX, mouseY, mouseButton);
    }

    public float method13602(float var1, float var2) {
        return this.field21282.getDirection() != Animation.Direction.BACKWARDS
                ? (float) (Math.pow(2.0, -10.0F * var1)
                        * Math.sin((double) (var1 - var2 / 4.0F) * (Math.PI * 2) / (double) var2) + 1.0)
                : 0.5F + QuadraticEasing.easeOutQuad(var1, 0.0F, 1.0F, 1.0F) * 0.5F;
    }

    @Override
    public void draw(float partialTicks) {
        if (this.field21282.calcPercent() != 0.0F) {
            int var4 = this.field21284 + 60;
            int var5 = this.field21285 + 60;
            float var7 = !this.isHovered() ? this.field21282.calcPercent()
                    : Math.min(this.field21282.calcPercent() / 0.25F, 1.0F);
            float var8 = this.method13602(this.field21282.calcPercent(), 1.0F);
            var4 = (int) ((float) var4 * var8);
            var5 = (int) ((float) var5 * var8);
            RenderUtil.drawTexture(
                    -5.0F,
                    -5.0F,
                    (float) (this.getWidthA() + 10),
                    (float) (this.getHeightA() + 10),
                    this.field21281,
                    RenderUtil2.applyAlpha(ClientColors.LIGHT_GREYISH_BLUE.getColor(), var7));
            RenderUtil.drawRoundedRect(
                    0.0F, 0.0F, (float) this.getWidthA(), (float) this.getHeightA(),
                    RenderUtil2.applyAlpha(ClientColors.DEEP_TEAL.getColor(), 0.1F * var7));
            if (var4 > 0) {
                RenderUtil.method11465(
                        (this.widthA - var4) / 2, (this.heightA - var5) / 2, var4, var5,
                        RenderUtil2.applyAlpha(ClientColors.LIGHT_GREYISH_BLUE.getColor(), var7));
            }

            super.method13279(var8, var8);
            super.method13224();
            super.draw(var7);
        } else {
            if (this.isFocused()) {
                this.setFocused(false);
                this.setSelfVisible(false);
                this.method13243();
            }
        }
    }

    @Override
    public boolean onClick(int mouseX, int mouseY, int mouseButton) {
        if (!super.onClick(mouseX, mouseY, mouseButton)) {
            int var6 = this.field21284 + 60;
            int var7 = this.field21285 + 60;
            if (mouseX > (this.widthA - var6) / 2
                    && mouseX < (this.widthA - var6) / 2 + var6
                    && mouseY > (this.heightA - var7) / 2
                    && mouseY < (this.heightA - var7) / 2 + var7) {
                // Clicking the failed-login overlay returns to the option controls to retry.
                if (this.loadingState && this.loginErrored) {
                    this.showOptionsState();
                }

                return false;
            } else {
                this.method13603(false);
                return false;
            }
        } else {
            return true;
        }
    }

    public void method13603(boolean var1) {
        if (var1 && !this.isHovered()) {
            // Opening: always start on the option controls, never a stale loading overlay.
            if (this.loadingState) {
                this.showOptionsState();
            }

            try {
                if (this.field21281 != null) {
                    this.field21281.release();
                }

                this.field21281 = BufferedImageUtil.getTexture(
                        "blur", ImageUtil.method35036(0, 0, this.getWidthA(), this.getHeightA(), 5, 10,
                                ClientColors.LIGHT_GREYISH_BLUE.getColor(), true));
            } catch (IOException var5) {
                Client.getInstance().logger.error(var5.getMessage());
            }
        } else if (!var1 && this.loadingState) {
            // Closing while a login is in flight is a user cancel: stop caring about the
            // result (generation bump) and release the background worker.
            this.cancelActiveLogin();
        }

        if (this.isHovered() != var1 && !var1) {
            this.method13605();
        }

        this.setHovered(var1);
        if (var1) {
            this.setSelfVisible(true);
        }

        this.setReAddChildren(var1);
    }

    public final void method13604(Class9448 var1) {
        this.field21287.add(var1);
    }

    public final void method13605() {
        for (Class9448 var4 : this.field21287) {
            var4.method36327(this);
        }
    }

    public interface Class9448 {
        void method36327(Element var1);
    }
}
