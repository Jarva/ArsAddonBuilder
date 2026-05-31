package dev.qther.doc_exporter.render;

import java.util.Map;
import java.util.function.Consumer;
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.ResourceLocation;

/**
 * Finds atlas sprites by UV coordinate.
 *
 * <p>Adapted from GuideME's site exporter.</p>
 */
final class SpriteFinder {
    private final Node root;
    private final TextureAtlas spriteAtlasTexture;

    SpriteFinder(Map<ResourceLocation, TextureAtlasSprite> sprites, TextureAtlas spriteAtlasTexture) {
        root = new Node(0.5f, 0.5f, 0.25f);
        this.spriteAtlasTexture = spriteAtlasTexture;
        sprites.values().forEach(root::add);
    }

    TextureAtlasSprite find(float u, float v) {
        return root.find(u, v);
    }

    private class Node {
        private static final float EPS = 0.00001f;

        final float midU;
        final float midV;
        final float cellRadius;
        Object lowLow = null;
        Object lowHigh = null;
        Object highLow = null;
        Object highHigh = null;

        Node(float midU, float midV, float radius) {
            this.midU = midU;
            this.midV = midV;
            cellRadius = radius;
        }

        void add(TextureAtlasSprite sprite) {
            final boolean lowU = sprite.getU0() < midU - EPS;
            final boolean highU = sprite.getU1() > midU + EPS;
            final boolean lowV = sprite.getV0() < midV - EPS;
            final boolean highV = sprite.getV1() > midV + EPS;

            if (lowU && lowV) {
                addInner(sprite, lowLow, -1, -1, q -> lowLow = q);
            }
            if (lowU && highV) {
                addInner(sprite, lowHigh, -1, 1, q -> lowHigh = q);
            }
            if (highU && lowV) {
                addInner(sprite, highLow, 1, -1, q -> highLow = q);
            }
            if (highU && highV) {
                addInner(sprite, highHigh, 1, 1, q -> highHigh = q);
            }
        }

        private void addInner(TextureAtlasSprite sprite, Object quadrant, int uStep, int vStep,
                Consumer<Object> setter) {
            if (quadrant == null) {
                setter.accept(sprite);
            } else if (quadrant instanceof Node node) {
                node.add(sprite);
            } else {
                Node n = new Node(midU + cellRadius * uStep, midV + cellRadius * vStep, cellRadius * 0.5f);
                if (quadrant instanceof TextureAtlasSprite quadrantSprite) {
                    n.add(quadrantSprite);
                }
                n.add(sprite);
                setter.accept(n);
            }
        }

        private TextureAtlasSprite find(float u, float v) {
            if (u < midU) {
                return v < midV ? findInner(lowLow, u, v) : findInner(lowHigh, u, v);
            } else {
                return v < midV ? findInner(highLow, u, v) : findInner(highHigh, u, v);
            }
        }

        private TextureAtlasSprite findInner(Object quadrant, float u, float v) {
            if (quadrant instanceof TextureAtlasSprite sprite) {
                return sprite;
            } else if (quadrant instanceof Node node) {
                return node.find(u, v);
            } else {
                return spriteAtlasTexture.getSprite(MissingTextureAtlasSprite.getLocation());
            }
        }
    }
}
