package com.novatech.cybertech.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

/**
 * Admin-managed registry of product category "shapes" — one row per category, carrying the
 * JSON Schema that {@code ProductValidationService} compiles and validates incoming product
 * {@code attributes} payloads against. Replaces the hardcoded {@code Category} enum + per-category
 * Java records: adding a new product category is now an admin API call, not a redeploy.
 *
 * <p>Hydrated at startup and kept in a Caffeine cache ({@code ProductCategorySchemaCacheImp}),
 * invalidated across instances via Redis Pub/Sub on every admin write.
 */
@Entity
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@Table(
        name = "productCategorySchemaTable",
        uniqueConstraints = @UniqueConstraint(name = "uk_product_category_schema_categoryKey", columnNames = "categoryKey")
)
@ToString(callSuper = true)
public class ProductCategorySchemaEntity extends BaseEntity<Long> {

    /** Free-form category identifier (e.g. {@code "COMPUTER"}), chosen by the admin at registration time. */
    @Column(name = "categoryKey", nullable = false, unique = true, length = 100)
    private String categoryKey;

    /** Human-readable display name (e.g. {@code "Computers"}). */
    @Column(name = "label", nullable = false, length = 255)
    private String label;

    /** Raw JSON Schema text describing/validating this category's {@code attributes} payload. */
    @Column(name = "jsonSchema", nullable = false, columnDefinition = "TEXT")
    private String jsonSchema;

    /** Soft-disable without deleting — existing products already carrying this category are unaffected. */
    @Column(name = "active", nullable = false)
    private boolean active;
}
