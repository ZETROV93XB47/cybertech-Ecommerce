package com.novatech.cybertech.api.controllers.implementation;

import com.novatech.cybertech.api.controllers.spec.DiscountControllerApiSpec;
import com.novatech.cybertech.dto.data.DiscountContext;
import com.novatech.cybertech.services.core.DiscountCampaignService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import static com.novatech.cybertech.constants.CyberTechAppConstants.APP_API_VERSION;
import static com.novatech.cybertech.constants.CyberTechAppConstants.DISCOUNT_PUBLIC_CONTROLLER_BASE_PATH;
import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

/**
 * Public read-only view of currently-active discount campaigns. Used by the storefront
 * (home page banner, checkout discount selector) to populate the choices customers can pick.
 *
 * <p>Whitelisted in {@link com.novatech.cybertech.config.SecurityConfig#PUBLIC_URLS} via
 * {@code /api/v1/services/discounts/**} — anonymous browsing of promos is desired marketing
 * behaviour. The admin-side mutation surface lives at
 * {@code /api/v1/services/admin/discounts} and is ROLE_ADMIN.
 *
 * <p>OpenAPI documentation lives on {@link DiscountControllerApiSpec}.
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping(version = APP_API_VERSION, value = DISCOUNT_PUBLIC_CONTROLLER_BASE_PATH)
public class DiscountController implements DiscountControllerApiSpec {

    private final DiscountCampaignService discountCampaignService;

    /**
     * Returns every campaign that is {@code enabled=true} AND inside its
     * {@code [startsAt, endsAt]} window at request time. The frontend uses this to populate
     * the discount picker on the checkout screen.
     */
    @Override
    @GetMapping(value = "/active", produces = APPLICATION_JSON_VALUE)
    public ResponseEntity<List<DiscountContext>> getActiveCampaigns() {
        return ResponseEntity.ok(discountCampaignService.getAllActiveCampaigns());
    }
}
