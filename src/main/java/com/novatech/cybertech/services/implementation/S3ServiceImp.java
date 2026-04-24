package com.novatech.cybertech.services.implementation;

import com.novatech.cybertech.services.core.S3Service;
import io.awspring.cloud.s3.S3Resource;
import io.awspring.cloud.s3.S3Template;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;


@Slf4j
@Service
@RequiredArgsConstructor
public class S3ServiceImp implements S3Service {

    private static final long MAX_FILE_SIZE_BYTES = 10L * 1024 * 1024; // 10 MB
    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
            "image/jpeg", "image/png", "image/webp", "image/gif"
    );

    private final S3Client s3Client; // <-- AJOUT IMPORTANT
    private final S3Template s3Template;

    @Value("${application.bucket.name}")
    private String bucketName;

    @Override
    public String uploadFile(MultipartFile file, String folder) {
        final String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_CONTENT_TYPES.contains(contentType)) {
            throw new IllegalArgumentException(
                    "Unsupported file type: " + contentType + ". Allowed: " + String.join(", ", new TreeSet<>(ALLOWED_CONTENT_TYPES)));
        }
        if (file.getSize() > MAX_FILE_SIZE_BYTES) {
            throw new IllegalArgumentException("File size " + file.getSize() + " bytes exceeds the maximum of " + MAX_FILE_SIZE_BYTES + " bytes (10 MB).");
        }

        final String originalFilename = file.getOriginalFilename();
        final String safeFilename = (originalFilename == null) ? "upload"
                : originalFilename.replaceAll("[^a-zA-Z0-9._-]", "_");
        String key = folder + "/" + UUID.randomUUID() + "_" + safeFilename;

        try {
            log.info("Uploading file {} to bucket {} with key {}", file.getOriginalFilename(), bucketName, key);
            S3Resource resource = s3Template.upload(bucketName, key, file.getInputStream());
            return resource.getURL().toString();
        } catch (IOException e) {
            log.error("Error uploading file to S3", e);
            throw new RuntimeException("Failed to upload file to S3", e);
        }
    }

    @Override
    public void deleteFile(String fileUrl) {
        try {
            URL url = new URL(fileUrl);
            String path = url.getPath();

            // /bucketName/folder/file.jpg → folder/file.jpg
            String key = path.substring(("/" + bucketName + "/").length());

            log.info("Deleting file from S3 with key: {}", key);

            s3Client.deleteObject(DeleteObjectRequest.builder()
                    .bucket(bucketName)
                    .key(key)
                    .build());

            log.info("Successfully deleted file with key: {}", key);

        } catch (MalformedURLException e) {
            log.error("Invalid URL for S3 deletion: {}", fileUrl, e);
        } catch (Exception e) {
            log.error("Error deleting file from S3: {}", fileUrl, e);
        }
    }
}