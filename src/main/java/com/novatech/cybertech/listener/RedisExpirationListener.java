package com.novatech.cybertech.listener;

import com.novatech.cybertech.entities.ProductEntity;
import com.novatech.cybertech.entities.StockEntity;
import com.novatech.cybertech.entities.enums.ReservationStatus;
import com.novatech.cybertech.exceptions.ProductNotFoundException;
import com.novatech.cybertech.repositories.ProductRepository;
import com.novatech.cybertech.repositories.StockRepository;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.listener.KeyExpirationEventMessageListener;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

import static com.novatech.cybertech.entities.enums.ReservationStatus.ACTIVE;

@Slf4j
@Component
public class RedisExpirationListener extends KeyExpirationEventMessageListener {

    private final StockRepository stockRepository;
    private final ProductRepository productRepository;

    public RedisExpirationListener(RedisMessageListenerContainer container,
                                   StockRepository stockRepository,
                                   ProductRepository productRepository) {
        super(container);
        this.productRepository = productRepository;
        this.stockRepository = stockRepository;
    }

    @Override
    @Transactional
    public void onMessage(Message message, byte[] pattern) {

        String key = message.toString();
        if (!key.startsWith("reservation:order:")) return;

        UUID orderUuid = UUID.fromString(key.replace("reservation:order:", ""));

        log.warn("Reservation expired for order {}", orderUuid);

        List<StockEntity> reservations = stockRepository.findByOrderUuid(orderUuid);
        if (reservations.isEmpty()) return;

        for (StockEntity r : reservations) {
            if (r.getReservationStatus() != ReservationStatus.ACTIVE) continue;

            // Marquer comme EXPIRED pour éviter double release
            r.setReservationStatus(ReservationStatus.EXPIRED);
            stockRepository.save(r);

            ProductEntity product = productRepository.lockByUuid(r.getProductUuid())
                    .orElseThrow(() -> new ProductNotFoundException("No product with the UUID : " + r.getProductUuid() + " found"));

            product.setReservedStock(product.getReservedStock() - r.getQuantity());
            productRepository.save(product);
        }

        // delete en 1 seule fois
        stockRepository.deleteByOrderUuid(orderUuid);
    }
}

