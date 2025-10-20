package dev.qther.doc_exporter;

import dev.qther.doc_exporter.mixin.AnimatedTextureAccessor;
import dev.qther.doc_exporter.mixin.AnimatedTextureFramesAccessor;
import dev.qther.doc_exporter.mixin.FrameInfoAccessor;
import dev.qther.doc_exporter.mixin.SpriteContentsAccessor;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectSets;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.SpriteContents;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.client.model.data.ModelData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Phaser;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Exports animated item textures as GIF files using Minecraft's animation system.
 *
 * <p>This exporter scans all registered items to find those with animated textures,
 * then uses Minecraft's AnimatedTexture to extract frames with proper timing and
 * interpolation settings from the texture's .mcmeta file.
 *
 * <h3>Implementation:</h3>
 * <ul>
 *   <li>Loads textures using ImageIO (pure Java, works reliably in headless environments)</li>
 *   <li>Scales textures to 128x128 using nearest neighbor interpolation (preserves pixel art look)</li>
 *   <li>Frames are extracted from vertical strip textures using AnimatedTexture metadata</li>
 *   <li>Respects animation timing and frame order from .mcmeta files</li>
 *   <li>Supports interpolation when enabled in the texture's animation settings</li>
 *   <li>Outputs GIF format for universal compatibility (Discord, browsers, viewers)</li>
 * </ul>
 */
public class AnimatedTextureExporter {
    private static final Logger LOGGER = LoggerFactory.getLogger(AnimatedTextureExporter.class);

    /**
     * Container for a single animation frame with its duration.
     */
    public static class AnimationFrame {
        public final BufferedImage image;
        public final int durationTicks;

        public AnimationFrame(BufferedImage image, int durationTicks) {
            this.image = image;
            this.durationTicks = durationTicks;
        }
    }

    /**
     * Exports all animated textures for items from the specified mod.
     *
     * @param modId The mod ID to export animated textures from
     */
    public static void exportAnimatedTextures(String modId) {
        Path baseOutputDir = ExportPaths.BASE.resolve("animated_textures");

        Minecraft minecraft = Minecraft.getInstance();

        var phaser = new Phaser(1);
        var exported = new AtomicInteger();

        for (Item item : BuiltInRegistries.ITEM) {
            if (!isItemFromMod(item, modId)) {
                continue;
            }

            TextureAtlasSprite sprite = getItemSprite(minecraft, item);

            if (isAnimated(sprite)) {
                phaser.register();
                DocExportHelper.executor.submit(() -> {
                    try {
                        processAnimatedItem(item, sprite, baseOutputDir);
                    } catch (IOException e) {
                        LOGGER.debug("Failed to check item {} for animation: {}",
                                BuiltInRegistries.ITEM.getKey(item), e.getMessage());
                    }
                    exported.incrementAndGet();
                    phaser.arriveAndDeregister();
                });
            }
        }

        phaser.arriveAndAwaitAdvance();

        LOGGER.info("Exported {} animated textures for mod {}", exported.get(), modId);
    }

    private static boolean isItemFromMod(Item item, String modId) {
        var itemNamespace = BuiltInRegistries.ITEM.getKey(item).getNamespace();
        return itemNamespace.equals(modId) || (modId.equals("not_enough_glyphs") && itemNamespace.matches("^(toomanyglyphs|arsomega|ars_scalaes|ars_trinkets)$"));
    }

    private static TextureAtlasSprite getItemSprite(Minecraft minecraft, Item item) {
        return minecraft.getItemRenderer()
                .getItemModelShaper()
                .getItemModel(item.getDefaultInstance())
                .getParticleIcon(ModelData.EMPTY);
    }

    private static boolean isAnimated(TextureAtlasSprite sprite) {
        return sprite.contents().getUniqueFrames().count() > 1;
    }

    private static final Set<ResourceLocation> PROCESSED = ObjectSets.synchronize(new ObjectOpenHashSet<>());

    /**
     * Processes a single animated item by extracting its frames and generating a GIF.
     * Files are organized by the texture's namespace and path: animated_textures/{namespace}/{texture_path}.gif
     */
    private static void processAnimatedItem(Item item, TextureAtlasSprite sprite, Path baseOutputDir) throws IOException {
        // Get the actual texture name from the sprite (e.g., "minecraft:item/diamond_sword")
        SpriteContents spriteContents = sprite.contents();
        ResourceLocation textureName = ((SpriteContentsAccessor) spriteContents).getName();

        if (!PROCESSED.add(textureName)) {
            LOGGER.error("GIF for texture {} already generated or generating, skipping", textureName);
            return;
        }

        String textureNamespace = textureName.getNamespace();
        String texturePath = textureName.getPath();

        // Create directory structure based on texture path (may include subdirectories like "item/")
        Path outputPath = baseOutputDir.resolve(textureNamespace).resolve(texturePath + ".gif");
        Files.createDirectories(outputPath.getParent());

        try {
            AnimationFrame[] frames = extractFramesFromSprite(sprite);
            GifGenerator.generateGifFromFrames(frames, outputPath);

            LOGGER.info("Generated animated GIF: {}", outputPath);
        } catch (Exception e) {
            LOGGER.error("Failed to generate GIF for texture {}: {}", textureName, e.getMessage(), e);
            writeErrorPlaceholder(outputPath.getParent(), outputPath.getFileName().toString().replace(".gif", ""), e);
        }
    }

    private static void writeErrorPlaceholder(Path outputDir, String textureName, Exception error) throws IOException {
        String errorMessage = String.format(
                "Failed to generate GIF for texture: %s. Error: %s",
                textureName, error.getMessage());

        Path placeholderPath = outputDir.resolve(textureName + ".gif.txt");
        Files.writeString(placeholderPath, errorMessage);
    }

    /**
     * Extracts animation frames from a TextureAtlasSprite using Minecraft's AnimatedTexture
     * frame sequence. This respects the animation's frame order and timing, including
     * interpolation if enabled.
     *
     * @param sprite The TextureAtlasSprite containing the animated texture
     * @return Array of AnimationFrames with images and durations from the animation metadata
     */
    private static AnimationFrame[] extractFramesFromSprite(TextureAtlasSprite sprite) {
        SpriteContents spriteContents = sprite.contents();

        // Reload texture from resources using ImageIO (pure Java, works reliably in headless)
        BufferedImage originalImage = TextureReloader.reloadTextureAsBufferedImage(sprite);
        if (originalImage == null) {
            throw new RuntimeException("Failed to reload sprite texture from resources");
        }

        int frameWidth = spriteContents.width();
        int frameHeight = spriteContents.height();

        // Scale up the texture using nearest neighbor (8x scale: 16x16 -> 128x128)
        int targetSize = 128;
        int scaleFactor = targetSize / frameWidth;
        if (scaleFactor > 1) {
            BufferedImage scaledImage = scaleImageNearestNeighbor(originalImage, scaleFactor);
            originalImage = scaledImage;
            frameWidth *= scaleFactor;
            frameHeight *= scaleFactor;
            LOGGER.debug("Scaled texture {}x up to {}x{}", scaleFactor, frameWidth, frameHeight);
        }

        var animatedTexture = ((AnimatedTextureAccessor) spriteContents).getAnimatedTexture();
        if (animatedTexture == null) {
            BufferedImage frame = copyRegion(originalImage, 0, 0, frameWidth, frameHeight);
            return new AnimationFrame[]{new AnimationFrame(frame, 1)};
        }

        AnimatedTextureFramesAccessor animationAccessor = (AnimatedTextureFramesAccessor) animatedTexture;
        List<SpriteContents.FrameInfo> frameInfoList = animationAccessor.getFrames();
        if (frameInfoList.isEmpty()) {
            BufferedImage frame = copyRegion(originalImage, 0, 0, frameWidth, frameHeight);
            return new AnimationFrame[]{new AnimationFrame(frame, 1)};
        }

        int frameRowSize = Math.max(1, animationAccessor.getFrameRowSize());
        boolean interpolateFrames = animationAccessor.getInterpolateFrames();
        Map<Integer, BufferedImage> frameCache = new HashMap<>();

        if (!interpolateFrames) {
            List<AnimationFrame> frames = new ArrayList<>();
            for (SpriteContents.FrameInfo frameInfo : frameInfoList) {
                int frameIndex = ((FrameInfoAccessor) frameInfo).getIndex();
                int duration = Math.max(1, ((FrameInfoAccessor) frameInfo).getTime());
                BufferedImage image = copyImage(getFrameImage(frameCache, originalImage, frameWidth, frameHeight, frameRowSize, frameIndex));
                frames.add(new AnimationFrame(image, duration));
            }
            return frames.toArray(new AnimationFrame[0]);
        }

        // CPU-based interpolation: generate interpolated sub-frames between each frame pair
        List<AnimationFrame> frames = new ArrayList<>();

        for (int frameIdx = 0; frameIdx < frameInfoList.size(); frameIdx++) {
            SpriteContents.FrameInfo frameInfo = frameInfoList.get(frameIdx);
            int frameIndex = ((FrameInfoAccessor) frameInfo).getIndex();
            int duration = Math.max(1, ((FrameInfoAccessor) frameInfo).getTime());

            BufferedImage currentFrame = getFrameImage(frameCache, originalImage, frameWidth, frameHeight, frameRowSize, frameIndex);

            // Get next frame for interpolation
            BufferedImage nextFrame;
            if (frameIdx < frameInfoList.size() - 1) {
                int nextIndex = ((FrameInfoAccessor) frameInfoList.get(frameIdx + 1)).getIndex();
                nextFrame = getFrameImage(frameCache, originalImage, frameWidth, frameHeight, frameRowSize, nextIndex);
            } else {
                // Last frame wraps to first frame
                int firstIndex = ((FrameInfoAccessor) frameInfoList.get(0)).getIndex();
                nextFrame = getFrameImage(frameCache, originalImage, frameWidth, frameHeight, frameRowSize, firstIndex);
            }

            // Generate interpolated sub-frames
            for (int sub = 0; sub < duration; sub++) {
                float t = (float) sub / (float) duration;
                BufferedImage interpolated = interpolateFrames(currentFrame, nextFrame, t);
                frames.add(new AnimationFrame(copyImage(interpolated), 1));
            }
        }

        return frames.toArray(new AnimationFrame[0]);
    }

    private static BufferedImage getFrameImage(Map<Integer, BufferedImage> cache, BufferedImage sourceImage,
                                               int frameWidth, int frameHeight, int frameRowSize, int frameIndex) {
        if (frameIndex < 0) {
            throw new RuntimeException("Negative frame index " + frameIndex);
        }

        BufferedImage cached = cache.get(frameIndex);
        if (cached != null) {
            return cached;
        }

        int framesPerRow = Math.max(1, frameRowSize);
        int xIndex = frameIndex % framesPerRow;
        int yIndex = frameIndex / framesPerRow;
        int xOffset = xIndex * frameWidth;
        int yOffset = yIndex * frameHeight;

        if (xOffset + frameWidth > sourceImage.getWidth() || yOffset + frameHeight > sourceImage.getHeight()) {
            throw new RuntimeException("Frame " + frameIndex + " exceeds sprite bounds (" + sourceImage.getWidth() + "x" + sourceImage.getHeight() + ")");
        }

        BufferedImage frame = copyRegion(sourceImage, xOffset, yOffset, frameWidth, frameHeight);
        cache.put(frameIndex, frame);
        return frame;
    }

    private static BufferedImage copyImage(BufferedImage source) {
        BufferedImage copy = new BufferedImage(source.getWidth(), source.getHeight(), BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < source.getHeight(); y++) {
            for (int x = 0; x < source.getWidth(); x++) {
                copy.setRGB(x, y, source.getRGB(x, y));
            }
        }
        return copy;
    }

    private static BufferedImage copyRegion(BufferedImage sourceImage, int xOffset, int yOffset, int width, int height) {
        // Extract a sub-region from the source image
        // BufferedImage to BufferedImage - no format conversion needed!
        return sourceImage.getSubimage(xOffset, yOffset, width, height);
    }

    /**
     * Interpolates between two frames using alpha blending (CPU-based).
     *
     * <p>This method performs per-pixel alpha blending to create smooth transitions
     * between frames, matching Minecraft's GPU-based interpolation behavior but using
     * pure CPU operations that work in headless environments.
     *
     * @param frame1 The first frame (at t=0)
     * @param frame2 The second frame (at t=1)
     * @param t      Interpolation factor (0.0 = fully frame1, 1.0 = fully frame2)
     * @return A new BufferedImage with interpolated pixel values
     */
    private static BufferedImage interpolateFrames(BufferedImage frame1, BufferedImage frame2, float t) {
        if (frame1.getWidth() != frame2.getWidth() || frame1.getHeight() != frame2.getHeight()) {
            throw new IllegalArgumentException("Frame dimensions must match for interpolation");
        }

        int width = frame1.getWidth();
        int height = frame1.getHeight();
        BufferedImage result = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);

        float oneMinusT = 1.0f - t;

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int argb1 = frame1.getRGB(x, y);
                int argb2 = frame2.getRGB(x, y);

                // Extract ARGB components from frame1
                int a1 = (argb1 >> 24) & 0xFF;
                int r1 = (argb1 >> 16) & 0xFF;
                int g1 = (argb1 >> 8) & 0xFF;
                int b1 = argb1 & 0xFF;

                // Extract ARGB components from frame2
                int a2 = (argb2 >> 24) & 0xFF;
                int r2 = (argb2 >> 16) & 0xFF;
                int g2 = (argb2 >> 8) & 0xFF;
                int b2 = argb2 & 0xFF;

                // Interpolate each channel
                int a = (int) (a1 * oneMinusT + a2 * t);
                int r = (int) (r1 * oneMinusT + r2 * t);
                int g = (int) (g1 * oneMinusT + g2 * t);
                int b = (int) (b1 * oneMinusT + b2 * t);

                // Clamp to valid range
                a = Math.min(255, Math.max(0, a));
                r = Math.min(255, Math.max(0, r));
                g = Math.min(255, Math.max(0, g));
                b = Math.min(255, Math.max(0, b));

                int argb = (a << 24) | (r << 16) | (g << 8) | b;
                result.setRGB(x, y, argb);
            }
        }

        return result;
    }

    /**
     * Scales an image using nearest neighbor interpolation to preserve pixel art appearance.
     *
     * @param source      The source image to scale
     * @param scaleFactor The integer scale factor (2 = double size, 8 = 16x16 to 128x128)
     * @return A new BufferedImage scaled up by the given factor
     */
    private static BufferedImage scaleImageNearestNeighbor(BufferedImage source, int scaleFactor) {
        int newWidth = source.getWidth() * scaleFactor;
        int newHeight = source.getHeight() * scaleFactor;
        BufferedImage scaled = new BufferedImage(newWidth, newHeight, BufferedImage.TYPE_INT_ARGB);

        for (int y = 0; y < newHeight; y++) {
            for (int x = 0; x < newWidth; x++) {
                int srcX = x / scaleFactor;
                int srcY = y / scaleFactor;
                int pixel = source.getRGB(srcX, srcY);
                scaled.setRGB(x, y, pixel);
            }
        }

        return scaled;
    }
}
