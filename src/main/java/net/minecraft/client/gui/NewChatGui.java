package net.minecraft.client.gui;

import com.google.common.collect.Lists;
import com.google.common.collect.Queues;
import com.mentalfrostbyte.jello.event.impl.game.render.EventRenderChat;
import com.mojang.blaze3d.matrix.MatrixStack;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MainWindow;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.entity.player.ChatVisibility;
import net.minecraft.util.IReorderingProcessor;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.Style;
import net.minecraft.util.text.TranslationTextComponent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import team.sdhq.eventBus.EventBus;

import javax.annotation.Nullable;
import java.util.Deque;
import java.util.List;

public class NewChatGui extends AbstractGui {
    private static final Logger LOGGER = LogManager.getLogger();
    private final Minecraft mc;
    private final List<String> sentMessages = Lists.newArrayList();
    private final List<ChatLine<ITextComponent>> chatLines = Lists.newArrayList();
    private final List<ChatLine<IReorderingProcessor>> drawnChatLines = Lists.newArrayList();
    private final Deque<ITextComponent> queuedMessages = Queues.newArrayDeque();
    private int scrollPos;
    private boolean isScrolled;
    private long field_238490_l_ = 0L;
    private int lastChatWidth = 0;

    public NewChatGui(Minecraft mcIn) {
        this.mc = mcIn;
    }

    public void func_238492_a_(MatrixStack matrix, int currentTick) {
        int chatWidth = this.getChatWidth();

        if (this.lastChatWidth != chatWidth) {
            this.lastChatWidth = chatWidth;
            this.refreshChat();
        }

        if (!this.isChatHidden()) {
            this.clearExpiredChatLines();
            int maxVisibleLines = this.getLineCount();
            int totalChatLines = this.drawnChatLines.size();

            if (totalChatLines > 0) {
                boolean isChatOpen = this.getChatOpen();
                double chatScale = this.getScale();

                int scaledChatWidth = MathHelper.ceil((double) this.getChatWidth() / chatScale);

                RenderSystem.pushMatrix();
                RenderSystem.translatef(2.0F, 8.0F, 0.0F);
                RenderSystem.scaled(chatScale, chatScale, 1.0D);

                double chatOpacity = this.mc.gameSettings.chatOpacity * (double) 0.9F + (double) 0.1F;
                double backgroundOpacity = this.mc.gameSettings.accessibilityTextBackgroundOpacity;
                double lineHeight = 9.0D * (this.mc.gameSettings.chatLineSpacing + 1.0D);
                double textOffset = -8.0D * (this.mc.gameSettings.chatLineSpacing + 1.0D) + 4.0D * this.mc.gameSettings.chatLineSpacing;
                int renderedLines = 0;

                for (int lineIndex = 0; lineIndex + this.scrollPos < this.drawnChatLines.size() && lineIndex < maxVisibleLines; ++lineIndex) {
                    ChatLine<IReorderingProcessor> chatline = this.drawnChatLines.get(lineIndex + this.scrollPos);

                    if (chatline != null) {
                        int age = currentTick - chatline.getUpdatedCounter();

                        if (age < 200 || isChatOpen) {
                            double lineOpacity = isChatOpen ? 1.0D : getLineBrightness(age);
                            int textAlpha = (int) (255.0D * lineOpacity * chatOpacity);
                            int backgroundAlpha = (int) (255.0D * lineOpacity * backgroundOpacity);
                            ++renderedLines;

                            if (textAlpha > 3) {
                                double yOffset = (double) (-lineIndex) * lineHeight;

                                EventRenderChat eventRenderChat = new EventRenderChat();
                                EventBus.call(eventRenderChat);

                                yOffset += eventRenderChat.getYOffset();

                                matrix.push();
                                matrix.translate(0.0D, 0.0D, 50.0D);

                                if (this.mc.gameSettings.ofChatBackground == 5) {
                                    scaledChatWidth = this.mc.fontRenderer.getStringWidth(chatline.getLineString()) - 2;
                                }

                                if (this.mc.gameSettings.ofChatBackground != 3) {
                                    fill(matrix, -2, (int) (yOffset - lineHeight), scaledChatWidth + 4, (int) yOffset, backgroundAlpha << 24);
                                }

                                RenderSystem.enableBlend();
                                matrix.translate(0.0D, 0.0D, 50.0D);

                                /*
                                   +20 would make the text go down, so do -20
                                 */
                                if (!this.mc.gameSettings.ofChatShadow) {
                                    this.mc.fontRenderer.func_238422_b_(matrix, chatline.getLineString(), 0.0F, (float) ((int) (yOffset + textOffset)), 16777215 + (textAlpha << 24));
                                } else {
                                    this.mc.fontRenderer.func_238407_a_(matrix, chatline.getLineString(), 0.0F, (float) ((int) (yOffset + textOffset)), 16777215 + (textAlpha << 24));
                                }

                                RenderSystem.disableAlphaTest();
                                RenderSystem.disableBlend();
                                matrix.pop();
                            }
                        }
                    }
                }

                if (!this.queuedMessages.isEmpty()) {
                    int queueAlpha = (int) (128.0D * chatOpacity);
                    int queueBackgroundAlpha = (int) (255.0D * backgroundOpacity);
                    matrix.push();
                    matrix.translate(0.0D, 0.0D, 50.0D);
                    fill(matrix, -2, 0, scaledChatWidth + 4, 9, queueBackgroundAlpha << 24);
                    RenderSystem.enableBlend();
                    matrix.translate(0.0D, 0.0D, 50.0D);
                    this.mc.fontRenderer.drawText(matrix, new TranslationTextComponent("chat.queue", this.queuedMessages.size()), 0.0F, 1.0F, 16777215 + (queueAlpha << 24));
                    matrix.pop();
                    RenderSystem.disableAlphaTest();
                    RenderSystem.disableBlend();
                }

                if (isChatOpen) {
                    int scrollbarHeight = 9;
                    RenderSystem.translatef(-3.0F, 0.0F, 0.0F);
                    int totalScrollHeight = totalChatLines * scrollbarHeight + totalChatLines;
                    int visibleScrollHeight = renderedLines * scrollbarHeight + renderedLines;
                    int scrollBarTop = this.scrollPos * visibleScrollHeight / totalChatLines;
                    int scrollBarHeight = visibleScrollHeight * visibleScrollHeight / totalScrollHeight;

                    if (totalScrollHeight != visibleScrollHeight) {
                        int scrollbarBaseColor = scrollBarTop > 0 ? 170 : 96;
                        int scrollbarBackgroundAlpha = this.isScrolled ? 13382451 : 3355562;
                        fill(matrix, 0, -scrollBarTop, 2, -scrollBarTop - scrollBarHeight, scrollbarBackgroundAlpha + (scrollbarBaseColor << 24));
                        fill(matrix, 2, -scrollBarTop, 1, -scrollBarTop - scrollBarHeight, 13421772 + (scrollbarBaseColor << 24));
                    }
                }

                RenderSystem.popMatrix();
            }
        }
    }

    private boolean isChatHidden() {
        return this.mc.gameSettings.chatVisibility == ChatVisibility.HIDDEN;
    }

    private static double getLineBrightness(int counterIn) {
        double d0 = (double) counterIn / 200.0D;
        d0 = 1.0D - d0;
        d0 = d0 * 10.0D;
        d0 = MathHelper.clamp(d0, 0.0D, 1.0D);
        return d0 * d0;
    }

    /**
     * Clears the chat.
     *
     * @param clearSentMsgHistory Whether or not to clear the user's sent message history
     */
    public void clearChatMessages(boolean clearSentMsgHistory) {
        this.queuedMessages.clear();
        this.drawnChatLines.clear();
        this.chatLines.clear();

        if (clearSentMsgHistory) {
            this.sentMessages.clear();
        }
    }

    public void printChatMessage(ITextComponent chatComponent) {
        this.printChatMessageWithOptionalDeletion(chatComponent, 0);
    }

    /**
     * prints the ChatComponent to Chat. If the ID is not 0, deletes an existing Chat Line of that ID from the GUI
     */
    public void printChatMessageWithOptionalDeletion(ITextComponent chatComponent, int chatLineId) {
        this.func_238493_a_(chatComponent, chatLineId, this.mc.ingameGUI.getTicks(), false);
        LOGGER.info("[CHAT] {}", (Object) chatComponent.getString().replaceAll("\r", "\\\\r").replaceAll("\n", "\\\\n"));
    }

    private void func_238493_a_(ITextComponent p_238493_1_, int p_238493_2_, int p_238493_3_, boolean p_238493_4_) {
        if (p_238493_2_ != 0) {
            this.deleteChatLine(p_238493_2_);
        }

        int i = MathHelper.floor((double) this.getChatWidth() / this.getScale());
        List<IReorderingProcessor> list = RenderComponentsUtil.func_238505_a_(p_238493_1_, i, this.mc.fontRenderer);
        boolean flag = this.getChatOpen();

        for (IReorderingProcessor ireorderingprocessor : list) {
            if (flag && this.scrollPos > 0) {
                this.isScrolled = true;
                this.addScrollPos(1.0D);
            }

            this.drawnChatLines.add(0, new ChatLine<>(p_238493_3_, ireorderingprocessor, p_238493_2_));
        }

        while (this.drawnChatLines.size() > 100) {
            this.drawnChatLines.remove(this.drawnChatLines.size() - 1);
        }

        if (!p_238493_4_) {
            this.chatLines.add(0, new ChatLine<>(p_238493_3_, p_238493_1_, p_238493_2_));

            while (this.chatLines.size() > 100) {
                this.chatLines.remove(this.chatLines.size() - 1);
            }
        }
    }

    public void refreshChat() {
        this.drawnChatLines.clear();
        this.resetScroll();

        for (int i = this.chatLines.size() - 1; i >= 0; --i) {
            ChatLine<ITextComponent> chatline = this.chatLines.get(i);
            this.func_238493_a_(chatline.getLineString(), chatline.getChatLineID(), chatline.getUpdatedCounter(), true);
        }
    }

    public List<String> getSentMessages() {
        return this.sentMessages;
    }

    /**
     * Adds this string to the list of sent messages, for recall using the up/down arrow keys
     */
    public void addToSentMessages(String message) {
        if (this.sentMessages.isEmpty() || !this.sentMessages.get(this.sentMessages.size() - 1).equals(message)) {
            this.sentMessages.add(message);
        }
    }

    /**
     * Resets the chat scroll (executed when the GUI is closed, among others)
     */
    public void resetScroll() {
        this.scrollPos = 0;
        this.isScrolled = false;
    }

    public void addScrollPos(double posInc) {
        this.scrollPos = (int) ((double) this.scrollPos + posInc);
        int i = this.drawnChatLines.size();

        if (this.scrollPos > i - this.getLineCount()) {
            this.scrollPos = i - this.getLineCount();
        }

        if (this.scrollPos <= 0) {
            this.scrollPos = 0;
            this.isScrolled = false;
        }
    }

    public boolean func_238491_a_(double p_238491_1_, double p_238491_3_) {
        if (this.getChatOpen() && !this.mc.gameSettings.hideGUI && !this.isChatHidden() && !this.queuedMessages.isEmpty()) {
            double d0 = p_238491_1_ - 2.0D;
            double d1 = (double) this.mc.getMainWindow().getScaledHeight() - p_238491_3_ - 40.0D;

            if (d0 <= (double) MathHelper.floor((double) this.getChatWidth() / this.getScale()) && d1 < 0.0D && d1 > (double) MathHelper.floor(-9.0D * this.getScale())) {
                this.printChatMessage(this.queuedMessages.remove());
                this.field_238490_l_ = System.currentTimeMillis();
                return true;
            } else {
                return false;
            }
        } else {
            return false;
        }
    }

    @Nullable
    public Style func_238494_b_(double p_238494_1_, double p_238494_3_) {
        if (this.getChatOpen() && !this.mc.gameSettings.hideGUI && !this.isChatHidden()) {
            double d0 = p_238494_1_ - 2.0D;
            double d1 = (double) this.mc.getMainWindow().getScaledHeight() - p_238494_3_ - 40.0D;
            d0 = (double) MathHelper.floor(d0 / this.getScale());
            d1 = (double) MathHelper.floor(d1 / (this.getScale() * (this.mc.gameSettings.chatLineSpacing + 1.0D)));

            if (!(d0 < 0.0D) && !(d1 < 0.0D)) {
                int i = Math.min(this.getLineCount(), this.drawnChatLines.size());

                if (d0 <= (double) MathHelper.floor((double) this.getChatWidth() / this.getScale()) && d1 < (double) (9 * i + i)) {
                    int j = (int) (d1 / 9.0D + (double) this.scrollPos);

                    if (j >= 0 && j < this.drawnChatLines.size()) {
                        ChatLine<IReorderingProcessor> chatline = this.drawnChatLines.get(j);
                        return this.mc.fontRenderer.getCharacterManager().func_243239_a(chatline.getLineString(), (int) d0);
                    }
                }

                return null;
            } else {
                return null;
            }
        } else {
            return null;
        }
    }

    /**
     * Returns true if the chat GUI is open
     */
    private boolean getChatOpen() {
        return this.mc.currentScreen instanceof ChatScreen;
    }

    /**
     * finds and deletes a Chat line by ID
     */
    public void deleteChatLine(int id) {
        this.drawnChatLines.removeIf((p_lambda$deleteChatLine$0_1_) ->
        {
            return p_lambda$deleteChatLine$0_1_.getChatLineID() == id;
        });
        this.chatLines.removeIf((p_lambda$deleteChatLine$1_1_) ->
        {
            return p_lambda$deleteChatLine$1_1_.getChatLineID() == id;
        });
    }

    public int getChatWidth() {
        int i = calculateChatboxWidth(this.mc.gameSettings.chatWidth);
        MainWindow mainwindow = Minecraft.getInstance().getMainWindow();
        int j = (int) ((double) (mainwindow.getFramebufferWidth() - 3) / mainwindow.getGuiScaleFactor());
        return MathHelper.clamp(i, 0, j);
    }

    public int getChatHeight() {
        return calculateChatboxHeight((this.getChatOpen() ? this.mc.gameSettings.chatHeightFocused : this.mc.gameSettings.chatHeightUnfocused) / (this.mc.gameSettings.chatLineSpacing + 1.0D));
    }

    public double getScale() {
        return this.mc.gameSettings.chatScale;
    }

    public static int calculateChatboxWidth(double p_194814_0_) {
        int i = 320;
        int j = 40;
        return MathHelper.floor(p_194814_0_ * 280.0D + 40.0D);
    }

    public static int calculateChatboxHeight(double p_194816_0_) {
        int i = 180;
        int j = 20;
        return MathHelper.floor(p_194816_0_ * 160.0D + 20.0D);
    }

    public int getLineCount() {
        return this.getChatHeight() / 9;
    }

    private long func_238497_j_() {
        return (long) (this.mc.gameSettings.chatDelay * 1000.0D);
    }

    private void clearExpiredChatLines() {
        if (!this.queuedMessages.isEmpty()) {
            long i = System.currentTimeMillis();

            if (i - this.field_238490_l_ >= this.func_238497_j_()) {
                this.printChatMessage(this.queuedMessages.remove());
                this.field_238490_l_ = i;
            }
        }
    }

    public void func_238495_b_(ITextComponent p_238495_1_) {
        if (this.mc.gameSettings.chatDelay <= 0.0D) {
            this.printChatMessage(p_238495_1_);
        } else {
            long i = System.currentTimeMillis();

            if (i - this.field_238490_l_ >= this.func_238497_j_()) {
                this.printChatMessage(p_238495_1_);
                this.field_238490_l_ = i;
            } else {
                this.queuedMessages.add(p_238495_1_);
            }
        }
    }
}
