package dev.qther.doc_exporter;

import com.mojang.blaze3d.platform.NativeImage;
import dev.qther.doc_exporter.mixin.AnimatedTextureAccessor;
import dev.qther.doc_exporter.mixin.AnimatedTextureFramesAccessor;
import dev.qther.doc_exporter.mixin.FrameInfoAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.SpriteContents;
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
        var spriteContents = sprite.contents();
        int frameWidth = spriteContents.width();
        int frameHeight = spriteContents.height();

        // Access the AnimatedTexture via mixin
        var contentsAccessor = (AnimatedTextureAccessor) spriteContents;
        var animatedTexture = contentsAccessor.getAnimatedTexture();

        if (animatedTexture == null) {
            throw new RuntimeException("AnimatedTexture is null for sprite");
        }

        // Get the frame sequence from AnimatedTexture
        var animTexAccessor = (AnimatedTextureFramesAccessor) animatedTexture;
        var frameInfoList = animTexAccessor.getFrames();
        boolean interpolate = animTexAccessor.getInterpolateFrames();

        LOGGER.debug("Extracting {} frames ({}x{} each), interpolation: {}",
            frameInfoList.size(), frameWidth, frameHeight, interpolate);

        // Get the mipmap containing all frames stacked vertically
        var mipmapImages = spriteContents.byMipLevel;
        if (mipmapImages.length == 0) {
            throw new RuntimeException("No mipmap images available");
        }
        NativeImage sourceImage = mipmapImages[0];

        // Extract frames according to the animation sequence
        java.util.List<AnimationFrame> frames = new java.util.ArrayList<>();

        for (int i = 0; i < frameInfoList.size(); i++) {
            SpriteContents.FrameInfo frameInfo = frameInfoList.get(i);
            var frameInfoAccessor = (FrameInfoAccessor) frameInfo;

            int frameIndex = frameInfoAccessor.getIndex();
            int frameDuration = frameInfoAccessor.getTime();

            BufferedImage image = extractSingleFrame(sourceImage, frameIndex, frameWidth, frameHeight);
            frames.add(new AnimationFrame(image, frameDuration));

            // If interpolation is enabled and there's a next frame, add interpolated frames
            if (interpolate && i < frameInfoList.size() - 1) {
                SpriteContents.FrameInfo nextFrameInfo = frameInfoList.get(i + 1);
                var nextFrameInfoAccessor = (FrameInfoAccessor) nextFrameInfo;
                int nextFrameIndex = nextFrameInfoAccessor.getIndex();

                BufferedImage nextImage = extractSingleFrame(sourceImage, nextFrameIndex, frameWidth, frameHeight);

                // Add one interpolated frame between current and next
                BufferedImage interpolated = interpolateFrames(image, nextImage, 0.5f);
                frames.add(new AnimationFrame(interpolated, frameDuration));
            }
        }

        return frames.toArray(new AnimationFrame[0]);
    }

    /**
     * Creates an interpolated frame between two images.
     */
    private static BufferedImage interpolateFrames(BufferedImage frame1, BufferedImage frame2, float alpha) {
        int width = frame1.getWidth();
        int height = frame1.getHeight();
        BufferedImage result = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int argb1 = frame1.getRGB(x, y);
                int argb2 = frame2.getRGB(x, y);

                int a1 = (argb1 >> 24) & 0xFF;
                int r1 = (argb1 >> 16) & 0xFF;
                int g1 = (argb1 >> 8) & 0xFF;
                int b1 = argb1 & 0xFF;

                int a2 = (argb2 >> 24) & 0xFF;
                int r2 = (argb2 >> 16) & 0xFF;
                int g2 = (argb2 >> 8) & 0xFF;
                int b2 = argb2 & 0xFF;

                int a = (int) (a1 * (1 - alpha) + a2 * alpha);
                int r = (int) (r1 * (1 - alpha) + r2 * alpha);
                int g = (int) (g1 * (1 - alpha) + g2 * alpha);
                int b = (int) (b1 * (1 - alpha) + b2 * alpha);

                result.setRGB(x, y, (a << 24) | (r << 16) | (g << 8) | b);
            }
        }

        return result;
    }

    /**
     * Extracts a single frame from the mipmap's vertical strip.
     *
     * @param sourceImage The NativeImage containing all frames stacked vertically
     * @param frameIndex Which frame to extract (0-based)
     * @param frameWidth Width of a single frame
     * @param frameHeight Height of a single frame
     * @return BufferedImage containing the extracted frame with ARGB color format
     */
    private static BufferedImage extractSingleFrame(NativeImage sourceImage, int frameIndex, int frameWidth, int frameHeight) {
        int yOffset = frameIndex * frameHeight;
        BufferedImage frame = new BufferedImage(frameWidth, frameHeight, BufferedImage.TYPE_INT_ARGB);

        for (int y = 0; y < frameHeight; y++) {
            for (int x = 0; x < frameWidth; x++) {
                int sourceY = yOffset + y;
                if (sourceY < sourceImage.getHeight()) {
                    int abgr = sourceImage.getPixelRGBA(x, sourceY);
                    int argb = convertABGRtoARGB(abgr);
                    frame.setRGB(x, y, argb);
                }
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
