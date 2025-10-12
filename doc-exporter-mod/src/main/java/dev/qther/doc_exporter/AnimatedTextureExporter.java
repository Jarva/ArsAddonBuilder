package dev.qther.doc_exporter;

import com.mojang.blaze3d.platform.NativeImage;
import dev.qther.doc_exporter.capture.FrameCaptureContext;
import dev.qther.doc_exporter.mixin.AnimatedTextureAccessor;
import dev.qther.doc_exporter.mixin.AnimatedTextureFramesAccessor;
import dev.qther.doc_exporter.mixin.FrameInfoAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.SpriteContents;
import net.minecraft.client.renderer.texture.SpriteTicker;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.client.model.data.ModelData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Exports animated item textures as APNG files using Minecraft's animation system.
 *
 * <p>This exporter scans all registered items to find those with animated textures,
 * then uses Minecraft's AnimatedTexture to extract frames with proper timing and
 * interpolation settings from the texture's .mcmeta file.
 *
 * <h3>Implementation:</h3>
 * <ul>
 *   <li>Frames are extracted from vertical strip textures using AnimatedTexture metadata</li>
 *   <li>Respects animation timing and frame order from .mcmeta files</li>
 *   <li>Supports interpolation when enabled in the texture's animation settings</li>
 *   <li>Converts ABGR pixel format to ARGB for BufferedImage compatibility</li>
 *   <li>Outputs APNG (Animated PNG) format for better quality and alpha channel support</li>
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
        Path outputDir = ExportPaths.BASE.resolve("animated_textures").resolve(modId);
        boolean outputDirCreated = false;

        int exportedCount = 0;
        Minecraft minecraft = Minecraft.getInstance();

        for (Item item : BuiltInRegistries.ITEM) {
            if (!isItemFromMod(item, modId)) {
                continue;
            }

            try {
                TextureAtlasSprite sprite = getItemSprite(minecraft, item);

                if (isAnimated(sprite)) {
                    if (!outputDirCreated) {
                        try {
                            Files.createDirectories(outputDir);
                            outputDirCreated = true;
                        } catch (IOException e) {
                            LOGGER.error("Failed to create output directory for animated textures", e);
                            return;
                        }
                    }
                    LOGGER.info("Found animated item texture: {}", BuiltInRegistries.ITEM.getKey(item));
                    processAnimatedItem(item, sprite, outputDir);
                    exportedCount++;
                }
            } catch (Exception e) {
                LOGGER.debug("Failed to check item {} for animation: {}",
                    BuiltInRegistries.ITEM.getKey(item), e.getMessage());
            }
        }

        LOGGER.info("Exported {} animated textures for mod {}", exportedCount, modId);
    }

    private static boolean isItemFromMod(Item item, String modId) {
        return BuiltInRegistries.ITEM.getKey(item).getNamespace().equals(modId);
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

    /**
     * Processes a single animated item by extracting its frames and generating a GIF.
     */
    private static void processAnimatedItem(Item item, TextureAtlasSprite sprite, Path outputDir) throws IOException {
        String itemName = BuiltInRegistries.ITEM.getKey(item).getPath();

        try {
            var spriteContents = sprite.contents();
            var contentsAccessor = (AnimatedTextureAccessor) spriteContents;
            var animatedTexture = contentsAccessor.getAnimatedTexture();
            var animTexAccessor = (AnimatedTextureFramesAccessor) animatedTexture;
            boolean interpolate = animTexAccessor.getInterpolateFrames();

            AnimationFrame[] frames = extractFramesFromSprite(sprite);
            LOGGER.debug("Processing {} with {} frames (interpolated: {})", itemName, frames.length, interpolate);

            Path outputPath = outputDir.resolve(itemName + ".png");
            ApngGenerator.generateApngFromFrames(frames, outputPath, interpolate);

            LOGGER.info("Generated animated PNG: {}", outputPath);
        } catch (Exception e) {
            LOGGER.error("Failed to generate APNG for item {}: {}", itemName, e.getMessage(), e);
            writeErrorPlaceholder(outputDir, itemName, e);
        }
    }

    private static void writeErrorPlaceholder(Path outputDir, String itemName, Exception error) throws IOException {
        String errorMessage = String.format(
            "Failed to generate APNG for item: %s. Error: %s",
            itemName, error.getMessage());

        Path placeholderPath = outputDir.resolve(itemName + ".png.txt");
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
        NativeImage originalImage = spriteContents.getOriginalImage();
        if (originalImage == null) {
            throw new RuntimeException("Original sprite image is not available");
        }

        int frameWidth = spriteContents.width();
        int frameHeight = spriteContents.height();

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

        List<AnimationFrame> frames = new ArrayList<>();
        int firstFrameIndex = ((FrameInfoAccessor) frameInfoList.get(0)).getIndex();

        try (FrameCaptureContext capture = FrameCaptureContext.activate()) {
            spriteContents.uploadFirstFrame(0, 0);
            BufferedImage currentFrame = capture.pollLatest();
            if (currentFrame == null) {
                currentFrame = copyImage(getFrameImage(frameCache, originalImage, frameWidth, frameHeight, frameRowSize, firstFrameIndex));
            }

            SpriteTicker ticker = spriteContents.createTicker();
            if (ticker == null) {
                int duration = Math.max(1, ((FrameInfoAccessor) frameInfoList.get(0)).getTime());
                frames.add(new AnimationFrame(copyImage(currentFrame), duration));
                return frames.toArray(new AnimationFrame[0]);
            }

            for (int frameIdx = 0; frameIdx < frameInfoList.size(); frameIdx++) {
                SpriteContents.FrameInfo frameInfo = frameInfoList.get(frameIdx);
                int duration = Math.max(1, ((FrameInfoAccessor) frameInfo).getTime());

                frames.add(new AnimationFrame(copyImage(currentFrame), 1));

                for (int sub = 1; sub < duration; sub++) {
                    ticker.tickAndUpload(0, 0);
                    BufferedImage updated = capture.pollLatest();
                    if (updated != null) {
                        currentFrame = updated;
                    }
                    frames.add(new AnimationFrame(copyImage(currentFrame), 1));
                }

                if (frameIdx < frameInfoList.size() - 1) {
                    ticker.tickAndUpload(0, 0);
                    BufferedImage nextFrame = capture.pollLatest();
                    int nextIndex = ((FrameInfoAccessor) frameInfoList.get(frameIdx + 1)).getIndex();
                    if (nextFrame == null) {
                        nextFrame = copyImage(getFrameImage(frameCache, originalImage, frameWidth, frameHeight, frameRowSize, nextIndex));
                    }
                    currentFrame = nextFrame;
                }
            }
        }

        return frames.toArray(new AnimationFrame[0]);
    }

    private static BufferedImage getFrameImage(Map<Integer, BufferedImage> cache, NativeImage sourceImage,
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

    private static BufferedImage copyRegion(NativeImage sourceImage, int xOffset, int yOffset, int width, int height) {
        BufferedImage frame = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int abgr = sourceImage.getPixelRGBA(xOffset + x, yOffset + y);
                int argb = convertABGRtoARGB(abgr);
                frame.setRGB(x, y, argb);
            }
        }
        return frame;
    }

    /**
     * Converts a pixel from ABGR format to ARGB format.
     *
     * <p>Minecraft's NativeImage stores pixels in ABGR format (Alpha-Blue-Green-Red),
     * while Java's BufferedImage expects ARGB format (Alpha-Red-Green-Blue).
     * This method swaps the red and blue channels to perform the conversion.
     *
     * @param abgr Pixel in ABGR format (0xAABBGGRR)
     * @return Pixel in ARGB format (0xAARRGGBB)
     */
    private static int convertABGRtoARGB(int abgr) {
        int a = (abgr >> 24) & 0xFF;
        int b = (abgr >> 16) & 0xFF;
        int g = (abgr >> 8) & 0xFF;
        int r = abgr & 0xFF;

        return (a << 24) | (r << 16) | (g << 8) | b;
    }

}
