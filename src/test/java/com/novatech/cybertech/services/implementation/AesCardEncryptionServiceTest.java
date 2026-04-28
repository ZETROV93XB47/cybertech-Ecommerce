package com.novatech.cybertech.services.implementation;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link AesCardEncryptionService}.
 *
 * <p>Pins the BUG-036 PCI-DSS contract:
 * <ul>
 *   <li>round-trip {@code decrypt(encrypt(pan)) == pan} for any well-formed PAN;</li>
 *   <li>encryption is non-deterministic — same input twice produces different envelopes
 *       (a fresh random 12-byte IV per call);</li>
 *   <li>the envelope is valid base64 and prefixes the IV;</li>
 *   <li>any tampering (truncation, corruption of the GCM tag, base64 noise) is rejected
 *       loud as {@link IllegalStateException};</li>
 *   <li>null inputs trigger a clear {@link IllegalArgumentException};</li>
 *   <li>the constructor refuses keys that are not exactly 32 bytes after base64 decode.</li>
 * </ul>
 */
class AesCardEncryptionServiceTest {

    /** Fixed AES-256 key (32 bytes of 0x00..0x1F) base64-encoded — reproducible across runs. */
    private static final String FIXED_AES_256_KEY_B64;

    static {
        final byte[] keyBytes = new byte[32];
        for (int i = 0; i < keyBytes.length; i++) {
            keyBytes[i] = (byte) i;
        }
        FIXED_AES_256_KEY_B64 = Base64.getEncoder().encodeToString(keyBytes);
    }

    private static final int GCM_IV_LENGTH_BYTES = 12;

    private AesCardEncryptionService service;

    @BeforeEach
    void setUp() {
        // Constructor injection — no Spring context, no ReflectionTestUtils needed.
        this.service = new AesCardEncryptionService(FIXED_AES_256_KEY_B64);
    }

    // ---------------------------------------------------------------------
    // Construction / key validation
    // ---------------------------------------------------------------------

    @Nested
    @DisplayName("Constructor — key validation")
    class ConstructorTests {

        @Test
        @DisplayName("accepts a valid base64-encoded 32-byte key (AES-256)")
        void acceptsValidAes256Key() {
            // Generate a fresh random AES-256 key and check no exception.
            final byte[] keyBytes = new byte[32];
            new SecureRandom().nextBytes(keyBytes);
            final String b64 = Base64.getEncoder().encodeToString(keyBytes);

            // Then: constructor does not throw.
            new AesCardEncryptionService(b64);
        }

        @Test
        @DisplayName("rejects a 16-byte (AES-128) key with IllegalStateException naming BUG-036")
        void rejectsAes128Key() {
            final byte[] tooShort = new byte[16];
            final String b64 = Base64.getEncoder().encodeToString(tooShort);

            assertThatThrownBy(() -> new AesCardEncryptionService(b64))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("BUG-036")
                    .hasMessageContaining("16 bytes");
        }

        @Test
        @DisplayName("rejects a 24-byte (AES-192) key — must be exactly 32 bytes")
        void rejectsAes192Key() {
            final byte[] tooShort = new byte[24];
            final String b64 = Base64.getEncoder().encodeToString(tooShort);

            assertThatThrownBy(() -> new AesCardEncryptionService(b64))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("BUG-036")
                    .hasMessageContaining("24 bytes");
        }

        @Test
        @DisplayName("rejects an oversized 64-byte key")
        void rejectsOversizedKey() {
            final byte[] tooLong = new byte[64];
            final String b64 = Base64.getEncoder().encodeToString(tooLong);

            assertThatThrownBy(() -> new AesCardEncryptionService(b64))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("BUG-036");
        }
    }

    // ---------------------------------------------------------------------
    // Round-trip happy paths
    // ---------------------------------------------------------------------

    @Test
    @DisplayName("encrypt then decrypt returns the original PAN (Visa 16-digit)")
    void roundTripVisa() {
        final String pan = "4111111111111111";

        final String envelope = service.encrypt(pan);
        final String decrypted = service.decrypt(envelope);

        assertThat(decrypted).isEqualTo(pan);
    }

    @Test
    @DisplayName("encrypt then decrypt returns the original PAN (15-digit Amex)")
    void roundTripAmex() {
        final String pan = "378282246310005";

        assertThat(service.decrypt(service.encrypt(pan))).isEqualTo(pan);
    }

    @Test
    @DisplayName("encrypt then decrypt of a long PAN spanning multiple AES blocks (>16 bytes)")
    void roundTripLongInput() {
        // 64 ASCII chars = 64 bytes — 4 AES blocks; exercises CTR streaming inside GCM.
        final String pan = "1234567890123456789012345678901234567890123456789012345678901234";

        assertThat(service.decrypt(service.encrypt(pan))).isEqualTo(pan);
    }

    @Test
    @DisplayName("encrypt then decrypt preserves multi-byte UTF-8 content")
    void roundTripUtf8() {
        // The contract is on the PAN (ASCII digits) but the impl uses StandardCharsets.UTF_8
        // for both directions — guard the symmetry on a non-ASCII payload.
        final String payload = "carte-éàü-✓-中文";

        assertThat(service.decrypt(service.encrypt(payload))).isEqualTo(payload);
    }

    @Test
    @DisplayName("encrypt then decrypt of an empty string returns an empty string")
    void roundTripEmpty() {
        // GCM accepts an empty plaintext; the envelope is still IV(12) + tag(16) = 28 bytes.
        final String empty = "";

        final String envelope = service.encrypt(empty);
        assertThat(service.decrypt(envelope)).isEqualTo(empty);
    }

    // ---------------------------------------------------------------------
    // Non-determinism / IV randomness — the security-critical property
    // ---------------------------------------------------------------------

    @Test
    @DisplayName("encrypting the same PAN twice yields two different envelopes (random IV)")
    void encryptSamePanProducesDifferentEnvelopes() {
        final String pan = "4111111111111111";

        final String first = service.encrypt(pan);
        final String second = service.encrypt(pan);

        assertThat(first).isNotEqualTo(second);
        // Both must still decrypt back to the same PAN.
        assertThat(service.decrypt(first)).isEqualTo(pan);
        assertThat(service.decrypt(second)).isEqualTo(pan);
    }

    @Test
    @DisplayName("encrypting the same PAN N times produces N distinct envelopes (no IV collision)")
    void manyEncryptionsAreAllDistinct() {
        final String pan = "4111111111111111";
        final int rounds = 100;
        final Set<String> envelopes = new HashSet<>();

        for (int i = 0; i < rounds; i++) {
            envelopes.add(service.encrypt(pan));
        }

        assertThat(envelopes).hasSize(rounds);
    }

    // ---------------------------------------------------------------------
    // Envelope shape
    // ---------------------------------------------------------------------

    @Test
    @DisplayName("envelope is valid base64 and at least 12 bytes longer than the plaintext (IV prefix)")
    void envelopeIsBase64WithIvPrefix() {
        final String pan = "4111111111111111";

        final String envelope = service.encrypt(pan);

        // Must decode without throwing — i.e., it is well-formed base64.
        final byte[] raw = Base64.getDecoder().decode(envelope);
        // 12-byte IV + ciphertext (= plaintext length under CTR) + 16-byte tag = pan.length + 28.
        assertThat(raw).hasSize(pan.getBytes(StandardCharsets.UTF_8).length + GCM_IV_LENGTH_BYTES + 16);
    }

    @Test
    @DisplayName("envelopes encrypted by two services sharing the same key decrypt cross-instance")
    void crossInstanceDecryptionWithSameKey() {
        final AesCardEncryptionService other = new AesCardEncryptionService(FIXED_AES_256_KEY_B64);
        final String pan = "5555555555554444";

        final String envelope = service.encrypt(pan);

        assertThat(other.decrypt(envelope)).isEqualTo(pan);
    }

    // ---------------------------------------------------------------------
    // Decryption — defensive paths
    // ---------------------------------------------------------------------

    @Test
    @DisplayName("decrypt with a different key fails the GCM authentication tag check")
    void decryptWithWrongKeyFails() {
        // Build a service with a different fixed key.
        final byte[] otherKeyBytes = new byte[32];
        for (int i = 0; i < otherKeyBytes.length; i++) {
            otherKeyBytes[i] = (byte) (0xFF - i);
        }
        final AesCardEncryptionService wrongKeyService =
                new AesCardEncryptionService(Base64.getEncoder().encodeToString(otherKeyBytes));

        final String envelope = service.encrypt("4111111111111111");

        assertThatThrownBy(() -> wrongKeyService.decrypt(envelope))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("BUG-036")
                .hasMessageContaining("decryption failed");
    }

    @Test
    @DisplayName("decrypt of a tampered ciphertext (flipped byte after IV) fails the AEAD tag check")
    void decryptTamperedCiphertextFails() {
        final String envelope = service.encrypt("4111111111111111");
        final byte[] raw = Base64.getDecoder().decode(envelope);
        // Flip a bit in the ciphertext region (right after the IV).
        raw[GCM_IV_LENGTH_BYTES] ^= 0x01;
        final String tampered = Base64.getEncoder().encodeToString(raw);

        assertThatThrownBy(() -> service.decrypt(tampered))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("BUG-036");
    }

    @Test
    @DisplayName("decrypt of a tampered tag (last byte flipped) fails the AEAD tag check")
    void decryptTamperedTagFails() {
        final String envelope = service.encrypt("4111111111111111");
        final byte[] raw = Base64.getDecoder().decode(envelope);
        // Flip the very last byte — that is inside the 16-byte GCM tag.
        raw[raw.length - 1] ^= 0x42;
        final String tampered = Base64.getEncoder().encodeToString(raw);

        assertThatThrownBy(() -> service.decrypt(tampered))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("BUG-036");
    }

    @Test
    @DisplayName("decrypt of an envelope shorter than the IV is rejected with a clear BUG-036 message")
    void decryptTooShortEnvelopeFails() {
        // Exactly 12 bytes — that's the IV but no ciphertext, which the impl rejects with
        // a dedicated message before even calling Cipher.
        final byte[] onlyIv = new byte[GCM_IV_LENGTH_BYTES];
        final String tooShort = Base64.getEncoder().encodeToString(onlyIv);

        assertThatThrownBy(() -> service.decrypt(tooShort))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("envelope too short");
    }

    @Test
    @DisplayName("decrypt of a 4-byte envelope is rejected — too short to contain even an IV")
    void decryptVeryShortEnvelopeFails() {
        final String tooShort = Base64.getEncoder().encodeToString(new byte[]{0x01, 0x02, 0x03, 0x04});

        assertThatThrownBy(() -> service.decrypt(tooShort))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("envelope too short");
    }

    @Test
    @DisplayName("decrypt of a non-base64 string is wrapped as IllegalStateException (BUG-036)")
    void decryptNonBase64Fails() {
        // '!' is not in the base64 alphabet — Base64.getDecoder().decode throws
        // IllegalArgumentException, which the catch-all wraps as IllegalStateException.
        assertThatThrownBy(() -> service.decrypt("!!!not-base64!!!"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("BUG-036");
    }

    // ---------------------------------------------------------------------
    // Null-input contracts
    // ---------------------------------------------------------------------

    @Test
    @DisplayName("encrypt(null) throws IllegalArgumentException with a clear BUG-036 message")
    void encryptNullPan() {
        assertThatThrownBy(() -> service.encrypt(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("BUG-036")
                .hasMessageContaining("null PAN");
    }

    @Test
    @DisplayName("decrypt(null) throws IllegalArgumentException with a clear BUG-036 message")
    void decryptNullCiphertext() {
        assertThatThrownBy(() -> service.decrypt(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("BUG-036")
                .hasMessageContaining("null ciphertext");
    }
}
