package com.novatech.cybertech.dto.data;

import com.novatech.cybertech.entities.enums.UserEventType;
import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * One recommendation signal produced by a business action (purchase, cart, wishlist, review),
 * captured inside the business transaction and written as a {@code UserEvent} after commit.
 * The product facts are copied here so the AFTER_COMMIT listener never touches JPA entities.
 */
@Value
@Builder
public class RecommendationSignalDto {

    /** Keycloak subject of the user, as GorseMapper#toUserDto sends it. */
    String userId;
    UserEventType eventType;
    UUID productUuid;
    String category;
    String brand;
    BigDecimal price;
    /** Only for PURCHASE: makes the event idempotent on (orderUuid, productUuid). */
    UUID orderUuid;
}
