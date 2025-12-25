package com.novatech.cybertech.dto.request.user;

import com.novatech.cybertech.entities.enums.BankCardType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BankCardCreationRequestDto {
    private String cardHolderName;
    private String cardNumber;
    private String expiryDate;
    private BankCardType cardType;
    private Boolean isDefault;
}
