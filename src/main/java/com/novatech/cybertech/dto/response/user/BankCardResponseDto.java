package com.novatech.cybertech.dto.response.user;

import com.novatech.cybertech.entities.enums.BankCardType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * API response shape for a bank card.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BankCardResponseDto {
    private UUID uuid;
    private String cardHolderName;


    /**
     * Safe-to-display masked representation (e.g. {@code "**** **** **** 4242"}).
     * Derived from {@code BankCardEntity.lastFourDigits}; never contains digits beyond
     * the last four.
     */
    private String maskedNumber;

    private String expiryDate;
    private BankCardType cardType;
    private UUID userUuid;
}
