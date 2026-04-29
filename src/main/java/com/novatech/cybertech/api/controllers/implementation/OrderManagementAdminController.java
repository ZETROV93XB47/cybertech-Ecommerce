package com.novatech.cybertech.api.controllers.implementation;

import com.novatech.cybertech.api.controllers.spec.OrderManagementAdminControllerApiSpec;
import com.novatech.cybertech.dto.response.order.OrderResponseDto;
import com.novatech.cybertech.entities.enums.OrderStatus;
import com.novatech.cybertech.services.core.OrderManagementService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Set;

import static com.novatech.cybertech.constants.CyberTechAppConstants.APP_API_VERSION;
import static com.novatech.cybertech.constants.CyberTechAppConstants.DEFAULT_PAGE_SIZE_ADMIN;
import static com.novatech.cybertech.constants.CyberTechAppConstants.DEFAULT_SORT_FIELD;
import static com.novatech.cybertech.constants.CyberTechAppConstants.ORDER_MANAGEMENT_ADMIN_CONTROLLER_BASE_PATH;
import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

/**
 * Frontend-gap #2 — admin-side paginated order listing.
 *
 * <p>Hosts {@code GET /api/v1/services/admin/management/order/get/all} with optional
 * {@code status} and {@code userKeycloakId} query params. Mirrors the existing
 * {@link UserManagementAdminController} pattern: class-level {@code @PreAuthorize("hasRole('ADMIN')")}
 * and dedicated ApiSpec interface.</p>
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@RequestMapping(version = APP_API_VERSION, value = ORDER_MANAGEMENT_ADMIN_CONTROLLER_BASE_PATH)
@Tag(name = "OrderAdminController", description = "API for Order management (Admin)")
public class OrderManagementAdminController implements OrderManagementAdminControllerApiSpec {

    // FIX(INTERFACE-CONTRACT): inject service interface instead of concrete impl per project convention
    private final OrderManagementService orderManagementService;

    @Override
    @GetMapping(value = "/get/all", produces = APPLICATION_JSON_VALUE)
    public ResponseEntity<Page<OrderResponseDto>> getAllOrders(
            @RequestParam(value = "status", required = false) final Set<OrderStatus> status,
            @RequestParam(value = "userKeycloakId", required = false) final String userKeycloakId,
            @PageableDefault(size = DEFAULT_PAGE_SIZE_ADMIN, sort = DEFAULT_SORT_FIELD, direction = Sort.Direction.DESC) final Pageable pageable) {
        return ResponseEntity.ok(orderManagementService.findAllPaged(status, userKeycloakId, pageable));
    }
}
