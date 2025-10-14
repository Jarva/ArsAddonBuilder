package dev.qther.doc_exporter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.*;
import javax.imageio.metadata.IIOInvalidTreeException;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.metadata.IIOMetadataNode;
import javax.imageio.stream.ImageOutputStream;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Generates animated GIF files from animation frames.
 *
 * <p>GIF is a widely supported format that works everywhere including Discord.
 * While limited to 256 colors per frame, it's sufficient for pixel art textures
 * and doesn't require any external dependencies.
 */
public class GifGenerator {
    private static final Logger LOGGER = LoggerFactory.getLogger(GifGenerator.class);
    private static final int TICKS_PER_SECOND = 20; // Minecraft runs at 20 ticks per second

    public static void generateGifFromFrames(AnimatedTextureExporter.AnimationFrame[] frames, Path outputPath) throws IOException {
        LOGGER.info("Starting GIF generation with {} frames to {}", frames.length, outputPath);

        if (frames.length == 0) {
            LOGGER.warn("Skipping GIF generation for {} because no frames were provided", outputPath);
            return;
        }

        try (ImageOutputStream output = ImageIO.createImageOutputStream(Files.newOutputStream(outputPath))) {
            ImageWriter writer = ImageIO.getImageWritersByFormatName("gif").next();
            writer.setOutput(output);

            ImageWriteParam writeParam = writer.getDefaultWriteParam();

            IIOMetadata streamMetadata = writer.getDefaultStreamMetadata(writeParam);
            configureStreamMetadata(streamMetadata);

            writer.prepareWriteSequence(streamMetadata);

            for (int i = 0; i < frames.length; i++) {
                BufferedImage frame = frames[i].image;
                int delayMs = (frames[i].durationTicks * 1000) / TICKS_PER_SECOND;

                IIOMetadata imageMetadata = writer.getDefaultImageMetadata(
                    ImageTypeSpecifier.createFromBufferedImageType(BufferedImage.TYPE_INT_ARGB),
                    writeParam
                );

                configureFrameMetadata(imageMetadata, delayMs);

                IIOImage iioImage = new IIOImage(frame, null, imageMetadata);
                writer.writeToSequence(iioImage, writeParam);

                LOGGER.debug("Frame {} delay: {}ms ({}x{})", i, delayMs, frame.getWidth(), frame.getHeight());
            }

            writer.endWriteSequence();
            writer.dispose();
        }

        LOGGER.info("Successfully wrote GIF to {}", outputPath);
    }

    private static void configureStreamMetadata(IIOMetadata metadata) throws IIOInvalidTreeException {
        String metaFormatName = metadata.getNativeMetadataFormatName();
        IIOMetadataNode root = (IIOMetadataNode) metadata.getAsTree(metaFormatName);

        // Set to loop forever
        IIOMetadataNode appExtensionsNode = getOrCreateNode(root, "ApplicationExtensions");
        IIOMetadataNode appExtensionNode = new IIOMetadataNode("ApplicationExtension");
        appExtensionNode.setAttribute("applicationID", "NETSCAPE");
        appExtensionNode.setAttribute("authenticationCode", "2.0");
        appExtensionNode.setUserObject(new byte[]{0x1, 0x0, 0x0}); // Loop forever
        appExtensionsNode.appendChild(appExtensionNode);

        metadata.setFromTree(metaFormatName, root);
    }

    private static void configureFrameMetadata(IIOMetadata metadata, int delayMs) throws IIOInvalidTreeException {
        String metaFormatName = metadata.getNativeMetadataFormatName();
        IIOMetadataNode root = (IIOMetadataNode) metadata.getAsTree(metaFormatName);

        // Set frame delay in centiseconds (GIF uses 1/100th of a second)
        int delayCentiseconds = Math.max(1, delayMs / 10);

        IIOMetadataNode graphicsControlExtensionNode = getOrCreateNode(root, "GraphicControlExtension");
        graphicsControlExtensionNode.setAttribute("disposalMethod", "restoreToBackgroundColor");
        graphicsControlExtensionNode.setAttribute("userInputFlag", "FALSE");
        graphicsControlExtensionNode.setAttribute("transparentColorFlag", "FALSE");
        graphicsControlExtensionNode.setAttribute("delayTime", String.valueOf(delayCentiseconds));
        graphicsControlExtensionNode.setAttribute("transparentColorIndex", "0");

        metadata.setFromTree(metaFormatName, root);
    }

    private static IIOMetadataNode getOrCreateNode(IIOMetadataNode parent, String nodeName) {
        for (int i = 0; i < parent.getLength(); i++) {
            if (parent.item(i).getNodeName().equalsIgnoreCase(nodeName)) {
                return (IIOMetadataNode) parent.item(i);
            }
        }
        IIOMetadataNode node = new IIOMetadataNode(nodeName);
        parent.appendChild(node);
        return node;
    }
}
