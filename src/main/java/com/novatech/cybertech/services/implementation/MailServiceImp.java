package com.novatech.cybertech.services.implementation;

import com.novatech.cybertech.dto.data.EmailDto;
import com.novatech.cybertech.exceptions.NotificationDeliveryException;
import com.novatech.cybertech.services.core.MailService;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;

import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class MailServiceImp implements MailService {

    private final JavaMailSender javaMailSender;
    private final SpringTemplateEngine templateEngine;

    @Value("${application.frontend.url:http://localhost:4200}")
    private String frontendUrl;

    /**
     * <p><b>Phase 1 contract change:</b> all delivery failures (template / MIME
     * setup via {@link MessagingException}, SMTP transport via
     * {@link MailException}) surface as
     * {@link NotificationDeliveryException} so the upstream retry policy —
     * currently the hand-rolled loop in
     * {@link com.novatech.cybertech.listener.NotificationListener}, replaced in
     * Phase 2 by Resilience4j {@code @Retry} — can act on them. Previously a
     * caught {@link MessagingException} returned silently (BUG-2507) and the
     * listener wrote {@code status=SENT} for an e-mail that never went out.
     */
    @Override
    public void sendEmail(final EmailDto emailDto) {
        final Context context = toEmailContext(emailDto.getTemplateVariables());

        log.debug("Sending email to={}", emailDto.getTo());

        final String content = templateEngine.process(emailDto.getTemplatePath(), context);

        final MimeMessage message = javaMailSender.createMimeMessage();

        try {
            final MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setTo(emailDto.getTo());
            helper.setSubject(emailDto.getSubject());
            helper.setText(content, true); // HTML content
        } catch (MessagingException e) {
            // FIX-2507 / Phase 1: do NOT swallow. Bubble up so the retry layer
            // can observe the failure, persist a FAILED audit row, and decide
            // whether to retry. send() is intentionally NOT invoked on a
            // half-built MimeMessage.
            log.error("MIME setup failed for recipient={}", emailDto.getTo(), e);
            throw new NotificationDeliveryException(
                    "Failed to assemble MIME message for " + emailDto.getTo(), e);
        }

        try {
            javaMailSender.send(message);
        } catch (MailException e) {
            // SMTP transport failure (connection refused, timeout, auth error,
            // ...). Spring's MailException hierarchy is unchecked but we
            // normalize it to the project's domain exception so the retry
            // allowlist in Phase 2 can target a single type.
            log.error("SMTP send failed for recipient={}", emailDto.getTo(), e);
            throw new NotificationDeliveryException(
                    "Failed to send e-mail to " + emailDto.getTo(), e);
        }
    }

    public static Context toEmailContext(final Map<String, Object> data) {
        final Context context = new Context();
        context.setVariables(data);

        return context;
    }
}
