package com.novatech.cybertech.entities.enums;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;

class EmailTemplateTypeEnumTest {

    @Test
    void hasFourTemplates() {
        assertThat(EmailTemplateType.values()).containsExactly(
                EmailTemplateType.ORDER_CANCELLATION,
                EmailTemplateType.ORDER_PENDING_PAYMENT,
                EmailTemplateType.ORDER_CONFIRMATION,
                EmailTemplateType.SHIPPING_CONFIRMATION);
    }

    @ParameterizedTest
    @EnumSource(EmailTemplateType.class)
    void everyTemplatePathStartsWithEmailDirectory(final EmailTemplateType template) {
        assertThat(template.getTemplatePath()).startsWith("email/");
    }

    @ParameterizedTest
    @EnumSource(EmailTemplateType.class)
    void everyTemplateBindsANotificationSubject(final EmailTemplateType template) {
        assertThat(template.getNotificationSubject()).isNotNull();
    }

    @Test
    void canonicalSubjectMapping() {
        assertThat(EmailTemplateType.ORDER_CANCELLATION.getNotificationSubject())
                .isEqualTo(NotificationSubject.ORDER_CANCELLATION);
        assertThat(EmailTemplateType.ORDER_CANCELLATION.getTemplatePath())
                .isEqualTo("email/order-cancelled");

        assertThat(EmailTemplateType.ORDER_PENDING_PAYMENT.getNotificationSubject())
                .isEqualTo(NotificationSubject.ORDER_PENDING_PAYMENT);
        assertThat(EmailTemplateType.ORDER_PENDING_PAYMENT.getTemplatePath())
                .isEqualTo("email/order-pending-payment");

        assertThat(EmailTemplateType.ORDER_CONFIRMATION.getNotificationSubject())
                .isEqualTo(NotificationSubject.ORDER_CONFIRMATION);
        assertThat(EmailTemplateType.ORDER_CONFIRMATION.getTemplatePath())
                .isEqualTo("email/order-confirmation");

        assertThat(EmailTemplateType.SHIPPING_CONFIRMATION.getNotificationSubject())
                .isEqualTo(NotificationSubject.SHIPPING_CONFIRMATION);
        assertThat(EmailTemplateType.SHIPPING_CONFIRMATION.getTemplatePath())
                .isEqualTo("email/shipping-confirmation");
    }
}
