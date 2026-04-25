package com.novatech.cybertech.dto.request.user;

import com.novatech.cybertech.entities.enums.BankCardType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BankCardCreationRequestDto {

    @NotBlank(message = "Card holder name cannot be blank")
    private String cardHolderName;

    @NotBlank(message = "Card number cannot be blank")
    @Size(min = 13, max = 19, message = "Card number must be between 13 and 19 digits")
    @Pattern(regexp = "\\d{13,19}", message = "Card number must contain only digits, 13–19 in length")
    private String cardNumber;

    @NotBlank(message = "Expiry date cannot be blank")
    @Pattern(regexp = "(0[1-9]|1[0-2])/[0-9]{4}", message = "Expiry date must be in format MM/YYYY")
    private String expiryDate;

    @NotNull(message = "Card type cannot be null")
    private BankCardType cardType;

    // Optionnel : Utilisé uniquement pour le CRUD Admin si on veut lier directement à un user
    private UUID userUuid;

    /**
     * PRE-4: aligned with the entity column {@code isDefault NOT NULL}. Defaults to
     * {@code false} via {@link Builder.Default} so callers that omit the field still
     * produce a well-formed request. {@link JsonProperty} pins the wire name to
     * {@code isDefault} (Jackson 3 would otherwise strip the {@code is} prefix and
     * emit {@code default}, breaking JSON round-trip for the primitive boolean).
     */
    @Builder.Default
    @JsonProperty("isDefault")
    private boolean isDefault = false;
}
