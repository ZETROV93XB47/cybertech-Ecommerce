package com.novatech.cybertech.services.core;

/**
 * Authenticated encryption boundary for short sensitive strings persisted by the application.
 */
public interface SensitiveDataEncryptionService {

    String encrypt(String plaintext);

    String decrypt(String ciphertext);
}
