package com.novatech.cybertech.services.implementation.support;

import com.novatech.cybertech.dto.data.RecommendedProductDto;
import com.novatech.cybertech.entities.enums.EmailTemplateType;
import com.novatech.cybertech.services.implementation.MailServiceImp;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Renders the real {@code templates/email/recommendation-digest.html} with a real Thymeleaf
 * engine: the handler tests only check the variables, not that the template reads them.
 */
class RecommendationDigestTemplateTest {

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

    @Test
    @DisplayName("shows the first name, each product (name, French price, image, link) and the unsubscribe link")
    void rendersProductsAndUnsubscribeLink() {
        final List<RecommendedProductDto> products = List.of(
                RecommendedProductDto.builder().name("Laptop Pro").price(new BigDecimal("1299.9"))
                        .imageUrl("https://cdn.example/laptop.jpg").productUrl("https://shop.example/products/p1").build(),
                RecommendedProductDto.builder().name("Mouse").price(new BigDecimal("19.00"))
                        .imageUrl(null).productUrl("https://shop.example/products/p2").build());

        final String html = templateEngine.process(EmailTemplateType.RECOMMENDATION_DIGEST.getTemplatePath(),
                MailServiceImp.toEmailContext(Map.of(
                        "userName", "Jane",
                        "products", products,
                        "unsubscribeUrl", "https://shop.example/unsubscribe/recommendations?token=abc")));

        assertThat(html)
                .contains("Jane")
                .contains("Laptop Pro").contains("1 299,90 €")
                .contains("Mouse").contains("19,00 €")
                .contains("src=\"https://cdn.example/laptop.jpg\"")
                .contains("href=\"https://shop.example/products/p1\"")
                .contains("href=\"https://shop.example/products/p2\"")
                .contains("href=\"https://shop.example/unsubscribe/recommendations?token=abc\"");
        // The product without a photo has no <img>: only one image in the e-mail.
        assertThat(html.split("<img", -1)).hasSize(2);
    }
}
