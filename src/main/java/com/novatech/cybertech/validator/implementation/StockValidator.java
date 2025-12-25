package com.novatech.cybertech.validator.implementation;

import com.novatech.cybertech.dto.data.OrderValidationDto;
import com.novatech.cybertech.entities.ProductEntity;
import com.novatech.cybertech.repositories.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class StockValidator extends ChainableOrderValidator {

    private final ProductRepository productRepository;

    @Override
    public void validate(final OrderValidationDto orderValidationDto) {

        orderValidationDto.getProductsByQuantityMap().forEach((productId, productQt) -> {
            ProductEntity product = productRepository.findByUuid(productId).orElseThrow(() -> new IllegalArgumentException("Produit introuvable"));
            if (product.getStock() < productQt) {
                throw new IllegalStateException("Stock insuffisant pour le produit : " + product.getName());
            }
        });
        nextStep(orderValidationDto);
    }
}
