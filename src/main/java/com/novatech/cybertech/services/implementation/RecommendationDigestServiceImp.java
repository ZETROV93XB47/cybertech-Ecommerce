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
import com.novatech.cybertech.repositories.CartRepository;
import com.novatech.cybertech.repositories.OrderItemRepository;
import com.novatech.cybertech.repositories.ProductRepository;
import com.novatech.cybertech.repositories.RecommendationRepository;
import com.novatech.cybertech.repositories.UserRepository;
import com.novatech.cybertech.services.core.RecommendationDigestService;
import com.novatech.cybertech.utils.GorseItemUtils;
import com.novatech.cybertech.utils.UserContactUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

/**
 * See {@link RecommendationDigestService}.
 *
 * <p>Calls {@link GorseClient} directly instead of {@code RecommendationServiceImp}: that service
 * caches per user and hides which tier answered, while the e-mail needs a large candidate pool to
 * survive the exclusions, topped up with best sellers when Gorse has too few (or nothing).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RecommendationDigestServiceImp implements RecommendationDigestService {

    /** Statuses in which the user owns the products of the order: not suggested again. */
    private static final Set<OrderStatus> BOUGHT_STATUSES =
            EnumSet.of(OrderStatus.PAID, OrderStatus.AWAITING_SHIPPING, OrderStatus.SHIPPED, OrderStatus.DELIVERED);

    /** Candidates asked per product of the e-mail, so the exclusions still leave enough. */
    private static final int CANDIDATE_POOL_FACTOR = 3;

    private final GorseClient gorseClient;
    private final UserRepository userRepository;
    private final ProductRepository productRepository;
    private final OrderItemRepository orderItemRepository;
    private final CartRepository cartRepository;
    private final RecommendationRepository recommendationRepository;

    @Value("${cybertech.recommendation.digest.products-per-email:6}")
    private int productsPerEmail;

    @Value("${cybertech.recommendation.digest.min-hours-between-emails:20}")
    private int minHoursBetweenEmails;

    @Value("${cybertech.recommendation.digest.exclude-sent-days:7}")
    private int excludeSentDays;

    @Value("${application.frontend.url:http://localhost:4200}")
    private String frontendUrl;

    @Value("${cybertech.recommendation.digest.product-path:/products/}")
    private String productPath;

    @Value("${cybertech.recommendation.digest.unsubscribe-path:/unsubscribe/recommendations?token=}")
    private String unsubscribePath;

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Optional<NotificationContext<RecommendationDigestPayload>> prepareDigest(final Long userId) {
        final Optional<UserEntity> found = userRepository.findById(userId)
                .filter(user -> Boolean.TRUE.equals(user.getIsActive()) && user.isRecommendationEmailEnabled());
        if (found.isEmpty()) {
            return Optional.empty();
        }
        final UserEntity user = found.get();

        final LocalDateTime now = LocalDateTime.now();
        if (recommendationRepository.existsByUser_IdAndGeneratedAtAfter(userId, now.minusHours(minHoursBetweenEmails))) {
            log.debug("User {} already received a suggestion e-mail recently — skipped", user.getUuid());
            return Optional.empty();
        }

        final Set<UUID> excluded = new HashSet<>(recommendationRepository.findProductUuidsSentSince(userId, now.minusDays(excludeSentDays)));
        excluded.addAll(orderItemRepository.findProductUuidsBoughtByUser(userId, BOUGHT_STATUSES));
        excluded.addAll(cartRepository.findProductUuidsInCartOfUser(userId));

        final List<ProductEntity> selected = Stream.concat(personalizedCandidates(user), bestSellerCandidates(excluded.size()))
                .filter(product -> !excluded.contains(product.getUuid()))
                .filter(RecommendationDigestServiceImp::isInStock)
                .filter(distinctByUuid())
                .limit(productsPerEmail)
                .toList();

        if (selected.isEmpty()) {
            log.info("No product left to suggest to user {} — no e-mail", user.getUuid());
            return Optional.empty();
        }

        recordSent(user, selected, now);
        return Optional.of(buildContext(user, selected));
    }

    @Override
    @Transactional
    public void unsubscribe(final UUID token) {
        final UserEntity user = userRepository.findByRecommendationEmailUnsubscribeToken(token)
                .orElseThrow(() -> new RecommendationUnsubscribeTokenNotFoundException("No user owns the unsubscribe token " + token));
        user.setRecommendationEmailEnabled(false);
        userRepository.save(user);
        log.info("User {} unsubscribed from the suggestion e-mails", user.getUuid());
    }

    /**
     * Gorse's personalized suggestions, in Gorse's order. Gorse down or returning ids we no longer
     * know: no personalized candidate, the best sellers fill the e-mail.
     */
    private Stream<ProductEntity> personalizedCandidates(final UserEntity user) {
        final List<String> itemIds;
        try {
            itemIds = gorseClient.getRecommendations(user.getKeycloakId(), productsPerEmail * CANDIDATE_POOL_FACTOR);
        } catch (Exception e) {
            log.warn("Gorse recommend call failed for user {} — best sellers only", user.getUuid(), e);
            return Stream.empty();
        }
        if (itemIds == null || itemIds.isEmpty()) {
            return Stream.empty();
        }
        final List<UUID> uuids = GorseItemUtils.toProductUuids(itemIds);
        final Map<UUID, ProductEntity> byUuid = productRepository.findAllByUuidIn(uuids).stream()
                .collect(Collectors.toMap(ProductEntity::getUuid, Function.identity()));
        return uuids.stream().map(byUuid::get).filter(Objects::nonNull);
    }

    /** Best sellers, enough of them to still fill the e-mail once the exclusions are removed. */
    private Stream<ProductEntity> bestSellerCandidates(final int excludedCount) {
        final int size = productsPerEmail * CANDIDATE_POOL_FACTOR + excludedCount;
        return productRepository.findBestSellers(PageRequest.of(0, size)).stream();
    }

    /**
     * One row per product sent, written before the e-mail goes out (same transaction as the
     * selection): a rerun of the job finds them and doesn't e-mail the user twice. The score is
     * the rank in the e-mail (first product = highest score): Gorse's recommend endpoint returns
     * no score.
     */
    private void recordSent(final UserEntity user, final List<ProductEntity> products, final LocalDateTime sentAt) {
        if (user.getRecommendationEmailUnsubscribeToken() == null) {
            user.setRecommendationEmailUnsubscribeToken(UUID.randomUUID());
            userRepository.save(user);
        }
        final List<RecommendationEntity> rows = IntStream.range(0, products.size())
                .<RecommendationEntity>mapToObj(index -> RecommendationEntity.builder()
                        .user(user)
                        .product(products.get(index))
                        .score((double) (products.size() - index))
                        .generatedAt(sentAt)
                        .build())
                .toList();
        recommendationRepository.saveAll(rows);
    }

    private NotificationContext<RecommendationDigestPayload> buildContext(final UserEntity user, final List<ProductEntity> products) {
        final RecommendationDigestPayload payload = RecommendationDigestPayload.builder()
                .userContactDto(UserContactUtils.toUserContact(user))
                .products(products.stream().map(this::toRecommendedProduct).toList())
                .unsubscribeUrl(frontendUrl + unsubscribePath + user.getRecommendationEmailUnsubscribeToken())
                .build();
        return NotificationContext.<RecommendationDigestPayload>builder()
                .payload(payload)
                .subject(EmailTemplateType.RECOMMENDATION_DIGEST.getNotificationSubject().getSubject())
                .user(payload.getUserContactDto())
                .templatePath(EmailTemplateType.RECOMMENDATION_DIGEST.getTemplatePath())
                .notificationType(NotificationType.RECOMMENDATION_DIGEST)
                // Marketing message: e-mail only, whatever the user's favorite channel.
                .communicationChanel(CommunicationChanel.EMAIL)
                .build();
    }

    private RecommendedProductDto toRecommendedProduct(final ProductEntity product) {
        final List<String> photos = product.getPhotos();
        return RecommendedProductDto.builder()
                .name(product.getName())
                .price(product.getPrice())
                .imageUrl(photos == null || photos.isEmpty() ? null : photos.getFirst())
                .productUrl(frontendUrl + productPath + product.getUuid())
                .build();
    }

    private static boolean isInStock(final ProductEntity product) {
        final int stock = product.getStock() == null ? 0 : product.getStock();
        final int reserved = product.getReservedStock() == null ? 0 : product.getReservedStock();
        return stock - reserved > 0;
    }

    private static Predicate<ProductEntity> distinctByUuid() {
        final Set<UUID> seen = new HashSet<>();
        return product -> seen.add(product.getUuid());
    }
}
