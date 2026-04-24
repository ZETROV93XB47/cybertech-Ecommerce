package com.novatech.cybertech.services.implementation;

import com.novatech.cybertech.dto.data.EmailDto;
import com.novatech.cybertech.services.core.MailService;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
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

    @Override
    public void sendEmail(final EmailDto emailDto) {
        final Context context = toEmailContext(emailDto.getContext());

        log.debug("Sending email to={}", emailDto.getTo());

        final String content = templateEngine.process(emailDto.getTemplatePath(), context);

        final MimeMessage message = javaMailSender.createMimeMessage();

        try {
            final MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setTo(emailDto.getTo());
            helper.setSubject(emailDto.getSubject());
            helper.setText(content, true); // HTML content
        } catch (MessagingException e) {
            log.error("Erreur lors de l'envoi de l'e-mail", e);
            return;   // BUG-2507: was missing — still called send() on broken message
        }

        javaMailSender.send(message);
    }

    public static Context toEmailContext(final Map<String, Object> data) {
        final Context context = new Context();
        context.setVariables(data);

        return context;
    }
}
