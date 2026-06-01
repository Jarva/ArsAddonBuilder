package dev.qther.doc_exporter;

import com.google.common.base.Stopwatch;
import dev.qther.doc_exporter.render.OffScreenRenderer;
import dev.qther.doc_exporter.render.RenderSpriteCollector;
import dev.qther.doc_exporter.render.WebPExporter;
import guideme.color.LightDarkMode;
import guideme.document.LytSize;
import guideme.extensions.ExtensionCollection;
import guideme.scene.CameraSettings;
import guideme.scene.GuidebookLevelRenderer;
import guideme.scene.GuidebookScene;
import guideme.scene.LytGuidebookScene;
import guideme.scene.PerspectivePreset;
import guideme.scene.level.GuidebookLevel;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import org.apache.commons.lang3.time.DurationFormatUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Exports fully rendered item, block and entity images for every loaded registry entry.
 *
 * <p>The block render path follows GuideME's BlockImage export strategy: create a minimal one-block scene, render it
 * through a GuideME-derived off-screen renderer, and use GuideME's animated WebP strategy when any referenced sprite
 * is animated.</p>
 */
public final class RenderedAssetExporter {
    private static final Logger LOGGER = LoggerFactory.getLogger(DocExportHelper.MODID + ":RenderedAssetExporter");

    private static final int ITEM_ICON_DIMENSION = 512;
    private static final int ENTITY_ICON_DIMENSION = 512;
    private static final int BLOCK_RENDER_SCALE = 32;
    private static final float ENTITY_Y_ROTATION = -135.0f;
    private static final float ENTITY_RENDER_PADDING = 1.5f;

    private RenderedAssetExporter() {
    }

    public static void exportAll() throws IOException {
        var sw = Stopwatch.createStarted();
        Files.createDirectories(ExportPaths.renderedBlocksBase());
        Files.createDirectories(ExportPaths.renderedItemsBase());
        Files.createDirectories(ExportPaths.renderedEntitiesBase());

        int entities = exportEntities();
        int blocks = exportBlocks();
        int items = exportItems();

        LOGGER.info("Exported {} block renders, {} item renders and {} entity renders in {}", blocks, items, entities,
                DurationFormatUtils.formatDurationHMS(sw.elapsed().toMillis()));
    }

    private static int exportBlocks() {
        int exported = 0;
        LOGGER.info("Exporting rendered block images...");

        for (Block block : BuiltInRegistries.BLOCK) {
            if (block == Blocks.AIR) {
                continue;
            }

            var id = BuiltInRegistries.BLOCK.getKey(block);
            if (id == null) {
                continue;
            }

            try {
                var state = block.defaultBlockState();
                var cameraSettings = new CameraSettings();
                cameraSettings.setZoom(1.0f);
                cameraSettings.setPerspectivePreset(PerspectivePreset.ISOMETRIC_NORTH_EAST);

                var level = new GuidebookLevel();
                var scene = new GuidebookScene(level, cameraSettings);
                level.setBlockAndUpdate(BlockPos.ZERO, state);
                scene.centerScene();

                var lytScene = new LytGuidebookScene(ExtensionCollection.empty());
                lytScene.setScene(scene);
                lytScene.setInteractive(false);

                var sprites = RenderSpriteCollector.getSprites(scene);
                writeRenderedScene(idToPath(ExportPaths.renderedBlocksBase(), id), lytScene, scene, sprites);
                exported++;
            } catch (Throwable e) {
                LOGGER.warn("Failed to export block render for {}", id, e);
            }
        }

        return exported;
    }

    private static int exportItems() {
        int exported = 0;
        var client = Minecraft.getInstance();
        LOGGER.info("Exporting rendered item images...");

        try (var renderer = new OffScreenRenderer(ITEM_ICON_DIMENSION, ITEM_ICON_DIMENSION)) {
            var guiGraphics = new GuiGraphics(client, client.renderBuffers().bufferSource());
            renderer.setupItemRendering();

            for (Item item : BuiltInRegistries.ITEM) {
                var id = BuiltInRegistries.ITEM.getKey(item);
                if (id == null) {
                    continue;
                }

                var stack = item.getDefaultInstance();
                if (stack.isEmpty()) {
                    continue;
                }

                try {
                    var itemModel = client.getItemRenderer().getModel(stack, null, null, 0);
                    var sprites = guessSprites(Set.of(itemModel));
                    writeRenderedIcon(renderer, idToPath(ExportPaths.renderedItemsBase(), id), () -> {
                        guiGraphics.renderItem(stack, 0, 0);
                        guiGraphics.renderItemDecorations(client.font, stack, 0, 0, "");
                    }, sprites, true);
                    exported++;
                } catch (Throwable e) {
                    LOGGER.warn("Failed to export item render for {}", id, e);
                }
            }
        }

        return exported;
    }

    private static int exportEntities() {
        int exported = 0;
        LOGGER.info("Exporting rendered entity images...");

        for (EntityType<?> entityType : BuiltInRegistries.ENTITY_TYPE) {
            var id = BuiltInRegistries.ENTITY_TYPE.getKey(entityType);
            if (id == null) {
                continue;
            }

            try {
                var level = new GuidebookLevel();
                var entity = entityType.create(level);
                if (entity == null) {
                    LOGGER.debug("Skipping entity render for {} because it cannot be created client-side", id);
                    continue;
                }

                entity.setPos(0.5, 0, 0.5);
                entity.setYRot(ENTITY_Y_ROTATION);
                entity.setXRot(0);
                entity.setOldPosAndRot();
                entity.setYHeadRot(entity.getYRot());
                entity.setYBodyRot(entity.getYRot());
                level.addEntity(entity);

                try {
                    entity.tick();
                } catch (Throwable e) {
                    LOGGER.debug("Entity {} failed its preview tick; attempting to render its initial state", id, e);
                }

                var cameraSettings = new CameraSettings();
                cameraSettings.setZoom(1.0f);
                cameraSettings.setPerspectivePreset(PerspectivePreset.ISOMETRIC_NORTH_EAST);

                prepareEntityRendering(level, entity);

                var scene = new GuidebookScene(level, cameraSettings);
                scene.centerScene();

                var lytScene = new LytGuidebookScene(ExtensionCollection.empty());
                lytScene.setScene(scene);
                lytScene.setInteractive(false);

                writeRenderedEntity(idToPath(ExportPaths.renderedEntitiesBase(), id), lytScene, scene);
                exported++;
            } catch (Throwable e) {
                LOGGER.warn("Failed to export entity render for {}", id, e);
            }
        }

        return exported;
    }

    private static void prepareEntityRendering(GuidebookLevel level, Entity entity) {
        // Entity renderers assume the vanilla dispatcher has a prepared camera. The normal world renderer does this
        // every frame, but our headless doc export happens from a tick hook before any real level render exists.
        var camera = new Camera();
        camera.setup(level, entity, false, false, 0.0f);
        Minecraft.getInstance().getEntityRenderDispatcher().prepare(level, camera, entity);
    }

    private static void writeRenderedScene(Path basePath,
            LytGuidebookScene lytScene,
            GuidebookScene scene,
            Collection<TextureAtlasSprite> sprites) throws IOException {
        var prefSize = getPreferredSceneSize(lytScene);
        int width = Math.max(1, prefSize.width() * BLOCK_RENDER_SCALE);
        int height = Math.max(1, prefSize.height() * BLOCK_RENDER_SCALE);

        renderSceneToIcon(basePath, scene, sprites, prefSize, width, height);
    }

    private static void writeRenderedEntity(Path basePath,
            LytGuidebookScene lytScene,
            GuidebookScene scene) throws IOException {
        var prefSize = getPaddedSquareSceneSize(lytScene, ENTITY_RENDER_PADDING);
        renderSceneToIcon(basePath, scene, Collections.emptyList(), prefSize, ENTITY_ICON_DIMENSION,
                ENTITY_ICON_DIMENSION);
    }

    private static LytSize getPreferredSceneSize(LytGuidebookScene lytScene) {
        var measuredSize = lytScene.getPreferredSize();
        if (measuredSize.width() <= 0 || measuredSize.height() <= 0) {
            // Some technical/invisible entries do not contribute layout size, but the exporter should still emit an
            // entry for every loaded registry entry. Use the normal one-block footprint and let rendering produce
            // transparency if there is no visible geometry.
            return new LytSize(16, 16);
        }
        return measuredSize;
    }

    private static LytSize getPaddedSquareSceneSize(LytGuidebookScene lytScene, float padding) {
        var measuredSize = getPreferredSceneSize(lytScene);
        var side = Math.max(measuredSize.width(), measuredSize.height());
        side = Math.max(16, (int) Math.ceil(side * padding));
        return new LytSize(side, side);
    }

    private static void renderSceneToIcon(Path basePath,
            GuidebookScene scene,
            Collection<TextureAtlasSprite> sprites,
            LytSize prefSize,
            int width,
            int height) throws IOException {
        try (var renderer = new OffScreenRenderer(width, height)) {
            writeRenderedIcon(renderer, basePath, () -> {
                scene.getCameraSettings().setViewportSize(prefSize);
                GuidebookLevelRenderer.getInstance().render(scene.getLevel(), scene.getCameraSettings(),
                        Collections.emptyList(), LightDarkMode.LIGHT_MODE);
            }, sprites, true);
        }
    }

    private static void writeRenderedIcon(OffScreenRenderer renderer,
            Path basePath,
            Runnable renderRunnable,
            Collection<TextureAtlasSprite> sprites,
            boolean withAlpha) throws IOException {
        String extension;
        byte[] content;
        if (renderer.isAnimated(sprites)) {
            extension = ".webp";
            content = renderer.captureAsWebp(renderRunnable, sprites,
                    withAlpha ? WebPExporter.Format.LOSSLESS_ALPHA : WebPExporter.Format.LOSSLESS);
        } else {
            extension = ".png";
            content = renderer.captureAsPng(renderRunnable);
        }

        var outputPath = Path.of(basePath.toString() + extension);
        Files.createDirectories(outputPath.getParent());
        Files.write(outputPath, content);
    }

    private static Set<TextureAtlasSprite> guessSprites(Collection<BakedModel> models) {
        var result = Collections.newSetFromMap(new IdentityHashMap<TextureAtlasSprite, Boolean>());
        var randomSource = RandomSource.create(0);

        for (var model : models) {
            for (var quad : model.getQuads(null, null, randomSource)) {
                result.add(quad.getSprite());
            }
        }

        return result;
    }

    private static Path idToPath(Path base, ResourceLocation id) {
        return base.resolve(id.getNamespace()).resolve(id.getPath());
    }
}
