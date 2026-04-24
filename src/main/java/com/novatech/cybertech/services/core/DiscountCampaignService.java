package com.novatech.cybertech.services.core;

import com.novatech.cybertech.dto.data.DiscountContext;
import com.novatech.cybertech.entities.enums.DiscountType;

public interface DiscountCampaignService {

    DiscountContext getActiveDiscountContext(DiscountType discountType);
}
