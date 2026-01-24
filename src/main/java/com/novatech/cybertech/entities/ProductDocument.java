package com.novatech.cybertech.entities;

import jakarta.persistence.Id;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(indexName = "products")
public class ProductDocument {

    @Id
    private String id;


    private UUID uuid;

    // --- Champs pour la recherche plein texte (analysés) ---

    @Field(type = FieldType.Text, name = "name", analyzer = "french")
    private String name;

    @Field(type = FieldType.Text, name = "description", analyzer = "french")
    private String description;

    // --- Champs pour le filtrage, le tri et les agrégations (non-analysés) ---

    @Field(type = FieldType.Keyword, name = "brand")
    private String brand; // Utiliser String pour les enums est plus flexible

    @Field(type = FieldType.Keyword, name = "category")
    private String category;

    @Field(type = FieldType.Flattened, name = "attributes")
    private Map<String, Object> attributes;

    // --- Champs numériques pour les filtres de plage et le tri ---
    @Field(type = FieldType.Double, name = "price")
    private BigDecimal price;
}

    // --- Champs dérivés pour la pertinence et le tri ---

//    @Field(type = FieldType.Double, name = "averageRating")
//    private Double averageRating;
//
//    @Field(type = FieldType.Integer, name = "reviewCount")
//    private Integer reviewCount;
