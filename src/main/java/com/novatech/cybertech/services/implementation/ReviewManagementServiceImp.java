package com.novatech.cybertech.services.implementation;


import com.novatech.cybertech.dto.response.review.MyReviewDto;
import com.novatech.cybertech.dto.request.review.ReviewCreateRequestDto;
import com.novatech.cybertech.dto.request.review.ReviewUpdateRequestDto;
import com.novatech.cybertech.dto.response.moderation.ModerationResponseDto;
import com.novatech.cybertech.dto.response.review.ProductReviewsResponseDto;
import com.novatech.cybertech.dto.response.review.PublicReviewDto;
import com.novatech.cybertech.dto.response.review.ReviewResponseDto;
import com.novatech.cybertech.dto.response.review.ReviewableProductDto;
import com.novatech.cybertech.entities.OrderEntity;
import com.novatech.cybertech.entities.OrderItemEntity;
import com.novatech.cybertech.entities.ProductEntity;
import com.novatech.cybertech.entities.ReviewEntity;
import com.novatech.cybertech.entities.UserEntity;
import com.novatech.cybertech.entities.enums.OrderStatus;
import com.novatech.cybertech.events.ProductReviewsChangedEvent;
import com.novatech.cybertech.exceptions.*;
import com.novatech.cybertech.mappers.entity.ReviewMapper;
import com.novatech.cybertech.repositories.OrderItemRepository;
import com.novatech.cybertech.repositories.OrderRepository;
import com.novatech.cybertech.repositories.ProductRepository;
import com.novatech.cybertech.repositories.ReviewRepository;
import com.novatech.cybertech.repositories.UserRepository;
import com.novatech.cybertech.services.core.ModerationService;
import com.novatech.cybertech.services.core.ReviewManagementService;
import com.novatech.cybertech.utils.LogSafetyUtils;
import com.novatech.cybertech.utils.RecommendationSignalUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PagedModel;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReviewManagementServiceImp implements ReviewManagementService {

    /**
     * Orders are considered "reviewable" once the customer has actually paid for them. We
     * accept PAID / SHIPPED / DELIVERED so users can leave a review as soon as the payment
     * settles, without waiting for delivery. Pre-payment statuses (CREATED, AWAITING_PAYMENT,
     * PAYMENT_FAILED) and post-cancel statuses must NOT be reviewable.
     */
    // A paid order stays reviewable while it is prepared (AWAITING_SHIPPING, set by the ship batch),
    // until it is canceled, refunded or returned. Shared by create() and getReviewable().
    private static final Set<OrderStatus> REVIEWABLE_ORDER_STATUSES =
            EnumSet.of(OrderStatus.PAID, OrderStatus.AWAITING_SHIPPING, OrderStatus.SHIPPED, OrderStatus.DELIVERED);

    private static final String CREATED_AT = "createdAt";
    private static final String ID = "id";
    private static final Set<String> MY_REVIEWS_SORTABLE_FIELDS = Set.of(CREATED_AT, "updatedAt", "rating");
    private static final Sort MY_REVIEWS_DEFAULT_ORDER = Sort.by(Sort.Direction.DESC, CREATED_AT);
    /** Tie-breaker so pages stay stable when several reviews share the same sort value. */
    private static final Sort BY_ID_DESC = Sort.by(Sort.Direction.DESC, ID);

    private static final int MIN_RATING = 1;
    private static final int MAX_RATING = 5;
    private static final int AVERAGE_RATING_SCALE = 1;
    // Newest first; id breaks ties between reviews posted in the same instant so pages never overlap.
    private static final Sort PUBLISHED_REVIEWS_ORDER = Sort.by(Sort.Order.desc(CREATED_AT), Sort.Order.desc(ID));
    private static final String MODERATION_NO_VERDICT_MESSAGE = "Comment moderation returned no verdict, please try again in a few minutes.";
    private static final String COMMENT_REJECTED_MESSAGE = "Your comment looks similar to other hateful comments detected on our website, our moderation team will review it and decide to post it or not.";

    private final ReviewMapper reviewMapper;
    private final UserRepository userRepository;
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final ReviewRepository reviewRepository;
    private final ModerationService moderationService;
    private final ProductRepository productRepository;
    private final ApplicationEventPublisher eventPublisher;


    @Override
    @Transactional(readOnly = true)
    public ReviewResponseDto getByUUID(final UUID uuid) {
        return reviewMapper.mapFromEntityToResponseDto(reviewRepository.findByUuid(uuid).orElseThrow(() -> new ReviewNotFoundException("No review with the UUID : " + uuid + " found")));
    }


    @Override
    @Transactional
    public ReviewResponseDto create(final ReviewCreateRequestDto reviewCreateRequestDto, final String keycloakId) {

        final UserEntity user = userRepository.findByKeycloakIdAndIsActive(keycloakId, true).orElseThrow(() -> new UserNotFoundException("User that's trying to post this comment doesn't exists or is not active"));

        final OrderEntity order = orderRepository.findByUuid(reviewCreateRequestDto.getOrderUuid()).orElseThrow(() -> new OrderNotFoundException("Order related to this review doesn't exists, order UUID : " + reviewCreateRequestDto.getOrderUuid()));

        if (!order.getUserEntity().getKeycloakId().equals(keycloakId)) {
            throw new OrderDoesntBelongsToUserException("Order " + reviewCreateRequestDto.getOrderUuid() + " does not belong to the current user");
        }

        if (!REVIEWABLE_ORDER_STATUSES.contains(order.getStatus())) {
            throw new OrderNotReviewableException(
                    "Order " + order.getUuid() + " is not in a reviewable state: " + order.getStatus());
        }

        checkIfUserAlreadyBoughtThisProduct(reviewCreateRequestDto, keycloakId, order, user);

        ReviewEntity reviewEntity = reviewMapper.mapFromCreationRequestToEntity(reviewCreateRequestDto);

        final ProductEntity product = productRepository.findByUuid(reviewCreateRequestDto.getProductUuid()).orElseThrow(() -> new ProductNotFoundException("Product related to this review doesn't exists"));

        // A rating without a comment is a valid review: there is no text to moderate, and the
        // fail-closed moderation would otherwise risk rejecting it.
        reviewEntity.setComment(normalizeComment(reviewEntity.getComment()));
        if (reviewEntity.getComment() != null) {
            assertCommentPassesModeration(reviewEntity.getComment());
        }

        reviewEntity.setIsHateful(false);
        reviewEntity.setUserEntity(user);
        reviewEntity.setProductEntity(product);
        reviewEntity.setOrderUuid(order.getUuid());

        final ReviewEntity saved = reviewRepository.save(reviewEntity);
        publishReviewsChanged(product.getUuid());
        publishRecommendationSignal(keycloakId, reviewEntity, product);
        return reviewMapper.mapFromEntityToResponseDto(saved);
    }


    @Override
    @Transactional
    public ReviewResponseDto update(final ReviewUpdateRequestDto reviewCreateRequestDto, final String keycloakId) {

        //TODO: Optimisation potentielle ici, chercher directement en base les users actifs
        final boolean userExists = userRepository.existsByKeycloakIdAndIsActive(keycloakId, true);
        if (!userExists) throw new UserNotFoundException("User that's trying to post this comment doesn't exists or is not active");

        final ReviewEntity review = reviewRepository.findByUuid(reviewCreateRequestDto.getReviewUuid()).orElseThrow(() -> new ReviewNotFoundException("No review with the UUID : " + reviewCreateRequestDto.getReviewUuid() + " found"));

        boolean isCurrentUserAuthorOfTheRequestReview = review.getUserEntity().getKeycloakId().equals(keycloakId);

        if (!isCurrentUserAuthorOfTheRequestReview) throw new UserNotAuthorOfReviewException("Current review Doesn't belongs to the connected user");

        // Patch the loaded entity in-place rather than saving a freshly-mapped one with null FKs
        // (mapping the update DTO to a brand-new entity dropped userEntity / productEntity, which
        // are non-null FKs — Hibernate then bricked the row on flush).
        if (reviewCreateRequestDto.getRating() != null) {
            review.setRating(reviewCreateRequestDto.getRating());
        }
        if (reviewCreateRequestDto.getComment() != null) {
            // Reviews are public: an edited comment goes through the same moderation as a new one,
            // otherwise a clean review could be edited into a hateful one after publication. A
            // rejected edit throws before the entity is touched, so the published text stays.
            // A blank comment removes the text and leaves a rating-only review: nothing to moderate.
            final String comment = normalizeComment(reviewCreateRequestDto.getComment());
            if (comment != null) {
                assertCommentPassesModeration(comment);
            }
            review.setComment(comment);
        }

        final ReviewEntity saved = reviewRepository.save(review);
        publishReviewsChanged(review.getProductEntity().getUuid());
        publishRecommendationSignal(keycloakId, review, review.getProductEntity());
        return reviewMapper.mapFromEntityToResponseDto(saved);
    }


    @Override
    @Transactional(readOnly = true)
    public PagedModel<MyReviewDto> getMyReviews(final String keycloakId, final Pageable pageable) {
        final Sort allowedSort = Sort.by(pageable.getSort().stream()
                .filter(order -> MY_REVIEWS_SORTABLE_FIELDS.contains(order.getProperty()))
                .toList());
        final Sort sort = (allowedSort.isSorted() ? allowedSort : MY_REVIEWS_DEFAULT_ORDER).and(BY_ID_DESC);
        final Page<MyReviewDto> reviews = reviewRepository
                .findByAuthorKeycloakId(keycloakId, PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), sort))
                .map(reviewMapper::mapFromEntityToMyReviewDto);
        log.info("Reviews of user {}: page {} ({} of {} review(s))", LogSafetyUtils.maskUuid(keycloakId),
                reviews.getNumber(), reviews.getNumberOfElements(), reviews.getTotalElements());
        return new PagedModel<>(reviews);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ReviewableProductDto> getReviewableProducts(final String keycloakId) {
        if (!userRepository.existsByKeycloakIdAndIsActive(keycloakId, true)) {
            throw new UserNotFoundException("User does not exist or is not active: " + keycloakId);
        }

        final Set<UUID> alreadyReviewed = reviewRepository.findReviewedProductUuidsByUserKeycloakId(keycloakId);

        // Single SQL round-trip: orders + items + products fetched together via JOIN FETCH,
        // so the downstream stream walk never triggers a lazy load.
        return orderRepository.findReviewableOrdersWithItemsByKeycloakIdAndStatusIn(keycloakId, REVIEWABLE_ORDER_STATUSES).stream()
                .flatMap(order -> order.getOrderItemEntities().stream()
                        .map(item -> toReviewable(order, item)))
                .filter(dto -> !alreadyReviewed.contains(dto.productUuid()))
                .toList();
    }

    private ReviewableProductDto toReviewable(final OrderEntity order, final OrderItemEntity item) {
        final ProductEntity product = item.getProductEntity();
        return ReviewableProductDto.builder()
                .productUuid(product.getUuid())
                .orderUuid(order.getUuid())
                .productName(product.getName())
                .orderDate(order.getOrderDate() == null ? null : order.getOrderDate().toLocalDate())
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public ProductReviewsResponseDto getPublishedReviewsOfProduct(final UUID productUuid, final Pageable pageable) {
        if (productRepository.findByUuid(productUuid).isEmpty()) {
            throw new ProductNotFoundException("No product with the UUID : " + productUuid + " found");
        }

        final Map<Integer, Long> countByRating = reviewRepository.countPublishedByRating(productUuid).stream()
                .collect(Collectors.toMap(ReviewRepository.RatingCount::getRating, ReviewRepository.RatingCount::getCount));
        final Map<String, Long> distribution = new LinkedHashMap<>();
        IntStream.rangeClosed(MIN_RATING, MAX_RATING)
                .forEach(rating -> distribution.put(String.valueOf(rating), countByRating.getOrDefault(rating, 0L)));
        final long reviewCount = countByRating.values().stream().mapToLong(Long::longValue).sum();
        final long ratingSum = countByRating.entrySet().stream().mapToLong(e -> e.getKey() * e.getValue()).sum();
        final BigDecimal averageRating = reviewCount == 0
                ? null
                : BigDecimal.valueOf(ratingSum).divide(BigDecimal.valueOf(reviewCount), AVERAGE_RATING_SCALE, RoundingMode.HALF_UP);

        final Pageable page = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), PUBLISHED_REVIEWS_ORDER);
        final Page<PublicReviewDto> reviews = reviewRepository.findPublishedByProductUuid(productUuid, page)
                .map(reviewMapper::mapFromEntityToPublicReviewDto);

        log.info("Public reviews of product {}: {} review(s), average {}, page {} ({} item(s))",
                productUuid, reviewCount, averageRating, reviews.getNumber(), reviews.getNumberOfElements());
        return ProductReviewsResponseDto.builder()
                .averageRating(averageRating)
                .reviewCount(reviewCount)
                .distribution(distribution)
                .reviews(new PagedModel<>(reviews))
                .build();
    }

    @Override
    @Transactional
    public void deleteByUUID(final UUID uuid, final String keycloakId) {
        final boolean userExists = userRepository.existsByKeycloakIdAndIsActive(keycloakId, true);
        if (!userExists) throw new UserNotFoundException("User that's trying to delete this comment doesn't exists or is not active");

        final ReviewEntity review = reviewRepository.findByUuid(uuid).orElseThrow(() -> new ReviewNotFoundException("No review with the UUID : " + uuid + " found"));

        boolean isCurrentUserAuthorOfTheRequestReview = review.getUserEntity().getKeycloakId().equals(keycloakId);

        if (!isCurrentUserAuthorOfTheRequestReview) throw new UserNotAuthorOfReviewException("Current review Doesn't belongs to the connected user");

        reviewRepository.deleteByUuid(uuid);
        publishReviewsChanged(review.getProductEntity().getUuid());
    }

    /** The product's average rating / review count in the search index is refreshed after commit. */
    private void publishReviewsChanged(final UUID productUuid) {
        eventPublisher.publishEvent(new ProductReviewsChangedEvent(this, productUuid));
    }


    /**
     * Recommendation signal of the review as published (rating and comment after the change). Only
     * reached once moderation accepted the comment: a refusal (403) or an unavailable moderation
     * (503) threw before, and the transaction rollback drops the event anyway.
     */
    private void publishRecommendationSignal(final String keycloakId, final ReviewEntity review, final ProductEntity product) {
        RecommendationSignalUtils.reviewSignalType(review.getRating(), review.getComment())
                .ifPresent(type -> RecommendationSignalUtils.publish(eventPublisher, this,
                        List.of(RecommendationSignalUtils.signal(type, keycloakId, product))));
    }

    /** Blank comments are stored as {@code null}: a review without text is a rating-only review. */
    private static String normalizeComment(final String comment) {
        return StringUtils.hasText(comment) ? comment : null;
    }

    /**
     * Fails closed on a missing / malformed moderation verdict: a null response or null verdict
     * means we could NOT confirm the comment is clean, so the comment is not published. That is an
     * unavailability of the moderator (503), not a rejection of the text (403 is reserved for a
     * comment judged hateful).
     * NOTE: isHateful (not the raw score) is the classifier's verdict — the score alone is just
     * the confidence for whichever label ("toxic" or its opposite) won, so a clean comment
     * classified with high confidence would otherwise be rejected, and a toxic comment
     * classified with low confidence would otherwise slip through.
     */
    private void assertCommentPassesModeration(final String comment) {
        final ModerationResponseDto moderationResponseDto = moderationService.checkIfIsHateful(comment);
        if (moderationResponseDto == null || moderationResponseDto.getIsHateful() == null) {
            throw new ModerationUnavailableException(MODERATION_NO_VERDICT_MESSAGE);
        }
        if (moderationResponseDto.getIsHateful()) {
            throw new CommentPostNotAllowedException(COMMENT_REJECTED_MESSAGE);
        }
    }

    private void checkIfUserAlreadyBoughtThisProduct(ReviewCreateRequestDto reviewCreateRequestDto, String keycloakId, OrderEntity order, UserEntity user) {
        final boolean orderContainsProduct = order.getOrderItemEntities().stream()
                .map(orderItemEntity -> orderItemEntity.getProductEntity().getUuid())
                .anyMatch(uuid -> reviewCreateRequestDto.getProductUuid().equals(uuid));

        //TODO: maybe make this part more explicit in the future, can be confusing
        if (!orderContainsProduct) {

            log.info("Review: product {} not in the referenced order, checking the user's other orders", reviewCreateRequestDto.getProductUuid());

            // Single boolean SQL query — replaces the in-memory walk over the lazy order graph
            // (user -> orders -> items -> product), which used to trigger a 4-level lazy load.
            final boolean isProductPartOfUserOrders =
                    orderItemRepository.userHasBoughtProduct(keycloakId, reviewCreateRequestDto.getProductUuid());

            if (!isProductPartOfUserOrders) {
                log.warn("Review rejected: user {} never bought product {}", LogSafetyUtils.maskUuid(keycloakId), reviewCreateRequestDto.getProductUuid());
                throw new ProductNotFoundException("This product is not part of your order, you can put a review only on a product that you have already bought");
            }
        }
    }

}
