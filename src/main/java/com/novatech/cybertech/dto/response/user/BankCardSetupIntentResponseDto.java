package com.novatech.cybertech.dto.response.user;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

/**
 * What Stripe Elements needs to collect and save a card in the browser.
 *
 * @param setupIntentId  id to send back to {@code POST /bank-card/add} once Stripe confirmed the card
 * @param clientSecret   secret of the SetupIntent, passed to {@code stripe.confirmCardSetup}
 * @param publishableKey Stripe publishable key ({@code pk_...}) to initialise Stripe.js
 */
@Builder
public record BankCardSetupIntentResponseDto(
        @Schema(example = "seti_1QxYzAbCdEfGhIjK") String setupIntentId,
        @Schema(example = "seti_1QxYzAbCdEfGhIjK_secret_abc123") String clientSecret,
        @Schema(example = "pk_test_51AbCdEf") String publishableKey) {
}
