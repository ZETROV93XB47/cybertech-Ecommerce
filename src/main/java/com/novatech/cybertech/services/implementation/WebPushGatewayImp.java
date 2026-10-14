package com.novatech.cybertech.services.implementation;

import com.novatech.cybertech.config.WebPushProperties;
import com.novatech.cybertech.exceptions.NotificationDeliveryException;
import com.novatech.cybertech.exceptions.PushServiceNotConfiguredException;
import com.novatech.cybertech.services.core.WebPushGateway;
import lombok.RequiredArgsConstructor;
import org.apache.http.HttpResponse;
import org.springframework.stereotype.Component;
import nl.martijndwars.webpush.Notification;
import nl.martijndwars.webpush.PushService;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.concurrent.ExecutionException;
import org.jose4j.lang.JoseException;

@Component
@RequiredArgsConstructor
public class WebPushGatewayImp implements WebPushGateway {

    private final WebPushProperties webPushProperties;

    @Override
    public int send(final String endpoint, final String p256dh, final String auth, final String payload) {
        if (!webPushProperties.isConfigured()) {
            throw new PushServiceNotConfiguredException();
        }
        try {
            final PushService pushService = new PushService(
                    webPushProperties.getPublicKey(),
                    webPushProperties.getPrivateKey(),
                    webPushProperties.getSubject());
            final HttpResponse response = pushService.send(new Notification(endpoint, p256dh, auth, payload));
            return response.getStatusLine().getStatusCode();
        } catch (IOException | GeneralSecurityException | JoseException | ExecutionException exception) {
            throw new NotificationDeliveryException("Web Push delivery failed", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new NotificationDeliveryException("Web Push delivery was interrupted", exception);
        }
    }
}
