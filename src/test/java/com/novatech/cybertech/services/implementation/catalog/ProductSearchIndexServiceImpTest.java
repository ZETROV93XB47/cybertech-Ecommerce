package com.novatech.cybertech.services.implementation.catalog;

import com.novatech.cybertech.entities.ProductEntity;
import com.novatech.cybertech.entities.document.ProductDocument;
import com.novatech.cybertech.fixtures.builders.ProductEntityBuilder;
import com.novatech.cybertech.mappers.entity.ProductMapper;
import com.novatech.cybertech.repositories.ProductRepository;
import com.novatech.cybertech.repositories.ProductSearchRepository;
import com.novatech.cybertech.repositories.ReviewRepository;
import com.novatech.cybertech.repositories.ReviewRepository.RatingStats;
import com.novatech.cybertech.services.implementation.ProductSearchIndexServiceImp;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.mapping.IndexCoordinates;
import org.springframework.data.elasticsearch.core.query.UpdateQuery;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductSearchIndexServiceImpTest {

    private static final IndexCoordinates PRODUCTS = IndexCoordinates.of("products");

    @Mock private ProductMapper productMapper;
    @Mock private ProductRepository productRepository;
    @Mock private ProductSearchRepository productSearchRepository;
    @Mock private ReviewRepository reviewRepository;
    @Mock private ElasticsearchOperations elasticsearchOperations;

    @InjectMocks
    private ProductSearchIndexServiceImp service;

    private static RatingStats stats(final UUID productUuid, final double average, final long count) {
        return new RatingStats() {
            @Override
            public UUID getProductUuid() {
                return productUuid;
            }

            @Override
            public Double getAverageRating() {
                return average;
            }

            @Override
            public Long getReviewCount() {
                return count;
            }
        };
    }

    private ProductDocument stubDocument(final ProductEntity product) {
        final ProductDocument document = ProductDocument.builder().id(product.getUuid().toString()).uuid(product.getUuid()).build();
        when(productMapper.mapFromProductEntityToProductDocument(product)).thenReturn(document);
        return document;
    }

    @Test
    @DisplayName("index: the document carries the published-review statistics")
    void indexCarriesRatings() {
        final ProductEntity product = ProductEntityBuilder.aValidProduct();
        final ProductDocument document = stubDocument(product);
        when(reviewRepository.findPublishedRatingStats(product.getUuid()))
                .thenReturn(Optional.of(stats(product.getUuid(), 4.5, 2)));

        service.index(product);

        verify(productSearchRepository).save(document);
        assertThat(document.getAverageRating()).isEqualTo(4.5);
        assertThat(document.getReviewCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("index: without published review, averageRating is null and reviewCount 0")
    void indexWithoutReviews() {
        final ProductEntity product = ProductEntityBuilder.aValidProduct();
        final ProductDocument document = stubDocument(product);
        when(reviewRepository.findPublishedRatingStats(product.getUuid())).thenReturn(Optional.empty());

        service.index(product);

        assertThat(document.getAverageRating()).isNull();
        assertThat(document.getReviewCount()).isZero();
    }

    @Test
    @DisplayName("refreshRatings: partial update of averageRating / reviewCount only")
    void refreshRatingsPartialUpdate() {
        final UUID productUuid = UUID.randomUUID();
        when(productSearchRepository.existsById(productUuid.toString())).thenReturn(true);
        when(reviewRepository.findPublishedRatingStats(productUuid)).thenReturn(Optional.of(stats(productUuid, 3.0, 4)));
        when(elasticsearchOperations.getIndexCoordinatesFor(ProductDocument.class)).thenReturn(PRODUCTS);

        service.refreshRatings(productUuid);

        final ArgumentCaptor<UpdateQuery> query = ArgumentCaptor.forClass(UpdateQuery.class);
        verify(elasticsearchOperations).update(query.capture(), any(IndexCoordinates.class));
        assertThat(query.getValue().getId()).isEqualTo(productUuid.toString());
        assertThat(query.getValue().getDocument()).containsOnlyKeys("averageRating", "reviewCount")
                .containsEntry("averageRating", 3.0)
                .containsEntry("reviewCount", 4);
    }

    @Test
    @DisplayName("refreshRatings: last published review removed -> averageRating null, reviewCount 0")
    void refreshRatingsWithoutReviews() {
        final UUID productUuid = UUID.randomUUID();
        when(productSearchRepository.existsById(productUuid.toString())).thenReturn(true);
        when(reviewRepository.findPublishedRatingStats(productUuid)).thenReturn(Optional.empty());
        when(elasticsearchOperations.getIndexCoordinatesFor(ProductDocument.class)).thenReturn(PRODUCTS);

        service.refreshRatings(productUuid);

        final ArgumentCaptor<UpdateQuery> query = ArgumentCaptor.forClass(UpdateQuery.class);
        verify(elasticsearchOperations).update(query.capture(), any(IndexCoordinates.class));
        assertThat(query.getValue().getDocument()).containsEntry("averageRating", null).containsEntry("reviewCount", 0);
    }

    @Test
    @DisplayName("refreshRatings: product not indexed -> no update")
    void refreshRatingsNotIndexed() {
        final UUID productUuid = UUID.randomUUID();
        when(productSearchRepository.existsById(productUuid.toString())).thenReturn(false);

        service.refreshRatings(productUuid);

        verify(elasticsearchOperations, never()).update(any(UpdateQuery.class), any(IndexCoordinates.class));
    }

    @Test
    @DisplayName("reindexAll: every product indexed with its own statistics, in batches of 500")
    void reindexAllInBatches() {
        final List<ProductEntity> products = new ArrayList<>(IntStream.range(0, 501)
                .mapToObj(i -> ProductEntityBuilder.aValidProduct()).toList());
        final ProductEntity rated = products.getFirst();
        when(productRepository.findAll()).thenReturn(products);
        when(reviewRepository.findPublishedRatingStatsOfAllProducts()).thenReturn(List.of(stats(rated.getUuid(), 5.0, 1)));
        final List<ProductDocument> documents = products.stream().map(this::stubDocument).toList();

        final int indexed = service.reindexAll();

        assertThat(indexed).isEqualTo(501);
        verify(productSearchRepository, times(2)).saveAll(anyList());
        assertThat(documents.getFirst().getAverageRating()).isEqualTo(5.0);
        assertThat(documents.getFirst().getReviewCount()).isEqualTo(1);
        assertThat(documents.get(1).getAverageRating()).isNull();
        assertThat(documents.get(1).getReviewCount()).isZero();
    }
}
