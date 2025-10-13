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

import java.io.IOException;
import java.io.InputStream;

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
            Resource resource = resourceManager.getResourceOrThrow(texturePath);
            try (InputStream inputStream = resource.open()) {
                // NativeImage.read() is a pure CPU operation - no OpenGL required
                NativeImage image = NativeImage.read(inputStream);
                LOGGER.debug("Successfully reloaded texture: {}", texturePath);
                return image;
            }
        } catch (IOException e) {
            LOGGER.error("Failed to reload texture {}: {}", texturePath, e.getMessage());
            return null;
        }
    }
}
