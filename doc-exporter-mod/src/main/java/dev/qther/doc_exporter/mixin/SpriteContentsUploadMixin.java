package dev.qther.doc_exporter.mixin;

import com.mojang.blaze3d.platform.NativeImage;
import dev.qther.doc_exporter.capture.FrameCaptureContext;
import net.minecraft.client.renderer.texture.SpriteContents;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SpriteContents.class)
public abstract class SpriteContentsUploadMixin {
    @Shadow public abstract int width();

    @Shadow public abstract int height();

    @Inject(method = "upload(IIII[Lcom/mojang/blaze3d/platform/NativeImage;)V", at = @At("HEAD"))
    private void docExporter$captureUpload(int x, int y, int frameX, int frameY, NativeImage[] images, CallbackInfo ci) {
        NativeImage source = images.length > 0 ? images[0] : null;
        if (source != null) {
            FrameCaptureContext.onUpload(source, frameX, frameY, width(), height());
        }
    }
}
