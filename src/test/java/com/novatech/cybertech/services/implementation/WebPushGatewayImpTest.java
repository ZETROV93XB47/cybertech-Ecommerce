package com.novatech.cybertech.services.implementation;

import com.novatech.cybertech.config.WebPushProperties;
import com.novatech.cybertech.exceptions.NotificationDeliveryException;
import com.novatech.cybertech.exceptions.PushServiceNotConfiguredException;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpServer;
import org.bouncycastle.jce.ECNamedCurveTable;
import org.bouncycastle.jce.interfaces.ECPrivateKey;
import org.bouncycastle.jce.interfaces.ECPublicKey;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.SecureRandom;
import java.security.Security;
import java.time.Duration;
import java.util.Base64;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Exercises the real web-push library (no mock): VAPID signing and payload encryption need the
 * BouncyCastle provider, which no other test covers. A local HTTP server stands in for the push
 * service.
 */
class WebPushGatewayImpTest {

    private static final String CURVE = "prime256v1";
    private static final String PUSH_PATH = "/push/device";
    private static final Base64.Encoder BASE64_URL = Base64.getUrlEncoder().withoutPadding();

    private static String vapidPublicKey;
    private static String vapidPrivateKey;
    private static String browserP256dh;
    private static String browserAuth;

    private HttpServer server;
    private WebPushGatewayImp gateway;

    @BeforeAll
    static void generateKeys() throws Exception {
        final KeyPair vapid = generateKeyPair();
        vapidPublicKey = BASE64_URL.encodeToString(((ECPublicKey) vapid.getPublic()).getQ().getEncoded(false));
        vapidPrivateKey = BASE64_URL.encodeToString(((ECPrivateKey) vapid.getPrivate()).getD().toByteArray());
        browserP256dh = BASE64_URL.encodeToString(((ECPublicKey) generateKeyPair().getPublic()).getQ().getEncoded(false));
        final byte[] auth = new byte[16];
        new SecureRandom().nextBytes(auth);
        browserAuth = BASE64_URL.encodeToString(auth);
    }

    @AfterEach
    void tearDown() throws IOException {
        if (gateway != null) {
            gateway.destroy();
        }
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void sendsEncryptedVapidSignedRequestAndReturnsProviderStatus() throws IOException {
        final AtomicReference<Headers> received = new AtomicReference<>();
        startServer(exchange -> {
            received.set(exchange.getRequestHeaders());
            exchange.getRequestBody().readAllBytes();
            exchange.sendResponseHeaders(201, -1);
            exchange.close();
        });
        // Regression: nothing registered the "BC" provider web-push loads keys with, so every send failed.
        Security.removeProvider(BouncyCastleProvider.PROVIDER_NAME);
        gateway = new WebPushGatewayImp(properties(vapidPublicKey, Duration.ofSeconds(5)));
        assertThat(Security.getProvider(BouncyCastleProvider.PROVIDER_NAME)).isNotNull();

        final int status = gateway.send(endpoint(), browserP256dh, browserAuth, "{\"title\":\"t\"}");

        assertThat(status).isEqualTo(201);
        assertThat(received.get().getFirst("Content-Encoding")).isEqualTo("aes128gcm");
        assertThat(received.get().getFirst("Authorization")).startsWith("vapid t=").contains("k=" + vapidPublicKey);
    }

    @Test
    void providerThatNeverAnswersFailsWithinTheConfiguredTimeout() throws IOException {
        startServer(exchange -> {
            try {
                Thread.sleep(5_000);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            }
            exchange.close();
        });
        gateway = new WebPushGatewayImp(properties(vapidPublicKey, Duration.ofMillis(300)));

        final long start = System.nanoTime();
        assertThatThrownBy(() -> gateway.send(endpoint(), browserP256dh, browserAuth, "{}"))
                .isInstanceOf(NotificationDeliveryException.class);
        assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofSeconds(3));
    }

    @Test
    void invalidVapidKeyDisablesPushWithoutBreakingStartup() {
        gateway = new WebPushGatewayImp(properties("not-a-valid-key", Duration.ofSeconds(1)));

        assertThatThrownBy(() -> gateway.send("https://fcm.googleapis.com/fcm/send/x", browserP256dh, browserAuth, "{}"))
                .isInstanceOf(PushServiceNotConfiguredException.class);
    }

    @Test
    void missingVapidConfigurationIsReportedOnSend() {
        gateway = new WebPushGatewayImp(new WebPushProperties());

        assertThatThrownBy(() -> gateway.send("https://fcm.googleapis.com/fcm/send/x", browserP256dh, browserAuth, "{}"))
                .isInstanceOf(PushServiceNotConfiguredException.class);
    }

    private void startServer(final com.sun.net.httpserver.HttpHandler handler) throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext(PUSH_PATH, handler);
        server.start();
    }

    private String endpoint() {
        return "http://127.0.0.1:" + server.getAddress().getPort() + PUSH_PATH;
    }

    private static WebPushProperties properties(final String publicKey, final Duration timeout) {
        final WebPushProperties properties = new WebPushProperties();
        properties.setPublicKey(publicKey);
        properties.setPrivateKey(vapidPrivateKey);
        properties.setSubject("mailto:admin@cybertech.local");
        properties.setSendTimeout(timeout);
        return properties;
    }

    private static KeyPair generateKeyPair() throws Exception {
        final KeyPairGenerator generator = KeyPairGenerator.getInstance("ECDH", new BouncyCastleProvider());
        generator.initialize(ECNamedCurveTable.getParameterSpec(CURVE), new SecureRandom());
        return generator.generateKeyPair();
    }
}
