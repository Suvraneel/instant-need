package com.b2b.instantneed.common.storage;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;

import java.io.IOException;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.*;

@ExtendWith(MockitoExtension.class)
class S3StorageServiceTest {

    private static final String BUCKET   = "test-bucket";
    private static final String BASE_URL = "https://cdn.example.com";

    @Mock S3Client s3Client;

    S3StorageService service;

    @BeforeEach
    void setUp() {
        service = new S3StorageService(s3Client, BUCKET, BASE_URL);
        // lenient: validation-failure tests throw before reaching putObject
        lenient().when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenReturn(PutObjectResponse.builder().build());
    }

    // ── store — success ────────────────────────────────────────────────────────

    @Test
    void store_validJpeg_returnsCloudFrontUrl() throws IOException {
        MockMultipartFile file = new MockMultipartFile(
                "file", "photo.jpg", "image/jpeg", new byte[1024]);

        String url = service.store(file, "products/uuid-1");

        assertThat(url).startsWith("https://cdn.example.com/products/uuid-1/");
        assertThat(url).endsWith(".jpg");
    }

    @Test
    void store_validPng_callsPutObjectWithCorrectBucketAndKey() throws IOException {
        MockMultipartFile file = new MockMultipartFile(
                "file", "image.png", "image/png", new byte[512]);

        String url = service.store(file, "products/uuid-2");

        ArgumentCaptor<PutObjectRequest> captor = ArgumentCaptor.forClass(PutObjectRequest.class);
        verify(s3Client).putObject(captor.capture(), any(RequestBody.class));

        PutObjectRequest req = captor.getValue();
        assertThat(req.bucket()).isEqualTo(BUCKET);
        assertThat(req.key()).startsWith("products/uuid-2/");
        assertThat(req.key()).endsWith(".png");
        assertThat(req.contentType()).isEqualTo("image/png");

        // URL must match the key
        String key = req.key();
        assertThat(url).isEqualTo(BASE_URL + "/" + key);
    }

    @Test
    void store_baseUrlWithTrailingSlash_normalised() throws IOException {
        S3StorageService svc = new S3StorageService(s3Client, BUCKET, "https://cdn.example.com/");
        MockMultipartFile file = new MockMultipartFile(
                "file", "img.webp", "image/webp", new byte[256]);

        String url = svc.store(file, "products/x");

        // Should not have double slash
        assertThat(url).doesNotContain("//products");
    }

    // ── store — validation failures ────────────────────────────────────────────

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
                "file", "doc.pdf", "application/pdf", new byte[100]);

        assertThatThrownBy(() -> service.store(file, "products/x"))
                .isInstanceOf(StorageException.class)
                .hasMessageContaining("Unsupported");
    }

    // ── delete ─────────────────────────────────────────────────────────────────

    @Test
    void delete_validUrl_callsDeleteObjectWithCorrectKey() {
        given(s3Client.deleteObject(any(DeleteObjectRequest.class)))
                .willReturn(DeleteObjectResponse.builder().build());

        service.delete("https://cdn.example.com/products/uuid-1/file.jpg");

        ArgumentCaptor<DeleteObjectRequest> captor = ArgumentCaptor.forClass(DeleteObjectRequest.class);
        verify(s3Client).deleteObject(captor.capture());

        DeleteObjectRequest req = captor.getValue();
        assertThat(req.bucket()).isEqualTo(BUCKET);
        assertThat(req.key()).isEqualTo("products/uuid-1/file.jpg");
    }

    @Test
    void delete_s3Throws_doesNotPropagate() {
        given(s3Client.deleteObject(any(DeleteObjectRequest.class)))
                .willThrow(new RuntimeException("S3 unavailable"));

        assertThatCode(() -> service.delete("https://cdn.example.com/products/x/file.jpg"))
                .doesNotThrowAnyException();
    }
}
