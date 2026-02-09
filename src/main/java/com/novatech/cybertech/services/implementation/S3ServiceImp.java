package com.novatech.cybertech.services.implementation;

import com.novatech.cybertech.services.core.S3Service;
import io.awspring.cloud.s3.S3Resource;
import io.awspring.cloud.s3.S3Template;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class S3ServiceImp implements S3Service {

    private final S3Template s3Template;

    @Value("${application.bucket.name}")
    private String bucketName;

    @Override
    public String uploadFile(MultipartFile file, String folder) {
        // Génération d'un nom unique : folder/uuid_filename
        String key = folder + "/" + UUID.randomUUID() + "_" + file.getOriginalFilename();

        try {
            log.info("Uploading file {} to bucket {} with key {}", file.getOriginalFilename(), bucketName, key);

            S3Resource resource = s3Template.upload(bucketName, key, file.getInputStream());

            // Retourne l'URL d'accès (pour MinIO local, assurez-vous que l'URL est accessible)
            return resource.getURL().toString();
        } catch (IOException e) {
            log.error("Error uploading file to S3", e);
            throw new RuntimeException("Failed to upload file to S3", e);
        }
    }

    @Override
    public void deleteFile(String fileUrl) {
        // Extraction de la clé depuis l'URL (simplifié)
        // Supposons que l'URL contient le nom du bucket ou que l'on stocke la clé relative
        // Ici, on suppose que fileUrl est la clé ou qu'on peut la déduire.
        // Pour faire simple, on peut passer la clé directement si on la stocke.
        try {
            // Logique d'extraction de la clé à adapter selon le format de l'URL MinIO
            // s3Template.delete(bucketName, key);
            log.info("Delete file requested for {}", fileUrl);
        } catch (Exception e) {
            log.error("Error deleting file from S3", e);
        }
    }
}