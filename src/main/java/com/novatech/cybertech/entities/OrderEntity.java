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
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.List;

//Est ce qu'il y a un intéret à garder la partie cart dans ce cas ? tous les éléments commandés par le user sont dans son order

@Entity
@Setter
@Getter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "orderTable")
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
@EntityListeners(AuditingEntityListener.class)
public class OrderEntity extends BaseEntity {

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
    @ToString.Exclude
    private List<OrderItemEntity> orderItemEntities;

    @OneToOne(fetch = FetchType.EAGER, cascade = CascadeType.ALL)
    @JoinColumn(name = "paymentId")
    private PaymentEntity paymentEntity;

    @LastModifiedDate
    @Column(name = "updatedAt")
    private LocalDateTime updatedAt;
}

    /*
    @OneToOne(cascade = CascadeType.ALL)
    @JoinColumn(name = "cartId")
    private CartEntity cartEntity;
     */
