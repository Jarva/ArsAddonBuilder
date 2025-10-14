package dev.qther.doc_exporter;

import com.luciad.imageio.webp.WebPWriteParam;
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
import java.util.Locale;

/**
 * Generates animated WebP files from animation frames.
 *
 * <p>WebP is a modern image format that supports both lossy and lossless compression,
 * as well as animation with full alpha channel support. It's widely supported in modern
 * browsers and applications like Discord.
 */
public class WebPGenerator {
    private static final Logger LOGGER = LoggerFactory.getLogger(WebPGenerator.class);
    private static final int TICKS_PER_SECOND = 20; // Minecraft runs at 20 ticks per second

    public static void generateWebPFromFrames(AnimatedTextureExporter.AnimationFrame[] frames, Path outputPath) throws IOException {
        LOGGER.info("Starting WebP generation with {} frames to {}", frames.length, outputPath);

        if (frames.length == 0) {
            LOGGER.warn("Skipping WebP generation for {} because no frames were provided", outputPath);
            return;
        }

        try (ImageOutputStream output = ImageIO.createImageOutputStream(Files.newOutputStream(outputPath))) {
            // Get WebP writer
            ImageWriter writer = ImageIO.getImageWritersByMIMEType("image/webp").next();
            writer.setOutput(output);

            // Configure WebP write parameters for lossless encoding
            WebPWriteParam writeParam = new WebPWriteParam(writer.getLocale());
            writeParam.setCompressionMode(WebPWriteParam.MODE_EXPLICIT);
            writeParam.setCompressionType(writeParam.getCompressionTypes()[WebPWriteParam.LOSSLESS_COMPRESSION]);

            // Prepare to write multiple frames
            writer.prepareWriteSequence(null);

            for (int i = 0; i < frames.length; i++) {
                BufferedImage frame = frames[i].image;
                int delayMs = (frames[i].durationTicks * 1000) / TICKS_PER_SECOND;

                // Create metadata for this frame with timing information
                IIOMetadata metadata = writer.getDefaultImageMetadata(
                    ImageTypeSpecifier.createFromBufferedImageType(BufferedImage.TYPE_INT_ARGB),
                    writeParam
                );

                // Set frame delay in metadata
                String metaFormatName = metadata.getNativeMetadataFormatName();
                IIOMetadataNode root = (IIOMetadataNode) metadata.getAsTree(metaFormatName);

                // WebP uses milliseconds for frame delay
                IIOMetadataNode animNode = new IIOMetadataNode("AnimationExtension");
                animNode.setAttribute("delayTime", String.valueOf(delayMs));
                root.appendChild(animNode);

                metadata.setFromTree(metaFormatName, root);

                // Write the frame
                IIOImage iioImage = new IIOImage(frame, null, metadata);
                writer.writeToSequence(iioImage, writeParam);

                LOGGER.debug("Frame {} delay: {}ms ({}x{})", i, delayMs, frame.getWidth(), frame.getHeight());
            }

            writer.endWriteSequence();
            writer.dispose();
        }

        LOGGER.info("Successfully wrote WebP to {}", outputPath);
    }
}
