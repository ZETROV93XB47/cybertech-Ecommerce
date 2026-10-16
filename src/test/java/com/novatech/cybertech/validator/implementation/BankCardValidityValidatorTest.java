package com.novatech.cybertech.validator.implementation;

import com.novatech.cybertech.dto.data.OrderValidationDto;
import com.novatech.cybertech.dto.data.BankCardValidationDto;
import com.novatech.cybertech.exceptions.BankCardExpiredException;
import com.novatech.cybertech.exceptions.BankCardNotFoundException;
import com.novatech.cybertech.validator.core.OrderValidator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class BankCardValidityValidatorTest {

    private static final DateTimeFormatter EXPIRY = DateTimeFormatter.ofPattern("MM/yyyy");

    @Mock
    private OrderValidator nextValidator;

    private final BankCardValidityValidator validator = new BankCardValidityValidator();

    private OrderValidationDto dtoWithCard(BankCardValidationDto card) {
        return OrderValidationDto.builder()
                .isUserActive(true)
                .productIds(List.of(1L))
                .productsByQuantityMap(Map.of(UUID.randomUUID(), 1))
                .bankCardValidationDto(card)
                .build();
    }

    private BankCardValidationDto cardExpiring(YearMonth ym) {
        return BankCardValidationDto.builder()
                .expiryDate(ym.format(EXPIRY))
                .build();
    }

    @Test
    @DisplayName("Non-expired card: passes and propagates to the next link")
    void nonExpiredCardPassesAndPropagates() {
        validator.setNext(nextValidator);
        BankCardValidationDto card = cardExpiring(YearMonth.now().plusYears(2));
        OrderValidationDto dto = dtoWithCard(card);

        validator.validate(dto);

        verify(nextValidator).validate(dto);
    }

    @Test
    @DisplayName("Expired card: throws BankCardExpiredException and short-circuits the chain")
    void expiredCardThrowsAndShortCircuits() {
        validator.setNext(nextValidator);
        BankCardValidationDto card = cardExpiring(YearMonth.now().minusYears(2));
        OrderValidationDto dto = dtoWithCard(card);

        assertThatThrownBy(() -> validator.validate(dto))
                .isInstanceOf(BankCardExpiredException.class)
                .hasMessageContaining("Bank card expired");

        verify(nextValidator, never()).validate(any());
    }

    @Test
    @DisplayName("Boundary: card expiring this month is still valid (prod uses end-of-month)")
    void cardExpiringThisMonthIsStillValid() {
        validator.setNext(nextValidator);
        BankCardValidationDto card = cardExpiring(YearMonth.now());
        OrderValidationDto dto = dtoWithCard(card);

        validator.validate(dto);

        verify(nextValidator).validate(dto);
    }

    @Test
    @DisplayName("Boundary: card expired last month throws BankCardExpiredException")
    void cardExpiringLastMonthIsExpired() {
        BankCardValidationDto card = cardExpiring(YearMonth.now().minusMonths(1));
        OrderValidationDto dto = dtoWithCard(card);

        assertThatThrownBy(() -> validator.validate(dto))
                .isInstanceOf(BankCardExpiredException.class);
    }

    @Test
    @DisplayName("Null bank card: throws BankCardNotFoundException")
    void nullBankCardThrowsBankCardNotFoundException() {
        OrderValidationDto dto = dtoWithCard(null);

        assertThatThrownBy(() -> validator.validate(dto))
                .isInstanceOf(BankCardNotFoundException.class);
    }

    @Test
    @DisplayName("Malformed expiry date: DateConverter returns null and triggers NPE on isAfter")
    void malformedExpiryDateThrowsNpe() {
        BankCardValidationDto card = BankCardValidationDto.builder()
                .expiryDate("not-a-date")
                .build();
        OrderValidationDto dto = dtoWithCard(card);

        assertThatThrownBy(() -> validator.validate(dto))
                .isInstanceOf(NullPointerException.class);
    }
}
