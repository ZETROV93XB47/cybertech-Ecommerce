package com.novatech.cybertech.services.implementation;

import com.novatech.cybertech.dto.data.NotificationContext;
import com.novatech.cybertech.dto.data.StockAlertPayload;
import com.novatech.cybertech.dto.data.UserContactDto;
import com.novatech.cybertech.dto.response.stockalert.StockAlertResponseDto;
import com.novatech.cybertech.entities.ProductEntity;
import com.novatech.cybertech.entities.StockAlertEntity;
import com.novatech.cybertech.entities.UserEntity;
import com.novatech.cybertech.entities.enums.CommunicationChanel;
import com.novatech.cybertech.entities.enums.EmailTemplateType;
import com.novatech.cybertech.entities.enums.NotificationType;
import com.novatech.cybertech.entities.enums.StockAlertStatus;
import com.novatech.cybertech.exceptions.ProductAlreadyInStockException;
import com.novatech.cybertech.exceptions.ProductNotFoundException;
import com.novatech.cybertech.exceptions.StockAlertAlreadyExistsException;
import com.novatech.cybertech.exceptions.UserNotFoundException;
import com.novatech.cybertech.mappers.entity.StockAlertMapper;
import com.novatech.cybertech.repositories.ProductRepository;
import com.novatech.cybertech.repositories.StockAlertRepository;
import com.novatech.cybertech.repositories.UserRepository;
import com.novatech.cybertech.services.core.StockAlertService;
import com.novatech.cybertech.services.core.WebPushDeliveryService;
import com.novatech.cybertech.utils.ProductStockUtils;
import com.novatech.cybertech.utils.UserContactUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

/** See {@link StockAlertService}. */
@Slf4j
@Service
@RequiredArgsConstructor
public class StockAlertServiceImp implements StockAlertService {

    private static final String ALERT_ALREADY_PENDING = "A back-in-stock alert is already pending for product ";

    private final StockAlertRepository stockAlertRepository;
    private final UserRepository userRepository;
    private final ProductRepository productRepository;
    private final StockAlertMapper stockAlertMapper;
    private final WebPushDeliveryService webPushDeliveryService;

    @Value("${cybertech.stock-alert.expire-after-days:90}")
    private int expireAfterDays;

    @Value("${application.frontend.url:http://localhost:4200}")
    private String frontendUrl;

    @Value("${cybertech.recommendation.digest.product-path:/products/}")
    private String productPath;

    @Override
    @Transactional
    public StockAlertResponseDto subscribe(final String keycloakId, final UUID productUuid) {
        final UserEntity user = userRepository.findByKeycloakId(keycloakId)
                .orElseThrow(() -> new UserNotFoundException("No user with the keycloakId: " + keycloakId + " found"));
        final ProductEntity product = productRepository.findByUuid(productUuid)
                .orElseThrow(() -> new ProductNotFoundException("Product " + productUuid + " not found"));
        if (ProductStockUtils.isAvailable(product)) {
            throw new ProductAlreadyInStockException("Product " + productUuid + " is in stock: no alert needed");
        }

        // One row per (user, product): a sent, cancelled or expired alert is reused.
        final StockAlertEntity alert = stockAlertRepository.findByUser_KeycloakIdAndProduct_Uuid(keycloakId, productUuid)
                .orElseGet(() -> StockAlertEntity.builder().user(user).product(product).build());
        if (alert.getStatus() == StockAlertStatus.PENDING) {
            throw new StockAlertAlreadyExistsException(ALERT_ALREADY_PENDING + productUuid);
        }
        alert.setStatus(StockAlertStatus.PENDING);
        alert.setRequestedAt(LocalDateTime.now());
        alert.setNotifiedAt(null);

        try {
            final StockAlertEntity saved = stockAlertRepository.saveAndFlush(alert);
            log.info("Back-in-stock alert {} pending for product {}", saved.getUuid(), productUuid);
            return stockAlertMapper.toResponseDto(saved);
        } catch (DataIntegrityViolationException e) {
            // Two concurrent subscriptions: the unique (userId, productId) constraint let only one in.
            throw new StockAlertAlreadyExistsException(ALERT_ALREADY_PENDING + productUuid, e);
        }
    }

    @Override
    @Transactional
    public void unsubscribe(final String keycloakId, final UUID productUuid) {
        stockAlertRepository.findByUser_KeycloakIdAndProduct_Uuid(keycloakId, productUuid)
                .filter(alert -> alert.getStatus() == StockAlertStatus.PENDING)
                .ifPresent(alert -> {
                    alert.setStatus(StockAlertStatus.CANCELLED);
                    stockAlertRepository.save(alert);
                    log.info("Back-in-stock alert {} cancelled", alert.getUuid());
                });
    }

    @Override
    @Transactional(readOnly = true)
    public Page<StockAlertResponseDto> findMine(final String keycloakId, final Pageable pageable) {
        return stockAlertRepository.findByUser_KeycloakIdAndStatus(keycloakId, StockAlertStatus.PENDING, pageable)
                .map(stockAlertMapper::toResponseDto);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public List<NotificationContext<StockAlertPayload>> prepareAlert(final Long alertId) {
        final StockAlertEntity alert = stockAlertRepository.findWithUserAndProductById(alertId).orElse(null);
        if (alert == null || alert.getStatus() != StockAlertStatus.PENDING) {
            return List.of();
        }
        final ProductEntity product = alert.getProduct();
        final UserEntity user = alert.getUser();
        // Re-checked here: the stock may have gone again since the job listed the alert.
        if (!ProductStockUtils.isAvailable(product) || !Boolean.TRUE.equals(user.getIsActive())) {
            return List.of();
        }

        // Marked before the notifications go out: a rerun of the job won't send the alert twice.
        alert.setStatus(StockAlertStatus.NOTIFIED);
        alert.setNotifiedAt(LocalDateTime.now());
        stockAlertRepository.save(alert);
        log.info("Back-in-stock alert {} sent for product {}", alert.getUuid(), product.getUuid());

        final UserContactDto contact = UserContactUtils.toUserContact(user);
        final StockAlertPayload payload = toPayload(contact, product);
        final NotificationContext<StockAlertPayload> email = NotificationContext.<StockAlertPayload>builder()
                .payload(payload)
                .subject(EmailTemplateType.STOCK_ALERT.getNotificationSubject().getSubject())
                .user(contact)
                .templatePath(EmailTemplateType.STOCK_ALERT.getTemplatePath())
                .notificationType(NotificationType.STOCK_ALERT)
                .communicationChanel(CommunicationChanel.EMAIL)
                .build();
        // Web Push comes on top of the e-mail, one notification per browser (same rule as the
        // payment and shipping pushes, see WebPushNotificationListener).
        final Stream<NotificationContext<StockAlertPayload>> pushes = webPushDeliveryService
                .findActiveSubscriptionUuids(user.getKeycloakId()).stream()
                .map(subscriptionUuid -> NotificationContext.<StockAlertPayload>builder()
                        .payload(payload)
                        .user(contact)
                        .notificationType(NotificationType.STOCK_ALERT)
                        .communicationChanel(CommunicationChanel.PUSH_NOTIFICATION)
                        .pushSubscriptionUuid(subscriptionUuid)
                        .build());
        return Stream.concat(Stream.of(email), pushes).toList();
    }

    @Override
    @Transactional
    public int expireOldAlerts() {
        final int expired = stockAlertRepository.expirePendingRequestedBefore(LocalDateTime.now().minusDays(expireAfterDays));
        if (expired > 0) {
            log.info("{} back-in-stock alert(s) expired after {} days", expired, expireAfterDays);
        }
        return expired;
    }

    private StockAlertPayload toPayload(final UserContactDto contact, final ProductEntity product) {
        final List<String> photos = product.getPhotos();
        return StockAlertPayload.builder()
                .userContactDto(contact)
                .productUuid(product.getUuid())
                .productName(product.getName())
                .price(product.getPrice())
                .imageUrl(photos == null || photos.isEmpty() ? null : photos.getFirst())
                .productUrl(frontendUrl + productPath + product.getUuid())
                .build();
    }
}
