package com.novatech.cybertech.services.implementation;

import com.novatech.cybertech.dto.data.NotificationContext;
import com.novatech.cybertech.dto.data.StockAlertPayload;
import com.novatech.cybertech.dto.response.stockalert.StockAlertResponseDto;
import com.novatech.cybertech.entities.ProductEntity;
import com.novatech.cybertech.entities.StockAlertEntity;
import com.novatech.cybertech.entities.UserEntity;
import com.novatech.cybertech.entities.enums.CommunicationChanel;
import com.novatech.cybertech.entities.enums.NotificationType;
import com.novatech.cybertech.entities.enums.StockAlertStatus;
import com.novatech.cybertech.exceptions.ProductAlreadyInStockException;
import com.novatech.cybertech.exceptions.ProductNotFoundException;
import com.novatech.cybertech.exceptions.StockAlertAlreadyExistsException;
import com.novatech.cybertech.exceptions.UserNotFoundException;
import com.novatech.cybertech.fixtures.builders.ProductEntityBuilder;
import com.novatech.cybertech.fixtures.builders.UserEntityBuilder;
import com.novatech.cybertech.mappers.entity.StockAlertMapper;
import com.novatech.cybertech.repositories.ProductRepository;
import com.novatech.cybertech.repositories.StockAlertRepository;
import com.novatech.cybertech.repositories.UserRepository;
import com.novatech.cybertech.services.core.WebPushDeliveryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link StockAlertServiceImp}: subscription rules, cancellation, "my alerts",
 * preparation of the notifications and expiry.
 */
@ExtendWith(MockitoExtension.class)
class StockAlertServiceImpTest {

    private static final String KEYCLOAK_ID = "kc-alert-user";
    private static final String FRONT_URL = "https://shop.example";
    private static final long ALERT_ID = 7L;

    @Mock
    private StockAlertRepository stockAlertRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private ProductRepository productRepository;
    @Mock
    private StockAlertMapper stockAlertMapper;
    @Mock
    private WebPushDeliveryService webPushDeliveryService;

    @InjectMocks
    private StockAlertServiceImp service;

    private UserEntity user;
    private ProductEntity outOfStock;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "expireAfterDays", 90);
        ReflectionTestUtils.setField(service, "frontendUrl", FRONT_URL);
        ReflectionTestUtils.setField(service, "productPath", "/products/");
        user = UserEntityBuilder.aValidUserBuilder().keycloakId(KEYCLOAK_ID).build();
        outOfStock = ProductEntityBuilder.aValidProductBuilder().stock(2).reservedStock(2).build();
    }

    private StockAlertEntity alert(final StockAlertStatus status, final ProductEntity product) {
        return StockAlertEntity.builder().uuid(UUID.randomUUID()).user(user).product(product)
                .status(status).requestedAt(LocalDateTime.now().minusDays(3)).build();
    }

    @Nested
    @DisplayName("subscribe")
    class Subscribe {

        @BeforeEach
        void knownUserAndProduct() {
            lenient().when(userRepository.findByKeycloakId(KEYCLOAK_ID)).thenReturn(Optional.of(user));
            lenient().when(productRepository.findByUuid(outOfStock.getUuid())).thenReturn(Optional.of(outOfStock));
            lenient().when(stockAlertRepository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
        }

        @Test
        @DisplayName("out-of-stock product, no previous alert: new PENDING alert saved and returned")
        void createsPendingAlert() {
            when(stockAlertRepository.findByUser_KeycloakIdAndProduct_Uuid(KEYCLOAK_ID, outOfStock.getUuid())).thenReturn(Optional.empty());
            final StockAlertResponseDto dto = StockAlertResponseDto.builder().status(StockAlertStatus.PENDING).build();
            when(stockAlertMapper.toResponseDto(any())).thenReturn(dto);

            assertThat(service.subscribe(KEYCLOAK_ID, outOfStock.getUuid())).isSameAs(dto);

            final ArgumentCaptor<StockAlertEntity> saved = ArgumentCaptor.forClass(StockAlertEntity.class);
            verify(stockAlertRepository).saveAndFlush(saved.capture());
            assertThat(saved.getValue().getUser()).isSameAs(user);
            assertThat(saved.getValue().getProduct()).isSameAs(outOfStock);
            assertThat(saved.getValue().getStatus()).isEqualTo(StockAlertStatus.PENDING);
            assertThat(saved.getValue().getRequestedAt()).isCloseTo(LocalDateTime.now(), within(5, ChronoUnit.SECONDS));
        }

        @Test
        @DisplayName("previous alert already sent: same row back to PENDING, notifiedAt cleared")
        void reusesFinishedAlert() {
            final StockAlertEntity previous = alert(StockAlertStatus.NOTIFIED, outOfStock);
            previous.setNotifiedAt(LocalDateTime.now().minusDays(1));
            when(stockAlertRepository.findByUser_KeycloakIdAndProduct_Uuid(KEYCLOAK_ID, outOfStock.getUuid())).thenReturn(Optional.of(previous));

            service.subscribe(KEYCLOAK_ID, outOfStock.getUuid());

            verify(stockAlertRepository).saveAndFlush(previous);
            assertThat(previous.getStatus()).isEqualTo(StockAlertStatus.PENDING);
            assertThat(previous.getNotifiedAt()).isNull();
            assertThat(previous.getRequestedAt()).isAfter(LocalDateTime.now().minusMinutes(1));
        }

        @Test
        @DisplayName("alert already pending: 409 STOCK_ALERT_ALREADY_EXISTS, nothing saved")
        void alreadyPending() {
            when(stockAlertRepository.findByUser_KeycloakIdAndProduct_Uuid(KEYCLOAK_ID, outOfStock.getUuid()))
                    .thenReturn(Optional.of(alert(StockAlertStatus.PENDING, outOfStock)));

            assertThatThrownBy(() -> service.subscribe(KEYCLOAK_ID, outOfStock.getUuid()))
                    .isInstanceOf(StockAlertAlreadyExistsException.class);
            verify(stockAlertRepository, never()).saveAndFlush(any());
        }

        @Test
        @DisplayName("concurrent subscription caught by the unique constraint: 409 STOCK_ALERT_ALREADY_EXISTS")
        void concurrentSubscription() {
            when(stockAlertRepository.findByUser_KeycloakIdAndProduct_Uuid(KEYCLOAK_ID, outOfStock.getUuid())).thenReturn(Optional.empty());
            when(stockAlertRepository.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("uk_stock_alert_user_product"));

            assertThatThrownBy(() -> service.subscribe(KEYCLOAK_ID, outOfStock.getUuid()))
                    .isInstanceOf(StockAlertAlreadyExistsException.class)
                    .hasCauseInstanceOf(DataIntegrityViolationException.class);
        }

        @Test
        @DisplayName("product that can be ordered: 409 PRODUCT_ALREADY_IN_STOCK")
        void productInStock() {
            final ProductEntity inStock = ProductEntityBuilder.aValidProductBuilder().stock(5).reservedStock(4).build();
            when(productRepository.findByUuid(inStock.getUuid())).thenReturn(Optional.of(inStock));

            assertThatThrownBy(() -> service.subscribe(KEYCLOAK_ID, inStock.getUuid()))
                    .isInstanceOf(ProductAlreadyInStockException.class);
            verifyNoInteractions(stockAlertRepository);
        }

        @Test
        @DisplayName("unknown product: ProductNotFoundException")
        void unknownProduct() {
            final UUID unknown = UUID.randomUUID();
            when(productRepository.findByUuid(unknown)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.subscribe(KEYCLOAK_ID, unknown)).isInstanceOf(ProductNotFoundException.class);
        }

        @Test
        @DisplayName("unknown user: UserNotFoundException")
        void unknownUser() {
            when(userRepository.findByKeycloakId("ghost")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.subscribe("ghost", outOfStock.getUuid())).isInstanceOf(UserNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("unsubscribe / findMine")
    class UnsubscribeAndMine {

        @Test
        @DisplayName("pending alert: CANCELLED and saved")
        void cancelsPending() {
            final StockAlertEntity pending = alert(StockAlertStatus.PENDING, outOfStock);
            when(stockAlertRepository.findByUser_KeycloakIdAndProduct_Uuid(KEYCLOAK_ID, outOfStock.getUuid())).thenReturn(Optional.of(pending));

            service.unsubscribe(KEYCLOAK_ID, outOfStock.getUuid());

            assertThat(pending.getStatus()).isEqualTo(StockAlertStatus.CANCELLED);
            verify(stockAlertRepository).save(pending);
        }

        @Test
        @DisplayName("already sent alert: untouched (idempotent)")
        void sentAlertUntouched() {
            final StockAlertEntity sent = alert(StockAlertStatus.NOTIFIED, outOfStock);
            when(stockAlertRepository.findByUser_KeycloakIdAndProduct_Uuid(KEYCLOAK_ID, outOfStock.getUuid())).thenReturn(Optional.of(sent));

            service.unsubscribe(KEYCLOAK_ID, outOfStock.getUuid());

            assertThat(sent.getStatus()).isEqualTo(StockAlertStatus.NOTIFIED);
            verify(stockAlertRepository, never()).save(any());
        }

        @Test
        @DisplayName("no alert: no-op")
        void noAlert() {
            when(stockAlertRepository.findByUser_KeycloakIdAndProduct_Uuid(KEYCLOAK_ID, outOfStock.getUuid())).thenReturn(Optional.empty());

            service.unsubscribe(KEYCLOAK_ID, outOfStock.getUuid());

            verify(stockAlertRepository, never()).save(any());
        }

        @Test
        @DisplayName("findMine reads the caller's PENDING alerts only and maps them")
        void findMine() {
            final StockAlertEntity pending = alert(StockAlertStatus.PENDING, outOfStock);
            final PageRequest pageable = PageRequest.of(0, 20);
            when(stockAlertRepository.findByUser_KeycloakIdAndStatus(KEYCLOAK_ID, StockAlertStatus.PENDING, pageable))
                    .thenReturn(new PageImpl<>(List.of(pending)));
            final StockAlertResponseDto dto = StockAlertResponseDto.builder().uuid(pending.getUuid()).build();
            when(stockAlertMapper.toResponseDto(pending)).thenReturn(dto);

            final Page<StockAlertResponseDto> page = service.findMine(KEYCLOAK_ID, pageable);

            assertThat(page.getContent()).containsExactly(dto);
        }
    }

    @Nested
    @DisplayName("prepareAlert")
    class PrepareAlert {

        private ProductEntity backInStock;

        @BeforeEach
        void productBack() {
            backInStock = ProductEntityBuilder.aValidProductBuilder().name("Laptop Pro").price(new BigDecimal("1299.90"))
                    .photos(List.of("https://cdn.example/laptop.jpg")).stock(3).reservedStock(1).build();
        }

        @Test
        @DisplayName("available product: NOTIFIED before sending, one e-mail + one push per browser")
        void emailAndPushes() {
            final StockAlertEntity pending = alert(StockAlertStatus.PENDING, backInStock);
            when(stockAlertRepository.findWithUserAndProductById(ALERT_ID)).thenReturn(Optional.of(pending));
            final UUID browser1 = UUID.randomUUID();
            final UUID browser2 = UUID.randomUUID();
            when(webPushDeliveryService.findActiveSubscriptionUuids(KEYCLOAK_ID)).thenReturn(List.of(browser1, browser2));

            final List<NotificationContext<StockAlertPayload>> notifications = service.prepareAlert(ALERT_ID);

            assertThat(pending.getStatus()).isEqualTo(StockAlertStatus.NOTIFIED);
            assertThat(pending.getNotifiedAt()).isNotNull();
            verify(stockAlertRepository).save(pending);

            assertThat(notifications).hasSize(3);
            final NotificationContext<StockAlertPayload> email = notifications.getFirst();
            assertThat(email.getCommunicationChanel()).isEqualTo(CommunicationChanel.EMAIL);
            assertThat(email.getNotificationType()).isEqualTo(NotificationType.STOCK_ALERT);
            assertThat(email.getTemplatePath()).isEqualTo("email/stock-alert");
            assertThat(email.getSubject()).isNotBlank();
            assertThat(email.getUser().getEmail()).isEqualTo(user.getEmail());
            assertThat(email.getPayload()).isEqualTo(StockAlertPayload.builder()
                    .userContactDto(email.getUser())
                    .productUuid(backInStock.getUuid())
                    .productName("Laptop Pro")
                    .price(new BigDecimal("1299.90"))
                    .imageUrl("https://cdn.example/laptop.jpg")
                    .productUrl(FRONT_URL + "/products/" + backInStock.getUuid())
                    .build());

            assertThat(notifications.subList(1, 3))
                    .allSatisfy(push -> {
                        assertThat(push.getCommunicationChanel()).isEqualTo(CommunicationChanel.PUSH_NOTIFICATION);
                        assertThat(push.getNotificationType()).isEqualTo(NotificationType.STOCK_ALERT);
                        assertThat(push.getPayload()).isSameAs(email.getPayload());
                    })
                    .extracting(NotificationContext::getPushSubscriptionUuid)
                    .containsExactly(browser1, browser2);
        }

        @Test
        @DisplayName("e-mail is sent whatever the favorite channel (SMS user still gets the e-mail)")
        void emailWhateverFavoriteChannel() {
            user.setFavoriteCommunicationChanel(CommunicationChanel.SMS);
            when(stockAlertRepository.findWithUserAndProductById(ALERT_ID)).thenReturn(Optional.of(alert(StockAlertStatus.PENDING, backInStock)));
            when(webPushDeliveryService.findActiveSubscriptionUuids(KEYCLOAK_ID)).thenReturn(List.of());

            final List<NotificationContext<StockAlertPayload>> notifications = service.prepareAlert(ALERT_ID);

            assertThat(notifications).singleElement()
                    .extracting(NotificationContext::getCommunicationChanel).isEqualTo(CommunicationChanel.EMAIL);
        }

        @Test
        @DisplayName("product without photo: null image")
        void noPhoto() {
            backInStock = ProductEntityBuilder.aValidProductBuilder().photos(List.of()).stock(1).reservedStock(0).build();
            when(stockAlertRepository.findWithUserAndProductById(ALERT_ID)).thenReturn(Optional.of(alert(StockAlertStatus.PENDING, backInStock)));

            assertThat(service.prepareAlert(ALERT_ID).getFirst().getPayload().getImageUrl()).isNull();
        }

        @Test
        @DisplayName("stock gone again since the job listed the alert: nothing, alert stays PENDING")
        void stockGoneAgain() {
            final StockAlertEntity pending = alert(StockAlertStatus.PENDING, outOfStock);
            when(stockAlertRepository.findWithUserAndProductById(ALERT_ID)).thenReturn(Optional.of(pending));

            assertThat(service.prepareAlert(ALERT_ID)).isEmpty();
            assertThat(pending.getStatus()).isEqualTo(StockAlertStatus.PENDING);
            verify(stockAlertRepository, never()).save(any());
        }

        @Test
        @DisplayName("already sent by a previous run: nothing")
        void alreadySent() {
            when(stockAlertRepository.findWithUserAndProductById(ALERT_ID)).thenReturn(Optional.of(alert(StockAlertStatus.NOTIFIED, backInStock)));

            assertThat(service.prepareAlert(ALERT_ID)).isEmpty();
            verifyNoInteractions(webPushDeliveryService);
        }

        @Test
        @DisplayName("inactive user: nothing")
        void inactiveUser() {
            user.setIsActive(false);
            when(stockAlertRepository.findWithUserAndProductById(ALERT_ID)).thenReturn(Optional.of(alert(StockAlertStatus.PENDING, backInStock)));

            assertThat(service.prepareAlert(ALERT_ID)).isEmpty();
            verify(stockAlertRepository, never()).save(any());
        }

        @Test
        @DisplayName("alert deleted meanwhile: nothing")
        void alertGone() {
            when(stockAlertRepository.findWithUserAndProductById(ALERT_ID)).thenReturn(Optional.empty());

            assertThat(service.prepareAlert(ALERT_ID)).isEmpty();
        }
    }

    @Test
    @DisplayName("expireOldAlerts: PENDING alerts requested more than 90 days ago")
    void expireOldAlerts() {
        when(stockAlertRepository.expirePendingRequestedBefore(any())).thenReturn(4);

        assertThat(service.expireOldAlerts()).isEqualTo(4);

        final ArgumentCaptor<LocalDateTime> cutoff = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(stockAlertRepository).expirePendingRequestedBefore(cutoff.capture());
        assertThat(cutoff.getValue()).isBetween(LocalDateTime.now().minusDays(90).minusMinutes(1), LocalDateTime.now().minusDays(90));
    }
}
