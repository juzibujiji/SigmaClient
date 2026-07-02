package com.mentalfrostbyte.jello.util.game.render;

import org.lwjgl.opengl.GL11;
import org.lwjgl.system.MemoryUtil;
import org.newdawn.slick.opengl.InternalTextureLoader;
import org.newdawn.slick.opengl.Texture;
import org.newdawn.slick.opengl.TextureImpl;

import java.awt.image.BufferedImage;

/**
 * Crash-safe replacement for {@link org.newdawn.slick.util.BufferedImageUtil#getTexture}
 * that eliminates the recurring NVIDIA-driver access violation seen during
 * {@code glTexImage2D} / {@code glTexSubImage2D}.
 *
 * <h3>Why earlier fixes failed</h3>
 *
 * <p>We tried, in order:
 * <ol>
 *   <li>Slick's path: a {@code DirectByteBuffer} +
 *       {@code GL_RGBA + GL_UNSIGNED_BYTE}. Crashed intermittently inside
 *       {@code nvoglv64+0xb77610} (driver pixel-format converter).</li>
 *   <li>Replaced the {@code DirectByteBuffer} with a
 *       {@code MemoryUtil.memCalloc} {@code ByteBuffer} (no {@code Cleaner}
 *       behind it, no GC race). Same crash address.</li>
 *   <li>Switched format to NVIDIA's native
 *       {@code GL_BGRA + GL_UNSIGNED_INT_8_8_8_8_REV} and split the upload
 *       into {@code glTexImage2D(NULL) + glTexSubImage2D}. Crash signature
 *       moved out of the converter but still reproduced 100%.</li>
 *   <li>Added 4&nbsp;KB tail padding to defend against driver SIMD overread.
 *       Still crashed, this time with the fault address inside the buffer
 *       (mid-allocation page boundary).</li>
 * </ol>
 *
 * <p>Common factor across all four crashes: we always handed the driver a
 * pointer extracted from a Java {@link java.nio.ByteBuffer}. Minecraft's own
 * {@link net.minecraft.client.renderer.texture.NativeImage} never does this.
 * It allocates with {@link MemoryUtil#nmemCalloc(long, long)} (raw
 * {@code long}), writes pixels with {@link MemoryUtil#memPutInt(long, int)},
 * and uploads via the {@code long}-pointer overload of
 * {@code glTexSubImage2D}. The pixel memory never enters the JVM's
 * {@code DirectBuffer} machinery at all. Minecraft uploads thousands of
 * textures per session through that path on the same driver, never crashes.
 *
 * <h3>What this implementation does</h3>
 *
 * <p>Same path as {@code NativeImage}:
 * <ul>
 *   <li>{@link MemoryUtil#nmemCalloc(long, long)} for a raw native pointer
 *       (no Cleaner, no Buffer wrapper, no GC interaction).</li>
 *   <li>{@link MemoryUtil#memPutInt(long, int)} per-pixel into the raw
 *       memory, packing as {@code ABGR} so bytes lay out in memory as
 *       {@code [R, G, B, A]} (what {@code GL_RGBA + GL_UNSIGNED_BYTE}
 *       expects on a little-endian host).</li>
 *   <li>{@code glTexImage2D(..., 0L)} to allocate storage.</li>
 *   <li>{@code glTexSubImage2D(..., ptr)} &mdash; the {@code long}-pointer
 *       overload &mdash; to push pixels.</li>
 *   <li>{@code glFinish()} so the driver has definitely consumed the
 *       memory, then {@link MemoryUtil#nmemFree(long)} to release it.</li>
 * </ul>
 *
 * <p>POT padding regions outside the original image bounds are left as zero
 * (calloc'd), matching Slick's legacy layout. A 4&nbsp;KB tail pad is kept
 * as belt-and-suspenders against driver SIMD overread on tiny textures.
 *
 * <h3>Threading</h3>
 *
 * <p>Render-thread only: makes direct GL calls and must be invoked from the
 * thread that owns the GL context.
 */
public final class SafeTextureUploader {
    private static final boolean DEBUG_THUMBNAIL_UPLOAD =
            Boolean.getBoolean("jello.debugThumbnailUpload");

    private SafeTextureUploader() {}

    /** Convenience overload using {@code GL_LINEAR} filters. */
    public static Texture upload(String name, BufferedImage image) {
        return upload(name, image, GL11.GL_LINEAR, GL11.GL_LINEAR);
    }

    /** Convenience overload that lets low-priority callers avoid a full pipeline finish. */
    public static Texture upload(String name, BufferedImage image, boolean forceFinish) {
        return upload(name, image, GL11.GL_LINEAR, GL11.GL_LINEAR, forceFinish);
    }

    /** Same as {@link #upload(String, BufferedImage, boolean)} with debug context. */
    public static Texture upload(String name, BufferedImage image, boolean forceFinish,
                                 Boolean uploadReserved, int frameUploadCount) {
        return upload(name, image, GL11.GL_LINEAR, GL11.GL_LINEAR,
                forceFinish, uploadReserved, frameUploadCount);
    }

    /**
     * Upload {@code image} as a 2D texture and return a Slick {@link Texture}
     * wrapper. Returns {@code null} if {@code image} is {@code null} or if any
     * failure occurs (failure prints a stack trace; it does not propagate).
     */
    public static Texture upload(String name, BufferedImage image, int minFilter, int magFilter) {
        return upload(name, image, minFilter, magFilter, true);
    }

    public static Texture upload(String name, BufferedImage image, int minFilter, int magFilter,
                                 boolean forceFinish) {
        return upload(name, image, minFilter, magFilter, forceFinish, null, -1);
    }

    public static Texture upload(String name, BufferedImage image, int minFilter, int magFilter,
                                 boolean forceFinish, Boolean uploadReserved, int frameUploadCount) {
        long startedNs = shouldLogUpload(name) ? System.nanoTime() : 0L;
        boolean success = false;
        if (image == null) return null;

        final int origW = image.getWidth();
        final int origH = image.getHeight();
        if (origW <= 0 || origH <= 0) return null;

        // Compute power-of-two padded dimensions (GL 1.x compatibility,
        // matches the layout that Slick's ImageIOImageData would produce).
        int texW = 2;
        while (texW < origW) texW *= 2;
        int texH = 2;
        while (texH < origH) texH *= 2;

        final long byteCount = (long) origW * origH * 4L;

        // 4 KB tail pad. Belt-and-suspenders defence against driver SIMD
        // overread on tiny textures (e.g. 4x4 = 64 bytes). nmemCalloc
        // zero-fills, so even if the driver speculatively reads into the
        // padding it reads zeros, never an AV.
        final long TAIL_PAD = 4096L;
        final long allocBytes = byteCount + TAIL_PAD;

        // ---- Allocate raw native memory (NO ByteBuffer wrapper) ----
        //
        // This is the same allocation primitive Minecraft's NativeImage uses
        // (`new NativeImage(w, h, true)` calls nmemCalloc(1, size) and stores
        // the long pointer). The pixel memory never enters Java's
        // DirectBuffer machinery, never has a Cleaner attached, and —
        // critically — we hand the driver a raw long pointer rather than a
        // ByteBuffer-derived address. Empirically the latter is the path
        // that triggers the recurring nvoglv64 AV.
        final long ptr = MemoryUtil.nmemCalloc(1L, allocBytes);
        if (ptr == 0L) return null;

        int textureID = 0;
        try {
            // --- Pack pixels directly into the native allocation ---
            //
            // Fast path: if the BufferedImage has a DataBufferInt backing
            // (TYPE_INT_ARGB / TYPE_INT_RGB), read the int[] directly
            // without going through getRGB()'s color-model conversion.
            // Slow path: fall back to getRGB() for other image types.
            int[] pixels;
            boolean directIntBuffer = false;
            if (image.getType() == BufferedImage.TYPE_INT_ARGB
                    || image.getType() == BufferedImage.TYPE_INT_RGB) {
                int[] buf = ((java.awt.image.DataBufferInt) image.getRaster().getDataBuffer()).getData();
                // If the raster has no offset/banding, use the buffer directly
                if (buf.length == origW * origH) {
                    pixels = buf;
                    directIntBuffer = true;
                } else {
                    pixels = new int[origW * origH];
                    image.getRGB(0, 0, origW, origH, pixels, 0, origW);
                }
            } else {
                pixels = new int[origW * origH];
                image.getRGB(0, 0, origW, origH, pixels, 0, origW);
            }

            if (directIntBuffer) {
                // Direct path: bulk copy with no per-pixel method call overhead
                for (int i = 0; i < pixels.length; i++) {
                    final int argb = pixels[i];
                    final int a = (argb >>> 24) & 0xFF;
                    final int r = (argb >>> 16) & 0xFF;
                    final int g = (argb >>>  8) & 0xFF;
                    final int b = (argb       ) & 0xFF;
                    final int abgr = (a << 24) | (b << 16) | (g << 8) | r;
                    MemoryUtil.memPutInt(ptr + ((long) i) * 4L, abgr);
                }
            } else {
                for (int y = 0; y < origH; y++) {
                    final long rowBase = ptr + ((long) y * origW) * 4L;
                    final int srcRow = y * origW;
                    for (int x = 0; x < origW; x++) {
                        final int argb = pixels[srcRow + x];
                        final int a = (argb >>> 24) & 0xFF;
                        final int r = (argb >>> 16) & 0xFF;
                        final int g = (argb >>>  8) & 0xFF;
                        final int b = (argb       ) & 0xFF;
                        final int abgr = (a << 24) | (b << 16) | (g << 8) | r;
                        MemoryUtil.memPutInt(rowBase + ((long) x) * 4L, abgr);
                    }
                }
            }

            // --- Allocate + configure the GL texture ---
            textureID = InternalTextureLoader.createTextureID();
            final TextureImpl texture = new TextureImpl(name, GL11.GL_TEXTURE_2D, textureID);
            texture.setWidth(origW);
            texture.setHeight(origH);
            texture.setTextureWidth(texW);
            texture.setTextureHeight(texH);
            texture.setAlpha(true);

            GL11.glEnable(GL11.GL_TEXTURE_2D);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, textureID);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, minFilter);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, magFilter);
            // 33071 = GL_CLAMP_TO_EDGE (GL12). We deliberately do NOT use
            // GL11.GL_CLAMP (10496) here: it was deprecated in OpenGL 3.0
            // and some NVIDIA drivers route textures parameterised with it
            // through fallback layout / conversion paths that have been
            // observed to interact badly with our uploads. Minecraft uses
            // GL_CLAMP_TO_EDGE everywhere via NativeImage.setWrapST(true).
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, 33071);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, 33071);

            final int prevUnpackSwapBytes = GL11.glGetInteger(GL11.GL_UNPACK_SWAP_BYTES);
            final int prevUnpackLsbFirst = GL11.glGetInteger(GL11.GL_UNPACK_LSB_FIRST);
            final int prevUnpackRowLength = GL11.glGetInteger(GL11.GL_UNPACK_ROW_LENGTH);
            final int prevUnpackSkipRows = GL11.glGetInteger(GL11.GL_UNPACK_SKIP_ROWS);
            final int prevUnpackSkipPixels = GL11.glGetInteger(GL11.GL_UNPACK_SKIP_PIXELS);
            final int prevUnpackAlign = GL11.glGetInteger(GL11.GL_UNPACK_ALIGNMENT);
            GL11.glPixelStorei(GL11.GL_UNPACK_SWAP_BYTES, 0);
            GL11.glPixelStorei(GL11.GL_UNPACK_LSB_FIRST, 0);
            GL11.glPixelStorei(GL11.GL_UNPACK_ROW_LENGTH, 0);
            GL11.glPixelStorei(GL11.GL_UNPACK_SKIP_ROWS, 0);
            GL11.glPixelStorei(GL11.GL_UNPACK_SKIP_PIXELS, 0);
            GL11.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT, 4);
            try {
                // Stage 1 — allocate storage with NULL pixel data. Use the
                // (IntBuffer) null overload, the same one GlStateManager uses
                // when bootstrapping textures (`@Nullable IntBuffer pixels`
                // at GlStateManager:1391). This goes through the driver's
                // storage-allocation path only; no pixel transfer happens.
                //
                // CRITICAL: internal format is GL_RGBA (generic), NOT
                // GL_RGBA8. GL_RGBA8 forces the driver to store as 8-bit
                // RGBA, but NVIDIA hardware's native format is BGRA8, so
                // every upload then requires a driver-internal RGBA→BGRA
                // SIMD conversion. THAT conversion is the function at
                // nvoglv64.dll+0xb77610 that has been crashing in every
                // hs_err so far. Using generic GL_RGBA lets the driver pick
                // BGRA8 as the actual storage layout and the converter
                // never runs. (See TextureUtil:80 — minecraft uses generic
                // GL_RGBA = 6408 here for exactly the same reason.)
                GL11.glTexImage2D(
                        GL11.GL_TEXTURE_2D,
                        0,
                        GL11.GL_RGBA,
                        texW, texH,
                        0,
                        GL11.GL_RGBA,
                        GL11.GL_UNSIGNED_BYTE,
                        (java.nio.IntBuffer) null);

                // Stage 2 — upload pixel data via the LONG-POINTER overload.
                // This is the exact same call NativeImage makes:
                //   GlStateManager.texSubImage2D(..., this.imagePointer)
                // The driver receives the pointer directly, with no Java
                // ByteBuffer wrapping it, no LWJGL Buffer pre-validation
                // pulling sun.nio internals. This is the only upload path
                // that is empirically stable on this NVIDIA driver build.
                GL11.glTexSubImage2D(
                        GL11.GL_TEXTURE_2D,
                        0,
                        0, 0,
                        origW, origH,
                        GL11.GL_RGBA,
                        GL11.GL_UNSIGNED_BYTE,
                        ptr);

                if (forceFinish) {
                    // Block until the GL pipeline has fully consumed `ptr`.
                    // After this returns, freeing the memory cannot race with
                    // any in-flight DMA.
                    GL11.glFinish();
                } else {
                    // Low-priority decorative uploads can choose a lighter
                    // barrier. Existing callers keep forceFinish=true.
                    GL11.glFlush();
                }
            } finally {
                GL11.glPixelStorei(GL11.GL_UNPACK_SWAP_BYTES, prevUnpackSwapBytes);
                GL11.glPixelStorei(GL11.GL_UNPACK_LSB_FIRST, prevUnpackLsbFirst);
                GL11.glPixelStorei(GL11.GL_UNPACK_ROW_LENGTH, prevUnpackRowLength);
                GL11.glPixelStorei(GL11.GL_UNPACK_SKIP_ROWS, prevUnpackSkipRows);
                GL11.glPixelStorei(GL11.GL_UNPACK_SKIP_PIXELS, prevUnpackSkipPixels);
                GL11.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT, prevUnpackAlign);
            }

            success = true;
            return texture;
        } catch (Throwable t) {
            // Catch Throwable: native driver paths may surface as Error / RuntimeException.
            t.printStackTrace();
            if (textureID != 0) {
                try { GL11.glDeleteTextures(textureID); } catch (Throwable ignored) {}
            }
            return null;
        } finally {
            // Manual free. Safe here because:
            //   - On normal callers: glFinish above already drained the pipeline.
            //   - On forceFinish=false callers: OpenGL client-memory uploads must consume
            //     the source pointer before the call returns; glFlush just nudges the queue.
            //   - On exception: no further GL command will reference `ptr`
            //     (the texture has been deleted or never bound).
            MemoryUtil.nmemFree(ptr);
            logUpload(name, origW, origH, startedNs, forceFinish, uploadReserved,
                    frameUploadCount, success, false);
        }
    }

    public static void logUploadSkipped(String name, BufferedImage image,
                                        Boolean uploadReserved, int frameUploadCount) {
        if (!shouldLogUpload(name)) {
            return;
        }

        int width = image == null ? 0 : image.getWidth();
        int height = image == null ? 0 : image.getHeight();
        logUpload(name, width, height, 0L, false, uploadReserved,
                frameUploadCount, false, true);
    }

    private static boolean shouldLogUpload(String name) {
        if (!DEBUG_THUMBNAIL_UPLOAD || name == null) {
            return false;
        }

        return name.startsWith("thumb_")
                || name.startsWith("music_bg")
                || name.startsWith("music_cover");
    }

    private static void logUpload(String name, int width, int height, long startedNs,
                                  boolean forceFinish, Boolean uploadReserved,
                                  int frameUploadCount, boolean success, boolean skipped) {
        if (!shouldLogUpload(name)) {
            return;
        }

        double elapsedMs = startedNs == 0L ? 0.0 : (System.nanoTime() - startedNs) / 1_000_000.0;
        System.out.printf(
                "[MusicThumbnailUpload] tex=%s size=%dx%d thumbBlur=%s ms=%.3f thread=%s reserved=%s frameUploads=%d finish=%s success=%s skipped=%s%n",
                name,
                width,
                height,
                name.startsWith("thumb_blur_"),
                elapsedMs,
                Thread.currentThread().getName(),
                uploadReserved == null ? "n/a" : uploadReserved.toString(),
                frameUploadCount,
                forceFinish,
                success,
                skipped);
    }
}
