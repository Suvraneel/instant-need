package com.b2b.instantneed.common.storage;

import lombok.extern.slf4j.Slf4j;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Iterator;
import java.util.Optional;

/**
 * Downscales uploaded images to a small JPEG suitable for list/thumbnail views.
 * Full-resolution originals (up to 5 MB) are wasteful to ship to mobile grid
 * cards that render at ~150dp — this produces a ~480px-max-side, ~80%-quality
 * JPEG that mobile list views request instead.
 *
 * <p>Not every source can be decoded by the JDK's built-in {@link ImageIO}
 * readers (notably WebP has no bundled plugin) — callers must treat an empty
 * {@link Optional} as "no thumbnail available" and fall back to the original.</p>
 */
@Slf4j
public final class ImageThumbnailer {

    static {
        // Server runs headless (no display) — required before any java.awt.* use.
        System.setProperty("java.awt.headless", "true");
    }

    private static final int MAX_DIMENSION = 480;
    private static final float JPEG_QUALITY = 0.8f;

    private ImageThumbnailer() {}

    public static Optional<byte[]> resize(byte[] original, String contentType) {
        try {
            BufferedImage source = ImageIO.read(new ByteArrayInputStream(original));
            if (source == null) {
                log.warn("[THUMBNAIL] No ImageIO reader for content type '{}' — skipping thumbnail", contentType);
                return Optional.empty();
            }

            int width = source.getWidth();
            int height = source.getHeight();
            double scale = Math.min(1.0, MAX_DIMENSION / (double) Math.max(width, height));
            int targetWidth = Math.max(1, (int) Math.round(width * scale));
            int targetHeight = Math.max(1, (int) Math.round(height * scale));

            BufferedImage scaled = new BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = scaled.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g.setColor(Color.WHITE); // flatten transparency (PNG) onto white before JPEG encode
            g.fillRect(0, 0, targetWidth, targetHeight);
            g.drawImage(source, 0, 0, targetWidth, targetHeight, null);
            g.dispose();

            return Optional.of(encodeJpeg(scaled));
        } catch (IOException e) {
            log.warn("[THUMBNAIL] Failed to generate thumbnail: {}", e.getMessage());
            return Optional.empty();
        }
    }

    private static byte[] encodeJpeg(BufferedImage image) throws IOException {
        Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpg");
        if (!writers.hasNext()) {
            throw new IOException("No JPEG writer available");
        }
        ImageWriter writer = writers.next();
        try {
            ImageWriteParam params = writer.getDefaultWriteParam();
            params.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            params.setCompressionQuality(JPEG_QUALITY);

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            try (ImageOutputStream ios = ImageIO.createImageOutputStream(out)) {
                writer.setOutput(ios);
                writer.write(null, new IIOImage(image, null, null), params);
            }
            return out.toByteArray();
        } finally {
            writer.dispose();
        }
    }
}
