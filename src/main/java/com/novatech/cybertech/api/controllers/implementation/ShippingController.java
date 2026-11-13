package com.novatech.cybertech.api.controllers.implementation;

import com.novatech.cybertech.api.controllers.spec.ShippingControllerApiSpec;
import com.novatech.cybertech.dto.response.shipping.ShippingOptionResponseDto;
import com.novatech.cybertech.entities.enums.ShippingProvider;
import com.novatech.cybertech.services.core.ShippingOptionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.List;

import static com.novatech.cybertech.constants.CyberTechAppConstants.APP_API_VERSION;
import static com.novatech.cybertech.constants.CyberTechAppConstants.SHIPPING_PUBLIC_CONTROLLER_BASE_PATH;
import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

/**
 * Public shipping options, so the storefront shows shipping fees before checkout without
 * duplicating the tariffs. Whitelisted in {@link com.novatech.cybertech.config.SecurityConfig}
 * via {@code /api/v1/services/shipping/**}.
 *
 * <p>OpenAPI documentation lives on {@link ShippingControllerApiSpec}.
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping(version = APP_API_VERSION, value = SHIPPING_PUBLIC_CONTROLLER_BASE_PATH)
public class ShippingController implements ShippingControllerApiSpec {

    // Tariffs rarely change and carry no personal data: a short public cache is safe.
    private static final Duration OPTIONS_CACHE_MAX_AGE = Duration.ofMinutes(10);

    private final ShippingOptionService shippingOptionService;

    /**
     * Every available (provider, type) pair with its fee, optionally restricted to one provider.
     * An unknown {@code provider} is a 400 METHOD_ARGUMENT_TYPE_MISMATCH.
     */
    @Override
    @GetMapping(value = "/options", produces = APPLICATION_JSON_VALUE)
    public ResponseEntity<List<ShippingOptionResponseDto>> getShippingOptions(@RequestParam(required = false) final ShippingProvider provider) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(OPTIONS_CACHE_MAX_AGE).cachePublic())
                .body(shippingOptionService.getShippingOptions(provider));
    }
}
