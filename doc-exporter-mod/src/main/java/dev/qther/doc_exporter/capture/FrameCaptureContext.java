package dev.qther.doc_exporter.capture;

import com.mojang.blaze3d.platform.NativeImage;

import java.awt.image.BufferedImage;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Thread-local helper that records frame uploads while active.
 */
public final class FrameCaptureContext implements AutoCloseable {
    private static final ThreadLocal<FrameCaptureContext> ACTIVE = new ThreadLocal<>();

    private final ConcurrentLinkedQueue<BufferedImage> capturedFrames = new ConcurrentLinkedQueue<>();

    private FrameCaptureContext() {
    }

    public static FrameCaptureContext activate() {
        FrameCaptureContext ctx = new FrameCaptureContext();
        ACTIVE.set(ctx);
        return ctx;
    }

    public static void onUpload(NativeImage image, int frameX, int frameY, int width, int height) {
        FrameCaptureContext ctx = ACTIVE.get();
        if (ctx != null) {
            ctx.capture(image, frameX, frameY, width, height);
        }
    }

    private void capture(NativeImage image, int frameX, int frameY, int width, int height) {
        if (width <= 0 || height <= 0 || image == null) {
            return;
        }

        BufferedImage frame = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int rgba = convertABGRtoARGB(image.getPixelRGBA(frameX + x, frameY + y));
                frame.setRGB(x, y, rgba);
            }
        }
        capturedFrames.add(frame);
    }

    public BufferedImage pollLatest() {
        BufferedImage latest = null;
        BufferedImage next;
        while ((next = capturedFrames.poll()) != null) {
            latest = next;
        }
        return latest;
    }

    @Override
    public void close() {
        ACTIVE.remove();
        capturedFrames.clear();
    }

    private static int convertABGRtoARGB(int abgr) {
        int a = (abgr >> 24) & 0xFF;
        int b = (abgr >> 16) & 0xFF;
        int g = (abgr >> 8) & 0xFF;
        int r = abgr & 0xFF;
        return (a << 24) | (r << 16) | (g << 8) | b;
    }
}
