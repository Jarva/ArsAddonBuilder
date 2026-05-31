package dev.qther.doc_exporter.render;

import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexFormatElement;
import java.nio.ByteBuffer;
import java.util.Objects;
import java.util.function.IntFunction;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector2f;
import org.joml.Vector4i;

/**
 * Captured rendering data for sprite discovery.
 *
 * <p>Adapted from GuideME's site exporter.</p>
 */
record Mesh(MeshData.DrawState drawState,
        ByteBuffer vertexBuffer,
        @Nullable ByteBuffer indexBuffer,
        RenderType renderType) {

    Stream<TextureAtlasSprite> getSprites() {
        var textureManager = Minecraft.getInstance().getTextureManager();

        if (drawState.mode() != VertexFormat.Mode.QUADS) {
            return Stream.of();
        }

        var samplers = RenderTypeIntrospection.getSamplers(renderType);
        if (samplers.isEmpty()) {
            return Stream.of();
        }
        var texture = textureManager.getTexture(samplers.get(0).texture());
        if (!(texture instanceof TextureAtlas textureAtlas)) {
            return Stream.of();
        }

        var offset = 0;
        VertexFormatElement uvElement = null;
        for (var element : renderType.format().getElements()) {
            if (element.usage() == VertexFormatElement.Usage.UV && element.index() == 0
                    && element.count() == 2) {
                uvElement = element;
                break;
            }
            offset += element.byteSize();
        }

        if (uvElement == null) {
            return Stream.of();
        }

        var uvSupplier = getUvSupplier(offset, uvElement);
        var spriteFinder = new SpriteFinder(textureAtlas.getTextures(), textureAtlas);

        return streamQuadMidpoints(uvSupplier)
                .map(uvPos -> spriteFinder.find(uvPos.x, uvPos.y))
                .filter(Objects::nonNull);
    }

    private Stream<Vector2f> streamQuadMidpoints(IntFunction<Vector2f> uvSupplier) {
        return streamIndices().map(indices -> getQuadMidpoint(indices.x, indices.y, indices.z, indices.w, uvSupplier));
    }

    private Stream<Vector4i> streamIndices() {
        if (indexBuffer == null) {
            var quadCount = drawState.vertexCount() / 4;
            return IntStream.range(0, quadCount)
                    .mapToObj(quadIdx -> new Vector4i(quadIdx * 4, quadIdx * 4 + 1, quadIdx * 4 + 2, quadIdx * 4 + 3));
        } else if (drawState.indexType() == VertexFormat.IndexType.INT) {
            var quadCount = drawState.indexCount() / 4;
            return IntStream.range(0, quadCount)
                    .mapToObj(quadIdx -> new Vector4i(
                            indexBuffer.getInt(quadIdx * 4 * 4),
                            indexBuffer.getInt(quadIdx * 4 * 4 + 4),
                            indexBuffer.getInt(quadIdx * 4 * 4 + 8),
                            indexBuffer.getInt(quadIdx * 4 * 4 + 12)));
        } else if (drawState.indexType() == VertexFormat.IndexType.SHORT) {
            var quadCount = drawState.indexCount() / 4;
            return IntStream.range(0, quadCount)
                    .mapToObj(quadIdx -> new Vector4i(
                            indexBuffer.getShort(quadIdx * 4 * 2),
                            indexBuffer.getShort(quadIdx * 4 * 2 + 2),
                            indexBuffer.getShort(quadIdx * 4 * 2 + 4),
                            indexBuffer.getShort(quadIdx * 4 * 2 + 6)));
        } else {
            throw new IllegalArgumentException("Unsupported index type: " + drawState.indexType());
        }
    }

    private IntFunction<Vector2f> getUvSupplier(int offset, VertexFormatElement uvElement) {
        return idx -> getUV(idx, offset, uvElement);
    }

    private Vector2f getQuadMidpoint(int i1, int i2, int i3, int i4, IntFunction<Vector2f> uvSupplier) {
        var uv1 = uvSupplier.apply(i1);
        var uv2 = uvSupplier.apply(i2);
        var uv3 = uvSupplier.apply(i3);
        var uv4 = uvSupplier.apply(i4);
        return new Vector2f(
                (uv1.x + uv2.x + uv3.x + uv4.x) / 4f,
                (uv1.y + uv2.y + uv3.y + uv4.y) / 4f);
    }

    private Vector2f getUV(int index, int offset, VertexFormatElement uvElement) {
        var stride = drawState.format().getVertexSize();
        var dataStart = index * stride + offset;
        return new Vector2f(
                readFloat(uvElement.type(), dataStart),
                readFloat(uvElement.type(), dataStart + uvElement.type().size()));
    }

    private float readFloat(VertexFormatElement.Type type, int offset) {
        return switch (type) {
            case FLOAT -> vertexBuffer.getFloat(offset);
            case UBYTE -> ((int) vertexBuffer.get(offset)) & 0xFF;
            case BYTE -> vertexBuffer.get(offset);
            case USHORT -> ((int) vertexBuffer.getShort(offset)) & 0xFFFF;
            case SHORT -> vertexBuffer.getShort(offset);
            case UINT, INT -> vertexBuffer.getInt(offset);
        };
    }
}
