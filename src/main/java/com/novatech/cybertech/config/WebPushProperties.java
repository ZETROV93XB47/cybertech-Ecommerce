package com.novatech.cybertech.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "cybertech.web-push")
public class WebPushProperties {

    /** Placeholder replaced by the order UUID in {@link #orderDetailsUrlTemplate}. */
    public static final String ORDER_UUID_PLACEHOLDER = "{orderUuid}";

    private String publicKey;
    private String privateKey;
    private String subject;
    private List<String> allowedEndpointHosts = List.of();

    /** Maximum time to wait for the push service to answer one delivery. */
    private Duration sendTimeout = Duration.ofSeconds(10);

    /** Registering one more browser beyond this limit replaces the user's oldest subscription. */
    private int maxSubscriptionsPerUser = 5;

    /** Frontend page opened when the user clicks the notification. */
    private String orderDetailsUrlTemplate = "/account/orders/" + ORDER_UUID_PLACEHOLDER;

    public boolean isConfigured() {
        return publicKey != null && !publicKey.isBlank()
                && privateKey != null && !privateKey.isBlank()
                && subject != null && !subject.isBlank();
    }
}
