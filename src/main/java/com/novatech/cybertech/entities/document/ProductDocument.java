package com.novatech.cybertech.entities.document;

import jakarta.persistence.Id;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;
import org.springframework.data.elasticsearch.annotations.InnerField;
import org.springframework.data.elasticsearch.annotations.MultiField;
import org.springframework.data.elasticsearch.annotations.Setting;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Search-side projection of a product. The index settings declare the {@code sort_normalizer}
 * (lowercase + asciifolding) used by {@code name.keyword}. A change of this mapping is applied to an
 * existing index by {@code ProductSearchIndexInitializer} (recreate + reindex from MySQL).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(indexName = "products")
@Setting(settingPath = "/elasticsearch/product-settings.json")
public class ProductDocument {

    @Id
    private String id;

    /** Keyword: exact value, used as the final tie-break of every sort. */
    @Field(type = FieldType.Keyword, name = "uuid")
    private UUID uuid;

    // --- Champs pour la recherche plein texte (analysés) ---

    /** Full-text on {@code name}; {@code name.keyword} (normalized) sorts A → Z regardless of case and accents. */
    @MultiField(
            mainField = @Field(type = FieldType.Text, name = "name", analyzer = "french"),
            otherFields = @InnerField(suffix = "keyword", type = FieldType.Keyword, normalizer = "sort_normalizer"))
    private String name;

    @Field(type = FieldType.Text, name = "description", analyzer = "french")
    private String description;

    // --- Champs pour le filtrage, le tri et les agrégations (non-analysés) ---

    @Field(type = FieldType.Keyword, name = "brand")
    private String brand; // Utiliser String pour les enums est plus flexible

    @Field(type = FieldType.Keyword, name = "category")
    private String category;

    @Field(type = FieldType.Object)
    private Map<String, Object> attributes;

    // --- Champs numériques pour les filtres de plage et le tri ---
    @Field(type = FieldType.Double, name = "price")
    private BigDecimal price;

    @Field(type = FieldType.Text, name = "photoUrls")
    private List<String> photoUrls;

    // --- Champs dérivés des avis publiés, pour le tri « mieux notés » ---

    /** Average of the published reviews' ratings; null without any published review. */
    @Field(type = FieldType.Double, name = "averageRating")
    private Double averageRating;

    /** Number of published reviews; 0 without any. */
    @Field(type = FieldType.Integer, name = "reviewCount")
    private Integer reviewCount;
}
