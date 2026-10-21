package com.novatech.cybertech.services.implementation.support;

import org.springframework.data.web.PagedModel;
import com.novatech.cybertech.dto.response.review.MyReviewDto;
import com.novatech.cybertech.dto.request.review.ReviewCreateRequestDto;
import com.novatech.cybertech.dto.request.review.ReviewUpdateRequestDto;
import com.novatech.cybertech.dto.response.moderation.ModerationResponseDto;
import com.novatech.cybertech.dto.response.review.ProductReviewsResponseDto;
import com.novatech.cybertech.dto.response.review.PublicReviewDto;
import com.novatech.cybertech.dto.response.review.ReviewResponseDto;
import com.novatech.cybertech.dto.response.review.ReviewableProductDto;
import com.novatech.cybertech.entities.enums.OrderStatus;
import com.novatech.cybertech.entities.OrderEntity;
import com.novatech.cybertech.entities.OrderItemEntity;
import com.novatech.cybertech.entities.ProductEntity;
import com.novatech.cybertech.entities.ReviewEntity;
import com.novatech.cybertech.entities.UserEntity;
import com.novatech.cybertech.exceptions.CommentPostNotAllowedException;
import com.novatech.cybertech.exceptions.OrderDoesntBelongsToUserException;
import com.novatech.cybertech.exceptions.OrderNotFoundException;
import com.novatech.cybertech.exceptions.OrderNotReviewableException;
import com.novatech.cybertech.exceptions.ProductNotFoundException;
import com.novatech.cybertech.exceptions.ReviewNotFoundException;
import com.novatech.cybertech.exceptions.UserNotAuthorOfReviewException;
import com.novatech.cybertech.exceptions.UserNotFoundException;
import com.novatech.cybertech.fixtures.builders.OrderEntityBuilder;
import com.novatech.cybertech.fixtures.builders.OrderItemEntityBuilder;
import com.novatech.cybertech.fixtures.builders.ProductEntityBuilder;
import com.novatech.cybertech.fixtures.builders.ReviewEntityBuilder;
import com.novatech.cybertech.fixtures.builders.UserEntityBuilder;
import com.novatech.cybertech.mappers.entity.ReviewMapper;
import com.novatech.cybertech.repositories.OrderItemRepository;
import com.novatech.cybertech.repositories.OrderRepository;
import com.novatech.cybertech.repositories.ProductRepository;
import com.novatech.cybertech.repositories.ReviewRepository;
import com.novatech.cybertech.repositories.UserRepository;
import com.novatech.cybertech.services.core.ModerationService;
import com.novatech.cybertech.services.implementation.ReviewManagementServiceImp;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link ReviewManagementServiceImp}.
 *
 * Pins the fact that {@code create} only checks whether the product appears anywhere in the
 * caller's order history, NOT whether the referenced {@code orderUuid} belongs to the caller.
 * User A can therefore review user B's order so long as user A has bought the same product elsewhere.
 */
@ExtendWith(MockitoExtension.class)
class ReviewManagementServiceImpTest {

    @Mock private ReviewMapper reviewMapper;
    @Mock private UserRepository userRepository;
    @Mock private OrderRepository orderRepository;
    @Mock private OrderItemRepository orderItemRepository;
    @Mock private ReviewRepository reviewRepository;
    @Mock private ModerationService moderationService;
    @Mock private ProductRepository productRepository;

    @InjectMocks
    private ReviewManagementServiceImp service;

    private final String keycloakId = "kc-1";
    private UUID productUuid;
    private UUID orderUuid;
    private UUID reviewUuid;

    @BeforeEach
    void seed() {
        productUuid = UUID.randomUUID();
        orderUuid = UUID.randomUUID();
        reviewUuid = UUID.randomUUID();
    }

    private ProductEntity productWith(final UUID uuid) {
        return ProductEntityBuilder.aValidProductBuilder().uuid(uuid).build();
    }

    private OrderEntity orderContainingProduct(final UUID uuid, final UserEntity owner) {
        // Default to a reviewable status (PAID) so happy-path tests do not trip the order status guard.
        return orderContainingProduct(uuid, owner, OrderStatus.PAID);
    }

    private OrderEntity orderContainingProduct(final UUID uuid, final UserEntity owner, final OrderStatus status) {
        ProductEntity p = productWith(uuid);
        OrderItemEntity oi = OrderItemEntityBuilder.aValidOrderItemBuilder().productEntity(p).build();
        List<OrderItemEntity> items = new ArrayList<>();
        items.add(oi);
        return OrderEntityBuilder.aValidOrderBuilder().uuid(orderUuid).orderItemEntities(items).userEntity(owner).status(status).build();
    }

    /** Convenience overload: builds a caller-owned order (keycloakId = {@code "kc-1"}). */
    private OrderEntity callerOrderContainingProduct(final UUID uuid) {
        UserEntity caller = UserEntityBuilder.aValidUserBuilder().keycloakId(keycloakId).build();
        return orderContainingProduct(uuid, caller);
    }

    private ReviewCreateRequestDto createDto() {
        // userUuid removed from the DTO — identity is now derived from the JWT in the controller.
        return ReviewCreateRequestDto.builder()
                .orderUuid(orderUuid)
                .productUuid(productUuid)
                .rating(5)
                .comment("Excellent")
                .build();
    }

    // ============================ getByUUID ============================

    @Nested
    class GetByUuid {

        @Test
        @DisplayName("happy: returns mapped DTO for an existing review")
        void getsReviewByUuid() {
            ReviewEntity entity = ReviewEntityBuilder.aValidReviewBuilder().uuid(reviewUuid).build();
            ReviewResponseDto dto = ReviewResponseDto.builder().uuid(reviewUuid).build();
            when(reviewRepository.findByUuid(reviewUuid)).thenReturn(Optional.of(entity));
            when(reviewMapper.mapFromEntityToResponseDto(entity)).thenReturn(dto);

            ReviewResponseDto result = service.getByUUID(reviewUuid);

            assertThat(result).isSameAs(dto);
        }

        @Test
        @DisplayName("missing review uuid raises ReviewNotFoundException with the uuid in the message")
        void unknownReviewThrows() {
            when(reviewRepository.findByUuid(reviewUuid)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.getByUUID(reviewUuid))
                    .isInstanceOf(ReviewNotFoundException.class)
                    .hasMessageContaining(reviewUuid.toString());
        }
    }

    // ============================ create ============================

    @Nested
    class Create {

        private ReviewEntity entityFromDto(final ReviewCreateRequestDto dto) {
            return ReviewEntity.builder()
                    .rating(dto.getRating())
                    .comment(dto.getComment())
                    .build();
        }

        @Test
        @DisplayName("happy: persists review and returns mapped DTO when product is in the order and moderation passes")
        void createHappyPath() {
            ReviewCreateRequestDto dto = createDto();
            UserEntity user = UserEntityBuilder.aValidUserBuilder().keycloakId(keycloakId).build();
            OrderEntity order = callerOrderContainingProduct(productUuid);
            ProductEntity product = productWith(productUuid);
            ReviewEntity reviewEntity = entityFromDto(dto);
            ReviewResponseDto responseDto = ReviewResponseDto.builder().rating(5).build();

            when(userRepository.findByKeycloakIdAndIsActive(keycloakId, true)).thenReturn(Optional.of(user));
            when(orderRepository.findByUuid(orderUuid)).thenReturn(Optional.of(order));
            when(reviewMapper.mapFromCreationRequestToEntity(dto)).thenReturn(reviewEntity);
            when(productRepository.findByUuid(productUuid)).thenReturn(Optional.of(product));
            when(moderationService.checkIfIsHateful("Excellent"))
                    .thenReturn(ModerationResponseDto.builder().label("not_toxic").score(0.95).isHateful(false).build());
            when(reviewRepository.save(reviewEntity)).thenReturn(reviewEntity);
            when(reviewMapper.mapFromEntityToResponseDto(reviewEntity)).thenReturn(responseDto);

            ReviewResponseDto result = service.create(dto, keycloakId);

            assertThat(result).isSameAs(responseDto);
            assertThat(reviewEntity.getIsHateful()).isFalse();
            assertThat(reviewEntity.getUserEntity()).isSameAs(user);
            assertThat(reviewEntity.getProductEntity()).isSameAs(product);
            assertThat(reviewEntity.getOrderUuid()).isEqualTo(order.getUuid());
        }

        @ParameterizedTest(name = "comment = [{0}]")
        @NullSource
        @ValueSource(strings = {"", "   "})
        @DisplayName("rating without comment: moderation is skipped and the review is stored with a null comment")
        void ratingOnlyReviewSkipsModeration(final String comment) {
            ReviewCreateRequestDto dto = ReviewCreateRequestDto.builder()
                    .orderUuid(orderUuid)
                    .productUuid(productUuid)
                    .rating(4)
                    .comment(comment)
                    .build();
            UserEntity user = UserEntityBuilder.aValidUserBuilder().keycloakId(keycloakId).build();
            OrderEntity order = callerOrderContainingProduct(productUuid);
            ProductEntity product = productWith(productUuid);
            ReviewEntity reviewEntity = entityFromDto(dto);

            when(userRepository.findByKeycloakIdAndIsActive(keycloakId, true)).thenReturn(Optional.of(user));
            when(orderRepository.findByUuid(orderUuid)).thenReturn(Optional.of(order));
            when(reviewMapper.mapFromCreationRequestToEntity(dto)).thenReturn(reviewEntity);
            when(productRepository.findByUuid(productUuid)).thenReturn(Optional.of(product));
            when(reviewRepository.save(reviewEntity)).thenReturn(reviewEntity);

            service.create(dto, keycloakId);

            assertThat(reviewEntity.getRating()).isEqualTo(4);
            assertThat(reviewEntity.getComment()).isNull();
            assertThat(reviewEntity.getIsHateful()).isFalse();
            verifyNoInteractions(moderationService);
            verify(reviewRepository).save(reviewEntity);
        }

        @Test
        @DisplayName("user inactive or missing -> UserNotFoundException; nothing persisted")
        void inactiveUserRejects() {
            ReviewCreateRequestDto dto = createDto();
            when(userRepository.findByKeycloakIdAndIsActive(keycloakId, true)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.create(dto, keycloakId))
                    .isInstanceOf(UserNotFoundException.class);

            verify(reviewRepository, never()).save(any());
        }

        @Test
        @DisplayName("missing order UUID -> OrderNotFoundException")
        void missingOrderRejects() {
            ReviewCreateRequestDto dto = createDto();
            UserEntity user = UserEntityBuilder.aValidUserBuilder().keycloakId(keycloakId).build();
            when(userRepository.findByKeycloakIdAndIsActive(keycloakId, true)).thenReturn(Optional.of(user));
            when(orderRepository.findByUuid(orderUuid)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.create(dto, keycloakId))
                    .isInstanceOf(OrderNotFoundException.class)
                    .hasMessageContaining(orderUuid.toString());
        }

        @Test
        @DisplayName("product not in order AND not in any user order -> ProductNotFoundException")
        void productNotPartOfAnyUserOrderRejects() {
            ReviewCreateRequestDto dto = createDto();
            UserEntity user = UserEntityBuilder.aValidUserBuilder().keycloakId(keycloakId).orderEntities(new ArrayList<>()).build();

            // The order in the request contains a DIFFERENT product.
            ProductEntity otherProduct = productWith(UUID.randomUUID());
            OrderItemEntity otherItem = OrderItemEntityBuilder.aValidOrderItemBuilder().productEntity(otherProduct).build();
            List<OrderItemEntity> items = new ArrayList<>();
            items.add(otherItem);
            // Order belongs to the caller so the ownership check passes; product check fails.
            // Status PAID so the reviewability guard is satisfied (we want the product check to fire).
            OrderEntity order = OrderEntityBuilder.aValidOrderBuilder().orderItemEntities(items).userEntity(user).status(OrderStatus.PAID).build();

            when(userRepository.findByKeycloakIdAndIsActive(keycloakId, true)).thenReturn(Optional.of(user));
            when(orderRepository.findByUuid(orderUuid)).thenReturn(Optional.of(order));
            // After the in-order check fails, the service delegates the global check to a single
            // boolean repository query (replaces the in-memory walk over user.getOrderEntities()).
            when(orderItemRepository.userHasBoughtProduct(keycloakId, productUuid)).thenReturn(false);

            assertThatThrownBy(() -> service.create(dto, keycloakId))
                    .isInstanceOf(ProductNotFoundException.class);

            verify(reviewRepository, never()).save(any());
        }

        @Test
        @DisplayName("product not in order BUT present in another user order -> falls through and persists")
        void productNotInThisOrderButInAnotherUserOrderPasses() {
            ReviewCreateRequestDto dto = createDto();

            UserEntity user = UserEntityBuilder.aValidUserBuilder()
                    .keycloakId(keycloakId)
                    .orderEntities(new ArrayList<>())
                    .build();

            // request order contains a DIFFERENT product but belongs to the caller
            ProductEntity otherProduct = productWith(UUID.randomUUID());
            OrderItemEntity otherItem = OrderItemEntityBuilder.aValidOrderItemBuilder().productEntity(otherProduct).build();
            List<OrderItemEntity> items = new ArrayList<>();
            items.add(otherItem);
            // PAID so the reviewability guard is satisfied for this happy-path scenario.
            OrderEntity requestOrder = OrderEntityBuilder.aValidOrderBuilder().orderItemEntities(items).userEntity(user).status(OrderStatus.PAID).build();

            ReviewEntity reviewEntity = ReviewEntity.builder().comment("Excellent").build();
            ProductEntity product = productWith(productUuid);

            when(userRepository.findByKeycloakIdAndIsActive(keycloakId, true)).thenReturn(Optional.of(user));
            when(orderRepository.findByUuid(orderUuid)).thenReturn(Optional.of(requestOrder));
            // Service no longer walks user.getOrderEntities() — it delegates to a single
            // boolean query that asserts the product appears in some historical order of the user.
            when(orderItemRepository.userHasBoughtProduct(keycloakId, productUuid)).thenReturn(true);
            when(reviewMapper.mapFromCreationRequestToEntity(dto)).thenReturn(reviewEntity);
            when(productRepository.findByUuid(productUuid)).thenReturn(Optional.of(product));
            when(moderationService.checkIfIsHateful("Excellent"))
                    .thenReturn(ModerationResponseDto.builder().label("not_toxic").score(0.1).isHateful(false).build());
            when(reviewRepository.save(reviewEntity)).thenReturn(reviewEntity);
            when(reviewMapper.mapFromEntityToResponseDto(reviewEntity))
                    .thenReturn(ReviewResponseDto.builder().build());

            assertThat(service.create(dto, keycloakId)).isNotNull();
        }

        @Test
        @DisplayName("Order belonging to another user is rejected even when product matches")
        void create_orderBelongsToAnotherUser_shouldThrowOrderDoesntBelongsToUserException() {
            // Arrange
            final String callerKeycloakId = "caller-kc-id";
            final String otherKeycloakId  = "other-user-kc-id";

            final UserEntity callerUser = UserEntityBuilder.aValidUserBuilder()
                    .keycloakId(callerKeycloakId)
                    .build();

            final UserEntity otherUser = UserEntityBuilder.aValidUserBuilder()
                    .keycloakId(otherKeycloakId)
                    .build();

            final UUID foreignOrderUuid = UUID.randomUUID();
            final OrderEntity foreignOrder = OrderEntityBuilder.aValidOrderBuilder()
                    .uuid(foreignOrderUuid)
                    .userEntity(otherUser)      // order belongs to a different user
                    .orderItemEntities(new ArrayList<>())
                    .build();

            final ReviewCreateRequestDto dto = ReviewCreateRequestDto.builder()
                    .orderUuid(foreignOrderUuid)
                    .productUuid(UUID.randomUUID())
                    .rating(5)
                    .comment("Test")
                    .build();

            when(userRepository.findByKeycloakIdAndIsActive(callerKeycloakId, true))
                    .thenReturn(Optional.of(callerUser));
            when(orderRepository.findByUuid(foreignOrderUuid))
                    .thenReturn(Optional.of(foreignOrder));

            // Act + Assert
            assertThatThrownBy(() -> service.create(dto, callerKeycloakId))
                    .isInstanceOf(OrderDoesntBelongsToUserException.class);

            // Verify no review was saved
            verify(reviewRepository, never()).save(any());
        }

        @Test
        @DisplayName("missing product after order/user checks -> ProductNotFoundException")
        void missingProductRejects() {
            ReviewCreateRequestDto dto = createDto();
            UserEntity user = UserEntityBuilder.aValidUserBuilder().keycloakId(keycloakId).build();
            OrderEntity order = callerOrderContainingProduct(productUuid);
            ReviewEntity reviewEntity = ReviewEntity.builder().comment("hi").build();

            when(userRepository.findByKeycloakIdAndIsActive(keycloakId, true)).thenReturn(Optional.of(user));
            when(orderRepository.findByUuid(orderUuid)).thenReturn(Optional.of(order));
            when(reviewMapper.mapFromCreationRequestToEntity(dto)).thenReturn(reviewEntity);
            when(productRepository.findByUuid(productUuid)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.create(dto, keycloakId))
                    .isInstanceOf(ProductNotFoundException.class);
        }

        @Test
        @DisplayName("moderation verdict isHateful=true -> CommentPostNotAllowedException; nothing persisted")
        void hatefulCommentIsBlocked() {
            ReviewCreateRequestDto dto = createDto();
            UserEntity user = UserEntityBuilder.aValidUserBuilder().keycloakId(keycloakId).build();
            OrderEntity order = callerOrderContainingProduct(productUuid);
            ProductEntity product = productWith(productUuid);
            ReviewEntity reviewEntity = ReviewEntity.builder().comment("hateful text").build();

            when(userRepository.findByKeycloakIdAndIsActive(keycloakId, true)).thenReturn(Optional.of(user));
            when(orderRepository.findByUuid(orderUuid)).thenReturn(Optional.of(order));
            when(reviewMapper.mapFromCreationRequestToEntity(dto)).thenReturn(reviewEntity);
            when(productRepository.findByUuid(productUuid)).thenReturn(Optional.of(product));
            when(moderationService.checkIfIsHateful("hateful text"))
                    .thenReturn(ModerationResponseDto.builder().label("toxic").score(0.95).isHateful(true).build());

            assertThatThrownBy(() -> service.create(dto, keycloakId))
                    .isInstanceOf(CommentPostNotAllowedException.class);

            verify(reviewRepository, never()).save(any());
        }

        @Test
        @DisplayName("moderation verdict isHateful=true even with a LOW confidence score still blocks (decision is label-based, not score-based)")
        void hatefulCommentIsBlockedRegardlessOfLowScore() {
            ReviewCreateRequestDto dto = createDto();
            UserEntity user = UserEntityBuilder.aValidUserBuilder().keycloakId(keycloakId).build();
            OrderEntity order = callerOrderContainingProduct(productUuid);
            ProductEntity product = productWith(productUuid);
            ReviewEntity reviewEntity = ReviewEntity.builder().comment("borderline toxic").build();

            when(userRepository.findByKeycloakIdAndIsActive(keycloakId, true)).thenReturn(Optional.of(user));
            when(orderRepository.findByUuid(orderUuid)).thenReturn(Optional.of(order));
            when(reviewMapper.mapFromCreationRequestToEntity(dto)).thenReturn(reviewEntity);
            when(productRepository.findByUuid(productUuid)).thenReturn(Optional.of(product));
            when(moderationService.checkIfIsHateful("borderline toxic"))
                    .thenReturn(ModerationResponseDto.builder().label("toxic").score(0.1).isHateful(true).build());

            assertThatThrownBy(() -> service.create(dto, keycloakId))
                    .isInstanceOf(CommentPostNotAllowedException.class);

            verify(reviewRepository, never()).save(any());
        }

        @Test
        @DisplayName("moderation verdict isHateful=null (malformed response) fails closed -> CommentPostNotAllowedException")
        void nullIsHatefulVerdictFailsClosed() {
            ReviewCreateRequestDto dto = createDto();
            UserEntity user = UserEntityBuilder.aValidUserBuilder().keycloakId(keycloakId).build();
            OrderEntity order = callerOrderContainingProduct(productUuid);
            ProductEntity product = productWith(productUuid);
            ReviewEntity reviewEntity = ReviewEntity.builder().comment("text").build();

            when(userRepository.findByKeycloakIdAndIsActive(keycloakId, true)).thenReturn(Optional.of(user));
            when(orderRepository.findByUuid(orderUuid)).thenReturn(Optional.of(order));
            when(reviewMapper.mapFromCreationRequestToEntity(dto)).thenReturn(reviewEntity);
            when(productRepository.findByUuid(productUuid)).thenReturn(Optional.of(product));
            when(moderationService.checkIfIsHateful("text"))
                    .thenReturn(ModerationResponseDto.builder().label("toxic").score(0.4).build());

            assertThatThrownBy(() -> service.create(dto, keycloakId))
                    .isInstanceOf(CommentPostNotAllowedException.class);

            verify(reviewRepository, never()).save(any());
        }

        // ============================ Order status guard ============================
        // create() must reject reviews on orders that have not reached a "reviewable" lifecycle
        // state. Pre-payment statuses (CREATED, AWAITING_PAYMENT, PAYMENT_FAILED) and post-cancel
        // statuses must produce OrderNotReviewableException; PAID/SHIPPED/DELIVERED must succeed.

        @Test
        @DisplayName("status CREATED -> OrderNotReviewableException; nothing persisted")
        void createReviewShouldFailWhenOrderStatusIsCreated() {
            assertOrderStatusIsRejected(OrderStatus.CREATED);
        }

        @Test
        @DisplayName("status PAYMENT_FAILED -> OrderNotReviewableException; nothing persisted")
        void createReviewShouldFailWhenOrderStatusIsPaymentFailed() {
            assertOrderStatusIsRejected(OrderStatus.PAYMENT_FAILED);
        }

        @Test
        @DisplayName("status AWAITING_PAYMENT -> OrderNotReviewableException; nothing persisted")
        void createReviewShouldFailWhenOrderStatusIsAwaitingPayment() {
            assertOrderStatusIsRejected(OrderStatus.AWAITING_PAYMENT);
        }

        @Test
        @DisplayName("status PAID is reviewable -> review persists")
        void createReviewShouldSucceedWhenOrderStatusIsPaid() {
            assertOrderStatusIsAccepted(OrderStatus.PAID);
        }

        @Test
        @DisplayName("status SHIPPED is reviewable -> review persists")
        void createReviewShouldSucceedWhenOrderStatusIsShipped() {
            assertOrderStatusIsAccepted(OrderStatus.SHIPPED);
        }

        @Test
        @DisplayName("status DELIVERED is reviewable -> review persists")
        void createReviewShouldSucceedWhenOrderStatusIsDelivered() {
            assertOrderStatusIsAccepted(OrderStatus.DELIVERED);
        }

        /**
         * Asserts that creating a review against an order in {@code status} raises
         * {@link OrderNotReviewableException} and that no review is persisted. The order is
         * caller-owned and contains the requested product, so the only check that can fail
         * is the order status guard.
         */
        private void assertOrderStatusIsRejected(final OrderStatus status) {
            final ReviewCreateRequestDto dto = createDto();
            final UserEntity user = UserEntityBuilder.aValidUserBuilder().keycloakId(keycloakId).build();
            final OrderEntity order = orderContainingProduct(productUuid, user, status);

            when(userRepository.findByKeycloakIdAndIsActive(keycloakId, true)).thenReturn(Optional.of(user));
            when(orderRepository.findByUuid(orderUuid)).thenReturn(Optional.of(order));

            assertThatThrownBy(() -> service.create(dto, keycloakId))
                    .isInstanceOf(OrderNotReviewableException.class)
                    .hasMessageContaining(status.name());

            verify(reviewRepository, never()).save(any());
        }

        /**
         * Asserts that creating a review against an order in {@code status} succeeds (review
         * persisted, mapped DTO returned). Used for the PAID/SHIPPED/DELIVERED regression coverage.
         */
        private void assertOrderStatusIsAccepted(final OrderStatus status) {
            final ReviewCreateRequestDto dto = createDto();
            final UserEntity user = UserEntityBuilder.aValidUserBuilder().keycloakId(keycloakId).build();
            final OrderEntity order = orderContainingProduct(productUuid, user, status);
            final ProductEntity product = productWith(productUuid);
            final ReviewEntity reviewEntity = ReviewEntity.builder().comment("Excellent").build();
            final ReviewResponseDto responseDto = ReviewResponseDto.builder().rating(5).build();

            when(userRepository.findByKeycloakIdAndIsActive(keycloakId, true)).thenReturn(Optional.of(user));
            when(orderRepository.findByUuid(orderUuid)).thenReturn(Optional.of(order));
            when(reviewMapper.mapFromCreationRequestToEntity(dto)).thenReturn(reviewEntity);
            when(productRepository.findByUuid(productUuid)).thenReturn(Optional.of(product));
            when(moderationService.checkIfIsHateful("Excellent"))
                    .thenReturn(ModerationResponseDto.builder().label("not_toxic").score(0.1).isHateful(false).build());
            when(reviewRepository.save(reviewEntity)).thenReturn(reviewEntity);
            when(reviewMapper.mapFromEntityToResponseDto(reviewEntity)).thenReturn(responseDto);

            final ReviewResponseDto result = service.create(dto, keycloakId);

            assertThat(result).isSameAs(responseDto);
            verify(reviewRepository).save(reviewEntity);
        }
    }

    // ============================ update ============================

    @Nested
    class Update {

        private ReviewUpdateRequestDto updateDto() {
            return ReviewUpdateRequestDto.builder()
                    .reviewUuid(reviewUuid)
                    .rating(4)
                    .comment("better")
                    .build();
        }

        @Test
        @DisplayName("happy: author can update his own review (loaded entity is patched, FKs preserved)")
        void authorCanUpdateOwnReview() {
            ReviewUpdateRequestDto dto = updateDto();
            UserEntity author = UserEntityBuilder.aValidUserBuilder().keycloakId(keycloakId).build();
            ReviewEntity existing = ReviewEntityBuilder.aValidReviewBuilder().uuid(reviewUuid).userEntity(author).build();
            ReviewResponseDto responseDto = ReviewResponseDto.builder().rating(4).build();

            when(userRepository.existsByKeycloakIdAndIsActive(keycloakId, true)).thenReturn(true);
            when(reviewRepository.findByUuid(reviewUuid)).thenReturn(Optional.of(existing));
            when(moderationService.checkIfIsHateful("better"))
                    .thenReturn(ModerationResponseDto.builder().label("not_toxic").score(0.95).isHateful(false).build());
            when(reviewRepository.save(existing)).thenReturn(existing);
            when(reviewMapper.mapFromEntityToResponseDto(existing)).thenReturn(responseDto);

            ReviewResponseDto result = service.update(dto, keycloakId);

            assertThat(result).isSameAs(responseDto);
            // Verify the loaded entity was mutated with DTO fields and the same instance saved
            assertThat(existing.getRating()).isEqualTo(4);
            assertThat(existing.getComment()).isEqualTo("better");
            assertThat(existing.getUserEntity()).isSameAs(author); // FK preserved
            verify(reviewRepository).save(existing);
        }

        @Test
        @DisplayName("inactive/missing user -> UserNotFoundException; nothing persisted")
        void inactiveUserRejects() {
            ReviewUpdateRequestDto dto = updateDto();
            when(userRepository.existsByKeycloakIdAndIsActive(keycloakId, true)).thenReturn(false);

            assertThatThrownBy(() -> service.update(dto, keycloakId))
                    .isInstanceOf(UserNotFoundException.class);

            verify(reviewRepository, never()).save(any());
        }

        @Test
        @DisplayName("missing review -> ReviewNotFoundException")
        void missingReviewRejects() {
            ReviewUpdateRequestDto dto = updateDto();
            when(userRepository.existsByKeycloakIdAndIsActive(keycloakId, true)).thenReturn(true);
            when(reviewRepository.findByUuid(reviewUuid)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.update(dto, keycloakId))
                    .isInstanceOf(ReviewNotFoundException.class);
        }

        @Test
        @DisplayName("an edited comment goes through moderation: rejected -> CommentPostNotAllowedException, published text kept")
        void hatefulEditIsBlockedAndKeepsPublishedText() {
            ReviewUpdateRequestDto dto = ReviewUpdateRequestDto.builder().reviewUuid(reviewUuid).rating(1).comment("hateful text").build();
            UserEntity author = UserEntityBuilder.aValidUserBuilder().keycloakId(keycloakId).build();
            ReviewEntity existing = ReviewEntityBuilder.aValidReviewBuilder().uuid(reviewUuid).userEntity(author)
                    .rating(5).comment("Great product").build();

            when(userRepository.existsByKeycloakIdAndIsActive(keycloakId, true)).thenReturn(true);
            when(reviewRepository.findByUuid(reviewUuid)).thenReturn(Optional.of(existing));
            when(moderationService.checkIfIsHateful("hateful text"))
                    .thenReturn(ModerationResponseDto.builder().label("toxic").score(0.97).isHateful(true).build());

            assertThatThrownBy(() -> service.update(dto, keycloakId))
                    .isInstanceOf(CommentPostNotAllowedException.class);

            assertThat(existing.getComment()).isEqualTo("Great product");
            verify(reviewRepository, never()).save(any());
        }

        @Test
        @DisplayName("a malformed moderation verdict on an edit fails closed, as on create")
        void editWithNullVerdictFailsClosed() {
            ReviewUpdateRequestDto dto = ReviewUpdateRequestDto.builder().reviewUuid(reviewUuid).comment("text").build();
            UserEntity author = UserEntityBuilder.aValidUserBuilder().keycloakId(keycloakId).build();
            ReviewEntity existing = ReviewEntityBuilder.aValidReviewBuilder().uuid(reviewUuid).userEntity(author).build();

            when(userRepository.existsByKeycloakIdAndIsActive(keycloakId, true)).thenReturn(true);
            when(reviewRepository.findByUuid(reviewUuid)).thenReturn(Optional.of(existing));
            when(moderationService.checkIfIsHateful("text")).thenReturn(ModerationResponseDto.builder().build());

            assertThatThrownBy(() -> service.update(dto, keycloakId))
                    .isInstanceOf(CommentPostNotAllowedException.class);
            verify(reviewRepository, never()).save(any());
        }

        @Test
        @DisplayName("a rating-only edit leaves the comment alone and skips moderation")
        void ratingOnlyEditSkipsModeration() {
            ReviewUpdateRequestDto dto = ReviewUpdateRequestDto.builder().reviewUuid(reviewUuid).rating(3).build();
            UserEntity author = UserEntityBuilder.aValidUserBuilder().keycloakId(keycloakId).build();
            ReviewEntity existing = ReviewEntityBuilder.aValidReviewBuilder().uuid(reviewUuid).userEntity(author).build();

            when(userRepository.existsByKeycloakIdAndIsActive(keycloakId, true)).thenReturn(true);
            when(reviewRepository.findByUuid(reviewUuid)).thenReturn(Optional.of(existing));
            when(reviewRepository.save(existing)).thenReturn(existing);

            service.update(dto, keycloakId);

            assertThat(existing.getRating()).isEqualTo(3);
            assertThat(existing.getComment()).isEqualTo("Great product");
            verifyNoInteractions(moderationService);
        }

        @ParameterizedTest(name = "comment = [{0}]")
        @ValueSource(strings = {"", "   "})
        @DisplayName("a blank comment on an edit removes the text without calling moderation")
        void blankCommentEditClearsCommentAndSkipsModeration(final String comment) {
            ReviewUpdateRequestDto dto = ReviewUpdateRequestDto.builder().reviewUuid(reviewUuid).comment(comment).build();
            UserEntity author = UserEntityBuilder.aValidUserBuilder().keycloakId(keycloakId).build();
            ReviewEntity existing = ReviewEntityBuilder.aValidReviewBuilder().uuid(reviewUuid).userEntity(author).build();

            when(userRepository.existsByKeycloakIdAndIsActive(keycloakId, true)).thenReturn(true);
            when(reviewRepository.findByUuid(reviewUuid)).thenReturn(Optional.of(existing));
            when(reviewRepository.save(existing)).thenReturn(existing);

            service.update(dto, keycloakId);

            assertThat(existing.getComment()).isNull();
            verifyNoInteractions(moderationService);
        }

        @Test
        @DisplayName("non-author cannot update someone else's review -> UserNotAuthorOfReviewException")
        void nonAuthorCannotUpdate() {
            ReviewUpdateRequestDto dto = updateDto();
            UserEntity otherAuthor = UserEntityBuilder.aValidUserBuilder().keycloakId("other-kc").build();
            ReviewEntity existing = ReviewEntityBuilder.aValidReviewBuilder().uuid(reviewUuid).userEntity(otherAuthor).build();

            when(userRepository.existsByKeycloakIdAndIsActive(keycloakId, true)).thenReturn(true);
            when(reviewRepository.findByUuid(reviewUuid)).thenReturn(Optional.of(existing));

            assertThatThrownBy(() -> service.update(dto, keycloakId))
                    .isInstanceOf(UserNotAuthorOfReviewException.class);

            verify(reviewRepository, never()).save(any());
        }
    }

    // ============================ deleteByUUID ============================

    @Nested
    class DeleteByUUID {

        @Test
        @DisplayName("happy: author can delete own review")
        void authorCanDeleteOwnReview() {
            UserEntity author = UserEntityBuilder.aValidUserBuilder().keycloakId(keycloakId).build();
            ReviewEntity existing = ReviewEntityBuilder.aValidReviewBuilder().uuid(reviewUuid).userEntity(author).build();
            when(userRepository.existsByKeycloakIdAndIsActive(keycloakId, true)).thenReturn(true);
            when(reviewRepository.findByUuid(reviewUuid)).thenReturn(Optional.of(existing));

            service.deleteByUUID(reviewUuid, keycloakId);

            verify(reviewRepository).deleteByUuid(reviewUuid);
        }

        @Test
        @DisplayName("inactive user -> UserNotFoundException; no delete")
        void inactiveUserRejects() {
            when(userRepository.existsByKeycloakIdAndIsActive(keycloakId, true)).thenReturn(false);

            assertThatThrownBy(() -> service.deleteByUUID(reviewUuid, keycloakId))
                    .isInstanceOf(UserNotFoundException.class);

            verify(reviewRepository, never()).deleteByUuid(any());
        }

        @Test
        @DisplayName("missing review -> ReviewNotFoundException; no delete")
        void missingReviewRejects() {
            when(userRepository.existsByKeycloakIdAndIsActive(keycloakId, true)).thenReturn(true);
            when(reviewRepository.findByUuid(reviewUuid)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.deleteByUUID(reviewUuid, keycloakId))
                    .isInstanceOf(ReviewNotFoundException.class);

            verify(reviewRepository, never()).deleteByUuid(any());
        }

        @Test
        @DisplayName("non-author cannot delete someone else's review -> UserNotAuthorOfReviewException")
        void nonAuthorCannotDelete() {
            UserEntity other = UserEntityBuilder.aValidUserBuilder().keycloakId("other-kc").build();
            ReviewEntity existing = ReviewEntityBuilder.aValidReviewBuilder().uuid(reviewUuid).userEntity(other).build();
            when(userRepository.existsByKeycloakIdAndIsActive(keycloakId, true)).thenReturn(true);
            when(reviewRepository.findByUuid(reviewUuid)).thenReturn(Optional.of(existing));

            assertThatThrownBy(() -> service.deleteByUUID(reviewUuid, keycloakId))
                    .isInstanceOf(UserNotAuthorOfReviewException.class);

            verify(reviewRepository, never()).deleteByUuid(any());
        }
    }

    // ============================ getReviewableProducts ============================

    @Nested
    class Reviewable {

        @Test
        @DisplayName("happy: returns one entry per (order, product) for orders in PAID/SHIPPED/DELIVERED")
        void returnsPairsForReviewableOrders() {
            final UUID p1 = UUID.randomUUID();
            final UUID p2 = UUID.randomUUID();
            final UserEntity caller = UserEntityBuilder.aValidUserBuilder().keycloakId(keycloakId).build();
            final OrderEntity order1 = orderContainingProduct(p1, caller);
            final OrderEntity order2 = orderContainingProduct(p2, caller);

            when(userRepository.existsByKeycloakIdAndIsActive(keycloakId, true)).thenReturn(true);
            when(reviewRepository.findReviewedProductUuidsByUserKeycloakId(keycloakId)).thenReturn(Set.of());
            when(orderRepository.findReviewableOrdersWithItemsByKeycloakIdAndStatusIn(eq(keycloakId), any(Collection.class)))
                    .thenReturn(List.of(order1, order2));

            final List<ReviewableProductDto> result = service.getReviewableProducts(keycloakId);

            assertThat(result).hasSize(2);
            assertThat(result).extracting(ReviewableProductDto::productUuid).containsExactlyInAnyOrder(p1, p2);
        }

        @Test
        @DisplayName("excludes products already reviewed by the user")
        void filtersAlreadyReviewed() {
            final UUID alreadyReviewed = UUID.randomUUID();
            final UUID notYetReviewed = UUID.randomUUID();
            final UserEntity caller = UserEntityBuilder.aValidUserBuilder().keycloakId(keycloakId).build();
            // Single order with two items
            final ProductEntity prodReviewed = productWith(alreadyReviewed);
            final ProductEntity prodFresh = productWith(notYetReviewed);
            final OrderItemEntity oi1 = OrderItemEntityBuilder.aValidOrderItemBuilder().productEntity(prodReviewed).build();
            final OrderItemEntity oi2 = OrderItemEntityBuilder.aValidOrderItemBuilder().productEntity(prodFresh).build();
            final List<OrderItemEntity> items = new ArrayList<>(List.of(oi1, oi2));
            final OrderEntity order = OrderEntityBuilder.aValidOrderBuilder()
                    .uuid(orderUuid).orderItemEntities(items).userEntity(caller).build();

            when(userRepository.existsByKeycloakIdAndIsActive(keycloakId, true)).thenReturn(true);
            when(reviewRepository.findReviewedProductUuidsByUserKeycloakId(keycloakId))
                    .thenReturn(Set.of(alreadyReviewed));
            when(orderRepository.findReviewableOrdersWithItemsByKeycloakIdAndStatusIn(eq(keycloakId), any(Collection.class)))
                    .thenReturn(List.of(order));

            final List<ReviewableProductDto> result = service.getReviewableProducts(keycloakId);

            assertThat(result).hasSize(1);
            assertThat(result.get(0).productUuid()).isEqualTo(notYetReviewed);
        }

        @Test
        @DisplayName("inactive/unknown user -> UserNotFoundException")
        void inactiveUserRejects() {
            when(userRepository.existsByKeycloakIdAndIsActive(keycloakId, true)).thenReturn(false);

            assertThatThrownBy(() -> service.getReviewableProducts(keycloakId))
                    .isInstanceOf(UserNotFoundException.class);

            verify(orderRepository, never()).findReviewableOrdersWithItemsByKeycloakIdAndStatusIn(any(), any());
        }

        @Test
        @DisplayName("no orders -> empty list")
        void noOrdersReturnsEmpty() {
            when(userRepository.existsByKeycloakIdAndIsActive(keycloakId, true)).thenReturn(true);
            when(reviewRepository.findReviewedProductUuidsByUserKeycloakId(keycloakId)).thenReturn(Set.of());
            when(orderRepository.findReviewableOrdersWithItemsByKeycloakIdAndStatusIn(eq(keycloakId), any(Collection.class)))
                    .thenReturn(List.of());

            assertThat(service.getReviewableProducts(keycloakId)).isEmpty();
        }

        @Test
        @DisplayName("queries orderRepository with the {PAID, SHIPPED, DELIVERED} status set")
        void queriesWithReviewableStatuses() {
            when(userRepository.existsByKeycloakIdAndIsActive(keycloakId, true)).thenReturn(true);
            when(reviewRepository.findReviewedProductUuidsByUserKeycloakId(keycloakId)).thenReturn(Set.of());
            when(orderRepository.findReviewableOrdersWithItemsByKeycloakIdAndStatusIn(eq(keycloakId), any(Collection.class)))
                    .thenReturn(List.of());

            service.getReviewableProducts(keycloakId);

            org.mockito.ArgumentCaptor<Collection<OrderStatus>> cap = org.mockito.ArgumentCaptor.forClass(Collection.class);
            verify(orderRepository).findReviewableOrdersWithItemsByKeycloakIdAndStatusIn(eq(keycloakId), cap.capture());
            assertThat(cap.getValue()).containsExactlyInAnyOrder(OrderStatus.PAID, OrderStatus.SHIPPED, OrderStatus.DELIVERED);
        }
    }

    // ============================ getPublishedReviewsOfProduct ============================

    @Nested
    class PublishedReviewsOfProduct {

        private ReviewRepository.RatingCount ratingCount(final int rating, final long count) {
            return new ReviewRepository.RatingCount() {
                @Override
                public Integer getRating() {
                    return rating;
                }

                @Override
                public Long getCount() {
                    return count;
                }
            };
        }

        @Test
        @DisplayName("average (1 decimal, half-up), count and distribution over every published review; page mapped")
        void summaryAndPage() {
            ReviewEntity review = ReviewEntityBuilder.aValidReview();
            PublicReviewDto publicReview = PublicReviewDto.builder().uuid(review.getUuid()).rating(5).build();
            when(productRepository.findByUuid(productUuid)).thenReturn(Optional.of(productWith(productUuid)));
            // 2x5 + 1x4 = 14 / 3 = 4.666... -> 4.7
            when(reviewRepository.countPublishedByRating(productUuid)).thenReturn(List.of(ratingCount(5, 2), ratingCount(4, 1)));
            when(reviewRepository.findPublishedByProductUuid(eq(productUuid), any(Pageable.class)))
                    .thenAnswer(inv -> new PageImpl<>(List.of(review), inv.getArgument(1), 3));
            when(reviewMapper.mapFromEntityToPublicReviewDto(review)).thenReturn(publicReview);

            ProductReviewsResponseDto result = service.getPublishedReviewsOfProduct(productUuid, PageRequest.of(0, 1));

            assertThat(result.getAverageRating()).isEqualByComparingTo(new BigDecimal("4.7"));
            assertThat(result.getAverageRating().scale()).isEqualTo(1);
            assertThat(result.getReviewCount()).isEqualTo(3);
            assertThat(result.getDistribution()).containsExactly(
                    org.assertj.core.api.Assertions.entry("1", 0L), org.assertj.core.api.Assertions.entry("2", 0L),
                    org.assertj.core.api.Assertions.entry("3", 0L), org.assertj.core.api.Assertions.entry("4", 1L),
                    org.assertj.core.api.Assertions.entry("5", 2L));
            assertThat(result.getReviews().getContent()).containsExactly(publicReview);
            assertThat(result.getReviews().getMetadata().totalElements()).isEqualTo(3);
        }

        @Test
        @DisplayName("no published review: null average, zero count, every rating at 0, empty page")
        void noReviews() {
            when(productRepository.findByUuid(productUuid)).thenReturn(Optional.of(productWith(productUuid)));
            when(reviewRepository.countPublishedByRating(productUuid)).thenReturn(List.of());
            when(reviewRepository.findPublishedByProductUuid(eq(productUuid), any(Pageable.class)))
                    .thenAnswer(inv -> Page.empty(inv.getArgument(1)));

            ProductReviewsResponseDto result = service.getPublishedReviewsOfProduct(productUuid, PageRequest.of(0, 10));

            assertThat(result.getAverageRating()).isNull();
            assertThat(result.getReviewCount()).isZero();
            assertThat(result.getDistribution()).containsOnlyKeys("1", "2", "3", "4", "5").allSatisfy((k, v) -> assertThat(v).isZero());
            assertThat(result.getReviews().getContent()).isEmpty();
        }

        @Test
        @DisplayName("keeps page number and size but always sorts newest first, whatever sort the caller sent")
        void fixedOrder() {
            when(productRepository.findByUuid(productUuid)).thenReturn(Optional.of(productWith(productUuid)));
            when(reviewRepository.countPublishedByRating(productUuid)).thenReturn(List.of());
            when(reviewRepository.findPublishedByProductUuid(eq(productUuid), any(Pageable.class)))
                    .thenAnswer(inv -> Page.empty(inv.getArgument(1)));

            service.getPublishedReviewsOfProduct(productUuid, PageRequest.of(2, 5, Sort.by("rating")));

            ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
            verify(reviewRepository).findPublishedByProductUuid(eq(productUuid), captor.capture());
            assertThat(captor.getValue().getPageNumber()).isEqualTo(2);
            assertThat(captor.getValue().getPageSize()).isEqualTo(5);
            assertThat(captor.getValue().getSort()).isEqualTo(Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")));
        }

        @Test
        @DisplayName("unknown product -> ProductNotFoundException, no review query")
        void unknownProduct() {
            when(productRepository.findByUuid(productUuid)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.getPublishedReviewsOfProduct(productUuid, PageRequest.of(0, 10)))
                    .isInstanceOf(ProductNotFoundException.class);
            verifyNoInteractions(reviewRepository);
        }
    }

    // ============================ getMyReviews ============================

    @Nested
    class MyReviews {

        private final String keycloakId = "kc-me";

        @Test
        @DisplayName("maps the caller's page; default order createdAt desc then id desc")
        void defaultOrderAndMapping() {
            ReviewEntity review = ReviewEntityBuilder.aValidReview();
            MyReviewDto dto = MyReviewDto.builder().uuid(review.getUuid()).rating(4).build();
            when(reviewRepository.findByAuthorKeycloakId(eq(keycloakId), any(Pageable.class)))
                    .thenAnswer(inv -> new PageImpl<>(List.of(review), inv.getArgument(1), 11));
            when(reviewMapper.mapFromEntityToMyReviewDto(review)).thenReturn(dto);

            PagedModel<MyReviewDto> result = service.getMyReviews(keycloakId, PageRequest.of(1, 10));

            assertThat(result.getContent()).containsExactly(dto);
            assertThat(result.getMetadata().totalElements()).isEqualTo(11);
            ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
            verify(reviewRepository).findByAuthorKeycloakId(eq(keycloakId), captor.capture());
            assertThat(captor.getValue().getPageNumber()).isEqualTo(1);
            assertThat(captor.getValue().getPageSize()).isEqualTo(10);
            assertThat(captor.getValue().getSort()).isEqualTo(Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")));
        }

        @Test
        @DisplayName("keeps an allowed sort (rating asc) and appends the id tie-breaker")
        void allowedSortKept() {
            when(reviewRepository.findByAuthorKeycloakId(eq(keycloakId), any(Pageable.class)))
                    .thenAnswer(inv -> Page.empty(inv.getArgument(1)));

            service.getMyReviews(keycloakId, PageRequest.of(0, 10, Sort.by(Sort.Order.asc("rating"))));

            ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
            verify(reviewRepository).findByAuthorKeycloakId(eq(keycloakId), captor.capture());
            assertThat(captor.getValue().getSort()).isEqualTo(Sort.by(Sort.Order.asc("rating"), Sort.Order.desc("id")));
        }

        @Test
        @DisplayName("an unknown sort field falls back to createdAt desc instead of failing")
        void unknownSortFallsBack() {
            when(reviewRepository.findByAuthorKeycloakId(eq(keycloakId), any(Pageable.class)))
                    .thenAnswer(inv -> Page.empty(inv.getArgument(1)));

            service.getMyReviews(keycloakId, PageRequest.of(0, 10, Sort.by("userEntity.email")));

            ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
            verify(reviewRepository).findByAuthorKeycloakId(eq(keycloakId), captor.capture());
            assertThat(captor.getValue().getSort()).isEqualTo(Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")));
        }
    }
}
