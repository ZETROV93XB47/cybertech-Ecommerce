package com.novatech.cybertech.listener;

import com.novatech.cybertech.entities.ProductEntity;
import com.novatech.cybertech.entities.StockEntity;
import com.novatech.cybertech.entities.enums.ReservationStatus;
import com.novatech.cybertech.exceptions.ProductNotFoundException;
import com.novatech.cybertech.fixtures.builders.ProductEntityBuilder;
import com.novatech.cybertech.fixtures.builders.StockEntityBuilder;
import com.novatech.cybertech.repositories.ProductRepository;
import com.novatech.cybertech.repositories.StockRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.connection.DefaultMessage;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static com.novatech.cybertech.constants.CyberTechAppConstants.RESERVATION_KEY_PREFIX;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RedisExpirationListenerTest {

    @Mock
    private RedisMessageListenerContainer container;
    @Mock
    private StockRepository stockRepository;
    @Mock
    private ProductRepository productRepository;

    private RedisExpirationListener listener;

    @BeforeEach
    void setUp() {
        listener = new RedisExpirationListener(container, stockRepository, productRepository);
    }

    private static Message messageOf(String key) {
        return new DefaultMessage(new byte[0], key.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void onMessageHappyPath_validKey_shouldExpireAndReleaseStock() {
        UUID orderUuid = UUID.randomUUID();
        UUID productUuid = UUID.randomUUID();
        StockEntity reservation = StockEntityBuilder.aValidStockBuilder()
                .orderUuid(orderUuid).productUuid(productUuid).quantity(2).build();
        ProductEntity product = ProductEntityBuilder.aValidProductBuilder()
                .uuid(productUuid).reservedStock(5).build();

        when(stockRepository.findByOrderUuid(orderUuid)).thenReturn(List.of(reservation));
        when(productRepository.lockByUuid(productUuid)).thenReturn(Optional.of(product));

        listener.onMessage(messageOf(RESERVATION_KEY_PREFIX + orderUuid), null);

        ArgumentCaptor<StockEntity> stockCap = ArgumentCaptor.forClass(StockEntity.class);
        verify(stockRepository).save(stockCap.capture());
        assertThat(stockCap.getValue().getReservationStatus()).isEqualTo(ReservationStatus.EXPIRED);

        ArgumentCaptor<ProductEntity> prodCap = ArgumentCaptor.forClass(ProductEntity.class);
        verify(productRepository).save(prodCap.capture());
        assertThat(prodCap.getValue().getReservedStock()).isEqualTo(3);

        verify(stockRepository).deleteByOrderUuid(orderUuid);
    }

    @Test
    void onMessage_emptyReservations_shouldShortCircuitWithoutDelete() {
        UUID orderUuid = UUID.randomUUID();
        when(stockRepository.findByOrderUuid(orderUuid)).thenReturn(List.of());

        listener.onMessage(messageOf(RESERVATION_KEY_PREFIX + orderUuid), null);

        verify(stockRepository).findByOrderUuid(orderUuid);
        verify(stockRepository, never()).save(any());
        verify(stockRepository, never()).deleteByOrderUuid(any());
        verifyNoInteractions(productRepository);
    }

    @Test
    void onMessage_inactiveReservation_shouldBeSkippedButStillDelete() {
        UUID orderUuid = UUID.randomUUID();
        StockEntity inactive = StockEntityBuilder.aValidStockBuilder()
                .orderUuid(orderUuid)
                .reservationStatus(ReservationStatus.RELEASED)
                .build();
        when(stockRepository.findByOrderUuid(orderUuid)).thenReturn(List.of(inactive));

        listener.onMessage(messageOf(RESERVATION_KEY_PREFIX + orderUuid), null);

        verify(stockRepository, never()).save(any());
        verifyNoInteractions(productRepository);
        verify(stockRepository).deleteByOrderUuid(orderUuid);
    }

    @Test
    void onMessage_wrongPrefixKey_shouldReturnSilently() {
        listener.onMessage(messageOf("some:other:key:" + UUID.randomUUID()), null);

        verifyNoInteractions(stockRepository, productRepository);
    }

    @Test
    void onMessage_productMissingOnReservedRow_shouldThrowProductNotFound() {
        UUID orderUuid = UUID.randomUUID();
        UUID productUuid = UUID.randomUUID();
        StockEntity reservation = StockEntityBuilder.aValidStockBuilder()
                .orderUuid(orderUuid).productUuid(productUuid).quantity(1).build();

        when(stockRepository.findByOrderUuid(orderUuid)).thenReturn(List.of(reservation));
        when(productRepository.lockByUuid(productUuid)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> listener.onMessage(messageOf(RESERVATION_KEY_PREFIX + orderUuid), null))
                .isInstanceOf(ProductNotFoundException.class)
                .hasMessageContaining(productUuid.toString());
    }

    @Test
    @Disabled("BUG-121: RedisExpirationListener.onMessage does not guard UUID.fromString against malformed key tails; should swallow/log not propagate IllegalArgumentException")
    void bug121_malformedUuidAfterPrefix_shouldNotPropagateIllegalArgumentException() {
        // Expected behaviour after a fix: silently log + skip the malformed key, no exception out.
        listener.onMessage(messageOf(RESERVATION_KEY_PREFIX + "not-a-uuid"), null);
    }

    @Test
    void bug121_pin_malformedUuidAfterPrefixCurrentlyThrowsIllegalArgument() {
        // Pin: the current implementation propagates IAE up through the listener callback.
        assertThatThrownBy(() -> listener.onMessage(messageOf(RESERVATION_KEY_PREFIX + "not-a-uuid"), null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
