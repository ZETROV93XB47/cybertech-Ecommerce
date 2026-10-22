package com.novatech.cybertech.services.implementation;

import com.novatech.cybertech.entities.ProductEntity;
import com.novatech.cybertech.entities.document.ProductDocument;
import com.novatech.cybertech.mappers.entity.ProductMapper;
import com.novatech.cybertech.repositories.ProductRepository;
import com.novatech.cybertech.repositories.ProductSearchRepository;
import com.novatech.cybertech.repositories.ReviewRepository;
import com.novatech.cybertech.repositories.ReviewRepository.RatingStats;
import com.novatech.cybertech.services.core.ProductSearchIndexService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.document.Document;
import org.springframework.data.elasticsearch.core.query.UpdateQuery;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.novatech.cybertech.utils.ProductSearchQueryUtils.AVERAGE_RATING_FIELD;
import static com.novatech.cybertech.utils.ProductSearchQueryUtils.REVIEW_COUNT_FIELD;

/**
 * See {@link ProductSearchIndexService} for the contract's intent.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProductSearchIndexServiceImp implements ProductSearchIndexService {

    private static final int REINDEX_BATCH_SIZE = 500;

    private final ProductMapper productMapper;
    private final ProductRepository productRepository;
    private final ProductSearchRepository productSearchRepository;
    private final ReviewRepository reviewRepository;
    private final ElasticsearchOperations elasticsearchOperations;

    @Override
    public void index(final ProductEntity product) {
        productSearchRepository.save(toDocument(product, reviewRepository.findPublishedRatingStats(product.getUuid())));
    }

    @Override
    @Transactional(readOnly = true)
    public void refreshRatings(final UUID productUuid) {
        final String documentId = productUuid.toString();
        if (!productSearchRepository.existsById(documentId)) {
            log.info("Product {} is not indexed — no rating to refresh", productUuid);
            return;
        }
        final Optional<RatingStats> stats = reviewRepository.findPublishedRatingStats(productUuid);
        // Partial update: only the two rating fields, so a concurrent product re-index is not overwritten.
        final Document ratings = Document.create();
        ratings.put(AVERAGE_RATING_FIELD, averageRating(stats));
        ratings.put(REVIEW_COUNT_FIELD, reviewCount(stats));
        elasticsearchOperations.update(UpdateQuery.builder(documentId).withDocument(ratings).build(),
                elasticsearchOperations.getIndexCoordinatesFor(ProductDocument.class));
        log.info("Product {} ratings re-indexed: average {}, {} review(s)", productUuid, averageRating(stats), reviewCount(stats));
    }

    @Override
    @Transactional(readOnly = true)
    public int reindexAll() {
        final Map<UUID, RatingStats> statsByProduct = reviewRepository.findPublishedRatingStatsOfAllProducts().stream()
                .collect(Collectors.toMap(RatingStats::getProductUuid, Function.identity()));
        final List<ProductDocument> documents = productRepository.findAll().stream()
                .map(product -> toDocument(product, Optional.ofNullable(statsByProduct.get(product.getUuid()))))
                .toList();
        for (int from = 0; from < documents.size(); from += REINDEX_BATCH_SIZE) {
            productSearchRepository.saveAll(documents.subList(from, Math.min(from + REINDEX_BATCH_SIZE, documents.size())));
        }
        log.info("Re-indexed {} product(s), {} with published reviews", documents.size(), statsByProduct.size());
        return documents.size();
    }

    private ProductDocument toDocument(final ProductEntity product, final Optional<RatingStats> stats) {
        final ProductDocument document = productMapper.mapFromProductEntityToProductDocument(product);
        document.setAverageRating(averageRating(stats));
        document.setReviewCount(reviewCount(stats));
        return document;
    }

    private static Double averageRating(final Optional<RatingStats> stats) {
        return stats.map(RatingStats::getAverageRating).orElse(null);
    }

    private static int reviewCount(final Optional<RatingStats> stats) {
        return stats.map(RatingStats::getReviewCount).map(Long::intValue).orElse(0);
    }
}
