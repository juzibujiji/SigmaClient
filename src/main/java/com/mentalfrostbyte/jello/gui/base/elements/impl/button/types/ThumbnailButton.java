package com.mentalfrostbyte.jello.gui.base.elements.impl.button.types;

import com.mentalfrostbyte.Client;
import com.mentalfrostbyte.jello.gui.base.animations.Animation;
import com.mentalfrostbyte.jello.gui.combined.CustomGuiScreen;
import com.mentalfrostbyte.jello.gui.impl.jello.buttons.ScrollableContentPanel;
import com.mentalfrostbyte.jello.gui.combined.AnimatedIconPanel;
import com.mentalfrostbyte.jello.util.client.network.youtube.YoutubeVideoData;
import com.mentalfrostbyte.jello.util.client.render.FontSizeAdjust;
import com.mentalfrostbyte.jello.util.client.render.ResourceRegistry;
import com.mentalfrostbyte.jello.util.client.render.Resources;
import com.mentalfrostbyte.jello.util.client.render.SkijaFontRenderer;
import com.mentalfrostbyte.jello.util.client.render.theme.ClientColors;
import com.mentalfrostbyte.jello.util.client.render.theme.ColorHelper;
import com.mentalfrostbyte.jello.util.game.render.RenderUtil;
import com.mentalfrostbyte.jello.util.game.render.RenderUtil2;
import com.mentalfrostbyte.jello.util.system.network.ImageUtil;
import org.newdawn.slick.TrueTypeFont;
import org.newdawn.slick.opengl.Texture;
import com.mentalfrostbyte.jello.util.game.render.SafeTextureUploader;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
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

    // === Bounded LRU cover cache ===
    // Shared across all ThumbnailButtons so scrolling back to a previously-downloaded cover
    // is instant (zero HTTP, zero decode, zero blur). LinkedHashMap in access-order mode
    // reorders on get() which is not thread-safe, so all accesses go through synchronized
    // helpers below. Worst-case heap: COVER_CACHE_MAX * (compatible + blurred) ≈ 22 MB.
    // Eviction simply drops references; buttons currently displaying the evicted image keep
    // their own ref via field20773/blurredImage, so eviction is always safe.
    private static final int COVER_CACHE_MAX = 16;
    private static final java.util.LinkedHashMap<String, CachedCover> COVER_CACHE =
            new java.util.LinkedHashMap<String, CachedCover>(32, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(java.util.Map.Entry<String, CachedCover> eldest) {
                    return size() > COVER_CACHE_MAX;
                }
            };

    private static final class CachedCover {
        final BufferedImage compatible;
        final BufferedImage blurred;     // may be null if blur threw during decode
        final BufferedImage tiny;         // 4x4 preview, pre-scaled on the worker (~64 bytes)
        final int dominantColor;          // average ARGB of the tiny — instant fallback color
        CachedCover(BufferedImage compatible, BufferedImage blurred, BufferedImage tiny, int dominantColor) {
            this.compatible = compatible;
            this.blurred = blurred;
            this.tiny = tiny;
            this.dominantColor = dominantColor;
        }
    }

    private static CachedCover coverCacheGet(String key) {
        synchronized (COVER_CACHE) { return COVER_CACHE.get(key); }
    }

    private static void coverCachePut(String key, CachedCover value) {
        synchronized (COVER_CACHE) { COVER_CACHE.put(key, value); }
    }
    // 快速滚动检测 — state is global because all cards share one scroll viewport.
    private static volatile int lastScrollY = 0;
    private static volatile int scrollDelta = 0;
    private static volatile int scrollStableFrames = 0;
    // Time-gate for global scroll-state updates. Previously each of the ~20 visible cards
    // updated `lastScrollY` in its own draw() call, so cards 2..N in the same frame always
    // saw delta=0 and defeated the fast-scroll guard. We now update at most once per ~12ms.
    private static volatile long lastScrollUpdateNs = 0L;
    private static final long SCROLL_UPDATE_INTERVAL_NS = 12_000_000L; // ~83 fps cap
    private static final int FAST_SCROLL_THRESHOLD = 50;
    private static final int SCROLL_STABLE_FRAMES_REQUIRED = 3;
    // Per-card debounce: only start loading when a card has been continuously in view for
    // this many frames. This prevents fire-and-forget HTTP requests for cards that merely
    // flash past the viewport during fast scrolls.
    private static final int IN_VIEW_STABLE_FRAMES_REQUIRED = 3;

    public static ColorHelper field20771 = new ColorHelper(
            ClientColors.DEEP_TEAL.getColor(),
            ClientColors.DEEP_TEAL.getColor(),
            ClientColors.DEEP_TEAL.getColor(),
            ClientColors.DEEP_TEAL.getColor(),
            FontSizeAdjust.field14488,
            FontSizeAdjust.NEGATE_AND_DIVIDE_BY_2);
    public URL videoUrl = null;
    // ============================================================================
    // CROSS-THREAD FIELDS — written by COVER_LOADER_POOL workers, read by the
    // render thread inside draw(). Marked volatile to give them safe-publication
    // semantics: a volatile write happens-before any subsequent volatile read of
    // the same field, which guarantees that all pixel writes inside the
    // BufferedImage are visible to the render thread *before* the field reference
    // itself becomes non-null.
    //
    // Without volatile, the JMM permits the render thread to observe the
    // reference assignment *before* the pixel-buffer writes that preceded it.
    // BufferedImageUtil.imageToByteBuffer() then reads partial pixel data and
    // hands a corrupt ByteBuffer to glTexImage2D — which on NVIDIA drivers
    // surfaces as EXCEPTION_ACCESS_VIOLATION in nvoglv64.dll during the
    // driver-side pixel-format conversion. The bug is timing-sensitive and was
    // mostly masked previously because uploads happened on the same frame as
    // the publish; the new GL upload throttle widened the window enough to make
    // the race reproduce reliably under fast-scroll bursts.
    // ============================================================================
    public volatile BufferedImage field20773;
    public volatile BufferedImage blurredImage;
    public volatile boolean field20774 = false;
    // Render-thread-only — GL Texture handles must never escape to other threads.
    private Texture field20775;
    private Texture field20776;
    private final Animation animation;
    private boolean activelyHoveredForBlur = false;
    // Per-button visibility debounce counter. Increments each draw() while the card is
    // in view, resets to 0 when the card scrolls out. We only kick off an HTTP load once
    // this reaches IN_VIEW_STABLE_FRAMES_REQUIRED so that cards which flash past the
    // viewport during a scroll burst don't queue wasted downloads.
    private int stableInViewFrames = 0;

    // === Progressive image loading state ===
    // Phase 1 (empty)  : nothing — render artworkPNG fallback
    // Phase 2 (color)  : dominantColor known — render solid colored rect
    // Phase 3 (tiny)   : tinyPreviewTexture uploaded — render 4x4 stretched (GL bilinear blur)
    // Phase 4 (fade)   : sharp texture uploaded — crossfade tiny -> sharp over FADE_DURATION_NS
    // Phase 5 (sharp)  : imageAlpha == 1.0 — sharp only
    //
    // The visual blur in phase 3 comes "for free" from GPU bilinear filtering when stretching
    // a 4x4 texture up to ~250x250 — equivalent to a NanoVG box blur but using the existing
    // Slick GL pipeline already wired into this file (no new dependencies).
    // Cross-thread — written by worker, read by render thread. See the volatile
    // rationale on field20773 above; same JMM concern applies here.
    private volatile BufferedImage tinyPreviewImage; // pre-scaled 4x4 preview from the worker
    private volatile int dominantColor = -1;         // average ARGB of cover; -1 = unknown
    // Render-thread-only — GL handle and animation state, never touched off-thread.
    private Texture tinyPreviewTexture;              // GL-uploaded version (drawn stretched)
    private float imageAlpha = 0f;                   // 0..1 — current sharp-image opacity
    private long fadeStartTime = 0L;                 // ns timestamp when sharp upload completed
    private static final long FADE_DURATION_NS = 200_000_000L; // 200ms crossfade

    // === Shared GL upload throttle ===
    // Burst-uploading textures on the render thread is the dominant remaining stutter source
    // when ~10 cards become visible at once (e.g. scroll-back through cached covers). We cap
    // to MAX_UPLOADS_PER_FRAME texture uploads per ~UPLOAD_FRAME_NS interval across all
    // ThumbnailButtons. Tiny-preview uploads are 64 bytes each so they almost always win the
    // budget; sharp uploads spread across subsequent frames, which is exactly the UX we want.
    private static final int MAX_UPLOADS_PER_FRAME = 2;
    private static volatile int uploadsThisFrame = 0;
    private static volatile int hoverBlurUploadsThisFrame = 0;
    private static volatile long uploadFrameStartNs = 0L;
    private static final long UPLOAD_FRAME_NS = 14_000_000L; // ~71fps frame budget

    private static void resetUploadBudgetIfNeeded(long now) {
        if (now - uploadFrameStartNs > UPLOAD_FRAME_NS) {
            uploadFrameStartNs = now;
            uploadsThisFrame = 0;
            hoverBlurUploadsThisFrame = 0;
        }
    }

    private static boolean tryReserveUpload() {
        long now = System.nanoTime();
        resetUploadBudgetIfNeeded(now);
        if (uploadsThisFrame >= MAX_UPLOADS_PER_FRAME) return false;
        uploadsThisFrame++;
        return true;
    }

    private static boolean tryReserveHoverBlurUpload() {
        long now = System.nanoTime();
        resetUploadBudgetIfNeeded(now);
        if (hoverBlurUploadsThisFrame >= 1 || uploadsThisFrame >= MAX_UPLOADS_PER_FRAME) return false;
        hoverBlurUploadsThisFrame++;
        uploadsThisFrame++;
        return true;
    }

    private static int getUploadsThisFrame() {
        return uploadsThisFrame;
    }

    // === Text-layout cache ===
    // The previous per-frame path did: 2x Pattern.compile + split + 2..N getTextWidth calls
    // (+ linear O(n) truncateToFit → up to ~50 extra getTextWidth per line for CJK titles).
    // Multiplied by ~20 visible cards × 60 fps that was >2000 text shapes per second for
    // content that literally never changes after the card is constructed. We cache the fully
    // parsed + truncated + measured layout here and invalidate only if text or width changes.
    private String layoutCachedText = null;
    private int layoutCachedWidthA = -1;
    private String layoutLine0 = null;   // lower line (artist)
    private String layoutLine1 = null;   // upper line (title); null for single-line items
    private float layoutWidth0 = 0f;     // cached NanoVG width of line0
    private float layoutWidth1 = 0f;     // cached NanoVG width of line1

    // Precompiled — String.replaceAll recompiles each call, which was hot on the render thread.
    private static final java.util.regex.Pattern STRIP_PARENS = java.util.regex.Pattern.compile("\\(.*\\)");
    private static final java.util.regex.Pattern STRIP_BRACKETS = java.util.regex.Pattern.compile("\\[.*\\]");

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
        this.activelyHoveredForBlur = var5;
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
        // === Global scroll-state update (time-gated, once per ~12ms) ===
        // Previously this block ran per-button, so after the first visible card drew,
        // `lastScrollY` matched the current value and cards 2..N all saw delta=0 and
        // incremented `scrollStableFrames`, defeating the fast-scroll guard. We now
        // update at most once per render frame.
        int currentScrollY = 0;
        if (this.getParent() != null && this.getParent().getParent() != null) {
            CustomGuiScreen container = this.getParent().getParent();
            if (container instanceof ScrollableContentPanel scp) {
                currentScrollY = scp.method13513();
            }
        }
        long nowNs = System.nanoTime();
        if (nowNs - lastScrollUpdateNs > SCROLL_UPDATE_INTERVAL_NS) {
            lastScrollUpdateNs = nowNs;
            int delta = Math.abs(currentScrollY - lastScrollY);
            if (delta > 0) {
                scrollDelta = delta;
                scrollStableFrames = 0;
            } else {
                scrollStableFrames++;
                if (scrollStableFrames >= SCROLL_STABLE_FRAMES_REQUIRED) {
                    scrollDelta = 0; // fully decay so next burst starts clean
                }
            }
            lastScrollY = currentScrollY;
        }
        boolean isFastScrolling = scrollDelta > FAST_SCROLL_THRESHOLD
                && scrollStableFrames < SCROLL_STABLE_FRAMES_REQUIRED;

        boolean inView = this.method13157();
        if (!inView) {
            // Scrolled out of viewport: release GPU textures + decoded BufferedImages so we
            // don't leak ~720 KB per card across long playlists. Reset debounce counters.
            if (this.field20775 != null) {
                this.field20775.release();
                this.field20775 = null;
            }

            if (this.field20776 != null) {
                this.field20776.release();
                this.field20776 = null;
            }

            if (this.tinyPreviewTexture != null) {
                try { this.tinyPreviewTexture.release(); } catch (Exception ignored) {}
                this.tinyPreviewTexture = null;
            }

            this.field20773 = null;
            this.blurredImage = null;
            this.tinyPreviewImage = null;
            this.dominantColor = -1;
            this.imageAlpha = 0f;
            this.fadeStartTime = 0L;
            this.field20774 = false;
            this.stableInViewFrames = 0;
        } else {
            // Accumulate continuous in-view frames. Cap to prevent int overflow on very
            // long sessions (at 60 fps it'd take ~1 year to hit MAX_VALUE, but guard anyway).
            if (this.stableInViewFrames < 1_000_000) {
                this.stableInViewFrames++;
            }

            // === Streaming load gate ===
            //  - Card must be in the viewport (method13157()).
            //  - Card must have been in view for IN_VIEW_STABLE_FRAMES_REQUIRED consecutive
            //    frames. This debounces drive-by cards that flash past during fast scrolls.
            //  - Global scroll velocity must be below the fast-scroll threshold.
            //  - We haven't already queued a load for this URL (LOADING_COVERS dedup).
            boolean stableEnough = this.stableInViewFrames >= IN_VIEW_STABLE_FRAMES_REQUIRED;
            if (stableEnough && !this.field20774 && !isFastScrolling) {
                String coverKey = this.videoUrl != null ? this.videoUrl.toString() : null;
                if (coverKey != null) {
                    // Cache hit fast path: zero HTTP, zero decode, zero blur. Scrolling back to
                    // a card we've already downloaded populates instantly from the shared LRU.
                    CachedCover cached = coverCacheGet(coverKey);
                    if (cached != null) {
                        this.field20773 = cached.compatible;
                        this.blurredImage = cached.blurred;
                        this.tinyPreviewImage = cached.tiny;
                        this.dominantColor = cached.dominantColor;
                        this.field20774 = true;
                    } else if (!LOADING_COVERS.contains(coverKey)) {
                        this.field20774 = true;
                        LOADING_COVERS.add(coverKey);

                        COVER_LOADER_POOL.submit(() -> {
                            try {
                                if (this.videoUrl == null) return;
                                // Stale-task check — card may have scrolled out of the viewport
                                // while this task sat in the pool queue. Skip the HTTP request.
                                if (!this.method13157()) return;

                                BufferedImage decoded = ImageIO.read(this.videoUrl);
                                if (decoded == null) return;

                                int w = decoded.getWidth();
                                int h = decoded.getHeight();
                                if (h != w && w > 180 && h > 180) {
                                    if (this.getText().contains("[NCS Release]") && w > 171 && h > 173) {
                                        decoded = decoded.getSubimage(1, 3, 170, 170);
                                    } else if (w > 250 && h > 180) {
                                        decoded = decoded.getSubimage(70, 0, 180, 180);
                                    }
                                }

                                BufferedImage compatible = new BufferedImage(decoded.getWidth(), decoded.getHeight(),
                                        BufferedImage.TYPE_INT_ARGB);
                                java.awt.Graphics2D g = compatible.createGraphics();
                                g.drawImage(decoded, 0, 0, null);
                                g.dispose();

                                BufferedImage blurred = null;
                                try {
                                    blurred = ImageUtil.applyBlur(compatible, 14);
                                } catch (Exception e) {
                                    e.printStackTrace();
                                }

                                // Pre-scale a 4x4 tiny preview off the render thread. 64 bytes
                                // ARGB makes the GL upload cost negligible. When stretched to
                                // ~250x250 by GL bilinear filtering, it produces a soft "blurry
                                // preview" appearance — equivalent to a NanoVG box blur but
                                // using the existing Slick GL pipeline.
                                BufferedImage tiny = new BufferedImage(4, 4, BufferedImage.TYPE_INT_ARGB);
                                java.awt.Graphics2D tg = tiny.createGraphics();
                                tg.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION,
                                        java.awt.RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                                tg.drawImage(compatible, 0, 0, 4, 4, null);
                                tg.dispose();

                                // Average the 4x4 to get a representative dominant color. Each
                                // pixel is already an average of its source quadrant via the
                                // bilinear downscale, so the per-pixel sum gives a stable mean.
                                int rSum = 0, gSum = 0, bSum = 0;
                                for (int yi = 0; yi < 4; yi++) {
                                    for (int xi = 0; xi < 4; xi++) {
                                        int rgb = tiny.getRGB(xi, yi);
                                        rSum += (rgb >> 16) & 0xFF;
                                        gSum += (rgb >> 8) & 0xFF;
                                        bSum += rgb & 0xFF;
                                    }
                                }
                                int dominant = (0xFF << 24)
                                        | ((rSum >>> 4) << 16)
                                        | ((gSum >>> 4) << 8)
                                        | (bSum >>> 4);

                                // Always cache successful downloads. Amortizes HTTP+blur cost
                                // across future re-scrolls and benefits any sibling button that
                                // happens to share the same URL.
                                coverCachePut(coverKey, new CachedCover(compatible, blurred, tiny, dominant));

                                // ===========================================================
                                // WORKER → RENDER THREAD HANDOFF
                                // No GL or NanoVG calls happen in this lambda. The worker only
                                // produces CPU-side BufferedImages plus a 32-bit dominant color
                                // and publishes them through volatile field writes. The render
                                // thread picks the references up in draw() and performs the
                                // actual glTexImage2D via BufferedImageUtil.getTexture().
                                //
                                // Each volatile assignment below establishes a happens-before
                                // edge so the render thread is guaranteed to see fully-written
                                // pixel buffers (not torn / partially-initialized memory).
                                // ===========================================================
                                // Only pin to this button's fields if it's still on-screen. The
                                // cache entry remains available for future cache-hits regardless.
                                if (this.method13157()) {
                                    this.field20773 = compatible;       // volatile publish
                                    this.blurredImage = blurred;        // volatile publish
                                    this.tinyPreviewImage = tiny;       // volatile publish
                                    this.dominantColor = dominant;      // volatile publish
                                }
                            } catch (Exception var5x) {
                                var5x.printStackTrace();
                            } finally {
                                LOADING_COVERS.remove(coverKey);
                            }
                        });
                    }
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
            // === Progressive image render ===
            // Layer 1 (back): dominant color — instant feedback as soon as cache is populated
            // Layer 2       : tiny 4x4 stretched (bilinear-blurred) — alpha = 1 - imageAlpha
            // Layer 3       : sharp full-res — alpha = imageAlpha (fades in over FADE_DURATION_NS)
            // Layer 4 (top) : hover blur — alpha tied to hover animation, independent of crossfade
            // Fallback      : artworkPNG only when literally nothing else is available
            //
            // GL uploads are throttled via tryReserveUpload() — at most MAX_UPLOADS_PER_FRAME
            // textures uploaded across all buttons per UPLOAD_FRAME_NS interval. Tiny previews
            // get scheduled first (cheap payload, big perceived-quality win).

            // ===========================================================
            // RENDER-THREAD-ONLY BLOCK
            // Snapshot every volatile field written by the worker into a local
            // ONCE at the top of the render block. This:
            //   1. Pays the volatile-read fence cost once, not per use.
            //   2. Defends against the JMM hazard where two non-synchronized
            //      reads of the same reference could observe different values
            //      mid-draw. The locals give us a stable snapshot to render
            //      against, and the BufferedImage's pixel data is fully
            //      visible thanks to the volatile happens-before edge.
            // Every GL/NanoVG call below this point runs strictly on the render
            // thread; the worker never touches GL state.
            // ===========================================================
            final BufferedImage localCompatible = this.field20773;
            final BufferedImage localBlurred   = this.blurredImage;
            final BufferedImage localTinyImage = this.tinyPreviewImage;
            final int           localDominant  = this.dominantColor;

            // --- Throttled uploads (render-thread-local snapshots only) ---
            //
            // All three GL uploads below route through SafeTextureUploader rather than
            // raw BufferedImageUtil.getTexture(). The wrapper:
            //   - calls glFinish() inside its own stack frame while the DirectByteBuffer
            //     reference is still strongly held — prevents the Cleaner from freeing
            //     the off-heap memory mid-DMA on NVIDIA drivers (root cause of
            //     EXCEPTION_ACCESS_VIOLATION at nvoglv64+0xb77610).
            //   - emits a Reference.reachabilityFence to block C2 scalar-replacing the
            //     buffer local before the native call returns.
            //   - returns null on failure (no need for try/catch unless we want to log).
            if (localTinyImage != null && this.tinyPreviewTexture == null && tryReserveUpload()) {
                String texName = "thumb_tiny_" + System.identityHashCode(this) + "_" + System.nanoTime();
                this.tinyPreviewTexture = SafeTextureUploader.upload(texName, localTinyImage);
            }

            if (this.field20775 == null && localCompatible != null && tryReserveUpload()) {
                // Unique texture name per upload: Slick's InternalTextureLoader caches by
                // name, so reusing "picture" aliased every card's TextureImpl onto a single
                // GL handle — release() on one card then freed another card's live texture,
                // producing native crashes on draw. Including identityHashCode + nanoTime
                // makes the name unique per button and per upload.
                String texName = "thumb_" + System.identityHashCode(this) + "_" + System.nanoTime();
                this.field20775 = SafeTextureUploader.upload(texName, localCompatible);
                if (this.field20775 != null) {
                    // Start the crossfade timer the moment the sharp texture is GL-resident.
                    this.fadeStartTime = System.nanoTime();
                }
            }

            // Hover blur is decorative: creation is throttled and only the active hover may do it.
            boolean hoverAnimating = var4 > 0.0F;
            boolean activelyHovered = this.activelyHoveredForBlur;

            if (this.field20776 == null && hoverAnimating && activelyHovered && localBlurred != null) {
                String texName = "thumb_blur_" + System.identityHashCode(this) + "_" + System.nanoTime();
                boolean reserved = tryReserveHoverBlurUpload();
                if (reserved) {
                    this.field20776 = SafeTextureUploader.upload(
                            texName, localBlurred, false, true, getUploadsThisFrame());
                } else {
                    SafeTextureUploader.logUploadSkipped(texName, localBlurred, false, getUploadsThisFrame());
                }
            } else if (!hoverAnimating && this.field20776 != null) {
                try { this.field20776.release(); } catch (Exception ignored) {}
                this.field20776 = null;
            }

            // --- Compute crossfade progress ---
            if (this.field20775 != null && this.fadeStartTime > 0L) {
                long elapsedNs = System.nanoTime() - this.fadeStartTime;
                this.imageAlpha = elapsedNs >= FADE_DURATION_NS
                        ? 1f
                        : (float) elapsedNs / (float) FADE_DURATION_NS;
            } else if (this.field20775 == null) {
                this.imageAlpha = 0f;
            }

            // --- Layered render ---
            int baseColor = ClientColors.LIGHT_GREYISH_BLUE.getColor();
            float baseAlpha = partialTicks * (1.0F - var4);

            if (localDominant != -1) {
                int domARGB = (((int) (baseAlpha * 255f) & 0xFF) << 24) | (localDominant & 0xFFFFFF);
                RenderUtil.drawRect2(var5, var6, var7, var8, domARGB);
            }

            if (this.tinyPreviewTexture != null && this.imageAlpha < 1.0f) {
                RenderUtil.drawImage(var5, var6, var7, var8, this.tinyPreviewTexture,
                        RenderUtil2.applyAlpha(baseColor, baseAlpha * (1.0f - this.imageAlpha)));
            }

            if (this.field20775 != null) {
                RenderUtil.drawImage(var5, var6, var7, var8, this.field20775,
                        RenderUtil2.applyAlpha(baseColor, baseAlpha * this.imageAlpha));
            }

            if (this.field20776 != null) {
                RenderUtil.drawImage(var5, var6, var7, var8, this.field20776,
                        RenderUtil2.applyAlpha(baseColor, var4 * partialTicks));
            }

            if (localDominant == -1 && this.tinyPreviewTexture == null && this.field20775 == null) {
                RenderUtil.drawImage(var5, var6, var7, var8, Resources.artworkPNG,
                        RenderUtil2.applyAlpha(baseColor, baseAlpha));
                if (this.field20776 != null) {
                    RenderUtil.drawImage(var5, var6, var7, var8, Resources.artworkPNG,
                            RenderUtil2.applyAlpha(baseColor, var4 * partialTicks));
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

                // Pull the parsed/truncated/measured layout from the per-button cache. This
                // skips ~2 Pattern.compile + ~2..100 NanoVG text shapes per draw() call.
                ensureTextLayoutCached();

                int sw = net.minecraft.client.Minecraft.getInstance().getMainWindow().getFramebufferWidth();
                int sh = net.minecraft.client.Minecraft.getInstance().getMainWindow().getFramebufferHeight();
                if (SkijaFontRenderer.ensureInitialized(sw, sh) && layoutLine0 != null) {
                    float fontSize = 12.0f;
                    // SkijaFontRenderer now draws through RenderUtil.drawImage (respecting the GL
                    // matrix + the parent panel's scissor), so use the same local element
                    // coordinates as the TTF fallback below instead of absolute screen coords.
                    int color = RenderUtil2.applyAlpha(ClientColors.LIGHT_GREYISH_BLUE.getColor(), partialTicks);
                    if (layoutLine1 != null) {
                        SkijaFontRenderer.drawText(layoutLine1,
                                (float) (this.getXA() + (this.getWidthA() - layoutWidth1) / 2),
                                (float) (this.getYA() + this.getWidthA() - 2),
                                fontSize, color);
                        SkijaFontRenderer.drawText(layoutLine0,
                                (float) (this.getXA() + (this.getWidthA() - layoutWidth0) / 2),
                                (float) (this.getYA() + this.getWidthA() - 2 + 13),
                                fontSize, color);
                    } else {
                        SkijaFontRenderer.drawText(layoutLine0,
                                (float) (this.getXA() + (this.getWidthA() - layoutWidth0) / 2),
                                (float) (this.getYA() + this.getWidthA() - 2 + 6),
                                fontSize, color);
                    }
                } else if (layoutLine0 != null) {
                    // TTF fallback: cached lines avoid the regex/split, but TTF widths are
                    // measured fresh (Slick's TrueTypeFont width lookup is cheap relative to
                    // NanoVG text shaping, and this path is rarely hit in practice).
                    int color = RenderUtil2.applyAlpha(ClientColors.LIGHT_GREYISH_BLUE.getColor(), partialTicks);
                    if (layoutLine1 != null) {
                        RenderUtil.drawString(var11,
                                (float) (this.getXA() + (this.getWidthA() - var11.getWidth(layoutLine1)) / 2),
                                (float) (this.getYA() + this.getWidthA() - 2),
                                layoutLine1, color);
                        RenderUtil.drawString(var11,
                                (float) (this.getXA() + (this.getWidthA() - var11.getWidth(layoutLine0)) / 2),
                                (float) (this.getYA() + this.getWidthA() - 2 + 13),
                                layoutLine0, color);
                    } else {
                        RenderUtil.drawString(var11,
                                (float) (this.getXA() + (this.getWidthA() - var11.getWidth(layoutLine0)) / 2),
                                (float) (this.getYA() + this.getWidthA() - 2 + 6),
                                layoutLine0, color);
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
     * 截断文字使其适合指定宽度。Binary search — O(log n) NanoVG measurements instead of
     * the previous O(n) linear scan. For a 50-char title this is ~6 measurements vs ~50.
     */
    private static String truncateToFit(String text, float maxWidth, float fontSize) {
        if (SkijaFontRenderer.getTextWidth(text, fontSize) <= maxWidth) return text;
        // Search the largest prefix length `lo` such that `text[0..lo] + "..."` fits.
        int lo = 0;
        int hi = text.length();
        while (lo < hi) {
            int mid = (lo + hi + 1) >>> 1;
            String candidate = text.substring(0, mid) + "...";
            if (SkijaFontRenderer.getTextWidth(candidate, fontSize) <= maxWidth) {
                lo = mid;
            } else {
                hi = mid - 1;
            }
        }
        return lo > 0 ? text.substring(0, lo) + "..." : "...";
    }

    /**
     * Lazily compute and cache the parsed/truncated/measured text layout for this card.
     * Invalidates only when the source text or card width changes — which in practice means
     * "once per card lifetime" since both are effectively immutable after construction.
     *
     * Eliminates the per-frame regex compilation and NanoVG text-shaping work that was the
     * dominant render-thread cost for visible cards.
     */
    private void ensureTextLayoutCached() {
        String currentText = this.getText();
        int currentWidthA = this.getWidthA();

        if (java.util.Objects.equals(currentText, layoutCachedText) && currentWidthA == layoutCachedWidthA) {
            return; // hot path — cache is fresh
        }

        layoutCachedText = currentText;
        layoutCachedWidthA = currentWidthA;
        layoutLine0 = null;
        layoutLine1 = null;
        layoutWidth0 = 0f;
        layoutWidth1 = 0f;

        if (currentText == null) return;

        // Strip "(...)" and "[...]" annotations using precompiled patterns, then split on " - ".
        String cleaned = STRIP_BRACKETS.matcher(STRIP_PARENS.matcher(currentText).replaceAll("")).replaceAll("");
        String[] parts = cleaned.split(" - ");

        String line0;
        String line1;
        if (parts.length > 1) {
            line1 = parts[1]; // upper line
            line0 = parts[0]; // lower line
        } else {
            line0 = parts.length > 0 ? parts[0] : "";
            line1 = null;
        }

        // Truncation requires Skija measurements. If Skija isn't ready yet (e.g. during
        // very early init), keep the raw lines — they'll be re-laid-out on the next draw
        // because we leave layoutCachedWidthA tracking the same value (cache stays valid).
        // The TTF fallback path doesn't truncate anyway.
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
        if (SkijaFontRenderer.ensureInitialized(
                mc.getMainWindow().getFramebufferWidth(),
                mc.getMainWindow().getFramebufferHeight())) {
            float fontSize = 12.0f;
            float maxWidth = currentWidthA - 10;
            if (line1 != null) {
                float w = SkijaFontRenderer.getTextWidth(line1, fontSize);
                if (w > maxWidth) {
                    line1 = truncateToFit(line1, maxWidth, fontSize);
                    w = SkijaFontRenderer.getTextWidth(line1, fontSize);
                }
                layoutWidth1 = w;
            }
            float w0 = SkijaFontRenderer.getTextWidth(line0, fontSize);
            if (w0 > maxWidth) {
                line0 = truncateToFit(line0, maxWidth, fontSize);
                w0 = SkijaFontRenderer.getTextWidth(line0, fontSize);
            }
            layoutWidth0 = w0;
        }

        layoutLine0 = line0;
        layoutLine1 = line1;
    }
}
