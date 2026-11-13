package com.novatech.cybertech.utils;

import com.novatech.cybertech.dto.data.RecommendationSignalDto;
import com.novatech.cybertech.entities.CartItemEntity;
import com.novatech.cybertech.entities.ProductEntity;
import com.novatech.cybertech.entities.enums.UserEventType;
import com.novatech.cybertech.events.RecommendationSignalsEvent;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.util.StringUtils;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.novatech.cybertech.constants.CyberTechAppConstants.REVIEW_NEGATIVE_RATING_THRESHOLD;
import static com.novatech.cybertech.constants.CyberTechAppConstants.REVIEW_POSITIVE_RATING_THRESHOLD;

/**
 * Builds the recommendation signals of the business actions (see {@link RecommendationSignalsEvent})
 * and publishes them inside the caller's transaction.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class RecommendationSignalUtils {

    /** Quantity of one product in a cart, with the product itself (for the signal's metadata). */
    public record CartLine(ProductEntity product, int quantity) {
    }

    public static RecommendationSignalDto signal(final UserEventType type, final String userId, final ProductEntity product, final UUID orderUuid) {
        return RecommendationSignalDto.builder()
                .userId(userId)
                .eventType(type)
                .productUuid(product.getUuid())
                .category(product.getCategory())
                .brand(product.getBrand() == null ? null : product.getBrand().name())
                .price(product.getPrice())
                .orderUuid(orderUuid)
                .build();
    }

    public static RecommendationSignalDto signal(final UserEventType type, final String userId, final ProductEntity product) {
        return signal(type, userId, product, null);
    }

    /**
     * Cart content keyed by product uuid. Taken BEFORE a mutation, it must be a copy: the cart
     * lines are mutated in place ({@code increaseQuantity}). A product present on two lines
     * (possible in a PATCH /cart/update body) counts once with the summed quantity.
     */
    public static Map<UUID, CartLine> snapshot(final Collection<CartItemEntity> items) {
        if (items == null) {
            return Map.of();
        }
        return items.stream().collect(Collectors.toMap(
                item -> item.getProductEntity().getUuid(),
                item -> new CartLine(item.getProductEntity(), item.getQuantity()),
                (a, b) -> new CartLine(a.product(), a.quantity() + b.quantity()),
                LinkedHashMap::new));
    }

    /**
     * Signals of a cart change, from the difference before / after: a new product or a higher
     * quantity → ADD_TO_CART; a product gone → REMOVE_FROM_CART; a lower quantity that keeps the
     * product → nothing.
     */
    public static List<RecommendationSignalDto> cartDiff(final String userId, final Map<UUID, CartLine> before, final Map<UUID, CartLine> after) {
        final Stream<RecommendationSignalDto> added = after.entrySet().stream()
                .filter(e -> !before.containsKey(e.getKey()) || e.getValue().quantity() > before.get(e.getKey()).quantity())
                .map(e -> signal(UserEventType.ADD_TO_CART, userId, e.getValue().product()));
        final Stream<RecommendationSignalDto> removed = before.entrySet().stream()
                .filter(e -> !after.containsKey(e.getKey()))
                .map(e -> signal(UserEventType.REMOVE_FROM_CART, userId, e.getValue().product()));
        return Stream.concat(added, removed).toList();
    }

    /**
     * Signal of a published review: a rating >= {@code REVIEW_POSITIVE_RATING_THRESHOLD} is
     * positive, <= {@code REVIEW_NEGATIVE_RATING_THRESHOLD} negative, in between nothing. With a
     * comment the REVIEW_* type replaces the RATING_* one.
     */
    public static Optional<UserEventType> reviewSignalType(final Integer rating, final String comment) {
        if (rating == null) {
            return Optional.empty();
        }
        final boolean withComment = StringUtils.hasText(comment);
        if (rating >= REVIEW_POSITIVE_RATING_THRESHOLD) {
            return Optional.of(withComment ? UserEventType.REVIEW_POSITIVE : UserEventType.RATING_POSITIVE);
        }
        if (rating <= REVIEW_NEGATIVE_RATING_THRESHOLD) {
            return Optional.of(withComment ? UserEventType.REVIEW_NEGATIVE : UserEventType.RATING_NEGATIVE);
        }
        return Optional.empty();
    }

    /** Publishes the signals in the current transaction; nothing when there are none. */
    public static void publish(final ApplicationEventPublisher publisher, final Object source, final List<RecommendationSignalDto> signals) {
        final List<RecommendationSignalDto> nonNull = signals.stream().filter(Objects::nonNull).toList();
        if (!nonNull.isEmpty()) {
            publisher.publishEvent(new RecommendationSignalsEvent(source, nonNull));
        }
    }
}
