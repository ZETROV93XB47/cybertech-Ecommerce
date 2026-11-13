package com.novatech.cybertech.api.controllers.implementation;

import com.novatech.cybertech.api.controllers.spec.StockAlertControllerApiSpec;
import com.novatech.cybertech.dto.response.stockalert.StockAlertResponseDto;
import com.novatech.cybertech.services.core.StockAlertService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

import static com.novatech.cybertech.constants.CyberTechAppConstants.APP_API_VERSION;
import static com.novatech.cybertech.constants.CyberTechAppConstants.DEFAULT_PAGE_SIZE_STOCK_ALERT;
import static com.novatech.cybertech.constants.CyberTechAppConstants.STOCK_ALERT_CONTROLLER_BASE_PATH;
import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping(version = APP_API_VERSION, value = STOCK_ALERT_CONTROLLER_BASE_PATH)
public class StockAlertController implements StockAlertControllerApiSpec {

    private final StockAlertService stockAlertService;

    @Override
    @PostMapping(value = "/{productUuid}", produces = APPLICATION_JSON_VALUE)
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<StockAlertResponseDto> subscribe(@PathVariable final UUID productUuid, @AuthenticationPrincipal final Jwt jwt) {
        return ResponseEntity.status(HttpStatus.CREATED).body(stockAlertService.subscribe(jwt.getSubject(), productUuid));
    }

    @Override
    @DeleteMapping(value = "/{productUuid}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> unsubscribe(@PathVariable final UUID productUuid, @AuthenticationPrincipal final Jwt jwt) {
        stockAlertService.unsubscribe(jwt.getSubject(), productUuid);
        return ResponseEntity.noContent().build();
    }

    @Override
    @GetMapping(value = "/mine", produces = APPLICATION_JSON_VALUE)
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Page<StockAlertResponseDto>> getMine(
            @AuthenticationPrincipal final Jwt jwt,
            @PageableDefault(size = DEFAULT_PAGE_SIZE_STOCK_ALERT, sort = "requestedAt", direction = Sort.Direction.DESC) final Pageable pageable
    ) {
        return ResponseEntity.ok(stockAlertService.findMine(jwt.getSubject(), pageable));
    }
}
