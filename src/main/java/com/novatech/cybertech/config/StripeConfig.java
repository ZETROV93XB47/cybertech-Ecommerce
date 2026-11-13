package com.novatech.cybertech.config;

import com.stripe.Stripe;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

@Configuration
public class StripeConfig {

    @Value("${stripe.api.key}")
    private String stripeApiKey;

    @Value("${stripe.connect-timeout-ms:5000}")
    private int connectTimeoutMs;

    @Value("${stripe.read-timeout-ms:10000}")
    private int readTimeoutMs;

    @PostConstruct
    public void init() {
        Stripe.apiKey = stripeApiKey;
        Stripe.setConnectTimeout(connectTimeoutMs);
        Stripe.setReadTimeout(readTimeoutMs);
    }
}