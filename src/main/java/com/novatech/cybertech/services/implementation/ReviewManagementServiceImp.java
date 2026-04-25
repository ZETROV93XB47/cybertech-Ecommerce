package com.novatech.cybertech.services.implementation;


import com.novatech.cybertech.dto.request.review.ReviewCreateRequestDto;
import com.novatech.cybertech.dto.request.review.ReviewUpdateRequestDto;
import com.novatech.cybertech.dto.response.moderation.ModerationResponseDto;
import com.novatech.cybertech.dto.response.review.ReviewResponseDto;
import com.novatech.cybertech.dto.response.review.ReviewableProductDto;
import com.novatech.cybertech.entities.OrderEntity;
import com.novatech.cybertech.entities.OrderItemEntity;
import com.novatech.cybertech.entities.ProductEntity;
import com.novatech.cybertech.entities.ReviewEntity;
import com.novatech.cybertech.entities.UserEntity;
import com.novatech.cybertech.entities.enums.OrderStatus;
import com.novatech.cybertech.exceptions.*;
import com.novatech.cybertech.mappers.entity.ReviewMapper;
import com.novatech.cybertech.repositories.OrderItemRepository;
import com.novatech.cybertech.repositories.OrderRepository;
import com.novatech.cybertech.repositories.ProductRepository;
import com.novatech.cybertech.repositories.ReviewRepository;
import com.novatech.cybertech.repositories.UserRepository;
import com.novatech.cybertech.services.core.ModerationService;
import com.novatech.cybertech.services.core.ReviewManagementService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static com.novatech.cybertech.utils.DataGenerator.generateReviewEntity;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReviewManagementServiceImp implements ReviewManagementService {

    private static final float HATEFUL_COMMENT_SCORE_THRESHOLD = 0.7f;

    private final ReviewMapper reviewMapper;
    private final UserRepository userRepository;
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final ReviewRepository reviewRepository;
    private final ModerationService moderationService;
    private final ProductRepository productRepository;


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

        checkIfUserAlreadyBoughtThisProduct(reviewCreateRequestDto, keycloakId, order, user);

        ReviewEntity reviewEntity = reviewMapper.mapFromCreationRequestToEntity(reviewCreateRequestDto);

        final ProductEntity product = productRepository.findByUuid(reviewCreateRequestDto.getProductUuid()).orElseThrow(() -> new ProductNotFoundException("Product related to this review doesn't exists"));

        //I ned to recompile the moderation api module cause made some changes in it

        ModerationResponseDto moderationResponseDto = moderationService.checkIfIsHateful(reviewEntity.getComment());
        if (moderationResponseDto.getScore() > HATEFUL_COMMENT_SCORE_THRESHOLD)
            throw new CommentPostNotAllowedException("Your comment looks similar to other hateful comments detected on our website, our moderation team will review it and decide to post it or not.");

        reviewEntity.setIsHateful(false);
        reviewEntity.setUserEntity(user);
        reviewEntity.setProductEntity(product);

        return reviewMapper.mapFromEntityToResponseDto(reviewRepository.save(reviewEntity));
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
            review.setComment(reviewCreateRequestDto.getComment());
        }

        return reviewMapper.mapFromEntityToResponseDto(reviewRepository.save(review));
    }


    /**
     * Orders are considered "reviewable" once the customer has actually paid for them. We
     * accept PAID / SHIPPED / DELIVERED so users can leave a review as soon as the payment
     * settles, without waiting for delivery.
     */
    private static final Set<OrderStatus> REVIEWABLE_ORDER_STATUSES =
            EnumSet.of(OrderStatus.PAID, OrderStatus.SHIPPED, OrderStatus.DELIVERED);

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
    @Transactional
    public void deleteByUUID(final UUID uuid, final String keycloakId) {
        final boolean userExists = userRepository.existsByKeycloakIdAndIsActive(keycloakId, true);
        if (!userExists) throw new UserNotFoundException("User that's trying to delete this comment doesn't exists or is not active");

        final ReviewEntity review = reviewRepository.findByUuid(uuid).orElseThrow(() -> new ReviewNotFoundException("No review with the UUID : " + uuid + " found"));

        boolean isCurrentUserAuthorOfTheRequestReview = review.getUserEntity().getKeycloakId().equals(keycloakId);

        if (!isCurrentUserAuthorOfTheRequestReview) throw new UserNotAuthorOfReviewException("Current review Doesn't belongs to the connected user");

        reviewRepository.deleteByUuid(uuid);
    }


    @Transactional
    public ReviewResponseDto saveReview() {
        ReviewEntity reviewEntity = generateReviewEntity();
        log.info(reviewEntity.toString());

        ModerationResponseDto moderationResponseDto = moderationService.checkIfIsHateful(reviewEntity.getComment());

        log.info("moderation service response : {}", moderationResponseDto.toString());

        if (moderationResponseDto.getScore() > HATEFUL_COMMENT_SCORE_THRESHOLD)
            throw new CommentPostNotAllowedException("Your comment seems similar to other hateful comments detected on our website, our moderation team will review it and decide to post it or not.");

        log.info("review saved");
        return reviewMapper.mapFromEntityToResponseDto(reviewRepository.save(reviewEntity));

    }


    private void checkIfUserAlreadyBoughtThisProduct(ReviewCreateRequestDto reviewCreateRequestDto, String keycloakId, OrderEntity order, UserEntity user) {
        final boolean orderContainsProduct = order.getOrderItemEntities().stream()
                .map(orderItemEntity -> orderItemEntity.getProductEntity().getUuid())
                .anyMatch(uuid -> reviewCreateRequestDto.getProductUuid().equals(uuid));

        //TODO: maybe make this part more explicit in the future, can be confusing
        if (!orderContainsProduct) {

            log.warn("Product is not part of current Order (the order sent in the request DTO), a search will be done in all user's orders");

            // Single boolean SQL query — replaces the in-memory walk over the lazy order graph
            // (user -> orders -> items -> product), which used to trigger a 4-level lazy load.
            final boolean isProductPartOfUserOrders =
                    orderItemRepository.userHasBoughtProduct(keycloakId, reviewCreateRequestDto.getProductUuid());

            if (!isProductPartOfUserOrders) {
                log.error("The product on which the user {} is trying to post a comment on is not a part of his order, maybe the product has already been bought in another order", keycloakId);
                throw new ProductNotFoundException("This product is not part of your order, you can put a review only on a product that you have already bought");
            }
        }
    }

    private static void isUserActive(final UserEntity user) {
        if (!user.getIsActive()) {
            log.error("User that's trying to post this comment is not active");
            throw new UserNotActiveException("User that's trying to post this comment is not active");
        }
    }
}
