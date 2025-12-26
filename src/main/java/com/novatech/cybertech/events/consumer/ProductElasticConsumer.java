package com.novatech.cybertech.events.consumer;

import com.novatech.cybertech.repositories.ProductSearchRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ProductElasticConsumer {
    private final ProductSearchRepository productSearchRepository;

//    @KafkaListener(topics = "product-events", groupId = "search-group")
//    public void consumeProductEvent(ProductEvent event) {
//        log.info("Réception de l'event Kafka pour le produit : {}", event.id());
//
//        // Mise à jour de l'index Elasticsearch
//        ProductDocument doc = ProductDocument.builder()
//                .id(event.id())
//                .attributes(event.attributes()) // Ton champ flattened/nested
//                .build();
//
//        productSearchRepository.save(doc);
//    }
}