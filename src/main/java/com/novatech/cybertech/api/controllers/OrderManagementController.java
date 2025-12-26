package com.novatech.cybertech.api.controllers;

import com.novatech.cybertech.api.controllers.spec.OrderManagementControllerApiSpec;
import com.novatech.cybertech.dto.request.order.OrderCancellationRequestDto;
import com.novatech.cybertech.dto.request.order.OrderPlacingRequestDto;
import com.novatech.cybertech.dto.response.order.OrderResponseDto;
import com.novatech.cybertech.services.core.OrderManagementService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static com.novatech.cybertech.constants.CyberTechAppConstants.ORDER_MANAGEMENT_CONTROLLER_BASE_PATH;
import static com.novatech.cybertech.utils.DataGenerator.orderGenerator;
import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;


@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping(ORDER_MANAGEMENT_CONTROLLER_BASE_PATH)
@Tag(name = " OrderManagementController", description = "API for managing Orders")
public class OrderManagementController implements OrderManagementControllerApiSpec {

    private final OrderManagementService orderManagementService;

    @Override
    @PreAuthorize("hasAuthority('ROLE_USER') or hasAuthority('ROLE_ADMIN')")
    @PostMapping(value = "/place", consumes = APPLICATION_JSON_VALUE, produces = APPLICATION_JSON_VALUE)
    public ResponseEntity<OrderResponseDto> placeOrder(@Valid @RequestBody final OrderPlacingRequestDto orderPlacingRequestDto, @AuthenticationPrincipal final Jwt jwt) {
        return ResponseEntity.status(HttpStatus.CREATED).body(orderManagementService.placeOrder(orderPlacingRequestDto, jwt.getSubject()));
    }

    @Override
    @PreAuthorize("hasAuthority('ROLE_USER') or hasAuthority('ROLE_ADMIN')")
    @PostMapping(value = "/cancel", consumes = APPLICATION_JSON_VALUE, produces = APPLICATION_JSON_VALUE)
    public OrderResponseDto cancelOrder(@Valid @RequestBody final OrderCancellationRequestDto orderCancellationRequestDto, @AuthenticationPrincipal final Jwt jwt) {
        return orderManagementService.cancelOrder(orderCancellationRequestDto.getOrderUuid(), jwt.getSubject());
    }


    @PreAuthorize("hasAuthority('ROLE_USER') or hasAuthority('ROLE_ADMIN')")
    @PostMapping(value = "/place/auto", consumes = APPLICATION_JSON_VALUE, produces = APPLICATION_JSON_VALUE)
    public ResponseEntity<OrderResponseDto> placeOrder2(@AuthenticationPrincipal final Jwt jwt) {
        return ResponseEntity.status(HttpStatus.CREATED).body(orderManagementService.placeOrder(orderGenerator(), jwt.getSubject()));
    }
}
