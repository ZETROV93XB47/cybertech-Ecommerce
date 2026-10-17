package com.novatech.cybertech.dto.data;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.novatech.cybertech.services.implementation.ShippingConfirmationPayload;

/**
 * Marker for payloads that drive a specific {@link com.novatech.cybertech.entities.enums.NotificationType}
 * dispatch.
 *
 * <p><b>Polymorphism (Phase 1 — redrive support):</b> the Phase 3 batch tasklet
 * needs to deserialize a persisted {@code NotificationRedrivePayload} blob from
 * a {@link com.novatech.cybertech.entities.NotificationEntity} row whose
 * concrete subtype is not known at compile-time of the read path. Jackson
 * therefore needs an explicit type-discriminator. We register the discriminator
 * on this interface (rather than spreading {@code @JsonTypeName} across each
 * subclass) so the round-trip stays self-contained: serialize via the
 * interface, read back via the interface, no extra config in the
 * {@link tools.jackson.databind.ObjectMapper}.
 *
 * <p>The {@link JsonSubTypes} list is the single source of truth — when a new
 * payload subclass is introduced its FQN does NOT need to be added; the
 * {@code MINIMAL_CLASS} discriminator only emits the relative class name and
 * Jackson resolves it through the standard polymorphic-type-validator chain.
 * The explicit list below is kept as documentation of <em>what we currently
 * round-trip</em> and to keep the {@code BasicPolymorphicTypeValidator}-style
 * allowlist enforceable if a project-wide validator is later added.
 *
 * <p>The annotations target the Jackson 2 namespace (com.fasterxml) — this is
 * intentional: Jackson 3 (tools.jackson) reads the legacy annotations through
 * its compatibility layer, and the annotations themselves still ship from the
 * jackson-annotations module.
 */
@JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        include = JsonTypeInfo.As.PROPERTY,
        property = "@type"
)
@JsonSubTypes({
        @JsonSubTypes.Type(value = ShippingConfirmationPayload.class, name = "shipping-confirmation"),
        @JsonSubTypes.Type(value = OrderConfirmationPayload.class, name = "order-confirmation")
})
public interface NotificationPayload {
}
