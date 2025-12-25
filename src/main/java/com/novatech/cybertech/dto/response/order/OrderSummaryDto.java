package com.novatech.cybertech.dto.response.order;

import com.novatech.cybertech.entities.enums.OrderStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Data // Génère getters, setters, toString, equals, hashCode
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderSummaryDto {

    private UUID orderUuid; // Identifiant externe de la commande

    private LocalDateTime orderDate;//Date et heure de la commande

    private OrderStatus status; //Statut actuel de la commande (ex: PENDING, SHIPPED, DELIVERED)

    private BigDecimal totalAmount; // Montant total de la commande

    private Integer totalltems; //Nombre total d'articles dans la commande (somme des quantités)

// Remarque : On exclut volontairement la liste détaillée des OrderltemDto ici.
// Les détails complets seraient disponibles via un endpoint dédié comme /api/v1/orders/[uuid)

}