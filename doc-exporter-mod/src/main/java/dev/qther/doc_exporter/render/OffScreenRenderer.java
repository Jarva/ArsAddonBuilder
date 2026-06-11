package dev.qther.doc_exporter.render;

import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import dev.qther.doc_exporter.mixin.AnimatedTextureAccessor;
import dev.qther.doc_exporter.mixin.AnimatedTextureFramesAccessor;
import dev.qther.doc_exporter.mixin.FrameInfoAccessor;
import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexSorting;
import java.io.IOException;
import java.nio.FloatBuffer;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Map;
import java.util.stream.Collectors;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.FogRenderer;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.lwjgl.opengl.GL12;

/**
 * Off-screen framebuffer capture utility adapted from GuideME 21.1.16.
 */
public class OffScreenRenderer implements AutoCloseable {
    private final NativeImage nativeImage;
    private final TextureTarget fb;
    private final int width;
    private final int height;

    public OffScreenRenderer(int width, int height) {
        this.width = width;
        this.height = height;
        RenderSystem.viewport(0, 0, width, height);
        nativeImage = new NativeImage(width, height, true);
        fb = new TextureTarget(width, height, true /* with depth */, true /* check error */);
        fb.setClearColor(0, 0, 0, 0);
        fb.clear(true /* check error */);
    }

    @Override
    public void close() {
        nativeImage.close();
        fb.destroyBuffers();

        var minecraft = Minecraft.getInstance();
        if (minecraft != null) {
            var window = minecraft.getWindow();
            RenderSystem.viewport(0, 0, window.getWidth(), window.getHeight());
        }
    }

    public byte[] captureAsPng(Runnable r) {
        renderToBuffer(r);

        try {
            return nativeImage.asByteArray();
        } catch (IOException e) {
            throw new RuntimeException("failed to encode image as PNG", e);
        }
    }

    public void captureAsPng(Runnable r, Path path) throws IOException {
        renderToBuffer(r);
        nativeImage.writeToFile(path);
    }

    public boolean isAnimated(Collection<TextureAtlasSprite> sprites) {
        return sprites.stream().anyMatch(s -> ((AnimatedTextureAccessor) s.contents()).getAnimatedTexture() != null);
    }

    public int getAnimationDurationTicks(Collection<TextureAtlasSprite> sprites) {
        return sprites.stream()
                .filter(OffScreenRenderer::isAnimated)
                .mapToInt(OffScreenRenderer::getAnimationDurationTicks)
                .max()
                .orElse(0);
    }

    public byte[] captureAsWebp(Runnable r, Collection<TextureAtlasSprite> sprites, WebPExporter.Format format) {
        var animatedSprites = sprites.stream()
                .filter(OffScreenRenderer::isAnimated)
                .toList();

        if (animatedSprites.isEmpty()) {
            return captureAsPng(r);
        }

        // This is an oversimplification. Not all animated textures may have the same loop frequency,
        // but the greatest common divisor could be so inconvenient that we are essentially looping forever.
        var maxTime = animatedSprites.stream()
                .mapToInt(OffScreenRenderer::getAnimationDurationTicks)
                .max()
                .orElse(0);

        var textureManager = Minecraft.getInstance().getTextureManager();

        var tickers = animatedSprites.stream()
                .collect(Collectors.groupingBy(TextureAtlasSprite::atlasLocation))
                .entrySet().stream().collect(
                        Collectors.toMap(
                                Map.Entry::getKey,
                                e -> e.getValue().stream().map(TextureAtlasSprite::createTicker).toList()));
        for (var sprite : animatedSprites) {
            textureManager.getTexture(sprite.atlasLocation()).bind();
            sprite.uploadFirstFrame();
        }

        try (var webpWriter = new WebPExporter(nativeImage.getWidth(), nativeImage.getHeight(), format)) {
            for (var i = 0; i < maxTime; i++) {
                for (var entry : tickers.entrySet()) {
                    textureManager.getTexture(entry.getKey()).bind();
                    for (var ticker : entry.getValue()) {
                        ticker.tickAndUpload();
                    }
                }

                renderToBuffer(r);
                webpWriter.writeFrame(i, nativeImage);
            }

            return webpWriter.finish();
        }
    }

    private static boolean isAnimated(TextureAtlasSprite sprite) {
        return ((AnimatedTextureAccessor) sprite.contents()).getAnimatedTexture() != null;
    }

    private static int getAnimationDurationTicks(TextureAtlasSprite sprite) {
        var animation = ((AnimatedTextureAccessor) sprite.contents()).getAnimatedTexture();
        if (animation == null) {
            return 0;
        }

        return ((AnimatedTextureFramesAccessor) animation)
                .getFrames().stream()
                .mapToInt(value -> ((FrameInfoAccessor) value).getTime())
                .sum();
    }

    private void renderToBuffer(Runnable r) {
        fb.bindWrite(true);
        try {
            GlStateManager._clear(GL12.GL_COLOR_BUFFER_BIT | GL12.GL_DEPTH_BUFFER_BIT, false);
            try {
                r.run();
            } catch (Throwable e) {
                // Some modded renderers can throw while the global model-view stack is pushed. Reset only on failure so
                // successful captures keep caller-provided setup such as setupItemRendering() for later frames/items.
                RenderSystem.getModelViewStack().clear();
                RenderSystem.applyModelViewMatrix();
                throw e;
            }
        } finally {
            fb.unbindWrite();
        }

        fb.bindRead();
        try {
            nativeImage.downloadTexture(0, false);
            nativeImage.flipY();
        } finally {
            fb.unbindRead();
        }
    }

    public void setupItemRendering() {
        var matrix4f = new Matrix4f().setOrtho(
                0.0f, 16,
                16, 0.0f,
                1000.0f, 21000.0f);
        RenderSystem.setProjectionMatrix(matrix4f, VertexSorting.ORTHOGRAPHIC_Z);

        var poseStack = RenderSystem.getModelViewStack();
        poseStack.identity();
        poseStack.translate(0.0f, 0.0f, -11000.0f);
        RenderSystem.applyModelViewMatrix();
        Lighting.setupFor3DItems();
        FogRenderer.setupNoFog();
    }

    public void setupOrtographicRendering() {
        float angle = 36;
        float renderHeight = 0;
        float renderScale = 100;
        float rotation = 45;

        RenderSystem.setProjectionMatrix(
                new Matrix4f().ortho(-1, 1, 1, -1, 1000, 3000),
                VertexSorting.ORTHOGRAPHIC_Z);

        var poseStack = RenderSystem.getModelViewStack();
        poseStack.identity();
        poseStack.translate(0.0F, 0.0F, -2000.0F);

        FogRenderer.setupNoFog();

        poseStack.scale(1, -1, -1);
        poseStack.rotate(new Quaternionf().rotationY(Mth.DEG_TO_RAD * -180));

        Quaternionf flip = new Quaternionf().rotationZ(Mth.DEG_TO_RAD * 180);
        flip.mul(new Quaternionf().rotationX(Mth.DEG_TO_RAD * angle));

        poseStack.translate(0, (renderHeight / -300f), 0);
        poseStack.scale(renderScale * 0.004f, renderScale * 0.004f, 1f);

        Quaternionf rotate = new Quaternionf().rotationY(Mth.DEG_TO_RAD * rotation);
        poseStack.rotate(flip);
        poseStack.rotate(rotate);

        RenderSystem.applyModelViewMatrix();
        Lighting.setupLevel();
    }

    public void setupPerspectiveRendering(float zoom, float fov, Vector3f eyePos, Vector3f lookAt) {
        float aspectRatio = (float) width / height;

        Matrix4fStack projMat = new Matrix4fStack();
        if (zoom != 1.0F) {
            projMat.scale(zoom, zoom, 1.0F);
        }

        projMat.mul(new Matrix4f().perspective(fov, aspectRatio, 0.05F, 16));
        RenderSystem.setProjectionMatrix(projMat, VertexSorting.DISTANCE_TO_ORIGIN);

        var poseStack = RenderSystem.getModelViewStack();
        poseStack.identity();
        var vm = createViewMatrix(eyePos, lookAt);
        poseStack.mul(vm);

        RenderSystem.applyModelViewMatrix();
        Lighting.setupLevel();
    }

    private static Matrix4f createViewMatrix(Vector3f eyePos, Vector3f lookAt) {
        Vector3f dir = new Vector3f(lookAt);
        dir.sub(eyePos);

        Vector3f up = new Vector3f(0, 1f, 0);
        dir.normalize();

        var right = new Vector3f(dir);
        right.cross(up);
        right.normalize();

        up = new Vector3f(right);
        up.cross(dir);
        up.normalize();

        var viewMatrix = new Matrix4f();
        viewMatrix.setTransposed(FloatBuffer.wrap(new float[] {
                right.x(), right.y(), right.z(), 0.0f,
                up.x(), up.y(), up.z(), 0.0f,
                -dir.x(), -dir.y(), -dir.z(), 0.0f,
                0.0f, 0.0f, 0.0f, 1.0f,
        }));

        viewMatrix.translate(-eyePos.x(), -eyePos.y(), -eyePos.z());
        return viewMatrix;
    }
}
