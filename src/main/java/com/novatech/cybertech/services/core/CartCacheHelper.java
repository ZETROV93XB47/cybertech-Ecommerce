package com.novatech.cybertech.services.core;

import com.novatech.cybertech.dto.response.cart.CartResponseDto;

public interface CartCacheHelper {
    String acquireLock(String userId);
    void releaseLock(String userId, String token);
    void refreshTtlWithJitter(String userId);
    void putWithJitter(String userId, CartResponseDto cart);
    CartResponseDto getRaw(String userId);
}
