package dev.qther.doc_exporter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.zip.CRC32;

/**
 * Generates APNG (Animated PNG) files from animation frames.
 *
 * <p>APNG is a PNG extension that supports animation by adding special chunks:
 * <ul>
 *   <li>acTL - Animation Control chunk (number of frames and loops)</li>
 *   <li>fcTL - Frame Control chunk (frame timing and dimensions)</li>
 *   <li>fdAT - Frame Data chunk (frame image data)</li>
 * </ul>
 */
public class ApngGenerator {
    private static final Logger LOGGER = LoggerFactory.getLogger(ApngGenerator.class);
    private static final int TICKS_PER_SECOND = 20; // Minecraft runs at 20 ticks per second
    public static void generateApngFromFrames(AnimatedTextureExporter.AnimationFrame[] frames, Path outputPath, boolean interpolated) throws IOException {
        LOGGER.info("Starting APNG generation with {} frames to {} (interpolated: {})", frames.length, outputPath, interpolated);

        if (frames.length == 0) {
            LOGGER.warn("Skipping APNG generation for {} because no frames were provided", outputPath);
            return;
        }

        try (DataOutputStream out = new DataOutputStream(Files.newOutputStream(outputPath))) {
            // Write PNG signature
            out.write(new byte[]{(byte)137, 80, 78, 71, 13, 10, 26, 10});

            // Get first frame to determine dimensions
            BufferedImage firstFrame = frames[0].image;
            int width = firstFrame.getWidth();
            int height = firstFrame.getHeight();

            // Write IHDR chunk (PNG header)
            writeIHDR(out, width, height);

            // Write acTL chunk (animation control) - must come before IDAT
            writeActL(out, frames.length, 0); // 0 = infinite loop

            int sequenceNumber = 0;

            int[] delayNumerators = new int[frames.length];
            int[] delayDenominators = new int[frames.length];
            computeFrameDelays(frames, interpolated, delayNumerators, delayDenominators);

            // Write first frame
            writeFctl(out, sequenceNumber++, width, height, 0, 0, delayNumerators[0], delayDenominators[0]);
            writeIDAT(out, firstFrame);

            // Write subsequent frames as fdAT
            for (int i = 1; i < frames.length; i++) {
                writeFctl(out, sequenceNumber++, width, height, 0, 0, delayNumerators[i], delayDenominators[i]);
                writeFdAT(out, sequenceNumber++, frames[i].image);

                double delayMs = (delayNumerators[i] * 1000.0) / delayDenominators[i];
                LOGGER.debug("Frame {} delay: {}ms ({}x{})", i, String.format(Locale.ROOT, "%.2f", delayMs), width, height);
            }

            // Write IEND chunk
            writeChunk(out, "IEND", new byte[0]);
        }

        LOGGER.info("Successfully wrote APNG to {}", outputPath);
    }

    private static void writeIHDR(DataOutputStream out, int width, int height) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DataOutputStream data = new DataOutputStream(baos);

        data.writeInt(width);
        data.writeInt(height);
        data.writeByte(8);  // bit depth
        data.writeByte(6);  // color type (RGBA)
        data.writeByte(0);  // compression method
        data.writeByte(0);  // filter method
        data.writeByte(0);  // interlace method

        writeChunk(out, "IHDR", baos.toByteArray());
    }

    private static void writeActL(DataOutputStream out, int numFrames, int numPlays) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DataOutputStream data = new DataOutputStream(baos);

        data.writeInt(numFrames);
        data.writeInt(numPlays);

        writeChunk(out, "acTL", baos.toByteArray());
    }

    private static void writeFctl(DataOutputStream out, int sequenceNumber, int width, int height,
                                   int xOffset, int yOffset, int delayNumerator, int delayDenominator) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DataOutputStream data = new DataOutputStream(baos);

        data.writeInt(sequenceNumber);
        data.writeInt(width);
        data.writeInt(height);
        data.writeInt(xOffset);
        data.writeInt(yOffset);
        data.writeShort(delayNumerator & 0xFFFF);  // delay numerator (ticks-based)
        data.writeShort(delayDenominator & 0xFFFF);      // delay denominator
        data.writeByte(0);          // dispose_op (APNG_DISPOSE_OP_NONE)
        data.writeByte(0);          // blend_op (APNG_BLEND_OP_SOURCE)

        writeChunk(out, "fcTL", baos.toByteArray());
    }

    private static void writeIDAT(DataOutputStream out, BufferedImage image) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(image, "PNG", baos);

        // Extract IDAT chunks from the PNG data
        byte[] pngData = baos.toByteArray();
        int offset = 8; // Skip PNG signature

        while (offset < pngData.length) {
            int length = readInt(pngData, offset);
            offset += 4;

            String type = new String(pngData, offset, 4);
            offset += 4;

            if ("IDAT".equals(type)) {
                byte[] chunkData = new byte[length];
                System.arraycopy(pngData, offset, chunkData, 0, length);
                writeChunk(out, "IDAT", chunkData);
            }

            offset += length + 4; // Skip data and CRC

            if ("IEND".equals(type)) {
                break;
            }
        }
    }

    private static void writeFdAT(DataOutputStream out, int sequenceNumber, BufferedImage image) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(image, "PNG", baos);

        // Extract IDAT chunks from the PNG data and convert to fdAT
        byte[] pngData = baos.toByteArray();
        int offset = 8; // Skip PNG signature

        ByteArrayOutputStream fdatData = new ByteArrayOutputStream();
        DataOutputStream fdatOut = new DataOutputStream(fdatData);
        fdatOut.writeInt(sequenceNumber);

        while (offset < pngData.length) {
            int length = readInt(pngData, offset);
            offset += 4;

            String type = new String(pngData, offset, 4);
            offset += 4;

            if ("IDAT".equals(type)) {
                fdatData.write(pngData, offset, length);
            }

            offset += length + 4; // Skip data and CRC

            if ("IEND".equals(type)) {
                break;
            }
        }

        writeChunk(out, "fdAT", fdatData.toByteArray());
    }

    private static int readInt(byte[] data, int offset) {
        return ((data[offset] & 0xFF) << 24) |
               ((data[offset + 1] & 0xFF) << 16) |
               ((data[offset + 2] & 0xFF) << 8) |
               (data[offset + 3] & 0xFF);
    }

    private static void writeChunk(DataOutputStream out, String type, byte[] data) throws IOException {
        out.writeInt(data.length);
        out.writeBytes(type);
        out.write(data);

        // Calculate and write CRC
        CRC32 crc = new CRC32();
        crc.update(type.getBytes());
        crc.update(data);
        out.writeInt((int) crc.getValue());
    }

    private static void computeFrameDelays(AnimatedTextureExporter.AnimationFrame[] frames, boolean interpolated,
                                           int[] delayNumerators, int[] delayDenominators) {
        for (int i = 0; i < frames.length; i++) {
            int ticks = normalizeTickDuration(frames[i].durationTicks);
            delayNumerators[i] = ticks;
            delayDenominators[i] = TICKS_PER_SECOND;
        }
    }

    private static int normalizeTickDuration(int ticks) {
        int sanitized = Math.max(1, ticks);
        return Math.min(0xFFFF, sanitized);
    }
}
