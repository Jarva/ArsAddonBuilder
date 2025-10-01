package dev.qther.doc_exporter.mixin;

import net.minecraft.client.renderer.texture.SpriteContents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(SpriteContents.class)
public interface AnimatedTextureAccessor {
    @Accessor("animatedTexture")
    SpriteContents.AnimatedTexture getAnimatedTexture();
}
