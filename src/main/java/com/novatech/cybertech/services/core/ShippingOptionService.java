package com.novatech.cybertech.services.core;

import com.novatech.cybertech.dto.response.shipping.ShippingOptionResponseDto;
import com.novatech.cybertech.entities.enums.ShippingProvider;

import java.util.List;

/**
 * Read-only catalogue of the shipping options an order can be placed with, priced by the same
 * code path as the order itself so a client never has to copy the tariffs.
 */
public interface ShippingOptionService {

    /**
     * Every available (provider, type) pair with its cost, sorted by provider then by cost.
     *
     * @param provider restricts the list to one carrier; {@code null} for all of them.
     * @return the options; empty when the requested provider has no shipping service wired.
     */
    List<ShippingOptionResponseDto> getShippingOptions(ShippingProvider provider);
}
