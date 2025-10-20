package dev.qther.doc_exporter;

import com.google.common.base.Stopwatch;
import org.apache.commons.lang3.time.DurationFormatUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.*;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.metadata.IIOMetadataNode;
import javax.imageio.stream.FileImageOutputStream;
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
        if (frames.length == 0) {
            LOGGER.warn("Skipping GIF generation for {} because no frames were provided", outputPath);
            return;
        }

        // Create temporary file to write GIF without loop extension first
        Path tempPath = outputPath.getParent().resolve(outputPath.getFileName() + ".tmp");

        Stopwatch sw = Stopwatch.createStarted();
        try (FileImageOutputStream output = new FileImageOutputStream(tempPath.toFile())) {
            ImageWriter writer = ImageIO.getImageWritersByFormatName("gif").next();
            writer.setOutput(output);

            ImageWriteParam writeParam = writer.getDefaultWriteParam();
            writer.prepareWriteSequence(null);

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
                    IIOMetadataNode root = (IIOMetadataNode) imageMetadata.getAsTree(metaFormat);

                    IIOMetadataNode graphicControlExt = new IIOMetadataNode("GraphicControlExtension");
                    graphicControlExt.setAttribute("disposalMethod", "none");
                    graphicControlExt.setAttribute("userInputFlag", "FALSE");
                    graphicControlExt.setAttribute("transparentColorFlag", "FALSE");
                    graphicControlExt.setAttribute("delayTime", String.valueOf(delayCentiseconds));
                    graphicControlExt.setAttribute("transparentColorIndex", "0");

                    root.appendChild(graphicControlExt);
                    imageMetadata.setFromTree(metaFormat, root);
                } catch (Exception e) {
                    LOGGER.warn("Could not configure frame {} timing metadata: {}", i, e.getMessage());
                }

                IIOImage iioImage = new IIOImage(frame, null, imageMetadata);
                writer.writeToSequence(iioImage, writeParam);
            }

            writer.endWriteSequence();
            writer.dispose();
            output.flush();
        }

        // Manually insert NETSCAPE 2.0 loop extension
        insertLoopExtension(tempPath, outputPath);

        // Delete temporary file
        Files.deleteIfExists(tempPath);

        LOGGER.info("Successfully wrote GIF with infinite loop to {} in {}", outputPath, DurationFormatUtils.formatDurationHMS(sw.elapsed().toMillis()));
    }

    /**
     * Inserts the NETSCAPE 2.0 application extension for infinite looping into a GIF file.
     * This is done by reading the GIF, finding the position after the Logical Screen Descriptor,
     * and inserting the loop extension block.
     */
    private static void insertLoopExtension(Path inputPath, Path outputPath) throws IOException {
        byte[] gifData = Files.readAllBytes(inputPath);

        // GIF header is 6 bytes: "GIF89a"
        // Logical Screen Descriptor is 7 bytes
        // After that, there may be a Global Color Table
        // We need to insert the NETSCAPE extension after all that

        int pos = 6; // Skip "GIF89a"

        // Read Logical Screen Descriptor
        int packed = gifData[pos + 4] & 0xFF;
        boolean hasGlobalColorTable = (packed & 0x80) != 0;
        int globalColorTableSize = 0;

        if (hasGlobalColorTable) {
            int sizeCode = packed & 0x07;
            globalColorTableSize = 3 * (1 << (sizeCode + 1));
        }

        pos += 7; // Skip Logical Screen Descriptor
        pos += globalColorTableSize; // Skip Global Color Table if present

        // Create NETSCAPE 2.0 extension block for infinite loop
        // Format: 0x21 0xFF 0x0B "NETSCAPE" "2.0" 0x03 0x01 [loop count low] [loop count high] 0x00
        byte[] loopExtension = new byte[]{
            0x21, // Extension Introducer
            (byte) 0xFF, // Application Extension Label
            0x0B, // Block Size (11 bytes)
            'N', 'E', 'T', 'S', 'C', 'A', 'P', 'E', // Application Identifier
            '2', '.', '0', // Application Authentication Code
            0x03, // Sub-block Data Size
            0x01, // Sub-block ID
            0x00, 0x00, // Loop count: 0 = infinite
            0x00  // Block Terminator
        };

        // Write output: header + loop extension + rest of data
        try (var out = Files.newOutputStream(outputPath)) {
            out.write(gifData, 0, pos); // Write everything up to insertion point
            out.write(loopExtension); // Insert loop extension
            out.write(gifData, pos, gifData.length - pos); // Write rest of GIF
            out.flush();
        }
    }
}
