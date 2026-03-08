package com.novatech.cybertech.entities;

import com.novatech.cybertech.entities.enums.DiscountType;
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
@Setter
@Getter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "orderTable")
@ToString(callSuper = true, exclude = {"orderItemEntities", "paymentAttempts"})
@EqualsAndHashCode(callSuper = true, exclude = {"orderItemEntities", "paymentAttempts"})
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

    @Column(name = "shippingType", nullable = false)
    @Enumerated(EnumType.STRING)
    private ShippingType shippingType;

    @Column(name = "shippingProvider", nullable = false)
    @Enumerated(EnumType.STRING)
    private ShippingProvider shippingProvider;

    @Column(name = "discountType", nullable = false)
    @Enumerated(EnumType.STRING)
    private DiscountType discountType;

    @ManyToOne
    @JoinColumn(name = "userId")
    private UserEntity userEntity;

    @OneToMany(mappedBy = "orderEntity", cascade = CascadeType.ALL, fetch = FetchType.EAGER)
    private List<OrderItemEntity> orderItemEntities;

    @OneToMany(mappedBy = "orderEntity", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<PaymentEntity> paymentAttempts = new ArrayList<>();
}
