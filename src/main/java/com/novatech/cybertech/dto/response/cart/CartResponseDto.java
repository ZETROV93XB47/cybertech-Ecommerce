package com.novatech.cybertech.dto.response.cart;


import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CartResponseDto {

    private UUID cartUuid;
    private UUID userUuid; // L'UUID de l'utilisateur propriétaire du panier
    private List<CartItemResponseDto> items;
    private BigDecimal totalPrice; // Le prix total de tous les articles dans le panier
    private Date createdAt;
    private Date updatedAt;
    // Tu pourrais ajouter un statut si les paniers peuvent avoir différents états (ex: "ACTIVE", "ABANDONED")
    // private String status;
}