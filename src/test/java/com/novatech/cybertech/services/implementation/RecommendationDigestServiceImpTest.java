package com.novatech.cybertech.services.implementation;

import com.novatech.cybertech.clients.GorseClient;
import com.novatech.cybertech.dto.data.NotificationContext;
import com.novatech.cybertech.dto.data.RecommendationDigestPayload;
import com.novatech.cybertech.dto.data.RecommendedProductDto;
import com.novatech.cybertech.entities.ProductEntity;
import com.novatech.cybertech.entities.RecommendationEntity;
import com.novatech.cybertech.entities.UserEntity;
import com.novatech.cybertech.entities.enums.CommunicationChanel;
import com.novatech.cybertech.entities.enums.EmailTemplateType;
import com.novatech.cybertech.entities.enums.NotificationType;
import com.novatech.cybertech.entities.enums.OrderStatus;
import com.novatech.cybertech.exceptions.RecommendationUnsubscribeTokenNotFoundException;
import com.novatech.cybertech.fixtures.builders.ProductEntityBuilder;
import com.novatech.cybertech.fixtures.builders.UserEntityBuilder;
import com.novatech.cybertech.repositories.CartRepository;
import com.novatech.cybertech.repositories.OrderItemRepository;
import com.novatech.cybertech.repositories.ProductRepository;
import com.novatech.cybertech.repositories.RecommendationRepository;
import com.novatech.cybertech.repositories.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link RecommendationDigestServiceImp}: product selection of the daily
 * suggestion e-mail (Gorse then best sellers, exclusions), "already sent" guard, recorded rows and
 * unsubscribe.
 */
@ExtendWith(MockitoExtension.class)
class RecommendationDigestServiceImpTest {

    private static final long USER_ID = 42L;
    private static final String FRONT_URL = "https://shop.example";
    private static final int PRODUCTS_PER_EMAIL = 6;

    @Mock
    private GorseClient gorseClient;
    @Mock
    private UserRepository userRepository;
    @Mock
    private ProductRepository productRepository;
    @Mock
    private OrderItemRepository orderItemRepository;
    @Mock
    private CartRepository cartRepository;
    @Mock
    private RecommendationRepository recommendationRepository;

    @InjectMocks
    private RecommendationDigestServiceImp service;

    private UserEntity user;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "productsPerEmail", PRODUCTS_PER_EMAIL);
        ReflectionTestUtils.setField(service, "minHoursBetweenEmails", 20);
        ReflectionTestUtils.setField(service, "excludeSentDays", 7);
        ReflectionTestUtils.setField(service, "frontendUrl", FRONT_URL);
        ReflectionTestUtils.setField(service, "productPath", "/products/");
        ReflectionTestUtils.setField(service, "unsubscribePath", "/unsubscribe/recommendations?token=");

        user = UserEntityBuilder.aValidUserBuilder().id(USER_ID).build();

        lenient().when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        lenient().when(recommendationRepository.existsByUser_IdAndGeneratedAtAfter(eq(USER_ID), any())).thenReturn(false);
        lenient().when(recommendationRepository.findProductUuidsSentSince(eq(USER_ID), any())).thenReturn(Set.of());
        lenient().when(orderItemRepository.findProductUuidsBoughtByUser(eq(USER_ID), anyCollection())).thenReturn(Set.of());
        lenient().when(cartRepository.findProductUuidsInCartOfUser(USER_ID)).thenReturn(Set.of());
        lenient().when(gorseClient.getRecommendations(anyString(), anyInt())).thenReturn(List.of());
        lenient().when(productRepository.findAllByUuidIn(anyCollection())).thenReturn(List.of());
        lenient().when(productRepository.findBestSellers(any(Pageable.class))).thenReturn(new PageImpl<>(List.of()));
    }

    private static List<ProductEntity> products(final int count) {
        return IntStream.range(0, count)
                .<ProductEntity>mapToObj(i -> ProductEntityBuilder.aValidProductBuilder().name("Product " + i).build())
                .toList();
    }

    private void gorseSuggests(final List<ProductEntity> products) {
        when(gorseClient.getRecommendations(user.getKeycloakId(), PRODUCTS_PER_EMAIL * 3))
                .thenReturn(products.stream().map(p -> p.getUuid().toString()).toList());
        when(productRepository.findAllByUuidIn(anyCollection())).thenReturn(products);
    }

    private void bestSellersAre(final List<ProductEntity> products) {
        when(productRepository.findBestSellers(any(Pageable.class))).thenReturn(new PageImpl<>(products));
    }

    private static List<String> namesOf(final NotificationContext<RecommendationDigestPayload> context) {
        return context.getPayload().getProducts().stream().map(RecommendedProductDto::getName).toList();
    }

    @Nested
    @DisplayName("prepareDigest — selection")
    class Selection {

        @Test
        @DisplayName("Gorse suggestions first, in Gorse's order, capped at 6")
        void gorseSuggestionsFirst() {
            final List<ProductEntity> gorse = products(8);
            gorseSuggests(gorse);

            final NotificationContext<RecommendationDigestPayload> context = service.prepareDigest(USER_ID).orElseThrow();

            assertThat(namesOf(context)).containsExactly(
                    "Product 0", "Product 1", "Product 2", "Product 3", "Product 4", "Product 5");
        }

        @Test
        @DisplayName("Gorse with too few suggestions: topped up with best sellers, no duplicate")
        void toppedUpWithBestSellers() {
            final List<ProductEntity> gorse = products(2);
            gorseSuggests(gorse);
            final ProductEntity bestSeller = ProductEntityBuilder.aValidProductBuilder().name("Best seller").build();
            bestSellersAre(List.of(gorse.getFirst(), bestSeller));

            final NotificationContext<RecommendationDigestPayload> context = service.prepareDigest(USER_ID).orElseThrow();

            assertThat(namesOf(context)).containsExactly("Product 0", "Product 1", "Best seller");
        }

        @Test
        @DisplayName("Gorse empty: best sellers only")
        void gorseEmptyBestSellersOnly() {
            bestSellersAre(products(3));

            final NotificationContext<RecommendationDigestPayload> context = service.prepareDigest(USER_ID).orElseThrow();

            assertThat(namesOf(context)).containsExactly("Product 0", "Product 1", "Product 2");
        }

        @Test
        @DisplayName("Gorse down: best sellers only, no exception")
        void gorseDownBestSellersOnly() {
            when(gorseClient.getRecommendations(anyString(), anyInt())).thenThrow(new IllegalStateException("gorse down"));
            bestSellersAre(products(1));

            final NotificationContext<RecommendationDigestPayload> context = service.prepareDigest(USER_ID).orElseThrow();

            assertThat(namesOf(context)).containsExactly("Product 0");
        }

        @Test
        @DisplayName("Gorse id that is not a UUID or no longer a product: skipped")
        void unknownGorseIdsSkipped() {
            final ProductEntity known = ProductEntityBuilder.aValidProductBuilder().name("Known").build();
            when(gorseClient.getRecommendations(anyString(), anyInt()))
                    .thenReturn(List.of("not-a-uuid", UUID.randomUUID().toString(), known.getUuid().toString()));
            when(productRepository.findAllByUuidIn(anyCollection())).thenReturn(List.of(known));

            final NotificationContext<RecommendationDigestPayload> context = service.prepareDigest(USER_ID).orElseThrow();

            assertThat(namesOf(context)).containsExactly("Known");
        }

        @Test
        @DisplayName("out of stock (stock - reserved = 0, or null stock) excluded")
        void outOfStockExcluded() {
            final ProductEntity reserved = ProductEntityBuilder.aValidProductBuilder().name("All reserved").stock(3).reservedStock(3).build();
            final ProductEntity noStock = ProductEntityBuilder.aValidProductBuilder().name("No stock").stock(null).build();
            final ProductEntity inStock = ProductEntityBuilder.aValidProductBuilder().name("In stock").stock(1).reservedStock(null).build();
            bestSellersAre(List.of(reserved, noStock, inStock));

            final NotificationContext<RecommendationDigestPayload> context = service.prepareDigest(USER_ID).orElseThrow();

            assertThat(namesOf(context)).containsExactly("In stock");
        }

        @Test
        @DisplayName("bought, in-cart and recently sent products excluded")
        void boughtCartAndRecentlySentExcluded() {
            final List<ProductEntity> all = products(4);
            when(orderItemRepository.findProductUuidsBoughtByUser(eq(USER_ID), anyCollection())).thenReturn(Set.of(all.get(0).getUuid()));
            when(cartRepository.findProductUuidsInCartOfUser(USER_ID)).thenReturn(Set.of(all.get(1).getUuid()));
            when(recommendationRepository.findProductUuidsSentSince(eq(USER_ID), any())).thenReturn(Set.of(all.get(2).getUuid()));
            gorseSuggests(all);

            final NotificationContext<RecommendationDigestPayload> context = service.prepareDigest(USER_ID).orElseThrow();

            assertThat(namesOf(context)).containsExactly("Product 3");
        }

        @Test
        @DisplayName("bought = PAID, AWAITING_SHIPPING, SHIPPED, DELIVERED orders; recently sent = last 7 days")
        @SuppressWarnings("unchecked")
        void exclusionWindows() {
            bestSellersAre(products(1));

            service.prepareDigest(USER_ID);

            final ArgumentCaptor<Collection<OrderStatus>> statuses = ArgumentCaptor.forClass(Collection.class);
            verify(orderItemRepository).findProductUuidsBoughtByUser(eq(USER_ID), statuses.capture());
            assertThat(statuses.getValue()).containsExactlyInAnyOrder(
                    OrderStatus.PAID, OrderStatus.AWAITING_SHIPPING, OrderStatus.SHIPPED, OrderStatus.DELIVERED);

            final ArgumentCaptor<LocalDateTime> since = ArgumentCaptor.forClass(LocalDateTime.class);
            verify(recommendationRepository).findProductUuidsSentSince(eq(USER_ID), since.capture());
            assertThat(since.getValue()).isBetween(LocalDateTime.now().minusDays(7).minusMinutes(1), LocalDateTime.now().minusDays(7));
        }

        @Test
        @DisplayName("best-seller page is large enough to survive the exclusions")
        void bestSellerPageCoversExclusions() {
            final Set<UUID> sent = Set.of(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
            when(recommendationRepository.findProductUuidsSentSince(eq(USER_ID), any())).thenReturn(sent);

            service.prepareDigest(USER_ID);

            final ArgumentCaptor<Pageable> page = ArgumentCaptor.forClass(Pageable.class);
            verify(productRepository).findBestSellers(page.capture());
            assertThat(page.getValue().getPageSize()).isEqualTo(PRODUCTS_PER_EMAIL * 3 + sent.size());
        }

        @Test
        @DisplayName("nothing left after the exclusions: no e-mail, nothing recorded")
        void nothingLeftNoEmail() {
            final ProductEntity bought = ProductEntityBuilder.aValidProduct();
            when(orderItemRepository.findProductUuidsBoughtByUser(eq(USER_ID), anyCollection())).thenReturn(Set.of(bought.getUuid()));
            bestSellersAre(List.of(bought));

            assertThat(service.prepareDigest(USER_ID)).isEmpty();

            verify(recommendationRepository, never()).saveAll(anyList());
            verify(userRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("prepareDigest — guards")
    class Guards {

        @Test
        @DisplayName("unknown user: empty")
        void unknownUser() {
            when(userRepository.findById(USER_ID)).thenReturn(Optional.empty());

            assertThat(service.prepareDigest(USER_ID)).isEmpty();
            verifyNoInteractions(gorseClient, recommendationRepository);
        }

        @Test
        @DisplayName("inactive user: empty")
        void inactiveUser() {
            user.setIsActive(false);

            assertThat(service.prepareDigest(USER_ID)).isEmpty();
            verifyNoInteractions(gorseClient);
        }

        @Test
        @DisplayName("unsubscribed user: empty")
        void unsubscribedUser() {
            user.setRecommendationEmailEnabled(false);

            assertThat(service.prepareDigest(USER_ID)).isEmpty();
            verifyNoInteractions(gorseClient);
        }

        @Test
        @DisplayName("e-mail already sent in the last 20 h: empty, Gorse not called")
        void alreadySentRecently() {
            when(recommendationRepository.existsByUser_IdAndGeneratedAtAfter(eq(USER_ID), any())).thenReturn(true);

            assertThat(service.prepareDigest(USER_ID)).isEmpty();

            final ArgumentCaptor<LocalDateTime> since = ArgumentCaptor.forClass(LocalDateTime.class);
            verify(recommendationRepository).existsByUser_IdAndGeneratedAtAfter(eq(USER_ID), since.capture());
            assertThat(since.getValue()).isBetween(LocalDateTime.now().minusHours(20).minusMinutes(1), LocalDateTime.now().minusHours(20));
            verifyNoInteractions(gorseClient);
            verify(recommendationRepository, never()).saveAll(anyList());
        }
    }

    @Nested
    @DisplayName("prepareDigest — recorded rows and notification")
    class Result {

        @Test
        @DisplayName("one row per product sent, score = rank, same send date")
        @SuppressWarnings("unchecked")
        void recordsOneRowPerProduct() {
            final List<ProductEntity> gorse = products(3);
            gorseSuggests(gorse);

            service.prepareDigest(USER_ID);

            final ArgumentCaptor<List<RecommendationEntity>> rows = ArgumentCaptor.forClass(List.class);
            verify(recommendationRepository).saveAll(rows.capture());
            assertThat(rows.getValue()).extracting(RecommendationEntity::getProduct).containsExactlyElementsOf(gorse);
            assertThat(rows.getValue()).extracting(RecommendationEntity::getUser).containsOnly(user);
            assertThat(rows.getValue()).extracting(RecommendationEntity::getScore).containsExactly(3.0, 2.0, 1.0);
            assertThat(rows.getValue()).extracting(RecommendationEntity::getGeneratedAt).doesNotContainNull()
                    .containsOnly(rows.getValue().getFirst().getGeneratedAt());
        }

        @Test
        @DisplayName("first e-mail: unsubscribe token generated and saved, used in the link")
        void tokenGeneratedOnFirstEmail() {
            bestSellersAre(products(1));
            assertThat(user.getRecommendationEmailUnsubscribeToken()).isNull();

            final NotificationContext<RecommendationDigestPayload> context = service.prepareDigest(USER_ID).orElseThrow();

            assertThat(user.getRecommendationEmailUnsubscribeToken()).isNotNull();
            verify(userRepository).save(user);
            assertThat(context.getPayload().getUnsubscribeUrl())
                    .isEqualTo(FRONT_URL + "/unsubscribe/recommendations?token=" + user.getRecommendationEmailUnsubscribeToken());
        }

        @Test
        @DisplayName("existing token kept, user not saved again")
        void existingTokenKept() {
            final UUID token = UUID.randomUUID();
            user.setRecommendationEmailUnsubscribeToken(token);
            bestSellersAre(products(1));

            final NotificationContext<RecommendationDigestPayload> context = service.prepareDigest(USER_ID).orElseThrow();

            assertThat(user.getRecommendationEmailUnsubscribeToken()).isEqualTo(token);
            verify(userRepository, never()).save(any());
            assertThat(context.getPayload().getUnsubscribeUrl()).endsWith("token=" + token);
        }

        @Test
        @DisplayName("notification: e-mail channel whatever the favorite one, digest type, subject and template")
        void notificationContext() {
            user.setFavoriteCommunicationChanel(CommunicationChanel.SMS);
            bestSellersAre(products(1));

            final NotificationContext<RecommendationDigestPayload> context = service.prepareDigest(USER_ID).orElseThrow();

            assertThat(context.getCommunicationChanel()).isEqualTo(CommunicationChanel.EMAIL);
            assertThat(context.getNotificationType()).isEqualTo(NotificationType.RECOMMENDATION_DIGEST);
            assertThat(context.getSubject()).isEqualTo(EmailTemplateType.RECOMMENDATION_DIGEST.getNotificationSubject().getSubject());
            assertThat(context.getTemplatePath()).isEqualTo("email/recommendation-digest");
            assertThat(context.getUser().getEmail()).isEqualTo(user.getEmail());
            assertThat(context.getPayload().getUserContactDto().getName()).isEqualTo("Jane");
        }

        @Test
        @DisplayName("product line: name, price, first photo, front product link; no photo → null image")
        void productLines() {
            final ProductEntity withPhoto = ProductEntityBuilder.aValidProductBuilder()
                    .name("Laptop").price(new BigDecimal("1299.90"))
                    .photos(List.of("https://cdn.example/a.jpg", "https://cdn.example/b.jpg")).build();
            final ProductEntity noPhoto = ProductEntityBuilder.aValidProductBuilder().name("Mouse").photos(List.of()).build();
            bestSellersAre(List.of(withPhoto, noPhoto));

            final List<RecommendedProductDto> lines = service.prepareDigest(USER_ID).orElseThrow().getPayload().getProducts();

            assertThat(lines.get(0)).isEqualTo(RecommendedProductDto.builder()
                    .name("Laptop").price(new BigDecimal("1299.90"))
                    .imageUrl("https://cdn.example/a.jpg")
                    .productUrl(FRONT_URL + "/products/" + withPhoto.getUuid())
                    .build());
            assertThat(lines.get(1).getImageUrl()).isNull();
        }
    }

    @Nested
    @DisplayName("unsubscribe")
    class Unsubscribe {

        @Test
        @DisplayName("known token: e-mails disabled and saved")
        void knownToken() {
            final UUID token = UUID.randomUUID();
            when(userRepository.findByRecommendationEmailUnsubscribeToken(token)).thenReturn(Optional.of(user));

            service.unsubscribe(token);

            assertThat(user.isRecommendationEmailEnabled()).isFalse();
            verify(userRepository).save(user);
        }

        @Test
        @DisplayName("unknown token: RecommendationUnsubscribeTokenNotFoundException")
        void unknownToken() {
            final UUID token = UUID.randomUUID();
            when(userRepository.findByRecommendationEmailUnsubscribeToken(token)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.unsubscribe(token))
                    .isInstanceOf(RecommendationUnsubscribeTokenNotFoundException.class)
                    .hasMessageContaining(token.toString());
            verify(userRepository, never()).save(any());
        }
    }

    @Test
    @DisplayName("new user defaults to receiving the e-mail")
    void newUserReceivesEmailByDefault() {
        assertThat(UserEntityBuilder.aValidUser().isRecommendationEmailEnabled()).isTrue();
        assertThat(new UserEntity().isRecommendationEmailEnabled()).isTrue();
    }
}
