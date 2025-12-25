package com.novatech.cybertech.services.implementation;

import com.novatech.cybertech.annotation.CommunicationTypeHandler;
import com.novatech.cybertech.dto.data.NotificationContext;
import com.novatech.cybertech.entities.enums.CommunicationChanel;
import com.novatech.cybertech.services.core.NotificationProcessor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@RequiredArgsConstructor
@Component("smsNotificationProcessor")
@CommunicationTypeHandler(CommunicationChanel.SMS)
public class SmsNotificationProcessor implements NotificationProcessor {
    @Override
    public void sendMessage(NotificationContext message) {
        log.info("SMS SENT");
    }
}