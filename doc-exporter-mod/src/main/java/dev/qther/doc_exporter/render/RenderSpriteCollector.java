package dev.qther.doc_exporter.render;

import guideme.scene.GuidebookLevelRenderer;
import guideme.scene.GuidebookScene;
import java.util.Set;
import java.util.stream.Collectors;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;

/**
 * Discovers sprites referenced by a rendered GuideME scene.
 */
public final class RenderSpriteCollector {
    private RenderSpriteCollector() {
    }

    public static Set<TextureAtlasSprite> getSprites(GuidebookScene scene) {
        var bufferSource = new MeshBuildingBufferSource();
        GuidebookLevelRenderer.getInstance().renderContent(scene.getLevel(), bufferSource);
        return bufferSource.getMeshes().stream()
                .flatMap(Mesh::getSprites)
                .collect(Collectors.toSet());
    }
}
