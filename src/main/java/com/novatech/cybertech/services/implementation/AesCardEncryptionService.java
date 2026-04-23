package com.novatech.cybertech.services.implementation;

import com.novatech.cybertech.services.core.CardEncryptionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * AES/GCM reference implementation of {@link CardEncryptionService} — BUG-036 (PCI-DSS).
 *
 * <p><b>Why AES/GCM rather than AES/CBC or AES/ECB.</b> GCM is an <i>authenticated</i>
 * encryption mode (AEAD): it provides confidentiality <i>and</i> integrity in a single pass.
 * CBC gives confidentiality only and then requires a separate HMAC to detect tampering — an
 * easy footgun. ECB leaks plaintext structure because identical blocks produce identical
 * ciphertext. PCI-DSS treats any tamper-undetected storage of the PAN as a finding, so we
 * insist on AEAD.</p>
 *
 * <p><b>Why a fresh 12-byte IV per record.</b> GCM is catastrophic if an (IV, key) pair is
 * ever reused — an attacker who sees two ciphertexts under the same IV can XOR them and
 * recover both plaintexts. We therefore generate 12 random bytes with
 * {@link SecureRandom} for every call to {@link #encrypt(String)} and prepend those bytes to
 * the ciphertext. On decrypt, we split the IV back out. This also thwarts replay: even the
 * same PAN encrypted twice produces two distinct envelopes.</p>
 *
 * <p><b>Why base64 envelope.</b> Databases and JSON are text-friendly. Storing
 * {@code base64(iv || ciphertext || tag)} in a single {@code VARCHAR} column keeps JPA happy
 * and lets the {@code BankCardEntity.encryptedNumber} stay a plain {@code String}.</p>
 *
 * <p><b>Key provisioning.</b> The key is injected from
 * {@code app.security.card-encryption-key} (base64, 32 bytes = AES-256). The
 * {@code application.properties} default is a DEV placeholder — production MUST override it
 * via Vault/KMS. A startup validation rejects keys that are not exactly 32 bytes after
 * base64 decoding, so a typo fails loud rather than silently downgrading to AES-128.</p>
 */
@Slf4j
@Service
public class AesCardEncryptionService implements CardEncryptionService {

    /** GCM recommended IV length (NIST SP 800-38D) — 96 bits = 12 bytes. */
    private static final int GCM_IV_LENGTH_BYTES = 12;

    /** GCM authentication tag length — 128 bits, the maximum and only safe choice for PCI. */
    private static final int GCM_TAG_LENGTH_BITS = 128;

    /** Required AES-256 key length after base64 decode. */
    private static final int AES_256_KEY_LENGTH_BYTES = 32;

    private static final String AES_ALGORITHM = "AES";
    private static final String AES_GCM_TRANSFORMATION = "AES/GCM/NoPadding";

    private final SecretKeySpec secretKey;
    private final SecureRandom secureRandom = new SecureRandom();

    /**
     * Builds the service from the base64-encoded key configured in
     * {@code app.security.card-encryption-key}.
     *
     * @param base64Key the base64 AES-256 key. Validated for length eagerly at bean creation.
     */
    public AesCardEncryptionService(@Value("${app.security.card-encryption-key}") final String base64Key) {
        final byte[] keyBytes = Base64.getDecoder().decode(base64Key);
        if (keyBytes.length != AES_256_KEY_LENGTH_BYTES) {
            throw new IllegalStateException(
                    "BUG-036: app.security.card-encryption-key must be a base64-encoded 32-byte (AES-256) key; got "
                            + keyBytes.length + " bytes.");
        }
        this.secretKey = new SecretKeySpec(keyBytes, AES_ALGORITHM);
        log.info("AesCardEncryptionService initialised with an AES-256 key (GCM mode, 128-bit tag).");
    }

    /**
     * {@inheritDoc}
     *
     * <p>Output layout: {@code base64( iv[12] || ciphertext || tag[16] )}. The tag is
     * appended by {@link Cipher} itself in GCM mode.</p>
     */
    @Override
    public String encrypt(final String pan) {
        if (pan == null) {
            throw new IllegalArgumentException("BUG-036: cannot encrypt a null PAN");
        }
        try {
            final byte[] iv = new byte[GCM_IV_LENGTH_BYTES];
            secureRandom.nextBytes(iv);

            final Cipher cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv));
            final byte[] ciphertext = cipher.doFinal(pan.getBytes(StandardCharsets.UTF_8));

            final byte[] envelope = new byte[iv.length + ciphertext.length];
            System.arraycopy(iv, 0, envelope, 0, iv.length);
            System.arraycopy(ciphertext, 0, envelope, iv.length, ciphertext.length);
            return Base64.getEncoder().encodeToString(envelope);
        } catch (final Exception e) {
            // Do NOT log the PAN.
            throw new IllegalStateException("BUG-036: AES/GCM encryption of PAN failed", e);
        }
    }

    /**
     * {@inheritDoc}
     *
     * <p>Fails loud (wrapped {@code IllegalStateException}) if the authentication tag check
     * fails — callers must treat that as tampered storage, not as recoverable input.</p>
     */
    @Override
    public String decrypt(final String ciphertext) {
        if (ciphertext == null) {
            throw new IllegalArgumentException("BUG-036: cannot decrypt a null ciphertext");
        }
        try {
            final byte[] envelope = Base64.getDecoder().decode(ciphertext);
            if (envelope.length <= GCM_IV_LENGTH_BYTES) {
                throw new IllegalStateException("BUG-036: ciphertext envelope too short to contain an IV");
            }
            final byte[] iv = new byte[GCM_IV_LENGTH_BYTES];
            System.arraycopy(envelope, 0, iv, 0, GCM_IV_LENGTH_BYTES);
            final byte[] actualCipher = new byte[envelope.length - GCM_IV_LENGTH_BYTES];
            System.arraycopy(envelope, GCM_IV_LENGTH_BYTES, actualCipher, 0, actualCipher.length);

            final Cipher cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv));
            final byte[] plain = cipher.doFinal(actualCipher);
            return new String(plain, StandardCharsets.UTF_8);
        } catch (final IllegalStateException e) {
            throw e;
        } catch (final Exception e) {
            throw new IllegalStateException("BUG-036: AES/GCM decryption failed (tampered or wrong key?)", e);
        }
    }
}
