package com.novatech.cybertech.validator.implementation;

import com.novatech.cybertech.dto.data.OrderValidationDto;
import com.novatech.cybertech.exceptions.BankCardExpiredException;
import com.novatech.cybertech.exceptions.BankCardNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

import static com.novatech.cybertech.utils.DateConverter.convertExpiryDateToLocalDate;

@Slf4j
@Component
public class BankCardValidityValidator extends ChainableOrderValidator {

    @Override
    public void validate(final OrderValidationDto orderValidationDto) {

        // Defensive: today the only construction site (OrderManagementServiceImp
        // .validateUserBeforeProcessingPayment) already guards this via
        // Optional.ofNullable(...).orElseThrow(BankCardNotFoundException), but OrderValidationDto
        // itself has no @NotNull on this field — guard here too so this validator's own contract
        // doesn't silently depend on a caller it cannot see.
        if (orderValidationDto.getUserDefaultBankCard() == null) {
            throw new BankCardNotFoundException("No default bank card associated with this order");
        }

        if (LocalDate.now().isAfter(convertExpiryDateToLocalDate(orderValidationDto.getUserDefaultBankCard().getExpiryDate())))
            throw new BankCardExpiredException("Bank card expired");
        log.info("Bank card valid");
        nextStep(orderValidationDto);
    }
}
