package dev.qther.doc_exporter.render;

import java.util.List;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Extracts texture sampler information from a render type for sprite discovery.
 *
 * <p>Adapted from GuideME's site exporter.</p>
 */
final class RenderTypeIntrospection {
    private static final Logger LOG = LoggerFactory.getLogger(RenderTypeIntrospection.class);

    private RenderTypeIntrospection() {
    }

    static List<Sampler> getSamplers(RenderType type) {
        if (!(type instanceof RenderType.CompositeRenderType compositeRenderType)) {
            return List.of();
        }

        var state = compositeRenderType.state();
        if (state.textureState instanceof RenderStateShard.TextureStateShard textureShard) {
            if (textureShard.texture.isPresent()) {
                var texture = textureShard.texture.get();
                return List.of(new Sampler(texture, textureShard.blur, textureShard.mipmap));
            } else {
                LOG.warn("Render type {} is using dynamic texture", type);
            }
        } else if (state.textureState != RenderStateShard.NO_TEXTURE) {
            LOG.warn("Cannot handle texturing of render-type {}", type);
        }

        return List.of();
    }

    record Sampler(ResourceLocation texture, boolean blur, boolean mipmap) {
    }
}
