package com.novatech.cybertech.services.implementation.support;

import com.novatech.cybertech.dto.data.NotificationContext;
import com.novatech.cybertech.dto.data.RecommendationDigestPayload;
import com.novatech.cybertech.dto.data.RecommendedProductDto;
import com.novatech.cybertech.dto.data.UserContactDto;
import com.novatech.cybertech.entities.enums.EmailTemplateType;
import com.novatech.cybertech.services.core.NotificationProcessor;
import com.novatech.cybertech.services.implementation.RecommendationDigestNotification;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

/** Unit tests for {@link RecommendationDigestNotification}. */
@ExtendWith(MockitoExtension.class)
class RecommendationDigestNotificationTest {

    @Mock
    private NotificationProcessor notificationProcessor;

    private final RecommendationDigestNotification notification = new RecommendationDigestNotification();

    @Test
    @DisplayName("populates userName/products/unsubscribeUrl, keeps subject and template, calls the processor once")
    void populatesTemplateVariables() {
        final List<RecommendedProductDto> products = List.of(RecommendedProductDto.builder()
                .name("Laptop").price(new BigDecimal("999.00")).productUrl("https://shop/products/1").build());
        final RecommendationDigestPayload payload = RecommendationDigestPayload.builder()
                .userContactDto(UserContactDto.builder().name("Jane").email("jane@example.com").build())
                .products(products)
                .unsubscribeUrl("https://shop/unsubscribe/recommendations?token=abc")
                .build();
        final NotificationContext<RecommendationDigestPayload> ctx = NotificationContext.<RecommendationDigestPayload>builder()
                .payload(payload)
                .subject(EmailTemplateType.RECOMMENDATION_DIGEST.getNotificationSubject().getSubject())
                .templatePath(EmailTemplateType.RECOMMENDATION_DIGEST.getTemplatePath())
                .build();

        notification.sendNotification(ctx, notificationProcessor);

        assertThat(ctx.getSubject()).isEqualTo("Nos suggestions pour vous - Cybertech");
        assertThat(ctx.getTemplatePath()).isEqualTo("email/recommendation-digest");
        assertThat(ctx.getTemplateVariables())
                .containsEntry("userName", "Jane")
                .containsEntry("products", products)
                .containsEntry("unsubscribeUrl", "https://shop/unsubscribe/recommendations?token=abc")
                .hasSize(3);
        verify(notificationProcessor).sendMessage(ctx);
        verifyNoMoreInteractions(notificationProcessor);
    }
}
