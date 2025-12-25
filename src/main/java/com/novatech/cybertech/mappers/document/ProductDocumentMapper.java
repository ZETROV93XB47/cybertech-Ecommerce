package com.novatech.cybertech.mappers.document;

import com.novatech.cybertech.entities.ProductDocument;
import com.novatech.cybertech.entities.ProductEntity;
import com.novatech.cybertech.entities.ReviewEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

import java.util.List;

@Mapper(componentModel = "spring")
public interface ProductDocumentMapper {

    @Mapping(source = "uuid", target = "id") // Mappe l'UUID de l'entité vers l'ID du document
    @Mapping(source = "reviewEntities", target = "averageRating", qualifiedByName = "calculateAverageRating")
    @Mapping(source = "reviewEntities", target = "reviewCount", qualifiedByName = "calculateReviewCount")
    ProductDocument toDocument(final ProductEntity entity);

    List<ProductDocument> toDocuments(final List<ProductEntity> entities);

    /**
     * Calcule la note moyenne à partir d'une liste d'avis.
     * MapStruct utilisera cette méthode pour le champ 'averageRating'.
     */
    @Named("calculateAverageRating")
    default Double calculateAverageRating(List<ReviewEntity> reviews) {
        if (reviews == null || reviews.isEmpty()) {
            return 0.0;
        }
        return reviews.stream()
                .mapToDouble(ReviewEntity::getRating)
                .average()
                .orElse(0.0);
    }

    /**
     * Calcule le nombre total d'avis.
     * MapStruct utilisera cette méthode pour le champ 'reviewCount'.
     */
    @Named("calculateReviewCount")
    default Integer calculateReviewCount(List<ReviewEntity> reviews) {
        if (reviews == null) {
            return 0;
        }
        return reviews.size();
    }
}