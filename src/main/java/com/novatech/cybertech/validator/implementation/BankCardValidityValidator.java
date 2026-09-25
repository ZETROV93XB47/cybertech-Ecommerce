package com.novatech.cybertech.validator.implementation;

import com.novatech.cybertech.dto.data.OrderValidationDto;
import com.novatech.cybertech.exceptions.BankCardExpiredException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

import static com.novatech.cybertech.utils.DateConverter.convertExpiryDateToLocalDate;

@Slf4j
@Component
public class BankCardValidityValidator extends ChainableOrderValidator {

    @Override
    public void validate(final OrderValidationDto orderValidationDto) {

        if (LocalDate.now().isAfter(convertExpiryDateToLocalDate(orderValidationDto.getUserDefaultBankCard().getExpiryDate())))
            throw new BankCardExpiredException("Bank card expired");
        log.info("Bank card valid");
        nextStep(orderValidationDto);
    }
}
