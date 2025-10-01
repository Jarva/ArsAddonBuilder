package dev.qther.doc_exporter.mixin;

import net.minecraft.client.renderer.texture.SpriteContents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;

@Mixin(SpriteContents.AnimatedTexture.class)
public interface AnimatedTextureFramesAccessor {
    @Accessor("frames")
    List<SpriteContents.FrameInfo> getFrames();

    @Accessor("interpolateFrames")
    boolean getInterpolateFrames();
}
