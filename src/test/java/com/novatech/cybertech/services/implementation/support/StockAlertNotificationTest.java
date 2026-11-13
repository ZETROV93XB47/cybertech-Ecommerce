package com.novatech.cybertech.services.implementation.support;

import com.novatech.cybertech.dto.data.NotificationContext;
import com.novatech.cybertech.dto.data.StockAlertPayload;
import com.novatech.cybertech.dto.data.UserContactDto;
import com.novatech.cybertech.entities.enums.EmailTemplateType;
import com.novatech.cybertech.services.core.NotificationProcessor;
import com.novatech.cybertech.services.implementation.MailServiceImp;
import com.novatech.cybertech.services.implementation.StockAlertNotification;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

/**
 * Unit tests for {@link StockAlertNotification}, plus a render of the real
 * {@code templates/email/stock-alert.html} with the variables the handler sets.
 */
@ExtendWith(MockitoExtension.class)
class StockAlertNotificationTest {

    @Mock
    private NotificationProcessor notificationProcessor;

    private final StockAlertNotification notification = new StockAlertNotification();

    private SpringTemplateEngine templateEngine;

    @BeforeEach
    void setUp() {
        final ClassLoaderTemplateResolver resolver = new ClassLoaderTemplateResolver();
        resolver.setPrefix("templates/");
        resolver.setSuffix(".html");
        resolver.setTemplateMode(TemplateMode.HTML);
        resolver.setCharacterEncoding("UTF-8");
        templateEngine = new SpringTemplateEngine();
        templateEngine.setTemplateResolver(resolver);
    }

    private static NotificationContext<StockAlertPayload> context(final String imageUrl) {
        final StockAlertPayload payload = StockAlertPayload.builder()
                .userContactDto(UserContactDto.builder().name("Jane").email("jane@example.com").build())
                .productUuid(UUID.randomUUID())
                .productName("Laptop Pro")
                .price(new BigDecimal("1299.9"))
                .imageUrl(imageUrl)
                .productUrl("https://shop.example/products/p1")
                .build();
        return NotificationContext.<StockAlertPayload>builder()
                .payload(payload)
                .subject(EmailTemplateType.STOCK_ALERT.getNotificationSubject().getSubject())
                .templatePath(EmailTemplateType.STOCK_ALERT.getTemplatePath())
                .build();
    }

    @Test
    @DisplayName("populates the template variables, keeps subject and template, calls the processor once")
    void populatesTemplateVariables() {
        final NotificationContext<StockAlertPayload> ctx = context("https://cdn.example/laptop.jpg");

        notification.sendNotification(ctx, notificationProcessor);

        assertThat(ctx.getTemplatePath()).isEqualTo("email/stock-alert");
        assertThat(ctx.getTemplateVariables())
                .containsEntry("userName", "Jane")
                .containsEntry("productName", "Laptop Pro")
                .containsEntry("price", new BigDecimal("1299.9"))
                .containsEntry("imageUrl", "https://cdn.example/laptop.jpg")
                .containsEntry("productUrl", "https://shop.example/products/p1")
                .hasSize(5);
        verify(notificationProcessor).sendMessage(ctx);
        verifyNoMoreInteractions(notificationProcessor);
    }

    @Test
    @DisplayName("a product without photo does not break the handler (null image)")
    void nullImage() {
        final NotificationContext<StockAlertPayload> ctx = context(null);

        notification.sendNotification(ctx, notificationProcessor);

        assertThat(ctx.getTemplateVariables()).containsEntry("imageUrl", null);
    }

    @Test
    @DisplayName("real template: name, French price, image, product link")
    void rendersTemplate() {
        final NotificationContext<StockAlertPayload> ctx = context("https://cdn.example/laptop.jpg");
        notification.sendNotification(ctx, notificationProcessor);

        final String html = templateEngine.process(ctx.getTemplatePath(), MailServiceImp.toEmailContext(ctx.getTemplateVariables()));

        assertThat(html)
                .contains("Jane")
                .contains("Laptop Pro")
                .contains("1 299,90 €")
                .contains("src=\"https://cdn.example/laptop.jpg\"")
                .contains("href=\"https://shop.example/products/p1\"");
    }

    @Test
    @DisplayName("real template without photo: no <img>")
    void rendersTemplateWithoutPhoto() {
        final NotificationContext<StockAlertPayload> ctx = context(null);
        notification.sendNotification(ctx, notificationProcessor);

        final String html = templateEngine.process(ctx.getTemplatePath(), MailServiceImp.toEmailContext(ctx.getTemplateVariables()));

        assertThat(html).doesNotContain("<img").contains("Laptop Pro");
    }
}
