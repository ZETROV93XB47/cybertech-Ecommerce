package com.novatech.cybertech.services.core;

public interface WebPushGateway {

    int send(String endpoint, String p256dh, String auth, String payload);
}
