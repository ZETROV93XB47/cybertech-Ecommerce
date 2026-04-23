package com.novatech.cybertech.entities;

import com.novatech.cybertech.entities.enums.Brand;
import com.novatech.cybertech.entities.enums.Category;
import io.hypersistence.utils.hibernate.type.json.JsonType;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.Type;

import java.math.BigDecimal;
import java.util.*;

@Entity
@Setter
@Getter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "productTable")
@ToString(callSuper = true, exclude = {"orderItemEntities", "reviewEntities", "wishlistEntries", "recommendations"})
@EqualsAndHashCode(callSuper = true, exclude = {"orderItemEntities", "reviewEntities", "wishlistEntries", "recommendations"})
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

    @OneToMany(mappedBy = "productEntity", /*cascade = CascadeType.ALL,*/ fetch = FetchType.LAZY)
    private List<OrderItemEntity> orderItemEntities;

    @OneToMany(mappedBy = "productEntity", /*cascade = CascadeType.ALL,*/ fetch = FetchType.LAZY)
    private List<ReviewEntity> reviewEntities;

    @OneToMany(mappedBy = "product", /*cascade = CascadeType.ALL,*/ orphanRemoval = true)
    private Set<WishlistEntity> wishlistEntries = new HashSet<>();

    @OneToMany(mappedBy = "product", /*cascade = CascadeType.ALL,*/ orphanRemoval = true)
    private Set<RecommendationEntity> recommendations = new HashSet<>();
}
