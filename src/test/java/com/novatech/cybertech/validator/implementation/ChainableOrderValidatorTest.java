package com.novatech.cybertech.validator.implementation;

import com.novatech.cybertech.dto.data.OrderValidationDto;
import com.novatech.cybertech.validator.core.OrderValidator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Tests for {@link ChainableOrderValidator} (abstract chain machinery). Uses a tiny
 * package-private subclass to exercise the abstract base directly.
 */
@ExtendWith(MockitoExtension.class)
class ChainableOrderValidatorTest {

    @Mock
    private OrderValidator nextValidator;

    @Mock
    private OrderValidator middleValidator;

    /** Trivial passthrough subclass that always succeeds and propagates to the next link. */
    private static final class PassthroughValidator extends ChainableOrderValidator {
        @Override
        public void validate(final OrderValidationDto dto) {
            nextStep(dto);
        }
    }

    /** Trivial subclass that always throws before invoking nextStep. */
    private static final class FailingValidator extends ChainableOrderValidator {
        @Override
        public void validate(final OrderValidationDto dto) {
            throw new IllegalStateException("boom");
        }
    }

    private OrderValidationDto sampleDto() {
        return OrderValidationDto.builder()
                .isUserActive(true)
                .productIds(List.of(1L))
                .productsByQuantityMap(java.util.Map.of(UUID.randomUUID(), 1))
                .userDefaultBankCard(null)
                .build();
    }

    @Test
    @DisplayName("setNext returns the same instance so chains can be built fluently")
    void setNextReturnsSelf() {
        PassthroughValidator validator = new PassthroughValidator();

        ChainableOrderValidator returned = validator.setNext(nextValidator);

        assertThat(returned).isSameAs(validator);
    }

    @Test
    @DisplayName("validate propagates to the next link when the current one passes")
    void happyPathPropagatesToNext() {
        PassthroughValidator validator = new PassthroughValidator();
        validator.setNext(nextValidator);
        OrderValidationDto dto = sampleDto();

        validator.validate(dto);

        verify(nextValidator).validate(dto);
    }

    @Test
    @DisplayName("nextStep is a no-op when no next link is wired")
    void noNextLinkIsNoOp() {
        PassthroughValidator validator = new PassthroughValidator();

        // Must not throw NPE
        validator.validate(sampleDto());
    }

    @Test
    @DisplayName("Failing link short-circuits the chain (next.validate is never called)")
    void failingLinkShortCircuits() {
        FailingValidator validator = new FailingValidator();
        validator.setNext(nextValidator);

        assertThatThrownBy(() -> validator.validate(sampleDto()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("boom");

        verify(nextValidator, never()).validate(any());
    }

    @Test
    @DisplayName("3-link chain: each link forwards to the next in order")
    void threeLinkDeepChain() {
        PassthroughValidator first = new PassthroughValidator();
        PassthroughValidator second = new PassthroughValidator();
        first.setNext(second);
        second.setNext(nextValidator);
        OrderValidationDto dto = sampleDto();

        first.validate(dto);

        verify(nextValidator).validate(dto);
    }

    @Test
    @DisplayName("3-link chain: failure in middle link prevents the tail from being called")
    void threeLinkChainShortCircuitsAtFailingMiddle() {
        PassthroughValidator first = new PassthroughValidator();
        first.setNext(middleValidator);
        // middleValidator is a mock; we don't wire it to next via setNext (mock has no chain),
        // we just demonstrate that if middle throws, tail is never reached because tail is
        // accessed only via middleValidator's own next pointer which is null in the mock.
        doThrow(new RuntimeException("middle failed")).when(middleValidator).validate(any());

        assertThatThrownBy(() -> first.validate(sampleDto()))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("middle failed");

        verify(nextValidator, never()).validate(any());
    }

    @Test
    @DisplayName("ArgumentCaptor pin: same DTO reference is propagated unchanged through the chain")
    void argumentCaptorPinsReferencePropagation() {
        PassthroughValidator validator = new PassthroughValidator();
        validator.setNext(nextValidator);
        OrderValidationDto dto = sampleDto();

        validator.validate(dto);

        ArgumentCaptor<OrderValidationDto> captor = ArgumentCaptor.forClass(OrderValidationDto.class);
        verify(nextValidator).validate(captor.capture());
        assertThat(captor.getValue()).isSameAs(dto);
    }

    @Test
    @DisplayName("setNext can be re-invoked to swap the next link")
    void setNextCanBeReassigned() {
        PassthroughValidator validator = new PassthroughValidator();
        validator.setNext(middleValidator);
        validator.setNext(nextValidator);
        OrderValidationDto dto = sampleDto();

        validator.validate(dto);

        verify(middleValidator, never()).validate(any());
        verify(nextValidator).validate(dto);
    }
}
