package com.novatech.cybertech.entities;

import com.novatech.cybertech.entities.enums.DiscountType;
import com.novatech.cybertech.entities.enums.OrderStatus;
import com.novatech.cybertech.entities.enums.ShippingProvider;
import com.novatech.cybertech.entities.enums.ShippingType;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

//Est ce qu'il y a un intéret à garder la partie cart dans ce cas ? tous les éléments commandés par le user sont dans son order

@Entity
@Setter
@Getter
@SuperBuilder
@EqualsAndHashCode
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "orderTable")
@ToString(callSuper = true)
public class OrderEntity extends BaseEntity {

    @Column(name = "orderDate", nullable = false)
    private LocalDateTime orderDate;

    @Column(name = "totalAmount", nullable = false)
    private BigDecimal totalAmount = BigDecimal.valueOf(0);

    @Column(name = "shippingAddress", nullable = false)
    private String shippingAddress;

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
}

    /*
    @OneToOne(cascade = CascadeType.ALL)
    @JoinColumn(name = "cartId")
    private CartEntity cartEntity;
     */
