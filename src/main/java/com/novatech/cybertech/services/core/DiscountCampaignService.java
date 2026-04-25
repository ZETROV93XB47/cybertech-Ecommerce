package com.novatech.cybertech.services.core;

import com.novatech.cybertech.dto.data.DiscountContext;
import com.novatech.cybertech.entities.enums.DiscountType;

import java.util.List;

public interface DiscountCampaignService {

    DiscountContext getActiveDiscountContext(DiscountType discountType);

    /**
     * Public-facing list of campaigns the customer can pick at checkout. Returns only
     * campaigns that are {@code enabled=true} AND inside their {@code [startsAt, endsAt]}
     * window at the moment of the call. Used to populate the discount selector in the UI.
     */
    List<DiscountContext> getAllActiveCampaigns();
}
