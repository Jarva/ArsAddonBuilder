package dev.qther.doc_exporter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.*;
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

            // Configure stream metadata for looping
            IIOMetadata streamMetadata = writer.getDefaultStreamMetadata(writeParam);
            if (streamMetadata != null) {
                try {
                    String metaFormat = "javax_imageio_gif_stream_1.0";
                    IIOMetadataNode root = new IIOMetadataNode(metaFormat);

                    IIOMetadataNode appExtensions = new IIOMetadataNode("ApplicationExtensions");
                    IIOMetadataNode appExtension = new IIOMetadataNode("ApplicationExtension");

                    appExtension.setAttribute("applicationID", "NETSCAPE");
                    appExtension.setAttribute("authenticationCode", "2.0");

                    // Loop count: 0 = infinite loop (little-endian: low byte, high byte)
                    appExtension.setUserObject(new byte[]{0x1, 0x0, 0x0});

                    appExtensions.appendChild(appExtension);
                    root.appendChild(appExtensions);

                    streamMetadata.mergeTree(metaFormat, root);
                    LOGGER.info("Configured GIF to loop infinitely");
                } catch (Exception e) {
                    LOGGER.error("Failed to configure GIF looping metadata: {}", e.getMessage(), e);
                }
            } else {
                LOGGER.warn("Stream metadata is null, GIF may not loop");
            }

            writer.prepareWriteSequence(streamMetadata);

            for (int i = 0; i < frames.length; i++) {
                BufferedImage frame = frames[i].image;
                int delayMs = (frames[i].durationTicks * 1000) / TICKS_PER_SECOND;
                int delayCentiseconds = Math.max(2, delayMs / 10);

                IIOMetadata imageMetadata = writer.getDefaultImageMetadata(
                    ImageTypeSpecifier.createFromBufferedImageType(BufferedImage.TYPE_INT_ARGB),
                    writeParam
                );

                try {
                    String metaFormat = "javax_imageio_gif_image_1.0";
                    IIOMetadataNode root = new IIOMetadataNode(metaFormat);

                    IIOMetadataNode graphicControlExt = new IIOMetadataNode("GraphicControlExtension");
                    graphicControlExt.setAttribute("disposalMethod", "none");
                    graphicControlExt.setAttribute("userInputFlag", "FALSE");
                    graphicControlExt.setAttribute("transparentColorFlag", "FALSE");
                    graphicControlExt.setAttribute("delayTime", String.valueOf(delayCentiseconds));
                    graphicControlExt.setAttribute("transparentColorIndex", "0");

                    root.appendChild(graphicControlExt);
                    imageMetadata.mergeTree(metaFormat, root);
                } catch (Exception e) {
                    LOGGER.warn("Could not configure frame {} timing metadata: {}", i, e.getMessage());
                }

                IIOImage iioImage = new IIOImage(frame, null, imageMetadata);
                writer.writeToSequence(iioImage, writeParam);

                LOGGER.debug("Frame {} delay: {}ms ({}x{})", i, delayMs, frame.getWidth(), frame.getHeight());
            }

            writer.endWriteSequence();
            writer.dispose();
        }

        LOGGER.info("Successfully wrote GIF to {}", outputPath);
    }
}
