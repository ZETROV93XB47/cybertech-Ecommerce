package com.novatech.cybertech.services.implementation;

import com.novatech.cybertech.config.WebPushProperties;
import com.novatech.cybertech.exceptions.NotificationDeliveryException;
import com.novatech.cybertech.exceptions.PushServiceNotConfiguredException;
import com.novatech.cybertech.services.core.WebPushGateway;
import lombok.extern.slf4j.Slf4j;
import nl.martijndwars.webpush.Encoding;
import nl.martijndwars.webpush.Notification;
import nl.martijndwars.webpush.PushService;
import org.apache.http.HttpResponse;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.impl.nio.client.CloseableHttpAsyncClient;
import org.apache.http.impl.nio.client.HttpAsyncClients;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.jose4j.lang.JoseException;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.Security;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Sends one encrypted Web Push message and returns the push service's HTTP status.
 *
 * <p>The VAPID {@link PushService} is built once at startup: it only holds the key pair and the
 * subject, and {@code preparePost} is stateless. The request it prepares is executed through a
 * single shared async HTTP client bounded by {@code cybertech.web-push.send-timeout}, so a push
 * service that never answers cannot pin the notification executor thread.</p>
 *
 * <p>web-push loads keys through the BouncyCastle JCA provider ({@code "BC"}); without it
 * registered every key load fails with {@code NoSuchProviderException}.</p>
 */
@Slf4j
@Component
public class WebPushGatewayImp implements WebPushGateway, DisposableBean {

    private static final String DELIVERY_FAILED = "Web Push delivery failed";
    private static final String DELIVERY_TIMED_OUT = "Web Push delivery timed out after ";
    private static final String DELIVERY_INTERRUPTED = "Web Push delivery was interrupted";

    private final WebPushProperties webPushProperties;
    private final PushService pushService;
    private final CloseableHttpAsyncClient httpClient;

    public WebPushGatewayImp(final WebPushProperties webPushProperties) {
        this.webPushProperties = webPushProperties;
        registerBouncyCastle();
        this.pushService = buildPushService(webPushProperties);
        final int timeoutMillis = Math.toIntExact(webPushProperties.getSendTimeout().toMillis());
        this.httpClient = HttpAsyncClients.custom()
                .setDefaultRequestConfig(RequestConfig.custom()
                        .setConnectTimeout(timeoutMillis)
                        .setConnectionRequestTimeout(timeoutMillis)
                        .setSocketTimeout(timeoutMillis)
                        .build())
                .build();
        this.httpClient.start();
    }

    @Override
    public int send(final String endpoint, final String p256dh, final String auth, final String payload) {
        if (pushService == null) {
            throw new PushServiceNotConfiguredException();
        }
        final Future<HttpResponse> response;
        try {
            response = httpClient.execute(
                    pushService.preparePost(new Notification(endpoint, p256dh, auth, payload), Encoding.AES128GCM),
                    null);
        } catch (IOException | GeneralSecurityException | JoseException exception) {
            throw new NotificationDeliveryException(DELIVERY_FAILED, exception);
        }
        try {
            return response.get(webPushProperties.getSendTimeout().toMillis(), TimeUnit.MILLISECONDS)
                    .getStatusLine()
                    .getStatusCode();
        } catch (TimeoutException exception) {
            response.cancel(true);
            throw new NotificationDeliveryException(DELIVERY_TIMED_OUT + webPushProperties.getSendTimeout(), exception);
        } catch (ExecutionException exception) {
            throw new NotificationDeliveryException(DELIVERY_FAILED, exception.getCause());
        } catch (InterruptedException exception) {
            response.cancel(true);
            Thread.currentThread().interrupt();
            throw new NotificationDeliveryException(DELIVERY_INTERRUPTED, exception);
        }
    }

    @Override
    public void destroy() throws IOException {
        httpClient.close();
    }

    private static void registerBouncyCastle() {
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
    }

    private static PushService buildPushService(final WebPushProperties properties) {
        if (!properties.isConfigured()) {
            return null;
        }
        try {
            return new PushService(properties.getPublicKey(), properties.getPrivateKey(), properties.getSubject());
        } catch (GeneralSecurityException | IllegalArgumentException exception) {
            // A bad VAPID key disables Web Push only; e-mail/SMS notifications must keep working.
            log.error("Invalid VAPID key pair, Web Push disabled", exception);
            return null;
        }
    }
}
