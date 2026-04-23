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
import java.util.HexFormat;
import java.util.List;

@Slf4j
@Service
public class IdempotencyKeyServiceGeneratorImpl implements IdempotencyKeyServiceGenerator {

    private static final String HASH_ALGORITHM = "SHA-256";

    @Override
    public String generateKey(String orderUUID, List<String> context) {
        if (orderUUID == null || context == null || context.isEmpty()) {
            log.warn("Cannot generate idempotency key with null or empty inputs. Returning a random key to prevent conflicts.");
            return java.util.UUID.randomUUID().toString();
        }

        // Sort so [A, B] and [B, A] yield the same key. "::" separator avoids UUID boundary collisions.
        final List<String> sortedContext = new ArrayList<>(context);
        Collections.sort(sortedContext);
        final String dataToHash = orderUUID + "::" + String.join(",", sortedContext);

        try {
            final MessageDigest digest = MessageDigest.getInstance(HASH_ALGORITHM);
            final byte[] hash = digest.digest(dataToHash.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IdempotencyKeyGenerationException("Could not generate idempotency key, algorithm not found: " + HASH_ALGORITHM, e);
        }
    }
}
