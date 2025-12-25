package com.novatech.cybertech.services.implementation;

import com.novatech.cybertech.dto.data.EmailDto;
import com.novatech.cybertech.services.core.MailService;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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


    @Override
    public void sendEmail(final EmailDto emailDto) {
        Context context = new Context();
        context.setVariables((Map<String, Object>) emailDto.getContext());

        String content = templateEngine.process("email/order-confirmation.html", context);

        MimeMessage message = javaMailSender.createMimeMessage();

        try {
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setTo(emailDto.getTo());
            helper.setSubject(emailDto.getSubject());
            helper.setText(content, true); // HTML content
        }
        catch (MessagingException e) {
            log.error("Erreur lors de l'envoi de l'e-mail", e);
        }

        javaMailSender.send(message);
    }
}
