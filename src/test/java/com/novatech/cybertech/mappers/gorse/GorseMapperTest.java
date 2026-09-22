package com.novatech.cybertech.mappers.gorse;

import com.novatech.cybertech.dto.request.gorse.GorseFeedbackDto;
import com.novatech.cybertech.dto.request.gorse.GorseItemDto;
import com.novatech.cybertech.dto.request.gorse.GorseUserDto;
import com.novatech.cybertech.entities.ProductEntity;
import com.novatech.cybertech.entities.UserEntity;
import com.novatech.cybertech.entities.document.UserEvent;
import com.novatech.cybertech.entities.enums.Brand;
import com.novatech.cybertech.entities.enums.UserEventType;
import com.novatech.cybertech.fixtures.builders.ProductEntityBuilder;
import com.novatech.cybertech.fixtures.builders.UserEntityBuilder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import java.time.Instant;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link GorseMapper}.
 */
class GorseMapperTest {

    private final GorseMapper mapper = Mappers.getMapper(GorseMapper.class);

    @Test
    @DisplayName("toItemDto maps uuid to itemId, category+brand to labels, createdAt to timestamp")
    void toItemDto_mapsFields() {
        final ProductEntity product = ProductEntityBuilder.aValidProductBuilder()
                .category("COMPUTER")
                .brand(Brand.ASUS)
                .createdAt(LocalDateTime.of(2026, 1, 1, 0, 0))
                .build();

        final GorseItemDto dto = mapper.toItemDto(product);

        assertThat(dto.getItemId()).isEqualTo(product.getUuid().toString());
        assertThat(dto.isHidden()).isFalse();
        assertThat(dto.getLabels()).containsExactly("COMPUTER", "ASUS");
        assertThat(dto.getTimestamp()).isEqualTo(product.getCreatedAt().toInstant(java.time.ZoneOffset.UTC));
    }

    @Test
    @DisplayName("toUserDto maps keycloakId to userId")
    void toUserDto_mapsKeycloakId() {
        final UserEntity user = UserEntityBuilder.aValidUserBuilder().keycloakId("kc-42").build();

        final GorseUserDto dto = mapper.toUserDto(user);

        assertThat(dto.getUserId()).isEqualTo("kc-42");
    }

    @Test
    @DisplayName("toFeedbackDto maps eventType name to feedbackType and productId to itemId")
    void toFeedbackDto_mapsFields() {
        final Instant timestamp = Instant.now();
        final UserEvent event = UserEvent.builder()
                .userId("kc-42")
                .sessionId("session-1")
                .eventType(UserEventType.RATING_NEGATIVE)
                .productId("product-1")
                .timestamp(timestamp)
                .build();

        final GorseFeedbackDto dto = mapper.toFeedbackDto(event);

        assertThat(dto.getFeedbackType()).isEqualTo("RATING_NEGATIVE");
        assertThat(dto.getUserId()).isEqualTo("kc-42");
        assertThat(dto.getItemId()).isEqualTo("product-1");
        assertThat(dto.getTimestamp()).isEqualTo(timestamp);
    }
}
