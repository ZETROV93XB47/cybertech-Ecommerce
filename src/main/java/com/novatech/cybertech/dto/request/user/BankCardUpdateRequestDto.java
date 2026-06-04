package com.novatech.cybertech.dto.request.user;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * Update payload for a bank card. By design the PAN ({@code cardNumber}) and the derived
 * {@code cardType} are WRITE-ONCE: once a card has been stored (and the PAN encrypted at rest)
 * it is never mutated in place. To change the card number, delete the card and add a new one.
 * Only the holder name and the expiry date are editable here.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BankCardUpdateRequestDto {

    @NotNull(message = "UUID cannot be null for update")
    private UUID uuid;

    @NotBlank(message = "Card holder name cannot be blank")
    private String cardHolderName;

    @NotBlank(message = "Expiry date cannot be blank")
    @Pattern(regexp = "(0[1-9]|1[0-2])/[0-9]{4}", message = "Expiry date must be in format MM/YYYY")
    private String expiryDate;
}