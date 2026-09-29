package com.novatech.cybertech.entities;

import com.novatech.cybertech.entities.enums.OrderStatus;
import com.novatech.cybertech.entities.enums.ShippingProvider;
import com.novatech.cybertech.entities.enums.ShippingType;
import com.novatech.cybertech.entities.valueObjects.Address;
import com.novatech.cybertech.entities.valueObjects.Money;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;


@Entity
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@Table(
        name = "orderTable",
        indexes = @Index(name = "idx_order_status_date", columnList = "status, orderDate")
)
@ToString(callSuper = true, exclude = {"userEntity", "orderItemEntities", "paymentAttempts"})
@EqualsAndHashCode(callSuper = true, exclude = {"userEntity", "orderItemEntities", "paymentAttempts"})
public class OrderEntity extends BaseEntity<Long> {

    @Column(name = "orderDate", nullable = false)
    private LocalDateTime orderDate;

    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "amount", column = @Column(name = "totalAmount", nullable = false)),
            @AttributeOverride(name = "currencyCode", column = @Column(name = "currency", nullable = false))
    })
    private Money totalAmount;

    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "street", column = @Column(name = "shippingStreet")),
            @AttributeOverride(name = "city", column = @Column(name = "shippingCity")),
            @AttributeOverride(name = "zipCode", column = @Column(name = "shippingZipCode")),
            @AttributeOverride(name = "country", column = @Column(name = "shippingCountry"))
    })
    private Address shippingAddress;

    @Column(name = "status", nullable = false)
    @Enumerated(EnumType.STRING)
    private OrderStatus status;

    /**
     * Set once, the first time this order is actually handed to a shipping provider — by either
     * {@code ShippingListener} or {@code ShipOrderTransactionalDelegate}. Unlike {@code status},
     * which {@code updateOrder}'s charge-difference path can legitimately regress from
     * {@code AWAITING_SHIPPING} back down to {@code AWAITING_PAYMENT} (to let the payment webhook
     * re-promote it to {@code PAID} and commit the resized stock reservation), this field is never
     * cleared — it is the idempotency marker that stops that regression from triggering a second,
     * real dispatch call to the carrier once the webhook republishes {@code OrderPaidEvent}.
     */
    @Column(name = "shippedAt")
    private LocalDateTime shippedAt;

    @Column(name = "shippingType", nullable = false)
    @Enumerated(EnumType.STRING)
    private ShippingType shippingType;

    @Column(name = "shippingProvider", nullable = false)
    @Enumerated(EnumType.STRING)
    private ShippingProvider shippingProvider;

    /**
     * The {@code discountKey} of the campaign applied at purchase time (or {@link
     * com.novatech.cybertech.constants.CyberTechAppConstants#NO_DISCOUNT_KEY}). Free-form String,
     * not an enum — see {@link com.novatech.cybertech.entities.DiscountCampaignEntity#getDiscountKey()}
     * for why. Column name kept as {@code discountType} (ddl-auto=update caveat, see there).
     */
    @Column(name = "discountType", nullable = false)
    private String discountKey;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "userId")
    private UserEntity userEntity;

    @OneToMany(mappedBy = "orderEntity", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<OrderItemEntity> orderItemEntities;

    @OneToMany(mappedBy = "orderEntity", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<PaymentEntity> paymentAttempts = new ArrayList<>();
}
