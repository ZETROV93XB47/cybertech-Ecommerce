package com.novatech.cybertech.utils;

import com.novatech.cybertech.dto.data.RecommendationSignalDto;
import com.novatech.cybertech.entities.CartItemEntity;
import com.novatech.cybertech.entities.ProductEntity;
import com.novatech.cybertech.entities.enums.UserEventType;
import com.novatech.cybertech.events.RecommendationSignalsEvent;
import com.novatech.cybertech.fixtures.builders.CartItemEntityBuilder;
import com.novatech.cybertech.fixtures.builders.ProductEntityBuilder;
import com.novatech.cybertech.utils.RecommendationSignalUtils.CartLine;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@DisplayName("RecommendationSignalUtils")
class RecommendationSignalUtilsTest {

    private static final String USER = "kc-user";

    private final ProductEntity phone = ProductEntityBuilder.aValidProductBuilder().category("SMARTPHONE").price(new BigDecimal("799.00")).build();
    private final ProductEntity watch = ProductEntityBuilder.aValidProductBuilder().category("SMARTWATCH").build();
    private final ProductEntity screen = ProductEntityBuilder.aValidProductBuilder().category("MONITOR").build();

    private static CartItemEntity line(final ProductEntity product, final int quantity) {
        return CartItemEntityBuilder.aValidCartItemBuilder().productEntity(product).quantity(quantity).build();
    }

    private static Map<UUID, CartLine> cart(final CartItemEntity... lines) {
        return RecommendationSignalUtils.snapshot(new ArrayList<>(Arrays.asList(lines)));
    }

    @Nested
    @DisplayName("cartDiff (before / after, as PATCH /cart/update replaces every line)")
    class CartDiff {

        @Test
        @DisplayName("new product -> ADD_TO_CART")
        void newProduct() {
            final List<RecommendationSignalDto> signals = RecommendationSignalUtils.cartDiff(USER, cart(line(phone, 1)), cart(line(phone, 1), line(watch, 1)));

            assertThat(signals).extracting(RecommendationSignalDto::getEventType, RecommendationSignalDto::getProductUuid)
                    .containsExactly(tuple(UserEventType.ADD_TO_CART, watch.getUuid()));
        }

        @Test
        @DisplayName("higher quantity -> ADD_TO_CART")
        void higherQuantity() {
            final List<RecommendationSignalDto> signals = RecommendationSignalUtils.cartDiff(USER, cart(line(phone, 1)), cart(line(phone, 3)));

            assertThat(signals).extracting(RecommendationSignalDto::getEventType).containsExactly(UserEventType.ADD_TO_CART);
        }

        @Test
        @DisplayName("lower quantity that keeps the product -> nothing")
        void lowerQuantity() {
            assertThat(RecommendationSignalUtils.cartDiff(USER, cart(line(phone, 3)), cart(line(phone, 1)))).isEmpty();
        }

        @Test
        @DisplayName("product gone -> REMOVE_FROM_CART")
        void productGone() {
            final List<RecommendationSignalDto> signals = RecommendationSignalUtils.cartDiff(USER, cart(line(phone, 1), line(watch, 2)), cart(line(phone, 1)));

            assertThat(signals).extracting(RecommendationSignalDto::getEventType, RecommendationSignalDto::getProductUuid)
                    .containsExactly(tuple(UserEventType.REMOVE_FROM_CART, watch.getUuid()));
        }

        @Test
        @DisplayName("everything at once: one ADD per new / increased product, one REMOVE per vanished one")
        void mixed() {
            final List<RecommendationSignalDto> signals = RecommendationSignalUtils.cartDiff(USER,
                    cart(line(phone, 1), line(watch, 4)),
                    cart(line(phone, 2), line(screen, 1)));

            assertThat(signals).extracting(RecommendationSignalDto::getEventType, RecommendationSignalDto::getProductUuid)
                    .containsExactlyInAnyOrder(
                            tuple(UserEventType.ADD_TO_CART, phone.getUuid()),
                            tuple(UserEventType.ADD_TO_CART, screen.getUuid()),
                            tuple(UserEventType.REMOVE_FROM_CART, watch.getUuid()));
        }

        @Test
        @DisplayName("same content -> nothing")
        void unchanged() {
            assertThat(RecommendationSignalUtils.cartDiff(USER, cart(line(phone, 2)), cart(line(phone, 2)))).isEmpty();
        }

        @Test
        @DisplayName("snapshot sums a product sent on two lines and survives later mutation of the lines")
        void snapshotIsACopy() {
            final CartItemEntity first = line(phone, 1);
            final Map<UUID, CartLine> snapshot = cart(first, line(phone, 2));
            first.increaseQuantity(9);

            assertThat(snapshot.get(phone.getUuid()).quantity()).isEqualTo(3);
            assertThat(RecommendationSignalUtils.snapshot(null)).isEmpty();
        }
    }

    @Test
    @DisplayName("signal copies uuid, category, brand and price of the product, plus the order")
    void signalCarriesProductFacts() {
        final UUID order = UUID.randomUUID();
        final RecommendationSignalDto signal = RecommendationSignalUtils.signal(UserEventType.PURCHASE, USER, phone, order);

        assertThat(signal.getUserId()).isEqualTo(USER);
        assertThat(signal.getProductUuid()).isEqualTo(phone.getUuid());
        assertThat(signal.getCategory()).isEqualTo("SMARTPHONE");
        assertThat(signal.getBrand()).isEqualTo(phone.getBrand().name());
        assertThat(signal.getPrice()).isEqualByComparingTo("799.00");
        assertThat(signal.getOrderUuid()).isEqualTo(order);
    }

    @ParameterizedTest(name = "rating {0}, comment ''{1}'' -> {2}")
    @CsvSource(nullValues = "NONE", value = {
            "5, NONE, RATING_POSITIVE",
            "4, NONE, RATING_POSITIVE",
            "3, NONE, NONE",
            "2, NONE, RATING_NEGATIVE",
            "1, NONE, RATING_NEGATIVE",
            "5, Super écran, REVIEW_POSITIVE",
            "4, Bien, REVIEW_POSITIVE",
            "3, Moyen, NONE",
            "2, Décevant, REVIEW_NEGATIVE",
            "1, Nul, REVIEW_NEGATIVE",
            "5, '   ', RATING_POSITIVE"
    })
    void reviewThresholds(final Integer rating, final String comment, final UserEventType expected) {
        assertThat(RecommendationSignalUtils.reviewSignalType(rating, comment)).isEqualTo(java.util.Optional.ofNullable(expected));
    }

    @Test
    @DisplayName("no rating -> no signal")
    void noRating() {
        assertThat(RecommendationSignalUtils.reviewSignalType(null, "text")).isEmpty();
    }

    @Test
    @DisplayName("publish: one event with the signals; nothing at all for an empty list")
    void publish() {
        final ApplicationEventPublisher publisher = mock(ApplicationEventPublisher.class);
        final RecommendationSignalDto signal = RecommendationSignalUtils.signal(UserEventType.WISHLIST_ADD, USER, phone);

        RecommendationSignalUtils.publish(publisher, this, List.of(signal));

        final ArgumentCaptor<RecommendationSignalsEvent> captor = ArgumentCaptor.forClass(RecommendationSignalsEvent.class);
        verify(publisher).publishEvent(captor.capture());
        assertThat(captor.getValue().getSignals()).containsExactly(signal);

        final ApplicationEventPublisher silent = mock(ApplicationEventPublisher.class);
        RecommendationSignalUtils.publish(silent, this, List.of());
        verifyNoInteractions(silent);
    }
}
