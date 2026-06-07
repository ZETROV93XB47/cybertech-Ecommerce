package com.novatech.cybertech.services.implementation.support;

import com.novatech.cybertech.dto.data.EmailDto;
import com.novatech.cybertech.dto.data.NotificationContext;
import com.novatech.cybertech.dto.data.UserContactDto;
import com.novatech.cybertech.entities.enums.CommunicationChanel;
import com.novatech.cybertech.services.core.MailService;
import com.novatech.cybertech.services.implementation.EmailNotificationProcessor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;

/**
 * Unit tests for {@link EmailNotificationProcessor}.
 *
 * Pins {@code BUG-2509}: hardcoded {@code from="abc@mail.com"} is shipped to production.
 * Pins {@code BUG-2511}: no {@code NotificationEntity} is persisted (no dedup repository call).
 */
@ExtendWith(MockitoExtension.class)
class EmailNotificationProcessorTest {

    @Mock
    private MailService mailService;

    @InjectMocks
    private EmailNotificationProcessor processor;

    private NotificationContext aContext(final String email, final String subject, final String templatePath) {
        Map<String, Object> vars = new HashMap<>();
        vars.put("orderId", "abc-123");
        return NotificationContext.builder()
                .user(UserContactDto.builder()
                        .name("Jane")
                        .email(email)
                        .phoneNumber("+33600000000")
                        .defaultCommunicationChanel(CommunicationChanel.EMAIL)
                        .build())
                .subject(subject)
                .templatePath(templatePath)
                .templateVariables(vars)
                .build();
    }

    @Test
    @DisplayName("BUG-2509: hardcoded from='abc@mail.com' is shipped to delegate (pin)")
    void shouldDelegateToMailServiceWithBuiltEmailDto() {
        NotificationContext ctx = aContext("user@example.com", "Order confirmation", "email/order-confirmation");

        processor.sendMessage(ctx);

        ArgumentCaptor<EmailDto> emailCaptor = ArgumentCaptor.forClass(EmailDto.class);
        verify(mailService).sendEmail(emailCaptor.capture());

        EmailDto sent = emailCaptor.getValue();
        assertThat(sent.getFrom())
                .as("BUG-2509: hardcoded sender")
                .isEqualTo("abc@mail.com");
        assertThat(sent.getTo()).isEqualTo("user@example.com");
        assertThat(sent.getSubject()).isEqualTo("Order confirmation");
        assertThat(sent.getTemplatePath()).isEqualTo("email/order-confirmation");
        assertThat(sent.getTemplateVariables()).containsEntry("orderId", "abc-123");
    }

    @Test
    @DisplayName("forwards templateVariables map by reference into EmailDto")
    void templateVariablesMapIsForwardedToEmailDto() {
        Map<String, Object> vars = new HashMap<>();
        vars.put("k1", "v1");
        vars.put("k2", 42);
        NotificationContext ctx = NotificationContext.builder()
                .user(UserContactDto.builder().email("x@y.z").build())
                .subject("subj")
                .templatePath("tpl")
                .templateVariables(vars)
                .build();

        processor.sendMessage(ctx);

        ArgumentCaptor<EmailDto> captor = ArgumentCaptor.forClass(EmailDto.class);
        verify(mailService).sendEmail(captor.capture());
        assertThat(captor.getValue().getTemplateVariables()).isSameAs(vars);
    }

    @Test
    @DisplayName("BUG-2511: processor never touches a NotificationEntity repository (no dedup, no SENT/FAILED tracking)")
    void doesNotPersistNotificationEntity_documentsBug2511() {
        // The processor field set is just `mailService`. No repo dependency exists.
        NotificationContext ctx = aContext("u@e.com", "s", "t");
        processor.sendMessage(ctx);

        // Only mailService is used; no other interactions exist (zero-arg classes mock would assert this).
        verify(mailService).sendEmail(any(EmailDto.class));
        // The absence of a repository field on the processor *is* the bug; documented here.
        assertThat(java.util.Arrays.stream(processor.getClass().getDeclaredFields())
                        .filter(f -> !java.lang.reflect.Modifier.isStatic(f.getModifiers()))
                        .map(java.lang.reflect.Field::getName)
                        .toList())
                .as("BUG-2511: no notification repository to dedup or track status")
                .containsExactly("mailService");
    }
}
