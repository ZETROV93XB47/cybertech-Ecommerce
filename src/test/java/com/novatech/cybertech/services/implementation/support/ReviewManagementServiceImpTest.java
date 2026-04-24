package com.novatech.cybertech.services.implementation.support;

import com.novatech.cybertech.dto.request.review.ReviewCreateRequestDto;
import com.novatech.cybertech.dto.request.review.ReviewUpdateRequestDto;
import com.novatech.cybertech.dto.response.moderation.ModerationResponseDto;
import com.novatech.cybertech.dto.response.review.ReviewResponseDto;
import com.novatech.cybertech.entities.OrderEntity;
import com.novatech.cybertech.entities.OrderItemEntity;
import com.novatech.cybertech.entities.ProductEntity;
import com.novatech.cybertech.entities.ReviewEntity;
import com.novatech.cybertech.entities.UserEntity;
import com.novatech.cybertech.exceptions.CommentPostNotAllowedException;
import com.novatech.cybertech.exceptions.OrderDoesntBelongsToUserException;
import com.novatech.cybertech.exceptions.OrderNotFoundException;
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
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link ReviewManagementServiceImp}.
 *
 * Pins {@code BUG-2506}: {@code create} only checks whether the product appears anywhere in the
 * caller's order history, NOT whether the referenced {@code orderUuid} belongs to the caller.
 * User A can therefore review user B's order so long as user A has bought the same product elsewhere.
 */
@ExtendWith(MockitoExtension.class)
class ReviewManagementServiceImpTest {

    @Mock private ReviewMapper reviewMapper;
    @Mock private UserRepository userRepository;
    @Mock private OrderRepository orderRepository;
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
        ProductEntity p = productWith(uuid);
        OrderItemEntity oi = OrderItemEntityBuilder.aValidOrderItemBuilder().productEntity(p).build();
        List<OrderItemEntity> items = new ArrayList<>();
        items.add(oi);
        return OrderEntityBuilder.aValidOrderBuilder().uuid(orderUuid).orderItemEntities(items).userEntity(owner).build();
    }

    /** Convenience overload: builds a caller-owned order (keycloakId = {@code "kc-1"}). */
    private OrderEntity callerOrderContainingProduct(final UUID uuid) {
        UserEntity caller = UserEntityBuilder.aValidUserBuilder().keycloakId(keycloakId).build();
        return orderContainingProduct(uuid, caller);
    }

    private ReviewCreateRequestDto createDto() {
        return ReviewCreateRequestDto.builder()
                .userUuid(UUID.randomUUID())
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
                    .thenReturn(ModerationResponseDto.builder().label("OK").score(0.1).build());
            when(reviewRepository.save(reviewEntity)).thenReturn(reviewEntity);
            when(reviewMapper.mapFromEntityToResponseDto(reviewEntity)).thenReturn(responseDto);

            ReviewResponseDto result = service.create(dto, keycloakId);

            assertThat(result).isSameAs(responseDto);
            assertThat(reviewEntity.getIsHateful()).isFalse();
            assertThat(reviewEntity.getUserEntity()).isSameAs(user);
            assertThat(reviewEntity.getProductEntity()).isSameAs(product);
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
            OrderEntity order = OrderEntityBuilder.aValidOrderBuilder().orderItemEntities(items).userEntity(user).build();

            when(userRepository.findByKeycloakIdAndIsActive(keycloakId, true)).thenReturn(Optional.of(user));
            when(orderRepository.findByUuid(orderUuid)).thenReturn(Optional.of(order));

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
            OrderEntity requestOrder = OrderEntityBuilder.aValidOrderBuilder().orderItemEntities(items).userEntity(user).build();

            // a HISTORICAL order DOES contain the product
            ProductEntity historicalProduct = productWith(productUuid);
            OrderItemEntity historicalItem = OrderItemEntityBuilder.aValidOrderItemBuilder().productEntity(historicalProduct).build();
            List<OrderItemEntity> hItems = new ArrayList<>();
            hItems.add(historicalItem);
            OrderEntity historicalOrder = OrderEntityBuilder.aValidOrderBuilder().orderItemEntities(hItems).build();

            user.getOrderEntities().add(historicalOrder);

            ReviewEntity reviewEntity = ReviewEntity.builder().comment("Excellent").build();
            ProductEntity product = productWith(productUuid);

            when(userRepository.findByKeycloakIdAndIsActive(keycloakId, true)).thenReturn(Optional.of(user));
            when(orderRepository.findByUuid(orderUuid)).thenReturn(Optional.of(requestOrder));
            when(reviewMapper.mapFromCreationRequestToEntity(dto)).thenReturn(reviewEntity);
            when(productRepository.findByUuid(productUuid)).thenReturn(Optional.of(product));
            when(moderationService.checkIfIsHateful("Excellent"))
                    .thenReturn(ModerationResponseDto.builder().score(0.1).build());
            when(reviewRepository.save(reviewEntity)).thenReturn(reviewEntity);
            when(reviewMapper.mapFromEntityToResponseDto(reviewEntity))
                    .thenReturn(ReviewResponseDto.builder().build());

            assertThat(service.create(dto, keycloakId)).isNotNull();
        }

        @Test
        @DisplayName("FIX BUG-2506: order belonging to another user is rejected even when product matches")
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
        @DisplayName("moderation score above 0.7 threshold -> CommentPostNotAllowedException; nothing persisted")
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
                    .thenReturn(ModerationResponseDto.builder().score(0.95).build());

            assertThatThrownBy(() -> service.create(dto, keycloakId))
                    .isInstanceOf(CommentPostNotAllowedException.class);

            verify(reviewRepository, never()).save(any());
        }

        @Test
        @DisplayName("threshold boundary: score < 0.7 (strictly) is allowed; > 0.7 strictly is blocked")
        void thresholdIsStrictlyGreaterThan0_7() {
            ReviewCreateRequestDto dto = createDto();
            UserEntity user = UserEntityBuilder.aValidUserBuilder().keycloakId(keycloakId).build();
            OrderEntity order = callerOrderContainingProduct(productUuid);
            ProductEntity product = productWith(productUuid);
            ReviewEntity reviewEntity = ReviewEntity.builder().comment("borderline").build();

            when(userRepository.findByKeycloakIdAndIsActive(keycloakId, true)).thenReturn(Optional.of(user));
            when(orderRepository.findByUuid(orderUuid)).thenReturn(Optional.of(order));
            when(reviewMapper.mapFromCreationRequestToEntity(dto)).thenReturn(reviewEntity);
            when(productRepository.findByUuid(productUuid)).thenReturn(Optional.of(product));
            // 0.69 is strictly below 0.7f promoted-to-double (~0.6999999880790710)
            when(moderationService.checkIfIsHateful("borderline"))
                    .thenReturn(ModerationResponseDto.builder().score(0.69d).build());
            when(reviewRepository.save(reviewEntity)).thenReturn(reviewEntity);
            when(reviewMapper.mapFromEntityToResponseDto(reviewEntity))
                    .thenReturn(ReviewResponseDto.builder().build());

            assertThat(service.create(dto, keycloakId)).isNotNull();
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
        @DisplayName("happy: author can update his own review")
        void authorCanUpdateOwnReview() {
            ReviewUpdateRequestDto dto = updateDto();
            UserEntity author = UserEntityBuilder.aValidUserBuilder().keycloakId(keycloakId).build();
            ReviewEntity existing = ReviewEntityBuilder.aValidReviewBuilder().uuid(reviewUuid).userEntity(author).build();
            ReviewEntity mappedFromUpdate = ReviewEntity.builder().rating(4).comment("better").build();
            ReviewResponseDto responseDto = ReviewResponseDto.builder().rating(4).build();

            when(userRepository.existsByKeycloakIdAndIsActive(keycloakId, true)).thenReturn(true);
            when(reviewRepository.findByUuid(reviewUuid)).thenReturn(Optional.of(existing));
            when(reviewMapper.mapFromUpdateRequestToEntity(dto)).thenReturn(mappedFromUpdate);
            when(reviewRepository.save(mappedFromUpdate)).thenReturn(mappedFromUpdate);
            when(reviewMapper.mapFromEntityToResponseDto(mappedFromUpdate)).thenReturn(responseDto);

            ReviewResponseDto result = service.update(dto, keycloakId);

            assertThat(result).isSameAs(responseDto);
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
}
