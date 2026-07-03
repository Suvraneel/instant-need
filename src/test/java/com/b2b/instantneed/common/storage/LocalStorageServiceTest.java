package com.b2b.instantneed.common.storage;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.*;

class LocalStorageServiceTest {

    @TempDir Path tempDir;

    LocalStorageService service;

    @BeforeEach
    void setUp() {
        service = new LocalStorageService(tempDir, "http://localhost:8080/uploads");
    }

    // ── store — success cases ────────────────���────────────────────────────────

    @Test
    void store_validJpeg_returnsPublicUrl() throws IOException {
        MockMultipartFile file = new MockMultipartFile(
                "file", "photo.jpg", "image/jpeg", new byte[1024]);

        String url = service.store(file, "products/uuid-1");

        assertThat(url).startsWith("http://localhost:8080/uploads/products/uuid-1/");
        assertThat(url).endsWith(".jpg");
    }

    @Test
    void store_validPng_createsFileOnDisk() throws IOException {
        MockMultipartFile file = new MockMultipartFile(
                "file", "image.png", "image/png", new byte[512]);

        String url = service.store(file, "products/uuid-2");

        // Derive filename from URL and verify the file exists
        String filename = url.substring(url.lastIndexOf('/') + 1);
        Path stored = tempDir.resolve("products/uuid-2/" + filename);
        assertThat(Files.exists(stored)).isTrue();
        assertThat(Files.size(stored)).isEqualTo(512);
    }

    @Test
    void store_createsSubdirIfMissing() throws IOException {
        MockMultipartFile file = new MockMultipartFile(
                "file", "img.webp", "image/webp", new byte[256]);

        service.store(file, "products/brand-new-dir");

        assertThat(Files.isDirectory(tempDir.resolve("products/brand-new-dir"))).isTrue();
    }

    // ── store — validation failures ──────────────────────────────────────────

    @Test
    void store_emptyFile_throwsStorageException() {
        MockMultipartFile empty = new MockMultipartFile(
                "file", "empty.jpg", "image/jpeg", new byte[0]);

        assertThatThrownBy(() -> service.store(empty, "products/x"))
                .isInstanceOf(StorageException.class)
                .hasMessageContaining("empty");
    }

    @Test
    void store_fileTooLarge_throwsStorageException() {
        byte[] bigFile = new byte[6 * 1024 * 1024]; // 6 MB
        MockMultipartFile file = new MockMultipartFile(
                "file", "big.jpg", "image/jpeg", bigFile);

        assertThatThrownBy(() -> service.store(file, "products/x"))
                .isInstanceOf(StorageException.class)
                .hasMessageContaining("5 MB");
    }

    @Test
    void store_unsupportedContentType_throwsStorageException() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "script.exe", "application/octet-stream", new byte[100]);

        assertThatThrownBy(() -> service.store(file, "products/x"))
                .isInstanceOf(StorageException.class)
                .hasMessageContaining("Unsupported");
    }

    @Test
    void store_nullContentType_throwsStorageException() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "photo.jpg", null, new byte[100]);

        assertThatThrownBy(() -> service.store(file, "products/x"))
                .isInstanceOf(StorageException.class);
    }

    // ── delete ─────────────────��──────────────────────────────────────────────

    @Test
    void delete_existingFile_removesItFromDisk() throws IOException {
        MockMultipartFile file = new MockMultipartFile(
                "file", "del.png", "image/png", new byte[128]);
        String url = service.store(file, "products/uuid-del");

        service.delete(url);

        String filename = url.substring(url.lastIndexOf('/') + 1);
        Path stored = tempDir.resolve("products/uuid-del/" + filename);
        assertThat(Files.exists(stored)).isFalse();
    }

    @Test
    void delete_nonExistentFile_doesNotThrow() {
        assertThatCode(() -> service.delete("http://localhost:8080/uploads/products/x/missing.jpg"))
                .doesNotThrowAnyException();
    }

    // ── storeWithThumbnail ────────────────────────────────────────────────────

    @Test
    void storeWithThumbnail_realImage_generatesSmallerThumbnail() throws IOException {
        byte[] largeJpeg = renderJpeg(1200, 900);
        MockMultipartFile file = new MockMultipartFile(
                "file", "large.jpg", "image/jpeg", largeJpeg);

        StorageService.StoredImage stored = service.storeWithThumbnail(file, "products/uuid-thumb");

        assertThat(stored.url()).startsWith("http://localhost:8080/uploads/products/uuid-thumb/");
        assertThat(stored.thumbnailUrl()).isNotNull();
        assertThat(stored.thumbnailUrl()).endsWith("_thumb.jpg");

        Path thumbFile = tempDir.resolve(
                "products/uuid-thumb/" + stored.thumbnailUrl().substring(stored.thumbnailUrl().lastIndexOf('/') + 1));
        assertThat(Files.exists(thumbFile)).isTrue();
        assertThat(Files.size(thumbFile)).isLessThan(largeJpeg.length);

        BufferedImage decoded = ImageIO.read(thumbFile.toFile());
        assertThat(Math.max(decoded.getWidth(), decoded.getHeight())).isLessThanOrEqualTo(480);
    }

    @Test
    void storeWithThumbnail_undecodableBytes_fallsBackToNullThumbnail() throws IOException {
        // Not a real image — ImageIO can't decode it, so no thumbnail should be produced,
        // but the original must still be stored successfully.
        MockMultipartFile file = new MockMultipartFile(
                "file", "not-an-image.jpg", "image/jpeg", new byte[1024]);

        StorageService.StoredImage stored = service.storeWithThumbnail(file, "products/uuid-bad");

        assertThat(stored.url()).isNotNull();
        assertThat(stored.thumbnailUrl()).isNull();
    }

    private static byte[] renderJpeg(int width, int height) throws IOException {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        var g = image.createGraphics();
        g.fillRect(0, 0, width, height);
        g.dispose();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "jpg", out);
        return out.toByteArray();
    }
}
