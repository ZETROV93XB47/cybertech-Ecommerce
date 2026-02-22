package com.novatech.cybertech.api.controllers.implementation;

import io.awspring.cloud.s3.S3Resource;
import io.awspring.cloud.s3.S3Template;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
@RestController
@RequestMapping("/test")
@RequiredArgsConstructor
public class TestUploadController {

    private static final String IMAGE = "image";
    private final S3Template s3Template; // Utilisation de S3Template (Spring Cloud AWS v3/v4)

    private static final String BUCKET = "testbucket";
    private static final String PREFIX = "test-upload/";

    @PostMapping(value = "/upload-image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> uploadImage(@RequestParam(IMAGE) MultipartFile image) {

        log.info("Request data : {}", image);

        try {
            String key = PREFIX + image.getOriginalFilename();

            // Upload via S3Template
            S3Resource resource = s3Template.upload(BUCKET, key, image.getInputStream());

            // Retourne l'URL. Note: resource.getURL() peut retourner l'IP interne docker, on force localhost pour le test
            return ResponseEntity.ok("http://localhost:4566/" + BUCKET + "/" + key);

        } catch (Exception e) {
            return ResponseEntity.status(500).body("Erreur upload: " + e.getMessage());
        }
    }
}