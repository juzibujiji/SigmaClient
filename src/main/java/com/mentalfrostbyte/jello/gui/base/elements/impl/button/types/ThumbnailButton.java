package com.mentalfrostbyte.jello.gui.base.elements.impl.button.types;

import com.mentalfrostbyte.Client;
import com.mentalfrostbyte.jello.gui.base.animations.Animation;
import com.mentalfrostbyte.jello.gui.combined.CustomGuiScreen;
import com.mentalfrostbyte.jello.gui.impl.jello.buttons.ScrollableContentPanel;
import com.mentalfrostbyte.jello.gui.combined.AnimatedIconPanel;
import com.mentalfrostbyte.jello.util.client.network.youtube.YoutubeVideoData;
import com.mentalfrostbyte.jello.util.client.render.FontSizeAdjust;
import com.mentalfrostbyte.jello.util.client.render.NanoVGFontRenderer;
import com.mentalfrostbyte.jello.util.client.render.ResourceRegistry;
import com.mentalfrostbyte.jello.util.client.render.Resources;
import com.mentalfrostbyte.jello.util.client.render.theme.ClientColors;
import com.mentalfrostbyte.jello.util.client.render.theme.ColorHelper;
import com.mentalfrostbyte.jello.util.game.render.RenderUtil;
import com.mentalfrostbyte.jello.util.game.render.RenderUtil2;
import com.mentalfrostbyte.jello.util.system.network.ImageUtil;
import org.newdawn.slick.TrueTypeFont;
import org.newdawn.slick.opengl.Texture;
import org.newdawn.slick.util.BufferedImageUtil;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

public class ThumbnailButton extends AnimatedIconPanel {
    // 共享线程池：限制同时下载封面图的线程数，避免线程爆炸
    private static final ExecutorService COVER_LOADER_POOL = Executors.newFixedThreadPool(4, r -> {
        Thread t = new Thread(r, "CoverLoader");
        t.setDaemon(true);
        return t;
    });
    // 正在加载中的封面URL集合，避免重复提交
    private static final Set<String> LOADING_COVERS = ConcurrentHashMap.newKeySet();
    // 快速滚动检测
    private static volatile int lastScrollY = 0;
    private static volatile int scrollDelta = 0;
    private static volatile int scrollStableFrames = 0;
    private static final int FAST_SCROLL_THRESHOLD = 50;
    private static final int SCROLL_STABLE_FRAMES_REQUIRED = 3;

    public static ColorHelper field20771 = new ColorHelper(
            ClientColors.DEEP_TEAL.getColor(),
            ClientColors.DEEP_TEAL.getColor(),
            ClientColors.DEEP_TEAL.getColor(),
            ClientColors.DEEP_TEAL.getColor(),
            FontSizeAdjust.field14488,
            FontSizeAdjust.NEGATE_AND_DIVIDE_BY_2);
    public URL videoUrl = null;
    public BufferedImage field20773;
    public BufferedImage blurredImage;
    public boolean field20774 = false;
    private Texture field20775;
    private Texture field20776;
    private final Animation animation;

    @Override
    protected void finalize() throws Throwable {
        try {
            if (this.field20775 != null) {
                Client.getInstance().addTexture(this.field20775);
            }

            if (this.field20776 != null) {
                Client.getInstance().addTexture(this.field20776);
            }
        } finally {
            super.finalize();
        }
    }

    public ThumbnailButton(CustomGuiScreen var1, int x, int y, int width, int height, YoutubeVideoData video) {
        super(var1, video.videoId, x, y, width, height, field20771, video.title, false);
        URL videoUrl = null;

        try {
            if (video.fullUrl != null) {
                String normalized = video.fullUrl.trim();
                if (normalized.startsWith("//")) {
                    normalized = "https:" + normalized;
                }
                if (!normalized.isEmpty()) {
                    videoUrl = new URL(normalized);
                }
            }
        } catch (MalformedURLException excep) {
            excep.printStackTrace();
        }

        this.videoUrl = videoUrl;
        this.animation = new Animation(125, 125);
    }

    @Override
    public void updatePanelDimensions(int newHeight, int newWidth) {
        boolean var5 = this.method13298() && this.getParent().getParent().method13114(newHeight, newWidth);
        this.animation.changeDirection(!var5 ? Animation.Direction.BACKWARDS : Animation.Direction.FORWARDS);

        super.updatePanelDimensions(newHeight, newWidth);
    }

    public boolean method13157() {
        if (this.getParent() != null && this.getParent().getParent() != null) {
            CustomGuiScreen var3 = this.getParent().getParent();
            if (var3 instanceof ScrollableContentPanel var4) {
                int var5 = var4.method13513() + var4.getHeightA() + this.getHeightA();
                int var6 = var4.method13513() - this.getHeightA();
                return this.getYA() <= var5 && this.getYA() >= var6;
            }
        }

        return true;
    }

    @Override
    public void draw(float partialTicks) {
        // 更新快速滚动检测状态
        int currentScrollY = 0;
        if (this.getParent() != null && this.getParent().getParent() != null) {
            CustomGuiScreen container = this.getParent().getParent();
            if (container instanceof ScrollableContentPanel scp) {
                currentScrollY = scp.method13513();
            }
        }
        int delta = Math.abs(currentScrollY - lastScrollY);
        if (delta > 0) {
            scrollDelta = delta;
            scrollStableFrames = 0;
        } else {
            scrollStableFrames++;
        }
        lastScrollY = currentScrollY;
        boolean isFastScrolling = scrollDelta > FAST_SCROLL_THRESHOLD && scrollStableFrames < SCROLL_STABLE_FRAMES_REQUIRED;

        if (!this.method13157()) {
            if (this.field20775 != null) {
                this.field20775.release();
                this.field20775 = null;
            }

            if (this.field20776 != null) {
                this.field20776.release();
                this.field20776 = null;
            }
        } else {
            // 封面懒加载：使用线程池，快速滚动时跳过加载
            if (this.method13157() && !this.field20774 && !isFastScrolling) {
                String coverKey = this.videoUrl != null ? this.videoUrl.toString() : null;
                if (coverKey != null && !LOADING_COVERS.contains(coverKey)) {
                    this.field20774 = true;
                    LOADING_COVERS.add(coverKey);

                    COVER_LOADER_POOL.submit(() -> {
                        try {
                            if (this.videoUrl == null)
                                return;

                            BufferedImage var3 = ImageIO.read(this.videoUrl);
                            if (var3 != null) {
                                int w = var3.getWidth();
                                int h = var3.getHeight();

                                if (h != w && w > 180 && h > 180) {
                                    if (this.getText().contains("[NCS Release]") && w > 171 && h > 173) {
                                        var3 = var3.getSubimage(1, 3, 170, 170);
                                    } else if (w > 250 && h > 180) {
                                        var3 = var3.getSubimage(70, 0, 180, 180);
                                    }
                                }

                                BufferedImage compatible = new BufferedImage(var3.getWidth(), var3.getHeight(),
                                        BufferedImage.TYPE_INT_ARGB);
                                java.awt.Graphics2D g = compatible.createGraphics();
                                g.drawImage(var3, 0, 0, null);
                                g.dispose();
                                this.field20773 = compatible;

                                try {
                                    this.blurredImage = ImageUtil.applyBlur(compatible, 14);
                                } catch (Exception e) {
                                    e.printStackTrace();
                                }
                            }
                        } catch (Exception var5x) {
                            var5x.printStackTrace();
                        } finally {
                            LOADING_COVERS.remove(coverKey);
                        }
                    });
                }
            }

            float var4 = this.animation.calcPercent();
            float var5 = (float) Math.round((float) (this.getXA() + 15) - 5.0F * var4);
            float var6 = (float) Math.round((float) (this.getYA() + 15) - 5.0F * var4);
            float var7 = (float) Math.round((float) (this.getWidthA() - 30) + 10.0F * var4);
            float var8 = (float) Math.round((float) (this.getWidthA() - 30) + 10.0F * var4);
            RenderUtil.drawRoundedRect(
                    (float) (this.getXA() + 15) - 5.0F * var4,
                    (float) (this.getYA() + 15) - 5.0F * var4,
                    (float) (this.getWidthA() - 30) + 10.0F * var4,
                    (float) (this.getWidthA() - 30) + 10.0F * var4,
                    20.0F,
                    partialTicks);
            if (this.field20775 == null && this.field20773 == null) {
                RenderUtil.drawImage(var5, var6, var7, var8, Resources.artworkPNG, RenderUtil2
                        .applyAlpha(ClientColors.LIGHT_GREYISH_BLUE.getColor(), partialTicks * (1.0F - var4)));
                if (this.field20776 != null) {
                    RenderUtil.drawImage(var5, var6, var7, var8, Resources.artworkPNG,
                            RenderUtil2.applyAlpha(ClientColors.LIGHT_GREYISH_BLUE.getColor(), var4 * partialTicks));
                }
            } else {
                if (this.field20775 == null) {
                    try {
                        if (this.field20775 != null) {
                            this.field20775.release();
                        }

                        this.field20775 = BufferedImageUtil.getTexture("picture", this.field20773);
                    } catch (IOException var14) {
                        var14.printStackTrace();
                    }
                }

                if (this.field20776 == null && var4 > 0.0F && this.blurredImage != null) {
                    try {
                        if (this.field20776 != null) {
                            this.field20776.release();
                        }

                        this.field20776 = BufferedImageUtil.getTexture("picture", this.blurredImage);
                    } catch (IOException var13) {
                        var13.printStackTrace();
                    }
                } else if (var4 == 0.0F && this.field20776 != null) {
                    this.field20776 = null;
                }

                RenderUtil.drawImage(var5, var6, var7, var8, this.field20775, RenderUtil2
                        .applyAlpha(ClientColors.LIGHT_GREYISH_BLUE.getColor(), partialTicks * (1.0F - var4)));
                if (this.field20776 != null) {
                    RenderUtil.drawImage(var5, var6, var7, var8, this.field20776,
                            RenderUtil2.applyAlpha(ClientColors.LIGHT_GREYISH_BLUE.getColor(), var4 * partialTicks));
                }
            }

            float var9 = 50;
            if (this.method13212()) {
                var9 = 40;
            }

            float var10 = 0.5F + var4 / 2.0F;
            RenderUtil.drawImage(
                    (float) (this.getXA() + this.getWidthA() / 2) - (var9 / 2) * var10,
                    (float) (this.getYA() + this.getWidthA() / 2) - (var9 / 2) * var10,
                    var9 * var10,
                    var9 * var10,
                    Resources.playIconPNG,
                    RenderUtil2.applyAlpha(ClientColors.LIGHT_GREYISH_BLUE.getColor(), var4 * partialTicks));
            TrueTypeFont var11 = ResourceRegistry.JelloLightFont12;
            if (this.text != null) {
                RenderUtil.method11415(this);
                String[] var12 = this.getText().replaceAll("\\(.*\\)", "").replaceAll("\\[.*\\]", "").split(" - ");

                // 使用NanoVG渲染歌名文字（支持中文/CJK字符）
                if (NanoVGFontRenderer.isInitialized()) {
                    int sw = net.minecraft.client.Minecraft.getInstance().getMainWindow().getFramebufferWidth();
                    int sh = net.minecraft.client.Minecraft.getInstance().getMainWindow().getFramebufferHeight();
                    float fontSize = 12.0f;
                    // 计算绝对坐标（NanoVG忽略GL矩阵）
                    int absXBase = getAbsoluteScreenX();
                    int absYBase = getAbsoluteScreenY();

                    // 计算父级ScrollableContentPanel的裁剪区域（NanoVG自身的scissor）
                    float clipX = 0, clipY = 0, clipW = sw, clipH = sh;
                    if (this.getParent() != null && this.getParent().getParent() != null) {
                        CustomGuiScreen container = this.getParent().getParent();
                        if (container instanceof ScrollableContentPanel scp) {
                            // 计算ScrollableContentPanel的绝对屏幕坐标
                            int scpAbsX = scp.getXA();
                            int scpAbsY = scp.getYA();
                            CustomGuiScreen p = scp.getParent();
                            while (p != null) {
                                scpAbsX += p.getXA();
                                scpAbsY += p.getYA();
                                p = p.getParent();
                            }
                            clipX = scpAbsX;
                            clipY = scpAbsY;
                            clipW = scp.getWidthA();
                            clipH = scp.getHeightA();
                        }
                    }

                    NanoVGFontRenderer.beginFrame(sw, sh);
                    // 设置NanoVG裁剪区域，防止文字溢出列表容器
                    NanoVGFontRenderer.setScissor(clipX, clipY, clipW, clipH);
                    if (var12.length > 1) {
                        float tw1 = NanoVGFontRenderer.getTextWidth(var12[1], fontSize);
                        float tw0 = NanoVGFontRenderer.getTextWidth(var12[0], fontSize);
                        // 限制文字宽度不超过按钮宽度
                        String text1 = var12[1];
                        String text0 = var12[0];
                        if (tw1 > this.getWidthA() - 10) {
                            text1 = truncateToFit(text1, this.getWidthA() - 10, fontSize);
                            tw1 = NanoVGFontRenderer.getTextWidth(text1, fontSize);
                        }
                        if (tw0 > this.getWidthA() - 10) {
                            text0 = truncateToFit(text0, this.getWidthA() - 10, fontSize);
                            tw0 = NanoVGFontRenderer.getTextWidth(text0, fontSize);
                        }
                        NanoVGFontRenderer.drawText(text1,
                                absXBase + (this.getWidthA() - tw1) / 2,
                                (float) (absYBase + this.getWidthA() - 2),
                                fontSize,
                                RenderUtil2.applyAlpha(ClientColors.LIGHT_GREYISH_BLUE.getColor(), partialTicks));
                        NanoVGFontRenderer.drawText(text0,
                                absXBase + (this.getWidthA() - tw0) / 2,
                                (float) (absYBase + this.getWidthA() - 2 + 13),
                                fontSize,
                                RenderUtil2.applyAlpha(ClientColors.LIGHT_GREYISH_BLUE.getColor(), partialTicks));
                    } else {
                        float tw = NanoVGFontRenderer.getTextWidth(var12[0], fontSize);
                        String text0 = var12[0];
                        if (tw > this.getWidthA() - 10) {
                            text0 = truncateToFit(text0, this.getWidthA() - 10, fontSize);
                            tw = NanoVGFontRenderer.getTextWidth(text0, fontSize);
                        }
                        NanoVGFontRenderer.drawText(text0,
                                absXBase + (this.getWidthA() - tw) / 2,
                                (float) (absYBase + this.getWidthA() - 2 + 6),
                                fontSize,
                                RenderUtil2.applyAlpha(ClientColors.LIGHT_GREYISH_BLUE.getColor(), partialTicks));
                    }
                    NanoVGFontRenderer.endFrame();
                } else {
                    // 回退到原始字体
                    if (var12.length > 1) {
                        RenderUtil.drawString(
                                var11,
                                (float) (this.getXA() + (this.getWidthA() - var11.getWidth(var12[1])) / 2),
                                (float) (this.getYA() + this.getWidthA() - 2),
                                var12[1],
                                RenderUtil2.applyAlpha(ClientColors.LIGHT_GREYISH_BLUE.getColor(), partialTicks));
                        RenderUtil.drawString(
                                var11,
                                (float) (this.getXA() + (this.getWidthA() - var11.getWidth(var12[0])) / 2),
                                (float) (this.getYA() + this.getWidthA() - 2 + 13),
                                var12[0],
                                RenderUtil2.applyAlpha(ClientColors.LIGHT_GREYISH_BLUE.getColor(), partialTicks));
                    } else {
                        RenderUtil.drawString(
                                var11,
                                (float) (this.getXA() + (this.getWidthA() - var11.getWidth(var12[0])) / 2),
                                (float) (this.getYA() + this.getWidthA() - 2 + 6),
                                var12[0],
                                RenderUtil2.applyAlpha(ClientColors.LIGHT_GREYISH_BLUE.getColor(), partialTicks));
                    }
                }

                RenderUtil.restoreScissor();
            }
        }
    }

    /**
     * 计算此组件在屏幕上的绝对X坐标（沿父级链累加）
     */
    private int getAbsoluteScreenX() {
        int x = this.getXA();
        CustomGuiScreen p = this.getParent();
        while (p != null) {
            x += p.getXA();
            p = p.getParent();
        }
        return x;
    }

    /**
     * 计算此组件在屏幕上的绝对Y坐标（沿父级链累加）
     */
    private int getAbsoluteScreenY() {
        int y = this.getYA();
        CustomGuiScreen p = this.getParent();
        while (p != null) {
            y += p.getYA();
            p = p.getParent();
        }
        return y;
    }

    /**
     * 截断文字使其适合指定宽度
     */
    private static String truncateToFit(String text, float maxWidth, float fontSize) {
        if (NanoVGFontRenderer.getTextWidth(text, fontSize) <= maxWidth) return text;
        for (int i = text.length() - 1; i > 0; i--) {
            String truncated = text.substring(0, i) + "...";
            if (NanoVGFontRenderer.getTextWidth(truncated, fontSize) <= maxWidth) {
                return truncated;
            }
        }
        return "...";
    }
}
