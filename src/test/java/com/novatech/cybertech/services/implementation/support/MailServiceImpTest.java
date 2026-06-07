package com.novatech.cybertech.services.implementation.support;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.novatech.cybertech.dto.data.EmailDto;
import com.novatech.cybertech.exceptions.NotificationDeliveryException;
import com.novatech.cybertech.services.implementation.MailServiceImp;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;

import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link MailServiceImp}.
 *
 * <p>Phase 1 hardening — the previous swallow-and-return path on
 * {@link jakarta.mail.MessagingException} (BUG-2507) is replaced by a rethrow
 * as {@link NotificationDeliveryException}; SMTP-level
 * {@link org.springframework.mail.MailException} failures are normalized to
 * the same domain exception so the upstream retry policy can target a single
 * type. The previous BUG-2511 pin (no notification-repository field) is
 * obsolete: persistence is now delegated to
 * {@link com.novatech.cybertech.services.implementation.NotificationOutcomeRecorder}
 * called from the listeners, not from this service.
 *
 * <p>FIX-2508 still applies: PII (recipient address) is logged at DEBUG only.
 */
@ExtendWith(MockitoExtension.class)
class MailServiceImpTest {

    @Mock
    private JavaMailSender javaMailSender;

    @Mock
    private SpringTemplateEngine templateEngine;

    @InjectMocks
    private MailServiceImp service;

    private MimeMessage mimeMessage;

    @BeforeEach
    void setUp() {
        // A real (not mocked) MimeMessage so MimeMessageHelper can actually call setTo / setSubject.
        Session session = Session.getInstance(new Properties());
        mimeMessage = new MimeMessage(session);
    }

    private EmailDto sampleEmail() {
        Map<String, Object> ctx = new HashMap<>();
        ctx.put("userName", "Jane Doe");
        ctx.put("orderTotal", 199.99);
        return EmailDto.builder()
                .from("noreply@cybertech.io")
                .to("user@example.com")
                .subject("Order confirmation")
                .templatePath("email/order-confirmation")
                .templateVariables(ctx)
                .build();
    }

    @Test
    @DisplayName("happy: renders template via SpringTemplateEngine and forwards rendered HTML to JavaMailSender")
    void shouldSendEmailWithRenderedTemplate() throws Exception {
        EmailDto dto = sampleEmail();
        when(javaMailSender.createMimeMessage()).thenReturn(mimeMessage);
        when(templateEngine.process(eq("email/order-confirmation"), any(Context.class)))
                .thenReturn("<html>rendered body</html>");

        service.sendEmail(dto);

        // Order: render template -> create message -> send
        InOrder order = inOrder(templateEngine, javaMailSender);
        order.verify(templateEngine).process(eq("email/order-confirmation"), any(Context.class));
        order.verify(javaMailSender).createMimeMessage();
        order.verify(javaMailSender).send(mimeMessage);

        // The MimeMessage was actually configured by MimeMessageHelper.
        assertThat(mimeMessage.getAllRecipients()).isNotNull();
        assertThat(mimeMessage.getSubject()).isEqualTo("Order confirmation");
    }

    @Test
    @DisplayName("Context is built from emailDto.context map and passed to the template engine")
    void shouldBuildContextFromEmailDtoMap() {
        EmailDto dto = sampleEmail();
        when(javaMailSender.createMimeMessage()).thenReturn(mimeMessage);
        when(templateEngine.process(any(String.class), any(Context.class))).thenReturn("rendered");

        service.sendEmail(dto);

        ArgumentCaptor<Context> captor = ArgumentCaptor.forClass(Context.class);
        verify(templateEngine).process(eq("email/order-confirmation"), captor.capture());
        Context capturedContext = captor.getValue();

        assertThat(capturedContext.getVariable("userName")).isEqualTo("Jane Doe");
        assertThat(capturedContext.getVariable("orderTotal")).isEqualTo(199.99);
    }

    @Test
    @DisplayName("Phase 1: MessagingException from MimeMessageHelper is rethrown as NotificationDeliveryException; send() is NOT called")
    void mimeFailureIsRethrownAsNotificationDeliveryException() {
        // MimeMessageHelper internally calls the 2-arg setSubject(String, String charset) variant,
        // so both overloads must throw to trigger the MessagingException path.
        MimeMessage broken = new MimeMessage((Session) null) {
            @Override
            public void setSubject(String subject) throws jakarta.mail.MessagingException {
                throw new jakarta.mail.MessagingException("boom");
            }
            @Override
            public void setSubject(String subject, String charset) throws jakarta.mail.MessagingException {
                throw new jakarta.mail.MessagingException("boom");
            }
        };
        when(javaMailSender.createMimeMessage()).thenReturn(broken);
        when(templateEngine.process(any(String.class), any(Context.class))).thenReturn("rendered");

        assertThatThrownBy(() -> service.sendEmail(sampleEmail()))
                .isInstanceOf(NotificationDeliveryException.class)
                .hasMessageContaining("user@example.com")
                .hasCauseInstanceOf(jakarta.mail.MessagingException.class);

        // send() must NOT be invoked on a half-built MimeMessage.
        verify(javaMailSender, never()).send(broken);
    }

    @Test
    @DisplayName("Phase 1: SMTP-level MailException from JavaMailSender.send() is rethrown as NotificationDeliveryException")
    void smtpFailureIsRethrownAsNotificationDeliveryException() {
        when(javaMailSender.createMimeMessage()).thenReturn(mimeMessage);
        when(templateEngine.process(any(String.class), any(Context.class))).thenReturn("rendered");
        // Simulate SMTP-side failure (connection refused, timeout, ...).
        doThrow(new MailSendException("connection refused")).when(javaMailSender).send(mimeMessage);

        assertThatThrownBy(() -> service.sendEmail(sampleEmail()))
                .isInstanceOf(NotificationDeliveryException.class)
                .hasMessageContaining("user@example.com")
                .hasCauseInstanceOf(MailSendException.class);
    }

    @Test
    @DisplayName("FIX BUG-2508: recipient is logged at DEBUG only — no INFO log, no PII leaked")
    void doesNotLogPiiAtInfo() {
        final Logger logger = (Logger) LoggerFactory.getLogger(MailServiceImp.class);
        final Level originalLevel = logger.getLevel();
        final ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.setLevel(Level.DEBUG);
        logger.addAppender(appender);
        try {
            when(javaMailSender.createMimeMessage()).thenReturn(mimeMessage);
            when(templateEngine.process(any(String.class), any())).thenReturn("<html>body</html>");

            service.sendEmail(sampleEmail());
        } finally {
            logger.detachAppender(appender);
            logger.setLevel(originalLevel);
        }

        assertThat(appender.list)
                .noneMatch(e -> e.getLevel() == Level.INFO);
        assertThat(appender.list)
                .anyMatch(e -> e.getLevel() == Level.DEBUG
                        && e.getFormattedMessage().contains("user@example.com"));
    }

    @Test
    @DisplayName("Phase 1: MailServiceImp deliberately holds no notification repository — persistence lives in NotificationOutcomeRecorder (centralisation)")
    void hasNoNotificationRepository() {
        // Reflective sanity check — only mailer + template engine + frontendUrl.
        // Persistence has been moved out of this service into
        // NotificationOutcomeRecorder, which is invoked from the listeners.
        // Replaces the BUG-2511 pin (which asserted the same shape but framed
        // it as a defect — the centralisation choice is now intentional).
        assertThat(java.util.Arrays.stream(service.getClass().getDeclaredFields())
                        .filter(f -> !java.lang.reflect.Modifier.isStatic(f.getModifiers()))
                        .map(java.lang.reflect.Field::getName)
                        .toList())
                .containsExactlyInAnyOrder("javaMailSender", "templateEngine", "frontendUrl");
    }

    @Test
    @DisplayName("toEmailContext exposes the variables it was built from (utility round trip)")
    void toEmailContextExposesVariablesRoundTrip() {
        Map<String, Object> data = new HashMap<>();
        data.put("a", "1");
        data.put("b", 2);

        Context context = MailServiceImp.toEmailContext(data);

        assertThat(context.getVariable("a")).isEqualTo("1");
        assertThat(context.getVariable("b")).isEqualTo(2);
    }

    @Test
    @DisplayName("happy path does not throw")
    void happyPathDoesNotThrow() {
        when(javaMailSender.createMimeMessage()).thenReturn(mimeMessage);
        when(templateEngine.process(any(String.class), any(Context.class))).thenReturn("rendered");

        assertThatCode(() -> service.sendEmail(sampleEmail())).doesNotThrowAnyException();
    }
}
