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

//        final BankCardEntity bankCardEntity = order
//                .getUserEntity()
//                .getBankCardEntities()
//                .stream()
//                .filter(b -> b.getIsDefault().equals(true))
//                .findFirst()
//                .orElseThrow(() -> new NoDefaultBankCartSetException("No default bank card set, please, set a default bank card and retry ..."));


        if (LocalDate.now().isAfter(convertExpiryDateToLocalDate(orderValidationDto.getUserDefaultBankCard().getExpiryDate())))
            throw new BankCardExpiredException("Bank card expired");
        log.info("Bank card valid");
        nextStep(orderValidationDto);
    }
}
