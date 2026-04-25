package com.novatech.cybertech.api.controllers.implementation;

import com.novatech.cybertech.api.controllers.spec.DiscountAdminControllerApiSpec;
import com.novatech.cybertech.dto.request.admin.DiscountCampaignUpdateRequestDto;
import com.novatech.cybertech.dto.response.admin.DiscountCampaignResponseDto;
import com.novatech.cybertech.entities.enums.DiscountType;
import com.novatech.cybertech.services.core.DiscountCampaignAdminService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import static com.novatech.cybertech.constants.CyberTechAppConstants.APP_API_VERSION;
import static com.novatech.cybertech.constants.CyberTechAppConstants.DISCOUNT_ADMIN_CONTROLLER_BASE_PATH;
import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

/**
 * Admin-only back-office for discount campaigns. PATCH writes flush the runtime cache,
 * so the next price calculation picks up the new state without redeploy.
 *
 * <p>OpenAPI documentation lives on {@link DiscountAdminControllerApiSpec}.
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping(version = APP_API_VERSION, value = DISCOUNT_ADMIN_CONTROLLER_BASE_PATH)
public class DiscountAdminController implements DiscountAdminControllerApiSpec {

    private final DiscountCampaignAdminService discountCampaignAdminService;

    @Override
    @GetMapping(produces = APPLICATION_JSON_VALUE)
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<DiscountCampaignResponseDto>> getAll() {
        return ResponseEntity.ok(discountCampaignAdminService.getAll());
    }

    @Override
    @GetMapping(value = "/{discountType}", produces = APPLICATION_JSON_VALUE)
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<DiscountCampaignResponseDto> getByDiscountType(
            @PathVariable final DiscountType discountType) {
        return ResponseEntity.ok(discountCampaignAdminService.getByDiscountType(discountType));
    }

    @Override
    @PatchMapping(value = "/{discountType}", consumes = APPLICATION_JSON_VALUE, produces = APPLICATION_JSON_VALUE)
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<DiscountCampaignResponseDto> update(
            @PathVariable final DiscountType discountType,
            @Valid @RequestBody final DiscountCampaignUpdateRequestDto request) {
        return ResponseEntity.ok(discountCampaignAdminService.update(discountType, request));
    }
}
