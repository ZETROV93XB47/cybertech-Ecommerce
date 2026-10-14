package com.novatech.cybertech.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "cybertech.web-push")
public class WebPushProperties {

    private String publicKey;
    private String privateKey;
    private String subject;
    private List<String> allowedEndpointHosts = List.of();

    public boolean isConfigured() {
        return publicKey != null && !publicKey.isBlank()
                && privateKey != null && !privateKey.isBlank()
                && subject != null && !subject.isBlank();
    }
}
