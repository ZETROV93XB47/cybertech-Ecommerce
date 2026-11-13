package com.novatech.cybertech.services.core;

import com.novatech.cybertech.dto.request.admin.DiscountCampaignCreateRequestDto;
import com.novatech.cybertech.dto.request.admin.DiscountCampaignUpdateRequestDto;
import com.novatech.cybertech.dto.response.admin.DiscountCampaignResponseDto;

import java.util.List;

/**
 * Admin-only management surface for the {@code discount_campaign} table.
 * Reads are unfiltered (admin sees everything, including disabled campaigns).
 * {@code create}/{@code update}/{@code delete} all evict the runtime cache so price
 * calculations pick up the new config on the next call. {@code discountKey} is free-form —
 * registering a brand-new campaign needs no code change or redeploy, only
 * {@code calculationType} (the underlying algorithm) is still fixed in code.
 */
public interface DiscountCampaignAdminService {

    List<DiscountCampaignResponseDto> getAll();

    DiscountCampaignResponseDto getByDiscountKey(String discountKey);

    DiscountCampaignResponseDto create(DiscountCampaignCreateRequestDto request);

    DiscountCampaignResponseDto update(String discountKey, DiscountCampaignUpdateRequestDto request);

    void deleteByDiscountKey(String discountKey);
}
