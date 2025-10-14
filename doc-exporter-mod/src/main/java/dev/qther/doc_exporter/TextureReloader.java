package dev.qther.doc_exporter;

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
import java.util.Optional;
import javax.imageio.ImageIO;

/**
 * Utility to reload texture data from resources without requiring OpenGL context.
 *
 * <p>This class uses Minecraft's ResourceManager and ImageIO to load texture PNG files
 * directly from disk using pure Java operations that work in headless environments.</p>
 */
public class TextureReloader {
    private static final Logger LOGGER = LoggerFactory.getLogger(TextureReloader.class);

    /**
     * Reloads a texture as a BufferedImage from resources using Minecraft's ResourceManager.
     *
     * <p>This method bypasses the GPU texture loading system and directly reads
     * the PNG file from resources using ImageIO.read(), which is pure Java
     * and works reliably in headless environments.</p>
     *
     * @param sprite The TextureAtlasSprite to reload texture data for
     * @return The loaded BufferedImage, or null if loading failed
     */
    public static BufferedImage reloadTextureAsBufferedImage(TextureAtlasSprite sprite) {
        SpriteContents contents = sprite.contents();
        ResourceLocation name = ((SpriteContentsAccessor) contents).getName();

        // Convert sprite name to texture path
        ResourceLocation texturePath = ResourceLocation.fromNamespaceAndPath(
            name.getNamespace(),
            "textures/" + name.getPath() + ".png"
        );

        return reloadTextureAsBufferedImage(texturePath);
    }


    /**
     * Reloads a texture as BufferedImage from resources by ResourceLocation.
     * This uses pure Java ImageIO which works reliably in headless environments.
     *
     * @param texturePath The full path to the texture
     * @return The loaded BufferedImage, or null if loading failed
     */
    public static BufferedImage reloadTextureAsBufferedImage(ResourceLocation texturePath) {
        ResourceManager resourceManager = Minecraft.getInstance().getResourceManager();

        try {
            Optional<Resource> resourceOpt = resourceManager.getResource(texturePath);

            if (resourceOpt.isEmpty()) {
                LOGGER.error("Resource not found: {}", texturePath);
                return null;
            }

            Resource resource = resourceOpt.get();

            try (var inputStream = resource.open()) {
                BufferedImage image = ImageIO.read(inputStream);
                if (image != null) {
                    return image;
                } else {
                    LOGGER.error("Failed to decode texture: {}", texturePath);
                    return null;
                }
            }
        } catch (IOException e) {
            LOGGER.error("Failed to reload texture {}: {}", texturePath, e.getMessage());
            return null;
        }
    }
}
