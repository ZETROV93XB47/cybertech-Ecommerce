package com.novatech.cybertech.services.implementation;

import com.novatech.cybertech.annotation.CommunicationTypeHandler;
import com.novatech.cybertech.dto.data.EmailDto;
import com.novatech.cybertech.dto.data.NotificationContext;
import com.novatech.cybertech.entities.enums.CommunicationChanel;
import com.novatech.cybertech.services.core.MailService;
import com.novatech.cybertech.services.core.NotificationProcessor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@RequiredArgsConstructor
@Component("emailNotificationProcessor")
@CommunicationTypeHandler(CommunicationChanel.EMAIL)
public class EmailNotificationProcessor implements NotificationProcessor {

    private static final String ORDER_CONFIRMATION_DATA_KEY = "OrderConfirmationData";
    private final MailService mailService;

    @Override
    public void sendMessage(final NotificationContext notificationContext) {

        final EmailDto emailDto = EmailDto.builder()
                .from("abc@mail.com")
                .to(notificationContext.getUser().getEmail())
                .subject(notificationContext.getSubject())
                .context(notificationContext.getData())
                .templatePath(notificationContext.getTemplatePath())
                .build();

        mailService.sendEmail(emailDto);
    }
}
