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

        if (orderValidationDto.getBankCardValidationDto() == null) {
            throw new BankCardNotFoundException("No default bank card associated with this order");
        }

        if (LocalDate.now().isAfter(convertExpiryDateToLocalDate(orderValidationDto.getBankCardValidationDto().getExpiryDate())))
            throw new BankCardExpiredException("Bank card expired");
        log.info("Bank card valid");
        nextStep(orderValidationDto);
    }
}
