package com.novatech.cybertech.services.implementation;

import com.novatech.cybertech.dto.data.NotificationContext;
import com.novatech.cybertech.dto.data.OrderEventDto;
import com.novatech.cybertech.dto.data.UserContactDto;
import com.novatech.cybertech.entities.NotificationEntity;
import com.novatech.cybertech.entities.enums.CommunicationChanel;
import com.novatech.cybertech.entities.enums.NotificationStatus;
import com.novatech.cybertech.entities.enums.NotificationType;
import com.novatech.cybertech.entities.enums.OrderStatus;
import com.novatech.cybertech.entities.enums.PaymentAttemptStatus;
import com.novatech.cybertech.entities.enums.ShippingProvider;
import com.novatech.cybertech.entities.enums.ShippingType;
import com.novatech.cybertech.repositories.NotificationRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link NotificationOutcomeRecorder}.
 *
 * <p>The recorder is the single point of truth for {@link NotificationEntity}
 * audit rows; these tests pin the three contracts called out in the Phase 1
 * spec: happy SENT row, FAILED row carrying the error message, and the
 * defensive "swallow serialization failure but still persist" path.
 */
@ExtendWith(MockitoExtension.class)
class NotificationOutcomeRecorderTest {

    @Mock
    private NotificationRepository notificationRepository;

    // Use a real Jackson 3 mapper so polymorphic round-trip is actually
    // exercised. The defensive-path test below substitutes a sabotaged
    // mapper via an explicit constructor call.
    private final ObjectMapper objectMapper = JsonMapper.builder().build();

    private NotificationContext<ShippingConfirmationPayload> shippingContext(final UUID orderUuid) {
        return NotificationContext.<ShippingConfirmationPayload>builder()
                .notificationType(NotificationType.SHIPPING_CONFIRMATION)
                .communicationChanel(CommunicationChanel.EMAIL)
                .subject("Your order has shipped")
                .templatePath("email/shipping-confirmation")
                .user(UserContactDto.builder()
                        .name("Jane")
                        .email("jane@example.com")
                        .phoneNumber("+33600000000")
                        .defaultCommunicationChanel(CommunicationChanel.EMAIL)
                        .build())
                .payload(ShippingConfirmationPayload.builder()
                        .orderUuid(orderUuid)
                        .userName("Jane")
                        .shippingType(ShippingType.STANDARD)
                        .shippingProvider(ShippingProvider.DHL)
                        .build())
                .build();
    }

    private NotificationContext<?> orderConfirmationContext(final UUID orderUuid) {
        // Mirror the OrderEventListener shape: orderEventDto stashed in data,
        // no typed payload set.
        final Map<String, Object> data = new HashMap<>();
        data.put("orderEventDto", OrderEventDto.builder()
                .orderUuid(orderUuid)
                .totalAmount(new BigDecimal("100.00"))
                .orderStatus(OrderStatus.CREATED)
                .shippingType(ShippingType.STANDARD)
                .shippingProvider(ShippingProvider.DHL)
                .userContactDto(UserContactDto.builder()
                        .name("Bob").email("bob@example.com")
                        .defaultCommunicationChanel(CommunicationChanel.EMAIL).build())
                .paymentAttemptStatus(PaymentAttemptStatus.CREATED)
                .build());
        return NotificationContext.builder()
                .notificationType(NotificationType.ORDER_CONFIRMATION)
                .user(UserContactDto.builder()
                        .name("Bob").email("bob@example.com")
                        .defaultCommunicationChanel(CommunicationChanel.EMAIL).build())
                .data(data)
                .build();
    }

    private NotificationOutcomeRecorder makeRecorder(final ObjectMapper mapper) {
        return new NotificationOutcomeRecorder(notificationRepository, mapper);
    }

    @Test
    @DisplayName("happy: persists SENT row with payload populated and sentAt set")
    void happyPathPersistsSentRowWithPayload() {
        final NotificationOutcomeRecorder recorder = makeRecorder(objectMapper);
        final UUID orderUuid = UUID.randomUUID();
        final NotificationContext<ShippingConfirmationPayload> ctx = shippingContext(orderUuid);
        when(notificationRepository.save(any(NotificationEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        final NotificationEntity persisted = recorder.recordOutcome(ctx, NotificationStatus.SENT, 0, null);

        final ArgumentCaptor<NotificationEntity> cap = ArgumentCaptor.forClass(NotificationEntity.class);
        org.mockito.Mockito.verify(notificationRepository).save(cap.capture());
        final NotificationEntity saved = cap.getValue();

        assertThat(saved).isSameAs(persisted);
        assertThat(saved.getOrderUuid()).isEqualTo(orderUuid);
        assertThat(saved.getNotificationType()).isEqualTo(NotificationType.SHIPPING_CONFIRMATION);
        assertThat(saved.getCommunicationChannel()).isEqualTo(CommunicationChanel.EMAIL);
        assertThat(saved.getStatus()).isEqualTo(NotificationStatus.SENT);
        assertThat(saved.getRecipient()).isEqualTo("jane@example.com");
        assertThat(saved.getRetryCount()).isZero();
        assertThat(saved.getLastAttemptAt()).isNotNull();
        assertThat(saved.getSentAt()).isNotNull();
        assertThat(saved.getErrorMessage()).isNull();
        assertThat(saved.getPayload())
                .as("payload column carries the JSON-serialised redrive snapshot")
                .isNotBlank()
                .contains("SHIPPING_CONFIRMATION")
                .contains("jane@example.com");
    }

    @Test
    @DisplayName("happy: order-confirmation context (no typed payload) extracts orderUuid from the data map")
    void orderConfirmationContextExtractsOrderUuidFromDataMap() {
        final NotificationOutcomeRecorder recorder = makeRecorder(objectMapper);
        final UUID orderUuid = UUID.randomUUID();
        final NotificationContext<?> ctx = orderConfirmationContext(orderUuid);
        when(notificationRepository.save(any(NotificationEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        recorder.recordOutcome(ctx, NotificationStatus.SENT, 0, null);

        final ArgumentCaptor<NotificationEntity> cap = ArgumentCaptor.forClass(NotificationEntity.class);
        org.mockito.Mockito.verify(notificationRepository).save(cap.capture());
        assertThat(cap.getValue().getOrderUuid()).isEqualTo(orderUuid);
    }

    @Test
    @DisplayName("failure: persists FAILED row with errorMessage truncated and payload populated")
    void failurePathPersistsFailedRowWithErrorAndPayload() {
        final NotificationOutcomeRecorder recorder = makeRecorder(objectMapper);
        final UUID orderUuid = UUID.randomUUID();
        final NotificationContext<ShippingConfirmationPayload> ctx = shippingContext(orderUuid);
        when(notificationRepository.save(any(NotificationEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        // Build a message longer than the 1000-char column cap.
        final String longMessage = "x".repeat(1500);
        final RuntimeException failure = new RuntimeException(longMessage);

        recorder.recordOutcome(ctx, NotificationStatus.FAILED, 3, failure);

        final ArgumentCaptor<NotificationEntity> cap = ArgumentCaptor.forClass(NotificationEntity.class);
        org.mockito.Mockito.verify(notificationRepository).save(cap.capture());
        final NotificationEntity saved = cap.getValue();

        assertThat(saved.getStatus()).isEqualTo(NotificationStatus.FAILED);
        assertThat(saved.getRetryCount()).isEqualTo(3);
        assertThat(saved.getSentAt()).as("sentAt is null on FAILED").isNull();
        assertThat(saved.getLastAttemptAt()).isNotNull();
        assertThat(saved.getErrorMessage())
                .as("errorMessage truncated to fit @Column(length=1000)")
                .hasSize(1000);
        assertThat(saved.getPayload()).isNotBlank();
    }

    @Test
    @DisplayName("defensive: serialization failure is swallowed; row is still persisted with payload=null and the call does not throw")
    void serializationFailureStillPersistsRow() {
        // Sabotage the mapper so writeValueAsString throws.
        final ObjectMapper sabotaged = spy(objectMapper);
        when(sabotaged.writeValueAsString(any())).thenThrow(new RuntimeException("simulated jackson failure"));

        final NotificationOutcomeRecorder recorder = makeRecorder(sabotaged);
        final NotificationContext<ShippingConfirmationPayload> ctx = shippingContext(UUID.randomUUID());
        when(notificationRepository.save(any(NotificationEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        assertThatCode(() -> recorder.recordOutcome(ctx, NotificationStatus.SENT, 0, null))
                .doesNotThrowAnyException();

        final ArgumentCaptor<NotificationEntity> cap = ArgumentCaptor.forClass(NotificationEntity.class);
        org.mockito.Mockito.verify(notificationRepository).save(cap.capture());
        final NotificationEntity saved = cap.getValue();
        // Row persisted, but payload column is null because we lost the trace.
        assertThat(saved.getPayload()).isNull();
        assertThat(saved.getStatus()).isEqualTo(NotificationStatus.SENT);
        assertThat(saved.getRecipient()).isEqualTo("jane@example.com");
    }
}
