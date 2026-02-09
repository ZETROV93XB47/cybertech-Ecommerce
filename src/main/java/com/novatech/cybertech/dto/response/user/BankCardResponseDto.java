package com.novatech.cybertech.dto.response.user;

import com.novatech.cybertech.entities.enums.BankCardType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BankCardResponseDto {
    private UUID uuid;
    private String cardHolderName;
    private String cardNumber; // Idéalement masqué dans une vraie app
    private String expiryDate;
    private BankCardType cardType;
    private UUID userUuid;
}