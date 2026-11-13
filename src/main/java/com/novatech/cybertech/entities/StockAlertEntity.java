package com.novatech.cybertech.entities;

import com.novatech.cybertech.entities.enums.StockAlertStatus;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.time.LocalDateTime;

/**
 * "Tell me when this product is back in stock" request of a user. One row per (user, product):
 * subscribing again after the alert was sent, cancelled or expired puts the same row back to
 * {@link StockAlertStatus#PENDING}. The unique constraint closes the check-then-insert race of
 * two concurrent subscriptions (same reasoning as {@link WishlistEntity}).
 */
@Entity
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "stockAlertTable",
        uniqueConstraints = @UniqueConstraint(name = "uk_stock_alert_user_product", columnNames = {"userId", "productId"}),
        indexes = @Index(name = "idx_stock_alert_status_product", columnList = "status, productId"))
@ToString(callSuper = true, exclude = {"user", "product"})
@EqualsAndHashCode(callSuper = true, exclude = {"user", "product"})
public class StockAlertEntity extends BaseEntity<Long> {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "userId", nullable = false)
    private UserEntity user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "productId", nullable = false)
    private ProductEntity product;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private StockAlertStatus status;

    /** Last time the user (re)subscribed: start of the expiry period. */
    @Column(name = "requestedAt", nullable = false)
    private LocalDateTime requestedAt;

    @Column(name = "notifiedAt")
    private LocalDateTime notifiedAt;
}
