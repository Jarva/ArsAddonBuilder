package dev.qther.doc_exporter.mixin;

import net.minecraft.client.renderer.texture.SpriteContents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(SpriteContents.FrameInfo.class)
public interface FrameInfoAccessor {
    @Accessor("index")
    int getIndex();

    @Accessor("time")
    int getTime();
}
