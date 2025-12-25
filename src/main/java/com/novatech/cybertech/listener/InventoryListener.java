package com.novatech.cybertech.listener;

import com.novatech.cybertech.dto.data.OrderEventDto;
import com.novatech.cybertech.entities.ProductEntity;
import com.novatech.cybertech.events.OrderCreatedEvent;
import com.novatech.cybertech.repositories.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.List;

//TODO: Vérifier la pertinence de cette classe, parce que ces opérations sont déjà réalisées dans le StockServiceImp

@Slf4j
@Component
@RequiredArgsConstructor
public class InventoryListener {
    /*

    private final ProductRepository productRepository;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void updateInventory(final OrderCreatedEvent event) {
        OrderEventDto order = event.getOrderEventDto();

        log.info("[InventoryListener] Mise à jour des stocks pour la commande #{}", order.getOrderUuid());

        final List<ProductEntity> orderProducts = productRepository.findAllByUuidIn(event.getOrderEventDto().getProductsByQuantityMap().keySet());

        List<ProductEntity> updatedProducts = orderProducts.stream().map(productEntity -> {
            if (event.getOrderEventDto().getProductsByQuantityMap().get(productEntity.getUuid()) != null) {
                productEntity.setStock(productEntity.getStock() - event.getOrderEventDto().getProductsByQuantityMap().get(productEntity.getUuid()));

                log.info("Produit: {} | Stock initial: {} | Stock final: {}", productEntity.getName(), productEntity.getStock(), productEntity.getStock());
            }
            return productEntity;
        }).toList();

        productRepository.saveAll(updatedProducts);
    }

     */
}