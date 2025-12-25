package com.novatech.cybertech.dto.request.cart;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CartCreateRequestDto {

    @NotNull(message = "Cart UUID cannot be null")
    private UUID cartUuid;

    @NotNull(message = "Order UUID cannot be null")
    private UUID orderUuid;



    // Les articles du panier (CartItemEntity) sont généralement gérés séparément
    // après la création du panier.
    // Si vous souhaitez permettre la création d'un panier avec des articles initiaux,
    // vous ajouteriez ici une List<CartItemCreateRequestDto>.
}