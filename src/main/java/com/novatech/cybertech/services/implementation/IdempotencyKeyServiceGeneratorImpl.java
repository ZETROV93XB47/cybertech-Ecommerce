package com.novatech.cybertech.services.implementation;


import com.novatech.cybertech.exceptions.IdempotencyKeyGenerationException;
import com.novatech.cybertech.services.core.IdempotencyKeyServiceGenerator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Slf4j
@Service
public class IdempotencyKeyServiceGeneratorImpl implements IdempotencyKeyServiceGenerator {

    private static final String HASH_ALGORITHM = "SHA-256";

    @Override
    public String generateKey(String orderUUID, List<String> orderProductsUUIDs) {
        if (orderUUID == null || orderProductsUUIDs == null || orderProductsUUIDs.isEmpty()) {
            log.warn("Cannot generate idempotency key with null or empty inputs. Returning a random key to prevent conflicts.");
            return java.util.UUID.randomUUID().toString();
        }

        // 1. Trier les UUIDs des produits pour garantir un ordre constant et déterministe.
        // C'est crucial pour que [A, B] et [B, A] produisent la même clé.
        // Copie de la liste pour éviter les effets de bord ou les exceptions sur les listes immuables
        List<String> sortedProducts = new ArrayList<>(orderProductsUUIDs);
        Collections.sort(sortedProducts);
        String sortedProductsString = String.join(",", sortedProducts);

        // 2. Créer une chaîne de caractères canonique qui représente la requête unique.
        // Le séparateur "::" évite les collisions si un UUID se terminait comme un autre commence.
        String dataToHash = orderUUID + "::" + sortedProductsString;

        try {
            // 3. Hacher la chaîne avec l'algorithme choisi.
            MessageDigest digest = MessageDigest.getInstance(HASH_ALGORITHM);
            byte[] hash = digest.digest(dataToHash.getBytes(StandardCharsets.UTF_8));

            // 4. Convertir le hash binaire en une chaîne hexadécimale lisible.
            return bytesToHex(hash);

        } catch (NoSuchAlgorithmException e) {
            // Ne devrait jamais arriver avec SHA-256 qui est standard dans le JDK.
            throw new IdempotencyKeyGenerationException("Could not generate idempotency key, algorithm not found: " + HASH_ALGORITHM, e);
        }
    }

    private String bytesToHex(byte[] hash) {
        StringBuilder hexString = new StringBuilder(2 * hash.length);
        for (byte b : hash) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) {
                hexString.append('0');
            }
            hexString.append(hex);
        }
        return hexString.toString();
    }
}
