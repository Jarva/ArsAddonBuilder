package dev.qther.doc_exporter;

import com.mojang.blaze3d.platform.NativeImage;
import dev.qther.doc_exporter.mixin.SpriteContentsAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.SpriteContents;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Optional;
import java.util.zip.CRC32;
import javax.imageio.ImageIO;

/**
 * Utility to reload texture data from resources without requiring OpenGL context.
 *
 * <p>This class uses Minecraft's ResourceManager and NativeImage.read() to load
 * texture PNG files directly from disk, which are pure CPU operations that work
 * in headless environments.</p>
 */
public class TextureReloader {
    private static final Logger LOGGER = LoggerFactory.getLogger(TextureReloader.class);

    /**
     * Reloads a texture from resources using Minecraft's ResourceManager.
     *
     * <p>This method bypasses the GPU texture loading system and directly reads
     * the PNG file from resources using NativeImage.read(), which is a pure CPU
     * operation that doesn't require an OpenGL context.</p>
     *
     * @param sprite The TextureAtlasSprite to reload texture data for
     * @return The loaded NativeImage, or null if loading failed
     */
    public static NativeImage reloadTexture(TextureAtlasSprite sprite) {
        SpriteContents contents = sprite.contents();
        ResourceLocation name = ((SpriteContentsAccessor) contents).getName();

        // Convert sprite name to texture path
        // Sprite names are like "minecraft:item/diamond_sword"
        // Need to prepend "textures/" and append ".png"
        ResourceLocation texturePath = ResourceLocation.fromNamespaceAndPath(
            name.getNamespace(),
            "textures/" + name.getPath() + ".png"
        );

        LOGGER.info("Attempting to reload texture: {} -> {}", name, texturePath);
        return reloadTexture(texturePath);
    }

    /**
     * Reloads a texture from resources by ResourceLocation.
     *
     * @param texturePath The full path to the texture (should include "textures/" prefix and ".png" suffix)
     * @return The loaded NativeImage, or null if loading failed
     */
    public static NativeImage reloadTexture(ResourceLocation texturePath) {
        ResourceManager resourceManager = Minecraft.getInstance().getResourceManager();

        try {
            // Try to get the resource - use Optional to handle missing resources gracefully
            Optional<Resource> resourceOpt = resourceManager.getResource(texturePath);

            if (resourceOpt.isEmpty()) {
                LOGGER.error("Resource not found: {}", texturePath);
                return null;
            }

            Resource resource = resourceOpt.get();
            try (var inputStream = resource.open()) {
                byte[] imageBytes = inputStream.readAllBytes();

                if (imageBytes.length == 0) {
                    LOGGER.error("Resource stream was empty for: {}", texturePath);
                    return null;
                }

                CRC32 crc32 = new CRC32();
                crc32.update(imageBytes);

                boolean hasNonZeroByte = false;
                for (byte b : imageBytes) {
                    if (b != 0) {
                        hasNonZeroByte = true;
                        break;
                    }
                }

                LOGGER.info("Read {} bytes for {} (CRC32={}, nonZeroBytes={}) header={}",
                    imageBytes.length, texturePath, Long.toHexString(crc32.getValue()), hasNonZeroByte,
                    hexSnippet(imageBytes, 16));

                // NativeImage.read() is a pure CPU operation - no OpenGL required
                NativeImage image = NativeImage.read(new ByteArrayInputStream(imageBytes));
                if (image != null) {
                    // Check if the image actually has non-transparent pixels
                    if (hasVisiblePixels(image)) {
                        LOGGER.info("Successfully reloaded texture: {} ({}x{})",
                            texturePath, image.getWidth(), image.getHeight());
                        return image;
                    }

                    logTransparentSample(texturePath, image);
                    image.close();
                } else {
                    LOGGER.error("NativeImage.read() returned null for texture: {}", texturePath);
                }

                // Fallback to ImageIO if NativeImage yielded only transparent pixels
                BufferedImage buffered = ImageIO.read(new ByteArrayInputStream(imageBytes));
                if (buffered != null) {
                    LOGGER.warn("Falling back to ImageIO decoder for texture {}", texturePath);
                    NativeImage fallbackImage = convertBufferedToNative(buffered);
                    if (hasVisiblePixels(fallbackImage)) {
                        LOGGER.info("ImageIO successfully decoded texture: {} ({}x{})",
                            texturePath, fallbackImage.getWidth(), fallbackImage.getHeight());
                    } else {
                        logTransparentSample(texturePath, fallbackImage);
                        writeDebugDump(texturePath, imageBytes);
                    }
                    return fallbackImage;
                }

                LOGGER.error("Failed to decode texture {} using both NativeImage and ImageIO", texturePath);
                writeDebugDump(texturePath, imageBytes);
                return null;
            }
        } catch (IOException e) {
            LOGGER.error("Failed to reload texture {}: {}", texturePath, e.getMessage(), e);
            return null;
        }
    }

    private static boolean hasVisiblePixels(NativeImage image) {
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                int pixel = image.getPixelRGBA(x, y);
                int alpha = (pixel >> 24) & 0xFF;
                if (alpha > 0) {
                    return true;
                }
            }
        }
        return false;
    }

    private static void logTransparentSample(ResourceLocation texturePath, NativeImage image) {
        StringBuilder sample = new StringBuilder();
        int sampleWidth = Math.min(image.getWidth(), 4);
        int sampleHeight = Math.min(image.getHeight(), 4);
        for (int y = 0; y < sampleHeight; y++) {
            for (int x = 0; x < sampleWidth; x++) {
                int pixel = image.getPixelRGBA(x, y);
                sample.append(String.format(Locale.ROOT, "0x%08X", pixel));
                if (x != sampleWidth - 1 || y != sampleHeight - 1) {
                    sample.append(",");
                }
            }
        }
        LOGGER.warn("Loaded texture {} ({}x{}) but sampled pixels are transparent: {}",
            texturePath, image.getWidth(), image.getHeight(), sample);
    }

    private static NativeImage convertBufferedToNative(BufferedImage buffered) {
        NativeImage nativeImage = new NativeImage(buffered.getWidth(), buffered.getHeight(), true);
        for (int y = 0; y < buffered.getHeight(); y++) {
            for (int x = 0; x < buffered.getWidth(); x++) {
                int argb = buffered.getRGB(x, y);
                nativeImage.setPixelRGBA(x, y, convertARGBtoABGR(argb));
            }
        }
        return nativeImage;
    }

    private static int convertARGBtoABGR(int argb) {
        int a = (argb >> 24) & 0xFF;
        int r = (argb >> 16) & 0xFF;
        int g = (argb >> 8) & 0xFF;
        int b = argb & 0xFF;
        return (a << 24) | (b << 16) | (g << 8) | r;
    }

    private static String hexSnippet(byte[] data, int length) {
        int actualLength = Math.min(data.length, length);
        StringBuilder builder = new StringBuilder(actualLength * 2);
        for (int i = 0; i < actualLength; i++) {
            builder.append(String.format(Locale.ROOT, "%02X", data[i]));
        }
        return builder.toString();
    }

    private static void writeDebugDump(ResourceLocation texturePath, byte[] data) {
        try {
            Path debugDir = ExportPaths.BASE.resolve("debug/raw")
                .resolve(texturePath.getNamespace());
            Path outputPath = debugDir.resolve(texturePath.getPath());
            Files.createDirectories(outputPath.getParent());
            Files.write(outputPath, data);
            LOGGER.warn("Wrote debug copy of {} to {}", texturePath, outputPath.toAbsolutePath());
        } catch (IOException e) {
            LOGGER.error("Failed to write debug dump for {}: {}", texturePath, e.getMessage());
        }
    }
}
