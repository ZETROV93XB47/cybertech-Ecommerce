package com.novatech.cybertech.entities;

import com.novatech.cybertech.entities.enums.*;
import io.hypersistence.utils.hibernate.type.json.JsonType;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.Type;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Entity
@Setter
@Getter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@ToString(callSuper = true, exclude = {"orderItemEntities", "reviewEntities"})
@Table(name = "productTable")
@EqualsAndHashCode(callSuper = true, exclude = {"orderItemEntities", "reviewEntities"})
public class ProductEntity extends BaseEntity<Long> {

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "price", nullable = false)
    private BigDecimal price;

    @Enumerated(EnumType.STRING)
    @Column(name = "brand", nullable = false)
    private Brand brand;

    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false)
    private Category category;

    @Column(name = "photo")
    private String photo;

    @Column(name = "stock", nullable = false)
    private Integer stock;

    @Column(name = "reservedStock", nullable = false)
    private Integer reservedStock = 0;

    @Column(name = "description", columnDefinition = "text")
    private String description;

    @Type(JsonType.class)
    @Column(columnDefinition = "json")
    private Map<String, Object> attributes = new HashMap<>();

    @OneToMany(mappedBy = "productEntity", cascade = CascadeType.ALL, fetch = FetchType.EAGER)
    private List<OrderItemEntity> orderItemEntities;

    @OneToMany(mappedBy = "productEntity", cascade = CascadeType.ALL, fetch = FetchType.EAGER)
    private List<ReviewEntity> reviewEntities;
}

//@OneToMany(mappedBy = "productEntity", cascade = CascadeType.ALL, fetch = FetchType.EAGER)
//private List<CartItemEntity> cartItemEntities;
