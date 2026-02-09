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

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BankCardUpdateRequestDto {

    @NotNull(message = "UUID cannot be null for update")
    private UUID uuid;

    @NotBlank(message = "Card holder name cannot be blank")
    private String cardHolderName;

    @NotBlank(message = "Card number cannot be blank")
    @Size(min = 13, max = 19, message = "Card number must be between 13 and 19 digits")
    private String cardNumber;

    @NotBlank(message = "Expiry date cannot be blank")
    @Pattern(regexp = "(0[1-9]|1[0-2])/[0-9]{4}", message = "Expiry date must be in format MM/YYYY")
    private String expiryDate;

    @NotNull(message = "Card type cannot be null")
    private BankCardType cardType;
}