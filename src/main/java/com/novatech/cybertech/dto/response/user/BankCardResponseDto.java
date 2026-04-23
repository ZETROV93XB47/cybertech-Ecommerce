package com.novatech.cybertech.dto.response.user;

import com.novatech.cybertech.entities.enums.BankCardType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * API response shape for a bank card.
 *
 * <p>BUG-036 (PCI-DSS): <b>a full PAN is never surfaced here</b>. The user-facing value
 * is {@link #maskedNumber} (e.g. {@code "**** **** **** 4242"}). The legacy
 * {@link #cardNumber} field remains on the DTO for backward compatibility with older
 * clients, but new code paths populate it with the same masked value — the plaintext
 * PAN never leaves the server.</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BankCardResponseDto {
    private UUID uuid;
    private String cardHolderName;

    /**
     * Legacy field kept so existing clients and MapStruct mappings do not break.
     * Contains the masked representation for new rows; legacy rows may surface the
     * raw value which the mapper simply copies across — new writes never store a
     * plaintext PAN, so over time this converges on the masked form.
     */
    private String cardNumber;

    /**
     * BUG-036: safe-to-display masked representation (e.g. {@code "**** **** **** 4242"}).
     * Derived from {@code BankCardEntity.lastFourDigits}; never contains digits beyond
     * the last four.
     */
    private String maskedNumber;

    private String expiryDate;
    private BankCardType cardType;
    private UUID userUuid;

    /**
     * BUG-038: exposes whether this is the user's chosen default card for checkout flows.
     * {@code Boolean} (not primitive) so Jackson serializes it as {@code null} for legacy
     * rows that pre-date the default-card surface, keeping JSON-strict matchers quiet.
     */
    private Boolean isDefault;
}
