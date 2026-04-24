package com.novatech.cybertech.services.implementation.support;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.novatech.cybertech.dto.data.EmailDto;
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
import org.springframework.mail.javamail.JavaMailSender;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;

import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link MailServiceImp}.
 *
 * FIX-2507: {@code MessagingException} from {@link org.springframework.mail.javamail.MimeMessageHelper}
 * now causes an early {@code return} so {@code javaMailSender.send()} is never called with a broken message.
 *
 * FIX-2508: PII is no longer logged at INFO — recipient address is logged at DEBUG only.
 *
 * Pins {@code BUG-2511}: no NotificationEntity is persisted with SENT/FAILED — the service has no
 * notification-repository field and no dedup lookup.
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
                .context(ctx)
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
    @DisplayName("FIX-2507: MessagingException from MimeMessageHelper is caught; send() is NOT called")
    void doesNotCallSendAfterMessagingException() {
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

        // Exception is still swallowed (no propagation), but send() must NOT be called after the fix.
        assertThatCode(() -> service.sendEmail(sampleEmail())).doesNotThrowAnyException();

        // FIX-2507: send() must NOT be invoked after a MessagingException during helper setup.
        verify(javaMailSender, never()).send(broken);
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
    @DisplayName("BUG-2511: MailServiceImp has no notification repository — no SENT/FAILED tracking, no dedup")
    void hasNoNotificationRepositoryDocumentingBug2511() {
        // Reflective sanity check — only mailer + template engine + frontendUrl (ignoring the slf4j logger).
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
}
