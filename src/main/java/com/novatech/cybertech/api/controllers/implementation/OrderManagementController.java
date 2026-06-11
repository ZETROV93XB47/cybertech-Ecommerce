package com.novatech.cybertech.api.controllers.implementation;

import com.novatech.cybertech.api.controllers.spec.OrderManagementControllerApiSpec;
import com.novatech.cybertech.dto.request.order.OrderCancellationRequestDto;
import com.novatech.cybertech.dto.request.order.OrderPlacingRequestDto;
import com.novatech.cybertech.dto.request.order.OrderUpdateRequestDto;
import com.novatech.cybertech.dto.response.order.OrderResponseDto;
import com.novatech.cybertech.dto.response.order.OrderStatusDto;
import com.novatech.cybertech.entities.enums.OrderStatus;
import com.novatech.cybertech.services.core.OrderManagementService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
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
import org.springframework.web.bind.annotation.*;

import java.util.Set;
import java.util.UUID;

import static com.novatech.cybertech.constants.CyberTechAppConstants.APP_API_VERSION;
import static com.novatech.cybertech.constants.CyberTechAppConstants.DEFAULT_SORT_FIELD;
import static com.novatech.cybertech.constants.CyberTechAppConstants.ORDER_MANAGEMENT_CONTROLLER_BASE_PATH;
import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;


@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping(version = APP_API_VERSION, value = ORDER_MANAGEMENT_CONTROLLER_BASE_PATH)
@Tag(name = "OrderManagementController", description = "API for managing Orders")
public class OrderManagementController implements OrderManagementControllerApiSpec {

    // FIX(INTERFACE-CONTRACT): inject service interface instead of concrete impl per project convention
    private final OrderManagementService orderManagementService;


    @Override
    @PreAuthorize("hasRole('USER') or hasRole('ADMIN')")
    @PostMapping(value = "/place", consumes = APPLICATION_JSON_VALUE, produces = APPLICATION_JSON_VALUE)
    public ResponseEntity<OrderResponseDto> placeOrder(@Valid @RequestBody final OrderPlacingRequestDto orderPlacingRequestDto, @AuthenticationPrincipal final Jwt jwt) {
        log.info("request : {}", orderPlacingRequestDto);
        return ResponseEntity.status(HttpStatus.CREATED).body(orderManagementService.placeOrder(orderPlacingRequestDto, jwt));
    }


    @Override
    @PreAuthorize("hasRole('USER') or hasRole('ADMIN')")
    @PostMapping(value = "/cancel", consumes = APPLICATION_JSON_VALUE, produces = APPLICATION_JSON_VALUE)
    public OrderResponseDto cancelOrder(@Valid @RequestBody final OrderCancellationRequestDto orderCancellationRequestDto, @AuthenticationPrincipal final Jwt jwt) {
        return orderManagementService.cancelOrder(orderCancellationRequestDto.getOrderUuid(), jwt);
    }


    @Override
    @PreAuthorize("hasRole('USER') or hasRole('ADMIN')")
    @PostMapping(value = "/update", consumes = APPLICATION_JSON_VALUE, produces = APPLICATION_JSON_VALUE)
    public ResponseEntity<OrderResponseDto> updateOrder(@Valid @RequestBody final OrderUpdateRequestDto orderUpdateRequestDto, @AuthenticationPrincipal final Jwt jwt) {
        return ResponseEntity.status(HttpStatus.OK).body(orderManagementService.updateOrder(orderUpdateRequestDto, jwt));
    }

    @Override
    @PreAuthorize("hasRole('USER') or hasRole('ADMIN')")
    @PostMapping(value = "/retry-payment/{uuid}", produces = APPLICATION_JSON_VALUE)
    public ResponseEntity<OrderResponseDto> retryPayment(@PathVariable("uuid") final UUID uuid, @AuthenticationPrincipal final Jwt jwt) {
        return ResponseEntity.ok(orderManagementService.retryPayment(uuid, jwt));
    }


    /**
     * BUG-IDOR-D1: ownership-checked read. Forwards the JWT subject to the service so the
     * service layer can throw {@link com.novatech.cybertech.exceptions.OrderDoesntBelongsToUserException}
     * (→ 403) when the caller is not the order's initiator.
     *
     * <p>Wave 3 regression-fix: ADMINs are also allowed on this endpoint and the service
     * layer bypasses the ownership check when the caller carries {@code ROLE_ADMIN}
     * (resolved from the SecurityContext inside the service to keep this signature stable).</p>
     */
    @Override
    @PreAuthorize("hasRole('USER') or hasRole('ADMIN')")
    @GetMapping(value = "/get/{uuid}", produces = APPLICATION_JSON_VALUE)
    public ResponseEntity<OrderResponseDto> getOrderByUuid(@PathVariable("uuid") UUID orderUuid,
                                                            @AuthenticationPrincipal final Jwt jwt) {
        return ResponseEntity.status(HttpStatus.OK).body(orderManagementService.getByUUID(orderUuid, jwt.getSubject()));
    }

    /**
     * Lightweight status read for the order-confirmation polling loop. Ownership-checked at
     * the service layer (OrderDoesntBelongsToUserException → 403).
     */
    @Override
    @PreAuthorize("hasRole('USER') or hasRole('ADMIN')")
    @GetMapping(value = "/status/{uuid}", produces = APPLICATION_JSON_VALUE)
    public ResponseEntity<OrderStatusDto> getOrderStatusByUuid(@PathVariable("uuid") final UUID orderUuid,
                                                               @AuthenticationPrincipal final Jwt jwt) {
        return ResponseEntity.ok(orderManagementService.getStatusByUUID(orderUuid, jwt.getSubject()));
    }


    /**
     * Frontend-gap #1 — paginated read of the authenticated user's own orders.
     *
     * <p>Resolves the caller via the JWT subject and delegates to
     * {@link OrderManagementService#findMyOrders(String, Pageable, Set)}; the optional
     * {@code status} query param is forwarded unchanged. Spring auto-binds the {@code page}
     * + {@code size} + {@code sort} request params to {@link Pageable} via
     * {@link PageableDefault} (size 20, sort {@code createdAt DESC}).</p>
     *
     * <p>Note: the URL exposed by this controller is
     * {@code /api/v1/services/management/order/mine} — the class-level mapping is
     * {@link com.novatech.cybertech.constants.CyberTechAppConstants#ORDER_MANAGEMENT_CONTROLLER_BASE_PATH}
     * which already hosts every other order endpoint.</p>
     */
    @Override
    @PreAuthorize("hasRole('USER') or hasRole('ADMIN')")
    @GetMapping(value = "/mine", produces = APPLICATION_JSON_VALUE)
    public ResponseEntity<Page<OrderResponseDto>> getMyOrders(
            @AuthenticationPrincipal final Jwt jwt,
            @PageableDefault(size = 20, sort = DEFAULT_SORT_FIELD, direction = Sort.Direction.DESC) final Pageable pageable,
            @RequestParam(value = "status", required = false) final Set<OrderStatus> status) {
        return ResponseEntity.ok(orderManagementService.findMyOrders(jwt.getSubject(), pageable, status));
    }
}
