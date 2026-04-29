package com.novatech.cybertech.validator.implementation;

import com.novatech.cybertech.dto.data.OrderValidationDto;
import com.novatech.cybertech.exceptions.UserNotActiveException;
import com.novatech.cybertech.validator.core.OrderValidator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ActiveUserValidatorTest {

    @Mock
    private OrderValidator nextValidator;

    private final ActiveUserValidator validator = new ActiveUserValidator();

    private OrderValidationDto dtoWithUserActive(boolean active) {
        return OrderValidationDto.builder()
                .isUserActive(active)
                .productIds(List.of(1L))
                .productsByQuantityMap(Map.of(UUID.randomUUID(), 1))
                .userDefaultBankCard(null)
                .build();
    }

    @Test
    @DisplayName("Active user: validation passes and the next link is invoked")
    void activeUserPassesAndPropagates() {
        validator.setNext(nextValidator);
        OrderValidationDto dto = dtoWithUserActive(true);

        validator.validate(dto);

        verify(nextValidator).validate(dto);
    }

    @Test
    @DisplayName("Active user with no next link: validate returns without throwing")
    void activeUserNoNextLink() {
        validator.validate(dtoWithUserActive(true));
    }

    @Test
    @DisplayName("Inactive user: throws UserNotActiveException (mapped to HTTP 403 by ErrorManagementController) and short-circuits chain")
    void inactiveUserThrowsAndShortCircuits() {
        // FIX(EXCEPTION-MAPPING): the validator now raises UserNotActiveException so the global
        // @ControllerAdvice can return a structured 403 instead of leaking IllegalStateException
        // as an opaque HTTP 500. The English message is the new contract surface.
        validator.setNext(nextValidator);
        OrderValidationDto dto = dtoWithUserActive(false);

        assertThatThrownBy(() -> validator.validate(dto))
                .isInstanceOf(UserNotActiveException.class)
                .hasMessage("User is inactive");

        verify(nextValidator, never()).validate(any());
    }

    @Test
    @DisplayName("Null DTO: throws NullPointerException")
    void nullDtoThrowsNpe() {
        assertThatThrownBy(() -> validator.validate(null))
                .isInstanceOf(NullPointerException.class);
    }
}
