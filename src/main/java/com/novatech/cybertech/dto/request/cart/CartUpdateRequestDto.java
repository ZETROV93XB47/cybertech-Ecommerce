package com.novatech.cybertech.dto.request.cart;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public class CartUpdateRequestDto {

    @NotNull(message = "Cart UUID cannot be null")
    private UUID cartUuid;

    @NotNull(message = "Order UUID cannot be null")
    private UUID orderUuid;

    //TODO: remplir ce dto plus tard ou même le supprimer
    // parce qu'un panier est lié à une commande et une fois
    // la commande validée, le panier n'a plis lieu d'être modifié
    // et on vuet faire simplei ici, donc,
}
