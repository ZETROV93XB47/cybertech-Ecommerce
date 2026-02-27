package com.novatech.cybertech.mappers.entity;

import com.novatech.cybertech.dto.request.product.ProductCreateRequestDto;
import com.novatech.cybertech.dto.request.product.ProductUpdateRequestDto;
import com.novatech.cybertech.dto.response.product.ProductResponseDto;
import com.novatech.cybertech.entities.document.ProductDocument;
import com.novatech.cybertech.entities.ProductEntity;
import com.novatech.cybertech.entities.ReviewEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;
import org.mapstruct.ReportingPolicy;

import java.util.List;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface ProductMapper extends BaseMapper<ProductEntity, ProductCreateRequestDto, ProductUpdateRequestDto, ProductResponseDto> {
    @Override
    @Mapping(target = "reservedStock", expression = "java(0)")
    ProductEntity mapFromCreationRequestToEntity(ProductCreateRequestDto productCreateRequestDto);

    ProductResponseDto mapFromProductDocumentToProductResponseDto(ProductDocument productDocument);

    @Mapping(source = "uuid", target = "id")
        // Mappe l'UUID de l'entité vers l'ID du document
        //@Mapping(source = "reviewEntities", target = "averageRating", qualifiedByName = "calculateAverageRating")
        //@Mapping(source = "reviewEntities", target = "reviewCount", qualifiedByName = "calculateReviewCount")
    ProductDocument mapFromProductEntityToProductDocument(final ProductEntity entity);

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
