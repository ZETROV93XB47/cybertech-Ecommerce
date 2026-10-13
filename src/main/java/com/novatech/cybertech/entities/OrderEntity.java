package com.novatech.cybertech.entities;

import com.novatech.cybertech.entities.enums.OrderStatus;
import com.novatech.cybertech.entities.enums.ShippingProvider;
import com.novatech.cybertech.entities.enums.ShippingType;
import com.novatech.cybertech.entities.valueObjects.Address;
import com.novatech.cybertech.entities.valueObjects.DiscountTermsSnapshot;
import com.novatech.cybertech.entities.valueObjects.Money;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;
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
     * Set once, the first time this order is actually handed to a shipping provider by
     * {@code ShipOrderTransactionalDelegate}. Unlike {@code status},
     * which {@code updateOrder}'s charge-difference path can legitimately regress from
     * {@code AWAITING_SHIPPING} back down to {@code AWAITING_PAYMENT} (to let the payment webhook
     * re-promote it to {@code PAID} and commit the resized stock reservation), this field is never
     * cleared — it is the idempotency marker that stops that regression from triggering a second,
     * real dispatch call to the carrier once the webhook re-promotes the order to {@code PAID}.
     */
    @Column(name = "shippedAt")
    private LocalDateTime shippedAt;

    /**
     * When the order last became fully paid — stamped on the transition to {@code PAID} (payment
     * webhook) and re-stamped by every successful {@code updateOrder}. Starts the modification
     * window: {@code ShipAllPaidOrdersTasklet} only ships orders paid for longer than
     * {@code cybertech.shipping.grace-period}, so a customer can still change a paid order.
     * Null on rows paid before this column existed — those are shipped without waiting.
     */
    @Column(name = "paidAt")
    private LocalDateTime paidAt;

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

    // --- Price breakdown, in the currency of totalAmount. Invariant once set:
    // totalAmount = max(0, subtotalAmount - discountAmount) + shippingCost.
    // Null on orders placed before the breakdown was persisted.

    /** Sum of the order lines ({@code unitPrice * quantity}) before discount and shipping. */
    @Column(name = "subtotalAmount", precision = 19, scale = 2)
    private BigDecimal subtotalAmount;

    /** Discount granted on {@link #subtotalAmount}; {@code 0} when none applies (or below the campaign minimum). */
    @Column(name = "discountAmount", precision = 19, scale = 2)
    private BigDecimal discountAmount;

    /** Shipping fee for {@link #shippingProvider} / {@link #shippingType}. */
    @Column(name = "shippingCost", precision = 19, scale = 2)
    private BigDecimal shippingCost;

    /**
     * Terms of the campaign {@link #discountKey} as they were when the order was placed. Kept
     * for the order's whole life so a modification re-prices with the same terms. Null on orders
     * placed before the snapshot existed.
     */
    @Embedded
    private DiscountTermsSnapshot discountTerms;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "userId")
    private UserEntity userEntity;

    @OneToMany(mappedBy = "orderEntity", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<OrderItemEntity> orderItemEntities;

    @OneToMany(mappedBy = "orderEntity", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<PaymentEntity> paymentAttempts = new ArrayList<>();
}
