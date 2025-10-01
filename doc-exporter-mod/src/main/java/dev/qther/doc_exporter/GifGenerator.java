package dev.qther.doc_exporter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.*;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.metadata.IIOMetadataNode;
import javax.imageio.stream.ImageOutputStream;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;

public class GifGenerator {
    private static final Logger LOGGER = LoggerFactory.getLogger(GifGenerator.class);
    private static final int TICKS_PER_SECOND = 20; // Minecraft runs at 20 ticks per second

    public static void generateGifFromFrames(AnimatedTextureExporter.AnimationFrame[] frames, Path outputPath) throws IOException {
        LOGGER.info("Starting GIF generation with {} frames to {}", frames.length, outputPath);

        // Extract images and convert durations to milliseconds
        BufferedImage[] images = new BufferedImage[frames.length];
        int[] delays = new int[frames.length];

        for (int i = 0; i < frames.length; i++) {
            images[i] = frames[i].image;
            // Convert Minecraft ticks to milliseconds (50ms per tick = 1000ms / 20 ticks)
            long durationMs = (frames[i].durationTicks * 1000L) / TICKS_PER_SECOND;
            // Ensure minimum duration of 10ms for GIF compatibility
            delays[i] = (int) Math.max(10, durationMs);
            LOGGER.debug("Frame {} delay: {}ms ({}x{})", i, delays[i], images[i].getWidth(), images[i].getHeight());
        }

        writeGifWithImageIO(images, delays, outputPath);
    }

    private static void writeGifWithImageIO(BufferedImage[] frames, int[] delays, Path outputPath) throws IOException {
        ImageWriter writer = ImageIO.getImageWritersByFormatName("gif").next();
        ImageWriteParam writeParam = writer.getDefaultWriteParam();

        try (ImageOutputStream ios = ImageIO.createImageOutputStream(outputPath.toFile())) {
            writer.setOutput(ios);
            writer.prepareWriteSequence(null);

            for (int i = 0; i < frames.length; i++) {
                BufferedImage frame = frames[i];
                IIOMetadata metadata = writer.getDefaultImageMetadata(
                    new ImageTypeSpecifier(frame), writeParam);

                configureGIFMetadata(metadata, delays[i], i == 0);

                IIOImage image = new IIOImage(frame, null, metadata);
                writer.writeToSequence(image, writeParam);
            }

            writer.endWriteSequence();
        } finally {
            writer.dispose();
        }

        LOGGER.info("Successfully wrote GIF with ImageIO to {}", outputPath);
    }

    private static void configureGIFMetadata(IIOMetadata metadata, int delayMs, boolean isFirst) throws IOException {
        String metaFormat = metadata.getNativeMetadataFormatName();
        IIOMetadataNode root = (IIOMetadataNode) metadata.getAsTree(metaFormat);

        // Graphics Control Extension
        IIOMetadataNode graphicsControlExt = getOrCreateNode(root, "GraphicControlExtension");
        graphicsControlExt.setAttribute("disposalMethod", "none");
        graphicsControlExt.setAttribute("userInputFlag", "FALSE");
        graphicsControlExt.setAttribute("transparentColorFlag", "FALSE");
        graphicsControlExt.setAttribute("delayTime", String.valueOf(delayMs / 10)); // centiseconds
        graphicsControlExt.setAttribute("transparentColorIndex", "0");

        // Application Extension for looping (only on first frame)
        if (isFirst) {
            IIOMetadataNode appExtNode = getOrCreateNode(root, "ApplicationExtensions");
            IIOMetadataNode child = new IIOMetadataNode("ApplicationExtension");
            child.setAttribute("applicationID", "NETSCAPE");
            child.setAttribute("authenticationCode", "2.0");
            child.setUserObject(new byte[]{0x1, 0x0, 0x0}); // Loop forever
            appExtNode.appendChild(child);
        }

        metadata.setFromTree(metaFormat, root);
    }

    private static IIOMetadataNode getOrCreateNode(IIOMetadataNode root, String nodeName) {
        org.w3c.dom.NodeList nodes = root.getElementsByTagName(nodeName);
        if (nodes.getLength() > 0) {
            return (IIOMetadataNode) nodes.item(0);
        }
        IIOMetadataNode node = new IIOMetadataNode(nodeName);
        root.appendChild(node);
        return node;
    }
}