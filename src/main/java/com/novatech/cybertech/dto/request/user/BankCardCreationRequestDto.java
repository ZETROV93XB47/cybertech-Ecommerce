package com.novatech.cybertech.dto.request.user;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Saves the card the browser just confirmed in Stripe Elements.
 *
 * <p>Only the SetupIntent id travels: the card number never reaches the backend. The former body
 * ({@code cardNumber}, {@code expiryDate}...) has no {@code setupIntentId} and is rejected with a 400.</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BankCardCreationRequestDto {

    @NotBlank(message = "setupIntentId cannot be blank")
    @Pattern(regexp = "seti_[A-Za-z0-9]+", message = "setupIntentId must be a Stripe SetupIntent id (seti_...)")
    @Schema(description = "Id of the SetupIntent confirmed by Stripe Elements (returned by POST /bank-card/setup-intent).",
            example = "seti_1QxYzAbCdEfGhIjK")
    private String setupIntentId;
}
