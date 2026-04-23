package com.novatech.cybertech.entities;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;

@Entity
@Getter
@Setter
@SuperBuilder
@EqualsAndHashCode(callSuper = true, exclude = {"orderEntity", "productEntity"})
@NoArgsConstructor
@AllArgsConstructor
@ToString(callSuper = true, exclude = {"orderEntity", "productEntity"})
@Table(name = "orderItemTable")
public class OrderItemEntity extends BaseEntity<Long> {

    @Column(name = "quantity", nullable = false)
    private Integer quantity;

    @Column(name = "unitPrice", precision = 10, scale = 2, nullable = false)
    private BigDecimal unitPrice;

    @Column(name = "subtotal", precision = 10, scale = 2, nullable = false)
    private BigDecimal subtotal;

    @ManyToOne(fetch = FetchType.LAZY,cascade=CascadeType.ALL)
    @JoinColumn(name = "orderId", nullable = false)
    private OrderEntity orderEntity;

    @ManyToOne(fetch = FetchType.LAZY,cascade=CascadeType.ALL)
    @JoinColumn(name = "productEntity", nullable = false)
    private ProductEntity productEntity;
}
