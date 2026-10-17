package com.novatech.cybertech.fixtures.support.stubs;

import com.stripe.net.Webhook;

import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;

/**
 * Utility builder for signed Stripe webhook payloads. Computes the HMAC-SHA256 over
 * {@code "<timestamp>.<payload>"} using {@link Webhook.Util#computeHmacSha256(String, String)} and
 * formats the resulting Stripe-Signature header as {@code t=<timestamp>,v1=<digest>}.
 */
public final class StripeEventBuilder {

    private StripeEventBuilder() {
    }

    public record Signed(String payload, String header) {
    }

    public static Signed signedPayload(final String secret,
                                       final String payloadJson,
                                       final long timestampSec) {
        final String signedPayload = timestampSec + "." + payloadJson;
        final String signature;
        try {
            signature = Webhook.Util.computeHmacSha256(secret, signedPayload);
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException("Failed to sign Stripe payload", e);
        }
        final String header = "t=" + timestampSec + ",v1=" + signature;
        return new Signed(payloadJson, header);
    }

    public static Signed signedPayloadNow(final String secret, final String payloadJson) {
        return signedPayload(secret, payloadJson, System.currentTimeMillis() / 1000L);
    }
}
