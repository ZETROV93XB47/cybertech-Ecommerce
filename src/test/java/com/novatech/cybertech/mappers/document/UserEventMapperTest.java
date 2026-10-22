package com.novatech.cybertech.mappers.document;

import com.novatech.cybertech.dto.request.event.UserEventDto;
import com.novatech.cybertech.entities.document.UserEvent;
import com.novatech.cybertech.entities.enums.UserEventType;
import com.novatech.cybertech.fixtures.dto.UserEventDtoFixtures;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link UserEventMapper}.
 */
class UserEventMapperTest {

    private final UserEventMapper mapper = Mappers.getMapper(UserEventMapper.class);

    @Test
    @DisplayName("shouldMapDtoToDocumentWithIgnoredIdAndTimestamp")
    void shouldMapDtoToDocumentWithIgnoredIdAndTimestamp() {
        Map<String, Object> meta = new HashMap<>();
        meta.put("source", "web");
        UserEventDto dto = UserEventDtoFixtures.aValidUserEventBuilder()
                .userId("user-1")
                .eventType(UserEventType.PURCHASE)
                .productId("p-1")
                .sessionId("s-1")
                .metadata(meta)
                .build();

        UserEvent doc = mapper.toDocument(dto);

        assertThat(doc).isNotNull();
        assertThat(doc.getUserId()).isEqualTo("user-1");
        assertThat(doc.getEventType()).isEqualTo(UserEventType.PURCHASE);
        assertThat(doc.getProductId()).isEqualTo("p-1");
        assertThat(doc.getSessionId()).isEqualTo("s-1");
        assertThat(doc.getMetadata()).containsEntry("source", "web");
        // id and timestamp are explicitly @Mapping(ignore = true).
        assertThat(doc.getId()).isNull();
        assertThat(doc.getTimestamp()).isNull();
    }

    @Test
    @DisplayName("shouldHandleNullMetadataGracefully")
    void shouldHandleNullMetadataGracefully() {
        UserEventDto dto = UserEventDtoFixtures.aValidUserEventBuilder().metadata(null).build();

        UserEvent doc = mapper.toDocument(dto);

        assertThat(doc).isNotNull();
        assertThat(doc.getMetadata()).isNull();
    }

    @Test
    @DisplayName("shouldReturnNullForNullSource")
    void shouldReturnNullForNullSource() {
        assertThat(mapper.toDocument(null)).isNull();
    }
}
