package com.novatech.cybertech.services.core;

/**
 * Cryptographic boundary used to protect a payment card Primary Account Number (PAN) at rest.
 *
 * <p><b>PCI-DSS rationale (BUG-036).</b> PCI-DSS requirement 3.5 mandates that the PAN be
 * rendered unreadable anywhere it is stored. We therefore route every persistence path through
 * this service rather than letting callers stash a plaintext PAN on a JPA entity. The interface
 * is deliberately tiny so it can be mocked in unit tests without dragging key material into the
 * test classpath.</p>
 *
 * <p><b>Why an interface, not a concrete class.</b> Production wires the AES/GCM
 * implementation, but tests and future ops swap-outs (HSM-backed, KMS-backed) can plug a
 * different bean without touching call sites. Implementations must be deterministic for the
 * decrypt path: {@code decrypt(encrypt(pan)).equals(pan)} for any well-formed PAN.</p>
 */
public interface CardEncryptionService {

    /**
     * Encrypts a PAN for at-rest storage.
     *
     * <p>Implementations MUST produce a self-describing ciphertext that embeds whatever
     * per-record randomness (e.g., the GCM IV) is required for {@link #decrypt(String)} to be
     * symmetric without external metadata. The output should be safe to store in a single
     * {@code VARCHAR} column.</p>
     *
     * @param pan the cleartext PAN; must be non-null. The implementation does not validate
     *            Luhn or length — the caller (DTO validation) is responsible for that.
     * @return an opaque, base64-encoded ciphertext envelope. Never {@code null}.
     */
    String encrypt(String pan);

    /**
     * Reverses {@link #encrypt(String)}.
     *
     * @param ciphertext the envelope produced by {@link #encrypt(String)}.
     * @return the original PAN.
     * @throws IllegalStateException if the envelope is corrupted, was encrypted with a
     *                               different key, or fails the AEAD authentication tag
     *                               check.
     */
    String decrypt(String ciphertext);
}
