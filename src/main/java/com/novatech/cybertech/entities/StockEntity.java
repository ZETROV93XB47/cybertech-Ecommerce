package com.novatech.cybertech.entities;

import com.novatech.cybertech.entities.enums.ReservationStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.util.UUID;

@Entity
@Getter
@Setter
@SuperBuilder
@EqualsAndHashCode
@NoArgsConstructor
@AllArgsConstructor
@Table(
        name = "stockTable",
        uniqueConstraints = @UniqueConstraint(columnNames = {"order_uuid", "product_uuid"}),
        indexes = {
                @Index(name = "idx_stock_order_uuid", columnList = "order_uuid"),
                @Index(name = "idx_stock_product_uuid", columnList = "product_uuid")
        }
)
public class StockEntity extends BaseEntity<Long> {

    @Column(name = "order_uuid", nullable = false)
    private UUID orderUuid;

    @Column(name = "product_uuid", nullable = false)
    private UUID productUuid;

    @Column(name = "quantity", nullable = false)
    private int quantity;

    @Enumerated(EnumType.STRING)
    @Column(name = "reservationStatus", nullable = false)
    private ReservationStatus reservationStatus;
}
