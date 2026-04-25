package com.novatech.cybertech.services.core;

import com.novatech.cybertech.dto.request.admin.DiscountCampaignUpdateRequestDto;
import com.novatech.cybertech.dto.response.admin.DiscountCampaignResponseDto;
import com.novatech.cybertech.entities.enums.DiscountType;

import java.util.List;

/**
 * Admin-only management surface for the {@code discount_campaign} table.
 * Reads are unfiltered (admin sees everything, including disabled campaigns).
 * The update path PATCHes a single campaign by {@link DiscountType} and evicts the
 * runtime cache so price calculations pick up the new config on the next call.
 */
public interface DiscountCampaignAdminService {

    List<DiscountCampaignResponseDto> getAll();

    DiscountCampaignResponseDto getByDiscountType(DiscountType discountType);

    DiscountCampaignResponseDto update(DiscountType discountType, DiscountCampaignUpdateRequestDto request);
}
