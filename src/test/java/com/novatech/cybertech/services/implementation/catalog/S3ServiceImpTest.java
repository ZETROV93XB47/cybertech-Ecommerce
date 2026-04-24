package com.novatech.cybertech.services.implementation.catalog;

import com.novatech.cybertech.services.implementation.S3ServiceImp;
import io.awspring.cloud.s3.S3Resource;
import io.awspring.cloud.s3.S3Template;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import software.amazon.awssdk.awscore.exception.AwsServiceException;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectResponse;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Mockito unit tests for {@link S3ServiceImp}.
 *
 * <p>SA-W3.5 wave — services/catalog. Pins BUG-082 (no content-type allow-list),
 * BUG-083 (no size cap), BUG-084 (deleteFile swallows every error).
 */
@ExtendWith(MockitoExtension.class)
class S3ServiceImpTest {

    @Mock S3Client s3Client;
    @Mock S3Template s3Template;

    private S3ServiceImp service;

    private static final String BUCKET = "test-bucket";

    @BeforeEach
    void setUp() {
        service = new S3ServiceImp(s3Client, s3Template);
        ReflectionTestUtils.setField(service, "bucketName", BUCKET);
    }

    // -----------------------------------------------------------------
    @Nested
    @DisplayName("uploadFile")
    class UploadFile {

        @Test
        @DisplayName("happy path uploads via S3Template and returns the URL string")
        void uploadFile_happyPath() throws Exception {
            MockMultipartFile file = new MockMultipartFile("photo", "kitten.jpg", "image/jpeg", new byte[]{1, 2, 3});
            S3Resource resource = mock(S3Resource.class);
            when(resource.getURL()).thenReturn(new URL("https://s3.example/test-bucket/products/abc_kitten.jpg"));
            when(s3Template.upload(eq(BUCKET), anyString(), any(InputStream.class))).thenReturn(resource);

            String url = service.uploadFile(file, "products");

            assertThat(url).isEqualTo("https://s3.example/test-bucket/products/abc_kitten.jpg");
            ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
            verify(s3Template).upload(eq(BUCKET), keyCaptor.capture(), any(InputStream.class));
            assertThat(keyCaptor.getValue()).startsWith("products/").endsWith("_kitten.jpg");
        }

        @Test
        @DisplayName("IOException reading the file is wrapped into RuntimeException")
        void uploadFile_ioException_wrapped() throws Exception {
            org.springframework.web.multipart.MultipartFile broken = mock(org.springframework.web.multipart.MultipartFile.class);
            when(broken.getContentType()).thenReturn("image/jpeg");
            when(broken.getSize()).thenReturn(1024L);
            when(broken.getOriginalFilename()).thenReturn("photo.jpg");
            when(broken.getInputStream()).thenThrow(new IOException("disk gone"));

            assertThatThrownBy(() -> service.uploadFile(broken, "products"))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("Failed to upload file to S3");
        }

        @Test
        @DisplayName("BUG-082 fixed: application/x-msdownload is rejected with IllegalArgumentException")
        void bug082_contentTypeAllowList_rejectsExecutable() {
            MockMultipartFile exe = new MockMultipartFile(
                    "f", "trojan.exe", "application/x-msdownload", new byte[]{0x4d, 0x5a});

            assertThatThrownBy(() -> service.uploadFile(exe, "products"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Unsupported file type")
                    .hasMessageContaining("application/x-msdownload");
            verifyNoInteractions(s3Template);
        }

        @Test
        @DisplayName("BUG-082 fixed: null content type is rejected with IllegalArgumentException")
        void bug082_contentTypeAllowList_rejectsNullContentType() {
            org.springframework.web.multipart.MultipartFile noType = mock(org.springframework.web.multipart.MultipartFile.class);
            when(noType.getContentType()).thenReturn(null);

            assertThatThrownBy(() -> service.uploadFile(noType, "products"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Unsupported file type");
            verifyNoInteractions(s3Template);
        }

        @Test
        @DisplayName("BUG-082 fixed: allowed content types (jpeg, png, webp, gif) pass the allow-list check")
        void bug082_contentTypeAllowList_permitsAllowedTypes() throws Exception {
            for (String type : java.util.List.of("image/jpeg", "image/png", "image/webp", "image/gif")) {
                MockMultipartFile file = new MockMultipartFile("photo", "file.img", type, new byte[]{1});
                S3Resource resource = mock(S3Resource.class);
                when(resource.getURL()).thenReturn(new URL("https://s3.example/test-bucket/products/x_file.img"));
                when(s3Template.upload(eq(BUCKET), anyString(), any(InputStream.class))).thenReturn(resource);

                String url = service.uploadFile(file, "products");

                assertThat(url).isNotNull();
            }
        }

        @Test
        @DisplayName("BUG-083 fixed: file exceeding 10 MB is rejected with IllegalArgumentException")
        void bug083_sizeCap_rejectsOversizedFile() {
            org.springframework.web.multipart.MultipartFile huge = mock(org.springframework.web.multipart.MultipartFile.class);
            when(huge.getContentType()).thenReturn("image/jpeg");
            when(huge.getSize()).thenReturn(10L * 1024 * 1024 + 1); // 10 MB + 1 byte

            assertThatThrownBy(() -> service.uploadFile(huge, "products"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("exceeds the maximum")
                    .hasMessageContaining("10 MB");
            verifyNoInteractions(s3Template);
        }

        @Test
        @DisplayName("BUG-083 fixed: file exactly at 10 MB is accepted")
        void bug083_sizeCap_permitsTenMbExactly() throws Exception {
            MockMultipartFile file = new MockMultipartFile(
                    "photo", "big.jpg", "image/jpeg", new byte[10 * 1024 * 1024]);
            S3Resource resource = mock(S3Resource.class);
            when(resource.getURL()).thenReturn(new URL("https://s3.example/test-bucket/products/x_big.jpg"));
            when(s3Template.upload(eq(BUCKET), anyString(), any(InputStream.class))).thenReturn(resource);

            String url = service.uploadFile(file, "products");

            assertThat(url).isNotNull();
        }

        @Test
        @DisplayName("key uses '<folder>/<uuid>_<originalFilename>' format")
        void uploadFile_keyFormat() throws Exception {
            MockMultipartFile file = new MockMultipartFile("photo", "name with spaces.png", "image/png", new byte[]{0});
            S3Resource resource = mock(S3Resource.class);
            when(resource.getURL()).thenReturn(new URL("https://s3.example/test-bucket/avatars/u_name with spaces.png"));
            when(s3Template.upload(eq(BUCKET), anyString(), any(InputStream.class))).thenReturn(resource);

            service.uploadFile(file, "avatars");

            ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
            verify(s3Template).upload(eq(BUCKET), keyCaptor.capture(), any(InputStream.class));
            assertThat(keyCaptor.getValue()).matches("avatars/[0-9a-fA-F-]{36}_name_with_spaces\\.png");
        }
    }

    // -----------------------------------------------------------------
    @Nested
    @DisplayName("deleteFile")
    class DeleteFile {

        @Test
        @DisplayName("happy path issues DeleteObjectRequest with the parsed key")
        void deleteFile_happyPath() {
            when(s3Client.deleteObject(any(DeleteObjectRequest.class))).thenReturn(DeleteObjectResponse.builder().build());

            service.deleteFile("https://s3.example/test-bucket/products/abc_kitten.jpg");

            ArgumentCaptor<DeleteObjectRequest> captor = ArgumentCaptor.forClass(DeleteObjectRequest.class);
            verify(s3Client).deleteObject(captor.capture());
            DeleteObjectRequest req = captor.getValue();
            assertThat(req.bucket()).isEqualTo(BUCKET);
            assertThat(req.key()).isEqualTo("products/abc_kitten.jpg");
        }

        @Test
        @DisplayName("BUG-084: malformed URL is swallowed silently — no S3 call, no exception")
        void bug084_malformedUrl_swallowed() {
            // Not a URL at all
            service.deleteFile("not-a-url");

            verifyNoInteractions(s3Client);
        }

        @Test
        @DisplayName("BUG-084: AwsServiceException from s3Client is swallowed, no propagation")
        void bug084_awsServiceException_swallowed() {
            when(s3Client.deleteObject(any(DeleteObjectRequest.class)))
                    .thenThrow(AwsServiceException.builder().message("boom").build());

            service.deleteFile("https://s3.example/test-bucket/products/abc_kitten.jpg");
            // No throw → contract pinned: caller cannot detect failure.
        }

        @Test
        @DisplayName("BUG-084: arbitrary RuntimeException is swallowed too")
        void bug084_runtimeException_swallowed() {
            when(s3Client.deleteObject(any(DeleteObjectRequest.class)))
                    .thenThrow(new RuntimeException("kaboom"));

            service.deleteFile("https://s3.example/test-bucket/products/abc.png");
            // No throw → contract pinned.
        }
    }
}
